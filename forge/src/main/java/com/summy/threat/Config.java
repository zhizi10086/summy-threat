package com.summy.threat;

import com.summy.threat.equipment.StarPrismAffixes;
import com.summy.threat.equipment.StarPrismConfigDefinitions;
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
	/** TL 腐蚀每层削减的后台护甲百分比 */
	public static final ForgeConfigSpec.DoubleValue CORROSION_PER_STACK_PERCENT;
	/** TL 腐蚀最大层数 */
	public static final ForgeConfigSpec.IntValue CORROSION_MAX_STACKS;
	/** TL 腐蚀持续时间（秒） */
	public static final ForgeConfigSpec.IntValue CORROSION_DURATION_SECONDS;

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
	/** 是否自动把 player_challenge_factor.mod_boss_ids 中的实体识别为 Mod Boss */
	public static final ForgeConfigSpec.BooleanValue AUTO_CALCULATE_MOD_BOSS;
	/** 未单独配置攻击锚点的 Mod Boss 在 TL20 使用的默认攻击倍率 */
	public static final ForgeConfigSpec.DoubleValue DEFAULT_MOD_BOSS_ATTACK;
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

	/** 区域倍率与首领生成概率 */
	public static final ForgeConfigSpec.DoubleValue REGION_MULTIPLIER_SPAWN_PROTECTION;
	public static final ForgeConfigSpec.DoubleValue REGION_MULTIPLIER_NORMAL_BIOME;
	public static final ForgeConfigSpec.DoubleValue REGION_MULTIPLIER_NETHER_END;
	public static final ForgeConfigSpec.DoubleValue REGION_MULTIPLIER_STRUCTURE;
	public static final ForgeConfigSpec.DoubleValue REGION_MULTIPLIER_SPAWNER;
	public static final ForgeConfigSpec.DoubleValue LEADER_GENERAL_NATURAL_CHANCE;
	public static final ForgeConfigSpec.DoubleValue LEADER_MONUMENT_EXTRA_CHANCE;
	public static final ForgeConfigSpec.DoubleValue LEADER_BASTION_EXTRA_CHANCE;

	/** 卓越精华 / 星渊棱晶基础掉落。 */
	public static final ForgeConfigSpec.BooleanValue EQUIPMENT_DROPS_ENABLE;
	public static final ForgeConfigSpec.BooleanValue EQUIPMENT_DROPS_REQUIRE_PLAYER_KILL;
	public static final ForgeConfigSpec.DoubleValue EXIMUS_ESSENCE_CHANCE_PERCENT;
	public static final ForgeConfigSpec.DoubleValue LEADER_ESSENCE_CHANCE_PERCENT;
	public static final ForgeConfigSpec.IntValue BOSS_ESSENCE_COUNT_LOW_TL;
	public static final ForgeConfigSpec.IntValue BOSS_ESSENCE_COUNT_HIGH_TL;
	public static final ForgeConfigSpec.DoubleValue BOSS_PRISM_CHANCE_LOW_TL_PERCENT;
	public static final ForgeConfigSpec.IntValue BOSS_PRISM_COUNT_HIGH_TL;
	public static final ForgeConfigSpec.IntValue BOSS_PRISM_TL_THRESHOLD;

	// ================= B15 卓越装备与星渊棱晶 =================
	public static final ForgeConfigSpec.BooleanValue EXIMUS_EQUIPMENT_ENABLED;
	public static final ForgeConfigSpec.IntValue EMERGENCY_MENDING_COOLDOWN_TICKS;
	public static final ForgeConfigSpec.DoubleValue EMERGENCY_MENDING_HEAL_PERCENT;
	public static final ForgeConfigSpec.DoubleValue EMERGENCY_MENDING_HEAL_MIN;
	public static final ForgeConfigSpec.DoubleValue EMERGENCY_MENDING_HEAL_MAX;
	public static final ForgeConfigSpec.DoubleValue SLOWNESS_GUARD_FLOOR_PERCENT;
	public static final ForgeConfigSpec.DoubleValue STAR_STRIDE_SPEED_PERCENT;
	public static final ForgeConfigSpec.DoubleValue STAR_STRIDE_STEP_HEIGHT;
	public static final ForgeConfigSpec.IntValue SHARED_MARK_DURATION_TICKS;
	public static final ForgeConfigSpec.DoubleValue SHARED_MARK_DAMAGE_PERCENT;
	public static final ForgeConfigSpec.IntValue ARMOR_DURABILITY_CAP_DIVISOR;
	public static final ForgeConfigSpec.IntValue ARMOR_DURABILITY_CAP_MIN;
	public static final ForgeConfigSpec.IntValue ARMOR_DURABILITY_CAP_MAX;

	public static final ForgeConfigSpec.BooleanValue STAR_PRISM_ENABLED;
	public static final ForgeConfigSpec.DoubleValue STAR_PRISM_STANDARD_TIER_I_WEIGHT;
	public static final ForgeConfigSpec.DoubleValue STAR_PRISM_STANDARD_TIER_II_WEIGHT;
	public static final ForgeConfigSpec.DoubleValue STAR_PRISM_STANDARD_TIER_III_WEIGHT;
	public static final ForgeConfigSpec.DoubleValue STAR_PRISM_ADVANCED_TIER_I_WEIGHT;
	public static final ForgeConfigSpec.DoubleValue STAR_PRISM_ADVANCED_TIER_II_WEIGHT;
	public static final ForgeConfigSpec.DoubleValue STAR_PRISM_ADVANCED_TIER_III_WEIGHT;
	public static final ForgeConfigSpec.DoubleValue[] STAR_PRISM_AFFIX_WEIGHTS =
			new ForgeConfigSpec.DoubleValue[StarPrismAffixes.all().size()];
	public static final ForgeConfigSpec.DoubleValue[] STAR_PRISM_DOUBLE_VALUES =
			new ForgeConfigSpec.DoubleValue[StarPrismConfigDefinitions.DOUBLE_VALUES.size()];
	public static final ForgeConfigSpec.IntValue[] STAR_PRISM_INT_VALUES =
			new ForgeConfigSpec.IntValue[StarPrismConfigDefinitions.INT_VALUES.size()];

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

	private static final List<String> DEFAULT_REDUCED_REGENERATION_ENTITY_IDS =
			List.of("minecraft:ender_dragon");
	private static final List<String> DEFAULT_IMMUNE_NEGATIVE_EFFECTS = List.of(
			"minecraft:blindness", "minecraft:darkness", "minecraft:nausea", "minecraft:levitation");

	/** 玩家承伤日志开关（效仿 SRA 的 log_trigger） */
	public static final ForgeConfigSpec.BooleanValue LOG_PLAYER_TAKEN;

	/** TL 能力总开关（关闭后不抽取、不生效） */
	public static final ForgeConfigSpec.BooleanValue TL_ABILITIES_ENABLED;
	/** 再生能力默认每秒恢复的最大生命百分比 */
	public static final ForgeConfigSpec.DoubleValue REGENERATION_PERCENT_PER_SECOND;
	/** 低恢复名单内实体每秒恢复的最大生命百分比 */
	public static final ForgeConfigSpec.DoubleValue REDUCED_REGENERATION_PERCENT_PER_SECOND;
	/** 使用低恢复比例的实体类型 ID 列表 */
	public static final ForgeConfigSpec.ConfigValue<List<? extends String>> REDUCED_REGENERATION_ENTITY_IDS;

	/** 区域首领基础传奇抗性总开关 */
	public static final ForgeConfigSpec.BooleanValue LEGENDARY_RESISTANCE_ENABLED;
	/** 非免疫负面 MobEffect 的玩家可见等级上限 */
	public static final ForgeConfigSpec.IntValue LEGENDARY_NEGATIVE_EFFECT_LEVEL_CAP;
	/** 基础传奇抗性直接免疫的负面 MobEffect ID */
	public static final ForgeConfigSpec.ConfigValue<List<? extends String>> LEGENDARY_IMMUNE_NEGATIVE_EFFECTS;
	/** 基础传奇抗性的击退抗性加成 */
	public static final ForgeConfigSpec.DoubleValue LEGENDARY_KNOCKBACK_RESISTANCE;
	/** 是否允许基础传奇抗性同时提供 SR 背叛免疫标签 */
	public static final ForgeConfigSpec.BooleanValue LEGENDARY_BETRAYAL_IMMUNE;

	/** 魔法抗性总开关 */
	public static final ForgeConfigSpec.BooleanValue MAGIC_RESISTANCE_ENABLED;
	/** 法抗表是否乘当前生态档的 r_hp */
	public static final ForgeConfigSpec.BooleanValue MAGIC_RESISTANCE_SCALE_WITH_ENVIRONMENT_HP;
	/** 法抗最终硬上限（百分比） */
	public static final ForgeConfigSpec.DoubleValue MAGIC_RESISTANCE_HARD_CAP_PERCENT;
	/** 普通、卓越者、区域首领/Boss 三套独立 TL 表 */
	public static final ForgeConfigSpec.DoubleValue[] NORMAL_MAGIC_RESISTANCE_PER_TL;
	public static final ForgeConfigSpec.DoubleValue[] EXIMUS_MAGIC_RESISTANCE_PER_TL;
	public static final ForgeConfigSpec.DoubleValue[] LEADER_BOSS_MAGIC_RESISTANCE_PER_TL;

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
		CORROSION_PER_STACK_PERCENT = builder
				.comment("TL 腐蚀每层从后台护甲 A 削减的百分比；多层按加法汇总")
				.defineInRange("corrosion_per_stack_percent", 10.0D, 0.0D, 100.0D);
		CORROSION_MAX_STACKS = builder
				.comment("TL 腐蚀最多层数；重复命中刷新最旧层")
				.defineInRange("corrosion_max_stacks", 3, 1, 10);
		CORROSION_DURATION_SECONDS = builder
				.comment("TL 腐蚀每层持续时间（秒）")
				.defineInRange("corrosion_duration_seconds", 6, 1, 60);
		builder.pop();

		builder.comment(
				"TL 魔法抗性：只作用于仅绕过护甲、但不绕过保护附魔/抗性/无敌帧等效果的伤害。",
				"最终法抗 = 表值 × r_hp（可关闭）后再受硬上限约束；玩家目标不读取本组配置。")
				.push("magic_resistance");
		MAGIC_RESISTANCE_ENABLED = builder
				.comment("是否启用 TL 魔法抗性")
				.define("enable", true);
		MAGIC_RESISTANCE_SCALE_WITH_ENVIRONMENT_HP = builder
				.comment("法抗表值是否乘当前生态档的 r_hp；默认先缩放再应用硬上限")
				.define("scale_with_environment_hp", true);
		MAGIC_RESISTANCE_HARD_CAP_PERCENT = builder
				.comment("法抗最终硬上限（百分比）；默认 90 表示至少保留 10% 魔法伤害")
				.defineInRange("hard_cap_percent", 90.0D, 0.0D, 100.0D);
		NORMAL_MAGIC_RESISTANCE_PER_TL = defineMagicResistanceTable(builder, "normal",
				new double[]{0.0D, 0.0D, 0.0D, 1.0D, 2.0D, 3.0D, 5.0D, 7.0D, 9.0D, 11.0D,
						14.0D, 17.0D, 20.0D, 23.0D, 26.0D, 30.0D, 34.0D, 39.0D, 44.0D, 49.0D, 55.0D});
		EXIMUS_MAGIC_RESISTANCE_PER_TL = defineMagicResistanceTable(builder, "eximus",
				new double[]{5.0D, 5.0D, 5.0D, 6.0D, 7.0D, 8.0D, 10.0D, 12.0D, 14.0D, 16.0D,
						19.0D, 22.0D, 25.0D, 28.0D, 31.0D, 35.0D, 39.0D, 44.0D, 49.0D, 54.0D, 60.0D});
		LEADER_BOSS_MAGIC_RESISTANCE_PER_TL = defineMagicResistanceTable(builder, "leader_boss",
				new double[]{40.0D, 41.0D, 42.0D, 43.0D, 44.0D, 45.0D, 46.0D, 47.0D, 48.0D, 49.0D,
						50.0D, 51.0D, 52.0D, 53.0D, 54.0D, 55.0D, 56.0D, 57.0D, 58.0D, 59.0D, 60.0D});
		builder.pop();

		builder.comment(
				"玩家护甲重填：按交接文档 §3.1 的逐件表重写原版护甲件（皮革 / 锁链 / 黄金 / 铁 / 钻石 / 下界合金基础）；",
				"B15 后不再包含回响下界合金；卓越升级通过通用装备 NBT 与物品级属性实现。")
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
				"猪灵蛮兵正常环境持金斧，攻击属性为 13.0。")
				.push("regional_boss_health");
		LEADER_ZOMBIE_MULTIPLIER = builder.defineInRange("leader_zombie", 1050.0D, 1.0D, 100_000.0D);
		LEADER_PIGLIN_BRUTE_MULTIPLIER = builder.defineInRange("piglin_brute", 420.0D, 1.0D, 100_000.0D);
		LEADER_ELDER_GUARDIAN_MULTIPLIER = builder.defineInRange("elder_guardian", 263.0D, 1.0D, 100_000.0D);
		LEADER_ZOMBIE_ATTACK = builder
				.comment("领袖僵尸攻击倍率（v2.8：属性 3.0 × 21.93 × 1.5 ≈ 一击 98.7，满配 6 次致死）")
				.defineInRange("leader_zombie_attack", 21.93D, 0.0D, 10_000.0D);
		LEADER_PIGLIN_BRUTE_ATTACK = builder
				.comment("猪灵蛮兵攻击倍率（带金斧属性 13.0 × 9.40，困难命中再 ×1.5）")
				.defineInRange("piglin_brute_attack", 9.40D, 0.0D, 10_000.0D);
		LEADER_ELDER_GUARDIAN_ATTACK = builder
				.comment("远古守卫者攻击倍率：属性攻击直接生效；",
						"激光固定魔法段与 thorns 2.0 由 A15 接管，继承生成时的最终攻击倍率。")
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
				"末影龙啃咬 10f/5f、凋灵爆炸与头颅投射物由 A15 接管，",
				"继承 Boss 生成时已写入的攻击倍率。")
				.push("boss_health");
		BOSS_WARDEN_MULTIPLIER = builder.defineInRange("warden", 70.0D, 1.0D, 100_000.0D);
		BOSS_ENDER_DRAGON_MULTIPLIER = builder.defineInRange("ender_dragon", 175.0D, 1.0D, 100_000.0D);
		BOSS_WITHER_MULTIPLIER = builder.defineInRange("wither", 117.0D, 1.0D, 100_000.0D);
		BOSS_WARDEN_ATTACK = builder
				.comment("监守者近战攻击倍率（v2.8：属性 30 × 3.29 × 1.5 ≈ 一击 148.1，满配 4 次致死）")
				.defineInRange("warden_melee_attack", 3.29D, 0.0D, 1000.0D);
		BOSS_ENDER_DRAGON_ATTACK = builder
				.comment("末影龙攻击倍率：接管啃咬 10f/5f 的硬编码伤害（无 ATTACK_DAMAGE 属性）")
				.defineInRange("ender_dragon_attack", 4.0D, 0.0D, 1000.0D);
		BOSS_WITHER_ATTACK = builder
				.comment("凋灵攻击倍率：接管生成爆炸、头颅直击 8f 与头颅爆炸")
				.defineInRange("wither_attack", 3.0D, 0.0D, 1000.0D);
		AUTO_CALCULATE_MOD_BOSS = builder
				.comment("是否把 player_challenge_factor.mod_boss_ids 中的实体自动识别为 Mod Boss",
						"关闭后该列表只继续用于 PCF 计分；显式 summy_threat:boss 标签仍然有效")
				.define("auto_calculate_mod_boss", true);
		DEFAULT_MOD_BOSS_ATTACK = builder
				.comment("未单独配置攻击锚点的 Mod Boss 在 TL20 使用的默认攻击倍率")
				.defineInRange("default_mod_boss_attack", 3.0D, 0.0D, 1000.0D);
		DEFAULT_EFFECTIVE_DPS = builder
				.comment("Mod Boss 反推公式用的默认有效 DPS（v2.7 校准常数：Boss 档实测 ≈700）")
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

		builder.comment("区域修正倍率；普通怪 / 卓越者 / 区域首领均只受 20 硬上限。")
				.push("region_multiplier");
		REGION_MULTIPLIER_SPAWN_PROTECTION = builder.defineInRange("spawn_protection", 0.5D, 0.0D, 100.0D);
		REGION_MULTIPLIER_NORMAL_BIOME = builder.defineInRange("normal_biome", 1.0D, 0.0D, 100.0D);
		REGION_MULTIPLIER_NETHER_END = builder.defineInRange("nether_end", 1.2D, 0.0D, 100.0D);
		REGION_MULTIPLIER_STRUCTURE = builder.defineInRange("structure", 1.5D, 0.0D, 100.0D);
		REGION_MULTIPLIER_SPAWNER = builder.defineInRange("spawner", 0.3D, 0.0D, 100.0D);
		builder.pop();

		builder.comment("区域首领自然生成概率；所有概率均为单个候选实体的独立概率。")
				.push("leader_spawn");
		LEADER_GENERAL_NATURAL_CHANCE = builder
				.comment("普通敌对怪自然生成区域首领的概率")
				.defineInRange("natural_chance", 0.001D, 0.0D, 1.0D);
		LEADER_MONUMENT_EXTRA_CHANCE = builder
				.comment("海底神殿除保底首领外，每只额外古守升级为首领的概率")
				.defineInRange("monument_extra_chance", 0.25D, 0.0D, 1.0D);
		LEADER_BASTION_EXTRA_CHANCE = builder
				.comment("堡垒前两只保底首领外，每只额外蛮兵升级为首领的概率")
				.defineInRange("bastion_extra_chance", 0.10D, 0.0D, 1.0D);
		builder.pop();

		builder.comment("卓越精华与星渊棱晶的基础死亡掉落；材料直接生成受保护掉落物，不进入事件掉落列表。")
				.push("equipment_drops");
		EQUIPMENT_DROPS_ENABLE = builder
				.comment("是否启用两种材料的基础掉落")
				.define("enable", true);
		EQUIPMENT_DROPS_REQUIRE_PLAYER_KILL = builder
				.comment("是否要求有效的玩家击杀归属；关闭后允许无玩家归属的死亡产生材料")
				.define("require_player_kill", true);
		EXIMUS_ESSENCE_CHANCE_PERCENT = builder
				.comment("卓越者掉落 1 个卓越精华的百分比")
				.defineInRange("eximus_essence_chance_percent", 50.0D, 0.0D, 100.0D);
		LEADER_ESSENCE_CHANCE_PERCENT = builder
				.comment("区域首领掉落 1 个卓越精华的百分比")
				.defineInRange("leader_essence_chance_percent", 75.0D, 0.0D, 100.0D);
		BOSS_ESSENCE_COUNT_LOW_TL = builder
				.comment("Boss 低于共享阈值时固定掉落的卓越精华数量")
				.defineInRange("boss_essence_count_low_tl", 1, 0, 16);
		BOSS_ESSENCE_COUNT_HIGH_TL = builder
				.comment("Boss 达到共享阈值时固定掉落的卓越精华数量")
				.defineInRange("boss_essence_count_high_tl", 2, 0, 16);
		BOSS_PRISM_CHANCE_LOW_TL_PERCENT = builder
				.comment("Boss 低于共享阈值时掉落 1 个星渊棱晶的百分比")
				.defineInRange("boss_prism_chance_low_tl_percent", 10.0D, 0.0D, 100.0D);
		BOSS_PRISM_COUNT_HIGH_TL = builder
				.comment("Boss 达到共享阈值时固定掉落的星渊棱晶数量")
				.defineInRange("boss_prism_count_high_tl", 1, 0, 8);
		BOSS_PRISM_TL_THRESHOLD = builder
				.comment("Boss 精华数量与星渊棱晶档位的共享 TL 阈值")
				.defineInRange("boss_prism_tl_threshold", 10, 0, 20);
		builder.pop();

		builder.comment("B15 卓越装备：两段升级、四项盔甲固定能力与词条运行时数值。")
				.push("eximus_equipment");
		EXIMUS_EQUIPMENT_ENABLED = builder
				.comment("B15 总开关；关闭后升级、固定能力、耐久保护、棱晶效果与全部装备配方失效，NBT 保留")
				.define("enable", true);
		EMERGENCY_MENDING_COOLDOWN_TICKS = builder
				.comment("应急生息冷却 tick")
				.defineInRange("emergency_mending_cooldown_ticks", 30, 1, 200);
		EMERGENCY_MENDING_HEAL_PERCENT = builder
				.comment("应急生息按最大生命计算的恢复百分比")
				.defineInRange("emergency_mending_heal_percent", 3.0D, 0.0D, 10.0D);
		EMERGENCY_MENDING_HEAL_MIN = builder
				.comment("应急生息最低治疗量")
				.defineInRange("emergency_mending_heal_min", 1.0D, 0.0D, 20.0D);
		EMERGENCY_MENDING_HEAL_MAX = builder
				.comment("应急生息最高治疗量")
				.defineInRange("emergency_mending_heal_max", 4.0D, 0.0D, 20.0D);
		SLOWNESS_GUARD_FLOOR_PERCENT = builder
				.comment("缓速抑制保留的正向移速基准百分比")
				.defineInRange("slowness_guard_floor_percent", 70.0D, 0.0D, 100.0D);
		STAR_STRIDE_SPEED_PERCENT = builder
				.comment("星履提供的移速百分比")
				.defineInRange("star_stride_speed_percent", 8.0D, 0.0D, 100.0D);
		STAR_STRIDE_STEP_HEIGHT = builder
				.comment("星履提供的台阶高度加成")
				.defineInRange("star_stride_step_height", 0.4D, 0.0D, 2.0D);
		SHARED_MARK_DURATION_TICKS = builder
				.comment("星痕刻印/远星标定共享标记持续 tick")
				.defineInRange("shared_mark_duration_ticks", 80, 20, 400);
		SHARED_MARK_DAMAGE_PERCENT = builder
				.comment("共享标记目标后续直接伤害增加百分比")
				.defineInRange("shared_mark_damage_percent", 10.0D, 0.0D, 100.0D);
		ARMOR_DURABILITY_CAP_DIVISOR = builder
				.comment("II 阶护甲单次耐久损耗上限公式除数")
				.defineInRange("armor_durability_cap_divisor", 100, 1, 1000);
		ARMOR_DURABILITY_CAP_MIN = builder
				.comment("II 阶护甲单次耐久损耗下限")
				.defineInRange("armor_durability_cap_min", 5, 1, 100);
		ARMOR_DURABILITY_CAP_MAX = builder
				.comment("II 阶护甲单次耐久损耗上限")
				.defineInRange("armor_durability_cap_max", 6, 1, 100);
		builder.pop();

		builder.comment("星渊棱晶抽取、揭示、刷新与全部三系词条数值。")
				.push("star_prism");
		STAR_PRISM_ENABLED = builder
				.comment("棱晶子系统开关；关闭后揭示、刷新、镶嵌和棱晶效果失效，升级与固定能力继续")
				.define("enable", true);
		STAR_PRISM_STANDARD_TIER_I_WEIGHT = builder
				.defineInRange("standard_tier_i_weight", 70.0D, 0.0D, 1000.0D);
		STAR_PRISM_STANDARD_TIER_II_WEIGHT = builder
				.defineInRange("standard_tier_ii_weight", 25.0D, 0.0D, 1000.0D);
		STAR_PRISM_STANDARD_TIER_III_WEIGHT = builder
				.defineInRange("standard_tier_iii_weight", 5.0D, 0.0D, 1000.0D);
		STAR_PRISM_ADVANCED_TIER_I_WEIGHT = builder
				.defineInRange("advanced_tier_i_weight", 35.0D, 0.0D, 1000.0D);
		STAR_PRISM_ADVANCED_TIER_II_WEIGHT = builder
				.defineInRange("advanced_tier_ii_weight", 50.0D, 0.0D, 1000.0D);
		STAR_PRISM_ADVANCED_TIER_III_WEIGHT = builder
				.defineInRange("advanced_tier_iii_weight", 15.0D, 0.0D, 1000.0D);
		for (int i = 0; i < STAR_PRISM_AFFIX_WEIGHTS.length; i++) {
			String id = StarPrismAffixes.all().get(i).id();
			STAR_PRISM_AFFIX_WEIGHTS[i] = builder
					.comment("词条 " + id + " 的抽中权重；0 只禁止未来抽取")
					.defineInRange("weights." + id, 1.0D, 0.0D, 1000.0D);
		}
		for (int i = 0; i < STAR_PRISM_DOUBLE_VALUES.length; i++) {
			StarPrismConfigDefinitions.DoubleDef definition =
					StarPrismConfigDefinitions.DOUBLE_VALUES.get(i);
			STAR_PRISM_DOUBLE_VALUES[i] = builder
					.comment("星渊数值 " + definition.key())
					.defineInRange("values." + definition.key(), definition.defaultValue(),
							definition.min(), definition.max());
		}
		for (int i = 0; i < STAR_PRISM_INT_VALUES.length; i++) {
			StarPrismConfigDefinitions.IntDef definition =
					StarPrismConfigDefinitions.INT_VALUES.get(i);
			STAR_PRISM_INT_VALUES[i] = builder
					.comment("星渊整数 " + definition.key())
					.defineInRange("values." + definition.key(), definition.defaultValue(),
							definition.min(), definition.max());
		}
		builder.pop();

		builder.comment("调试：全局 TL（-2 = 完全关闭；-1 = 自动 PCF/CDF；0~20 = 手动覆盖）。运行期用 /summythreat tl 修改，重启复位。")
				.push("scaling_debug");
		SCALING_GLOBAL_TL = builder
				.comment("启动时的默认全局 TL；-2 关闭，-1 自动 PCF/CDF")
				.defineInRange("global_tl", -1, -2, 20);
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
		REGENERATION_PERCENT_PER_SECOND = builder
				.comment("再生能力默认每秒恢复最大生命的百分比", "默认 1.0 表示每秒恢复 1% 最大生命")
				.defineInRange("regeneration_percent_per_second", 1.0D, 0.0D, 100.0D);
		REDUCED_REGENERATION_PERCENT_PER_SECOND = builder
				.comment("reduced_regeneration_entity_ids 中的实体每秒恢复最大生命的百分比",
						"默认 0.25 表示每秒恢复 0.25% 最大生命")
				.defineInRange("reduced_regeneration_percent_per_second", 0.25D, 0.0D, 100.0D);
		REDUCED_REGENERATION_ENTITY_IDS = builder
				.comment("使用低恢复比例的实体类型 ID 列表；按实体类型 ID 精确匹配，忽略大小写",
						"默认仅末影龙使用低恢复比例；从列表移除后恢复全局再生比例")
				.defineListAllowEmpty("reduced_regeneration_entity_ids",
						DEFAULT_REDUCED_REGENERATION_ENTITY_IDS,
						element -> element instanceof String);
		builder.pop();

		builder.comment("区域首领基础传奇抗性：永久被动防护层，不是实际护盾。")
				.push("legendary_resistance");
		LEGENDARY_RESISTANCE_ENABLED = builder
				.comment("是否启用区域首领基础传奇抗性")
				.define("enable", true);
		LEGENDARY_NEGATIVE_EFFECT_LEVEL_CAP = builder
				.comment("其它负面 MobEffect 的玩家可见等级上限；4 对应内部 amplifier=3")
				.defineInRange("negative_effect_level_cap", 4, 1, 10);
		LEGENDARY_IMMUNE_NEGATIVE_EFFECTS = builder
				.comment("直接免疫的负面 MobEffect ID 列表；未知 ID 忽略并只警告一次")
				.defineListAllowEmpty("immune_negative_effects",
						DEFAULT_IMMUNE_NEGATIVE_EFFECTS,
						element -> element instanceof String);
		LEGENDARY_KNOCKBACK_RESISTANCE = builder
				.comment("基础传奇抗性提供的 KNOCKBACK_RESISTANCE 加成")
				.defineInRange("knockback_resistance_bonus", 1.0D, 0.0D, 1.0D);
		LEGENDARY_BETRAYAL_IMMUNE = builder
				.comment("是否写入 SR 背叛免疫标签；SR 接口等待对方发布后生效")
				.define("betrayal_immune", true);
		builder.pop();

		SPEC = builder.build();
	}

	private Config() {
	}

	/** 三列法抗表均声明为 21 个独立配置值，禁止一列推导另一列。 */
	private static ForgeConfigSpec.DoubleValue[] defineMagicResistanceTable(
			ForgeConfigSpec.Builder builder, String prefix, double[] defaults) {
		if (defaults.length != 21) {
			throw new IllegalArgumentException("法抗 TL 表必须包含 21 项：" + prefix);
		}
		ForgeConfigSpec.DoubleValue[] values = new ForgeConfigSpec.DoubleValue[21];
		for (int tl = 0; tl < defaults.length; tl++) {
			values[tl] = builder.defineInRange(prefix + "_tl" + tl, defaults[tl], 0.0D, 100.0D);
		}
		return values;
	}
}
