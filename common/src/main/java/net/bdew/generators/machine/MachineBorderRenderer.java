package net.bdew.generators.machine;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.bdew.generators.Generators;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Draws the connected 1px frame from the original BdLib multiblock overlay texture. */
public final class MachineBorderRenderer implements BlockEntityRenderer<MachineBlockEntity> {
    private static final ResourceLocation EDGE = Generators.id("textures/block/connected/edge.png");

    public MachineBorderRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(MachineBlockEntity machine, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        if (machine.getLevel() == null) return;
        VertexConsumer out = buffers.getBuffer(RenderType.entityCutoutNoCull(EDGE));
        for (Direction face : Direction.values()) {
            BlockEntity neighbor = machine.getLevel().getBlockEntity(machine.getBlockPos().relative(face));
            if (neighbor instanceof MachineBlockEntity) continue;
            drawFace(pose, out, face, packedLight, packedOverlay);
        }
    }

    private static void drawFace(PoseStack stack, VertexConsumer out, Direction face, int light, int overlay) {
        float[][] points = switch (face) {
            case DOWN -> new float[][]{{0,-0.001f,0},{1,-0.001f,0},{1,-0.001f,1},{0,-0.001f,1}};
            case UP -> new float[][]{{0,1.001f,0},{0,1.001f,1},{1,1.001f,1},{1,1.001f,0}};
            case NORTH -> new float[][]{{0,0,-0.001f},{0,1,-0.001f},{1,1,-0.001f},{1,0,-0.001f}};
            case SOUTH -> new float[][]{{0,0,1.001f},{1,0,1.001f},{1,1,1.001f},{0,1,1.001f}};
            case WEST -> new float[][]{{-0.001f,0,0},{-0.001f,0,1},{-0.001f,1,1},{-0.001f,1,0}};
            case EAST -> new float[][]{{1.001f,0,0},{1.001f,1,0},{1.001f,1,1},{1.001f,0,1}};
        };
        var pose = stack.last();
        Matrix4f position = pose.pose();
        Matrix3f normal = pose.normal();
        float[][] uv = {{0,0},{0,1},{1,1},{1,0}};
        for (int i = 0; i < 4; i++) {
            out.vertex(position, points[i][0], points[i][1], points[i][2])
                .color(255, 255, 255, 255)
                .uv(uv[i][0], uv[i][1])
                .overlayCoords(overlay)
                .uv2(light)
                .normal(normal, face.getStepX(), face.getStepY(), face.getStepZ())
                .endVertex();
        }
    }
}
