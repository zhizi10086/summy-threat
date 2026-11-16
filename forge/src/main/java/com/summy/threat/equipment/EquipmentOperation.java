package com.summy.threat.equipment;

/** 锻造台动态操作；JSON 中的 id 永久固定。 */
public enum EquipmentOperation {
	UPGRADE_I("upgrade_i"),
	UPGRADE_II("upgrade_ii"),
	EMBED_PRISM("embed_prism"),
	REFRESH_STANDARD("refresh_standard"),
	REFRESH_ADVANCED("refresh_advanced"),
	DISASSEMBLE_ALL("disassemble_all");

	private final String id;

	EquipmentOperation(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	public static EquipmentOperation byId(String id) {
		for (EquipmentOperation operation : values()) {
			if (operation.id.equals(id)) {
				return operation;
			}
		}
		return null;
	}
}
