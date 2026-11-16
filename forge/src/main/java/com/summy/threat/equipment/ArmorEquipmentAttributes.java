package com.summy.threat.equipment;

import com.summy.threat.Config;
import com.summy.threat.api.event.ThreatConfigChangedEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.UUID;

/** 玩家侧星渊属性、星履与缓速地板的统一刷新器。 */
public final class ArmorEquipmentAttributes {
	private static final UUID ASTRAL_ARMOR =
			UUID.fromString("6f5c1a2e-0016-4a0b-9d1e-000000000701");
	private static final UUID ASTRAL_TOUGHNESS =
			UUID.fromString("6f5c1a2e-0016-4a0b-9d1e-000000000702");
	private static final UUID VITAL_HEALTH =
			UUID.fromString("6f5c1a2e-0016-4a0b-9d1e-000000000703");
	private static final UUID ANCHORED_KNOCKBACK =
			UUID.fromString("6f5c1a2e-0016-4a0b-9d1e-000000000704");
	private static final UUID STAR_STRIDE_SPEED =
			UUID.fromString("6f5c1a2e-0016-4a0b-9d1e-000000000705");
	private static final UUID STAR_STRIDE_STEP =
			UUID.fromString("6f5c1a2e-0016-4a0b-9d1e-000000000706");
	private static final UUID SLOWNESS_FLOOR =
			UUID.fromString("6f5c1a2e-0016-4a0b-9d1e-000000000707");

	private ArmorEquipmentAttributes() {
	}

	@SubscribeEvent
	public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
		if (event.phase == TickEvent.Phase.END && !event.player.level().isClientSide()
				&& event.player instanceof ServerPlayer player) {
			WornArmorState.invalidate(player);
			refresh(player);
		}
	}

	@SubscribeEvent
	public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
		if (event.getEntity() instanceof ServerPlayer player
				&& event.getSlot().getType() == EquipmentSlot.Type.ARMOR) {
			WornArmorState.invalidate(player);
			refresh(player);
		}
	}

	@SubscribeEvent
	public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			WornArmorState.invalidate(player);
			refresh(player);
		}
	}

	@SubscribeEvent
	public static void onConfigChanged(ThreatConfigChangedEvent event) {
		WornArmorState.clearAll();
	}

	@SubscribeEvent
	public static void onServerStopped(ServerStoppedEvent event) {
		WornArmorState.clearAll();
	}

	public static void refresh(ServerPlayer player) {
		clear(player);
		if (!Config.SPEC.isLoaded() || !Config.EXIMUS_EQUIPMENT_ENABLED.get()) {
			return;
		}
		ArmorAffixSelection selection = WornArmorState.selection(player);
		int plating = selection.count("astral_plating");
		if (plating > 0) {
			add(player, Attributes.ARMOR, ASTRAL_ARMOR, "summy_threat:astral_plating",
					plating * StarPrismConfig.doubleValue(
							"armor.astral_plating.armor_value_per_stack"),
					AttributeModifier.Operation.ADDITION);
			add(player, Attributes.ARMOR_TOUGHNESS, ASTRAL_TOUGHNESS,
					"summy_threat:astral_plating_toughness",
					plating * StarPrismConfig.doubleValue(
							"armor.astral_plating.toughness_value_per_stack"),
					AttributeModifier.Operation.ADDITION);
		}
		int vital = selection.count("vital_constellation");
		if (vital > 0) {
			add(player, Attributes.MAX_HEALTH, VITAL_HEALTH,
					"summy_threat:vital_constellation",
					vital * StarPrismConfig.doubleValue(
							"armor.vital_constellation.max_health_per_stack"),
					AttributeModifier.Operation.ADDITION);
		}
		if (selection.has("anchored_stride")) {
			add(player, Attributes.KNOCKBACK_RESISTANCE, ANCHORED_KNOCKBACK,
					"summy_threat:anchored_stride",
					StarPrismConfig.doubleValue("armor.anchored_stride.knockback_bonus"),
					AttributeModifier.Operation.ADDITION);
		}
		if (hasFixed(player, EquipmentSlot.FEET, 2)) {
			double speedPercent = Config.STAR_STRIDE_SPEED_PERCENT.get();
			if (speedPercent > 0.0D) {
				add(player, Attributes.MOVEMENT_SPEED, STAR_STRIDE_SPEED,
						"summy_threat:star_stride_speed", speedPercent / 100.0D,
						AttributeModifier.Operation.MULTIPLY_TOTAL);
			}
			add(player, ForgeMod.STEP_HEIGHT_ADDITION.get(), STAR_STRIDE_STEP,
					"summy_threat:star_stride_step", Config.STAR_STRIDE_STEP_HEIGHT.get(),
					AttributeModifier.Operation.ADDITION);
		}
		if (hasFixed(player, EquipmentSlot.LEGS, 2)) {
			applySlownessFloor(player);
		}
	}

	public static void clear(ServerPlayer player) {
		remove(player, Attributes.ARMOR, ASTRAL_ARMOR);
		remove(player, Attributes.ARMOR_TOUGHNESS, ASTRAL_TOUGHNESS);
		remove(player, Attributes.MAX_HEALTH, VITAL_HEALTH);
		remove(player, Attributes.KNOCKBACK_RESISTANCE, ANCHORED_KNOCKBACK);
		remove(player, Attributes.MOVEMENT_SPEED, STAR_STRIDE_SPEED);
		remove(player, ForgeMod.STEP_HEIGHT_ADDITION.get(), STAR_STRIDE_STEP);
		remove(player, Attributes.MOVEMENT_SPEED, SLOWNESS_FLOOR);
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
	}

	private static void applySlownessFloor(ServerPlayer player) {
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed == null) {
			return;
		}
		speed.removeModifier(SLOWNESS_FLOOR);
		double base = speed.getBaseValue();
		double positiveAdd = 0.0D;
		double positiveBaseFactor = 1.0D;
		double positiveTotalFactor = 1.0D;
		for (AttributeModifier modifier : speed.getModifiers()) {
			if (modifier.getAmount() <= 0.0D) {
				continue;
			}
			switch (modifier.getOperation()) {
				case ADDITION -> positiveAdd += modifier.getAmount();
				case MULTIPLY_BASE -> positiveBaseFactor += modifier.getAmount();
				case MULTIPLY_TOTAL -> positiveTotalFactor *= 1.0D + modifier.getAmount();
			}
		}
		double positiveBaseline = (base + positiveAdd) * positiveBaseFactor
				* positiveTotalFactor;
		double target = positiveBaseline
				* Config.SLOWNESS_GUARD_FLOOR_PERCENT.get() / 100.0D;
		double current = speed.getValue();
		if (current > 1.0E-6D && current < target) {
			speed.addTransientModifier(new AttributeModifier(SLOWNESS_FLOOR,
					"summy_threat:slowness_guard", target / current - 1.0D,
					AttributeModifier.Operation.MULTIPLY_TOTAL));
		}
	}

	private static boolean hasFixed(ServerPlayer player, EquipmentSlot slot, int level) {
		ItemStack stack = player.getItemBySlot(slot);
		return EximusEquipmentData.upgradeLevel(stack) >= level
				&& stack.getItem() instanceof ArmorItem armor
				&& armor.getType().getSlot() == slot;
	}

	private static void add(ServerPlayer player, Attribute attribute, UUID id,
			String name, double amount, AttributeModifier.Operation operation) {
		if (!Double.isFinite(amount) || Math.abs(amount) < 1.0E-9D) {
			return;
		}
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance != null) {
			instance.addTransientModifier(new AttributeModifier(id, name, amount, operation));
		}
	}

	private static void remove(ServerPlayer player, Attribute attribute, UUID id) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance != null) {
			instance.removeModifier(id);
		}
	}
}
