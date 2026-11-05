package net.bdew.generators.neoforge.create;

import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.bdew.generators.config.GeneratorConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;

import java.util.List;

/**
 * Burns FE every tick to turn. While there is FE it provides rotation and stress capacity. When the FE runs out it
 * keeps the rotation but offers no stress capacity, so the kinetic network it belongs to is not split apart.
 */
public class KineticConverterBlockEntity extends GeneratingKineticBlockEntity {
    private final EnergyStorage buffer;
    private final IEnergyStorage inputOnly;
    private boolean powered, started;

    public KineticConverterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        int capacity = Math.max(GeneratorConfig.INSTANCE.createBuffer, GeneratorConfig.INSTANCE.createFePerTick);
        buffer = new EnergyStorage(capacity, capacity, capacity);
        inputOnly = new IEnergyStorage() {
            @Override public int receiveEnergy(int amount, boolean simulate) {
                int accepted = buffer.receiveEnergy(amount, simulate);
                if (accepted > 0 && !simulate) setChanged();
                return accepted;
            }
            @Override public int extractEnergy(int amount, boolean simulate) { return 0; }
            @Override public int getEnergyStored() { return buffer.getEnergyStored(); }
            @Override public int getMaxEnergyStored() { return buffer.getMaxEnergyStored(); }
            @Override public boolean canExtract() { return false; }
            @Override public boolean canReceive() { return true; }
        };
    }

    public IEnergyStorage energyHandler() { return inputOnly; }

    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    @Override public float getGeneratedSpeed() {
        return powered || started ? GeneratorConfig.INSTANCE.createSpeedRpm : 0;
    }

    /** Stress capacity is per RPM in Create, so the configured total is spread over the speed. */
    @Override public float calculateAddedStressCapacity() {
        var config = GeneratorConfig.INSTANCE;
        lastCapacityProvided = powered ? (float) config.createStressCapacity / Math.max(1, config.createSpeedRpm) : 0;
        return lastCapacityProvided;
    }

    @Override public void initialize() {
        super.initialize();
        if (level != null && !level.isClientSide) {
            powered = buffer.getEnergyStored() >= GeneratorConfig.INSTANCE.createFePerTick;
            started = started || powered;
            updateGeneratedRotation();
        }
    }

    @Override public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;
        int cost = GeneratorConfig.INSTANCE.createFePerTick;
        boolean nowPowered = buffer.getEnergyStored() >= cost;
        if (nowPowered) buffer.extractEnergy(cost, false);
        boolean nowStarted = started || nowPowered;
        if (nowPowered != powered || nowStarted != started) {
            boolean spinChanged = nowStarted != started;
            powered = nowPowered;
            started = nowStarted;
            if (spinChanged) updateGeneratedRotation();
            if (hasNetwork()) notifyStressCapacityChange(calculateAddedStressCapacity());
            setChanged();
        }
    }

    @Override protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putBoolean("ag_powered", powered);
        tag.putBoolean("ag_started", started);
        tag.putInt("ag_energy", buffer.getEnergyStored());
    }

    @Override protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        powered = tag.getBoolean("ag_powered");
        started = tag.getBoolean("ag_started");
        int stored = tag.getInt("ag_energy") - buffer.getEnergyStored();
        if (stored > 0) buffer.receiveEnergy(stored, false);
    }
}
