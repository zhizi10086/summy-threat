package com.summy.threat.legendary;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 凋亡潮汐：四道低矮扩散波，可以跳跃规避。 */
final class DecayTideAction implements LegendaryActionHandler {
	private static final int WARNING_TICKS = 30;
	private static final int WAVE_INTERVAL = 15;
	private static final double WAVE_SPEED = 0.6D;
	private static final double MAX_RADIUS = 18.0D;

	@Override
	public LegendaryActionInstance start(LegendaryActionContext context) {
		if (context.target() == null || !context.target().isAlive()) {
			return null;
		}
		return new Instance(LegendaryActionCombat.groundProjection(context.owner(), 16.0D));
	}

	private static final class Instance implements LegendaryActionInstance {
		private final Vec3 center;
		private final List<Wave> waves = new ArrayList<>();

		private Instance(Vec3 center) {
			this.center = center;
		}

		@Override
		public boolean tick(LegendaryActionContext context, int age) {
			ServerLevel level = context.level();
			if (age < WARNING_TICKS) {
				LegendaryVisuals.ring(level, center, 3.0D,
						LegendaryActionId.DECAY_TIDE.color(), 28, 1.1F, 0.05D);
				if (age % 7 == 0) {
					LegendaryVisuals.sound(level, center, SoundEvents.WITHER_AMBIENT,
							0.6F, 0.65F + age * 0.01F);
				}
				return true;
			}
			int activeAge = age - WARNING_TICKS;
			for (int waveIndex = 0; waveIndex < 4; waveIndex++) {
				if (activeAge == waveIndex * WAVE_INTERVAL) {
					waves.add(new Wave(age));
				}
			}
			waves.removeIf(wave -> age - wave.startAge > MAX_RADIUS / WAVE_SPEED + 2);
			for (Wave wave : waves) {
				double radius = Math.min(MAX_RADIUS,
						(age - wave.startAge) * WAVE_SPEED);
				LegendaryVisuals.ring(level, center, radius,
						LegendaryActionId.DECAY_TIDE.color(), 64, 1.3F, 0.06D);
				double inner = Math.max(0.0D, radius - 1.2D);
				for (LivingEntity target : LegendaryActionCombat.defaultTargets(
						context, center, MAX_RADIUS, true)) {
					if (wave.hit.contains(target.getUUID())
							|| target.getY() > center.y + 0.8D
							|| !LegendaryActionCombat.hasWavePath(level, center, target)) {
						continue;
					}
					double distance = horizontalDistance(target.position(), center);
					if (distance >= inner && distance <= radius) {
						boolean landed = LegendaryActionCombat.hit(context, target,
								LegendaryActionCombat.DamageKind.INDIRECT_MAGIC, 70.0D);
						if (landed) {
							wave.hit.add(target.getUUID());
							target.addEffect(new MobEffectInstance(MobEffects.WITHER,
									80, 0, false, true, true));
						}
					}
				}
			}
			return age < LegendaryActionMath.waveEndExclusiveAge(
					WARNING_TICKS + 3 * WAVE_INTERVAL, MAX_RADIUS, WAVE_SPEED);
		}

		private static double horizontalDistance(Vec3 first, Vec3 second) {
			double dx = first.x - second.x;
			double dz = first.z - second.z;
			return Math.sqrt(dx * dx + dz * dz);
		}
	}

	private static final class Wave {
		private final int startAge;
		private final Set<UUID> hit = new HashSet<>();

		private Wave(int startAge) {
			this.startAge = startAge;
		}
	}
}
