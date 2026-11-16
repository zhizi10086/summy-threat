package com.summy.threat.equipment;

/** 单个星渊词条的固定注册元数据；数值仍由 SERVER 配置提供。 */
public record StarPrismAffix(String id, EquipmentCategory category, StarPrismTier tier) {
	public String translationKey() {
		return "summy_threat.star_prism." + category.id() + "." + id;
	}
}
