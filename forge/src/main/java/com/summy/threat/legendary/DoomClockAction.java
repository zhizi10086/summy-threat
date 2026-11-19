package com.summy.threat.legendary;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** 终末轮盘：以目标方向为起点，顺时针引爆四个扇区。 */
final class DoomClockAction implements LegendaryActionHandler {
	private static final int WARNING_TICKS = 30;
	private static final int STEP_TICKS = 15;
	private static final int END_TICK = WARNING_TICKS + STEP_TICKS * 4;
	private static final double OUTER_RADIUS = 20.0D;

	static int detonationAt(int age) {
		int activeAge = age - WARNING_TICKS;
		return age < WARNING_TICKS || age >= END_TICK || activeAge % STEP_TICKS != 0
				? -1 : activeAge / STEP_TICKS;
	}

	/** 余闪结束后转到下一扇区。第四次引爆后不再预警。 */
	static Cue visualCue(int age) {
		int activeAge = age - WARNING_TICKS;
		if (activeAge >= 0) {
			int sector = activeAge / STEP_TICKS;
			int sinceBurst = activeAge % STEP_TICKS;
			if (sector < 4 && sinceBurst < 4) {
				return new Cue(sector, ThreatVisualEntity.IMPACT, 1.0F - sinceBurst * 0.14F);
			}
		}
		int next = activeAge < 0 ? 0 : Math.min(4, activeAge / STEP_TICKS + 1);
		if (next == 4) {
			return new Cue(3, ThreatVisualEntity.ACTIVE, 0.0F);
		}
		int remaining = WARNING_TICKS + STEP_TICKS * next - age;
		float intensity = remaining <= 5 ? (remaining % 2 == 1 ? 0.84F : 0.48F) : 0.32F;
		return new Cue(next, ThreatVisualEntity.WARNING, intensity);
	}

	record Cue(int sector, byte phase, float intensity) {}

	@Override
	public LegendaryActionInstance start(LegendaryActionContext context) {
		LivingEntity target = context.target();
		if (target == null || !target.isAlive()) {
			return null;
		}
		Vec3 center = LegendaryActionCombat.groundProjection(context.owner(), 20.0D);
		double baseAngle = angle(center, target.position());
		return new Instance(center, baseAngle, ThreatVisualEntity.spawnGround(context.level(),
				LegendaryActionId.DOOM_CLOCK, center, END_TICK + 20, (float) OUTER_RADIUS,
				(float) baseAngle, visualCue(0).intensity()));
	}

	private static final class Instance implements LegendaryActionInstance {
		private final Vec3 center;
		private final double baseAngle;
		@javax.annotation.Nullable
		private final ThreatVisualEntity visual;

		private Instance(Vec3 center, double baseAngle, @javax.annotation.Nullable ThreatVisualEntity visual) {
			this.center = center;
			this.baseAngle = baseAngle;
			this.visual = visual;
		}

		@Override
		public boolean tick(LegendaryActionContext context, int age) {
			if (age >= END_TICK) {
				return false;
			}
			if (visual != null && !visual.isRemoved()) {
				Cue cue = visualCue(age);
				float direction = (float) (baseAngle + LegendaryActionMath.clockwiseSectorOffset(cue.sector()));
				visual.update(cue.phase(), age / (float) END_TICK, (float) OUTER_RADIUS,
						direction, cue.intensity(), 0, Vec3.ZERO);
			}
			if (age < WARNING_TICKS && age % 6 == 0) {
				LegendaryVisuals.sound(context.level(), center, SoundEvents.BELL_BLOCK,
						0.55F, 0.7F + age * 0.012F);
			}
			int sector = detonationAt(age);
			if (sector >= 0) {
				detonate(context, sector);
			}
			return true;
		}

		@Override
		public void stop(LegendaryActionContext context, StopReason reason) {
			if (visual != null) {
				visual.finish(reason);
			}
		}

		private void detonate(LegendaryActionContext context, int sector) {
			ServerLevel level = context.level();
			for (LivingEntity target : LegendaryActionCombat.cylinderTargets(
					context, center, OUTER_RADIUS, 8.0D, 8.0D)) {
				double relative = angle(center, target.position()) - baseAngle;
				double normalized = Math.atan2(Math.sin(relative), Math.cos(relative));
				double sectorAngle = LegendaryActionMath.clockwiseSectorOffset(sector);
				if (angleDistance(normalized, sectorAngle) <= Math.PI / 4.0D) {
					LegendaryActionCombat.hit(context, target,
							LegendaryActionCombat.DamageKind.INDIRECT_MAGIC, 55.0D);
				}
			}
			LegendaryVisuals.sound(level, center, SoundEvents.LIGHTNING_BOLT_THUNDER,
					0.65F, 1.45F);
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
