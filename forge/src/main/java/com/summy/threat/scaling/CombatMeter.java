package com.summy.threat.scaling;

import com.summy.threat.Config;
import com.summy.threat.SummyThreat;
import com.summy.threat.eximus.EximusShield;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 伤害计量：为"实机校准"提供数据（有效 DPS、击杀耗时、无视护甲占比、承伤、致死次数）。
 *
 * <p>只统计与"被本模组数值化过的怪"相关的战斗：
 * <ul>
 *     <li>玩家 → TL 怪：逐目标记录首次命中时间、总伤害、无视护甲伤害；击杀时输出一行 INFO 日志。</li>
 *     <li>怪 → 玩家：记录单次伤害（最大 / 平均 / 次数）与玩家致死次数。</li>
 * </ul>
 *
 * <p>伤害取 {@link LivingDamageEvent}（已过护甲 / 附魔 / 吸收的最终值），因此"有效 DPS"就是真实生效输出。
 */
public final class CombatMeter {
	/** 单个目标（被数值化的怪）的战斗记录 */
	private static final class Target {
		long firstTick = -1L;
		double dealt;
		double bypass;
		double shieldAbsorbed;
	}

	/** 单个玩家的统计 */
	public static final class Stats {
		public double windowDealt;
		public long windowStartTick = -1L;
		public int kills;
		public double lastKillSeconds = -1.0D;
		public double lastKillHp;
		public double lastKillDps = -1.0D;
		public double lastKillBypassShare;
		public double lastKillShield;
		public double takenMax;
		public double takenSum;
		public int takenCount;
		public int deaths;
	}

	private static final Map<UUID, Target> TARGETS = new HashMap<>();
	private static final Map<UUID, Stats> PLAYERS = new HashMap<>();

	private CombatMeter() {
	}

	public static Stats stats(Player player) {
		return PLAYERS.computeIfAbsent(player.getUUID(), id -> new Stats());
	}

	public static void reset(Player player) {
		PLAYERS.remove(player.getUUID());
	}

	@SubscribeEvent
	public static void onDamage(LivingDamageEvent event) {
		MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
		if (server == null) {
			return;
		}
		long now = server.getTickCount();
		LivingEntity victim = event.getEntity();
		// 击杀瞬间的后续伤害（多段伤害的后段等）会落在已死亡的实体上：若继续登记，
		// 计量目标会被重建，同 tick 的二次死亡事件就会再打一条"0.00s / DPS −1.00"残影日志。
		// 击杀本体那次 LivingDamageEvent 在扣血前触发（生命 > 0），不受这条保护影响。
		if (victim.isDeadOrDying()) {
			return;
		}
		Entity attacker = event.getSource().getEntity();
		float amount = event.getAmount();
		boolean bypass = event.getSource().is(DamageTypeTags.BYPASSES_ARMOR);

		if (victim.getPersistentData().getBoolean(MonsterScaler.TAG_SCALED) && attacker instanceof Player player) {
			Target target = TARGETS.computeIfAbsent(victim.getUUID(), id -> new Target());
			if (target.firstTick < 0L) {
				target.firstTick = now;
			}
			// 溢出封顶：LivingDamageEvent 触发时"吸收已结算、生命尚未扣减"，所以按目标当前生命封顶，
			// 即"最多只计到打死为止"——否则斩杀类超量伤害会把有效 DPS 抬高。
			double absorbed = EximusShield.consumeAbsorbed(victim);
			double counted = Math.min(amount, Math.max(0.0D, victim.getHealth())) + absorbed;
			target.dealt += counted;
			target.shieldAbsorbed += absorbed;
			if (bypass) {
				target.bypass += counted;
			}
			Stats s = stats(player);
			if (s.windowStartTick < 0L) {
				s.windowStartTick = now;
			}
			s.windowDealt += counted;
			return;
		}

		if (victim instanceof Player player && attacker != null
				&& attacker.getPersistentData().getBoolean(MonsterScaler.TAG_SCALED)) {
			Stats s = stats(player);
			s.takenSum += amount;
			s.takenCount++;
			s.takenMax = Math.max(s.takenMax, amount);
			// 逐击承伤日志（效仿 SRA 的 log_trigger）：只影响日志输出，不影响统计与结算
			if (Config.SPEC.isLoaded() && Config.LOG_PLAYER_TAKEN.get()) {
				SummyThreat.LOGGER.info(
						"[Summy Threat] 玩家承伤 {}：{} 点（攻击者 {}，TL {}），生命 {}/{}，吸收 {}，累计第 {} 次（单次最大 {}）",
						player.getGameProfile().getName(), fmt(amount), attacker.getName().getString(),
						tlOf(attacker), fmt(player.getHealth()), fmt(player.getMaxHealth()),
						fmt(player.getAbsorptionAmount()), s.takenCount, fmt(s.takenMax));
			}
		}
	}

	@SubscribeEvent
	public static void onDeath(LivingDeathEvent event) {
		MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
		if (server == null) {
			return;
		}
		long now = server.getTickCount();
		LivingEntity victim = event.getEntity();
		Entity killer = event.getSource().getEntity();

		if (victim.getPersistentData().getBoolean(MonsterScaler.TAG_SCALED)) {
			Target target = TARGETS.remove(victim.getUUID());
			// dealt > 0：排除"击杀瞬间残留伤害重建出的空记录"，避免残影日志、击杀数翻倍与
			// "最近一次"被 0.00s 记录覆盖；真正的秒杀（dealt > 0）仍照常记录。
			if (killer instanceof Player player && target != null && target.firstTick >= 0L && target.dealt > 0.0D) {
				double seconds = (now - target.firstTick) / 20.0D;
				double dps = seconds > 0.0D ? target.dealt / seconds : -1.0D;
				double bypassShare = target.dealt > 0.0D ? target.bypass / target.dealt : 0.0D;
				Stats s = stats(player);
				s.kills++;
				s.lastKillSeconds = seconds;
				s.lastKillHp = victim.getMaxHealth();
				s.lastKillDps = dps;
				s.lastKillBypassShare = bypassShare;
				s.lastKillShield = target.shieldAbsorbed;
				SummyThreat.LOGGER.info(
						"[Summy Threat] 击杀 {}（TL {}）：最大生命 {}，耗时 {}s，有效 DPS {}，无视护甲占比 {}，护盾吸收 {}",
						victim.getName().getString(), victim.getPersistentData().getInt(MonsterScaler.TAG_TL),
						fmt(victim.getMaxHealth()), fmt(seconds), fmt(dps), pct(bypassShare),
						fmt(target.shieldAbsorbed));
			}
			return;
		}

		if (victim instanceof Player player && killer != null
				&& killer.getPersistentData().getBoolean(MonsterScaler.TAG_SCALED)) {
			Stats s = stats(player);
			s.deaths++;
			if (Config.SPEC.isLoaded() && Config.LOG_PLAYER_TAKEN.get()) {
				SummyThreat.LOGGER.info(
						"[Summy Threat] 玩家被 TL 怪击杀：{}（会话累计：受击 {} 次 / 总伤 {} / 最大 {}；死亡 {} 次）",
						player.getGameProfile().getName(), s.takenCount, fmt(s.takenSum), fmt(s.takenMax), s.deaths);
			}
		}
	}

	/** 攻击者的 TL（未数值化时显示 —） */
	private static String tlOf(Entity attacker) {
		return attacker.getPersistentData().contains(MonsterScaler.TAG_TL)
				? String.valueOf(attacker.getPersistentData().getInt(MonsterScaler.TAG_TL)) : "—";
	}

	/** 开服：清掉上一场会话遗留的"逐目标"记录（旧 tick 计数会污染跨会话的计时与 DPS） */
	@SubscribeEvent
	public static void onServerAboutToStart(ServerAboutToStartEvent event) {
		TARGETS.clear();
	}

	/** 最近窗口的实际 DPS（窗口内伤害 ÷ 窗口已过秒数） */
	public static double windowDps(Stats stats, long nowTick) {
		if (stats.windowStartTick < 0L || stats.windowDealt <= 0.0D) {
			return -1.0D;
		}
		double seconds = (nowTick - stats.windowStartTick) / 20.0D;
		return seconds > 0.0D ? stats.windowDealt / seconds : -1.0D;
	}

	public static String fmt(double value) {
		return String.format("%.2f", value);
	}

	public static String pct(double ratio) {
		return String.format("%.1f%%", ratio * 100.0D);
	}
}
