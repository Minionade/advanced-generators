package net.bdew.generators.machine;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

/** Fractional amounts are required for heat-exchanger item outputs (e.g. 0.001 obsidian/mB). */
public final class ResourceBuffer {
    public boolean item;
    public ResourceLocation id;
    public DataComponentPatch components = DataComponentPatch.EMPTY;
    public double amount;
    public double capacity;
    public ResourceBuffer(double capacity) { this.capacity = capacity; }
    public boolean empty() { return id == null || amount <= 1e-9; }
    public boolean same(boolean item, ResourceLocation id, DataComponentPatch components) {
        return !empty() && this.item == item && this.id.equals(id) && this.components.equals(components);
    }
    public double insert(boolean item, ResourceLocation id, DataComponentPatch components, double quantity, boolean simulate) {
        if (!Double.isFinite(quantity) || quantity <= 0 || id == null || (!empty() && !same(item, id, components))) return 0;
        double accepted = Math.max(0, Math.min(quantity, capacity - amount));
        if (!simulate && accepted > 0) { this.item = item; this.id = id; this.components = components; amount += accepted; }
        return accepted;
    }
    public double extract(double quantity, boolean simulate) {
        if (!Double.isFinite(quantity) || quantity <= 0) return 0;
        double result = Math.min(quantity, amount);
        if (!simulate) { amount -= result; if (amount < 1e-9) clear(); }
        return result;
    }
    public void clear() { id = null; amount = 0; components = DataComponentPatch.EMPTY; }
    public ItemStack stack() {
        if (empty() || !item) return ItemStack.EMPTY;
        var stack = new ItemStack(BuiltInRegistries.ITEM.get(id), (int) Math.min(64, Math.floor(amount + 1e-9)));
        stack.applyComponents(components); return stack;
    }
    public Fluid fluid() { return empty() || item ? net.minecraft.world.level.material.Fluids.EMPTY : BuiltInRegistries.FLUID.get(id); }
    public CompoundTag save(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        if (!empty()) {
            tag.putString("id", id.toString()); tag.putBoolean("item", item); tag.putDouble("amount", amount);
            DataComponentPatch.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), components).result().ifPresent(x -> tag.put("components", x));
        }
        return tag;
    }
    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        clear();
        ResourceLocation parsed = ResourceLocation.tryParse(tag.getString("id"));
        double n = tag.getDouble("amount"); boolean isItem = tag.getBoolean("item");
        if (parsed == null || !Double.isFinite(n) || n <= 0 || !(isItem ? BuiltInRegistries.ITEM.containsKey(parsed) : BuiltInRegistries.FLUID.containsKey(parsed))) return;
        id = parsed; item = isItem; amount = Math.min(n, capacity);
        if (tag.contains("components")) components = DataComponentPatch.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag.get("components")).result().orElse(DataComponentPatch.EMPTY);
    }
}
