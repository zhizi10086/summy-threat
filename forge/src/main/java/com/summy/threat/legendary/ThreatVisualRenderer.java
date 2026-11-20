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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** 客户端按视觉 ID 和角色绘制图元。 */
public final class ThreatVisualRenderer extends EntityRenderer<ThreatVisualEntity> {
	private static final ResourceLocation BEAM = texture("beam");
	private static final ResourceLocation JADE_BEAM = texture("jade_beam");
	private static final ResourceLocation JADE_ORB = texture("jade_orb");
	private static final ResourceLocation DRAGON_WAVE = texture("dragon_lair_wave");
	private static final ResourceLocation DECAY_WAVE = texture("decay_tide_wave");
	private static final ResourceLocation SHARD = texture("shard");
	private static final ResourceLocation COLUMN = texture("column");
	private static final ResourceLocation CLOUD = texture("cloud");
	private static final ResourceLocation CRACK = texture("crack");
	private static final ResourceLocation SECTOR = texture("sector");
	private static final ResourceLocation RUNE = texture("rune");
	private static final ResourceLocation RING = texture("ring");
	private static final ResourceLocation SHELL = texture("shell");
	private static final float RING_OUTER_DIAMETER_UV = 0.942F;
	private static final float SECTOR_OUTER_DIAMETER_UV = 0.930F;
	// 原贴图的最后一行可见像素位于第 123 行，反转后宽端贴住天幕。
	private static final float COLUMN_ROOT_V = 123.5F / 128.0F;
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
		float fade = entity.opacity(partialTick);
		if (fade <= 0.0F) {
			return;
		}
		int color = entity.color();
		if (entity.category() == ThreatVisualEntity.LEGENDARY && entity.role() == ThreatVisualEntity.PRIMARY) {
			if (LegendaryActionId.COLLAPSING_HORIZON.id().equals(entity.visualId())) {
				drawHorizon(entity, pose, buffers, color, fade);
				drawMotes(entity, pose, buffers, ThreatFieldMotes.Band.ring(entity.radius(),
						CollapsingHorizonAction.OUTER_RADIUS), 0, 24, partialTick, color, fade, 0.55F, false);
			} else if (LegendaryActionId.DOOM_CLOCK.id().equals(entity.visualId())) {
				drawClock(entity, pose, buffers, color, fade);
				if (entity.intensity() > 0.0F) {
					drawMotes(entity, pose, buffers, new ThreatFieldMotes.Band(0.0D, entity.radius(),
							entity.angle() - Math.PI / 4.0D, Math.PI / 2.0D), 0, 14,
							partialTick, color, fade * (0.65F + entity.intensity() * 0.35F), 0.55F, false);
				}
			} else if (LegendaryActionId.DRAGON_LAIR_PULSE.id().equals(entity.visualId())) {
				drawWaveField(entity, LegendaryWavePattern.DRAGON, DRAGON_WAVE, pose, buffers, partialTick, color, fade);
			} else if (LegendaryActionId.DECAY_TIDE.id().equals(entity.visualId())) {
				drawWaveField(entity, LegendaryWavePattern.DECAY, DECAY_WAVE, pose, buffers, partialTick, color, fade);
			} else if (LegendaryActionId.MIRROR_STEP.id().equals(entity.visualId())) {
				drawMirrorStep(entity, pose, buffers, partialTick, color, fade);
			} else if (LegendaryActionId.LAIR_BREACH.id().equals(entity.visualId())) {
				drawLairBreach(entity, pose, buffers, partialTick, color, fade);
			} else if (LegendaryActionId.REVERSE_GRAVITY.id().equals(entity.visualId())) {
				drawReverseGravity(entity, pose, buffers, partialTick, color, fade);
			} else if (LegendaryActionId.LEGENDARY_RIPOSTE.id().equals(entity.visualId())) {
				drawLegendaryRiposte(entity, pose, buffers, partialTick, color, fade);
			} else if (LegendaryActionId.FRIGHTFUL_PRESENCE.id().equals(entity.visualId())) {
				drawFrightfulPresence(entity, pose, buffers, partialTick, color, fade);
			} else if (LegendaryActionId.DRAGONFALL_EDICT.id().equals(entity.visualId())) {
				drawDragonfall(entity, pose, buffers, partialTick, color, fade);
			} else if (LegendaryActionId.ECHO_SENTENCE.id().equals(entity.visualId())) {
				drawEchoSentence(entity, pose, buffers, partialTick, color, fade);
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
		float alpha = (warning ? 0.26F + 0.32F * entity.progress() : 0.88F + 0.12F * impact) * fade;
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
			MultiBufferSource buffers, int color, float fade) {
		float flash = entity.intensity();
		drawGround(pose, buffers, RING, (float) CollapsingHorizonAction.OUTER_RADIUS / RING_OUTER_DIAMETER_UV,
				0.025F, color, fade * (0.70F + flash * 0.25F));
		drawGround(pose, buffers, RING, entity.radius() / RING_OUTER_DIAMETER_UV,
				0.035F, 0xFFE099, fade * (0.78F + flash * 0.22F));
	}

	private static void drawClock(ThreatVisualEntity entity, PoseStack pose,
			MultiBufferSource buffers, int color, float fade) {
		float radius = entity.radius();
		float tile = radius / SECTOR_OUTER_DIAMETER_UV;
		boolean impact = entity.phase() == ThreatVisualEntity.IMPACT;
		drawGround(pose, buffers, RING, radius / RING_OUTER_DIAMETER_UV, 0.021F, color, 0.55F * fade);
		for (int sector = 0; sector < 4; sector++) {
			pose.pushPose();
			// 正角度朝 +Z，对齐服务端 atan2(dz, dx)。
			pose.mulPose(Axis.YP.rotation(-entity.angle()
					- (float) LegendaryActionMath.clockwiseSectorOffset(sector)));
			float glow = sector == 0 ? entity.intensity() : 0.0F;
			int tint = glow > 0.0F ? (impact ? 0xFFF2AF : 0xFFD579) : color;
			drawGround(pose, buffers, SECTOR, tile, 0.028F, tint, fade * (0.36F + 0.64F * glow));
			pose.popPose();
		}
	}

	private void drawWaveField(ThreatVisualEntity entity, LegendaryWavePattern pattern, ResourceLocation texture,
			PoseStack pose, MultiBufferSource buffers, float partialTick, int color, float fade) {
		if (entity.phase() == ThreatVisualEntity.WARNING) {
			drawGround(pose, buffers, RING, (float) pattern.warningRadius / RING_OUTER_DIAMETER_UV,
					0.035F, color, fade * (0.42F + 0.40F * entity.progress() * pattern.endAge() / pattern.warningTicks));
			return;
		}
		int age = Math.round(entity.progress() * pattern.endAge());
		boolean fading = entity.phase() == ThreatVisualEntity.FADING;
		float interpolation = fading ? 0.0F : partialTick;
		int newest = Math.min(LegendaryWavePattern.COUNT - 1, (age - pattern.warningTicks) / LegendaryWavePattern.INTERVAL);
		float blend = Math.min(1.0F, (pattern.waveAge(age, newest) + interpolation) / 4.0F);
		boolean dragon = pattern == LegendaryWavePattern.DRAGON;
		var buffer = buffers.getBuffer(RenderType.entityTranslucentEmissive(texture));
		var matrix = pose.last();
		for (int index = 0; index <= newest; index++) {
			int waveAge = pattern.waveAge(age, index);
			if (!pattern.isPresent(waveAge)) {
				continue;
			}
			float radius = (float) pattern.radiusAt(waveAge + interpolation);
			if (radius < 0.01F) {
				continue;
			}
			float alpha = index == newest ? 0.90F * blend : 0.28F;
			if (index == newest - 1) {
				alpha = Mth.lerp(blend, 0.90F, alpha);
			}
			if (index < LegendaryWavePattern.COUNT - 1) {
				alpha *= Mth.clamp((33.0F - waveAge - interpolation) / 3.0F, 0.0F, 1.0F);
			}
			float inner = Math.max(0.0F, radius - (float) pattern.width);
			double start = pattern.gapCenter(entity.angle(), index) + pattern.gapHalfWidth;
			double span = 2.0D * Math.PI - 2.0D * pattern.gapHalfWidth;
			drawWaveBand(buffer, matrix, start, span, inner, radius, dragon, color, alpha * fade);
		}
		// 先提交所有波带顶点，再切换贴图绘制光点。
		for (int index = 0; index <= newest; index++) {
			int waveAge = pattern.waveAge(age, index);
			if (!pattern.isPresent(waveAge)) {
				continue;
			}
			double outer = pattern.radiusAt(waveAge + interpolation);
			if (outer < 0.8D) {
				continue;
			}
			double start = pattern.gapCenter(entity.angle(), index) + pattern.gapHalfWidth;
			var band = new ThreatFieldMotes.Band(Math.max(0.0D, outer - pattern.width),
					outer, start, ThreatFieldMotes.FULL_CIRCLE - 2 * pattern.gapHalfWidth);
			float alpha = index == newest ? 0.90F * blend : 0.48F;
			if (index < LegendaryWavePattern.COUNT - 1) {
				alpha *= Mth.clamp((33.0F - waveAge - interpolation) / 3.0F, 0.0F, 1.0F);
			}
			drawMotes(entity, pose, buffers, band, index * 6, 6, partialTick, color,
					alpha * fade, dragon ? 0.55F : 0.38F, false);
		}
	}

	private static void drawWaveBand(VertexConsumer buffer, PoseStack.Pose pose,
			double start, double span, float inner, float outer, boolean dragon, int color, float alpha) {
		// 在真实伤害弧上分段铺纹理，缺口两侧在几何上截断。
		int tiles = Math.max(1, (int) Math.ceil(span * outer / 9.0D));
		int segments = tiles * 8;
		float insideV = dragon ? 0.55F : 0.60F;
		float frontHeight = dragon ? 0.18F : 0.08F;
		for (int segment = 0; segment < segments; segment++) {
			double a = start + span * segment / segments;
			double b = start + span * (segment + 1) / segments;
			float ax = (float) Math.cos(a), az = (float) Math.sin(a);
			float bx = (float) Math.cos(b), bz = (float) Math.sin(b);
			float u0 = (segment % 8) / 8.0F;
			float u1 = u0 + 0.125F;
			vertex(buffer, pose, ax * inner, 0.04F, az * inner, u0, insideV, color, alpha);
			vertex(buffer, pose, bx * inner, 0.04F, bz * inner, u1, insideV, color, alpha);
			vertex(buffer, pose, bx * outer, frontHeight, bz * outer, u1, 0.95F, color, alpha);
			vertex(buffer, pose, ax * outer, frontHeight, az * outer, u0, 0.95F, color, alpha);
		}
	}

	private void drawMirrorStep(ThreatVisualEntity entity, PoseStack pose,
			MultiBufferSource buffers, float partialTick, int color, float fade) {
		float baseAngle = entity.angle();
		if (entity.phase() == ThreatVisualEntity.WARNING) {
			drawGround(pose, buffers, RUNE, 1.0F, 0.032F, color, fade * 0.84F);
			drawRing(pose, buffers, 1.2F, color, fade * 0.58F);
			drawShardRing(pose, buffers, 0.82F, 1.08F, 0.52F,
					baseAngle, color, fade * (0.62F + entity.progress() * 0.28F));
			drawShardDecals(pose, buffers, 0.76F, 0.42F, baseAngle, color, fade * 0.78F);
			return;
		}
		var offset = entity.interpolatedOffset(partialTick);
		pose.pushPose();
		pose.translate(offset.x(), offset.y(), offset.z());
		float radius = entity.radius();
		boolean impact = entity.phase() == ThreatVisualEntity.IMPACT;
		drawGround(pose, buffers, RING, radius / RING_OUTER_DIAMETER_UV,
				0.035F, color, fade * (impact ? 0.94F : 0.74F));
		drawGround(pose, buffers, CRACK, Math.max(0.75F, radius * 0.42F),
				0.045F, color, fade * (impact ? 0.72F : 0.30F));
		drawShardRing(pose, buffers, radius * 0.68F, 1.12F, 0.54F,
				baseAngle + entity.progress() * 180.0F, color, fade * (impact ? 0.96F : 0.7F));
		drawShardDecals(pose, buffers, Math.max(0.5F, radius * 0.68F), 0.46F,
				baseAngle + entity.progress() * 180.0F, color, fade * (impact ? 0.86F : 0.66F));
		drawMotes(entity, pose, buffers, ThreatFieldMotes.Band.ring(0.0D, radius),
				0, 8, partialTick, color, fade * 0.85F, 0.5F, false);
		pose.popPose();

		if (offset.lengthSquared() > 0.01F) {
			drawGround(pose, buffers, RUNE, 0.82F, 0.03F, color, fade * 0.4F);
			drawShardRing(pose, buffers, 0.68F, 0.96F, 0.4F,
					baseAngle + 45.0F, color, fade * 0.52F);
			drawShardDecals(pose, buffers, 0.58F, 0.34F,
					baseAngle + 45.0F, color, fade * 0.48F);
		}
	}

	private void drawLairBreach(ThreatVisualEntity entity, PoseStack pose,
			MultiBufferSource buffers, float partialTick, int color, float fade) {
		boolean warning = entity.phase() == ThreatVisualEntity.WARNING;
		boolean fading = entity.phase() == ThreatVisualEntity.FADING;
		float age = Math.round(entity.progress() * LairBreachAction.DURATION_TICKS);
		float burstAge = age + (fading ? entity.fadeElapsed(partialTick) : partialTick);
		int highlighted = Math.round(entity.angle());
		int erupted = entity.flags();
		for (int vent = 0; vent < 4; vent++) {
			Vec3 offset = LairBreachAction.ventCenter(Vec3.ZERO, vent);
			boolean isCurrent = vent == highlighted;
			boolean hasErupted = (erupted & (1 << vent)) != 0;
			float alpha = isCurrent ? 0.48F + entity.intensity() * 0.34F : hasErupted ? 0.2F : 0.42F;
			float elapsed = burstAge - LairBreachAction.burstTick(vent);
			pose.pushPose();
			pose.translate(offset.x, 0.0D, offset.z);
			drawGround(pose, buffers, RUNE, 1.55F, 0.032F, color, fade * alpha);
			drawGround(pose, buffers, RING,
					(float) LairBreachAction.VENT_RADIUS / RING_OUTER_DIAMETER_UV,
					0.04F, color, fade * (isCurrent ? 0.76F : 0.4F));
			if (hasErupted && elapsed < 8.0F) {
				drawLairBurst(pose, buffers, Math.max(0.0F, elapsed), fade);
			}
			float moteElapsed = age - LairBreachAction.burstTick(vent);
			if (!fading && (warning || !hasErupted || moteElapsed < 3.0F)) {
				drawMotes(entity, pose, buffers, ThreatFieldMotes.Band.ring(0.0D, LairBreachAction.VENT_RADIUS),
						vent * 4, 4, partialTick, color, fade * 0.72F, 0.53F, false);
			}
			pose.popPose();
		}
	}

	private void drawLairBurst(PoseStack pose, MultiBufferSource buffers, float elapsed, float fade) {
		float travel = Mth.clamp(elapsed / 5.0F, 0.0F, 1.0F);
		float ringRadius = Mth.lerp(1.0F - (1.0F - travel) * (1.0F - travel),
				0.55F, (float) LairBreachAction.VENT_RADIUS);
		float flash = Math.max(0.0F, 1.0F - elapsed / 3.5F);
		drawGround(pose, buffers, CRACK, 1.6F + travel * 0.45F, 0.055F,
				0xFFE0AD, fade * flash * 0.95F);
		drawGround(pose, buffers, RING, ringRadius / RING_OUTER_DIAMETER_UV, 0.065F,
				0xFFD19B, fade * (1.0F - elapsed / 8.0F) * 0.92F);
		float smoke = (1.0F - elapsed / 8.0F) * Math.min(1.0F, (elapsed + 0.45F) / 2.0F);
		for (int puff = 0; puff < 3; puff++) {
			double angle = puff * ThreatFieldMotes.FULL_CIRCLE / 3.0D + 0.35D;
			float spread = 0.24F + 1.22F * travel;
			drawBillboard(pose, buffers, CLOUD, (float) Math.cos(angle) * spread,
					0.48F + 0.45F * travel, (float) Math.sin(angle) * spread,
					0.68F + travel * 0.52F, (puff - 1) * 19.0F, 0xB69779, fade * smoke * 0.86F);
		}
	}

	private void drawReverseGravity(ThreatVisualEntity entity, PoseStack pose,
			MultiBufferSource buffers, float partialTick, int color, float fade) {
		float radius = entity.radius();
		float phaseAlpha = entity.phase() == ThreatVisualEntity.WARNING ? 0.56F
				: entity.intensity() > 0.0F ? 0.96F : 0.76F;
		drawGround(pose, buffers, RING, radius / RING_OUTER_DIAMETER_UV,
				0.03F, color, fade * phaseAlpha);
		if (entity.phase() != ThreatVisualEntity.WARNING) {
			float time = entity.moteTime(partialTick);
			float pulse = entity.intensity() > 0.0F ? 0.55F + 0.45F * Mth.cos(time * 1.55F) : 0.0F;
			drawMotes(entity, pose, buffers, ThreatFieldMotes.Band.ring(0.0D, radius), 0, 24,
					partialTick, pulse > 0.4F ? 0xF0E8FF : 0xC9A8FF,
					fade * (0.87F + 0.13F * pulse), ReverseGravityAction.MOTE_HEIGHT, true);
		}
		if ((entity.flags() & ThreatVisualEntity.LOCKED) != 0) {
			var offset = entity.interpolatedOffset(partialTick);
			pose.pushPose();
			pose.translate(offset.x(), offset.y(), offset.z());
			drawGround(pose, buffers, RUNE, 0.88F, 0.035F, color, fade * 0.88F);
			pose.popPose();
		}
	}

	private void drawLegendaryRiposte(ThreatVisualEntity entity, PoseStack pose,
			MultiBufferSource buffers, float partialTick, int color, float fade) {
		float charge = Mth.clamp(entity.intensity(), 0.0F, 1.0F);
		boolean impact = charge >= 1.0F;
		float alpha = fade * (impact ? 1.0F : 0.34F + charge * 0.54F);
		drawGround(pose, buffers, RING, entity.radius() / RING_OUTER_DIAMETER_UV,
				0.035F, color, alpha);
		drawGround(pose, buffers, RUNE, 1.1F, 0.04F, color, alpha * 0.8F);
		drawGround(pose, buffers, CRACK, impact ? 2.2F : 0.85F,
				0.05F, color, alpha * (impact ? 0.95F : 0.28F + charge * 0.35F));
		if (charge >= 0.5F) {
			drawMotes(entity, pose, buffers, ThreatFieldMotes.Band.ring(0.0D, entity.radius()),
					0, 8, partialTick, color, alpha, 0.5F, false);
		}
		var bodyOffset = entity.interpolatedOffset(partialTick);
		pose.pushPose();
		pose.translate(bodyOffset.x(), bodyOffset.y(), bodyOffset.z());
		drawBodyCrack(pose, buffers, 2.55F, 0.92F, color,
				alpha * (impact ? 1.0F : 0.22F + charge * 0.64F));
		var viewOffset = entityRenderDispatcher.camera.getPosition()
				.subtract(entity.position().add(bodyOffset.x(), bodyOffset.y(), bodyOffset.z()))
				.normalize().scale(0.22D);
		drawBillboard(pose, buffers, CRACK, (float) viewOffset.x, 1.25F + (float) viewOffset.y,
				(float) viewOffset.z, 0.78F, entity.tickCount * 1.5F,
				color, alpha * (impact ? 0.95F : 0.48F + charge * 0.35F));
		if (impact) {
			drawShardRing(pose, buffers, 0.78F, 1.15F, 0.34F,
					entity.tickCount + partialTick, color, fade * 0.88F);
		}
		pose.popPose();
	}

	private static void drawFrightfulPresence(ThreatVisualEntity entity, PoseStack pose,
			MultiBufferSource buffers, float partialTick, int color, float fade) {
		float pressure = entity.progress();
		float edge = entity.radius() / RING_OUTER_DIAMETER_UV;
		drawRing(pose, buffers, edge, color, fade * (0.45F + pressure * 0.45F));
		drawGround(pose, buffers, RING, edge * 0.82F, 0.042F, color, fade * pressure * 0.33F);
		// 高处的两层环向内收拢，落地边界保持 24 格。
		float sky = entity.intensity();
		drawGround(pose, buffers, RING, edge * (0.94F - pressure * 0.24F),
				sky, color, fade * 0.49F);
		drawGround(pose, buffers, RING, edge * (0.80F - pressure * 0.24F),
				sky + 1.1F, color, fade * 0.24F);
		drawTargetRunes(entity, pose, buffers, partialTick, 0.84F, color, fade * 0.72F);
	}

	private void drawDragonfall(ThreatVisualEntity entity, PoseStack pose, MultiBufferSource buffers,
			float partialTick, int color, float fade) {
		boolean warning = entity.phase() == ThreatVisualEntity.WARNING;
		float flash = entity.phase() == ThreatVisualEntity.FADING ? 0.0F : entity.intensity();
		Vec3 origin = entity.getPosition(partialTick);
		float flow = (entity.moteTime(partialTick) * 0.10F) % 1.0F;
		for (int id : entity.targetIds()) {
			Entity target = entity.level().getEntity(id);
			if (target == null || !target.isAlive()) {
				continue;
			}
			Vec3 feet = target.getPosition(partialTick).subtract(origin);
			float top = target.getBbHeight() + 2.4F;
			float radius = warning ? 2.55F - 0.52F * entity.progress() : 2.02F - 0.32F * flash;
			pose.pushPose();
			pose.translate(feet.x, feet.y, feet.z);
			drawGround(pose, buffers, RING, radius, top, color, fade * (0.74F + flash * 0.23F));
			drawGround(pose, buffers, RING, radius * 0.67F, top - 0.48F,
					0xBCA0FF, fade * (0.4F + flash * 0.35F));
			if (!warning) {
				pose.mulPose(Axis.YP.rotationDegrees(-entityRenderDispatcher.camera.getYRot()));
				var layer = buffers.getBuffer(RenderType.entityTranslucentEmissive(COLUMN));
				float source = top - 0.48F;
				float side = radius * 0.54F;
				float strength = fade * (0.64F + 0.28F * flash);
				drawDownwardJet(layer, pose.last(), -side, source, flow, strength);
				drawDownwardJet(layer, pose.last(), side, source, (flow + 0.5F) % 1.0F, strength);
			}
			pose.popPose();
		}
	}

	private static void drawDownwardJet(VertexConsumer buffer, PoseStack.Pose matrix, float center,
			float source, float flow, float alpha) {
		float end = 0.25F;
		drawInvertedColumn(buffer, matrix, center, 0.47F, source, end,
				0xBCA0FF, alpha, alpha * 0.30F);

		// 亮纹从环下向尖端落下，淡过两端后再开始下一轮。
		float length = source - end;
		float streakTop = source - flow * length * 0.70F;
		float streakBottom = streakTop - length * 0.30F;
		float streakWidth = Mth.lerp(flow, 0.30F, 0.08F);
		float streakAlpha = alpha * 0.85F * Math.min(1.0F, Math.min(flow, 1.0F - flow) * 6.0F);
		drawInvertedColumn(buffer, matrix, center, streakWidth, streakTop, streakBottom,
				0xF4E8FF, streakAlpha, streakAlpha * 0.40F);
	}

	private static void drawInvertedColumn(VertexConsumer buffer, PoseStack.Pose matrix, float center,
			float halfWidth, float top, float bottom, int color, float topAlpha, float bottomAlpha) {
		vertex(buffer, matrix, center - halfWidth, bottom, 0.0F, 0.0F, 0.0F, color, bottomAlpha);
		vertex(buffer, matrix, center + halfWidth, bottom, 0.0F, 1.0F, 0.0F, color, bottomAlpha);
		vertex(buffer, matrix, center + halfWidth, top, 0.0F, 1.0F, COLUMN_ROOT_V, color, topAlpha);
		vertex(buffer, matrix, center - halfWidth, top, 0.0F, 0.0F, COLUMN_ROOT_V, color, topAlpha);
	}

	private static void drawTargetRunes(ThreatVisualEntity entity, PoseStack pose, MultiBufferSource buffers,
			float partialTick, float radius, int color, float alpha) {
		Vec3 origin = entity.getPosition(partialTick);
		for (int id : entity.targetIds()) {
			Entity target = entity.level().getEntity(id);
			if (target != null && target.isAlive()) {
				Vec3 feet = target.getPosition(partialTick).subtract(origin);
				pose.pushPose();
				pose.translate(feet.x, feet.y, feet.z);
				drawGround(pose, buffers, RUNE, radius, 0.045F, color, alpha);
				pose.popPose();
			}
		}
	}

	private static void drawEchoSentence(ThreatVisualEntity entity, PoseStack pose, MultiBufferSource buffers,
			float partialTick, int color, float fade) {
		float wave = (entity.moteTime(partialTick) % 22.0F) / 22.0F;
		drawGround(pose, buffers, RUNE, 2.6F, 0.036F, color, fade * 0.56F);
		drawRing(pose, buffers, 2.7F + 4.3F * wave, color, fade * (0.65F - 0.48F * wave));
		drawTargetRunes(entity, pose, buffers, partialTick, 0.7F, color, fade * 0.58F);

		Vec3 origin = entity.getPosition(partialTick);
		for (Tag tag : entity.echoes()) {
			CompoundTag echo = (CompoundTag) tag;
			Vec3 point = EchoSentenceAction.echoPosition(echo).subtract(origin);
			float elapsed = entity.level().getGameTime() - echo.getLong("tick") + partialTick;
			float radius = EchoSentenceAction.ECHO_RADIUS / RING_OUTER_DIAMETER_UV;
			pose.pushPose();
			pose.translate(point.x, point.y, point.z);
			if (echo.getBoolean("impact")) {
				float expansion = Mth.clamp(elapsed / EchoSentenceAction.ECHO_FLASH, 0.0F, 1.0F);
				float flash = fade * (1.0F - expansion);
				drawRing(pose, buffers, radius * (0.58F + 0.42F * expansion), 0xB4FFFF, flash);
				drawGround(pose, buffers, RUNE, radius * 0.78F, 0.065F, color, flash * 0.68F);
			} else {
				float charge = Mth.clamp(1.0F + elapsed / EchoSentenceAction.ECHO_DELAY, 0.0F, 1.0F);
				drawRing(pose, buffers, radius, color, fade * (0.65F + 0.35F * charge));
				drawGround(pose, buffers, RUNE, radius * 0.78F, 0.052F, color, fade * 0.72F);
				// 十颗音点随倒计时逐个亮起，完整外环始终标出原地爆发半径。
				int lit = Mth.clamp(Mth.ceil(charge * EchoSentenceAction.ECHO_DELAY), 0, EchoSentenceAction.ECHO_DELAY);
				for (int i = 0; i < EchoSentenceAction.ECHO_DELAY; i++) {
					double angle = Math.PI * 2.0D * i / EchoSentenceAction.ECHO_DELAY;
					pose.pushPose();
					pose.translate(2.0D * Math.cos(angle), 0.12D, 2.0D * Math.sin(angle));
					drawGround(pose, buffers, SHARD, 0.23F, 0.0F,
							i < lit ? 0xB4FFFF : color, fade * (i < lit ? 1.0F : 0.28F));
					pose.popPose();
				}
			}
			pose.popPose();
		}
	}

	private void drawShardRing(PoseStack pose, MultiBufferSource buffers, float radius,
			float height, float size, float rotationDegrees, int color, float alpha) {
		for (int shard = 0; shard < 4; shard++) {
			double angle = Math.toRadians(rotationDegrees + shard * 90.0D);
			drawBillboard(pose, buffers, SHARD, (float) Math.cos(angle) * radius,
					height + (shard % 2) * 0.22F, (float) Math.sin(angle) * radius,
					size, rotationDegrees + shard * 41.0F, color, alpha);
		}
	}

	private static void drawShardDecals(PoseStack pose, MultiBufferSource buffers, float radius,
			float halfSize, float rotationDegrees, int color, float alpha) {
		var buffer = buffers.getBuffer(RenderType.entityTranslucentEmissive(SHARD));
		for (int shard = 0; shard < 4; shard++) {
			double angle = Math.toRadians(rotationDegrees + shard * 90.0D);
			pose.pushPose();
			pose.translate(Math.cos(angle) * radius, 0.055D, Math.sin(angle) * radius);
			pose.mulPose(Axis.YP.rotationDegrees(-rotationDegrees - shard * 41.0F));
			var matrix = pose.last();
			vertex(buffer, matrix, -halfSize, 0.0F, -halfSize, 0.0F, 1.0F, color, alpha);
			vertex(buffer, matrix, -halfSize, 0.0F, halfSize, 0.0F, 0.0F, color, alpha);
			vertex(buffer, matrix, halfSize, 0.0F, halfSize, 1.0F, 0.0F, color, alpha);
			vertex(buffer, matrix, halfSize, 0.0F, -halfSize, 1.0F, 1.0F, color, alpha);
			pose.popPose();
		}
	}

	private void drawBillboard(PoseStack pose, MultiBufferSource buffers, ResourceLocation texture,
			float x, float y, float z, float size, float rotation, int color, float alpha) {
		pose.pushPose();
		pose.translate(x, y, z);
		pose.mulPose(entityRenderDispatcher.cameraOrientation());
		pose.mulPose(Axis.ZP.rotationDegrees(rotation));
		var matrix = pose.last();
		var buffer = buffers.getBuffer(RenderType.entityTranslucentEmissive(texture));
		vertex(buffer, matrix, -size, -size, 0.0F, 0.0F, 1.0F, color, alpha);
		vertex(buffer, matrix, size, -size, 0.0F, 1.0F, 1.0F, color, alpha);
		vertex(buffer, matrix, size, size, 0.0F, 1.0F, 0.0F, color, alpha);
		vertex(buffer, matrix, -size, size, 0.0F, 0.0F, 0.0F, color, alpha);
		pose.popPose();
	}

	private void drawMotes(ThreatVisualEntity entity, PoseStack pose, MultiBufferSource buffers,
			ThreatFieldMotes.Band band, int first, int count, float partialTick, int color,
			float alpha, float maxY, boolean rising) {
		if (alpha <= 0.02F) {
			return;
		}
		float time = entity.moteTime(partialTick);
		long seed = entity.getUUID().getLeastSignificantBits();
		for (int slot = first; slot < Math.min(ThreatFieldMotes.BUDGET, first + count); slot++) {
			Vec3 point = band.sample(seed, slot, time);
			double phase = ThreatFieldMotes.unit(seed, slot * 4 + 2);
			double height = rising ? phase + 0.017D * time : phase + 0.12D * Math.sin(time * 0.08D + phase * 8.0D);
			float level = rising ? (float) (height - Math.floor(height)) : Mth.clamp((float) height, 0.0F, 1.0F);
			float brightness = rising ? Math.min(1.0F, Math.min(level, 1.0F - level) * 9.0F) : 1.0F;
			float size = (float) (0.10D + 0.09D * ThreatFieldMotes.unit(seed, slot * 4 + 3));
			drawBillboard(pose, buffers, SHARD, (float) point.x, Mth.lerp(level, 0.3F, maxY),
					(float) point.z, size, slot * 47.0F + time * 0.68F, color, alpha * brightness * 0.8F);
		}
	}

	private static void drawBodyCrack(PoseStack pose, MultiBufferSource buffers,
			float height, float halfWidth, int color, float alpha) {
		var buffer = buffers.getBuffer(RenderType.entityTranslucentEmissive(CRACK));
		pose.pushPose();
		pose.translate(0.0D, 0.05D, 0.0D);
		for (int plane = 0; plane < 2; plane++) {
			var matrix = pose.last();
			vertex(buffer, matrix, -halfWidth, 0.0F, 0.0F, 0.0F, 1.0F, color, alpha);
			vertex(buffer, matrix, halfWidth, 0.0F, 0.0F, 1.0F, 1.0F, color, alpha);
			vertex(buffer, matrix, halfWidth, height, 0.0F, 1.0F, 0.0F, color, alpha);
			vertex(buffer, matrix, -halfWidth, height, 0.0F, 0.0F, 0.0F, color, alpha);
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
