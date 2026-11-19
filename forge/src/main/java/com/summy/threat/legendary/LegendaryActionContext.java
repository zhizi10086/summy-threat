package com.summy.threat.legendary;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** 传奇动作启动时的服务器上下文。 */
public final class LegendaryActionContext {
	private final ServerLevel level;
	private final LivingEntity owner;
	private final LivingEntity target;
	private final LegendaryActionId action;
	private final Vec3 center;
	private final boolean test;
	private boolean cancelled;

	public LegendaryActionContext(ServerLevel level, LivingEntity owner, LivingEntity target,
			LegendaryActionId action, Vec3 center, boolean test) {
		this.level = level;
		this.owner = owner;
		this.target = target;
		this.action = action;
		this.center = center;
		this.test = test;
	}

	public ServerLevel level() { return level; }
	public LivingEntity owner() { return owner; }
	public LivingEntity target() { return target; }
	public LegendaryActionId action() { return action; }
	public Vec3 center() { return center; }
	public boolean test() { return test; }
	boolean cancelled() { return cancelled; }
	void cancel() { cancelled = true; }
}
