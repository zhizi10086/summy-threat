package com.summy.threat;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/**
 * 模组配置（Forge 原生 SERVER 档）。
 *
 * <p>内容分组：
 * <ul>
 *     <li>{@code attribute_caps}：把被硬编码封顶的属性上限抬起来；下界刻意钉在原版上限上，
 *         配合 {@code AttributeCaps} 里的 {@code Math.max} 形成"只抬不压"的双保险。</li>
 *     <li>{@code damage_reduction}：自定义护甲减伤公式的常量（口径见 docs/护甲公式计算.txt）。</li>
 *     <li>{@code player_armor}：玩家护甲重填总开关（逐件数值在 PlayerArmorRefill 里以常量保存）。</li>
 *     <li>数值表（生命 v2.7 / 攻击 v2.8）：{@code health_multiplier_per_tl} / {@code attack_multiplier_per_tl} /
 *         {@code eximus_health_multiplier_per_tl} / {@code eximus} / {@code regional_boss_health} /
 *         {@code boss_health}——口径见内部《Summy Threat — 核心数值系统交接文档》§5~§8。</li>
 *     <li>{@code environment_tier}：生态分档（两维系数 r_hp / r_atk，见交接文档 §5.0）。</li>
 *     <li>{@code scaling_debug}：调试用——全局 TL（-1 = 关闭接管，默认值在内存里，重启复位）。</li>
 * </ul>
 *
 * <p>SERVER 档写在存档的 {@code serverconfig/summy_threat-server.toml}，Forge 会把服务端的值
 * 自动同步给客户端。
 */
public final class Config {
	// ================= 属性上限 =================
	/** 总开关：关闭时把上限写回原版值（运行期可逆） */
	public static final ForgeConfigSpec.BooleanValue ENABLE_ATTRIBUTE_CAP_OVERRIDE;
	/** generic.armor 的新上限 */
	public static final ForgeConfigSpec.DoubleValue ARMOR_MAX_VALUE;
	/** generic.armor_toughness 的新上限 */
	public static final ForgeConfigSpec.DoubleValue ARMOR_TOUGHNESS_MAX_VALUE;
	/** generic.max_health 的新上限 */
	public static final ForgeConfigSpec.DoubleValue MAX_HEALTH_MAX_VALUE;

	// ================= 减伤公式 =================
	/** 是否用本模组的公式替换原版护甲减伤那一步 */
	public static final ForgeConfigSpec.BooleanValue ENABLE_CUSTOM_ARMOR_FORMULA;
	/** 协同除数 C：EA = A + (A*T)/C */
	public static final ForgeConfigSpec.DoubleValue SYNERGY_DIVISOR;
	/** 玩家侧曲率 K：减伤 = EA/(EA+K) */
	public static final ForgeConfigSpec.DoubleValue PLAYER_K;
	/** 玩家侧减伤上限 */
	public static final ForgeConfigSpec.DoubleValue PLAYER_MAX;
	/** 玩家侧减伤下限（默认 0，仅作负值保护） */
	public static final ForgeConfigSpec.DoubleValue PLAYER_MIN;
	/** 怪物侧减伤上限 */
	public static final ForgeConfigSpec.DoubleValue MONSTER_MAX;
	/** 怪物侧参与减伤计算的有效 EA 上限（属性本身不受这个限制） */
	public static final ForgeConfigSpec.DoubleValue MONSTER_EFFECTIVE_EA_CAP;

	// ================= 玩家护甲重填 =================
	/** 是否按交接文档 §3.1 的逐件表重写原版护甲件（皮革 ~ 下界合金基础） */
	public static final ForgeConfigSpec.BooleanValue ENABLE_PLAYER_ARMOR_REFILL;

	// ================= 数值表（v2.6，索引 = TL） =================
	/** 生命倍率 M(TL) */
	public static final ForgeConfigSpec.DoubleValue[] HEALTH_MULTIPLIER_PER_TL = new ForgeConfigSpec.DoubleValue[21];
	/** 攻击倍率 A(TL) */
	public static final ForgeConfigSpec.DoubleValue[] ATTACK_MULTIPLIER_PER_TL = new ForgeConfigSpec.DoubleValue[21];
	/** 卓越者额外生命倍率 E(TL) */
	public static final ForgeConfigSpec.DoubleValue[] EXIMUS_HEALTH_MULTIPLIER_PER_TL = new ForgeConfigSpec.DoubleValue[21];

	/** 卓越者：护甲加成（加法） */
	public static final ForgeConfigSpec.DoubleValue EXIMUS_ARMOR_BONUS;
	/** 卓越者：韧性加成（加法） */
	public static final ForgeConfigSpec.DoubleValue EXIMUS_TOUGHNESS_BONUS;
	/** 卓越者：攻击倍率 */
	public static final ForgeConfigSpec.DoubleValue EXIMUS_DAMAGE_MULTIPLIER;
	/** 卓越者：移速加成（比例） */
	public static final ForgeConfigSpec.DoubleValue EXIMUS_SPEED_BONUS;
	/** 卓越者：经验倍率 */
	public static final ForgeConfigSpec.DoubleValue EXIMUS_XP_MULTIPLIER;
	/** 卓越者：卓越护盾在 TL20 时占最大生命的比例（等级曲线 f1 归一化） */
	public static final ForgeConfigSpec.DoubleValue EXIMUS_SHIELD_TL20_RATIO;
	/** 卓越者：自然生成概率 */
	public static final ForgeConfigSpec.DoubleValue EXIMUS_NATURAL_SPAWN_CHANCE;

	/** 区域首领：领袖僵尸生命固定倍率 */
	public static final ForgeConfigSpec.DoubleValue LEADER_ZOMBIE_MULTIPLIER;
	/** 区域首领：猪灵蛮兵生命固定倍率 */
	public static final ForgeConfigSpec.DoubleValue LEADER_PIGLIN_BRUTE_MULTIPLIER;
	/** 区域首领：远古守卫者生命固定倍率 */
	public static final ForgeConfigSpec.DoubleValue LEADER_ELDER_GUARDIAN_MULTIPLIER;
	/** 区域首领：领袖僵尸攻击倍率 */
	public static final ForgeConfigSpec.DoubleValue LEADER_ZOMBIE_ATTACK;
	/** 区域首领：猪灵蛮兵攻击倍率 */
	public static final ForgeConfigSpec.DoubleValue LEADER_PIGLIN_BRUTE_ATTACK;
	/** 区域首领：远古守卫者攻击倍率 */
	public static final ForgeConfigSpec.DoubleValue LEADER_ELDER_GUARDIAN_ATTACK;
	/** 区域首领 PCF 缩放：基数 */
	public static final ForgeConfigSpec.DoubleValue LEADER_PCF_BASE_FACTOR;
	/** 区域首领 PCF 缩放：除数 */
	public static final ForgeConfigSpec.DoubleValue LEADER_PCF_SCALE_DIVISOR;

	/** Boss：监守者生命固定倍率 */
	public static final ForgeConfigSpec.DoubleValue BOSS_WARDEN_MULTIPLIER;
	/** Boss：末影龙生命固定倍率 */
	public static final ForgeConfigSpec.DoubleValue BOSS_ENDER_DRAGON_MULTIPLIER;
	/** Boss：凋灵生命固定倍率 */
	public static final ForgeConfigSpec.DoubleValue BOSS_WITHER_MULTIPLIER;
	/** Boss：监守者攻击倍率 */
	public static final ForgeConfigSpec.DoubleValue BOSS_WARDEN_ATTACK;
	/** Boss：末影龙攻击倍率 */
	public static final ForgeConfigSpec.DoubleValue BOSS_ENDER_DRAGON_ATTACK;
	/** Boss：凋灵攻击倍率 */
	public static final ForgeConfigSpec.DoubleValue BOSS_WITHER_ATTACK;
	/** Mod Boss 反推用的默认有效 DPS（后续 Mod Boss 支持落地后启用） */
	public static final ForgeConfigSpec.DoubleValue DEFAULT_EFFECTIVE_DPS;
	/** Mod Boss 反推用的默认目标 TTK（秒；后续 Mod Boss 支持落地后启用） */
	public static final ForgeConfigSpec.DoubleValue DEFAULT_TARGET_TTK;

	/** 生态档位模式；FREE 为手动自由档，AUTO 在无三件套时仍落到 NONE。 */
	public enum TierMode { AUTO, NONE, SR, SE, SR_APOCRYPHA, SE_SR, FULL, FREE }

	/** 生态档位：auto = 自动检测三件套，其余为强制档 */
	public static final ForgeConfigSpec.EnumValue<TierMode> ENVIRONMENT_TIER_MODE;
	/** 档位系数（r_hp / r_atk） */
	public static final ForgeConfigSpec.DoubleValue HP_SCALE_SR;
	public static final ForgeConfigSpec.DoubleValue ATK_SCALE_SR;
	public static final ForgeConfigSpec.DoubleValue HP_SCALE_SE;
	public static final ForgeConfigSpec.DoubleValue ATK_SCALE_SE;
	public static final ForgeConfigSpec.DoubleValue HP_SCALE_SR_APOCRYPHA;
	public static final ForgeConfigSpec.DoubleValue ATK_SCALE_SR_APOCRYPHA;
	public static final ForgeConfigSpec.DoubleValue HP_SCALE_SE_SR;
	public static final ForgeConfigSpec.DoubleValue ATK_SCALE_SE_SR;
	public static final ForgeConfigSpec.DoubleValue HP_SCALE_FULL;
	public static final ForgeConfigSpec.DoubleValue ATK_SCALE_FULL;
	/** 自由档：由玩家自行填写，允许 0~2 以适配其它模组的战力。 */
	public static final ForgeConfigSpec.DoubleValue HP_SCALE_FREE;
	public static final ForgeConfigSpec.DoubleValue ATK_SCALE_FREE;

	/** 调试：全局 TL（-2 = 完全关闭；-1 = 自动 PCF/CDF；0~20 = 手动覆盖） */
	public static final ForgeConfigSpec.IntValue SCALING_GLOBAL_TL;

	/** SR 固定伤害的跨模组增伤兼容开关 */
	public static final ForgeConfigSpec.BooleanValue ENABLE_RELIQUARY_FIXED_DAMAGE_BOOSTS;

	// ================= PCF / CDF（v2.34） =================
	/** PCF 四项权重 */
	public static final ForgeConfigSpec.DoubleValue PCF_EQUIPMENT_WEIGHT;
	public static final ForgeConfigSpec.DoubleValue PCF_BOSS_WEIGHT;
	public static final ForgeConfigSpec.DoubleValue PCF_EXPLORATION_WEIGHT;
	public static final ForgeConfigSpec.DoubleValue PCF_RESOURCE_WEIGHT;
	/** PCF 指数平滑系数 */
	public static final ForgeConfigSpec.DoubleValue PCF_SMOOTHING;
	/** 创世纪重置后的锁定天数与上限 */
	public static final ForgeConfigSpec.IntValue PCF_RESET_LOCK_DAYS;
	public static final ForgeConfigSpec.DoubleValue PCF_RESET_LOCK_CAP;
	/** 装备分细则 */
	public static final ForgeConfigSpec.DoubleValue PCF_ARMOR_PER_POINT;
	public static final ForgeConfigSpec.DoubleValue PCF_ARMOR_CAP;
	public static final ForgeConfigSpec.DoubleValue PCF_ATTACK_PER_POINT;
	public static final ForgeConfigSpec.DoubleValue PCF_ATTACK_CAP;
	public static final ForgeConfigSpec.DoubleValue PCF_ENCHANT_PER_LEVEL;
	public static final ForgeConfigSpec.DoubleValue PCF_ENCHANT_CAP;
	/** Boss 分细则 */
	public static final ForgeConfigSpec.DoubleValue PCF_BOSS_CAP;
	public static final ForgeConfigSpec.DoubleValue PCF_BOSS_SCORE_ENDER_DRAGON;
	public static final ForgeConfigSpec.DoubleValue PCF_BOSS_SCORE_WITHER;
	public static final ForgeConfigSpec.DoubleValue PCF_BOSS_SCORE_WARDEN;
	public static final ForgeConfigSpec.DoubleValue PCF_BOSS_SCORE_ELDER_GUARDIAN;
	public static final ForgeConfigSpec.DoubleValue PCF_BOSS_SCORE_MOD;
	/** 探索分细则 */
	public static final ForgeConfigSpec.DoubleValue PCF_NETHER_ENTRY_SCORE;
	public static final ForgeConfigSpec.DoubleValue PCF_END_ENTRY_SCORE;
	public static final ForgeConfigSpec.DoubleValue PCF_BIOME_PER_NEW;
	public static final ForgeConfigSpec.DoubleValue PCF_BIOME_CAP;
	/** 资源分细则 */
	public static final ForgeConfigSpec.DoubleValue PCF_DIAMOND_PER_UNIT;
	public static final ForgeConfigSpec.DoubleValue PCF_DIAMOND_CAP;
	public static final ForgeConfigSpec.DoubleValue PCF_DEBRIS_PER_UNIT;
	public static final ForgeConfigSpec.DoubleValue PCF_DEBRIS_CAP;
	public static final ForgeConfigSpec.DoubleValue PCF_MOD_MATERIAL_PER_UNIT;
	public static final ForgeConfigSpec.DoubleValue PCF_MOD_MATERIAL_CAP;
	public static final ForgeConfigSpec.IntValue PCF_DIAMOND_DIMINISH_1;
	public static final ForgeConfigSpec.IntValue PCF_DIAMOND_DIMINISH_2;
	public static final ForgeConfigSpec.IntValue PCF_BIOME_DIMINISH_1;
	public static final ForgeConfigSpec.IntValue PCF_BIOME_DIMINISH_2;
	/** A5 / A6 可编辑 ID 列表 */
	public static final ForgeConfigSpec.ConfigValue<List<? extends String>> PCF_MOD_BOSS_IDS;
	public static final ForgeConfigSpec.ConfigValue<List<? extends String>> PCF_MOD_MATERIAL_IDS;

	/** CDF 每秒上升、击杀加成、死亡惩罚与衰减 */
	public static final ForgeConfigSpec.DoubleValue CDF_RISE_RATE;
	public static final ForgeConfigSpec.DoubleValue CDF_KILL_RISE_BONUS;
	public static final ForgeConfigSpec.DoubleValue CDF_KILL_RISE_MAX_BONUS;
	public static final ForgeConfigSpec.DoubleValue CDF_DEATH_RISE_PENALTY;
	public static final ForgeConfigSpec.IntValue CDF_DEATH_PENALTY_DURATION;
	public static final ForgeConfigSpec.DoubleValue CDF_DECAY_RATE;
	public static final ForgeConfigSpec.DoubleValue CDF_DECAY_FLOOR_RATIO;
	public static final ForgeConfigSpec.DoubleValue CDF_LEGACY_CHUNK_INIT_RATIO;

	/** 多人 PCF 选取 */
	public static final ForgeConfigSpec.ConfigValue<String> MULTIPLAYER_NORMAL_PCF_MODE;
	public static final ForgeConfigSpec.DoubleValue MULTIPLAYER_WEIGHTED_MIN_WEIGHT;
	public static final ForgeConfigSpec.DoubleValue MULTIPLAYER_ELITE_HIGH_WEIGHT;
	public static final ForgeConfigSpec.ConfigValue<String> MULTIPLAYER_BOSS_ANCHOR;
	public static final ForgeConfigSpec.IntValue MULTIPLAYER_PLAYER_DETECTION_RADIUS;

	/** 区域倍率与区域首领 TL 上限 */
	public static final ForgeConfigSpec.DoubleValue REGION_MULTIPLIER_SPAWN_PROTECTION;
	public static final ForgeConfigSpec.DoubleValue REGION_MULTIPLIER_NORMAL_BIOME;
	public static final ForgeConfigSpec.DoubleValue REGION_MULTIPLIER_NETHER_END;
	public static final ForgeConfigSpec.DoubleValue REGION_MULTIPLIER_STRUCTURE;
	public static final ForgeConfigSpec.DoubleValue REGION_MULTIPLIER_SPAWNER;
	public static final ForgeConfigSpec.IntValue REGION_TL_CAP_SPAWN_PROTECTION;
	public static final ForgeConfigSpec.IntValue REGION_TL_CAP_NORMAL_BIOME;
	public static final ForgeConfigSpec.IntValue REGION_TL_CAP_NETHER_END;
	public static final ForgeConfigSpec.IntValue REGION_TL_CAP_STRUCTURE;
	public static final ForgeConfigSpec.IntValue REGION_TL_CAP_SPAWNER;

	private static final List<String> DEFAULT_MOD_MATERIAL_IDS = List.of(
			"summy_reliquary:sin_fragment_pride",
			"summy_reliquary:sin_fragment_gluttony",
			"summy_reliquary:sin_fragment_wrath",
			"summy_reliquary:sin_fragment_envy",
			"summy_reliquary:sin_fragment_sloth",
			"summy_reliquary:sin_fragment_lust",
			"summy_reliquary:sin_fragment_greed",
			"summy_reliquary_apocrypha:fallen_plume",
			"summy_reliquary_apocrypha:scourged_heart",
			"summy_reliquary_apocrypha:withered_brush",
			"summy_reliquary_apocrypha:final_song");

	private static final List<String> DEFAULT_MOD_BOSS_IDS = List.of(
			"cataclysm:netherite_monstrosity", "cataclysm:ignis", "cataclysm:the_harbinger",
			"cataclysm:ender_guardian", "cataclysm:the_leviathan", "cataclysm:ancient_remnant",
			"cataclysm:maledictus", "cataclysm:scylla",
			"twilightforest:naga", "twilightforest:lich", "twilightforest:minoshroom",
			"twilightforest:hydra", "twilightforest:ur_ghast", "twilightforest:snow_queen",
			"twilightforest:knight_phantom", "twilightforest:alpha_yeti",
			"aether:slider", "aether:valkyrie_queen", "aether:sun_spirit",
			"mowziesmobs:ferrous_wroughtnaut", "mowziesmobs:frostmaw", "mowziesmobs:umvuthi",
			"mutantmonsters:mutant_zombie", "mutantmonsters:mutant_skeleton",
			"mutantmonsters:mutant_creeper", "mutantmonsters:mutant_enderman",
			"irons_spellbooks:dead_king", "irons_spellbooks:fire_boss",
			"iceandfire:fire_dragon", "iceandfire:ice_dragon", "iceandfire:lightning_dragon",
			"iceandfire:hydra", "iceandfire:sea_serpent", "iceandfire:dread_lich", "iceandfire:dread_queen",
			"goety:apostle", "goety:vizier", "goety:minister", "goety:hostile_redstone_monstrosity",
			"meetyourfight:bellringer", "meetyourfight:dame_fortuna", "meetyourfight:swampjaw",
			"meetyourfight:rosalyne", "bosses_of_mass_destruction:lich",
			"aquamirae:captain_cornelia", "theinkarena:ink_titan");

	/** 玩家承伤日志开关（效仿 SRA 的 log_trigger） */
	public static final ForgeConfigSpec.BooleanValue LOG_PLAYER_TAKEN;

	/** TL 能力总开关（关闭后不抽取、不生效） */
	public static final ForgeConfigSpec.BooleanValue TL_ABILITIES_ENABLED;

	public static final ForgeConfigSpec SPEC;

	static {
		ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

		builder.comment(
				"属性上限抬升：只抬上限、不动下限；关闭开关会把上限写回原版值。",
				"原版上限：generic.armor 30 / generic.armor_toughness 20 / generic.max_health 1024。")
				.push("attribute_caps");
		ENABLE_ATTRIBUTE_CAP_OVERRIDE = builder
				.comment("是否抬升属性上限")
				.define("enable_attribute_cap_override", true);
		ARMOR_MAX_VALUE = builder
				.comment("generic.armor 的上限；低于原版 30 的值会被忽略（只抬不压）")
				.defineInRange("armor_max_value", 500.0D, 30.0D, 1_000_000.0D);
		ARMOR_TOUGHNESS_MAX_VALUE = builder
				.comment("generic.armor_toughness 的上限；低于原版 20 的值会被忽略（只抬不压）")
				.defineInRange("armor_toughness_max_value", 100.0D, 20.0D, 1_000_000.0D);
		MAX_HEALTH_MAX_VALUE = builder
				.comment("generic.max_health 的上限；低于原版 1024 的值会被忽略（只抬不压）")
				.defineInRange("max_health_max_value", 1_000_000.0D, 1024.0D, 10_000_000.0D);
		builder.pop();

		builder.comment(
				"护甲减伤公式（替换原版那一步）——口径见 docs/护甲公式计算.txt：",
				"EA = A + (A*T)/协同除数；玩家侧不截断 EA，怪物侧只把 EA 截断到 有效EA上限 参与计算。")
				.push("damage_reduction");
		ENABLE_CUSTOM_ARMOR_FORMULA = builder
				.comment("是否启用本模组的护甲减伤公式；关闭后完全走原版")
				.define("enable_custom_armor_formula", true);
		SYNERGY_DIVISOR = builder
				.comment("协同除数 C：EA = A + (A*T)/C，越小则韧性放大越强")
				.defineInRange("synergy_divisor", 15.0D, 1.0D, 1000.0D);
		PLAYER_K = builder
				.comment("玩家侧曲率 K：减伤 = EA/(EA+K)")
				.defineInRange("player_k", 20.0D, 0.0D, 10_000.0D);
		PLAYER_MAX = builder
				.comment("玩家侧减伤上限")
				.defineInRange("player_max", 0.85D, 0.0D, 1.0D);
		PLAYER_MIN = builder
				.comment("玩家侧减伤下限（默认 0，仅作负值保护）")
				.defineInRange("player_min", 0.0D, 0.0D, 1.0D);
		MONSTER_MAX = builder
				.comment("怪物侧减伤上限")
				.defineInRange("monster_max", 0.90D, 0.0D, 1.0D);
		MONSTER_EFFECTIVE_EA_CAP = builder
				.comment("怪物侧参与减伤计算的有效 EA 上限（怪物实际护甲可以更高，用于配合基于护甲的效果）")
				.defineInRange("monster_effective_ea_cap", 100.0D, 0.0D, 10_000.0D);
		builder.pop();

		builder.comment(
				"玩家护甲重填：按交接文档 §3.1 的逐件表重写原版护甲件（皮革 / 锁链 / 黄金 / 铁 / 钻石 / 下界合金基础）；",
				"「下界合金（升级后）」= 回响下界合金（本模组物品，锻造台升级获得，逐件 6/11/8/5 与 4/6/5/3）。")
				.push("player_armor");
		ENABLE_PLAYER_ARMOR_REFILL = builder
				.comment("是否启用玩家护甲重填")
				.define("enabled", true);
		builder.pop();

		double[] mTable = {1.0, 1.3, 1.6, 2.3, 2.9, 3.9, 4.9, 6.2, 7.4, 9.1, 10.7, 12.6, 14.8, 17.4, 20.3,
				23.5, 27.4, 31.6, 36.4, 41.9, 48.0};
		// 攻击表 v2.8：按"满配一口血 168.5（护甲段减伤 71.5% + 生命 84 + 吸收 84.5）"口径整表重标定，
		// A' = 1 + (v2.6值 − 1) × 0.772；TL20 = 16.44（僵尸对满配 ≈8 次致死）。口径见交接文档 §5.6 / §13。
		double[] aTable = {1.0, 1.0, 1.08, 1.15, 1.31, 1.46, 1.62, 1.85, 2.08, 2.39, 2.70, 3.16, 3.86,
				4.71, 5.63, 6.79, 8.26, 9.88, 11.81, 14.12, 16.44};
		double[] eTable = {1.20, 1.21, 1.22, 1.24, 1.26, 1.30, 1.33, 1.36, 1.40, 1.45, 1.50, 1.55, 1.60,
				1.65, 1.70, 1.75, 1.85, 1.95, 2.05, 2.12, 2.20};

		builder.comment("普通单位生命倍率 M(TL)（v2.6；TL0 锚定原版，不受档位系数影响）")
				.push("health_multiplier_per_tl");
		for (int i = 0; i < 21; i++) {
			HEALTH_MULTIPLIER_PER_TL[i] = builder.defineInRange("tl" + i, mTable[i], 0.0D, 10_000.0D);
		}
		builder.pop();

		builder.comment("普通单位攻击倍率 A(TL)（v2.8：整表按满配口径重标定 k = 0.772；只缩放基础攻击力）")
				.push("attack_multiplier_per_tl");
		for (int i = 0; i < 21; i++) {
			ATTACK_MULTIPLIER_PER_TL[i] = builder.defineInRange("tl" + i, aTable[i], 0.0D, 10_000.0D);
		}
		builder.pop();

		builder.comment("卓越者额外生命倍率 E(TL)（v2.6，查表制）")
				.push("eximus_health_multiplier_per_tl");
		for (int i = 0; i < 21; i++) {
			EXIMUS_HEALTH_MULTIPLIER_PER_TL[i] = builder.defineInRange("tl" + i, eTable[i], 0.0D, 10_000.0D);
		}
		builder.pop();

		builder.comment("卓越者的数值强化（本轮不含能力池）")
				.push("eximus");
		EXIMUS_ARMOR_BONUS = builder
				.comment("护甲加成（加法）")
				.defineInRange("armor_bonus", 15.0D, 0.0D, 10_000.0D);
		EXIMUS_TOUGHNESS_BONUS = builder
				.comment("韧性加成（加法）")
				.defineInRange("toughness_bonus", 10.0D, 0.0D, 10_000.0D);
		EXIMUS_DAMAGE_MULTIPLIER = builder
				.comment("攻击倍率")
				.defineInRange("damage_multiplier", 1.25D, 0.0D, 1000.0D);
		EXIMUS_SPEED_BONUS = builder
				.comment("移速加成（比例，0.05 = +5%）")
				.defineInRange("speed_bonus", 0.05D, -1.0D, 10.0D);
		EXIMUS_XP_MULTIPLIER = builder
				.comment("经验倍率")
				.defineInRange("xp_multiplier", 2.0D, 0.0D, 1000.0D);
		EXIMUS_SHIELD_TL20_RATIO = builder
				.comment("卓越护盾：TL20 时占最大生命的比例（等级曲线 f1 归一化；0.75 = 75%）")
				.defineInRange("eximus_shield_tl20_ratio", 0.75D, 0.0D, 10.0D);
		EXIMUS_NATURAL_SPAWN_CHANCE = builder
				.comment("自然生成的卓越者概率（0.05 = 5%；刷怪笼/命令/召唤不参与）")
				.defineInRange("natural_spawn_chance", 0.05D, 0.0D, 1.0D);
		builder.pop();

		builder.comment("区域首领（领袖）：生命 = 基础生命 × 固定倍率 × (基数 + PCF / 除数)",
				"攻击口径（v2.8）：困难难度对玩家 ×1.5；满配一口血 168.5，目标 6 次致死；",
				"猪灵蛮兵原版攻击属性实为 7.0（旧文档按 13.0 推算有误，已校正）。")
				.push("regional_boss_health");
		LEADER_ZOMBIE_MULTIPLIER = builder.defineInRange("leader_zombie", 1050.0D, 1.0D, 100_000.0D);
		LEADER_PIGLIN_BRUTE_MULTIPLIER = builder.defineInRange("piglin_brute", 420.0D, 1.0D, 100_000.0D);
		LEADER_ELDER_GUARDIAN_MULTIPLIER = builder.defineInRange("elder_guardian", 263.0D, 1.0D, 100_000.0D);
		LEADER_ZOMBIE_ATTACK = builder
				.comment("领袖僵尸攻击倍率（v2.8：属性 3.0 × 21.93 × 1.5 ≈ 一击 98.7，满配 6 次致死）")
				.defineInRange("leader_zombie_attack", 21.93D, 0.0D, 10_000.0D);
		LEADER_PIGLIN_BRUTE_ATTACK = builder
				.comment("猪灵蛮兵攻击倍率（v2.8：属性 7.0 × 9.40 × 1.5 ≈ 一击 98.7，满配 6 次致死）")
				.defineInRange("piglin_brute_attack", 9.40D, 0.0D, 10_000.0D);
		LEADER_ELDER_GUARDIAN_ATTACK = builder
				.comment("远古守卫者攻击倍率——**当前无效**：古守伤害为硬编码（thorns 2.0 接触 + 激光），",
						"不吃 ATTACK_DAMAGE 属性；数值暂留占位，待「攻击接管」轮启用。")
				.defineInRange("elder_guardian_attack", 8.0D, 0.0D, 10_000.0D);
		LEADER_PCF_BASE_FACTOR = builder
				.comment("PCF 缩放的基数（默认 0.3）")
				.defineInRange("pcf_base_factor", 0.3D, 0.0D, 10.0D);
		LEADER_PCF_SCALE_DIVISOR = builder
				.comment("PCF 缩放的除数（默认 28）")
				.defineInRange("pcf_scale_divisor", 28.0D, 1.0D, 1000.0D);
		builder.pop();

		builder.comment("原版 Boss：生命 = 基础生命 × 固定倍率（不随 PCF 缩放）",
				"攻击口径（v2.8）：监守者目标 4 次致死（属性 30 × 3.29 × 1.5 ≈ 一击 148.1）；",
				"末影龙（啃咬硬编码 10f/5f）与凋灵（爆炸 + 头颅投射物）伤害**不走 ATTACK_DAMAGE**，",
				"对应倍率当前无效、数值暂留占位，待「攻击接管」轮启用。")
				.push("boss_health");
		BOSS_WARDEN_MULTIPLIER = builder.defineInRange("warden", 70.0D, 1.0D, 100_000.0D);
		BOSS_ENDER_DRAGON_MULTIPLIER = builder.defineInRange("ender_dragon", 175.0D, 1.0D, 100_000.0D);
		BOSS_WITHER_MULTIPLIER = builder.defineInRange("wither", 117.0D, 1.0D, 100_000.0D);
		BOSS_WARDEN_ATTACK = builder
				.comment("监守者近战攻击倍率（v2.8：属性 30 × 3.29 × 1.5 ≈ 一击 148.1，满配 4 次致死）")
				.defineInRange("warden_melee_attack", 3.29D, 0.0D, 1000.0D);
		BOSS_ENDER_DRAGON_ATTACK = builder
				.comment("末影龙攻击倍率——**当前无效**（啃咬伤害硬编码 10f/5f，无 ATTACK_DAMAGE 属性）")
				.defineInRange("ender_dragon_attack", 4.0D, 0.0D, 1000.0D);
		BOSS_WITHER_ATTACK = builder
				.comment("凋灵攻击倍率——**对实际伤害无效**（伤害来自爆炸与头颅投射物；属性存在但默认 2.0，源码不读它）")
				.defineInRange("wither_attack", 3.0D, 0.0D, 1000.0D);
		DEFAULT_EFFECTIVE_DPS = builder
				.comment("Mod Boss 反推公式用的默认有效 DPS（v2.7 校准常数：Boss 档实测 ≈700；Mod Boss 支持落地后启用）")
				.defineInRange("default_effective_dps", 700.0D, 1.0D, 1_000_000.0D);
		DEFAULT_TARGET_TTK = builder
				.comment("Mod Boss 反推公式用的默认目标 TTK（秒；v2.7 定为 50）")
				.defineInRange("default_target_ttk", 50.0D, 1.0D, 10_000.0D);
		builder.pop();

		builder.comment(
				"生态分档（交接文档 §5.0）：auto 检测 summy_enchantment / summy_reliquary / summy_reliquary_apocrypha，",
				"SRA 强制依赖 SR，故为 6 个预设档 + 1 个手动 free 档；none 档怪物侧整体短路（不接管、不缩放）。",
				"r_hp 管怪物生命 / 护甲，r_atk 管怪物攻击与特殊伤害；缩放公式 v' = 1 + (v − 1) × r。",
				"free 为手动自由档：AUTO 不会自动选择它；玩家需显式使用 /summythreat tier free。")
				.push("environment_tier");
		ENVIRONMENT_TIER_MODE = builder
				.comment("档位模式：auto = 自动检测；其余为强制档（供整合包 / 测试覆盖）")
				.defineEnum("mode", TierMode.AUTO);
		HP_SCALE_SR = builder.defineInRange("hp_scale_sr", 0.35D, 0.0D, 1.0D);
		ATK_SCALE_SR = builder.defineInRange("atk_scale_sr", 0.35D, 0.0D, 1.0D);
		HP_SCALE_SE = builder.defineInRange("hp_scale_se", 0.55D, 0.0D, 1.0D);
		ATK_SCALE_SE = builder.defineInRange("atk_scale_se", 0.55D, 0.0D, 1.0D);
		HP_SCALE_SR_APOCRYPHA = builder.defineInRange("hp_scale_sr_apocrypha", 0.55D, 0.0D, 1.0D);
		ATK_SCALE_SR_APOCRYPHA = builder.defineInRange("atk_scale_sr_apocrypha", 0.35D, 0.0D, 1.0D);
		HP_SCALE_SE_SR = builder.defineInRange("hp_scale_se_sr", 0.75D, 0.0D, 1.0D);
		ATK_SCALE_SE_SR = builder.defineInRange("atk_scale_se_sr", 0.75D, 0.0D, 1.0D);
		HP_SCALE_FULL = builder.defineInRange("hp_scale_full", 1.00D, 0.0D, 1.0D);
		ATK_SCALE_FULL = builder.defineInRange("atk_scale_full", 1.00D, 0.0D, 1.0D);
		HP_SCALE_FREE = builder
				.comment("自由档 r_hp；仅 mode=free 时生效，允许 0~2 以适配其它模组")
				.defineInRange("hp_scale_free", 0.50D, 0.0D, 2.0D);
		ATK_SCALE_FREE = builder
				.comment("自由档 r_atk；仅 mode=free 时生效，允许 0~2 以适配其它模组")
				.defineInRange("atk_scale_free", 0.50D, 0.0D, 2.0D);
		builder.pop();

		builder.comment("PCF / CDF（交接文档 §5.0；PCF CDF 定义补全.md）：玩家进度与区块难度共同决定 TL。")
				.push("player_challenge_factor");
		PCF_EQUIPMENT_WEIGHT = builder.defineInRange("equipment_weight", 0.30D, 0.0D, 1.0D);
		PCF_BOSS_WEIGHT = builder.defineInRange("boss_weight", 0.25D, 0.0D, 1.0D);
		PCF_EXPLORATION_WEIGHT = builder.defineInRange("exploration_weight", 0.20D, 0.0D, 1.0D);
		PCF_RESOURCE_WEIGHT = builder.defineInRange("resource_weight", 0.25D, 0.0D, 1.0D);
		PCF_SMOOTHING = builder.defineInRange("pcf_smoothing", 0.30D, 0.0D, 1.0D);
		PCF_RESET_LOCK_DAYS = builder.defineInRange("reset_lock_days", 3, 0, 1000);
		PCF_RESET_LOCK_CAP = builder.defineInRange("reset_lock_cap", 3.0D, 0.0D, 20.0D);
		PCF_ARMOR_PER_POINT = builder.defineInRange("armor_per_point", 0.64D, 0.0D, 100.0D);
		PCF_ARMOR_CAP = builder.defineInRange("armor_cap", 12.0D, 0.0D, 100.0D);
		PCF_ATTACK_PER_POINT = builder.defineInRange("attack_per_point", 0.8D, 0.0D, 100.0D);
		PCF_ATTACK_CAP = builder.defineInRange("attack_cap", 8.0D, 0.0D, 100.0D);
		PCF_ENCHANT_PER_LEVEL = builder.defineInRange("enchant_per_level", 0.4D, 0.0D, 100.0D);
		PCF_ENCHANT_CAP = builder.defineInRange("enchant_cap", 4.0D, 0.0D, 100.0D);
		PCF_BOSS_CAP = builder.defineInRange("boss_cap", 20.0D, 0.0D, 100.0D);
		PCF_BOSS_SCORE_ENDER_DRAGON = builder.defineInRange("boss_score_ender_dragon", 7.0D, 0.0D, 100.0D);
		PCF_BOSS_SCORE_WITHER = builder.defineInRange("boss_score_wither", 7.0D, 0.0D, 100.0D);
		PCF_BOSS_SCORE_WARDEN = builder.defineInRange("boss_score_warden", 4.0D, 0.0D, 100.0D);
		PCF_BOSS_SCORE_ELDER_GUARDIAN = builder.defineInRange("boss_score_elder_guardian", 2.0D, 0.0D, 100.0D);
		PCF_BOSS_SCORE_MOD = builder.defineInRange("boss_score_mod", 4.0D, 0.0D, 100.0D);
		PCF_NETHER_ENTRY_SCORE = builder.defineInRange("nether_entry_score", 4.0D, 0.0D, 100.0D);
		PCF_END_ENTRY_SCORE = builder.defineInRange("end_entry_score", 4.0D, 0.0D, 100.0D);
		PCF_BIOME_PER_NEW = builder.defineInRange("biome_per_new", 0.5D, 0.0D, 100.0D);
		PCF_BIOME_CAP = builder.defineInRange("biome_cap", 8.0D, 0.0D, 100.0D);
		PCF_DIAMOND_PER_UNIT = builder.defineInRange("diamond_per_unit", 0.3D, 0.0D, 100.0D);
		PCF_DIAMOND_CAP = builder.defineInRange("diamond_cap", 8.0D, 0.0D, 100.0D);
		PCF_DEBRIS_PER_UNIT = builder.defineInRange("debris_per_unit", 1.0D, 0.0D, 100.0D);
		PCF_DEBRIS_CAP = builder.defineInRange("debris_cap", 8.0D, 0.0D, 100.0D);
		PCF_MOD_MATERIAL_PER_UNIT = builder.defineInRange("mod_material_per_unit", 0.5D, 0.0D, 100.0D);
		PCF_MOD_MATERIAL_CAP = builder.defineInRange("mod_material_cap", 4.0D, 0.0D, 100.0D);
		PCF_DIAMOND_DIMINISH_1 = builder.defineInRange("diamond_diminish_1", 10, 1, 100000);
		PCF_DIAMOND_DIMINISH_2 = builder.defineInRange("diamond_diminish_2", 30, 1, 100000);
		PCF_BIOME_DIMINISH_1 = builder.defineInRange("biome_diminish_1", 10, 1, 100000);
		PCF_BIOME_DIMINISH_2 = builder.defineInRange("biome_diminish_2", 20, 1, 100000);
		PCF_MOD_BOSS_IDS = builder.defineListAllowEmpty("mod_boss_ids", DEFAULT_MOD_BOSS_IDS,
				element -> element instanceof String);
		PCF_MOD_MATERIAL_IDS = builder.defineListAllowEmpty("mod_material_ids", DEFAULT_MOD_MATERIAL_IDS,
				element -> element instanceof String);
		builder.pop();

		builder.comment("CDF：区块难度因子。每秒上升，玩家离开后按时间戳衰减。")
				.push("chunk_difficulty_factor");
		CDF_RISE_RATE = builder.defineInRange("rise_rate", 0.01D, 0.0D, 1.0D);
		CDF_KILL_RISE_BONUS = builder.defineInRange("kill_rise_bonus", 0.005D, 0.0D, 1.0D);
		CDF_KILL_RISE_MAX_BONUS = builder.defineInRange("kill_rise_max_bonus", 0.05D, 0.0D, 1.0D);
		CDF_DEATH_RISE_PENALTY = builder.defineInRange("death_rise_penalty", 0.10D, 0.0D, 1.0D);
		CDF_DEATH_PENALTY_DURATION = builder.defineInRange("death_penalty_duration", 60, 0, 100000);
		CDF_DECAY_RATE = builder.defineInRange("decay_rate", 0.0002D, 0.0D, 1.0D);
		CDF_DECAY_FLOOR_RATIO = builder.defineInRange("decay_floor_ratio", 0.20D, 0.0D, 1.0D);
		CDF_LEGACY_CHUNK_INIT_RATIO = builder.defineInRange("legacy_chunk_init_ratio", 0.5D, 0.0D, 1.0D);
		builder.pop();

		builder.comment("多人 PCF 选取（128 格内玩家集合）。")
				.push("multiplayer");
		MULTIPLAYER_NORMAL_PCF_MODE = builder.define("normal_pcf_mode", "weighted");
		MULTIPLAYER_WEIGHTED_MIN_WEIGHT = builder.defineInRange("weighted_min_weight", 0.7D, 0.0D, 1.0D);
		MULTIPLAYER_ELITE_HIGH_WEIGHT = builder.defineInRange("elite_high_weight", 0.5D, 0.0D, 1.0D);
		MULTIPLAYER_BOSS_ANCHOR = builder.define("boss_anchor", "highest");
		MULTIPLAYER_PLAYER_DETECTION_RADIUS = builder.defineInRange("player_detection_radius", 128, 1, 512);
		builder.pop();

		builder.comment("区域修正倍率与区域首领 TL 上限；普通怪 / 卓越者只受 20 硬上限。")
				.push("region_multiplier");
		REGION_MULTIPLIER_SPAWN_PROTECTION = builder.defineInRange("spawn_protection", 0.5D, 0.0D, 100.0D);
		REGION_MULTIPLIER_NORMAL_BIOME = builder.defineInRange("normal_biome", 1.0D, 0.0D, 100.0D);
		REGION_MULTIPLIER_NETHER_END = builder.defineInRange("nether_end", 1.2D, 0.0D, 100.0D);
		REGION_MULTIPLIER_STRUCTURE = builder.defineInRange("structure", 1.5D, 0.0D, 100.0D);
		REGION_MULTIPLIER_SPAWNER = builder.defineInRange("spawner", 0.3D, 0.0D, 100.0D);
		builder.pop();

		builder.push("region_tl_cap");
		REGION_TL_CAP_SPAWN_PROTECTION = builder.defineInRange("spawn_protection", 12, 0, 20);
		REGION_TL_CAP_NORMAL_BIOME = builder.defineInRange("normal_biome", 12, 0, 20);
		REGION_TL_CAP_NETHER_END = builder.defineInRange("nether_end", 14, 0, 20);
		REGION_TL_CAP_STRUCTURE = builder.defineInRange("structure", 15, 0, 20);
		REGION_TL_CAP_SPAWNER = builder.defineInRange("spawner", 12, 0, 20);
		builder.pop();

		builder.comment("调试：全局 TL（-2 = 完全关闭；-1 = 自动 PCF/CDF；0~20 = 手动覆盖）。运行期用 /summythreat tl 修改，重启复位。")
				.push("scaling_debug");
		SCALING_GLOBAL_TL = builder
				.comment("启动时的默认全局 TL；-2 关闭，-1 自动 PCF/CDF")
				.defineInRange("global_tl", -1, -2, 20);
		builder.pop();

		builder.comment(
				"SR 固定伤害兼容：只处理 7 项写死数值的固定伤害（5 项 isDivine + 2 项 isExactDamage）。",
				"默认关闭；开启后 5 项补 SR/SE 全伤害增伤，2 项只补 SE 死神祝福，不处理近战/命中专属乘区。")
				.push("reliquary_compat");
		ENABLE_RELIQUARY_FIXED_DAMAGE_BOOSTS = builder
				.comment("启用 SR 固定伤害的全伤害增伤兼容")
				.define("enable_fixed_damage_boosts", false);
		builder.pop();

		builder.comment("战斗日志（效仿 SRA 的 log_trigger）：只影响日志输出，不影响统计与结算。")
				.push("combat_log");
		LOG_PLAYER_TAKEN = builder
				.comment("玩家承伤日志：每次受击打一行 INFO（伤害 / 攻击者 / TL / 剩余生命与吸收）；",
						"关闭后不再逐击记录，但仍计入 /summythreat stats")
				.define("log_player_taken", true);
		builder.pop();

		builder.comment("TL 能力（交接文档 §6.5）：按 TL 区间抽取 0/1/2/3/4 个基础能力、卓越者再 +1；",
				"关闭后既不抽取也不生效（已挂载的标签保留）。")
				.push("tl_abilities");
		TL_ABILITIES_ENABLED = builder
				.comment("是否启用 TL 能力")
				.define("enable_tl_abilities", true);
		builder.pop();

		SPEC = builder.build();
	}

	private Config() {
	}
}
