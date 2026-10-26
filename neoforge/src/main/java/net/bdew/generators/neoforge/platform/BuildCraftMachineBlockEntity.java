package net.bdew.generators.neoforge.platform;

import buildcraft.api.v2.BuildCraftApi;
import buildcraft.api.v2.BuildCraftServices;
import buildcraft.api.v2.OperationMode;
import buildcraft.api.v2.energy.EnergyConversion;
import buildcraft.api.v2.energy.MjAmount;
import buildcraft.api.v2.energy.MjPort;
import buildcraft.api.v2.energy.MjPortDescriptor;
import buildcraft.api.v2.energy.MjPortProvider;
import buildcraft.api.v2.energy.MjPortRole;
import buildcraft.api.v2.energy.MjTransferResult;
import buildcraft.lib.internal.mj.IMjConnector;
import buildcraft.lib.internal.mj.IMjPassiveProvider;
import buildcraft.lib.internal.mj.IMjReadable;
import buildcraft.lib.internal.mj.MjCapabilities;
import buildcraft.lib.internal.mj.MjCapabilityHelper;
import net.bdew.generators.machine.MachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Native BuildCraft MJ endpoint used only when BCCE is installed. */
public final class BuildCraftMachineBlockEntity extends MachineBlockEntity implements MjPortProvider {
    private long outputTick = Long.MIN_VALUE;
    private long outputThisTick;
    private final Map<Direction, MjCapabilityHelper> mjCapabilities = new EnumMap<>(Direction.class);

    private BuildCraftMachineBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
        for (Direction side : Direction.values()) {
            mjCapabilities.put(side, new MjCapabilityHelper(new OutputConnector(side)));
        }
    }

    public static MachineBlockEntity create(BlockPos pos, BlockState state) {
        return new BuildCraftMachineBlockEntity(pos, state);
    }

    @Override
    public <T> T getCapability(BlockCapability<T, Direction> capability, Direction side) {
        if (kind().isMjOutput() && side != null) {
            T value = mjCapabilities.get(side).getCapability(capability, side);
            if (value != null) return value;
        }
        return super.getCapability(capability, side);
    }

    public static boolean canOutputOn(MachineBlockEntity machine, Direction side) {
        var level = machine.getLevel();
        if (level == null || side == null || !machine.kind().isMjOutput()) return false;
        BlockPos target = machine.getBlockPos().relative(side);
        if (!level.hasChunkAt(target)) return false;
        return BuildCraftApi.service(BuildCraftServices.ENERGY)
            .port(level, target, side.getOpposite())
            .map(MjPort::canInsert)
            .orElse(false);
    }

    @Override
    public Optional<MjPortDescriptor> mjPortDescriptor(Direction side) {
        if (!kind().isMjOutput() || side == null) return Optional.empty();
        var core = controller();
        EnergyConversion conversion = BuildCraftApi.service(BuildCraftServices.ENERGY).conversion();
        long rateFe = core == null ? 0 : Math.max(0, core.outputLimit(this, side));
        long rateMicroMj = conversion.feToMicroMj(rateFe);
        return Optional.of(new MjPortDescriptor(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("buildcraft", "mj"),
            Set.of(MjPortRole.PROVIDER, MjPortRole.CONNECTOR, MjPortRole.READABLE),
            MjAmount.ZERO,
            MjAmount.ofMicro(rateMicroMj)
        ));
    }

    @Override
    public Optional<MjPort> mjPort(Direction side) {
        if (!kind().isMjOutput() || side == null) return Optional.empty();
        return Optional.of(new MjPort() {
            @Override public MjTransferResult insert(MjAmount offered, OperationMode mode) {
                return MjTransferResult.none(offered);
            }

            @Override public MjTransferResult extract(MjAmount requested, OperationMode mode) {
                EnergyConversion conversion = BuildCraftApi.service(BuildCraftServices.ENERGY).conversion();
                long requestedFe = requested.microMj() / conversion.microMjPerFe();
                long extractedFe = extractFe(side, requestedFe, mode == OperationMode.SIMULATE);
                long transferred = conversion.feToMicroMj(extractedFe);
                return MjTransferResult.of(requested, MjAmount.ofMicro(transferred));
            }

            @Override public MjAmount stored() {
                var core = controller();
                return core == null ? MjAmount.ZERO : MjAmount.ofMicro(
                    BuildCraftApi.service(BuildCraftServices.ENERGY).conversion().feToMicroMj(Math.max(0, core.energy)));
            }

            @Override public MjAmount capacity() {
                var core = controller();
                return core == null ? MjAmount.ZERO : MjAmount.ofMicro(
                    BuildCraftApi.service(BuildCraftServices.ENERGY).conversion().feToMicroMj(Math.max(0, core.energyCapacity)));
            }

            @Override public boolean canInsert() { return false; }
            @Override public boolean canExtract() { return outputEnabled(side) && controller() != null; }
        });
    }

    /** Actively feeds normal Kinesis pipe networks; wooden pipes may also pull via CAP_PASSIVE_PROVIDER. */
    public void pushMjOutput() {
        if (level == null || level.isClientSide || !kind().isMjOutput()) return;
        var core = controller();
        if (core == null) return;

        beginOutputTick();
        EnergyConversion conversion = BuildCraftApi.service(BuildCraftServices.ENERGY).conversion();
        for (Direction side : Direction.values()) {
            if (!outputEnabled(side)) continue;
            long remaining = Math.max(0, core.outputLimit(this, side) - outputThisTick);
            long offerFe = Math.min(remaining, Math.max(0, core.energy));
            if (offerFe <= 0) continue;

            BlockPos target = worldPosition.relative(side);
            if (!level.hasChunkAt(target)) continue;
            MjPort destination = BuildCraftApi.service(BuildCraftServices.ENERGY)
                .port(level, target, side.getOpposite()).orElse(null);
            if (destination == null || !destination.canInsert()) continue;

            long offeredMicroMj = conversion.feToMicroMj(offerFe);
            MjTransferResult result = destination.insert(MjAmount.ofMicro(offeredMicroMj), OperationMode.EXECUTE);
            long acceptedFe = result.transferred().microMj() / conversion.microMjPerFe();
            if (acceptedFe > 0) extractFe(side, acceptedFe, false);
        }
    }

    private long extractFe(Direction side, long maximumFe, boolean simulate) {
        if (maximumFe <= 0 || !kind().isMjOutput() || !outputEnabled(side)) return 0;
        var core = controller();
        if (core == null) return 0;
        beginOutputTick();

        long rateLimitFe = Math.max(0, core.outputLimit(this, side));
        long availableFe = Math.min(Math.min(maximumFe, rateLimitFe - outputThisTick), Math.max(0, core.energy));
        if (availableFe <= 0) return 0;
        if (!simulate) {
            long extracted = core.extractEnergy(availableFe, false);
            outputThisTick += extracted;
            long transferred = BuildCraftApi.service(BuildCraftServices.ENERGY).conversion().feToMicroMj(extracted);
            if (transferred > 0) core.recordOutput(this, side,
                (double) transferred / MjAmount.MICRO_MJ_PER_MJ);
            return extracted;
        }
        return availableFe;
    }

    private void beginOutputTick() {
        long tick = level == null ? Long.MIN_VALUE : level.getGameTime();
        if (tick != outputTick) {
            outputTick = tick;
            outputThisTick = 0;
        }
    }

    private final class OutputConnector implements IMjPassiveProvider, IMjReadable {
        private final Direction side;
        private OutputConnector(Direction side) { this.side = side; }

        @Override public boolean canConnect(IMjConnector other) { return true; }

        @Override public long extractPower(long min, long max, boolean doExtract) {
            if (max <= 0) return 0;
            EnergyConversion conversion = BuildCraftApi.service(BuildCraftServices.ENERGY).conversion();
            long availableFe = extractFe(side, max / conversion.microMjPerFe(), true);
            long availableMicroMj = conversion.feToMicroMj(availableFe);
            if (availableMicroMj < min) return 0;
            if (!doExtract) return availableMicroMj;
            long extractedFe = extractFe(side, availableFe, false);
            return conversion.feToMicroMj(extractedFe);
        }

        @Override public long getStored() {
            var core = controller();
            return core == null ? 0 : BuildCraftApi.service(BuildCraftServices.ENERGY)
                .conversion().feToMicroMj(Math.max(0, core.energy));
        }

        @Override public long getCapacity() {
            var core = controller();
            return core == null ? 0 : BuildCraftApi.service(BuildCraftServices.ENERGY)
                .conversion().feToMicroMj(Math.max(0, core.energyCapacity));
        }
    }
}
