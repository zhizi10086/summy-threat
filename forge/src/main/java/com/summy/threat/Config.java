package com.summy.threat;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 模组配置（Forge 原生 SERVER 档）。
 *
 * <p>下界刻意钉在原版上限上（armor 30 / max_health 1024），配合 {@code AttributeCaps} 里的
 * {@code Math.max} 形成"只抬不压"的双保险：配置怎么写都不会把上限降到原版以下。
 *
 * <p>SERVER 档写在存档的 {@code serverconfig/summy_threat-server.toml}，Forge 会把服务端的值
 * 自动同步给客户端。
 */
public final class Config {
	/** 总开关：关闭时把上限写回原版值（运行期可逆） */
	public static final ForgeConfigSpec.BooleanValue ENABLE_ATTRIBUTE_CAP_OVERRIDE;
	/** generic.armor 的新上限 */
	public static final ForgeConfigSpec.DoubleValue ARMOR_MAX_VALUE;
	/** generic.max_health 的新上限 */
	public static final ForgeConfigSpec.DoubleValue MAX_HEALTH_MAX_VALUE;
	public static final ForgeConfigSpec SPEC;

	static {
		ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
		builder.comment(
				"属性上限抬升：只抬上限、不动下限；关闭开关会写回原版上限（armor 30 / max_health 1024）。",
				"属性值本身仍沿用原版语义（generic.armor / generic.max_health）。")
				.push("attribute_caps");
		ENABLE_ATTRIBUTE_CAP_OVERRIDE = builder
				.comment("是否抬升属性上限")
				.define("enable_attribute_cap_override", true);
		ARMOR_MAX_VALUE = builder
				.comment("generic.armor 的上限；低于原版 30 的值会被忽略（只抬不压）")
				.defineInRange("armor_max_value", 100.0D, 30.0D, 1_000_000.0D);
		MAX_HEALTH_MAX_VALUE = builder
				.comment("generic.max_health 的上限；低于原版 1024 的值会被忽略（只抬不压）")
				.defineInRange("max_health_max_value", 100_000.0D, 1024.0D, 10_000_000.0D);
		builder.pop();
		SPEC = builder.build();
	}

	private Config() {
	}
}
