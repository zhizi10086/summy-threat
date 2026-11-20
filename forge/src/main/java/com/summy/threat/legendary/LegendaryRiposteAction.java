package com.summy.threat.legendary;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** 传奇反击：阈值触发后的固定中心冲击。 */
final class LegendaryRiposteAction implements LegendaryActionHandler {
	private static final int WARNING_TICKS = 20;
	private static final double RADIUS = 8.0D;
	private static final int DURATION_TICKS = WARNING_TICKS + 2;

	static float chargeProgress(int age) {
		return Math.max(0.0F, Math.min(1.0F, age / (float) WARNING_TICKS));
	}

	@Override
	public LegendaryActionInstance start(LegendaryActionContext context) {
		if (context.target() == null || !context.target().isAlive()) {
			return null;
		}
		Vec3 center = context.owner().position();
		ThreatVisualEntity visual = ThreatVisualEntity.spawnGround(context.level(),
				LegendaryActionId.LEGENDARY_RIPOSTE, center, DURATION_TICKS + 20,
				(float) RADIUS, 0.0F, 0.0F);
		return new Instance(center, visual);
	}

	private static final class Instance implements LegendaryActionInstance {
		private final Vec3 center;
		@javax.annotation.Nullable
		private final ThreatVisualEntity visual;
		private boolean resolved;

		private Instance(Vec3 center, @javax.annotation.Nullable ThreatVisualEntity visual) {
			this.center = center;
			this.visual = visual;
		}

		@Override
		public boolean tick(LegendaryActionContext context, int age) {
			ServerLevel level = context.level();
			byte phase = age < WARNING_TICKS ? ThreatVisualEntity.WARNING : ThreatVisualEntity.IMPACT;
			updateVisual(context, phase, chargeProgress(age));
			if (age < WARNING_TICKS) {
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

		private void updateVisual(LegendaryActionContext context, byte phase, float charge) {
			if (visual != null && !visual.isRemoved()) {
				// 裂纹跟随施法者，伤害和范围环继续以触发点为中心。
				visual.update(phase, charge, (float) RADIUS, 0.0F, charge, 0,
						context.owner().position().subtract(center));
			}
		}

		@Override
		public void stop(LegendaryActionContext context, StopReason reason) {
			if (visual != null) {
				visual.finish(reason);
			}
		}
	}
}
