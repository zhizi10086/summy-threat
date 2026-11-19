package com.summy.threat.legendary;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 传奇反击的 40 tick 受击阈值统计。 */
public final class LegendaryRiposteService {
	private static final int WINDOW_TICKS = 40;
	private static final Map<UUID, Deque<DamageEntry>> DAMAGE = new HashMap<>();

	private LegendaryRiposteService() {
	}

	static void onSettledDamage(LivingEntity owner, DamageSource source, float amount) {
		if (amount <= 0.0F || !(source.getEntity() instanceof ServerPlayer player)
				|| LegendaryActionState.selection(owner) != LegendaryActionId.LEGENDARY_RIPOSTE) {
			return;
		}
		long now = owner.level().getGameTime();
		if (!LegendaryActionRuntime.validPlayer(owner, player)
				|| !LegendaryActionRuntime.canStartReactive(owner, LegendaryActionId.LEGENDARY_RIPOSTE, now)) {
			DAMAGE.remove(owner.getUUID());
			return;
		}
		Deque<DamageEntry> window = DAMAGE.computeIfAbsent(owner.getUUID(),
				ignored -> new ArrayDeque<>());
		while (!window.isEmpty() && now - window.peekFirst().tick > WINDOW_TICKS) {
			window.removeFirst();
		}
		window.addLast(new DamageEntry(now, amount));
		double total = window.stream().mapToDouble(DamageEntry::amount).sum();
		if (total >= owner.getMaxHealth() * 0.12D
				&& LegendaryActionRuntime.startReactive(owner, player,
						LegendaryActionId.LEGENDARY_RIPOSTE, now)) {
			window.clear();
		}
	}

	static void clearOwner(UUID ownerId) {
		DAMAGE.remove(ownerId);
	}

	static void clearAll() {
		DAMAGE.clear();
	}

	private record DamageEntry(long tick, float amount) {
	}
}
