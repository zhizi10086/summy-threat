package com.summy.threat.devcheck;

import com.summy.threat.SummyThreat;
import com.summy.threat.combat.BossHardcodedDamageScaling;
import com.summy.threat.scaling.MonsterScaler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import java.util.ArrayList;
import java.util.List;

/** A15 Boss 硬编码伤害接管的无世界副作用断言。 */
final class BossHardcodedDamageDevCheck {
	private static final float EPSILON = 1.0E-4F;

	private BossHardcodedDamageDevCheck() {
	}

	static List<String> run(ServerPlayer player) {
		List<String> failures = new ArrayList<>();
		int[] count = {0};
		ServerLevel level = player.serverLevel();
		DamageSources sources = level.damageSources();

		EnderDragon dragon = EntityType.ENDER_DRAGON.create(level);
		WitherBoss wither = EntityType.WITHER.create(level);
		ElderGuardian elder = EntityType.ELDER_GUARDIAN.create(level);
		Guardian guardian = EntityType.GUARDIAN.create(level);
		if (dragon == null || wither == null || elder == null || guardian == null) {
			failures.add("A15 无法创建测试 Boss");
			return failures;
		}

		MonsterScaler.scaleNow(dragon, 20, MonsterScaler.Kind.BOSS);
		MonsterScaler.scaleNow(wither, 20, MonsterScaler.Kind.BOSS);
		elder.getPersistentData().putBoolean(MonsterScaler.TAG_LEADER, true);
		MonsterScaler.scaleNow(elder, 20, MonsterScaler.Kind.LEADER);

		double dragonMultiplier = MonsterScaler.hardcodedAttackMultiplier(dragon);
		double witherMultiplier = MonsterScaler.hardcodedAttackMultiplier(wither);
		double elderMultiplier = MonsterScaler.hardcodedAttackMultiplier(elder);
		check(failures, count, "末影龙 TL20 full 倍率 4.0",
				close(dragonMultiplier, 4.0D), "actual=" + dragonMultiplier);
		check(failures, count, "凋灵 TL20 full 倍率 3.0",
				close(witherMultiplier, 3.0D), "actual=" + witherMultiplier);
		check(failures, count, "区域首领古守 TL20 full 倍率 8.0",
				close(elderMultiplier, 8.0D), "actual=" + elderMultiplier);

		checkAmount(failures, count, "末影龙碰撞 10 → 40",
				player, sources.mobAttack(dragon), 10.0F, 40.0F);
		checkAmount(failures, count, "末影龙身体接触 5 → 20",
				player, sources.mobAttack(dragon), 5.0F, 20.0F);

		WitherSkull skull = new WitherSkull(level, wither, 0.0D, 0.0D, 0.0D);
		checkAmount(failures, count, "凋灵头颅直击 8 → 24",
				player, sources.witherSkull(skull, wither), 8.0F, 24.0F);
		checkAmount(failures, count, "凋灵生成爆炸 10 → 30",
				player, sources.explosion(wither, wither), 10.0F, 30.0F);
		checkAmount(failures, count, "凋灵头颅爆炸 10 → 30",
				player, sources.explosion(skull, wither), 10.0F, 30.0F);

		checkAmount(failures, count, "古守固定激光 1 → 8",
				player, sources.indirectMagic(elder, elder), 1.0F, 8.0F);
		checkAmount(failures, count, "古守困难固定激光 5 → 40",
				player, sources.indirectMagic(elder, elder), 5.0F, 40.0F);
		checkAmount(failures, count, "古守 thorns 2 → 16",
				player, sources.thorns(elder), 2.0F, 16.0F);
		checkAmount(failures, count, "古守 ATTACK_DAMAGE 段不重复缩放",
				player, sources.mobAttack(elder), 10.0F, 10.0F);

		checkAmount(failures, count, "末影龙非 mob_attack 来源不缩放",
				player, sources.indirectMagic(dragon, dragon), 10.0F, 10.0F);
		checkAmount(failures, count, "普通 Guardian 激光不缩放",
				player, sources.indirectMagic(guardian, guardian), 5.0F, 5.0F);
		checkAmount(failures, count, "无关 explosion 不缩放",
				player, sources.explosion(player, player), 10.0F, 10.0F);
		checkAmount(failures, count, "普通 mob_attack 不缩放",
				player, sources.mobAttack(elder), 7.0F, 7.0F);

		checkAmount(failures, count, "施法者自身伤害不缩放",
				dragon, sources.mobAttack(dragon), 10.0F, 10.0F);
		checkAmount(failures, count, "零伤害不缩放",
				player, sources.mobAttack(dragon), 0.0F, 0.0F);
		LivingHurtEvent canceled = event(player, sources.mobAttack(dragon), 10.0F);
		canceled.setCanceled(true);
		BossHardcodedDamageScaling.onLivingHurt(canceled);
		check(failures, count, "取消事件不缩放",
				close(canceled.getAmount(), 10.0F), "actual=" + canceled.getAmount());

		Zombie zombie = EntityType.ZOMBIE.create(level);
		Villager villager = EntityType.VILLAGER.create(level);
		Wolf wolf = EntityType.WOLF.create(level);
		if (zombie != null && villager != null && wolf != null) {
			wolf.setOwnerUUID(player.getUUID());
			for (LivingEntity target : List.of(zombie, villager, wolf)) {
				checkAmount(failures, count, "不同活体目标同倍率：" + target.getType(),
						target, sources.mobAttack(dragon), 10.0F, 40.0F);
			}
		} else {
			failures.add("A15 无法创建目标类型样本");
		}

		dragon.getPersistentData().remove(MonsterScaler.TAG_HARDCODED_ATTACK_MULTIPLIER);
		double restored = MonsterScaler.hardcodedAttackMultiplier(dragon);
		check(failures, count, "旧存档倍率标签可补算",
				close(restored, 4.0D), "actual=" + restored);
		double repeated = MonsterScaler.hardcodedAttackMultiplier(dragon);
		check(failures, count, "倍率补算不累积",
				close(repeated, restored), "first=" + restored + " second=" + repeated);

		EnderDragon unscaled = EntityType.ENDER_DRAGON.create(level);
		if (unscaled != null) {
			checkAmount(failures, count, "未数值化 Boss 倍率为 1",
					player, sources.mobAttack(unscaled), 10.0F, 10.0F);
		}

		SummyThreat.LOGGER.info("[A15] Boss 硬编码伤害检查：{} 项，失败 {}",
				count[0], failures.size());
		return failures;
	}

	private static void checkAmount(List<String> failures, int[] count, String label,
			LivingEntity target, DamageSource source, float input, float expected) {
		LivingHurtEvent event = event(target, source, input);
		BossHardcodedDamageScaling.onLivingHurt(event);
		check(failures, count, label, close(event.getAmount(), expected),
				"actual=" + event.getAmount() + " expected=" + expected
						+ " id=" + source.getMsgId()
						+ " owner=" + source.getEntity()
						+ " direct=" + source.getDirectEntity());
	}

	private static LivingHurtEvent event(LivingEntity target, DamageSource source, float amount) {
		return new LivingHurtEvent(target, source, amount);
	}

	private static void check(List<String> failures, int[] count, String label,
			boolean ok, String detail) {
		count[0]++;
		SummyThreat.LOGGER.info("[A15] {} {} | {}", ok ? "OK" : "FAIL", label, detail);
		if (!ok) {
			failures.add(label + "：" + detail);
		}
	}

	private static boolean close(double actual, double expected) {
		return Math.abs(actual - expected) <= EPSILON;
	}
}
