package com.summy.threat.equipment;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** 单次射击或齐射共享的一次性结算状态。 */
public final class RangedShotState {
	private final UUID shotId;
	private final boolean siderealAim;
	private final boolean terminalStarShot;
	private final boolean markCapable;
	private final Set<String> claimed = new HashSet<>();
	private long lastActivity;

	public RangedShotState(UUID shotId, boolean siderealAim, boolean terminalStarShot,
			boolean markCapable, long now) {
		if (shotId == null) {
			throw new IllegalArgumentException("shotId");
		}
		this.shotId = shotId;
		this.siderealAim = siderealAim;
		this.terminalStarShot = terminalStarShot;
		this.markCapable = markCapable;
		this.lastActivity = now;
	}

	public UUID shotId() {
		return shotId;
	}

	public boolean siderealAim() {
		return siderealAim;
	}

	public boolean terminalStarShot() {
		return terminalStarShot;
	}

	public boolean markCapable() {
		return markCapable;
	}

	public boolean claim(String effectId) {
		touch(lastActivity);
		return claimed.add(effectId);
	}

	public void touch(long now) {
		lastActivity = Math.max(lastActivity, now);
	}

	public boolean expired(long now, long ttlTicks) {
		return now - lastActivity >= Math.max(1, ttlTicks);
	}
}
