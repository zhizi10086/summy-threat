package com.summy.threat.legendary;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/** 巨龙威仪：龙吼施加畏怖状态。 */
final class FrightfulPresenceAction implements LegendaryActionHandler {
	private static final int WARNING_TICKS = 35;

	@Override
	public LegendaryActionInstance start(LegendaryActionContext context) {
		return context.target() != null && context.target().isAlive()
				? new Instance() : null;
	}

	private static final class Instance implements LegendaryActionInstance {
		@Override
		public boolean tick(LegendaryActionContext context, int age) {
			ServerLevel level = context.level();
			int color = LegendaryVisuals.dragonColor(context.owner());
			if (age < WARNING_TICKS) {
				LegendaryVisuals.ring(level, context.owner().position(), 24.0D,
						color, 88,
						1.0F + age / 50.0F, 0.12D);
				level.sendParticles(LegendaryVisuals.endRod(),
						context.owner().getX(),
						context.owner().getY() + context.owner().getBbHeight() * 0.7D,
						context.owner().getZ(), 4, 1.2D, 0.8D, 1.2D, 0.01D);
				if (age % 7 == 0) {
					LegendaryVisuals.sound(level, context.center(),
							SoundEvents.ENDER_DRAGON_GROWL, 0.85F,
							0.62F + age * 0.012F);
				}
				return true;
			}
			if (age == WARNING_TICKS) {
				List<Player> affected = new ArrayList<>();
				for (Player target : LegendaryActionCombat.playerTargets(
						context, context.owner().position(), 24.0D)) {
					if (LegendaryActionCombat.hasLineOfSight(context.owner(), target)) {
						affected.add(target);
					}
				}
				FrightfulPresenceService.apply(context.owner(), affected,
						level.getGameTime());
				LegendaryVisuals.burst(level, context.center(),
						color, 90, 8.0D, 1.8F);
				LegendaryVisuals.sound(level, context.center(),
						SoundEvents.ENDER_DRAGON_GROWL, 1.0F, 0.55F);
			}
			return false;
		}
	}
}
