package com.summy.threat.legendary;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** 天穹收束：固定中心与持续缩小的安全圈。 */
final class CollapsingHorizonAction implements LegendaryActionHandler {
	private static final int WARNING_TICKS = 30;
	private static final int ACTIVE_TICKS = 120;
	static final double OUTER_RADIUS = 20.0D;

	@Override
	public LegendaryActionInstance start(LegendaryActionContext context) {
		LivingEntity target = context.target();
		if (target == null || !target.isAlive()) {
			return null;
		}
		Vec3 center = LegendaryActionCombat.groundProjection(target, 20.0D);
		return new Instance(center, ThreatVisualEntity.spawnGround(context.level(),
				LegendaryActionId.COLLAPSING_HORIZON, center, WARNING_TICKS + ACTIVE_TICKS + 20,
				10.0F, 0.0F, 0.0F));
	}

	private static final class Instance implements LegendaryActionInstance {
		private final Vec3 center;

		@javax.annotation.Nullable
		private final ThreatVisualEntity visual;

		private Instance(Vec3 center, @javax.annotation.Nullable ThreatVisualEntity visual) {
			this.center = center;
			this.visual = visual;
		}

		@Override
		public boolean tick(LegendaryActionContext context, int age) {
			ServerLevel level = context.level();
			if (age < WARNING_TICKS) {
				if (age % 7 == 0) {
					LegendaryVisuals.sound(level, center,
							SoundEvents.BEACON_AMBIENT, 0.5F, 0.55F + age * 0.01F);
				}
				return true;
			}
			int activeAge = age - WARNING_TICKS;
			if (activeAge >= ACTIVE_TICKS) {
				return false;
			}
			double safeRadius = 10.0D - 7.0D * activeAge / ACTIVE_TICKS;
			int pulseAge = activeAge % 10;
			if (visual != null && !visual.isRemoved()) {
				float flash = Math.max(0.0F, 1.0F - pulseAge / 4.0F);
				visual.update(pulseAge == 0 ? ThreatVisualEntity.IMPACT : ThreatVisualEntity.ACTIVE,
						activeAge / (float) ACTIVE_TICKS, (float) safeRadius, 0.0F, flash, 0, Vec3.ZERO);
			}
			if (pulseAge == 0) {
				for (LivingEntity target : LegendaryActionCombat.cylinderTargets(
					context, center, OUTER_RADIUS, 8.0D, 8.0D)) {
					double distance = horizontalDistance(target.position(), center);
					if (distance > safeRadius) {
						LegendaryActionCombat.hit(context, target,
								LegendaryActionCombat.DamageKind.INDIRECT_MAGIC, 45.0D);
					}
				}
				LegendaryVisuals.sound(level, center, SoundEvents.BEACON_POWER_SELECT,
						0.7F, 0.55F);
			}
			return true;
		}

		@Override
		public void stop(LegendaryActionContext context, StopReason reason) {
			if (visual != null) {
				visual.finish(reason);
			}
		}

		private static double horizontalDistance(Vec3 first, Vec3 second) {
			double dx = first.x - second.x;
			double dz = first.z - second.z;
			return Math.sqrt(dx * dx + dz * dz);
		}
	}
}
