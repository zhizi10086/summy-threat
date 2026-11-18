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

	@Override
	public LegendaryActionInstance start(LegendaryActionContext context) {
		LivingEntity target = context.target();
		if (target == null || !target.isAlive()) {
			return null;
		}
		return new Instance(target.position());
	}

	private static final class Instance implements LegendaryActionInstance {
		private final Vec3 center;
		private final Map<UUID, Double> startHeights = new HashMap<>();

		private Instance(Vec3 center) {
			this.center = center;
		}

		@Override
		public boolean tick(LegendaryActionContext context, int age) {
			ServerLevel level = context.level();
			if (age < WARNING_TICKS) {
				LegendaryVisuals.ring(level, center, 12.0D,
						LegendaryActionId.REVERSE_GRAVITY.color(), 48, 1.1F, 0.05D);
				level.sendParticles(LegendaryVisuals.endRod(),
						center.x, center.y + 0.3D, center.z,
						5, 2.5D, 0.2D, 2.5D, 0.01D);
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
			for (LivingEntity target : LegendaryActionCombat.defaultTargets(
					context, center, 12.0D, true)) {
				if (Math.abs(target.getY() - center.y) > 4.0D) {
					continue;
				}
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
				level.sendParticles(LegendaryVisuals.dust(
								LegendaryActionId.REVERSE_GRAVITY.color(),
								fallingWarning ? 1.5F : 1.0F),
						target.getX(), target.getY() + 0.2D, target.getZ(),
						2, 0.2D, 0.05D, 0.2D, 0.01D);
			}
			LegendaryVisuals.ring(level, center, 12.0D,
					LegendaryActionId.REVERSE_GRAVITY.color(), 48,
					fallingWarning ? 1.5F : 1.0F, 0.05D);
			if (fallingWarning && activeAge % 2 == 0) {
				LegendaryVisuals.sound(level, center,
						SoundEvents.NOTE_BLOCK_BASS.value(), 0.45F, 0.55F);
			}
			return true;
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
