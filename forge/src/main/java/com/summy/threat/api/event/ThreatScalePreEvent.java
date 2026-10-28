package com.summy.threat.api.event;

import com.summy.threat.scaling.MonsterScaler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.Cancelable;

import java.util.Objects;

/** 数值化前事件：脚本可修改 TL/类别，或取消本次缩放。 */
@Cancelable
public class ThreatScalePreEvent extends LivingEvent {
	private int tl;
	private MonsterScaler.Kind kind;

	public ThreatScalePreEvent(LivingEntity entity, int tl, MonsterScaler.Kind kind) {
		super(entity);
		this.tl = tl;
		this.kind = kind;
	}

	public int getTl() {
		return tl;
	}

	public void setTl(int tl) {
		this.tl = tl;
	}

	public MonsterScaler.Kind getKind() {
		return kind;
	}

	public void setKind(MonsterScaler.Kind kind) {
		this.kind = Objects.requireNonNull(kind, "kind");
	}
}
