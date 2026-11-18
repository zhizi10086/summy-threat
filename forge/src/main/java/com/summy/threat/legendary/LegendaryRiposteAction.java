package com.summy.threat.legendary;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** 传奇反击：阈值触发后的固定中心冲击。 */
final class LegendaryRiposteAction implements LegendaryActionHandler {
	private static final int WARNING_TICKS = 20;
	private static final double RADIUS = 8.0D;

	@Override
	public LegendaryActionInstance start(LegendaryActionContext context) {
		return context.target() != null && context.target().isAlive()
				? new Instance(context.owner().position()) : null;
	}

	private static final class Instance implements LegendaryActionInstance {
		private final Vec3 center;
		private boolean resolved;

		private Instance(Vec3 center) {
			this.center = center;
		}

		@Override
		public boolean tick(LegendaryActionContext context, int age) {
			ServerLevel level = context.level();
			if (age < WARNING_TICKS) {
				LegendaryVisuals.ring(level, center, RADIUS,
						LegendaryActionId.LEGENDARY_RIPOSTE.color(), 44,
						1.0F + age / 30.0F, 0.07D);
				if (age % 5 == 0) {
					LegendaryVisuals.sound(level, center,
							SoundEvents.NOTE_BLOCK_PLING.value(), 0.5F,
							0.7F + age * 0.03F);
				}
				return true;
			}
			if (!resolved) {
				resolved = true;
				for (LivingEntity target : LegendaryActionCombat.defaultTargets(
						context, center, RADIUS, true)) {
					if (LegendaryActionCombat.hit(context, target,
							LegendaryActionCombat.DamageKind.INDIRECT_MAGIC, 110.0D)) {
						LegendaryActionCombat.push(target, center, 0.6D, 0.0D,
								"legendary_riposte:" + context.owner().getUUID() + ":"
										+ level.getGameTime(),
								level.getGameTime() + 20L);
					}
				}
				LegendaryVisuals.burst(level, center,
						LegendaryActionId.LEGENDARY_RIPOSTE.color(), 72, 8.0D, 1.8F);
				LegendaryVisuals.sound(level, center, SoundEvents.BELL_RESONATE,
						1.0F, 0.65F);
			}
			return age < WARNING_TICKS + 2;
		}
	}
}
