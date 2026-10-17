package com.summy.threat.compat.jade;

import com.summy.threat.ability.AbilityTexts;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
			tooltip.add(Component.translatable("summy_threat.jade.status", tl,
					Component.translatable("summy_threat.jade.kind." + kind)));
		}
		if (data.contains(EntityAbilityDataProvider.KEY_OVERGUARD)) {
			tooltip.add(Component.translatable("summy_threat.jade.overguard",
					fmt(data.getDouble(EntityAbilityDataProvider.KEY_OVERGUARD)),
					fmt(data.getDouble(EntityAbilityDataProvider.KEY_OVERGUARD_MAX)))
					.withStyle(ChatFormatting.AQUA));
		}
		if (data.contains(EntityAbilityDataProvider.KEY_ABILITIES)) {
			String csv = data.getString(EntityAbilityDataProvider.KEY_ABILITIES);
			if (!csv.isEmpty()) {
				tooltip.add(AbilityTexts.join(csv, "·"));
			}
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
