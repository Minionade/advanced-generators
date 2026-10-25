package net.bdew.generators.fabric;

import net.bdew.generators.machine.MachineBlockEntity;
import net.bdew.generators.machine.MachineKind;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.item.base.SingleItemStorage;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

final class ItemBufferStorage extends SingleItemStorage {
    private final MachineBlockEntity machine;
    private final int slot;
    private final boolean insert,extract;
    ItemBufferStorage(MachineBlockEntity machine,int slot,boolean insert,boolean extract) {
        this.machine=machine;this.slot=slot;this.insert=insert;this.extract=extract;
        var buffer=machine.buffers[slot];
        if(!buffer.empty()&&buffer.item){variant=ItemVariant.of(BuiltInRegistries.ITEM.get(buffer.id),buffer.components);amount=(long)Math.floor(buffer.amount);}
    }
    @Override protected long getCapacity(ItemVariant v) { return 64; }
    @Override protected boolean canInsert(ItemVariant v) {
        if(!insert)return false;
        if(machine.kind()==MachineKind.SYNGAS_CONTROLLER)return machine.carbonValue(v.toStack())>0;
        return machine.accepts(slot,true,BuiltInRegistries.ITEM.getKey(v.getItem()),v.getComponents());
    }
    @Override protected boolean canExtract(ItemVariant v) { return extract; }
    @Override protected void onFinalCommit() {
        var b=machine.buffers[slot];
        if(amount<=0)b.clear(); else {b.item=true;b.id=BuiltInRegistries.ITEM.getKey(variant.getItem());b.components=variant.getComponents();b.amount=amount;}
        machine.setChanged();
    }
}
