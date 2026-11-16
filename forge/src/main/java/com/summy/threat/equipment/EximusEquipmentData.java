package com.summy.threat.equipment;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/** {@code summy_threat:eximus_upgrade} 的固定 schema 读写。 */
public final class EximusEquipmentData {
	public static final String EQUIPMENT_ROOT = "summy_threat:eximus_upgrade";
	public static final int SCHEMA = 1;
	public static final int SLOT_COUNT = 2;

	private EximusEquipmentData() {
	}

	public static int upgradeLevel(ItemStack stack) {
		return stack.isEmpty() ? 0 : upgradeLevel(stack.getTag());
	}

	public static int upgradeLevel(CompoundTag tag) {
		if (tag == null) {
			return 0;
		}
		CompoundTag root = root(tag);
		if (root == null || root.getInt("schema") != SCHEMA) {
			return 0;
		}
		int level = root.getByte("upgrade_level");
		return level >= 0 && level <= 2 ? level : 0;
	}

	public static boolean isValid(ItemStack stack) {
		return !stack.isEmpty() && isValid(stack.getTag());
	}

	public static boolean isValid(CompoundTag tag) {
		if (tag == null) {
			return false;
		}
		CompoundTag root = root(tag);
		if (root == null || root.getInt("schema") != SCHEMA) {
			return false;
		}
		int level = root.getByte("upgrade_level");
		if (level < 0 || level > 2) {
			return false;
		}
		ListTag prisms = root.getList("prisms", Tag.TAG_COMPOUND);
		return prisms.size() == SLOT_COUNT;
	}

	public static int prismCount(ItemStack stack) {
		return prismCount(stack.getTag());
	}

	public static int prismCount(CompoundTag tag) {
		return isValid(tag) ? SLOT_COUNT : 0;
	}

	public static ItemStack prism(ItemStack stack, int slot) {
		CompoundTag tag = prismTag(stack.getTag(), slot);
		return tag.isEmpty() ? ItemStack.EMPTY : ItemStack.of(tag);
	}

	public static CompoundTag prismTag(CompoundTag tag, int slot) {
		if (!isValid(tag) || slot < 0 || slot >= SLOT_COUNT) {
			return new CompoundTag();
		}
		ListTag prisms = root(tag).getList("prisms", Tag.TAG_COMPOUND);
		return prisms.getCompound(slot).copy();
	}

	public static ItemStack withPrism(ItemStack input, int slot, ItemStack prism) {
		ItemStack out = input.copy();
		CompoundTag prismTag = prism.isEmpty() ? new CompoundTag() : prism.save(new CompoundTag());
		out.setTag(withPrism(out.getTag(), slot, prismTag));
		return out;
	}

	public static CompoundTag withPrism(CompoundTag input, int slot, CompoundTag prism) {
		CompoundTag out = input == null ? new CompoundTag() : input.copy();
		if (slot < 0 || slot >= SLOT_COUNT) {
			throw new IllegalArgumentException("星渊槽索引必须在 0~1");
		}
		CompoundTag root = ensureRoot(out);
		ListTag prisms = root.getList("prisms", Tag.TAG_COMPOUND);
		if (prisms.size() != SLOT_COUNT) {
			prisms = emptyPrismList();
			root.put("prisms", prisms);
		}
		prisms.set(slot, prism == null ? new CompoundTag() : prism.copy());
		return out;
	}

	public static ItemStack withUpgradeLevel(ItemStack input, int level) {
		ItemStack out = input.copy();
		out.setTag(withUpgradeLevel(out.getTag(), level));
		return out;
	}

	public static CompoundTag withUpgradeLevel(CompoundTag input, int level) {
		if (level < 0 || level > 2) {
			throw new IllegalArgumentException("升级等级必须在 0~2");
		}
		CompoundTag out = input == null ? new CompoundTag() : input.copy();
		CompoundTag root = ensureRoot(out);
		root.putInt("schema", SCHEMA);
		root.putByte("upgrade_level", (byte) level);
		ListTag prisms = root.getList("prisms", Tag.TAG_COMPOUND);
		if (prisms.size() != SLOT_COUNT) {
			root.put("prisms", emptyPrismList());
		}
		return out;
	}

	/** 按槽位 1、槽位 2 的顺序返回第一个可用空槽；无空槽时返回 -1。 */
	public static int firstEmptySlot(ItemStack stack) {
		return firstEmptySlot(stack.getTag());
	}

	public static int firstEmptySlot(CompoundTag tag) {
		if (!isValid(tag)) {
			return -1;
		}
		int available = upgradeLevel(tag) >= 2 ? SLOT_COUNT : Math.min(1, SLOT_COUNT);
		for (int slot = 0; slot < available; slot++) {
			if (prismTag(tag, slot).isEmpty()) {
				return slot;
			}
		}
		return -1;
	}

	public static ItemStack clearPrisms(ItemStack input) {
		ItemStack out = input.copy();
		out.setTag(clearPrisms(out.getTag()));
		return out;
	}

	public static CompoundTag clearPrisms(CompoundTag input) {
		CompoundTag out = input == null ? new CompoundTag() : input.copy();
		if (!isValid(out)) {
			return out;
		}
		CompoundTag root = root(out);
		root.put("prisms", emptyPrismList());
		return out;
	}

	private static CompoundTag ensureRoot(ItemStack stack) {
		return ensureRoot(stack.getOrCreateTag());
	}

	private static CompoundTag ensureRoot(CompoundTag tag) {
		CompoundTag root = root(tag);
		if (root == null) {
			root = new CompoundTag();
			tag.put(EQUIPMENT_ROOT, root);
		}
		root.putInt("schema", SCHEMA);
		return root;
	}

	private static CompoundTag root(ItemStack stack) {
		return root(stack.getTag());
	}

	private static CompoundTag root(CompoundTag tag) {
		if (tag == null || !tag.contains(EQUIPMENT_ROOT, Tag.TAG_COMPOUND)) {
			return null;
		}
		return tag.getCompound(EQUIPMENT_ROOT);
	}

	private static ListTag emptyPrismList() {
		ListTag list = new ListTag();
		for (int i = 0; i < SLOT_COUNT; i++) {
			list.add(new CompoundTag());
		}
		return list;
	}
}
