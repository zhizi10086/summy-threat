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
	private static final LegendaryWavePattern PATTERN = LegendaryWavePattern.DECAY;

	@Override
	public LegendaryActionInstance start(LegendaryActionContext context) {
		if (context.target() == null || !context.target().isAlive()) {
			return null;
		}
		Vec3 center = LegendaryActionCombat.groundProjection(context.owner(), 16.0D);
		ThreatVisualEntity visual = ThreatVisualEntity.spawnGround(context.level(),
				LegendaryActionId.DECAY_TIDE, center, PATTERN.endAge() + 20,
				(float) LegendaryWavePattern.MAX_RADIUS, 0.0F, 0.0F);
		return new Instance(center, visual);
	}

	private static final class Instance implements LegendaryActionInstance {
		private final Vec3 center;
		private final List<Wave> waves = new ArrayList<>();

		@javax.annotation.Nullable
		private final ThreatVisualEntity visual;

		private Instance(Vec3 center, @javax.annotation.Nullable ThreatVisualEntity visual) {
			this.center = center;
			this.visual = visual;
		}

		@Override
		public boolean tick(LegendaryActionContext context, int age) {
			if (age >= PATTERN.endAge()) {
				return false;
			}
			ServerLevel level = context.level();
			if (visual != null && !visual.isRemoved()) {
				byte phase = age < PATTERN.warningTicks ? ThreatVisualEntity.WARNING : ThreatVisualEntity.ACTIVE;
				visual.update(phase, age / (float) PATTERN.endAge(), (float) LegendaryWavePattern.MAX_RADIUS,
						0.0F, 0.0F, 0, Vec3.ZERO);
			}
			if (age < PATTERN.warningTicks) {
				if (age % 7 == 0) {
					LegendaryVisuals.sound(level, center, SoundEvents.WITHER_AMBIENT,
							0.6F, 0.65F + age * 0.01F);
				}
				return true;
			}
			for (int index = 0; index < LegendaryWavePattern.COUNT; index++) {
				if (PATTERN.waveAge(age, index) == 0) {
					waves.add(new Wave(index));
				}
			}
			waves.removeIf(wave -> !PATTERN.isPresent(PATTERN.waveAge(age, wave.index)));
			for (Wave wave : waves) {
				double radius = PATTERN.radiusAt(PATTERN.waveAge(age, wave.index));
				double inner = Math.max(0.0D, radius - PATTERN.width);
				for (LivingEntity target : LegendaryActionCombat.cylinderTargets(
					context, center, LegendaryWavePattern.MAX_RADIUS, 18.0D, 0.8D)) {
					if (wave.hit.contains(target.getUUID())
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

	private static final class Wave {
		private final int index;
		private final Set<UUID> hit = new HashSet<>();

		private Wave(int index) {
			this.index = index;
		}
	}
}
