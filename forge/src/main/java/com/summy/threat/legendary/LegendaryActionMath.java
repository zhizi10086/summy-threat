package com.summy.threat.legendary;

/** 传奇动作的纯数值规则。 */
public final class LegendaryActionMath {
	private static final double VANILLA_GRAVITY_PER_TICK = 0.08D;
	private static final double REVERSE_GRAVITY_NET_ASCENT = 0.14D;

	private LegendaryActionMath() {
	}

	public static float damage(double base, double attackCurve, double rAtk,
			boolean playerSide) {
		return (float) (base * attackCurve * rAtk * (playerSide ? 1.0D : 10.0D));
	}

	public static int jadeMultiplier(int previousHits) {
		if (previousHits <= 0) {
			return 1;
		}
		return Math.min(12, 1 << Math.min(4, previousHits));
	}

	public static double jadeContactAnchorY(double feetY, double height) {
		return feetY + height + 0.35D;
	}

	public static double jadeVisualAnchorY(double contactY, double height) {
		return contactY - height - 0.35D;
	}

	public static double jadeContactMinY(double anchorY, double height) {
		return anchorY - height;
	}

	public static double jadeContactMaxY(double anchorY) {
		return anchorY;
	}

	public static double clockwiseSectorOffset(int sector) {
		return sector * Math.PI / 2.0D;
	}

	public static int waveEndExclusiveAge(int finalWaveStartAge, double maxRadius,
			double speed) {
		return finalWaveStartAge + (int) Math.ceil(maxRadius / speed) + 3;
	}

	public static double reverseGravityLiftVelocity() {
		return VANILLA_GRAVITY_PER_TICK + REVERSE_GRAVITY_NET_ASCENT;
	}
}
