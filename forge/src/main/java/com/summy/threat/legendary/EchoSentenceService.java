package com.summy.threat.legendary;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

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

	@SubscribeEvent(priority = EventPriority.NORMAL)
	public static void onDamage(LivingDamageEvent event) {
		EchoSentenceAction.Instance instance = ACTIVE.get(event.getEntity().getUUID());
		net.minecraft.world.entity.Entity direct = event.getSource().getDirectEntity();
		if (instance == null || event.getAmount() <= 0.0F
				|| !(event.getSource().getEntity() instanceof ServerPlayer player)
				|| !(direct == player || direct instanceof net.minecraft.world.entity.projectile.Projectile)) {
			return;
		}
		long now = player.level().getGameTime();
		if (instance.canTrigger(player.getUUID(), now)) {
			instance.trigger(player.getUUID(), player.position(), now);
		}
	}
}
