package com.summy.threat.mixin;

import net.minecraft.world.entity.ai.navigation.PathNavigation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 高速追击时临时放宽寻路节点容差。
 *
 * <p>原版 {@code maxDistanceToWaypoint} 默认约为 0.75 格；电涌 4× 冲刺每 tick
 * 会越过节点，导致在节点附近来回转身。这里通过 accessor 在冲刺期间临时提高该值。
 */
@Mixin(PathNavigation.class)
public interface PathNavigationAccessor {
	@Accessor("maxDistanceToWaypoint")
	void summyThreat$setMaxDistanceToWaypoint(float value);

	@Accessor("maxDistanceToWaypoint")
	float summyThreat$getMaxDistanceToWaypoint();
}
