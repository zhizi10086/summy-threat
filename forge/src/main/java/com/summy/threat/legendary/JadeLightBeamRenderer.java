package com.summy.threat.legendary;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** 用交叉光带绘制持续向上的翠玉光柱。 */
public final class JadeLightBeamRenderer
		extends EntityRenderer<JadeLightBeamEntity> {
	private static final ResourceLocation TEXTURE =
			new ResourceLocation("minecraft", "textures/misc/white.png");

	public JadeLightBeamRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public void render(JadeLightBeamEntity entity, float yaw, float partialTick,
			PoseStack pose, MultiBufferSource buffers, int packedLight) {
		float cameraYaw = Minecraft.getInstance().gameRenderer.getMainCamera().getYRot();
		pose.pushPose();
		pose.mulPose(Axis.YP.rotationDegrees(-cameraYaw));
		pose.mulPose(Axis.YP.rotationDegrees(Mth.sin(entity.tickCount * 0.08F) * 8.0F));
		VertexConsumer vertices = buffers.getBuffer(RenderType.lightning());
		drawStrip(pose, vertices, 0.075F, 12.0F);
		pose.mulPose(Axis.YP.rotationDegrees(90.0F));
		drawStrip(pose, vertices, 0.075F, 12.0F);
		pose.popPose();
	}

	private static void drawStrip(PoseStack pose, VertexConsumer vertices,
			float width, float height) {
		var matrix = pose.last().pose();
		vertices.vertex(matrix, -width, 0.0F, 0.0F).color(0.23F, 1.0F, 0.69F, 0.9F).endVertex();
		vertices.vertex(matrix, width, 0.0F, 0.0F).color(0.23F, 1.0F, 0.69F, 0.9F).endVertex();
		vertices.vertex(matrix, width, height, 0.0F).color(0.23F, 1.0F, 0.69F, 0.0F).endVertex();
		vertices.vertex(matrix, -width, height, 0.0F).color(0.23F, 1.0F, 0.69F, 0.0F).endVertex();
	}

	@Override
	public ResourceLocation getTextureLocation(JadeLightBeamEntity entity) {
		return TEXTURE;
	}
}
