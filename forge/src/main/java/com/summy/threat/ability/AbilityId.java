package com.summy.threat.ability;

import java.util.Locale;

/**
 * TL 能力 id（交接文档 §6.5 表；{@link #id()} 的小写形式就是实体标签里的字符串）。
 */
public enum AbilityId {
	SWIFT(4),
	TOUGHNESS(4),
	REGENERATION(6),
	STRENGTH(6),
	FIREPROOF(8),
	FRENZY(8),
	THICK_HIDE(10),
	LIFESTEAL(12),
	THORNS(12),
	EMBER(14),
	NECRO_REINFORCEMENT(16),
	CORROSION(16),
	DEATH_BURST(18),
	PHASE(20);

	private final int unlockTl;

	AbilityId(int unlockTl) {
		this.unlockTl = unlockTl;
	}

	public int unlockTl() {
		return unlockTl;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** 解析 id（大小写不敏感）；未知返回 null。 */
	public static AbilityId byId(String raw) {
		if (raw == null) {
			return null;
		}
		try {
			return valueOf(raw.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/** 基础能力数量（《基础TL抽取.txt》）：0-3 → 0，4-7 → 1，8-11 → 2，12-15 → 3，16-20 → 4。 */
	public static int baseCountFor(int tl) {
		if (tl < 4) {
			return 0;
		}
		return Math.min(4, (tl - 4) / 4 + 1);
	}

	/** 阶段（《基础TL抽取.txt》区间）：0-3 → 1、4-7 → 2、8-11 → 3、12-15 → 4、16-20 → 5。 */
	public static int stageOf(int tl) {
		if (tl < 4) {
			return 1;
		}
		return Math.min(5, (tl - 4) / 4 + 2);
	}

	/** 该能力所属的解锁阶段。 */
	public int stage() {
		return stageOf(unlockTl);
	}
}
