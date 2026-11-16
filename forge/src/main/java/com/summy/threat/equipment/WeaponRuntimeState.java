package com.summy.threat.equipment;

import java.util.UUID;

/** 单个玩家的武器会话状态；不保存到 ItemStack。 */
public final class WeaponRuntimeState {
	private UUID markedTarget;
	private long markExpiresAt;
	private UUID pursuitTarget;
	private String pursuitWeapon = "";
	private long pursuitLastHit;
	private int pursuitStacks;
	private int starfireHits;
	private int starfallHits;
	private String mainhand = "";

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
		if (!pursuitMatches(target, weapon, now, resetTicks)) {
			clearPursuit();
			return 0.0D;
		}
		return pursuitStacks * Math.max(0.0D, percentPerStack) / 100.0D;
	}

	public int pursuitHit(UUID target, String weapon, long now, int maxStacks, int resetTicks) {
		if (!pursuitMatches(target, weapon, now, resetTicks)) {
			pursuitTarget = target;
			pursuitWeapon = weapon == null ? "" : weapon;
			pursuitStacks = 0;
		}
		pursuitStacks = Math.min(Math.max(1, maxStacks), pursuitStacks + 1);
		pursuitLastHit = now;
		return pursuitStacks;
	}

	public void changeMainhand(String weapon) {
		String normalized = weapon == null ? "" : weapon;
		if (!mainhand.equals(normalized)) {
			mainhand = normalized;
			clearPursuit();
		}
	}

	public int pursuitStacks() {
		return pursuitStacks;
	}

	public int activePursuitStacks(long now, int resetTicks) {
		if (pursuitTarget == null || now - pursuitLastHit >= Math.max(1, resetTicks)) {
			clearPursuit();
			return 0;
		}
		return pursuitStacks;
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
	}

	private boolean pursuitMatches(UUID target, String weapon, long now, int resetTicks) {
		return target != null && target.equals(pursuitTarget)
				&& (weapon == null ? "" : weapon).equals(pursuitWeapon)
				&& now - pursuitLastHit < Math.max(1, resetTicks);
	}

	private void clearPursuit() {
		pursuitTarget = null;
		pursuitWeapon = "";
		pursuitLastHit = 0L;
		pursuitStacks = 0;
	}
}
