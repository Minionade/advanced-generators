package net.bdew.generators.machine;

import net.bdew.generators.config.GeneratorConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import java.util.*;

/** Loaded-chunk-only flood fill. Ambiguous/oversized networks fail closed, never load chunks. */
public record MultiblockNetwork(BlockPos controller, List<BlockPos> members, int error) {
    private static final Map<Level, Long> REVISIONS = new WeakHashMap<>();
    public static long revision(Level level) { return REVISIONS.getOrDefault(level, 0L); }
    public static void invalidate(Level level) { REVISIONS.put(level, revision(level) + 1); }
    public static MultiblockNetwork discover(MachineBlockEntity origin) {
        Level level = origin.getLevel(); var cfg = GeneratorConfig.INSTANCE;
        var queue = new ArrayDeque<BlockPos>(); var seen = new HashSet<BlockPos>(); var members = new ArrayList<BlockPos>();
        queue.add(origin.getBlockPos()); BlockPos controller = null; int error = 0, controllers = 0;
        var counts = new HashMap<String, Integer>(); MachineKind controllerKind = null;
        while (!queue.isEmpty()) {
            var pos = queue.removeFirst(); if (!seen.add(pos)) continue;
            if (!level.hasChunkAt(pos)) { if (error == 0) error = 3; continue; }
            if (!(level.getBlockEntity(pos) instanceof MachineBlockEntity machine)) continue;
            members.add(pos.immutable());
            if (members.size() > cfg.maxConnectedBlocks) { error = 4; break; }
            if (machine.kind().controller()) { controllers++; controller = pos; controllerKind = machine.kind(); }
            else counts.merge(machine.kind().category, 1, Integer::sum);
            for (var direction : Direction.values()) queue.add(pos.relative(direction));
        }
        if (error == 0) {
            if (controllers == 0) error = 1;
            else if (controllers != 1) error = 2;
            else {
                for (var entry : counts.entrySet()) {
                    int max = cfg.limit(controllerKind, entry.getKey());
                    if (max == 0) { error = 5; break; }
                    if (entry.getValue() > max) { error = 6; break; }
                }
                String required = switch (controllerKind) {
                    case FUEL_TURBINE_CONTROLLER, STEAM_TURBINE_CONTROLLER -> "turbine";
                    case SYNGAS_CONTROLLER -> "mixingChamber";
                    case EXCHANGER_CONTROLLER -> "heatExchanger";
                    default -> "";
                };
                if (error == 0 && counts.getOrDefault(required, 0) == 0) error = 7;
            }
        }
        var network = new MultiblockNetwork(controllers == 1 ? controller : null, List.copyOf(members), error);
        for (var pos : members) if (level.getBlockEntity(pos) instanceof MachineBlockEntity machine) machine.cacheNetwork(network);
        return network;
    }
}
