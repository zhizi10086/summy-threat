package com.summy.threat.eximus;

import com.summy.threat.api.ArchetypeDefinition;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** 卓越者专属原型 ID：8 个内置项 + KJS 运行期自定义项。 */
public final class EximusArchetype {
	private static final int CUSTOM_LIMIT = 256;
	private static final Map<String, EximusArchetype> BUILTINS = new LinkedHashMap<>();
	private static final Map<String, EximusArchetype> CUSTOM = new LinkedHashMap<>();
	private static final Map<String, EximusArchetype> CLIENT_DISPLAY = new LinkedHashMap<>();

	public static final EximusArchetype FLAME = builtIn("flame", "烈焰", 0xFF7A00);
	public static final EximusArchetype FROST = builtIn("frost", "冰霜", 0x7DD3FC);
	public static final EximusArchetype POISON = builtIn("poison", "剧毒", 0x55C96B);
	public static final EximusArchetype STORM = builtIn("storm", "电涌", 0x57C7FF);
	public static final EximusArchetype SHOCK = builtIn("shock", "震荡", 0xFFD25A);
	public static final EximusArchetype GUARDIAN = builtIn("guardian", "守护", 0xFFC44D);
	public static final EximusArchetype VAMPIRE = builtIn("vampire", "吸血", 0xD6344F);
	public static final EximusArchetype MAGNET = builtIn("magnet", "磁力", 0xB778FF);

	private final String id;
	private final String displayName;
	private final int color;
	private final boolean rollable;
	private final int weight;
	private final boolean translated;
	private final boolean builtIn;

	private EximusArchetype(String id, String displayName, int color,
			boolean rollable, int weight, boolean translated, boolean builtIn) {
		this.id = id;
		this.displayName = displayName;
		this.color = color;
		this.rollable = rollable;
		this.weight = weight;
		this.translated = translated;
		this.builtIn = builtIn;
	}

	public String id() {
		return id;
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

	public boolean translated() {
		return translated;
	}

	public boolean builtIn() {
		return builtIn;
	}

	public ArchetypeDefinition definition() {
		return toDefinition();
	}

	public static EximusArchetype byId(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		String id = raw.trim().toLowerCase(Locale.ROOT);
		EximusArchetype builtIn = BUILTINS.get(id);
		if (builtIn != null) {
			return builtIn;
		}
		EximusArchetype custom = CUSTOM.get(id);
		return custom != null ? custom : CLIENT_DISPLAY.get(id);
	}

	public static EximusArchetype[] values() {
		Map<String, EximusArchetype> all = new LinkedHashMap<>(BUILTINS);
		all.putAll(CUSTOM);
		CLIENT_DISPLAY.forEach(all::putIfAbsent);
		return all.values().toArray(EximusArchetype[]::new);
	}

	public static EximusArchetype[] rollableValues() {
		List<EximusArchetype> out = new ArrayList<>();
		for (EximusArchetype archetype : values()) {
			if (archetype.rollable) {
				out.add(archetype);
			}
		}
		return out.toArray(EximusArchetype[]::new);
	}

	public static EximusArchetype random(RandomSource random) {
		EximusArchetype[] values = rollableValues();
		if (values.length == 0) {
			return FLAME;
		}
		int total = 0;
		for (EximusArchetype value : values) {
			total += value.weight;
		}
		int roll = random.nextInt(Math.max(1, total));
		for (EximusArchetype value : values) {
			roll -= value.weight;
			if (roll < 0) {
				return value;
			}
		}
		return values[values.length - 1];
	}

	public static EximusArchetype registerCustom(ArchetypeDefinition definition) {
		Objects.requireNonNull(definition, "definition");
		if (BUILTINS.containsKey(definition.id())) {
			throw new IllegalArgumentException("不能覆盖内置原型：" + definition.id());
		}
		if (!CUSTOM.containsKey(definition.id()) && CUSTOM.size() >= CUSTOM_LIMIT) {
			throw new IllegalStateException("自定义原型数量超过上限 " + CUSTOM_LIMIT);
		}
		EximusArchetype value = new EximusArchetype(definition.id(), definition.displayName(),
				definition.color(), definition.rollable(), definition.weight(), false, false);
		CUSTOM.put(value.id, value);
		return value;
	}

	public static boolean unregisterCustom(String id) {
		return CUSTOM.remove(normalize(id)) != null;
	}

	public static void clearCustom() {
		CUSTOM.clear();
	}

	public static void replaceClientDefinitions(List<ArchetypeDefinition> definitions) {
		CLIENT_DISPLAY.clear();
		for (ArchetypeDefinition definition : definitions) {
			CLIENT_DISPLAY.put(definition.id(), new EximusArchetype(definition.id(),
					definition.displayName(), definition.color(), definition.rollable(),
					definition.weight(), false, false));
		}
	}

	public static List<ArchetypeDefinition> customDefinitions() {
		return CUSTOM.values().stream().map(EximusArchetype::toDefinition).toList();
	}

	public static List<ArchetypeDefinition> clientDefinitions() {
		return CLIENT_DISPLAY.values().stream().map(EximusArchetype::toDefinition).toList();
	}

	public static String displayNameOf(String raw) {
		EximusArchetype archetype = byId(raw);
		return archetype == null ? "无" : archetype.displayName;
	}

	private static EximusArchetype builtIn(String id, String displayName, int color) {
		EximusArchetype value = new EximusArchetype(id, displayName, color, true, 1, true, true);
		BUILTINS.put(id, value);
		return value;
	}

	private ArchetypeDefinition toDefinition() {
		return new ArchetypeDefinition(id, displayName, color, rollable, weight);
	}

	private static String normalize(String id) {
		return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof EximusArchetype value && id.equals(value.id);
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
