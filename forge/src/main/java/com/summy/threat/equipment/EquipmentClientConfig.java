package com.summy.threat.equipment;

import com.summy.threat.Config;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.LinkedHashMap;
import java.util.Map;

/** 服务端 B15 配置的只读客户端快照，供工具提示使用。 */
public final class EquipmentClientConfig {
	private static volatile Snapshot snapshot = Snapshot.empty();

	private EquipmentClientConfig() {
	}

	public static CompoundTag captureServer() {
		CompoundTag root = new CompoundTag();
		CompoundTag equipment = new CompoundTag();
		equipment.putBoolean("enable", read(Config.EXIMUS_EQUIPMENT_ENABLED));
		equipment.putInt("emergency_mending_cooldown_ticks",
				read(Config.EMERGENCY_MENDING_COOLDOWN_TICKS));
		equipment.putDouble("emergency_mending_heal_percent",
				read(Config.EMERGENCY_MENDING_HEAL_PERCENT));
		equipment.putDouble("emergency_mending_heal_min",
				read(Config.EMERGENCY_MENDING_HEAL_MIN));
		equipment.putDouble("emergency_mending_heal_max",
				read(Config.EMERGENCY_MENDING_HEAL_MAX));
		equipment.putDouble("slowness_guard_floor_percent",
				read(Config.SLOWNESS_GUARD_FLOOR_PERCENT));
		equipment.putDouble("star_stride_speed_percent",
				read(Config.STAR_STRIDE_SPEED_PERCENT));
		equipment.putDouble("star_stride_step_height",
				read(Config.STAR_STRIDE_STEP_HEIGHT));
		equipment.putInt("shared_mark_duration_ticks",
				read(Config.SHARED_MARK_DURATION_TICKS));
		equipment.putDouble("shared_mark_damage_percent",
				read(Config.SHARED_MARK_DAMAGE_PERCENT));
		equipment.putInt("armor_durability_cap_divisor",
				read(Config.ARMOR_DURABILITY_CAP_DIVISOR));
		equipment.putInt("armor_durability_cap_min",
				read(Config.ARMOR_DURABILITY_CAP_MIN));
		equipment.putInt("armor_durability_cap_max",
				read(Config.ARMOR_DURABILITY_CAP_MAX));
		root.put("eximus_equipment", equipment);

		CompoundTag prism = new CompoundTag();
		prism.putBoolean("enable", read(Config.STAR_PRISM_ENABLED));
		prism.putDouble("standard_tier_i_weight",
				read(Config.STAR_PRISM_STANDARD_TIER_I_WEIGHT));
		prism.putDouble("standard_tier_ii_weight",
				read(Config.STAR_PRISM_STANDARD_TIER_II_WEIGHT));
		prism.putDouble("standard_tier_iii_weight",
				read(Config.STAR_PRISM_STANDARD_TIER_III_WEIGHT));
		prism.putDouble("advanced_tier_i_weight",
				read(Config.STAR_PRISM_ADVANCED_TIER_I_WEIGHT));
		prism.putDouble("advanced_tier_ii_weight",
				read(Config.STAR_PRISM_ADVANCED_TIER_II_WEIGHT));
		prism.putDouble("advanced_tier_iii_weight",
				read(Config.STAR_PRISM_ADVANCED_TIER_III_WEIGHT));
		for (int i = 0; i < StarPrismAffixes.all().size(); i++) {
			prism.putDouble("weight." + StarPrismAffixes.all().get(i).id(),
					read(Config.STAR_PRISM_AFFIX_WEIGHTS[i]));
		}
		for (int i = 0; i < StarPrismConfigDefinitions.DOUBLE_VALUES.size(); i++) {
			StarPrismConfigDefinitions.DoubleDef definition =
					StarPrismConfigDefinitions.DOUBLE_VALUES.get(i);
			prism.putDouble("value." + definition.key(),
					read(Config.STAR_PRISM_DOUBLE_VALUES[i]));
		}
		for (int i = 0; i < StarPrismConfigDefinitions.INT_VALUES.size(); i++) {
			StarPrismConfigDefinitions.IntDef definition =
					StarPrismConfigDefinitions.INT_VALUES.get(i);
			prism.putInt("value." + definition.key(),
					read(Config.STAR_PRISM_INT_VALUES[i]));
		}
		root.put("star_prism", prism);
		return root;
	}

	public static void apply(CompoundTag root) {
		if (root == null || !root.contains("eximus_equipment", Tag.TAG_COMPOUND)
				|| !root.contains("star_prism", Tag.TAG_COMPOUND)) {
			snapshot = Snapshot.empty();
			return;
		}
		CompoundTag equipment = root.getCompound("eximus_equipment");
		CompoundTag prism = root.getCompound("star_prism");
		Map<String, Double> doubles = new LinkedHashMap<>();
		Map<String, Integer> integers = new LinkedHashMap<>();
		for (String key : equipment.getAllKeys()) {
			if (equipment.contains(key, Tag.TAG_DOUBLE)) {
				doubles.put("equipment." + key, equipment.getDouble(key));
			} else if (equipment.contains(key, Tag.TAG_INT)) {
				integers.put("equipment." + key, equipment.getInt(key));
			}
		}
		for (String key : prism.getAllKeys()) {
			if (prism.contains(key, Tag.TAG_DOUBLE)) {
				doubles.put("star." + key, prism.getDouble(key));
			} else if (prism.contains(key, Tag.TAG_INT)) {
				integers.put("star." + key, prism.getInt(key));
			}
		}
		snapshot = new Snapshot(equipment.getBoolean("enable"),
				prism.getBoolean("enable"), Map.copyOf(doubles), Map.copyOf(integers));
	}

	public static boolean equipmentEnabled() {
		return snapshot.equipmentEnabled();
	}

	public static boolean starPrismEnabled() {
		return snapshot.starPrismEnabled();
	}

	public static double doubleValue(String key) {
		Double value = snapshot.doubles().get(key);
		if (value != null) {
			return value;
		}
		return switchResourceDouble(key);
	}

	public static int intValue(String key) {
		Integer value = snapshot.integers().get(key);
		if (value != null) {
			return value;
		}
		return switchResourceInt(key);
	}

	private static double switchResourceDouble(String key) {
		if (key.startsWith("star.value.")) {
			return StarPrismConfig.doubleValue(key.substring("star.value.".length()));
		}
		return switch (key) {
			case "equipment.emergency_mending_heal_percent" ->
					read(Config.EMERGENCY_MENDING_HEAL_PERCENT);
			case "equipment.emergency_mending_heal_min" ->
					read(Config.EMERGENCY_MENDING_HEAL_MIN);
			case "equipment.emergency_mending_heal_max" ->
					read(Config.EMERGENCY_MENDING_HEAL_MAX);
			case "equipment.slowness_guard_floor_percent" ->
					read(Config.SLOWNESS_GUARD_FLOOR_PERCENT);
			case "equipment.star_stride_speed_percent" ->
					read(Config.STAR_STRIDE_SPEED_PERCENT);
			case "equipment.star_stride_step_height" ->
					read(Config.STAR_STRIDE_STEP_HEIGHT);
			case "equipment.shared_mark_damage_percent" ->
					read(Config.SHARED_MARK_DAMAGE_PERCENT);
			default -> 0.0D;
		};
	}

	private static int switchResourceInt(String key) {
		if (key.startsWith("star.value.")) {
			return StarPrismConfig.intValue(key.substring("star.value.".length()));
		}
		return switch (key) {
			case "equipment.emergency_mending_cooldown_ticks" ->
					read(Config.EMERGENCY_MENDING_COOLDOWN_TICKS);
			case "equipment.shared_mark_duration_ticks" ->
					read(Config.SHARED_MARK_DURATION_TICKS);
			case "equipment.armor_durability_cap_divisor" ->
					read(Config.ARMOR_DURABILITY_CAP_DIVISOR);
			case "equipment.armor_durability_cap_min" ->
					read(Config.ARMOR_DURABILITY_CAP_MIN);
			case "equipment.armor_durability_cap_max" ->
					read(Config.ARMOR_DURABILITY_CAP_MAX);
			default -> 0;
		};
	}

	private static boolean read(ForgeConfigSpec.BooleanValue value) {
		return Config.SPEC.isLoaded() ? value.get() : value.getDefault();
	}

	private static int read(ForgeConfigSpec.IntValue value) {
		return Config.SPEC.isLoaded() ? value.get() : value.getDefault();
	}

	private static double read(ForgeConfigSpec.DoubleValue value) {
		return Config.SPEC.isLoaded() ? value.get() : value.getDefault();
	}

	private record Snapshot(boolean equipmentEnabled, boolean starPrismEnabled,
			Map<String, Double> doubles, Map<String, Integer> integers) {
		private static Snapshot empty() {
			return new Snapshot(false, false, Map.of(), Map.of());
		}
	}
}
