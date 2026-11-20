package com.summy.threat.legendary;

import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** 龙威坠界：持续压制高空玩家。 */
final class DragonfallEdictAction implements LegendaryActionHandler {
	private static final int WARNING_TICKS = 35;
	private static final int ACTIVE_TICKS = 160;
	private static final float RADIUS = 48.0F;

	@Override
	public LegendaryActionInstance start(LegendaryActionContext context) {
		if (context.target() == null || !context.target().isAlive()) {
			return null;
		}
		return new Instance(ThreatVisualEntity.spawnGround(context.level(),
				LegendaryActionId.DRAGONFALL_EDICT, context.owner().position(),
				WARNING_TICKS + ACTIVE_TICKS + 1, RADIUS, 0.0F, 0.0F));
	}

	private static final class Instance implements LegendaryActionInstance {
		private final ThreatVisualEntity visual;

		private Instance(@javax.annotation.Nullable ThreatVisualEntity visual) {
			this.visual = visual;
		}

		@Override
		public boolean tick(LegendaryActionContext context, int age) {
			ServerLevel level = context.level();
			int activeAge = age - WARNING_TICKS;
			if (activeAge >= ACTIVE_TICKS) {
				return false;
			}
			boolean warning = activeAge < 0;
			boolean damageTick = !warning && activeAge % 20 == 0;
			if (warning) {
				if (age % 7 == 0) {
					LegendaryVisuals.sound(level, context.center(),
							SoundEvents.ENDER_DRAGON_GROWL, 0.8F, 0.72F + age * 0.01F);
				}
			}
			List<Integer> airborneIds = new ArrayList<>();
			for (LivingEntity target : LegendaryActionCombat.defaultTargets(
					context, context.owner().position(), RADIUS, true)) {
				double support = supportDistance(level, target);
				boolean airborne = target.isFallFlying() || support < 0.0D || support > 4.0D;
				if (!airborne) {
					continue;
				}
				airborneIds.add(target.getId());
				if (warning) {
					continue;
				}
				Vec3 delta = target.getDeltaMovement();
				if (support >= 0.0D && delta.y > -0.08D) {
					LegendaryActionCombat.applyVelocity(target,
							new Vec3(delta.x, -0.08D, delta.z));
				}
				if (damageTick) {
					LegendaryActionCombat.hit(context, target,
							LegendaryActionCombat.DamageKind.INDIRECT_MAGIC, 45.0D);
				}
			}
			if (damageTick) {
				LegendaryVisuals.sound(level, context.center(),
						SoundEvents.ENDER_DRAGON_FLAP, 1.0F, 0.65F);
			}
			if (visual != null && !visual.isRemoved()) {
				Vec3 center = context.owner().position();
				visual.setPos(center.x, center.y, center.z);
				float flash = warning ? 0.0F : Math.max(0.0F, 1.0F - (activeAge % 20) / 5.0F);
				visual.update(warning ? ThreatVisualEntity.WARNING : ThreatVisualEntity.ACTIVE,
						warning ? age / (float) WARNING_TICKS : activeAge / (float) ACTIVE_TICKS,
						RADIUS, 0.0F, flash, 0, Vec3.ZERO);
				visual.updateDetails(airborneIds.stream().mapToInt(Integer::intValue).sorted().toArray(), new ListTag());
			}
			return true;
		}

		@Override
		public void stop(LegendaryActionContext context, StopReason reason) {
			if (visual != null) {
				visual.finish(reason);
			}
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
