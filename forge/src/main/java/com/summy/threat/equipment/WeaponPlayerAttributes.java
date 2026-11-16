package com.summy.threat.equipment;

import com.summy.threat.Config;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.UUID;

/**
 * 主手近战武器的玩家临时属性。
 *
 * <p>物品级 I 阶攻速由 {@link EximusWeaponAttributes} 对任何持有者添加；
 * 这里只维护玩家专属的星渊词条、暴击桥与追猎层数，并在每次刷新前清掉同 UUID 修正。
 */
public final class WeaponPlayerAttributes {
	private static final UUID PRISM_EDGE_DAMAGE =
			UUID.fromString("6f5c1a2e-0018-4a0b-9d1e-000000000801");
	private static final UUID ASTRAL_HASTE =
			UUID.fromString("6f5c1a2e-0018-4a0b-9d1e-000000000802");
	private static final UUID CONCUSSIVE_KNOCKBACK =
			UUID.fromString("6f5c1a2e-0018-4a0b-9d1e-000000000804");
	private static final UUID WEAKPOINT_CRIT_CHANCE =
			UUID.fromString("6f5c1a2e-0018-4a0b-9d1e-000000000805");
	private static final UUID WEAKPOINT_CRIT_DAMAGE =
			UUID.fromString("6f5c1a2e-0018-4a0b-9d1e-000000000806");
	private static final UUID PURSUIT_ATTACK_SPEED =
			UUID.fromString("6f5c1a2e-0018-4a0b-9d1e-000000000807");

	private static final ResourceLocation CRIT_CHANCE_ID =
			new ResourceLocation("attributeslib", "crit_chance");
	private static final ResourceLocation CRIT_DAMAGE_ID =
			new ResourceLocation("attributeslib", "crit_damage");

	private WeaponPlayerAttributes() {
	}

	public static boolean externalCritAttributes() {
		return attribute(CRIT_CHANCE_ID) != null && attribute(CRIT_DAMAGE_ID) != null;
	}

	public static void refresh(ServerPlayer player, WeaponRuntimeState state) {
		if (player == null) {
			return;
		}
		clear(player);
		if (!Config.SPEC.isLoaded() || !Config.EXIMUS_EQUIPMENT_ENABLED.get()
				|| state == null) {
			return;
		}
		if (EquipmentTypeResolver.category(player.getMainHandItem()) != EquipmentCategory.MELEE) {
			return;
		}
		int level = EximusEquipmentData.upgradeLevel(player.getMainHandItem());
		if (level < 1) {
			return;
		}

		WeaponAffixSnapshot snapshot = Config.STAR_PRISM_ENABLED.get()
				? WeaponAffixSnapshot.read(player.getMainHandItem(), EquipmentCategory.MELEE)
				: new WeaponAffixSnapshot(level, java.util.List.of());
		int pursuitStacks = Config.STAR_PRISM_ENABLED.get()
				? state.activePursuitStacks(now(player),
						StarPrismConfig.intValue("melee.abyssal_pursuit.reset_ticks"))
				: 0;
		Attribute critChance = attribute(CRIT_CHANCE_ID);
		Attribute critDamage = attribute(CRIT_DAMAGE_ID);
		boolean externalCrit = critChance != null && critDamage != null;
		WeaponAttributeProfile profile = WeaponAttributeProfile.of(snapshot,
				externalCrit, pursuitStacks, configuredValues());

		add(player, Attributes.ATTACK_DAMAGE, PRISM_EDGE_DAMAGE,
				"summy_threat:weapon/prism_edge", profile.attackDamage(),
				AttributeModifier.Operation.ADDITION);
		add(player, Attributes.ATTACK_SPEED, ASTRAL_HASTE,
				"summy_threat:weapon/astral_haste", profile.attackSpeedBase(),
				AttributeModifier.Operation.MULTIPLY_BASE);
		add(player, Attributes.ATTACK_KNOCKBACK, CONCUSSIVE_KNOCKBACK,
				"summy_threat:weapon/concussive", profile.attackKnockback(),
				AttributeModifier.Operation.ADDITION);
		add(player, Attributes.ATTACK_SPEED, PURSUIT_ATTACK_SPEED,
				"summy_threat:weapon/abyssal_pursuit", profile.pursuitSpeed(),
				AttributeModifier.Operation.MULTIPLY_TOTAL);
		if (externalCrit) {
			add(player, critChance, WEAKPOINT_CRIT_CHANCE,
					"summy_threat:weapon/weakpoint_crit_chance", profile.critChance(),
					AttributeModifier.Operation.ADDITION);
			add(player, critDamage, WEAKPOINT_CRIT_DAMAGE,
					"summy_threat:weapon/weakpoint_crit_damage", profile.critDamage(),
					AttributeModifier.Operation.ADDITION);
		}
	}

	public static void clear(ServerPlayer player) {
		if (player == null) {
			return;
		}
		remove(player, Attributes.ATTACK_DAMAGE, PRISM_EDGE_DAMAGE);
		remove(player, Attributes.ATTACK_SPEED, ASTRAL_HASTE);
		remove(player, Attributes.ATTACK_KNOCKBACK, CONCUSSIVE_KNOCKBACK);
		remove(player, Attributes.ATTACK_SPEED, PURSUIT_ATTACK_SPEED);
		remove(player, attribute(CRIT_CHANCE_ID), WEAKPOINT_CRIT_CHANCE);
		remove(player, attribute(CRIT_DAMAGE_ID), WEAKPOINT_CRIT_DAMAGE);
	}

	private static WeaponAttributeProfile.Values configuredValues() {
		return new WeaponAttributeProfile.Values(
				StarPrismConfig.doubleValue("melee.prism_edge.attack_bonus"),
				StarPrismConfig.doubleValue("melee.astral_haste.attack_speed_percent") / 100.0D,
				0.10D,
				StarPrismConfig.doubleValue("melee.concussive_prism.knockback_bonus"),
				StarPrismConfig.doubleValue(
						"melee.weakpoint_prism.crit_chance_percent") / 100.0D,
				StarPrismConfig.doubleValue(
						"melee.weakpoint_prism.crit_multiplier_bonus"),
				StarPrismConfig.doubleValue(
						"melee.abyssal_pursuit.attack_speed_per_stack_percent") / 100.0D);
	}

	private static Attribute attribute(ResourceLocation id) {
		return BuiltInRegistries.ATTRIBUTE.get(id);
	}

	private static long now(ServerPlayer player) {
		return player.server.overworld().getGameTime();
	}

	private static void add(ServerPlayer player, Attribute attribute, UUID id,
			String name, double amount, AttributeModifier.Operation operation) {
		if (attribute == null || !Double.isFinite(amount) || Math.abs(amount) < 1.0E-9D) {
			return;
		}
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance != null) {
			instance.addTransientModifier(new AttributeModifier(id, name, amount, operation));
		}
	}

	private static void remove(ServerPlayer player, Attribute attribute, UUID id) {
		if (attribute == null) {
			return;
		}
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance != null) {
			instance.removeModifier(id);
		}
	}
}
