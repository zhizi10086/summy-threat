package com.summy.threat.equipment;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.Optional;

/** 不依赖运行时菜单的装备操作规则；配方与 DevCheck 共用。 */
public final class EquipmentRules {
	/**
	 * 阶段 2 起三系装备全部开放，阶段 3 只补外部模组桥接。
	 */
	private static final boolean WEAPONS_ENABLED = true;

	private EquipmentRules() {
	}

	public static boolean categoryEnabled(EquipmentCategory category) {
		if (category == EquipmentCategory.ARMOR) {
			return true;
		}
		return WEAPONS_ENABLED && category != null;
	}

	public static boolean canUpgrade(CompoundTag equipment, int targetLevel,
			EquipmentCategory category) {
		if (!categoryEnabled(category) || targetLevel < 1 || targetLevel > 2) {
			return false;
		}
		if (!hasEquipmentData(equipment)) {
			return targetLevel == 1;
		}
		if (!EximusEquipmentData.isValid(equipment)) {
			return false;
		}
		return EximusEquipmentData.upgradeLevel(equipment) + 1 == targetLevel;
	}

	/**
	 * 返回按槽位 1、槽位 2 顺序选择的最小可用空槽。
	 * 返回 -1 表示配方不应匹配。
	 */
	public static int embeddingSlot(CompoundTag equipment, CompoundTag prism,
			EquipmentCategory category) {
		if (!categoryEnabled(category) || !EximusEquipmentData.isValid(equipment)) {
			return -1;
		}
		Optional<StarPrismData> decoded = StarPrismData.read(prism);
		if (decoded.isEmpty()) {
			return -1;
		}
		StarPrismData prismData = decoded.get();
		if (!prismData.revealed() || prismData.pendingRefresh() != null) {
			return -1;
		}
		String incoming = prismData.affix(category);
		StarPrismAffix affix = StarPrismAffixes.find(incoming);
		if (affix == null || affix.category() != category) {
			return -1;
		}

		int empty = EximusEquipmentData.firstEmptySlot(equipment);
		if (empty < 0) {
			return -1;
		}
		for (int slot = 0; slot < EximusEquipmentData.SLOT_COUNT; slot++) {
			CompoundTag existing = EximusEquipmentData.prismTag(equipment, slot);
			if (existing.isEmpty()) {
				continue;
			}
			Optional<StarPrismData> existingData = StarPrismData.read(existing);
			if (existingData.isEmpty()
					&& existing.contains("tag", Tag.TAG_COMPOUND)) {
				existingData = StarPrismData.read(existing.getCompound("tag"));
			}
			if (existingData.isEmpty()) {
				return -1;
			}
			if (incoming.equals(existingData.get().affix(category))) {
				return -1;
			}
		}
		return empty;
	}

	private static boolean hasEquipmentData(CompoundTag equipment) {
		return equipment != null
				&& equipment.contains(EximusEquipmentData.EQUIPMENT_ROOT);
	}
}
