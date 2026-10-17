package com.summy.threat.armor;

import com.summy.threat.item.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.UUID;

/**
 * 回响下界合金四件的被动效果。
 *
 * <p>头盔：夜视 + 失明/黑暗免疫；靴子：移速 +20%，持有玩家卓越护盾时 +40%。
 * 护腿的“对敌方卓越护盾伤害”读取 {@link #shieldDamageMultiplier(Player)}，由
 * {@code EximusShield} 在护盾吸收阶段调用。
 *
 * <p>四件的额外击退抗性在 {@link PlayerArmorRefill} 里按物品栏位注入，不在这里处理。
 */
public final class EchoArmorEffects {
	private static final UUID ID_BOOTS_SPEED =
			UUID.fromString("6f5c1a2e-0009-4a0b-9d1e-000000000301");
	private static final String MODIFIER_NAME = "summy_threat:echo_boots_speed";
	/** 夜视持续时间；必须高于原版 200 tick 的闪烁阈值，并留出刷新余量。 */
	private static final int NIGHT_VISION_DURATION = 300;
	private static final int NIGHT_VISION_REFRESH_BELOW = 240;

	private EchoArmorEffects() {
	}

	public static boolean hasHelmet(Player player) {
		return wears(player, EquipmentSlot.HEAD, ModItems.ECHO_HELMET.get());
	}

	public static boolean hasChestplate(Player player) {
		return wears(player, EquipmentSlot.CHEST, ModItems.ECHO_CHESTPLATE.get());
	}

	public static boolean hasLeggings(Player player) {
		return wears(player, EquipmentSlot.LEGS, ModItems.ECHO_LEGGINGS.get());
	}

	public static boolean hasBoots(Player player) {
		return wears(player, EquipmentSlot.FEET, ModItems.ECHO_BOOTS.get());
	}

	private static boolean wears(Player player, EquipmentSlot slot, net.minecraft.world.item.Item item) {
		if (player == null) {
			return false;
		}
		ItemStack stack = player.getItemBySlot(slot);
		return stack.is(item);
	}

	/** 玩家自己是否持有卓越护盾（用于护腿/靴子二档效果）。 */
	public static boolean hasPlayerShield(Player player) {
		return player != null && PlayerEximusShield.current(player) > 0.0D;
	}

	/** 护腿：对敌方卓越护盾 1.3×；自身有盾时 1.5×。 */
	public static double shieldDamageMultiplier(Player player) {
		if (!hasLeggings(player)) {
			return 1.0D;
		}
		return hasPlayerShield(player) ? 1.5D : 1.3D;
	}

	/** 每 20 tick 刷新夜视与靴子移速。 */
	@SubscribeEvent
	public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
		if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide()
				|| !(event.player instanceof ServerPlayer player)) {
			return;
		}
		if (player.tickCount % 20 != 0) {
			return;
		}
		tickHelmet(player);
		refreshBoots(player);
	}

	/** 换靴子立即刷新，不必等到下一次 20 tick。 */
	@SubscribeEvent
	public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
		if (!(event.getEntity() instanceof ServerPlayer player)) {
			return;
		}
		if (event.getSlot() == EquipmentSlot.FEET) {
			refreshBoots(player);
		} else if (event.getSlot() == EquipmentSlot.HEAD) {
			tickHelmet(player);
		}
	}

	/** 头盔：持续夜视，并清除失明/黑暗。 */
	private static void tickHelmet(ServerPlayer player) {
		MobEffectInstance current = player.getEffect(MobEffects.NIGHT_VISION);
		if (!hasHelmet(player)) {
			// 只清理头盔自己的隐藏夜视，保留可见药水夜视。
			if (current != null && current.isAmbient() && !current.isVisible()) {
				player.removeEffect(MobEffects.NIGHT_VISION);
			}
			return;
		}
		// 可见药水夜视优先保留；只在没有夜视或头盔自己的隐藏夜视快到期时续期。
		if (current == null || (current.isAmbient() && !current.isVisible()
				&& current.getDuration() < NIGHT_VISION_REFRESH_BELOW)) {
			player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,
					NIGHT_VISION_DURATION, 0, true, false, false));
		}
		player.removeEffect(MobEffects.BLINDNESS);
		player.removeEffect(MobEffects.DARKNESS);
	}

	/** 失明/黑暗在施加时直接拒绝；已存在的会在头盔 tick 中移除。 */
	@SubscribeEvent
	public static void onEffectApplicable(MobEffectEvent.Applicable event) {
		if (!(event.getEntity() instanceof Player player) || !hasHelmet(player)) {
			return;
		}
		if (event.getEffectInstance().getEffect() == MobEffects.BLINDNESS
				|| event.getEffectInstance().getEffect() == MobEffects.DARKNESS) {
			event.setResult(Event.Result.DENY);
		}
	}

	/** 刷新靴子移速：无盾 +20%，有盾 +40%；脱下时完全移除。 */
	public static void refreshBoots(ServerPlayer player) {
		if (player == null) {
			return;
		}
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed == null) {
			return;
		}
		speed.removeModifier(ID_BOOTS_SPEED);
		if (!hasBoots(player)) {
			return;
		}
		double amount = hasPlayerShield(player) ? 0.40D : 0.20D;
		speed.addTransientModifier(new AttributeModifier(ID_BOOTS_SPEED, MODIFIER_NAME, amount,
				AttributeModifier.Operation.MULTIPLY_TOTAL));
	}
}
