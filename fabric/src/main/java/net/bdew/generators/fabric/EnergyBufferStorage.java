package net.bdew.generators.fabric;

import net.bdew.generators.machine.MachineBlockEntity;
import net.bdew.generators.machine.MachineKind;
import net.minecraft.core.Direction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import team.reborn.energy.api.EnergyStorage;

final class EnergyBufferStorage extends SnapshotParticipant<Long> implements EnergyStorage {
    private static final long FE_PER_EU=4;
    private final MachineBlockEntity controller, module; private final Direction side; private final MachineKind outputKind; private long value, pendingOutput;
    EnergyBufferStorage(MachineBlockEntity controller,MachineBlockEntity module,Direction side,MachineKind outputKind){this.controller=controller;this.module=module;this.side=side;this.outputKind=outputKind;value=controller.energy;}
    @Override public long insert(long max,TransactionContext tx){return 0;}
    @Override public long extract(long max,TransactionContext tx){
        if(max<=0||!module.outputEnabled(side))return 0;
        if(outputKind.isEuOutput()) {
            long limitEu=Math.min(outputKind.euOutputRate(),Math.max(0,controller.outputLimit(module,side))/FE_PER_EU);
            long movedEu=Math.min(Math.min(max,limitEu),Math.max(0,Math.min(controller.energy,value))/FE_PER_EU);
            if(movedEu<=0)return 0;
            updateSnapshots(tx);
            value-=movedEu*FE_PER_EU;
            pendingOutput+=movedEu;
            return movedEu;
        }
        updateSnapshots(tx);
        long limit=Math.max(0,controller.outputLimit(module,side));
        long moved=Math.min(Math.min(max,limit),Math.max(0,Math.min(controller.energy,value)));
        value-=moved;
        pendingOutput+=moved;
        return moved;
    }
    @Override public long getAmount(){return outputKind.isEuOutput()?value/FE_PER_EU:value;}
    @Override public long getCapacity(){return outputKind.isEuOutput()?controller.energyCapacity/FE_PER_EU:controller.energyCapacity;}
    @Override public boolean supportsExtraction(){return module.outputEnabled(side)&&(!outputKind.isEuOutput()||outputKind.euOutputRate()>0);}
    @Override public boolean supportsInsertion(){return false;}
    @Override protected Long createSnapshot(){return value;}
    @Override protected void readSnapshot(Long old){value=old;pendingOutput=0;}
    @Override protected void onFinalCommit(){controller.energy=value;controller.recordOutput(module,side,pendingOutput);pendingOutput=0;controller.setChanged();}
}
