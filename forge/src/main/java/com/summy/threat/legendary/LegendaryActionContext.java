package com.summy.threat.legendary;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** 传奇动作启动时的服务器上下文。 */
public record LegendaryActionContext(
		ServerLevel level,
		LivingEntity owner,
		LivingEntity target,
		LegendaryActionId action,
		Vec3 center,
		boolean test) {
}
