package com.summy.threat.legendary;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 逆重力场：先抬升、后恢复重力的可控坠落风险。 */
final class ReverseGravityAction implements LegendaryActionHandler {
	private static final int WARNING_TICKS = 30;
	private static final int LIFT_TICKS = 40;
	private static final int DURATION_TICKS = WARNING_TICKS + LIFT_TICKS;
	private static final double RADIUS = 12.0D;
	private static final double BELOW = 4.0D;
	private static final double ABOVE = 6.0D;
	static final float MOTE_HEIGHT = 5.5F;

	@Override
	public LegendaryActionInstance start(LegendaryActionContext context) {
		LivingEntity target = context.target();
		if (target == null || !target.isAlive()) {
			return null;
		}
		Vec3 center = target.position();
		ThreatVisualEntity visual = ThreatVisualEntity.spawnGround(context.level(),
				LegendaryActionId.REVERSE_GRAVITY, center, DURATION_TICKS + 20,
				(float) RADIUS, 0.0F, 0.0F);
		return new Instance(center, visual);
	}

	private static final class Instance implements LegendaryActionInstance {
		private final Vec3 center;
		private final Map<UUID, Double> startHeights = new HashMap<>();
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
				updateVisual(context, age, ThreatVisualEntity.WARNING, 0.0F);
				if (age % 7 == 0) {
					LegendaryVisuals.sound(level, center, SoundEvents.AMETHYST_BLOCK_RESONATE,
							0.55F, 0.8F + age * 0.012F);
				}
				return true;
			}
			int activeAge = age - WARNING_TICKS;
			if (activeAge >= LIFT_TICKS) {
				return false;
			}
			boolean fallingWarning = activeAge >= LIFT_TICKS - 10;
			for (LivingEntity target : LegendaryActionCombat.cylinderTargets(
					context, center, RADIUS, BELOW, ABOVE)) {
				double start = startHeights.computeIfAbsent(target.getUUID(),
						ignored -> target.getY());
				if (target.getY() >= start + 6.0D
						|| !clearAbove(level, target)) {
					continue;
				}
				Vec3 delta = target.getDeltaMovement();
				LegendaryActionCombat.applyVelocity(target,
						new Vec3(delta.x * 0.98D,
								LegendaryActionMath.reverseGravityLiftVelocity(),
								delta.z * 0.98D));
			}
			updateVisual(context, age, fallingWarning ? ThreatVisualEntity.IMPACT
					: ThreatVisualEntity.ACTIVE, fallingWarning ? 1.0F : 0.0F);
			if (fallingWarning && activeAge % 2 == 0) {
				LegendaryVisuals.sound(level, center,
						SoundEvents.NOTE_BLOCK_BASS.value(), 0.45F, 0.55F);
			}
			return true;
		}

		private void updateVisual(LegendaryActionContext context, int age, byte phase, float intensity) {
			if (visual == null || visual.isRemoved()) {
				return;
			}
			LivingEntity tracked = context.target();
			boolean visible = tracked != null && tracked.isAlive() && tracked.level() == context.level();
			Vec3 offset = visible ? tracked.position().subtract(center) : Vec3.ZERO;
			visible = visible && offset.horizontalDistanceSqr() <= RADIUS * RADIUS
					&& offset.y >= -BELOW && offset.y <= ABOVE;
			visual.update(phase, age / (float) DURATION_TICKS, (float) RADIUS, 0.0F,
					intensity, visible ? ThreatVisualEntity.LOCKED : 0, visible ? offset : Vec3.ZERO);
		}

		@Override
		public void stop(LegendaryActionContext context, StopReason reason) {
			if (visual != null) {
				visual.finish(reason);
			}
		}

		private static boolean clearAbove(ServerLevel level, LivingEntity target) {
			BlockPos feet = BlockPos.containing(target.position());
			for (int offset = 1; offset <= 2; offset++) {
				if (!level.isEmptyBlock(feet.above(offset))) {
					return false;
				}
			}
			return true;
		}
	}
}
