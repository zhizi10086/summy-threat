package com.summy.threat.ability;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/**
 * 能力名文本工具：按"解锁 TL 阶段"上色（Jade 与命令输出共用同一套色）。
 * 阶段色：4-7 白 / 8-11 蓝 / 12-15 紫 / 16-20 金（0-3 灰，仅占位）。
 */
public final class AbilityTexts {
	private static final int[] STAGE_COLORS = {0x9E9E9E, 0xF0F0F0, 0x3FA9FF, 0xB45CFF, 0xFFB300};

	private AbilityTexts() {
	}

	public static int colorOf(int stage) {
		return STAGE_COLORS[Math.max(1, Math.min(5, stage)) - 1];
	}

	/** 单个能力名（带阶段色）。 */
	public static MutableComponent name(AbilityId id) {
		return Component.translatable("summy_threat.jade.ability." + id.id())
				.withStyle(Style.EMPTY.withColor(TextColor.fromRgb(colorOf(id.stage()))));
	}

	/** 逗号分隔的 id 串 → 用分隔符拼接的彩色组件；空串返回"无"。 */
	public static Component join(String csv, String separator) {
		MutableComponent out = Component.empty();
		boolean first = true;
		if (csv != null) {
			for (String part : csv.split(",")) {
				AbilityId id = AbilityId.byId(part);
				if (id == null) {
					continue;
				}
				if (!first) {
					out.append(Component.literal(separator));
				}
				out.append(name(id));
				first = false;
			}
		}
		if (first) {
			return Component.literal("无");
		}
		return out;
	}
}
