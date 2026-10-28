package com.summy.threat.api;

import net.minecraft.resources.ResourceLocation;

/** KJS 注册的自定义 TL 能力元数据；实际行为由脚本在标准事件中实现。 */
public record AbilityDefinition(String id, int unlockTl, String displayName, int color,
		boolean rollable, int weight, boolean eximusOnly) {
	public AbilityDefinition {
		id = validateCustomId(id);
		displayName = validateName(displayName);
		if (unlockTl < 0 || unlockTl > 20) {
			throw new IllegalArgumentException("ability.unlockTl 必须在 0~20");
		}
		if (color < 0 || color > 0xFFFFFF) {
			throw new IllegalArgumentException("ability.color 必须在 0x000000~0xFFFFFF");
		}
		if (weight < 1 || weight > 1000) {
			throw new IllegalArgumentException("ability.weight 必须在 1~1000");
		}
	}

	private static String validateCustomId(String raw) {
		String id = raw == null ? "" : raw.trim();
		ResourceLocation parsed = ResourceLocation.tryParse(id);
		if (parsed == null || !id.contains(":") || !parsed.toString().equals(id)) {
			throw new IllegalArgumentException("自定义能力 ID 必须是小写 namespace:path：" + raw);
		}
		return id;
	}

	private static String validateName(String raw) {
		String name = raw == null ? "" : raw.trim();
		if (name.isEmpty() || name.length() > 128) {
			throw new IllegalArgumentException("ability.displayName 必须为 1~128 字符");
		}
		return name;
	}
}
