package com.summy.threat.equipment;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WeaponAttributeProfileTest {
	@Test
	void profileCombinesFixedAndPrismAttributes() {
		WeaponAffixSnapshot snapshot = new WeaponAffixSnapshot(2,
				List.of("prism_edge", "astral_haste", "weakpoint_prism",
						"concussive_prism"));

		WeaponAttributeProfile profile = WeaponAttributeProfile.of(snapshot, true, 3);

		assertEquals(8.5D, profile.attackDamage(), 1.0E-9D);
		assertEquals(0.30D, profile.attackSpeedBase(), 1.0E-9D);
		assertEquals(0.10D, profile.attackSpeedTotal(), 1.0E-9D);
		assertEquals(1.0D, profile.attackKnockback(), 1.0E-9D);
		assertEquals(0.45D, profile.critChance(), 1.0E-9D);
		assertEquals(0.25D, profile.critDamage(), 1.0E-9D);
		assertEquals(0.15D, profile.pursuitSpeed(), 1.0E-9D);
	}

	@Test
	void critAttributesAreOmittedWhenExternalBridgeIsUnavailable() {
		WeaponAttributeProfile profile = WeaponAttributeProfile.of(
				new WeaponAffixSnapshot(1, List.of("weakpoint_prism")), false, 0);

		assertEquals(0.0D, profile.critChance(), 1.0E-9D);
		assertEquals(0.0D, profile.critDamage(), 1.0E-9D);
	}

	@Test
	void profileUsesConfiguredValuesInsteadOfBakedDefaults() {
		WeaponAffixSnapshot snapshot = new WeaponAffixSnapshot(2,
				List.of("prism_edge", "astral_haste", "weakpoint_prism",
						"concussive_prism"));

		WeaponAttributeProfile profile = WeaponAttributeProfile.of(snapshot, true, 2,
				new WeaponAttributeProfile.Values(12.0D, 0.4D, 0.1D, 1.5D,
						0.6D, 0.35D, 0.08D));

		assertEquals(12.0D, profile.attackDamage(), 1.0E-9D);
		assertEquals(0.40D, profile.attackSpeedBase(), 1.0E-9D);
		assertEquals(1.5D, profile.attackKnockback(), 1.0E-9D);
		assertEquals(0.60D, profile.critChance(), 1.0E-9D);
		assertEquals(0.35D, profile.critDamage(), 1.0E-9D);
		assertEquals(0.16D, profile.pursuitSpeed(), 1.0E-9D);
	}
}
