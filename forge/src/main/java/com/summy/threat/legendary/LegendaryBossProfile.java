package com.summy.threat.legendary;

/** 传奇动作池所需的 Boss 类别快照。 */
public record LegendaryBossProfile(
		boolean eligible,
		boolean enderDragonBoss,
		boolean witherBoss,
		boolean wardenBoss,
		boolean dragonFamily) {

	private static final LegendaryBossProfile NONE =
			new LegendaryBossProfile(false, false, false, false, false);
	private static final LegendaryBossProfile GENERIC =
			new LegendaryBossProfile(true, false, false, false, false);
	private static final LegendaryBossProfile ENDER =
			new LegendaryBossProfile(true, true, false, false, true);
	private static final LegendaryBossProfile WITHER =
			new LegendaryBossProfile(true, false, true, false, false);
	private static final LegendaryBossProfile WARDEN =
			new LegendaryBossProfile(true, false, false, true, false);
	private static final LegendaryBossProfile DRAGON =
			new LegendaryBossProfile(true, false, false, false, true);

	public static LegendaryBossProfile none() {
		return NONE;
	}

	public static LegendaryBossProfile genericBoss() {
		return GENERIC;
	}

	public static LegendaryBossProfile enderDragon() {
		return ENDER;
	}

	public static LegendaryBossProfile wither() {
		return WITHER;
	}

	public static LegendaryBossProfile warden() {
		return WARDEN;
	}

	public static LegendaryBossProfile dragonBoss() {
		return DRAGON;
	}
}
