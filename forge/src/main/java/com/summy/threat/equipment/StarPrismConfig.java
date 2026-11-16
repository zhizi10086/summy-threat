package com.summy.threat.equipment;

import com.summy.threat.Config;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.LinkedHashMap;
import java.util.Map;

/** 把固定注册表映射到 Config 中的独立权重和数值字段。 */
public final class StarPrismConfig {
	private static final Map<String, ForgeConfigSpec.DoubleValue> WEIGHTS = new LinkedHashMap<>();
	private static final Map<String, ForgeConfigSpec.DoubleValue> DOUBLE_VALUES =
			new LinkedHashMap<>();
	private static final Map<String, ForgeConfigSpec.IntValue> INT_VALUES =
			new LinkedHashMap<>();

	static {
		for (int i = 0; i < StarPrismAffixes.all().size(); i++) {
			WEIGHTS.put(StarPrismAffixes.all().get(i).id(), Config.STAR_PRISM_AFFIX_WEIGHTS[i]);
		}
		for (int i = 0; i < StarPrismConfigDefinitions.DOUBLE_VALUES.size(); i++) {
			DOUBLE_VALUES.put(StarPrismConfigDefinitions.DOUBLE_VALUES.get(i).key(),
					Config.STAR_PRISM_DOUBLE_VALUES[i]);
		}
		for (int i = 0; i < StarPrismConfigDefinitions.INT_VALUES.size(); i++) {
			INT_VALUES.put(StarPrismConfigDefinitions.INT_VALUES.get(i).key(),
					Config.STAR_PRISM_INT_VALUES[i]);
		}
	}

	private StarPrismConfig() {
	}

	public static boolean enabled() {
		return read(Config.STAR_PRISM_ENABLED);
	}

	public static double weight(String affixId) {
		ForgeConfigSpec.DoubleValue value = WEIGHTS.get(affixId);
		if (value == null) {
			return 0.0D;
		}
		return read(value);
	}

	public static double doubleValue(String key) {
		ForgeConfigSpec.DoubleValue value = DOUBLE_VALUES.get(key);
		if (value == null) {
			throw new IllegalArgumentException("未知星渊数值键：" + key);
		}
		return read(value);
	}

	public static int intValue(String key) {
		ForgeConfigSpec.IntValue value = INT_VALUES.get(key);
		if (value == null) {
			throw new IllegalArgumentException("未知星渊整数键：" + key);
		}
		return Config.SPEC.isLoaded() ? value.get() : value.getDefault();
	}

	public static PrismRollSettings rollSettings() {
		return new PrismRollSettings(
				read(Config.STAR_PRISM_STANDARD_TIER_I_WEIGHT),
				read(Config.STAR_PRISM_STANDARD_TIER_II_WEIGHT),
				read(Config.STAR_PRISM_STANDARD_TIER_III_WEIGHT),
				read(Config.STAR_PRISM_ADVANCED_TIER_I_WEIGHT),
				read(Config.STAR_PRISM_ADVANCED_TIER_II_WEIGHT),
				read(Config.STAR_PRISM_ADVANCED_TIER_III_WEIGHT));
	}

	public static StarPrismRoller.WeightProvider weightProvider() {
		return affix -> weight(affix.id());
	}

	private static boolean read(ForgeConfigSpec.BooleanValue value) {
		return Config.SPEC.isLoaded() ? value.get() : value.getDefault();
	}

	private static double read(ForgeConfigSpec.DoubleValue value) {
		return Config.SPEC.isLoaded() ? value.get() : value.getDefault();
	}
}
