package com.summy.threat.equipment;

/** 星渊词条的三个品级。 */
public enum StarPrismTier {
	T1(0x79C8FF),
	T2(0xB76CFF),
	T3(0xFFD45C);

	private final int color;

	StarPrismTier(int color) {
		this.color = color;
	}

	public int color() {
		return color;
	}
}
