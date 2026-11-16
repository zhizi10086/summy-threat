package com.summy.threat.equipment;

import com.summy.threat.Config;
import com.summy.threat.api.event.ThreatConfigChangedEvent;
import com.summy.threat.eximus.EximusShield;
import com.summy.threat.leader.LegendaryResistance;
import com.summy.threat.scaling.MonsterScaler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.ArrowLooseEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 远程武器的发射快照、投射物初始修正和命中词条。
 *
 * <p>一次齐射共用一个 shot ID；多重射击/穿透箭共享概率与计数，但每支实体只应用一次初速和穿透修正。</p>
 */
public final class RangedWeaponRuntime {
	private static final Map<UUID, PendingShot> PENDING_SHOTS = new HashMap<>();
	private static final Map<UUID, RangedShotState> SHOTS = new HashMap<>();
	private static final List<AbstractArrow> PENDING_ARROWS = new ArrayList<>();
	private static final Map<UUID, List<RangedDamageRecord>> DAMAGE_RECORDS = new HashMap<>();
	private static final List<DelayedMagic> MAGIC = new ArrayList<>();
	private static final List<DelayedHeal> HEALS = new ArrayList<>();
	private static final long SHOT_TTL = 240L;

	private RangedWeaponRuntime() {
	}

	@SubscribeEvent
	public static void onArrowLoose(ArrowLooseEvent event) {
		if (event.isCanceled() || event.getLevel().isClientSide()
				|| !(event.getEntity() instanceof ServerPlayer player)
				|| !event.hasAmmo() || !eligible(event.getBow())) {
			return;
		}
		boolean crossbow = event.getBow().getItem() instanceof CrossbowItem;
		if (!crossbow && event.getCharge() < requiredDrawTicks(event.getBow())) {
			return;
		}
		WeaponAffixSnapshot snapshot = snapshot(event.getBow());
		String armorAffix = joined(categorySnapshot(event.getBow(), EquipmentCategory.ARMOR));
		String meleeAffix = joined(categorySnapshot(event.getBow(), EquipmentCategory.MELEE));
		UUID shotId = UUID.randomUUID();
		boolean sidereal = snapshot.has("sidereal_aim")
				&& player.getRandom().nextDouble()
				< StarPrismConfig.doubleValue("ranged.sidereal_aim.chance_percent") / 100.0D;
		boolean terminal = snapshot.has("terminal_star_shot")
				&& player.getRandom().nextDouble()
				< StarPrismConfig.doubleValue("ranged.terminal_star_shot.chance_percent") / 100.0D;
		String weaponKey = WeaponRuntimeService.weaponKey(event.getBow());
		RangedShotState state = new RangedShotState(shotId, sidereal, terminal,
				snapshot.markCapable(), now(player), weaponKey);
		SHOTS.put(shotId, state);
		PENDING_SHOTS.put(player.getUUID(),
				new PendingShot(snapshot, armorAffix, meleeAffix, state, now(player)));
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onEntityJoin(EntityJoinLevelEvent event) {
		if (event.isCanceled() || event.loadedFromDisk() || event.getLevel().isClientSide()
				|| !(event.getEntity() instanceof AbstractArrow arrow)
				|| arrow instanceof ThrownTrident
				|| !(arrow.getOwner() instanceof LivingEntity owner)) {
			return;
		}
		PrismProjectileData existing = PrismProjectileData.read(arrow.getPersistentData()).orElse(null);
		if (existing != null) {
			if (owner instanceof ServerPlayer && (!existing.velocityApplied()
					|| !existing.pierceApplied())) {
				PENDING_ARROWS.add(arrow);
			}
			return;
		}
		if (!(owner instanceof ServerPlayer player)) {
			if (!eligible(owner.getMainHandItem())) {
				return;
			}
			WeaponAffixSnapshot snapshot = snapshot(owner.getMainHandItem());
			PrismProjectileData data = new PrismProjectileData(
					PrismProjectileData.SCHEMA, UUID.randomUUID(), snapshot.level(),
					null, null, joined(snapshot), false, false, false, false, false);
			PrismProjectileData.write(arrow.getPersistentData(), data);
			PENDING_ARROWS.add(arrow);
			return;
		}
		PendingShot pending = PENDING_SHOTS.get(player.getUUID());
		if (pending == null || now(player) - pending.tick() > 2L) {
			return;
		}
		WeaponAffixSnapshot snapshot = pending.snapshot();
		PrismProjectileData data = new PrismProjectileData(
				PrismProjectileData.SCHEMA, pending.state().shotId(), snapshot.level(),
				pending.armorAffix(), pending.meleeAffix(), joined(snapshot), snapshot.markCapable(),
				pending.state().siderealAim(), pending.state().terminalStarShot(),
				false, false);
		PrismProjectileData.write(arrow.getPersistentData(), data);
		PENDING_ARROWS.add(arrow);
	}

	@SubscribeEvent
	public static void onServerTick(TickEvent.ServerTickEvent event) {
		if (event.phase == TickEvent.Phase.START) {
			applyInitialProjectileModifiers();
			return;
		}
		if (event.phase != TickEvent.Phase.END) {
			return;
		}
		long now = event.getServer().overworld().getGameTime();
		PENDING_SHOTS.entrySet().removeIf(entry -> now - entry.getValue().tick() > 2L);
		SHOTS.entrySet().removeIf(entry -> entry.getValue().expired(now, SHOT_TTL));
		for (DelayedMagic magic : new ArrayList<>(MAGIC)) {
			if (magic.target().isAlive() && magic.player().isAlive()) {
				PrismMagicDamage.hurt(magic.target(), magic.player(), (float) magic.amount());
			}
			MAGIC.remove(magic);
		}
		for (DelayedHeal heal : new ArrayList<>(HEALS)) {
			if (heal.player().isAlive() && heal.amount() > 0.0D) {
				heal.player().heal((float) heal.amount());
			}
			HEALS.remove(heal);
		}
	}

	@SubscribeEvent(priority = EventPriority.LOW)
	public static void onLivingHurt(LivingHurtEvent event) {
		if (event.isCanceled() || event.getAmount() <= 0.0F
				|| event.getEntity().level().isClientSide()
				|| !(event.getSource().getDirectEntity() instanceof AbstractArrow arrow)
				|| !(arrow.getOwner() instanceof LivingEntity owner)) {
			return;
		}
		PrismProjectileData data = PrismProjectileData.read(arrow.getPersistentData()).orElse(null);
		if (data == null) {
			return;
		}
		if (!(owner instanceof ServerPlayer player)) {
			if (data.fixedLevel() >= 1) {
				event.setAmount(event.getAmount() * 1.10F);
			}
			return;
		}
		RangedShotState state = SHOTS.computeIfAbsent(data.shotId(),
				id -> new RangedShotState(id, data.siderealAim(), data.terminalStarShot(),
						data.markCapable(), now(player)));
		WeaponAffixSnapshot snapshot = snapshot(data);
		double amount = event.getAmount();
		if (data.fixedLevel() >= 1) {
			amount *= 1.10D;
		}
		if (snapshot.has("starlit_bowstring")) {
			amount *= 1.0D + StarPrismConfig.doubleValue(
					"ranged.starlit_bowstring.damage_percent") / 100.0D;
		}
		if (snapshot.has("shield_render_arrow") && isShielded(event.getEntity())) {
			amount *= 1.0D + StarPrismConfig.doubleValue(
					"ranged.shield_render_arrow.bonus_percent") / 100.0D;
		}
		double pursuitPercent = WeaponRuntimeService.rangedPursuitMultiplier(player,
				event.getEntity(), state.weaponKey());
		if (pursuitPercent > 0.0D) {
			amount *= 1.0D + pursuitPercent;
		}
		if (data.siderealAim() && state.claim("sidereal_aim")) {
			amount *= StarPrismConfig.doubleValue("ranged.sidereal_aim.multiplier");
		}
		if (data.terminalStarShot() && state.claim("terminal_star_shot")) {
			amount *= StarPrismConfig.doubleValue("ranged.terminal_star_shot.multiplier");
		}
		event.setAmount((float) amount);
		if (snapshot.has("breach_star_arrow")) {
			ArmorReductionContext.add(event.getEntity(), event.getSource(),
					StarPrismConfig.doubleValue(
							"ranged.breach_star_arrow.armor_reduction_points"));
		}
		boolean highThreat = isHighThreat(event.getEntity());
		if (snapshot.has("arcane_arrow") && state.claim("arcane_arrow")) {
			queueMagic(event.getEntity(), player, amount * StarPrismConfig.doubleValue(
					"ranged.arcane_arrow.magic_percent") / 100.0D);
		}
		if (highThreat && snapshot.has("starfall_cycle_arrow")
				&& state.claim("starfall_cycle") && WeaponRuntimeService.advanceStarfall(player)) {
			queueMagic(event.getEntity(), player, amount * StarPrismConfig.doubleValue(
					"ranged.starfall_cycle_arrow.magic_percent") / 100.0D);
		}
		if (snapshot.has("abyssal_hunt_arrow") && state.claim("abyssal_hunt")) {
		WeaponRuntimeService.addRangedPursuitStack(player, event.getEntity(),
					state.weaponKey());
		}
		if (state.markCapable() && state.claim("mark")) {
			WeaponRuntimeService.markHit(player, event.getEntity());
		}
		if (snapshot.has("starlight_dew") && state.claim("starlight_dew")
				&& isValidEnemy(event.getEntity())) {
			tryStarlightDew(player);
		}
		if (snapshot.has("void_quiver") && state.claim("void_quiver")) {
			DAMAGE_RECORDS.computeIfAbsent(event.getEntity().getUUID(),
					id -> new ArrayList<>()).add(new RangedDamageRecord(
							event.getEntity(), event.getSource(), player,
							StarPrismConfig.doubleValue(
									"ranged.void_quiver.lifesteal_percent")));
		}
	}

	/** 由最终承伤入口在护盾、吸收和减伤之后调用。 */
	public static void onFinalDamage(LivingDamageEvent event) {
		ArmorReductionContext.clear(event.getEntity(), event.getSource());
		List<RangedDamageRecord> records = DAMAGE_RECORDS.get(event.getEntity().getUUID());
		if (records == null) {
			return;
		}
		Iterator<RangedDamageRecord> iterator = records.iterator();
		while (iterator.hasNext()) {
			RangedDamageRecord record = iterator.next();
			if (record.target() != event.getEntity() || record.source() != event.getSource()) {
				continue;
			}
			iterator.remove();
			if (!event.isCanceled() && event.getAmount() > 0.0F) {
				double actual = Math.min(event.getAmount(), event.getEntity().getHealth());
				if (actual > 0.0D) {
					HEALS.add(new DelayedHeal(record.player(),
							actual * record.lifestealPercent() / 100.0D));
				}
			}
			break;
		}
		if (records.isEmpty()) {
			DAMAGE_RECORDS.remove(event.getEntity().getUUID());
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
		PENDING_SHOTS.clear();
		SHOTS.clear();
		PENDING_ARROWS.clear();
		DAMAGE_RECORDS.clear();
		MAGIC.clear();
		HEALS.clear();
		ArmorReductionContext.clearAll();
	}

	private static void clearPlayer(UUID player) {
		PENDING_SHOTS.remove(player);
		DAMAGE_RECORDS.entrySet().removeIf(entry -> entry.getValue().stream()
				.anyMatch(record -> record.player().getUUID().equals(player)));
	}

	private static void applyInitialProjectileModifiers() {
		for (AbstractArrow arrow : new ArrayList<>(PENDING_ARROWS)) {
			PENDING_ARROWS.remove(arrow);
			if (arrow.isRemoved()) {
				continue;
			}
			PrismProjectileData data = PrismProjectileData.read(arrow.getPersistentData()).orElse(null);
			if (data == null) {
				continue;
			}
			boolean velocityApplied = data.velocityApplied();
			boolean pierceApplied = data.pierceApplied();
			if (!velocityApplied && data.fixedLevel() >= 1) {
				Vec3 velocity = arrow.getDeltaMovement().scale(1.10D * 1.15D);
				arrow.setDeltaMovement(velocity);
				velocityApplied = true;
			}
			if (!pierceApplied && hasAffix(data, "comet_fletching")) {
				int bonus = StarPrismConfig.intValue("ranged.comet_fletching.pierce_bonus");
				int level = Math.min(127, arrow.getPierceLevel() + bonus);
				arrow.setPierceLevel((byte) level);
				pierceApplied = true;
			}
			PrismProjectileData.write(arrow.getPersistentData(),
					new PrismProjectileData(data.schema(), data.shotId(), data.fixedLevel(),
							data.armorAffix(), data.meleeAffix(), data.rangedAffix(),
							data.markCapable(), data.siderealAim(), data.terminalStarShot(),
							velocityApplied, pierceApplied));
		}
	}

	private static void tryStarlightDew(ServerPlayer player) {
		if (player.getHealth() >= player.getMaxHealth()
				|| !PlayerEquipmentCooldowns.ready(player, "ranged.starlight_dew")) {
			return;
		}
		double heal = StarPrismConfig.doubleValue("ranged.starlight_dew.heal_flat")
				+ player.getMaxHealth()
				* StarPrismConfig.doubleValue("ranged.starlight_dew.heal_percent") / 100.0D;
		heal = Math.min(StarPrismConfig.doubleValue("ranged.starlight_dew.heal_max"), heal);
		if (heal > 0.0D) {
			PlayerEquipmentCooldowns.set(player, "ranged.starlight_dew",
					StarPrismConfig.intValue("ranged.starlight_dew.cooldown_ticks"));
			HEALS.add(new DelayedHeal(player, heal));
		}
	}

	private static boolean isValidEnemy(LivingEntity target) {
		if (target instanceof net.minecraft.world.entity.player.Player
				|| target instanceof TamableAnimal tame && tame.isTame()
				|| target instanceof OwnableEntity ownable && ownable.getOwner() != null) {
			return false;
		}
		return target instanceof Enemy || MonsterScaler.kindOf(target) != MonsterScaler.Kind.NORMAL
				|| target.getPersistentData().getBoolean(MonsterScaler.TAG_SCALED);
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

	private static WeaponAffixSnapshot snapshot(ServerPlayer player) {
		return snapshot(player.getMainHandItem());
	}

	private static WeaponAffixSnapshot snapshot(ItemStack stack) {
		if (!Config.STAR_PRISM_ENABLED.get()) {
			return new WeaponAffixSnapshot(Math.max(1, EximusEquipmentData.upgradeLevel(
					stack)), List.of());
		}
		return WeaponAffixSnapshot.read(stack, EquipmentCategory.RANGED);
	}

	private static WeaponAffixSnapshot categorySnapshot(ItemStack stack,
			EquipmentCategory category) {
		if (!Config.STAR_PRISM_ENABLED.get()) {
			return new WeaponAffixSnapshot(Math.max(1, EximusEquipmentData.upgradeLevel(stack)),
					List.of());
		}
		return WeaponAffixSnapshot.read(stack, category);
	}

	private static WeaponAffixSnapshot snapshot(PrismProjectileData data) {
		List<String> affixes = new ArrayList<>(3);
		if (data.armorAffix() != null) {
			affixes.add(data.armorAffix());
		}
		if (data.meleeAffix() != null) {
			affixes.add(data.meleeAffix());
		}
		if (data.rangedAffix() != null) {
			for (String affix : data.rangedAffix().split(";")) {
				if (!affix.isBlank()) {
					affixes.add(affix);
				}
			}
		}
		return new WeaponAffixSnapshot(data.fixedLevel(), affixes);
	}

	private static String joined(WeaponAffixSnapshot snapshot) {
		return snapshot.affixes().isEmpty() ? null : String.join(";", snapshot.affixes());
	}

	private static boolean hasAffix(PrismProjectileData data, String id) {
		return snapshot(data).has(id);
	}

	private static boolean eligible(ItemStack stack) {
		return Config.SPEC.isLoaded() && Config.EXIMUS_EQUIPMENT_ENABLED.get()
				&& EximusEquipmentData.upgradeLevel(stack) >= 1
				&& EquipmentTypeResolver.category(stack) == EquipmentCategory.RANGED;
	}

	private static int requiredDrawTicks(ItemStack bow) {
		if (!ModList.get().isLoaded("summy_enchantment")) {
			return 20;
		}
		try {
			Class<?> type = Class.forName("com.summy.enchantment.combat.EnchantmentEffects");
			Method method = type.getMethod("requiredDrawTicks", ItemStack.class);
			Object value = method.invoke(null, bow);
			return Math.max(1, (int) value);
		} catch (Throwable ignored) {
			return 20;
		}
	}

	private static long now(ServerPlayer player) {
		return player.server.overworld().getGameTime();
	}

	private static void queueMagic(LivingEntity target, ServerPlayer player, double amount) {
		if (amount > 0.0D) {
			MAGIC.add(new DelayedMagic(target, player, amount));
		}
	}

	private record PendingShot(WeaponAffixSnapshot snapshot, String armorAffix,
			String meleeAffix, RangedShotState state, long tick) {
	}

	private record RangedDamageRecord(LivingEntity target, net.minecraft.world.damagesource.DamageSource source,
			ServerPlayer player, double lifestealPercent) {
	}

	private record DelayedMagic(LivingEntity target, ServerPlayer player, double amount) {
	}

	private record DelayedHeal(ServerPlayer player, double amount) {
	}
}
