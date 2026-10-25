package net.bdew.generators.machine;

import net.bdew.generators.ModContent;
import net.bdew.generators.config.GeneratorConfig;
import net.bdew.generators.menu.MachineMenu;
import net.bdew.generators.platform.TransferPlatform;
import net.bdew.generators.recipe.MachineRecipe;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import java.util.*;

public class MachineBlockEntity extends BlockEntity implements MenuProvider {
    public final ResourceBuffer[] buffers = {new ResourceBuffer(32000), new ResourceBuffer(32000), new ResourceBuffer(32000), new ResourceBuffer(32000)};
    public final SimpleContainer inventory = new SimpleContainer(4) { @Override public boolean canPlaceItem(int slot, ItemStack stack) { return kind()==MachineKind.SYNGAS_CONTROLLER && carbonValue(stack)>0; } };
    public long energy, energyCapacity;
    public double maxOutput, heat, carbon, burnTime, rpm, energyFraction;
    public double generationRate, consumptionRate, processRate, heatLossRate;
    public double heatDeltaRate;
    public ResourceLocation lastExchangerInput, lastExchangerOutput;
    public boolean lastExchangerInputItem, lastExchangerOutputItem;
    public int turbines, heatingChambers, mixingChambers, exchangers;
    public double inertia = 1, efficiency = 1.1;
    public int controlAction, sensorMetric, sensorCondition;
    public boolean controlHigh = true;
    public final int[] outputMode = new int[6], outputSelection = new int[6];
    public final long[] outputLimit = new long[6];
    private final LinkedHashMap<OutputFace, OutputConfig> outputConfigs = new LinkedHashMap<>();
    private final double[] steamHistory = new double[20];
    private int steamIndex;
    private final RollingAverage generationAverage = new RollingAverage();
    private final RollingAverage consumptionAverage = new RollingAverage();
    private final RollingAverage processAverage = new RollingAverage();
    private final RollingAverage heatLossAverage = new RollingAverage();
    private final RollingAverage heatDeltaAverage = new RollingAverage();
    private MultiblockNetwork cachedNetwork;
    private long networkRevision = -1, networkTick = -1;
    private BlockPos connectedCore;
    public MachineBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.MACHINE_ENTITY.get(), pos, state);
        Arrays.fill(outputLimit, Integer.MAX_VALUE);
        inventory.addListener(c -> setChanged());
    }
    public record OutputFace(long position, Direction direction) {
        public OutputFace(BlockPos position, Direction direction) { this(position.asLong(), direction); }
        public BlockPos blockPos() { return BlockPos.of(position); }
    }
    public static final class OutputConfig {
        public int mode, selection;
        public long limit;
        public double average;
        private final double[] recent = new double[20];
        private int next;
        private double total;
        private OutputConfig(int mode, int selection, long limit) {
            this.mode = mode; this.selection = selection; this.limit = limit;
        }
        private void sample(double amount) {
            total += amount - recent[next]; recent[next] = amount; next = (next + 1) % recent.length;
            average = total / recent.length;
        }
    }
    public record OutputFaceEntry(MachineKind kind, Direction direction, OutputConfig config) {}
    public MachineKind kind() { return ((MachineBlock)getBlockState().getBlock()).kind; }
    public void cacheNetwork(MultiblockNetwork network) { cachedNetwork = network; networkRevision = MultiblockNetwork.revision(level); networkTick = level.getGameTime(); }
    public MultiblockNetwork network() {
        if (level == null) return new MultiblockNetwork(null, List.of(), 1);
        if (cachedNetwork == null || networkRevision != MultiblockNetwork.revision(level) || level.getGameTime() - networkTick >= 20) return MultiblockNetwork.discover(this);
        return cachedNetwork;
    }
    public BlockPos connectedCore() { return connectedCore; }
    public boolean connectTo(MachineBlockEntity core) {
        if (kind().controller() || core == null || !core.kind().controller() || connectedCore != null || !core.canAcceptModule(kind())) return false;
        connectedCore = core.getBlockPos().immutable();
        setChanged();
        if (level != null) {
            MultiblockNetwork.invalidate(level);
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        }
        return true;
    }
    public boolean tryConnect() {
        if (level == null || kind().controller()) return false;
        if (connectedCore != null) {
            if (controller() != null) return false;
            connectedCore = null;
        }
        var candidates = MultiblockNetwork.findConnections(level, worldPosition, kind());
        if (candidates.size() != 1) return false;
        return level.getBlockEntity(candidates.iterator().next()) instanceof MachineBlockEntity core && connectTo(core);
    }
    public boolean canAcceptModule(MachineKind moduleKind) {
        if (!kind().controller() || moduleKind.controller() || level == null) return false;
        int max = GeneratorConfig.INSTANCE.limit(kind(), moduleKind.category);
        if (max <= 0) return false;
        return members().stream().filter(m -> m.kind().category.equals(moduleKind.category)).count() < max;
    }
    public boolean hasRequiredModules() {
        return missingRequiredModules().isEmpty();
    }
    public Map<String,Integer> missingRequiredModules() {
        var required = switch (kind()) {
            case FUEL_TURBINE_CONTROLLER, STEAM_TURBINE_CONTROLLER -> Map.of("turbine",1);
            case SYNGAS_CONTROLLER -> Map.of("mixingChamber",1);
            case EXCHANGER_CONTROLLER -> Map.of("heatExchanger",1);
            default -> Map.<String,Integer>of();
        };
        var counts = new HashMap<String,Integer>();
        for (var module : members()) counts.merge(module.kind().category,1,Integer::sum);
        var missing = new LinkedHashMap<String,Integer>();
        required.forEach((category,count) -> { if (counts.getOrDefault(category,0)<count) missing.put(category,count-counts.getOrDefault(category,0)); });
        return missing;
    }
    public void detachModules() {
        if (level == null || !kind().controller()) return;
        for (var module : members()) {
            module.connectedCore = null;
            module.setChanged();
            level.sendBlockUpdated(module.worldPosition, module.getBlockState(), module.getBlockState(), 3);
            level.updateNeighborsAt(module.worldPosition, module.getBlockState().getBlock());
        }
        MultiblockNetwork.invalidate(level);
    }
    public MachineBlockEntity controller() {
        if (level == null) return null;
        if (!kind().controller() && connectedCore == null) return null;
        var network = network();
        if (network.error() != 0 || network.controller() == null || !level.hasChunkAt(network.controller())) return null;
        if (!kind().controller() && !network.members().contains(worldPosition)) return null;
        return level.getBlockEntity(network.controller()) instanceof MachineBlockEntity m ? m : null;
    }
    public List<MachineBlockEntity> members() {
        var result = new ArrayList<MachineBlockEntity>();
        for (var p : network().members()) if (level.hasChunkAt(p) && level.getBlockEntity(p) instanceof MachineBlockEntity m) result.add(m);
        return result;
    }
    public BlockPos findUpgradeTarget(ResourceLocation from) {
        if (BuiltInRegistries.BLOCK.getKey(getBlockState().getBlock()).equals(from)) return worldPosition;
        for (var machine : members()) if (BuiltInRegistries.BLOCK.getKey(machine.getBlockState().getBlock()).equals(from)) return machine.getBlockPos();
        return null;
    }
    public static void tick(Level level, BlockPos pos, BlockState state, MachineBlockEntity machine) {
        var network = machine.network();
        if (machine.kind().controller()) {
            machine.recalculate();
            machine.refreshOutputFaces();
            machine.generationRate = machine.consumptionRate = machine.processRate = machine.heatLossRate = machine.heatDeltaRate = 0;
            if (network.error() == 0) {
                switch (machine.kind()) {
                    case FUEL_TURBINE_CONTROLLER -> machine.tickFuel();
                    case STEAM_TURBINE_CONTROLLER -> machine.tickSteam();
                    case SYNGAS_CONTROLLER -> machine.tickSyngas();
                    case EXCHANGER_CONTROLLER -> machine.tickExchanger();
                    default -> {}
                }
                machine.sampleRates();
                machine.setChanged();
            }
        } else if (machine.kind().output() && network.error() == 0) TransferPlatform.push(machine);
        if (machine.kind() == MachineKind.SENSOR || machine.kind() == MachineKind.CONTROL) {
            boolean powered = machine.kind() == MachineKind.CONTROL ? level.hasNeighborSignal(pos) : machine.sensor();
            if (state.getValue(BlockStateProperties.POWERED) != powered) {
                level.setBlock(pos, state.setValue(BlockStateProperties.POWERED, powered), 3);
                level.updateNeighborsAt(pos, state.getBlock());
            }
        }
    }
    private void sampleRates() {
        generationRate = generationAverage.update(generationRate);
        consumptionRate = consumptionAverage.update(consumptionRate);
        processRate = processAverage.update(processRate);
        heatLossRate = heatLossAverage.update(heatLossRate);
        heatDeltaRate = heatDeltaAverage.update(heatDeltaRate);
    }
    private static final class RollingAverage {
        private final double[] values = new double[20];
        private int index;
        private double total;
        double update(double value) {
            total += value - values[index];
            values[index] = value;
            index = (index + 1) % values.length;
            return total / values.length;
        }
        double average() { return total / values.length; }
        void clear() { Arrays.fill(values, 0); index = 0; total = 0; }
    }
    public void recalculate() {
        var c = GeneratorConfig.INSTANCE;
        energyCapacity = c.energyCapacity; maxOutput = 0; turbines = heatingChambers = mixingChambers = exchangers = 0;
        int tanks = 0; boolean tier1 = false, tier2 = false; double totalInertia = 0;
        for (var m : members()) {
            var kind = m.kind();
            switch (kind.category) {
                case "turbine" -> { turbines++; maxOutput += c.turbineOutput[kind.tier-1]; totalInertia += c.turbineInertia[kind.tier-1]; }
                case "powerCapacitor" -> energyCapacity += c.capacitorCapacity[kind.tier-1];
                case "fuelTank" -> tanks++;
                case "heatingChamber" -> heatingChambers++;
                case "mixingChamber" -> mixingChambers++;
                case "heatExchanger" -> exchangers++;
                case "efficiencyUpgradeTier1" -> tier1 = true;
                case "efficiencyUpgradeTier2" -> tier2 = true;
            }
        }
        inertia = turbines == 0 ? 1 : totalInertia / turbines;
        efficiency = c.fuelEfficiency[tier1 ? (tier2 ? 2 : 1) : 0];
        double capacity = switch (kind()) {
            case FUEL_TURBINE_CONTROLLER -> c.fuelCapacity + (double)tanks * c.fuelTankCapacity;
            case STEAM_TURBINE_CONTROLLER -> c.steamCapacity;
            case SYNGAS_CONTROLLER -> c.syngasCapacity;
            default -> c.exchangerCapacity;
        };
        for (var buffer : buffers) buffer.capacity = capacity;
        // Do not erase stored resources when a module or chunk disappears. Over-capacity storage refuses insertions.
    }
    private List<RecipeHolder<MachineRecipe>> recipes(String type) { return level.getRecipeManager().getAllRecipesFor(ModContent.RECIPE_TYPES.get(type).get()); }
    public double carbonValue(ItemStack stack) {
        return recipes("carbon_source").stream().map(RecipeHolder::value).filter(r -> r.ingredient.test(stack)).mapToDouble(r -> r.value("carbonValue")).findFirst().orElse(0);
    }
    private double fuelValue() {
        if (buffers[0].empty()) return 0;
        return recipes("liquid_fuel").stream().map(RecipeHolder::value).filter(r -> r.matchesFluid(buffers[0].fluid())).mapToDouble(r -> r.value("fePerMb")).findFirst().orElse(0);
    }
    private void addEnergy(double quantity) {
        double accepted = Math.min(quantity, Math.max(0, energyCapacity - energy));
        energyFraction += accepted; long whole = (long)Math.floor(energyFraction); energy += whole; energyFraction -= whole; generationRate = accepted;
    }
    private void tickFuel() {
        if (maxOutput <= 0 || !control(1, true)) return;
        double fuelPerTick = fuelValue() > 0 ? maxOutput / fuelValue() / efficiency : 0;
        if (burnTime < 5 && fuelPerTick > 0) {
            double consumed = buffers[0].extract(Math.ceil(10 * fuelPerTick), false);
            burnTime += consumed / fuelPerTick; consumptionRate = consumed;
        }
        // Preserve the legacy controller's strict thresholds: a full output
        // packet needs a little more than one tick of stored burn time, and
        // the capacitor must have room strictly greater than that packet.
        if (burnTime > 1 && energyCapacity - energy > maxOutput) { burnTime -= 1; addEnergy(maxOutput); }
    }
    private void tickSteam() {
        var c = GeneratorConfig.INSTANCE;
        if (maxOutput <= 0) { rpm = 0; return; }
        double delta = EngineMath.rpmDelta(rpm, c.maxRpm, c.inertiaMultiplier, c.inertiaExponent, inertia);
        if (control(3, energy < energyCapacity) && rpm > 1) { addEnergy(rpm / c.maxRpm * maxOutput); rpm -= delta * c.coilDrag; }
        rpm = Math.max(0, rpm - delta * c.baseDrag);
        double maxSteam = maxOutput / c.steamEnergyDensity;
        double used = control(2, true) ? buffers[0].extract(Math.ceil(maxSteam), false) : 0;
        steamHistory[steamIndex++ % 20] = used; consumptionRate = used;
        double average = Arrays.stream(steamHistory).sum() / 20;
        if (average > 0) rpm = Math.min(rpm + delta * c.spinUp, Math.max(rpm, Math.min(c.maxRpm, average / maxSteam * c.maxRpm)));
        if (rpm < 1) rpm = 0;
    }
    private void tickSyngas() {
        var c = GeneratorConfig.INSTANCE;
        double startingHeat = heat;
        if (heat < c.syngasMaxHeat && carbon > 0 && heatingChambers > 0 && control(5, buffers[0].amount > 0)) {
            double add = EngineMath.min(c.syngasMaxHeat-heat, carbon/c.carbonPerHeat, heatingChambers*c.chamberHeating);
            heat += add; carbon -= add*c.carbonPerHeat; consumptionRate += add*c.carbonPerHeat;
        }
        if (heat > c.syngasWorkHeat && heatingChambers > 0) {
            double steam = EngineMath.min(buffers[0].amount*c.waterSteamRatio, c.syngasCapacity-buffers[1].amount, heatingChambers*c.heatingThroughput*heat/c.syngasMaxHeat);
            buffers[0].extract(Math.ceil(steam/c.waterSteamRatio), false);
            buffers[1].insert(false, net.bdew.generators.Generators.id("steam"), DataComponentPatch.EMPTY, steam, false);
        }
        if (control(6, true)) {
            double gas = Math.floor(EngineMath.min(carbon/c.carbonPerSyngas, buffers[1].amount/c.steamPerSyngas, buffers[2].capacity-buffers[2].amount, c.mixingThroughput*mixingChambers));
            carbon -= gas*c.carbonPerSyngas; buffers[1].extract(gas*c.steamPerSyngas, false);
            buffers[2].insert(false, net.bdew.generators.Generators.id("syngas"), DataComponentPatch.EMPTY, gas, false);
            processRate = gas; consumptionRate += gas*c.carbonPerSyngas;
        }
        heat -= Math.min(heat, heatingChambers*c.chamberLoss);
        for (int slot=0;slot<4;slot++) {
            double value = carbonValue(inventory.getItem(slot));
            if (value > 0 && c.syngasCapacity-carbon >= value) { inventory.removeItem(slot,1); carbon += value; }
        }
        heatDeltaRate = heat - startingHeat;
    }
    private MachineRecipe exchangerRecipe(String type, boolean item, ResourceLocation id, DataComponentPatch patch) {
        if (id == null) return null;
        return recipes(type).stream().map(RecipeHolder::value).filter(r -> r.matchesInput(item,id,patch)).findFirst().orElse(null);
    }
    private record Exchange(double heat, double input, double output) {}
    private Exchange exchange(ResourceBuffer input, ResourceBuffer output, String type, double budget) {
        if (input.empty()) return new Exchange(0,0,0);
        var r = exchangerRecipe(type,input.item,input.id,input.components); if (r == null) return new Exchange(0,0,0);
        double space = output.insert(r.outputItem(),r.outputId(),DataComponentPatch.EMPTY,Math.max(0,budget*r.outPerHeat()),true);
        double hu = EngineMath.heatTransfer(budget,input.amount,space,r.inPerHeat(),r.outPerHeat());
        if (hu <= 0) return new Exchange(0,0,0);
        double inputAmount = input.extract(hu*r.inPerHeat(),false);
        double outputAmount = output.insert(r.outputItem(),r.outputId(),DataComponentPatch.EMPTY,hu*r.outPerHeat(),false);
        return new Exchange(hu,inputAmount,outputAmount);
    }
    private void tickExchanger() {
        var c = GeneratorConfig.INSTANCE; boolean enabled = control(4,true);
        consumptionRate = processRate = 0;
        if (enabled && heat > c.exchangerStartHeat) {
            Exchange transfer = exchange(buffers[1],buffers[2],"exchanger_cooling",Math.min(heat-c.exchangerStartHeat,exchangers*c.exchangerTransfer));
            heat -= transfer.heat(); processRate = transfer.output();
            if (transfer.output() > 0) {
                if (lastExchangerOutput == null || !lastExchangerOutput.equals(buffers[2].id) || lastExchangerOutputItem != buffers[2].item) processAverage.clear();
                lastExchangerOutput = buffers[2].id; lastExchangerOutputItem = buffers[2].item;
            }
        }
        double beforeLoss=heat;
        heat *= 1-c.exchangerDecay;
        heatLossRate=beforeLoss-heat;
        if (enabled && heat < c.exchangerMaxHeat) {
            ResourceLocation inputId = buffers[0].id;
            boolean inputItem = buffers[0].item;
            Exchange transfer = exchange(buffers[0],buffers[3],"exchanger_heating",Math.min(c.exchangerMaxHeat-heat,exchangers*c.exchangerTransfer));
            heat += transfer.heat(); consumptionRate = transfer.input();
            if (transfer.input() > 0) {
                if (lastExchangerInput == null || !lastExchangerInput.equals(inputId) || lastExchangerInputItem != inputItem) consumptionAverage.clear();
                lastExchangerInput = inputId; lastExchangerInputItem = inputItem;
            }
        }
    }
    /** Actions: disabled, fuel, steam, generation, exchange, heating, mixing. Enable wins over disable. */
    public boolean control(int action, boolean fallback) {
        boolean disabled = false;
        for (var m : members()) if (m.kind() == MachineKind.CONTROL && m.controlAction == action) {
            if (level.hasNeighborSignal(m.worldPosition) == m.controlHigh) return true;
            disabled = true;
        }
        return disabled ? false : fallback;
    }
    public boolean outputEnabled(Direction side) {
        if (!kind().output() || level==null) return false;
        var config=outputConfig(this, side); if(config==null)return false;
        int mode = config.mode; boolean signal = level.hasNeighborSignal(worldPosition);
        return mode == 0 || mode == 1 && signal || mode == 2 && !signal;
    }
    public void refreshOutputFaces() {
        if (level == null || level.isClientSide || !kind().controller()) return;
        var members = members();
        var liveModules = new HashSet<Long>();
        var memberPositions = new HashSet<BlockPos>();
        for (var module : members) memberPositions.add(module.getBlockPos());
        for (var module : members) {
            if (!module.kind().output()) continue;
            liveModules.add(module.getBlockPos().asLong());
            for (Direction direction : Direction.values()) {
                BlockPos target = module.getBlockPos().relative(direction);
                if (!level.hasChunkAt(target)) continue;
                OutputFace face = new OutputFace(module.getBlockPos(), direction);
                boolean available = !memberPositions.contains(target) && !target.equals(worldPosition)
                    && TransferPlatform.canOutputOn(module, direction);
                if (available) {
                    outputConfigs.computeIfAbsent(face, key -> {
                        int side = direction.get3DDataValue();
                        return new OutputConfig(outputMode[side], outputSelection[side], outputLimit[side]);
                    });
                } else outputConfigs.remove(face);
            }
        }
        outputConfigs.keySet().removeIf(face -> level.hasChunkAt(face.blockPos()) && !liveModules.contains(face.position()));
        while (outputConfigs.size() > 6) outputConfigs.remove(outputConfigs.keySet().stream().reduce((a,b) -> b).orElseThrow());
    }
    private OutputConfig outputConfig(MachineBlockEntity module, Direction direction) {
        var core = module.kind().controller() ? module : module.controller();
        return core == null ? null : core.outputConfigs.get(new OutputFace(module.getBlockPos(), direction));
    }
    public List<OutputFaceEntry> outputEntries() {
        var result = new ArrayList<OutputFaceEntry>();
        for (var entry : outputConfigs.entrySet()) {
            BlockPos pos = entry.getKey().blockPos();
            if (level != null && level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof MachineBlockEntity module)
                result.add(new OutputFaceEntry(module.kind(), entry.getKey().direction(), entry.getValue()));
        }
        return result;
    }
    public int outputIndex(MachineBlockEntity module, Direction direction) {
        var core = module.kind().controller() ? module : module.controller();
        if (core == null) return -1;
        int index = 0;
        for (var face : core.outputConfigs.keySet()) {
            if (face.equals(new OutputFace(module.getBlockPos(), direction))) return index;
            index++;
        }
        return -1;
    }
    public long outputLimit(MachineBlockEntity module, Direction direction) {
        var config = outputConfig(module, direction); return config == null ? 0 : config.limit;
    }
    public int outputSelection(MachineBlockEntity module, Direction direction) {
        var config = outputConfig(module, direction); return config == null ? 0 : config.selection;
    }
    public void setOutputMode(int row, int value) { outputEntry(row).ifPresent(entry -> entry.config().mode = value); }
    public void cycleOutputSelection(int row) { outputEntry(row).ifPresent(entry -> entry.config().selection = (entry.config().selection + 1) % 3); }
    public void cycleOutputLimit(int row) {
        long[] limits = {8000, 32000, 128000, Integer.MAX_VALUE};
        outputEntry(row).ifPresent(entry -> {
            int index = outputLimitIndex(entry.config().limit);
            entry.config().limit = limits[(index + 1) % limits.length];
        });
    }
    private Optional<OutputFaceEntry> outputEntry(int row) {
        var entries = outputEntries(); return row < 0 || row >= entries.size() ? Optional.empty() : Optional.of(entries.get(row));
    }
    private static int outputLimitIndex(long limit) {
        if (limit <= 8000) return 0; if (limit <= 32000) return 1; if (limit <= 128000) return 2; return 3;
    }
    public int outputLimitIndex(int row) { return outputEntry(row).map(entry -> outputLimitIndex(entry.config().limit)).orElse(3); }
    public void recordOutput(MachineBlockEntity module, Direction side, double amount) {
        var config = outputConfig(module, side);
        if (config != null && amount > 0) { config.sample(amount); setChanged(); }
    }
    public int[] inputSlots() { return kind() == MachineKind.SYNGAS_CONTROLLER || kind() == MachineKind.EXCHANGER_CONTROLLER ? new int[]{0,1} : new int[]{0}; }
    public int[] outputSlots(int selection) {
        if (kind() == MachineKind.SYNGAS_CONTROLLER) return new int[]{2};
        // Exchanger buffers: 2 = hot output, 3 = cold output. The GUI's
        // selection order is Both, Cold, Hot.
        if (kind() == MachineKind.EXCHANGER_CONTROLLER) return selection == 1 ? new int[]{3} : selection == 2 ? new int[]{2} : new int[]{2,3};
        return new int[0];
    }
    private boolean steam(ResourceLocation id) {
        var fluid = BuiltInRegistries.FLUID.get(id);
        return id.equals(net.bdew.generators.Generators.id("steam")) || fluid.is(TagKey.create(Registries.FLUID, ResourceLocation.fromNamespaceAndPath("c","steam")));
    }
    public boolean accepts(int slot, boolean item, ResourceLocation id, DataComponentPatch patch) {
        if (item && kind() != MachineKind.EXCHANGER_CONTROLLER) return false;
        return switch (kind()) {
            case FUEL_TURBINE_CONTROLLER -> !item && slot == 0 && recipes("liquid_fuel").stream().anyMatch(r -> r.value().matchesFluid(BuiltInRegistries.FLUID.get(id)));
            case STEAM_TURBINE_CONTROLLER -> !item && slot == 0 && steam(id);
            case SYNGAS_CONTROLLER -> !item && (slot == 0 && BuiltInRegistries.FLUID.get(id) == Fluids.WATER || slot == 1 && steam(id));
            case EXCHANGER_CONTROLLER -> slot < 2 && exchangerRecipe(slot == 0 ? "exchanger_heating" : "exchanger_cooling",item,id,patch) != null;
            default -> false;
        };
    }
    public double insertResource(int slot, boolean item, ResourceLocation id, DataComponentPatch patch, double amount, boolean simulate) {
        if (!accepts(slot,item,id,patch)) return 0;
        if (!item && (kind() == MachineKind.STEAM_TURBINE_CONTROLLER || kind() == MachineKind.SYNGAS_CONTROLLER && slot == 1)) id = net.bdew.generators.Generators.id("steam");
        double added = buffers[slot].insert(item,id,patch,amount,simulate); if (!simulate && added > 0) setChanged(); return added;
    }
    public long extractEnergy(long amount, boolean simulate) {
        long moved = Math.max(0,Math.min(amount,energy)); if (!simulate && moved > 0) { energy -= moved; setChanged(); } return moved;
    }
    public double metric(int metric) {
        return switch(metric) {
            case 1 -> energyCapacity == 0 ? 0 : (double)energy/energyCapacity;
            case 2,3,4,5 -> buffers[metric-2].capacity == 0 ? 0 : buffers[metric-2].amount/buffers[metric-2].capacity;
            case 6 -> rpm/GeneratorConfig.INSTANCE.maxRpm;
            case 7 -> heat/(kind() == MachineKind.SYNGAS_CONTROLLER ? GeneratorConfig.INSTANCE.syngasMaxHeat : GeneratorConfig.INSTANCE.exchangerMaxHeat);
            case 8 -> carbon/GeneratorConfig.INSTANCE.syngasCapacity;
            case 9 -> maxOutput == 0 ? 0 : generationAverage.average()/maxOutput;
            default -> 0;
        };
    }
    private boolean sensor() {
        var c = controller();
        if (c == null || sensorMetric == 0) return false;
        int metric = c.sensorMetricFor(sensorMetric);
        if (metric == 0) return false;
        double value = c.metric(metric);

        // The legacy sensor offers named states for analog metrics (RPM, heat,
        // and generation), and fill thresholds for tanks/buffers.
        if (metric == 6 || metric == 7 || metric == 9) {
            double midpoint = switch (metric) {
                case 7 -> c.kind() == MachineKind.SYNGAS_CONTROLLER
                    ? GeneratorConfig.INSTANCE.syngasWorkHeat / GeneratorConfig.INSTANCE.syngasMaxHeat
                    : GeneratorConfig.INSTANCE.exchangerStartHeat / GeneratorConfig.INSTANCE.exchangerMaxHeat;
                default -> .5;
            };
            return switch (sensorCondition) {
                case 0 -> value <= 0;
                case 1 -> value > 0 && (metric != 7 || value < midpoint);
                case 2 -> metric == 9 ? value > midpoint : value >= midpoint;
                default -> value >= 1;
            };
        }
        if (kind() == MachineKind.EXCHANGER_CONTROLLER && metric >= 2 && metric <= 5) {
            ResourceBuffer buffer = buffers[metric - 2];
            return switch (sensorCondition) {
                case 0 -> buffer.empty();
                case 1 -> !buffer.empty() && buffer.item;
                case 2 -> !buffer.empty() && !buffer.item;
                case 3 -> value >= .05;
                case 4 -> value >= .25;
                case 5 -> value >= .5;
                case 6 -> value >= .75;
                case 7 -> value >= .95;
                case 8 -> value < 1;
                default -> value >= 1;
            };
        }
        return switch (sensorCondition) {
            case 0 -> value <= 0;
            case 1 -> value > 0;
            case 2 -> value >= .05;
            case 3 -> value >= .25;
            case 4 -> value >= .5;
            case 5 -> value >= .75;
            case 6 -> value >= .95;
            case 7 -> value < 1;
            default -> value >= 1;
        };
    }

    /** Sensor choices are controller-specific, matching the original machine screens. */
    public static int sensorMetricCount(MachineKind controller) {
        return switch (controller) {
            case FUEL_TURBINE_CONTROLLER, STEAM_TURBINE_CONTROLLER -> 3;
            case SYNGAS_CONTROLLER -> 5;
            case EXCHANGER_CONTROLLER -> 5;
            default -> 0;
        };
    }

    /** Returns the shared metric id selected by the controller-specific sensor list. */
    public int sensorMetricFor(int selection) {
        if (selection < 1 || selection > sensorMetricCount(kind())) return 0;
        int[] choices = switch (kind()) {
            case FUEL_TURBINE_CONTROLLER -> new int[]{1, 2, 9}; // power, fuel, generation
            case STEAM_TURBINE_CONTROLLER -> new int[]{1, 2, 6}; // power, steam, speed
            case SYNGAS_CONTROLLER -> new int[]{2, 4, 3, 8, 7}; // water, gas, steam, carbon, heat
            case EXCHANGER_CONTROLLER -> new int[]{2, 4, 3, 5, 7}; // hot in/out, cold in/out, heat
            default -> new int[0];
        };
        return choices[selection - 1];
    }

    public static int sensorConditionCount(MachineKind controller, int selection) {
        int[] choices = switch (controller) {
            case FUEL_TURBINE_CONTROLLER -> new int[]{1, 2, 9};
            case STEAM_TURBINE_CONTROLLER -> new int[]{1, 2, 6};
            case SYNGAS_CONTROLLER -> new int[]{2, 4, 3, 8, 7};
            case EXCHANGER_CONTROLLER -> new int[]{2, 4, 3, 5, 7};
            default -> new int[0];
        };
        if (selection < 1 || selection > choices.length) return 9;
        int metric = choices[selection - 1];
        if (metric == 6 || metric == 7 || metric == 9) return 4;
        return controller == MachineKind.EXCHANGER_CONTROLLER ? 10 : 9;
    }

    public static int[] controlActions(MachineKind controller) {
        return switch (controller) {
            case FUEL_TURBINE_CONTROLLER -> new int[]{0, 1};
            case STEAM_TURBINE_CONTROLLER -> new int[]{0, 2, 3};
            case SYNGAS_CONTROLLER -> new int[]{0, 5, 6};
            case EXCHANGER_CONTROLLER -> new int[]{0, 4};
            default -> new int[]{0};
        };
    }
    public void dropItems() {
        if (level == null || level.isClientSide) return;
        Containers.dropContents(level,worldPosition,inventory);
        for (var b : buffers) while (b.item && b.amount >= 1) {
            ItemStack stack = b.stack(); if (stack.isEmpty()) break;
            Containers.dropItemStack(level,worldPosition.getX()+.5,worldPosition.getY()+.5,worldPosition.getZ()+.5,stack); b.extract(stack.getCount(),false);
        }
    }
    @Override public Component getDisplayName() {
        String key = switch (kind()) {
            case FUEL_TURBINE_CONTROLLER -> "advgenerators.gui.turbine.title";
            case STEAM_TURBINE_CONTROLLER -> "advgenerators.gui.turbine.steam.title";
            case SYNGAS_CONTROLLER -> "advgenerators.gui.syngas.title";
            case EXCHANGER_CONTROLLER -> "advgenerators.gui.exchanger.title";
            case CONTROL -> "advgenerators.gui.control.title";
            case SENSOR -> "advgenerators.gui.sensor.title";
            default -> "block.advgenerators."+kind().id();
        };
        return Component.translatable(key);
    }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) { return new MachineMenu(id,inv,this); }
    public CompoundTag snapshot() { var tag = new CompoundTag(); saveAdditional(tag,level.registryAccess()); return tag; }
    public void restore(CompoundTag tag) { loadAdditional(tag,level.registryAccess()); }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag,registries);
        if (connectedCore != null) tag.putLong("connectedCore", connectedCore.asLong());
        tag.putLong("energy",energy); tag.putDouble("heat",heat); tag.putDouble("carbon",carbon); tag.putDouble("burnTime",burnTime); tag.putDouble("rpm",rpm); tag.putDouble("fraction",energyFraction);
        for(int i=0;i<4;i++) tag.put("buffer"+i,buffers[i].save(registries));
        tag.put("inventory",inventory.createTag(registries));
        tag.putInt("action",controlAction); tag.putBoolean("high",controlHigh); tag.putInt("metric",sensorMetric); tag.putInt("condition",sensorCondition);
        tag.putIntArray("outputMode",outputMode); tag.putIntArray("outputSelection",outputSelection); tag.putLongArray("outputLimit",outputLimit);
        ListTag faces = new ListTag();
        for (var entry : outputConfigs.entrySet()) {
            var face = new CompoundTag();
            face.putLong("position", entry.getKey().position()); face.putInt("direction", entry.getKey().direction().get3DDataValue());
            face.putInt("mode", entry.getValue().mode); face.putInt("selection", entry.getValue().selection);
            face.putLong("limit", entry.getValue().limit); face.putDouble("average", entry.getValue().average);
            faces.add(face);
        }
        tag.put("outputConfigs", faces);
        if (lastExchangerInput != null) tag.putString("lastExchangerInput",lastExchangerInput.toString());
        if (lastExchangerOutput != null) tag.putString("lastExchangerOutput",lastExchangerOutput.toString());
        tag.putBoolean("lastExchangerInputItem",lastExchangerInputItem); tag.putBoolean("lastExchangerOutputItem",lastExchangerOutputItem);
    }
    private static double finite(CompoundTag tag,String key) { double v = tag.getDouble(key); return Double.isFinite(v) ? Math.max(0,v) : 0; }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag,registries);
        connectedCore = tag.contains("connectedCore", Tag.TAG_LONG) ? BlockPos.of(tag.getLong("connectedCore")) : null;
        energy = Math.max(0,tag.getLong("energy")); heat=finite(tag,"heat"); carbon=finite(tag,"carbon"); burnTime=finite(tag,"burnTime"); rpm=finite(tag,"rpm"); energyFraction=Math.min(.999999,finite(tag,"fraction"));
        for(int i=0;i<4;i++) { buffers[i].capacity = Double.MAX_VALUE; buffers[i].load(tag.getCompound("buffer"+i),registries); }
        inventory.clearContent(); inventory.fromTag(tag.getList("inventory",Tag.TAG_COMPOUND),registries);
        controlAction=Math.clamp(tag.getInt("action"),0,6); controlHigh=!tag.contains("high") || tag.getBoolean("high");
        sensorMetric=Math.clamp(tag.getInt("metric"),0,9); sensorCondition=Math.clamp(tag.getInt("condition"),0,9);
        int[] modes=tag.getIntArray("outputMode"), selections=tag.getIntArray("outputSelection"); long[] limits=tag.getLongArray("outputLimit");
        lastExchangerInput=ResourceLocation.tryParse(tag.getString("lastExchangerInput"));
        lastExchangerOutput=ResourceLocation.tryParse(tag.getString("lastExchangerOutput"));
        lastExchangerInputItem=tag.getBoolean("lastExchangerInputItem"); lastExchangerOutputItem=tag.getBoolean("lastExchangerOutputItem");
        for(int i=0;i<6;i++) { outputMode[i]=i<modes.length?Math.clamp(modes[i],0,3):0; outputSelection[i]=i<selections.length?Math.clamp(selections[i],0,2):0; outputLimit[i]=i<limits.length?Math.clamp(limits[i],1,Integer.MAX_VALUE):Integer.MAX_VALUE; }
        outputConfigs.clear();
        for (Tag faceEntry : tag.getList("outputConfigs", Tag.TAG_COMPOUND)) {
            if (!(faceEntry instanceof CompoundTag faceTag)) continue;
            long position = faceTag.getLong("position");
            int direction = faceTag.getInt("direction");
            if (direction < 0 || direction >= Direction.values().length) continue;
            var face = new OutputFace(position, Direction.from3DDataValue(direction));
            var config = new OutputConfig(Math.clamp(faceTag.getInt("mode"),0,3), Math.clamp(faceTag.getInt("selection"),0,2), Math.clamp(faceTag.getLong("limit"),1,Integer.MAX_VALUE));
            config.average = finite(faceTag,"average");
            outputConfigs.put(face, config);
            if (outputConfigs.size() == 6) break;
        }
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { var tag=new CompoundTag();saveAdditional(tag,registries);return tag; }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
