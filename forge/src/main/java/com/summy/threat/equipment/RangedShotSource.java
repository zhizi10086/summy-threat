package com.summy.threat.equipment;

/** 一次远程齐射的发射来源。 */
enum RangedShotSource {
	STANDARD(true),
	TINKERS_CROSSBOW(true),
	TETRA(false);

	private final boolean attachOnEntityJoin;

	RangedShotSource(boolean attachOnEntityJoin) {
		this.attachOnEntityJoin = attachOnEntityJoin;
	}

	boolean attachOnEntityJoin() {
		return attachOnEntityJoin;
	}
}
