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
	private static final ResourceLocation JADE_BEAM = texture("jade_beam");
	private static final ResourceLocation JADE_ORB = texture("jade_orb");
	private static final ResourceLocation SECTOR = texture("sector");
	private static final ResourceLocation RUNE = texture("rune");
	private static final ResourceLocation RING = texture("ring");
	private static final ResourceLocation SHELL = texture("shell");
	private static final float RING_OUTER_DIAMETER_UV = 0.942F;
	private static final float SECTOR_OUTER_DIAMETER_UV = 0.930F;
	private static final int WHITE = 0xFFFFFF;

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
		if (entity.category() == ThreatVisualEntity.LEGENDARY && entity.role() == ThreatVisualEntity.PRIMARY) {
			if (LegendaryActionId.COLLAPSING_HORIZON.id().equals(entity.visualId())) {
				drawHorizon(entity, pose, buffers, color);
			} else if (LegendaryActionId.DOOM_CLOCK.id().equals(entity.visualId())) {
				drawClock(entity, pose, buffers, color);
			}
			return;
		}
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
		// 落点环跟随光柱，锁定符文继续留在目标脚下。
		drawRing(pose, buffers, entity.radius() * 1.5F, color, fade * 0.52F);
		boolean warning = entity.phase() == ThreatVisualEntity.WARNING;
		float width = warning ? 0.115F + 0.115F * entity.progress() : 0.414F + 0.138F * impact;
		float alpha = warning ? 0.26F + 0.32F * entity.progress() : (0.88F + 0.12F * impact) * fade;
		float scroll = (time * (warning ? 0.028F : 0.065F + 0.055F * impact)) % 1.0F;
		pose.pushPose();
		pose.mulPose(Axis.YP.rotationDegrees(entity.angle() - entityRenderDispatcher.camera.getYRot()));
		drawBeam(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(JADE_BEAM)),
				width, scroll, alpha);
		if (warning) {
			drawBeam(pose, buffers.getBuffer(CoreLayer.TYPE), width * 0.36F, scroll, alpha * 0.28F);
		}
		pose.popPose();
		float orbSize = warning ? 0.25F + 0.12F * entity.progress() : 0.67F + 0.12F * impact;
		drawJadeOrb(pose, buffers, orbSize, alpha);
		pose.popPose();
	}

	private static void drawBeam(PoseStack pose, VertexConsumer buffer, float width, float scroll,
			float alpha) {
		pose.pushPose();
		float height = ThreatVisualEntity.JADE_BEAM_HEIGHT;
		float seam = height * (1.0F - scroll);
		// V 从柱顶到地面递增。拆分接缝后亮纹随时间向下，无需采样器环绕。
		for (int i = 0; i < 2; i++) {
			var matrix = pose.last();
			beamSegment(buffer, matrix, width, 0.0F, seam, 1.0F - scroll, 0.0F, alpha);
			if (scroll > 0.0F) {
				beamSegment(buffer, matrix, width, seam, height, 1.0F, 1.0F - scroll, alpha);
			}
			pose.mulPose(Axis.YP.rotationDegrees(90.0F));
		}
		pose.popPose();
	}

	private static void beamSegment(VertexConsumer buffer, PoseStack.Pose pose, float width,
			float bottom, float top, float bottomV, float topV, float alpha) {
		vertex(buffer, pose, -width, bottom, 0.0F, 0.0F, bottomV, WHITE, alpha);
		vertex(buffer, pose, width, bottom, 0.0F, 1.0F, bottomV, WHITE, alpha);
		vertex(buffer, pose, width, top, 0.0F, 1.0F, topV, WHITE, alpha);
		vertex(buffer, pose, -width, top, 0.0F, 0.0F, topV, WHITE, alpha);
	}

	private void drawJadeOrb(PoseStack pose, MultiBufferSource buffers, float size, float alpha) {
		pose.pushPose();
		pose.translate(0.0D, ThreatVisualEntity.JADE_BEAM_HEIGHT, 0.0D);
		pose.mulPose(entityRenderDispatcher.cameraOrientation());
		var matrix = pose.last();
		var buffer = buffers.getBuffer(RenderType.entityTranslucentEmissive(JADE_ORB));
		vertex(buffer, matrix, -size, -size, 0.0F, 0.0F, 1.0F, WHITE, alpha);
		vertex(buffer, matrix, size, -size, 0.0F, 1.0F, 1.0F, WHITE, alpha);
		vertex(buffer, matrix, size, size, 0.0F, 1.0F, 0.0F, WHITE, alpha);
		vertex(buffer, matrix, -size, size, 0.0F, 0.0F, 0.0F, WHITE, alpha);
		pose.popPose();
	}

	private static void drawHorizon(ThreatVisualEntity entity, PoseStack pose,
			MultiBufferSource buffers, int color) {
		float flash = entity.intensity();
		drawGround(pose, buffers, RING, (float) CollapsingHorizonAction.OUTER_RADIUS / RING_OUTER_DIAMETER_UV,
				0.025F, color, 0.70F + flash * 0.25F);
		drawGround(pose, buffers, RING, entity.radius() / RING_OUTER_DIAMETER_UV,
				0.035F, 0xFFE099, 0.78F + flash * 0.22F);
	}

	private static void drawClock(ThreatVisualEntity entity, PoseStack pose,
			MultiBufferSource buffers, int color) {
		float radius = entity.radius();
		float tile = radius / SECTOR_OUTER_DIAMETER_UV;
		boolean impact = entity.phase() == ThreatVisualEntity.IMPACT;
		drawGround(pose, buffers, RING, radius / RING_OUTER_DIAMETER_UV, 0.021F, color, 0.55F);
		for (int sector = 0; sector < 4; sector++) {
			pose.pushPose();
			// 正角度朝 +Z，对齐服务端 atan2(dz, dx)。
			pose.mulPose(Axis.YP.rotation(-entity.angle()
					- (float) LegendaryActionMath.clockwiseSectorOffset(sector)));
			float glow = sector == 0 ? entity.intensity() : 0.0F;
			int tint = glow > 0.0F ? (impact ? 0xFFF2AF : 0xFFD579) : color;
			drawGround(pose, buffers, SECTOR, tile, 0.028F, tint, 0.36F + 0.64F * glow);
			pose.popPose();
		}
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
						.setTextureState(new TextureStateShard(JADE_BEAM, false, false))
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
