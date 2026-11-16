package com.summy.threat.equipment;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.UUID;

/**
 * 星渊武器附伤使用的独立魔法伤害。
 *
 * <p>该伤害只绕过护甲，仍会进入保护、抗性、吸收和 A37 的 TL 魔法抗性；独立伤害类型也避免
 * SE 死神祝福与 SR 定值真伤出伤乘区再次放大已经计算过的武器附伤。</p>
 */
public final class PrismMagicDamage {
	private static final ResourceKey<DamageType> TYPE = ResourceKey.create(
			Registries.DAMAGE_TYPE,
			new ResourceLocation("summy_threat", "star_prism_magic"));
	private static final UUID CRIT_SUPPRESSION =
			UUID.fromString("6f5c1a2e-0018-4a0b-9d1e-000000000808");
	private static final ResourceLocation CRIT_CHANCE_ID =
			new ResourceLocation("attributeslib", "crit_chance");
	private static final ThreadLocal<Boolean> SUPPRESSING = ThreadLocal.withInitial(
			() -> false);

	private PrismMagicDamage() {
	}

	public static boolean hurt(LivingEntity target, LivingEntity owner, float amount) {
		if (target == null || owner == null || target.level().isClientSide()
				|| amount <= 0.0F) {
			return false;
		}
		DamageSource source = source(target, owner);
		AttributeInstance crit = null;
		boolean suppress = !SUPPRESSING.get() && owner instanceof ServerPlayer;
		if (suppress) {
			Attribute attribute = BuiltInRegistries.ATTRIBUTE.get(CRIT_CHANCE_ID);
			crit = attribute == null ? null : owner.getAttribute(attribute);
			if (crit != null) {
				double value = crit.getValue();
				if (Math.abs(value) > 1.0E-9D) {
					crit.addTransientModifier(new AttributeModifier(CRIT_SUPPRESSION,
							"summy_threat:prism_magic_no_crit", -value,
							AttributeModifier.Operation.ADDITION));
				} else {
					crit = null;
				}
			}
		}
		// 附伤是独立命中，不继承上一击尚未结束的原版受击无敌帧。
		try {
			if (crit != null) {
				SUPPRESSING.set(true);
			}
			target.invulnerableTime = 0;
			return target.hurt(source, amount);
		} finally {
			target.invulnerableTime = 0;
			if (crit != null) {
				crit.removeModifier(CRIT_SUPPRESSION);
				SUPPRESSING.remove();
			}
		}
	}

	public static DamageSource source(LivingEntity target, LivingEntity owner) {
		var registry = target.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
		return new DamageSource(registry.getHolderOrThrow(TYPE), owner, owner);
	}
}
