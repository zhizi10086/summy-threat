package com.summy.threat.devcheck;

import com.summy.threat.SummyThreat;
import com.summy.threat.equipment.EquipmentCategory;
import com.summy.threat.equipment.EquipmentCompatibility;
import com.summy.threat.equipment.EximusEquipmentData;
import com.summy.threat.equipment.PrismProjectileData;
import com.summy.threat.equipment.RangedWeaponRuntime;
import com.summy.threat.equipment.StarPrismData;
import com.summy.threat.equipment.TConstructRangedCompat;
import com.summy.threat.item.ModItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.SimpleContainer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

/** B15 阶段 3 的隔离服务端兼容矩阵。 */
@Mod.EventBusSubscriber(modid = SummyThreat.MOD_ID,
		bus = Mod.EventBusSubscriber.Bus.FORGE,
		value = net.minecraftforge.api.distmarker.Dist.DEDICATED_SERVER)
public final class CompatibilityServerDevCheck {
	private static boolean ran;

	private CompatibilityServerDevCheck() {
	}

	@SubscribeEvent
	public static void onServerStarted(ServerStartedEvent event) {
		if (ran || !Boolean.getBoolean("summy_threat.devcheck")
				|| !Boolean.getBoolean("summy_threat.compatcheck")) {
			return;
		}
		ran = true;
		List<String> failures = new ArrayList<>();
		int[] checks = {0};
		ServerLevel level = event.getServer().overworld();
		ServerPlayer player = FakePlayerFactory.getMinecraft(level);
		try {
			if (ModList.get().isLoaded("tconstruct")) {
				runTconstruct(player, checks, failures);
			}
			if (ModList.get().isLoaded("tetra")) {
				runTetra(player, checks, failures);
			}
			if (ModList.get().isLoaded("apotheosis")) {
				runApotheosis(player, checks, failures);
			}
		} catch (Throwable throwable) {
			failures.add("兼容矩阵异常：" + throwable);
			throwable.printStackTrace();
		}
		SummyThreat.LOGGER.info("[COMPAT] checks={} FAIL={}", checks[0], failures.size());
		for (String failure : failures) {
			SummyThreat.LOGGER.error("[COMPAT] FAILED｜{}", failure);
		}
		if (failures.isEmpty()) {
			SummyThreat.LOGGER.info("[COMPAT] B15 阶段 3 兼容矩阵通过");
		}
		event.getServer().halt(false);
	}

	private static void runTconstruct(ServerPlayer player, int[] checks,
			List<String> failures) throws Exception {
		assertCategory(checks, failures, "tconstruct:plate_chestplate",
				EquipmentCategory.ARMOR);
		assertCategory(checks, failures, "tconstruct:cleaver", EquipmentCategory.MELEE);
		assertCategory(checks, failures, "tconstruct:longbow", EquipmentCategory.RANGED);
		assertCategory(checks, failures, "tconstruct:crossbow", EquipmentCategory.RANGED);
		assertCategory(checks, failures, "tconstruct:pickaxe", null);

		ItemStack armor = new ItemStack(requireItem("tconstruct:plate_chestplate"));
		initializeTinkers(armor, player);
		CompoundTag modularBefore = armor.getOrCreateTag().copy();
		ItemStack upgraded = EximusEquipmentData.withUpgradeLevel(armor, 2);
		check(checks, failures, "匠魂护甲两阶升级保留 tic_*",
				upgraded.getTag() != null
						&& upgraded.getTag().getCompound("tic_materials")
						.equals(modularBefore.getCompound("tic_materials"))
						&& upgraded.getTag().getCompound("tic_stats")
						.equals(modularBefore.getCompound("tic_stats")),
				"materials=" + (upgraded.getTag() == null ? "missing"
						: upgraded.getTag().get("tic_materials")));

		int maxDamage = upgraded.getMaxDamage();
		upgraded.setDamageValue(0);
		int beforeDamage = upgraded.getDamageValue();
		Object tool = staticInvoke("slimeknights.tconstruct.library.tools.nbt.ToolStack",
				"from", new Class<?>[]{ItemStack.class}, upgraded);
		Object modifier = staticField(
				"slimeknights.tconstruct.library.modifiers.ModifierId", "EMPTY");
		Method damage = Arrays.stream(Class.forName(
						"slimeknights.tconstruct.library.tools.helper.ToolDamageUtil")
				.getMethods()).filter(method -> method.getName().equals("damage")
						&& method.getParameterCount() == 5)
				.findFirst().orElseThrow();
		damage.invoke(null, tool, 20, player, upgraded, modifier);
		int actualLoss = upgraded.getDamageValue() - beforeDamage;
		check(checks, failures, "匠魂直通耐久入口应用 II 阶上限",
				maxDamage > 0 && actualLoss > 0 && actualLoss <= 6,
				"max=" + maxDamage + ", loss=" + actualLoss);

		ItemStack crossbow = new ItemStack(requireItem("tconstruct:crossbow"));
		initializeTinkers(crossbow, player);
		crossbow = EximusEquipmentData.withPrism(
				EximusEquipmentData.withUpgradeLevel(crossbow, 1),
				0, prism("comet_fletching"));
		player.setItemSlot(EquipmentSlot.MAINHAND, crossbow);
		TConstructRangedCompat.onRightClick(
				new PlayerInteractEvent.RightClickItem(player, InteractionHand.MAIN_HAND));
		AbstractArrow first = arrow(player);
		AbstractArrow second = arrow(player);
		if (first != null) {
			RangedWeaponRuntime.onEntityJoin(new EntityJoinLevelEvent(first, player.level()));
		}
		if (second != null) {
			RangedWeaponRuntime.onEntityJoin(new EntityJoinLevelEvent(second, player.level()));
		}
		PrismProjectileData firstData = first == null ? null
				: PrismProjectileData.read(first.getPersistentData()).orElse(null);
		PrismProjectileData secondData = second == null ? null
				: PrismProjectileData.read(second.getPersistentData()).orElse(null);
		check(checks, failures, "匠魂弩多箭共享 shot 快照",
				firstData != null && secondData != null
						&& firstData.shotId().equals(secondData.shotId()),
				"first=" + firstData + ", second=" + secondData);
		if (first != null) {
			first.discard();
		}
		if (second != null) {
			second.discard();
		}
		player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
	}

	private static void runTetra(ServerPlayer player, int[] checks,
			List<String> failures) throws Exception {
		assertCategory(checks, failures, "tetra:modular_sword", EquipmentCategory.MELEE);
		assertCategory(checks, failures, "tetra:modular_double", EquipmentCategory.MELEE);
		assertCategory(checks, failures, "tetra:modular_bow", EquipmentCategory.RANGED);
		assertCategory(checks, failures, "tetra:modular_crossbow", EquipmentCategory.RANGED);
		assertCategory(checks, failures, "tetra:modular_single", null);
		assertCategory(checks, failures, "tetra:dynamic_handheld", null);

		Class<?> swordClass = Class.forName(
				"se.mickelus.tetra.items.modular.impl.ModularBladedItem");
		Method create = swordClass.getMethod("createItemStack",
				String.class, String.class, String.class);
		ItemStack sword = (ItemStack) create.invoke(null, "short_blade", "iron", "stick");
		String blade = sword.getOrCreateTag().getString("sword/blade");
		String bladeMaterial = sword.getOrCreateTag().getString("sword/blade_material");
		sword = EximusEquipmentData.withPrism(
				EximusEquipmentData.withUpgradeLevel(sword, 2), 0,
				prism("prism_edge"));
		check(checks, failures, "Tetra 剑升级镶嵌保留模块键",
				blade.equals(sword.getOrCreateTag().getString("sword/blade"))
						&& bladeMaterial.equals(
								sword.getOrCreateTag().getString("sword/blade_material")),
				"blade=" + blade + ", material=" + bladeMaterial);

		verifyTetraShot(player, checks, failures, "tetra:modular_bow", 20);
		verifyTetraShot(player, checks, failures, "tetra:modular_crossbow", 1);
	}

	private static void verifyTetraShot(ServerPlayer player, int[] checks,
			List<String> failures, String itemId, int drawProgress) throws Exception {
		ItemStack weapon = EximusEquipmentData.withPrism(
				EximusEquipmentData.withUpgradeLevel(
						new ItemStack(requireItem(itemId)), 1), 0,
				prism("comet_fletching"));
		ItemStack ammo = new ItemStack(Items.ARROW);
		Class<?> looseClass = Class.forName(
				"se.mickelus.tetra.event.ModularLooseProjectilesEvent");
		Constructor<?> looseCtor = looseClass.getDeclaredConstructor(
				ItemStack.class, ItemStack.class, net.minecraft.world.entity.player.Player.class,
				net.minecraft.world.level.Level.class, int.class, double.class,
				boolean.class, float.class, double.class, float.class,
				boolean.class, int.class, double.class, double.class);
		Event loose = (Event) looseCtor.newInstance(weapon, ammo, player, player.level(),
				drawProgress, 6.0D, false, 1.0F, 0.0D, 1.0F,
				false, 2, 0.0D, 0.0D);
		MinecraftForge.EVENT_BUS.post(loose);

		Class<?> spawnClass = Class.forName(
				"se.mickelus.tetra.event.ModularProjectileSpawnEvent");
		Constructor<?> spawnCtor = spawnClass.getDeclaredConstructor(
				ItemStack.class, ItemStack.class, LivingEntity.class,
				AbstractArrow.class, net.minecraft.world.level.Level.class, int.class);
		AbstractArrow first = arrow(player);
		AbstractArrow second = arrow(player);
		if (first != null) {
			MinecraftForge.EVENT_BUS.post((Event) spawnCtor.newInstance(
					weapon, ammo, player, first, player.level(), drawProgress));
		}
		if (second != null) {
			MinecraftForge.EVENT_BUS.post((Event) spawnCtor.newInstance(
					weapon, ammo, player, second, player.level(), drawProgress));
		}
		PrismProjectileData firstData = first == null ? null
				: PrismProjectileData.read(first.getPersistentData()).orElse(null);
		PrismProjectileData secondData = second == null ? null
				: PrismProjectileData.read(second.getPersistentData()).orElse(null);
		check(checks, failures, "Tetra " + itemId + " 齐射共享 shot",
				firstData != null && secondData != null
						&& firstData.shotId().equals(secondData.shotId()),
				"first=" + firstData + ", second=" + secondData);
		if (first != null) {
			first.discard();
		}
		if (second != null) {
			second.discard();
		}
	}

	private static void runApotheosis(ServerPlayer player, int[] checks,
			List<String> failures) throws Exception {
		ItemStack base = EximusEquipmentData.withPrism(
				EximusEquipmentData.withPrism(
						EximusEquipmentData.withUpgradeLevel(
								new ItemStack(Items.DIAMOND_CHESTPLATE), 2),
						0, prism("astral_plating")),
				1, prism("starlight_guard"));
		CompoundTag stBefore = stData(base);
		Object rarityHolder = staticInvoke(
				"dev.shadowsoffire.apotheosis.adventure.loot.RarityRegistry",
				"byLegacyId", new Class<?>[]{String.class}, "rare");
		Object rarity = invoke(rarityHolder, "get");
		Class<?> rarityClass = Class.forName(
				"dev.shadowsoffire.apotheosis.adventure.loot.LootRarity");
		ItemStack reforged = (ItemStack) staticInvoke(
				"dev.shadowsoffire.apotheosis.adventure.loot.LootController",
				"createLootItem",
				new Class<?>[]{ItemStack.class, rarityClass, RandomSource.class},
				base.copy(), rarity, RandomSource.create(20261116L));
		check(checks, failures, "神化重铸保留 ST 双槽",
				reforged.getTag() != null
						&& stBefore.equals(stData(reforged))
						&& reforged.getTag().contains("affix_data"),
				"tag=" + reforged.getTag());

		CompoundTag apoBefore = affixData(reforged).copy();
		ItemStack reverse = EximusEquipmentData.withUpgradeLevel(reforged, 2);
		check(checks, failures, "ST 升级镶嵌保留 affix_data",
				stBefore.equals(stData(reverse)) && apoBefore.equals(affixData(reverse)),
				"affix=" + affixData(reverse) + ", st=" + stData(reverse));

		ItemStack socketed = addApotheosisSocket(reverse, player);
		socketed = socketApotheosisGem(socketed, player);
		check(checks, failures, "神化开孔与宝石保留 ST 数据",
				stBefore.equals(stData(socketed))
						&& sameExistingAffixFields(apoBefore, affixData(socketed)),
				"tag=" + socketed.getTag());

		ItemStack disassembled = EximusEquipmentData.clearPrisms(socketed);
		check(checks, failures, "ST 同步拆卸保留神化槽位与宝石",
				sameExistingAffixFields(apoBefore, affixData(disassembled))
						&& StPrismEmpty(disassembled),
				"tag=" + disassembled.getTag());
	}

	private static ItemStack addApotheosisSocket(ItemStack input, ServerPlayer player)
			throws Exception {
		Object helper = Class.forName(
				"dev.shadowsoffire.apotheosis.adventure.socket.SocketHelper");
		staticInvoke((Class<?>) helper, "setSockets",
				new Class<?>[]{ItemStack.class, int.class},
				input, 2);
		return input;
	}

	private static ItemStack socketApotheosisGem(ItemStack input, ServerPlayer player)
			throws Exception {
		Class<?> gemClass = Class.forName(
				"dev.shadowsoffire.apotheosis.adventure.socket.gem.Gem");
		Class<?> gemRegistry = Class.forName(
				"dev.shadowsoffire.apotheosis.adventure.socket.gem.GemRegistry");
		Method create = gemRegistry.getMethod("createRandomGemStack",
				RandomSource.class, ServerLevel.class, float.class, Predicate[].class);
		Object recipe = Class.forName(
						"dev.shadowsoffire.apotheosis.adventure.socket.SocketingRecipe")
				.getConstructor().newInstance();
		int base = ((Number) staticField(
				"dev.shadowsoffire.apotheosis.adventure.AdventureModule$ApothSmithingRecipe",
				"BASE")).intValue();
		int addition = ((Number) staticField(
				"dev.shadowsoffire.apotheosis.adventure.AdventureModule$ApothSmithingRecipe",
				"ADDITION")).intValue();
		for (int attempt = 0; attempt < 64; attempt++) {
			ItemStack gem = (ItemStack) create.invoke(null, RandomSource.create(attempt + 7L),
					player.serverLevel(), 1.0F, new Predicate[0]);
			SimpleContainer container = new SimpleContainer(3);
			container.setItem(base, input);
			container.setItem(addition, gem);
			boolean matches = (boolean) invoke(recipe, "matches", new Class<?>[]{
					net.minecraft.world.Container.class, net.minecraft.world.level.Level.class},
					container, player.level());
			if (matches) {
				return (ItemStack) invoke(recipe, "assemble", new Class<?>[]{
						net.minecraft.world.Container.class,
						net.minecraft.core.RegistryAccess.class},
						container, player.level().registryAccess());
			}
		}
		throw new IllegalStateException("未生成可镶嵌到钻石胸甲的神化宝石");
	}

	private static void assertCategory(int[] checks, List<String> failures,
			String itemId, EquipmentCategory expected) {
		Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(itemId));
		EquipmentCategory actual = item == null || item == Items.AIR ? null
				: EquipmentTypeResolverProxy.category(new ItemStack(item));
		check(checks, failures, "兼容分类 " + itemId,
				actual == expected, "expected=" + expected + ", actual=" + actual);
	}

	/** 避免在自检入口重复暴露解析器全部实现。 */
	private static final class EquipmentTypeResolverProxy {
		private static EquipmentCategory category(ItemStack stack) {
			return com.summy.threat.equipment.EquipmentTypeResolver.category(stack);
		}
	}

	private static ItemStack prism(String rangedAffix) {
		ItemStack prism = new ItemStack(ModItems.ABYSSAL_STAR_PRISM.get());
		StarPrismData.write(prism, new StarPrismData(StarPrismData.SCHEMA, true, null,
				"astral_plating", "prism_edge", rangedAffix));
		return prism;
	}

	private static AbstractArrow arrow(ServerPlayer player) {
		AbstractArrow arrow = EntityType.ARROW.create(player.serverLevel());
		if (arrow != null) {
			arrow.setOwner(player);
		}
		return arrow;
	}

	private static void initializeTinkers(ItemStack stack, ServerPlayer player)
			throws Exception {
		Method method = stack.getItem().getClass().getMethod("onCraftedBy",
				ItemStack.class, net.minecraft.world.level.Level.class,
				net.minecraft.world.entity.player.Player.class);
		method.invoke(stack.getItem(), stack, player.level(), player);
	}

	private static Item requireItem(String id) {
		Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(id));
		if (item == null || item == Items.AIR) {
			throw new IllegalStateException("缺少物品 " + id);
		}
		return item;
	}

	private static CompoundTag stData(ItemStack stack) {
		return stack.getOrCreateTag().getCompound(EximusEquipmentData.EQUIPMENT_ROOT)
				.copy();
	}

	private static CompoundTag affixData(ItemStack stack) {
		return stack.getOrCreateTag().getCompound("affix_data").copy();
	}

	private static boolean sameExistingAffixFields(CompoundTag before, CompoundTag after) {
		CompoundTag expected = before.copy();
		CompoundTag actual = after.copy();
		expected.remove("gems");
		actual.remove("gems");
		return expected.equals(actual) && after.contains("gems");
	}

	private static boolean StPrismEmpty(ItemStack stack) {
		return EximusEquipmentData.prism(stack, 0).isEmpty()
				&& EximusEquipmentData.prism(stack, 1).isEmpty();
	}

	private static Object staticInvoke(String className, String methodName,
			Class<?>[] parameterTypes, Object... args) throws Exception {
		return staticInvoke(Class.forName(className), methodName, parameterTypes, args);
	}

	private static Object staticInvoke(Class<?> type, String methodName,
			Class<?>[] parameterTypes, Object... args) throws Exception {
		return type.getMethod(methodName, parameterTypes).invoke(null, args);
	}

	private static Object staticField(String className, String fieldName)
			throws Exception {
		Field field = Class.forName(className).getField(fieldName);
		return field.get(null);
	}

	private static Object invoke(Object target, String methodName,
			Class<?>[] parameterTypes, Object... args) throws Exception {
		return target.getClass().getMethod(methodName, parameterTypes)
				.invoke(target, args);
	}

	private static Object invoke(Object target, String methodName) throws Exception {
		return target.getClass().getMethod(methodName).invoke(target);
	}

	private static void check(int[] checks, List<String> failures, String label,
			boolean ok, String detail) {
		checks[0]++;
		if (!ok) {
			failures.add(label + "｜" + detail);
		}
		SummyThreat.LOGGER.info("[COMPAT] {} {}｜{}", ok ? "OK" : "FAIL", label, detail);
	}
}
