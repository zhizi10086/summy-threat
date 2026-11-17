package com.summy.threat.devcheck;

import com.google.common.collect.Multimap;
import com.summy.threat.Config;
import com.summy.threat.SummyThreat;
import com.summy.threat.api.event.ThreatConfigChangedEvent;
import com.summy.threat.equipment.ArmorAffixEffects;
import com.summy.threat.equipment.ArmorDurabilityProtection;
import com.summy.threat.equipment.ArmorEquipmentAttributes;
import com.summy.threat.equipment.EquipmentCategory;
import com.summy.threat.equipment.EquipmentCooldownData;
import com.summy.threat.equipment.EquipmentOperation;
import com.summy.threat.equipment.EquipmentRules;
import com.summy.threat.equipment.EquipmentSmithingRecipe;
import com.summy.threat.equipment.EximusArmorPassives;
import com.summy.threat.equipment.EximusEquipmentData;
import com.summy.threat.equipment.StarPrismConfig;
import com.summy.threat.equipment.StarPrismData;
import com.summy.threat.equipment.ThreatCategory;
import com.summy.threat.equipment.ThreatHitContext;
import com.summy.threat.equipment.ThreatMovementContext;
import com.summy.threat.equipment.WornArmorState;
import com.summy.threat.combat.ArmorShred;
import com.summy.threat.item.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** B15 核心、锻造事务、固定能力与盔甲词条的隔离服务端自检。 */
final class EquipmentSystemDevCheck {
	private static final EquipmentSlot[] ARMOR = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
	};
	private static ServerPlayer pendingPlayer;
	private static boolean pendingEmergencyHeal;
	private static float emergencyHealthBefore;

	private EquipmentSystemDevCheck() {
	}

	static List<String> start(ServerPlayer player) {
		List<String> failures = new ArrayList<>();
		StarPrismAffixCoverage.reset();
		preparePlayer(player);
		checkNbtAndSlots(failures);
		checkRecipesAndReturns(player, failures);
		checkItemAttributes(failures);
		checkDurabilityAndFixedAttributes(player, failures);
		checkArmorRuntime(player, failures);
		checkAffixCoverage(failures, EquipmentCategory.ARMOR);
		pendingEmergencyHeal = prepareEmergencyHeal(player);
		pendingPlayer = pendingEmergencyHeal ? player : null;
		SummyThreat.LOGGER.info("[DEVCHECK] B15 核心检查完成，等待下一 tick 验证应急生息");
		return failures;
	}

	static List<String> tick(ServerPlayer player) {
		if (!pendingEmergencyHeal || pendingPlayer != player) {
			return List.of();
		}
		List<String> failures = new ArrayList<>();
		pendingEmergencyHeal = false;
		pendingPlayer = null;
		float before = emergencyHealthBefore;
		EximusArmorPassives.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
		double expected = Math.max(Config.EMERGENCY_MENDING_HEAL_MIN.get(),
				Math.min(Config.EMERGENCY_MENDING_HEAL_MAX.get(),
						player.getMaxHealth() * Config.EMERGENCY_MENDING_HEAL_PERCENT.get()
								/ 100.0D));
		check(failures, "B15 应急生息在下一 tick 实际治疗",
				close(player.getHealth() - before, expected),
				"before=%.2f after=%.2f expected=%.2f".formatted(
						before, player.getHealth(), expected));
		cleanup(player);
		return failures;
	}

	private static void preparePlayer(ServerPlayer player) {
		player.getInventory().clearContent();
		for (EquipmentSlot slot : ARMOR) {
			player.setItemSlot(slot, ItemStack.EMPTY);
		}
		player.removeAllEffects();
		player.setHealth(player.getMaxHealth());
		clearCooldowns(player);
		ArmorShred.cleanup(player);
		WornArmorState.invalidate(player);
		ArmorEquipmentAttributes.clear(player);
	}

	private static void checkNbtAndSlots(List<String> failures) {
		ItemStack original = new ItemStack(Items.DIAMOND_CHESTPLATE);
		original.setHoverName(net.minecraft.network.chat.Component.literal("NBT-KEEP"));
		original.enchant(Enchantments.UNBREAKING, 3);
		original.setDamageValue(7);
		original.setRepairCost(9);
		CompoundTag tag = original.getOrCreateTag();
		tag.putString("UnknownRoot", "keep-root");
		CompoundTag affix = new CompoundTag();
		affix.putString("id", "apotheosis:test_affix");
		tag.put("affix_data", affix);

		ItemStack levelOne = EximusEquipmentData.withUpgradeLevel(original, 1);
		ItemStack levelTwo = EximusEquipmentData.withUpgradeLevel(levelOne, 2);
		CompoundTag twoTag = levelTwo.getTag();
		check(failures, "B15 NBT 升级保留附魔",
				EnchantmentHelper.getItemEnchantmentLevel(Enchantments.UNBREAKING, levelTwo) == 3,
				"level=" + EnchantmentHelper.getItemEnchantmentLevel(Enchantments.UNBREAKING, levelTwo));
		check(failures, "B15 NBT 升级保留名称与 Damage/RepairCost",
				"NBT-KEEP".equals(levelTwo.getHoverName().getString())
						&& levelTwo.getDamageValue() == 7
						&& levelTwo.getOrCreateTag().getInt("RepairCost") == 9,
				"name=%s damage=%d repair=%d".formatted(levelTwo.getHoverName().getString(),
						levelTwo.getDamageValue(),
						levelTwo.getOrCreateTag().getInt("RepairCost")));
		check(failures, "B15 NBT 升级保留未知根键与 affix_data",
				twoTag != null && "keep-root".equals(twoTag.getString("UnknownRoot"))
						&& "apotheosis:test_affix".equals(
								twoTag.getCompound("affix_data").getString("id")),
				String.valueOf(twoTag));
		check(failures, "B15 assemble 输入对象不被原地修改",
				!original.getOrCreateTag().contains(EximusEquipmentData.EQUIPMENT_ROOT),
				"root=" + original.getTag());

		ItemStack prismOne = prism("astral_plating", "prism_edge", "starlit_bowstring",
				true, null);
		ItemStack prismTwo = prism("vital_constellation", "terminal_star",
				"terminal_star_shot", true, null);
		levelTwo = EximusEquipmentData.withPrism(levelTwo, 0, prismOne);
		ItemStack reopened = EximusEquipmentData.withUpgradeLevel(levelTwo, 2);
		check(failures, "B15 II 阶重写保留槽位 1",
				"astral_plating".equals(StarPrismData.read(
						EximusEquipmentData.prism(reopened, 0)).orElseThrow().armorAffix()),
				"slot1=" + StarPrismData.read(EximusEquipmentData.prism(reopened, 0)));

		ItemStack full = EximusEquipmentData.withPrism(reopened, 1, prismTwo);
		check(failures, "B15 槽位按 1→2 最小空槽写入",
				EximusEquipmentData.firstEmptySlot(reopened) == 1
						&& EximusEquipmentData.firstEmptySlot(full) == -1,
				"level2Empty=%d fullEmpty=%d".formatted(
						EximusEquipmentData.firstEmptySlot(reopened),
						EximusEquipmentData.firstEmptySlot(full)));
		StarPrismData duplicate = new StarPrismData(1, true, null,
				"astral_plating", "starfire_cycle", "starfall_cycle_arrow");
		CompoundTag dupTag = new CompoundTag();
		StarPrismData.write(dupTag, duplicate);
		check(failures, "B15 同 ID 与双满槽拒绝镶嵌",
				EquipmentRules.embeddingSlot(reopened.getTag(), dupTag,
						EquipmentCategory.ARMOR) == -1
						&& EquipmentRules.embeddingSlot(full.getTag(), prismTag(
								"steady_heart", "shield_render", "void_quiver"),
								EquipmentCategory.ARMOR) == -1,
				"duplicate/full");
	}

	private static void checkRecipesAndReturns(ServerPlayer player, List<String> failures) {
		ItemStack base = new ItemStack(Items.DIAMOND_CHESTPLATE);
		SimpleContainer upgradeOne = smith(Items.ECHO_SHARD, base,
				ModItems.EXIMUS_ESSENCE.get());
		EquipmentSmithingRecipe recipeOne = recipe(EquipmentOperation.UPGRADE_I);
		check(failures, "B15 锻造 I 阶配方匹配", recipeOne.matches(upgradeOne, null),
				"container=" + upgradeOne);
		ItemStack levelOne = recipeOne.assemble(upgradeOne, player.server.registryAccess());
		SimpleContainer upgradeTwo = smith(Items.ECHO_SHARD, levelOne,
				ModItems.EXIMUS_ESSENCE.get());
		EquipmentSmithingRecipe recipeTwo = recipe(EquipmentOperation.UPGRADE_II);
		ItemStack levelTwo = recipeTwo.assemble(upgradeTwo, player.server.registryAccess());
		check(failures, "B15 两段升级结果等级正确",
				EximusEquipmentData.upgradeLevel(levelOne) == 1
						&& EximusEquipmentData.upgradeLevel(levelTwo) == 2,
				"level1=%d level2=%d".formatted(
						EximusEquipmentData.upgradeLevel(levelOne),
						EximusEquipmentData.upgradeLevel(levelTwo)));

		ItemStack prismOne = prism("astral_plating", "prism_edge",
				"starlit_bowstring", true, null);
		SimpleContainer embed = smith(Items.ECHO_SHARD, levelTwo, prismOne);
		EquipmentSmithingRecipe embedRecipe = recipe(EquipmentOperation.EMBED_PRISM);
		ItemStack oneEmbedded = embedRecipe.assemble(embed, player.server.registryAccess());
		check(failures, "B15 镶嵌操作写入槽位 1",
				!EximusEquipmentData.prism(oneEmbedded, 0).isEmpty()
						&& EximusEquipmentData.prism(oneEmbedded, 1).isEmpty(),
				"slot1=%s slot2=%s".formatted(
						EximusEquipmentData.prism(oneEmbedded, 0),
						EximusEquipmentData.prism(oneEmbedded, 1)));

		ItemStack prismTwo = prism("vital_constellation", "terminal_star",
				"terminal_star_shot", true, null);
		SimpleContainer secondEmbed = smith(Items.ECHO_SHARD, oneEmbedded, prismTwo);
		boolean secondMatches = embedRecipe.matches(secondEmbed, null);
		int secondSlot = EquipmentRules.embeddingSlot(oneEmbedded.getTag(),
				prismTwo.getTag(), EquipmentCategory.ARMOR);
		check(failures, "B15 第二槽配方 matches 与槽位计算一致",
				secondMatches && secondSlot == 1,
				"matches=%s slot=%d existing=%s".formatted(
						secondMatches, secondSlot,
						EximusEquipmentData.prism(oneEmbedded, 0)));
		ItemStack twoEmbedded = embedRecipe.assemble(secondEmbed,
				player.server.registryAccess());
		check(failures, "B15 第二次镶嵌实际写入槽位 2",
				!EximusEquipmentData.prism(twoEmbedded, 0).isEmpty()
						&& !EximusEquipmentData.prism(twoEmbedded, 1).isEmpty(),
				"slot1=%s slot2=%s".formatted(
						EximusEquipmentData.prism(twoEmbedded, 0),
						EximusEquipmentData.prism(twoEmbedded, 1)));

		ItemStack bow = EximusEquipmentData.withPrism(
				EximusEquipmentData.withUpgradeLevel(new ItemStack(Items.BOW), 2),
				0, prism("astral_plating", "prism_edge", "starlit_bowstring", true, null));
		ItemStack bowSecond = prism("vital_constellation", "terminal_star",
				"starfall_cycle_arrow", true, null);
		SmithingMenu menu = new SmithingMenu(0, player.getInventory());
		menu.getSlot(0).set(new ItemStack(Items.ECHO_SHARD));
		menu.getSlot(1).set(bow);
		menu.getSlot(2).set(bowSecond);
		menu.createResult();
		ItemStack menuResult = menu.getSlot(3).getItem();
		check(failures, "B15 实际 SmithingMenu 输出第二槽结果",
				!menuResult.isEmpty()
						&& !EximusEquipmentData.prism(menuResult, 0).isEmpty()
						&& !EximusEquipmentData.prism(menuResult, 1).isEmpty(),
				"result=" + menuResult);

		SmithingMenu quickMoveMenu = new SmithingMenu(0, player.getInventory());
		int emptyBaseTarget = quickMoveMenu.getSlotToQuickMoveTo(prismOne.copy());
		check(failures, "B15 棱晶快速移动优先中间槽",
				emptyBaseTarget == SmithingMenu.BASE_SLOT,
				"target=" + emptyBaseTarget);
		quickMoveMenu.getSlot(SmithingMenu.BASE_SLOT).set(prismOne.copy());
		int occupiedBaseTarget = quickMoveMenu.getSlotToQuickMoveTo(prismOne.copy());
		check(failures, "B15 中间槽占用时棱晶快速移动至右槽",
				occupiedBaseTarget == SmithingMenu.ADDITIONAL_SLOT,
				"target=" + occupiedBaseTarget);

		ItemStack full = EximusEquipmentData.withPrism(oneEmbedded, 1, prismTwo);
		SimpleContainer disassemble = smith(Items.ECHO_SHARD, full, Items.AMETHYST_SHARD);
		EquipmentSmithingRecipe disassembleRecipe = recipe(EquipmentOperation.DISASSEMBLE_ALL);
		ItemStack disassembled = disassembleRecipe.assemble(disassemble,
				player.server.registryAccess());
		CompoundTag root = disassembled.getTag().getCompound(
				EximusEquipmentData.EQUIPMENT_ROOT);
		ListTag returns = root.getList(EquipmentSmithingRecipe.RETURN_PRISMS, Tag.TAG_COMPOUND);
		check(failures, "B15 同步拆卸双满槽返回 2 枚且槽位清空",
				returns.size() == 2
						&& EximusEquipmentData.prism(disassembled, 0).isEmpty()
						&& EximusEquipmentData.prism(disassembled, 1).isEmpty(),
				"returns=%d".formatted(returns.size()));

		player.getInventory().clearContent();
		SmithingMenu disassembleMenu = new SmithingMenu(0, player.getInventory());
		disassembleMenu.getSlot(0).set(new ItemStack(Items.ECHO_SHARD));
		disassembleMenu.getSlot(1).set(full.copy());
		disassembleMenu.getSlot(2).set(new ItemStack(Items.AMETHYST_SHARD));
		disassembleMenu.createResult();
		ItemStack menuReturn = disassembleMenu.getSlot(3).getItem().copy();
		disassembleMenu.getSlot(3).onTake(player,
				disassembleMenu.getSlot(3).getItem().copy());
		int returnedByMenu = countItem(player, ModItems.ABYSSAL_STAR_PRISM.get());
		check(failures, "B15 真实 SmithingMenu 取回同步拆卸返还 2 枚",
				returnedByMenu == 2 && !menuReturn.isEmpty(),
				"inventoryCount=%d result=%s".formatted(returnedByMenu, menuReturn));
		player.getInventory().clearContent();

		ItemStack oneFilled = EximusEquipmentData.withPrism(levelTwo, 0, prismOne);
		ItemStack oneReturn = disassembleRecipe.assemble(
				smith(Items.ECHO_SHARD, oneFilled, Items.AMETHYST_SHARD),
				player.server.registryAccess());
		check(failures, "B15 一空一满返回 1 枚",
				oneReturn.getTag().getCompound(EximusEquipmentData.EQUIPMENT_ROOT)
						.getList(EquipmentSmithingRecipe.RETURN_PRISMS, Tag.TAG_COMPOUND).size() == 1,
				"returns=1");
		check(failures, "B15 双空不匹配拆卸",
				!disassembleRecipe.matches(
						smith(Items.ECHO_SHARD, levelTwo, Items.AMETHYST_SHARD), null),
				"empty sockets");
	}

	private static void checkItemAttributes(List<String> failures) {
		ItemStack stack = EximusEquipmentData.withUpgradeLevel(
				new ItemStack(Items.DIAMOND_CHESTPLATE), 1);
		ItemStack plain = new ItemStack(Items.DIAMOND_CHESTPLATE);
		Multimap<net.minecraft.world.entity.ai.attributes.Attribute, AttributeModifier> modifiers =
				stack.getAttributeModifiers(EquipmentSlot.CHEST);
		Multimap<net.minecraft.world.entity.ai.attributes.Attribute, AttributeModifier> plainModifiers =
				plain.getAttributeModifiers(EquipmentSlot.CHEST);
		double armor = sumAmount(modifiers, Attributes.ARMOR)
				- sumAmount(plainModifiers, Attributes.ARMOR);
		double toughness = sumAmount(modifiers, Attributes.ARMOR_TOUGHNESS)
				- sumAmount(plainModifiers, Attributes.ARMOR_TOUGHNESS);
		double knockback = sumAmount(modifiers, Attributes.KNOCKBACK_RESISTANCE)
				- sumAmount(plainModifiers, Attributes.KNOCKBACK_RESISTANCE);
		check(failures, "B15 I 阶物品级属性增量正确",
				close(armor, 2.0D) && close(toughness, 1.5D) && close(knockback, 0.05D),
				"armor=%.2f toughness=%.2f knockback=%.2f map=%s".formatted(
						armor, toughness, knockback, modifiers));
	}

	private static void checkDurabilityAndFixedAttributes(ServerPlayer player,
			List<String> failures) {
		check(failures, "B15 II 阶单次耐久损耗夹在 5~6",
				ArmorDurabilityProtection.capLoss(1000, 50, 2, 100, 5, 6) == 6
						&& ArmorDurabilityProtection.capLoss(250, 50, 2, 100, 5, 6) == 5,
				"1000→%d 250→%d".formatted(
						ArmorDurabilityProtection.capLoss(1000, 50, 2, 100, 5, 6),
						ArmorDurabilityProtection.capLoss(250, 50, 2, 100, 5, 6)));

		equipFixed(player, EquipmentSlot.HEAD, Items.DIAMOND_HELMET);
		player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0));
		player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0));
		EximusArmorPassives.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
		check(failures, "B15 澄明视界清除失明与黑暗",
				!player.hasEffect(MobEffects.BLINDNESS)
						&& !player.hasEffect(MobEffects.DARKNESS),
				"blindness=%s darkness=%s".formatted(
						player.hasEffect(MobEffects.BLINDNESS),
						player.hasEffect(MobEffects.DARKNESS)));

		clearArmor(player);
		equipFixed(player, EquipmentSlot.CHEST, Items.DIAMOND_CHESTPLATE);
		EquipmentCooldownData.clear(player.getPersistentData(), "emergency_mending");
		player.setHealth(player.getMaxHealth() - 5.0F);
		float emergencyEnvironmentBefore = player.getHealth();
		EximusArmorPassives.onLivingDamage(new LivingDamageEvent(
				player, player.damageSources().fall(), 1.0F));
		EximusArmorPassives.onPlayerTick(new TickEvent.PlayerTickEvent(
				TickEvent.Phase.END, player));
		check(failures, "B15 应急生息可由环境伤害触发",
				player.getHealth() > emergencyEnvironmentBefore,
				"before=%.2f after=%.2f".formatted(
						emergencyEnvironmentBefore, player.getHealth()));
		player.setHealth(player.getMaxHealth());
		EquipmentCooldownData.clear(player.getPersistentData(), "emergency_mending");

		clearArmor(player);
		ArmorEquipmentAttributes.clear(player);
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		double speedBeforeStride = speed == null ? 0.0D : speed.getValue();
		equipFixed(player, EquipmentSlot.FEET, Items.DIAMOND_BOOTS);
		ArmorEquipmentAttributes.refresh(player);
		AttributeInstance step = player.getAttribute(ForgeMod.STEP_HEIGHT_ADDITION.get());
		check(failures, "B15 星履提供移速与台阶高度",
				speed != null && speed.getValue() > speedBeforeStride
						&& step != null && step.getValue() >= Config.STAR_STRIDE_STEP_HEIGHT.get(),
				"speed=%s before=%s step=%s".formatted(
						speed == null ? "null" : speed.getValue(),
						speedBeforeStride,
						step == null ? "null" : step.getValue()));

		clearArmor(player);
		equipFixed(player, EquipmentSlot.LEGS, Items.DIAMOND_LEGGINGS);
		UUID slowdown = UUID.fromString("6f5c1a2e-7777-4a0b-9d1e-000000000001");
		speed.removeModifier(slowdown);
		speed.addTransientModifier(new AttributeModifier(slowdown,
				"devcheck_external_slow", -0.8D,
				AttributeModifier.Operation.MULTIPLY_TOTAL));
		ArmorEquipmentAttributes.refresh(player);
		check(failures, "B15 缓速抑制覆盖全部负移速 modifier",
				speed.getModifier(UUID.fromString(
						"6f5c1a2e-0016-4a0b-9d1e-000000000707")) != null,
				"speed=" + speed.getValue());
		speed.removeModifier(slowdown);
		clearArmor(player);
		ArmorEquipmentAttributes.clear(player);
	}

	private static void checkArmorRuntime(ServerPlayer player, List<String> failures) {
		checkArmorAffixAttributes(player, failures);
		ServerLevel level = player.serverLevel();
		Zombie attacker = net.minecraft.world.entity.EntityType.ZOMBIE.create(level);
		if (attacker == null) {
			check(failures, "B15 创建词条测试攻击者", false, "create 返回 null");
			return;
		}
		attacker.moveTo(player.getX() + 2.0D, player.getY(), player.getZ());

		equipAffixes(player, "star_mantle", "star_mantle");
		StarPrismAffixCoverage.cover("star_mantle");
		float mantle = damage(player, attacker.damageSources().mobAttack(attacker), 100.0F);
		check(failures, "B15 两份星辉壁障独立乘算",
				close(mantle, (float) (100.0D
						* Math.pow(1.0D - StarPrismConfig.doubleValue(
								"armor.star_mantle.reduction_percent_per_stack") / 100.0D, 2))),
				"amount=" + mantle);

		equipAffixes(player, "projectile_ward");
		StarPrismAffixCoverage.cover("projectile_ward");
		Arrow arrow = new Arrow(level, attacker);
		DamageSource arrowSource = arrow.damageSources().arrow(arrow, attacker);
		float projectile = damage(player, arrowSource, 100.0F);
		check(failures, "B15 星幕偏折降低有归属投射物",
				close(projectile, (float) (100.0D - StarPrismConfig.doubleValue(
						"armor.projectile_ward.reduction_percent"))),
				"amount=" + projectile);
		Arrow unattributedArrow = new Arrow(level, player.getX(), player.getY(), player.getZ());
		float unattributedProjectile = damage(player,
				player.damageSources().arrow(unattributedArrow, null), 100.0F);
		check(failures, "B15 星幕偏折覆盖无归属投射物",
				close(unattributedProjectile, (float) (100.0D - StarPrismConfig.doubleValue(
						"armor.projectile_ward.reduction_percent"))),
				"amount=" + unattributedProjectile);

		equipAffixes(player, "spellward_prism");
		StarPrismAffixCoverage.cover("spellward_prism");
		float spell = ArmorAffixEffects.spellwardMultiplier(player,
				player.damageSources().indirectMagic(attacker, attacker));
		check(failures, "B15 星咒屏障乘区为 0.88",
				close(spell, (float) (1.0D - StarPrismConfig.doubleValue(
						"armor.spellward_prism.reduction_percent") / 100.0D)),
				"multiplier=" + spell);
		checkSpellwardFullChain(player, attacker, failures);

		equipAffixes(player, "anchored_stride");
		StarPrismAffixCoverage.cover("anchored_stride");
		float fall = damage(player, player.damageSources().fall(), 100.0F);
		check(failures, "B15 磐星步摔落减伤",
				close(fall, (float) (100.0D - StarPrismConfig.doubleValue(
						"armor.anchored_stride.fall_reduction_percent"))),
				"amount=" + fall);

		equipAffixes(player, "thorns_insulation");
		StarPrismAffixCoverage.cover("thorns_insulation");
		DamageSource thorns = player.damageSources().thorns(attacker);
		LivingAttackEvent thornsAttack = new LivingAttackEvent(player, thorns, 10.0F);
		ThreatHitContext.run(player, thorns, ThreatCategory.TL_ABILITY, "thorns",
				() -> MinecraftForge.EVENT_BUS.post(thornsAttack));
		check(failures, "B15 荆棘绝缘在受击反馈前取消 ST 荆棘",
				thornsAttack.isCanceled(),
				"canceled=" + thornsAttack.isCanceled());
		LivingHurtEvent thornsEvent = new LivingHurtEvent(player, thorns, 10.0F);
		ThreatHitContext.run(player, thorns, ThreatCategory.TL_ABILITY, "thorns",
				() -> ArmorAffixEffects.onLivingHurt(thornsEvent));
		check(failures, "B15 荆棘绝缘只取消 ST TL 荆棘",
				thornsEvent.isCanceled(),
				"canceled=" + thornsEvent.isCanceled());
		LivingHurtEvent vanillaThorns = new LivingHurtEvent(player, thorns, 10.0F);
		ArmorAffixEffects.onLivingHurt(vanillaThorns);
		check(failures, "B15 荆棘绝缘不取消无上下文荆棘",
				!vanillaThorns.isCanceled(), "canceled=" + vanillaThorns.isCanceled());

		equipAffixes(player, "corrosion_baffle");
		StarPrismAffixCoverage.cover("corrosion_baffle");
		ArmorShred.cleanup(player);
		for (int i = 0; i < Config.CORROSION_MAX_STACKS.get(); i++) {
			ArmorShred.applyCorrosion(player);
		}
		double expectedCorrosion = Config.CORROSION_MAX_STACKS.get()
				* Config.CORROSION_PER_STACK_PERCENT.get() / 100.0D
				* (1.0D - StarPrismConfig.doubleValue(
						"armor.corrosion_baffle.suppression_percent") / 100.0D);
		check(failures, "B15 蚀星隔层抑制 TL 腐蚀",
				close(ArmorShred.activePercent(player), expectedCorrosion),
				"active=%.4f expected=%.4f".formatted(
						ArmorShred.activePercent(player), expectedCorrosion));
		ArmorShred.cleanup(player);

		equipAffixes(player, "steady_heart");
		StarPrismAffixCoverage.cover("steady_heart");
		ArmorAffixEffects.onConfigChanged(new ThreatConfigChangedEvent(
				"devcheck", java.util.Map.of(), java.util.Map.of()));
		EquipmentCooldownData.clear(player.getPersistentData(), "armor.steady_heart");
		player.setHealth(player.getMaxHealth() * 0.6F);
		damage(player, player.damageSources().fall(), player.getMaxHealth() * 0.2F);
		float steadyEnvironment = damage(player, player.damageSources().fall(), 20.0F);
		check(failures, "B15 定心回响可由环境伤害触发",
				close(steadyEnvironment, (float) (20.0D * (1.0D
						- StarPrismConfig.doubleValue(
								"armor.steady_heart.reduction_percent") / 100.0D))),
				"amount=" + steadyEnvironment);
		ArmorAffixEffects.onConfigChanged(new ThreatConfigChangedEvent(
				"devcheck", java.util.Map.of(), java.util.Map.of()));
		EquipmentCooldownData.clear(player.getPersistentData(), "armor.steady_heart");
		player.setHealth(player.getMaxHealth() * 0.6F);
		damage(player, attacker.damageSources().mobAttack(attacker),
				player.getMaxHealth() * 0.2F);
		float steady = damage(player, attacker.damageSources().mobAttack(attacker), 20.0F);
		check(failures, "B15 定心回响低血线窗口减伤 35%",
				close(steady, (float) (20.0D * (1.0D - StarPrismConfig.doubleValue(
						"armor.steady_heart.reduction_percent") / 100.0D))),
				"amount=" + steady);
		player.setHealth(player.getMaxHealth());

		equipAffixes(player, "threat_aegis");
		StarPrismAffixCoverage.cover("threat_aegis");
		DamageSource skill = attacker.damageSources().mobAttack(attacker);
		LivingDamageEvent aegis = new LivingDamageEvent(player, skill, 100.0F);
		ThreatHitContext.run(player, skill, ThreatCategory.LEADER_ABILITY,
				"gravity_pulse", () -> ArmorAffixEffects.onLivingDamage(aegis));
		check(failures, "B15 危星壁垒只降低 ST 威胁技能",
				close(aegis.getAmount(), (float) (100.0D - StarPrismConfig.doubleValue(
						"armor.threat_aegis.reduction_percent"))),
				"amount=" + aegis.getAmount());
		LivingDamageEvent plain = new LivingDamageEvent(player, skill, 100.0F);
		ArmorAffixEffects.onLivingDamage(plain);
		check(failures, "B15 危星壁垒不作用于普通攻击",
				close(plain.getAmount(), 100.0F), "amount=" + plain.getAmount());

		equipAffixes(player, "last_light");
		StarPrismAffixCoverage.cover("last_light");
		EquipmentCooldownData.clear(player.getPersistentData(), "armor.last_light");
		player.setHealth(player.getMaxHealth() * 0.3F);
		LivingDamageEvent fatal = new LivingDamageEvent(player, skill,
				player.getMaxHealth());
		ArmorAffixEffects.onLastLight(fatal);
		float floor = Math.max(1.0F, player.getMaxHealth()
				* (float) (StarPrismConfig.doubleValue(
						"armor.last_light.health_floor_percent") / 100.0D));
		check(failures, "B15 终明不熄将致命伤害锁至 20% 生命",
				close(player.getHealth() - fatal.getAmount(), floor),
				"health=%.2f final=%.2f floor=%.2f".formatted(
						player.getHealth(), fatal.getAmount(), floor));
		LivingDamageEvent guarded = new LivingDamageEvent(player, skill, 100.0F);
		ArmorAffixEffects.onGuardedDamage(guarded);
		check(failures, "B15 终明不熄 40 tick 保护",
				guarded.getAmount() == 0.0F, "amount=" + guarded.getAmount());
		LivingDamageEvent bypass = new LivingDamageEvent(player,
				player.damageSources().genericKill(), 100.0F);
		ArmorAffixEffects.onGuardedDamage(bypass);
		check(failures, "B15 终明不熄不拦截穿透伤害",
				bypass.getAmount() == 100.0F, "amount=" + bypass.getAmount());
		player.setHealth(player.getMaxHealth());

		equipAffixes(player, "phase_anchor");
		StarPrismAffixCoverage.cover("phase_anchor");
		EquipmentCooldownData.clear(player.getPersistentData(), "armor.phase_anchor");
		long now = player.server.overworld().getGameTime();
		boolean anchored = ThreatMovementContext.call(
				player, "devcheck_pull", now + 20L,
				() -> ArmorAffixEffects.blocksForcedMovement(player));
		boolean sameEvent = ThreatMovementContext.call(
				player, "devcheck_pull", now + 20L,
				() -> ArmorAffixEffects.blocksForcedMovement(player));
		boolean otherEvent = ThreatMovementContext.call(
				player, "devcheck_other", now + 20L,
				() -> ArmorAffixEffects.blocksForcedMovement(player));
		check(failures, "B15 星轨锚定完整抵消一次持续位移事件",
				anchored && sameEvent && !otherEvent,
				"first=%s same=%s other=%s".formatted(anchored, sameEvent, otherEvent));

		attacker.discard();
		clearArmor(player);
		WornArmorState.invalidate(player);
	}

	private static void checkArmorAffixAttributes(ServerPlayer player, List<String> failures) {
		clearArmor(player);
		equipAffixes(player, "steady_heart", "steady_heart");
		ArmorEquipmentAttributes.refresh(player);
		double baseArmor = player.getAttributeValue(Attributes.ARMOR);
		double baseToughness = player.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
		float baseHealth = player.getMaxHealth();

		clearArmor(player);
		equipAffixes(player, "astral_plating", "astral_plating");
		ArmorEquipmentAttributes.refresh(player);
		double expectedArmor = baseArmor + 2.0D * StarPrismConfig.doubleValue(
				"armor.astral_plating.armor_value_per_stack");
		double expectedToughness = baseToughness + 2.0D * StarPrismConfig.doubleValue(
				"armor.astral_plating.toughness_value_per_stack");
		check(failures, "B15 星铸甲胄两份属性叠加",
				close(player.getAttributeValue(Attributes.ARMOR), expectedArmor)
						&& close(player.getAttributeValue(Attributes.ARMOR_TOUGHNESS),
								expectedToughness),
				"armor=%.2f/%.2f toughness=%.2f/%.2f".formatted(
						player.getAttributeValue(Attributes.ARMOR), expectedArmor,
						player.getAttributeValue(Attributes.ARMOR_TOUGHNESS), expectedToughness));
		StarPrismAffixCoverage.cover("astral_plating");

		clearArmor(player);
		equipAffixes(player, "vital_constellation", "vital_constellation");
		ArmorEquipmentAttributes.refresh(player);
		double expectedHealth = baseHealth + 2.0D * StarPrismConfig.doubleValue(
				"armor.vital_constellation.max_health_per_stack");
		check(failures, "B15 命星簇两份最大生命叠加",
				close(player.getMaxHealth(), expectedHealth),
				"health=%.2f expected=%.2f".formatted(
						player.getMaxHealth(), expectedHealth));
		StarPrismAffixCoverage.cover("vital_constellation");
		player.setHealth(player.getMaxHealth());
	}

	private static void checkAffixCoverage(List<String> failures,
			EquipmentCategory category) {
		for (String id : StarPrismAffixCoverage.missing(category)) {
			check(failures, "B15 星渊词条已执行：" + id, false, "UNCOVERED");
		}
	}

	private static boolean prepareEmergencyHeal(ServerPlayer player) {
		clearArmor(player);
		equipFixed(player, EquipmentSlot.CHEST, Items.DIAMOND_CHESTPLATE);
		EquipmentCooldownData.clear(player.getPersistentData(), "emergency_mending");
		ServerLevel level = player.serverLevel();
		Zombie attacker = new Zombie(net.minecraft.world.entity.EntityType.ZOMBIE, level);
		attacker.moveTo(player.getX() + 2.0D, player.getY(), player.getZ());
		// 保持饥饿值稳定，避免测试等待下一 tick 时被饥饿/SE 饮食机制扣血，
		// 影响应急生息治疗量的独立断言。
		player.getFoodData().setFoodLevel(20);
		player.getFoodData().setSaturation(20.0F);
		player.getFoodData().setExhaustion(0.0F);
		player.setHealth(player.getMaxHealth() - 5.0F);
		emergencyHealthBefore = player.getHealth();
		EximusArmorPassives.onLivingDamage(new LivingDamageEvent(
				player, attacker.damageSources().mobAttack(attacker), 1.0F));
		attacker.discard();
		return true;
	}

	private static void cleanup(ServerPlayer player) {
		clearArmor(player);
		player.getInventory().clearContent();
		player.removeAllEffects();
		player.setHealth(player.getMaxHealth());
		clearCooldowns(player);
		ArmorShred.cleanup(player);
		WornArmorState.invalidate(player);
		ArmorEquipmentAttributes.clear(player);
	}

	private static void clearCooldowns(ServerPlayer player) {
		EquipmentCooldownData.clear(player.getPersistentData(), "emergency_mending");
		EquipmentCooldownData.clear(player.getPersistentData(), "armor.steady_heart");
		EquipmentCooldownData.clear(player.getPersistentData(), "armor.last_light");
		EquipmentCooldownData.clear(player.getPersistentData(), "armor.phase_anchor");
	}

	private static void clearArmor(ServerPlayer player) {
		for (EquipmentSlot slot : ARMOR) {
			player.setItemSlot(slot, ItemStack.EMPTY);
		}
		WornArmorState.invalidate(player);
	}

	private static void equipFixed(ServerPlayer player, EquipmentSlot slot, Item item) {
		ItemStack stack = EximusEquipmentData.withUpgradeLevel(new ItemStack(item), 2);
		player.setItemSlot(slot, stack);
		WornArmorState.invalidate(player);
	}

	private static void equipAffixes(ServerPlayer player, String... ids) {
		clearArmor(player);
		for (int i = 0; i < ids.length; i++) {
			EquipmentSlot slot = ARMOR[Math.min(ARMOR.length - 1, i / 2)];
			Item item = switch (slot) {
				case HEAD -> Items.DIAMOND_HELMET;
				case CHEST -> Items.DIAMOND_CHESTPLATE;
				case LEGS -> Items.DIAMOND_LEGGINGS;
				case FEET -> Items.DIAMOND_BOOTS;
				default -> Items.DIAMOND_CHESTPLATE;
			};
			ItemStack current = player.getItemBySlot(slot);
			if (current.isEmpty()) {
				current = EximusEquipmentData.withUpgradeLevel(new ItemStack(item), 2);
			}
			int prismSlot = EximusEquipmentData.firstEmptySlot(current);
			current = EximusEquipmentData.withPrism(current, Math.max(0, prismSlot),
					prism(ids[i], "prism_edge", "starlit_bowstring", true, null));
			player.setItemSlot(slot, current);
		}
		WornArmorState.invalidate(player);
		ArmorEquipmentAttributes.refresh(player);
	}

	private static void checkSpellwardFullChain(ServerPlayer player, Zombie attacker,
			List<String> failures) {
		MobEffectInstance previousResistance = player.getEffect(
				MobEffects.DAMAGE_RESISTANCE);
		net.minecraft.world.level.GameType previousGameMode =
				player.gameMode.getGameModeForPlayer();
		float previousHealth = player.getHealth();
		float previousAbsorption = player.getAbsorptionAmount();
		try {
			player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
			player.removeEffect(MobEffects.DAMAGE_RESISTANCE);
			player.addEffect(new MobEffectInstance(
					MobEffects.DAMAGE_RESISTANCE, 200, 1, false, false));

			equipProtectionArmor(player, false);
			MagicAbsorbProbe baseline = absorbMagicHit(player, attacker);
			equipProtectionArmor(player, true);
			MagicAbsorbProbe warded = absorbMagicHit(player, attacker);
			double expected = baseline.absorbed()
					* (1.0D - StarPrismConfig.doubleValue(
							"armor.spellward_prism.reduction_percent") / 100.0D);
			check(failures, "B15 星咒屏障位于保护抗性后吸收前",
					baseline.absorbed() > 0.0F && baseline.absorbed() < 100.0F
							&& Math.abs(warded.absorbed() - expected) <= 0.01D
							&& Math.abs(warded.healthLost()) <= 0.01D,
					"baseline=%.4f warded=%.4f expected=%.4f healthLost=%.4f"
							.formatted(baseline.absorbed(), warded.absorbed(),
									expected, warded.healthLost()));
		} finally {
			player.removeEffect(MobEffects.DAMAGE_RESISTANCE);
			if (previousResistance != null) {
				player.addEffect(previousResistance);
			}
			player.setGameMode(previousGameMode);
			player.setAbsorptionAmount(previousAbsorption);
			player.setHealth(Math.min(previousHealth, player.getMaxHealth()));
			clearArmor(player);
			ArmorEquipmentAttributes.clear(player);
		}
	}

	private static void equipProtectionArmor(ServerPlayer player, boolean spellward) {
		clearArmor(player);
		for (EquipmentSlot slot : ARMOR) {
			Item item = switch (slot) {
				case HEAD -> Items.DIAMOND_HELMET;
				case CHEST -> Items.DIAMOND_CHESTPLATE;
				case LEGS -> Items.DIAMOND_LEGGINGS;
				case FEET -> Items.DIAMOND_BOOTS;
				default -> Items.DIAMOND_CHESTPLATE;
			};
			ItemStack stack = EximusEquipmentData.withUpgradeLevel(new ItemStack(item), 2);
			stack.enchant(Enchantments.ALL_DAMAGE_PROTECTION, 5);
			if (spellward && slot == EquipmentSlot.CHEST) {
				stack = EximusEquipmentData.withPrism(stack, 0,
						prism("spellward_prism", "prism_edge",
								"starlit_bowstring", true, null));
			}
			player.setItemSlot(slot, stack);
		}
		WornArmorState.invalidate(player);
		ArmorEquipmentAttributes.refresh(player);
	}

	private static MagicAbsorbProbe absorbMagicHit(ServerPlayer player, Zombie attacker) {
		player.setHealth(player.getMaxHealth());
		player.setAbsorptionAmount(10_000.0F);
		player.invulnerableTime = 0;
		player.hurtTime = 0;
		float healthBefore = player.getHealth();
		float absorptionBefore = player.getAbsorptionAmount();
		player.hurt(player.damageSources().indirectMagic(attacker, attacker), 100.0F);
		return new MagicAbsorbProbe(
				absorptionBefore - player.getAbsorptionAmount(),
				healthBefore - player.getHealth());
	}

	private static float damage(ServerPlayer player, DamageSource source, float amount) {
		LivingDamageEvent event = new LivingDamageEvent(player, source, amount);
		ArmorAffixEffects.onLivingDamage(event);
		return event.getAmount();
	}

	private record MagicAbsorbProbe(float absorbed, float healthLost) {
	}

	private static ItemStack prism(String armor, String melee, String ranged,
			boolean revealed, String pending) {
		ItemStack stack = new ItemStack(ModItems.ABYSSAL_STAR_PRISM.get());
		StarPrismData.write(stack, new StarPrismData(StarPrismData.SCHEMA,
				revealed, pending, armor, melee, ranged));
		return stack;
	}

	private static CompoundTag prismTag(String armor, String melee, String ranged) {
		return prism(armor, melee, ranged, true, null).getTag();
	}

	private static EquipmentSmithingRecipe recipe(EquipmentOperation operation) {
		return new EquipmentSmithingRecipe(
				ResourceLocation.fromNamespaceAndPath("summy_threat",
						"devcheck_" + operation.id()),
				operation);
	}

	private static SimpleContainer smith(Item template, ItemStack base, Item addition) {
		return smith(template, base, new ItemStack(addition));
	}

	private static SimpleContainer smith(Item template, ItemStack base, ItemStack addition) {
		SimpleContainer container = new SimpleContainer(3);
		container.setItem(0, new ItemStack(template));
		container.setItem(1, base.copy());
		container.setItem(2, addition.copy());
		return container;
	}

	private static double sumAmount(
			Multimap<net.minecraft.world.entity.ai.attributes.Attribute,
					AttributeModifier> modifiers,
			net.minecraft.world.entity.ai.attributes.Attribute attribute) {
		return modifiers.get(attribute).stream()
				.mapToDouble(AttributeModifier::getAmount)
				.sum();
	}

	private static int countItem(ServerPlayer player, Item item) {
		int count = 0;
		for (ItemStack stack : player.getInventory().items) {
			if (stack.is(item)) {
				count += stack.getCount();
			}
		}
		return count;
	}

	private static boolean close(double actual, double expected) {
		return Math.abs(actual - expected) <= 1.0E-4D;
	}

	private static void check(List<String> failures, String label, boolean ok, String detail) {
		SummyThreat.LOGGER.info("[DEVCHECK] {} {}｜{}", ok ? "OK  " : "FAIL", label, detail);
		if (!ok) {
			failures.add(label + "｜" + detail);
		}
	}
}
