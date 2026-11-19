package com.summy.threat.legendary;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** 回响判罪：玩家直接攻击后在原地留下延迟声波。 */
final class EchoSentenceAction implements LegendaryActionHandler {
	private static final int WARNING_TICKS = 35;
	private static final int ACCEPT_END = 185;
	private static final int END_TICKS = 195;

	@Override
	public LegendaryActionInstance start(LegendaryActionContext context) {
		if (context.target() == null || !context.target().isAlive()) {
			return null;
		}
		Instance instance = new Instance(context.owner().getUUID());
		EchoSentenceService.activate(context.owner().getUUID(), instance);
		return instance;
	}

	static final class Instance implements LegendaryActionInstance {
		private final UUID ownerId;
		private final Map<UUID, Long> nextTrigger = new HashMap<>();
		private final Map<UUID, PendingEcho> pending = new HashMap<>();
		private int age;
		private boolean active = true;

		private Instance(UUID ownerId) {
			this.ownerId = ownerId;
		}

		@Override
		public boolean tick(LegendaryActionContext context, int age) {
			this.age = age;
			ServerLevel level = context.level();
			if (age < WARNING_TICKS) {
				LegendaryVisuals.ring(level, context.owner().position(), 8.0D,
						LegendaryActionId.ECHO_SENTENCE.color(), 36,
						1.0F + age / 70.0F, 0.08D);
				if (age % 7 == 0) {
					LegendaryVisuals.sound(level, context.center(),
							SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.8F,
							0.75F + age * 0.01F);
				}
			}
			if (age >= WARNING_TICKS && !pending.isEmpty()) {
				for (PendingEcho echo : pending.values()) {
					if (age % 2 == 0) {
						LegendaryVisuals.ring(level, echo.position, 2.5D,
								LegendaryActionId.ECHO_SENTENCE.color(), 30,
								1.2F, 0.08D);
					}
					level.sendParticles(LegendaryVisuals.endRod(),
							echo.position.x, echo.position.y + 0.35D, echo.position.z,
							1, 0.08D, 0.2D, 0.08D, 0.0D);
				}
				Iterator<Map.Entry<UUID, PendingEcho>> iterator = pending.entrySet().iterator();
				while (iterator.hasNext()) {
					PendingEcho echo = iterator.next().getValue();
					if (level.getGameTime() < echo.dueAt) {
						continue;
					}
					iterator.remove();
					resolve(context, echo);
				}
			}
			if (age >= END_TICKS) {
				active = false;
				return false;
			}
			return true;
		}

		boolean canTrigger(UUID playerId, long now) {
			return active && age >= WARNING_TICKS && age < ACCEPT_END
					&& !pending.containsKey(playerId)
					&& now >= nextTrigger.getOrDefault(playerId, 0L);
		}

		void trigger(UUID playerId, Vec3 position, long now) {
			pending.put(playerId, new PendingEcho(position, now + 10L));
			nextTrigger.put(playerId, now + 30L);
		}

		private void resolve(LegendaryActionContext context, PendingEcho echo) {
			ServerLevel level = context.level();
			for (LivingEntity target : LegendaryActionCombat.cylinderTargets(
					context, echo.position, 2.5D, 2.0D, 2.0D)) {
				LegendaryActionCombat.hit(context, target,
						LegendaryActionCombat.DamageKind.SONIC_BOOM, 65.0D);
			}
			LegendaryVisuals.ring(level, echo.position, 2.5D,
					LegendaryActionId.ECHO_SENTENCE.color(), 30, 1.7F, 0.08D);
			LegendaryVisuals.sound(level, echo.position,
					SoundEvents.WARDEN_SONIC_BOOM, 0.85F, 1.1F);
		}

		@Override
		public void stop(LegendaryActionContext context, StopReason reason) {
			active = false;
			pending.clear();
			nextTrigger.clear();
			EchoSentenceService.deactivate(ownerId, this);
		}
	}

	private record PendingEcho(Vec3 position, long dueAt) {
	}
}
