package net.bdew.generators.machine;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.bdew.generators.Generators;
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
        if (machine.getLevel() == null) return;
        var level = machine.getLevel();
        var pos = machine.getBlockPos();
        var consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(EDGE));
        var outputConsumer = buffers.getBuffer(RenderType.entityCutoutNoCull(OUTPUT));
        var arrowConsumer = buffers.getBuffer(RenderType.entityCutoutNoCull(ARROW));
        int[] outputs = new int[Direction.values().length];
        java.util.Arrays.fill(outputs, -1);
        if (machine.kind().output()) {
            for (Direction direction : Direction.values()) {
                if (!connects(machine, pos.relative(direction))) outputs[direction.ordinal()] = machine.outputIndex(machine, direction);
            }
        }
        for (Direction face : Direction.values()) {
            BlockPos outside = pos.relative(face);
            boolean blocked = level.getBlockState(outside).isSolidRender(level, outside);

            Direction top = top(face);
            Direction right = right(face);
            if (!blocked) {
                boolean u = !connects(machine, pos.relative(top));
                boolean d = !connects(machine, pos.relative(top.getOpposite()));
                boolean l = !connects(machine, pos.relative(right.getOpposite()));
                boolean r = !connects(machine, pos.relative(right));

                if (u) drawRect(stack, consumer, face, 0, 1-EDGE_SIZE, 1, 1, 0, 1-EDGE_SIZE, 1, 1, packedLight, packedOverlay);
                if (d) drawRect(stack, consumer, face, 0, 0, 1, EDGE_SIZE, 0, 0, 1, EDGE_SIZE, packedLight, packedOverlay);
                if (l) drawRect(stack, consumer, face, 0, 0, EDGE_SIZE, 1, 0, 0, EDGE_SIZE, 1, packedLight, packedOverlay);
                if (r) drawRect(stack, consumer, face, 1-EDGE_SIZE, 0, 1, 1, 1-EDGE_SIZE, 0, 1, 1, packedLight, packedOverlay);

                if (!u && !r && !connects(machine, pos.relative(top).relative(right)))
                    drawRect(stack, consumer, face, 1-EDGE_SIZE, 1-EDGE_SIZE, 1, 1, 1-EDGE_SIZE, 1-EDGE_SIZE, 1, 1, packedLight, packedOverlay);
                if (!u && !l && !connects(machine, pos.relative(top).relative(right.getOpposite())))
                    drawRect(stack, consumer, face, 0, 1-EDGE_SIZE, EDGE_SIZE, 1, 0, 1-EDGE_SIZE, EDGE_SIZE, 1, packedLight, packedOverlay);
                if (!d && !r && !connects(machine, pos.relative(top.getOpposite()).relative(right)))
                    drawRect(stack, consumer, face, 1-EDGE_SIZE, 0, 1, EDGE_SIZE, 1-EDGE_SIZE, 0, 1, EDGE_SIZE, packedLight, packedOverlay);
                if (!d && !l && !connects(machine, pos.relative(top.getOpposite()).relative(right.getOpposite())))
                    drawRect(stack, consumer, face, 0, 0, EDGE_SIZE, EDGE_SIZE, 0, 0, EDGE_SIZE, EDGE_SIZE, packedLight, packedOverlay);
            }

            // BdLib's model enhancer places the output badge on its connected
            // face and puts a direction arrow on each perpendicular face.
            if (outputs[face.ordinal()] >= 0) drawOverlay(stack, outputConsumer, face, 0, outputs[face.ordinal()], packedLight, packedOverlay);
            if (!blocked && outputs[top.ordinal()] >= 0) drawOverlay(stack, arrowConsumer, face, 0, outputs[top.ordinal()], packedLight, packedOverlay);
            if (!blocked && outputs[right.ordinal()] >= 0) drawOverlay(stack, arrowConsumer, face, 1, outputs[right.ordinal()], packedLight, packedOverlay);
            if (!blocked && outputs[top.getOpposite().ordinal()] >= 0) drawOverlay(stack, arrowConsumer, face, 2, outputs[top.getOpposite().ordinal()], packedLight, packedOverlay);
            if (!blocked && outputs[right.getOpposite().ordinal()] >= 0) drawOverlay(stack, arrowConsumer, face, 3, outputs[right.getOpposite().ordinal()], packedLight, packedOverlay);
        }
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
        for (int i = 0; i < 4; i++) {
            float[] p = positions[i];
            float x = (p[0] - .5f) * EXPANSION + .5f;
            float y = (p[1] - .5f) * EXPANSION + .5f;
            float z = (p[2] - .5f) * EXPANSION + .5f;
            out.addVertex(pose.pose(), x, y, z)
                .setColor(255, 255, 255, 255)
                .setUv(uvs[i][0], uvs[i][1])
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(pose, face.getStepX(), face.getStepY(), face.getStepZ());
        }
    }

    private static void drawOverlay(PoseStack stack, VertexConsumer out, Direction face,
                                    int rotation, int colorIndex, int light, int overlay) {
        int[][] colors={{255,0,0},{0,255,0},{0,0,255},{255,255,0},{0,255,255},{255,0,255}};
        int[] color=colors[Math.floorMod(colorIndex,colors.length)];
        float[][] uv = switch (rotation) {
            case 1 -> new float[][]{{1,0},{1,1},{0,1},{0,0}};
            case 2 -> new float[][]{{1,1},{0,1},{0,0},{1,0}};
            case 3 -> new float[][]{{0,1},{0,0},{1,0},{1,1}};
            default -> new float[][]{{0,0},{1,0},{1,1},{0,1}};
        };
        float[][] positions = {point(face,0,0), point(face,1,0), point(face,1,1), point(face,0,1)};
        var pose = stack.last();
        for (int i = 0; i < 4; i++) {
            float[] p = positions[i];
            float x = (p[0] - .5f) * EXPANSION + .5f;
            float y = (p[1] - .5f) * EXPANSION + .5f;
            float z = (p[2] - .5f) * EXPANSION + .5f;
            out.addVertex(pose.pose(), x, y, z)
                .setColor(color[0], color[1], color[2], 255)
                .setUv(uv[i][0], uv[i][1])
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(pose, face.getStepX(), face.getStepY(), face.getStepZ());
        }
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
