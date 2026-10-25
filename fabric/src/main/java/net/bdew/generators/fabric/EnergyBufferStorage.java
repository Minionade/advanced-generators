package net.bdew.generators.fabric;

import net.bdew.generators.machine.MachineBlockEntity;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import team.reborn.energy.api.EnergyStorage;

final class EnergyBufferStorage extends SnapshotParticipant<Long> implements EnergyStorage {
    private final MachineBlockEntity controller; private long value;
    EnergyBufferStorage(MachineBlockEntity controller){this.controller=controller;value=controller.energy;}
    @Override public long insert(long max,TransactionContext tx){if(max<=0)return 0;updateSnapshots(tx);long moved=Math.min(max,Math.max(0,controller.energyCapacity-value));value+=moved;return moved;}
    @Override public long extract(long max,TransactionContext tx){return 0;}
    @Override public long getAmount(){return value;}
    @Override public long getCapacity(){return controller.energyCapacity;}
    @Override public boolean supportsExtraction(){return false;}
    @Override protected Long createSnapshot(){return value;}
    @Override protected void readSnapshot(Long old){value=old;}
    @Override protected void onFinalCommit(){controller.energy=value;controller.setChanged();}
}
