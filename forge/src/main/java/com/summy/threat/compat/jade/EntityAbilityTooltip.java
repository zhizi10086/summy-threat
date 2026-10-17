package com.summy.threat.compat.jade;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.util.Locale;

/**
 * 客户端提示：`[Summy Threat] TL 20 · 卓越者`；若能力标签存在则追加 `能力：…`。
 * 走 Jade 自带开关（enabledByDefault = true，可在 Jade 配置里关闭）。
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
		tooltip.add(Component.translatable("summy_threat.jade.status", tl,
				Component.translatable("summy_threat.jade.kind." + kind)));
		if (data.contains(EntityAbilityDataProvider.KEY_ABILITIES)) {
			tooltip.add(Component.translatable("summy_threat.jade.abilities",
					data.getString(EntityAbilityDataProvider.KEY_ABILITIES)));
		}
	}

	@Override
	public ResourceLocation getUid() {
		return new ResourceLocation("summy_threat", "entity_status");
	}
}
