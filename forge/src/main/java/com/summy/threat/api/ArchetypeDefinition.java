package com.summy.threat.api;

import net.minecraft.resources.ResourceLocation;

/** KJS 注册的自定义卓越者原型元数据；内置原型技能不会自动套用到自定义原型。 */
public record ArchetypeDefinition(String id, String displayName, int color,
		boolean rollable, int weight) {
	public ArchetypeDefinition {
		id = validateCustomId(id);
		displayName = validateName(displayName);
		if (color < 0 || color > 0xFFFFFF) {
			throw new IllegalArgumentException("archetype.color 必须在 0x000000~0xFFFFFF");
		}
		if (weight < 1 || weight > 1000) {
			throw new IllegalArgumentException("archetype.weight 必须在 1~1000");
		}
	}

	private static String validateCustomId(String raw) {
		String id = raw == null ? "" : raw.trim();
		ResourceLocation parsed = ResourceLocation.tryParse(id);
		if (parsed == null || !id.contains(":") || !parsed.toString().equals(id)) {
			throw new IllegalArgumentException("自定义原型 ID 必须是小写 namespace:path：" + raw);
		}
		return id;
	}

	private static String validateName(String raw) {
		String name = raw == null ? "" : raw.trim();
		if (name.isEmpty() || name.length() > 128) {
			throw new IllegalArgumentException("archetype.displayName 必须为 1~128 字符");
		}
		return name;
	}
}
