package com.summy.threat.devcheck;

import com.summy.threat.SummyThreat;
import com.summy.threat.ability.AbilityEffects;
import com.summy.threat.ability.AbilityId;
import com.summy.threat.api.AbilityDefinition;
import com.summy.threat.api.ArchetypeDefinition;
import com.summy.threat.api.SummyThreatApi;
import com.summy.threat.api.ThreatConfigApi;
import com.summy.threat.api.event.ThreatAbilityChangedEvent;
import com.summy.threat.api.event.ThreatArchetypeChangedEvent;
import com.summy.threat.api.event.ThreatConfigChangedEvent;
import com.summy.threat.api.event.ThreatScalePostEvent;
import com.summy.threat.api.event.ThreatScalePreEvent;
import com.summy.threat.eximus.EximusArchetype;
import com.summy.threat.scaling.MonsterScaler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** KJS API 的 Java 侧无头自检；实际 JS 语法由 kubejsdev 脚本回归负责。 */
final class KubeJSCompatDevCheck {
	private KubeJSCompatDevCheck() {
	}

	static List<String> run(ServerPlayer player) {
		List<String> failures = new ArrayList<>();
		ServerLevel level = player.serverLevel();
		Zombie zombie = EntityType.ZOMBIE.create(level);
		if (zombie == null) {
			failures.add("KJS API 无法创建测试僵尸");
			return failures;
		}
		zombie.getPersistentData().putBoolean(MonsterScaler.TAG_SCALED, true);
		zombie.getPersistentData().putInt(MonsterScaler.TAG_TL, 20);
		zombie.setHealth(zombie.getMaxHealth());

		AbilityDefinition manual = new AbilityDefinition("sttest:manual", 8,
				"测试能力", 0x55AAFF, false, 1, false);
		AbilityDefinition rollable = new AbilityDefinition("sttest:rollable", 8,
				"随机测试", 0xAA55FF, true, 3, false);
		ArchetypeDefinition customArchetype = new ArchetypeDefinition("sttest:custom",
				"测试原型", 0x33CC99, false, 1);
		AbilityId.registerCustom(manual);
		AbilityId.registerCustom(rollable);
		EximusArchetype.registerCustom(customArchetype);

		ThreatDevCheck.checkDetailed("KJS API 自定义能力注册",
				AbilityId.byId("sttest:manual") != null
						&& AbilityId.byId("sttest:rollable").rollable(),
				"manual/rollable");
		boolean manualInRandom = false;
		for (int i = 0; i < 200; i++) {
			String rolled = AbilityEffects.roll(20, 0,
					net.minecraft.util.RandomSource.create(i), true);
			manualInRandom |= List.of(rolled.split(",")).contains("sttest:manual");
		}
		ThreatDevCheck.checkDetailed("默认 rollable=false 不进随机池", !manualInRandom,
				"manualInRandom=" + manualInRandom);
		ThreatDevCheck.checkDetailed("自定义原型注册",
				EximusArchetype.byId("sttest:custom") != null,
				EximusArchetype.displayNameOf("sttest:custom"));

		EventProbe probe = new EventProbe(zombie);
		MinecraftForge.EVENT_BUS.register(probe);
		try {
			int preBefore = probe.pre;
			int postBefore = probe.post;
			MonsterScaler.scaleNow(zombie, 20, MonsterScaler.Kind.NORMAL);
			ThreatDevCheck.checkDetailed("scale.pre 可修改 TL", probe.lastPreTl == 7,
					"preTl=" + probe.lastPreTl + " tl=" + SummyThreatApi.tl(zombie));
			ThreatDevCheck.checkDetailed("scale.pre/post 各触发一次",
					probe.pre == preBefore + 1 && probe.post == postBefore + 1,
					"pre=" + probe.pre + " post=" + probe.post);

			probe.abilityChanged = 0;
			probe.archetypeChanged = 0;
			AbilityEffects.setAbilities(zombie, 7, "swift,sttest:manual");
			ThreatDevCheck.checkDetailed("ability.changed 事件与自定义能力查询",
					probe.abilityChanged == 1
							&& SummyThreatApi.hasAbility(zombie, "sttest:manual"),
					"changed=" + probe.abilityChanged);
			SummyThreatApi.unregisterAbility("sttest:manual");
			ThreatDevCheck.checkDetailed("未注册自定义 ID 保留但暂停",
					SummyThreatApi.abilities(zombie).contains("sttest:manual")
							&& !SummyThreatApi.hasAbility(zombie, "sttest:manual"),
					SummyThreatApi.abilities(zombie).toString());
			AbilityId.registerCustom(manual);
			ThreatDevCheck.checkDetailed("同 ID 重新注册后恢复",
					SummyThreatApi.hasAbility(zombie, "sttest:manual"),
					SummyThreatApi.abilities(zombie).toString());

			SummyThreatApi.setArchetype(zombie, "sttest:custom");
			ThreatDevCheck.checkDetailed("archetype.changed 事件",
					probe.archetypeChanged == 1
							&& "sttest:custom".equals(SummyThreatApi.archetype(zombie)),
					"changed=" + probe.archetypeChanged);
			checkConfigApi();
		} finally {
			MinecraftForge.EVENT_BUS.unregister(probe);
		}

		Zombie canceled = EntityType.ZOMBIE.create(level);
		if (canceled != null) {
			CancelProbe cancelProbe = new CancelProbe(canceled);
			MinecraftForge.EVENT_BUS.register(cancelProbe);
			try {
				boolean scaled = MonsterScaler.scaleNow(canceled, 20, MonsterScaler.Kind.NORMAL);
				ThreatDevCheck.checkDetailed("scale.pre 取消阻止数值化",
						!scaled && !SummyThreatApi.isScaled(canceled),
						"scaled=" + scaled);
			} finally {
				MinecraftForge.EVENT_BUS.unregister(cancelProbe);
				canceled.discard();
			}
		}

		AbilityId.unregisterCustom("sttest:manual");
		AbilityId.unregisterCustom("sttest:rollable");
		EximusArchetype.unregisterCustom("sttest:custom");
		zombie.discard();
		SummyThreat.LOGGER.info("[KJS] Java API 自检结束：失败 {}", failures.size());
		return failures;
	}

	private static void checkConfigApi() {
		String booleanKey = "player_armor.enabled";
		String integerKey = "chunk_difficulty_factor.death_penalty_duration";
		String doubleKey = "player_challenge_factor.resource_weight";
		String stringKey = "multiplayer.normal_pcf_mode";
		String enumKey = "environment_tier.mode";
		String listKey = "player_challenge_factor.mod_boss_ids";
		String regenDoubleKey = "tl_abilities.regeneration_percent_per_second";
		String regenListKey = "tl_abilities.reduced_regeneration_entity_ids";
		String modBossAttackKey = "boss_health.default_mod_boss_attack";

		Map<String, Object> original = new LinkedHashMap<>();
		for (String key : List.of(booleanKey, integerKey, doubleKey, stringKey, enumKey, listKey,
				regenDoubleKey, regenListKey, modBossAttackKey)) {
			original.put(key, ThreatConfigApi.get(key));
		}
		ThreatDevCheck.checkDetailed("配置 API 分组枚举",
				ThreatConfigApi.groups().contains("player_challenge_factor")
						&& ThreatConfigApi.keys("boss_health")
						.contains("boss_health.auto_calculate_mod_boss"),
				"groups=" + ThreatConfigApi.groups().size());
		ThreatDevCheck.checkDetailed("配置 API 六种声明类型识别",
				"boolean".equals(ThreatConfigApi.type(booleanKey))
						&& "integer".equals(ThreatConfigApi.type(integerKey))
						&& "double".equals(ThreatConfigApi.type(doubleKey))
						&& "string".equals(ThreatConfigApi.type(stringKey))
						&& "enum".equals(ThreatConfigApi.type(enumKey))
						&& "list".equals(ThreatConfigApi.type(listKey)),
				List.of(ThreatConfigApi.type(booleanKey), ThreatConfigApi.type(integerKey),
						ThreatConfigApi.type(doubleKey), ThreatConfigApi.type(stringKey),
						ThreatConfigApi.type(enumKey), ThreatConfigApi.type(listKey)).toString());

		boolean changedBoolean = !(Boolean) original.get(booleanKey);
		int oldInteger = ((Number) original.get(integerKey)).intValue();
		int changedInteger = oldInteger == 60 ? 61 : 60;
		String oldEnum = String.valueOf(original.get(enumKey));
		String changedEnum = "none".equals(oldEnum) ? "free" : "none";
		Map<String, Object> changes = new LinkedHashMap<>();
		changes.put(booleanKey, changedBoolean);
		changes.put(integerKey, changedInteger);
		changes.put(doubleKey, 0.31D);
		changes.put(stringKey, "kjs_test");
		changes.put(enumKey, changedEnum);
		changes.put(listKey, List.of("summy_threat:api_test"));
		changes.put(regenDoubleKey, 0.75D);
		changes.put(regenListKey, List.of("MINECRAFT:ENDER_DRAGON", "minecraft:wither"));
		changes.put(modBossAttackKey, 2.5D);
		int oldEvents = EventProbe.apiConfigChanged;
		try {
			Map<String, Object> written = ThreatConfigApi.setMany(changes);
			boolean allWritten = Boolean.TRUE.equals(ThreatConfigApi.get(booleanKey))
					== changedBoolean
					&& ((Number) ThreatConfigApi.get(integerKey)).intValue() == changedInteger
					&& Math.abs(((Number) ThreatConfigApi.get(doubleKey)).doubleValue() - 0.31D) < 1.0E-6D
					&& "kjs_test".equals(ThreatConfigApi.get(stringKey))
					&& changedEnum.equals(ThreatConfigApi.get(enumKey))
					&& List.of("summy_threat:api_test").equals(ThreatConfigApi.get(listKey))
					&& Math.abs(((Number) ThreatConfigApi.get(regenDoubleKey)).doubleValue() - 0.75D) < 1.0E-6D
					&& List.of("MINECRAFT:ENDER_DRAGON", "minecraft:wither")
							.equals(ThreatConfigApi.get(regenListKey))
					&& Math.abs(((Number) ThreatConfigApi.get(modBossAttackKey)).doubleValue() - 2.5D) < 1.0E-6D;
			ThreatDevCheck.checkDetailed("配置 API 六种类型 setMany 可写",
					allWritten && written.size() == changes.size(), written.toString());
			ThreatDevCheck.checkDetailed("config.changed 单次触发",
					EventProbe.apiConfigChanged == oldEvents + 1
							&& EventProbe.lastApiKeys.equals(changes.keySet()),
					"events=" + EventProbe.apiConfigChanged + " keys=" + EventProbe.lastApiKeys);

			Map<String, Object> partial = new LinkedHashMap<>();
			partial.put(doubleKey, 0.32D);
			partial.put(doubleKey + "_invalid", 0.50D);
			int partialEvents = EventProbe.apiConfigChanged;
			boolean partialRejected = false;
			try {
				ThreatConfigApi.setMany(partial);
			} catch (IllegalArgumentException exception) {
				partialRejected = true;
			}
			ThreatDevCheck.checkDetailed("配置 setMany 部分失败整体回滚",
					partialRejected
							&& Math.abs(((Number) ThreatConfigApi.get(doubleKey)).doubleValue()
									- 0.31D) < 1.0E-6D
							&& EventProbe.apiConfigChanged == partialEvents,
					String.valueOf(ThreatConfigApi.get(doubleKey)));

			boolean integerRejected = false;
			try {
				ThreatConfigApi.set(integerKey, 1.5D);
			} catch (IllegalArgumentException exception) {
				integerRejected = true;
			}
			ThreatDevCheck.checkDetailed("配置整数键拒绝小数",
					integerRejected
							&& ((Number) ThreatConfigApi.get(integerKey)).intValue()
									== changedInteger,
					String.valueOf(ThreatConfigApi.get(integerKey)));

			boolean rangeRejected = false;
			try {
				ThreatConfigApi.set(doubleKey, 2.0D);
			} catch (IllegalArgumentException exception) {
				rangeRejected = true;
			}
			ThreatDevCheck.checkDetailed("配置越界拒绝且不落盘", rangeRejected
					&& Math.abs(((Number) ThreatConfigApi.get(doubleKey)).doubleValue()
							- 0.31D) < 1.0E-6D,
					String.valueOf(ThreatConfigApi.get(doubleKey)));

			Object resetValue = ThreatConfigApi.reset(doubleKey);
			ThreatDevCheck.checkDetailed("配置 reset 恢复声明默认值",
					Objects.equals(resetValue, ThreatConfigApi.defaultValue(doubleKey))
							&& Objects.equals(ThreatConfigApi.get(doubleKey), resetValue),
					String.valueOf(resetValue));

			RecursiveWriteProbe recursive = new RecursiveWriteProbe(doubleKey);
			MinecraftForge.EVENT_BUS.register(recursive);
			try {
				ThreatConfigApi.set(doubleKey, 0.30D);
			} finally {
				MinecraftForge.EVENT_BUS.unregister(recursive);
			}
			ThreatDevCheck.checkDetailed("config.changed 内递归写入被拒绝",
					recursive.attempted && recursive.rejected,
					"attempted=" + recursive.attempted + " rejected=" + recursive.rejected);
		} finally {
			ThreatConfigApi.setMany(original);
		}
	}

	private static final class RecursiveWriteProbe {
		private final String key;
		private boolean attempted;
		private boolean rejected;

		private RecursiveWriteProbe(String key) {
			this.key = key;
		}

		@SubscribeEvent
		public void onConfig(ThreatConfigChangedEvent event) {
			if (attempted) {
				return;
			}
			attempted = true;
			try {
				ThreatConfigApi.set(key, 0.29D);
			} catch (IllegalStateException exception) {
				rejected = true;
			}
		}
	}

	private static final class EventProbe {
		private static int configChanged;
		private static int apiConfigChanged;
		private static Set<String> lastApiKeys = Set.of();
		private final Zombie zombie;
		private int pre;
		private int post;
		private int abilityChanged;
		private int archetypeChanged;
		private int lastPreTl;

		private EventProbe(Zombie zombie) {
			this.zombie = zombie;
		}

		@SubscribeEvent
		public void onPre(ThreatScalePreEvent event) {
			if (event.getEntity() == zombie) {
				pre++;
				event.setTl(7);
				lastPreTl = event.getTl();
			}
		}

		@SubscribeEvent
		public void onPost(ThreatScalePostEvent event) {
			if (event.getEntity() == zombie) {
				post++;
			}
		}

		@SubscribeEvent
		public void onAbility(ThreatAbilityChangedEvent event) {
			if (event.getEntity() == zombie) {
				abilityChanged++;
			}
		}

		@SubscribeEvent
		public void onArchetype(ThreatArchetypeChangedEvent event) {
			if (event.getEntity() == zombie) {
				archetypeChanged++;
			}
		}

		@SubscribeEvent
		public void onConfig(ThreatConfigChangedEvent event) {
			configChanged++;
			if ("api".equals(event.getSource())) {
				apiConfigChanged++;
				lastApiKeys = Set.copyOf(event.getNewValues().keySet());
			}
		}
	}

	private static final class CancelProbe {
		private final Zombie zombie;

		private CancelProbe(Zombie zombie) {
			this.zombie = zombie;
		}

		@SubscribeEvent
		public void onPre(ThreatScalePreEvent event) {
			if (event.getEntity() == zombie) {
				event.setCanceled(true);
			}
		}
	}
}
