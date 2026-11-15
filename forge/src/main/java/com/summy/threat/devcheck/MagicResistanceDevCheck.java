package com.summy.threat.devcheck;

import com.summy.threat.Config;
import com.summy.threat.SummyThreat;
import com.summy.threat.api.ThreatConfigApi;
import com.summy.threat.combat.MagicResistance;
import com.summy.threat.eximus.EximusShield;
import com.summy.threat.scaling.MonsterScaler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A37 TL 魔法抗性的数据标签、配置表与实际受击顺序自检。 */
final class MagicResistanceDevCheck {
	private static final double EPSILON = 1.0E-4D;
	private static final float DAMAGE_EPSILON = 0.02F;

	private MagicResistanceDevCheck() {
	}

	static List<String> run(ServerPlayer player) {
		List<String> failures = new ArrayList<>();
		int[] count = {0};
		ServerLevel level = player.serverLevel();
		DamageSources sources = level.damageSources();
		List<LivingEntity> targets = new ArrayList<>();
		Map<String, Object> original = new LinkedHashMap<>();
		List<String> keys = List.of(
				"environment_tier.mode",
				"environment_tier.hp_scale_free",
				"magic_resistance.enable",
				"magic_resistance.scale_with_environment_hp",
				"magic_resistance.hard_cap_percent",
				"magic_resistance.normal_tl9",
				"magic_resistance.normal_tl20",
				"magic_resistance.eximus_tl20",
				"magic_resistance.leader_boss_tl9",
				"magic_resistance.leader_boss_tl20");
		keys.forEach(key -> original.put(key, ThreatConfigApi.get(key)));

		try {
			checkDefaults(failures, count);
			ThreatConfigApi.setMany(Map.of(
					"environment_tier.mode", "full",
					"magic_resistance.enable", true,
					"magic_resistance.scale_with_environment_hp", true,
					"magic_resistance.hard_cap_percent", 90.0D,
					"magic_resistance.normal_tl9", 11.0D,
					"magic_resistance.normal_tl20", 55.0D,
					"magic_resistance.eximus_tl20", 60.0D,
					"magic_resistance.leader_boss_tl9", 49.0D,
					"magic_resistance.leader_boss_tl20", 60.0D));

			Zombie normal = target(level, MonsterScaler.Kind.NORMAL, 20);
			Zombie eximus = target(level, MonsterScaler.Kind.EXIMUS, 20);
			Zombie leader = target(level, MonsterScaler.Kind.LEADER, 20);
			Zombie boss = target(level, MonsterScaler.Kind.BOSS, 20);
			Zombie lowLeader = target(level, MonsterScaler.Kind.LEADER, 9);
			targets.add(normal);
			targets.add(eximus);
			targets.add(leader);
			targets.add(boss);
			targets.add(lowLeader);

			checkClassification(failures, count, normal, player, sources);
			checkAmount(failures, count, "普通 TL20 法抗 55%",
					normal, sources.magic(), 100.0F, 45.0F);
			checkAmount(failures, count, "卓越者 TL20 法抗 60%",
					eximus, sources.magic(), 100.0F, 40.0F);
			checkAmount(failures, count, "区域首领 TL20 法抗 60%",
					leader, sources.magic(), 100.0F, 40.0F);
			checkAmount(failures, count, "Boss TL20 法抗 60%",
					boss, sources.magic(), 100.0F, 40.0F);
			checkAmount(failures, count, "异常 TL9 区域首领回退普通列",
					lowLeader, sources.magic(), 100.0F, 89.0F);

			checkAmount(failures, count, "玩家目标不获得 ST TL 法抗",
					player, sources.magic(), 100.0F, 100.0F);
			Zombie unscaled = EntityType.ZOMBIE.create(level);
			if (unscaled == null) {
				failures.add("A37 无法创建未缩放僵尸");
			} else {
				targets.add(unscaled);
				checkAmount(failures, count, "未缩放目标不获得 ST TL 法抗",
						unscaled, sources.magic(), 100.0F, 100.0F);
			}

			LivingHurtEvent canceled = event(normal, sources.magic(), 100.0F);
			canceled.setCanceled(true);
			MagicResistance.onLivingHurt(canceled);
			check(failures, count, "取消事件不被法抗修改",
					Math.abs(canceled.getAmount() - 100.0F) <= DAMAGE_EPSILON,
					"amount=" + canceled.getAmount());
			checkAmount(failures, count, "零伤害不结算法抗",
					normal, sources.magic(), 0.0F, 0.0F);

			ThreatConfigApi.setMany(Map.of(
					"environment_tier.mode", "free",
					"environment_tier.hp_scale_free", 0.50D,
					"magic_resistance.scale_with_environment_hp", true,
					"magic_resistance.hard_cap_percent", 90.0D,
					"magic_resistance.leader_boss_tl20", 60.0D));
			checkAmount(failures, count, "r_hp=0.5 时先缩放至 30%",
					boss, sources.magic(), 100.0F, 70.0F);

			ThreatConfigApi.set("magic_resistance.hard_cap_percent", 20.0D);
			checkAmount(failures, count, "硬上限 20% 在 r_hp 缩放后截断",
					boss, sources.magic(), 100.0F, 80.0F);

			ThreatConfigApi.set("magic_resistance.scale_with_environment_hp", false);
			ThreatConfigApi.set("magic_resistance.hard_cap_percent", 90.0D);
			checkAmount(failures, count, "关闭环境缩放后使用原表值",
					boss, sources.magic(), 100.0F, 40.0F);

			ThreatConfigApi.set("magic_resistance.enable", false);
			checkAmount(failures, count, "关闭总开关后伤害不变",
					normal, sources.magic(), 100.0F, 100.0F);
			boolean rangeRejected = false;
			try {
				ThreatConfigApi.set("magic_resistance.hard_cap_percent", 101.0D);
			} catch (IllegalArgumentException expected) {
				rangeRejected = true;
			}
			check(failures, count, "法抗键拒绝越界写入", rangeRejected, "rejected=" + rangeRejected);

			ThreatConfigApi.setMany(Map.of(
					"environment_tier.mode", "full",
					"magic_resistance.enable", true,
					"magic_resistance.scale_with_environment_hp", true,
					"magic_resistance.hard_cap_percent", 90.0D,
					"magic_resistance.normal_tl20", 55.0D));
			checkActualHurt(failures, count, level, sources, targets);
		} catch (Throwable throwable) {
			failures.add("A37 检查异常：" + throwable);
			throwable.printStackTrace();
		} finally {
			try {
				ThreatConfigApi.setMany(original);
			} catch (Throwable throwable) {
				failures.add("A37 配置恢复失败：" + throwable);
			}
			targets.forEach(LivingEntity::discard);
		}

		SummyThreat.LOGGER.info("[A37] TL 魔法抗性检查：{} 项，失败 {}",
				count[0], failures.size());
		return failures;
	}

	private static void checkDefaults(List<String> failures, int[] count) {
		List<String> group = ThreatConfigApi.keys("magic_resistance");
		check(failures, count, "法抗配置组包含 66 项",
				group.size() == 66, "keys=" + group.size());
		check(failures, count, "法抗默认开关与硬上限",
				Config.MAGIC_RESISTANCE_ENABLED.getDefault()
						&& Config.MAGIC_RESISTANCE_SCALE_WITH_ENVIRONMENT_HP.getDefault()
						&& close(Config.MAGIC_RESISTANCE_HARD_CAP_PERCENT.getDefault(), 90.0D),
				"hardCap=" + Config.MAGIC_RESISTANCE_HARD_CAP_PERCENT.getDefault());
		check(failures, count, "三列 TL20 默认值独立",
				close(Config.NORMAL_MAGIC_RESISTANCE_PER_TL[20].getDefault(), 55.0D)
						&& close(Config.EXIMUS_MAGIC_RESISTANCE_PER_TL[20].getDefault(), 60.0D)
						&& close(Config.LEADER_BOSS_MAGIC_RESISTANCE_PER_TL[20].getDefault(), 60.0D),
				"normal/eximus/leaderBoss="
						+ Config.NORMAL_MAGIC_RESISTANCE_PER_TL[20].getDefault() + "/"
						+ Config.EXIMUS_MAGIC_RESISTANCE_PER_TL[20].getDefault() + "/"
						+ Config.LEADER_BOSS_MAGIC_RESISTANCE_PER_TL[20].getDefault());
	}

	private static void checkClassification(List<String> failures, int[] count,
			Zombie target, ServerPlayer player, DamageSources sources) {
		check(failures, count, "magic 属于魔法伤害",
				MagicResistance.isMagicDamage(sources.magic()), "");
		check(failures, count, "indirect_magic 属于魔法伤害",
				MagicResistance.isMagicDamage(sources.indirectMagic(player, player)), "");
		check(failures, count, "wither 属于魔法伤害",
				MagicResistance.isMagicDamage(sources.wither()), "");
		check(failures, count, "dragon_breath 属于魔法伤害",
				MagicResistance.isMagicDamage(sources.dragonBreath()), "");
		check(failures, count, "sonic_boom 排除",
				!MagicResistance.isMagicDamage(sources.sonicBoom(target)), "");
		check(failures, count, "out_of_world/generic_kill 排除",
				!MagicResistance.isMagicDamage(sources.fellOutOfWorld())
						&& !MagicResistance.isMagicDamage(sources.genericKill()), "");
		check(failures, count, "环境伤害排除标签已加载",
				!MagicResistance.isMagicDamage(sources.onFire())
						&& !MagicResistance.isMagicDamage(sources.inWall())
						&& !MagicResistance.isMagicDamage(sources.cramming())
						&& !MagicResistance.isMagicDamage(sources.drown())
						&& !MagicResistance.isMagicDamage(sources.flyIntoWall())
						&& !MagicResistance.isMagicDamage(sources.fall())
						&& !MagicResistance.isMagicDamage(sources.freeze())
						&& !MagicResistance.isMagicDamage(sources.stalagmite())
						&& !MagicResistance.isMagicDamage(sources.starve())
						&& !MagicResistance.isMagicDamage(sources.outOfBorder())
						&& !MagicResistance.isMagicDamage(sources.generic()),
				"on_fire/in_wall/cramming/drown/fly_into_wall/fall/freeze/stalagmite/starve/outside_border/generic");
		check(failures, count, "普通物理与爆炸不进入法抗",
				!MagicResistance.isMagicDamage(sources.playerAttack(player))
						&& !MagicResistance.isMagicDamage(sources.explosion(player, player))
						&& !MagicResistance.isMagicDamage(sources.thorns(player)), "");
	}

	private static void checkActualHurt(List<String> failures, int[] count,
			ServerLevel level, DamageSources sources, List<LivingEntity> targets) {
		Zombie resisted = target(level, MonsterScaler.Kind.NORMAL, 20);
		resisted.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0D);
		resisted.setHealth(200.0F);
		resisted.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 1, false, false));
		resisted.setAbsorptionAmount(10.0F);
		resisted.invulnerableTime = 0;
		float before = resisted.getHealth();
		boolean landed = resisted.hurt(sources.magic(), 100.0F);
		float delta = before - resisted.getHealth();
		check(failures, count, "实际受击按法抗→抗性→吸收顺序结算",
				landed && Math.abs(delta - 17.0F) <= 0.05F,
				"landed=" + landed + " delta=" + delta + " absorption=" + resisted.getAbsorptionAmount());
		targets.add(resisted);

		Zombie protectedTarget = target(level, MonsterScaler.Kind.NORMAL, 20);
		protectedTarget.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0D);
		protectedTarget.setHealth(200.0F);
		ItemStack helmet = new ItemStack(Items.DIAMOND_HELMET);
		helmet.enchant(Enchantments.ALL_DAMAGE_PROTECTION, 4);
		protectedTarget.setItemSlot(EquipmentSlot.HEAD, helmet);
		protectedTarget.invulnerableTime = 0;
		before = protectedTarget.getHealth();
		landed = protectedTarget.hurt(sources.magic(), 100.0F);
		delta = before - protectedTarget.getHealth();
		check(failures, count, "保护附魔在法抗后继续生效",
				landed && Math.abs(delta - 37.8F) <= 0.05F,
				"landed=" + landed + " delta=" + delta);
		targets.add(protectedTarget);

		Zombie shielded = target(level, MonsterScaler.Kind.EXIMUS, 20);
		shielded.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0D);
		shielded.setHealth(200.0F);
		shielded.getPersistentData().putDouble(EximusShield.TAG_SHIELD, 100.0D);
		shielded.getPersistentData().putDouble(EximusShield.TAG_SHIELD_MAX, 100.0D);
		shielded.invulnerableTime = 0;
		before = shielded.getHealth();
		landed = shielded.hurt(sources.magic(), 100.0F);
		delta = before - shielded.getHealth();
		check(failures, count, "卓越护盾在法抗后吸收",
				landed && Math.abs(delta) <= 0.05F
						&& Math.abs(EximusShield.current(shielded) - 60.0D) <= 0.05D,
				"landed=" + landed + " delta=" + delta + " shield=" + EximusShield.current(shielded));
		targets.add(shielded);
	}

	private static Zombie target(ServerLevel level, MonsterScaler.Kind kind, int tl) {
		Zombie zombie = EntityType.ZOMBIE.create(level);
		if (zombie == null) {
			throw new IllegalStateException("无法创建法抗测试僵尸");
		}
		zombie.getPersistentData().putBoolean(MonsterScaler.TAG_SCALED, true);
		zombie.getPersistentData().putInt(MonsterScaler.TAG_TL, tl);
		zombie.getPersistentData().putBoolean(MonsterScaler.TAG_EXIMUS,
				kind == MonsterScaler.Kind.EXIMUS);
		zombie.getPersistentData().putBoolean(MonsterScaler.TAG_LEADER,
				kind == MonsterScaler.Kind.LEADER);
		zombie.getPersistentData().putBoolean(MonsterScaler.TAG_BOSS,
				kind == MonsterScaler.Kind.BOSS);
		return zombie;
	}

	private static void checkAmount(List<String> failures, int[] count, String label,
			LivingEntity target, DamageSource source, float input, float expected) {
		LivingHurtEvent hurt = event(target, source, input);
		MagicResistance.onLivingHurt(hurt);
		check(failures, count, label,
				Math.abs(hurt.getAmount() - expected) <= DAMAGE_EPSILON,
				"actual=" + hurt.getAmount() + " expected=" + expected
						+ " source=" + source.getMsgId());
	}

	private static LivingHurtEvent event(LivingEntity target, DamageSource source, float amount) {
		return new LivingHurtEvent(target, source, amount);
	}

	private static void check(List<String> failures, int[] count, String label,
			boolean ok, String detail) {
		count[0]++;
		SummyThreat.LOGGER.info("[A37] {} {} | {}", ok ? "OK" : "FAIL", label, detail);
		if (!ok) {
			failures.add(label + "：" + detail);
		}
	}

	private static boolean close(double actual, double expected) {
		return Math.abs(actual - expected) <= EPSILON;
	}
}
