package com.summy.threat.eximus;

import com.summy.threat.Config;
import com.summy.threat.scaling.MonsterScaler;
import com.summy.threat.scaling.ScalingState;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.warden.Warden;
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
			return;
		}
		if (!isNatural(event.getSpawnType())) {
			return;
		}
		if (mob.getPersistentData().getBoolean(MonsterScaler.TAG_EXIMUS)) {
			return;
		}
		double chance = Config.EXIMUS_NATURAL_SPAWN_CHANCE.get();
		if (chance > 0.0D && mob.getRandom().nextDouble() < chance) {
			mob.getPersistentData().putBoolean(MonsterScaler.TAG_EXIMUS, true);
		}
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
