package com.summy.threat.devcheck;

import com.summy.threat.Config;
import com.summy.threat.api.ThreatConfigApi;
import com.summy.threat.item.EquipmentDropHandler;
import com.summy.threat.item.ModItems;
import com.summy.threat.scaling.MonsterScaler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDropsEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** B16 基础材料掉落的 DevCheck；不依赖 SR 事务，也不会调用 SR 写接口。 */
final class EquipmentDropDevCheck {
	private static final String PREFIX = "equipment_drops.";
	private static final Map<String, Object> DETERMINISTIC_VALUES = Map.of(
			PREFIX + "enable", true,
			PREFIX + "require_player_kill", true,
			PREFIX + "eximus_essence_chance_percent", 100.0D,
			PREFIX + "leader_essence_chance_percent", 100.0D,
			PREFIX + "boss_essence_count_low_tl", 1,
			PREFIX + "boss_essence_count_high_tl", 2,
			PREFIX + "boss_prism_chance_low_tl_percent", 100.0D,
			PREFIX + "boss_prism_count_high_tl", 1,
			PREFIX + "boss_prism_tl_threshold", 10);

	private EquipmentDropDevCheck() {
	}

	/** 客户端在独立阶段写配置；后续仅读取，避开文件监听触发的异步重载。 */
	static Map<String, Object> prepareDeterministicConfig() {
		Map<String, Object> original = new LinkedHashMap<>();
		for (String key : DETERMINISTIC_VALUES.keySet()) {
			original.put(key, ThreatConfigApi.get(key));
		}
		ThreatConfigApi.setMany(DETERMINISTIC_VALUES);
		return original;
	}

	static void restoreConfig(Map<String, Object> original) {
		if (original != null && !original.isEmpty()) {
			ThreatConfigApi.setMany(original);
		}
	}

	/** 配置已由独立阶段准备完成时，只执行确定性正掉落用例。 */
	static List<String> runPrepared(ServerPlayer player) {
		List<String> failures = new ArrayList<>();
		if (!Config.SPEC.isLoaded()) {
			failures.add("材料掉落：配置尚未加载");
			return failures;
		}
		ServerLevel level = player.serverLevel();
		Set<UUID> existingItems = itemIds(level, player);
		List<LivingEntity> victims = new ArrayList<>();
		try {
			Zombie normal = spawn(player, 2.0D, MonsterScaler.Kind.NORMAL, 20);
			victims.add(normal);
			drop(normal, player.damageSources().playerAttack(player));
			checkCounts(failures, level, player, "普通敌人", 0, 0);
			clearNewItems(level, player, existingItems);

			Zombie eximus = spawn(player, 4.0D, MonsterScaler.Kind.EXIMUS, 20);
			victims.add(eximus);
			drop(eximus, player.damageSources().playerAttack(player));
			checkCounts(failures, level, player, "卓越者", 1, 0);
			drop(eximus, player.damageSources().playerAttack(player));
			checkCounts(failures, level, player, "同一目标第二次掉落事件", 1, 0);
			clearNewItems(level, player, existingItems);

			Zombie bossLow = spawn(player, 6.0D, MonsterScaler.Kind.BOSS, 9);
			victims.add(bossLow);
			drop(bossLow, player.damageSources().playerAttack(player));
			checkCounts(failures, level, player, "Boss TL9", 1, 1);
			clearNewItems(level, player, existingItems);

			Zombie bossHigh = spawn(player, 8.0D, MonsterScaler.Kind.BOSS, 10);
			victims.add(bossHigh);
			drop(bossHigh, player.damageSources().playerAttack(player));
			checkCounts(failures, level, player, "Boss TL10", 2, 1);
		} catch (Throwable throwable) {
			failures.add("材料掉落异常：" + throwable);
		} finally {
			clearNewItems(level, player, existingItems);
			for (LivingEntity victim : victims) {
				victim.discard();
			}
		}
		return failures;
	}

	static List<String> run(ServerPlayer player) {
		List<String> failures = new ArrayList<>();
		if (!Config.SPEC.isLoaded()) {
			failures.add("材料掉落：配置尚未加载");
			return failures;
		}
		ServerLevel level = player.serverLevel();
		Map<String, Object> original;
		try {
			original = prepareDeterministicConfig();
		} catch (Throwable throwable) {
			failures.add("材料掉落配置准备失败：" + throwable);
			return failures;
		}
		Set<UUID> existingItems = itemIds(level, player);
		List<LivingEntity> victims = new ArrayList<>();
		try {
			failures.addAll(runPrepared(player));
			existingItems = itemIds(level, player);

			ThreatConfigApi.set(PREFIX + "enable", false);
			Zombie disabled = spawn(player, 9.0D, MonsterScaler.Kind.BOSS, 10);
			victims.add(disabled);
			drop(disabled, player.damageSources().playerAttack(player));
			checkCounts(failures, level, player, "关闭材料掉落", 0, 0);
			clearNewItems(level, player, existingItems);
			ThreatConfigApi.set(PREFIX + "enable", true);

			ThreatConfigApi.set(PREFIX + "require_player_kill", true);
			Zombie noPlayer = spawn(player, 10.0D, MonsterScaler.Kind.BOSS, 10);
			victims.add(noPlayer);
			drop(noPlayer, noPlayer.damageSources().generic());
			checkCounts(failures, level, player, "要求玩家击杀时无归属", 0, 0);
			clearNewItems(level, player, existingItems);

			ThreatConfigApi.set(PREFIX + "require_player_kill", false);
			Zombie noPlayerAllowed = spawn(player, 12.0D, MonsterScaler.Kind.BOSS, 10);
			victims.add(noPlayerAllowed);
			drop(noPlayerAllowed, noPlayerAllowed.damageSources().generic());
			checkCounts(failures, level, player, "关闭玩家击杀要求后允许无归属", 2, 1);
		} catch (Throwable throwable) {
			failures.add("材料掉落异常：" + throwable);
		} finally {
			try {
				restoreConfig(original);
			} catch (Throwable throwable) {
				failures.add("材料掉落配置恢复失败：" + throwable);
			}
			clearNewItems(level, player, existingItems);
			for (LivingEntity victim : victims) {
				victim.discard();
			}
		}
		return failures;
	}

	private static Zombie spawn(ServerPlayer player, double dx, MonsterScaler.Kind kind, int tl) {
		ServerLevel level = player.serverLevel();
		Zombie zombie = EntityType.ZOMBIE.create(level);
		if (zombie == null) {
			throw new IllegalStateException("无法创建材料掉落测试僵尸");
		}
		zombie.moveTo(player.getX() + dx, player.getY(), player.getZ(), 0.0F, 0.0F);
		zombie.setNoAi(true);
		zombie.setPersistenceRequired();
		level.addFreshEntity(zombie);
		MonsterScaler.scaleNow(zombie, tl, kind, false);
		return zombie;
	}

	private static void drop(LivingEntity victim, DamageSource source) {
		EquipmentDropHandler.onLivingDrops(new LivingDropsEvent(victim, source,
				new ArrayList<ItemEntity>(), 0, true));
	}

	private static int count(ServerLevel level, ServerPlayer player, Item item) {
		int count = 0;
		for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class,
				player.getBoundingBox().inflate(32.0D))) {
			if (entity.getItem().is(item)) {
				count += entity.getItem().getCount();
			}
		}
		return count;
	}

	private static void checkCounts(List<String> failures, ServerLevel level, ServerPlayer player,
			String label, int expectedEssence, int expectedPrism) {
		int essence = count(level, player, ModItems.EXIMUS_ESSENCE.get());
		int prism = count(level, player, ModItems.ABYSSAL_STAR_PRISM.get());
		check(failures, label + "：精华 " + expectedEssence + " / 棱晶 " + expectedPrism,
				essence == expectedEssence && prism == expectedPrism,
				"actual=" + essence + "/" + prism);
	}

	private static Set<UUID> itemIds(ServerLevel level, ServerPlayer player) {
		Set<UUID> ids = new HashSet<>();
		for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class,
				player.getBoundingBox().inflate(32.0D))) {
			ids.add(entity.getUUID());
		}
		return ids;
	}

	private static void clearNewItems(ServerLevel level, ServerPlayer player, Set<UUID> existing) {
		for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class,
				player.getBoundingBox().inflate(32.0D))) {
			if (!existing.contains(entity.getUUID())) {
				entity.discard();
			}
		}
	}

	private static void check(List<String> failures, String label, boolean ok) {
		check(failures, label, ok, "");
	}

	private static void check(List<String> failures, String label, boolean ok, String detail) {
		if (!ok) {
			failures.add(label + (detail.isEmpty() ? "" : "（" + detail + "）"));
		}
		System.out.println("[EQUIPMENT] " + (ok ? "OK " : "FAIL ") + label
				+ (detail.isEmpty() ? "" : "：" + detail));
	}

}
