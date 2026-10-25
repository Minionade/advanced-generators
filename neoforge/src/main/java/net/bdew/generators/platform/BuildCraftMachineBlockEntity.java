package net.bdew.generators.platform;

import buildcraft.api.v2.BuildCraftApi;
import buildcraft.api.v2.BuildCraftServices;
import buildcraft.api.v2.OperationMode;
import buildcraft.api.v2.energy.EnergyConversion;
import buildcraft.api.v2.energy.MjAmount;
import buildcraft.api.v2.energy.MjPort;
import buildcraft.api.v2.energy.MjPortProvider;
import buildcraft.api.v2.energy.MjTransferResult;
import net.bdew.generators.machine.MachineBlockEntity;
import net.bdew.generators.machine.MachineKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/** Native BuildCraft MJ endpoint used only when BCCE is installed. */
public final class BuildCraftMachineBlockEntity extends MachineBlockEntity implements MjPortProvider {
    private long outputTick = Long.MIN_VALUE;
    private long outputThisTick;

    private BuildCraftMachineBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
    }

    public static MachineBlockEntity create(BlockPos pos, BlockState state) {
        return new BuildCraftMachineBlockEntity(pos, state);
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
    public Optional<MjPort> mjPort(Direction side) {
        if (!kind().isMjOutput() || side == null || !outputEnabled(side)) return Optional.empty();
        return Optional.of(new MjPort() {
            @Override public MjTransferResult insert(MjAmount offered, OperationMode mode) {
                return MjTransferResult.none(offered);
            }

            @Override public MjTransferResult extract(MjAmount requested, OperationMode mode) {
                if (requested.isZero() || !outputEnabled(side)) return MjTransferResult.none(requested);
                var core = controller();
                if (core == null) return MjTransferResult.none(requested);

                EnergyConversion conversion = BuildCraftApi.service(BuildCraftServices.ENERGY).conversion();
                long rateLimitFe = Math.max(0, core.outputLimit(BuildCraftMachineBlockEntity.this, side));
                long requestedFe = requested.microMj() / conversion.microMjPerFe();
                long availableFe = Math.min(Math.min(requestedFe, rateLimitFe), Math.max(0, core.energy));
                if (mode == OperationMode.SIMULATE) {
                    return MjTransferResult.of(requested, MjAmount.ofMicro(conversion.feToMicroMj(availableFe)));
                }

                long tick = level == null ? Long.MIN_VALUE : level.getGameTime();
                if (tick != outputTick) {
                    outputTick = tick;
                    outputThisTick = 0;
                }
                availableFe = Math.min(availableFe, Math.max(0, rateLimitFe - outputThisTick));
                long extracted = core.extractEnergy(availableFe, false);
                outputThisTick += extracted;
                long transferred = conversion.feToMicroMj(extracted);
                if (transferred > 0) core.recordOutput(BuildCraftMachineBlockEntity.this, side,
                    (double) transferred / MjAmount.MICRO_MJ_PER_MJ);
                return MjTransferResult.of(requested, MjAmount.ofMicro(transferred));
            }

            @Override public MjAmount stored() {
                var core = controller();
                return core == null ? MjAmount.ZERO : MjAmount.ofMicro(
                    BuildCraftApi.service(BuildCraftServices.ENERGY).conversion().feToMicroMjClamped(Math.max(0, core.energy)));
            }

            @Override public MjAmount capacity() {
                var core = controller();
                return core == null ? MjAmount.ZERO : MjAmount.ofMicro(
                    BuildCraftApi.service(BuildCraftServices.ENERGY).conversion().feToMicroMjClamped(Math.max(0, core.energyCapacity)));
            }

            @Override public boolean canInsert() { return false; }
            @Override public boolean canExtract() { return outputEnabled(side) && controller() != null; }
        });
    }
}
