package com.summy.threat.eximus;

import net.minecraft.util.RandomSource;

import java.util.Locale;

/**
 * 卓越者专属原型 id。
 *
 * <p>本轮只负责“类型标签 + 指令指定”，不在这里写技能效果；后续能力/粒子系统按
 * {@code summy_threat:archetype} 读取即可。
 */
public enum EximusArchetype {
	FLAME("flame", "烈焰"),
	FROST("frost", "冰霜"),
	POISON("poison", "剧毒"),
	STORM("storm", "电涌"),
	SHOCK("shock", "震荡"),
	GUARDIAN("guardian", "守护"),
	VAMPIRE("vampire", "吸血"),
	MAGNET("magnet", "磁力");

	private final String id;
	private final String displayName;

	EximusArchetype(String id, String displayName) {
		this.id = id;
		this.displayName = displayName;
	}

	public String id() {
		return id;
	}

	public String displayName() {
		return displayName;
	}

	public static EximusArchetype byId(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		String value = raw.trim().toLowerCase(Locale.ROOT);
		for (EximusArchetype archetype : values()) {
			if (archetype.id.equals(value)) {
				return archetype;
			}
		}
		return null;
	}

	public static EximusArchetype random(RandomSource random) {
		EximusArchetype[] values = values();
		return values[random.nextInt(values.length)];
	}

	public static String displayNameOf(String raw) {
		EximusArchetype archetype = byId(raw);
		return archetype == null ? "无" : archetype.displayName;
	}
}
