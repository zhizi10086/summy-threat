package com.summy.threat;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 模组配置（Forge 原生 SERVER 档）。
 *
 * <p>两组内容：
 * <ul>
 *     <li>{@code attribute_caps}：把被硬编码封顶的属性上限抬起来；下界刻意钉在原版上限上，
 *         配合 {@code AttributeCaps} 里的 {@code Math.max} 形成"只抬不压"的双保险。</li>
 *     <li>{@code damage_reduction}：自定义护甲减伤公式的常量（口径见 docs/护甲公式计算.txt）。</li>
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

		SPEC = builder.build();
	}

	private Config() {
	}
}
