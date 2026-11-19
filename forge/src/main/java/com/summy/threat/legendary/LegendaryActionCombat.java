package com.summy.threat.legendary;

import com.summy.threat.equipment.ThreatCategory;
import com.summy.threat.equipment.ThreatHitContext;
import com.summy.threat.equipment.ThreatMovementContext;
import com.summy.threat.leader.LeaderTargeting;
import com.summy.threat.scaling.MonsterScaler;
import com.summy.threat.scaling.ScalingState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 传奇动作统一伤害、目标域和位移工具。 */
public final class LegendaryActionCombat {
	public enum DamageKind {
		MOB_ATTACK,
		INDIRECT_MAGIC,
		EXPLOSION,
		SONIC_BOOM
	}

	private LegendaryActionCombat() {
	}

	public static boolean hit(LegendaryActionContext context, LivingEntity target,
			DamageKind kind, double base) {
		LivingEntity owner = context.owner();
		if (context.cancelled() || !target.isAlive()) {
			return false;
		}
		DamageSource source = source(owner, kind);
		int tl = owner.getPersistentData().contains(MonsterScaler.TAG_TL)
				? ScalingState.clampTl(owner.getPersistentData().getInt(MonsterScaler.TAG_TL))
				: 20;
		float amount = LegendaryActionMath.damage(base,
				ScalingState.relativeAttackMultiplier(tl), ScalingState.rAtk(),
				LeaderTargeting.isPlayerSide(target));
		return LegendaryDamageSettlement.hit(target, source, () ->
				ThreatHitContext.call(target, source, ThreatCategory.LEGENDARY_ACTION,
						context.action().id(), () -> target.hurt(source, amount))) && !context.cancelled();
	}

	public static DamageSource source(LivingEntity owner, DamageKind kind) {
		return switch (kind) {
			case MOB_ATTACK -> owner.damageSources().mobAttack(owner);
			case INDIRECT_MAGIC -> owner.damageSources().indirectMagic(owner, owner);
			case EXPLOSION -> owner.damageSources().explosion(owner, owner);
			case SONIC_BOOM -> owner.damageSources().sonicBoom(owner);
		};
	}

	public static List<LivingEntity> defaultTargets(LegendaryActionContext context,
			Vec3 center, double radius, boolean includeTestTarget) {
		return targets(context, center, radius, target ->
				LeaderTargeting.isTarget(context.owner(), target)
						|| (includeTestTarget && context.test() && target == context.target()));
	}

	public static List<LivingEntity> cylinderTargets(LegendaryActionContext context, Vec3 center,
			double radius, double below, double above) {
		return targets(context, center, radius, below, above, target ->
				LeaderTargeting.isTarget(context.owner(), target)
						|| (context.test() && target == context.target()), true);
	}

	public static List<Player> playerTargets(LegendaryActionContext context,
			Vec3 center, double radius) {
		List<Player> out = new ArrayList<>();
		for (LivingEntity target : targets(context, center, radius,
				entity -> entity instanceof Player)) {
			out.add((Player) target);
		}
		return out;
	}

	public static List<LivingEntity> targetsAt(LegendaryActionContext context,
			Vec3 center, double radius) {
		return targets(context, center, radius, target -> true);
	}

	public static LivingEntity nearestValidTarget(LegendaryActionContext context,
			Vec3 center, double radius) {
		return defaultTargets(context, center, radius, true).stream()
				.min(Comparator.comparingDouble(target -> target.position().distanceToSqr(center)))
				.orElse(null);
	}

	public static boolean hasLineOfSight(LivingEntity owner, LivingEntity target) {
		if (!(owner.level() instanceof ServerLevel level)) {
			return false;
		}
		Vec3 start = target.getEyePosition();
		Vec3 end = owner.getEyePosition();
		return level.clip(new net.minecraft.world.level.ClipContext(start, end,
				net.minecraft.world.level.ClipContext.Block.COLLIDER,
				net.minecraft.world.level.ClipContext.Fluid.NONE, target)).getType()
				== net.minecraft.world.phys.HitResult.Type.MISS;
	}

	public static boolean hasWavePath(ServerLevel level, Vec3 center,
			LivingEntity target) {
		Vec3 start = center.add(0.0D, 0.25D, 0.0D);
		Vec3 end = target.position().add(0.0D, 0.25D, 0.0D);
		return level.clip(new net.minecraft.world.level.ClipContext(start, end,
				net.minecraft.world.level.ClipContext.Block.COLLIDER,
				net.minecraft.world.level.ClipContext.Fluid.NONE, target)).getType()
				== net.minecraft.world.phys.HitResult.Type.MISS;
	}

	public static Vec3 groundProjection(LivingEntity owner, double distance) {
		Vec3 start = owner.position();
		Vec3 end = start.subtract(0.0D, distance, 0.0D);
		var hit = owner.level().clip(new net.minecraft.world.level.ClipContext(start, end,
				net.minecraft.world.level.ClipContext.Block.COLLIDER,
				net.minecraft.world.level.ClipContext.Fluid.NONE, owner));
		return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS
				? start : hit.getLocation();
	}

	public static void push(LivingEntity target, Vec3 from, double horizontal,
			double vertical, String eventId, long expiresAt) {
		Vec3 delta = target.position().subtract(from);
		Vec3 flat = new Vec3(delta.x, 0.0D, delta.z);
		if (flat.lengthSqr() < 1.0E-6D) {
			flat = new Vec3(0.0D, 0.0D, 1.0D);
		}
		Vec3 direction = flat.normalize().scale(horizontal);
		ThreatMovementContext.run(target, eventId, expiresAt,
				() -> {
					target.push(direction.x, vertical, direction.z);
					syncVelocity(target);
				});
	}

	public static void pull(LivingEntity target, Vec3 center, double horizontal,
			String eventId, long expiresAt) {
		Vec3 delta = center.subtract(target.position());
		Vec3 flat = new Vec3(delta.x, 0.0D, delta.z);
		if (flat.lengthSqr() < 1.0E-6D) {
			return;
		}
		Vec3 movement = target.getDeltaMovement().add(
				flat.normalize().scale(horizontal * (1.0D - target.getAttributeValue(
						net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE))));
		ThreatMovementContext.run(target, eventId, expiresAt,
				() -> applyVelocity(target, movement));
	}

	public static void applyVelocity(LivingEntity target, Vec3 velocity) {
		target.setDeltaMovement(velocity);
		target.hasImpulse = true;
		syncVelocity(target);
	}

	private static void syncVelocity(LivingEntity target) {
		target.hurtMarked = true;
		if (target instanceof ServerPlayer player) {
			player.connection.send(new ClientboundSetEntityMotionPacket(player));
		}
	}

	private static List<LivingEntity> targets(LegendaryActionContext context,
			Vec3 center, double radius,
			java.util.function.Predicate<LivingEntity> predicate) {
		return targets(context, center, radius, radius, radius, predicate, false);
	}

	private static List<LivingEntity> targets(LegendaryActionContext context, Vec3 center, double radius,
			double below, double above, java.util.function.Predicate<LivingEntity> predicate, boolean cylinder) {
		if (context.cancelled()) {
			return List.of();
		}
		ServerLevel level = context.level();
		AABB box = new AABB(center.x - radius, center.y - below, center.z - radius,
				center.x + radius, center.y + above, center.z + radius).inflate(1.0E-6D);
		List<LivingEntity> out = new ArrayList<>();
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box)) {
			Vec3 delta = target.position().subtract(center);
			double distanceSquared = cylinder ? delta.horizontalDistanceSqr() : delta.lengthSqr();
			if (!target.isAlive() || delta.y < -below || delta.y > above || distanceSquared > radius * radius
					|| !predicate.test(target)) {
				continue;
			}
			if (target instanceof Player player
					&& (player.isCreative() || player.isSpectator())) {
				continue;
			}
			out.add(target);
		}
		return out;
	}
}
