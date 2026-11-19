package com.summy.threat.legendary;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** 终末轮盘：以目标方向为起点，顺时针引爆四个扇区。 */
final class DoomClockAction implements LegendaryActionHandler {
	private static final int WARNING_TICKS = 30;
	private static final int STEP_TICKS = 15;

	@Override
	public LegendaryActionInstance start(LegendaryActionContext context) {
		LivingEntity target = context.target();
		if (target == null || !target.isAlive()) {
			return null;
		}
		Vec3 center = LegendaryActionCombat.groundProjection(context.owner(), 20.0D);
		double baseAngle = angle(center, target.position());
		return new Instance(center, baseAngle);
	}

	private static final class Instance implements LegendaryActionInstance {
		private final Vec3 center;
		private final double baseAngle;
		private int lastPulse = -1;

		private Instance(Vec3 center, double baseAngle) {
			this.center = center;
			this.baseAngle = baseAngle;
		}

		@Override
		public boolean tick(LegendaryActionContext context, int age) {
			ServerLevel level = context.level();
			if (age < WARNING_TICKS) {
				LegendaryVisuals.ring(level, center, 20.0D,
						LegendaryActionId.DOOM_CLOCK.color(), 80, 1.1F, 0.06D);
				for (int sector = 0; sector < 4; sector++) {
					drawBoundary(level, sector);
				}
				if (age % 6 == 0) {
					LegendaryVisuals.sound(level, center, SoundEvents.BELL_BLOCK,
							0.55F, 0.7F + age * 0.012F);
				}
				return true;
			}
			int activeAge = age - WARNING_TICKS;
			if (activeAge >= STEP_TICKS * 4) {
				return false;
			}
			int pulse = activeAge / STEP_TICKS;
			int within = activeAge % STEP_TICKS;
			int activeSector = pulse;
			if (within >= STEP_TICKS - 5) {
				drawSector(level, activeSector, 1.5F, 0.06D);
			}
			if (within == 0 && pulse != lastPulse) {
				lastPulse = pulse;
				detonate(context, pulse);
			}
			return true;
		}

		private void detonate(LegendaryActionContext context, int sector) {
			ServerLevel level = context.level();
			for (LivingEntity target : LegendaryActionCombat.cylinderTargets(
					context, center, 20.0D, 8.0D, 8.0D)) {
				double relative = angle(center, target.position()) - baseAngle;
				double normalized = Math.atan2(Math.sin(relative), Math.cos(relative));
				double sectorAngle = LegendaryActionMath.clockwiseSectorOffset(sector);
				if (angleDistance(normalized, sectorAngle) <= Math.PI / 4.0D) {
					LegendaryActionCombat.hit(context, target,
							LegendaryActionCombat.DamageKind.INDIRECT_MAGIC, 55.0D);
				}
			}
			drawSector(level, sector, 1.8F, 0.1D);
			LegendaryVisuals.sound(level, center, SoundEvents.LIGHTNING_BOLT_THUNDER,
					0.65F, 1.45F);
		}

		private void drawSector(ServerLevel level, int sector, float scale,
				double yOffset) {
			double centerAngle = baseAngle
					+ LegendaryActionMath.clockwiseSectorOffset(sector);
			for (double radius = 2.0D; radius <= 20.0D; radius += 2.0D) {
				for (int offset = -3; offset <= 3; offset++) {
					double angle = centerAngle + offset * Math.PI / 24.0D;
					level.sendParticles(LegendaryVisuals.dust(
									LegendaryActionId.DOOM_CLOCK.color(), scale),
							center.x + Math.cos(angle) * radius,
							center.y + yOffset,
							center.z + Math.sin(angle) * radius,
							1, 0.0D, 0.0D, 0.0D, 0.0D);
				}
			}
		}

		private void drawBoundary(ServerLevel level, int sector) {
			double angle = baseAngle
					+ LegendaryActionMath.clockwiseSectorOffset(sector) - Math.PI / 4.0D;
			Vec3 end = center.add(Math.cos(angle) * 20.0D, 0.0D,
					Math.sin(angle) * 20.0D);
			LegendaryVisuals.line(level, center.add(0.0D, 0.06D, 0.0D), end,
					LegendaryActionId.DOOM_CLOCK.color(), 0.8D, 1.0F);
		}
	}

	private static double angle(Vec3 center, Vec3 point) {
		return Math.atan2(point.z - center.z, point.x - center.x);
	}

	private static double angleDistance(double first, double second) {
		double delta = first - second;
		return Math.abs(Math.atan2(Math.sin(delta), Math.cos(delta)));
	}
}
