package com.summy.threat.legendary;

import java.util.Set;

/** 不引用冰火类的龙族 Boss 资格规则。 */
public final class DragonBossPolicy {
	private static final Set<String> ICE_FIRE_DRAGONS = Set.of(
			"iceandfire:fire_dragon",
			"iceandfire:ice_dragon",
			"iceandfire:lightning_dragon");

	private DragonBossPolicy() {
	}

	public static boolean isKnownIceFireDragon(String entityTypeId) {
		return entityTypeId != null && ICE_FIRE_DRAGONS.contains(entityTypeId);
	}

	public static boolean isEligibleDragonBoss(String entityTypeId, int dragonStage,
			boolean tamed, boolean boss) {
		if (!boss || entityTypeId == null) {
			return false;
		}
		if ("minecraft:ender_dragon".equals(entityTypeId)) {
			return true;
		}
		return isKnownIceFireDragon(entityTypeId) && !tamed && dragonStage >= 4;
	}
}
