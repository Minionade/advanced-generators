package net.bdew.generators.machine;

import net.bdew.generators.config.GeneratorConfig;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Exercises the shared discovery, capacity, persistence and connection code with unloaded chunks. */
class MultiblockLifecycleTest {
    private static final BlockPos CORE = new BlockPos(15, 64, 8);

    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static void field(Class<?> owner, Object target, String name, Object value) throws Exception {
        var field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class World {
        final Level level = mock(Level.class);
        final Map<BlockPos, MachineBlockEntity> blocks = new HashMap<>();
        final Set<ChunkPos> unloaded = new HashSet<>();
        final RegistryAccess registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        long time;

        World() {
            when(level.hasChunkAt(any(BlockPos.class))).thenAnswer(call -> !unloaded.contains(new ChunkPos(call.getArgument(0))));
            when(level.getBlockEntity(any(BlockPos.class))).thenAnswer(call -> {
                BlockPos pos = call.getArgument(0);
                assertTrue(level.hasChunkAt(pos), "Discovery must never load a chunk: " + pos);
                return blocks.get(pos);
            });
            when(level.getGameTime()).thenAnswer(call -> time);
        }

        MachineBlockEntity machine(BlockPos pos, MachineKind kind, BlockPos core) throws Exception {
            var machine = mock(MachineBlockEntity.class);
            field(BlockEntity.class, machine, "level", level);
            field(BlockEntity.class, machine, "worldPosition", pos);
            field(MachineBlockEntity.class, machine, "connectedCore", core);
            field(MachineBlockEntity.class, machine, "acceptingModules", true);
            field(MachineBlockEntity.class, machine, "moduleInventory", new ModuleInventory());
            field(MachineBlockEntity.class, machine, "buffers", new ResourceBuffer[]{
                new ResourceBuffer(32000), new ResourceBuffer(32000), new ResourceBuffer(32000), new ResourceBuffer(32000)});
            field(MachineBlockEntity.class, machine, "inventory", new SimpleContainer(4));
            field(MachineBlockEntity.class, machine, "outputConfigs", new LinkedHashMap<>());
            field(MachineBlockEntity.class, machine, "outputMode", new int[6]);
            field(MachineBlockEntity.class, machine, "outputSelection", new int[6]);
            field(MachineBlockEntity.class, machine, "outputLimit", new long[]{1000,1000,1000,1000,1000,1000});
            when(machine.getLevel()).thenReturn(level);
            when(machine.getBlockPos()).thenReturn(pos);
            when(machine.getBlockState()).thenReturn(Blocks.DISPENSER.defaultBlockState());
            when(machine.kind()).thenReturn(kind);
            when(machine.connectedCore()).thenCallRealMethod();
            when(machine.moduleInventory()).thenCallRealMethod();
            when(machine.network()).thenCallRealMethod();
            doCallRealMethod().when(machine).cacheNetwork(any());
            when(machine.controller()).thenCallRealMethod();
            when(machine.members()).thenCallRealMethod();
            when(machine.canAcceptModule(any())).thenCallRealMethod();
            when(machine.tryConnect()).thenCallRealMethod();
            when(machine.connectTo(any())).thenCallRealMethod();
            when(machine.forced(any())).thenCallRealMethod();
            when(machine.missingRequiredModules()).thenCallRealMethod();
            when(machine.outputEntries()).thenCallRealMethod();
            doCallRealMethod().when(machine).recalculate();
            doCallRealMethod().when(machine).refreshOutputFaces();
            doCallRealMethod().when(machine).saveAdditional(any(), any());
            doCallRealMethod().when(machine).loadAdditional(any(), any());
            blocks.put(pos, machine);
            return machine;
        }

        MachineBlockEntity fuelMachine() throws Exception {
            var core = machine(CORE, MachineKind.FUEL_TURBINE_CONTROLLER, null);
            machine(CORE.east(), MachineKind.FUEL_TANK, CORE);
            machine(CORE.east().above(), MachineKind.POWER_CAPACITOR_TIER1, CORE);
            machine(CORE.above(), MachineKind.TURBINE_TIER1, CORE);
            core.energy = 900000;
            core.energyFraction = .375;
            core.buffers[0].id = ResourceLocation.parse("minecraft:lava");
            core.buffers[0].amount = 16000.25;
            return core;
        }

        CompoundTag save(MachineBlockEntity machine) {
            var tag = new CompoundTag();
            machine.saveAdditional(tag, registries);
            return tag;
        }

        void rediscover() { time += 20; }
        void remove(BlockPos pos) { blocks.remove(pos); MultiblockNetwork.invalidate(level); }
    }

    private static void assertContents(MachineBlockEntity core) {
        assertEquals(900000, core.energy);
        assertEquals(.375, core.energyFraction);
        assertEquals(16000.25, core.buffers[0].amount);
    }

    private static void assertBaseCapacity(MachineBlockEntity core) {
        assertEquals(GeneratorConfig.INSTANCE.energyCapacity, core.energyCapacity);
        assertEquals(GeneratorConfig.INSTANCE.energyCapacity, core.energy);
        assertEquals(0, core.energyFraction);
        assertEquals(GeneratorConfig.INSTANCE.fuelCapacity, core.buffers[0].capacity);
        assertEquals(GeneratorConfig.INSTANCE.fuelCapacity, core.buffers[0].amount);
    }

    @Test void unloadingAndReloadingStorageDoesNotDiscardOrDuplicateContents() throws Exception {
        var world = new World();
        var core = world.fuelMachine();
        core.recalculate();
        long energyCapacity = core.energyCapacity;
        double fuelCapacity = core.buffers[0].capacity;
        world.unloaded.add(new ChunkPos(1, 0));
        core.recalculate(); // Even the previously cached network must retain capacity.
        world.rediscover();
        core.recalculate();
        assertContents(core);
        assertEquals(energyCapacity, core.energyCapacity);
        assertEquals(fuelCapacity, core.buffers[0].capacity);
        assertEquals(0, core.network().error());
        world.unloaded.clear();
        world.rediscover();
        core.recalculate();
        assertContents(core);
    }

    @Test void loadedRemovalStillClampsAndDoesNotDependOnUnrelatedNeighbourChunks() throws Exception {
        var world = new World();
        var core = world.fuelMachine();
        world.remove(CORE.east());
        world.remove(CORE.east().above());
        world.machine(CORE.west(), MachineKind.FUEL_TANK, CORE);
        world.machine(CORE.west().above(), MachineKind.POWER_CAPACITOR_TIER1, CORE);
        core.recalculate();
        // This unloaded chunk borders the controller, but contains no machine modules.
        world.unloaded.add(new ChunkPos(1, 0));
        world.remove(CORE.west());
        world.remove(CORE.west().above());
        core.recalculate();
        assertBaseCapacity(core);
        assertEquals(0, core.network().error());
    }

    @Test void savingAndReloadingWithStorageUnloadedRetainsItsCapacity() throws Exception {
        var world = new World();
        var core = world.fuelMachine();
        core.recalculate();
        world.unloaded.add(new ChunkPos(1, 0));
        world.rediscover();
        core.recalculate();
        var saved = world.save(core);
        core = world.machine(CORE, MachineKind.FUEL_TURBINE_CONTROLLER, null);
        core.loadAdditional(saved, world.registries);
        core.recalculate();
        assertContents(core);
        assertEquals(GeneratorConfig.INSTANCE.energyCapacity + GeneratorConfig.INSTANCE.capacitorCapacity[0], core.energyCapacity);
        assertEquals(GeneratorConfig.INSTANCE.fuelCapacity + GeneratorConfig.INSTANCE.fuelTankCapacity, core.buffers[0].capacity);
        world.unloaded.clear();
        world.remove(CORE.east());
        world.remove(CORE.east().above());
        core.recalculate();
        assertBaseCapacity(core);
    }

    @Test void unloadedBridgeKeepsLoadedBranchConnectedAndOutputSettingsIntact() throws Exception {
        var world = new World();
        var position = new BlockPos(15, 64, 0);
        var bridge = position.east();
        var core = world.machine(position, MachineKind.FUEL_TURBINE_CONTROLLER, null);
        world.machine(bridge, MachineKind.TURBINE_TIER1, position);
        world.machine(bridge.north(), MachineKind.FUEL_TANK, position);
        world.machine(bridge.north().above(), MachineKind.POWER_CAPACITOR_TIER1, position);
        var output = world.machine(bridge.north().east(), MachineKind.EU_OUTPUT_LV, position);
        core.recalculate();
        core.refreshOutputFaces();
        var config = core.outputEntries().getFirst().config();
        config.mode = 3;
        config.limit = 123;
        world.unloaded.add(new ChunkPos(1, 0));
        world.rediscover();
        core.recalculate();
        core.refreshOutputFaces();
        assertSame(core, output.controller());
        assertTrue(core.missingRequiredModules().isEmpty());
        assertEquals(1, core.turbines);
        assertSame(config, core.outputEntries().getFirst().config());
        assertEquals(3, config.mode);
        assertEquals(123, config.limit);
        world.unloaded.clear();
        world.remove(bridge);
        core.recalculate();
        assertNull(output.controller());
        assertEquals(GeneratorConfig.INSTANCE.energyCapacity, core.energyCapacity);
        assertEquals(GeneratorConfig.INSTANCE.fuelCapacity, core.buffers[0].capacity);
    }

    @Test void unloadedCapacitorsStillCountTowardsModuleLimit() throws Exception {
        var world = new World();
        var core = world.machine(CORE, MachineKind.FUEL_TURBINE_CONTROLLER, null);
        int max = GeneratorConfig.INSTANCE.limit(core.kind(), "powerCapacitor");
        for (int i = 1; i <= max; i++) world.machine(CORE.offset(i, 0, 0), MachineKind.POWER_CAPACITOR_TIER1, CORE);
        assertFalse(core.canAcceptModule(MachineKind.POWER_CAPACITOR_TIER1));
        world.unloaded.add(new ChunkPos(1, 0));
        world.rediscover();
        assertFalse(core.canAcceptModule(MachineKind.POWER_CAPACITOR_TIER1));
        world.unloaded.clear();
        world.remove(CORE.offset(max, 0, 0));
        assertTrue(core.canAcceptModule(MachineKind.POWER_CAPACITOR_TIER1));
    }

    @Test void neighbourUpdatesRetainLinkAndForcedFacesWhileControllerIsUnloaded() throws Exception {
        var world = new World();
        var core = world.machine(CORE, MachineKind.FUEL_TURBINE_CONTROLLER, null);
        var output = world.machine(CORE.east(), MachineKind.FE_OUTPUT, CORE);
        field(MachineBlockEntity.class, output, "forcedSides", 1 << Direction.NORTH.get3DDataValue());
        assertSame(core, output.controller());
        world.unloaded.add(new ChunkPos(0, 0));
        assertFalse(output.tryConnect());
        assertEquals(CORE, output.connectedCore());
        assertTrue(output.forced(Direction.NORTH));
        world.unloaded.clear();
        world.remove(CORE);
        assertFalse(output.tryConnect());
        assertNull(output.connectedCore());
        assertFalse(output.forced(Direction.NORTH));
    }

    @Test void upgradingBridgePreservesRememberedUnloadedBranch() throws Exception {
        var world = new World();
        var position = new BlockPos(15, 64, 0);
        var bridge = position.east();
        var core = world.machine(position, MachineKind.FUEL_TURBINE_CONTROLLER, null);
        world.machine(bridge, MachineKind.TURBINE_TIER1, position);
        world.machine(bridge.north(), MachineKind.POWER_CAPACITOR_TIER1, position);
        core.recalculate();
        long capacity = core.energyCapacity;
        world.unloaded.add(new ChunkPos(1, -1));
        world.remove(bridge);
        var upgraded = world.machine(bridge, MachineKind.TURBINE_TIER2, null);
        assertTrue(upgraded.connectTo(core));
        core.recalculate();
        assertEquals(capacity, core.energyCapacity);
        assertEquals(GeneratorConfig.INSTANCE.turbineOutput[1], core.maxOutput);
    }

    @Test void breakingLoadedBridgeRemovesCapacityOfItsUnloadedBranch() throws Exception {
        var world = new World();
        var position = new BlockPos(15, 64, 0);
        var bridge = position.east();
        var core = world.machine(position, MachineKind.FUEL_TURBINE_CONTROLLER, null);
        world.machine(bridge, MachineKind.TURBINE_TIER1, position);
        world.machine(bridge.north(), MachineKind.POWER_CAPACITOR_TIER1, position);
        world.machine(bridge.north().above(), MachineKind.FUEL_TANK, position);
        core.energy = 900000;
        core.buffers[0].amount = 16000;
        core.recalculate();
        world.unloaded.add(new ChunkPos(1, -1));
        world.remove(bridge);
        core.recalculate();
        assertBaseCapacity(core);
    }

    private static void oldJavaSave(boolean modulesStillExist) throws Exception {
        var world = new World();
        var core = world.fuelMachine();
        var saved = world.save(core);
        saved.remove("modules");
        saved.remove("moduleInventoryIncomplete");
        world.unloaded.add(new ChunkPos(1, 0));
        core.loadAdditional(saved, world.registries);
        core.recalculate();
        assertContents(core);
        assertEquals(0, core.network().error());
        var partialSave = world.save(core);
        assertTrue(partialSave.getBoolean("moduleInventoryIncomplete"));
        core = world.machine(CORE, MachineKind.FUEL_TURBINE_CONTROLLER, null);
        core.loadAdditional(partialSave, world.registries);
        core.recalculate();
        assertContents(core);
        if (!modulesStillExist) {
            world.remove(CORE.east());
            world.remove(CORE.east().above());
        }
        world.unloaded.clear();
        world.rediscover();
        core.recalculate();
        assertFalse(world.save(core).getBoolean("moduleInventoryIncomplete"));
        if (modulesStillExist) assertContents(core);
        else assertBaseCapacity(core);
    }

    @Test void earlyJavaSavesRetainContentsUntilMissingChunksCanBeInspected() throws Exception {
        oldJavaSave(true);
    }

    @Test void earlyJavaRecoveryEventuallyClampsIfModulesReallyAreGone() throws Exception {
        oldJavaSave(false);
    }

    @Test void neighbourUpdatesRetainOldJavaLinksBehindAnUnknownUnloadedBridge() throws Exception {
        var world = new World();
        var position = new BlockPos(15, 64, 0);
        var bridge = position.east();
        var core = world.machine(position, MachineKind.FUEL_TURBINE_CONTROLLER, null);
        world.machine(bridge, MachineKind.TURBINE_TIER1, position);
        var output = world.machine(bridge.north(), MachineKind.FE_OUTPUT, position);
        field(MachineBlockEntity.class, output, "forcedSides", 1 << Direction.NORTH.get3DDataValue());
        var old = world.save(core);
        old.remove("modules");
        world.unloaded.add(new ChunkPos(1, 0));
        core.loadAdditional(old, world.registries);
        assertNull(output.controller());
        assertFalse(output.tryConnect());
        assertEquals(position, output.connectedCore());
        assertTrue(output.forced(Direction.NORTH));
        world.unloaded.clear();
        world.rediscover();
        assertSame(core, output.controller());
    }

    @Test void scalaPositionsWithoutModuleTypesAreKeptUntilTheirChunksLoad() throws Exception {
        var world = new World();
        var core = world.fuelMachine();
        core.recalculate();
        var saved = world.save(core);
        for (var entry : saved.getList("modules", Tag.TAG_COMPOUND)) ((CompoundTag) entry).remove("kind");
        world.unloaded.add(new ChunkPos(1, 0));
        core.loadAdditional(saved, world.registries);
        core.recalculate();
        assertContents(core);
        var partialSave = world.save(core);
        assertEquals(3, partialSave.getList("modules", Tag.TAG_COMPOUND).size());
        world.unloaded.clear();
        world.rediscover();
        core.recalculate();
        assertContents(core);
        assertTrue(core.moduleInventory().knowsAll(core.network().members()));
    }
}
