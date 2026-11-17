package com.summy.threat.legendary;

import java.util.Locale;

/** Boss 传奇动作固定 ID、抽取池和权重。 */
public enum LegendaryActionId {
	JADE_LIGHT("jade_light", Pool.GENERIC, 1.0D, 0x3BE0B0),
	MIRROR_STEP("mirror_step", Pool.GENERIC, 1.0D, 0xC9D8FF),
	COLLAPSING_HORIZON("collapsing_horizon", Pool.GENERIC, 1.0D, 0xD4A64E),
	LAIR_BREACH("lair_breach", Pool.GENERIC, 1.0D, 0xB57A45),
	DOOM_CLOCK("doom_clock", Pool.GENERIC, 1.0D, 0xE0B64B),
	REVERSE_GRAVITY("reverse_gravity", Pool.GENERIC, 1.0D, 0xB88CFF),
	LEGENDARY_RIPOSTE("legendary_riposte", Pool.GENERIC, 1.0D, 0xFF6B55),
	FRIGHTFUL_PRESENCE("frightful_presence", Pool.DRAGON_EXCLUSIVE, 1.5D, 0x8F5CFF),
	DRAGON_LAIR_PULSE("dragon_lair_pulse", Pool.DRAGON_EXCLUSIVE, 1.5D, 0xE26B3D),
	DRAGONFALL_EDICT("dragonfall_edict", Pool.BOSS_EXCLUSIVE, 1.5D, 0x6D4BB3),
	DECAY_TIDE("decay_tide", Pool.BOSS_EXCLUSIVE, 1.5D, 0x557A3B),
	ECHO_SENTENCE("echo_sentence", Pool.BOSS_EXCLUSIVE, 1.5D, 0x49D7DF);

	public enum Pool {
		GENERIC,
		BOSS_EXCLUSIVE,
		DRAGON_EXCLUSIVE
	}

	private final String id;
	private final Pool pool;
	private final double weight;
	private final int color;
	private final String translationKey;

	LegendaryActionId(String id, Pool pool, double weight, int color) {
		this.id = id;
		this.pool = pool;
		this.weight = weight;
		this.color = color;
		this.translationKey = "summy_threat.legendary_action." + id;
	}

	public String id() {
		return id;
	}

	public Pool pool() {
		return pool;
	}

	public double weight() {
		return weight;
	}

	public int color() {
		return color;
	}

	public String translationKey() {
		return translationKey;
	}

	public static LegendaryActionId byId(String raw) {
		if (raw == null) {
			return null;
		}
		String normalized = raw.trim().toLowerCase(Locale.ROOT);
		for (LegendaryActionId value : values()) {
			if (value.id.equals(normalized)) {
				return value;
			}
		}
		return null;
	}
}
