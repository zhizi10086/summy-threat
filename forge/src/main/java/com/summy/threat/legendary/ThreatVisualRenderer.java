package com.summy.threat.legendary;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import com.summy.threat.SummyThreat;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** 客户端按视觉 ID 和角色绘制图元。 */
public final class ThreatVisualRenderer extends EntityRenderer<ThreatVisualEntity> {
	private static final ResourceLocation BEAM = texture("beam");
	private static final ResourceLocation RUNE = texture("rune");
	private static final ResourceLocation RING = texture("ring");
	private static final ResourceLocation SHELL = texture("shell");
	private static final float BEAM_HEIGHT = 12.0F;

	public ThreatVisualRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	private static ResourceLocation texture(String id) {
		return new ResourceLocation(SummyThreat.MOD_ID, "textures/effect/threat_" + id + ".png");
	}

	@Override
	public void render(ThreatVisualEntity entity, float yaw, float partialTick, PoseStack pose,
			MultiBufferSource buffers, int packedLight) {
		float fade = entity.phase() == ThreatVisualEntity.FADING
				? Mth.clamp((1.0F - entity.progress()) * 20.0F, 0.0F, 1.0F) : 1.0F;
		int color = entity.color();
		if (entity.role() == ThreatVisualEntity.AURA) {
			drawAura(pose, buffers, entity.radius(), color, fade * 0.65F);
			return;
		}
		if (entity.role() == ThreatVisualEntity.GROUND_RING) {
			drawRing(pose, buffers, entity.radius(), color, fade * 0.8F);
			return;
		}
		if (entity.role() == ThreatVisualEntity.MARKER) {
			drawGround(pose, buffers, RUNE, entity.radius(), 0.025F, color, fade * 0.9F);
			return;
		}
		if (entity.role() != ThreatVisualEntity.BEAM || entity.category() != ThreatVisualEntity.LEGENDARY
				|| !LegendaryActionId.JADE_LIGHT.id().equals(entity.visualId())) {
			return;
		}
		float impact = Mth.clamp(entity.intensity(), 0.0F, 1.0F);
		float time = entity.tickCount + partialTick;
		if ((entity.flags() & ThreatVisualEntity.LOCKED) != 0) {
			drawGround(pose, buffers, RUNE, 0.7F, 0.025F, color, fade * 0.9F);
		}
		var offset = entity.interpolatedOffset(partialTick);
		pose.pushPose();
		pose.translate(offset.x(), offset.y(), offset.z());
		// ???????????????????????????
		drawRing(pose, buffers, entity.radius() * 1.5F, color, fade * 0.52F);
		pose.mulPose(Axis.YP.rotationDegrees(entity.angle() - entityRenderDispatcher.camera.getYRot()));
		boolean warning = entity.phase() == ThreatVisualEntity.WARNING;
		float width = warning ? 0.10F + 0.10F * entity.progress() : 0.36F + 0.12F * impact;
		float alpha = warning ? 0.28F + 0.34F * entity.progress() : 0.93F * fade;
		float scroll = (time * (warning ? 0.028F : 0.065F + 0.055F * impact)) % 1.0F;
		drawBeam(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(BEAM)),
				width, scroll, color, alpha);
		if (warning) {
			drawBeam(pose, buffers.getBuffer(CoreLayer.TYPE), width * 0.36F, scroll, color, alpha * 0.28F);
		} else if (entity.phase() == ThreatVisualEntity.IMPACT) {
			drawBeam(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(BEAM)),
					width * 0.38F, scroll, 0xDEFFF4, fade * (0.28F + 0.55F * impact));
		}
		pose.popPose();
	}

	private static void drawBeam(PoseStack pose, VertexConsumer buffer, float width, float scroll,
			int color, float alpha) {
		pose.pushPose();
		for (int i = 0; i < 2; i++) {
			var matrix = pose.last();
			vertex(buffer, matrix, -width, 0.0F, 0.0F, 0.0F, 1.0F + scroll, color, alpha);
			vertex(buffer, matrix, width, 0.0F, 0.0F, 1.0F, 1.0F + scroll, color, alpha);
			vertex(buffer, matrix, width, BEAM_HEIGHT, 0.0F, 1.0F, scroll, color, 0.0F);
			vertex(buffer, matrix, -width, BEAM_HEIGHT, 0.0F, 0.0F, scroll, color, 0.0F);
			pose.mulPose(Axis.YP.rotationDegrees(90.0F));
		}
		pose.popPose();
	}

	private static void drawRing(PoseStack pose, MultiBufferSource buffers, float radius, int color, float alpha) {
		drawGround(pose, buffers, RING, radius, 0.018F, color, alpha);
	}

	private static void drawGround(PoseStack pose, MultiBufferSource buffers, ResourceLocation texture,
			float radius, float y, int color, float alpha) {
		VertexConsumer buffer = buffers.getBuffer(RenderType.entityTranslucentEmissive(texture));
		var matrix = pose.last();
		vertex(buffer, matrix, -radius, y, -radius, 0.0F, 0.0F, color, alpha);
		vertex(buffer, matrix, -radius, y, radius, 0.0F, 1.0F, color, alpha);
		vertex(buffer, matrix, radius, y, radius, 1.0F, 1.0F, color, alpha);
		vertex(buffer, matrix, radius, y, -radius, 1.0F, 0.0F, color, alpha);
	}

	private static void drawAura(PoseStack pose, MultiBufferSource buffers, float radius, int color, float alpha) {
		VertexConsumer buffer = buffers.getBuffer(RenderType.entityTranslucentEmissive(SHELL));
		pose.pushPose();
		for (int i = 0; i < 2; i++) {
			var matrix = pose.last();
			vertex(buffer, matrix, -radius, 0.0F, 0.0F, 0.0F, 1.0F, color, alpha);
			vertex(buffer, matrix, radius, 0.0F, 0.0F, 1.0F, 1.0F, color, alpha);
			vertex(buffer, matrix, radius, 2.0F * radius, 0.0F, 1.0F, 0.0F, color, alpha);
			vertex(buffer, matrix, -radius, 2.0F * radius, 0.0F, 0.0F, 0.0F, color, alpha);
			pose.mulPose(Axis.YP.rotationDegrees(90.0F));
		}
		pose.popPose();
	}

	private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z,
			float u, float v, int color, float alpha) {
		buffer.vertex(pose.pose(), x, y, z)
				.color((color >> 16) & 255, (color >> 8) & 255, color & 255, Math.round(255.0F * alpha))
				.uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
				.normal(pose.normal(), 0.0F, 1.0F, 0.0F).endVertex();
	}

	@Override
	public ResourceLocation getTextureLocation(ThreatVisualEntity entity) {
		return BEAM;
	}

	/** 隔墙只保留低透明的预警细芯。 */
	private abstract static class CoreLayer extends RenderType {
		private static final RenderType TYPE = create("summy_threat_warning_core", DefaultVertexFormat.NEW_ENTITY,
				VertexFormat.Mode.QUADS, 256, false, true, CompositeState.builder()
						.setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
						.setTextureState(new TextureStateShard(BEAM, false, false))
						.setTransparencyState(TRANSLUCENT_TRANSPARENCY)
						.setDepthTestState(NO_DEPTH_TEST)
						.setCullState(NO_CULL)
						.setOverlayState(OVERLAY)
						.setWriteMaskState(COLOR_WRITE)
						.createCompositeState(false));

		private CoreLayer() {
			super("summy_threat_warning_core", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS,
					256, false, true, () -> {}, () -> {});
		}
	}
}
