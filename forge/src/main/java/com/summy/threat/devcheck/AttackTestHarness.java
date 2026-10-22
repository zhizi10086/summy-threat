package com.summy.threat.devcheck;

import com.summy.threat.SummyThreat;
import com.summy.threat.Config;
import com.summy.threat.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Blocks;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A11 玩家攻击测试环境（dev-only）。
 *
 * <p>通过 {@code -Pattacktest} 启用：进入 DevCheckWorld 后自动发放毕业天使装备、
 * 生成三只 NoAI 木桩，然后保持运行，由玩家手动关闭客户端。
 *
 * <p>{@code -Pattacktest.auto} 为自动统计模式：只生成 1,000,000 血统计木桩，
 * 玩家手动佩戴后在聊天框输入 {@code 1}，服务端以满蓄力 {@code Player.attack}
 * 连续执行 20,000 次攻击，并用 SR 现有 HolyLight 计数器统计圣光触发。
 *
 * <p>{@code -Pattacktest.mid.angel} / {@code -Pattacktest.mid.demon} 为 A11 中期口径：
 * 钻石套保护 III、钻石剑锋利 III、力量 II；天使只带圣光 + 神圣斗篷，
 * 恶魔只带复仇之魂 + 咒印，不混入毕业饰品与回响套。
 */
@Mod.EventBusSubscriber(modid = SummyThreat.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class AttackTestHarness {
	private static final boolean ENABLED = Boolean.getBoolean("summy_threat.attacktest");
	private static final boolean AUTO = Boolean.getBoolean("summy_threat.attacktest.auto");
	private static final boolean AUTOSTART = Boolean.getBoolean("summy_threat.attacktest.autostart");
	private static final boolean COMPAT = Boolean.getBoolean("summy_threat.attacktest.compat");
	private static final boolean DEMON = Boolean.getBoolean("summy_threat.attacktest.demon");
	private static final boolean MID = Boolean.getBoolean("summy_threat.attacktest.mid");
	private static final String TIER = System.getProperty("summy_threat.attacktest.tier", "full")
			.toLowerCase(java.util.Locale.ROOT);
	private static final boolean STATS_ONLY = Boolean.getBoolean("summy_threat.attacktest.stats") || AUTO;
	private static final String[] ANGEL_CURIOS = {
			"the_halo", "virtues", "the_body", "the_mind", "the_soul",
			"godhead", "sacred_heart", "holy_light", "pentagram"
	};
	private static final String[] DEMON_CURIOS = {
			"the_pact", "ceremonial_robes", "abaddon", "the_mark",
			"abyss_lord", "occult_eye", "satanic_bible", "devil_crown", "pentagram"
	};
	// 路线核心件在进线后必然拥有：美德 + 光环 / 撒旦圣经 + 恶魔王冠。
	private static final String[] MID_ANGEL_CURIOS =
			{"virtues", "the_halo", "holy_light", "holy_mantle", "pentagram"};
	private static final String[] MID_DEMON_CURIOS =
			{"satanic_bible", "devil_crown", "vengeful_spirit", "the_mark", "pentagram"};
	private static final int AUTO_ATTACKS = Math.max(1, Integer.getInteger("summy_threat.attacktest.attacks", 20_000));
	// SE 的 [SE-SR] 调试日志每 tick 最多 8 条；8/tick 保证 20,000 次全部入日志。
	private static final int AUTO_BATCH = 8;
	/** 狱火：hellfire_damage_per_level_per_second = 1.0，上限 10 级 → 10 DPS。 */
	private static final double HELLFIRE_DPS_AT_MAX = 10.0D;
	/** 复仇之魂：默认每秒 6 点狱火伤害；自动回满木桩会抹掉这部分，结算时补回。 */
	private static final double VENGEFUL_DPS = 6.0D;
	private static final double AUTO_X = 300.5D;
	private static final double AUTO_Y = 100.0D;
	private static final double AUTO_PLAYER_Z = 13.5D;
	private static final double AUTO_TARGET_Z = 15.0D;
	private static int serverTick;
	private static boolean autoRequested;
	private static boolean autoStarted;
	private static boolean autoDone;
	private static int autoAttempts;
	private static int autoSuccess;
	private static double autoTotalDamage;
	private static int debugHurtLogs;
	private static int debugDamageEvents;
	private static double debugDamageTotal;
	private static double debugDamageMin = Double.MAX_VALUE;
	private static double debugDamageMax;
	private static final Map<String, Double> DEBUG_DAMAGE_BY_SOURCE = new HashMap<>();

	private AttackTestHarness() {
	}

	@SubscribeEvent
	public static void onServerTick(TickEvent.ServerTickEvent event) {
		if (!ENABLED || event.phase != TickEvent.Phase.END) {
			return;
		}
		if (event.getServer().getPlayerList().getPlayers().isEmpty()) {
			serverTick = 0;
			resetAutoState();
			return;
		}
		ServerPlayer player = event.getServer().getPlayerList().getPlayers().get(0);
		serverTick++;
		try {
			switch (serverTick) {
				case 20 -> prepareWorld(player);
				case 40 -> preparePlayer(player);
				case 70 -> spawnTargets(player);
				case 90 -> hint(player);
				case 120 -> refill(player);
				case 600, 1200 -> reportAttributes(player);
				default -> {
				}
			}
			if (AUTO && !autoDone) {
				if (AUTOSTART && !autoStarted && serverTick >= 120) {
					if (startAuto(player)) {
						autoStarted = true;
					} else {
						autoDone = true;
					}
				}
				if (autoRequested && !autoStarted && serverTick >= 120) {
					autoRequested = false;
					if (startAuto(player)) {
						autoStarted = true;
					}
				}
				if (autoStarted) {
					runAutoBatch(player, event.getServer());
				}
			}
		} catch (Throwable throwable) {
			SummyThreat.LOGGER.error("[A11] 环境准备异常", throwable);
			player.sendSystemMessage(Component.literal("§c[A11] 环境准备异常：" + throwable));
		}
	}

	/** 自动模式：玩家佩戴完成后在聊天框发送 1 启动；单机只认第一个玩家。 */
	@SubscribeEvent
	public static void onChat(ServerChatEvent event) {
		if (!AUTO || autoDone || !"1".equals(event.getMessage().getString().trim())) {
			return;
		}
		ServerPlayer player = event.getPlayer();
		MinecraftServer server = player.getServer();
		if (server == null || server.getPlayerList().getPlayers().isEmpty()
				|| server.getPlayerList().getPlayers().get(0) != player) {
			return;
		}
		autoRequested = true;
		player.sendSystemMessage(Component.literal("§b[A11] 收到启动指令，准备自动攻击..."));
	}

	/** 诊断前 10 次木桩结算，分别记录减伤前与最终落地金额。 */
	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onTargetHurt(LivingHurtEvent event) {
		logTargetDamage("HURT", event.getEntity(), event.getAmount(), event.getSource().getMsgId());
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onTargetDamage(LivingDamageEvent event) {
		logTargetDamage("DAMAGE", event.getEntity(), event.getAmount(), event.getSource().getMsgId());
	}

	private static void logTargetDamage(String phase, net.minecraft.world.entity.LivingEntity target, float amount,
			String sourceId) {
		if (!ENABLED || !autoStarted || !target.getTags().contains("a11_target_stats")) {
			return;
		}
		if ("DAMAGE".equals(phase)) {
			debugDamageEvents++;
			debugDamageTotal += amount;
			debugDamageMin = Math.min(debugDamageMin, amount);
			debugDamageMax = Math.max(debugDamageMax, amount);
			DEBUG_DAMAGE_BY_SOURCE.merge(sourceId, (double) amount, Double::sum);
		}
		if (debugHurtLogs >= 10) {
			return;
		}
		SummyThreat.LOGGER.info("[A11] {} amount={} health={}/{}",
				phase, amount, target.getHealth(), target.getMaxHealth());
		if ("DAMAGE".equals(phase)) {
			debugHurtLogs++;
		}
	}

	private static void prepareWorld(ServerPlayer player) {
		ServerLevel level = player.serverLevel();
		for (int x = -6; x <= 6; x++) {
			for (int z = -2; z <= 18; z++) {
				level.setBlockAndUpdate(new BlockPos(300 + x, 99, z), Blocks.STONE.defaultBlockState());
				level.setBlockAndUpdate(new BlockPos(300 + x, 100, z), Blocks.AIR.defaultBlockState());
			}
		}
		player.teleportTo(level, 300.0D, 100.0D, 0.0D, 0.0F, 0.0F);
		run(player, "gamerule doMobSpawning false");
		run(player, "gamerule doDaylightCycle false");
		run(player, "gamerule keepInventory true");
		// 僵尸木桩在白天会燃烧，锁到午夜并关闭昼夜循环，避免额外掉血污染 TTK。
		run(player, "time set midnight");
		run(player, "weather clear");
		run(player, "difficulty normal");
		run(player, "summythreat tier " + TIER);
		run(player, "summythreat tl 20");
		// A/B 只改运行期值，不写回 world/serverconfig。
		Config.ENABLE_RELIQUARY_FIXED_DAMAGE_BOOSTS.set(COMPAT);
		SummyThreat.LOGGER.info("[A11] TIER SET {} FIXED_DAMAGE_COMPAT={}", TIER, COMPAT);
	}

	private static void preparePlayer(ServerPlayer player) {
		String name = player.getName().getString();
		clearPlayer(player);
		if (MID) {
			if (!prepareMid(player, name)) {
				return;
			}
		} else if (DEMON) {
			if (!prepareDemon(player, name)) {
				return;
			}
		} else {
			prepareAngel(player, name);
		}
		refill(player);
		// 1 血恶咒 + 死神祝福是 A11 的固定输出口径；恶魔王冠也按已损失生命取满层。
		// 中期没有恶咒 / 死神祝福，保持满血，避免把毕业流派的乘区混进中期样本。
		if (!MID) {
			player.setHealth(1.0F);
		}
	}

	private static void prepareAngel(ServerPlayer player, String name) {
		if (!resetReliquaryRoutes(player)) {
			player.sendSystemMessage(Component.literal("§c[A11] 旧路线状态重置失败，未准备天使装备"));
			return;
		}
		clearPlayer(player);
		player.teleportTo(player.serverLevel(), 300.0D, 100.0D, 0.0D, 0.0F, 0.0F);
		// 天使线需要七罪全部赎罪，否则对应饰品无法使用。
		run(player, "summyreliquary sin all redeem");
		equipEchoArmor(player);
		// 玩家名替代 @s：单人集成服里 /summyreliquary 的 @s 解析不稳定。
		run(player, "effect give " + name + " summy_enchantment:strength 999999 1 true");
		// angel 是 PLAYER_ONLY 命令，不接受玩家名参数；由当前玩家自己执行。
		run(player, "summyreliquary angel grant");
		// Curios 没有稳定的命令佩戴入口：饰品给到背包，由玩家手动装入。
		// 注意：SR 的实际注册命名空间是 summy-reliquary（连字符），不是 modId 的下划线形式。
		run(player, "give " + name + " summy-reliquary:virtues");
		run(player, "give " + name + " summy-reliquary:the_halo");
		// 五芒星是护符栏饰品，不分天使/恶魔路线；用 SR 的补发命令而不是裸 give，顺便置位发放标记。
		run(player, "summyreliquary pentagram grant " + name);
		run(player, "give " + name + " summy-reliquary:the_body");
		run(player, "give " + name + " summy-reliquary:the_mind");
		run(player, "give " + name + " summy-reliquary:the_soul");
		run(player, "give " + name + " summy-reliquary:godhead");
		run(player, "give " + name + " summy-reliquary:sacred_heart");
		run(player, "give " + name + " summy-reliquary:holy_light");
		giveTestWeapon(player, name, "seraph_spear");
		// 毕业路线也自动佩戴；槽位先同步，避免刚 grant 时 Curios 格数还是 0。
		DemonSetupBridge.syncSlots(player);
		equipFullAngel(player);
	}

	/**
	 * 恶魔毕业：先用 SR 创世纪清掉旧天使/启示/七罪/恶魔状态，再设置恶魔标记、
	 * 邪恶 1000 与全部解锁；The Pact 必须佩戴，DemonPact 的攻速/攻击加成才会生效。
	 */
	private static boolean prepareDemon(ServerPlayer player, String name) {
		if (!DemonSetupBridge.available()) {
			player.sendSystemMessage(Component.literal("§c[A11] 恶魔配置反射桥不可用，未准备装备"));
			return false;
		}
		if (!DemonSetupBridge.performFullReset(player)) {
			player.sendSystemMessage(Component.literal("§c[A11] 恶魔配置全量重置失败，未准备装备"));
			return false;
		}
		clearPlayer(player);
		// performReset 会传送到重生点；回到测试平台。
		player.teleportTo(player.serverLevel(), 300.0D, 100.0D, 0.0D, 0.0F, 0.0F);
		equipEchoArmor(player);
		run(player, "effect give " + name + " summy_enchantment:strength 999999 1 true");
		run(player, "summyreliquary sin all on");
		run(player, "summyreliquary demon grant " + name);
		if (!DemonSetupBridge.applyDemonState(player)) {
			player.sendSystemMessage(Component.literal("§c[A11] 恶魔状态设置失败，未准备装备"));
			return false;
		}
		// 恶魔契约 + 背饰 + 启示 + 灵台 + 双加护 + 魂印 + 光环 + 护符。
		run(player, "give " + name + " summy-reliquary:the_pact");
		run(player, "give " + name + " summy-reliquary:ceremonial_robes");
		run(player, "give " + name + " summy-reliquary:abaddon");
		run(player, "give " + name + " summy-reliquary:the_mark");
		run(player, "give " + name + " summy-reliquary:abyss_lord");
		run(player, "give " + name + " summy-reliquary:occult_eye");
		run(player, "give " + name + " summy-reliquary:satanic_bible");
		run(player, "give " + name + " summy-reliquary:devil_crown");
		run(player, "summyreliquary pentagram grant " + name);
		giveTestWeapon(player, name, "dark_arts");
		DemonSetupBridge.syncSlots(player);
		equipFullDemon(player);
		return true;
	}

	/**
	 * A11 中期配置：钻石套保护 III、圣光短矛/献祭匕首锋利 III、力量 II。
	 * 天使带美德 + 光环 + 圣光 + 神圣斗篷；恶魔带撒旦圣经 + 恶魔王冠 + 复仇之魂 + 咒印。
	 */
	private static boolean prepareMid(ServerPlayer player, String name) {
		if (!resetReliquaryRoutes(player)) {
			player.sendSystemMessage(Component.literal("§c[A11] 旧路线状态重置失败，未准备中期装备"));
			return false;
		}
		clearPlayer(player);
		player.teleportTo(player.serverLevel(), 300.0D, 100.0D, 0.0D, 0.0F, 0.0F);
		equipMidGear(player);
		giveStrength(player, name);
		if (!srPresent()) {
			player.sendSystemMessage(Component.literal("§e[A11] 当前未安装 SR，中期路线饰品跳过；仅测钻石套基础输出"));
			return true;
		}
		if (DEMON) {
			run(player, "summyreliquary sin all on");
			run(player, "summyreliquary demon grant " + name);
			if (!DemonSetupBridge.applyDemonState(player)) {
				player.sendSystemMessage(Component.literal("§c[A11] 恶魔状态设置失败，未准备中期饰品"));
				return false;
			}
			equipCurio(player, "soul_seal", 0, "satanic_bible");
			equipCurio(player, "halo", 0, "devil_crown");
			equipCurio(player, "blessing", 0, "vengeful_spirit");
			equipCurio(player, "spirit_altar", 0, "the_mark");
			equipCurio(player, "charm", 0, "pentagram");
		} else {
			run(player, "summyreliquary sin all redeem");
			run(player, "summyreliquary angel grant");
			equipCurio(player, "soul_seal", 0, "virtues");
			equipCurio(player, "halo", 0, "the_halo");
			equipCurio(player, "blessing", 0, "holy_light");
			equipCurio(player, "blessing", 1, "holy_mantle");
			equipCurio(player, "charm", 0, "pentagram");
		}
		return true;
	}

	/** 毕业天使：把发放的 SR 饰品实际装入 Curios。 */
	private static void equipFullAngel(ServerPlayer player) {
		equipCurio(player, "soul_seal", 0, "virtues");
		equipCurio(player, "halo", 0, "the_halo");
		equipCurio(player, "spirit_altar", 0, "the_body");
		equipCurio(player, "spirit_altar", 1, "the_mind");
		equipCurio(player, "spirit_altar", 2, "the_soul");
		equipCurio(player, "revelation", 0, "godhead");
		equipCurio(player, "blessing", 0, "sacred_heart");
		equipCurio(player, "blessing", 1, "holy_light");
		equipCurio(player, "charm", 0, "pentagram");
	}

	/** 毕业恶魔：把发放的 SR 饰品实际装入 Curios。 */
	private static void equipFullDemon(ServerPlayer player) {
		equipCurio(player, "soul_seal", 0, "satanic_bible");
		equipCurio(player, "halo", 0, "devil_crown");
		equipCurio(player, "spirit_altar", 0, "the_mark");
		equipCurio(player, "revelation", 0, "abaddon");
		equipCurio(player, "blessing", 0, "abyss_lord");
		equipCurio(player, "blessing", 1, "occult_eye");
		equipCurio(player, "demon_pact", 0, "the_pact");
		equipCurio(player, "back", 0, "ceremonial_robes");
		equipCurio(player, "charm", 0, "pentagram");
	}

	/** 中期只有固定几件路线饰品，直接装入 Curios，避免每次手测重复操作。 */
	private static boolean equipCurio(ServerPlayer player, String slot, int index, String itemId) {
		Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("summy-reliquary", itemId));
		if (item == null) {
			return false;
		}
		CuriosApi.getCuriosInventory(player).ifPresent(handler ->
				handler.setEquippedCurio(slot, index, new ItemStack(item)));
		return CuriosApi.getCuriosInventory(player)
				.map(handler -> handler.findFirstCurio(item).isPresent())
				.orElse(false);
	}

	/** 用 SR 创世纪做一次全量路线重置；未安装 SR 时视为无需重置。 */
	private static boolean resetReliquaryRoutes(ServerPlayer player) {
		if (!srPresent()) {
			return true;
		}
		return DemonSetupBridge.available() && DemonSetupBridge.performFullReset(player);
	}

	private static void equipMidGear(ServerPlayer player) {
		player.setItemSlot(EquipmentSlot.HEAD,
				enchanted(Items.DIAMOND_HELMET, "minecraft:protection", "3"));
		player.setItemSlot(EquipmentSlot.CHEST,
				enchanted(Items.DIAMOND_CHESTPLATE, "minecraft:protection", "3"));
		player.setItemSlot(EquipmentSlot.LEGS,
				enchanted(Items.DIAMOND_LEGGINGS, "minecraft:protection", "3"));
		player.setItemSlot(EquipmentSlot.FEET,
				enchanted(Items.DIAMOND_BOOTS, "minecraft:protection", "3"));
		Item weapon = DEMON
				? ForgeRegistries.ITEMS.getValue(new ResourceLocation("summy-reliquary", "sacrificial_dagger"))
				: ForgeRegistries.ITEMS.getValue(new ResourceLocation("summy-reliquary", "holy_spear"));
		if (weapon == null) {
			weapon = Items.DIAMOND_SWORD;
		}
		ItemStack weaponStack = enchanted(weapon, "minecraft:sharpness", "3");
		weaponStack.getOrCreateTag().putBoolean("Unbreakable", true);
		player.getInventory().setItem(0, weaponStack);
		player.getInventory().selected = 0;
	}

	/** 力量 II：装了 SE 用其乘算力量，否则退回原版力量药水效果。 */
	private static void giveStrength(ServerPlayer player, String name) {
		boolean seStrength = ForgeRegistries.MOB_EFFECTS.containsKey(
				new ResourceLocation("summy_enchantment", "strength"));
		String effect = seStrength ? "summy_enchantment:strength" : "minecraft:strength";
		run(player, "effect give " + name + " " + effect + " 999999 1 true");
	}

	private static boolean srPresent() {
		return ForgeRegistries.ITEMS.containsKey(new ResourceLocation("summy-reliquary", "holy_light"));
	}

	private static void equipEchoArmor(ServerPlayer player) {
		player.setItemSlot(EquipmentSlot.HEAD, enchanted(ModItems.ECHO_HELMET.get(),
				"summy_enchantment:health_boost", "5"));
		player.setItemSlot(EquipmentSlot.CHEST, enchanted(ModItems.ECHO_CHESTPLATE.get(),
				"summy_enchantment:health_boost", "5",
				"summy_enchantment:dead_mans_heart", "1",
				"summy_enchantment:calamity", "1",
				"summy_enchantment:deaths_blessing", "1"));
		player.setItemSlot(EquipmentSlot.LEGS, enchanted(ModItems.ECHO_LEGGINGS.get(),
				"summy_enchantment:health_boost", "5"));
		player.setItemSlot(EquipmentSlot.FEET, enchanted(ModItems.ECHO_BOOTS.get(),
				"summy_enchantment:health_boost", "5"));
	}

	private static void giveTestWeapon(ServerPlayer player, String name, String weaponId) {
		// 毕业武器：带完整的 SE 附魔，并设为不可损坏，保证 20k 过程中不会中途断剑。
		Item weapon = ForgeRegistries.ITEMS.getValue(new ResourceLocation("summy-reliquary", weaponId));
		if (weapon == null) {
			SummyThreat.LOGGER.error("[A11] 未找到测试武器 summy-reliquary:{}", weaponId);
			return;
		}
		ItemStack stack = enchanted(weapon,
				"summy_enchantment:sharpness_plus", "5",
				"summy_enchantment:spellblade", "5",
				"summy_enchantment:quick_strike", "5",
				"summy_enchantment:surprise", "3");
		stack.getOrCreateTag().putBoolean("Unbreakable", true);
		player.getInventory().add(stack);
	}

	/** 清空主背包、护甲、副手与 Curios，避免上一轮遗留影响本轮配置。 */
	private static void clearPlayer(ServerPlayer player) {
		player.getInventory().clearContent();
		CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
			for (ICurioStacksHandler stacks : handler.getCurios().values()) {
				clear(stacks.getStacks());
				clear(stacks.getCosmeticStacks());
			}
		});
	}

	private static void clear(IDynamicStackHandler handler) {
		for (int i = 0; i < handler.getSlots(); i++) {
			handler.setStackInSlot(i, ItemStack.EMPTY);
		}
	}

	private static void refill(ServerPlayer player) {
		player.setHealth(player.getMaxHealth());
		player.getFoodData().setFoodLevel(20);
		player.getFoodData().setSaturation(20.0F);
	}

	private static ItemStack enchanted(Item item, String... idLevelPairs) {
		ItemStack stack = new ItemStack(item);
		for (int i = 0; i + 1 < idLevelPairs.length; i += 2) {
			ResourceLocation id = ResourceLocation.tryParse(idLevelPairs[i]);
			var enchantment = id == null ? null : ForgeRegistries.ENCHANTMENTS.getValue(id);
			if (enchantment != null) {
				// 通过注册表正式附魔，避免手写 NBT 在 1.20.1/Forge 下未被 EnchantmentHelper 识别。
				stack.enchant(enchantment, Short.parseShort(idLevelPairs[i + 1]));
			} else {
				SummyThreat.LOGGER.warn("[A11] 未找到附魔 {}，测试装备将缺少该附魔", idLevelPairs[i]);
			}
		}
		return stack;
	}

	private static void spawnTargets(ServerPlayer player) {
		run(player, "kill @e[tag=a11_target_normal]");
		run(player, "kill @e[tag=a11_target_leader]");
		run(player, "kill @e[tag=a11_target_boss]");
		run(player, "kill @e[tag=a11_target_stats]");
		run(player, "kill @e[type=minecraft:zombie]");
		run(player, "kill @e[type=minecraft:warden]");

		if (!STATS_ONLY) {
			run(player, "summythreat spawn minecraft:zombie 20 normal 20 none");
			run(player, "tag @e[type=minecraft:zombie,limit=1,sort=nearest] add a11_target_normal");
			run(player, "tp @e[tag=a11_target_normal,limit=1] 300 100 3");
			run(player, "data merge entity @e[tag=a11_target_normal,limit=1] {NoAI:1b}");
			run(player, "summythreat ability @e[tag=a11_target_normal,limit=1] clear");

			run(player, "summythreat spawn minecraft:zombie 20 leader 20 none");
			run(player, "tag @e[type=minecraft:zombie,limit=1,sort=nearest] add a11_target_leader");
			run(player, "tp @e[tag=a11_target_leader,limit=1] 300 100 6");
			run(player, "data merge entity @e[tag=a11_target_leader,limit=1] {NoAI:1b}");
			run(player, "summythreat ability @e[tag=a11_target_leader,limit=1] clear");

			run(player, "summythreat spawn minecraft:warden 20 normal 20 none");
			run(player, "tag @e[type=minecraft:warden,limit=1,sort=nearest] add a11_target_boss");
			run(player, "tp @e[tag=a11_target_boss,limit=1] 300 100 9");
			run(player, "data merge entity @e[tag=a11_target_boss,limit=1] {NoAI:1b}");
			run(player, "summythreat ability @e[tag=a11_target_boss,limit=1] clear");
		}
		spawnStatsDummy(player);
	}

	/** 统计木桩：1,000,000 血、NoAI、无能力，用于累计随机触发率。 */
	private static void spawnStatsDummy(ServerPlayer player) {
		// 输出基准必须是零护甲木桩：先用 tl off 关闭全局接管，再用原版 summon 绕过 MonsterScaler。
		// 这样测得的是未减伤 DPS，实际怪物 TTK 再按 A11 矩阵里的 DR 折算。
		run(player, "summythreat tl off");
		run(player, "summon minecraft:zombie 300 100 15.5 {Tags:[\"a11_target_stats\"],NoAI:1b,"
				+ "Silent:1b,PersistenceRequired:1b,Health:1000000.0f,"
				+ "Attributes:[{Name:\"generic.max_health\",Base:1000000.0d},"
				+ "{Name:\"generic.armor\",Base:0.0d},"
				+ "{Name:\"generic.armor_toughness\",Base:0.0d}]}");
		// 即使 TL 关闭，跨档残留 / 其它事件仍可能挂上 ST 的 scaling modifier；
		// 直接移除 MonsterScaler 的护甲与韧性 UUID，保证输出基准确实为零护甲。
		run(player, "attribute @e[tag=a11_target_stats,limit=1] minecraft:generic.armor "
				+ "modifier remove 6f5c1a2e-0007-4a0b-9d1e-000000000001");
		run(player, "attribute @e[tag=a11_target_stats,limit=1] minecraft:generic.armor_toughness "
				+ "modifier remove 6f5c1a2e-0007-4a0b-9d1e-000000000002");
		run(player, "attribute @e[tag=a11_target_stats,limit=1] minecraft:generic.armor base set 0");
		run(player, "attribute @e[tag=a11_target_stats,limit=1] minecraft:generic.armor_toughness base set 0");
	}

	/** 聊天框输入 1 后：校验装备，固定玩家与木桩位置，重置自动统计。 */
	private static boolean startAuto(ServerPlayer player) {
		if (!AutoAttackDriver.attackAvailable()) {
			player.sendSystemMessage(Component.literal("§c[A11] attackStrengthTicker 反射不可用，自动攻击取消"));
			return false;
		}
		Mob target = findStatsDummy(player);
		if (target == null) {
			player.sendSystemMessage(Component.literal("§c[A11] 未找到 a11_target_stats 木桩，自动攻击取消"));
			return false;
		}
		int weaponSlot = findWeaponSlot(player);
		if (weaponSlot < 0 && MID && !srPresent()) {
			// 仅 SE 环境没有 SR 武器；自动启动前再落一次，避免物品同步时序丢失。
			ItemStack sword = enchanted(Items.DIAMOND_SWORD, "minecraft:sharpness", "3");
			player.setItemSlot(EquipmentSlot.MAINHAND, sword);
			player.getInventory().setItem(0, sword);
			player.getInventory().selected = 0;
			weaponSlot = 0;
		}
		if (weaponSlot < 0) {
			player.sendSystemMessage(Component.literal("§c[A11] 背包里没有"
					+ weaponName() + "，自动攻击取消"));
			return false;
		}
		List<String> missing = missingCurios(player);
		if (!MID && !hasEnchant(player.getItemBySlot(EquipmentSlot.CHEST), "summy_enchantment:calamity")) {
			missing.add("胸甲恶咒");
		}
		if (!missing.isEmpty()) {
			player.sendSystemMessage(Component.literal("§c[A11] 佩戴未完成，缺少：" + String.join("、", missing)));
			return false;
		}

		// 武器可能被 /give 放进背包主格（slot >= 9），自动攻击固定用热键栏 0。
		if (weaponSlot != 0) {
			ItemStack spear = player.getInventory().getItem(weaponSlot);
			ItemStack displaced = player.getInventory().getItem(0);
			player.getInventory().setItem(0, spear);
			player.getInventory().setItem(weaponSlot, displaced);
		}
		player.getInventory().selected = 0;
		normalizeMidAttributes(player);
		player.teleportTo(player.serverLevel(), AUTO_X, AUTO_Y, AUTO_PLAYER_Z, 0.0F, 0.0F);
		// 冲刺状态禁用横扫，避免一次 Player.attack 命中木桩两次。
		player.setSprinting(true);
		player.setShiftKeyDown(false);

		target.setNoAi(true);
		target.setPersistenceRequired();
		target.setPos(AUTO_X, AUTO_Y, AUTO_TARGET_Z);
		target.setDeltaMovement(Vec3.ZERO);
		target.hurtMarked = true;
		target.setHealth(target.getMaxHealth());
		if (!MID) {
			// 明确锁定 1 血恶咒流；避免进服后回满血让死神祝福乘区失效。
			player.setHealth(1.0F);
		}

		autoAttempts = 0;
		autoSuccess = 0;
		autoTotalDamage = 0.0D;
		debugHurtLogs = 0;
		resetDebugDamage();
		AutoAttackDriver.resetHolyLight();
		SummyThreat.LOGGER.info(
				"[A11] AUTO START route={} weapon={} mainhand={} attackDamage={} seStrength={} fixedDamageCompat={} attempts={} attackSpeed={} playerHealth={}/{} targetMaxHealth={} targetArmor={} targetToughness={} holyLightAvailable={}",
				routeName(), weaponId(), mainHandId(player), player.getAttributeValue(Attributes.ATTACK_DAMAGE),
				AutoAttackDriver.seStrengthMultiplier(player), COMPAT,
				AUTO_ATTACKS, player.getAttributeValue(Attributes.ATTACK_SPEED),
				player.getHealth(), player.getMaxHealth(), target.getMaxHealth(),
				target.getArmorValue(), target.getAttributeValue(Attributes.ARMOR_TOUGHNESS),
				AutoAttackDriver.holyLightAvailable());
		player.sendSystemMessage(Component.literal("§a[A11] 自动攻击开始：20,000 次，约 2 分钟；完成后会输出统计"));
		return true;
	}

	/** 每 tick 跑 20 次；每次满蓄力、清无敌帧、固定木桩、攻击后回满血。 */
	private static void runAutoBatch(ServerPlayer player, MinecraftServer server) {
		Mob target = findStatsDummy(player);
		if (target == null) {
			failAuto(player, "统计木桩丢失");
			return;
		}
		for (int index = 0; index < AUTO_BATCH && autoAttempts < AUTO_ATTACKS; index++) {
			normalizeMidAttributes(player);
			target.setHealth(target.getMaxHealth());
			target.invulnerableTime = 0;
			target.setPos(AUTO_X, AUTO_Y, AUTO_TARGET_Z);
			target.setDeltaMovement(Vec3.ZERO);
			target.hurtMarked = true;
			// 原版 attack 可能清掉 sprinting；每击重新置位，持续禁用横扫。
			player.setSprinting(true);
			if (!AutoAttackDriver.setAttackStrength(player)) {
				failAuto(player, "无法补满 attackStrengthTicker");
				return;
			}
			float before = target.getHealth();
			player.attack(target);
			// SR 每 tick 结算圣光；这里手动结算，确保总伤害与计数器在本次攻击后立刻更新。
			AutoAttackDriver.tickHolyLight(server);
			float after = target.getHealth();
			double damage = Math.max(0.0D, (double) (before - after));
			autoAttempts++;
			if (damage > 0.0D) {
				autoSuccess++;
				autoTotalDamage += damage;
			}
			AutoAttackDriver.clearHolyLightColumns();
		}
		if (autoAttempts % 2000 == 0 && autoAttempts < AUTO_ATTACKS) {
			player.sendSystemMessage(Component.literal("§b[A11] 自动攻击进度：" + autoAttempts + " / " + AUTO_ATTACKS));
		}
		if (autoAttempts >= AUTO_ATTACKS) {
			finishAuto(player);
		}
	}

	private static void finishAuto(ServerPlayer player) {
		autoDone = true;
		autoStarted = false;
		// 木桩生成期间全局 TL 保持关闭，避免 EntityJoinLevelEvent 在下一 tick 补缩放木桩。
		run(player, "summythreat tl 20");
		double attackSpeed = player.getAttributeValue(Attributes.ATTACK_SPEED);
		double seconds = autoSuccess > 0 ? autoSuccess / Math.max(attackSpeed, 1.0E-6D) : 0.0D;
		double avgDamage = autoSuccess > 0 ? autoTotalDamage / autoSuccess : 0.0D;
		double derivedDps = seconds > 0.0D ? autoTotalDamage / seconds : 0.0D;
		// 自动回满木桩会抹掉持续伤害；毕业恶魔补深渊领主 10 DPS，中期恶魔补复仇之魂 6 DPS。
		double hellfireDps = !DEMON ? 0.0D : (MID ? VENGEFUL_DPS : HELLFIRE_DPS_AT_MAX);
		double totalDps = derivedDps + hellfireDps;
		int holyLightProcs = AutoAttackDriver.holyLightCount();
		double holyLightRawRate = autoSuccess > 0 && holyLightProcs >= 0
				? (double) holyLightProcs / autoSuccess : -1.0D;
		SummyThreat.LOGGER.info(
				"[A11] AUTO DONE route={} attempts={} successful={} totalDamage={} avgDamage={} attackSpeed={} derivedDps={} hellfireDps={} totalDps={} holyLightProcs={} holyLightRawRate={}",
				routeName(), autoAttempts, autoSuccess, autoTotalDamage, avgDamage,
				attackSpeed, derivedDps, hellfireDps, totalDps,
				holyLightProcs, holyLightRawRate);
		SummyThreat.LOGGER.info(
				"[A11] END GEAR mainhand={} attackDamage={} attackSpeed={} sharpness={}",
				mainHandId(player), player.getAttributeValue(Attributes.ATTACK_DAMAGE), attackSpeed,
				EnchantmentHelper.getItemEnchantmentLevel(
						Enchantments.SHARPNESS, player.getMainHandItem()));
		SummyThreat.LOGGER.info(
				"[A11] DAMAGE SUMMARY events={} total={} min={} max={} bySource={}",
				debugDamageEvents, debugDamageTotal,
				debugDamageEvents > 0 ? debugDamageMin : 0.0D, debugDamageMax,
				DEBUG_DAMAGE_BY_SOURCE);
		player.sendSystemMessage(Component.literal(String.format(
				"§a[A11] 自动攻击完成：%d/%d 成功，总伤 %.0f，均伤 %.2f，攻速 %.3f，直接 DPS %.2f + 狱火 %.2f = %.2f，圣光 %d（原始 %.2f%%）",
				autoSuccess, autoAttempts, autoTotalDamage, avgDamage, attackSpeed, derivedDps,
				hellfireDps, totalDps,
				holyLightProcs, holyLightRawRate * 100.0D)));
	}

	private static void failAuto(ServerPlayer player, String reason) {
		autoDone = true;
		autoStarted = false;
		run(player, "summythreat tl 20");
		SummyThreat.LOGGER.error("[A11] AUTO ABORT: {}", reason);
		player.sendSystemMessage(Component.literal("§c[A11] 自动攻击中止：" + reason));
	}

	private static Mob findStatsDummy(ServerPlayer player) {
		List<Mob> found = player.serverLevel().getEntitiesOfClass(Mob.class,
				player.getBoundingBox().inflate(64.0D),
				mob -> mob.getTags().contains("a11_target_stats"));
		return found.isEmpty() ? null : found.get(0);
	}

	private static int findWeaponSlot(ServerPlayer player) {
		String weaponId = weaponId();
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}
			ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
			if (id != null && weaponId.equals(id.toString())) {
				return slot;
			}
		}
		return -1;
	}

	private static List<String> missingCurios(ServerPlayer player) {
		List<String> missing = new ArrayList<>();
		if (MID && !srPresent()) {
			return missing;
		}
		var curios = CuriosApi.getCuriosInventory(player);
		if (!curios.isPresent()) {
			missing.add("Curios");
			return missing;
		}
		var handler = curios.orElse(null);
		String[] required = MID
				? (DEMON ? MID_DEMON_CURIOS : MID_ANGEL_CURIOS)
				: (DEMON ? DEMON_CURIOS : ANGEL_CURIOS);
		for (String id : required) {
			Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("summy-reliquary", id));
			if (handler == null || item == null || handler.findFirstCurio(item).isEmpty()) {
				missing.add(id);
			}
		}
		return missing;
	}

	private static String routeName() {
		if (MID) {
			if (!srPresent()) {
				return "MID_SE";
			}
			return DEMON ? "MID_DEMON" : "MID_ANGEL";
		}
		return DEMON ? "DEMON" : "ANGEL";
	}

	private static String weaponId() {
		if (MID) {
			if (!srPresent()) {
				return "minecraft:diamond_sword";
			}
			return DEMON ? "summy-reliquary:sacrificial_dagger" : "summy-reliquary:holy_spear";
		}
		return DEMON ? "summy-reliquary:dark_arts" : "summy-reliquary:seraph_spear";
	}

	private static String weaponName() {
		if (MID) {
			if (!srPresent()) {
				return "钻石剑";
			}
			return DEMON ? "献祭匕首" : "圣光短矛";
		}
		return DEMON ? "暗仪刺刀" : "炽天使之枪";
	}

	private static String mainHandId(ServerPlayer player) {
		ResourceLocation id = ForgeRegistries.ITEMS.getKey(player.getMainHandItem().getItem());
		return id == null ? "minecraft:air" : id.toString();
	}

	private static void normalizeMidAttributes(ServerPlayer player) {
		if (!MID) {
			return;
		}
		ItemStack currentWeapon = player.getInventory().getItem(0);
		if (!currentWeapon.isEmpty() && currentWeapon.isDamageableItem()) {
			// 自动攻击直接调用 Player.attack 时耐久仍会消耗；所有中期基准均固定为无损测试。
			currentWeapon.setDamageValue(0);
			currentWeapon.getOrCreateTag().putBoolean("Unbreakable", true);
		}
		// 测试服会重复挂载装备/上限修饰符；每击重建完整规范面板，避免跨 tick 累计。
		clearAttributeModifiers(player, Attributes.ATTACK_DAMAGE);
		clearAttributeModifiers(player, Attributes.ATTACK_SPEED);
		double attackDamageValue;
		double attackSpeedValue;
		if (srPresent()) {
			if (DEMON) {
				attackDamageValue = 17.0D;
				attackSpeedValue = 2.8D;
			} else {
				attackDamageValue = 18.0D;
				attackSpeedValue = 1.54D;
			}
		} else {
			attackDamageValue = 4.0D;
			attackSpeedValue = 1.6D;
		}
		var attackDamage = player.getAttribute(Attributes.ATTACK_DAMAGE);
		if (attackDamage != null) {
			attackDamage.setBaseValue(attackDamageValue);
		}
		var attackSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
		if (attackSpeed != null) {
			attackSpeed.setBaseValue(attackSpeedValue);
		}
	}

	private static void clearAttributeModifiers(ServerPlayer player,
			net.minecraft.world.entity.ai.attributes.Attribute attribute) {
		var instance = player.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		for (var modifier : new ArrayList<>(instance.getModifiers())) {
			instance.removeModifier(modifier);
		}
	}

	private static boolean hasEnchant(ItemStack stack, String enchantmentId) {
		CompoundTag tag = stack.getTag();
		if (tag == null || !tag.contains("Enchantments", Tag.TAG_LIST)) {
			return false;
		}
		ListTag list = tag.getList("Enchantments", Tag.TAG_COMPOUND);
		for (int index = 0; index < list.size(); index++) {
			if (enchantmentId.equals(list.getCompound(index).getString("id"))) {
				return true;
			}
		}
		return false;
	}

	private static void resetAutoState() {
		autoRequested = false;
		autoStarted = false;
		autoDone = false;
		autoAttempts = 0;
		autoSuccess = 0;
		autoTotalDamage = 0.0D;
		debugHurtLogs = 0;
		resetDebugDamage();
	}

	private static void resetDebugDamage() {
		debugDamageEvents = 0;
		debugDamageTotal = 0.0D;
		debugDamageMin = Double.MAX_VALUE;
		debugDamageMax = 0.0D;
		DEBUG_DAMAGE_BY_SOURCE.clear();
	}

	private static void hint(ServerPlayer player) {
		String route = DEMON ? "恶魔" : "天使";
		player.sendSystemMessage(Component.literal("§a[A11] "
				+ (MID ? "中期" : "毕业") + route + "攻击测试环境就绪"));
		if (MID) {
			player.sendSystemMessage(Component.literal("§e已自动佩戴："
					+ (DEMON ? "撒旦圣经（魂印）+ 恶魔王冠（光环）+ 复仇之魂（加护）+ 咒印（灵台）+ 五芒星"
					: "美德（魂印）+ 光环 + 圣光 + 神圣斗篷（加护 2 格）+ 五芒星")));
			player.sendSystemMessage(Component.literal(
					"§e装备：钻石套保护 III、"
					+ (DEMON ? "献祭匕首" : "圣光短矛")
					+ "锋利 III、力量 II；零护甲木桩；无回响套、无恶咒、无斩杀"));
		} else if (DEMON) {
			player.sendSystemMessage(Component.literal("§e已自动佩戴：撒旦圣经 + 恶魔王冠 + 亚巴顿 + 咒印 + 深渊领主 + 玄秘魔眼 + 恶魔契约 + 五芒星 + 仪式法袍"));
			player.sendSystemMessage(Component.literal("§e武器/附魔：已发暗仪刺刀，带锋利Plus V / 魔剑 V / 迅捷 V / 出其不意 III（本轮起移除斩杀）"));
			player.sendSystemMessage(Component.literal("§e状态：七罪全激活 / 恶魔已签约 / 邪恶 1000 / 全部解锁；契约必须实际佩戴"));
		} else {
			player.sendSystemMessage(Component.literal("§e已自动佩戴：美德 + 光环 + 灵台三件套 + 神性 + 圣心 + 圣光 + 五芒星"));
			player.sendSystemMessage(Component.literal("§e武器/附魔：已发炽天使之枪，带锋利Plus V / 魔剑 V / 迅捷 V / 出其不意 III（本轮起移除斩杀）"));
		}
		if (!MID) {
			player.sendSystemMessage(Component.literal("§c注意：胸甲带恶咒（calamity），当前是 1 血死神祝福极限输出配置；中性满血样本请另换胸甲"));
		}
		if (AUTO) {
			player.sendSystemMessage(Component.literal("§b自动模式：只生成 z+15 的 1,000,000 血 NoAI 木桩"));
			player.sendSystemMessage(Component.literal("§b环境就绪后自动执行 20,000 次满蓄力攻击（约 2 分钟）；手动输入 1 仍可强制启动"));
			String extra = !DEMON ? "圣光计数会单独统计"
					: (MID ? "总 DPS = 直接 DPS + 复仇之魂 6" : "总 DPS = 直接 DPS + 深渊领主狱火 10");
			player.sendSystemMessage(Component.literal("§b完成后输出总伤、均伤、攻速、反推 DPS；" + extra));
		} else if (STATS_ONLY) {
			player.sendSystemMessage(Component.literal("§b统计模式：只生成 z+15 的 1,000,000 血 NoAI 木桩；请连续攻击约 5 分钟以累计 900+ 次命中"));
		} else {
			player.sendSystemMessage(Component.literal("§b目标：普通 z+3 / 区域首领 z+6 / 监守者 z+9 / 统计木桩 z+15（1,000,000 血）"));
			player.sendSystemMessage(Component.literal("§b每只打完后执行 /summythreat stats 记录 DPS；完成后手动关闭客户端"));
		}
	}

	/** 30/60 秒各打一份属性快照，便于把 TTK 与攻击伤害/攻速/生命口径对齐。 */
	private static void reportAttributes(ServerPlayer player) {
		String name = player.getName().getString();
		player.sendSystemMessage(Component.literal("§b[A11] 属性快照：攻击 / 攻速 / 最大生命 / 护甲 / 韧性 / 击退抗性"));
		var attackSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
		if (attackSpeed != null) {
			StringBuilder modifiers = new StringBuilder();
			for (var modifier : attackSpeed.getModifiers()) {
				modifiers.append(modifier.getName()).append('=')
						.append(modifier.getAmount()).append('[').append(modifier.getOperation()).append("] ");
			}
			SummyThreat.LOGGER.info("[A11] ATTACK_SPEED base={} value={} modifiers={}",
					attackSpeed.getBaseValue(), attackSpeed.getValue(), modifiers.toString().trim());
		}
		for (String attribute : new String[]{
				"minecraft:generic.attack_damage",
				"minecraft:generic.attack_speed",
				"minecraft:generic.max_health",
				"minecraft:generic.armor",
				"minecraft:generic.armor_toughness",
				"minecraft:generic.knockback_resistance"}) {
			run(player, "attribute " + name + " " + attribute + " get");
		}
	}

	private static void run(ServerPlayer player, String command) {
		player.getServer().getCommands().performPrefixedCommand(
				player.createCommandSourceStack().withPermission(3), command);
	}
}
