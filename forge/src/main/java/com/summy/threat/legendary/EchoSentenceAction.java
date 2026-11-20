package com.summy.threat.legendary;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** 回响判罪：玩家直接攻击后在原地留下延迟声波。 */
final class EchoSentenceAction implements LegendaryActionHandler {
	private static final int WARNING_TICKS = 35;
	private static final int ACCEPT_END = 185;
	private static final int END_TICKS = 195;
	static final int ECHO_DELAY = 10;
	static final int ECHO_FLASH = 8;
	static final float ECHO_RADIUS = 2.5F;

	@Override
	public LegendaryActionInstance start(LegendaryActionContext context) {
		if (context.target() == null || !context.target().isAlive()) {
			return null;
		}
		ThreatVisualEntity visual = ThreatVisualEntity.spawnGround(context.level(),
				LegendaryActionId.ECHO_SENTENCE, context.owner().position(),
				END_TICKS + 1, 8.0F, 0.0F, 0.0F);
		Instance instance = new Instance(context.owner().getUUID(), visual);
		EchoSentenceService.activate(context.owner().getUUID(), instance);
		return instance;
	}

	static final class Instance implements LegendaryActionInstance {
		private final UUID ownerId;
		private final ThreatVisualEntity visual;
		private final List<PendingEcho> impacts = new ArrayList<>();
		private int[] listeners = new int[0];
		private final Map<UUID, Long> nextTrigger = new HashMap<>();
		private final Map<UUID, PendingEcho> pending = new HashMap<>();
		private int age;
		private boolean active = true;

		private Instance(UUID ownerId, @javax.annotation.Nullable ThreatVisualEntity visual) {
			this.ownerId = ownerId;
			this.visual = visual;
		}

		@Override
		public boolean tick(LegendaryActionContext context, int age) {
			this.age = age;
			ServerLevel level = context.level();
			if (age < WARNING_TICKS) {
				if (age % 7 == 0) {
					LegendaryVisuals.sound(level, context.center(),
							SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.8F,
							0.75F + age * 0.01F);
				}
			}
			if (age >= WARNING_TICKS && !pending.isEmpty()) {
				Iterator<Map.Entry<UUID, PendingEcho>> iterator = pending.entrySet().iterator();
				while (iterator.hasNext()) {
					PendingEcho echo = iterator.next().getValue();
					if (level.getGameTime() < echo.dueAt) {
						continue;
					}
					iterator.remove();
					resolve(context, echo);
					if (visual != null) {
						impacts.add(new PendingEcho(echo.position, level.getGameTime()));
					}
				}
			}
			updateVisual(context, age, level.getGameTime());
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
			pending.put(playerId, new PendingEcho(position, now + ECHO_DELAY));
			nextTrigger.put(playerId, now + 30L);
		}

		private void resolve(LegendaryActionContext context, PendingEcho echo) {
			ServerLevel level = context.level();
			for (LivingEntity target : LegendaryActionCombat.cylinderTargets(
					context, echo.position, ECHO_RADIUS, 2.0D, 2.0D)) {
				LegendaryActionCombat.hit(context, target,
						LegendaryActionCombat.DamageKind.SONIC_BOOM, 65.0D);
			}
			LegendaryVisuals.sound(level, echo.position,
					SoundEvents.WARDEN_SONIC_BOOM, 0.85F, 1.1F);
		}

		private void updateVisual(LegendaryActionContext context, int age, long now) {
			if (visual == null || visual.isRemoved()) {
				return;
			}
			Vec3 owner = context.owner().position();
			visual.setPos(owner.x, owner.y, owner.z);
			visual.update(age < WARNING_TICKS ? ThreatVisualEntity.WARNING : ThreatVisualEntity.ACTIVE,
					age / (float) END_TICKS, 8.0F, 0.0F, 0.0F, 0, Vec3.ZERO);

			// 声纹提示只标记附近的战斗玩家，触发回响仍以真实承伤为准。
			if (age >= ACCEPT_END) {
				listeners = new int[0];
			} else if (age % 5 == 0) {
				listeners = LegendaryActionCombat.playerTargets(context, owner, 48.0D).stream()
						.mapToInt(Entity::getId).sorted().toArray();
			}
			impacts.removeIf(echo -> now - echo.dueAt >= ECHO_FLASH);
			ListTag points = new ListTag();
			pending.values().forEach(echo -> points.add(echo.toVisual(false)));
			impacts.forEach(echo -> points.add(echo.toVisual(true)));
			visual.updateDetails(listeners, points);
		}

		@Override
		public void stop(LegendaryActionContext context, StopReason reason) {
			active = false;
			pending.clear();
			nextTrigger.clear();
			EchoSentenceService.deactivate(ownerId, this);
			impacts.clear();
			if (visual != null) {
				visual.finish(reason);
			}
		}
	}

	static Vec3 echoPosition(CompoundTag tag) {
		return new Vec3(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"));
	}

	private record PendingEcho(Vec3 position, long dueAt) {
		private CompoundTag toVisual(boolean impact) {
			CompoundTag tag = new CompoundTag();
			tag.putDouble("x", position.x);
			tag.putDouble("y", position.y);
			tag.putDouble("z", position.z);
			tag.putLong("tick", dueAt);
			tag.putBoolean("impact", impact);
			return tag;
		}
	}
}
