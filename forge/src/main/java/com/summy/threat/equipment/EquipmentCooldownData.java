package com.summy.threat.equipment;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** 玩家 ForgeData 中按词条 ID 保存的绝对到期 tick。 */
public final class EquipmentCooldownData {
	public static final String ROOT = "summy_threat:equipment_cooldowns";

	private EquipmentCooldownData() {
	}

	public static long remaining(CompoundTag data, String id, long now) {
		if (data == null || id == null || !data.contains(ROOT, Tag.TAG_COMPOUND)) {
			return 0L;
		}
		CompoundTag root = data.getCompound(ROOT);
		if (!root.contains(id, Tag.TAG_LONG)) {
			return 0L;
		}
		return Math.max(0L, root.getLong(id) - now);
	}

	public static boolean ready(CompoundTag data, String id, long now) {
		return remaining(data, id, now) <= 0L;
	}

	public static void set(CompoundTag data, String id, long expiresAt) {
		if (data == null || id == null || id.isBlank()) {
			return;
		}
		CompoundTag root = data.contains(ROOT, Tag.TAG_COMPOUND)
				? data.getCompound(ROOT) : new CompoundTag();
		root.putLong(id, expiresAt);
		data.put(ROOT, root);
	}

	public static void clear(CompoundTag data, String id) {
		if (data != null && id != null && data.contains(ROOT, Tag.TAG_COMPOUND)) {
			data.getCompound(ROOT).remove(id);
		}
	}
}
