package com.summy.threat.legendary;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** 龙威坠界：持续压制高空玩家。 */
final class DragonfallEdictAction implements LegendaryActionHandler {
	private static final int WARNING_TICKS = 35;
	private static final int ACTIVE_TICKS = 160;

	@Override
	public LegendaryActionInstance start(LegendaryActionContext context) {
		return context.target() != null && context.target().isAlive()
				? new Instance() : null;
	}

	private static final class Instance implements LegendaryActionInstance {
		@Override
		public boolean tick(LegendaryActionContext context, int age) {
			ServerLevel level = context.level();
			if (age < WARNING_TICKS) {
				LegendaryVisuals.ring(level, context.owner().position(), 18.0D,
						LegendaryActionId.DRAGONFALL_EDICT.color(), 64, 1.2F, 0.08D);
				if (age % 7 == 0) {
					LegendaryVisuals.sound(level, context.center(),
							SoundEvents.ENDER_DRAGON_GROWL, 0.8F, 0.72F + age * 0.01F);
				}
				return true;
			}
			int activeAge = age - WARNING_TICKS;
			if (activeAge >= ACTIVE_TICKS) {
				return false;
			}
			boolean damageTick = activeAge % 20 == 0;
			for (LivingEntity target : LegendaryActionCombat.defaultTargets(
					context, context.owner().position(), 48.0D, true)) {
				double support = supportDistance(level, target);
				boolean airborne = target.isFallFlying() || support < 0.0D || support > 4.0D;
				if (!airborne) {
					continue;
				}
				Vec3 delta = target.getDeltaMovement();
				if (support >= 0.0D && delta.y > -0.08D) {
					LegendaryActionCombat.applyVelocity(target,
							new Vec3(delta.x, -0.08D, delta.z));
				}
				level.sendParticles(LegendaryVisuals.dust(
								LegendaryActionId.DRAGONFALL_EDICT.color(), 1.1F),
						target.getX(), target.getY() + target.getBbHeight() + 1.5D, target.getZ(),
						4, 0.25D, 0.1D, 0.25D, 0.01D);
				if (damageTick) {
					LegendaryActionCombat.hit(context, target,
							LegendaryActionCombat.DamageKind.INDIRECT_MAGIC, 45.0D);
				}
			}
			if (damageTick) {
				LegendaryVisuals.sound(level, context.center(),
						SoundEvents.ENDER_DRAGON_FLAP, 1.0F, 0.65F);
			}
			return true;
		}

		private static double supportDistance(ServerLevel level, LivingEntity target) {
			Vec3 start = target.position();
			Vec3 end = start.subtract(0.0D, 16.0D, 0.0D);
			var hit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
					ClipContext.Fluid.NONE, target));
			return hit.getType() == HitResult.Type.MISS ? -1.0D
					: start.distanceTo(hit.getLocation());
		}
	}
}
