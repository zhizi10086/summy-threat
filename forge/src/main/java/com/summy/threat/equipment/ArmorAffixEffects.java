package com.summy.threat.equipment;

import com.summy.threat.Config;
import com.summy.threat.api.event.ThreatConfigChangedEvent;
import com.summy.threat.combat.MagicResistance;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 星渊盔甲十二项词条的运行时效果。 */
public final class ArmorAffixEffects {
	private static final String STEADY_HEART_COOLDOWN = "armor.steady_heart";
	private static final String LAST_LIGHT_COOLDOWN = "armor.last_light";
	private static final String PHASE_ANCHOR_COOLDOWN = "armor.phase_anchor";
	private static final Map<UUID, TimedWindow> STEADY_HEART = new HashMap<>();
	private static final Map<UUID, TimedWindow> LAST_LIGHT_GUARD = new HashMap<>();
	private static final Map<UUID, Map<String, Long>> NEGATED_MOVEMENTS = new HashMap<>();

	private ArmorAffixEffects() {
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onLivingAttack(LivingAttackEvent event) {
		if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) {
			return;
		}
		ThreatHitContext.Hit context = ThreatHitContext.current(player, event.getSource());
		if (context != null && context.category() == ThreatCategory.TL_ABILITY
				&& "thorns".equals(context.abilityId())
				&& active(player).has("thorns_insulation")) {
			event.setCanceled(true);
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onLivingHurt(LivingHurtEvent event) {
		if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) {
			return;
		}
		ThreatHitContext.Hit context = ThreatHitContext.current(player, event.getSource());
		if (context != null && context.category() == ThreatCategory.TL_ABILITY
				&& "thorns".equals(context.abilityId())
				&& active(player).has("thorns_insulation")) {
			event.setCanceled(true);
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onGuardedDamage(LivingDamageEvent event) {
		if (event.isCanceled() || event.getAmount() <= 0.0F
				|| event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)
				|| !(event.getEntity() instanceof ServerPlayer player)) {
			return;
		}
		TimedWindow guard = LAST_LIGHT_GUARD.get(player.getUUID());
		if (guard == null) {
			return;
		}
		if (guard.expiresAt() <= now(player)) {
			LAST_LIGHT_GUARD.remove(player.getUUID());
			return;
		}
		if (active(player).has("last_light")) {
			event.setAmount(0.0F);
		} else {
			LAST_LIGHT_GUARD.remove(player.getUUID());
		}
	}

	@SubscribeEvent(priority = EventPriority.LOW)
	public static void onLivingDamage(LivingDamageEvent event) {
		if (event.isCanceled() || event.getAmount() <= 0.0F
				|| !(event.getEntity() instanceof ServerPlayer player)) {
			return;
		}
		ArmorAffixSelection active = active(player);
		if (active.counts().isEmpty()) {
			clearTransient(player);
			return;
		}
		DamageSource source = event.getSource();
		float amount = event.getAmount();

		if (active.has("projectile_ward") && source.is(DamageTypeTags.IS_PROJECTILE)) {
			amount *= multiplier("armor.projectile_ward.reduction_percent");
		}
		if (active.has("anchored_stride") && source.is(DamageTypeTags.IS_FALL)) {
			amount *= multiplier("armor.anchored_stride.fall_reduction_percent");
		}
		int mantle = active.count("star_mantle");
		if (mantle > 0) {
			amount *= Math.pow(multiplier("armor.star_mantle.reduction_percent_per_stack"),
					mantle);
		}
		long now = now(player);
		TimedWindow steady = STEADY_HEART.get(player.getUUID());
		if (steady != null) {
			if (steady.expiresAt() <= now || !active.has("steady_heart")) {
				STEADY_HEART.remove(player.getUUID());
			} else {
				amount *= multiplier("armor.steady_heart.reduction_percent");
			}
		}
		if (active.has("threat_aegis") && isHighThreatHit(player, source)) {
			amount *= multiplier("armor.threat_aegis.reduction_percent");
		}

		event.setAmount(Math.max(0.0F, amount));
		tryActivateSteadyHeart(player, source, event.getAmount(), now);
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onLastLight(LivingDamageEvent event) {
		if (event.isCanceled() || event.getAmount() <= 0.0F
				|| event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)
				|| !(event.getEntity() instanceof ServerPlayer player)
				|| !active(player).has("last_light")) {
			return;
		}
		float health = player.getHealth();
		if (event.getAmount() < health) {
			return;
		}
		long now = now(player);
		if (!PlayerEquipmentCooldowns.ready(player, LAST_LIGHT_COOLDOWN)) {
			return;
		}
		float floor = Math.max(1.0F, player.getMaxHealth()
				* (float) (StarPrismConfig.doubleValue("armor.last_light.health_floor_percent")
						/ 100.0D));
		if (health > floor) {
			event.setAmount(Math.max(0.0F, health - floor));
		} else {
			player.setHealth(Math.min(player.getMaxHealth(), floor));
			event.setAmount(0.0F);
		}
		int guardTicks = StarPrismConfig.intValue("armor.last_light.guard_ticks");
		LAST_LIGHT_GUARD.put(player.getUUID(), new TimedWindow(now + guardTicks));
		PlayerEquipmentCooldowns.set(player, LAST_LIGHT_COOLDOWN,
				StarPrismConfig.intValue("armor.last_light.cooldown_ticks"));
	}

	@SubscribeEvent
	public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
		if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
			return;
		}
		long now = now(player);
		STEADY_HEART.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
		LAST_LIGHT_GUARD.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
		cleanupMovementEvents(player, now);
		if (active(player).counts().isEmpty()) {
			clearTransient(player);
		}
	}

	@SubscribeEvent
	public static void onLivingDeath(LivingDeathEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			clearTransient(player);
		}
	}

	@SubscribeEvent
	public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			clearTransient(player);
		}
	}

	@SubscribeEvent
	public static void onConfigChanged(ThreatConfigChangedEvent event) {
		STEADY_HEART.clear();
		LAST_LIGHT_GUARD.clear();
		NEGATED_MOVEMENTS.clear();
	}

	@SubscribeEvent
	public static void onServerStopped(ServerStoppedEvent event) {
		STEADY_HEART.clear();
		LAST_LIGHT_GUARD.clear();
		NEGATED_MOVEMENTS.clear();
	}

	/** TL 腐蚀应用前读取隔层折扣；只处理实际穿戴该词条的玩家。 */
	public static double suppressCorrosionPercent(LivingEntity target, double percent) {
		if (!(target instanceof ServerPlayer player)
				|| !active(player).has("corrosion_baffle")) {
			return percent;
		}
		double suppression = clampPercent(
				StarPrismConfig.doubleValue("armor.corrosion_baffle.suppression_percent"));
		return percent * (1.0D - suppression);
	}

	/** 星咒屏障在 Protection / Resistance 后、吸收前使用的最终乘数。 */
	public static float spellwardMultiplier(LivingEntity target, DamageSource source) {
		if (!(target instanceof ServerPlayer player)
				|| !active(player).has("spellward_prism")
				|| !MagicResistance.isMagicDamage(source)) {
			return 1.0F;
		}
		return (float) multiplier("armor.spellward_prism.reduction_percent");
	}

	/**
	 * 判断一次 ST 强制位移是否应被星轨锚定抵消。
	 *
	 * <p>同一持续事件只在首次应用时消耗冷却，并在事件到期前整段抵消。
	 */
	public static boolean blocksForcedMovement(ServerPlayer player) {
		ThreatMovementContext.Movement movement = ThreatMovementContext.current(player);
		if (movement == null || !active(player).has("phase_anchor")) {
			return false;
		}
		long now = now(player);
		cleanupMovementEvents(player, now);
		Map<String, Long> events = NEGATED_MOVEMENTS.computeIfAbsent(player.getUUID(),
				id -> new HashMap<>());
		Long negatedUntil = events.get(movement.eventId());
		if (negatedUntil != null && negatedUntil > now) {
			return true;
		}
		if (!PlayerEquipmentCooldowns.ready(player, PHASE_ANCHOR_COOLDOWN)) {
			return false;
		}
		PlayerEquipmentCooldowns.set(player, PHASE_ANCHOR_COOLDOWN,
				StarPrismConfig.intValue("armor.phase_anchor.cooldown_ticks"));
		events.put(movement.eventId(), Math.max(now + 1L, movement.expiresAt()));
		return true;
	}

	private static void tryActivateSteadyHeart(ServerPlayer player, DamageSource source,
			float amount, long now) {
		if (!active(player).has("steady_heart") || isSelfDamage(player, source)
				|| !PlayerEquipmentCooldowns.ready(player, STEADY_HEART_COOLDOWN)) {
			return;
		}
		double thresholdPercent = Math.max(0.0D, Math.min(100.0D,
				StarPrismConfig.doubleValue("armor.steady_heart.threshold_percent")));
		double threshold = player.getMaxHealth() * thresholdPercent / 100.0D;
		if (player.getHealth() - amount > threshold) {
			return;
		}
		STEADY_HEART.put(player.getUUID(), new TimedWindow(now
				+ StarPrismConfig.intValue("armor.steady_heart.duration_ticks")));
		PlayerEquipmentCooldowns.set(player, STEADY_HEART_COOLDOWN,
				StarPrismConfig.intValue("armor.steady_heart.cooldown_ticks"));
	}

	private static ArmorAffixSelection active(ServerPlayer player) {
		if (!Config.SPEC.isLoaded() || !Config.EXIMUS_EQUIPMENT_ENABLED.get()
				|| !Config.STAR_PRISM_ENABLED.get()) {
			return ArmorAffixSelection.select(Map.of(), 1);
		}
		return WornArmorState.selection(player);
	}

	private static boolean isHighThreatHit(ServerPlayer player, DamageSource source) {
		return ThreatHitContext.current(player, source) != null;
	}

	private static boolean isSelfDamage(ServerPlayer player, DamageSource source) {
		if (source.getEntity() == player) {
			return true;
		}
		return source.getDirectEntity() instanceof Projectile projectile
				&& projectile.getOwner() == player;
	}

	private static double multiplier(String key) {
		return 1.0D - clampPercent(StarPrismConfig.doubleValue(key));
	}

	private static double clampPercent(double value) {
		if (!Double.isFinite(value)) {
			return 0.0D;
		}
		return Math.max(0.0D, Math.min(100.0D, value)) / 100.0D;
	}

	private static long now(ServerPlayer player) {
		return player.server.overworld().getGameTime();
	}

	private static void cleanupMovementEvents(ServerPlayer player, long now) {
		Map<String, Long> events = NEGATED_MOVEMENTS.get(player.getUUID());
		if (events == null) {
			return;
		}
		events.entrySet().removeIf(entry -> entry.getValue() <= now);
		if (events.isEmpty()) {
			NEGATED_MOVEMENTS.remove(player.getUUID());
		}
	}

	private static void clearTransient(ServerPlayer player) {
		UUID id = player.getUUID();
		STEADY_HEART.remove(id);
		LAST_LIGHT_GUARD.remove(id);
		NEGATED_MOVEMENTS.remove(id);
	}

	private record TimedWindow(long expiresAt) {
	}
}
