package com.summy.threat.pcf;

import com.google.common.collect.Multimap;
import com.summy.threat.Config;
import com.summy.threat.SummyThreat;
import com.summy.threat.scaling.MonsterScaler;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 玩家挑战因子（PCF）：玩家个人进度的平滑评分。
 *
 * <p>四项分数：装备、Boss、探索、资源；每 5 秒重算一次并使用指数平滑。
 * 所有数据写入玩家 ForgeData 的 {@code summy_threat:pcf} 根下，死亡重生时复制。
 */
public final class PcfManager {
	public static final String ROOT = "summy_threat:pcf";
	private static final String VALUE = "value";
	private static final String INITIALIZED = "initialized";
	private static final String BIOMES = "biomes";
	private static final String BOSSES = "bosses";
	private static final String RESOURCES = "resources";
	private static final String DIAMONDS = "diamonds";
	private static final String DEBRIS = "debris";
	private static final String MATERIALS = "materials";
	private static final String GENESIS_SEEN = "genesis_seen";
	private static final String RESET_LOCK_UNTIL = "reset_lock_until";

	private static final String SR_ROOT = "summy_reliquary";
	private static final String SR_GENESIS_USED = "genesis_used";
	private static final String ENDER_DRAGON = "minecraft:ender_dragon";
	private static final String WITHER = "minecraft:wither";
	private static final String WARDEN = "minecraft:warden";
	private static final String ELDER_GUARDIAN = "minecraft:elder_guardian";

	private PcfManager() {
	}

	public static double value(ServerPlayer player) {
		ensureData(player);
		CompoundTag root = root(player);
		return clamp(root.getDouble(VALUE), 0.0D, 20.0D);
	}

	public static void setValue(ServerPlayer player, double value) {
		CompoundTag root = root(player);
		root.putDouble(VALUE, clamp(value, 0.0D, 20.0D));
		root.putBoolean(INITIALIZED, true);
		saveRoot(player, root);
	}

	/** 当前选中 PCF：普通怪加权、卓越者中值、首领/Boss 取最高。 */
	public static double selectedPcf(ServerLevel level, Vec3 pos, MonsterScaler.Kind kind) {
		int radius = intCfg(Config.MULTIPLAYER_PLAYER_DETECTION_RADIUS, 128);
		double r2 = (double) radius * radius;
		List<ServerPlayer> players = level.getPlayers(player -> player.distanceToSqr(pos) <= r2);
		if (players.isEmpty()) {
			return 0.0D;
		}
		double min = 20.0D;
		double max = 0.0D;
		for (ServerPlayer player : players) {
			double pcf = value(player);
			min = Math.min(min, pcf);
			max = Math.max(max, pcf);
		}
		return switch (kind) {
			case NORMAL -> min * doubleCfg(Config.MULTIPLAYER_WEIGHTED_MIN_WEIGHT, 0.7D)
					+ max * (1.0D - doubleCfg(Config.MULTIPLAYER_WEIGHTED_MIN_WEIGHT, 0.7D));
			case EXIMUS -> min + (max - min) * doubleCfg(Config.MULTIPLAYER_ELITE_HIGH_WEIGHT, 0.5D);
			case LEADER, BOSS -> max;
		};
	}

	public static Snapshot snapshot(ServerPlayer player) {
		ensureData(player);
		CompoundTag root = root(player);
		Components components = components(player, root);
		boolean locked = player.level().getGameTime() < root.getLong(RESET_LOCK_UNTIL);
		return new Snapshot(
				clamp(root.getDouble(VALUE), 0.0D, 20.0D),
				weighted(components),
				components.equipment,
				components.boss,
				components.exploration,
				components.resource,
				locked);
	}

	@SubscribeEvent
	public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			ensureData(player);
			refresh(player);
		}
	}

	@SubscribeEvent
	public static void onPlayerClone(PlayerEvent.Clone event) {
		if (event.getEntity() instanceof ServerPlayer player && event.getOriginal() instanceof ServerPlayer original) {
			CompoundTag data = original.getPersistentData();
			if (data.contains(ROOT, Tag.TAG_COMPOUND)) {
				player.getPersistentData().put(ROOT, data.getCompound(ROOT).copy());
			}
		}
	}

	@SubscribeEvent
	public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
		if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
			return;
		}
		if (player.tickCount % 100 != 0) {
			return;
		}
		ensureData(player);
		CompoundTag root = root(player);
		updateBiome(player, root);
		refresh(player);
	}

	@SubscribeEvent
	public static void onItemPickup(PlayerEvent.ItemPickupEvent event) {
		if (!(event.getEntity() instanceof ServerPlayer player)) {
			return;
		}
		ensureData(player);
		ItemStack stack = event.getStack();
		if (stack.isEmpty()) {
			return;
		}
		ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
		if (itemId == null) {
			return;
		}
		String id = itemId.toString();
		CompoundTag root = root(player);
		CompoundTag resources = resources(root);
		if ("minecraft:diamond".equals(id)) {
			resources.putInt(DIAMONDS, resources.getInt(DIAMONDS) + stack.getCount());
		} else if ("minecraft:ancient_debris".equals(id)) {
			resources.putInt(DEBRIS, resources.getInt(DEBRIS) + stack.getCount());
		} else if (configuredMaterials().contains(id)) {
			ListTag materials = resources.getList(MATERIALS, Tag.TAG_STRING);
			boolean found = false;
			for (int i = 0; i < materials.size(); i++) {
				if (id.equals(materials.getString(i))) {
					found = true;
					break;
				}
			}
			if (!found) {
				materials.add(StringTag.valueOf(id));
				resources.put(MATERIALS, materials);
			}
		}
		root.put(RESOURCES, resources);
		saveRoot(player, root);
	}

	@SubscribeEvent
	public static void onBossDeath(LivingDeathEvent event) {
		ServerPlayer killer = playerOf(event.getSource());
		if (killer == null) {
			return;
		}
		ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType());
		if (typeId == null || !isBossId(typeId.toString())) {
			return;
		}
		ensureData(killer);
		CompoundTag root = root(killer);
		CompoundTag bosses = root.getCompound(BOSSES);
		String id = typeId.toString();
		if (!bosses.getBoolean(id)) {
			bosses.putBoolean(id, true);
			root.put(BOSSES, bosses);
			saveRoot(killer, root);
		}
	}

	@SubscribeEvent
	public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			ensureData(player);
			CompoundTag root = root(player);
			markDimension(root, event.getTo());
			saveRoot(player, root);
		}
	}

	private static void refresh(ServerPlayer player) {
		CompoundTag root = root(player);
		long now = player.level().getGameTime();
		boolean genesisUsed = genesisUsed(player);
		if (genesisUsed && !root.getBoolean(GENESIS_SEEN)) {
			root.putBoolean(GENESIS_SEEN, true);
			root.putDouble(VALUE, 0.0D);
			long days = Math.max(0, intCfg(Config.PCF_RESET_LOCK_DAYS, 3));
			root.putLong(RESET_LOCK_UNTIL, now + days * 24_000L);
			SummyThreat.LOGGER.info("[Summy Threat] 检测到创世纪使用：{} 的 PCF 已归零并进入重置锁", player.getGameProfile().getName());
		}

		Components components = components(player, root);
		double calc = weighted(components);
		double smoothing = clamp(doubleCfg(Config.PCF_SMOOTHING, 0.3D), 0.0D, 1.0D);
		double next = root.getBoolean(INITIALIZED)
				? root.getDouble(VALUE) + (calc - root.getDouble(VALUE)) * smoothing
				: calc;
		if (now < root.getLong(RESET_LOCK_UNTIL)) {
			next = Math.min(next, doubleCfg(Config.PCF_RESET_LOCK_CAP, 3.0D));
		}
		root.putDouble(VALUE, clamp(next, 0.0D, 20.0D));
		root.putBoolean(INITIALIZED, true);
		saveRoot(player, root);
	}

	private static void ensureData(ServerPlayer player) {
		CompoundTag root = root(player);
		if (root.getBoolean(INITIALIZED)) {
			return;
		}
		root.putDouble(VALUE, 0.0D);
		root.putBoolean(INITIALIZED, false);
		root.put(BIOMES, new ListTag());
		root.put(BOSSES, new CompoundTag());
		root.put(RESOURCES, new CompoundTag());
		root.putBoolean(GENESIS_SEEN, genesisUsed(player));
		root.putLong(RESET_LOCK_UNTIL, 0L);
		saveRoot(player, root);
	}

	private static void updateBiome(ServerPlayer player, CompoundTag root) {
		ResourceKey<net.minecraft.world.level.biome.Biome> key = player.level()
				.getBiome(player.blockPosition()).unwrapKey().orElse(null);
		if (key != null) {
			ListTag biomes = root.getList(BIOMES, Tag.TAG_STRING);
			String id = key.location().toString();
			boolean found = false;
			for (int i = 0; i < biomes.size(); i++) {
				if (id.equals(biomes.getString(i))) {
					found = true;
					break;
				}
			}
			if (!found) {
				biomes.add(StringTag.valueOf(id));
				root.put(BIOMES, biomes);
			}
		}
		markDimension(root, player.level().dimension());
		saveRoot(player, root);
	}

	private static void markDimension(CompoundTag root, ResourceKey<Level> dimension) {
		if (dimension == Level.NETHER) {
			root.putBoolean("nether", true);
		} else if (dimension == Level.END) {
			root.putBoolean("end", true);
		}
	}

	private static Components components(ServerPlayer player, CompoundTag root) {
		double armor = Math.min(player.getArmorValue() * doubleCfg(Config.PCF_ARMOR_PER_POINT, 0.64D),
				doubleCfg(Config.PCF_ARMOR_CAP, 12.0D));
		double attack = Math.min(mainHandAttack(player) * doubleCfg(Config.PCF_ATTACK_PER_POINT, 0.8D),
				doubleCfg(Config.PCF_ATTACK_CAP, 8.0D));
		double enchant = Math.min(enchantLevels(player) * doubleCfg(Config.PCF_ENCHANT_PER_LEVEL, 0.4D),
				doubleCfg(Config.PCF_ENCHANT_CAP, 4.0D));
		double equipment = armor + attack + enchant;
		double boss = bossScore(root);
		double exploration = explorationScore(root);
		double resource = resourceScore(root);
		return new Components(equipment, boss, exploration, resource);
	}

	private static double weighted(Components components) {
		return clamp(
				components.equipment * doubleCfg(Config.PCF_EQUIPMENT_WEIGHT, 0.30D)
						+ components.boss * doubleCfg(Config.PCF_BOSS_WEIGHT, 0.25D)
						+ components.exploration * doubleCfg(Config.PCF_EXPLORATION_WEIGHT, 0.20D)
						+ components.resource * doubleCfg(Config.PCF_RESOURCE_WEIGHT, 0.25D),
				0.0D, 20.0D);
	}

	private static double bossScore(CompoundTag root) {
		CompoundTag bosses = root.getCompound(BOSSES);
		double score = 0.0D;
		if (bosses.getBoolean(ENDER_DRAGON)) {
			score += doubleCfg(Config.PCF_BOSS_SCORE_ENDER_DRAGON, 7.0D);
		}
		if (bosses.getBoolean(WITHER)) {
			score += doubleCfg(Config.PCF_BOSS_SCORE_WITHER, 7.0D);
		}
		if (bosses.getBoolean(WARDEN)) {
			score += doubleCfg(Config.PCF_BOSS_SCORE_WARDEN, 4.0D);
		}
		if (bosses.getBoolean(ELDER_GUARDIAN)) {
			score += doubleCfg(Config.PCF_BOSS_SCORE_ELDER_GUARDIAN, 2.0D);
		}
		for (String id : configuredBosses()) {
			if (bosses.getBoolean(id)) {
				score += doubleCfg(Config.PCF_BOSS_SCORE_MOD, 4.0D);
			}
		}
		return Math.min(score, doubleCfg(Config.PCF_BOSS_CAP, 20.0D));
	}

	private static double explorationScore(CompoundTag root) {
		double score = (root.getBoolean("nether") ? doubleCfg(Config.PCF_NETHER_ENTRY_SCORE, 4.0D) : 0.0D)
				+ (root.getBoolean("end") ? doubleCfg(Config.PCF_END_ENTRY_SCORE, 4.0D) : 0.0D);
		int biomes = root.getList(BIOMES, Tag.TAG_STRING).size();
		double per = doubleCfg(Config.PCF_BIOME_PER_NEW, 0.5D);
		score += diminishing(biomes, per, per * 0.5D, per * 0.2D,
				intCfg(Config.PCF_BIOME_DIMINISH_1, 10), intCfg(Config.PCF_BIOME_DIMINISH_2, 20),
				doubleCfg(Config.PCF_BIOME_CAP, 8.0D));
		return Math.min(score, 16.0D);
	}

	private static double resourceScore(CompoundTag root) {
		CompoundTag resources = root.getCompound(RESOURCES);
		double diamondPer = doubleCfg(Config.PCF_DIAMOND_PER_UNIT, 0.3D);
		double diamonds = diminishing(resources.getInt(DIAMONDS), diamondPer, diamondPer * 0.5D, diamondPer / 6.0D,
				intCfg(Config.PCF_DIAMOND_DIMINISH_1, 10), intCfg(Config.PCF_DIAMOND_DIMINISH_2, 30),
				doubleCfg(Config.PCF_DIAMOND_CAP, 8.0D));
		double debris = Math.min(resources.getInt(DEBRIS) * doubleCfg(Config.PCF_DEBRIS_PER_UNIT, 1.0D),
				doubleCfg(Config.PCF_DEBRIS_CAP, 8.0D));
		double materials = Math.min(resources.getList(MATERIALS, Tag.TAG_STRING).size()
				* doubleCfg(Config.PCF_MOD_MATERIAL_PER_UNIT, 0.5D),
				doubleCfg(Config.PCF_MOD_MATERIAL_CAP, 4.0D));
		return Math.min(diamonds + debris + materials, 20.0D);
	}

	private static double diminishing(int count, double first, double second, double third, int t1, int t2, double cap) {
		int n = Math.max(0, count);
		double score = Math.min(n, t1) * first;
		score += Math.min(Math.max(0, n - t1), Math.max(0, t2 - t1)) * second;
		score += Math.max(0, n - t2) * third;
		return Math.min(score, cap);
	}

	private static double mainHandAttack(ServerPlayer player) {
		Multimap<Attribute, AttributeModifier> modifiers = player.getMainHandItem()
				.getAttributeModifiers(EquipmentSlot.MAINHAND);
		double addition = 0.0D;
		double baseMultiplier = 0.0D;
		double totalMultiplier = 0.0D;
		for (AttributeModifier modifier : modifiers.get(Attributes.ATTACK_DAMAGE)) {
			switch (modifier.getOperation()) {
				case ADDITION -> addition += modifier.getAmount();
				case MULTIPLY_BASE -> baseMultiplier += modifier.getAmount();
				case MULTIPLY_TOTAL -> totalMultiplier += modifier.getAmount();
			}
		}
		return Math.max(0.0D, (1.0D + addition) * (1.0D + baseMultiplier) * (1.0D + totalMultiplier));
	}

	private static int enchantLevels(ServerPlayer player) {
		int levels = 0;
		for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST,
				EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) {
			ItemStack stack = player.getItemBySlot(slot);
			if (!stack.isEmpty()) {
				for (int level : EnchantmentHelper.getEnchantments(stack).values()) {
					levels += level;
				}
			}
		}
		return levels;
	}

	private static boolean isBossId(String id) {
		return ENDER_DRAGON.equals(id) || WITHER.equals(id) || WARDEN.equals(id) || ELDER_GUARDIAN.equals(id)
				|| configuredBosses().contains(id);
	}

	private static Set<String> configuredBosses() {
		if (!Config.SPEC.isLoaded()) {
			return Set.of();
		}
		Set<String> out = new HashSet<>();
		for (Object value : Config.PCF_MOD_BOSS_IDS.get()) {
			if (value instanceof String id && !id.isBlank()) {
				out.add(id.trim().toLowerCase(Locale.ROOT));
			}
		}
		return out;
	}

	private static Set<String> configuredMaterials() {
		if (!Config.SPEC.isLoaded()) {
			return Set.of();
		}
		Set<String> out = new HashSet<>();
		for (Object value : Config.PCF_MOD_MATERIAL_IDS.get()) {
			if (value instanceof String id && !id.isBlank()) {
				out.add(id.trim().toLowerCase(Locale.ROOT));
			}
		}
		return out;
	}

	private static boolean genesisUsed(ServerPlayer player) {
		CompoundTag data = player.getPersistentData();
		return data.contains(SR_ROOT, Tag.TAG_COMPOUND)
				&& data.getCompound(SR_ROOT).getBoolean(SR_GENESIS_USED);
	}

	private static ServerPlayer playerOf(DamageSource source) {
		if (source.getEntity() instanceof ServerPlayer player) {
			return player;
		}
		if (source.getDirectEntity() instanceof Projectile projectile
				&& projectile.getOwner() instanceof ServerPlayer player) {
			return player;
		}
		return null;
	}

	private static CompoundTag resources(CompoundTag root) {
		return root.contains(RESOURCES, Tag.TAG_COMPOUND) ? root.getCompound(RESOURCES) : new CompoundTag();
	}

	private static CompoundTag root(ServerPlayer player) {
		CompoundTag data = player.getPersistentData();
		return data.contains(ROOT, Tag.TAG_COMPOUND) ? data.getCompound(ROOT) : new CompoundTag();
	}

	private static void saveRoot(ServerPlayer player, CompoundTag root) {
		player.getPersistentData().put(ROOT, root);
	}

	private static double doubleCfg(ForgeConfigSpec.DoubleValue value, double fallback) {
		return Config.SPEC.isLoaded() ? value.get() : fallback;
	}

	private static int intCfg(ForgeConfigSpec.IntValue value, int fallback) {
		return Config.SPEC.isLoaded() ? value.get() : fallback;
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}

	private record Components(double equipment, double boss, double exploration, double resource) {
	}

	public record Snapshot(double value, double calc, double equipment, double boss,
			double exploration, double resource, boolean locked) {
	}
}
