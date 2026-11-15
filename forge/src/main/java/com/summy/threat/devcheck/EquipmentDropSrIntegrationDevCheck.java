package com.summy.threat.devcheck;

import com.summy.threat.compat.sr.SRBundleCompat;
import com.summy.threat.item.EquipmentDropHandler;
import com.summy.threat.item.ModItems;
import com.summy.threat.scaling.MonsterScaler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** B16：使用 SR 2.0.1 测试事务验证真实在线玩家上的跨模组掉落契约。 */
final class EquipmentDropSrIntegrationDevCheck {
	private static final String HUMBLEING_LOOT = "com.summy.reliquary.loot.HumbleingBundleLoot";

	private EquipmentDropSrIntegrationDevCheck() {
	}

	static List<String> run(ServerPlayer player) {
		List<String> failures = new ArrayList<>();
		if (!ModList.get().isLoaded("summy_reliquary")) {
			System.out.println("[EQUIPMENT] SKIP SR 联合：未安装 Summy Reliquary");
			return failures;
		}
		if (player.getServer() == null
				|| player.getServer().getPlayerList().getPlayer(player.getUUID()) != player) {
			System.out.println("[EQUIPMENT] SKIP SR 联合：当前不是在线真实玩家");
			return failures;
		}

		ServerLevel level = player.serverLevel();
		Set<UUID> existing = itemIds(level, player);
		List<LivingEntity> victims = new ArrayList<>();
		try {
			invokeVoid(HUMBLEING_LOOT, "clearStateForTest");
			Zombie bonusVictim = spawn(level, player, 2.0D);
			victims.add(bonusVictim);
			invokeVoid(HUMBLEING_LOOT, "markKillSuccessForTest",
					new Class<?>[]{ServerPlayer.class, LivingEntity.class}, player, bonusVictim);
			check(failures, "SR 联合：成功击杀事务可由 ST 查询",
					SRBundleCompat.specialDropBonusPlayer(bonusVictim) == player);
			EquipmentDropHandler.onLivingDrops(new LivingDropsEvent(bonusVictim,
					player.damageSources().playerAttack(player),
					new ArrayList<ItemEntity>(), 0, true));
			int essence = countNew(level, player, existing, ModItems.EXIMUS_ESSENCE.get());
			int prism = countNew(level, player, existing, ModItems.ABYSSAL_STAR_PRISM.get());
			check(failures, "SR 联合：Boss 精华基础 2 + 额外 2 = 4", essence == 4,
					"essence=" + essence);
			check(failures, "SR 联合：星渊棱晶保持 1 份", prism == 1,
					"prism=" + prism);
			clearNew(level, player, existing);

			invokeVoid(HUMBLEING_LOOT, "clearStateForTest");
			Zombie copyVictim = spawn(level, player, 4.0D);
			victims.add(copyVictim);
			invokeVoid(HUMBLEING_LOOT, "markKillSuccessForTest",
					new Class<?>[]{ServerPlayer.class, LivingEntity.class}, player, copyVictim);
			LivingDropsEvent drops = new LivingDropsEvent(copyVictim,
					player.damageSources().playerAttack(player),
					new ArrayList<ItemEntity>(), 0, true);
			invokeVoid(HUMBLEING_LOOT, "captureLivingDrops",
					new Class<?>[]{LivingDropsEvent.class}, drops);
			drops.getDrops().add(new ItemEntity(level, player.getX(), player.getY(), player.getZ(),
					new ItemStack(ModItems.ABYSSAL_STAR_PRISM.get())));
			drops.getDrops().add(new ItemEntity(level, player.getX(), player.getY(), player.getZ(),
					new ItemStack(Items.STONE, 2)));
			invokeVoid(HUMBLEING_LOOT, "copyEventAddedDrops",
					new Class<?>[]{LivingDropsEvent.class}, drops);
			long prismDrops = drops.getDrops().stream()
					.filter(entity -> entity.getItem().is(ModItems.ABYSSAL_STAR_PRISM.get()))
					.count();
			long stoneDrops = drops.getDrops().stream()
					.filter(entity -> entity.getItem().is(Items.STONE))
					.count();
			check(failures, "SR 联合：棱晶标签阻止外部复制",
					prismDrops == 1 && stoneDrops == 2,
					"prism=" + prismDrops + " stone=" + stoneDrops);
		} catch (ReflectiveOperationException exception) {
			failures.add("SR 联合反射失败：" + exception);
		} catch (Throwable throwable) {
			failures.add("SR 联合异常：" + throwable);
		} finally {
			try {
				invokeVoid(HUMBLEING_LOOT, "clearStateForTest");
			} catch (ReflectiveOperationException ignored) {
			}
			clearNew(level, player, existing);
			for (LivingEntity victim : victims) {
				victim.discard();
			}
		}
		return failures;
	}

	private static Zombie spawn(ServerLevel level, ServerPlayer player, double dx) {
		Zombie zombie = EntityType.ZOMBIE.create(level);
		if (zombie == null) {
			throw new IllegalStateException("无法创建 SR 联合测试目标");
		}
		zombie.moveTo(player.getX() + dx, player.getY(), player.getZ(), 0.0F, 0.0F);
		zombie.setNoAi(true);
		zombie.setPersistenceRequired();
		level.addFreshEntity(zombie);
		MonsterScaler.scaleNow(zombie, 10, MonsterScaler.Kind.BOSS, false);
		return zombie;
	}

	private static int countNew(ServerLevel level, ServerPlayer player, Set<UUID> existing, Item item) {
		int count = 0;
		for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class,
				player.getBoundingBox().inflate(32.0D))) {
			if (!existing.contains(entity.getUUID()) && entity.getItem().is(item)) {
				count += entity.getItem().getCount();
			}
		}
		return count;
	}

	private static Set<UUID> itemIds(ServerLevel level, ServerPlayer player) {
		Set<UUID> ids = new HashSet<>();
		for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class,
				player.getBoundingBox().inflate(32.0D))) {
			ids.add(entity.getUUID());
		}
		return ids;
	}

	private static void clearNew(ServerLevel level, ServerPlayer player, Set<UUID> existing) {
		for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class,
				player.getBoundingBox().inflate(32.0D))) {
			if (!existing.contains(entity.getUUID())) {
				entity.discard();
			}
		}
	}

	private static void check(List<String> failures, String label, boolean ok) {
		check(failures, label, ok, "");
	}

	private static void check(List<String> failures, String label, boolean ok, String detail) {
		if (!ok) {
			failures.add(label + (detail.isEmpty() ? "" : "（" + detail + "）"));
		}
		System.out.println("[EQUIPMENT] " + (ok ? "OK " : "FAIL ") + label
				+ (detail.isEmpty() ? "" : "：" + detail));
	}

	private static void invokeVoid(String className, String methodName, Class<?>[] types,
			Object... args) throws ReflectiveOperationException {
		Class<?> type = Class.forName(className);
		Method method = type.getMethod(methodName, types);
		try {
			method.invoke(null, args);
		} catch (InvocationTargetException exception) {
			throw new IllegalStateException(className + "#" + methodName + " 调用失败",
					exception.getCause());
		}
	}

	private static void invokeVoid(String className, String methodName)
			throws ReflectiveOperationException {
		invokeVoid(className, methodName, new Class<?>[0]);
	}

}
