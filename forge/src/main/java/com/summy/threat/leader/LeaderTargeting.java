package com.summy.threat.leader;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/** 区域首领技能统一目标域：玩家、玩家友军、施法者当前攻击目标。 */
public final class LeaderTargeting {
	private static final String BETRAYAL_TARGET = "summy_reliquary:betrayal_target";

	private LeaderTargeting() {
	}

	public static boolean isBetrayalTarget(LivingEntity entity) {
		return entity.getPersistentData().getLong(BETRAYAL_TARGET) > entity.level().getGameTime();
	}

	public static LivingEntity currentTarget(LivingEntity owner) {
		return owner instanceof Mob mob ? mob.getTarget() : null;
	}

	public static boolean isPlayerSide(LivingEntity target) {
		if (target instanceof Player player) {
			return !player.isCreative() && !player.isSpectator();
		}
		if (target instanceof OwnableEntity ownable && ownable.getOwnerUUID() != null) {
			if (ownable.getOwner() instanceof Player) {
				return true;
			}
			if (target.level() instanceof ServerLevel level) {
				for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
					if (ownable.getOwnerUUID().equals(player.getUUID())) {
						return true;
					}
				}
			}
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

	public static boolean isTarget(LivingEntity owner, LivingEntity target) {
		if (target == owner || target.isDeadOrDying()) {
			return false;
		}
		if (target instanceof Player player && (player.isCreative() || player.isSpectator())) {
			return false;
		}
		if (isPlayerSide(target)) {
			return true;
		}
		return currentTarget(owner) == target;
	}

	public static boolean isMonsterAlly(LivingEntity owner, LivingEntity target) {
		return target != owner && !target.isDeadOrDying()
				&& target instanceof Enemy
				&& target != currentTarget(owner)
				&& !isBetrayalTarget(target)
				&& !isPlayerSide(target);
	}

	public static boolean isLeaderSideSource(LivingEntity owner, LivingEntity source) {
		return source == owner || isMonsterAlly(owner, source);
	}

	public static List<LivingEntity> offensiveTargets(LivingEntity owner, double radius) {
		List<LivingEntity> out = new ArrayList<>();
		LivingEntity current = currentTarget(owner);
		if (current != null && current.distanceTo(owner) <= radius
				&& isTarget(owner, current)) {
			out.add(current);
		}
		for (LivingEntity target : targets(owner, radius, false)) {
			if (!out.contains(target)) {
				out.add(target);
			}
		}
		return out;
	}

	public static List<LivingEntity> supportTargets(LivingEntity owner, double radius) {
		List<LivingEntity> out = new ArrayList<>();
		out.add(owner);
		out.addAll(targets(owner, radius, true));
		return out;
	}

	private static List<LivingEntity> targets(LivingEntity owner, double radius, boolean support) {
		List<LivingEntity> out = new ArrayList<>();
		if (!(owner.level() instanceof ServerLevel level)) {
			return out;
		}
		AABB box = owner.getBoundingBox().inflate(radius);
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box)) {
			if (target.distanceTo(owner) > radius) {
				continue;
			}
			boolean valid = support ? isMonsterAlly(owner, target) : isTarget(owner, target);
			if (valid) {
				out.add(target);
			}
		}
		return out;
	}
}
