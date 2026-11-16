package com.summy.threat.equipment;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.Optional;
import java.util.UUID;

/** 投射物发射时的星渊词条快照。 */
public record PrismProjectileData(int schema, UUID shotId, int fixedLevel,
		String armorAffix, String meleeAffix, String rangedAffix, boolean markCapable,
		boolean siderealAim, boolean terminalStarShot, boolean velocityApplied,
		boolean pierceApplied) {
	public static final String ROOT = "summy_threat:prism_projectile";
	public static final int SCHEMA = 1;

	public PrismProjectileData {
		if (schema != SCHEMA || shotId == null || fixedLevel < 0 || fixedLevel > 2) {
			throw new IllegalArgumentException("非法星渊投射物快照");
		}
	}

	public static Optional<PrismProjectileData> read(CompoundTag root) {
		if (root == null || !root.contains(ROOT, Tag.TAG_COMPOUND)) {
			return Optional.empty();
		}
		CompoundTag data = root.getCompound(ROOT);
		int schema = data.getInt("schema");
		if (schema != SCHEMA || !data.hasUUID("shot_id")) {
			return Optional.empty();
		}
		int fixedLevel = data.getByte("fixed_level");
		if (fixedLevel < 0 || fixedLevel > 2) {
			return Optional.empty();
		}
		CompoundTag affixes = data.getCompound("affixes");
		return Optional.of(new PrismProjectileData(schema, data.getUUID("shot_id"),
				fixedLevel, stringOrNull(affixes, "armor"),
				stringOrNull(affixes, "melee"), stringOrNull(affixes, "ranged"),
				data.getBoolean("mark_capable"), data.getBoolean("sidereal_aim"),
				data.getBoolean("terminal_star_shot"),
				data.getBoolean("velocity_applied"), data.getBoolean("pierce_applied")));
	}

	public static void write(CompoundTag root, PrismProjectileData value) {
		CompoundTag data = new CompoundTag();
		data.putInt("schema", SCHEMA);
		data.putUUID("shot_id", value.shotId());
		data.putByte("fixed_level", (byte) value.fixedLevel());
		CompoundTag affixes = new CompoundTag();
		putIfPresent(affixes, "armor", value.armorAffix());
		putIfPresent(affixes, "melee", value.meleeAffix());
		putIfPresent(affixes, "ranged", value.rangedAffix());
		data.put("affixes", affixes);
		data.putBoolean("mark_capable", value.markCapable());
		data.putBoolean("sidereal_aim", value.siderealAim());
		data.putBoolean("terminal_star_shot", value.terminalStarShot());
		data.putBoolean("velocity_applied", value.velocityApplied());
		data.putBoolean("pierce_applied", value.pierceApplied());
		root.put(ROOT, data);
	}

	private static String stringOrNull(CompoundTag tag, String key) {
		return tag.contains(key, Tag.TAG_STRING) ? tag.getString(key) : null;
	}

	private static void putIfPresent(CompoundTag tag, String key, String value) {
		if (value != null && !value.isBlank()) {
			tag.putString(key, value);
		}
	}
}
