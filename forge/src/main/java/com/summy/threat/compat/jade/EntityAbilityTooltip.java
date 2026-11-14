package com.summy.threat.compat.jade;

import com.summy.threat.ability.AbilityTexts;
import com.summy.threat.eximus.EximusArchetype;
import com.summy.threat.leader.LeaderAbilityTexts;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.util.Locale;

/**
 * 客户端提示（两行）：`[Summy Threat] TL 20 卓越者`（普通单位不显示类型）、
 * `迅捷·力量`（能力名按解锁 TL 阶段上色）。走 Jade 自带开关。
 */
public enum EntityAbilityTooltip implements IEntityComponentProvider {
	INSTANCE;

	@Override
	public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
		CompoundTag data = accessor.getServerData();
		if (!data.contains(EntityAbilityDataProvider.KEY_TL)) {
			return;
		}
		int tl = data.getInt(EntityAbilityDataProvider.KEY_TL);
		String kind = data.getString(EntityAbilityDataProvider.KEY_KIND).toLowerCase(Locale.ROOT);
		if ("normal".equals(kind)) {
			tooltip.add(Component.translatable("summy_threat.jade.status_normal", tl));
		} else {
			Component type = Component.translatable("summy_threat.jade.kind." + kind);
			String archetype = data.getString(EntityAbilityDataProvider.KEY_ARCHETYPE);
			if (!archetype.isEmpty()) {
				EximusArchetype value = EximusArchetype.byId(archetype);
				Component archetypeText = value == null
						? Component.literal(archetype)
						: value.translated()
								? Component.translatable("summy_threat.jade.archetype." + archetype)
								: Component.literal(value.displayName())
										.withStyle(Style.EMPTY.withColor(TextColor.fromRgb(value.color())));
				type = type.copy().append("·").append(archetypeText);
			}
			tooltip.add(Component.translatable("summy_threat.jade.status", tl, type));
		}
		if (data.contains(EntityAbilityDataProvider.KEY_SHIELD)) {
			tooltip.add(Component.translatable("summy_threat.jade.eximus_shield",
					fmt(data.getDouble(EntityAbilityDataProvider.KEY_SHIELD)),
					fmt(data.getDouble(EntityAbilityDataProvider.KEY_SHIELD_MAX)))
					.withStyle(ChatFormatting.AQUA));
		}
		if (data.contains(EntityAbilityDataProvider.KEY_ABILITIES)) {
			String csv = data.getString(EntityAbilityDataProvider.KEY_ABILITIES);
			if (!csv.isEmpty()) {
				tooltip.add(AbilityTexts.join(csv, "·"));
			}
		}
		String leader = data.getString(EntityAbilityDataProvider.KEY_LEADER_ABILITIES);
		if (!leader.isEmpty()) {
			tooltip.add(LeaderAbilityTexts.joinCsv(leader, "·"));
		}
	}

	private static String fmt(double value) {
		return value > 0.0D && value < 10.0D ? String.format("%.1f", value) : String.format("%,.0f", value);
	}

	@Override
	public ResourceLocation getUid() {
		return new ResourceLocation("summy_threat", "entity_status");
	}
}
