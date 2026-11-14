package com.summy.threat.leader;

import com.summy.threat.Config;
import com.summy.threat.SummyThreat;
import com.summy.threat.scaling.MonsterScaler;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** 区域首领基础传奇抗性；仅保存运行标签与击退修正，不是实际护盾。 */
public final class LegendaryResistance {
	public static final String TAG_RESISTANCE = "summy_threat:legendary_resistance";
	public static final String TAG_BETRAYAL_IMMUNE =
			"summy_threat:legendary_resistance_betrayal_immune";

	private static final UUID KNOCKBACK_ID =
			UUID.fromString("6f5c1a2e-0009-4a0b-9d1e-000000000501");
	private static final String MODIFIER_NAME = "summy_threat:legendary_resistance/knockback";
	private static final Set<String> NORMALIZING = new HashSet<>();
	private static final Set<String> WARNED_EFFECT_IDS = new HashSet<>();

	private LegendaryResistance() {
	}

	public static boolean enabled() {
		return !Config.SPEC.isLoaded() || Config.LEGENDARY_RESISTANCE_ENABLED.get();
	}

	public static boolean isProtected(LivingEntity entity) {
		return entity.getPersistentData().getBoolean(TAG_RESISTANCE)
				&& MonsterScaler.kindOf(entity) == MonsterScaler.Kind.LEADER;
	}

	public static boolean isBetrayalImmune(LivingEntity entity) {
		return isProtected(entity) && entity.getPersistentData().getBoolean(TAG_BETRAYAL_IMMUNE);
	}

	public static void refresh(LivingEntity entity) {
		if (MonsterScaler.kindOf(entity) != MonsterScaler.Kind.LEADER || !enabled()) {
			clear(entity);
			return;
		}
		entity.getPersistentData().putBoolean(TAG_RESISTANCE, true);
		boolean betrayalImmune = !Config.SPEC.isLoaded() || Config.LEGENDARY_BETRAYAL_IMMUNE.get();
		if (betrayalImmune) {
			entity.getPersistentData().putBoolean(TAG_BETRAYAL_IMMUNE, true);
		} else {
			entity.getPersistentData().remove(TAG_BETRAYAL_IMMUNE);
		}
		applyKnockback(entity);
	}

	public static void clear(LivingEntity entity) {
		entity.getPersistentData().remove(TAG_RESISTANCE);
		entity.getPersistentData().remove(TAG_BETRAYAL_IMMUNE);
		AttributeInstance resistance = entity.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
		if (resistance != null) {
			resistance.removeModifier(KNOCKBACK_ID);
		}
	}

	private static void applyKnockback(LivingEntity entity) {
		AttributeInstance resistance = entity.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
		if (resistance == null) {
			return;
		}
		resistance.removeModifier(KNOCKBACK_ID);
		double value = Config.SPEC.isLoaded() ? Config.LEGENDARY_KNOCKBACK_RESISTANCE.get() : 1.0D;
		if (value > 0.0D) {
			resistance.addTransientModifier(new AttributeModifier(
					KNOCKBACK_ID, MODIFIER_NAME, value, AttributeModifier.Operation.ADDITION));
		}
	}

	@SubscribeEvent
	public static void onEffectApplicable(MobEffectEvent.Applicable event) {
		if (!isProtected(event.getEntity())
				|| event.getEffectInstance().getEffect().isBeneficial()) {
			return;
		}
		String id = effectId(event.getEffectInstance().getEffect());
		if (id != null && immuneEffectIds().contains(id)) {
			event.setResult(Event.Result.DENY);
			return;
		}
		int cap = Math.max(0, negativeEffectCap() - 1);
		MobEffectInstance incoming = event.getEffectInstance();
		if (incoming.getAmplifier() <= cap) {
			return;
		}
		String key = event.getEntity().getUUID() + "|" + id;
		if (!NORMALIZING.add(key)) {
			return;
		}
		event.setResult(Event.Result.DENY);
		try {
			event.getEntity().addEffect(clamped(incoming, cap));
		} finally {
			NORMALIZING.remove(key);
		}
	}

	@SubscribeEvent
	public static void onEffectAdded(MobEffectEvent.Added event) {
		LivingEntity entity = event.getEntity();
		if (!isProtected(entity) || event.getEffectInstance().getEffect().isBeneficial()) {
			return;
		}
		clampCurrentEffects(entity);
	}

	@SubscribeEvent
	public static void onLivingTick(LivingEvent.LivingTickEvent event) {
		LivingEntity entity = event.getEntity();
		if (entity.level().isClientSide() || entity.tickCount % 10 != 0) {
			return;
		}
		refresh(entity);
		if (!isProtected(entity)) {
			return;
		}
		normalizeEffects(entity);
		LeaderVisuals.legendaryResistance(entity);
	}

	@SubscribeEvent
	public static void onLivingDeath(LivingDeathEvent event) {
		clear(event.getEntity());
	}

	@SubscribeEvent
	public static void onServerStopped(ServerStoppedEvent event) {
		NORMALIZING.clear();
		WARNED_EFFECT_IDS.clear();
	}

	private static void normalizeEffects(LivingEntity entity) {
		Set<String> immune = immuneEffectIds();
		for (MobEffectInstance effect : new ArrayList<>(entity.getActiveEffects())) {
			MobEffect mobEffect = effect.getEffect();
			if (mobEffect.isBeneficial()) {
				continue;
			}
			String id = effectId(mobEffect);
			if (id != null && immune.contains(id)) {
				entity.removeEffect(mobEffect);
			}
		}
		clampCurrentEffects(entity);
	}

	private static void clampCurrentEffects(LivingEntity entity) {
		int cap = Math.max(0, negativeEffectCap() - 1);
		for (MobEffectInstance current : new ArrayList<>(entity.getActiveEffects())) {
			if (current.getEffect().isBeneficial() || current.getAmplifier() <= cap) {
				continue;
			}
			replaceClamped(entity, current, cap);
		}
	}

	private static void replaceClamped(LivingEntity entity, MobEffectInstance current, int cap) {
		String key = entity.getUUID() + "|" + effectId(current.getEffect());
		if (!NORMALIZING.add(key)) {
			return;
		}
		try {
			MobEffectInstance clamped = clamped(current, cap);
			entity.removeEffect(current.getEffect());
			entity.addEffect(clamped);
		} finally {
			NORMALIZING.remove(key);
		}
	}

	private static MobEffectInstance clamped(MobEffectInstance instance, int amplifier) {
		MobEffectInstance out = new MobEffectInstance(instance.getEffect(),
				instance.getDuration(), amplifier, instance.isAmbient(), instance.isVisible(),
				instance.showIcon());
		out.setCurativeItems(instance.getCurativeItems());
		return out;
	}

	private static int negativeEffectCap() {
		return Config.SPEC.isLoaded() ? Config.LEGENDARY_NEGATIVE_EFFECT_LEVEL_CAP.get() : 4;
	}

	private static Set<String> immuneEffectIds() {
		Set<String> out = new HashSet<>();
		if (!Config.SPEC.isLoaded()) {
			out.add("minecraft:blindness");
			out.add("minecraft:darkness");
			out.add("minecraft:nausea");
			out.add("minecraft:levitation");
			return out;
		}
		for (Object raw : Config.LEGENDARY_IMMUNE_NEGATIVE_EFFECTS.get()) {
			if (!(raw instanceof String text) || text.isBlank()) {
				continue;
			}
			String normalized = text.trim().toLowerCase(Locale.ROOT);
			ResourceLocation id = ResourceLocation.tryParse(normalized);
			MobEffect effect = id == null ? null : BuiltInRegistries.MOB_EFFECT.get(id);
			if (effect == null) {
				if (WARNED_EFFECT_IDS.add(normalized)) {
					SummyThreat.LOGGER.warn("[Summy Threat] 传奇抗性免疫列表包含未知 MobEffect：{}",
							normalized);
				}
				continue;
			}
			out.add(normalized);
		}
		return out;
	}

	private static String effectId(MobEffect effect) {
		ResourceLocation id = BuiltInRegistries.MOB_EFFECT.getKey(effect);
		return id == null ? null : id.toString();
	}
}
