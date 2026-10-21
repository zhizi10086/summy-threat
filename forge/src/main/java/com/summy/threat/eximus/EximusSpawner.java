package com.summy.threat.eximus;

import com.summy.threat.Config;
import com.summy.threat.scaling.MonsterScaler;
import com.summy.threat.scaling.ScalingState;
import com.summy.threat.scaling.SpawnAudit;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.util.RandomSource;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 5% 自然卓越者刷新：只处理自然类生成，在 {@code EntityJoinLevelEvent} 缩放前写入标签，
 * 这样 E(TL)、护甲/韧性/攻击/移速与卓越护盾都会按卓越者分支生效。
 */
public final class EximusSpawner {
	private EximusSpawner() {
	}

	@SubscribeEvent
	public static void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
		if (!Config.SPEC.isLoaded() || !ScalingState.enabled()) {
			return;
		}
		Mob mob = event.getEntity();
		if (!MonsterScaler.isScalable(mob)) {
			return;
		}
		// Boss 类不参与自然卓越者；远古守卫者按普通怪处理
		if (mob instanceof Warden || mob instanceof EnderDragon || mob instanceof WitherBoss) {
			SpawnAudit.recordFinalize(mob, event.getSpawnType(), false, false, 0.0D, "boss_excluded");
			return;
		}
		if (!isNatural(event.getSpawnType())) {
			SpawnAudit.recordFinalize(mob, event.getSpawnType(), false, false, 0.0D, "spawn_type_excluded");
			return;
		}
		if (mob.getPersistentData().getBoolean(MonsterScaler.TAG_EXIMUS)) {
			SpawnAudit.recordFinalize(mob, event.getSpawnType(), true, true, 0.0D, "already_tagged");
			return;
		}
		double chance = Config.EXIMUS_NATURAL_SPAWN_CHANCE.get();
		boolean tagged = false;
		if (roll(mob.getRandom(), chance)) {
			mob.getPersistentData().putBoolean(MonsterScaler.TAG_EXIMUS, true);
			tagged = true;
		}
		SpawnAudit.recordFinalize(mob, event.getSpawnType(), true, tagged, chance, "natural");
	}

	/** 纯掷骰函数：自检用大样本验证 5% 概率，正式链路也复用它。 */
	public static boolean roll(RandomSource random, double chance) {
		return chance > 0.0D && random.nextDouble() < chance;
	}

	/**
	 * 自然类生成：NATURAL / CHUNK_GENERATION / STRUCTURE / PATROL / JOCKEY / REINFORCEMENT。
	 * 刷怪笼、命令、召唤、刷怪蛋、发射器、繁殖、桶装、转化、事件/触发均不参与。
	 */
	public static boolean isNatural(MobSpawnType type) {
		return type == MobSpawnType.NATURAL
				|| type == MobSpawnType.CHUNK_GENERATION
				|| type == MobSpawnType.STRUCTURE
				|| type == MobSpawnType.PATROL
				|| type == MobSpawnType.JOCKEY
				|| type == MobSpawnType.REINFORCEMENT;
	}
}
