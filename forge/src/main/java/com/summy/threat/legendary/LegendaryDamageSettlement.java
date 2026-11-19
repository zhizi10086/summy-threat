package com.summy.threat.legendary;

import com.summy.threat.equipment.ThreatCategory;
import com.summy.threat.equipment.ThreatHitContext;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.function.BooleanSupplier;

/** 以实际扣血调用为作用域，隔离取消事件和伤害源重入。 */
public final class LegendaryDamageSettlement {
	private static final ThreadLocal<Damage> ACTIVE = new ThreadLocal<>();
	private static final ThreadLocal<Hit> OUTGOING = new ThreadLocal<>();

	private LegendaryDamageSettlement() {
	}

	static boolean hit(LivingEntity target, DamageSource source, BooleanSupplier action) {
		Hit parent = OUTGOING.get();
		Hit hit = new Hit(target, source);
		OUTGOING.set(hit);
		try {
			action.getAsBoolean();
			return hit.settled;
		} finally {
			restore(OUTGOING, parent);
		}
	}

	public static void observe(LivingEntity target, DamageSource source, Runnable action) {
		Damage parent = ACTIVE.get();
		Hit outgoing = OUTGOING.get();
		var context = ThreatHitContext.current(target, source);
		boolean ownHit = outgoing != null && outgoing.target == target && outgoing.source == source
				&& context != null && context.category() == ThreatCategory.LEGENDARY_ACTION
				&& (parent == null || parent.target != target || parent.source != source);
		boolean riposte = LegendaryActionState.selection(target) == LegendaryActionId.LEGENDARY_RIPOSTE;
		boolean echo = EchoSentenceService.active(target);
		if (parent == null && (target.level().isClientSide() || !(ownHit || riposte || echo))) {
			action.run();
			return;
		}
		Damage damage = new Damage(target, source);
		ACTIVE.set(damage);
		try {
			action.run();
		} finally {
			restore(ACTIVE, parent);
		}
		boolean settled = damage.observed && !damage.cancelled
				&& ((damage.finalAmount > 0.0F && target.getHealth() < damage.healthBeforeApply)
						|| damage.absorptionConsumed);
		if (settled) {
			if (ownHit) {
				outgoing.settled = true;
			}
			if (riposte) {
				LegendaryRiposteService.onSettledDamage(target, source, damage.beforeAbsorption);
			}
			if (echo) {
				EchoSentenceService.onSettledDamage(target, source);
			}
		}
	}

	public static float beforeAbsorption(LivingEntity target, DamageSource source, float amount) {
		Damage damage = ACTIVE.get();
		if (damage != null && damage.target == target && damage.source == source) {
			damage.beforeAbsorption = amount;
			damage.absorptionBeforeApply = target.getAbsorptionAmount();
		}
		return amount;
	}

	@SubscribeEvent(priority = EventPriority.MONITOR, receiveCanceled = true)
	public static void onDamage(LivingDamageEvent event) {
		Damage damage = ACTIVE.get();
		if (damage != null && damage.target == event.getEntity() && damage.source == event.getSource()) {
			damage.observed = true;
			damage.cancelled = event.isCanceled();
			damage.finalAmount = event.getAmount();
			damage.healthBeforeApply = damage.target.getHealth();
			damage.absorptionConsumed = damage.target.getAbsorptionAmount() < damage.absorptionBeforeApply;
		}
	}

	private static <T> void restore(ThreadLocal<T> slot, T parent) {
		if (parent == null) {
			slot.remove();
		} else {
			slot.set(parent);
		}
	}

	private static final class Damage {
		final LivingEntity target;
		final DamageSource source;
		float healthBeforeApply;
		float absorptionBeforeApply;
		float finalAmount;
		boolean absorptionConsumed;
		float beforeAbsorption;
		boolean observed;
		boolean cancelled;

		Damage(LivingEntity target, DamageSource source) {
			this.target = target;
			this.source = source;
		}
	}

	private static final class Hit {
		final LivingEntity target;
		final DamageSource source;
		boolean settled;

		Hit(LivingEntity target, DamageSource source) {
			this.target = target;
			this.source = source;
		}
	}
}
