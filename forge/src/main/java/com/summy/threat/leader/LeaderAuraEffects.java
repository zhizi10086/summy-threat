package com.summy.threat.leader;

import com.summy.threat.scaling.MonsterScaler;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 区域首领五项光环的成员维护、属性修正和事件乘区。 */
public final class LeaderAuraEffects {
	private enum Kind {
		COMMAND,
		INTIMIDATION,
		BASTION,
		SUPPRESSION,
		BLOODLUST
	}

	private record Source(UUID owner, Kind kind) {
	}

	private static final long LINGER_TICKS = 20L;
	private static final Map<Source, Map<UUID, Long>> MEMBERS = new HashMap<>();
	private static final Map<Source, ResourceKey<Level>> SOURCE_LEVELS = new HashMap<>();
	private static final Map<Kind, Set<UUID>> APPLIED = new HashMap<>();
	private static final Map<UUID, Boolean> BLOODLUST_ACTIVE = new HashMap<>();
	private static final UUID COMMAND_ATTACK =
			UUID.fromString("6f5c1a2e-0009-4a0b-9d1e-000000000511");
	private static final UUID COMMAND_SPEED =
			UUID.fromString("6f5c1a2e-0009-4a0b-9d1e-000000000512");
	private static final UUID SUPPRESSION_SPEED =
			UUID.fromString("6f5c1a2e-0009-4a0b-9d1e-000000000513");
	private static final UUID BLOODLUST_ATTACK =
			UUID.fromString("6f5c1a2e-0009-4a0b-9d1e-000000000514");
	private static final UUID BLOODLUST_SPEED =
			UUID.fromString("6f5c1a2e-0009-4a0b-9d1e-000000000515");

	private LeaderAuraEffects() {
	}

	public static void tickLeader(LivingEntity leader, long now) {
		if (MonsterScaler.kindOf(leader) != MonsterScaler.Kind.LEADER) {
			cleanupLeader(leader);
			return;
		}
		List<LeaderAbilityId> abilities = LeaderAbilityState.abilities(leader);
		boolean any = false;
		for (Kind kind : Kind.values()) {
			LeaderAbilityId id = abilityFor(kind);
			if (abilities.contains(id)) {
				any = true;
				refreshMembers(leader, kind, now);
			} else {
				removeMembers(leader, kind);
			}
		}
		if (!any) {
			cleanupLeader(leader);
			return;
		}
		emitParticles(leader, abilities);
	}

	@SubscribeEvent
	public static void onLivingTick(LivingEvent.LivingTickEvent event) {
		LivingEntity entity = event.getEntity();
		if (entity.level().isClientSide() || entity.tickCount % 10 != 0) {
			return;
		}
		if (MonsterScaler.kindOf(entity) == MonsterScaler.Kind.LEADER) {
			tickLeader(entity, entity.level().getGameTime());
		}
	}

	@SubscribeEvent(priority = EventPriority.NORMAL)
	public static void onLivingHurt(LivingHurtEvent event) {
		if (event.isCanceled()) {
			return;
		}
		LivingEntity victim = event.getEntity();
		Entity sourceEntity = event.getSource().getEntity();
		if (!(sourceEntity instanceof LivingEntity source) || event.getAmount() <= 0.0F) {
			return;
		}
		long now = victim.level().getGameTime();
		for (Map.Entry<Source, Map<UUID, Long>> entry : MEMBERS.entrySet()) {
			if (entry.getKey().kind() != Kind.INTIMIDATION
					|| entry.getValue().getOrDefault(victim.getUUID(), 0L) <= now) {
				continue;
			}
			LivingEntity leader = living(victim, entry.getKey().owner());
			if (leader != null && LeaderTargeting.isTarget(leader, victim)
					&& LeaderTargeting.isLeaderSideSource(leader, source)) {
				event.setAmount(event.getAmount() * 1.10F);
				return;
			}
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGH)
	public static void onLivingDamage(LivingDamageEvent event) {
		if (event.isCanceled()
				|| event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)
				|| event.getAmount() <= 0.0F) {
			return;
		}
		if (covered(Kind.BASTION, event.getEntity().getUUID(), event.getEntity().level().getGameTime())) {
			event.setAmount(event.getAmount() * 0.90F);
		}
	}

	@SubscribeEvent
	public static void onLivingDeath(LivingDeathEvent event) {
		LivingEntity entity = event.getEntity();
		cleanupLeader(entity);
		for (Map<UUID, Long> targets : MEMBERS.values()) {
			targets.remove(entity.getUUID());
		}
		for (Kind kind : Kind.values()) {
			reconcile(kind, entity.level().getGameTime(),
					entity.level() instanceof ServerLevel level ? level : null);
		}
	}

	@SubscribeEvent
	public static void onServerStopped(ServerStoppedEvent event) {
		MEMBERS.clear();
		SOURCE_LEVELS.clear();
		APPLIED.clear();
		BLOODLUST_ACTIVE.clear();
	}

	@SubscribeEvent
	public static void onLevelUnload(LevelEvent.Unload event) {
		if (event.getLevel() instanceof ServerLevel level) {
			cleanupLevel(level);
		}
	}

	public static void cleanupLevel(ServerLevel level) {
		ResourceKey<Level> dimension = level.dimension();
		Set<UUID> owners = new HashSet<>();
		for (Map.Entry<Source, ResourceKey<Level>> entry : new HashMap<>(SOURCE_LEVELS).entrySet()) {
			if (!dimension.equals(entry.getValue())) {
				continue;
			}
			Source source = entry.getKey();
			owners.add(source.owner());
			MEMBERS.remove(source);
			SOURCE_LEVELS.remove(source);
		}
		owners.forEach(BLOODLUST_ACTIVE::remove);
		for (Kind kind : Kind.values()) {
			reconcile(kind, level.getGameTime(), level);
		}
	}

	public static void cleanupLeader(LivingEntity leader) {
		UUID owner = leader.getUUID();
		for (Kind kind : Kind.values()) {
			Source source = new Source(owner, kind);
			MEMBERS.remove(source);
			SOURCE_LEVELS.remove(source);
		}
		BLOODLUST_ACTIVE.remove(owner);
		for (Kind kind : Kind.values()) {
			reconcile(kind, leader.level().getGameTime(),
					leader.level() instanceof ServerLevel level ? level : null);
		}
	}

	private static void refreshMembers(LivingEntity leader, Kind kind, long now) {
		List<LivingEntity> candidates;
		if (kind == Kind.COMMAND || kind == Kind.BASTION || kind == Kind.BLOODLUST) {
			candidates = LeaderTargeting.supportTargets(leader, 8.0D);
		} else {
			double radius = kind == Kind.INTIMIDATION ? 6.0D : 6.0D;
			candidates = LeaderTargeting.offensiveTargets(leader, radius);
		}
		if (kind == Kind.BLOODLUST) {
			boolean previous = BLOODLUST_ACTIVE.getOrDefault(leader.getUUID(), false);
			double maxHealth = Math.max(1.0D, leader.getMaxHealth());
			double threshold = maxHealth * (previous ? 0.55D : 0.50D);
			boolean active = leader.getHealth() <= threshold + 1.0E-4D;
			BLOODLUST_ACTIVE.put(leader.getUUID(), active);
			if (!active) {
				candidates = List.of();
			} else if (!previous && leader.level() instanceof ServerLevel level) {
				level.playSound(null, leader.getX(), leader.getY(), leader.getZ(),
						SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 0.55F, 1.2F);
			}
		}
		LeaderVisuals.auraTargets(leader, abilityFor(kind), candidates);

		Source source = new Source(leader.getUUID(), kind);
		SOURCE_LEVELS.put(source, leader.level().dimension());
		Map<UUID, Long> members = MEMBERS.computeIfAbsent(source, ignored -> new HashMap<>());
		members.entrySet().removeIf(entry -> entry.getValue() <= now);
		for (LivingEntity target : candidates) {
			members.put(target.getUUID(), now + LINGER_TICKS);
		}
		if (members.isEmpty()) {
			MEMBERS.remove(source);
			SOURCE_LEVELS.remove(source);
		}
		reconcile(kind, now, leader.level() instanceof ServerLevel level ? level : null);
	}

	private static void removeMembers(LivingEntity leader, Kind kind) {
		Source source = new Source(leader.getUUID(), kind);
		MEMBERS.remove(source);
		SOURCE_LEVELS.remove(source);
		reconcile(kind, leader.level().getGameTime(),
				leader.level() instanceof ServerLevel level ? level : null);
	}

	private static boolean covered(Kind kind, UUID target, long now) {
		for (Map.Entry<Source, Map<UUID, Long>> entry : MEMBERS.entrySet()) {
			if (entry.getKey().kind() == kind && entry.getValue().getOrDefault(target, 0L) > now) {
				return true;
			}
		}
		return false;
	}

	private static void reconcile(Kind kind, long now, ServerLevel level) {
		if (level == null) {
			return;
		}
		Set<UUID> active = new HashSet<>();
		for (Map.Entry<Source, Map<UUID, Long>> entry : MEMBERS.entrySet()) {
			if (entry.getKey().kind() != kind) {
				continue;
			}
			entry.getValue().entrySet().removeIf(target -> target.getValue() <= now);
			entry.getValue().keySet().forEach(active::add);
		}
		MEMBERS.entrySet().removeIf(entry -> entry.getKey().kind() == kind
				&& entry.getValue().isEmpty());
		Set<UUID> previously = APPLIED.computeIfAbsent(kind, ignored -> new HashSet<>());
		for (UUID id : new HashSet<>(previously)) {
			if (!active.contains(id)) {
				LivingEntity target = livingById(level, id);
				if (target != null) {
					removeModifiers(target, kind);
				}
				previously.remove(id);
			}
		}
		for (UUID id : active) {
			LivingEntity target = livingById(level, id);
			if (target != null) {
				applyModifiers(target, kind);
				previously.add(id);
			}
		}
	}

	private static LivingEntity livingById(ServerLevel level, UUID id) {
		return level.getEntity(id) instanceof LivingEntity living ? living : null;
	}

	private static LivingEntity living(LivingEntity context, UUID id) {
		if (context.level() instanceof ServerLevel level && level.getEntity(id) instanceof LivingEntity living) {
			return living;
		}
		return null;
	}

	private static void applyModifiers(LivingEntity target, Kind kind) {
		switch (kind) {
			case COMMAND -> {
				modifier(target, Attributes.ATTACK_DAMAGE, COMMAND_ATTACK,
						"summy_threat:leader_aura/command_attack", 0.10D,
						AttributeModifier.Operation.MULTIPLY_TOTAL);
				modifier(target, Attributes.MOVEMENT_SPEED, COMMAND_SPEED,
						"summy_threat:leader_aura/command_speed", 0.10D,
						AttributeModifier.Operation.MULTIPLY_TOTAL);
			}
			case SUPPRESSION -> modifier(target, Attributes.MOVEMENT_SPEED, SUPPRESSION_SPEED,
					"summy_threat:leader_aura/suppression_speed", -0.15D,
					AttributeModifier.Operation.MULTIPLY_TOTAL);
			case BLOODLUST -> {
				modifier(target, Attributes.ATTACK_DAMAGE, BLOODLUST_ATTACK,
						"summy_threat:leader_aura/bloodlust_attack", 0.15D,
						AttributeModifier.Operation.MULTIPLY_TOTAL);
				modifier(target, Attributes.MOVEMENT_SPEED, BLOODLUST_SPEED,
						"summy_threat:leader_aura/bloodlust_speed", 0.10D,
						AttributeModifier.Operation.MULTIPLY_TOTAL);
			}
			default -> {
			}
		}
	}

	private static void removeModifiers(LivingEntity target, Kind kind) {
		switch (kind) {
			case COMMAND -> {
				remove(target, Attributes.ATTACK_DAMAGE, COMMAND_ATTACK);
				remove(target, Attributes.MOVEMENT_SPEED, COMMAND_SPEED);
			}
			case SUPPRESSION -> remove(target, Attributes.MOVEMENT_SPEED, SUPPRESSION_SPEED);
			case BLOODLUST -> {
				remove(target, Attributes.ATTACK_DAMAGE, BLOODLUST_ATTACK);
				remove(target, Attributes.MOVEMENT_SPEED, BLOODLUST_SPEED);
			}
			default -> {
			}
		}
	}

	private static void modifier(LivingEntity target, net.minecraft.world.entity.ai.attributes.Attribute attribute,
			UUID id, String name, double amount, AttributeModifier.Operation operation) {
		AttributeInstance instance = target.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		instance.removeModifier(id);
		instance.addTransientModifier(new AttributeModifier(id, name, amount, operation));
	}

	private static void remove(LivingEntity target, net.minecraft.world.entity.ai.attributes.Attribute attribute,
			UUID id) {
		AttributeInstance instance = target.getAttribute(attribute);
		if (instance != null) {
			instance.removeModifier(id);
		}
	}

	private static LeaderAbilityId abilityFor(Kind kind) {
		return switch (kind) {
			case COMMAND -> LeaderAbilityId.COMMAND_AURA;
			case INTIMIDATION -> LeaderAbilityId.INTIMIDATION_AURA;
			case BASTION -> LeaderAbilityId.BASTION_AURA;
			case SUPPRESSION -> LeaderAbilityId.SUPPRESSION_AURA;
			case BLOODLUST -> LeaderAbilityId.BLOODLUST_AURA;
		};
	}

	private static void emitParticles(LivingEntity leader, List<LeaderAbilityId> abilities) {
		for (LeaderAbilityId id : abilities) {
			if (id.aura()) {
				LeaderVisuals.auraLeader(leader, id);
			}
		}
	}
}
