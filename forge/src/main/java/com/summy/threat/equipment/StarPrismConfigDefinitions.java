package com.summy.threat.equipment;

import java.util.List;

/** 星渊词条的全部数值配置定义，按固定顺序生成 ForgeConfigSpec 字段。 */
public final class StarPrismConfigDefinitions {
	public record DoubleDef(String key, double defaultValue, double min, double max) {
	}

	public record IntDef(String key, int defaultValue, int min, int max) {
	}

	public static final List<DoubleDef> DOUBLE_VALUES = List.of(
			d("armor.astral_plating.armor_value_per_stack", 6.0D, 0.0D, 1000.0D),
			d("armor.astral_plating.toughness_value_per_stack", 4.0D, 0.0D, 1000.0D),
			d("armor.vital_constellation.max_health_per_stack", 6.0D, 0.0D, 1000.0D),
			d("armor.star_mantle.reduction_percent_per_stack", 4.0D, 0.0D, 100.0D),
			d("armor.corrosion_baffle.suppression_percent", 40.0D, 0.0D, 100.0D),
			d("armor.projectile_ward.reduction_percent", 12.0D, 0.0D, 100.0D),
			d("armor.spellward_prism.reduction_percent", 12.0D, 0.0D, 100.0D),
			d("armor.anchored_stride.knockback_bonus", 0.25D, 0.0D, 1.0D),
			d("armor.anchored_stride.fall_reduction_percent", 50.0D, 0.0D, 100.0D),
			d("armor.steady_heart.threshold_percent", 50.0D, 0.0D, 100.0D),
			d("armor.steady_heart.reduction_percent", 35.0D, 0.0D, 100.0D),
			d("armor.last_light.health_floor_percent", 20.0D, 0.0D, 100.0D),
			d("armor.threat_aegis.reduction_percent", 30.0D, 0.0D, 100.0D),

			d("melee.prism_edge.attack_bonus", 8.5D, 0.0D, 1000.0D),
			d("melee.astral_haste.attack_speed_percent", 30.0D, 0.0D, 1000.0D),
			d("melee.weakpoint_prism.crit_chance_percent", 45.0D, 0.0D, 100.0D),
			d("melee.weakpoint_prism.crit_multiplier_bonus", 0.25D, 0.0D, 100.0D),
			d("melee.concussive_prism.knockback_bonus", 1.0D, 0.0D, 10.0D),
			d("melee.concussive_prism.damage_per_resistance_percent", 15.0D, 0.0D, 100.0D),
			d("melee.breach_constellation.armor_reduction_points", 5.0D, 0.0D, 100.0D),
			d("melee.shield_render.bonus_percent", 45.0D, 0.0D, 1000.0D),
			d("melee.void_siphon.lifesteal_percent", 30.0D, 0.0D, 100.0D),
			d("melee.arcane_edge.magic_percent", 20.0D, 0.0D, 1000.0D),
			d("melee.starfire_cycle.magic_percent", 100.0D, 0.0D, 1000.0D),
			d("melee.terminal_star.chance_percent", 10.0D, 0.0D, 100.0D),
			d("melee.terminal_star.multiplier", 7.0D, 1.0D, 100.0D),
			d("melee.abyssal_pursuit.damage_per_stack_percent", 6.0D, 0.0D, 100.0D),
			d("melee.abyssal_pursuit.attack_speed_per_stack_percent", 5.0D, 0.0D, 100.0D),

			d("ranged.starlit_bowstring.damage_percent", 30.0D, 0.0D, 1000.0D),
			d("ranged.sidereal_aim.chance_percent", 45.0D, 0.0D, 100.0D),
			d("ranged.sidereal_aim.multiplier", 1.8D, 1.0D, 100.0D),
			d("ranged.comet_fletching.velocity_percent", 15.0D, 0.0D, 100.0D),
			d("ranged.starlight_dew.heal_flat", 1.0D, 0.0D, 20.0D),
			d("ranged.starlight_dew.heal_percent", 2.0D, 0.0D, 100.0D),
			d("ranged.starlight_dew.heal_max", 4.0D, 0.0D, 20.0D),
			d("ranged.breach_star_arrow.armor_reduction_points", 6.0D, 0.0D, 100.0D),
			d("ranged.shield_render_arrow.bonus_percent", 60.0D, 0.0D, 1000.0D),
			d("ranged.arcane_arrow.magic_percent", 20.0D, 0.0D, 1000.0D),
			d("ranged.void_quiver.lifesteal_percent", 30.0D, 0.0D, 100.0D),
			d("ranged.starfall_cycle_arrow.magic_percent", 75.0D, 0.0D, 1000.0D),
			d("ranged.terminal_star_shot.chance_percent", 7.5D, 0.0D, 100.0D),
			d("ranged.terminal_star_shot.multiplier", 11.0D, 1.0D, 100.0D),
			d("ranged.abyssal_hunt_arrow.damage_per_stack_percent", 15.0D,
					0.0D, 100.0D)
	);

	public static final List<IntDef> INT_VALUES = List.of(
			i("armor.basic_stack_limit", 2, 1, 4),
			i("armor.steady_heart.duration_ticks", 80, 1, 72000),
			i("armor.steady_heart.cooldown_ticks", 200, 1, 72000),
			i("armor.last_light.guard_ticks", 40, 1, 72000),
			i("armor.last_light.cooldown_ticks", 3600, 1, 72000),
			i("armor.phase_anchor.cooldown_ticks", 400, 1, 72000),
			i("melee.starfire_cycle.hits", 4, 1, 100),
			i("melee.abyssal_pursuit.max_stacks", 5, 1, 100),
			i("melee.abyssal_pursuit.reset_ticks", 60, 1, 72000),
			i("ranged.comet_fletching.pierce_bonus", 1, 0, 20),
			i("ranged.starlight_dew.cooldown_ticks", 40, 1, 72000),
			i("ranged.starfall_cycle_arrow.hits", 3, 1, 100),
			i("ranged.abyssal_hunt_arrow.max_stacks", 5, 1, 100),
			i("ranged.abyssal_hunt_arrow.reset_ticks", 100, 1, 72000)
	);

	private StarPrismConfigDefinitions() {
	}

	private static DoubleDef d(String key, double defaultValue, double min, double max) {
		return new DoubleDef(key, defaultValue, min, max);
	}

	private static IntDef i(String key, int defaultValue, int min, int max) {
		return new IntDef(key, defaultValue, min, max);
	}
}
