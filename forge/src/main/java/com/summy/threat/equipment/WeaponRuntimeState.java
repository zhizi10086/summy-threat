package com.summy.threat.equipment;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 单个玩家的武器会话状态；不保存到 ItemStack。 */
public final class WeaponRuntimeState {
	private static final int MAX_PURSUIT_TARGETS = 8;
	private UUID markedTarget;
	private long markExpiresAt;
	private final Map<UUID, Pursuit> meleePursuits = new HashMap<>();
	private final Map<UUID, Pursuit> rangedPursuits = new HashMap<>();
	private UUID lastMeleePursuitTarget;
	private UUID lastRangedPursuitTarget;
	private int starfireHits;
	private int starfallHits;
	private String mainhand = "";
	private String rangedMainhand = "";

	public boolean sharedMarkActive(UUID target, long now) {
		if (markedTarget == null || markedTarget.equals(target) == false) {
			return false;
		}
		if (now >= markExpiresAt) {
			markedTarget = null;
			markExpiresAt = 0L;
			return false;
		}
		return true;
	}

	public void markHit(UUID target, long now, int durationTicks) {
		if (target == null) {
			return;
		}
		markedTarget = target;
		markExpiresAt = now + Math.max(1, durationTicks);
	}

	public double pursuitMultiplier(UUID target, String weapon, long now, int resetTicks) {
		return pursuitMultiplier(target, weapon, now, resetTicks, 6.0D);
	}

	public double pursuitMultiplier(UUID target, String weapon, long now, int resetTicks,
			double percentPerStack) {
		return pursuitMultiplier(meleePursuits, target, weapon, now, resetTicks,
				percentPerStack);
	}

	public double rangedPursuitMultiplier(UUID target, String weapon, long now,
			int resetTicks, double percentPerStack) {
		return pursuitMultiplier(rangedPursuits, target, weapon, now, resetTicks,
				percentPerStack);
	}

	public int pursuitHit(UUID target, String weapon, long now, int maxStacks, int resetTicks) {
		int stacks = pursuitHit(meleePursuits, target, weapon, now, maxStacks, resetTicks);
		lastMeleePursuitTarget = target;
		return stacks;
	}

	public int rangedPursuitHit(UUID target, String weapon, long now, int maxStacks,
			int resetTicks) {
		int stacks = pursuitHit(rangedPursuits, target, weapon, now, maxStacks, resetTicks);
		lastRangedPursuitTarget = target;
		return stacks;
	}

	public void changeMainhand(String weapon) {
		String normalized = weapon == null ? "" : weapon;
		if (!mainhand.equals(normalized)) {
			mainhand = normalized;
			meleePursuits.clear();
			lastMeleePursuitTarget = null;
		}
	}

	public void changeRangedMainhand(String weapon) {
		String normalized = weapon == null ? "" : weapon;
		if (!rangedMainhand.equals(normalized)) {
			rangedMainhand = normalized;
			rangedPursuits.clear();
			lastRangedPursuitTarget = null;
		}
	}

	public int pursuitStacks() {
		Pursuit pursuit = meleePursuits.get(lastMeleePursuitTarget);
		return pursuit == null ? 0 : pursuit.stacks();
	}

	public int activePursuitStacks(long now, int resetTicks) {
		cleanupExpired(meleePursuits, now, resetTicks);
		Pursuit pursuit = meleePursuits.get(lastMeleePursuitTarget);
		return pursuit == null ? 0 : pursuit.stacks();
	}

	public boolean starfireHit(int every) {
		starfireHits++;
		if (starfireHits >= Math.max(1, every)) {
			starfireHits = 0;
			return true;
		}
		return false;
	}

	public boolean starfallHit(int every) {
		starfallHits++;
		if (starfallHits >= Math.max(1, every)) {
			starfallHits = 0;
			return true;
		}
		return false;
	}

	public void clearSession() {
		markedTarget = null;
		markExpiresAt = 0L;
		clearPursuit();
		starfireHits = 0;
		starfallHits = 0;
		mainhand = "";
		rangedMainhand = "";
	}

	private void clearPursuit() {
		meleePursuits.clear();
		rangedPursuits.clear();
		lastMeleePursuitTarget = null;
		lastRangedPursuitTarget = null;
	}

	private static double pursuitMultiplier(Map<UUID, Pursuit> pursuits, UUID target,
			String weapon, long now, int resetTicks, double percentPerStack) {
		Pursuit pursuit = pursuits.get(target);
		if (pursuit == null || !(weapon == null ? "" : weapon).equals(pursuit.weapon())
				|| now - pursuit.lastHit() >= Math.max(1, resetTicks)) {
			pursuits.remove(target);
			return 0.0D;
		}
		return pursuit.stacks() * Math.max(0.0D, percentPerStack) / 100.0D;
	}

	private static int pursuitHit(Map<UUID, Pursuit> pursuits, UUID target,
			String weapon, long now, int maxStacks, int resetTicks) {
		cleanupExpired(pursuits, now, resetTicks);
		String normalizedWeapon = weapon == null ? "" : weapon;
		Pursuit pursuit = pursuits.get(target);
		if (pursuit == null || !normalizedWeapon.equals(pursuit.weapon())) {
			if (!pursuits.containsKey(target) && pursuits.size() >= MAX_PURSUIT_TARGETS) {
				pursuits.entrySet().stream()
						.min(Map.Entry.comparingByValue(
								java.util.Comparator.comparingLong(Pursuit::lastHit)))
						.map(Map.Entry::getKey)
						.ifPresent(pursuits::remove);
			}
			pursuit = new Pursuit(normalizedWeapon, now, 0);
		}
		int stacks = Math.min(Math.max(1, maxStacks), pursuit.stacks() + 1);
		pursuits.put(target, new Pursuit(normalizedWeapon, now, stacks));
		return stacks;
	}

	private static void cleanupExpired(Map<UUID, Pursuit> pursuits, long now,
			int resetTicks) {
		pursuits.entrySet().removeIf(entry ->
				now - entry.getValue().lastHit() >= Math.max(1, resetTicks));
	}

	private record Pursuit(String weapon, long lastHit, int stacks) {
	}
}
