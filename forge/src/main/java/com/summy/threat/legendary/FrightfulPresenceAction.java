package com.summy.threat.legendary;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/** 巨龙威仪：龙吼施加畏怖状态。 */
final class FrightfulPresenceAction implements LegendaryActionHandler {
	private static final int WARNING_TICKS = 35;
	private static final float RADIUS = 24.0F;

	@Override
	public LegendaryActionInstance start(LegendaryActionContext context) {
		if (context.target() == null || !context.target().isAlive()) {
			return null;
		}
		ThreatVisualEntity visual = ThreatVisualEntity.spawnGround(context.level(),
				LegendaryActionId.FRIGHTFUL_PRESENCE, context.owner().position(),
				WARNING_TICKS + 1, RADIUS, 0.0F, 0.0F, LegendaryVisuals.dragonColor(context.owner()));
		return new Instance(visual);
	}

	private static List<Player> visiblePlayers(LegendaryActionContext context) {
		return LegendaryActionCombat.playerTargets(context, context.owner().position(), RADIUS).stream()
				.filter(player -> LegendaryActionCombat.hasLineOfSight(context.owner(), player))
				.toList();
	}

	private record Instance(@javax.annotation.Nullable ThreatVisualEntity visual) implements LegendaryActionInstance {
		@Override
		public boolean tick(LegendaryActionContext context, int age) {
			ServerLevel level = context.level();
			if (visual != null && !visual.isRemoved()) {
				Vec3 ground = LegendaryActionCombat.groundProjection(context.owner(), 48.0D);
				visual.setPos(ground.x, ground.y, ground.z);
				float sky = (float) (context.owner().getY() - ground.y) + context.owner().getBbHeight() + 6.0F;
				visual.update(age < WARNING_TICKS ? ThreatVisualEntity.WARNING : ThreatVisualEntity.IMPACT,
						age / (float) WARNING_TICKS, RADIUS, 0.0F, sky, 0, Vec3.ZERO);
				if (age % 5 == 0) {
					int[] ids = age == WARNING_TICKS ? new int[0]
							: visiblePlayers(context).stream().mapToInt(Entity::getId).sorted().toArray();
					visual.updateDetails(ids, new ListTag());
				}
			}
			if (age < WARNING_TICKS) {
				if (age % 7 == 0) {
					LegendaryVisuals.sound(level, context.center(),
							SoundEvents.ENDER_DRAGON_GROWL, 0.85F,
							0.62F + age * 0.012F);
				}
				return true;
			}
			if (age == WARNING_TICKS) {
				FrightfulPresenceService.apply(context.owner(), visiblePlayers(context),
						level.getGameTime(), context.test());
				LegendaryVisuals.sound(level, context.center(),
						SoundEvents.ENDER_DRAGON_GROWL, 1.0F, 0.55F);
			}
			return false;
		}

		@Override
		public void stop(LegendaryActionContext context, StopReason reason) {
			if (visual != null) {
				visual.finish(reason);
			}
		}
	}
}
