package com.summy.threat.leader;

import com.summy.threat.combat.ArmorPenetration;
import com.summy.threat.combat.ArmorShred;
import com.summy.threat.scaling.MonsterScaler;
import com.summy.threat.scaling.ScalingState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** 区域首领主动技能状态机和低层结算入口。 */
public final class LeaderActiveEffects {
	private static final UUID TECTONIC_SLOW =
			UUID.fromString("6f5c1a2e-0009-4a0b-9d1e-000000000521");
	private static final UUID COLLAPSE_SLOW =
			UUID.fromString("6f5c1a2e-0009-4a0b-9d1e-000000000522");
	private static final String SLOW_NAME = "summy_threat:leader_ability/tectonic_slow";
	private static final String COLLAPSE_SLOW_NAME = "summy_threat:leader_ability/collapse_slow";

	private record TectonicCast(long triggerTick, Vec3 point) {
	}

	private record ArmorCast(long triggerTick, LivingEntity target) {
	}

	private record RayCast(long triggerTick, Vec3 origin, Vec3 direction) {
	}

	private record GravityCast(long triggerTick) {
	}

	private record PullState(LivingEntity target, UUID owner, long until) {
	}

	private record PhaseCast(long triggerTick, Vec3 start, Vec3 end, Vec3 direction,
			LivingEntity target) {
	}

	private record IronState(long endTick, int hits) {
	}

	private record CollapseState(long warningEnd, long nextDamage, long endTick, Vec3 point) {
	}

	private record Slow(LivingEntity target, UUID source, long until, UUID modifier, String name,
			double amount) {
	}

	private record ControlBudget(Vec3 base, double x, double z, double y) {
	}

	private static final class PendingHit {
		private final LivingEntity target;
		private final DamageSource source;
		private boolean seen;
		private boolean canceled;
		private float amount;

		private PendingHit(LivingEntity target, DamageSource source) {
			this.target = target;
			this.source = source;
		}
	}

	private static final Map<UUID, TectonicCast> TECTONIC = new HashMap<>();
	private static final Map<UUID, ArmorCast> ARMOR = new HashMap<>();
	private static final Map<UUID, RayCast> RAY = new HashMap<>();
	private static final Map<UUID, GravityCast> GRAVITY = new HashMap<>();
	private static final List<PullState> PULLS = new java.util.ArrayList<>();
	private static final Map<UUID, PhaseCast> PHASE = new HashMap<>();
	private static final Map<UUID, IronState> IRON = new HashMap<>();
	private static final Set<UUID> IRON_PENDING = new HashSet<>();
	private static final Map<UUID, CollapseState> COLLAPSE = new HashMap<>();
	private static final Map<UUID, Long> COLLAPSE_LAST_HIT = new HashMap<>();
	private static final Map<String, Long> READY = new HashMap<>();
	private static final Map<String, Slow> SLOWS = new HashMap<>();
	private static final Map<UUID, ControlBudget> CONTROL = new HashMap<>();
	private static final Map<UUID, ResourceKey<Level>> OWNER_LEVELS = new HashMap<>();
	private static final ThreadLocal<Deque<PendingHit>> SKILL_HITS =
			ThreadLocal.withInitial(ArrayDeque::new);

	private LeaderActiveEffects() {
	}

	public static void tickLeader(LivingEntity owner, long now) {
		if (MonsterScaler.kindOf(owner) != MonsterScaler.Kind.LEADER) {
			cleanupLeader(owner);
			return;
		}
		OWNER_LEVELS.put(owner.getUUID(), owner.level().dimension());
		LeaderAbilityId active = activeAbility(owner);
		if (active == null) {
			cleanupOwner(owner.getUUID());
			return;
		}
		if (active == LeaderAbilityId.TECTONIC_RUPTURE) {
			tickTectonic(owner, now);
		} else if (active == LeaderAbilityId.ARMOR_BREAKER) {
			tickArmor(owner, now);
		} else if (active == LeaderAbilityId.HUNT_RAY) {
			tickRay(owner, now);
		} else if (active == LeaderAbilityId.GRAVITY_PULSE) {
			tickGravity(owner, now);
		} else if (active == LeaderAbilityId.PHASE_LUNGE) {
			tickPhase(owner, now);
		} else if (active == LeaderAbilityId.IRON_GUARD) {
			tickIron(owner, now);
		} else if (active == LeaderAbilityId.COLLAPSE_FIELD) {
			tickCollapse(owner, now);
		}
	}

	@SubscribeEvent
	public static void onLivingTick(LivingEvent.LivingTickEvent event) {
		LivingEntity entity = event.getEntity();
		if (entity.level().isClientSide()) {
			return;
		}
		if (MonsterScaler.kindOf(entity) == MonsterScaler.Kind.LEADER) {
			tickLeader(entity, entity.level().getGameTime());
		}
	}

	@SubscribeEvent
	public static void onServerTick(TickEvent.ServerTickEvent event) {
		if (event.phase != TickEvent.Phase.END) {
			return;
		}
		for (ServerLevel level : event.getServer().getAllLevels()) {
			tickServer(level, level.getGameTime());
		}
	}

	public static void tickServer(ServerLevel level, long now) {
		for (UUID ownerId : new HashSet<>(IRON_PENDING)) {
			if (level.getEntity(ownerId) instanceof LivingEntity owner && owner.isAlive()) {
				IRON_PENDING.remove(ownerId);
				resolveIronCounter(owner, now);
			}
		}
		PULLS.removeIf(pull -> {
			if (pull.until() <= now || !pull.target().isAlive()) {
				return true;
			}
			if (pull.target().level() != level) {
				return false;
			}
			Entity source = level.getEntity(pull.owner());
			if (!(source instanceof LivingEntity owner) || !owner.isAlive()) {
				return true;
			}
			Vec3 diff = owner.position().subtract(pull.target().position());
			Vec3 flat = new Vec3(diff.x, 0.0D, diff.z);
			if (flat.lengthSqr() > 1.0E-5D) {
				double resistance = pull.target().getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
				double strength = 0.12D * (1.0D - Math.max(0.0D, Math.min(1.0D, resistance)));
				Vec3 direction = flat.normalize().scale(strength);
				control(pull.target(), direction.x, direction.z, 0.0D);
			}
			return false;
		});
		for (String id : new HashSet<>(SLOWS.keySet())) {
			Slow slow = SLOWS.get(id);
			if (slow == null || slow.target().level() != level) {
				continue;
			}
			if (slow.until() <= now || !slow.target().isAlive()) {
				SLOWS.remove(id);
				if (slow != null) {
					removeModifier(slow.target(), Attributes.MOVEMENT_SPEED, slow.modifier());
				}
			}
		}
		CONTROL.clear();
	}

	@SubscribeEvent
	public static void onLivingDeath(LivingDeathEvent event) {
		cleanupLeader(event.getEntity());
	}

	@SubscribeEvent
	public static void onLevelUnload(LevelEvent.Unload event) {
		if (event.getLevel() instanceof ServerLevel level) {
			cleanupLevel(level);
		}
	}

	public static void cleanupLevel(ServerLevel level) {
		ResourceKey<Level> dimension = level.dimension();
		for (Map.Entry<UUID, ResourceKey<Level>> entry : new HashMap<>(OWNER_LEVELS).entrySet()) {
			if (dimension.equals(entry.getValue())) {
				cleanupOwner(entry.getKey());
			}
		}
	}

	@SubscribeEvent
	public static void onServerStopped(ServerStoppedEvent event) {
		TECTONIC.clear();
		ARMOR.clear();
		RAY.clear();
		GRAVITY.clear();
		PULLS.clear();
		PHASE.clear();
		IRON.clear();
		IRON_PENDING.clear();
		COLLAPSE.clear();
		COLLAPSE_LAST_HIT.clear();
		READY.clear();
		SLOWS.clear();
		CONTROL.clear();
		OWNER_LEVELS.clear();
		SKILL_HITS.remove();
	}

	public static void cleanupLeader(LivingEntity owner) {
		cleanupOwner(owner.getUUID());
	}

	private static void cleanupOwner(UUID ownerId) {
		TECTONIC.remove(ownerId);
		ARMOR.remove(ownerId);
		RAY.remove(ownerId);
		GRAVITY.remove(ownerId);
		PHASE.remove(ownerId);
		IRON.remove(ownerId);
		IRON_PENDING.remove(ownerId);
		COLLAPSE.remove(ownerId);
		PULLS.removeIf(pull -> pull.owner().equals(ownerId));
		for (String key : new HashSet<>(SLOWS.keySet())) {
			Slow slow = SLOWS.get(key);
			if (slow != null && slow.source().equals(ownerId)) {
				SLOWS.remove(key);
				removeModifier(slow.target(), Attributes.MOVEMENT_SPEED, slow.modifier());
			}
		}
		String prefix = ownerId + "|";
		READY.keySet().removeIf(key -> key.startsWith(prefix));
		OWNER_LEVELS.remove(ownerId);
	}

	public static void resetAbilities(LivingEntity owner) {
		cleanupLeader(owner);
		COLLAPSE_LAST_HIT.clear();
	}

	public static boolean hasIronGuard(LivingEntity owner) {
		return IRON.containsKey(owner.getUUID());
	}

	private static void tickTectonic(LivingEntity owner, long now) {
		TectonicCast cast = TECTONIC.get(owner.getUUID());
		if (cast != null) {
			warnTectonic(owner, cast);
			if (now >= cast.triggerTick()) {
				resolveTectonic(owner, cast);
				TECTONIC.remove(owner.getUUID());
			}
			return;
		}
		if (ready(owner, LeaderAbilityId.TECTONIC_RUPTURE) > now
				|| LeaderTargeting.currentTarget(owner) == null) {
			return;
		}
		Vec3 point = LeaderTargeting.currentTarget(owner).position();
		TECTONIC.put(owner.getUUID(), new TectonicCast(now + 20L, point));
		READY.put(cooldownKey(owner, LeaderAbilityId.TECTONIC_RUPTURE), now + 160L);
	}

	private static void tickArmor(LivingEntity owner, long now) {
		ArmorCast cast = ARMOR.get(owner.getUUID());
		if (cast != null) {
			if (now >= cast.triggerTick()) {
				LivingEntity target = cast.target();
				if (target.isAlive() && target.level() == owner.level()
						&& owner.distanceTo(target) <= 4.0D) {
					target.invulnerableTime = 0;
					DamageSource source = owner.damageSources().mobAttack(owner);
					float amount = skillDamage(owner, target, 75.0D);
					boolean landed = skillHurt(owner, target, source,
							() -> ArmorPenetration.hurt(target, source, amount, 0.30D));
					if (landed) {
						ArmorShred.applyCorrosion(target);
					}
					particles(owner, cast.triggerTick(), target.position(), 0xB7442E, 18);
				}
				ARMOR.remove(owner.getUUID());
			}
			return;
		}
		LivingEntity target = LeaderTargeting.currentTarget(owner);
		if (ready(owner, LeaderAbilityId.ARMOR_BREAKER) > now || target == null
				|| owner.distanceTo(target) > 4.0D) {
			return;
		}
		ARMOR.put(owner.getUUID(), new ArmorCast(now + 16L, target));
		READY.put(cooldownKey(owner, LeaderAbilityId.ARMOR_BREAKER), now + 140L);
	}

	private static void tickRay(LivingEntity owner, long now) {
		RayCast cast = RAY.get(owner.getUUID());
		if (cast != null) {
			if (now >= cast.triggerTick()) {
				resolveRay(owner, cast);
				RAY.remove(owner.getUUID());
			}
			return;
		}
		LivingEntity target = LeaderTargeting.currentTarget(owner);
		if (ready(owner, LeaderAbilityId.HUNT_RAY) > now || target == null
				|| owner.distanceTo(target) > 20.0D) {
			return;
		}
		Vec3 origin = owner.getEyePosition();
		Vec3 diff = target.getBoundingBox().getCenter().subtract(origin);
		if (diff.lengthSqr() < 1.0E-5D) {
			return;
		}
		RAY.put(owner.getUUID(), new RayCast(now + 25L, origin, diff.normalize()));
		READY.put(cooldownKey(owner, LeaderAbilityId.HUNT_RAY), now + 180L);
	}

	private static void tickGravity(LivingEntity owner, long now) {
		GravityCast cast = GRAVITY.get(owner.getUUID());
		if (cast != null) {
			if (now >= cast.triggerTick()) {
				resolveGravity(owner, now);
				GRAVITY.remove(owner.getUUID());
			}
			return;
		}
		if (ready(owner, LeaderAbilityId.GRAVITY_PULSE) > now
				|| targetsAt(owner, owner.position(), 8.0D).isEmpty()) {
			return;
		}
		GRAVITY.put(owner.getUUID(), new GravityCast(now + 20L));
		READY.put(cooldownKey(owner, LeaderAbilityId.GRAVITY_PULSE), now + 160L);
	}

	private static void tickPhase(LivingEntity owner, long now) {
		PhaseCast cast = PHASE.get(owner.getUUID());
		if (cast != null) {
			if (now >= cast.triggerTick()) {
				resolvePhase(owner, cast);
				PHASE.remove(owner.getUUID());
			}
			return;
		}
		LivingEntity target = LeaderTargeting.currentTarget(owner);
		if (ready(owner, LeaderAbilityId.PHASE_LUNGE) > now || target == null
				|| owner.distanceTo(target) > 12.0D) {
			return;
		}
		Vec3 start = owner.position();
		Vec3 diff = new Vec3(target.getX() - start.x, 0.0D, target.getZ() - start.z);
		if (diff.lengthSqr() < 1.0E-5D) {
			return;
		}
		double distance = Math.max(0.0D, Math.min(12.0D, diff.length() - 1.0D));
		Vec3 direction = diff.normalize();
		Vec3 end = clipEndpoint(owner, start, start.add(direction.scale(distance)));
		PHASE.put(owner.getUUID(), new PhaseCast(now + 16L, start, end, direction, target));
		READY.put(cooldownKey(owner, LeaderAbilityId.PHASE_LUNGE), now + 180L);
	}

	private static void tickIron(LivingEntity owner, long now) {
		IronState state = IRON.get(owner.getUUID());
		if (state != null) {
			if (now >= state.endTick()) {
				IRON.remove(owner.getUUID());
			}
			return;
		}
		LivingEntity target = LeaderTargeting.currentTarget(owner);
		if (ready(owner, LeaderAbilityId.IRON_GUARD) > now || target == null
				|| owner.distanceTo(target) > 8.0D) {
			return;
		}
		IRON.put(owner.getUUID(), new IronState(now + 60L, 0));
		READY.put(cooldownKey(owner, LeaderAbilityId.IRON_GUARD), now + 220L);
		particles(owner, now, owner.position(), 0xD9B74C, 14);
	}

	private static void tickCollapse(LivingEntity owner, long now) {
		CollapseState state = COLLAPSE.get(owner.getUUID());
		if (state == null) {
			LivingEntity target = LeaderTargeting.currentTarget(owner);
			if (ready(owner, LeaderAbilityId.COLLAPSE_FIELD) > now || target == null) {
				return;
			}
			Vec3 point = target.position();
			COLLAPSE.put(owner.getUUID(),
					new CollapseState(now + 20L, now + 20L, now + 180L, point));
			READY.put(cooldownKey(owner, LeaderAbilityId.COLLAPSE_FIELD), now + 240L);
			return;
		}
		if (now >= state.endTick()) {
			COLLAPSE.remove(owner.getUUID());
			return;
		}
		if (now < state.warningEnd()) {
			particles(owner, now, state.point(), 0x694C34, 6);
			return;
		}
		List<LivingEntity> targets = targetsAt(owner, state.point(), 5.0D);
		for (LivingEntity target : targets) {
			applySlow(owner, target, now, 10L, COLLAPSE_SLOW, COLLAPSE_SLOW_NAME, -0.20D);
		}
		if (now >= state.nextDamage()) {
			for (LivingEntity target : targets) {
				Long last = COLLAPSE_LAST_HIT.get(target.getUUID());
				if (last != null && now - last < 20L) {
					continue;
				}
				target.invulnerableTime = 0;
				boolean landed = skillHurt(owner, target,
						owner.damageSources().indirectMagic(owner, owner),
						skillDamage(owner, target, 10.0D));
				if (landed) {
					COLLAPSE_LAST_HIT.put(target.getUUID(), now);
				}
			}
			COLLAPSE.put(owner.getUUID(), new CollapseState(state.warningEnd(),
					state.nextDamage() + 20L, state.endTick(), state.point()));
			particles(owner, now, state.point(), 0x5A3E2C, 18);
		}
	}

	private static void resolveRay(LivingEntity owner, RayCast cast) {
		if (!(owner.level() instanceof ServerLevel level)) {
			return;
		}
		Vec3 end = cast.origin().add(cast.direction().scale(20.0D));
		BlockHitResult block = level.clip(new ClipContext(cast.origin(), end,
				ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
		if (block.getType() != HitResult.Type.MISS) {
			end = block.getLocation();
		}
		for (LivingEntity target : lineTargets(owner, cast.origin(), end, 0.5D)) {
			target.invulnerableTime = 0;
			skillHurt(owner, target, owner.damageSources().indirectMagic(owner, owner),
					skillDamage(owner, target, 60.0D));
		}
		lineParticles(level, cast.origin(), end, 0xF3E6A1);
		level.playSound(null, cast.origin().x, cast.origin().y, cast.origin().z,
				SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 0.55F, 1.6F);
	}

	private static void resolveGravity(LivingEntity owner, long now) {
		if (!(owner.level() instanceof ServerLevel level)) {
			return;
		}
		for (LivingEntity target : targetsAt(owner, owner.position(), 8.0D)) {
			target.invulnerableTime = 0;
			boolean landed = skillHurt(owner, target,
					owner.damageSources().indirectMagic(owner, owner),
					skillDamage(owner, target, 30.0D));
			if (landed && MonsterScaler.kindOf(target) != MonsterScaler.Kind.BOSS
					&& MonsterScaler.kindOf(target) != MonsterScaler.Kind.LEADER) {
				PULLS.add(new PullState(target, owner.getUUID(), now + 10L));
			}
		}
		particles(owner, now, owner.position(), 0xA56BE8, 28);
		level.playSound(null, owner.getX(), owner.getY(), owner.getZ(),
				SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 0.6F, 0.75F);
	}

	private static void resolvePhase(LivingEntity owner, PhaseCast cast) {
		if (!(owner.level() instanceof ServerLevel level)) {
			return;
		}
		Set<UUID> hit = new HashSet<>();
		for (LivingEntity target : lineTargets(owner, cast.start(), cast.end(), 0.75D)) {
			hit.add(target.getUUID());
			target.invulnerableTime = 0;
			boolean landed = skillHurt(owner, target, owner.damageSources().mobAttack(owner),
					skillDamage(owner, target, 65.0D));
			if (landed) {
				control(target, cast.direction().x * 0.8D, cast.direction().z * 0.8D, 0.25D);
			}
		}
		LivingEntity locked = cast.target();
		if (locked != null && locked.isAlive() && !hit.contains(locked.getUUID())
				&& locked.distanceToSqr(cast.end()) <= 2.25D) {
			locked.invulnerableTime = 0;
			boolean landed = skillHurt(owner, locked, owner.damageSources().mobAttack(owner),
					skillDamage(owner, locked, 65.0D));
			if (landed) {
				control(locked, cast.direction().x * 0.8D, cast.direction().z * 0.8D, 0.25D);
			}
		}
		lineParticles(level, cast.start(), cast.end(), 0xE8C44D);
		BlockPos endPos = BlockPos.containing(cast.end());
		int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				endPos.getX(), endPos.getZ());
		double y = Math.max(cast.start().y, Math.min(cast.start().y + 1.0D, ground));
		owner.moveTo(cast.end().x, y, cast.end().z, owner.getYRot(), owner.getXRot());
		level.playSound(null, cast.start().x, cast.start().y, cast.start().z,
				SoundEvents.ENDER_DRAGON_FLAP, SoundSource.HOSTILE, 0.7F, 1.1F);
	}

	private static void resolveIronCounter(LivingEntity owner, long now) {
		if (!(owner.level() instanceof ServerLevel level)) {
			return;
		}
		for (LivingEntity target : targetsAt(owner, owner.position(), 5.0D)) {
			target.invulnerableTime = 0;
			skillHurt(owner, target, owner.damageSources().mobAttack(owner),
					skillDamage(owner, target, 60.0D));
		}
		particles(owner, now, owner.position(), 0xFFD45A, 32);
		level.playSound(null, owner.getX(), owner.getY(), owner.getZ(),
				SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 0.8F, 0.75F);
	}

	@SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
	public static void observeLivingHurt(LivingHurtEvent event) {
		for (PendingHit hit : SKILL_HITS.get()) {
			if (hit.target == event.getEntity() && hit.source == event.getSource()) {
				hit.seen = true;
				hit.canceled = event.isCanceled();
				hit.amount = event.getAmount();
				return;
			}
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGH)
	public static void onLivingDamage(LivingDamageEvent event) {
		LivingEntity owner = event.getEntity();
		IronState state = IRON.get(owner.getUUID());
		if (state == null || event.isCanceled() || event.getAmount() <= 0.0F) {
			return;
		}
		long now = owner.level().getGameTime();
		if (now >= state.endTick()) {
			IRON.remove(owner.getUUID());
			return;
		}
		boolean bypass = event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY);
		if (!bypass) {
			event.setAmount(event.getAmount() * 0.70F);
		}
		Entity sourceEntity = event.getSource().getEntity();
		if (sourceEntity instanceof LivingEntity attacker
				&& LeaderTargeting.isTarget(owner, attacker)) {
			int hits = state.hits() + 1;
			if (hits >= 3) {
				IRON_PENDING.add(owner.getUUID());
				IRON.remove(owner.getUUID());
			} else {
				IRON.put(owner.getUUID(), new IronState(state.endTick(), hits));
			}
		}
	}

	private static boolean skillHurt(LivingEntity owner, LivingEntity target,
			DamageSource source, BooleanSupplier action) {
		PendingHit hit = new PendingHit(target, source);
		Deque<PendingHit> hits = SKILL_HITS.get();
		hits.push(hit);
		boolean accepted;
		try {
			accepted = action.getAsBoolean();
		} finally {
			hits.pop();
			if (hits.isEmpty()) {
				SKILL_HITS.remove();
			}
		}
		return accepted && hit.seen && !hit.canceled && hit.amount > 0.0F;
	}

	private static boolean skillHurt(LivingEntity owner, LivingEntity target,
			DamageSource source, float amount) {
		return skillHurt(owner, target, source, () -> target.hurt(source, amount));
	}

	private static void resolveTectonic(LivingEntity owner, TectonicCast cast) {
		if (!(owner.level() instanceof ServerLevel level)) {
			return;
		}
		AABB area = new AABB(cast.point(), cast.point()).inflate(4.0D);
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area)) {
			if (target.distanceToSqr(cast.point()) > 16.0D
					|| !LeaderTargeting.isTarget(owner, target)) {
				continue;
			}
			target.invulnerableTime = 0;
			boolean landed = skillHurt(owner, target, owner.damageSources().mobAttack(owner),
					skillDamage(owner, target, 60.0D));
			if (landed) {
				pushFromPoint(target, cast.point(), 0.8D, 0.25D);
				applySlow(owner, target, cast.triggerTick(), 40L,
						TECTONIC_SLOW, SLOW_NAME, -0.30D);
			}
		}
		level.sendParticles(new DustParticleOptions(new Vector3f(0.66F, 0.50F, 0.25F), 1.2F),
				cast.point().x, cast.point().y + 0.15D, cast.point().z,
				36, 2.2D, 0.2D, 2.2D, 0.04D);
		level.playSound(null, cast.point().x, cast.point().y, cast.point().z,
				SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 0.65F, 0.75F);
	}

	private static void warnTectonic(LivingEntity owner, TectonicCast cast) {
		if (!(owner.level() instanceof ServerLevel level)) {
			return;
		}
		level.sendParticles(new DustParticleOptions(new Vector3f(0.72F, 0.53F, 0.24F), 0.9F),
				cast.point().x, cast.point().y + 0.05D, cast.point().z,
				8, 1.8D, 0.05D, 1.8D, 0.0D);
	}

	private static void applySlow(LivingEntity owner, LivingEntity target, long now, long duration,
			UUID modifier, String name, double amount) {
		String key = target.getUUID() + "|" + modifier;
		Slow existing = SLOWS.get(key);
		long until = Math.max(now + duration, existing == null ? 0L : existing.until());
		SLOWS.put(key, new Slow(target, owner.getUUID(), until, modifier, name, amount));
		AttributeInstance speed = target.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.removeModifier(modifier);
			speed.addTransientModifier(new AttributeModifier(modifier, name, amount,
					AttributeModifier.Operation.MULTIPLY_TOTAL));
		}
	}

	private static void pushFromPoint(LivingEntity target, Vec3 point, double horizontal, double vertical) {
		Vec3 diff = new Vec3(target.getX() - point.x, 0.0D, target.getZ() - point.z);
		if (diff.lengthSqr() < 1.0E-4D) {
			diff = new Vec3(0.0D, 0.0D, 1.0D);
		}
		diff = diff.normalize().scale(horizontal);
		control(target, diff.x, diff.z, vertical);
	}

	private static void control(LivingEntity target, double x, double z, double y) {
		ControlBudget current = CONTROL.get(target.getUUID());
		Vec3 base = current == null ? target.getDeltaMovement() : current.base();
		double dx = x + (current == null ? 0.0D : current.x());
		double dz = z + (current == null ? 0.0D : current.z());
		double dy = y + (current == null ? 0.0D : current.y());
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		if (horizontal > 1.5D) {
			dx = dx / horizontal * 1.5D;
			dz = dz / horizontal * 1.5D;
		}
		dy = Math.max(-0.5D, Math.min(0.5D, dy));
		CONTROL.put(target.getUUID(), new ControlBudget(base, dx, dz, dy));
		target.setDeltaMovement(base.x + dx, base.y + dy, base.z + dz);
		target.hurtMarked = true;
	}

	private static List<LivingEntity> targetsAt(LivingEntity owner, Vec3 point, double radius) {
		if (!(owner.level() instanceof ServerLevel level)) {
			return List.of();
		}
		AABB area = new AABB(point, point).inflate(radius);
		return level.getEntitiesOfClass(LivingEntity.class, area,
				target -> target.distanceToSqr(point) <= radius * radius
						&& LeaderTargeting.isTarget(owner, target));
	}

	private static List<LivingEntity> lineTargets(LivingEntity owner, Vec3 start, Vec3 end,
			double radius) {
		if (!(owner.level() instanceof ServerLevel level)) {
			return List.of();
		}
		AABB area = new AABB(start, end).inflate(radius);
		return level.getEntitiesOfClass(LivingEntity.class, area,
				target -> LeaderTargeting.isTarget(owner, target)
						&& distanceToSegmentSqr(target.getBoundingBox().getCenter(), start, end)
								<= radius * radius);
	}

	private static double distanceToSegmentSqr(Vec3 point, Vec3 start, Vec3 end) {
		Vec3 segment = end.subtract(start);
		double lengthSqr = segment.lengthSqr();
		if (lengthSqr <= 1.0E-8D) {
			return point.distanceToSqr(start);
		}
		double t = point.subtract(start).dot(segment) / lengthSqr;
		t = Math.max(0.0D, Math.min(1.0D, t));
		return point.distanceToSqr(start.add(segment.scale(t)));
	}

	private static Vec3 clipEndpoint(LivingEntity owner, Vec3 start, Vec3 end) {
		if (!(owner.level() instanceof ServerLevel level)) {
			return end;
		}
		Vec3 offset = new Vec3(0.0D, 1.0D, 0.0D);
		BlockHitResult hit = level.clip(new ClipContext(start.add(offset), end.add(offset),
				ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
		if (hit.getType() == HitResult.Type.MISS) {
			return end;
		}
		Vec3 direction = end.subtract(start);
		return hit.getLocation().subtract(offset).subtract(direction.normalize().scale(0.35D));
	}

	private static void lineParticles(ServerLevel level, Vec3 start, Vec3 end, int color) {
		Vec3 direction = end.subtract(start);
		double length = direction.length();
		if (length <= 1.0E-5D) {
			return;
		}
		Vec3 step = direction.scale(1.0D / length);
		Vector3f rgb = rgb(color);
		for (double distance = 0.0D; distance <= length; distance += 0.5D) {
			Vec3 point = start.add(step.scale(distance));
			level.sendParticles(new DustParticleOptions(rgb, 0.8F),
					point.x, point.y, point.z, 1, 0.02D, 0.02D, 0.02D, 0.0D);
		}
	}

	private static void particles(LivingEntity owner, long now, Vec3 point, int color, int count) {
		if (owner.level() instanceof ServerLevel level) {
			level.sendParticles(new DustParticleOptions(rgb(color), 1.0F),
					point.x, point.y + 0.2D, point.z,
					count, 1.2D, 0.35D, 1.2D, 0.02D);
		}
	}

	private static Vector3f rgb(int color) {
		return new Vector3f(((color >> 16) & 0xFF) / 255.0F,
				((color >> 8) & 0xFF) / 255.0F, (color & 0xFF) / 255.0F);
	}

	private static float skillDamage(LivingEntity owner, LivingEntity target, double base) {
		double factor = LeaderTargeting.isPlayerSide(target) ? 1.0D : 10.0D;
		return (float) (base * ScalingState.rAtk() * factor);
	}

	private static long ready(LivingEntity owner, LeaderAbilityId ability) {
		return READY.getOrDefault(cooldownKey(owner, ability), 0L);
	}

	private static String cooldownKey(LivingEntity owner, LeaderAbilityId ability) {
		return owner.getUUID() + "|" + ability.id();
	}

	private static LeaderAbilityId activeAbility(LivingEntity owner) {
		for (LeaderAbilityId id : LeaderAbilityState.abilities(owner)) {
			if (id.type() == LeaderAbilityId.Type.ACTIVE) {
				return id;
			}
		}
		return null;
	}

	private static void removeModifier(LivingEntity target,
			net.minecraft.world.entity.ai.attributes.Attribute attribute, UUID id) {
		AttributeInstance instance = target.getAttribute(attribute);
		if (instance != null) {
			instance.removeModifier(id);
		}
	}
}
