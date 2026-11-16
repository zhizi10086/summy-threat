package com.summy.threat.equipment;

/** 卓越装备与星渊词条的三类作用域。 */
public enum EquipmentCategory {
	ARMOR("armor"),
	RANGED("ranged"),
	MELEE("melee");

	private final String id;

	EquipmentCategory(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}
}
