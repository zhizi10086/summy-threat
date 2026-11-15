package com.summy.threat.item;

import com.summy.threat.Config;
import com.summy.threat.compat.sr.SRBundleCompat;
import com.summy.threat.scaling.MonsterScaler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.RandomSource;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 卓越精华与星渊棱晶的基础死亡掉落。
 *
 * <p>材料直接生成受保护 ItemEntity，不写入 LivingDropsEvent 的 drops 集合；
 * 这样 SR 的外部掉落复制路径不会把它们误当成普通事件新增掉落。
 */
public final class EquipmentDropHandler {
	public static final String TAG_PROCESSED = "summy_threat:equipment_drop_processed";
	private static final int MAX_OWNER_DEPTH = 8;

	private EquipmentDropHandler() {
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onLivingDrops(LivingDropsEvent event) {
		if (!Config.SPEC.isLoaded()
				|| !Config.EQUIPMENT_DROPS_ENABLE.get()
				|| !(event.getEntity().level() instanceof ServerLevel level)
				|| event.getEntity() instanceof Player) {
			return;
		}
		LivingEntity victim = event.getEntity();
		if (victim.getPersistentData().getBoolean(TAG_PROCESSED)) {
			return;
		}

		MonsterScaler.Kind kind = MonsterScaler.kindOf(victim);
		if (kind == MonsterScaler.Kind.NORMAL) {
			return;
		}

		Credit credit = credit(event.getSource());
		if (credit.spectator()
				|| (Config.EQUIPMENT_DROPS_REQUIRE_PLAYER_KILL.get() && credit.player() == null)) {
			return;
		}

		// 死亡实体可能在同一 tick 再次触发掉落事件；先锁定一次判定。
		victim.getPersistentData().putBoolean(TAG_PROCESSED, true);
		int tl = Math.max(0, Math.min(20,
				victim.getPersistentData().getInt(MonsterScaler.TAG_TL)));
		int threshold = Config.BOSS_PRISM_TL_THRESHOLD.get();
		RandomSource random = victim.getRandom();

		int essence = essenceCount(kind, tl, threshold, random);
		ServerPlayer bonusPlayer = SRBundleCompat.specialDropBonusPlayer(victim);
		if (bonusPlayer != null) {
			essence += essenceBonusCount(kind, tl, threshold, random);
		}
		int prism = prismCount(kind, tl, threshold, random);

		spawnProtected(level, victim, ModItems.EXIMUS_ESSENCE.get(), essence);
		spawnProtected(level, victim, ModItems.ABYSSAL_STAR_PRISM.get(), prism);
	}

	private static int essenceCount(MonsterScaler.Kind kind, int tl, int threshold,
			RandomSource random) {
		return switch (kind) {
			case EXIMUS -> chance(random, Config.EXIMUS_ESSENCE_CHANCE_PERCENT.get()) ? 1 : 0;
			case LEADER -> chance(random, Config.LEADER_ESSENCE_CHANCE_PERCENT.get()) ? 1 : 0;
			case BOSS -> tl >= threshold
					? Config.BOSS_ESSENCE_COUNT_HIGH_TL.get()
					: Config.BOSS_ESSENCE_COUNT_LOW_TL.get();
			case NORMAL -> 0;
		};
	}

	private static int essenceBonusCount(MonsterScaler.Kind kind, int tl, int threshold,
			RandomSource random) {
		return switch (kind) {
			case EXIMUS -> chance(random, Config.EXIMUS_ESSENCE_CHANCE_PERCENT.get()) ? 1 : 0;
			case LEADER -> chance(random, Config.LEADER_ESSENCE_CHANCE_PERCENT.get()) ? 1 : 0;
			case BOSS -> tl >= threshold
					? Config.BOSS_ESSENCE_COUNT_HIGH_TL.get()
					: Config.BOSS_ESSENCE_COUNT_LOW_TL.get();
			case NORMAL -> 0;
		};
	}

	private static int prismCount(MonsterScaler.Kind kind, int tl, int threshold,
			RandomSource random) {
		if (kind != MonsterScaler.Kind.BOSS) {
			return 0;
		}
		if (tl < threshold) {
			return chance(random, Config.BOSS_PRISM_CHANCE_LOW_TL_PERCENT.get()) ? 1 : 0;
		}
		return Config.BOSS_PRISM_COUNT_HIGH_TL.get();
	}

	private static boolean chance(RandomSource random, double percent) {
		if (percent <= 0.0D) {
			return false;
		}
		if (percent >= 100.0D) {
			return true;
		}
		return random.nextDouble() * 100.0D < percent;
	}

	private static void spawnProtected(ServerLevel level, LivingEntity victim, Item item, int count) {
		if (count <= 0) {
			return;
		}
		ItemEntity drop = new ItemEntity(level, victim.getX(), victim.getY() + 0.5D, victim.getZ(),
				new ItemStack(item, count));
		drop.setInvulnerable(true);
		level.addFreshEntity(drop);
	}

	private static Credit credit(DamageSource source) {
		Credit attacker = creditFrom(source.getEntity(), new HashSet<>(), 0);
		if (attacker.player() != null || attacker.spectator()) {
			return attacker;
		}
		return creditFrom(source.getDirectEntity(), new HashSet<>(), 0);
	}

	private static Credit creditFrom(@Nullable Entity entity, Set<UUID> visited, int depth) {
		if (entity == null || depth > MAX_OWNER_DEPTH || !visited.add(entity.getUUID())) {
			return Credit.NONE;
		}
		if (entity instanceof ServerPlayer player) {
			return player.isSpectator() ? Credit.SPECTATOR : new Credit(player, false);
		}
		if (entity instanceof Projectile projectile) {
			Credit owner = creditFrom(projectile.getOwner(), visited, depth + 1);
			if (owner.player() != null || owner.spectator()) {
				return owner;
			}
		}
		if (entity instanceof OwnableEntity ownable) {
			Credit owner = creditFrom(ownable.getOwner(), visited, depth + 1);
			if (owner.player() != null || owner.spectator()) {
				return owner;
			}
		}
		return Credit.NONE;
	}

	private record Credit(@Nullable ServerPlayer player, boolean spectator) {
		private static final Credit NONE = new Credit(null, false);
		private static final Credit SPECTATOR = new Credit(null, true);
	}
}
