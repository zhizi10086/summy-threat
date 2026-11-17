package com.summy.threat.devcheck;

import com.summy.threat.equipment.EquipmentCategory;
import com.summy.threat.equipment.EximusEquipmentData;
import com.summy.threat.equipment.MeleeWeaponRuntime;
import com.summy.threat.equipment.PrismProjectileData;
import com.summy.threat.equipment.PrismMagicDamage;
import com.summy.threat.equipment.RangedWeaponRuntime;
import com.summy.threat.equipment.StarPrismAffixes;
import com.summy.threat.equipment.StarPrismData;
import com.summy.threat.equipment.WeaponAffixSnapshot;
import com.summy.threat.equipment.WeaponDamageMath;
import com.summy.threat.item.ModItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import com.summy.threat.combat.MagicResistance;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;

import java.util.List;
import java.util.UUID;

/** B15 阶段 2 的服务端武器专项；只验证运行入口，不做完整战斗回归。 */
public final class WeaponSystemDevCheck {
	private WeaponSystemDevCheck() {
	}

	public static void run(ServerPlayer player) {
		StarPrismAffixCoverage.reset();
		WeaponAffixAuditDevCheck.run(player);
		ThreatDevCheck.checkDetailed("武器词条数量：近战 11、远程 11",
				StarPrismAffixes.byCategory(EquipmentCategory.MELEE).size() == 11
						&& StarPrismAffixes.byCategory(EquipmentCategory.RANGED).size() == 11,
				"melee=" + StarPrismAffixes.byCategory(EquipmentCategory.MELEE).size()
						+ ", ranged=" + StarPrismAffixes.byCategory(EquipmentCategory.RANGED).size());

		ItemStack sword = upgradedPrismWeapon(Items.NETHERITE_SWORD, "prism_edge",
				"astral_haste");
		player.setItemSlot(EquipmentSlot.MAINHAND, sword);
		WeaponAffixSnapshot snapshot = WeaponAffixSnapshot.read(sword,
				EquipmentCategory.MELEE);
		ThreatDevCheck.checkDetailed("近战双槽快照读取",
				snapshot.level() == 2 && snapshot.has("prism_edge")
						&& snapshot.has("astral_haste"),
				"level=" + snapshot.level() + ", affixes=" + snapshot.affixes()
						+ ", valid=" + EximusEquipmentData.isValid(sword)
						+ ", slot0=" + EximusEquipmentData.prismTag(sword.getTag(), 0)
						+ ", slot1=" + EximusEquipmentData.prismTag(sword.getTag(), 1));

		ServerLevel level = player.serverLevel();
		Zombie primary = spawnDummy(level, player, 2.0D);
		Zombie sweep = spawnDummy(level, player, 3.0D);
		if (primary == null || sweep == null) {
			ThreatDevCheck.checkDetailed("生成近战测试目标", false,
					"primary=" + primary + ", sweep=" + sweep);
			if (primary != null) {
				primary.discard();
			}
			if (sweep != null) {
				sweep.discard();
			}
			return;
		}
		MeleeWeaponRuntime.onCriticalHit(new CriticalHitEvent(player, primary, 1.0F, false));
		float otherBefore = 100.0F;
		LivingHurtEvent sweepHurt = new LivingHurtEvent(sweep,
				player.damageSources().playerAttack(player), otherBefore);
		MeleeWeaponRuntime.onLivingHurt(sweepHurt);
		ThreatDevCheck.checkDetailed("近战不漏算横扫目标",
				Math.abs(sweepHurt.getAmount() - otherBefore) < 0.001F,
				"amount=" + sweepHurt.getAmount());

		LivingHurtEvent primaryHurt = new LivingHurtEvent(primary,
				player.damageSources().playerAttack(player), 100.0F);
		MeleeWeaponRuntime.onLivingHurt(primaryHurt);
		ThreatDevCheck.checkDetailed("近战 I 阶主目标终伤乘区",
				primaryHurt.getAmount() > 100.0F, "amount=" + primaryHurt.getAmount());

		ThreatDevCheck.checkDetailed("法术护甲削减按百分点计算",
				Math.abs(WeaponDamageMath.reduceArmorReduction(0.90D, 5.0D) - 0.85D) < 1.0E-9D,
				"reduction=" + WeaponDamageMath.reduceArmorReduction(0.90D, 5.0D));
		var prismMagic = PrismMagicDamage.source(primary, player);
		TagKey<DamageType> seExclusion = TagKey.create(Registries.DAMAGE_TYPE,
				new ResourceLocation("summy_enchantment", "no_deaths_blessing_outgoing"));
		TagKey<DamageType> srExclusion = TagKey.create(Registries.DAMAGE_TYPE,
				new ResourceLocation("summy-reliquary", "fixed_true_damage_no_bonus"));
		ThreatDevCheck.checkDetailed("星渊附伤登记为魔法护甲穿透类型",
				MagicResistance.isMagicDamage(prismMagic),
				"type=" + prismMagic.typeHolder().unwrapKey());
		ThreatDevCheck.checkDetailed("星渊附伤进入 SE/SR 二次出伤排除标签",
				prismMagic.is(seExclusion) && prismMagic.is(srExclusion),
				"se=" + prismMagic.is(seExclusion) + ", sr=" + prismMagic.is(srExclusion));
		primary.invulnerableTime = 8;
		PrismMagicDamage.hurt(primary, player, 1.0F);
		ThreatDevCheck.checkDetailed("星渊附伤前后清空受击帧",
				primary.invulnerableTime == 0, "invulnerableTime=" + primary.invulnerableTime);

		AbstractArrow arrow = EntityType.ARROW.create(level);
		if (arrow == null) {
			ThreatDevCheck.checkDetailed("生成远程测试箭", false, "EntityType.ARROW.create null");
		} else {
			arrow.setOwner(player);
			PrismProjectileData.write(arrow.getPersistentData(),
					new PrismProjectileData(PrismProjectileData.SCHEMA, UUID.randomUUID(),
							1, null, null, "starlit_bowstring", false, false, false,
							true, true));
			LivingHurtEvent arrowHurt = new LivingHurtEvent(primary,
					player.damageSources().arrow(arrow, player), 100.0F);
			RangedWeaponRuntime.onLivingHurt(arrowHurt);
			ThreatDevCheck.checkDetailed("远程 I 阶与星弦定力乘区",
					arrowHurt.getAmount() > 130.0F, "amount=" + arrowHurt.getAmount());
			arrow.discard();
		}
		primary.discard();
		sweep.discard();
	}

	private static ItemStack upgradedPrismWeapon(net.minecraft.world.item.Item item,
			String meleeA, String meleeB) {
		ItemStack stack = EximusEquipmentData.withUpgradeLevel(new ItemStack(item), 2);
		stack = EximusEquipmentData.withPrism(stack, 0, prism(meleeA));
		stack = EximusEquipmentData.withPrism(stack, 1, prism(meleeB));
		return stack;
	}

	private static ItemStack prism(String melee) {
		ItemStack prism = new ItemStack(ModItems.ABYSSAL_STAR_PRISM.get());
		StarPrismData.write(prism, new StarPrismData(StarPrismData.SCHEMA, true, null,
				"astral_plating", melee, "starlit_bowstring"));
		return prism;
	}

	private static Zombie spawnDummy(ServerLevel level, ServerPlayer player, double offset) {
		Zombie zombie = EntityType.ZOMBIE.create(level);
		if (zombie != null) {
			zombie.moveTo(player.getX() + offset, player.getY(), player.getZ());
			zombie.setHealth(zombie.getMaxHealth());
			level.addFreshEntity(zombie);
		}
		return zombie;
	}
}
