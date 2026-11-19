package com.summy.threat.legendary;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** 天穹收束：固定中心与持续缩小的安全圈。 */
final class CollapsingHorizonAction implements LegendaryActionHandler {
	private static final int WARNING_TICKS = 30;
	private static final int ACTIVE_TICKS = 120;
	private static final double OUTER_RADIUS = 20.0D;

	@Override
	public LegendaryActionInstance start(LegendaryActionContext context) {
		LivingEntity target = context.target();
		if (target == null || !target.isAlive()) {
			return null;
		}
		return new Instance(LegendaryActionCombat.groundProjection(target, 20.0D));
	}

	private static final class Instance implements LegendaryActionInstance {
		private final Vec3 center;

		private Instance(Vec3 center) {
			this.center = center;
		}

		@Override
		public boolean tick(LegendaryActionContext context, int age) {
			ServerLevel level = context.level();
			if (age < WARNING_TICKS) {
				LegendaryVisuals.ring(level, center, 10.0D,
						LegendaryActionId.COLLAPSING_HORIZON.color(), 44, 1.15F, 0.06D);
				LegendaryVisuals.ring(level, center, OUTER_RADIUS,
						LegendaryActionId.COLLAPSING_HORIZON.color(), 72, 0.85F, 0.06D);
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
			LegendaryVisuals.ring(level, center, safeRadius,
					LegendaryActionId.COLLAPSING_HORIZON.color(), 36, 1.2F, 0.06D);
			LegendaryVisuals.ring(level, center, OUTER_RADIUS,
					LegendaryActionId.COLLAPSING_HORIZON.color(), 72, 0.8F, 0.06D);
			if (activeAge % 10 == 0) {
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

		private static double horizontalDistance(Vec3 first, Vec3 second) {
			double dx = first.x - second.x;
			double dz = first.z - second.z;
			return Math.sqrt(dx * dx + dz * dz);
		}
	}
}
