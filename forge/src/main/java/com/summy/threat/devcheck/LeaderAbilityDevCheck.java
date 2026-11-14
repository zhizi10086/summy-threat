package com.summy.threat.devcheck;

import com.summy.threat.combat.ArmorPenetration;
import com.summy.threat.leader.LeaderAbilityId;
import com.summy.threat.leader.LeaderAbilityPool;
import com.summy.threat.leader.LeaderAbilityState;
import com.summy.threat.leader.LeaderActiveEffects;
import com.summy.threat.leader.LeaderAuraEffects;
import com.summy.threat.leader.LeaderTargeting;
import com.summy.threat.leader.LeaderVisuals;
import com.summy.threat.leader.LegendaryResistance;
import com.summy.threat.scaling.MonsterScaler;
import com.summy.threat.scaling.ScalingState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 区域首领能力注册、抽取与运行时专项自检。 */
final class LeaderAbilityDevCheck {
	private LeaderAbilityDevCheck() {
	}

	static List<String> run(ServerPlayer player) {
		List<String> failures = new ArrayList<>();
		check(failures, "首领能力注册数量为 12", LeaderAbilityId.values().length == 12,
				"size=" + LeaderAbilityId.values().length);
		check(failures, "首领光环注册数量为 5", LeaderAbilityId.auraValues().length == 5,
				"size=" + LeaderAbilityId.auraValues().length);
		check(failures, "首领主动注册数量为 7", LeaderAbilityId.activeValues().length == 7,
				"size=" + LeaderAbilityId.activeValues().length);

		Set<String> ids = new HashSet<>();
		for (LeaderAbilityId id : LeaderAbilityId.values()) {
			ids.add(id.id());
		}
		check(failures, "首领能力 ID 唯一", ids.size() == LeaderAbilityId.values().length,
				"unique=" + ids.size());
		boolean auraWeightsOk = true;
		String auraWeightDetail = "";
		for (LeaderAbilityId id : LeaderAbilityId.auraValues()) {
			if (Math.abs(id.weight() - 0.4D) > 1.0E-6D) {
				auraWeightsOk = false;
				auraWeightDetail = id + "=" + id.weight();
				break;
			}
		}
		check(failures, "5 项光环权重统一为 0.4", auraWeightsOk, auraWeightDetail);
		boolean activeWeightsOk = true;
		String activeWeightDetail = "";
		for (LeaderAbilityId id : LeaderAbilityId.activeValues()) {
			if (Math.abs(id.weight() - 1.0D) > 1.0E-6D) {
				activeWeightsOk = false;
				activeWeightDetail = id + "=" + id.weight();
				break;
			}
		}
		check(failures, "7 项主动能力权重保持 1.0", activeWeightsOk, activeWeightDetail);
		int visualProfiles = 0;
		for (LeaderAbilityId id : LeaderAbilityId.values()) {
			if (LeaderVisuals.supports(id) && id.color() != 0) {
				visualProfiles++;
			}
		}
		check(failures, "12 项首领能力均配置有效视觉与颜色",
				visualProfiles == LeaderAbilityId.values().length,
				"profiles=" + visualProfiles);
		check(failures, "5 项光环视觉半径与效果距离一致",
				LeaderVisuals.auraRadius(LeaderAbilityId.COMMAND_AURA) == 8.0D
						&& LeaderVisuals.auraRadius(LeaderAbilityId.INTIMIDATION_AURA) == 6.0D
						&& LeaderVisuals.auraRadius(LeaderAbilityId.BASTION_AURA) == 8.0D
						&& LeaderVisuals.auraRadius(LeaderAbilityId.SUPPRESSION_AURA) == 6.0D
						&& LeaderVisuals.auraRadius(LeaderAbilityId.BLOODLUST_AURA) == 8.0D,
				"");

		RandomSource random = RandomSource.create(0x5A17C0DEL);
		boolean tl10Ok = true;
		String tl10Detail = "";
		for (int i = 0; i < 1_000; i++) {
			List<LeaderAbilityId> rolled = LeaderAbilityPool.roll(10, random);
			boolean valid = rolled.size() == 2
					&& rolled.get(0).type() == LeaderAbilityId.Type.AURA
					&& rolled.get(0).unlockTl() <= 10
					&& rolled.get(1).unlockTl() <= 10
					&& !rolled.get(0).equals(rolled.get(1));
			if (!valid) {
				tl10Ok = false;
				tl10Detail = rolled.toString();
				break;
			}
		}
		check(failures, "TL10 首领能力固定一光环加一自由能力且不重复", tl10Ok, tl10Detail);

		RandomSource weightRandom = RandomSource.create(0x7714A11L);
		int secondAuraCount = 0;
		boolean weightedFirstAuraOk = true;
		for (int i = 0; i < 100_000; i++) {
			List<LeaderAbilityId> rolled = LeaderAbilityPool.roll(14, weightRandom);
			if (rolled.size() != 2 || !rolled.get(0).aura()) {
				weightedFirstAuraOk = false;
				break;
			}
			if (rolled.get(1).aura()) {
				secondAuraCount++;
			}
		}
		double secondAuraRatio = secondAuraCount / 100_000.0D;
		check(failures, "TL14 首槽仍固定为光环", weightedFirstAuraOk, "");
		check(failures, "TL14 第二槽光环概率降至约 18.6%",
				secondAuraRatio >= 0.17D && secondAuraRatio <= 0.20D,
				"ratio=" + secondAuraRatio);

		boolean suppressionAvailable = false;
		for (LeaderAbilityId id : LeaderAbilityPool.eligible(12)) {
			suppressionAvailable |= id == LeaderAbilityId.SUPPRESSION_AURA;
		}
		check(failures, "压制光环在 TL12 解锁", suppressionAvailable, "pool=" + LeaderAbilityPool.eligible(12));

		Zombie leader = EntityType.ZOMBIE.create(player.serverLevel());
		if (leader == null) {
			failures.add("无法创建区域首领测试僵尸");
			return failures;
		}
		leader.moveTo(player.getX() + 2.0D, player.getY(), player.getZ());
		leader.getPersistentData().putBoolean(MonsterScaler.TAG_LEADER, true);
		leader.getPersistentData().putString(MonsterScaler.TAG_ABILITIES, "swift,strength");
		player.serverLevel().addFreshEntity(leader);
		boolean scaled = MonsterScaler.scaleNow(leader, 12, MonsterScaler.Kind.LEADER);
		check(failures, "区域首领缩放成功", scaled, "scaled=false");

		List<LeaderAbilityId> assigned = LeaderAbilityState.abilities(leader);
		check(failures, "区域首领获得两项首领能力", assigned.size() == 2, "abilities=" + assigned);
		check(failures, "区域首领至少一项光环", assigned.stream().anyMatch(LeaderAbilityId::aura),
				"abilities=" + assigned);
		check(failures, "旧普通 TL 能力已迁移清空",
				leader.getPersistentData().getString(MonsterScaler.TAG_ABILITIES).isEmpty(),
				"tl=" + leader.getPersistentData().getString(MonsterScaler.TAG_ABILITIES));
		check(failures, "区域首领获得基础传奇抗性标签",
				LegendaryResistance.isProtected(leader),
				"tag=false");
		check(failures, "基础传奇抗性提供 100% 击退抗性",
				leader.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) >= 0.999D,
				"knockback=" + leader.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
		check(failures, "基础传奇抗性不是卓越护盾",
				leader.getPersistentData().getDouble("summy_threat:eximus_shield") <= 0.0D,
				"eximusShield=" + leader.getPersistentData().getDouble("summy_threat:eximus_shield"));
		boolean blindnessApplied = leader.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0));
		check(failures, "默认免疫失明", !blindnessApplied && !leader.hasEffect(MobEffects.BLINDNESS),
				"applied=" + blindnessApplied + " effect=" + leader.getEffect(MobEffects.BLINDNESS));
		leader.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 5));
		MobEffectInstance slow = leader.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
		check(failures, "其它负面效果截断至玩家可见 4 级",
				slow != null && slow.getAmplifier() == 3,
				"effect=" + slow);

		Zombie boss = EntityType.ZOMBIE.create(player.serverLevel());
		if (boss != null) {
			boss.moveTo(player.getX() + 3.0D, player.getY(), player.getZ());
			boss.getPersistentData().putBoolean(MonsterScaler.TAG_BOSS, true);
			player.serverLevel().addFreshEntity(boss);
			MonsterScaler.scaleNow(boss, 12, MonsterScaler.Kind.BOSS);
			check(failures, "基础传奇抗性本轮仅应用于区域首领",
					!LegendaryResistance.isProtected(boss), "bossTag="
							+ boss.getPersistentData().getBoolean(LegendaryResistance.TAG_RESISTANCE));
			boss.discard();
		}

		LeaderAbilityState.setAbilities(leader,
				List.of(LeaderAbilityId.COMMAND_AURA, LeaderAbilityId.TECTONIC_RUPTURE));
		Zombie auraAlly = EntityType.ZOMBIE.create(player.serverLevel());
		if (auraAlly != null) {
			auraAlly.moveTo(leader.getX() + 2.0D, leader.getY(), leader.getZ());
			player.serverLevel().addFreshEntity(auraAlly);
			double baseAttack = auraAlly.getAttributeValue(Attributes.ATTACK_DAMAGE);
			double baseSpeed = auraAlly.getAttributeValue(Attributes.MOVEMENT_SPEED);
			long auraNow = player.serverLevel().getGameTime();
			LeaderAuraEffects.tickLeader(leader, auraNow);
			check(failures, "统御光环提高怪物友军攻击 10%",
					Math.abs(auraAlly.getAttributeValue(Attributes.ATTACK_DAMAGE)
							/ baseAttack - 1.10D) < 0.002D,
					"before=" + baseAttack + " after="
							+ auraAlly.getAttributeValue(Attributes.ATTACK_DAMAGE));
			check(failures, "统御光环提高怪物友军移速 10%",
					Math.abs(auraAlly.getAttributeValue(Attributes.MOVEMENT_SPEED)
							/ baseSpeed - 1.10D) < 0.002D,
					"before=" + baseSpeed + " after="
							+ auraAlly.getAttributeValue(Attributes.MOVEMENT_SPEED));
			auraAlly.moveTo(leader.getX() + 20.0D, leader.getY(), leader.getZ());
			LeaderAuraEffects.tickLeader(leader, auraNow + 100L);
			check(failures, "统御光环离开范围并超时后移除",
					Math.abs(auraAlly.getAttributeValue(Attributes.ATTACK_DAMAGE) - baseAttack) < 0.001D
							&& Math.abs(auraAlly.getAttributeValue(Attributes.MOVEMENT_SPEED) - baseSpeed) < 0.001D,
					"attack=" + auraAlly.getAttributeValue(Attributes.ATTACK_DAMAGE)
							+ " speed=" + auraAlly.getAttributeValue(Attributes.MOVEMENT_SPEED));
			auraAlly.discard();
		}

		LeaderAbilityState.setAbilities(leader,
				List.of(LeaderAbilityId.BASTION_AURA, LeaderAbilityId.TECTONIC_RUPTURE));
		checkActiveDamage(failures, player, leader, LeaderAbilityId.TECTONIC_RUPTURE,
				2_000L, 2_020L, "TECTONIC 震地断层造成伤害");
		checkActiveDamage(failures, player, leader, LeaderAbilityId.ARMOR_BREAKER,
				3_000L, 3_016L, "ARMOR 裂甲重击造成伤害");
		checkActiveDamage(failures, player, leader, LeaderAbilityId.HUNT_RAY,
				4_000L, 4_025L, "RAY 猎杀射线造成伤害");
		checkActiveDamage(failures, player, leader, LeaderAbilityId.GRAVITY_PULSE,
				5_000L, 5_020L, "GRAVITY 引力脉冲造成伤害");
		checkActiveDamage(failures, player, leader, LeaderAbilityId.PHASE_LUNGE,
				6_000L, 6_016L, "PHASE 断步突进造成伤害");
		checkActiveDamage(failures, player, leader, LeaderAbilityId.COLLAPSE_FIELD,
				7_000L, 7_020L, "COLLAPSE 崩裂场首跳造成伤害");
		checkApprovedStrengthening(failures, player, leader);

		checkAuraEdges(failures, player, leader);
		checkTargetingEdges(failures, player, leader);
		checkActiveEdges(failures, player, leader);

		LeaderAbilityState.setAbilities(leader,
				List.of(LeaderAbilityId.BASTION_AURA, LeaderAbilityId.IRON_GUARD));
		Zombie ironAttacker = EntityType.ZOMBIE.create(player.serverLevel());
		if (ironAttacker != null) {
			ironAttacker.moveTo(leader.getX() + 2.0D, leader.getY(), leader.getZ());
			player.serverLevel().addFreshEntity(ironAttacker);
			ironAttacker.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10_000.0D);
			ironAttacker.setHealth(ironAttacker.getMaxHealth());
			leader.setTarget(ironAttacker);
			long ironStart = player.serverLevel().getGameTime();
			LeaderActiveEffects.tickLeader(leader, ironStart);
			check(failures, "IRON 铁壁姿态成功进入",
					LeaderActiveEffects.hasIronGuard(leader),
					"current=" + leader.getTarget() + " distance="
							+ leader.distanceTo(ironAttacker)
							+ " abilities=" + LeaderAbilityState.abilities(leader));
			LivingDamageEvent first = new LivingDamageEvent(leader,
					leader.damageSources().mobAttack(ironAttacker), 100.0F);
			LeaderActiveEffects.onLivingDamage(first);
			check(failures, "IRON 铁壁姿态最终承伤降低 30%",
					Math.abs(first.getAmount() - 70.0F) < 0.01F,
					"amount=" + first.getAmount() + " current=" + leader.getTarget()
							+ " distance=" + leader.distanceTo(ironAttacker)
							+ " abilities=" + LeaderAbilityState.abilities(leader));
			LeaderActiveEffects.onLivingDamage(new LivingDamageEvent(leader,
					leader.damageSources().mobAttack(ironAttacker), 100.0F));
			LeaderActiveEffects.onLivingDamage(new LivingDamageEvent(leader,
					leader.damageSources().mobAttack(ironAttacker), 100.0F));
			HurtCapture counter = new HurtCapture(List.of(ironAttacker), true);
			MinecraftForge.EVENT_BUS.register(counter);
			try {
				LeaderActiveEffects.tickServer(player.serverLevel(), ironStart + 1L);
			} finally {
				MinecraftForge.EVENT_BUS.unregister(counter);
			}
			double expectedCounter = expectedRawDamage(leader, ironAttacker, 180.0D);
			check(failures, "IRON 铁壁反击三次受击后反震 5 格目标",
					Math.abs(counter.amountFor(ironAttacker) - expectedCounter) < 0.05D,
					"amount=" + counter.amountFor(ironAttacker) + " expected=" + expectedCounter);
			ironAttacker.discard();
			leader.setTarget(null);
		}
		checkLevelUnloadCleanup(failures, player, leader);
		LeaderAuraEffects.cleanupLeader(leader);
		LeaderActiveEffects.cleanupLeader(leader);
		leader.discard();
		return failures;
	}

	private static void checkActiveDamage(List<String> failures, ServerPlayer player,
			Zombie leader, LeaderAbilityId active, long startTick, long hitTick, String label) {
		LeaderAbilityState.setAbilities(leader, List.of(LeaderAbilityId.BASTION_AURA, active));
		Zombie target = EntityType.ZOMBIE.create(player.serverLevel());
		if (target == null) {
			failures.add(label + "：无法创建靶子");
			return;
		}
		double distance = active == LeaderAbilityId.PHASE_LUNGE ? 5.0D : 2.0D;
		target.moveTo(leader.getX() + distance, leader.getY(), leader.getZ());
		player.serverLevel().addFreshEntity(target);
		leader.setTarget(target);
		float before = target.getHealth();
		LeaderActiveEffects.tickLeader(leader, startTick);
		LeaderActiveEffects.tickLeader(leader, hitTick);
		check(failures, label, target.getHealth() < before || !target.isAlive(),
				"before=" + before + " after=" + target.getHealth() + " alive=" + target.isAlive()
						+ " current=" + leader.getTarget() + " distance=" + leader.distanceTo(target)
						+ " abilities=" + LeaderAbilityState.abilities(leader));
		target.discard();
		leader.setTarget(null);
	}

	private static void checkApprovedStrengthening(List<String> failures, ServerPlayer player,
			Zombie leader) {
		ServerLevel level = player.serverLevel();
		int baseX = player.getBlockX() + 72;
		int baseY = player.getBlockY();
		int baseZ = player.getBlockZ() + 48;
		prepareLane(level, baseX, baseY, baseZ, 32);
		leader.moveTo(baseX + 0.5D, baseY, baseZ + 0.5D, 0.0F, 0.0F);
		long now = level.getGameTime() + 120_000L;

		checkSkillDamageValue(failures, player, leader, LeaderAbilityId.TECTONIC_RUPTURE,
				"TECTONIC", 120.0D, 2.0D, now, now + 20L);
		checkSkillDamageValue(failures, player, leader, LeaderAbilityId.ARMOR_BREAKER,
				"ARMOR", 120.0D, 2.0D, now + 1_000L, now + 1_016L);
		checkSkillDamageValue(failures, player, leader, LeaderAbilityId.HUNT_RAY,
				"RAY", 75.0D, 5.0D, now + 2_000L, now + 2_025L);
		checkSkillDamageValue(failures, player, leader, LeaderAbilityId.GRAVITY_PULSE,
				"GRAVITY", 40.0D, 2.0D, now + 3_000L, now + 3_020L);
		checkSkillDamageValue(failures, player, leader, LeaderAbilityId.PHASE_LUNGE,
				"PHASE", 120.0D, 5.0D, now + 4_000L, now + 4_016L);
		checkSkillDamageValue(failures, player, leader, LeaderAbilityId.COLLAPSE_FIELD,
				"COLLAPSE", 12.0D, 2.0D, now + 5_000L, now + 5_020L);

		checkTectonicTracking(failures, player, leader, baseX, baseY, baseZ);
		checkArmorRangeAndPenetration(failures, player, leader, baseX, baseY, baseZ);
		checkRayWidthAndTracking(failures, player, leader, baseX, baseY, baseZ);
		checkPhaseWidthAndTracking(failures, player, leader, baseX, baseY, baseZ);

		LeaderActiveEffects.cleanupLeader(leader);
		LeaderAbilityState.clear(leader);
		leader.setTarget(null);
	}

	private static void checkSkillDamageValue(List<String> failures, ServerPlayer player,
			Zombie leader, LeaderAbilityId ability, String label, double base, double distance,
			long startTick, long hitTick) {
		Zombie target = spawnZombie(player, leader.getX() + distance, leader.getY(), leader.getZ());
		if (target == null) {
			failures.add(label + " 强度测试无法创建靶子");
			return;
		}
		target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10_000.0D);
		target.setHealth(target.getMaxHealth());
		LeaderActiveEffects.cleanupLeader(leader);
		float amount = captureSkillDamage(leader, target, ability, startTick, hitTick);
		double expected = expectedRawDamage(leader, target, base);
		check(failures, label + " 已应用新基础值与 TL 曲线",
				Math.abs(amount - expected) < 0.05D,
				"amount=" + amount + " expected=" + expected + " base=" + base);
		target.discard();
		leader.setTarget(null);
	}

	private static void checkTectonicTracking(List<String> failures, ServerPlayer player,
			Zombie leader, int baseX, int baseY, int baseZ) {
		ServerLevel level = player.serverLevel();
		prepareLane(level, baseX, baseY, baseZ, 32);
		leader.moveTo(baseX + 0.5D, baseY, baseZ + 0.5D, 0.0F, 0.0F);
		Zombie target = spawnZombie(player, baseX + 3.5D, baseY, baseZ + 0.5D);
		if (target == null) {
			failures.add("TECTONIC 追踪测试无法创建实体");
			return;
		}
		Vec3 playerPosition = player.position();
		long now = level.getGameTime() + 130_000L;
		leader.setTarget(target);
		LeaderAbilityState.setAbilities(leader,
				List.of(LeaderAbilityId.BASTION_AURA, LeaderAbilityId.TECTONIC_RUPTURE));
		HurtCapture capture = new HurtCapture(List.of(target), true);
		MinecraftForge.EVENT_BUS.register(capture);
		try {
			LeaderActiveEffects.tickLeader(leader, now);
			target.moveTo(baseX + 9.5D, baseY, baseZ + 0.5D);
			LeaderActiveEffects.tickLeader(leader, now + 5L);
			LeaderActiveEffects.tickLeader(leader, now + 10L);
			LeaderActiveEffects.tickLeader(leader, now + 20L);
		} finally {
			MinecraftForge.EVENT_BUS.unregister(capture);
		}
		check(failures, "TECTONIC 前 10 tick 跟随目标，后 10 tick 保留落点",
				!Float.isNaN(capture.amountFor(target)),
				"target=" + capture.amountFor(target));

		LeaderActiveEffects.cleanupLeader(leader);
		target.moveTo(baseX + 3.5D, baseY, baseZ + 0.5D);
		HurtCapture locked = new HurtCapture(List.of(target), true);
		MinecraftForge.EVENT_BUS.register(locked);
		try {
			LeaderActiveEffects.tickLeader(leader, now + 1_000L);
			target.moveTo(baseX + 9.5D, baseY, baseZ + 0.5D);
			LeaderActiveEffects.tickLeader(leader, now + 1_005L);
			LeaderActiveEffects.tickLeader(leader, now + 1_010L);
			target.moveTo(baseX + 14.5D, baseY, baseZ + 0.5D);
			LeaderActiveEffects.tickLeader(leader, now + 1_020L);
		} finally {
			MinecraftForge.EVENT_BUS.unregister(locked);
		}
		check(failures, "TECTONIC 锁定后不继续追随移动目标",
				Float.isNaN(locked.amountFor(target)),
				"target=" + locked.amountFor(target));

		LeaderActiveEffects.cleanupLeader(leader);
		target.moveTo(baseX + 3.5D, baseY, baseZ + 0.5D);
		player.teleportTo(level, baseX + 7.8D, baseY, baseZ + 0.5D, player.getYRot(), player.getXRot());
		HurtCapture radius = new HurtCapture(List.of(player), true);
		MinecraftForge.EVENT_BUS.register(radius);
		try {
			LeaderActiveEffects.tickLeader(leader, now + 2_000L);
			LeaderActiveEffects.tickLeader(leader, now + 2_020L);
		} finally {
			MinecraftForge.EVENT_BUS.unregister(radius);
			player.teleportTo(level, playerPosition.x, playerPosition.y, playerPosition.z,
					player.getYRot(), player.getXRot());
		}
		check(failures, "TECTONIC 有效半径扩大到 4.5 格",
				!Float.isNaN(radius.amountFor(player)),
				"decoy=" + radius.amountFor(player));
		target.discard();
		leader.setTarget(null);
	}

	private static void checkArmorRangeAndPenetration(List<String> failures, ServerPlayer player,
			Zombie leader, int baseX, int baseY, int baseZ) {
		ServerLevel level = player.serverLevel();
		prepareLane(level, baseX, baseY, baseZ, 32);
		leader.moveTo(baseX + 0.5D, baseY, baseZ + 0.5D, 0.0F, 0.0F);
		Zombie moving = spawnZombie(player, baseX + 2.5D, baseY, baseZ + 0.5D);
		if (moving == null) {
			failures.add("ARMOR 范围测试无法创建靶子");
			return;
		}
		long now = level.getGameTime() + 140_000L;
		leader.setTarget(moving);
		LeaderAbilityState.setAbilities(leader,
				List.of(LeaderAbilityId.BASTION_AURA, LeaderAbilityId.ARMOR_BREAKER));
		HurtCapture rangeCapture = new HurtCapture(List.of(moving), true);
		MinecraftForge.EVENT_BUS.register(rangeCapture);
		try {
			LeaderActiveEffects.tickLeader(leader, now);
			moving.moveTo(baseX + 4.8D, baseY, baseZ + 0.5D);
			LeaderActiveEffects.tickLeader(leader, now + 16L);
		} finally {
			MinecraftForge.EVENT_BUS.unregister(rangeCapture);
		}
		check(failures, "ARMOR 施放与结算距离扩大到 5 格",
				!Float.isNaN(rangeCapture.amountFor(moving)),
				"amount=" + rangeCapture.amountFor(moving));

		LeaderActiveEffects.cleanupLeader(leader);
		moving.discard();
		Zombie armored = spawnZombie(player, baseX + 2.5D, baseY, baseZ + 0.5D);
		if (armored == null) {
			failures.add("ARMOR 穿甲测试无法创建靶子");
			return;
		}
		armored.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10_000.0D);
		armored.getAttribute(Attributes.ARMOR).setBaseValue(38.0D);
		armored.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(28.0D);
		armored.setHealth(armored.getMaxHealth());
		Zombie control = spawnZombie(player, baseX + 12.5D, baseY, baseZ + 0.5D);
		if (control == null) {
			armored.discard();
			failures.add("ARMOR 穿甲对照无法创建靶子");
			return;
		}
		control.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10_000.0D);
		control.getAttribute(Attributes.ARMOR).setBaseValue(38.0D);
		control.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(28.0D);
		control.setHealth(control.getMaxHealth());
		float controlBefore = control.getHealth();
		DamageSource armorSource = leader.damageSources().mobAttack(leader);
		ArmorPenetration.hurt(control, armorSource, 1_200.0F, 0.50D);
		double expectedLoss = controlBefore - control.getHealth();
		int originalTl = leader.getPersistentData().getInt(MonsterScaler.TAG_TL);
		float healthBefore = armored.getHealth();
		try {
			leader.getPersistentData().putInt(MonsterScaler.TAG_TL, 20);
			leader.setTarget(armored);
			LeaderAbilityState.setAbilities(leader,
					List.of(LeaderAbilityId.BASTION_AURA, LeaderAbilityId.ARMOR_BREAKER));
			LeaderActiveEffects.tickLeader(leader, now + 1_000L);
			LeaderActiveEffects.tickLeader(leader, now + 1_016L);
		} finally {
			leader.getPersistentData().putInt(MonsterScaler.TAG_TL, originalTl);
		}
		double actualLoss = healthBefore - armored.getHealth();
		check(failures, "ARMOR 使用 120 基础值与 50% 本次穿甲",
				Math.abs(actualLoss - expectedLoss) < 1.0D,
				"actual=" + actualLoss + " expected=" + expectedLoss);
		control.discard();
		armored.discard();
		leader.setTarget(null);
		LeaderActiveEffects.cleanupLeader(leader);
	}

	private static void checkRayWidthAndTracking(List<String> failures, ServerPlayer player,
			Zombie leader, int baseX, int baseY, int baseZ) {
		ServerLevel level = player.serverLevel();
		prepareLane(level, baseX, baseY, baseZ, 32);
		leader.moveTo(baseX + 0.5D, baseY, baseZ + 0.5D, 0.0F, 0.0F);
		Wolf primary = spawnPet(player, baseX + 8.5D, baseY, baseZ + 0.5D);
		Wolf side = spawnPet(player, baseX + 6.5D, baseY, baseZ + 1.35D);
		if (primary == null || side == null) {
			failures.add("RAY 宽度测试无法创建目标");
			return;
		}
		long now = level.getGameTime() + 150_000L;
		leader.setTarget(primary);
		LeaderAbilityState.setAbilities(leader,
				List.of(LeaderAbilityId.BASTION_AURA, LeaderAbilityId.HUNT_RAY));
		HurtCapture width = new HurtCapture(List.of(primary, side), true);
		MinecraftForge.EVENT_BUS.register(width);
		try {
			LeaderActiveEffects.tickLeader(leader, now);
			LeaderActiveEffects.tickLeader(leader, now + 25L);
		} finally {
			MinecraftForge.EVENT_BUS.unregister(width);
		}
		check(failures, "RAY 宽度扩大到 2 格",
				!Float.isNaN(width.amountFor(side)),
				"side=" + width.amountFor(side));

		LeaderActiveEffects.cleanupLeader(leader);
		prepareLane(level, baseX, baseY, baseZ, 32);
		primary.moveTo(baseX + 8.5D, baseY, baseZ + 0.5D);
		Wolf oldLine = spawnPet(player, baseX + 6.5D, baseY, baseZ + 0.5D);
		if (oldLine == null) {
			failures.add("RAY 追踪测试无法创建旧方向目标");
			return;
		}
		leader.moveTo(baseX + 0.5D, baseY, baseZ + 0.5D, 0.0F, 0.0F);
		leader.setTarget(primary);
		HurtCapture tracking = new HurtCapture(List.of(primary, oldLine), true);
		MinecraftForge.EVENT_BUS.register(tracking);
		try {
			LeaderActiveEffects.tickLeader(leader, now + 1_000L);
			primary.moveTo(baseX + 8.5D, baseY, baseZ + 2.5D);
			LeaderActiveEffects.tickLeader(leader, now + 1_014L);
			LeaderActiveEffects.tickLeader(leader, now + 1_015L);
			LeaderActiveEffects.tickLeader(leader, now + 1_025L);
		} finally {
			MinecraftForge.EVENT_BUS.unregister(tracking);
		}
		check(failures, "RAY 前 15 tick 追踪、后 10 tick 冻结方向",
				!Float.isNaN(tracking.amountFor(primary))
						&& Float.isNaN(tracking.amountFor(oldLine)),
				"primary=" + tracking.amountFor(primary)
						+ " oldLine=" + tracking.amountFor(oldLine));
		primary.discard();
		side.discard();
		oldLine.discard();
		leader.setTarget(null);
	}

	private static void checkPhaseWidthAndTracking(List<String> failures, ServerPlayer player,
			Zombie leader, int baseX, int baseY, int baseZ) {
		ServerLevel level = player.serverLevel();
		prepareLane(level, baseX, baseY, baseZ, 32);
		leader.moveTo(baseX + 0.5D, baseY, baseZ + 0.5D, 0.0F, 0.0F);
		Wolf primary = spawnPet(player, baseX + 8.5D, baseY, baseZ + 0.5D);
		Wolf side = spawnPet(player, baseX + 6.5D, baseY, baseZ + 1.35D);
		if (primary == null || side == null) {
			failures.add("PHASE 宽度测试无法创建目标");
			return;
		}
		long now = level.getGameTime() + 160_000L;
		leader.setTarget(primary);
		LeaderAbilityState.setAbilities(leader,
				List.of(LeaderAbilityId.BASTION_AURA, LeaderAbilityId.PHASE_LUNGE));
		HurtCapture width = new HurtCapture(List.of(primary, side), true);
		MinecraftForge.EVENT_BUS.register(width);
		try {
			LeaderActiveEffects.tickLeader(leader, now);
			LeaderActiveEffects.tickLeader(leader, now + 16L);
		} finally {
			MinecraftForge.EVENT_BUS.unregister(width);
		}
		check(failures, "PHASE 路径宽度扩大到 2 格",
				!Float.isNaN(width.amountFor(side)),
				"side=" + width.amountFor(side));

		LeaderActiveEffects.cleanupLeader(leader);
		prepareLane(level, baseX, baseY, baseZ, 32);
		primary.moveTo(baseX + 8.5D, baseY, baseZ + 0.5D);
		Wolf oldLine = spawnPet(player, baseX + 6.5D, baseY, baseZ + 0.5D);
		if (oldLine == null) {
			failures.add("PHASE 追踪测试无法创建旧方向目标");
			return;
		}
		leader.moveTo(baseX + 0.5D, baseY, baseZ + 0.5D, 0.0F, 0.0F);
		leader.setTarget(primary);
		HurtCapture tracking = new HurtCapture(List.of(primary, oldLine), true);
		MinecraftForge.EVENT_BUS.register(tracking);
		try {
			LeaderActiveEffects.tickLeader(leader, now + 1_000L);
			primary.moveTo(baseX + 8.5D, baseY, baseZ + 2.5D);
			LeaderActiveEffects.tickLeader(leader, now + 1_007L);
			LeaderActiveEffects.tickLeader(leader, now + 1_008L);
			LeaderActiveEffects.tickLeader(leader, now + 1_016L);
		} finally {
			MinecraftForge.EVENT_BUS.unregister(tracking);
		}
		check(failures, "PHASE 前 8 tick 跟踪、最后 8 tick 锁定方向",
				!Float.isNaN(tracking.amountFor(primary))
						&& Float.isNaN(tracking.amountFor(oldLine)),
				"primary=" + tracking.amountFor(primary)
						+ " oldLine=" + tracking.amountFor(oldLine));
		primary.discard();
		side.discard();
		oldLine.discard();
		leader.setTarget(null);
	}

	private static double expectedRawDamage(LivingEntity owner, LivingEntity target, double base) {
		int tl = Math.max(10, ScalingState.clampTl(
				owner.getPersistentData().getInt(MonsterScaler.TAG_TL)));
		double factor = LeaderTargeting.isPlayerSide(target) ? 1.0D : 10.0D;
		return base * ScalingState.relativeAttackMultiplier(tl)
				* ScalingState.rAtk() * factor;
	}

	private static void checkAuraEdges(List<String> failures, ServerPlayer player, Zombie leader) {
		ServerLevel level = player.serverLevel();
		long now = level.getGameTime() + 20_000L;
		Zombie victim = spawnZombie(player, leader.getX() + 2.0D, leader.getY(), leader.getZ());
		Zombie ally = spawnZombie(player, leader.getX() + 1.0D, leader.getY(), leader.getZ());
		if (victim == null || ally == null) {
			failures.add("AURA 边界测试无法创建测试僵尸");
			return;
		}
		leader.setTarget(victim);

		LeaderAbilityState.setAbilities(leader,
				List.of(LeaderAbilityId.INTIMIDATION_AURA, LeaderAbilityId.TECTONIC_RUPTURE));
		LeaderAuraEffects.tickLeader(leader, now);
		LivingHurtEvent intimidation = new LivingHurtEvent(victim,
				victim.damageSources().mobAttack(ally), 100.0F);
		LeaderAuraEffects.onLivingHurt(intimidation);
		check(failures, "INTIMIDATION 首领方普通伤害放大 10%",
				Math.abs(intimidation.getAmount() - 110.0F) < 0.01F,
				"amount=" + intimidation.getAmount());
		LivingHurtEvent canceledIntimidation = new LivingHurtEvent(victim,
				victim.damageSources().mobAttack(ally), 100.0F);
		canceledIntimidation.setCanceled(true);
		LeaderAuraEffects.onLivingHurt(canceledIntimidation);
		check(failures, "INTIMIDATION 跳过已取消伤害",
				Math.abs(canceledIntimidation.getAmount() - 100.0F) < 0.01F,
				"amount=" + canceledIntimidation.getAmount());
		LivingHurtEvent environmental = new LivingHurtEvent(victim,
				victim.damageSources().generic(), 100.0F);
		LeaderAuraEffects.onLivingHurt(environmental);
		check(failures, "INTIMIDATION 不放大环境伤害",
				Math.abs(environmental.getAmount() - 100.0F) < 0.01F,
				"amount=" + environmental.getAmount());

		LeaderAbilityState.setAbilities(leader,
				List.of(LeaderAbilityId.BASTION_AURA, LeaderAbilityId.TECTONIC_RUPTURE));
		LeaderAuraEffects.tickLeader(leader, now + 100L);
		LivingDamageEvent bastion = new LivingDamageEvent(leader,
				leader.damageSources().mobAttack(ally), 100.0F);
		LeaderAuraEffects.onLivingDamage(bastion);
		check(failures, "BASTION 最终承伤降低 10%",
				Math.abs(bastion.getAmount() - 90.0F) < 0.01F,
				"amount=" + bastion.getAmount());
		LivingDamageEvent canceledBastion = new LivingDamageEvent(leader,
				leader.damageSources().mobAttack(ally), 100.0F);
		canceledBastion.setCanceled(true);
		LeaderAuraEffects.onLivingDamage(canceledBastion);
		check(failures, "BASTION 跳过已取消伤害",
				Math.abs(canceledBastion.getAmount() - 100.0F) < 0.01F,
				"amount=" + canceledBastion.getAmount());
		LivingDamageEvent bypass = new LivingDamageEvent(leader,
				leader.damageSources().fellOutOfWorld(), 100.0F);
		LeaderAuraEffects.onLivingDamage(bypass);
		check(failures, "BASTION 不降低穿透无敌伤害",
				Math.abs(bypass.getAmount() - 100.0F) < 0.01F,
				"amount=" + bypass.getAmount());

		LeaderAbilityState.setAbilities(leader,
				List.of(LeaderAbilityId.SUPPRESSION_AURA, LeaderAbilityId.TECTONIC_RUPTURE));
		AttributeInstance speed = victim.getAttribute(Attributes.MOVEMENT_SPEED);
		UUID speedBonusId = UUID.fromString("6f5c1a2e-0009-4a0b-9d1e-00000000d501");
		speed.addTransientModifier(new AttributeModifier(speedBonusId,
				"summy_threat:leader_devcheck/speed_bonus", 0.20D,
				AttributeModifier.Operation.MULTIPLY_BASE));
		double beforeSuppression = speed.getValue();
		LeaderAuraEffects.tickLeader(leader, now + 200L);
		double afterSuppression = speed.getValue();
		check(failures, "SUPPRESSION 对最终移速独立乘 85%",
				Math.abs(afterSuppression / beforeSuppression - 0.85D) < 0.002D,
				"before=" + beforeSuppression + " after=" + afterSuppression);
		speed.removeModifier(speedBonusId);
		leader.setTarget(null);
		LeaderAuraEffects.tickLeader(leader, now + 300L);
		check(failures, "SUPPRESSION 离开范围并超时后移除",
				Math.abs(speed.getValue() - speed.getBaseValue()) < 0.001D,
				"value=" + speed.getValue());

		LeaderAbilityState.setAbilities(leader,
				List.of(LeaderAbilityId.BLOODLUST_AURA, LeaderAbilityId.TECTONIC_RUPTURE));
		double baseAttack = leader.getAttributeValue(Attributes.ATTACK_DAMAGE);
		double baseSpeed = leader.getAttributeValue(Attributes.MOVEMENT_SPEED);
		leader.setHealth(leader.getMaxHealth() * 0.50F);
		LeaderAuraEffects.tickLeader(leader, now + 400L);
		check(failures, "BLOODLUST 50% 阈值触发攻击 +15%",
				Math.abs(leader.getAttributeValue(Attributes.ATTACK_DAMAGE) / baseAttack - 1.15D) < 0.002D,
				"before=" + baseAttack + " after="
						+ leader.getAttributeValue(Attributes.ATTACK_DAMAGE));
		check(failures, "BLOODLUST 50% 阈值触发移速 +10%",
				Math.abs(leader.getAttributeValue(Attributes.MOVEMENT_SPEED) / baseSpeed - 1.10D) < 0.002D,
				"before=" + baseSpeed + " after="
						+ leader.getAttributeValue(Attributes.MOVEMENT_SPEED));
		leader.setHealth(leader.getMaxHealth() * 0.55F);
		LeaderAuraEffects.tickLeader(leader, now + 500L);
		check(failures, "BLOODLUST 55% 回差边界仍保持",
				Math.abs(leader.getAttributeValue(Attributes.ATTACK_DAMAGE) / baseAttack - 1.15D) < 0.002D,
				"attack=" + leader.getAttributeValue(Attributes.ATTACK_DAMAGE));
		leader.setHealth(leader.getMaxHealth() * 0.56F);
		LeaderAuraEffects.tickLeader(leader, now + 600L);
		check(failures, "BLOODLUST 高于 55% 后解除",
				Math.abs(leader.getAttributeValue(Attributes.ATTACK_DAMAGE) - baseAttack) < 0.001D
						&& Math.abs(leader.getAttributeValue(Attributes.MOVEMENT_SPEED) - baseSpeed) < 0.001D,
				"attack=" + leader.getAttributeValue(Attributes.ATTACK_DAMAGE)
						+ " speed=" + leader.getAttributeValue(Attributes.MOVEMENT_SPEED));

		LeaderAbilityState.clear(leader);
		leader.setTarget(null);
		victim.discard();
		ally.discard();
	}

	private static void checkTargetingEdges(List<String> failures, ServerPlayer player, Zombie leader) {
		GameType previous = player.gameMode.getGameModeForPlayer();
		leader.setTarget(player);
		player.setGameMode(GameType.SPECTATOR);
		check(failures, "TARGETING 排除旁观玩家即使为其当前目标",
				!LeaderTargeting.isTarget(leader, player),
				"target=" + LeaderTargeting.currentTarget(leader));
		player.setGameMode(GameType.CREATIVE);
		check(failures, "TARGETING 排除创造玩家即使为其当前目标",
				!LeaderTargeting.isTarget(leader, player),
				"target=" + LeaderTargeting.currentTarget(leader));
		player.setGameMode(previous);
		leader.setTarget(null);
	}

	private static void checkActiveEdges(List<String> failures, ServerPlayer player, Zombie leader) {
		ServerLevel level = player.serverLevel();
		int baseX = player.getBlockX() + 32;
		int baseY = player.getBlockY();
		int baseZ = player.getBlockZ() + 32;
		prepareLane(level, baseX, baseY, baseZ, 24);

		Zombie normal = spawnZombie(player, baseX + 8.5D, baseY, baseZ + 0.5D);
		Wolf pet = spawnPet(player, baseX + 20.5D, baseY, baseZ + 0.5D);
		Wolf wildWolf = spawnWolf(player, baseX + 21.5D, baseY, baseZ + 0.5D);
		if (normal == null || pet == null || wildWolf == null) {
			failures.add("ACTIVE 边界测试无法创建伤害倍率目标");
			return;
		}
		leader.moveTo(baseX + 0.5D, baseY, baseZ + 0.5D, 0.0F, 0.0F);
		leader.setTarget(normal);
		long now = level.getGameTime() + 30_000L;
		int leaderTl = Math.max(10, leader.getPersistentData().getInt(MonsterScaler.TAG_TL));
		double expectedDirectNormal = 600.0D * ScalingState.rawAttackMultiplier(
				leaderTl) / ScalingState.rawAttackMultiplier(20) * ScalingState.rAtk();
		float directNormalHit = directSkillDamage(leader, normal, 60.0D);
		check(failures, "SKILL 直接结算同时应用首领 TL 与 10 倍目标倍率",
				Math.abs(directNormalHit - expectedDirectNormal) < 0.05D,
				"amount=" + directNormalHit + " expected=" + expectedDirectNormal
						+ " tl=" + leaderTl + " rAtk=" + ScalingState.rAtk());
		double expectedPlayerHit = 60.0D * ScalingState.rawAttackMultiplier(
				leaderTl) / ScalingState.rawAttackMultiplier(20) * ScalingState.rAtk();
		float directPlayerHit = directSkillDamage(leader, player, 60.0D);
		check(failures, "SKILL 玩家侧应用首领 TL 且保持 1 倍目标倍率",
				Math.abs(directPlayerHit - expectedPlayerHit) < 0.05D,
				"amount=" + directPlayerHit + " expected=" + expectedPlayerHit
						+ " tl=" + leaderTl + " rAtk=" + ScalingState.rAtk());
		leader.getPersistentData().putInt(MonsterScaler.TAG_TL, 9);
		double expectedTl10 = 600.0D * ScalingState.rawAttackMultiplier(
				10) / ScalingState.rawAttackMultiplier(20) * ScalingState.rAtk();
		float directTl10 = directSkillDamage(leader, normal, 60.0D);
		check(failures, "SKILL 首领 TL 下限固定按 10 计算",
				Math.abs(directTl10 - expectedTl10) < 0.05D,
				"amount=" + directTl10 + " expected=" + expectedTl10);
		leader.getPersistentData().putInt(MonsterScaler.TAG_TL, 24);
		double expectedTl20 = 600.0D * ScalingState.rAtk();
		float directTl20 = directSkillDamage(leader, normal, 60.0D);
		check(failures, "SKILL 首领 TL 上限固定按 20 计算",
				Math.abs(directTl20 - expectedTl20) < 0.05D,
				"amount=" + directTl20 + " expected=" + expectedTl20);
		leader.getPersistentData().putInt(MonsterScaler.TAG_TL, leaderTl);
		float normalHit = captureSkillDamage(leader, normal, LeaderAbilityId.TECTONIC_RUPTURE,
				now, now + 20L);
		double expectedNormalHit = 1_200.0D * ScalingState.rawAttackMultiplier(
				leaderTl) / ScalingState.rawAttackMultiplier(20) * ScalingState.rAtk();
		check(failures, "SKILL 普通非玩家目标同时应用 TL 与 10 倍原始伤害",
				Math.abs(normalHit - expectedNormalHit) < 0.05F,
				"amount=" + normalHit + " expected=" + expectedNormalHit
						+ " tl=" + leaderTl + " rAtk=" + ScalingState.rAtk());
		pet.moveTo(baseX + 8.5D, baseY, baseZ + 0.5D);
		leader.setTarget(pet);
		float petHit = directSkillDamage(leader, pet, 60.0D);
		wildWolf.moveTo(baseX + 8.5D, baseY, baseZ + 0.5D);
		leader.setTarget(wildWolf);
		float wildHit = directSkillDamage(leader, wildWolf, 60.0D);
		check(failures, "SKILL 玩家驯服宠物相对非玩家同类使用 1/10 倍率",
				petHit > 0.0F && Math.abs(wildHit / petHit - 10.0F) < 0.20F,
				"pet=" + petHit + " wild=" + wildHit + " ratio=" + (wildHit / petHit));

		Zombie cancelTarget = spawnZombie(player, baseX + 2.5D, baseY, baseZ + 0.5D);
		if (cancelTarget != null) {
			leader.setTarget(cancelTarget);
			double cancelBaseSpeed = cancelTarget.getAttributeValue(Attributes.MOVEMENT_SPEED);
			cancelTarget.setDeltaMovement(Vec3.ZERO);
			HurtCapture capture = new HurtCapture(List.of(cancelTarget), true);
			MinecraftForge.EVENT_BUS.register(capture);
			try {
				LeaderAbilityState.setAbilities(leader,
						List.of(LeaderAbilityId.BASTION_AURA, LeaderAbilityId.TECTONIC_RUPTURE));
				LeaderActiveEffects.tickLeader(leader, now + 2_000L);
				LeaderActiveEffects.tickLeader(leader, now + 2_020L);
			} finally {
				MinecraftForge.EVENT_BUS.unregister(capture);
			}
			check(failures, "TECTONIC 伤害取消后不击退或减速",
					Math.abs(cancelTarget.getDeltaMovement().y) < 0.01D
							&& Math.hypot(cancelTarget.getDeltaMovement().x,
									cancelTarget.getDeltaMovement().z) < 0.5D
							&& Math.abs(cancelTarget.getAttributeValue(Attributes.MOVEMENT_SPEED)
									- cancelBaseSpeed) < 0.001D,
					"delta=" + cancelTarget.getDeltaMovement() + " speed="
							+ cancelTarget.getAttributeValue(Attributes.MOVEMENT_SPEED));
			cancelTarget.discard();
		}

		checkRayTerrainAndPassThrough(failures, player, leader, baseX, baseY, baseZ);
		checkPhaseTerrain(failures, player, leader, baseX, baseY, baseZ);
		checkGravityLifecycle(failures, player, leader, baseX, baseY, baseZ);
		checkCollapseLifecycle(failures, player, leader, baseX, baseY, baseZ);
		checkIronCanceledHits(failures, player, leader);

		LeaderActiveEffects.cleanupLeader(leader);
		LeaderAuraEffects.cleanupLeader(leader);
		LeaderAbilityState.clear(leader);
		leader.setTarget(null);
		normal.discard();
		pet.discard();
		wildWolf.discard();
	}

	private static void checkRayTerrainAndPassThrough(List<String> failures, ServerPlayer player,
			Zombie leader, int baseX, int baseY, int baseZ) {
		ServerLevel level = player.serverLevel();
		prepareLane(level, baseX, baseY, baseZ, 24);
		leader.moveTo(baseX + 0.5D, baseY, baseZ + 0.5D, 0.0F, 0.0F);
		Wolf hidden = spawnPet(player, baseX + 8.5D, baseY, baseZ + 0.5D);
		if (hidden == null) {
			failures.add("RAY 地形测试无法创建目标");
			return;
		}
		for (int dy = 0; dy <= 2; dy++) {
			level.setBlockAndUpdate(new BlockPos(baseX + 4, baseY + dy, baseZ),
					Blocks.STONE.defaultBlockState());
		}
		leader.setTarget(hidden);
		long now = level.getGameTime() + 40_000L;
		HurtCapture blocked = new HurtCapture(List.of(hidden), true);
		MinecraftForge.EVENT_BUS.register(blocked);
		try {
			LeaderAbilityState.setAbilities(leader,
					List.of(LeaderAbilityId.BASTION_AURA, LeaderAbilityId.HUNT_RAY));
			LeaderActiveEffects.tickLeader(leader, now);
			LeaderActiveEffects.tickLeader(leader, now + 25L);
		} finally {
			MinecraftForge.EVENT_BUS.unregister(blocked);
		}
		check(failures, "RAY 被实体方块截断",
				Float.isNaN(blocked.amountFor(hidden)),
				"amount=" + blocked.amountFor(hidden));

		prepareLane(level, baseX, baseY, baseZ, 24);
		hidden.discard();
		Wolf near = spawnPet(player, baseX + 4.5D, baseY + 0.4D, baseZ + 0.5D);
		Wolf far = spawnPet(player, baseX + 8.5D, baseY, baseZ + 0.5D);
		if (near == null || far == null) {
			failures.add("RAY 穿透测试无法创建目标");
			return;
		}
		leader.moveTo(baseX + 0.5D, baseY, baseZ + 0.5D, 0.0F, 0.0F);
		leader.setTarget(far);
		HurtCapture passThrough = new HurtCapture(List.of(near, far), true);
		MinecraftForge.EVENT_BUS.register(passThrough);
		try {
			LeaderAbilityState.setAbilities(leader,
					List.of(LeaderAbilityId.BASTION_AURA, LeaderAbilityId.HUNT_RAY));
			LeaderActiveEffects.tickLeader(leader, now + 1_000L);
			LeaderActiveEffects.tickLeader(leader, now + 1_025L);
		} finally {
			MinecraftForge.EVENT_BUS.unregister(passThrough);
		}
		check(failures, "RAY 合法目标之间可穿透且各命中一次",
				!Float.isNaN(passThrough.amountFor(near))
						&& !Float.isNaN(passThrough.amountFor(far)),
				"near=" + passThrough.amountFor(near) + " far=" + passThrough.amountFor(far)
						+ " nearPlayerSide=" + LeaderTargeting.isPlayerSide(near)
						+ " nearPos=" + near.position() + " farPos=" + far.position());
		near.discard();
		far.discard();
		leader.setTarget(null);
	}

	private static void checkPhaseTerrain(List<String> failures, ServerPlayer player, Zombie leader,
			int baseX, int baseY, int baseZ) {
		ServerLevel level = player.serverLevel();
		prepareLane(level, baseX, baseY, baseZ, 24);
		Wolf target = spawnPet(player, baseX + 8.5D, baseY, baseZ + 0.5D);
		if (target == null) {
			failures.add("PHASE 地形测试无法创建目标");
			return;
		}
		for (int dy = 0; dy <= 2; dy++) {
			level.setBlockAndUpdate(new BlockPos(baseX + 4, baseY + dy, baseZ),
					Blocks.STONE.defaultBlockState());
		}
		leader.moveTo(baseX + 0.5D, baseY, baseZ + 0.5D, 0.0F, 0.0F);
		leader.setTarget(target);
		long now = level.getGameTime() + 50_000L;
		HurtCapture wallCapture = new HurtCapture(List.of(target), true);
		MinecraftForge.EVENT_BUS.register(wallCapture);
		try {
			LeaderAbilityState.setAbilities(leader,
					List.of(LeaderAbilityId.BASTION_AURA, LeaderAbilityId.PHASE_LUNGE));
			LeaderActiveEffects.tickLeader(leader, now);
			LeaderActiveEffects.tickLeader(leader, now + 16L);
		} finally {
			MinecraftForge.EVENT_BUS.unregister(wallCapture);
		}
		check(failures, "PHASE 两格高墙截断突进路径",
				leader.getX() < baseX + 4.0D,
				"x=" + leader.getX() + " wallX=" + (baseX + 4));

		prepareLane(level, baseX, baseY, baseZ, 24);
		level.setBlockAndUpdate(new BlockPos(baseX + 4, baseY, baseZ),
				Blocks.STONE.defaultBlockState());
		leader.moveTo(baseX + 0.5D, baseY, baseZ + 0.5D, 0.0F, 0.0F);
		target.moveTo(baseX + 8.5D, baseY, baseZ + 0.5D);
		leader.setTarget(target);
		HurtCapture stepCapture = new HurtCapture(List.of(target), true);
		MinecraftForge.EVENT_BUS.register(stepCapture);
		try {
			LeaderAbilityState.setAbilities(leader,
					List.of(LeaderAbilityId.BASTION_AURA, LeaderAbilityId.PHASE_LUNGE));
			LeaderActiveEffects.tickLeader(leader, now + 1_000L);
			LeaderActiveEffects.tickLeader(leader, now + 1_016L);
		} finally {
			MinecraftForge.EVENT_BUS.unregister(stepCapture);
		}
		check(failures, "PHASE 可跨越一格高台阶",
				leader.getX() > baseX + 4.5D,
				"x=" + leader.getX() + " stepX=" + (baseX + 4));
		target.discard();
		leader.setTarget(null);
	}

	private static void checkGravityLifecycle(List<String> failures, ServerPlayer player, Zombie leader,
			int baseX, int baseY, int baseZ) {
		ServerLevel level = player.serverLevel();
		leader.moveTo(baseX + 0.5D, baseY, baseZ + 0.5D, 0.0F, 0.0F);
		Zombie normal = spawnZombie(player, baseX + 4.5D, baseY, baseZ + 0.5D);
		if (normal == null) {
			failures.add("GRAVITY 生命周期测试无法创建普通目标");
			return;
		}
		normal.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10_000.0D);
		normal.setHealth(normal.getMaxHealth());
		leader.setTarget(normal);
		long now = level.getGameTime() + 60_000L;
		LeaderAbilityState.setAbilities(leader,
				List.of(LeaderAbilityId.BASTION_AURA, LeaderAbilityId.GRAVITY_PULSE));
		LeaderActiveEffects.tickLeader(leader, now);
		LeaderActiveEffects.tickLeader(leader, now + 20L);
		normal.setDeltaMovement(Vec3.ZERO);
		LeaderActiveEffects.tickServer(level, now + 21L);
		check(failures, "GRAVITY 普通目标持续牵引",
				normal.getDeltaMovement().lengthSqr() > 1.0E-6D,
				"delta=" + normal.getDeltaMovement());
		normal.setDeltaMovement(Vec3.ZERO);
		LeaderActiveEffects.cleanupLeader(leader);
		LeaderActiveEffects.tickServer(level, now + 22L);
		check(failures, "GRAVITY 首领死亡后立即停止牵引",
				normal.getDeltaMovement().lengthSqr() < 1.0E-6D,
				"delta=" + normal.getDeltaMovement());

		Zombie boss = spawnZombie(player, baseX + 4.5D, baseY, baseZ + 2.5D);
		if (boss == null) {
			normal.discard();
			failures.add("GRAVITY 免疫测试无法创建 Boss 目标");
			return;
		}
		boss.getPersistentData().putBoolean(MonsterScaler.TAG_BOSS, true);
		boss.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10_000.0D);
		boss.setHealth(boss.getMaxHealth());
		leader.setTarget(boss);
		LeaderAbilityState.setAbilities(leader,
				List.of(LeaderAbilityId.BASTION_AURA, LeaderAbilityId.GRAVITY_PULSE));
		LeaderActiveEffects.tickLeader(leader, now + 1_000L);
		LeaderActiveEffects.tickLeader(leader, now + 1_020L);
		boss.setDeltaMovement(Vec3.ZERO);
		LeaderActiveEffects.tickServer(level, now + 1_021L);
		check(failures, "GRAVITY Boss 只吃伤害不吃牵引",
				boss.getDeltaMovement().lengthSqr() < 1.0E-6D,
				"delta=" + boss.getDeltaMovement());
		normal.discard();
		boss.discard();
		leader.setTarget(null);
	}

	private static void checkCollapseLifecycle(List<String> failures, ServerPlayer player, Zombie leader,
			int baseX, int baseY, int baseZ) {
		ServerLevel level = player.serverLevel();
		leader.moveTo(baseX + 0.5D, baseY, baseZ + 0.5D, 0.0F, 0.0F);
		Zombie target = spawnZombie(player, baseX + 3.5D, baseY, baseZ + 0.5D);
		if (target == null) {
			failures.add("COLLAPSE 生命周期测试无法创建目标");
			return;
		}
		target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20_000.0D);
		target.setHealth(target.getMaxHealth());
		leader.setTarget(target);
		long now = level.getGameTime() + 70_000L;
		LeaderAbilityState.setAbilities(leader,
				List.of(LeaderAbilityId.BASTION_AURA, LeaderAbilityId.COLLAPSE_FIELD));
		LeaderActiveEffects.tickLeader(leader, now);
		int hits = 0;
		for (int i = 1; i <= 8; i++) {
			float before = target.getHealth();
			LeaderActiveEffects.tickLeader(leader, now + 20L * i);
			if (target.getHealth() < before) {
				hits++;
			}
		}
		check(failures, "COLLAPSE 生效瞬间首跳并完成八次周期伤害",
				hits == 8, "hits=" + hits + " health=" + target.getHealth());

		AttributeInstance speed = target.getAttribute(Attributes.MOVEMENT_SPEED);
		double baseSpeed = speed.getBaseValue();
		LeaderAbilityState.setAbilities(leader,
				List.of(LeaderAbilityId.BASTION_AURA, LeaderAbilityId.COLLAPSE_FIELD));
		LeaderActiveEffects.tickLeader(leader, now + 2_000L);
		LeaderActiveEffects.tickLeader(leader, now + 2_020L);
		check(failures, "COLLAPSE 场内移速乘 80%",
				Math.abs(speed.getValue() / baseSpeed - 0.80D) < 0.002D,
				"before=" + baseSpeed + " after=" + speed.getValue());
		LeaderActiveEffects.cleanupLeader(leader);
		check(failures, "COLLAPSE 首领死亡立即移除减速",
				Math.abs(speed.getValue() - baseSpeed) < 0.001D,
				"value=" + speed.getValue());
		target.discard();
		leader.setTarget(null);
	}

	private static void checkIronCanceledHits(List<String> failures, ServerPlayer player, Zombie leader) {
		Zombie target = spawnZombie(player,
				leader.getX() + 2.0D, leader.getY(), leader.getZ());
		if (target == null) {
			failures.add("IRON 取消计数测试无法创建目标");
			return;
		}
		leader.setTarget(target);
		LeaderAbilityState.setAbilities(leader,
				List.of(LeaderAbilityId.BASTION_AURA, LeaderAbilityId.IRON_GUARD));
		LeaderActiveEffects.tickLeader(leader, player.serverLevel().getGameTime() + 80_000L);
		for (int i = 0; i < 3; i++) {
			LivingDamageEvent canceled = new LivingDamageEvent(leader,
					leader.damageSources().mobAttack(target), 100.0F);
			canceled.setCanceled(true);
			LeaderActiveEffects.onLivingDamage(canceled);
		}
		check(failures, "IRON 已取消与零伤害事件不推进三次计数",
				LeaderActiveEffects.hasIronGuard(leader),
				"guard=" + LeaderActiveEffects.hasIronGuard(leader));
		LivingDamageEvent bypass = new LivingDamageEvent(leader,
				leader.damageSources().fellOutOfWorld(), 100.0F);
		LeaderActiveEffects.onLivingDamage(bypass);
		check(failures, "IRON 穿透伤害不降低但可计数",
				Math.abs(bypass.getAmount() - 100.0F) < 0.01F
						&& LeaderActiveEffects.hasIronGuard(leader),
				"amount=" + bypass.getAmount() + " guard="
						+ LeaderActiveEffects.hasIronGuard(leader));
		target.discard();
		LeaderActiveEffects.cleanupLeader(leader);
		leader.setTarget(null);
	}

	private static void checkLevelUnloadCleanup(List<String> failures, ServerPlayer player, Zombie leader) {
		ServerLevel level = player.serverLevel();
		Zombie ally = spawnZombie(player,
				leader.getX() + 2.0D, leader.getY(), leader.getZ());
		Zombie target = spawnZombie(player,
				leader.getX() + 3.0D, leader.getY(), leader.getZ());
		if (ally == null || target == null) {
			failures.add("LEVEL 清理测试无法创建测试实体");
			return;
		}
		leader.setTarget(target);
		double baseAttack = ally.getAttributeValue(Attributes.ATTACK_DAMAGE);
		long now = level.getGameTime() + 90_000L;
		LeaderAbilityState.setAbilities(leader,
				List.of(LeaderAbilityId.COMMAND_AURA, LeaderAbilityId.IRON_GUARD));
		LeaderAuraEffects.tickLeader(leader, now);
		LeaderActiveEffects.tickLeader(leader, now);
		check(failures, "LEVEL 卸载前光环与主动状态已建立",
				LeaderActiveEffects.hasIronGuard(leader)
						&& Math.abs(ally.getAttributeValue(Attributes.ATTACK_DAMAGE)
								/ baseAttack - 1.10D) < 0.002D,
				"guard=" + LeaderActiveEffects.hasIronGuard(leader)
						+ " attack=" + ally.getAttributeValue(Attributes.ATTACK_DAMAGE));
		LeaderAuraEffects.cleanupLevel(level);
		LeaderActiveEffects.cleanupLevel(level);
		check(failures, "LEVEL 卸载清理主动状态与光环属性",
				!LeaderActiveEffects.hasIronGuard(leader)
						&& Math.abs(ally.getAttributeValue(Attributes.ATTACK_DAMAGE) - baseAttack) < 0.001D,
				"guard=" + LeaderActiveEffects.hasIronGuard(leader)
						+ " attack=" + ally.getAttributeValue(Attributes.ATTACK_DAMAGE));
		ally.discard();
		target.discard();
		leader.setTarget(null);
	}

	private static Zombie spawnZombie(ServerPlayer player, double x, double y, double z) {
		Zombie zombie = EntityType.ZOMBIE.create(player.serverLevel());
		if (zombie == null) {
			return null;
		}
		zombie.moveTo(x, y, z, 0.0F, 0.0F);
		zombie.setNoAi(true);
		zombie.setPersistenceRequired();
		player.serverLevel().addFreshEntity(zombie);
		return zombie;
	}

	private static Wolf spawnPet(ServerPlayer player, double x, double y, double z) {
		Wolf wolf = EntityType.WOLF.create(player.serverLevel());
		if (wolf == null) {
			return null;
		}
		wolf.moveTo(x, y, z, 0.0F, 0.0F);
		wolf.setTame(true);
		wolf.setOwnerUUID(player.getUUID());
		wolf.setNoAi(true);
		wolf.setPersistenceRequired();
		player.serverLevel().addFreshEntity(wolf);
		return wolf;
	}

	private static Wolf spawnWolf(ServerPlayer player, double x, double y, double z) {
		Wolf wolf = EntityType.WOLF.create(player.serverLevel());
		if (wolf == null) {
			return null;
		}
		wolf.moveTo(x, y, z, 0.0F, 0.0F);
		wolf.setNoAi(true);
		wolf.setPersistenceRequired();
		player.serverLevel().addFreshEntity(wolf);
		return wolf;
	}

	private static void prepareLane(ServerLevel level, int baseX, int baseY, int baseZ, int length) {
		for (int dx = -2; dx <= length; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				level.setBlockAndUpdate(new BlockPos(baseX + dx, baseY - 1, baseZ + dz),
						Blocks.STONE.defaultBlockState());
				for (int dy = 0; dy <= 3; dy++) {
					level.setBlockAndUpdate(new BlockPos(baseX + dx, baseY + dy, baseZ + dz),
							Blocks.AIR.defaultBlockState());
				}
			}
		}
	}

	private static float captureSkillDamage(Zombie leader, LivingEntity target,
			LeaderAbilityId active, long startTick, long hitTick) {
		LeaderAbilityState.setAbilities(leader,
				List.of(LeaderAbilityId.BASTION_AURA, active));
		leader.setTarget(target);
		HurtCapture capture = new HurtCapture(List.of(target), true);
		MinecraftForge.EVENT_BUS.register(capture);
		try {
			LeaderActiveEffects.tickLeader(leader, startTick);
			LeaderActiveEffects.tickLeader(leader, hitTick);
		} finally {
			MinecraftForge.EVENT_BUS.unregister(capture);
		}
		return capture.amountFor(target);
	}

	private static float directSkillDamage(Zombie leader, LivingEntity target, double base) {
		try {
			Method method = LeaderActiveEffects.class.getDeclaredMethod(
					"skillDamage", LivingEntity.class, LivingEntity.class, double.class);
			method.setAccessible(true);
			return (float) method.invoke(null, leader, target, base);
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException("无法调用首领技能伤害入口", exception);
		}
	}

	private static final class HurtCapture {
		private final Set<UUID> targets = new HashSet<>();
		private final Map<UUID, Float> amounts = new HashMap<>();
		private final boolean cancel;

		private HurtCapture(List<? extends LivingEntity> targets, boolean cancel) {
			for (LivingEntity target : targets) {
				this.targets.add(target.getUUID());
			}
			this.cancel = cancel;
		}

		@SubscribeEvent(priority = EventPriority.HIGHEST)
		public void onLivingHurt(LivingHurtEvent event) {
			UUID id = event.getEntity().getUUID();
			if (!targets.contains(id)) {
				return;
			}
			amounts.put(id, event.getAmount());
			if (cancel) {
				event.setCanceled(true);
			}
		}

		private float amountFor(LivingEntity entity) {
			return amounts.getOrDefault(entity.getUUID(), Float.NaN);
		}
	}

	private static void check(List<String> failures, String label, boolean ok, String detail) {
		if (ok) {
			System.out.println("[LEADER] OK " + label);
		} else {
			String message = label + "：" + detail;
			failures.add(message);
			System.out.println("[LEADER] FAIL " + message);
		}
	}
}
