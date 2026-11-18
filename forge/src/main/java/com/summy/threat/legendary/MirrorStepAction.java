package com.summy.threat.legendary;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** 镜界折跃：延迟折跃至目标附近并释放落点冲击。 */
final class MirrorStepAction implements LegendaryActionHandler {
	private static final int WARNING_TICKS = 30;
	private static final int ARRIVAL_WARNING_TICKS = 10;

	@Override
	public LegendaryActionInstance start(LegendaryActionContext context) {
		LivingEntity target = context.target();
		if (target == null || !target.isAlive()
				|| (!context.test() && target.distanceTo(context.owner()) > 24.0D)) {
			return null;
		}
		return new Instance(target.getUUID());
	}

	private static final class Instance implements LegendaryActionInstance {
		private final java.util.UUID targetId;
		private Vec3 destination;
		private boolean exploded;

		private Instance(java.util.UUID targetId) {
			this.targetId = targetId;
		}

		@Override
		public boolean tick(LegendaryActionContext context, int age) {
			ServerLevel level = context.level();
			LivingEntity target = level.getEntity(targetId) instanceof LivingEntity living
					? living : null;
			if (age < WARNING_TICKS) {
				Vec3 shown = target == null ? context.target().position() : target.position();
				LegendaryVisuals.ring(level, shown, 1.2D,
						LegendaryActionId.MIRROR_STEP.color(), 18, 1.0F, 0.05D);
				if (age % 6 == 0) {
					LegendaryVisuals.sound(level, shown, SoundEvents.AMETHYST_BLOCK_CHIME,
							0.6F, 0.8F + age * 0.02F);
				}
				return true;
			}
			if (age == WARNING_TICKS) {
				if (target == null) {
					return false;
				}
				destination = safeDestination(context.owner(), target);
				if (destination == null) {
					destination = context.owner().position();
				}
				context.owner().teleportTo(destination.x, destination.y, destination.z);
				LegendaryVisuals.burst(level, destination,
						LegendaryActionId.MIRROR_STEP.color(), 36, 1.0D, 1.5F);
				LegendaryVisuals.sound(level, destination,
						SoundEvents.ENDERMAN_TELEPORT, 0.9F, 1.25F);
			}
			Vec3 center = destination == null ? context.owner().position() : destination;
			LegendaryVisuals.ring(level, center, 5.0D,
					LegendaryActionId.MIRROR_STEP.color(), 32, 1.2F, 0.05D);
			if (!exploded && age >= WARNING_TICKS + ARRIVAL_WARNING_TICKS) {
				exploded = true;
				List<LivingEntity> targets = LegendaryActionCombat.defaultTargets(
						context, center, 5.0D, true);
				for (LivingEntity hit : targets) {
					if (LegendaryActionCombat.hit(context, hit,
							LegendaryActionCombat.DamageKind.MOB_ATTACK, 130.0D)) {
						LegendaryActionCombat.push(hit, center, 0.8D, 0.25D,
								"legendary_mirror_step:" + context.owner().getUUID() + ":" + age,
								level.getGameTime() + 20L);
					}
				}
				LegendaryVisuals.burst(level, center,
						LegendaryActionId.MIRROR_STEP.color(), 56, 5.0D, 1.6F);
				LegendaryVisuals.sound(level, center, SoundEvents.GLASS_BREAK, 1.0F, 0.75F);
			}
			return age < WARNING_TICKS + ARRIVAL_WARNING_TICKS + 2;
		}
	}

	private static Vec3 safeDestination(LivingEntity owner, LivingEntity target) {
		Vec3 look = target.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0.0D, look.z);
		if (flat.lengthSqr() < 1.0E-6D) {
			flat = new Vec3(0.0D, 0.0D, 1.0D);
		} else {
			flat = flat.normalize();
		}
		double[] angles = {0.0D, 45.0D, -45.0D, 90.0D, -90.0D, 135.0D, -135.0D, 180.0D};
		for (double distance : new double[]{6.0D, 5.0D, 4.0D}) {
			for (double degrees : angles) {
				double radians = Math.toRadians(degrees);
				double cos = Math.cos(radians);
				double sin = Math.sin(radians);
				Vec3 back = new Vec3(-flat.x, 0.0D, -flat.z);
				Vec3 direction = new Vec3(
						back.x * cos - back.z * sin, 0.0D,
						back.x * sin + back.z * cos);
				Vec3 candidate = target.position().add(direction.scale(distance));
				AABB moved = owner.getBoundingBox().move(candidate.subtract(owner.position()));
				if (owner.level().noCollision(owner, moved)
						&& target.level().getFluidState(net.minecraft.core.BlockPos.containing(candidate))
								.isEmpty()
						&& candidate.y > target.level().getMinBuildHeight()) {
					return candidate;
				}
			}
		}
		return null;
	}
}
