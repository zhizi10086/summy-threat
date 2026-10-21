package com.summy.threat.eximus;

import com.summy.threat.mixin.PathNavigationAccessor;
import com.summy.threat.scaling.MonsterScaler;
import com.summy.threat.scaling.ScalingState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 8 个卓越者专属原型的实际效果。
 *
 * <p>这里只处理“原型”层；TL 能力仍由 {@code AbilityEffects} 处理，卓越护盾由
 * {@link EximusShield} 处理。所有范围伤害统一走 {@link #isOffensiveTarget}：
 * 玩家、当前仇恨为卓越者的单位、以及背叛靶子。
 */
public final class EximusArchetypeEffects {
	private static final UUID STORM_SPEED = UUID.fromString("6f5c1a2e-0009-4a0b-9d1e-000000000401");
	private static final UUID STORM_STEP = UUID.fromString("6f5c1a2e-0009-4a0b-9d1e-000000000402");
	private static final UUID STORM_FOLLOW = UUID.fromString("6f5c1a2e-0009-4a0b-9d1e-000000000406");
	private static final UUID FROST_SLOW_SPEED = UUID.fromString("6f5c1a2e-0009-4a0b-9d1e-000000000403");
	private static final UUID FROST_SLOW_ATTACK = UUID.fromString("6f5c1a2e-0009-4a0b-9d1e-000000000404");
	private static final UUID CAST_ROOT = UUID.fromString("6f5c1a2e-0009-4a0b-9d1e-000000000405");
	private static final String BETRAYAL_TARGET = "summy_reliquary:betrayal_target";
	private static final String TAG_FROST_SHIELD = "summy_threat:frost_shield";
	private static final String TAG_FROST_SHIELD_MAX = "summy_threat:frost_shield_max";
	private static final String TAG_FROST_GRANTED = "summy_threat:frost_granted";
	/** 原型伤害以 TL20 的 A 表值 16.44 为基准。 */
	private static final double A_TL20 = 16.44D;
	private static final float STORM_WAYPOINT_TOLERANCE = 2.0F;
	private static final int STORM_STUCK_LIMIT = 10;
	private static final int STORM_STUCK_REST = 40;

	private static final Map<UUID, FlameCast> FLAME = new HashMap<>();
	private static final Map<UUID, Long> FLAME_READY = new HashMap<>();
	private static final Map<UUID, FrostState> FROST_STATE = new HashMap<>();
	private static final Map<UUID, Long> FROST_PENDING = new HashMap<>();
	private static final Map<UUID, FrostShield> FROST_SHIELDS = new HashMap<>();
	private static final Set<UUID> FROST_GRANTED = new HashSet<>();
	private static final Map<UUID, Long> FROST_SLOW = new HashMap<>();
	private static final Map<UUID, StormState> STORM = new HashMap<>();
	private static final Map<UUID, ShockState> SHOCK = new HashMap<>();
	private static final Map<UUID, Long> SHOCK_READY = new HashMap<>();
	private static final Map<UUID, List<Cloud>> CLOUDS = new HashMap<>();
	private static final Map<UUID, Long> NEXT_CLOUD = new HashMap<>();
	private static final Map<UUID, Long> CLOUD_HIT = new HashMap<>();
	private static final Map<UUID, Long> CLOUD_HEAL = new HashMap<>();
	private static final Set<UUID> CREEPER_FUSING = new HashSet<>();
	/** 当前被前摇定身的实体，避免对全部非卓越者每 tick 做属性查询。 */
	private static final Set<UUID> ROOTED = new HashSet<>();

	private EximusArchetypeEffects() {
	}

	@SubscribeEvent
	public static void onLivingTick(LivingEvent.LivingTickEvent event) {
		LivingEntity entity = event.getEntity();
		if (entity.level().isClientSide()) {
			return;
		}
		if (!EximusShield.isEximus(entity)) {
			// 标签被清空/破盾后不再推进原型，同时兜底移除前摇定身。
			if (STORM.containsKey(entity.getUUID())) {
				resetStorm(entity);
			} else if (ROOTED.contains(entity.getUUID())) {
				root(entity, false);
			}
			return;
		}
		EximusArchetype archetype = archetype(entity);
		if (archetype == null) {
			resetStorm(entity);
			return;
		}
		if (entity.tickCount % 10 == 0) {
			ambient(entity, archetype);
		}
		long now = entity.level().getGameTime();
		switch (archetype) {
			case FLAME -> tickFlame(entity, now);
			case FROST -> tickFrost(entity, now);
			case POISON -> tickPoison(entity);
			case STORM -> tickStorm(entity, now);
			case SHOCK -> tickShock(entity, now);
			case GUARDIAN -> {
				// 守护只通过伤害转移工作，不需要每 tick 推进状态。
			}
			case VAMPIRE, MAGNET -> tickClouds(entity, archetype, now);
		}
		if (entity.tickCount % 20 == 0) {
			cleanupFrostSlow(now);
		}
	}

	private static EximusArchetype archetype(LivingEntity entity) {
		return EximusArchetype.byId(entity.getPersistentData().getString(MonsterScaler.TAG_ARCHETYPE));
	}

	private static boolean isBetrayalTarget(LivingEntity entity) {
		return entity.getPersistentData().getLong(BETRAYAL_TARGET) > entity.level().getGameTime();
	}

	private static LivingEntity currentTarget(LivingEntity owner) {
		return owner instanceof Mob mob ? mob.getTarget() : null;
	}

	private static boolean isOffensiveTarget(LivingEntity owner, LivingEntity target) {
		if (target == owner || target.isDeadOrDying()) {
			return false;
		}
		if (target instanceof Player player) {
			return !player.isCreative() && !player.isSpectator();
		}
		// 卓越者自己正在追的目标始终是有效攻击目标（如僵尸追村民）。
		if (owner instanceof Mob ownerMob && ownerMob.getTarget() == target) {
			return true;
		}
		return target instanceof Mob mob && (mob.getTarget() == owner || isBetrayalTarget(mob));
	}

	private static boolean isSupportTarget(LivingEntity owner, LivingEntity target) {
		// 只把敌对生物视为“怪物友军”；村民、铁傀儡等非 Enemy 不再吃冰盾/守护。
		return target != owner && !target.isDeadOrDying()
				&& target instanceof Enemy
				&& target != currentTarget(owner)
				&& !isBetrayalTarget(target);
	}

	/** 玩家视角友军：玩家本人、玩家拥有/驯服单位、与任一在线玩家同队。 */
	private static boolean isPlayerAlly(LivingEntity target) {
		if (target instanceof Player) {
			return true;
		}
		if (target instanceof OwnableEntity ownable && ownable.getOwner() instanceof Player) {
			return true;
		}
		if (target.level() instanceof ServerLevel level) {
			for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
				if (target.isAlliedTo(player)) {
					return true;
				}
			}
		}
		return false;
	}

	private static List<LivingEntity> targets(LivingEntity owner, double radius, boolean offensive) {
		List<LivingEntity> out = new ArrayList<>();
		if (!(owner.level() instanceof ServerLevel level)) {
			return out;
		}
		AABB box = owner.getBoundingBox().inflate(radius);
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box)) {
			if (target.distanceTo(owner) > radius) {
				continue;
			}
			if (offensive ? isOffensiveTarget(owner, target) : isSupportTarget(owner, target)) {
				out.add(target);
			}
		}
		return out;
	}

	/**
	 * 多目标索敌统一规则：玩家优先，其次按距离；每个目标最多一个实例。
	 * 少于 max 个时由调用方用随机落点补位。
	 */
	private static List<LivingEntity> selectUniqueTargets(LivingEntity owner, double radius, int max) {
		if (max <= 0) {
			return List.of();
		}
		List<LivingEntity> candidates = targets(owner, radius, true);
		candidates.sort((a, b) -> {
			boolean ap = a instanceof Player;
			boolean bp = b instanceof Player;
			if (ap != bp) {
				return ap ? -1 : 1;
			}
			return Double.compare(a.distanceToSqr(owner), b.distanceToSqr(owner));
		});
		List<LivingEntity> out = new ArrayList<>();
		Set<UUID> seen = new HashSet<>();
		for (LivingEntity candidate : candidates) {
			if (seen.add(candidate.getUUID())) {
				out.add(candidate);
			}
			if (out.size() >= max) {
				break;
			}
		}
		return out;
	}

	private static void hurt(LivingEntity target, net.minecraft.world.damagesource.DamageSource source, float amount) {
		target.invulnerableTime = 0;
		target.hurt(source, amount);
	}

	private static Vec3 flatLook(LivingEntity entity) {
		Vec3 look = entity.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0.0D, look.z);
		return flat.lengthSqr() < 1.0E-5D ? new Vec3(0.0D, 0.0D, 1.0D) : flat.normalize();
	}

	/** 震荡瞄准：优先当前目标的水平方向，没有目标才回退视线。 */
	private static Vec3 aimDirection(LivingEntity owner) {
		LivingEntity target = currentTarget(owner);
		if (target != null) {
			Vec3 diff = target.position().subtract(owner.position());
			Vec3 flat = new Vec3(diff.x, 0.0D, diff.z);
			if (flat.lengthSqr() > 1.0E-4D) {
				return flat.normalize();
			}
		}
		return flatLook(owner);
	}

	/** 原型技能的伤害口径：玩家/友军 1×，其他非玩家 10×。 */
	private static float skillDamage(LivingEntity owner, LivingEntity target, double d20) {
		int tl = ScalingState.clampTl(owner.getPersistentData().getInt(MonsterScaler.TAG_TL));
		double a = ScalingState.rawAttackMultiplier(tl);
		double factor = isPlayerAlly(target) ? 1.0D : 10.0D;
		return (float) (d20 * a / A_TL20 * ScalingState.rAtk() * factor);
	}

	/** 前摇/锁定期间把移速乘数压到 0；结束后恢复原版移速。 */
	private static void root(LivingEntity entity, boolean rooted) {
		if (rooted) {
			ROOTED.add(entity.getUUID());
		} else {
			ROOTED.remove(entity.getUUID());
		}
		AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed == null) {
			return;
		}
		boolean present = speed.getModifier(CAST_ROOT) != null;
		if (rooted && !present) {
			speed.addTransientModifier(new AttributeModifier(CAST_ROOT, "summy_threat:archetype_cast_root",
					-1.0D, AttributeModifier.Operation.MULTIPLY_TOTAL));
		} else if (!rooted && present) {
			speed.removeModifier(CAST_ROOT);
		}
	}

	/** 电涌的 48 格仇恨需要同步抬高原版跟随范围，否则寻路只认到默认 35 格。 */
	private static void applyStormFollowRange(LivingEntity owner) {
		AttributeInstance follow = owner.getAttribute(Attributes.FOLLOW_RANGE);
		if (follow == null) {
			return;
		}
		if (follow.getModifier(STORM_FOLLOW) != null && follow.getValue() >= 47.999D) {
			return;
		}
		follow.removeModifier(STORM_FOLLOW);
		double addition = Math.max(0.0D, 48.0D - follow.getValue());
		if (addition > 0.0D) {
			follow.addTransientModifier(new AttributeModifier(STORM_FOLLOW,
					"summy_threat:storm_follow_range", addition, AttributeModifier.Operation.ADDITION));
		}
	}

	private static void removeStormFollowRange(LivingEntity owner) {
		AttributeInstance follow = owner.getAttribute(Attributes.FOLLOW_RANGE);
		if (follow != null) {
			follow.removeModifier(STORM_FOLLOW);
		}
	}

	/** 冲刺期间放大寻路节点容差，结束后恢复原值。 */
	private static void applyStormWaypoint(LivingEntity owner, StormState state, boolean sprinting) {
		if (!(owner instanceof Mob mob) || !(mob.getNavigation() instanceof PathNavigationAccessor accessor)) {
			return;
		}
		if (sprinting) {
			if (state.previousWaypoint < 0.0F) {
				state.previousWaypoint = mob.getNavigation().getMaxDistanceToWaypoint();
			}
			accessor.summyThreat$setMaxDistanceToWaypoint(STORM_WAYPOINT_TOLERANCE);
		} else if (state.previousWaypoint >= 0.0F) {
			accessor.summyThreat$setMaxDistanceToWaypoint(state.previousWaypoint);
			state.previousWaypoint = -1.0F;
		}
	}

	/** 原型清除/死亡时撤销电涌的临时速度、台阶、跟随范围与节点容差。 */
	private static void resetStorm(LivingEntity owner) {
		StormState state = STORM.remove(owner.getUUID());
		if (state != null) {
			applyStormWaypoint(owner, state, false);
		}
		if (owner instanceof Mob mob) {
			mob.getNavigation().stop();
		}
		applyStormSpeed(owner, false);
		removeStormFollowRange(owner);
		root(owner, false);
	}

	private static double horizontalDistanceSqr(LivingEntity owner, StormState state) {
		double dx = owner.getX() - state.lastX;
		double dz = owner.getZ() - state.lastZ;
		return dx * dx + dz * dz;
	}

	private static void groundRing(LivingEntity owner, double radius, Vector3f color) {
		if (!(owner.level() instanceof ServerLevel level)) {
			return;
		}
		int count = Math.max(16, (int) (radius * 4.0D));
		DustParticleOptions dust = new DustParticleOptions(color, 0.9F);
		for (int i = 0; i < count; i++) {
			double angle = Math.PI * 2.0D * i / count;
			double x = owner.getX() + Math.cos(angle) * radius;
			double z = owner.getZ() + Math.sin(angle) * radius;
			level.sendParticles(dust, x, owner.getY() + 0.08D, z, 1, 0.02D, 0.0D, 0.02D, 0.0D);
		}
	}

	// ==================== 烈焰 ====================

	private static void tickFlame(LivingEntity owner, long now) {
		FlameCast cast = FLAME.get(owner.getUUID());
		if (cast == null) {
			if (now >= FLAME_READY.getOrDefault(owner.getUUID(), 0L) && currentTarget(owner) != null) {
				FLAME.put(owner.getUUID(), new FlameCast(now));
				FLAME_READY.put(owner.getUUID(), now + 180L);
				if (owner.level() instanceof ServerLevel level) {
					level.playSound(null, owner.getX(), owner.getY(), owner.getZ(),
							SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 0.8F, 0.8F);
				}
			}
			return;
		}
		root(owner, true);
		long elapsed = now - cast.start;
		if (elapsed < 20L) {
			ringParticles(owner, 0.8D, ParticleTypes.FLAME);
			return;
		}
		if (elapsed <= 50L) {
			double radius = 9.0D * (elapsed - 20L) / 30.0D;
			ringParticles(owner, radius, ParticleTypes.FLAME);
			for (LivingEntity target : targets(owner, 9.0D, true)) {
				double distance = target.distanceTo(owner);
				if (distance < radius - 0.75D || distance > radius + 0.75D
						|| !cast.hit.add(target.getUUID())) {
					continue;
				}
				hurt(target, owner.damageSources().mobAttack(owner), skillDamage(owner, target, 45.0D));
				target.setSecondsOnFire(3);
				Vec3 away = target.position().subtract(owner.position());
				if (away.lengthSqr() > 1.0E-4D) {
					Vec3 dir = away.normalize();
					target.push(dir.x * 1.2D, 0.3D, dir.z * 1.2D);
					target.hurtMarked = true;
				}
			}
			if ((elapsed & 1L) == 0L) {
				ringParticles(owner, radius, ParticleTypes.SMOKE);
			}
			return;
		}
		FLAME.remove(owner.getUUID());
		root(owner, false);
	}

	private static void ringParticles(LivingEntity owner, double radius, net.minecraft.core.particles.SimpleParticleType particle) {
		if (!(owner.level() instanceof ServerLevel level)) {
			return;
		}
		int count = Math.max(8, (int) (radius * 3.0D));
		for (int i = 0; i < count; i++) {
			double angle = Math.PI * 2.0D * i / count;
			double x = owner.getX() + Math.cos(angle) * radius;
			double z = owner.getZ() + Math.sin(angle) * radius;
			level.sendParticles(particle, x, owner.getY() + 0.25D, z, 1, 0.05D, 0.05D, 0.05D, 0.0D);
		}
	}

	// ==================== 冰霜 ====================

	private static void tickFrost(LivingEntity owner, long now) {
		FrostState state = FROST_STATE.computeIfAbsent(owner.getUUID(), id -> new FrostState(now));
		long elapsed = now - state.start;
		if (elapsed < 20L) {
			// 初始 1 秒冰环前摇：停止移动，环状雪花预警。
			root(owner, true);
			if (owner.tickCount % 4 == 0) {
				ringParticles(owner, 1.0D + elapsed * 0.04D, ParticleTypes.SNOWFLAKE);
			}
			return;
		}
		root(owner, false);
		// 护盾：新友军进入 8 格后 0.8 秒获得一次；已有冰盾不移除、不刷新。
		if (owner.tickCount % 10 == 0) {
			List<LivingEntity> candidates = new ArrayList<>();
			candidates.add(owner);
			candidates.addAll(targets(owner, 8.0D, false));
			Set<UUID> nearby = new HashSet<>();
			for (LivingEntity target : candidates) {
				nearby.add(target.getUUID());
				if (frostGranted(target)) {
					FROST_GRANTED.add(target.getUUID());
					FROST_PENDING.remove(target.getUUID());
					continue;
				}
				Long due = FROST_PENDING.get(target.getUUID());
				if (due == null) {
					FROST_PENDING.put(target.getUUID(), now + 16L);
					if (owner.level() instanceof ServerLevel level) {
						level.sendParticles(ParticleTypes.SNOWFLAKE, target.getX(),
								target.getY() + target.getBbHeight() * 0.5D, target.getZ(),
								4, 0.6D, 0.4D, 0.6D, 0.01D);
					}
					continue;
				}
				if (now < due) {
					continue;
				}
				grantFrostShield(target);
				FROST_PENDING.remove(target.getUUID());
			}
			FROST_PENDING.keySet().removeIf(id -> !nearby.contains(id));
		}
		// 减速光环属于攻击效果，背叛靶子同样会被减速。
		if (owner.tickCount % 10 == 0) {
			for (LivingEntity target : targets(owner, 3.0D, true)) {
				applyFrostSlow(target, now + 20L);
			}
		}
		if (owner.tickCount % 10 == 0) {
			groundRing(owner, 3.0D, new Vector3f(0.61F, 0.91F, 1.0F));
		}
	}

	private static boolean frostGranted(LivingEntity target) {
		return FROST_GRANTED.contains(target.getUUID())
				|| target.getPersistentData().getBoolean(TAG_FROST_GRANTED);
	}

	private static void grantFrostShield(LivingEntity target) {
		double max = target.getMaxHealth() * 0.25D;
		target.getPersistentData().putDouble(TAG_FROST_SHIELD_MAX, max);
		target.getPersistentData().putDouble(TAG_FROST_SHIELD, max);
		target.getPersistentData().putBoolean(TAG_FROST_GRANTED, true);
		FROST_SHIELDS.put(target.getUUID(), new FrostShield(max));
		FROST_GRANTED.add(target.getUUID());
		ringParticles(target, 0.8D, ParticleTypes.SNOWFLAKE);
	}

	private static FrostShield frostShieldOf(LivingEntity target) {
		FrostShield shield = FROST_SHIELDS.get(target.getUUID());
		if (shield != null) {
			return shield;
		}
		CompoundTag data = target.getPersistentData();
		double max = Math.max(0.0D, data.getDouble(TAG_FROST_SHIELD_MAX));
		double current = Math.max(0.0D, data.getDouble(TAG_FROST_SHIELD));
		if (max <= 0.0D && current <= 0.0D) {
			return null;
		}
		shield = new FrostShield(max > 0.0D ? Math.min(current, max) : current);
		FROST_SHIELDS.put(target.getUUID(), shield);
		return shield;
	}

	private static void breakFrostShield(LivingEntity target) {
		FROST_SHIELDS.remove(target.getUUID());
		target.getPersistentData().remove(TAG_FROST_SHIELD);
		target.getPersistentData().remove(TAG_FROST_SHIELD_MAX);
		if (target.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.SNOWFLAKE, target.getX(),
					target.getY() + target.getBbHeight() * 0.5D, target.getZ(),
					16, 1.0D, 0.6D, 1.0D, 0.02D);
		}
	}

	private static void applyFrostSlow(LivingEntity target, long until) {
		AttributeInstance speed = target.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.removeModifier(FROST_SLOW_SPEED);
			speed.addTransientModifier(new AttributeModifier(FROST_SLOW_SPEED, "summy_threat:frost_slow_speed",
					-0.50D, AttributeModifier.Operation.MULTIPLY_TOTAL));
		}
		AttributeInstance attack = target.getAttribute(Attributes.ATTACK_SPEED);
		if (attack != null) {
			attack.removeModifier(FROST_SLOW_ATTACK);
			attack.addTransientModifier(new AttributeModifier(FROST_SLOW_ATTACK, "summy_threat:frost_slow_attack",
					-0.50D, AttributeModifier.Operation.MULTIPLY_TOTAL));
		}
		FROST_SLOW.put(target.getUUID(), until);
	}

	private static void cleanupFrostSlow(long now) {
		Iterator<Map.Entry<UUID, Long>> iterator = FROST_SLOW.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, Long> entry = iterator.next();
			if (entry.getValue() > now) {
				continue;
			}
			Entity entity = null;
			var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
			if (server != null) {
				for (ServerLevel level : server.getAllLevels()) {
					entity = level.getEntity(entry.getKey());
					if (entity != null) {
						break;
					}
				}
			}
			if (entity instanceof LivingEntity living) {
				AttributeInstance speed = living.getAttribute(Attributes.MOVEMENT_SPEED);
				AttributeInstance attack = living.getAttribute(Attributes.ATTACK_SPEED);
				if (speed != null) {
					speed.removeModifier(FROST_SLOW_SPEED);
				}
				if (attack != null) {
					attack.removeModifier(FROST_SLOW_ATTACK);
				}
			}
			iterator.remove();
		}
	}

	// ==================== 剧毒 ====================

	private static void tickPoison(LivingEntity owner) {
		if (owner.tickCount % 20 != 0 || !(owner.level() instanceof ServerLevel level)) {
			return;
		}
		for (LivingEntity target : targets(owner, 3.0D, true)) {
			hurt(target, owner.damageSources().indirectMagic(owner, owner), skillDamage(owner, target, 5.0D));
			level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, target.getX(),
					target.getY() + target.getBbHeight() * 0.5D, target.getZ(), 4, 0.4D, 0.4D, 0.4D, 0.01D);
		}
	}

	// ==================== 电涌 ====================

	private static void tickStorm(LivingEntity owner, long now) {
		StormState state = STORM.computeIfAbsent(owner.getUUID(), id -> new StormState());
		applyStormFollowRange(owner);
		// 电涌的仇恨范围固定 48 格；没有目标时主动锁定最近的玩家。
		if (currentTarget(owner) == null && owner instanceof Mob mob) {
			Player nearest = owner.level().getNearestPlayer(owner, 48.0D);
			if (nearest != null && !nearest.isCreative() && !nearest.isSpectator()) {
				mob.setTarget(nearest);
			}
		}
		boolean paused = owner instanceof AbstractSkeleton skeleton
				&& skeleton.isUsingItem() && skeleton.getUseItem().is(Items.BOW);
		boolean hasTarget = currentTarget(owner) != null;

		// 只做直线接近：冲刺 2 秒、休息 4 秒；拉弓期间暂停整套循环计时。
		if (!paused) {
			if (state.sprint > 0) {
				state.sprint--;
				if (state.sprint == 0) {
					state.rest = hasTarget ? 80 : 0;
				}
			} else if (hasTarget && state.rest > 0) {
				state.rest--;
			} else if (hasTarget && state.castStart < 0L) {
				state.sprint = 40;
				state.stuckTicks = 0;
				state.lastX = owner.getX();
				state.lastZ = owner.getZ();
				stormStartFx(owner);
			}
		}

		boolean sprinting = state.sprint > 0 && !paused;
		if (sprinting) {
			// 4× 速度下原版节点容差太小：连续 10 tick 卡住/几乎不位移就中止本次冲刺，
			// 让 AI 在 2 秒休息期重新寻路，避免原地绕圈。
			boolean stuck = owner instanceof Mob mob && mob.getNavigation().isStuck()
					|| (state.stuckTicks > 0 && horizontalDistanceSqr(owner, state) < 0.0025D);
			state.stuckTicks = stuck ? state.stuckTicks + 1 : 0;
			state.lastX = owner.getX();
			state.lastZ = owner.getZ();
			if (state.stuckTicks >= STORM_STUCK_LIMIT) {
				if (owner instanceof Mob mob) {
					mob.getNavigation().stop();
				}
				state.sprint = 0;
				state.rest = STORM_STUCK_REST;
				state.stuckTicks = 0;
				sprinting = false;
			}
		}
		applyStormSpeed(owner, sprinting);
		applyStormWaypoint(owner, state, sprinting);
		if (sprinting && owner.level() instanceof ServerLevel level && owner.tickCount % 4 == 0) {
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, owner.getX(), owner.getY() + 0.3D, owner.getZ(),
					2, 0.3D, 0.2D, 0.3D, 0.01D);
		}
		tickCreeperFuse(owner);

		// 前 3 秒追踪与预警同步：落点跟随目标，施法者不根定。
		if (state.castStart < 0L && !sprinting && hasTarget && now >= state.nextCast) {
			if (owner.level() instanceof ServerLevel level) {
				List<LockSlot> slots = new ArrayList<>();
				for (LivingEntity target : selectUniqueTargets(owner, 12.0D, 2)) {
					slots.add(new LockSlot(target.getUUID(), groundPointAt(level, target)));
				}
				while (slots.size() < 2) {
					slots.add(new LockSlot(null, groundPoint(level, owner, 12.0D)));
				}
				state.lockSlots = slots;
				state.castStart = now;
				state.nextCast = now + 160L;
				state.stuckTicks = 0;
			}
		}
		if (state.castStart >= 0L) {
			long elapsed = now - state.castStart;
			if (elapsed < 60L) {
				if (owner.level() instanceof ServerLevel level) {
					updateStormTracking(owner, state, level);
					for (LockSlot slot : state.lockSlots) {
						level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
								slot.point.x, slot.point.y + 0.2D, slot.point.z,
								8, 0.8D, 0.2D, 0.8D, 0.01D);
					}
				}
				return;
			}
			if (state.points.isEmpty()) {
				state.points = state.lockSlots.stream().map(slot -> slot.point).toList();
			}
			for (Vec3 point : state.points) {
				if (elapsed < 80L && owner.level() instanceof ServerLevel level) {
					// 1 秒锁定期：落点固定，用收缩的电弧圈提示玩家离开 2.5 格范围。
					double radius = 2.5D * (1.0D - (elapsed - 60L) / 20.0D) + 0.2D;
					DustParticleOptions dust = new DustParticleOptions(new Vector3f(1.0F, 0.88F, 0.4F), 0.9F);
					for (int i = 0; i < 16; i++) {
						double angle = Math.PI * 2.0D * i / 16.0D;
						level.sendParticles(dust, point.x + Math.cos(angle) * radius, point.y + 0.15D,
								point.z + Math.sin(angle) * radius, 1, 0.02D, 0.0D, 0.02D, 0.0D);
					}
				} else if (!state.struck && elapsed >= 80L) {
					strikeLightning(owner, point);
				}
			}
			if (elapsed >= 80L) {
				state.struck = true;
			}
			if (elapsed > 85L) {
				state.castStart = -1L;
				state.struck = false;
				state.points = List.of();
			}
		}
	}

	private static void stormStartFx(LivingEntity owner) {
		if (!(owner.level() instanceof ServerLevel level)) {
			return;
		}
		level.playSound(null, owner.getX(), owner.getY(), owner.getZ(),
				SoundEvents.TRIDENT_RIPTIDE_1, SoundSource.HOSTILE, 0.7F, 1.2F);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, owner.getX(), owner.getY() + 0.4D, owner.getZ(),
				10, 0.5D, 0.3D, 0.5D, 0.02D);
		level.sendParticles(ParticleTypes.CLOUD, owner.getX(), owner.getY() + 0.2D, owner.getZ(),
				6, 0.5D, 0.2D, 0.5D, 0.02D);
	}

	/** 追踪段：目标仍有效就跟随其脚下；失效的槽位保留最后位置。 */
	private static void updateStormTracking(LivingEntity owner, StormState state, ServerLevel level) {
		for (LockSlot slot : state.lockSlots) {
			if (slot.targetId == null || slot.fixed) {
				continue;
			}
			Entity entity = level.getEntity(slot.targetId);
			if (entity instanceof LivingEntity target && target.isAlive()
					&& target.distanceTo(owner) <= 12.0D) {
				slot.point = groundPointAt(level, target);
			} else {
				slot.fixed = true;
			}
		}
	}

	/** 苦力怕引线保持原版 30 tick；引线开始时补一圈电弧与原版嘶声。 */
	private static void tickCreeperFuse(LivingEntity owner) {
		if (!(owner instanceof Creeper creeper)) {
			return;
		}
		boolean fusing = creeper.getSwellDir() > 0;
		if (fusing && CREEPER_FUSING.add(owner.getUUID())) {
			ringParticles(owner, 1.4D, ParticleTypes.ELECTRIC_SPARK);
			if (owner.level() instanceof ServerLevel level) {
				level.playSound(null, owner.getX(), owner.getY(), owner.getZ(),
						SoundEvents.CREEPER_PRIMED, SoundSource.HOSTILE, 0.8F, 1.0F);
			}
		} else if (!fusing) {
			CREEPER_FUSING.remove(owner.getUUID());
		}
	}

	private static void strikeLightning(LivingEntity owner, Vec3 point) {
		if (!(owner.level() instanceof ServerLevel level)) {
			return;
		}
		var bolt = EntityType.LIGHTNING_BOLT.create(level);
		if (bolt != null) {
			bolt.moveTo(point.x, point.y, point.z);
			bolt.setVisualOnly(true);
			level.addFreshEntity(bolt);
		}
		for (LivingEntity target : allTargetsAtExcept(owner, point, 2.5D)) {
			hurt(target, owner.damageSources().indirectMagic(owner, owner),
					skillDamage(owner, target, 30.0D));
		}
	}

	/** 电涌落雷：除施法者外的所有活体，友军/敌人/中立一视同仁。 */
	private static List<LivingEntity> allTargetsAtExcept(LivingEntity owner, Vec3 point, double radius) {
		List<LivingEntity> out = new ArrayList<>();
		if (!(owner.level() instanceof ServerLevel level)) {
			return out;
		}
		AABB box = new AABB(point, point).inflate(radius);
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box)) {
			if (target == owner || target.isDeadOrDying()) {
				continue;
			}
			if (target instanceof Player player && (player.isCreative() || player.isSpectator())) {
				continue;
			}
			if (target.distanceToSqr(point) <= radius * radius) {
				out.add(target);
			}
		}
		return out;
	}

	private static List<LivingEntity> targetsAt(LivingEntity owner, Vec3 point, double radius) {
		List<LivingEntity> out = new ArrayList<>();
		if (!(owner.level() instanceof ServerLevel level)) {
			return out;
		}
		AABB box = new AABB(point, point).inflate(radius);
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box)) {
			if (target.distanceToSqr(point) <= radius * radius && isOffensiveTarget(owner, target)) {
				out.add(target);
			}
		}
		return out;
	}

	private static void applyStormSpeed(LivingEntity owner, boolean sprinting) {
		AttributeInstance speed = owner.getAttribute(Attributes.MOVEMENT_SPEED);
		// 1.20.1 原版没有 STEP_HEIGHT 属性，Forge 用 STEP_HEIGHT_ADDITION 叠在 0.6 基础值上。
		AttributeInstance step = owner.getAttribute(ForgeMod.STEP_HEIGHT_ADDITION.get());
		if (speed != null) {
			boolean present = speed.getModifier(STORM_SPEED) != null;
			if (sprinting && !present) {
				speed.addTransientModifier(new AttributeModifier(STORM_SPEED, "summy_threat:storm_sprint",
						3.0D, AttributeModifier.Operation.MULTIPLY_TOTAL));
			} else if (!sprinting && present) {
				speed.removeModifier(STORM_SPEED);
			}
		}
		if (step != null) {
			boolean present = step.getModifier(STORM_STEP) != null;
			if (sprinting && !present) {
				// 0.6 + 0.5 = 1.1，给 1 格方块留出余量；退出冲刺立即恢复原版。
				step.addTransientModifier(new AttributeModifier(STORM_STEP, "summy_threat:storm_step",
						0.5D, AttributeModifier.Operation.ADDITION));
			} else if (!sprinting && present) {
				step.removeModifier(STORM_STEP);
			}
		}
	}

	// ==================== 震荡 ====================

	private static void tickShock(LivingEntity owner, long now) {
		ShockState state = SHOCK.get(owner.getUUID());
		if (state == null) {
			if (currentTarget(owner) != null && now >= SHOCK_READY.getOrDefault(owner.getUUID(), 0L)) {
				state = new ShockState(now, aimDirection(owner));
				SHOCK.put(owner.getUUID(), state);
				SHOCK_READY.put(owner.getUUID(), now + 160L);
				if (owner.level() instanceof ServerLevel level) {
					level.playSound(null, owner.getX(), owner.getY(), owner.getZ(),
							SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 0.6F, 1.3F);
				}
			}
			return;
		}
		root(owner, true);
		long elapsed = now - state.start;
		if (elapsed < 20L) {
			// 1.0 秒直线预警：沿固定朝向铺一条粒子线。
			if (owner.level() instanceof ServerLevel level) {
				DustParticleOptions dust = new DustParticleOptions(new Vector3f(1.0F, 0.69F, 0.0F), 0.9F);
				for (int i = 0; i <= 24; i++) {
					Vec3 point = owner.position().add(state.direction.scale(12.0D * i / 24.0D));
					level.sendParticles(dust, point.x, owner.getY() + 0.1D, point.z, 1,
							0.02D, 0.0D, 0.02D, 0.0D);
					if (i % 4 == 0) {
						level.sendParticles(ParticleTypes.ELECTRIC_SPARK, point.x, owner.getY() + 0.2D, point.z,
								1, 0.05D, 0.02D, 0.05D, 0.01D);
					}
					if (i % 6 == 0) {
						level.sendParticles(ParticleTypes.CRIT, point.x, owner.getY() + 0.15D, point.z,
								1, 0.05D, 0.02D, 0.05D, 0.01D);
					}
				}
			}
			return;
		}
		for (int i = 0; i < 8; i++) {
			long columnStart = 20L + i * 4L;
			if (elapsed < columnStart || elapsed > columnStart + 20L) {
				continue;
			}
			double distance = 1.5D * (i + 1);
			Vec3 point = owner.position().add(state.direction.scale(distance));
			if (elapsed < columnStart + 8L) {
				if (owner.level() instanceof ServerLevel level) {
					shockColumnWarning(level, point, (elapsed - columnStart) / 8.0D);
				}
				continue;
			}
			if (owner.level() instanceof ServerLevel level) {
				if (state.burst.add(i)) {
					shockBurst(level, point);
				}
				// 完整柱状粒子只在激活首 2 tick 发射；0.6 秒判定窗口内不再持续堆叠。
				if (elapsed < columnStart + 10L) {
					shockColumnActive(level, owner, point);
				}
			}
			for (LivingEntity target : targetsAt(owner, point, 1.1D)) {
				if (!state.hit.add(target.getUUID() + "#" + i)) {
					continue;
				}
			hurt(target, owner.damageSources().mobAttack(owner), skillDamage(owner, target, 20.0D));
				Vec3 away = target.position().subtract(owner.position());
				if (away.lengthSqr() > 1.0E-4D) {
					Vec3 dir = away.normalize();
					target.push(dir.x, 0.25D, dir.z);
					target.hurtMarked = true;
				}
			}
		}
		if (elapsed > 20L + 8L * 4L + 20L) {
			SHOCK.remove(owner.getUUID());
			root(owner, false);
		}
	}

	/** 冲击柱 0.4 秒预警：地面扩张环 + 垂直白色预警光束。 */
	private static void shockColumnWarning(ServerLevel level, Vec3 point, double progress) {
		DustParticleOptions amber = new DustParticleOptions(new Vector3f(1.0F, 0.69F, 0.0F), 1.0F);
		double radius = 0.3D + 0.8D * Math.max(0.0D, Math.min(1.0D, progress));
		for (int i = 0; i < 16; i++) {
			double angle = Math.PI * 2.0D * i / 16.0D;
			level.sendParticles(amber, point.x + Math.cos(angle) * radius, point.y + 0.08D,
					point.z + Math.sin(angle) * radius, 1, 0.02D, 0.0D, 0.02D, 0.0D);
		}
		for (double y = 0.2D; y <= 2.0D; y += 0.4D) {
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, point.x, point.y + y, point.z,
					1, 0.04D, 0.0D, 0.04D, 0.0D);
			if (progress > 0.75D) {
				level.sendParticles(ParticleTypes.END_ROD, point.x, point.y + y, point.z,
						1, 0.03D, 0.0D, 0.03D, 0.0D);
			}
		}
		level.sendParticles(ParticleTypes.CRIT, point.x, point.y + 0.2D, point.z,
				3, 0.25D, 0.1D, 0.25D, 0.01D);
	}

	/** 冲击柱激活：琥珀尘柱 + 白色核心 + 电弧 + 石块碎屑。 */
	private static void shockColumnActive(ServerLevel level, LivingEntity owner, Vec3 point) {
		DustParticleOptions amber = new DustParticleOptions(new Vector3f(1.0F, 0.69F, 0.0F), 1.0F);
		for (double y = 0.2D; y <= 2.6D; y += 0.3D) {
			level.sendParticles(amber, point.x, point.y + y, point.z,
					1, 0.12D, 0.0D, 0.12D, 0.0D);
			level.sendParticles(ParticleTypes.END_ROD, point.x, point.y + y, point.z,
					1, 0.06D, 0.0D, 0.06D, 0.0D);
			if (owner.tickCount % 2 == 0) {
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, point.x, point.y + y, point.z,
						1, 0.1D, 0.0D, 0.1D, 0.01D);
			}
		}
		level.sendParticles(ParticleTypes.CRIT, point.x, point.y + 0.3D, point.z,
				6, 0.6D, 0.2D, 0.6D, 0.02D);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
				point.x, point.y + 0.1D, point.z, 6, 0.6D, 0.1D, 0.6D, 0.01D);
	}

	/** 每根柱首次激活时播放一次冲击波环与低沉爆炸音。 */
	private static void shockBurst(ServerLevel level, Vec3 point) {
		DustParticleOptions hot = new DustParticleOptions(new Vector3f(1.0F, 0.95F, 0.69F), 1.0F);
		for (int i = 0; i < 20; i++) {
			double angle = Math.PI * 2.0D * i / 20.0D;
			level.sendParticles(hot, point.x + Math.cos(angle) * 1.5D, point.y + 0.12D,
					point.z + Math.sin(angle) * 1.5D, 1, 0.02D, 0.0D, 0.02D, 0.0D);
		}
		level.sendParticles(ParticleTypes.CRIT, point.x, point.y + 0.35D, point.z,
				12, 0.8D, 0.2D, 0.8D, 0.02D);
		level.sendParticles(ParticleTypes.CLOUD, point.x, point.y + 0.15D, point.z,
				6, 0.7D, 0.1D, 0.7D, 0.02D);
		level.playSound(null, point.x, point.y, point.z,
				SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 0.35F, 1.5F);
	}

	// ==================== 吸血 / 磁力 ====================

	private static void tickClouds(LivingEntity owner, EximusArchetype archetype, long now) {
		List<Cloud> clouds = CLOUDS.computeIfAbsent(owner.getUUID(), id -> new ArrayList<>());
		clouds.removeIf(cloud -> now > cloud.end);
		if (currentTarget(owner) != null && clouds.size() < 3
				&& now >= NEXT_CLOUD.getOrDefault(owner.getUUID(), 0L)) {
			NEXT_CLOUD.put(owner.getUUID(), now + 160L);
			if (owner.level() instanceof ServerLevel level) {
				List<Vec3> positions = new ArrayList<>();
				// 每个目标最多匹配一团；不足 3 团时随机补位。
				for (LivingEntity target : selectUniqueTargets(owner, 8.0D, 3)) {
					positions.add(groundPointAt(level, target));
				}
				while (positions.size() < 3) {
					positions.add(groundPoint(level, owner, 8.0D));
				}
				for (Vec3 pos : positions) {
					// 1.5 秒预警 + 10 秒生效；场上最多 3 团。
					clouds.add(new Cloud(pos.x, pos.y, pos.z, now + 30L, now + 230L, now + 30L));
					level.sendParticles(archetype == EximusArchetype.VAMPIRE
									? ParticleTypes.DAMAGE_INDICATOR : ParticleTypes.PORTAL,
							pos.x, pos.y + 0.2D, pos.z, 8, 0.8D, 0.4D, 0.8D, 0.01D);
				}
			}
		}
		boolean warning = false;
		for (Cloud cloud : clouds) {
			if (now < cloud.armUntil) {
				warning = true;
				if (owner.level() instanceof ServerLevel level) {
					level.sendParticles(archetype == EximusArchetype.VAMPIRE
									? ParticleTypes.DAMAGE_INDICATOR : ParticleTypes.PORTAL,
							cloud.x, cloud.y + 0.2D, cloud.z, 2, 0.5D, 0.2D, 0.5D, 0.01D);
					Vector3f color = archetype == EximusArchetype.VAMPIRE
							? new Vector3f(0.7F, 0.0F, 0.1F) : new Vector3f(0.71F, 0.36F, 1.0F);
					cloudRing(level, cloud, color);
				}
				continue;
			}
			for (LivingEntity target : targetsAt(owner, new Vec3(cloud.x, cloud.y, cloud.z), 2.0D)) {
				if (archetype == EximusArchetype.MAGNET && !isBossLike(target)) {
					pull(target, cloud);
				}
				if (now < cloud.nextTick || now - CLOUD_HIT.getOrDefault(target.getUUID(), -100L) < 20L) {
					continue;
				}
				CLOUD_HIT.put(target.getUUID(), now);
				hurt(target, owner.damageSources().indirectMagic(owner, owner),
						skillDamage(owner, target, archetype == EximusArchetype.VAMPIRE ? 6.0D : 4.0D));
				if (owner.level() instanceof ServerLevel level) {
					Vector3f color = archetype == EximusArchetype.VAMPIRE
							? new Vector3f(0.7F, 0.0F, 0.1F) : new Vector3f(0.71F, 0.36F, 1.0F);
					level.sendParticles(new DustParticleOptions(color, 0.9F), target.getX(),
							target.getY() + target.getBbHeight() * 0.5D, target.getZ(),
							6, 0.4D, 0.3D, 0.4D, 0.01D);
				}
				if (archetype == EximusArchetype.VAMPIRE
						&& now - CLOUD_HEAL.getOrDefault(owner.getUUID(), -100L) >= 20L) {
					CLOUD_HEAL.put(owner.getUUID(), now);
					owner.heal(owner.getMaxHealth() * 0.05F);
				}
			}
			if (now >= cloud.nextTick) {
				cloud.nextTick += 20L;
			}
		}
		root(owner, warning);
	}

	private static void cloudRing(ServerLevel level, Cloud cloud, Vector3f color) {
		DustParticleOptions dust = new DustParticleOptions(color, 0.9F);
		for (int i = 0; i < 12; i++) {
			double angle = Math.PI * 2.0D * i / 12.0D;
			level.sendParticles(dust, cloud.x + Math.cos(angle) * 2.0D, cloud.y + 0.1D,
					cloud.z + Math.sin(angle) * 2.0D, 1, 0.02D, 0.0D, 0.02D, 0.0D);
		}
	}

	private static Vec3 groundPoint(ServerLevel level, LivingEntity owner, double radius) {
		double angle = level.random.nextDouble() * Math.PI * 2.0D;
		double distance = 2.0D + level.random.nextDouble() * (radius - 2.0D);
		double x = owner.getX() + Math.cos(angle) * distance;
		double z = owner.getZ() + Math.sin(angle) * distance;
		BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				BlockPos.containing(x, owner.getY(), z));
		return new Vec3(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
	}

	/** 目标脚下的地面点（高度图，不落在树叶/积雪上）。 */
	private static Vec3 groundPointAt(ServerLevel level, LivingEntity target) {
		BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				BlockPos.containing(target.getX(), target.getY(), target.getZ()));
		return new Vec3(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
	}

	private static void pull(LivingEntity target, Cloud cloud) {
		Vec3 away = new Vec3(cloud.x - target.getX(), 0.0D, cloud.z - target.getZ());
		if (away.lengthSqr() < 1.0E-4D) {
			return;
		}
		Vec3 dir = away.normalize();
		// 拉拽按击退抗性削减；击退抗性 1.0 时完全免疫。
		double resistance = Math.max(0.0D, Math.min(1.0D,
				target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)));
		double strength = 0.15D * (1.0D - resistance);
		target.push(dir.x * strength, 0.0D, dir.z * strength);
		target.hurtMarked = true;
	}

	private static boolean isBossLike(LivingEntity entity) {
		MonsterScaler.Kind kind = MonsterScaler.kindOf(entity);
		return kind == MonsterScaler.Kind.BOSS || kind == MonsterScaler.Kind.LEADER;
	}

	// ==================== 伤害修正 ====================

	@SubscribeEvent(priority = EventPriority.NORMAL)
	public static void onFrostDamage(LivingDamageEvent event) {
		LivingEntity victim = event.getEntity();
		FrostShield shield = frostShieldOf(victim);
		if (shield == null || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return;
		}
		// 护盾存在时先吃 50% 全伤害减免；非物理伤害穿盾，但保留这 50%。
		event.setAmount(event.getAmount() * 0.5F);
		boolean nonPhysical = event.getSource().is(DamageTypeTags.BYPASSES_ARMOR)
				|| event.getSource().is(DamageTypeTags.IS_FIRE)
				|| event.getSource().is(DamageTypeTags.IS_EXPLOSION)
				|| event.getSource().is(DamageTypeTags.IS_LIGHTNING);
		if (nonPhysical) {
			return;
		}
		double absorbed = Math.min(shield.amount, event.getAmount());
		event.setAmount((float) (event.getAmount() - absorbed));
		shield.amount -= absorbed;
		victim.getPersistentData().putDouble(TAG_FROST_SHIELD, shield.amount);
		if (shield.amount <= 0.0D) {
			breakFrostShield(victim);
		}
	}

	// LOW：在坚韧/冰盾（NORMAL）之后、卓越护盾（LOWEST）之前结算守护转移。
	@SubscribeEvent(priority = EventPriority.LOW)
	public static void onGuardianDamage(LivingDamageEvent event) {
		LivingEntity victim = event.getEntity();
		if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return;
		}
		if (archetype(victim) == EximusArchetype.GUARDIAN) {
			// 守护者自身直接承伤与转移伤害都只吃 40% 减伤。
			event.setAmount(event.getAmount() * 0.6F);
			return;
		}
		if (!(victim instanceof Enemy) || isBetrayalTarget(victim)) {
			return;
		}
		LivingEntity guardian = nearestGuardian(victim);
		if (guardian == null || !isSupportTarget(guardian, victim)) {
			return;
		}
		float original = event.getAmount();
		event.setAmount(original * 0.1F);
		guardian.invulnerableTime = 0;
		guardian.hurt(guardian.damageSources().generic(), original * 0.9F);
		if (guardian.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, guardian.getX(),
					guardian.getY() + guardian.getBbHeight() * 0.5D, guardian.getZ(), 8, 0.6D, 0.4D, 0.6D, 0.02D);
		}
	}

	private static LivingEntity nearestGuardian(LivingEntity victim) {
		if (!(victim.level() instanceof ServerLevel level)) {
			return null;
		}
		LivingEntity best = null;
		double bestDistance = Double.MAX_VALUE;
		for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class,
				victim.getBoundingBox().inflate(8.0D))) {
			if (!(candidate instanceof Mob) || candidate == victim || isBetrayalTarget(candidate)
					|| archetype(candidate) != EximusArchetype.GUARDIAN) {
				continue;
			}
			double distance = candidate.distanceToSqr(victim);
			if (distance <= 64.0D && distance < bestDistance) {
				best = candidate;
				bestDistance = distance;
			}
		}
		return best;
	}

	// ==================== 粒子与清理 ====================

	private static void ambient(LivingEntity entity, EximusArchetype archetype) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}
		Vector3f color = color(archetype);
		double x = entity.getX();
		double y = entity.getY() + 0.25D;
		double z = entity.getZ();
		level.sendParticles(new DustParticleOptions(color, 0.9F), x, y, z, 2, 0.3D, 0.1D, 0.3D, 0.01D);
		switch (archetype) {
			case FLAME -> level.sendParticles(ParticleTypes.FLAME, x, y, z, 1, 0.2D, 0.1D, 0.2D, 0.01D);
			case FROST -> level.sendParticles(ParticleTypes.SNOWFLAKE, x, y, z, 1, 0.2D, 0.1D, 0.2D, 0.01D);
			case POISON -> {
				level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, x, y, z, 1, 0.2D, 0.1D, 0.2D, 0.01D);
				// 常驻绿色地面圈，提示 3 格毒光环范围。
				groundRing(entity, 3.0D, color(archetype));
			}
			case STORM -> level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 1, 0.2D, 0.1D, 0.2D, 0.01D);
			case SHOCK -> level.sendParticles(ParticleTypes.CRIT, x, y, z, 1, 0.2D, 0.1D, 0.2D, 0.01D);
			case GUARDIAN -> {
				level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, x, y, z, 1, 0.2D, 0.1D, 0.2D, 0.01D);
				// 常驻金色地面圈，提示 8 格守护光环范围。
				groundRing(entity, 8.0D, color(archetype));
			}
			case VAMPIRE -> level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, x, y, z, 1, 0.2D, 0.1D, 0.2D, 0.01D);
			case MAGNET -> level.sendParticles(ParticleTypes.PORTAL, x, y, z, 1, 0.2D, 0.1D, 0.2D, 0.01D);
		}
	}

	private static Vector3f color(EximusArchetype archetype) {
		return switch (archetype) {
			case FLAME -> new Vector3f(1.0F, 0.42F, 0.0F);
			case FROST -> new Vector3f(0.61F, 0.91F, 1.0F);
			case POISON -> new Vector3f(0.4F, 0.8F, 0.2F);
			case STORM -> new Vector3f(1.0F, 0.88F, 0.4F);
			case SHOCK -> new Vector3f(1.0F, 0.69F, 0.0F);
			case GUARDIAN -> new Vector3f(1.0F, 0.84F, 0.0F);
			case VAMPIRE -> new Vector3f(0.7F, 0.0F, 0.1F);
			case MAGNET -> new Vector3f(0.71F, 0.36F, 1.0F);
		};
	}

	@SubscribeEvent
	public static void onLivingDeath(LivingDeathEvent event) {
		LivingEntity entity = event.getEntity();
		UUID id = entity.getUUID();
		resetStorm(entity);
		FLAME.remove(id);
		FLAME_READY.remove(id);
		FROST_STATE.remove(id);
		FROST_PENDING.remove(id);
		SHOCK.remove(id);
		SHOCK_READY.remove(id);
		CLOUDS.remove(id);
		NEXT_CLOUD.remove(id);
		CLOUD_HIT.remove(id);
		CLOUD_HEAL.remove(id);
		CREEPER_FUSING.remove(id);
		ROOTED.remove(id);
		FROST_SHIELDS.remove(id);
		FROST_GRANTED.remove(id);
		entity.getPersistentData().remove(TAG_FROST_SHIELD);
		entity.getPersistentData().remove(TAG_FROST_SHIELD_MAX);
		entity.getPersistentData().remove(TAG_FROST_GRANTED);
		removeFrostSlow(entity);
	}

	private static void removeFrostSlow(LivingEntity entity) {
		FROST_SLOW.remove(entity.getUUID());
		AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
		AttributeInstance attack = entity.getAttribute(Attributes.ATTACK_SPEED);
		if (speed != null) {
			speed.removeModifier(FROST_SLOW_SPEED);
		}
		if (attack != null) {
			attack.removeModifier(FROST_SLOW_ATTACK);
		}
	}

	@SubscribeEvent
	public static void onServerStopped(ServerStoppedEvent event) {
		FLAME.clear();
		FLAME_READY.clear();
		FROST_STATE.clear();
		FROST_PENDING.clear();
		FROST_SHIELDS.clear();
		FROST_GRANTED.clear();
		FROST_SLOW.clear();
		STORM.clear();
		SHOCK.clear();
		SHOCK_READY.clear();
		CLOUDS.clear();
		NEXT_CLOUD.clear();
		CLOUD_HIT.clear();
		CLOUD_HEAL.clear();
		CREEPER_FUSING.clear();
		ROOTED.clear();
	}

	private record FlameCast(long start, Set<UUID> hit) {
		private FlameCast(long start) {
			this(start, new HashSet<>());
		}
	}

	private static final class FrostShield {
		private double amount;

		private FrostShield(double amount) {
			this.amount = amount;
		}
	}

	private record FrostState(long start) {
	}

	private static final class StormState {
		private int sprint;
		private int rest;
		private int stuckTicks;
		private double lastX;
		private double lastZ;
		private float previousWaypoint = -1.0F;
		private List<LockSlot> lockSlots = List.of();
		private long nextCast;
		private long castStart = -1L;
		private boolean struck;
		private List<Vec3> points = List.of();
	}

	private static final class LockSlot {
		private final UUID targetId;
		private Vec3 point;
		private boolean fixed;

		private LockSlot(UUID targetId, Vec3 point) {
			this.targetId = targetId;
			this.point = point;
			this.fixed = targetId == null;
		}
	}

	private static final class ShockState {
		private final long start;
		private final Vec3 direction;
		private final Set<String> hit = new HashSet<>();
		private final Set<Integer> burst = new HashSet<>();

		private ShockState(long start, Vec3 direction) {
			this.start = start;
			this.direction = direction;
		}
	}

	private static final class Cloud {
		private final double x;
		private final double y;
		private final double z;
		private final long armUntil;
		private final long end;
		private long nextTick;

		private Cloud(double x, double y, double z, long armUntil, long end, long nextTick) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.armUntil = armUntil;
			this.end = end;
			this.nextTick = nextTick;
		}
	}
}
