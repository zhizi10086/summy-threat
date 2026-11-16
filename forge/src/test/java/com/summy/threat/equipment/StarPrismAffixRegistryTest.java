package com.summy.threat.equipment;

import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StarPrismAffixRegistryTest {
	@Test
	void registryContainsFinalArmorMeleeRangedDistribution() {
		assertEquals(12, StarPrismAffixes.byCategory(EquipmentCategory.ARMOR).size());
		assertEquals(11, StarPrismAffixes.byCategory(EquipmentCategory.MELEE).size());
		assertEquals(11, StarPrismAffixes.byCategory(EquipmentCategory.RANGED).size());
		assertEquals(34, StarPrismAffixes.all().size());
	}

	@Test
	void everyAffixIdIsGloballyUniqueAndTiered() {
		assertEquals(34, StarPrismAffixes.all().stream()
				.map(StarPrismAffix::id).collect(java.util.stream.Collectors.toSet()).size());
		assertTrue(new HashSet<>(StarPrismAffixes.all()).stream()
				.allMatch(affix -> affix.tier() != null));
	}

	@Test
	void knownHighestTierIdsAreRegisteredInExpectedCategories() {
		assertEquals(EquipmentCategory.ARMOR,
				StarPrismAffixes.require("last_light").category());
		assertEquals(EquipmentCategory.MELEE,
				StarPrismAffixes.require("terminal_star").category());
		assertEquals(EquipmentCategory.RANGED,
				StarPrismAffixes.require("terminal_star_shot").category());
	}
}
