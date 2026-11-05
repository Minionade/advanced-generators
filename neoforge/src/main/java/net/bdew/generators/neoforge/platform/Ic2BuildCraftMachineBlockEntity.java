package net.bdew.generators.neoforge.platform;

import ic2.api.energy.tile.IEnergyAcceptor;
import ic2.api.energy.tile.IEnergySource;
import net.bdew.generators.machine.MachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/** The machine entity when both BuildCraft and IC2: Nostalgia are installed. */
public final class Ic2BuildCraftMachineBlockEntity extends BuildCraftMachineBlockEntity implements IEnergySource {
    private Ic2BuildCraftMachineBlockEntity(BlockPos pos, BlockState state) { super(pos, state); }

    public static MachineBlockEntity create(BlockPos pos, BlockState state) { return new Ic2BuildCraftMachineBlockEntity(pos, state); }

    @Override public void onLoad() { super.onLoad(); Ic2Source.join(this, this); }
    @Override public void setRemoved() { Ic2Source.leave(this, this); super.setRemoved(); }
    @Override public void onChunkUnloaded() { Ic2Source.leave(this, this); super.onChunkUnloaded(); }
    @Override public void setBlockState(BlockState state) {
        boolean turned = getLevel() != null && kind().isEuOutput() && state != getBlockState();
        if (turned) Ic2Source.leave(this, this);
        super.setBlockState(state);
        if (turned) Ic2Source.join(this, this);
    }

    @Override public double getOfferedEnergy() { return Ic2Source.offered(this); }
    @Override public void drawEnergy(double amount) { Ic2Source.draw(this, amount); }
    @Override public int getSourceTier() { return Ic2Source.tier(this); }
    @Override public boolean emitsEnergyTo(IEnergyAcceptor acceptor, Direction side) { return Ic2Source.emitsTo(this, side); }
}
