package com.summy.threat.command;

import com.summy.threat.Config;
import com.summy.threat.scaling.ScalingState;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * free 档的配置白名单。
 *
 * <p>只暴露“影响成长缩放”的现有全局键，不复制第二套参数：
 * r_hp/r_atk 使用 free 专用键，PCF/CDF、区域倍率与上限、区域首领/Boss 倍率
 * 仍直接读写各自的全局配置。三张 21 级 TL 表、列表键、未参与运行时的字符串键
 * 以及战斗/能力配置不在此处，避免命令面变成无边界配置编辑器。
 */
final class FreeTierSettings {
	private enum ValueType { DOUBLE, INTEGER }

	private record Key(String id, String group,
			ForgeConfigSpec.ConfigValue<?> value, ValueType type, double min, double max) {
	}

	private static final Map<String, Key> KEYS = new LinkedHashMap<>();
	private static final Map<String, List<String>> GROUPS = new LinkedHashMap<>();

	static {
		register("core", "hp", Config.HP_SCALE_FREE, ValueType.DOUBLE, 0.0D, 2.0D);
		register("core", "atk", Config.ATK_SCALE_FREE, ValueType.DOUBLE, 0.0D, 2.0D);
		registerPcf();
		registerCdf();
		registerMultiplayer();
		registerRegions();
		registerLeaders();
		registerBosses();
	}

	private FreeTierSettings() {
	}

	static List<String> groups() {
		return List.copyOf(GROUPS.keySet());
	}

	static List<String> keys() {
		return List.copyOf(KEYS.keySet());
	}

	static String get(String id) {
		Key key = require(id);
		return key.id() + " = " + format(key.value().get());
	}

	static List<String> list(String group) {
		List<String> ids = GROUPS.get(group);
		if (ids == null) {
			throw new IllegalArgumentException("未知分组：" + group + "（可用 free list 查看）");
		}
		List<String> lines = new ArrayList<>(ids.size());
		for (String id : ids) {
			lines.add(get(id));
		}
		return lines;
	}

	/** 写入一个白名单键，返回“旧值 → 新值”；调用方负责提示当前是否处于 free。 */
	static String set(String id, String raw) {
		Key key = require(id);
		Object old = key.value().get();
		Object parsed = parse(key, raw);
		write(key, parsed);
		persist();
		return key.id() + "：" + format(old) + " → " + format(parsed);
	}

	/** tier free <hp> <atk> 的快捷写入；两个值都校验通过后才落盘。 */
	static void setCore(double hp, double atk) {
		requireRange("core.hp", hp, 0.0D, 2.0D);
		requireRange("core.atk", atk, 0.0D, 2.0D);
		write(require("core.hp"), hp);
		write(require("core.atk"), atk);
		persist();
	}

	static String describeGroup(String group) {
		return switch (group) {
			case "core" -> "free 两维系";
			case "pcf" -> "PCF 玩家挑战因子";
			case "cdf" -> "CDF 区块难度因子";
			case "multiplayer" -> "多人 PCF 选取";
			case "region_multiplier" -> "区域倍率";
			case "region_tl_cap" -> "区域首领 TL 上限";
			case "leader" -> "区域首领生命/攻击倍率";
			case "boss" -> "原版 Boss 生命/攻击与 Mod Boss 反推";
			default -> group;
		};
	}

	private static void registerPcf() {
		register("pcf", "equipment_weight", Config.PCF_EQUIPMENT_WEIGHT, ValueType.DOUBLE, 0.0D, 1.0D);
		register("pcf", "boss_weight", Config.PCF_BOSS_WEIGHT, ValueType.DOUBLE, 0.0D, 1.0D);
		register("pcf", "exploration_weight", Config.PCF_EXPLORATION_WEIGHT, ValueType.DOUBLE, 0.0D, 1.0D);
		register("pcf", "resource_weight", Config.PCF_RESOURCE_WEIGHT, ValueType.DOUBLE, 0.0D, 1.0D);
		register("pcf", "pcf_smoothing", Config.PCF_SMOOTHING, ValueType.DOUBLE, 0.0D, 1.0D);
		register("pcf", "reset_lock_days", Config.PCF_RESET_LOCK_DAYS, ValueType.INTEGER, 0.0D, 1000.0D);
		register("pcf", "reset_lock_cap", Config.PCF_RESET_LOCK_CAP, ValueType.DOUBLE, 0.0D, 20.0D);
		register("pcf", "armor_per_point", Config.PCF_ARMOR_PER_POINT, ValueType.DOUBLE, 0.0D, 100.0D);
		register("pcf", "armor_cap", Config.PCF_ARMOR_CAP, ValueType.DOUBLE, 0.0D, 100.0D);
		register("pcf", "attack_per_point", Config.PCF_ATTACK_PER_POINT, ValueType.DOUBLE, 0.0D, 100.0D);
		register("pcf", "attack_cap", Config.PCF_ATTACK_CAP, ValueType.DOUBLE, 0.0D, 100.0D);
		register("pcf", "enchant_per_level", Config.PCF_ENCHANT_PER_LEVEL, ValueType.DOUBLE, 0.0D, 100.0D);
		register("pcf", "enchant_cap", Config.PCF_ENCHANT_CAP, ValueType.DOUBLE, 0.0D, 100.0D);
		register("pcf", "boss_cap", Config.PCF_BOSS_CAP, ValueType.DOUBLE, 0.0D, 100.0D);
		register("pcf", "boss_score_ender_dragon", Config.PCF_BOSS_SCORE_ENDER_DRAGON, ValueType.DOUBLE, 0.0D, 100.0D);
		register("pcf", "boss_score_wither", Config.PCF_BOSS_SCORE_WITHER, ValueType.DOUBLE, 0.0D, 100.0D);
		register("pcf", "boss_score_warden", Config.PCF_BOSS_SCORE_WARDEN, ValueType.DOUBLE, 0.0D, 100.0D);
		register("pcf", "boss_score_elder_guardian", Config.PCF_BOSS_SCORE_ELDER_GUARDIAN, ValueType.DOUBLE, 0.0D, 100.0D);
		register("pcf", "boss_score_mod", Config.PCF_BOSS_SCORE_MOD, ValueType.DOUBLE, 0.0D, 100.0D);
		register("pcf", "nether_entry_score", Config.PCF_NETHER_ENTRY_SCORE, ValueType.DOUBLE, 0.0D, 100.0D);
		register("pcf", "end_entry_score", Config.PCF_END_ENTRY_SCORE, ValueType.DOUBLE, 0.0D, 100.0D);
		register("pcf", "biome_per_new", Config.PCF_BIOME_PER_NEW, ValueType.DOUBLE, 0.0D, 100.0D);
		register("pcf", "biome_cap", Config.PCF_BIOME_CAP, ValueType.DOUBLE, 0.0D, 100.0D);
		register("pcf", "diamond_per_unit", Config.PCF_DIAMOND_PER_UNIT, ValueType.DOUBLE, 0.0D, 100.0D);
		register("pcf", "diamond_cap", Config.PCF_DIAMOND_CAP, ValueType.DOUBLE, 0.0D, 100.0D);
		register("pcf", "debris_per_unit", Config.PCF_DEBRIS_PER_UNIT, ValueType.DOUBLE, 0.0D, 100.0D);
		register("pcf", "debris_cap", Config.PCF_DEBRIS_CAP, ValueType.DOUBLE, 0.0D, 100.0D);
		register("pcf", "mod_material_per_unit", Config.PCF_MOD_MATERIAL_PER_UNIT, ValueType.DOUBLE, 0.0D, 100.0D);
		register("pcf", "mod_material_cap", Config.PCF_MOD_MATERIAL_CAP, ValueType.DOUBLE, 0.0D, 100.0D);
		register("pcf", "diamond_diminish_1", Config.PCF_DIAMOND_DIMINISH_1, ValueType.INTEGER, 1.0D, 100000.0D);
		register("pcf", "diamond_diminish_2", Config.PCF_DIAMOND_DIMINISH_2, ValueType.INTEGER, 1.0D, 100000.0D);
		register("pcf", "biome_diminish_1", Config.PCF_BIOME_DIMINISH_1, ValueType.INTEGER, 1.0D, 100000.0D);
		register("pcf", "biome_diminish_2", Config.PCF_BIOME_DIMINISH_2, ValueType.INTEGER, 1.0D, 100000.0D);
	}

	private static void registerCdf() {
		register("cdf", "rise_rate", Config.CDF_RISE_RATE, ValueType.DOUBLE, 0.0D, 1.0D);
		register("cdf", "kill_rise_bonus", Config.CDF_KILL_RISE_BONUS, ValueType.DOUBLE, 0.0D, 1.0D);
		register("cdf", "kill_rise_max_bonus", Config.CDF_KILL_RISE_MAX_BONUS, ValueType.DOUBLE, 0.0D, 1.0D);
		register("cdf", "death_rise_penalty", Config.CDF_DEATH_RISE_PENALTY, ValueType.DOUBLE, 0.0D, 1.0D);
		register("cdf", "death_penalty_duration", Config.CDF_DEATH_PENALTY_DURATION, ValueType.INTEGER, 0.0D, 100000.0D);
		register("cdf", "decay_rate", Config.CDF_DECAY_RATE, ValueType.DOUBLE, 0.0D, 1.0D);
		register("cdf", "decay_floor_ratio", Config.CDF_DECAY_FLOOR_RATIO, ValueType.DOUBLE, 0.0D, 1.0D);
		register("cdf", "legacy_chunk_init_ratio", Config.CDF_LEGACY_CHUNK_INIT_RATIO, ValueType.DOUBLE, 0.0D, 1.0D);
	}

	private static void registerMultiplayer() {
		register("multiplayer", "weighted_min_weight", Config.MULTIPLAYER_WEIGHTED_MIN_WEIGHT, ValueType.DOUBLE, 0.0D, 1.0D);
		register("multiplayer", "elite_high_weight", Config.MULTIPLAYER_ELITE_HIGH_WEIGHT, ValueType.DOUBLE, 0.0D, 1.0D);
		register("multiplayer", "player_detection_radius", Config.MULTIPLAYER_PLAYER_DETECTION_RADIUS, ValueType.INTEGER, 1.0D, 512.0D);
	}

	private static void registerRegions() {
		register("region_multiplier", "spawn_protection", Config.REGION_MULTIPLIER_SPAWN_PROTECTION, ValueType.DOUBLE, 0.0D, 100.0D);
		register("region_multiplier", "normal_biome", Config.REGION_MULTIPLIER_NORMAL_BIOME, ValueType.DOUBLE, 0.0D, 100.0D);
		register("region_multiplier", "nether_end", Config.REGION_MULTIPLIER_NETHER_END, ValueType.DOUBLE, 0.0D, 100.0D);
		register("region_multiplier", "structure", Config.REGION_MULTIPLIER_STRUCTURE, ValueType.DOUBLE, 0.0D, 100.0D);
		register("region_multiplier", "spawner", Config.REGION_MULTIPLIER_SPAWNER, ValueType.DOUBLE, 0.0D, 100.0D);
		register("region_tl_cap", "spawn_protection", Config.REGION_TL_CAP_SPAWN_PROTECTION, ValueType.INTEGER, 0.0D, 20.0D);
		register("region_tl_cap", "normal_biome", Config.REGION_TL_CAP_NORMAL_BIOME, ValueType.INTEGER, 0.0D, 20.0D);
		register("region_tl_cap", "nether_end", Config.REGION_TL_CAP_NETHER_END, ValueType.INTEGER, 0.0D, 20.0D);
		register("region_tl_cap", "structure", Config.REGION_TL_CAP_STRUCTURE, ValueType.INTEGER, 0.0D, 20.0D);
		register("region_tl_cap", "spawner", Config.REGION_TL_CAP_SPAWNER, ValueType.INTEGER, 0.0D, 20.0D);
	}

	private static void registerLeaders() {
		register("leader", "leader_zombie", Config.LEADER_ZOMBIE_MULTIPLIER, ValueType.DOUBLE, 1.0D, 100000.0D);
		register("leader", "piglin_brute", Config.LEADER_PIGLIN_BRUTE_MULTIPLIER, ValueType.DOUBLE, 1.0D, 100000.0D);
		register("leader", "elder_guardian", Config.LEADER_ELDER_GUARDIAN_MULTIPLIER, ValueType.DOUBLE, 1.0D, 100000.0D);
		register("leader", "leader_zombie_attack", Config.LEADER_ZOMBIE_ATTACK, ValueType.DOUBLE, 0.0D, 10000.0D);
		register("leader", "piglin_brute_attack", Config.LEADER_PIGLIN_BRUTE_ATTACK, ValueType.DOUBLE, 0.0D, 10000.0D);
		register("leader", "elder_guardian_attack", Config.LEADER_ELDER_GUARDIAN_ATTACK, ValueType.DOUBLE, 0.0D, 10000.0D);
		register("leader", "pcf_base_factor", Config.LEADER_PCF_BASE_FACTOR, ValueType.DOUBLE, 0.0D, 10.0D);
		register("leader", "pcf_scale_divisor", Config.LEADER_PCF_SCALE_DIVISOR, ValueType.DOUBLE, 1.0D, 1000.0D);
	}

	private static void registerBosses() {
		register("boss", "warden", Config.BOSS_WARDEN_MULTIPLIER, ValueType.DOUBLE, 1.0D, 100000.0D);
		register("boss", "ender_dragon", Config.BOSS_ENDER_DRAGON_MULTIPLIER, ValueType.DOUBLE, 1.0D, 100000.0D);
		register("boss", "wither", Config.BOSS_WITHER_MULTIPLIER, ValueType.DOUBLE, 1.0D, 100000.0D);
		register("boss", "warden_melee_attack", Config.BOSS_WARDEN_ATTACK, ValueType.DOUBLE, 0.0D, 1000.0D);
		register("boss", "ender_dragon_attack", Config.BOSS_ENDER_DRAGON_ATTACK, ValueType.DOUBLE, 0.0D, 1000.0D);
		register("boss", "wither_attack", Config.BOSS_WITHER_ATTACK, ValueType.DOUBLE, 0.0D, 1000.0D);
		register("boss", "default_effective_dps", Config.DEFAULT_EFFECTIVE_DPS, ValueType.DOUBLE, 1.0D, 1000000.0D);
		register("boss", "default_target_ttk", Config.DEFAULT_TARGET_TTK, ValueType.DOUBLE, 1.0D, 10000.0D);
	}

	private static void register(String group, String id, ForgeConfigSpec.ConfigValue<?> value,
			ValueType type, double min, double max) {
		Key key = new Key(group + "." + id, group, value, type, min, max);
		if (KEYS.putIfAbsent(key.id(), key) != null) {
			throw new IllegalStateException("重复的 free 配置键：" + key.id());
		}
		GROUPS.computeIfAbsent(group, ignored -> new ArrayList<>()).add(key.id());
	}

	private static Key require(String id) {
		Key key = KEYS.get(id);
		if (key == null) {
			throw new IllegalArgumentException("未知键：" + id + "（可用 free list 查看）");
		}
		return key;
	}

	private static Object parse(Key key, String raw) {
		String text = raw == null ? "" : raw.trim();
		try {
			if (key.type() == ValueType.INTEGER) {
				int value = Integer.parseInt(text);
				requireRange(key.id(), value, key.min(), key.max());
				return value;
			}
			double value = Double.parseDouble(text);
			if (!Double.isFinite(value)) {
				throw new IllegalArgumentException(key.id() + " 需要有限数值");
			}
			requireRange(key.id(), value, key.min(), key.max());
			return value;
		} catch (NumberFormatException exception) {
			throw new IllegalArgumentException(key.id() + " 需要"
					+ (key.type() == ValueType.INTEGER ? "整数" : "数值") + "：" + raw);
		}
	}

	private static void requireRange(String id, double value, double min, double max) {
		if (value < min || value > max) {
			throw new IllegalArgumentException(id + " 超出范围：" + format(min) + " ~ " + format(max));
		}
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static void write(Key key, Object value) {
		((ForgeConfigSpec.ConfigValue) key.value()).set(value);
	}

	private static void persist() {
		if (Config.SPEC.isLoaded()) {
			Config.SPEC.save();
		}
		ScalingState.refreshPreservingTl();
	}

	private static String format(Object value) {
		if (value instanceof Number number) {
			double raw = number.doubleValue();
			if (value instanceof Integer || value instanceof Long) {
				return Long.toString(number.longValue());
			}
			String text = String.format(Locale.ROOT, "%.4f", raw);
			while (text.endsWith("0")) {
				text = text.substring(0, text.length() - 1);
			}
			if (text.endsWith(".")) {
				text = text.substring(0, text.length() - 1);
			}
			return text.isEmpty() ? "0" : text;
		}
		return String.valueOf(value);
	}
}
