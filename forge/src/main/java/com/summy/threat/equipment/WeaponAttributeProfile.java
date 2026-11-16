package com.summy.threat.equipment;

/** 主手近战武器的玩家动态属性。 */
public record WeaponAttributeProfile(double attackDamage, double attackSpeedBase,
		double attackSpeedTotal, double attackKnockback, double critChance,
		double critDamage, double pursuitSpeed) {
	public static WeaponAttributeProfile of(WeaponAffixSnapshot snapshot,
			boolean externalCritAttributes, int pursuitStacks) {
		return of(snapshot, externalCritAttributes, pursuitStacks, Values.defaults());
	}

	public static WeaponAttributeProfile of(WeaponAffixSnapshot snapshot,
			boolean externalCritAttributes, int pursuitStacks, Values values) {
		Values safeValues = values == null ? Values.defaults() : values;
		double attackDamage = snapshot != null && snapshot.has("prism_edge")
				? safeValues.prismEdgeDamage() : 0.0D;
		double attackSpeedBase = snapshot != null && snapshot.has("astral_haste")
				? safeValues.astralHaste() : 0.0D;
		double attackSpeedTotal = snapshot != null && snapshot.level() >= 1
				? safeValues.fixedAttackSpeed() : 0.0D;
		double attackKnockback = snapshot != null && snapshot.has("concussive_prism")
				? safeValues.concussiveKnockback() : 0.0D;
		boolean weakpoint = snapshot != null && snapshot.has("weakpoint_prism");
		double critChance = externalCritAttributes && weakpoint
				? safeValues.weakpointCritChance() : 0.0D;
		double critDamage = externalCritAttributes && weakpoint
				? safeValues.weakpointCritDamage() : 0.0D;
		double pursuitSpeed = Math.max(0, pursuitStacks) * safeValues.pursuitSpeedPerStack();
		return new WeaponAttributeProfile(attackDamage, attackSpeedBase, attackSpeedTotal,
				attackKnockback, critChance, critDamage, pursuitSpeed);
	}

	/** 属性配置以比例保存，避免运行时重复换算。 */
	public record Values(double prismEdgeDamage, double astralHaste,
			double fixedAttackSpeed, double concussiveKnockback, double weakpointCritChance,
			double weakpointCritDamage, double pursuitSpeedPerStack) {
		public Values {
			prismEdgeDamage = nonNegative(prismEdgeDamage);
			astralHaste = nonNegative(astralHaste);
			fixedAttackSpeed = nonNegative(fixedAttackSpeed);
			concussiveKnockback = nonNegative(concussiveKnockback);
			weakpointCritChance = nonNegative(weakpointCritChance);
			weakpointCritDamage = nonNegative(weakpointCritDamage);
			pursuitSpeedPerStack = nonNegative(pursuitSpeedPerStack);
		}

		public static Values defaults() {
			return new Values(8.5D, 0.30D, 0.10D, 1.0D, 0.45D, 0.25D, 0.05D);
		}

		private static double nonNegative(double value) {
			return Double.isFinite(value) ? Math.max(0.0D, value) : 0.0D;
		}
	}
}
