package com.summy.threat.equipment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 三系 34 项星渊词条的单一注册来源。 */
public final class StarPrismAffixes {
	private static final List<StarPrismAffix> ALL = List.of(
			affix("astral_plating", EquipmentCategory.ARMOR, StarPrismTier.T1),
			affix("vital_constellation", EquipmentCategory.ARMOR, StarPrismTier.T1),
			affix("star_mantle", EquipmentCategory.ARMOR, StarPrismTier.T1),
			affix("thorns_insulation", EquipmentCategory.ARMOR, StarPrismTier.T1),
			affix("corrosion_baffle", EquipmentCategory.ARMOR, StarPrismTier.T2),
			affix("projectile_ward", EquipmentCategory.ARMOR, StarPrismTier.T2),
			affix("spellward_prism", EquipmentCategory.ARMOR, StarPrismTier.T2),
			affix("anchored_stride", EquipmentCategory.ARMOR, StarPrismTier.T2),
			affix("steady_heart", EquipmentCategory.ARMOR, StarPrismTier.T3),
			affix("last_light", EquipmentCategory.ARMOR, StarPrismTier.T3),
			affix("threat_aegis", EquipmentCategory.ARMOR, StarPrismTier.T3),
			affix("phase_anchor", EquipmentCategory.ARMOR, StarPrismTier.T3),

			affix("prism_edge", EquipmentCategory.MELEE, StarPrismTier.T1),
			affix("astral_haste", EquipmentCategory.MELEE, StarPrismTier.T1),
			affix("weakpoint_prism", EquipmentCategory.MELEE, StarPrismTier.T1),
			affix("concussive_prism", EquipmentCategory.MELEE, StarPrismTier.T1),
			affix("breach_constellation", EquipmentCategory.MELEE, StarPrismTier.T2),
			affix("shield_render", EquipmentCategory.MELEE, StarPrismTier.T2),
			affix("void_siphon", EquipmentCategory.MELEE, StarPrismTier.T2),
			affix("arcane_edge", EquipmentCategory.MELEE, StarPrismTier.T2),
			affix("starfire_cycle", EquipmentCategory.MELEE, StarPrismTier.T3),
			affix("terminal_star", EquipmentCategory.MELEE, StarPrismTier.T3),
			affix("abyssal_pursuit", EquipmentCategory.MELEE, StarPrismTier.T3),

			affix("starlit_bowstring", EquipmentCategory.RANGED, StarPrismTier.T1),
			affix("sidereal_aim", EquipmentCategory.RANGED, StarPrismTier.T1),
			affix("comet_fletching", EquipmentCategory.RANGED, StarPrismTier.T1),
			affix("starlight_dew", EquipmentCategory.RANGED, StarPrismTier.T1),
			affix("breach_star_arrow", EquipmentCategory.RANGED, StarPrismTier.T2),
			affix("shield_render_arrow", EquipmentCategory.RANGED, StarPrismTier.T2),
			affix("arcane_arrow", EquipmentCategory.RANGED, StarPrismTier.T2),
			affix("void_quiver", EquipmentCategory.RANGED, StarPrismTier.T2),
			affix("starfall_cycle_arrow", EquipmentCategory.RANGED, StarPrismTier.T3),
			affix("terminal_star_shot", EquipmentCategory.RANGED, StarPrismTier.T3),
			affix("abyssal_hunt_arrow", EquipmentCategory.RANGED, StarPrismTier.T3)
	);
	private static final Map<String, StarPrismAffix> BY_ID;
	private static final Map<EquipmentCategory, List<StarPrismAffix>> BY_CATEGORY;

	static {
		Map<String, StarPrismAffix> byId = new LinkedHashMap<>();
		Map<EquipmentCategory, List<StarPrismAffix>> byCategory =
				new EnumMap<>(EquipmentCategory.class);
		for (EquipmentCategory category : EquipmentCategory.values()) {
			byCategory.put(category, new ArrayList<>());
		}
		for (StarPrismAffix affix : ALL) {
			if (byId.putIfAbsent(affix.id(), affix) != null) {
				throw new IllegalStateException("重复星渊词条 ID：" + affix.id());
			}
			byCategory.get(affix.category()).add(affix);
		}
		BY_ID = Collections.unmodifiableMap(byId);
		Map<EquipmentCategory, List<StarPrismAffix>> frozen =
				new EnumMap<>(EquipmentCategory.class);
		byCategory.forEach((category, values) ->
				frozen.put(category, List.copyOf(values)));
		BY_CATEGORY = Collections.unmodifiableMap(frozen);
	}

	private StarPrismAffixes() {
	}

	public static List<StarPrismAffix> all() {
		return ALL;
	}

	public static List<StarPrismAffix> byCategory(EquipmentCategory category) {
		return BY_CATEGORY.get(category);
	}

	public static StarPrismAffix find(String id) {
		return id == null ? null : BY_ID.get(id);
	}

	public static StarPrismAffix require(String id) {
		StarPrismAffix affix = find(id);
		if (affix == null) {
			throw new IllegalArgumentException("未知星渊词条 ID：" + id);
		}
		return affix;
	}

	private static StarPrismAffix affix(String id, EquipmentCategory category,
			StarPrismTier tier) {
		return new StarPrismAffix(id, category, tier);
	}
}
