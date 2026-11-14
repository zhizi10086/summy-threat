package com.summy.threat.leader;

import com.summy.threat.ability.AbilityTexts;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/** 区域首领能力名称的颜色与拼接工具。 */
public final class LeaderAbilityTexts {
	private LeaderAbilityTexts() {
	}

	public static MutableComponent name(LeaderAbilityId id) {
		return Component.translatable(id.displayName())
				.withStyle(Style.EMPTY.withColor(TextColor.fromRgb(
						AbilityTexts.colorOf(id.stage()))));
	}

	public static Component join(Iterable<LeaderAbilityId> abilities, String separator) {
		MutableComponent out = Component.empty();
		boolean first = true;
		for (LeaderAbilityId id : abilities) {
			if (!first) {
				out.append(Component.literal(separator));
			}
			out.append(name(id));
			first = false;
		}
		return first ? Component.literal("无") : out;
	}

	public static Component joinCsv(String csv, String separator) {
		MutableComponent out = Component.empty();
		boolean first = true;
		if (csv != null) {
			for (String part : csv.split(",")) {
				LeaderAbilityId id = LeaderAbilityId.byId(part);
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
		return first ? Component.literal("无") : out;
	}
}
