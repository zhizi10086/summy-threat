package com.summy.threat.equipment;

/** 武器词条共用的纯数值规则。 */
public final class WeaponDamageMath {
	private WeaponDamageMath() {
	}

	public static double concussiveMultiplier(double knockbackResistance,
			double maximumPercent) {
		double resistance = clamp(knockbackResistance, 0.0D, 1.0D);
		double maximum = Math.max(0.0D, maximumPercent) / 100.0D;
		return 1.0D + resistance * maximum;
	}

	public static double multiplyBonuses(double... percentBonuses) {
		double multiplier = 1.0D;
		for (double percent : percentBonuses) {
			if (Double.isFinite(percent)) {
				multiplier *= 1.0D + Math.max(0.0D, percent) / 100.0D;
			}
		}
		return multiplier;
	}

	public static double reduceArmorReduction(double reduction, double points) {
		return Math.max(0.0D, clamp(reduction, 0.0D, 1.0D) - Math.max(0.0D, points) / 100.0D);
	}

	private static double clamp(double value, double min, double max) {
		return Math.min(max, Math.max(min, value));
	}
}
