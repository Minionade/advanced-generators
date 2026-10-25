package net.bdew.generators.fabric;

import net.bdew.generators.machine.MachineBlockEntity;
import net.minecraft.core.Direction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import team.reborn.energy.api.EnergyStorage;

final class EnergyBufferStorage extends SnapshotParticipant<Long> implements EnergyStorage {
    private final MachineBlockEntity controller; private final Direction side; private long value;
    EnergyBufferStorage(MachineBlockEntity controller,Direction side){this.controller=controller;this.side=side;value=controller.energy;}
    @Override public long insert(long max,TransactionContext tx){return 0;}
    @Override public long extract(long max,TransactionContext tx){
        if(max<=0||!controller.outputEnabled(side))return 0;
        updateSnapshots(tx);
        long limit=Math.max(0,controller.outputLimit[side.get3DDataValue()]);
        long moved=Math.min(Math.min(max,limit),Math.max(0,Math.min(controller.energy,value)));
        value-=moved;
        return moved;
    }
    @Override public long getAmount(){return value;}
    @Override public long getCapacity(){return controller.energyCapacity;}
    @Override public boolean supportsExtraction(){return controller.outputEnabled(side);}
    @Override public boolean supportsInsertion(){return false;}
    @Override protected Long createSnapshot(){return value;}
    @Override protected void readSnapshot(Long old){value=old;}
    @Override protected void onFinalCommit(){controller.energy=value;controller.setChanged();}
}
