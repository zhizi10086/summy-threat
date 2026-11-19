package com.summy.threat.legendary;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 龙巢脉冲：四道带旋转安全缺口的元素环。 */
final class DragonLairPulseAction implements LegendaryActionHandler {
	private static final int WARNING_TICKS = 35;
	private static final int WAVE_INTERVAL = 15;
	private static final double SPEED = 0.6D;
	private static final double MAX_RADIUS = 18.0D;

	@Override
	public LegendaryActionInstance start(LegendaryActionContext context) {
		LivingEntity target = context.target();
		if (target == null || !target.isAlive()) {
			return null;
		}
		Vec3 center = LegendaryActionCombat.groundProjection(context.owner(), 20.0D);
		double baseAngle = Math.atan2(target.getZ() - center.z, target.getX() - center.x);
		return new Instance(center, baseAngle);
	}

	private static final class Instance implements LegendaryActionInstance {
		private final Vec3 center;
		private final double baseAngle;
		private final List<Wave> waves = new ArrayList<>();

		private Instance(Vec3 center, double baseAngle) {
			this.center = center;
			this.baseAngle = baseAngle;
		}

		@Override
		public boolean tick(LegendaryActionContext context, int age) {
			ServerLevel level = context.level();
			int color = LegendaryVisuals.dragonColor(context.owner());
			if (age < WARNING_TICKS) {
				LegendaryVisuals.ring(level, center, 5.0D,
						color, 36, 1.2F, 0.06D);
				if (age % 7 == 0) {
					LegendaryVisuals.sound(level, center, SoundEvents.ENDER_DRAGON_GROWL,
							0.75F, 0.65F + age * 0.01F);
				}
				return true;
			}
			int activeAge = age - WARNING_TICKS;
			for (int index = 0; index < 4; index++) {
				if (activeAge == index * WAVE_INTERVAL) {
					waves.add(new Wave(age, index));
				}
			}
			waves.removeIf(wave -> age - wave.startAge > MAX_RADIUS / SPEED + 2);
			for (Wave wave : waves) {
				double radius = Math.min(MAX_RADIUS, (age - wave.startAge) * SPEED);
				double gapCenter = baseAngle + Math.toRadians(wave.index * 90.0D);
				LegendaryVisuals.ringWithGap(level, center, radius,
						color, 90, 1.45F, 0.06D,
						gapCenter, Math.toRadians(30.0D));
				double inner = Math.max(0.0D, radius - 2.5D);
				for (LivingEntity target : LegendaryActionCombat.cylinderTargets(
					context, center, MAX_RADIUS, 4.0D, 4.0D)) {
					if (wave.hit.contains(target.getUUID())
							|| !LegendaryActionCombat.hasWavePath(level, center, target)) {
						continue;
					}
					double distance = horizontalDistance(target.position(), center);
					double angle = Math.atan2(target.getZ() - center.z,
							target.getX() - center.x);
					if (distance >= inner && distance <= radius
							&& LegendaryVisuals.angularDistance(angle, gapCenter)
									> Math.toRadians(30.0D)) {
						if (LegendaryActionCombat.hit(context, target,
								LegendaryActionCombat.DamageKind.INDIRECT_MAGIC, 70.0D)) {
							wave.hit.add(target.getUUID());
						}
					}
				}
			}
			return age < LegendaryActionMath.waveEndExclusiveAge(
					WARNING_TICKS + 3 * WAVE_INTERVAL, MAX_RADIUS, SPEED);
		}

		private static double horizontalDistance(Vec3 first, Vec3 second) {
			double dx = first.x - second.x;
			double dz = first.z - second.z;
			return Math.sqrt(dx * dx + dz * dz);
		}
	}

	private static final class Wave {
		private final int startAge;
		private final int index;
		private final Set<UUID> hit = new HashSet<>();

		private Wave(int startAge, int index) {
			this.startAge = startAge;
			this.index = index;
		}
	}
}
