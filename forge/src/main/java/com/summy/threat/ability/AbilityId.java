package com.summy.threat.ability;

import com.summy.threat.api.AbilityDefinition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * TL 能力 ID。
 *
 * <p>内置 ID 保持旧短名（swift、strength 等）；KJS 自定义 ID 必须是 namespace:path。
 * 自定义项只提供元数据，技能行为由脚本标准事件实现。
 */
public final class AbilityId {
	private static final int CUSTOM_LIMIT = 256;
	private static final Map<String, AbilityId> BUILTINS = new LinkedHashMap<>();
	private static final Map<String, AbilityId> CUSTOM = new LinkedHashMap<>();
	private static final Map<String, AbilityId> CLIENT_DISPLAY = new LinkedHashMap<>();

	public static final AbilityId SWIFT = builtIn("swift", 4, 0xF0F0F0);
	public static final AbilityId TOUGHNESS = builtIn("toughness", 4, 0xF0F0F0);
	public static final AbilityId REGENERATION = builtIn("regeneration", 6, 0xF0F0F0);
	public static final AbilityId STRENGTH = builtIn("strength", 6, 0xF0F0F0);
	public static final AbilityId FIREPROOF = builtIn("fireproof", 8, 0x3FA9FF);
	public static final AbilityId FRENZY = builtIn("frenzy", 8, 0x3FA9FF);
	public static final AbilityId THICK_HIDE = builtIn("thick_hide", 10, 0x3FA9FF);
	public static final AbilityId LIFESTEAL = builtIn("lifesteal", 12, 0xB45CFF);
	public static final AbilityId THORNS = builtIn("thorns", 12, 0xB45CFF);
	public static final AbilityId EMBER = builtIn("ember", 14, 0xB45CFF);
	public static final AbilityId NECRO_REINFORCEMENT = builtIn("necro_reinforcement", 16, 0xFFB300);
	public static final AbilityId CORROSION = builtIn("corrosion", 16, 0xFFB300);
	public static final AbilityId DEATH_BURST = builtIn("death_burst", 18, 0xFFB300);
	public static final AbilityId PHASE = builtIn("phase", 20, 0xFFB300);

	private final String id;
	private final int unlockTl;
	private final String displayName;
	private final int color;
	private final boolean rollable;
	private final int weight;
	private final boolean eximusOnly;
	private final boolean translated;
	private final boolean builtIn;

	private AbilityId(String id, int unlockTl, String displayName, int color,
			boolean rollable, int weight, boolean eximusOnly, boolean translated, boolean builtIn) {
		this.id = id;
		this.unlockTl = unlockTl;
		this.displayName = displayName;
		this.color = color;
		this.rollable = rollable;
		this.weight = weight;
		this.eximusOnly = eximusOnly;
		this.translated = translated;
		this.builtIn = builtIn;
	}

	public String id() {
		return id;
	}

	public int unlockTl() {
		return unlockTl;
	}

	public String displayName() {
		return displayName;
	}

	public int color() {
		return color;
	}

	public boolean rollable() {
		return rollable;
	}

	public int weight() {
		return weight;
	}

	public boolean eximusOnly() {
		return eximusOnly;
	}

	public boolean translated() {
		return translated;
	}

	public boolean builtIn() {
		return builtIn;
	}

	public int stage() {
		return stageOf(unlockTl);
	}

	public AbilityDefinition definition() {
		return toDefinition();
	}

	public static AbilityId byId(String raw) {
		if (raw == null) {
			return null;
		}
		String id = raw.trim().toLowerCase(Locale.ROOT);
		AbilityId builtIn = BUILTINS.get(id);
		if (builtIn != null) {
			return builtIn;
		}
		AbilityId custom = CUSTOM.get(id);
		return custom != null ? custom : CLIENT_DISPLAY.get(id);
	}

	public static AbilityId[] values() {
		Map<String, AbilityId> all = new LinkedHashMap<>(BUILTINS);
		all.putAll(CUSTOM);
		CLIENT_DISPLAY.forEach(all::putIfAbsent);
		return all.values().toArray(AbilityId[]::new);
	}

	public static AbilityId[] rollable(int tl, boolean eximus, boolean necroEligible) {
		List<AbilityId> out = new ArrayList<>();
		for (AbilityId value : values()) {
			if (value.rollable && value.unlockTl <= tl
					&& (!value.eximusOnly || eximus)
					&& (value != NECRO_REINFORCEMENT || necroEligible)) {
				out.add(value);
			}
		}
		return out.toArray(AbilityId[]::new);
	}

	public static AbilityId registerCustom(AbilityDefinition definition) {
		Objects.requireNonNull(definition, "definition");
		if (BUILTINS.containsKey(definition.id())) {
			throw new IllegalArgumentException("不能覆盖内置能力：" + definition.id());
		}
		if (!CUSTOM.containsKey(definition.id()) && CUSTOM.size() >= CUSTOM_LIMIT) {
			throw new IllegalStateException("自定义能力数量超过上限 " + CUSTOM_LIMIT);
		}
		AbilityId value = new AbilityId(definition.id(), definition.unlockTl(),
				definition.displayName(), definition.color(), definition.rollable(),
				definition.weight(), definition.eximusOnly(), false, false);
		CUSTOM.put(value.id, value);
		return value;
	}

	public static boolean unregisterCustom(String id) {
		return CUSTOM.remove(normalize(id)) != null;
	}

	public static void clearCustom() {
		CUSTOM.clear();
	}

	public static void replaceClientDefinitions(List<AbilityDefinition> definitions) {
		CLIENT_DISPLAY.clear();
		for (AbilityDefinition definition : definitions) {
			CLIENT_DISPLAY.put(definition.id(), new AbilityId(definition.id(), definition.unlockTl(),
					definition.displayName(), definition.color(), definition.rollable(),
					definition.weight(), definition.eximusOnly(), false, false));
		}
	}

	public static List<AbilityDefinition> customDefinitions() {
		return CUSTOM.values().stream().map(AbilityId::toDefinition).toList();
	}

	public static List<AbilityDefinition> clientDefinitions() {
		return CLIENT_DISPLAY.values().stream().map(AbilityId::toDefinition).toList();
	}

	public static int baseCountFor(int tl) {
		if (tl < 4) {
			return 0;
		}
		return Math.min(4, (tl - 4) / 4 + 1);
	}

	public static int stageOf(int tl) {
		if (tl < 4) {
			return 1;
		}
		return Math.min(5, (tl - 4) / 4 + 2);
	}

	private static AbilityId builtIn(String id, int unlockTl, int color) {
		AbilityId value = new AbilityId(id, unlockTl, "summy_threat.jade.ability." + id,
				color, true, 1, false, true, true);
		BUILTINS.put(id, value);
		return value;
	}

	private AbilityDefinition toDefinition() {
		return new AbilityDefinition(id, unlockTl, displayName, color, rollable, weight, eximusOnly);
	}

	private static String normalize(String id) {
		return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof AbilityId value && id.equals(value.id);
	}

	@Override
	public int hashCode() {
		return id.hashCode();
	}

	@Override
	public String toString() {
		return id;
	}
}
