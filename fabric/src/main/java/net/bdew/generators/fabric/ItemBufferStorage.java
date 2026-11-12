package net.bdew.generators.fabric;

import net.bdew.generators.machine.MachineBlockEntity;
import net.bdew.generators.machine.ResourceBuffer;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Direction;

/** A live view: cached pipes must never commit a stale copy of machine contents. */
final class ItemBufferStorage implements SingleSlotStorage<ItemVariant> {
    private final MachineBlockEntity machine, module;
    private final Direction side;
    private final int slot;
    private final boolean insert, extract;
    private final MachineTransaction state;
    ItemBufferStorage(MachineBlockEntity machine, MachineBlockEntity module, Direction side, int slot, boolean insert, boolean extract) {
        this.machine = machine; this.module = module; this.side = side; this.slot = slot; this.insert = insert; this.extract = extract;
        state = MachineTransaction.of(machine);
    }
    private boolean accessible() { return machine.canAccessBuffer(module, side, slot, extract); }
    private ResourceBuffer buffer() { return machine.buffers[slot]; }
    @Override public ItemVariant getResource() {
        var buffer = buffer();
        return !accessible() || buffer.empty() || !buffer.item ? ItemVariant.blank() : ItemVariant.of(BuiltInRegistries.ITEM.get(buffer.id), buffer.components);
    }
    @Override public boolean isResourceBlank() { return getResource().isBlank(); }
    @Override public long getAmount() { return isResourceBlank() ? 0 : (long) Math.floor(buffer().amount); }
    @Override public long getCapacity() { return (long) Math.floor(buffer().capacityFor(true)); }
    @Override public boolean supportsInsertion() { return insert && accessible(); }
    @Override public boolean supportsExtraction() { return extract && accessible(); }
    @Override public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
        if (maxAmount < 0) throw new IllegalArgumentException("Negative transfer amount");
        if (!insert || !accessible() || resource.isBlank() || maxAmount == 0) return 0;
        var id = BuiltInRegistries.ITEM.getKey(resource.getItem());
        double accepted = machine.insertResource(slot, true, id, resource.getComponents(), maxAmount, true);
        long moved = Math.min(maxAmount, (long) Math.floor(accepted));
        if (moved > 0) {
            state.beforeChange(transaction);
            machine.insertResource(slot, true, id, resource.getComponents(), moved, false);
        }
        return moved;
    }
    @Override public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
        if (maxAmount < 0) throw new IllegalArgumentException("Negative transfer amount");
        if (!extract || !accessible() || resource.isBlank() || !resource.equals(getResource())) return 0;
        long moved = Math.min(maxAmount, getAmount());
        if (moved > 0) {
            state.beforeChange(transaction);
            buffer().extract(moved, false);
        }
        return moved;
    }
}
