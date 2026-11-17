package com.summy.threat.client;

import com.summy.threat.equipment.EquipmentCategory;
import net.minecraft.world.entity.EquipmentSlot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EquipmentTooltipTextTest {
	@Test
	void fixedAttributeKeysCoverAllEquipmentCategories() {
		assertEquals("summy_threat.tooltip.fixed.armor",
				EquipmentTooltipHandler.fixedAttributeKey(EquipmentCategory.ARMOR));
		assertEquals("summy_threat.tooltip.fixed.melee",
				EquipmentTooltipHandler.fixedAttributeKey(EquipmentCategory.MELEE));
		assertEquals("summy_threat.tooltip.fixed.ranged",
				EquipmentTooltipHandler.fixedAttributeKey(EquipmentCategory.RANGED));
	}

	@Test
	void fixedAttributeArgumentsMatchImplementedEffects() {
		assertEquals(List.of("2", "1.5", "0.05"),
				EquipmentTooltipHandler.fixedAttributeArguments(EquipmentCategory.ARMOR));
		assertEquals(List.of("10", "10"),
				EquipmentTooltipHandler.fixedAttributeArguments(EquipmentCategory.MELEE));
		assertEquals(List.of("10", "10"),
				EquipmentTooltipHandler.fixedAttributeArguments(EquipmentCategory.RANGED));
	}

	@Test
	void fixedAbilityKeysCoverArmorSlotsAndWeapons() {
		assertEquals("summy_threat.tooltip.fixed_ability.clear_sight",
				EquipmentTooltipHandler.fixedAbilityNameKey(
						EquipmentCategory.ARMOR, EquipmentSlot.HEAD));
		assertEquals("summy_threat.tooltip.fixed_ability.emergency_mending",
				EquipmentTooltipHandler.fixedAbilityNameKey(
						EquipmentCategory.ARMOR, EquipmentSlot.CHEST));
		assertEquals("summy_threat.tooltip.fixed_ability.star_brand",
				EquipmentTooltipHandler.fixedAbilityNameKey(
						EquipmentCategory.MELEE, EquipmentSlot.MAINHAND));
		assertEquals("summy_threat.tooltip.fixed_ability.far_star_mark",
				EquipmentTooltipHandler.fixedAbilityNameKey(
						EquipmentCategory.RANGED, EquipmentSlot.MAINHAND));
	}

	@Test
	void fixedAbilityEffectKeysCoverAllFixedAbilities() {
		assertEquals("summy_threat.tooltip.fixed_ability.effect.clear_sight",
				EquipmentTooltipHandler.fixedAbilityEffectKey(
						EquipmentCategory.ARMOR, EquipmentSlot.HEAD));
		assertEquals("summy_threat.tooltip.fixed_ability.effect.emergency_mending",
				EquipmentTooltipHandler.fixedAbilityEffectKey(
						EquipmentCategory.ARMOR, EquipmentSlot.CHEST));
		assertEquals("summy_threat.tooltip.fixed_ability.effect.slowness_guard",
				EquipmentTooltipHandler.fixedAbilityEffectKey(
						EquipmentCategory.ARMOR, EquipmentSlot.LEGS));
		assertEquals("summy_threat.tooltip.fixed_ability.effect.star_stride",
				EquipmentTooltipHandler.fixedAbilityEffectKey(
						EquipmentCategory.ARMOR, EquipmentSlot.FEET));
		assertEquals("summy_threat.tooltip.fixed_ability.effect.star_brand",
				EquipmentTooltipHandler.fixedAbilityEffectKey(
						EquipmentCategory.MELEE, EquipmentSlot.MAINHAND));
		assertEquals("summy_threat.tooltip.fixed_ability.effect.far_star_mark",
				EquipmentTooltipHandler.fixedAbilityEffectKey(
						EquipmentCategory.RANGED, EquipmentSlot.MAINHAND));
	}

	@Test
	void fixedAbilityEffectArgumentsUsePlayerFacingUnitsAndValues() {
		assertEquals(List.of(),
				EquipmentTooltipHandler.fixedAbilityEffectArguments(
						EquipmentCategory.ARMOR, EquipmentSlot.HEAD));
		assertEquals(List.of("3", "1", "4", "1.5"),
				EquipmentTooltipHandler.fixedAbilityEffectArguments(
						EquipmentCategory.ARMOR, EquipmentSlot.CHEST));
		assertEquals(List.of("70"),
				EquipmentTooltipHandler.fixedAbilityEffectArguments(
						EquipmentCategory.ARMOR, EquipmentSlot.LEGS));
		assertEquals(List.of("8", "0.4"),
				EquipmentTooltipHandler.fixedAbilityEffectArguments(
						EquipmentCategory.ARMOR, EquipmentSlot.FEET));
		assertEquals(List.of("4", "10"),
				EquipmentTooltipHandler.fixedAbilityEffectArguments(
						EquipmentCategory.MELEE, EquipmentSlot.MAINHAND));
		assertEquals(List.of("4", "10"),
				EquipmentTooltipHandler.fixedAbilityEffectArguments(
						EquipmentCategory.RANGED, EquipmentSlot.MAINHAND));
	}
}
