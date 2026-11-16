package com.summy.threat.equipment;

import com.summy.threat.Config;
import com.summy.threat.api.event.ThreatConfigChangedEvent;
import com.summy.threat.eximus.EximusShield;
import com.summy.threat.leader.LegendaryResistance;
import com.summy.threat.scaling.MonsterScaler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 近战武器的单次主目标、词条结算和延迟附伤。
 *
 * <p>CriticalHitEvent 只记录本次真正的主目标；LivingHurtEvent 再按 DamageSource
 * 直接归属确认，因此横扫、投掷三叉戟、取消伤害和没有主目标的伤害都不会误触发。</p>
 */
public final class MeleeWeaponRuntime {
	private static final Map<UUID, Pending> PENDING = new HashMap<>();
	private static final Map<UUID, DamageRecord> DAMAGE_RECORDS = new HashMap<>();
	private static final List<DelayedMagic> MAGIC = new ArrayList<>();
	private static final List<DelayedHeal> HEALS = new ArrayList<>();

	private MeleeWeaponRuntime() {
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onCriticalHit(CriticalHitEvent event) {
		if (event.getEntity().level().isClientSide()
				|| !(event.getEntity() instanceof ServerPlayer player)
				|| !(event.getTarget() instanceof LivingEntity target)
				|| !eligible(player.getMainHandItem())) {
			return;
		}
		WeaponAffixSnapshot snapshot = snapshot(player);
		double critChance = StarPrismConfig.doubleValue(
				"melee.weakpoint_prism.crit_chance_percent") / 100.0D;
		double critBonus = StarPrismConfig.doubleValue(
				"melee.weakpoint_prism.crit_multiplier_bonus");
		boolean weakpoint = snapshot.has("weakpoint_prism");
		if (weakpoint && !WeaponPlayerAttributes.externalCritAttributes()) {
			if (event.isVanillaCritical()) {
				event.setDamageModifier((float) (event.getDamageModifier() + critBonus));
			} else if (player.getRandom().nextDouble() < clamp01(critChance)) {
				// 没有 ApothicAttributes 时，普通命中补判为独立 1.75 倍暴击。
				event.setDamageModifier((float) (event.getDamageModifier() * 1.75D));
			}
		}
		WeaponRuntimeService.syncMainhand(player, player.getMainHandItem());
		PENDING.put(player.getUUID(), new Pending(target.getUUID(), snapshot,
				player.server.overworld().getGameTime()));
	}

	@SubscribeEvent(priority = EventPriority.LOW)
	public static void onLivingHurt(LivingHurtEvent event) {
		if (event.isCanceled() || event.getAmount() <= 0.0F
				|| event.getEntity().level().isClientSide()
				|| event.getSource().is(DamageTypeTags.IS_PROJECTILE)) {
			return;
		}
		if (isPrismMagic(event.getSource())) {
			return;
		}
		if (event.getSource().getDirectEntity() instanceof LivingEntity attacker
				&& !(attacker instanceof ServerPlayer)
				&& eligible(attacker.getMainHandItem())) {
			event.setAmount(event.getAmount() * 1.10F);
		}
		if (!(event.getSource().getEntity() instanceof ServerPlayer player)
				|| event.getSource().getDirectEntity() != player) {
			return;
		}
		Pending pending = PENDING.get(player.getUUID());
		if (pending == null || !pending.target().equals(event.getEntity().getUUID())) {
			return;
		}
		PENDING.remove(player.getUUID());
		if (!eligible(player.getMainHandItem())) {
			return;
		}
		WeaponAffixSnapshot snapshot = pending.snapshot();
		LivingEntity target = event.getEntity();
		double amount = event.getAmount();
		if (snapshot.level() >= 1) {
			amount *= 1.10D;
		}
		if (WeaponRuntimeService.sharedMarkActive(player, target)) {
			amount *= 1.0D + Config.SHARED_MARK_DAMAGE_PERCENT.get() / 100.0D;
		}
		double pursuitPercent = WeaponRuntimeService.pursuitMultiplier(player, target,
				WeaponRuntimeService.weaponKey(player.getMainHandItem()));
		if (pursuitPercent > 0.0D) {
			amount *= 1.0D + pursuitPercent;
		}
		if (snapshot.has("shield_render") && isShielded(target)) {
			amount *= 1.0D + StarPrismConfig.doubleValue(
					"melee.shield_render.bonus_percent") / 100.0D;
		}
		if (snapshot.has("concussive_prism")) {
			double resistance = target.getAttributes().hasAttribute(Attributes.KNOCKBACK_RESISTANCE)
					? target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) : 0.0D;
			amount *= WeaponDamageMath.concussiveMultiplier(resistance,
					StarPrismConfig.doubleValue(
							"melee.concussive_prism.damage_per_resistance_percent"));
		}
		if (snapshot.has("terminal_star") && player.getRandom().nextDouble()
				< StarPrismConfig.doubleValue("melee.terminal_star.chance_percent") / 100.0D) {
			amount *= StarPrismConfig.doubleValue("melee.terminal_star.multiplier");
		}

		event.setAmount((float) amount);
		if (snapshot.has("breach_constellation")) {
			ArmorReductionContext.add(target, event.getSource(),
					StarPrismConfig.doubleValue(
							"melee.breach_constellation.armor_reduction_points"));
		}
		boolean highThreat = isHighThreat(target);
		if (snapshot.has("arcane_edge")) {
			queueMagic(target, player, amount * StarPrismConfig.doubleValue(
					"melee.arcane_edge.magic_percent") / 100.0D);
		}
		if (highThreat && snapshot.has("starfire_cycle")
				&& WeaponRuntimeService.advanceStarfire(player)) {
			queueMagic(target, player, amount * StarPrismConfig.doubleValue(
					"melee.starfire_cycle.magic_percent") / 100.0D);
		}
		if (snapshot.has("void_siphon")) {
			DAMAGE_RECORDS.put(target.getUUID(), new DamageRecord(target, event.getSource(),
					player, StarPrismConfig.doubleValue(
							"melee.void_siphon.lifesteal_percent")));
		}
		if (snapshot.markCapable()) {
			WeaponRuntimeService.markHit(player, target);
		}
		if (snapshot.has("abyssal_pursuit")) {
			WeaponRuntimeService.addPursuitStack(player, target,
					WeaponRuntimeService.weaponKey(player.getMainHandItem()));
		}
	}

	/** 由最终承伤入口在护盾、吸收和减伤之后调用。 */
	public static void onFinalDamage(LivingDamageEvent event) {
		ArmorReductionContext.clear(event.getEntity(), event.getSource());
		DamageRecord record = DAMAGE_RECORDS.get(event.getEntity().getUUID());
		if (record == null || record.target() != event.getEntity()
				|| record.source() != event.getSource()) {
			return;
		}
		DAMAGE_RECORDS.remove(event.getEntity().getUUID());
		if (event.isCanceled() || event.getAmount() <= 0.0F) {
			return;
		}
		// LivingDamageEvent 的金额是护甲/保护/吸收后、真正准备扣除的生命值。
		double actual = Math.min(event.getAmount(), event.getEntity().getHealth());
		if (actual <= 0.0D) {
			return;
		}
		HEALS.add(new DelayedHeal(record.player(),
				actual * record.lifestealPercent() / 100.0D));
	}

	@SubscribeEvent
	public static void onServerTick(TickEvent.ServerTickEvent event) {
		if (event.phase != TickEvent.Phase.END) {
			return;
		}
		long now = event.getServer().overworld().getGameTime();
		PENDING.entrySet().removeIf(entry -> now - entry.getValue().tick() > 2L);
		for (DelayedMagic magic : new ArrayList<>(MAGIC)) {
			if (!magic.target().isAlive() || !magic.player().isAlive()) {
				MAGIC.remove(magic);
				continue;
			}
			PrismMagicDamage.hurt(magic.target(), magic.player(), (float) magic.amount());
			MAGIC.remove(magic);
		}
		for (DelayedHeal heal : new ArrayList<>(HEALS)) {
			if (heal.player().isAlive() && heal.amount() > 0.0D) {
				heal.player().heal((float) heal.amount());
			}
			HEALS.remove(heal);
		}
	}

	@SubscribeEvent
	public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			clearPlayer(player.getUUID());
		}
	}

	@SubscribeEvent
	public static void onLivingDeath(LivingDeathEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			clearPlayer(player.getUUID());
		}
		DAMAGE_RECORDS.remove(event.getEntity().getUUID());
	}

	@SubscribeEvent
	public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			clearPlayer(player.getUUID());
		}
	}

	@SubscribeEvent
	public static void onConfigChanged(ThreatConfigChangedEvent event) {
		clearAll();
	}

	@SubscribeEvent
	public static void onServerStopped(ServerStoppedEvent event) {
		clearAll();
	}

	public static void clearAll() {
		PENDING.clear();
		DAMAGE_RECORDS.clear();
		MAGIC.clear();
		HEALS.clear();
		ArmorReductionContext.clearAll();
	}

	private static void clearPlayer(UUID player) {
		PENDING.remove(player);
		DAMAGE_RECORDS.entrySet().removeIf(entry -> entry.getValue().player().getUUID()
				.equals(player));
	}

	private static void queueMagic(LivingEntity target, ServerPlayer player, double amount) {
		if (amount > 0.0D) {
			MAGIC.add(new DelayedMagic(target, player, amount));
		}
	}

	private static WeaponAffixSnapshot snapshot(ServerPlayer player) {
		if (!Config.STAR_PRISM_ENABLED.get()) {
			return new WeaponAffixSnapshot(Math.max(1, EximusEquipmentData.upgradeLevel(
					player.getMainHandItem())), List.of());
		}
		return WeaponAffixSnapshot.read(player.getMainHandItem(), EquipmentCategory.MELEE);
	}

	private static boolean eligible(net.minecraft.world.item.ItemStack stack) {
		return Config.SPEC.isLoaded() && Config.EXIMUS_EQUIPMENT_ENABLED.get()
				&& EximusEquipmentData.upgradeLevel(stack) >= 1
				&& EquipmentTypeResolver.category(stack) == EquipmentCategory.MELEE;
	}

	private static boolean isPrismMagic(DamageSource source) {
		return source.typeHolder().unwrapKey()
				.map(key -> "summy_threat:star_prism_magic".equals(key.location().toString()))
				.orElse(false);
	}

	private static boolean isShielded(LivingEntity target) {
		return EximusShield.isEximus(target)
				|| target.getPersistentData().getBoolean(LegendaryResistance.TAG_RESISTANCE);
	}

	private static boolean isHighThreat(LivingEntity target) {
		MonsterScaler.Kind kind = MonsterScaler.kindOf(target);
		return kind == MonsterScaler.Kind.EXIMUS || kind == MonsterScaler.Kind.LEADER
				|| kind == MonsterScaler.Kind.BOSS || isShielded(target);
	}

	private static double clamp01(double value) {
		return Math.max(0.0D, Math.min(1.0D, value));
	}

	private record Pending(UUID target, WeaponAffixSnapshot snapshot, long tick) {
	}

	private record DamageRecord(LivingEntity target, DamageSource source,
			ServerPlayer player, double lifestealPercent) {
	}

	private record DelayedMagic(LivingEntity target, ServerPlayer player, double amount) {
	}

	private record DelayedHeal(ServerPlayer player, double amount) {
	}
}
