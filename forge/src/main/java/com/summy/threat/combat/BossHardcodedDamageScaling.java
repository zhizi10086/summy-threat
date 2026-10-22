package com.summy.threat.combat;

import com.summy.threat.scaling.MonsterScaler;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 把 Boss 生成时已确定的攻击倍率接到原版硬编码伤害。
 *
 * <p>末影龙、凋灵没有可用的 ATTACK_DAMAGE 属性；远古守卫者的激光又固定分为魔法段和属性段。
 * 本层只接收硬编码段，无法通过属性倍率覆盖的部分才在此乘一次，避免古守属性段被重复放大。
 */
public final class BossHardcodedDamageScaling {
	private BossHardcodedDamageScaling() {
	}

	@SubscribeEvent(priority = EventPriority.HIGH)
	public static void onLivingHurt(LivingHurtEvent event) {
		if (event.isCanceled() || event.getAmount() <= 0.0F) {
			return;
		}
		DamageSource source = event.getSource();
		LivingEntity owner = causingOwner(source);
		if (owner == null || event.getEntity() == owner || !isHardcodedAttack(owner, source)) {
			return;
		}
		double multiplier = MonsterScaler.hardcodedAttackMultiplier(owner);
		if (multiplier != 1.0D) {
			event.setAmount((float) (event.getAmount() * multiplier));
		}
	}

	/**
	 * 回溯伤害的间接施法者。凋灵头颅和头颅爆炸的 causing entity 都是凋灵，
	 * 但无主的投射物或普通爆炸不会命中本层。
	 */
	static LivingEntity causingOwner(DamageSource source) {
		Entity causing = source.getEntity();
		if (causing instanceof LivingEntity living) {
			return living;
		}
		Entity direct = source.getDirectEntity();
		if (direct instanceof Projectile projectile && projectile.getOwner() instanceof LivingEntity living) {
			return living;
		}
		return null;
	}

	/** 只列出 A15 明确接管的固定伤害来源。 */
	static boolean isHardcodedAttack(LivingEntity owner, DamageSource source) {
		Entity direct = source.getDirectEntity();
		if (owner instanceof EnderDragon) {
			return source.is(DamageTypes.MOB_ATTACK);
		}
		if (owner instanceof WitherBoss) {
			return source.is(DamageTypes.WITHER_SKULL)
					|| ((source.is(DamageTypes.EXPLOSION) || source.is(DamageTypes.PLAYER_EXPLOSION))
							&& (direct instanceof WitherBoss || direct instanceof WitherSkull));
		}
		if (owner instanceof ElderGuardian) {
			return source.is(DamageTypes.INDIRECT_MAGIC) || source.is(DamageTypes.THORNS);
		}
		return false;
	}
}
