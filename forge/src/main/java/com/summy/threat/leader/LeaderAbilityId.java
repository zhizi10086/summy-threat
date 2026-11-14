package com.summy.threat.leader;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 区域首领专用能力注册表；与普通 TL 能力、卓越者原型完全分离。 */
public final class LeaderAbilityId {
	public enum Type {
		AURA,
		ACTIVE
	}

	private static final List<LeaderAbilityId> BUILTINS = new ArrayList<>();

	public static final LeaderAbilityId COMMAND_AURA =
			builtIn("command_aura", Type.AURA, 10, 0.4D, 0xFFD45A);
	public static final LeaderAbilityId INTIMIDATION_AURA =
			builtIn("intimidation_aura", Type.AURA, 10, 0.4D, 0xD86955);
	public static final LeaderAbilityId BASTION_AURA =
			builtIn("bastion_aura", Type.AURA, 10, 0.4D, 0xE5B84B);
	public static final LeaderAbilityId SUPPRESSION_AURA =
			builtIn("suppression_aura", Type.AURA, 12, 0.4D, 0xB9A46B);
	public static final LeaderAbilityId BLOODLUST_AURA =
			builtIn("bloodlust_aura", Type.AURA, 14, 0.4D, 0xC43D37);

	public static final LeaderAbilityId TECTONIC_RUPTURE =
			builtIn("tectonic_rupture", Type.ACTIVE, 10, 1.0D, 0xD29A45);
	public static final LeaderAbilityId ARMOR_BREAKER =
			builtIn("armor_breaker", Type.ACTIVE, 12, 1.0D, 0xD16B4E);
	public static final LeaderAbilityId HUNT_RAY =
			builtIn("hunt_ray", Type.ACTIVE, 12, 1.0D, 0xF3E6A1);
	public static final LeaderAbilityId GRAVITY_PULSE =
			builtIn("gravity_pulse", Type.ACTIVE, 12, 1.0D, 0xA56BE8);
	public static final LeaderAbilityId PHASE_LUNGE =
			builtIn("phase_lunge", Type.ACTIVE, 14, 1.0D, 0xE8C44D);
	public static final LeaderAbilityId IRON_GUARD =
			builtIn("iron_guard", Type.ACTIVE, 14, 1.0D, 0xD9B74C);
	public static final LeaderAbilityId COLLAPSE_FIELD =
			builtIn("collapse_field", Type.ACTIVE, 14, 1.0D, 0x8E6547);

	private final String id;
	private final Type type;
	private final int unlockTl;
	private final double weight;
	private final int color;
	private final String displayName;

	private LeaderAbilityId(String id, Type type, int unlockTl, double weight, int color) {
		this.id = id;
		this.type = type;
		this.unlockTl = unlockTl;
		this.weight = weight;
		this.color = color;
		this.displayName = "summy_threat.leader_ability." + id;
	}

	public String id() {
		return id;
	}

	public Type type() {
		return type;
	}

	public boolean aura() {
		return type == Type.AURA;
	}

	public int unlockTl() {
		return unlockTl;
	}

	public double weight() {
		return weight;
	}

	public int color() {
		return color;
	}

	public String displayName() {
		return displayName;
	}

	public int stage() {
		return Math.min(5, (unlockTl - 2) / 4 + 1);
	}

	public static LeaderAbilityId byId(String raw) {
		if (raw == null) {
			return null;
		}
		String normalized = raw.trim().toLowerCase(Locale.ROOT);
		for (LeaderAbilityId value : BUILTINS) {
			if (value.id.equals(normalized)) {
				return value;
			}
		}
		return null;
	}

	public static LeaderAbilityId[] values() {
		return BUILTINS.toArray(LeaderAbilityId[]::new);
	}

	public static LeaderAbilityId[] auraValues() {
		return valuesOfType(Type.AURA);
	}

	public static LeaderAbilityId[] activeValues() {
		return valuesOfType(Type.ACTIVE);
	}

	private static LeaderAbilityId[] valuesOfType(Type type) {
		return BUILTINS.stream().filter(value -> value.type == type)
				.toArray(LeaderAbilityId[]::new);
	}

	private static LeaderAbilityId builtIn(String id, Type type, int unlockTl,
			double weight, int color) {
		LeaderAbilityId value = new LeaderAbilityId(id, type, unlockTl, weight, color);
		BUILTINS.add(value);
		return value;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof LeaderAbilityId value && id.equals(value.id);
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
