package com.summy.threat.client;

import com.summy.threat.equipment.EquipmentCategory;
import com.summy.threat.equipment.EquipmentClientConfig;
import com.summy.threat.equipment.EquipmentTypeResolver;
import com.summy.threat.equipment.EximusEquipmentData;
import com.summy.threat.equipment.StarPrismAffix;
import com.summy.threat.equipment.StarPrismAffixes;
import com.summy.threat.equipment.StarPrismData;
import com.summy.threat.item.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.loading.FMLLoader;

import java.util.List;
import java.util.Locale;

/** B15 材料与卓越装备的工具提示。 */
public final class EquipmentTooltipHandler {
	private static final int VALUE_COLOR = 0xD7DEE8;

	private EquipmentTooltipHandler() {
	}

	@SubscribeEvent
	public static void onItemTooltip(ItemTooltipEvent event) {
		ItemStack stack = event.getItemStack();
		if (stack.is(ModItems.ABYSSAL_STAR_PRISM.get())) {
			addPrismTooltip(event.getToolTip(), stack);
			return;
		}
		if (EximusEquipmentData.upgradeLevel(stack) > 0) {
			addEquipmentTooltip(event.getToolTip(), stack);
		}
	}

	private static void addPrismTooltip(List<Component> tooltip, ItemStack stack) {
		if (!EquipmentClientConfig.starPrismEnabled()) {
			tooltip.add(translation("summy_threat.tooltip.prism.disabled", ChatFormatting.DARK_GRAY));
			return;
		}
		StarPrismData data = StarPrismData.read(stack).orElse(null);
		if (data == null || !data.revealed()) {
			tooltip.add(translation("summy_threat.tooltip.prism.unrevealed", ChatFormatting.GRAY));
			tooltip.add(translation("summy_threat.tooltip.prism.reveal_hint", ChatFormatting.DARK_GRAY));
			return;
		}
		if (data.pendingRefresh() != null) {
			tooltip.add(Component.translatable("summy_threat.tooltip.prism.pending."
							+ data.pendingRefresh())
					.withStyle(ChatFormatting.GOLD));
			tooltip.add(translation("summy_threat.tooltip.prism.refresh_hint", ChatFormatting.GRAY));
		}
		tooltip.add(translation("summy_threat.tooltip.potential_lines", ChatFormatting.DARK_GRAY));
		for (EquipmentCategory category : EquipmentCategory.values()) {
			String id = data.affix(category);
			StarPrismAffix affix = StarPrismAffixes.find(id);
			MutableComponent line = Component.empty()
					.append(translation("summy_threat.tooltip.category." + category.id(),
							ChatFormatting.GRAY))
					.append(Component.literal("：").withStyle(ChatFormatting.DARK_GRAY));
			if (affix == null) {
				line.append(translation("summy_threat.tooltip.affix.invalid",
						ChatFormatting.DARK_RED));
			} else {
				line.append(affixName(affix));
				if (Screen.hasShiftDown()) {
					line.append(Component.literal(" ").withStyle(ChatFormatting.DARK_GRAY))
							.append(affixValues(affix));
				}
			}
			if (category != EquipmentCategory.ARMOR) {
				line.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
						.append(translation("summy_threat.tooltip.weapon_locked",
								ChatFormatting.DARK_GRAY));
			}
			tooltip.add(line);
		}
		if (!Screen.hasShiftDown()) {
			tooltip.add(translation("summy_threat.tooltip.shift_values", ChatFormatting.DARK_GRAY));
		}
	}

	private static void addEquipmentTooltip(List<Component> tooltip, ItemStack stack) {
		if (!EquipmentClientConfig.equipmentEnabled()) {
			tooltip.add(translation("summy_threat.tooltip.equipment.disabled",
					ChatFormatting.DARK_GRAY));
			return;
		}
		int level = EximusEquipmentData.upgradeLevel(stack);
		tooltip.add(Component.translatable("summy_threat.tooltip.upgrade_level." + level)
				.withStyle(ChatFormatting.AQUA));
		EquipmentCategory category = EquipmentTypeResolver.category(stack);
		for (int slot = 0; slot < EximusEquipmentData.SLOT_COUNT; slot++) {
			MutableComponent line = Component.translatable("summy_threat.tooltip.slot." + (slot + 1))
					.withStyle(ChatFormatting.GRAY);
			ItemStack prism = EximusEquipmentData.prism(stack, slot);
			StarPrismData data = StarPrismData.read(prism).orElse(null);
			if (slot >= level) {
				line.append(Component.literal("：").withStyle(ChatFormatting.DARK_GRAY))
						.append(translation("summy_threat.tooltip.slot.locked",
								ChatFormatting.DARK_GRAY));
			} else if (data == null) {
				line.append(Component.literal("：").withStyle(ChatFormatting.DARK_GRAY))
						.append(translation("summy_threat.tooltip.slot.empty",
								ChatFormatting.DARK_GRAY));
			} else {
				StarPrismAffix affix = category == null ? null
						: StarPrismAffixes.find(data.affix(category));
				line.append(Component.literal("：").withStyle(ChatFormatting.DARK_GRAY));
				if (affix == null) {
					line.append(translation("summy_threat.tooltip.affix.unavailable",
							ChatFormatting.DARK_GRAY));
				} else {
					line.append(affixName(affix));
					if (Screen.hasShiftDown()) {
						line.append(Component.literal(" ").withStyle(ChatFormatting.DARK_GRAY))
								.append(affixValues(affix));
					}
				}
			}
			tooltip.add(line);
		}
		if (category != null) {
			tooltip.add(Component.translatable("summy_threat.tooltip.active_category",
							Component.translatable("summy_threat.tooltip.category." + category.id()))
					.withStyle(ChatFormatting.DARK_GRAY));
			if (category != EquipmentCategory.ARMOR) {
				tooltip.add(translation("summy_threat.tooltip.weapon_locked",
						ChatFormatting.DARK_GRAY));
			}
		}
		if (!Screen.hasShiftDown()) {
			tooltip.add(translation("summy_threat.tooltip.shift_values", ChatFormatting.DARK_GRAY));
		}
	}

	private static MutableComponent affixName(StarPrismAffix affix) {
		MutableComponent line = Component.empty();
		if (!FMLLoader.isProduction()) {
			line.append(Component.literal("[" + affix.tier().name() + "] ")
					.withStyle(style -> style.withColor(affix.tier().color())));
		}
		line.append(Component.translatable("summy_threat.star_prism.affix." + affix.id())
				.withStyle(style -> style.withColor(affix.tier().color())));
		return line;
	}

	private static Component affixValues(StarPrismAffix affix) {
		String key = "summy_threat.tooltip.value." + affix.id();
		return switch (affix.id()) {
			case "astral_plating" -> value(key,
					number("star.value.armor.astral_plating.armor_value_per_stack"),
					number("star.value.armor.astral_plating.toughness_value_per_stack"));
			case "vital_constellation" -> value(key,
					number("star.value.armor.vital_constellation.max_health_per_stack"));
			case "star_mantle" -> value(key,
					number("star.value.armor.star_mantle.reduction_percent_per_stack"));
			case "corrosion_baffle" -> value(key,
					number("star.value.armor.corrosion_baffle.suppression_percent"));
			case "projectile_ward" -> value(key,
					number("star.value.armor.projectile_ward.reduction_percent"));
			case "spellward_prism" -> value(key,
					number("star.value.armor.spellward_prism.reduction_percent"));
			case "anchored_stride" -> value(key,
					number("star.value.armor.anchored_stride.knockback_bonus"),
					number("star.value.armor.anchored_stride.fall_reduction_percent"));
			case "steady_heart" -> value(key,
					number("star.value.armor.steady_heart.threshold_percent"),
					number("star.value.armor.steady_heart.reduction_percent"),
					integer("star.value.armor.steady_heart.duration_ticks"),
					integer("star.value.armor.steady_heart.cooldown_ticks"));
			case "last_light" -> value(key,
					number("star.value.armor.last_light.health_floor_percent"),
					integer("star.value.armor.last_light.guard_ticks"),
					integer("star.value.armor.last_light.cooldown_ticks"));
			case "threat_aegis" -> value(key,
					number("star.value.armor.threat_aegis.reduction_percent"));
			case "phase_anchor" -> value(key,
					integer("star.value.armor.phase_anchor.cooldown_ticks"));
			case "prism_edge" -> value(key,
					number("star.value.melee.prism_edge.attack_bonus"));
			case "astral_haste" -> value(key,
					number("star.value.melee.astral_haste.attack_speed_percent"));
			case "weakpoint_prism" -> value(key,
					number("star.value.melee.weakpoint_prism.crit_chance_percent"),
					number("star.value.melee.weakpoint_prism.crit_multiplier_bonus"));
			case "concussive_prism" -> value(key,
					number("star.value.melee.concussive_prism.knockback_bonus"),
					number("star.value.melee.concussive_prism.damage_per_resistance_percent"));
			case "breach_constellation" -> value(key,
					number("star.value.melee.breach_constellation.armor_reduction_points"));
			case "shield_render" -> value(key,
					number("star.value.melee.shield_render.bonus_percent"));
			case "void_siphon" -> value(key,
					number("star.value.melee.void_siphon.lifesteal_percent"));
			case "arcane_edge" -> value(key,
					number("star.value.melee.arcane_edge.magic_percent"));
			case "starfire_cycle" -> value(key,
					number("star.value.melee.starfire_cycle.magic_percent"),
					integer("star.value.melee.starfire_cycle.hits"));
			case "terminal_star" -> value(key,
					number("star.value.melee.terminal_star.chance_percent"),
					number("star.value.melee.terminal_star.multiplier"));
			case "abyssal_pursuit" -> value(key,
					number("star.value.melee.abyssal_pursuit.damage_per_stack_percent"),
					number("star.value.melee.abyssal_pursuit.attack_speed_per_stack_percent"),
					integer("star.value.melee.abyssal_pursuit.max_stacks"));
			case "starlit_bowstring" -> value(key,
					number("star.value.ranged.starlit_bowstring.damage_percent"));
			case "sidereal_aim" -> value(key,
					number("star.value.ranged.sidereal_aim.chance_percent"),
					number("star.value.ranged.sidereal_aim.multiplier"));
			case "comet_fletching" -> value(key,
					number("star.value.ranged.comet_fletching.velocity_percent"),
					integer("star.value.ranged.comet_fletching.pierce_bonus"));
			case "starlight_dew" -> value(key,
					number("star.value.ranged.starlight_dew.heal_flat"),
					number("star.value.ranged.starlight_dew.heal_percent"),
					number("star.value.ranged.starlight_dew.heal_max"),
					integer("star.value.ranged.starlight_dew.cooldown_ticks"));
			case "breach_star_arrow" -> value(key,
					number("star.value.ranged.breach_star_arrow.armor_reduction_points"));
			case "shield_render_arrow" -> value(key,
					number("star.value.ranged.shield_render_arrow.bonus_percent"));
			case "arcane_arrow" -> value(key,
					number("star.value.ranged.arcane_arrow.magic_percent"));
			case "void_quiver" -> value(key,
					number("star.value.ranged.void_quiver.lifesteal_percent"));
			case "starfall_cycle_arrow" -> value(key,
					number("star.value.ranged.starfall_cycle_arrow.magic_percent"),
					integer("star.value.ranged.starfall_cycle_arrow.hits"));
			case "terminal_star_shot" -> value(key,
					number("star.value.ranged.terminal_star_shot.chance_percent"),
					number("star.value.ranged.terminal_star_shot.multiplier"));
			case "abyssal_hunt_arrow" -> value(key,
					number("star.value.ranged.abyssal_hunt_arrow.damage_per_stack_percent"),
					integer("star.value.ranged.abyssal_hunt_arrow.max_stacks"));
			default -> translation("summy_threat.tooltip.affix.no_values", ChatFormatting.GRAY);
		};
	}

	private static Component value(String key, Object... args) {
		return Component.translatable(key, args)
				.withStyle(style -> style.withColor(VALUE_COLOR));
	}

	private static String number(String key) {
		double value = EquipmentClientConfig.doubleValue(key);
		if (Math.abs(value - Math.rint(value)) < 1.0E-6D) {
			return Long.toString(Math.round(value));
		}
		return String.format(Locale.ROOT, "%.2f", value).replaceAll("0+$", "")
				.replaceAll("\\.$", "");
	}

	private static String integer(String key) {
		return Integer.toString(EquipmentClientConfig.intValue(key));
	}

	private static MutableComponent translation(String key, ChatFormatting color) {
		return Component.translatable(key).withStyle(color);
	}
}
