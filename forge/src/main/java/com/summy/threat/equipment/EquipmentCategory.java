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

	/** 固定优先级：盔甲 > 远程 > 近战。 */
	public static EquipmentCategory resolve(boolean armor, boolean ranged, boolean melee) {
		if (armor) {
			return ARMOR;
		}
		if (ranged) {
			return RANGED;
		}
		return melee ? MELEE : null;
	}
}
