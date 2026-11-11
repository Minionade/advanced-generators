package net.bdew.generators.fabric;

import net.bdew.generators.machine.MachineBlockEntity;
import net.bdew.generators.machine.ResourceBuffer;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.core.registries.BuiltInRegistries;

/** A live view: cached pipes must never commit a stale copy of machine contents. */
final class ItemBufferStorage implements SingleSlotStorage<ItemVariant> {
    private final MachineBlockEntity machine;
    private final int slot;
    private final boolean insert, extract;
    private final MachineTransaction state;
    ItemBufferStorage(MachineBlockEntity machine, int slot, boolean insert, boolean extract) {
        this.machine = machine; this.slot = slot; this.insert = insert; this.extract = extract;
        state = MachineTransaction.of(machine);
    }
    private ResourceBuffer buffer() { return machine.buffers[slot]; }
    @Override public ItemVariant getResource() {
        var buffer = buffer();
        return buffer.empty() || buffer.item != true ? ItemVariant.blank() : ItemVariant.of(BuiltInRegistries.ITEM.get(buffer.id), buffer.components);
    }
    @Override public boolean isResourceBlank() { return getResource().isBlank(); }
    @Override public long getAmount() { return isResourceBlank() ? 0 : (long) Math.floor(buffer().amount * 1); }
    @Override public long getCapacity() { return (long) Math.floor(buffer().capacityFor(true) * 1); }
    @Override public boolean supportsInsertion() { return insert; }
    @Override public boolean supportsExtraction() { return extract; }
    @Override public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
        if (maxAmount < 0) throw new IllegalArgumentException("Negative transfer amount");
        if (!insert || resource.isBlank() || maxAmount == 0) return 0;
        var id = BuiltInRegistries.ITEM.getKey(resource.getItem());
        double accepted = machine.insertResource(slot, true, id, resource.getComponents(), maxAmount / 1.0, true);
        long moved = Math.min(maxAmount, (long) Math.floor(accepted * 1));
        if (moved > 0) {
            state.beforeChange(transaction);
            machine.insertResource(slot, true, id, resource.getComponents(), moved / 1.0, false);
        }
        return moved;
    }
    @Override public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
        if (maxAmount < 0) throw new IllegalArgumentException("Negative transfer amount");
        if (!extract || resource.isBlank() || !resource.equals(getResource())) return 0;
        long moved = Math.min(maxAmount, getAmount());
        if (moved > 0) {
            state.beforeChange(transaction);
            buffer().extract(moved / 1.0, false);
        }
        return moved;
    }
}
