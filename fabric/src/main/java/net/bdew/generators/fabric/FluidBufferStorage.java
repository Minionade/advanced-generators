package net.bdew.generators.fabric;

import net.bdew.generators.machine.MachineBlockEntity;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.base.SingleFluidStorage;
import net.minecraft.core.registries.BuiltInRegistries;

final class FluidBufferStorage extends SingleFluidStorage {
    private final MachineBlockEntity machine;
    private final int slot;
    private final boolean insert;
    private final boolean extract;
    FluidBufferStorage(MachineBlockEntity machine,int slot,boolean insert,boolean extract) {
        this.machine=machine; this.slot=slot; this.insert=insert; this.extract=extract;
        var buffer=machine.buffers[slot];
        if(!buffer.empty()&&!buffer.item){variant=FluidVariant.of(buffer.fluid()); amount=(long)Math.floor(buffer.amount*81+1e-9);}
    }
    @Override protected long getCapacity(FluidVariant v) { return Math.max(0,(long)Math.floor(machine.buffers[slot].capacity*81)); }
    @Override protected boolean canInsert(FluidVariant v) { return insert && machine.accepts(slot,false,BuiltInRegistries.FLUID.getKey(v.getFluid()),v.getComponents()); }
    @Override protected boolean canExtract(FluidVariant v) { return extract; }
    @Override protected void onFinalCommit() {
        var buffer=machine.buffers[slot];
        if(amount<=0) buffer.clear();
        else { buffer.item=false; buffer.id=BuiltInRegistries.FLUID.getKey(variant.getFluid()); buffer.components=variant.getComponents(); buffer.amount=amount/81.0; }
        machine.setChanged();
    }
}
