package com.summy.threat.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.summy.threat.pcf.SpawnRegion;
import net.minecraft.server.commands.SummonCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** 原版带 NBT 的 /summon 会跳过 finalizeSpawn，在可信入口记录来源。 */
@Mixin(SummonCommand.class)
public abstract class SummonCommandMixin {
	@WrapOperation(method = "createEntity", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/server/level/ServerLevel;tryAddFreshEntityWithPassengers(Lnet/minecraft/world/entity/Entity;)Z"))
	private static boolean summyThreat$markCommandOrigin(ServerLevel level, Entity entity,
			Operation<Boolean> original) {
		entity.getSelfAndPassengers().filter(Mob.class::isInstance).forEach(mob ->
				mob.getPersistentData().putString(SpawnRegion.TAG_SPAWN_TYPE, MobSpawnType.COMMAND.name()));
		return original.call(level, entity);
	}
}
