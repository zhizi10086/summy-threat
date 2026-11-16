package com.summy.threat.equipment;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/** {@code summy_threat:prism_data} 的三系词条、揭示与待刷新状态。 */
public record StarPrismData(int schema, boolean revealed, String pendingRefresh,
		String armorAffix, String meleeAffix, String rangedAffix) {
	public static final String PRISM_ROOT = "summy_threat:prism_data";
	public static final int SCHEMA = 1;

	public StarPrismData {
		if (pendingRefresh != null && !"standard".equals(pendingRefresh)
				&& !"advanced".equals(pendingRefresh)) {
			throw new IllegalArgumentException("未知刷新类型：" + pendingRefresh);
		}
	}

	public String affix(EquipmentCategory category) {
		return switch (category) {
			case ARMOR -> armorAffix;
			case MELEE -> meleeAffix;
			case RANGED -> rangedAffix;
		};
	}

	public static Optional<StarPrismData> read(ItemStack stack) {
		return stack.isEmpty() ? Optional.empty() : read(stack.getTag());
	}

	public static Optional<StarPrismData> read(CompoundTag tag) {
		if (tag == null || !tag.contains(PRISM_ROOT, Tag.TAG_COMPOUND)) {
			return Optional.empty();
		}
		CompoundTag root = tag.getCompound(PRISM_ROOT);
		int schema = root.getInt("schema");
		if (schema != SCHEMA) {
			return Optional.empty();
		}
		String pending = root.contains("pending_refresh", Tag.TAG_STRING)
				? root.getString("pending_refresh") : null;
		if (pending != null && !pending.isBlank()
				&& !"standard".equals(pending) && !"advanced".equals(pending)) {
			return Optional.empty();
		}
		CompoundTag affixes = root.getCompound("affixes");
		return Optional.of(new StarPrismData(schema, root.getBoolean("revealed"),
				pending == null || pending.isBlank() ? null : pending,
				stringOrNull(affixes, "armor"),
				stringOrNull(affixes, "melee"),
				stringOrNull(affixes, "ranged")));
	}

	public static void write(ItemStack stack, StarPrismData data) {
		write(stack.getOrCreateTag(), data);
	}

	public static void write(CompoundTag target, StarPrismData data) {
		CompoundTag root = new CompoundTag();
		root.putInt("schema", SCHEMA);
		root.putBoolean("revealed", data.revealed());
		if (data.pendingRefresh() != null) {
			root.putString("pending_refresh", data.pendingRefresh());
		}
		CompoundTag affixes = new CompoundTag();
		putIfPresent(affixes, "armor", data.armorAffix());
		putIfPresent(affixes, "melee", data.meleeAffix());
		putIfPresent(affixes, "ranged", data.rangedAffix());
		root.put("affixes", affixes);
		target.put(PRISM_ROOT, root);
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
