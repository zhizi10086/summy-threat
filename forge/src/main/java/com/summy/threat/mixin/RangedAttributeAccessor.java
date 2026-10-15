package com.summy.threat.mixin;

import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 属性上限的写入口。
 *
 * <p>{@code RangedAttribute} 的 minValue / maxValue 是 final 字段，只能由 Mixin 改写；
 * 这里只开放 maxValue（本模组只抬上限，不动下限）。
 *
 * <p>生产环境的字段名是 SRG（maxValue → f_22308_），由 refmap 完成名字翻译，
 * 所以必须走 accessor 而不是反射。
 */
@Mixin(RangedAttribute.class)
public interface RangedAttributeAccessor {
	@Accessor("maxValue")
	@Mutable
	void summyThreat$setMaxValue(double maxValue);
}
