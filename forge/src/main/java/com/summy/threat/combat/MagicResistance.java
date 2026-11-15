package com.summy.threat.combat;

import com.summy.threat.Config;
import com.summy.threat.scaling.MonsterScaler;
import com.summy.threat.scaling.ScalingState;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * TL 魔法抗性。
 *
 * <p>只把“仅绕过护甲、但仍受保护附魔/抗性/无敌帧约束”的伤害视为魔法伤害；
 * 真伤、声波和环境伤害由原版标签或 {@link #NON_MAGIC_ARMOR_BYPASS} 排除。
 *
 * <p>本层在 {@link EventPriority#LOW} 修改 {@link LivingHurtEvent} 的名义伤害：
 * Boss 硬编码攻击与首领威压已经结算，后续原版护甲豁免、保护附魔、抗性、
 * 吸收与卓越护盾仍按原有顺序继续执行。
 */
public final class MagicResistance {
	public static final TagKey<DamageType> NON_MAGIC_ARMOR_BYPASS = TagKey.create(
			Registries.DAMAGE_TYPE,
			ResourceLocation.fromNamespaceAndPath("summy_threat", "non_magic_armor_bypass"));

	private MagicResistance() {
	}

	@SubscribeEvent(priority = EventPriority.LOW)
	public static void onLivingHurt(LivingHurtEvent event) {
		if (event.isCanceled() || event.getAmount() <= 0.0F
				|| !Config.SPEC.isLoaded() || !Config.MAGIC_RESISTANCE_ENABLED.get()) {
			return;
		}
		LivingEntity target = event.getEntity();
		if (target.level().isClientSide() || target instanceof Player
				|| !target.getPersistentData().getBoolean(MonsterScaler.TAG_SCALED)
				|| !ScalingState.enabled()
				|| !isMagicDamage(event.getSource())) {
			return;
		}
		double percent = resistancePercent(target);
		if (percent > 0.0D) {
			event.setAmount((float) (event.getAmount() * (1.0D - percent / 100.0D)));
		}
	}

	/** 数据标签判定；新增同类伤害类型无需在 Java 内逐个登记。 */
	public static boolean isMagicDamage(DamageSource source) {
		return source.is(DamageTypeTags.BYPASSES_ARMOR)
				&& !source.is(DamageTypeTags.BYPASSES_EFFECTS)
				&& !source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS)
				&& !source.is(DamageTypeTags.BYPASSES_RESISTANCE)
				&& !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
				&& !source.is(DamageTypeTags.BYPASSES_COOLDOWN)
				&& !source.is(NON_MAGIC_ARMOR_BYPASS);
	}

	/** 当前目标最终法抗百分比；玩家不会进入本方法。 */
	public static double resistancePercent(LivingEntity target) {
		int tl = ScalingState.clampTl(
				target.getPersistentData().getInt(MonsterScaler.TAG_TL));
		MonsterScaler.Kind kind = MonsterScaler.kindOf(target);
		double base = tableFor(kind, tl)[tl].get();
		double scaled = Config.MAGIC_RESISTANCE_SCALE_WITH_ENVIRONMENT_HP.get()
				? base * ScalingState.rHp()
				: base;
		double cap = Config.MAGIC_RESISTANCE_HARD_CAP_PERCENT.get();
		return Math.max(0.0D, Math.min(scaled, cap));
	}

	private static ForgeConfigSpec.DoubleValue[] tableFor(MonsterScaler.Kind kind, int tl) {
		if (kind == MonsterScaler.Kind.LEADER && tl < 10) {
			kind = MonsterScaler.Kind.NORMAL;
		}
		return switch (kind) {
			case EXIMUS -> Config.EXIMUS_MAGIC_RESISTANCE_PER_TL;
			case LEADER, BOSS -> Config.LEADER_BOSS_MAGIC_RESISTANCE_PER_TL;
			default -> Config.NORMAL_MAGIC_RESISTANCE_PER_TL;
		};
	}
}
