package com.summy.threat.devcheck;

import com.summy.threat.Config;
import com.summy.threat.compat.ReliquaryFixedDamageCompat;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.registries.ForgeRegistries;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.lang.reflect.Method;

/**
 * SR 固定伤害兼容层的开发期自检。
 *
 * <p>只通过 {@code -Pdevcheck} 编译进开发客户端。覆盖：
 * <ul>
 *     <li>7 项名单与“5 项补 SR + SE / 2 项只补 SE”的分流；</li>
 *     <li>SR / SE 反射桥在真实模组环境下的签名可用性；</li>
 *     <li>关闭开关、取消事件、零伤害、无玩家攻击者时金额不变；</li>
 *     <li>用 SR 自己的 {@code ReliquaryEvents.onLivingHurt} 做运行时对照，确认
 *     isDivine 的 5 项确实没被 SR 乘过、isExactDamage 的 2 项确实已经被 SR 乘过；</li>
 *     <li>SE 死神祝福在损失生命时对 7 项都补一次。</li>
 * </ul>
 */
public final class ReliquaryCompatDevCheck {
	private static final String[] DIVINE = {
			"summy-reliquary.holy_light_burst",
			"summy-reliquary.golden_razor",
			"summy-reliquary.revelation_light",
			"summy-reliquary.demon_flame",
			"summy-reliquary.abyss_aura"
	};
	private static final String[] EXACT_ONLY = {
			"summy-reliquary.godhead_aura",
			"summy-reliquary.sacrifice"
	};
	private static final String[] EXCLUDED = {
			"summy-reliquary.holy_light",
			"summy-reliquary.hellfire",
			"summy-reliquary.pact_shatter",
			"summy-reliquary.sacrifice_self"
	};

	private ReliquaryCompatDevCheck() {
	}

	public static void run(ServerPlayer player) {
		boolean previous = Config.ENABLE_RELIQUARY_FIXED_DAMAGE_BOOSTS.get();
		ServerLevel level = player.serverLevel();
		Zombie victim = EntityType.ZOMBIE.create(level);
		Zombie other = EntityType.ZOMBIE.create(level);
		ItemStack chestBefore = player.getItemBySlot(EquipmentSlot.CHEST).copy();
		if (victim == null || other == null) {
			ThreatDevCheck.checkDetailed("SR 兼容：创建测试僵尸", false, "EntityType.ZOMBIE.create 返回 null");
			return;
		}
		victim.moveTo(player.getX() + 3.0D, player.getY(), player.getZ());
		other.moveTo(player.getX() + 4.0D, player.getY(), player.getZ());
		victim.setNoAi(true);
		other.setNoAi(true);
		victim.setInvulnerable(true);
		other.setInvulnerable(true);
		level.addFreshEntity(victim);
		level.addFreshEntity(other);
		// 嫉妒的增伤条件：目标穿有任意护甲。
		victim.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
		player.setHealth(player.getMaxHealth());

		try {
			runChecks(player, victim, other);
		} catch (Throwable throwable) {
			ThreatDevCheck.checkDetailed("SR 兼容：自检未抛异常", false, throwable.toString());
			ThreatDevCheck.logDetailed("[DEVCHECK] SR 兼容自检异常：" + throwable);
			throwable.printStackTrace();
		} finally {
			Config.ENABLE_RELIQUARY_FIXED_DAMAGE_BOOSTS.set(previous);
			// 恢复玩家状态，避免影响后续自然刷怪 / TL 检查。
			ThreatDevCheck.runCommand(player, "summyreliquary sin all off");
			CuriosApi.getCuriosInventory(player).ifPresent(handler ->
					handler.setEquippedCurio("soul_seal", 0, ItemStack.EMPTY));
			player.setItemSlot(EquipmentSlot.CHEST, chestBefore);
			player.setHealth(player.getMaxHealth());
			victim.discard();
			other.discard();
		}
	}

	private static void runChecks(ServerPlayer player, Zombie victim, Zombie other) {
		Config.ENABLE_RELIQUARY_FIXED_DAMAGE_BOOSTS.set(true);
		ThreatDevCheck.runCommand(player, "summyreliquary sin all off");
		// DevCheckWorld 会跨轮保留 Curios；先全部清空，保证“无状态”基线真的是 1.0。
		clearCurios(player);

		// 1. 名单与分流：5 项 isDivine 补 SR + SE，2 项 isExactDamage 只补 SE。
		for (String id : DIVINE) {
			ThreatDevCheck.checkDetailed("SR 兼容：%s 属于 5 项 isDivine".formatted(id),
					ReliquaryFixedDamageCompat.isTrackedDamage(id)
							&& ReliquaryFixedDamageCompat.needsSrAllDamage(id), "needsSr="
							+ ReliquaryFixedDamageCompat.needsSrAllDamage(id));
		}
		for (String id : EXACT_ONLY) {
			ThreatDevCheck.checkDetailed("SR 兼容：%s 只补 SE".formatted(id),
					ReliquaryFixedDamageCompat.isTrackedDamage(id)
							&& !ReliquaryFixedDamageCompat.needsSrAllDamage(id), "needsSr="
							+ ReliquaryFixedDamageCompat.needsSrAllDamage(id));
		}
		for (String id : EXCLUDED) {
			ThreatDevCheck.checkDetailed("SR 兼容：%s 不处理".formatted(id),
					!ReliquaryFixedDamageCompat.isTrackedDamage(id),
					"tracked=" + ReliquaryFixedDamageCompat.isTrackedDamage(id));
		}

		// 2. 反射桥就绪（只在对应模组实际安装时断言）。
		if (net.minecraftforge.fml.ModList.get().isLoaded("summy_reliquary")) {
			ThreatDevCheck.checkDetailed("SR 兼容：SR 反射桥就绪",
					ReliquaryFixedDamageCompat.srBridgeReady(), "srBridgeReady=false");
		} else {
			ThreatDevCheck.logDetailed("[DEVCHECK] 未安装 SR，跳过 SR 反射桥断言");
		}
		if (net.minecraftforge.fml.ModList.get().isLoaded("summy_enchantment")) {
			ThreatDevCheck.checkDetailed("SR 兼容：SE 死神祝福反射桥就绪",
					ReliquaryFixedDamageCompat.seBridgeReady(), "seBridgeReady=false");
		} else {
			ThreatDevCheck.logDetailed("[DEVCHECK] 未安装 SE，跳过死神祝福反射桥断言");
		}

		// 3. 没有任何七罪/饰品时，7 项都应是 1.0。
		for (String id : trackable()) {
			double factor = ReliquaryFixedDamageCompat.boostFactor(id, player, victim);
			ThreatDevCheck.checkDetailed("SR 兼容：无状态时 %s 倍率为 1".formatted(id),
					Math.abs(factor - 1.0D) < 1.0E-6D, "factor=" + factor);
		}

		// 4. 事件门控：关闭开关 / 取消 / 零伤害 / 非玩家攻击者都不改金额。
		checkGate(player, victim, other);

		// 4.5 最终乘区逐件：无七罪状态下单独佩戴，倍率应精确等于 1 + 配置百分比。
		checkFinalCurio(player, victim, "blessing", "sacred_heart",
				"sacredHeartDamagePercent", true);
		checkFinalCurio(player, victim, "revelation", "godhead",
				"godheadDamagePercent", true);
		checkFinalCurio(player, victim, "revelation", "star_of_bethlehem",
				"starDamagePercent", true);
		checkFinalCurio(player, victim, "revelation", "final_revelation",
				"starDamagePercent", true);
		// 恶魔契约栏是动态 0~1 格，未签约时跳过；签约后只断言“有正向增伤”。
		checkFinalCurio(player, victim, "demon_pact", "the_pact",
				"pactDamagePercent", false);

		// 4.6 SpiritAltarSet 条件层：思想/咒印、玄秘魔眼、恶魔王冠。
		// 七罪全部关闭，避免暴怒/嫉妒/谦逊/最终乘区干扰条件层的精确断言。
		checkSpiritLayers(player, victim);
		checkSraApoptosis(player, victim);

		// 5. 运行对照：撒旦圣经 + 七罪全赎，确认 SR 实际只跳过 isDivine 的 5 项。
		clearCurios(player);
		equipBible(player);
		boolean hasDeathsBlessing = addDeathsBlessing(player);
		ThreatDevCheck.runCommand(player, "summyreliquary sin all redeem");
		player.setHealth(player.getMaxHealth());
		for (String id : DIVINE) {
			// SR 的嫉妒/色欲路径可能随机脱掉目标护甲；每次采样前补回，保证增伤条件稳定。
			ensureEnvyArmor(victim);
			double factor = ReliquaryFixedDamageCompat.boostFactor(id, player, victim);
			ThreatDevCheck.checkDetailed("SR 兼容：%s 补 SR 暴怒/嫉妒（0.75~3.0）".formatted(id),
					factor >= 0.74D && factor <= 3.01D && Math.abs(factor - 1.0D) > 1.0E-4D,
					"factor=" + factor);
			ensureEnvyArmor(victim);
			float afterSr = srAmount(id, player, victim, 100.0F);
			ThreatDevCheck.checkDetailed("SR 运行时对照：%s 原样保留金额".formatted(id),
					Math.abs(afterSr - 100.0F) < 1.0E-4F, "amount=" + afterSr);
		}
		for (String id : EXACT_ONLY) {
			ensureEnvyArmor(victim);
			double factor = ReliquaryFixedDamageCompat.boostFactor(id, player, victim);
			ThreatDevCheck.checkDetailed("SR 兼容：%s 不重复补 SR（满血时 1.0）".formatted(id),
					Math.abs(factor - 1.0D) < 1.0E-6D, "factor=" + factor);
			ensureEnvyArmor(victim);
			float afterSr = srAmount(id, player, victim, 100.0F);
			ThreatDevCheck.checkDetailed("SR 运行时对照：%s 已被 SR 乘过".formatted(id),
					Math.abs(afterSr - 100.0F) > 1.0E-3F, "amount=" + afterSr);
		}

		// 6. SE 死神祝福：关闭七罪/清空饰品，确保只剩 SE 乘区；
		// 否则暴怒随机会让 full/half 两次采样不可直接比较。
		ThreatDevCheck.runCommand(player, "summyreliquary sin all off");
		clearCurios(player);
		player.setHealth(player.getMaxHealth() * 0.5F);
		if (hasDeathsBlessing) {
			double expected = seDeathsBlessingNumber(player);
			for (String id : trackable()) {
				ensureEnvyArmor(victim);
				double factor = ReliquaryFixedDamageCompat.boostFactor(id, player, victim);
				ThreatDevCheck.checkDetailed("SR 兼容：死神祝福精确作用于 %s".formatted(id),
						Math.abs(factor - expected) < 1.0E-4D,
						"factor=" + factor + " expected=" + expected);
			}
		} else {
			ThreatDevCheck.logDetailed("[DEVCHECK] 未安装/未找到 SE 死神祝福，跳过半血乘区断言");
		}
	}

	private static void checkSpiritLayers(ServerPlayer player, Zombie victim) {
		ThreatDevCheck.runCommand(player, "summyreliquary sin all off");
		clearCurios(player);
		victim.setGlowingTag(false);
		player.setHealth(player.getMaxHealth());
		// 打开恶魔线栏位（魂印 / 灵台），保证 the_mark、撒旦圣经与王冠可装入。
		if (DemonSetupBridge.available() && !DemonSetupBridge.applyDemonState(player)) {
			ThreatDevCheck.logDetailed("[DEVCHECK] 恶魔线栏位准备失败，思想/魔眼/王冠测试可能跳过");
		}

		// ① 思想 / 咒印：佩戴咒印（灵台栏）并让目标发光 → ×1.10。
		boolean mark = equipCurio(player, "spirit_altar", 0, "the_mark");
		ThreatDevCheck.checkDetailed("SR 兼容：咒印装入灵台栏", mark, "the_mark not equipped");
		double mindPercent = srConfigNumber("mindBonusPercent");
		double mindExpected = 1.0D + Math.max(0.0D, mindPercent) / 100.0D;
		victim.setGlowingTag(true);
		for (String id : trackable()) {
			double factor = ReliquaryFixedDamageCompat.boostFactor(id, player, victim);
			ThreatDevCheck.checkDetailed("SR 兼容：思想/咒印发光增伤 %s".formatted(id),
					Math.abs(factor - mindExpected) < 1.0E-4D,
					"factor=" + factor + " expected=" + mindExpected);
		}
		victim.setGlowingTag(false);
		for (String id : trackable()) {
			double factor = ReliquaryFixedDamageCompat.boostFactor(id, player, victim);
			ThreatDevCheck.checkDetailed("SR 兼容：目标不发光时思想不生效 %s".formatted(id),
					Math.abs(factor - 1.0D) < 1.0E-6D, "factor=" + factor);
		}
		clearCurios(player);

		// ② 玄秘魔眼：正对目标 → ×1.3；转开 → 1.0。
		boolean eye = equipCurio(player, "blessing", 0, "occult_eye");
		ThreatDevCheck.checkDetailed("SR 兼容：玄秘魔眼装入加护栏", eye, "occult_eye not equipped");
		double fearExpected = srConfigNumber("fearDamageMultiplier");
		float oldYaw = player.getYRot();
		float oldPitch = player.getXRot();
		// 目标在 +X 方向；Minecraft yaw -90 = 朝 +X。
		player.setYRot(-90.0F);
		player.setXRot(0.0F);
		for (String id : trackable()) {
			double factor = ReliquaryFixedDamageCompat.boostFactor(id, player, victim);
			ThreatDevCheck.checkDetailed("SR 兼容：魔眼注视增伤 %s".formatted(id),
					Math.abs(factor - fearExpected) < 1.0E-4D,
					"factor=" + factor + " expected=" + fearExpected);
		}
		player.setYRot(180.0F);
		for (String id : trackable()) {
			double factor = ReliquaryFixedDamageCompat.boostFactor(id, player, victim);
			ThreatDevCheck.checkDetailed("SR 兼容：转开视线后魔眼不生效 %s".formatted(id),
					Math.abs(factor - 1.0D) < 1.0E-6D, "factor=" + factor);
		}
		player.setYRot(oldYaw);
		player.setXRot(oldPitch);
		clearCurios(player);

		// ③ 恶魔王冠：王冠 + 撒旦圣经 + hell_locked + 50% 生命 → 上限档 +15%。
		boolean crown = equipCurio(player, "halo", 0, "devil_crown");
		boolean bible = equipCurio(player, "soul_seal", 0, "satanic_bible");
		ThreatDevCheck.checkDetailed("SR 兼容：恶魔王冠 + 撒旦圣经已佩戴", crown && bible,
				"crown=" + crown + " bible=" + bible);
		applySrAttributes(player);
		setHellLocked(player, true);
		player.setHealth(player.getMaxHealth() * 0.5F);
		double perDecile = srConfigNumber("devilCrownDamagePerDecilePercent");
		double cap = srConfigNumber("devilCrownDamageCapPercent");
		double crownPercent = Math.max(0.0D, Math.min(cap, perDecile * 5.0D));
		double crownExpected = 1.0D + crownPercent / 100.0D;
		for (String id : trackable()) {
			double factor = ReliquaryFixedDamageCompat.boostFactor(id, player, victim);
			ThreatDevCheck.checkDetailed("SR 兼容：恶魔王冠条件增伤 %s".formatted(id),
					Math.abs(factor - crownExpected) < 1.0E-4D,
					"factor=" + factor + " expected=" + crownExpected);
		}
		setHellLocked(player, false);
		for (String id : trackable()) {
			double factor = ReliquaryFixedDamageCompat.boostFactor(id, player, victim);
			ThreatDevCheck.checkDetailed("SR 兼容：未 hell_locked 时王冠不生效 %s".formatted(id),
					Math.abs(factor - 1.0D) < 1.0E-6D, "factor=" + factor);
		}
		setHellLocked(player, false);
		player.setHealth(player.getMaxHealth());
		victim.setGlowingTag(false);
		clearCurios(player);
	}

	/**
	 * SRA 凋亡：受伤 +10% 应作用于 SR 固定伤害，但固定伤害不能被当成近战直击去推条/斩杀。
	 */
	private static void checkSraApoptosis(ServerPlayer player, Zombie victim) {
		if (!net.minecraftforge.fml.ModList.get().isLoaded("summy_reliquary_apocrypha")) {
			ThreatDevCheck.logDetailed("[DEVCHECK] 未安装 SRA，跳过凋亡兼容断言");
			return;
		}
		clearCurios(player);
		boolean fallen = equipCurio(player, "summy-reliquary-apocrypha",
				"perdition_synecdoche", 0, "fallen_heaven");
		ThreatDevCheck.checkDetailed("SRA 兼容：堕天装入凋亡栏", fallen, "fallen_heaven not equipped");
		if (!fallen) {
			return;
		}

		CompoundTag persistent = victim.getPersistentData();
		if (!persistent.contains("summy_reliquary_apocrypha", 10)) {
			persistent.put("summy_reliquary_apocrypha", new CompoundTag());
		}
		CompoundTag data = persistent.getCompound("summy_reliquary_apocrypha");
		long now = victim.level().getGameTime();
		data.putLong("apoptosis_injured_until", now + 200L);
		data.putDouble("apoptosis_damage_taken_bonus", 10.0D);
		data.putDouble("apoptosis_extra_percent", 0.0D);
		data.putDouble("apoptosis_value", 0.0D);
		data.putLong("apoptosis_resist_until", 0L);
		persistent.put("summy_reliquary_apocrypha", data);

		LivingHurtEvent fixed = event("summy-reliquary.holy_light_burst", player, victim, 100.0F);
		postHurt(fixed);
		ThreatDevCheck.checkDetailed("SRA 凋亡：损伤状态受伤 +10% 作用于固定伤害",
				Math.abs(fixed.getAmount() - 110.0F) < 1.0E-3F, "amount=" + fixed.getAmount());
		ThreatDevCheck.checkDetailed("SRA 凋亡：固定伤害不推凋亡条",
				Math.abs(apoptosisValue(victim)) < 1.0E-6D,
				"value=" + apoptosisValue(victim));

		// 对照组：普通玩家近战仍然推条。
		data.putLong("apoptosis_injured_until", 0L);
		data.putLong("apoptosis_resist_until", 0L);
		data.putDouble("apoptosis_damage_taken_bonus", 0.0D);
		data.putDouble("apoptosis_value", 0.0D);
		persistent.put("summy_reliquary_apocrypha", data);
		LivingHurtEvent melee = event("minecraft.player_attack", player, victim, 10.0F);
		postHurt(melee);
		ThreatDevCheck.checkDetailed("SRA 凋亡：普通近战仍正常推条",
				apoptosisValue(victim) > 0.0D,
				"value=" + apoptosisValue(victim));

		data.putLong("apoptosis_injured_until", 0L);
		data.putLong("apoptosis_resist_until", 0L);
		data.putDouble("apoptosis_damage_taken_bonus", 0.0D);
		data.putDouble("apoptosis_value", 0.0D);
		persistent.put("summy_reliquary_apocrypha", data);
		clearCurios(player);
	}

	private static double apoptosisValue(LivingEntity victim) {
		return victim.getPersistentData().getCompound("summy_reliquary_apocrypha")
				.getDouble("apoptosis_value");
	}

	private static void postHurt(LivingHurtEvent event) {
		boolean previous = Config.ENABLE_RELIQUARY_FIXED_DAMAGE_BOOSTS.get();
		Config.ENABLE_RELIQUARY_FIXED_DAMAGE_BOOSTS.set(false);
		try {
			MinecraftForge.EVENT_BUS.post(event);
		} finally {
			Config.ENABLE_RELIQUARY_FIXED_DAMAGE_BOOSTS.set(previous);
		}
	}

	private static void checkGate(ServerPlayer player, Zombie victim, Zombie other) {
		String id = "summy-reliquary.holy_light_burst";

		Config.ENABLE_RELIQUARY_FIXED_DAMAGE_BOOSTS.set(false);
		LivingHurtEvent off = event(id, player, victim, 100.0F);
		ReliquaryFixedDamageCompat.onLivingHurt(off);
		ThreatDevCheck.checkDetailed("SR 兼容：开关关闭不改金额", off.getAmount() == 100.0F,
				"amount=" + off.getAmount());

		Config.ENABLE_RELIQUARY_FIXED_DAMAGE_BOOSTS.set(true);
		LivingHurtEvent canceled = event(id, player, victim, 100.0F);
		canceled.setCanceled(true);
		ReliquaryFixedDamageCompat.onLivingHurt(canceled);
		ThreatDevCheck.checkDetailed("SR 兼容：已取消事件不改金额", canceled.getAmount() == 100.0F,
				"amount=" + canceled.getAmount());

		LivingHurtEvent zero = event(id, player, victim, 0.0F);
		ReliquaryFixedDamageCompat.onLivingHurt(zero);
		ThreatDevCheck.checkDetailed("SR 兼容：零伤害不改金额", zero.getAmount() == 0.0F,
				"amount=" + zero.getAmount());

		LivingHurtEvent mob = event(id, other, victim, 100.0F);
		ReliquaryFixedDamageCompat.onLivingHurt(mob);
		ThreatDevCheck.checkDetailed("SR 兼容：非玩家攻击者不改金额", mob.getAmount() == 100.0F,
				"amount=" + mob.getAmount());
	}

	private static String[] trackable() {
		String[] all = new String[DIVINE.length + EXACT_ONLY.length];
		System.arraycopy(DIVINE, 0, all, 0, DIVINE.length);
		System.arraycopy(EXACT_ONLY, 0, all, DIVINE.length, EXACT_ONLY.length);
		return all;
	}

	private static void equipBible(ServerPlayer player) {
		Item bible = ForgeRegistries.ITEMS.getValue(new ResourceLocation("summy-reliquary", "satanic_bible"));
		if (bible == null) {
			ThreatDevCheck.checkDetailed("SR 兼容：找到撒旦圣经", false, "物品未注册");
			return;
		}
		CuriosApi.getCuriosInventory(player).ifPresent(handler ->
				handler.setEquippedCurio("soul_seal", 0, new ItemStack(bible)));
		boolean equipped = CuriosApi.getCuriosInventory(player)
				.map(handler -> handler.findFirstCurio(bible).isPresent())
				.orElse(false);
		ThreatDevCheck.checkDetailed("SR 兼容：撒旦圣经装入魂印栏", equipped, "findFirstCurio=" + equipped);
	}

	private static boolean equipCurio(ServerPlayer player, String slot, int index, String itemId) {
		return equipCurio(player, "summy-reliquary", slot, index, itemId);
	}

	private static boolean equipCurio(ServerPlayer player, String namespace,
			String slot, int index, String itemId) {
		Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(namespace, itemId));
		if (item == null) {
			ThreatDevCheck.logDetailed("[DEVCHECK] 未找到饰品 " + itemId);
			return false;
		}
		CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
			try {
				handler.setEquippedCurio(slot, index, new ItemStack(item));
			} catch (Throwable throwable) {
				ThreatDevCheck.logDetailed("[DEVCHECK] " + itemId + " 装入 " + slot
						+ "#" + index + " 失败：" + throwable);
			}
		});
		return CuriosApi.getCuriosInventory(player)
				.map(handler -> handler.findFirstCurio(item).isPresent())
				.orElse(false);
	}

	private static void applySrAttributes(ServerPlayer player) {
		try {
			Class<?> manager = Class.forName("com.summy.reliquary.effect.AttributeManager");
			manager.getMethod("apply", LivingEntity.class).invoke(null, player);
		} catch (Throwable throwable) {
			ThreatDevCheck.logDetailed("[DEVCHECK] SR AttributeManager.apply 调用失败：" + throwable);
		}
	}

	private static void setHellLocked(ServerPlayer player, boolean value) {
		try {
			Class<?> flags = Class.forName("com.summy.reliquary.effect.PlayerFlags");
			flags.getMethod("setHellLocked", ServerPlayer.class, boolean.class)
					.invoke(null, player, value);
		} catch (Throwable throwable) {
			ThreatDevCheck.logDetailed("[DEVCHECK] SR setHellLocked 调用失败：" + throwable);
		}
	}

	private static void clearCurios(ServerPlayer player) {
		CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
			for (ICurioStacksHandler stacks : handler.getCurios().values()) {
				clearStacks(stacks.getStacks());
				clearStacks(stacks.getCosmeticStacks());
			}
		});
	}

	private static void ensureEnvyArmor(LivingEntity victim) {
		if (victim.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
			victim.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
		}
	}

	/**
	 * 单独佩戴一件最终乘区饰品，验证 {@code 1 + 配置百分比}。
	 *
	 * @param exact 是否要求精确等于配置值；恶魔契约受邪恶度影响，只做正向断言
	 */
	private static void checkFinalCurio(ServerPlayer player, LivingEntity victim,
			String slot, String itemId, String percentMethod, boolean exact) {
		Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("summy-reliquary", itemId));
		if (item == null) {
			ThreatDevCheck.logDetailed("[DEVCHECK] 未找到饰品 " + itemId + "，跳过");
			return;
		}
		clearCurios(player);
		CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
			try {
				handler.setEquippedCurio(slot, 0, new ItemStack(item));
			} catch (Throwable throwable) {
				ThreatDevCheck.logDetailed("[DEVCHECK] " + itemId + " 装入 " + slot
						+ " 失败：" + throwable);
			}
		});
		boolean equipped = CuriosApi.getCuriosInventory(player)
				.map(handler -> handler.findFirstCurio(item).isPresent())
				.orElse(false);
		if (!equipped) {
			ThreatDevCheck.logDetailed("[DEVCHECK] " + itemId + " 无法装入 " + slot + "，跳过");
			return;
		}
		ensureEnvyArmor(victim);
		double factor = ReliquaryFixedDamageCompat.boostFactor(
				"summy-reliquary.holy_light_burst", player, victim);
		if (exact) {
			double percent = srConfigNumber(percentMethod);
			double expected = 1.0D + percent / 100.0D;
			ThreatDevCheck.checkDetailed("SR 兼容：最终乘区 %s = %.2f".formatted(itemId, expected),
					Double.isFinite(percent) && Math.abs(factor - expected) < 1.0E-4D,
					"factor=" + factor + " expected=" + expected + " percent=" + percent);
		} else {
			ThreatDevCheck.checkDetailed("SR 兼容：最终乘区 %s 有正向增伤".formatted(itemId),
					factor > 1.0D, "factor=" + factor);
		}
	}

	private static double srConfigNumber(String method) {
		try {
			Class<?> config = Class.forName("com.summy.reliquary.config.ReliquaryConfig");
			Object value = config.getMethod(method).invoke(null);
			return value instanceof Number number ? number.doubleValue() : Double.NaN;
		} catch (Throwable throwable) {
			ThreatDevCheck.logDetailed("[DEVCHECK] 读取 SR 配置 " + method + " 失败：" + throwable);
			return Double.NaN;
		}
	}

	private static double seDeathsBlessingNumber(ServerPlayer player) {
		try {
			Class<?> effects = Class.forName("com.summy.enchantment.combat.EnchantmentEffects");
			Object value = effects.getMethod("deathsBlessingOutgoing", LivingEntity.class)
					.invoke(null, player);
			return value instanceof Number number ? number.doubleValue() : 1.0D;
		} catch (Throwable throwable) {
			ThreatDevCheck.logDetailed("[DEVCHECK] 读取 SE 死神祝福倍率失败：" + throwable);
			return 1.0D;
		}
	}

	private static void clearStacks(IDynamicStackHandler stacks) {
		for (int slot = 0; slot < stacks.getSlots(); slot++) {
			stacks.setStackInSlot(slot, ItemStack.EMPTY);
		}
	}

	/** 临时给胸甲补一级死神祝福，验证 SE 乘区；返回是否成功。 */
	private static boolean addDeathsBlessing(ServerPlayer player) {
		Enchantment blessing = ForgeRegistries.ENCHANTMENTS.getValue(
				new ResourceLocation("summy_enchantment", "deaths_blessing"));
		if (blessing == null) {
			return false;
		}
		ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST).copy();
		if (chest.isEmpty()) {
			return false;
		}
		chest.enchant(blessing, 1);
		player.setItemSlot(EquipmentSlot.CHEST, chest);
		return true;
	}

	private static LivingHurtEvent event(String id, Entity attacker, LivingEntity victim, float amount) {
		DamageType type = new DamageType(id, 0.1F);
		DamageSource source = new DamageSource(new Holder.Direct<>(type), attacker);
		return new LivingHurtEvent(victim, source, amount);
	}

	/** 直接调用 SR 的常规伤害入口，做“SR 是否已经乘过”的运行时对照。 */
	private static float srAmount(String id, ServerPlayer player, LivingEntity victim, float amount) {
		LivingHurtEvent event = event(id, player, victim, amount);
		try {
			Class<?> events = Class.forName("com.summy.reliquary.ReliquaryEvents");
			Method hurt = events.getMethod("onLivingHurt", LivingHurtEvent.class);
			hurt.invoke(null, event);
			return event.getAmount();
		} catch (Throwable throwable) {
			ThreatDevCheck.logDetailed("[DEVCHECK] SR 运行对照调用失败：" + throwable);
			return amount;
		}
	}
}
