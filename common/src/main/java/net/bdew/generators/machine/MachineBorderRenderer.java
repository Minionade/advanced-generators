package net.bdew.generators.machine;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.bdew.generators.Generators;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * BdLib-style connected frame renderer. It draws only those 1/16th edge
 * segments whose neighboring block is not part of the same linked machine.
 */
public final class MachineBorderRenderer implements BlockEntityRenderer<MachineBlockEntity> {
    private static final ResourceLocation EDGE = Generators.id("textures/block/connected/edge.png");
    private static final ResourceLocation OUTPUT = Generators.id("textures/block/connected/output.png");
    private static final ResourceLocation ARROW = Generators.id("textures/block/connected/arrow.png");
    private static final float EDGE_SIZE = 1.0f / 16.0f;
    private static final float EXPANSION = 1.001f;

    public MachineBorderRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(MachineBlockEntity machine, float partialTick, PoseStack stack, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        renderBorder(machine, stack, buffers, packedOverlay);
    }

    private static void renderBorder(MachineBlockEntity machine, PoseStack stack, MultiBufferSource buffers, int packedOverlay) {
        var level = machine.getLevel();
        if (level == null) return;
        var pos = machine.getBlockPos();
        var near = connections(machine);
        int[] outputs = near.outputs;
        // Ask for the buffer again for every quad: asking for another render type's buffer ends the previous one,
        // so a consumer kept across the badge and arrow quads below would throw "Not building!".
        for (Direction face : Direction.values()) {
            Direction top = top(face);
            Direction right = right(face);
            boolean u = !near.at(top);
            boolean d = !near.at(top.getOpposite());
            boolean l = !near.at(right.getOpposite());
            boolean r = !near.at(right);
            boolean tr = !u && !r && !near.at(top, right);
            boolean tl = !u && !l && !near.at(top, right.getOpposite());
            boolean br = !d && !r && !near.at(top.getOpposite(), right);
            boolean bl = !d && !l && !near.at(top.getOpposite(), right.getOpposite());
            boolean badge = outputs[face.ordinal()] >= 0;
            boolean arrows = outputs[top.ordinal()] >= 0 || outputs[right.ordinal()] >= 0
                || outputs[top.getOpposite().ordinal()] >= 0 || outputs[right.getOpposite().ordinal()] >= 0;
            // Most faces of a large machine sit between other parts and have nothing to draw: leave them alone
            // without looking anything up in the world.
            if (!(u || d || l || r || tr || tl || br || bl || badge || arrows)) continue;

            BlockPos outside = pos.relative(face);
            boolean blocked = level.getBlockState(outside).isSolidRender(level, outside);
            // The machine's own position is opaque and so has no light; light each face from the space it faces.
            int light = LevelRenderer.getLightColor(level, outside);

            if (!blocked) {
                if (u) drawRect(stack, buffers.getBuffer(RenderType.entityCutoutNoCullZOffset(EDGE)), face, 0, 1-EDGE_SIZE, 1, 1, 0, 1-EDGE_SIZE, 1, 1, light, packedOverlay);
                if (d) drawRect(stack, buffers.getBuffer(RenderType.entityCutoutNoCullZOffset(EDGE)), face, 0, 0, 1, EDGE_SIZE, 0, 0, 1, EDGE_SIZE, light, packedOverlay);
                if (l) drawRect(stack, buffers.getBuffer(RenderType.entityCutoutNoCullZOffset(EDGE)), face, 0, 0, EDGE_SIZE, 1, 0, 0, EDGE_SIZE, 1, light, packedOverlay);
                if (r) drawRect(stack, buffers.getBuffer(RenderType.entityCutoutNoCullZOffset(EDGE)), face, 1-EDGE_SIZE, 0, 1, 1, 1-EDGE_SIZE, 0, 1, 1, light, packedOverlay);

                if (tr) drawRect(stack, buffers.getBuffer(RenderType.entityCutoutNoCullZOffset(EDGE)), face, 1-EDGE_SIZE, 1-EDGE_SIZE, 1, 1, 1-EDGE_SIZE, 1-EDGE_SIZE, 1, 1, light, packedOverlay);
                if (tl) drawRect(stack, buffers.getBuffer(RenderType.entityCutoutNoCullZOffset(EDGE)), face, 0, 1-EDGE_SIZE, EDGE_SIZE, 1, 0, 1-EDGE_SIZE, EDGE_SIZE, 1, light, packedOverlay);
                if (br) drawRect(stack, buffers.getBuffer(RenderType.entityCutoutNoCullZOffset(EDGE)), face, 1-EDGE_SIZE, 0, 1, EDGE_SIZE, 1-EDGE_SIZE, 0, 1, EDGE_SIZE, light, packedOverlay);
                if (bl) drawRect(stack, buffers.getBuffer(RenderType.entityCutoutNoCullZOffset(EDGE)), face, 0, 0, EDGE_SIZE, EDGE_SIZE, 0, 0, EDGE_SIZE, EDGE_SIZE, light, packedOverlay);
            }

            // BdLib's model enhancer places the output badge on its connected
            // face and puts a direction arrow on each perpendicular face.
            if (badge) drawOverlay(stack, buffers.getBuffer(RenderType.entityCutoutNoCullZOffset(OUTPUT)), face, 0, outputs[face.ordinal()], light, packedOverlay);
            if (!blocked && outputs[top.ordinal()] >= 0) drawOverlay(stack, buffers.getBuffer(RenderType.entityCutoutNoCullZOffset(ARROW)), face, 0, outputs[top.ordinal()], light, packedOverlay);
            if (!blocked && outputs[right.ordinal()] >= 0) drawOverlay(stack, buffers.getBuffer(RenderType.entityCutoutNoCullZOffset(ARROW)), face, 1, outputs[right.ordinal()], light, packedOverlay);
            if (!blocked && outputs[top.getOpposite().ordinal()] >= 0) drawOverlay(stack, buffers.getBuffer(RenderType.entityCutoutNoCullZOffset(ARROW)), face, 2, outputs[top.getOpposite().ordinal()], light, packedOverlay);
            if (!blocked && outputs[right.getOpposite().ordinal()] >= 0) drawOverlay(stack, buffers.getBuffer(RenderType.entityCutoutNoCullZOffset(ARROW)), face, 3, outputs[right.getOpposite().ordinal()], light, packedOverlay);
        }
    }

    /**
     * Which of the 26 surrounding blocks belong to the same machine, and the output index of each face. Looked up
     * only when the structure changes (or every half second, to follow chunk and data updates), not once per frame.
     */
    private static final class Connections {
        long revision = -1, bucket = -1;
        final boolean[] near = new boolean[27];
        final int[] outputs = new int[6];
        static int index(int dx, int dy, int dz) { return (dx + 1) * 9 + (dy + 1) * 3 + (dz + 1); }
        boolean at(Direction a) { return near[index(a.getStepX(), a.getStepY(), a.getStepZ())]; }
        boolean at(Direction a, Direction b) { return near[index(a.getStepX() + b.getStepX(), a.getStepY() + b.getStepY(), a.getStepZ() + b.getStepZ())]; }
    }

    private static Connections connections(MachineBlockEntity machine) {
        var level = machine.getLevel();
        Connections c = machine.borderCache instanceof Connections known ? known : null;
        if (c == null) machine.borderCache = c = new Connections();
        long revision = MultiblockNetwork.revision(level), bucket = level.getGameTime() / 10;
        if (c.revision != revision || c.bucket != bucket) {
            var pos = machine.getBlockPos();
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++)
                if (dx != 0 || dy != 0 || dz != 0) c.near[Connections.index(dx, dy, dz)] = connects(machine, pos.offset(dx, dy, dz));
            java.util.Arrays.fill(c.outputs, -1);
            if (machine.kind().output())
                for (Direction direction : Direction.values())
                    if (!c.at(direction)) c.outputs[direction.ordinal()] = machine.outputIndex(machine, direction);
            c.revision = revision;
            c.bucket = bucket;
        }
        return c;
    }

    private static boolean connects(MachineBlockEntity machine, BlockPos targetPos) {
        var level = machine.getLevel();
        if (level == null || !level.hasChunkAt(targetPos)) return false;
        BlockEntity targetEntity = level.getBlockEntity(targetPos);
        if (!(targetEntity instanceof MachineBlockEntity target)) return false;

        if (machine.kind().controller()) return machine.network().members().contains(targetPos);
        BlockPos core = machine.connectedCore();
        if (core == null) return false;
        if (targetPos.equals(core)) return true;
        return !target.kind().controller() && core.equals(target.connectedCore());
    }

    private static Direction top(Direction face) {
        return switch (face) {
            case UP -> Direction.NORTH;
            case DOWN -> Direction.SOUTH;
            default -> Direction.UP;
        };
    }

    private static Direction right(Direction face) {
        return switch (face) {
            case UP, DOWN, SOUTH -> Direction.EAST;
            case NORTH -> Direction.WEST;
            case WEST -> Direction.SOUTH;
            case EAST -> Direction.NORTH;
        };
    }

    private static void drawRect(PoseStack stack, VertexConsumer out, Direction face,
                                 float x1, float y1, float x2, float y2,
                                 float u1, float v1, float u2, float v2, int light, int overlay) {
        float[][] positions = {
            point(face, x1, y1), point(face, x2, y1), point(face, x2, y2), point(face, x1, y2)
        };
        float[][] uvs = {{u1,v1},{u2,v1},{u2,v2},{u1,v2}};
        var pose = stack.last();
        int shade = (int)(faceShade(face) * 255);
        for (int i = 0; i < 4; i++) {
            float[] p = positions[i];
            float x = (p[0] - .5f) * EXPANSION + .5f;
            float y = (p[1] - .5f) * EXPANSION + .5f;
            float z = (p[2] - .5f) * EXPANSION + .5f;
            out.addVertex(pose.pose(), x, y, z)
                .setColor(shade, shade, shade, 255)
                .setUv(uvs[i][0], uvs[i][1])
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(pose, 0, 1, 0);
        }
    }

    private static void drawOverlay(PoseStack stack, VertexConsumer out, Direction face,
                                    int rotation, int colorIndex, int light, int overlay) {
        int[][] colors={{255,0,0},{0,255,0},{0,0,255},{255,255,0},{0,255,255},{255,0,255}};
        int[] color=colors[Math.floorMod(colorIndex,colors.length)];
        // Corners as seen from outside the face: upper left, lower left, lower right, upper right. The texture is
        // laid on them upright and then turned by `rotation` quarter turns, like the original's Texture.rotate.
        // (Image rows run downwards, so the face's own "up" must map to v = 0.)
        float[][] base = {{0,0},{0,1},{1,1},{1,0}};
        float[][] uv = new float[4][];
        for (int i = 0; i < 4; i++) uv[i] = base[(i + rotation) % 4];
        float[][] positions = {point(face,0,1), point(face,0,0), point(face,1,0), point(face,1,1)};
        var pose = stack.last();
        for (int i = 0; i < 4; i++) {
            float[] p = positions[i];
            float x = (p[0] - .5f) * EXPANSION + .5f;
            float y = (p[1] - .5f) * EXPANSION + .5f;
            float z = (p[2] - .5f) * EXPANSION + .5f;
            out.addVertex(pose.pose(), x, y, z)
                .setColor(color[0], color[1], color[2], 255) // the original baked these without face shading
                .setUv(uv[i][0], uv[i][1])
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(pose, 0, 1, 0);
        }
    }

    /**
     * Vanilla's flat face shading (the original drew these quads on the cutout layer without ambient occlusion).
     * The entity shader would shade by the vertex normal as well, so the quads use a neutral up normal and
     * carry the face shade in their colour instead, which keeps them exactly as bright as the block's own faces.
     */
    private static float faceShade(Direction face) {
        return switch (face) {
            case DOWN -> 0.5f;
            case UP -> 1.0f;
            case NORTH, SOUTH -> 0.8f;
            case WEST, EAST -> 0.6f;
        };
    }

    private static float[] point(Direction face, float u, float v) {
        return switch (face) {
            case UP -> new float[]{u,1,1-v};
            case DOWN -> new float[]{u,0,v};
            case NORTH -> new float[]{1-u,v,0};
            case SOUTH -> new float[]{u,v,1};
            case EAST -> new float[]{1,v,1-u};
            case WEST -> new float[]{0,v,u};
        };
    }
}
