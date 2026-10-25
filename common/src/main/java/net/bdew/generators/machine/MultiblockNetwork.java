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

        var queue = new ArrayDeque<BlockPos>(); var seen = new HashSet<BlockPos>(); var members = new ArrayList<BlockPos>();
        queue.add(controller); int error = 0;
        while (!queue.isEmpty()) {
            var pos = queue.removeFirst(); if (!seen.add(pos)) continue;
            // BdLib's safe block-entity lookup ignores unloaded neighbors; it does not
            // invalidate a structure just because an unrelated adjacent chunk is unloaded.
            if (!level.hasChunkAt(pos)) continue;
            if (!(level.getBlockEntity(pos) instanceof MachineBlockEntity machine)) continue;
            if (!pos.equals(controller)) {
                if (machine.kind().controller() || !controller.equals(machine.connectedCore())) continue;
                members.add(pos.immutable());
            }
            for (var direction : Direction.values()) {
                var neighbor = pos.relative(direction);
                if (!neighbor.equals(controller) && level.hasChunkAt(neighbor) && level.getBlockEntity(neighbor) instanceof MachineBlockEntity module
                    && !module.kind().controller() && controller.equals(module.connectedCore())) queue.add(neighbor);
            }
        }
        var network = new MultiblockNetwork(controller.immutable(), List.copyOf(members), error);
        core.cacheNetwork(network);
        for (var pos : members) if (level.getBlockEntity(pos) instanceof MachineBlockEntity machine) machine.cacheNetwork(network);
        return network;
    }
}
