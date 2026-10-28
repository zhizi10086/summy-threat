package com.summy.threat.api.event;

import com.summy.threat.scaling.MonsterScaler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingEvent;

/** 数值化成功后的只读通知。 */
public class ThreatScalePostEvent extends LivingEvent {
	private final int tl;
	private final MonsterScaler.Kind kind;
	private final double healthMultiplier;
	private final double attackMultiplier;

	public ThreatScalePostEvent(LivingEntity entity, int tl, MonsterScaler.Kind kind,
			double healthMultiplier, double attackMultiplier) {
		super(entity);
		this.tl = tl;
		this.kind = kind;
		this.healthMultiplier = healthMultiplier;
		this.attackMultiplier = attackMultiplier;
	}

	public int getTl() {
		return tl;
	}

	public MonsterScaler.Kind getKind() {
		return kind;
	}

	public double getHealthMultiplier() {
		return healthMultiplier;
	}

	public double getAttackMultiplier() {
		return attackMultiplier;
	}
}
