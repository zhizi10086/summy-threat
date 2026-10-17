package com.summy.threat.armor;

import com.summy.threat.eximus.EximusShield;
import com.summy.threat.network.ModNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 回响胸甲提供的“玩家卓越护盾”池。
 *
 * <p>击杀卓越者 +20、上限 100；承伤在护甲/附魔/原版吸收之后、生命之前扣除。
 * 服务端把当前值同步给客户端，由 {@code PlayerShieldHud} 绘制青色顶部条。
 */
public final class PlayerEximusShield {
	public static final String TAG_SHIELD = "summy_threat:player_eximus_shield";
	public static final double MAX_SHIELD = 100.0D;
	public static final double GAIN_PER_EXIMUS_KILL = 20.0D;

	private PlayerEximusShield() {
	}

	public static double current(Player player) {
		if (player == null) {
			return 0.0D;
		}
		return Math.max(0.0D, Math.min(MAX_SHIELD,
				player.getPersistentData().getDouble(TAG_SHIELD)));
	}

	public static void set(ServerPlayer player, double value) {
		if (player == null) {
			return;
		}
		double clamped = Math.max(0.0D, Math.min(MAX_SHIELD, value));
		player.getPersistentData().putDouble(TAG_SHIELD, clamped);
		ModNetwork.sendShield(player, clamped);
		EchoArmorEffects.refreshBoots(player);
	}

	public static void add(ServerPlayer player, double amount) {
		if (player != null && amount > 0.0D) {
			set(player, current(player) + amount);
		}
	}

	public static void clear(Player player) {
		if (player == null) {
			return;
		}
		player.getPersistentData().remove(TAG_SHIELD);
		if (player instanceof ServerPlayer serverPlayer) {
			ModNetwork.sendShield(serverPlayer, 0.0D);
			EchoArmorEffects.refreshBoots(serverPlayer);
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onLivingDamage(LivingDamageEvent event) {
		if (event.getEntity().level().isClientSide()
				|| !(event.getEntity() instanceof ServerPlayer player)) {
			return;
		}
		if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return;
		}
		double shield = current(player);
		float amount = event.getAmount();
		if (shield <= 0.0D || amount <= 0.0F) {
			return;
		}
		double absorbed = Math.min(shield, amount);
		double left = shield - absorbed;
		player.getPersistentData().putDouble(TAG_SHIELD, left);
		event.setAmount((float) (amount - absorbed));
		ModNetwork.sendShield(player, left);
		EchoArmorEffects.refreshBoots(player);
	}

	@SubscribeEvent
	public static void onLivingDeath(LivingDeathEvent event) {
		LivingEntity victim = event.getEntity();
		if (victim instanceof ServerPlayer player) {
			clear(player);
			return;
		}
		if (!EximusShield.isEximus(victim)) {
			return;
		}
		ServerPlayer killer = killerOf(event);
		if (killer != null && EchoArmorEffects.hasChestplate(killer)) {
			add(killer, GAIN_PER_EXIMUS_KILL);
		}
	}

	private static ServerPlayer killerOf(LivingDeathEvent event) {
		if (event.getSource().getEntity() instanceof ServerPlayer player) {
			return player;
		}
		return null;
	}

	@SubscribeEvent
	public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			ModNetwork.sendShield(player, current(player));
		}
	}

	@SubscribeEvent
	public static void onClone(PlayerEvent.Clone event) {
		if (event.isWasDeath() && event.getEntity() instanceof ServerPlayer player) {
			clear(player);
		}
	}

	@SubscribeEvent
	public static void onServerStopped(ServerStoppedEvent event) {
		// 客户端 HUD 状态在玩家退出时由 ClientPlayerNetworkEvent.LoggingOut 清理。
	}
}
