package com.summy.threat.equipment;

/** 两套刷新模式各自的三档品级权重。 */
public record PrismRollSettings(double standardT1, double standardT2, double standardT3,
		double advancedT1, double advancedT2, double advancedT3) {
	public double weight(PrismRollMode mode, StarPrismTier tier) {
		return switch (mode) {
			case STANDARD -> switch (tier) {
				case T1 -> standardT1;
				case T2 -> standardT2;
				case T3 -> standardT3;
			};
			case ADVANCED -> switch (tier) {
				case T1 -> advancedT1;
				case T2 -> advancedT2;
				case T3 -> advancedT3;
			};
		};
	}
}
