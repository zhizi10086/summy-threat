package com.summy.threat.equipment;

import com.summy.threat.Config;
import com.summy.threat.api.event.ThreatConfigChangedEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** 玩家共享标记、追猎和轮转计数的服务端生命周期入口。 */
public final class WeaponRuntimeService {
	private static final Map<UUID, WeaponRuntimeState> STATES = new HashMap<>();

	private WeaponRuntimeService() {
	}

	@SubscribeEvent
	public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
		if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
			return;
		}
		if (!Config.SPEC.isLoaded() || !Config.EXIMUS_EQUIPMENT_ENABLED.get()) {
			clear(player);
			return;
		}
		WeaponRuntimeState state = state(player);
		ItemStack mainhand = player.getMainHandItem();
		EquipmentCategory category = EquipmentTypeResolver.category(mainhand);
		boolean melee = category == EquipmentCategory.MELEE;
		boolean ranged = category == EquipmentCategory.RANGED;
		state.changeMainhand(melee ? weaponKey(mainhand) : "");
		state.changeRangedMainhand(ranged ? weaponKey(mainhand) : "");
		WeaponPlayerAttributes.refresh(player, state);
	}

	@SubscribeEvent
	public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
		if (event.getSlot().getType() == net.minecraft.world.entity.EquipmentSlot.Type.HAND
				&& event.getEntity() instanceof ServerPlayer player) {
			WeaponRuntimeState state = state(player);
			EquipmentCategory category = EquipmentTypeResolver.category(player.getMainHandItem());
			state.changeMainhand(category == EquipmentCategory.MELEE
					? weaponKey(player.getMainHandItem()) : "");
			state.changeRangedMainhand(category == EquipmentCategory.RANGED
					? weaponKey(player.getMainHandItem()) : "");
			WeaponPlayerAttributes.refresh(player, state);
		}
	}

	@SubscribeEvent
	public static void onLivingDeath(LivingDeathEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			clear(player);
		}
	}

	@SubscribeEvent
	public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			clear(player);
		}
	}

	@SubscribeEvent
	public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			clear(player);
		}
	}

	@SubscribeEvent
	public static void onConfigChanged(ThreatConfigChangedEvent event) {
		STATES.clear();
		var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
		if (server != null) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				WeaponPlayerAttributes.clear(player);
			}
		}
	}

	@SubscribeEvent
	public static void onServerStopped(ServerStoppedEvent event) {
		STATES.clear();
	}

	public static boolean sharedMarkActive(ServerPlayer player, LivingEntity target) {
		return enabled() && state(player).sharedMarkActive(target.getUUID(), now(player));
	}

	public static void markHit(ServerPlayer player, LivingEntity target) {
		if (!enabled() || player == null || target == null) {
			return;
		}
		int duration = Config.SPEC.isLoaded()
				? Config.SHARED_MARK_DURATION_TICKS.get() : 80;
		state(player).markHit(target.getUUID(), now(player), duration);
	}

	public static double pursuitMultiplier(ServerPlayer player, LivingEntity target,
			String weaponKey) {
		if (!enabled() || player == null || target == null) {
			return 1.0D;
		}
		double percent = StarPrismConfig.doubleValue(
				"melee.abyssal_pursuit.damage_per_stack_percent");
		return state(player).pursuitMultiplier(target.getUUID(), weaponKey,
				now(player), StarPrismConfig.intValue("melee.abyssal_pursuit.reset_ticks"),
				percent);
	}

	public static double rangedPursuitMultiplier(ServerPlayer player, LivingEntity target,
			String weaponKey) {
		if (!enabled() || player == null || target == null) {
			return 0.0D;
		}
		double percent = StarPrismConfig.doubleValue(
				"ranged.abyssal_hunt_arrow.damage_per_stack_percent");
		return state(player).pursuitMultiplier(target.getUUID(), weaponKey,
				now(player), StarPrismConfig.intValue("ranged.abyssal_hunt_arrow.reset_ticks"),
				percent);
	}

	public static int addPursuitStack(ServerPlayer player, LivingEntity target,
			String weaponKey) {
		if (!enabled() || player == null || target == null) {
			return 0;
		}
		return state(player).pursuitHit(target.getUUID(), weaponKey, now(player),
				StarPrismConfig.intValue("melee.abyssal_pursuit.max_stacks"),
				StarPrismConfig.intValue("melee.abyssal_pursuit.reset_ticks"));
	}

	public static int addRangedPursuitStack(ServerPlayer player, LivingEntity target,
			String weaponKey) {
		if (!enabled() || player == null || target == null) {
			return 0;
		}
		return state(player).pursuitHit(target.getUUID(), weaponKey, now(player),
				StarPrismConfig.intValue("ranged.abyssal_hunt_arrow.max_stacks"),
				StarPrismConfig.intValue("ranged.abyssal_hunt_arrow.reset_ticks"));
	}

	public static boolean advanceStarfire(ServerPlayer player) {
		return enabled() && state(player).starfireHit(
				StarPrismConfig.intValue("melee.starfire_cycle.hits"));
	}

	public static boolean advanceStarfall(ServerPlayer player) {
		return enabled() && state(player).starfallHit(
				StarPrismConfig.intValue("ranged.starfall_cycle_arrow.hits"));
	}

	public static int pursuitStacks(ServerPlayer player) {
		return player == null ? 0 : state(player).pursuitStacks();
	}

	public static String weaponKey(ItemStack stack) {
		if (stack.isEmpty()) {
			return "";
		}
		return BuiltInRegistries.ITEM.getKey(stack.getItem()) + "|"
				+ stack.getDamageValue() + "|" + Objects.hashCode(stack.getTag());
	}

	public static void syncMainhand(ServerPlayer player, ItemStack stack) {
		if (player == null) {
			return;
		}
		state(player).changeMainhand(EquipmentTypeResolver.category(stack)
				== EquipmentCategory.MELEE ? weaponKey(stack) : "");
		state(player).changeRangedMainhand(EquipmentTypeResolver.category(stack)
				== EquipmentCategory.RANGED ? weaponKey(stack) : "");
	}

	public static void clear(ServerPlayer player) {
		if (player == null) {
			return;
		}
		STATES.remove(player.getUUID());
		WeaponPlayerAttributes.clear(player);
	}

	public static boolean enabled() {
		return WeaponRuntimeService.enabledInternal();
	}

	private static WeaponRuntimeState state(ServerPlayer player) {
		return STATES.computeIfAbsent(player.getUUID(), id -> new WeaponRuntimeState());
	}

	private static long now(ServerPlayer player) {
		return player.server.overworld().getGameTime();
	}

	private static boolean enabledInternal() {
		return Config.SPEC.isLoaded() && Config.EXIMUS_EQUIPMENT_ENABLED.get()
				&& Config.STAR_PRISM_ENABLED.get();
	}
}
