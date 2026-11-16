package com.summy.threat.equipment;

import com.summy.threat.Config;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** 澄明视界与应急生息。 */
public final class EximusArmorPassives {
	private static final int NIGHT_VISION_DURATION = 300;
	private static final int NIGHT_VISION_REFRESH_BELOW = 240;
	private static final Set<UUID> PENDING_HEAL = new HashSet<>();

	private EximusArmorPassives() {
	}

	@SubscribeEvent
	public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
		if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide()
				|| !(event.player instanceof ServerPlayer player)) {
			return;
		}
		tickHelmet(player);
		if (PENDING_HEAL.remove(player.getUUID())) {
			applyMending(player);
		}
	}

	@SubscribeEvent
	public static void onEffectApplicable(MobEffectEvent.Applicable event) {
		if (event.getEntity() instanceof ServerPlayer player
				&& hasFixed(player, EquipmentSlot.HEAD)
				&& (event.getEffectInstance().getEffect() == MobEffects.BLINDNESS
				|| event.getEffectInstance().getEffect() == MobEffects.DARKNESS)) {
			event.setResult(Event.Result.DENY);
		}
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onLivingDamage(LivingDamageEvent event) {
		if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)
				|| !hasFixed(player, EquipmentSlot.CHEST)
				|| !validAttack(player, event.getSource())
				|| !EquipmentConfigValidation.emergencyMendingValid()
				|| !PlayerEquipmentCooldowns.ready(player, "emergency_mending")) {
			return;
		}
		PENDING_HEAL.add(player.getUUID());
	}

	@SubscribeEvent
	public static void onLivingDeath(LivingDeathEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			PENDING_HEAL.remove(player.getUUID());
		}
	}

	private static void applyMending(ServerPlayer player) {
		if (!Config.SPEC.isLoaded() || !Config.EXIMUS_EQUIPMENT_ENABLED.get()
				|| !hasFixed(player, EquipmentSlot.CHEST)
				|| !EquipmentConfigValidation.emergencyMendingValid()
				|| player.isDeadOrDying() || player.getHealth() >= player.getMaxHealth()) {
			return;
		}
		PlayerEquipmentCooldowns.set(player, "emergency_mending",
				Config.EMERGENCY_MENDING_COOLDOWN_TICKS.get());
		double amount = Math.max(Config.EMERGENCY_MENDING_HEAL_MIN.get(),
				Math.min(Config.EMERGENCY_MENDING_HEAL_MAX.get(),
						player.getMaxHealth()
								* Config.EMERGENCY_MENDING_HEAL_PERCENT.get() / 100.0D));
		player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + (float) amount));
	}

	private static void tickHelmet(ServerPlayer player) {
		MobEffectInstance current = player.getEffect(MobEffects.NIGHT_VISION);
		boolean enabled = Config.SPEC.isLoaded() && Config.EXIMUS_EQUIPMENT_ENABLED.get()
				&& hasFixed(player, EquipmentSlot.HEAD);
		if (!enabled) {
			if (current != null && current.isAmbient() && !current.isVisible()) {
				player.removeEffect(MobEffects.NIGHT_VISION);
			}
			return;
		}
		player.removeEffect(MobEffects.BLINDNESS);
		player.removeEffect(MobEffects.DARKNESS);
		int light = player.level().getMaxLocalRawBrightness(player.blockPosition());
		if (light > Config.CLEAR_SIGHT_NIGHT_VISION_LIGHT.get()) {
			return;
		}
		if (current == null || current.isAmbient() && !current.isVisible()
				&& current.getDuration() < NIGHT_VISION_REFRESH_BELOW) {
			player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,
					NIGHT_VISION_DURATION, 0, true, false, false));
		}
	}

	private static boolean hasFixed(ServerPlayer player, EquipmentSlot slot) {
		ItemStack stack = player.getItemBySlot(slot);
		return EximusEquipmentData.upgradeLevel(stack) >= 2
				&& stack.getItem() instanceof ArmorItem armor
				&& armor.getType().getSlot() == slot;
	}

	private static boolean validAttack(ServerPlayer player, DamageSource source) {
		Entity sourceEntity = source.getEntity();
		Entity direct = source.getDirectEntity();
		if (sourceEntity == player
				|| direct instanceof Projectile projectile && projectile.getOwner() == player) {
			return false;
		}
		if (sourceEntity instanceof LivingEntity) {
			return true;
		}
		return direct instanceof Projectile projectile
				&& projectile.getOwner() instanceof LivingEntity;
	}
}
