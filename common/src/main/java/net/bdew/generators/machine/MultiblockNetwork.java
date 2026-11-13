package net.bdew.generators.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import java.util.*;

/** Mirrors BdLib's module links: only modules explicitly connected to this controller belong to it. */
public record MultiblockNetwork(BlockPos controller, List<BlockPos> members, int error) {
    private static final Map<Level, Long> REVISIONS = new WeakHashMap<>();
    public static long revision(Level level) { return REVISIONS.getOrDefault(level, 0L); }
    public static void invalidate(Level level) { REVISIONS.put(level, revision(level) + 1); }

    /** Find eligible controllers adjacent to the module or to one of its linked neighbors. */
    public static Set<BlockPos> findConnections(Level level, BlockPos start, MachineKind moduleKind) {
        var result = new LinkedHashSet<BlockPos>();
        for (var direction : Direction.values()) {
            var adjacentPos = start.relative(direction);
            if (!level.hasChunkAt(adjacentPos) || !(level.getBlockEntity(adjacentPos) instanceof MachineBlockEntity adjacent)) continue;
            BlockPos corePos = adjacent.kind().controller() ? adjacentPos : adjacent.connectedCore();
            if (corePos == null || !level.hasChunkAt(corePos) || !(level.getBlockEntity(corePos) instanceof MachineBlockEntity core)) continue;
            if (!adjacent.kind().controller() && !core.network().members().contains(adjacentPos)) continue;
            if (core.kind().controller() && core.canAcceptModule(moduleKind)) result.add(corePos.immutable());
        }
        return result;
    }

    public static MultiblockNetwork discover(MachineBlockEntity origin) {
        Level level = origin.getLevel();
        if (level == null) return new MultiblockNetwork(null, List.of(), 1);
        BlockPos controller = origin.kind().controller() ? origin.getBlockPos() : origin.connectedCore();
        if (controller == null || !level.hasChunkAt(controller) || !(level.getBlockEntity(controller) instanceof MachineBlockEntity core) || !core.kind().controller())
            return new MultiblockNetwork(null, List.of(), 1);

        var inventory = core.moduleInventory();
        boolean changed = false;
        // Only a loaded position can prove a saved module was removed or replaced.
        // Retain disconnected, unloaded positions as well: an upgrade may temporarily
        // replace the bridge to them before reconnecting the new block.
        for (var pos : inventory.positions()) {
            if (!level.hasChunkAt(pos)) continue;
            if (level.getBlockEntity(pos) instanceof MachineBlockEntity module && !module.isRemoved()
                && !module.kind().controller() && controller.equals(module.connectedCore()))
                changed |= inventory.remember(pos, module.kind());
            else changed |= inventory.forget(pos);
        }

        var queue = new ArrayDeque<BlockPos>(); var seen = new HashSet<BlockPos>(); var members = new ArrayList<BlockPos>();
        queue.add(controller);
        boolean allBoundariesLoaded = true;
        while (!queue.isEmpty()) {
            var pos = queue.removeFirst(); if (!seen.add(pos)) continue;
            if (!pos.equals(controller)) {
                if (!level.hasChunkAt(pos)) {
                    if (!inventory.contains(pos)) { allBoundariesLoaded = false; continue; }
                } else {
                    if (!(level.getBlockEntity(pos) instanceof MachineBlockEntity machine) || machine.isRemoved()
                        || machine.kind().controller() || !controller.equals(machine.connectedCore())) continue;
                    changed |= inventory.remember(pos, machine.kind());
                }
                members.add(pos.immutable());
            }
            for (var direction : Direction.values()) queue.add(pos.relative(direction));
        }
        changed |= inventory.finishDiscovery(allBoundariesLoaded);
        if (changed && !level.isClientSide) core.setChanged();
        var network = new MultiblockNetwork(controller.immutable(), List.copyOf(members), 0);
        core.cacheNetwork(network);
        for (var pos : members) if (level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof MachineBlockEntity machine) machine.cacheNetwork(network);
        return network;
    }
}
