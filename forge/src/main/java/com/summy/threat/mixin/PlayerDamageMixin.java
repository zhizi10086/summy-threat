package com.summy.threat.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.summy.threat.legendary.LegendaryDamageSettlement;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** 玩家覆盖了实际伤害结算，需要单独记录吸收和事件结果。 */
@Mixin(Player.class)
public abstract class PlayerDamageMixin {
	@WrapMethod(method = "actuallyHurt")
	private void summyThreat$observeSettledDamage(DamageSource source, float amount, Operation<Void> original) {
		LegendaryDamageSettlement.observe((Player) (Object) this, source,
				() -> original.call(source, amount));
	}

	@ModifyExpressionValue(method = "actuallyHurt", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/player/Player;getDamageAfterMagicAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F"))
	private float summyThreat$capturePreAbsorption(float value, DamageSource source) {
		return LegendaryDamageSettlement.beforeAbsorption((Player) (Object) this, source, value);
	}
}
