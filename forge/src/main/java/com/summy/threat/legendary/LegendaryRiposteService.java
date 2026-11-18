package com.summy.threat.legendary;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 传奇反击的 40 tick 受击阈值统计。 */
public final class LegendaryRiposteService {
	private static final int WINDOW_TICKS = 40;
	private static final Map<UUID, Deque<DamageEntry>> DAMAGE = new HashMap<>();
	private static final ThreadLocal<Deque<PendingHit>> PENDING =
			ThreadLocal.withInitial(ArrayDeque::new);

	private LegendaryRiposteService() {
	}

	public static float capturePreAbsorptionDamage(LivingEntity owner,
			DamageSource source, float amount) {
		if (amount <= 0.0F
				|| !(source.getEntity() instanceof ServerPlayer player)
				|| LegendaryActionState.selection(owner) != LegendaryActionId.LEGENDARY_RIPOSTE) {
			return amount;
		}
		long now = player.level().getGameTime();
		if (LegendaryActionRuntime.canStartReactive(owner,
				LegendaryActionId.LEGENDARY_RIPOSTE, now)) {
			PENDING.get().addLast(new PendingHit(owner.getUUID(), source,
					player.getUUID(), amount, now));
		} else {
			DAMAGE.remove(owner.getUUID());
		}
		return amount;
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onDamage(LivingDamageEvent event) {
		LivingEntity owner = event.getEntity();
		PendingHit pending = takePending(owner, event.getSource());
		if (pending == null || event.isCanceled()
				|| !(event.getSource().getEntity() instanceof ServerPlayer player)
				|| !pending.playerId().equals(player.getUUID())) {
			return;
		}
		long now = pending.tick();
		if (!LegendaryActionRuntime.canStartReactive(owner,
				LegendaryActionId.LEGENDARY_RIPOSTE, now)) {
			DAMAGE.remove(owner.getUUID());
			return;
		}
		Deque<DamageEntry> window = DAMAGE.computeIfAbsent(owner.getUUID(),
				ignored -> new ArrayDeque<>());
		while (!window.isEmpty() && now - window.peekFirst().tick > WINDOW_TICKS) {
			window.removeFirst();
		}
		window.addLast(new DamageEntry(now, pending.amount()));
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
		PENDING.remove();
	}

	private static PendingHit takePending(LivingEntity owner, DamageSource source) {
		Deque<PendingHit> pending = PENDING.get();
		for (java.util.Iterator<PendingHit> iterator = pending.iterator();
				iterator.hasNext();) {
			PendingHit hit = iterator.next();
			if (hit.ownerId().equals(owner.getUUID()) && hit.source() == source) {
				iterator.remove();
				if (pending.isEmpty()) {
					PENDING.remove();
				}
				return hit;
			}
		}
		return null;
	}

	private record DamageEntry(long tick, float amount) {
	}

	private record PendingHit(UUID ownerId, DamageSource source, UUID playerId,
			float amount, long tick) {
	}
}
