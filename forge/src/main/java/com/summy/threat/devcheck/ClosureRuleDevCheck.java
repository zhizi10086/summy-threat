package com.summy.threat.devcheck;

import com.summy.threat.Config;
import com.summy.threat.ability.AbilityEffects;
import com.summy.threat.scaling.MonsterScaler;
import com.summy.threat.scaling.ScalingState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Zombie;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 收尾轮：A23 再生配置与 Mod Boss 自动反推的无世界副作用断言。 */
final class ClosureRuleDevCheck {
	private static final double EPSILON = 1.0E-4D;

	private ClosureRuleDevCheck() {
	}

	static List<String> run(ServerPlayer player) {
		List<String> failures = new ArrayList<>();
		ServerLevel level = player.serverLevel();
		Config.TierMode originalMode = Config.ENVIRONMENT_TIER_MODE.get();
		int originalTl = ScalingState.globalTl();
		boolean originalAuto = Config.AUTO_CALCULATE_MOD_BOSS.get();
		double originalGlobalRegen = Config.REGENERATION_PERCENT_PER_SECOND.get();
		double originalReducedRegen = Config.REDUCED_REGENERATION_PERCENT_PER_SECOND.get();
		double originalTtk = Config.DEFAULT_TARGET_TTK.get();
		double originalDps = Config.DEFAULT_EFFECTIVE_DPS.get();
		double originalModAttack = Config.DEFAULT_MOD_BOSS_ATTACK.get();
		List<String> originalModBosses = strings(Config.PCF_MOD_BOSS_IDS.get());
		List<String> originalReducedIds = strings(Config.REDUCED_REGENERATION_ENTITY_IDS.get());

		try {
			Config.ENVIRONMENT_TIER_MODE.set(Config.TierMode.FULL);
			ScalingState.refresh();
			ScalingState.setGlobalTl(20);
			Config.DEFAULT_TARGET_TTK.set(50.0D);
			Config.DEFAULT_EFFECTIVE_DPS.set(700.0D);
			Config.DEFAULT_MOD_BOSS_ATTACK.set(3.0D);

			checkDefaults(failures);
			checkModBoss(failures, level);
			checkRegeneration(failures, level);
		} finally {
			Config.AUTO_CALCULATE_MOD_BOSS.set(originalAuto);
			Config.REGENERATION_PERCENT_PER_SECOND.set(originalGlobalRegen);
			Config.REDUCED_REGENERATION_PERCENT_PER_SECOND.set(originalReducedRegen);
			Config.DEFAULT_TARGET_TTK.set(originalTtk);
			Config.DEFAULT_EFFECTIVE_DPS.set(originalDps);
			Config.DEFAULT_MOD_BOSS_ATTACK.set(originalModAttack);
			Config.PCF_MOD_BOSS_IDS.set(originalModBosses);
			Config.REDUCED_REGENERATION_ENTITY_IDS.set(originalReducedIds);
			Config.ENVIRONMENT_TIER_MODE.set(originalMode);
			ScalingState.refresh();
			ScalingState.setGlobalTl(originalTl);
		}
		return failures;
	}

	private static void checkDefaults(List<String> failures) {
		check(failures, "A23 默认全局再生 1%",
				close(Config.REGENERATION_PERCENT_PER_SECOND.get(), 1.0D),
				"value=" + Config.REGENERATION_PERCENT_PER_SECOND.get());
		check(failures, "A23 默认低恢复 0.25%",
				close(Config.REDUCED_REGENERATION_PERCENT_PER_SECOND.get(), 0.25D),
				"value=" + Config.REDUCED_REGENERATION_PERCENT_PER_SECOND.get());
		check(failures, "A23 默认名单仅末影龙",
				Config.REDUCED_REGENERATION_ENTITY_IDS.get().size() == 1
						&& "minecraft:ender_dragon".equalsIgnoreCase(
								String.valueOf(Config.REDUCED_REGENERATION_ENTITY_IDS.get().get(0))),
				"value=" + Config.REDUCED_REGENERATION_ENTITY_IDS.get());
		check(failures, "Mod Boss 默认自动计算开启", Config.AUTO_CALCULATE_MOD_BOSS.get(),
				"value=" + Config.AUTO_CALCULATE_MOD_BOSS.get());
		check(failures, "Mod Boss 默认攻击 3.0", close(Config.DEFAULT_MOD_BOSS_ATTACK.get(), 3.0D),
				"value=" + Config.DEFAULT_MOD_BOSS_ATTACK.get());
	}

	private static void checkModBoss(List<String> failures, ServerLevel level) {
		Config.PCF_MOD_BOSS_IDS.set(List.of("MINECRAFT:ZOMBIE"));
		Config.AUTO_CALCULATE_MOD_BOSS.set(true);

		Zombie auto = EntityType.ZOMBIE.create(level);
		if (auto == null) {
			failures.add("Mod Boss 自动识别：无法创建僵尸");
			return;
		}
		check(failures, "Mod Boss 名单忽略大小写",
				MonsterScaler.kindOf(auto) == MonsterScaler.Kind.BOSS,
				"kind=" + MonsterScaler.kindOf(auto));
		check(failures, "Mod Boss 参与卓越者排除", MonsterScaler.isBoss(auto),
				"isBoss=" + MonsterScaler.isBoss(auto));
		// 隔离 TL 能力，避免 TL20 随机“力量”污染攻击锚点断言。
		auto.getPersistentData().putString(MonsterScaler.TAG_ABILITIES, "");
		check(failures, "Mod Boss 自动缩放成功", MonsterScaler.scaleNow(auto, 20),
				"scaled=" + auto.getPersistentData().getBoolean(MonsterScaler.TAG_SCALED));
		check(failures, "Mod Boss TL20 生命 35,000", close(auto.getMaxHealth(), 35_000.0D),
				"maxHealth=" + auto.getMaxHealth());
		check(failures, "Mod Boss TL20 攻击 9.0", close(attack(auto), 9.0D),
				"attack=" + attack(auto));
		check(failures, "Mod Boss 显式标签持久化", auto.getPersistentData().getBoolean(MonsterScaler.TAG_BOSS),
				"tag=" + auto.getPersistentData().getBoolean(MonsterScaler.TAG_BOSS));

		Zombie tl0 = EntityType.ZOMBIE.create(level);
		if (tl0 != null) {
			MonsterScaler.scaleNow(tl0, 0);
			check(failures, "Mod Boss TL0 保持原版生命 20", close(tl0.getMaxHealth(), 20.0D),
					"maxHealth=" + tl0.getMaxHealth());
			check(failures, "Mod Boss TL0 保持原版攻击 3", close(attack(tl0), 3.0D),
					"attack=" + attack(tl0));
			tl0.discard();
		}

		Config.AUTO_CALCULATE_MOD_BOSS.set(false);
		Zombie disabled = EntityType.ZOMBIE.create(level);
		if (disabled != null) {
			check(failures, "关闭自动计算后名单实体不再自动成为 Boss",
					MonsterScaler.kindOf(disabled) == MonsterScaler.Kind.NORMAL,
					"kind=" + MonsterScaler.kindOf(disabled));
			disabled.discard();
		}
		Zombie explicit = EntityType.ZOMBIE.create(level);
		if (explicit != null) {
			explicit.getPersistentData().putBoolean(MonsterScaler.TAG_BOSS, true);
			check(failures, "关闭自动计算后显式 Boss 标签仍有效",
					MonsterScaler.kindOf(explicit) == MonsterScaler.Kind.BOSS,
					"kind=" + MonsterScaler.kindOf(explicit));
			explicit.discard();
		}
		auto.discard();
	}

	private static void checkRegeneration(List<String> failures, ServerLevel level) {
		Config.AUTO_CALCULATE_MOD_BOSS.set(true);
		Config.PCF_MOD_BOSS_IDS.set(List.of());
		Config.REDUCED_REGENERATION_ENTITY_IDS.set(List.of("minecraft:ender_dragon"));
		Config.REGENERATION_PERCENT_PER_SECOND.set(1.0D);
		Config.REDUCED_REGENERATION_PERCENT_PER_SECOND.set(0.25D);

		EnderDragon dragon = EntityType.ENDER_DRAGON.create(level);
		if (dragon == null) {
			failures.add("A23 末影龙低恢复：无法创建实体");
			return;
		}
		MonsterScaler.scaleNow(dragon, 20);
		check(failures, "A23 末影龙每秒恢复 87.5", close(AbilityEffects.regenerationAmount(dragon), 87.5D),
				"amount=" + AbilityEffects.regenerationAmount(dragon));

		Husk husk = EntityType.HUSK.create(level);
		if (husk == null) {
			failures.add("A23 普通实体再生：无法创建实体");
			dragon.discard();
			return;
		}
		MonsterScaler.scaleNow(husk, 20);
		check(failures, "A23 默认全局每秒恢复 1%", close(AbilityEffects.regenerationAmount(husk), 9.6D),
				"amount=" + AbilityEffects.regenerationAmount(husk));
		Config.REGENERATION_PERCENT_PER_SECOND.set(0.75D);
		check(failures, "A23 全局再生可修改为 0.75%", close(AbilityEffects.regenerationAmount(husk), 7.2D),
				"amount=" + AbilityEffects.regenerationAmount(husk));
		Config.REDUCED_REGENERATION_PERCENT_PER_SECOND.set(0.5D);
		Config.REDUCED_REGENERATION_ENTITY_IDS.set(List.of("minecraft:ender_dragon", "minecraft:husk"));
		check(failures, "A23 实体加入低恢复名单后使用 0.5%",
				close(AbilityEffects.regenerationAmount(husk), 4.8D),
				"amount=" + AbilityEffects.regenerationAmount(husk));
		Config.REDUCED_REGENERATION_ENTITY_IDS.set(List.of("minecraft:ender_dragon"));
		check(failures, "A23 实体移出低恢复名单后恢复全局值",
				close(AbilityEffects.regenerationAmount(husk), 7.2D),
				"amount=" + AbilityEffects.regenerationAmount(husk));
		Config.REGENERATION_PERCENT_PER_SECOND.set(0.0D);
		Config.REDUCED_REGENERATION_PERCENT_PER_SECOND.set(0.0D);
		check(failures, "A23 再生百分比可设为 0",
				close(AbilityEffects.regenerationAmount(husk), 0.0D)
						&& close(AbilityEffects.regenerationAmount(dragon), 0.0D),
				"husk=" + AbilityEffects.regenerationAmount(husk)
						+ " dragon=" + AbilityEffects.regenerationAmount(dragon));
		husk.discard();
		dragon.discard();
	}

	private static List<String> strings(List<? extends String> values) {
		return values.stream().map(String::valueOf).toList();
	}

	private static double attack(LivingEntity entity) {
		AttributeInstance attack = entity.getAttribute(Attributes.ATTACK_DAMAGE);
		return attack == null ? 0.0D : attack.getValue();
	}

	private static boolean close(double actual, double expected) {
		return Math.abs(actual - expected) <= EPSILON;
	}

	private static void check(List<String> failures, String label, boolean ok, String detail) {
		if (!ok) {
			failures.add(label + "（" + detail + "）");
		}
		if (ok) {
			System.out.printf(Locale.ROOT, "[CLOSURE] OK %s%n", label);
		} else {
			System.out.printf(Locale.ROOT, "[CLOSURE] FAIL %s：%s%n", label, detail);
		}
	}
}
