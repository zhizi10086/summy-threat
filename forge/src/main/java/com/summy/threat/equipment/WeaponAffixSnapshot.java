package com.summy.threat.equipment;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** 某一类别在命中或发射时生效的装备词条快照。 */
public record WeaponAffixSnapshot(int level, List<String> affixes) {
	private static final WeaponAffixSnapshot EMPTY = new WeaponAffixSnapshot(0, List.of());

	public WeaponAffixSnapshot {
		affixes = List.copyOf(affixes);
	}

	public static WeaponAffixSnapshot empty() {
		return EMPTY;
	}

	public static WeaponAffixSnapshot read(ItemStack stack, EquipmentCategory category) {
		return stack.isEmpty() ? EMPTY : read(stack.getTag(), category);
	}

	public static WeaponAffixSnapshot read(CompoundTag equipment, EquipmentCategory category) {
		if (!EximusEquipmentData.isValid(equipment) || category == null) {
			return EMPTY;
		}
		int level = EximusEquipmentData.upgradeLevel(equipment);
		List<String> affixes = new ArrayList<>(2);
		for (int slot = 0; slot < Math.min(level, EximusEquipmentData.SLOT_COUNT); slot++) {
			CompoundTag prism = EximusEquipmentData.prismTag(equipment, slot);
			if (prism.contains("tag", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
				prism = prism.getCompound("tag");
			}
			StarPrismData.read(prism).ifPresent(data -> {
				if (!data.revealed() || data.pendingRefresh() != null) {
					return;
				}
				String id = data.affix(category);
				StarPrismAffix affix = StarPrismAffixes.find(id);
				if (affix != null && affix.category() == category && !affixes.contains(id)) {
					affixes.add(id);
				}
			});
		}
		return new WeaponAffixSnapshot(level, affixes);
	}

	public boolean has(String id) {
		return affixes.contains(id);
	}

	public boolean markCapable() {
		return level >= 2;
	}
}
