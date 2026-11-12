package net.bdew.generators.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.*;

/** Saved controller membership. An unloaded module is still part of the machine. */
final class ModuleInventory {
    // A null kind is a position imported from BDLib, whose saves did not include module types.
    private final Map<BlockPos, MachineKind> modules = new LinkedHashMap<>();
    private boolean complete = true;

    Set<BlockPos> positions() { return Set.copyOf(modules.keySet()); }
    boolean contains(BlockPos pos) { return modules.containsKey(pos); }
    boolean remember(BlockPos pos, MachineKind kind) {
        if (modules.containsKey(pos) && modules.get(pos) == kind) return false;
        modules.put(pos.immutable(), kind);
        return true;
    }
    boolean forget(BlockPos pos) {
        if (!modules.containsKey(pos)) return false;
        modules.remove(pos);
        return true;
    }
    boolean finishDiscovery(boolean allBoundariesLoaded) {
        if (complete || !allBoundariesLoaded) return false;
        complete = true;
        return true;
    }
    boolean knowsAll(Collection<BlockPos> reachable) {
        return complete && reachable.stream().allMatch(pos -> modules.get(pos) != null);
    }
    List<MachineKind> kinds(Collection<BlockPos> reachable) {
        return reachable.stream().map(modules::get).filter(Objects::nonNull).toList();
    }

    void save(CompoundTag tag) {
        var list = new ListTag();
        modules.forEach((pos, kind) -> {
            var entry = new CompoundTag();
            entry.putInt("x", pos.getX()); entry.putInt("y", pos.getY()); entry.putInt("z", pos.getZ());
            if (kind != null) entry.putString("kind", kind.id());
            list.add(entry);
        });
        tag.put("modules", list);
        tag.putBoolean("moduleInventoryIncomplete", !complete);
    }

    void load(CompoundTag tag) {
        modules.clear();
        // Early Java saves omitted membership entirely. Keep their contents until discovery
        // has seen the whole boundary; an unrelated unloaded chunk never invalidates a machine.
        complete = tag.contains("modules", Tag.TAG_LIST) && !tag.getBoolean("moduleInventoryIncomplete");
        for (var element : tag.getList("modules", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) element;
            if (!entry.contains("x", Tag.TAG_INT) || !entry.contains("y", Tag.TAG_INT) || !entry.contains("z", Tag.TAG_INT)) continue;
            var kind = Arrays.stream(MachineKind.values())
                .filter(k -> !k.controller() && k.id().equals(entry.getString("kind"))).findFirst().orElse(null);
            remember(new BlockPos(entry.getInt("x"), entry.getInt("y"), entry.getInt("z")), kind);
        }
    }
}
