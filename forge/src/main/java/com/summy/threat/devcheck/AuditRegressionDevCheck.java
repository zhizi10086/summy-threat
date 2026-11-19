package com.summy.threat.devcheck;

import com.summy.threat.SummyThreat;
import com.summy.threat.api.ThreatConfigApi;
import com.summy.threat.eximus.EximusShield;
import com.summy.threat.legendary.JadeLightBeamEntity;
import com.summy.threat.legendary.LegendaryActionCombat;
import com.summy.threat.legendary.LegendaryActionContext;
import com.summy.threat.legendary.LegendaryActionId;
import com.summy.threat.legendary.LegendaryActionStateData;
import com.summy.threat.legendary.ModLegendaryEntities;
import com.summy.threat.scaling.MonsterScaler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 在隔离专用服验证读档、受击帧和伤害取消。 */
final class AuditRegressionDevCheck {
	private AuditRegressionDevCheck() {
	}

	static List<String> run(ServerLevel level) {
		List<String> failures = new ArrayList<>();
		String tier = "environment_tier.mode";
		Object oldTier = ThreatConfigApi.get(tier);
		ThreatConfigApi.set(tier, "free");
		var owner = EntityType.ZOMBIE.create(level);
		var target = EntityType.ZOMBIE.create(level);
		var probe = new CancellationProbe(target);
		MinecraftForge.EVENT_BUS.register(probe);
		try {
			LegendaryActionContext context = new LegendaryActionContext(level, owner, target,
					LegendaryActionId.JADE_LIGHT, owner.position(), true);
			for (int stage = 1; stage <= 3; stage++) {
				probe.stage = stage;
				target.invulnerableTime = 0;
				float health = target.getHealth();
				check(failures, "伤害阶段 " + stage + " 取消后无有效命中",
						!hit(context, target) && target.getHealth() == health);
			}
			probe.stage = 0;
			target.invulnerableTime = 0;
			check(failures, "传奇首击有效，下一击遵守原版受击帧",
					hit(context, target) && target.invulnerableTime > 0 && !hit(context, target));
			target.invulnerableTime = 0;
			target.setAbsorptionAmount(100);
			float before = target.getHealth();
			check(failures, "全吸收的有效伤害可归因至本次传奇命中",
					hit(context, target) && target.getHealth() == before && target.getAbsorptionAmount() < 100);

			var eximus = EntityType.ZOMBIE.create(level);
			MonsterScaler.scaleNow(eximus, 20, MonsterScaler.Kind.EXIMUS, false);
			eximus.setHealth(71.0F);
			double max = eximus.getMaxHealth();
			int modifiers = eximus.getAttribute(Attributes.MAX_HEALTH).getModifiers().size();
			double shield = EximusShield.max(eximus) * 0.25D;
			eximus.getPersistentData().putDouble(EximusShield.TAG_SHIELD, shield);
			CompoundTag saved = eximus.saveWithoutId(new CompoundTag());
			var restored = EntityType.ZOMBIE.create(level);
			restored.load(saved);
			check(failures, "缩放属性、当前生命及护盾跨 NBT 重载",
					restored.getHealth() == 71.0F && restored.getMaxHealth() == max
							&& restored.getAttribute(Attributes.MAX_HEALTH).getModifiers().size() == modifiers
							&& Math.abs(EximusShield.current(restored) - shield) < 0.001D);
			CompoundTag legacy = saved.copy();
			legacy.remove("Attributes");
			legacy.getCompound("ForgeData").remove("summy_threat:persistent_attributes");
			restored = EntityType.ZOMBIE.create(level);
			restored.load(legacy);
			check(failures, "旧存档按原始血量恢复，且临时属性只应用一次",
					restored.getHealth() == 71.0F && restored.getMaxHealth() == max
							&& Math.abs(EximusShield.current(restored) - shield) < 0.001D);

			MonsterScaler.scaleNow(owner, 20, MonsterScaler.Kind.BOSS, false);
			LegendaryActionStateData.setSelection(owner.getPersistentData(), LegendaryActionId.JADE_LIGHT);
			double ownerMax = owner.getMaxHealth();
			Object oldTl = ThreatConfigApi.get("scaling_debug.global_tl");
			try {
				ThreatConfigApi.setMany(Map.of("scaling_debug.global_tl", -2));
				check(failures, "关闭 TL 后保留基础属性与持久技能，暂停护盾",
						owner.getMaxHealth() == ownerMax && !EximusShield.isActive(restored)
								&& LegendaryActionStateData.selection(owner.getPersistentData()) == LegendaryActionId.JADE_LIGHT);
			} finally {
				ThreatConfigApi.set("scaling_debug.global_tl", oldTl);
			}
			JadeLightBeamEntity beam = ModLegendaryEntities.JADE_LIGHT_BEAM.get().create(level);
			check(failures, "翠玉光柱不可写入存档", !beam.save(new CompoundTag()));
			beam.load(new CompoundTag());
			check(failures, "读入旧光柱时立即丢弃", beam.isRemoved());
		} finally {
			MinecraftForge.EVENT_BUS.unregister(probe);
			ThreatConfigApi.set(tier, oldTier);
		}
		return failures;
	}

	private static boolean hit(LegendaryActionContext context, LivingEntity target) {
		return LegendaryActionCombat.hit(context, target, LegendaryActionCombat.DamageKind.INDIRECT_MAGIC, 0.4D);
	}

	private static void check(List<String> failures, String name, boolean pass) {
		SummyThreat.LOGGER.info("[ST-AUDIT] {} {}", pass ? "PASS" : "FAIL", name);
		if (!pass) {
			failures.add(name);
		}
	}

	public static final class CancellationProbe {
		private final LivingEntity target;
		int stage;

		CancellationProbe(LivingEntity target) {
			this.target = target;
		}

		@SubscribeEvent
		public void onAttack(LivingAttackEvent event) {
			if (stage == 1 && event.getEntity() == target) {
				event.setCanceled(true);
			}
		}

		@SubscribeEvent
		public void onHurt(LivingHurtEvent event) {
			if (stage == 2 && event.getEntity() == target) {
				event.setCanceled(true);
			}
		}

		@SubscribeEvent
		public void onDamage(LivingDamageEvent event) {
			if (stage == 3 && event.getEntity() == target) {
				event.setCanceled(true);
			}
		}
	}
}
