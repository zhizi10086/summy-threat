package com.summy.threat.legendary;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 监听监守者直接承伤并为回响判罪创建延迟落点。 */
public final class EchoSentenceService {
	private static final Map<UUID, EchoSentenceAction.Instance> ACTIVE =
			new HashMap<>();

	private EchoSentenceService() {
	}

	static void activate(UUID ownerId, EchoSentenceAction.Instance instance) {
		ACTIVE.put(ownerId, instance);
	}

	static void deactivate(UUID ownerId, EchoSentenceAction.Instance instance) {
		ACTIVE.remove(ownerId, instance);
	}

	static boolean active(LivingEntity owner) {
		return ACTIVE.containsKey(owner.getUUID());
	}

	static void onSettledDamage(LivingEntity owner, DamageSource source) {
		EchoSentenceAction.Instance instance = ACTIVE.get(owner.getUUID());
		net.minecraft.world.entity.Entity direct = source.getDirectEntity();
		if (instance == null
				|| !(source.getEntity() instanceof ServerPlayer player)
				|| !LegendaryActionRuntime.validPlayer(owner, player)
				|| !(direct == player || direct instanceof net.minecraft.world.entity.projectile.Projectile)) {
			return;
		}
		long now = player.level().getGameTime();
		if (instance.canTrigger(player.getUUID(), now)) {
			instance.trigger(player.getUUID(), player.position(), now);
		}
	}
}
