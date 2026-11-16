package com.summy.threat.equipment;

import java.util.Optional;

/** 棱晶首次主手处理动作。 */
public enum PrismProcessingMode {
	NONE,
	REVEAL_STANDARD,
	REFRESH_STANDARD,
	REFRESH_ADVANCED;

	public static PrismProcessingMode resolve(Optional<StarPrismData> data) {
		if (data.isEmpty()) {
			return REVEAL_STANDARD;
		}
		StarPrismData value = data.get();
		if ("standard".equals(value.pendingRefresh())) {
			return REFRESH_STANDARD;
		}
		if ("advanced".equals(value.pendingRefresh())) {
			return REFRESH_ADVANCED;
		}
		return value.revealed() ? NONE : REVEAL_STANDARD;
	}

	public PrismRollMode rollMode() {
		return this == REFRESH_ADVANCED ? PrismRollMode.ADVANCED : PrismRollMode.STANDARD;
	}

	public boolean processes() {
		return this != NONE;
	}
}
