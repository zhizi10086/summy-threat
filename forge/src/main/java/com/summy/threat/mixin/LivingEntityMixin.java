package com.summy.threat.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.summy.threat.combat.ArmorDamageFormula;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 用自定义公式替换原版护甲减伤那一步。
 *
 * <p>只包住 {@code getDamageAfterArmorAbsorb} 内对 {@code CombatRules.getDamageAfterAbsorb} 的
 * 这一次调用，所以：
 * <ul>
 *     <li>{@code hurtArmor}（护甲耐久消耗）与 {@code DamageTypeTags.BYPASSES_ARMOR} 闸门都在
 *         调用点之外，行为完全保持原版；</li>
 *     <li>不改 {@code CombatRules} 本体，与其它改 CombatRules 的模组冲突面最小；</li>
 *     <li>后续的保护附魔、抗性、伤害吸收、无敌帧全都照原版走。</li>
 * </ul>
 *
 * <p>关闭总开关（{@code damage_reduction.enable_custom_armor_formula}）时原样返回原版结果。
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@ModifyExpressionValue(
			method = "getDamageAfterArmorAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/damagesource/CombatRules;getDamageAfterAbsorb(FFF)F"
			)
	)
	private float summyThreat$customArmorFormula(float original, DamageSource source, float amount) {
		if (!ArmorDamageFormula.enabled()) {
			return original;
		}
		LivingEntity self = (LivingEntity) (Object) this;
		return (float) ArmorDamageFormula.armorAbsorb(self, amount);
	}
}
