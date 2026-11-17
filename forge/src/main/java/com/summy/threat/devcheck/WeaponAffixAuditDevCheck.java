package com.summy.threat.devcheck;

import com.summy.threat.Config;
import com.summy.threat.equipment.EquipmentCategory;
import com.summy.threat.equipment.EximusEquipmentData;
import com.summy.threat.equipment.MeleeWeaponRuntime;
import com.summy.threat.equipment.PrismProjectileData;
import com.summy.threat.equipment.RangedWeaponRuntime;
import com.summy.threat.equipment.StarPrismData;
import com.summy.threat.equipment.StarPrismConfig;
import com.summy.threat.equipment.WeaponAffixSnapshot;
import com.summy.threat.equipment.WeaponPlayerAttributes;
import com.summy.threat.equipment.WeaponRuntimeService;
import com.summy.threat.equipment.WeaponRuntimeState;
import com.summy.threat.item.ModItems;
import com.summy.threat.leader.LegendaryResistance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.ArrowLooseEvent;
import net.minecraftforge.event.entity.player.CriticalHitEvent;

import java.lang.reflect.Method;
import java.util.UUID;

/** DevCheck 专用的近战/远程星渊词条逐项审计；不修改生产逻辑。 */
final class WeaponAffixAuditDevCheck {
	private static final float BASE_HIT = 100.0F;

	private WeaponAffixAuditDevCheck() {
	}

	static void run(ServerPlayer player) {
		StarPrismAffixCoverage.reset();
		try {
			checkMeleeAttributes(player);
			checkMeleeEvents(player);
			checkRangedEvents(player);
		} finally {
			resetRuntime(player);
			WeaponPlayerAttributes.clear(player);
		}
		for (String id : StarPrismAffixCoverage.missing(EquipmentCategory.MELEE)) {
			check("B15 词条执行覆盖：" + id, false, "UNCOVERED");
		}
		for (String id : StarPrismAffixCoverage.missing(EquipmentCategory.RANGED)) {
			check("B15 词条执行覆盖：" + id, false, "UNCOVERED");
		}
	}

	private static void checkMeleeAttributes(ServerPlayer player) {
		ItemStack weapon = weapon(Items.NETHERITE_SWORD, "prism_edge", "starlit_bowstring");
		player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, weapon);
		WeaponRuntimeService.clear(player);
		WeaponPlayerAttributes.refresh(player, new WeaponRuntimeState());
		check("B15 星棱锋缘攻击力属性",
				hasModifier(player, Attributes.ATTACK_DAMAGE,
						"summy_threat:weapon/prism_edge"), attributeDetail(
						player, Attributes.ATTACK_DAMAGE));
		StarPrismAffixCoverage.cover("prism_edge");

		weapon = weapon(Items.NETHERITE_SWORD, "astral_haste", "starlit_bowstring");
		player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, weapon);
		WeaponRuntimeService.clear(player);
		WeaponPlayerAttributes.refresh(player, new WeaponRuntimeState());
		check("B15 星辉疾刃攻击速度属性",
				hasModifier(player, Attributes.ATTACK_SPEED,
						"summy_threat:weapon/astral_haste"), attributeDetail(
						player, Attributes.ATTACK_SPEED));
		StarPrismAffixCoverage.cover("astral_haste");

		weapon = weapon(Items.NETHERITE_SWORD, "concussive_prism", "starlit_bowstring");
		player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, weapon);
		WeaponRuntimeService.clear(player);
		WeaponPlayerAttributes.refresh(player, new WeaponRuntimeState());
		check("B15 星震重刃攻击击退属性",
				hasModifier(player, Attributes.ATTACK_KNOCKBACK,
						"summy_threat:weapon/concussive"), attributeDetail(
						player, Attributes.ATTACK_KNOCKBACK));
		StarPrismAffixCoverage.cover("concussive_prism");

		weapon = weapon(Items.NETHERITE_SWORD, "weakpoint_prism", "starlit_bowstring");
		player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, weapon);
		WeaponRuntimeService.clear(player);
		WeaponPlayerAttributes.refresh(player, new WeaponRuntimeState());
		boolean external = WeaponPlayerAttributes.externalCritAttributes();
		if (external) {
			Attribute critChance = BuiltInRegistries.ATTRIBUTE.get(
					new ResourceLocation("attributeslib", "crit_chance"));
			check("B15 星芒弱点 ApothicAttributes 暴击属性桥",
					critChance != null && hasModifier(player, critChance,
							"summy_threat:weapon/weakpoint_crit_chance"),
					critChance == null ? "attribute=null" : attributeDetail(player, critChance));
		} else {
			CriticalHitEvent event = new CriticalHitEvent(player,
					target(player.serverLevel(), player), 1.5F, true);
			MeleeWeaponRuntime.onCriticalHit(event);
			check("B15 星芒弱点无属性桥时原版暴击回退",
					Math.abs(event.getDamageModifier() - 1.75F) < 0.001F,
					"modifier=" + event.getDamageModifier());
		}
		StarPrismAffixCoverage.cover("weakpoint_prism");
	}

	private static void checkMeleeEvents(ServerPlayer player) {
		Zombie target = target(player.serverLevel(), player);
		if (target == null) {
			check("B15 近战词条测试目标", false, "zombie=null");
			return;
		}

		ItemStack weapon = weapon(Items.NETHERITE_SWORD, "breach_constellation",
				"starlit_bowstring");
		player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, weapon);
		WeaponRuntimeService.clear(player);
		LivingHurtEvent breach = meleeHit(player, target, BASE_HIT);
		double breachPoints = com.summy.threat.equipment.ArmorReductionContext.consume(
				target, breach.getSource());
		check("B15 破界星轨护甲减伤百分点", Math.abs(breachPoints - 5.0D) < 1.0E-6D,
				"points=" + breachPoints + " amount=" + breach.getAmount());
		StarPrismAffixCoverage.cover("breach_constellation");

		target.getPersistentData().putBoolean(LegendaryResistance.TAG_RESISTANCE, true);
		weapon = weapon(Items.NETHERITE_SWORD, "shield_render", "starlit_bowstring");
		player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, weapon);
		WeaponRuntimeService.clear(player);
		meleeHit(player, target, BASE_HIT);
		check("B15 裂盾棱刃高威胁增伤",
				meleeAmountAfter(player, target, "shield_render") > BASE_HIT * 1.4F,
				"target has Legendary Resistance");
		StarPrismAffixCoverage.cover("shield_render");
		target.getPersistentData().remove(LegendaryResistance.TAG_RESISTANCE);

		player.setHealth(player.getMaxHealth() - 20.0F);
		weapon = weapon(Items.NETHERITE_SWORD, "void_siphon", "starlit_bowstring");
		player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, weapon);
		WeaponRuntimeService.clear(player);
		float beforeHeal = player.getHealth();
		LivingHurtEvent siphon = meleeHit(player, target, BASE_HIT);
		MeleeWeaponRuntime.onFinalDamage(new LivingDamageEvent(target,
				siphon.getSource(), 50.0F));
		MeleeWeaponRuntime.onServerTick(new TickEvent.ServerTickEvent(
				TickEvent.Phase.END, () -> false, player.server));
		check("B15 噬渊回响命中后回血", player.getHealth() > beforeHeal,
				"before=%.2f after=%.2f".formatted(beforeHeal, player.getHealth()));
		StarPrismAffixCoverage.cover("void_siphon");
		Zombie siphonTarget = new Zombie(player.serverLevel());
		siphonTarget.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0D);
		siphonTarget.setHealth(1000.0F);
		player.setHealth(player.getMaxHealth() - 100.0F);
		float siphonPreciseBefore = player.getHealth();
		LivingHurtEvent preciseSiphon = meleeHit(player, siphonTarget, BASE_HIT);
		float siphonNominal = preciseSiphon.getAmount();
		MeleeWeaponRuntime.onFinalDamage(new LivingDamageEvent(siphonTarget,
				preciseSiphon.getSource(), 20.0F));
		MeleeWeaponRuntime.onServerTick(new TickEvent.ServerTickEvent(
				TickEvent.Phase.END, () -> false, player.server));
		double siphonExpected = Math.min(siphonNominal, siphonTarget.getHealth())
				* StarPrismConfig.doubleValue("melee.void_siphon.lifesteal_percent")
				/ 100.0D;
		check("B15 噬渊回响按名义伤害与当前生命较低值吸血",
				close(player.getHealth() - siphonPreciseBefore, siphonExpected),
				"heal=%.2f expected=%.2f nominal=%.2f health=%.2f".formatted(
						player.getHealth() - siphonPreciseBefore, siphonExpected,
						siphonNominal, siphonTarget.getHealth()));
		siphonTarget.discard();
		player.setHealth(player.getMaxHealth());

		weapon = weapon(Items.NETHERITE_SWORD, "arcane_edge", "starlit_bowstring");
		player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, weapon);
		WeaponRuntimeService.clear(player);
		float healthBeforeMagic = target.getHealth();
		meleeHit(player, target, BASE_HIT);
		MeleeWeaponRuntime.onServerTick(new TickEvent.ServerTickEvent(
				TickEvent.Phase.END, () -> false, player.server));
		check("B15 星蚀附伤魔法追加", target.getHealth() < healthBeforeMagic,
				"before=%.2f after=%.2f".formatted(
						healthBeforeMagic, target.getHealth()));
		StarPrismAffixCoverage.cover("arcane_edge");

		target.getPersistentData().putBoolean(LegendaryResistance.TAG_RESISTANCE, true);
		weapon = weapon(Items.NETHERITE_SWORD, "starfire_cycle", "starlit_bowstring");
		player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, weapon);
		WeaponRuntimeService.clear(player);
		resetTargetHealth(target);
		float starfireBefore = target.getHealth();
		for (int i = 0; i < 4; i++) {
			meleeHit(player, target, BASE_HIT);
		}
		MeleeWeaponRuntime.onServerTick(new TickEvent.ServerTickEvent(
				TickEvent.Phase.END, () -> false, player.server));
		check("B15 星火循环第四次追加魔法",
				target.getHealth() < starfireBefore,
				"before=%.2f after=%.2f".formatted(
						starfireBefore, target.getHealth()));
		StarPrismAffixCoverage.cover("starfire_cycle");
		target.getPersistentData().remove(LegendaryResistance.TAG_RESISTANCE);

		weapon = weapon(Items.NETHERITE_SWORD, "abyssal_pursuit", "starlit_bowstring");
		player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, weapon);
		WeaponRuntimeService.clear(player);
		WeaponAffixSnapshot pursuitSnapshot = WeaponAffixSnapshot.read(
				player.getMainHandItem(), EquipmentCategory.MELEE);
		String pursuitWeaponKey = WeaponRuntimeService.weaponKey(player.getMainHandItem());
		float firstPursuit = meleeHit(player, target, BASE_HIT).getAmount();
		int firstPursuitStacks = WeaponRuntimeService.pursuitStacks(player);
		float secondPursuit = meleeHit(player, target, BASE_HIT).getAmount();
		int secondPursuitStacks = WeaponRuntimeService.pursuitStacks(player);
		check("B15 渊痕追猎首次叠层后续增伤", secondPursuit > firstPursuit,
				"affix=%s stableKey=%s first=%.2f stacks1=%d second=%.2f stacks2=%d"
						.formatted(pursuitSnapshot.has("abyssal_pursuit"),
								pursuitWeaponKey.equals(WeaponRuntimeService.weaponKey(
										player.getMainHandItem())),
								firstPursuit, firstPursuitStacks, secondPursuit,
								secondPursuitStacks));
		StarPrismAffixCoverage.cover("abyssal_pursuit");

		weapon = weapon(Items.NETHERITE_SWORD, "weakpoint_prism", "starlit_bowstring");
		player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, weapon);
		CriticalHitEvent crit = new CriticalHitEvent(player, target, 1.5F, true);
		MeleeWeaponRuntime.onCriticalHit(crit);
		check("B15 星芒弱点原版暴击分支只执行一次",
				crit.getDamageModifier() >= 1.5F, "modifier=" + crit.getDamageModifier());
		StarPrismAffixCoverage.cover("weakpoint_prism");

		Zombie markTarget = new Zombie(player.serverLevel());
		Zombie thirdParty = new Zombie(player.serverLevel());
		WeaponRuntimeService.clear(player);
		WeaponRuntimeService.markHit(player, markTarget);
		LivingHurtEvent allSourceMark = new LivingHurtEvent(markTarget,
				thirdParty.damageSources().mobAttack(thirdParty), BASE_HIT);
		MinecraftForge.EVENT_BUS.post(allSourceMark);
		check("B15 星痕刻印使目标受到所有来源伤害 +10%",
				close(allSourceMark.getAmount(), BASE_HIT * 1.10F),
				"amount=" + allSourceMark.getAmount());
		markTarget.discard();
		thirdParty.discard();
		WeaponRuntimeService.clear(player);

		// 直接测试独立状态，避免 KubeJS 配置重载按会话生命周期清空全局玩家状态，
		// 造成追猎容量断言被测试环境时序污染。
		WeaponRuntimeState pursuitState = new WeaponRuntimeState();
		UUID[] pursuitTargets = new UUID[8];
		for (int i = 0; i < pursuitTargets.length; i++) {
			pursuitTargets[i] = UUID.randomUUID();
			pursuitState.pursuitHit(pursuitTargets[i], "devcheck|pursuit", 20L, 5, 60);
		}
		int retainedPursuitTargets = 0;
		for (UUID pursuitTarget : pursuitTargets) {
			if (pursuitState.pursuitHit(pursuitTarget, "devcheck|pursuit",
					21L, 5, 60) == 2) {
				retainedPursuitTargets++;
			}
		}
		check("B15 渊痕追猎独立保留 8 名目标",
				retainedPursuitTargets == pursuitTargets.length,
				"retained=" + retainedPursuitTargets);

		// 配置写入会触发异步文件重载并清空玩家会话状态，放在所有有状态断言之后。
		weapon = weapon(Items.NETHERITE_SWORD, "terminal_star", "starlit_bowstring");
		player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, weapon);
		WeaponRuntimeService.clear(player);
		String terminalChanceKey = "star_prism.values.melee.terminal_star.chance_percent";
		net.minecraftforge.common.ForgeConfigSpec.DoubleValue terminalChance =
				doubleValue(terminalChanceKey);
		double terminalChanceOriginal = terminalChance.get();
		terminalChance.set(100.0D);
		try {
			check("B15 终末星落独立倍率",
					meleeAmountAfter(player, target, "terminal_star") > BASE_HIT,
					"chance=100");
		} finally {
			terminalChance.set(terminalChanceOriginal);
		}
		StarPrismAffixCoverage.cover("terminal_star");

		target.discard();
	}

	private static void checkRangedEvents(ServerPlayer player) {
		Zombie target = target(player.serverLevel(), player);
		if (target == null) {
			check("B15 远程词条测试目标", false, "zombie=null");
			return;
		}
		RangedCase c;
		c = rangedHit(player, target, "starlit_bowstring", 0, false, false, BASE_HIT);
		check("B15 星弦定力投射物伤害", Math.abs(c.event().getAmount() - 130.0F) < 0.01F,
				"amount=" + c.event().getAmount());
		StarPrismAffixCoverage.cover("starlit_bowstring");

		c = rangedHit(player, target, "sidereal_aim", 0, true, false, BASE_HIT);
		check("B15 星芒准星首个命中倍率", Math.abs(c.event().getAmount() - 180.0F) < 0.01F,
				"amount=" + c.event().getAmount());
		StarPrismAffixCoverage.cover("sidereal_aim");

		check("B15 彗羽流光伏速与穿透配置",
				velocityMultiplier(false) == 1.10D
						&& Math.abs(velocityMultiplier(true) - 1.265D) < 1.0E-9D,
				"bare=%.4f comet=%.4f".formatted(
						velocityMultiplier(false), velocityMultiplier(true)));
		StarPrismAffixCoverage.cover("comet_fletching");

		player.setHealth(player.getMaxHealth() - 20.0F);
		float dewBefore = player.getHealth();
		c = rangedHit(player, target, "starlight_dew", 0, false, false, BASE_HIT);
		RangedWeaponRuntime.onServerTick(new TickEvent.ServerTickEvent(
				TickEvent.Phase.END, () -> false, player.server));
		check("B15 星露回响命中后治疗", player.getHealth() > dewBefore,
				"before=%.2f after=%.2f".formatted(dewBefore, player.getHealth()));
		StarPrismAffixCoverage.cover("starlight_dew");

		c = rangedHit(player, target, "breach_star_arrow", 0, false, false, BASE_HIT);
		double breachPoints = com.summy.threat.equipment.ArmorReductionContext.consume(
				target, c.event().getSource());
		check("B15 破界星矢护甲减伤百分点", Math.abs(breachPoints - 6.0D) < 1.0E-6D,
				"points=" + breachPoints);
		StarPrismAffixCoverage.cover("breach_star_arrow");

		target.getPersistentData().putBoolean(LegendaryResistance.TAG_RESISTANCE, true);
		c = rangedHit(player, target, "shield_render_arrow", 0, false, false, BASE_HIT);
		check("B15 裂盾星矢高威胁增伤", c.event().getAmount() > BASE_HIT * 1.5F,
				"amount=" + c.event().getAmount());
		StarPrismAffixCoverage.cover("shield_render_arrow");
		target.getPersistentData().remove(LegendaryResistance.TAG_RESISTANCE);

		resetTargetHealth(target);
		float arcaneBefore = target.getHealth();
		c = rangedHit(player, target, "arcane_arrow", 0, false, false, BASE_HIT);
		RangedWeaponRuntime.onServerTick(new TickEvent.ServerTickEvent(
				TickEvent.Phase.END, () -> false, player.server));
		check("B15 星蚀箭芒魔法追加", target.getHealth() < arcaneBefore,
				"before=%.2f after=%.2f".formatted(arcaneBefore, target.getHealth()));
		StarPrismAffixCoverage.cover("arcane_arrow");

		resetTargetHealth(target);
		player.setHealth(player.getMaxHealth() - 20.0F);
		float quiverBefore = player.getHealth();
		c = rangedHit(player, target, "void_quiver", 0, false, false, BASE_HIT);
		RangedWeaponRuntime.onFinalDamage(new LivingDamageEvent(target,
				c.event().getSource(), 50.0F));
		RangedWeaponRuntime.onServerTick(new TickEvent.ServerTickEvent(
				TickEvent.Phase.END, () -> false, player.server));
		check("B15 噬渊箭匣命中后回血", player.getHealth() > quiverBefore,
				"before=%.2f after=%.2f".formatted(quiverBefore, player.getHealth()));
		StarPrismAffixCoverage.cover("void_quiver");
		Zombie quiverTarget = new Zombie(player.serverLevel());
		quiverTarget.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0D);
		quiverTarget.setHealth(1000.0F);
		player.setHealth(player.getMaxHealth() - 100.0F);
		float quiverPreciseBefore = player.getHealth();
		c = rangedHit(player, quiverTarget, "void_quiver", 0, false, false, BASE_HIT);
		float quiverNominal = c.event().getAmount();
		RangedWeaponRuntime.onFinalDamage(new LivingDamageEvent(quiverTarget,
				c.event().getSource(), 20.0F));
		RangedWeaponRuntime.onServerTick(new TickEvent.ServerTickEvent(
				TickEvent.Phase.END, () -> false, player.server));
		double quiverExpected = Math.min(quiverNominal, quiverTarget.getHealth())
				* StarPrismConfig.doubleValue("ranged.void_quiver.lifesteal_percent")
				/ 100.0D;
		check("B15 噬渊箭匣按名义伤害与当前生命较低值吸血",
				close(player.getHealth() - quiverPreciseBefore, quiverExpected),
				"heal=%.2f expected=%.2f nominal=%.2f health=%.2f".formatted(
						player.getHealth() - quiverPreciseBefore, quiverExpected,
						quiverNominal, quiverTarget.getHealth()));
		quiverTarget.discard();
		player.setHealth(player.getMaxHealth());

		target.getPersistentData().putBoolean(LegendaryResistance.TAG_RESISTANCE, true);
		resetTargetHealth(target);
		float fallBefore = target.getHealth();
		for (int i = 0; i < 3; i++) {
			rangedHit(player, target, "starfall_cycle_arrow", 0, false, false, BASE_HIT);
		}
		RangedWeaponRuntime.onServerTick(new TickEvent.ServerTickEvent(
				TickEvent.Phase.END, () -> false, player.server));
		check("B15 星落轮转第三次追加魔法", target.getHealth() < fallBefore,
				"before=%.2f after=%.2f".formatted(fallBefore, target.getHealth()));
		StarPrismAffixCoverage.cover("starfall_cycle_arrow");

		c = rangedHit(player, target, "terminal_star_shot", 0, false, true, BASE_HIT);
		check("B15 终末星陨独立倍率", c.event().getAmount() > BASE_HIT * 10.0F,
				"amount=" + c.event().getAmount());
		StarPrismAffixCoverage.cover("terminal_star_shot");

		RangedCase first = rangedHit(player, target, "abyssal_hunt_arrow", 0,
				false, false, BASE_HIT);
		RangedCase second = rangedHit(player, target, "abyssal_hunt_arrow", 0,
				false, false, BASE_HIT);
		check("B15 远程渊痕追猎首次叠层后续增伤",
				second.event().getAmount() > first.event().getAmount(),
				"first=%.2f second=%.2f".formatted(
						first.event().getAmount(), second.event().getAmount()));
		StarPrismAffixCoverage.cover("abyssal_hunt_arrow");
		checkRangedProbabilityRolls(player);
		checkDisabledPursuitHasNoBonus(player, target);
		target.getPersistentData().remove(LegendaryResistance.TAG_RESISTANCE);
		target.discard();
	}

	private static void checkDisabledPursuitHasNoBonus(ServerPlayer player,
			LivingEntity target) {
		boolean original = Config.STAR_PRISM_ENABLED.get();
		try {
			Config.STAR_PRISM_ENABLED.set(false);
			double multiplier = WeaponRuntimeService.pursuitMultiplier(
					player, target, "devcheck|disabled-pursuit");
			check("B15 关闭星渊后追猎不提供倍率", close(multiplier, 0.0D),
					"multiplier=" + multiplier);
		} finally {
			Config.STAR_PRISM_ENABLED.set(original);
		}
	}

	private static void checkRangedProbabilityRolls(ServerPlayer player) {
		checkRangedProbabilityRoll(player, "sidereal_aim",
				"ranged.sidereal_aim.chance_percent",
				data -> data.siderealAim(), "星芒准星");
		checkRangedProbabilityRoll(player, "terminal_star_shot",
				"ranged.terminal_star_shot.chance_percent",
				data -> data.terminalStarShot(), "终末星陨");
	}

	private static void checkRangedProbabilityRoll(ServerPlayer player, String affix,
			String configKey, java.util.function.Predicate<PrismProjectileData> flagged,
			String label) {
		double chance = StarPrismConfig.doubleValue(configKey) / 100.0D;
		long enabledSeed = randomSeed(chance, true);
		long disabledSeed = randomSeed(chance, false);
		PrismProjectileData enabled = actualShot(player, affix, enabledSeed);
		PrismProjectileData disabled = actualShot(player, affix, disabledSeed);
		check("B15 %s真实射击概率抽取".formatted(label),
				enabled != null && flagged.test(enabled)
						&& disabled != null && !flagged.test(disabled),
				"chance=%.4f enabledSeed=%d disabledSeed=%d enabled=%s disabled=%s"
						.formatted(chance, enabledSeed, disabledSeed, enabled, disabled));
	}

	private static PrismProjectileData actualShot(ServerPlayer player, String affix,
			long randomSeed) {
		ItemStack bow = weapon(Items.BOW, "prism_edge", affix);
		player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, bow);
		RangedWeaponRuntime.clearAll();
		player.getRandom().setSeed(randomSeed);
		RangedWeaponRuntime.onArrowLoose(new ArrowLooseEvent(
				player, bow, player.serverLevel(), 20, true));
		Arrow arrow = new Arrow(player.serverLevel(), player);
		RangedWeaponRuntime.onEntityJoin(new EntityJoinLevelEvent(
				arrow, player.serverLevel()));
		PrismProjectileData data = PrismProjectileData.read(
				arrow.getPersistentData()).orElse(null);
		arrow.discard();
		return data;
	}

	private static long randomSeed(double chance, boolean enabled) {
		double threshold = Math.max(0.0D, Math.min(1.0D, chance));
		for (long seed = 0L; seed < 10_000L; seed++) {
			double rolled = net.minecraft.util.RandomSource.create(seed).nextDouble();
			if (enabled ? rolled < threshold : rolled >= threshold) {
				return seed;
			}
		}
		throw new IllegalStateException("找不到可命中概率边界的随机种子");
	}

	private static RangedCase rangedHit(ServerPlayer player, LivingEntity target,
			String rangedAffix, int fixedLevel, boolean sidereal, boolean terminal,
			float amount) {
		ServerLevel level = player.serverLevel();
		AbstractArrow arrow = new Arrow(level, player);
		PrismProjectileData data = new PrismProjectileData(
				PrismProjectileData.SCHEMA, UUID.randomUUID(), fixedLevel,
				null, null, rangedAffix, false, sidereal, terminal, true, true);
		PrismProjectileData.write(arrow.getPersistentData(), data);
		LivingHurtEvent event = new LivingHurtEvent(target,
				player.damageSources().arrow(arrow, player), amount);
		RangedWeaponRuntime.onLivingHurt(event);
		return new RangedCase(event, arrow);
	}

	private static LivingHurtEvent meleeHit(ServerPlayer player, LivingEntity target,
			float amount) {
		MeleeWeaponRuntime.onCriticalHit(new CriticalHitEvent(player, target, 1.0F, false));
		LivingHurtEvent event = new LivingHurtEvent(target,
				player.damageSources().playerAttack(player), amount);
		MeleeWeaponRuntime.onLivingHurt(event);
		return event;
	}

	private static float meleeAmountAfter(ServerPlayer player, LivingEntity target,
			String affix) {
		player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,
				weapon(Items.NETHERITE_SWORD, affix, "starlit_bowstring"));
		WeaponRuntimeService.clear(player);
		return meleeHit(player, target, BASE_HIT).getAmount();
	}

	private static void resetTargetHealth(LivingEntity target) {
		target.clearFire();
		target.setAirSupply(target.getMaxAirSupply());
		target.setHealth(target.getMaxHealth());
	}

	private static ItemStack weapon(Item item, String melee, String ranged) {
		ItemStack stack = EximusEquipmentData.withUpgradeLevel(new ItemStack(item), 2);
		ItemStack prism = new ItemStack(ModItems.ABYSSAL_STAR_PRISM.get());
		StarPrismData.write(prism, new StarPrismData(StarPrismData.SCHEMA, true, null,
				"astral_plating", melee, ranged));
		return EximusEquipmentData.withPrism(stack, 0, prism);
	}

	private static Zombie target(ServerLevel level, ServerPlayer player) {
		Zombie zombie = EntityType.ZOMBIE.create(level);
		if (zombie == null) {
			return null;
		}
		zombie.moveTo(player.getX() + 2.0D, player.getY(), player.getZ());
		zombie.setHealth(1000.0F);
		zombie.getPersistentData().putBoolean(
				com.summy.threat.scaling.MonsterScaler.TAG_SCALED, true);
		level.addFreshEntity(zombie);
		return zombie;
	}

	private static boolean hasModifier(ServerPlayer player, Attribute attribute,
			String name) {
		AttributeInstance instance = player.getAttribute(attribute);
		return instance != null && instance.getModifiers().stream()
				.anyMatch(modifier -> name.equals(modifier.getName()));
	}

	private static String attributeDetail(ServerPlayer player, Attribute attribute) {
		AttributeInstance instance = player.getAttribute(attribute);
		return instance == null ? "attribute=null" : "value=" + instance.getValue()
				+ " modifiers=" + instance.getModifiers();
	}

	private static double velocityMultiplier(boolean comet) {
		try {
			Method method = RangedWeaponRuntime.class.getDeclaredMethod(
					"initialVelocityMultiplier", int.class, boolean.class, double.class);
			method.setAccessible(true);
			return (double) method.invoke(null, 1, comet, 15.0D);
		} catch (ReflectiveOperationException exception) {
			return Double.NaN;
		}
	}

	private static boolean close(double actual, double expected) {
		return Math.abs(actual - expected) <= 1.0E-4D;
	}

	private static net.minecraftforge.common.ForgeConfigSpec.DoubleValue doubleValue(String path) {
		for (net.minecraftforge.common.ForgeConfigSpec.DoubleValue value
				: Config.STAR_PRISM_DOUBLE_VALUES) {
			if (String.join(".", value.getPath()).equals(path)) {
				return value;
			}
		}
		throw new IllegalArgumentException("缺少星渊浮点配置：" + path);
	}

	private static void resetRuntime(ServerPlayer player) {
		MeleeWeaponRuntime.clearAll();
		RangedWeaponRuntime.clearAll();
		WeaponRuntimeService.clear(player);
		WeaponPlayerAttributes.clear(player);
		player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, ItemStack.EMPTY);
	}

	private static void check(String label, boolean ok, String detail) {
		ThreatDevCheck.checkDetailed(label, ok, detail);
	}

	private record RangedCase(LivingHurtEvent event, AbstractArrow arrow) {
	}
}
