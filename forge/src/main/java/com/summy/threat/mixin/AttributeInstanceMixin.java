package com.summy.threat.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.summy.threat.eximus.OverguardFloor;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 超宏防护的属性快照地板：护盾存在时，所有 {@link AttributeInstance#getValue()} 读取
 * 都返回 {@code max(原始值, 快照)}。负向 modifier 仍然保留，破盾后清除快照即重新生效。
 */
@Mixin(AttributeInstance.class)
public abstract class AttributeInstanceMixin implements OverguardFloor {
	@Unique
	private double summyThreat$floor = Double.NaN;

	@Override
	public void summyThreat$setFloor(double value) {
		this.summyThreat$floor = value;
	}

	@Override
	public void summyThreat$clearFloor() {
		this.summyThreat$floor = Double.NaN;
	}

	@Override
	public double summyThreat$getFloor() {
		return this.summyThreat$floor;
	}

	@ModifyReturnValue(method = "getValue", at = @At("RETURN"))
	private double summyThreat$applyFloor(double original) {
		double floor = this.summyThreat$floor;
		return Double.isNaN(floor) ? original : Math.max(original, floor);
	}
}
