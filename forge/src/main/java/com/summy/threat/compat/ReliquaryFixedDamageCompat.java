package com.summy.threat.compat;

import com.summy.threat.Config;
import com.summy.threat.SummyThreat;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SR 固定伤害的全伤害增伤兼容层。
 *
 * <p>SR 将本模组的若干特效/真伤标成“不参与自身增伤”，但这些伤害在设定上仍属于
 * “全部伤害类型”增伤的对象。这里只在 ST 侧补一次，不修改 SR：
 * <ul>
 *     <li>5 项 isDivine 固定伤害：补 SR 七罪/七德/最终全伤害乘区和 SE 死神祝福；</li>
 *     <li>godhead_aura / sacrifice：SR 当前已吃到全伤害乘区，只补 SE 死神祝福，避免重复。</li>
 *     <li>全部 7 项固定伤害：补 SR {@code SpiritAltarSet} 的三条条件乘区——
 *     思想/咒印对发光目标 +10%、玄秘魔眼对注视目标 ×1.3、恶魔王冠在 hell_locked 下的
 *     已损失生命分档加伤。</li>
 * </ul>
 *
 * <p>本类不直接调用 {@code SinEffects.modifyOutgoingDamage}：那个入口还会触发暴怒自伤、
 * 色欲脱甲与贪婪惩罚。这里只读状态并复算正向全伤害增伤。SR/SE 未安装时完全跳过。
 *
 * <p>SE 复仇是剑/斧/三叉戟近战专属，不进入本兼容层；SRA 凋亡的“损伤状态受伤 +10%”
 * 是目标侧受伤乘区，由 SRA 自身的 {@code Apoptosis} 处理，本类不重复补。
 */
public final class ReliquaryFixedDamageCompat {
	/** 兼容层对单项伤害需要补的乘区。 */
	public enum BoostKind {
		/** 不在本轮 7 项名单内，完全不处理。 */
		NONE,
		/** SR 的 isDivine 已跳过全伤害乘区：ST 补 SR + SE。 */
		SR_AND_SE,
		/** SR 的 isExactDamage 已经乘过全伤害：ST 只补 SE，避免重复。 */
		SE_ONLY
	}

	private static final Set<String> FIXED_IDS = Set.of(
			"summy-reliquary.holy_light_burst",
			"summy-reliquary.golden_razor",
			"summy-reliquary.revelation_light",
			"summy-reliquary.demon_flame",
			"summy-reliquary.abyss_aura",
			"summy-reliquary.godhead_aura",
			"summy-reliquary.sacrifice"
	);

	/** 只属于 isExactDamage、未被 SR 的 isDivine 排除的伤害：SR 已经乘过全伤害。 */
	private static final Set<String> SR_ALREADY_APPLIED = Set.of(
			"summy-reliquary.godhead_aura",
			"summy-reliquary.sacrifice"
	);

	private static final AtomicBoolean SR_WARNED = new AtomicBoolean();
	private static final AtomicBoolean SR_SPIRIT_WARNED = new AtomicBoolean();
	private static final AtomicBoolean SE_WARNED = new AtomicBoolean();

	private static boolean srInitialized;
	private static boolean srReady;
	private static Method srAwakened;
	private static Method srTierRedeemed;
	private static Method srEnvyTarget;
	private static Method srVirtuesActive;
	private static Method srGodheadActive;
	private static Method srPactActive;
	private static Method srPactAttackBonus;
	private static Method srWears;
	private static Method srWrathMin;
	private static Method srWrathMax;
	private static Method srWrathMaxRedeemed;
	private static Method srEnvyBonus;
	private static Method srHumilityBonus;
	private static Method srSacredHeartDamage;
	private static Method srGodheadDamage;
	private static Method srStarDamage;
	private static Object srWrath;
	private static Object srEnvy;
	private static Item srSacredHeart;
	private static Item srStar;
	private static Item srFinalRevelation;

	/** SpiritAltarSet 条件乘区（思想 / 魔眼 / 王冠）的独立反射桥。 */
	private static boolean srSpiritInitialized;
	private static boolean srSpiritReady;
	private static Method srHasSetEffects;
	private static Method srMindBonus;
	private static Method srEnableOccultEye;
	private static Method srFearRadius;
	private static Method srFearDamageMultiplier;
	private static Method srGazeLookedAt;
	private static Method srDevilCrownBonus;
	private static Item srOccultEye;

	private static boolean seInitialized;
	private static boolean seReady;
	private static Method seDeathsBlessingOutgoing;

	private ReliquaryFixedDamageCompat() {
	}

	@SubscribeEvent(priority = EventPriority.LOW)
	public static void onLivingHurt(LivingHurtEvent event) {
		if (!Config.SPEC.isLoaded() || !Config.ENABLE_RELIQUARY_FIXED_DAMAGE_BOOSTS.get()) {
			return;
		}
		if (event.getEntity().level().isClientSide() || event.isCanceled() || event.getAmount() <= 0.0F) {
			return;
		}
		String id = event.getSource().getMsgId();
		if (boostKind(id) == BoostKind.NONE) {
			return;
		}
		DamageSource source = event.getSource();
		if (!(source.getEntity() instanceof ServerPlayer attacker) || attacker == event.getEntity()) {
			return;
		}

		double factor = boostFactor(id, attacker, event.getEntity());
		if (!Double.isFinite(factor) || factor <= 0.0D || factor == 1.0D) {
			return;
		}
		event.setAmount((float) (event.getAmount() * factor));
	}

	/** 自检 / 诊断：该伤害 ID 是否属于本轮 7 项。 */
	public static boolean isTrackedDamage(String id) {
		return boostKind(id) != BoostKind.NONE;
	}

	/** 自检 / 诊断：该伤害是否需要 ST 补 SR 全伤害乘区。 */
	public static boolean needsSrAllDamage(String id) {
		return boostKind(id) == BoostKind.SR_AND_SE;
	}

	/** 自检 / 诊断：SR 反射桥是否就绪。 */
	public static boolean srBridgeReady() {
		return initSr();
	}

	/** 自检 / 诊断：SpiritAltarSet 条件乘区反射桥是否就绪。 */
	public static boolean srSpiritBridgeReady() {
		return initSpirit();
	}

	/** 自检 / 诊断：SE 反射桥是否就绪。 */
	public static boolean seBridgeReady() {
		return initSe();
	}

	/**
	 * 计算单项固定伤害应补的总倍率（不改事件、不产生任何副作用）。
	 *
	 * <p>只做“读状态 + 相乘”，供事件处理与自检共用；同样不会触发暴怒自伤、
	 * 色欲脱甲或贪婪惩罚。
	 */
	public static double boostFactor(String id, ServerPlayer attacker, LivingEntity victim) {
		BoostKind kind = boostKind(id);
		if (kind == BoostKind.NONE || attacker == null || victim == null) {
			return 1.0D;
		}
		double factor = 1.0D;
		if (kind == BoostKind.SR_AND_SE) {
			factor *= srAllDamageFactor(attacker, victim);
		}
		// 思想/咒印、玄秘魔眼、恶魔王冠属于 SpiritAltarSet 的独立乘区；
		// SR 对 isDivine/isExactDamage 都会跳过，这里对 7 项统一补一次。
		factor *= srConditionalFactor(attacker, victim);
		factor *= seDeathsBlessing(attacker);
		return Double.isFinite(factor) && factor > 0.0D ? factor : 1.0D;
	}

	private static BoostKind boostKind(String id) {
		if (!FIXED_IDS.contains(id)) {
			return BoostKind.NONE;
		}
		return SR_ALREADY_APPLIED.contains(id) ? BoostKind.SE_ONLY : BoostKind.SR_AND_SE;
	}

	/** 按 SR 的“全伤害”乘区复算；不含近战专属、贪婪惩罚与暴怒自伤。 */
	private static double srAllDamageFactor(ServerPlayer attacker, LivingEntity victim) {
		if (!initSr() || !srReady) {
			return 1.0D;
		}
		double factor = 1.0D;
		if (invokeBoolean(SR_WARNED, srAwakened, attacker, srWrath)) {
			double min = invokeDouble(SR_WARNED, srWrathMin);
			double max = invokeBoolean(SR_WARNED, srTierRedeemed, attacker, srWrath)
					? invokeDouble(SR_WARNED, srWrathMaxRedeemed)
					: invokeDouble(SR_WARNED, srWrathMax);
			// 配置读取失败时 invokeDouble 返回 0；不要因此把整次伤害乘成 0。
			if (min > 0.0D && max >= min) {
				factor *= min + attacker.getRandom().nextDouble() * (max - min);
			}
		}
		if (invokeBoolean(SR_WARNED, srAwakened, attacker, srEnvy)
				&& invokeBoolean(SR_WARNED, srEnvyTarget, attacker, victim)) {
			factor *= 1.0D + Math.max(0.0D, invokeDouble(SR_WARNED, srEnvyBonus)) / 100.0D;
		}
		if (invokeBoolean(SR_WARNED, srVirtuesActive, attacker)) {
			float victimRatio = victim.getMaxHealth() <= 0.0F
					? 0.0F : victim.getHealth() / victim.getMaxHealth();
			float selfRatio = attacker.getMaxHealth() <= 0.0F
					? 0.0F : attacker.getHealth() / attacker.getMaxHealth();
			if (victim.getMaxHealth() > attacker.getMaxHealth() || victimRatio > selfRatio) {
				factor *= 1.0D + Math.max(0.0D, invokeDouble(SR_WARNED, srHumilityBonus)) / 100.0D;
			}
		}

		double finalPercent = 0.0D;
		if (wears(attacker, srSacredHeart)) {
			finalPercent += invokeDouble(SR_WARNED, srSacredHeartDamage);
		}
		if (invokeBoolean(SR_WARNED, srGodheadActive, attacker)) {
			finalPercent += invokeDouble(SR_WARNED, srGodheadDamage);
		}
		if (wears(attacker, srStar) || wears(attacker, srFinalRevelation)) {
			finalPercent += invokeDouble(SR_WARNED, srStarDamage);
		}
		if (invokeBoolean(SR_WARNED, srPactActive, attacker)) {
			finalPercent += invokeDouble(SR_WARNED, srPactAttackBonus, attacker);
		}
		return factor * (1.0D + Math.max(0.0D, finalPercent) / 100.0D);
	}

	/** 按 SR 的 SpiritAltarSet 三条条件乘区复算；条件不满足时按 1.0。 */
	private static double srConditionalFactor(ServerPlayer attacker, LivingEntity victim) {
		if (!initSpirit() || !srSpiritReady) {
			return 1.0D;
		}
		double factor = 1.0D;

		// 思想 / 咒印：套装或咒印 + 目标正在发光。
		if (invokeBoolean(SR_SPIRIT_WARNED, srHasSetEffects, attacker)
				&& victim.isCurrentlyGlowing()) {
			factor *= 1.0D + Math.max(0.0D, invokeDouble(SR_SPIRIT_WARNED, srMindBonus)) / 100.0D;
		}

		// 玄秘魔眼：佩戴魔眼 + 命中瞬间的视线射线正好落在受害者身上。
		if (invokeBoolean(SR_SPIRIT_WARNED, srEnableOccultEye)
				&& wears(attacker, srOccultEye)
				&& gazeHits(attacker, invokeDouble(SR_SPIRIT_WARNED, srFearRadius), victim)) {
			double multiplier = invokeDouble(SR_SPIRIT_WARNED, srFearDamageMultiplier);
			if (multiplier > 0.0D) {
				factor *= multiplier;
			}
		}

		// 恶魔王冠：DevilCrown 自身已检查王冠 + 撒旦圣经 + hell_locked + 已损失生命分档。
		double crownBonus = invokeDouble(SR_SPIRIT_WARNED, srDevilCrownBonus, attacker);
		if (crownBonus > 0.0D) {
			factor *= 1.0D + crownBonus / 100.0D;
		}
		return factor;
	}

	private static boolean gazeHits(ServerPlayer attacker, double radius, LivingEntity victim) {
		if (srGazeLookedAt == null || radius <= 0.0D) {
			return false;
		}
		try {
			return srGazeLookedAt.invoke(null, attacker, radius) == victim;
		} catch (Throwable throwable) {
			warnOnce(SR_SPIRIT_WARNED, "固定伤害兼容：调用 GazeLook.lookedAt 失败", throwable);
			return false;
		}
	}

	private static double seDeathsBlessing(ServerPlayer attacker) {
		if (!initSe() || !seReady) {
			return 1.0D;
		}
		double factor = invokeDouble(SE_WARNED, seDeathsBlessingOutgoing, attacker);
		return factor > 0.0D ? factor : 1.0D;
	}

	private static boolean wears(ServerPlayer player, Item item) {
		if (srWears == null || item == null) {
			return false;
		}
		return invokeBoolean(SR_WARNED, srWears, player, item);
	}

	private static boolean initSr() {
		if (srInitialized) {
			return srReady;
		}
		srInitialized = true;
		if (!ModList.get().isLoaded("summy_reliquary")) {
			return false;
		}
		try {
			Class<?> sinEffects = Class.forName("com.summy.reliquary.sin.SinEffects");
			Class<?> sin = Class.forName("com.summy.reliquary.sin.Sin");
			Class<?> virtues = Class.forName("com.summy.reliquary.effect.VirtuesEffects");
			Class<?> godhead = Class.forName("com.summy.reliquary.effect.Godhead");
			Class<?> pact = Class.forName("com.summy.reliquary.effect.DemonPact");
			Class<?> curio = Class.forName("com.summy.reliquary.util.CurioHelper");
			Class<?> config = Class.forName("com.summy.reliquary.config.ReliquaryConfig");
			srWrath = enumValue(sin, "WRATH");
			srEnvy = enumValue(sin, "ENVY");
			srAwakened = method(SR_WARNED, sinEffects, "awakened", LivingEntity.class, sin);
			srTierRedeemed = method(SR_WARNED, sinEffects, "tierRedeemed", LivingEntity.class, sin);
			srEnvyTarget = method(SR_WARNED, sinEffects, "envyTarget", LivingEntity.class, LivingEntity.class);
			srVirtuesActive = method(SR_WARNED, virtues, "active", LivingEntity.class);
			srGodheadActive = method(SR_WARNED, godhead, "active", LivingEntity.class);
			srPactActive = method(SR_WARNED, pact, "active", LivingEntity.class);
			srPactAttackBonus = method(SR_WARNED, pact, "attackBonusPercent", LivingEntity.class);
			srWears = method(SR_WARNED, curio, "wears", LivingEntity.class, Item.class);
			srWrathMin = method(SR_WARNED, config, "wrathRandomMin");
			srWrathMax = method(SR_WARNED, config, "wrathRandomMax");
			srWrathMaxRedeemed = method(SR_WARNED, config, "wrathRandomMaxRedeemed");
			srEnvyBonus = method(SR_WARNED, config, "envyBonusPercent");
			srHumilityBonus = method(SR_WARNED, config, "humilityDamageBonusPercent");
			srSacredHeartDamage = method(SR_WARNED, config, "sacredHeartDamagePercent");
			srGodheadDamage = method(SR_WARNED, config, "godheadDamagePercent");
			srStarDamage = method(SR_WARNED, config, "starDamagePercent");
			srSacredHeart = item(SR_WARNED, "sacred_heart");
			srStar = item(SR_WARNED, "star_of_bethlehem");
			srFinalRevelation = item(SR_WARNED, "final_revelation");
			srReady = srWrath != null && srEnvy != null && srAwakened != null
					&& srTierRedeemed != null && srEnvyTarget != null && srVirtuesActive != null
					&& srGodheadActive != null && srPactActive != null && srPactAttackBonus != null
					&& srWears != null && srWrathMin != null && srWrathMax != null
					&& srWrathMaxRedeemed != null && srEnvyBonus != null && srHumilityBonus != null
					&& srSacredHeartDamage != null && srGodheadDamage != null && srStarDamage != null;
		} catch (Throwable throwable) {
			warnOnce(SR_WARNED, "SR 固定伤害兼容：读取 SR 状态接口失败", throwable);
		}
		if (!srReady) {
			warnOnce(SR_WARNED, "SR 固定伤害兼容：SR 状态接口不完整，SR 增伤部分跳过", null);
		}
		return srReady;
	}

	private static boolean initSpirit() {
		if (srSpiritInitialized) {
			return srSpiritReady;
		}
		srSpiritInitialized = true;
		if (!ModList.get().isLoaded("summy_reliquary")) {
			return false;
		}
		try {
			// 先确保 CurioHelper.wears 已就绪；核心桥的其它接口失败不影响本条件层。
			initSr();
			if (srWears == null) {
				Class<?> curio = Class.forName("com.summy.reliquary.util.CurioHelper");
				srWears = method(SR_SPIRIT_WARNED, curio, "wears", LivingEntity.class, Item.class);
			}
			Class<?> spirit = Class.forName("com.summy.reliquary.effect.SpiritAltarSet");
			Class<?> config = Class.forName("com.summy.reliquary.config.ReliquaryConfig");
			Class<?> gaze = Class.forName("com.summy.reliquary.effect.GazeLook");
			Class<?> crown = Class.forName("com.summy.reliquary.effect.DevilCrown");
			srHasSetEffects = method(SR_SPIRIT_WARNED, spirit, "hasSetEffects", LivingEntity.class);
			srMindBonus = method(SR_SPIRIT_WARNED, config, "mindBonusPercent");
			srEnableOccultEye = method(SR_SPIRIT_WARNED, config, "enableOccultEye");
			srFearRadius = method(SR_SPIRIT_WARNED, config, "fearRadius");
			srFearDamageMultiplier = method(SR_SPIRIT_WARNED, config, "fearDamageMultiplier");
			srGazeLookedAt = method(SR_SPIRIT_WARNED, gaze, "lookedAt", ServerPlayer.class, double.class);
			srDevilCrownBonus = method(SR_SPIRIT_WARNED, crown, "damageBonusPercent", LivingEntity.class);
			srOccultEye = item(SR_SPIRIT_WARNED, "occult_eye");
			srSpiritReady = srHasSetEffects != null && srMindBonus != null
					&& srEnableOccultEye != null && srFearRadius != null
					&& srFearDamageMultiplier != null && srGazeLookedAt != null
					&& srDevilCrownBonus != null && srOccultEye != null && srWears != null;
		} catch (Throwable throwable) {
			warnOnce(SR_SPIRIT_WARNED, "SR 固定伤害兼容：读取 SpiritAltarSet 状态接口失败", throwable);
		}
		if (!srSpiritReady) {
			warnOnce(SR_SPIRIT_WARNED,
					"SR 固定伤害兼容：SpiritAltarSet 条件接口不完整，思想/魔眼/王冠部分跳过", null);
		}
		return srSpiritReady;
	}

	private static boolean initSe() {
		if (seInitialized) {
			return seReady;
		}
		seInitialized = true;
		if (!ModList.get().isLoaded("summy_enchantment")) {
			return false;
		}
		try {
			Class<?> effects = Class.forName("com.summy.enchantment.combat.EnchantmentEffects");
			seDeathsBlessingOutgoing = method(SE_WARNED, effects, "deathsBlessingOutgoing", LivingEntity.class);
			seReady = seDeathsBlessingOutgoing != null;
		} catch (Throwable throwable) {
			warnOnce(SE_WARNED, "SR 固定伤害兼容：读取 SE 死神祝福接口失败", throwable);
		}
		if (!seReady) {
			warnOnce(SE_WARNED, "SR 固定伤害兼容：SE 死神祝福接口不完整，SE 部分跳过", null);
		}
		return seReady;
	}

	private static boolean invokeBoolean(AtomicBoolean flag, Method method, Object... args) {
		if (method == null) {
			return false;
		}
		try {
			return Boolean.TRUE.equals(method.invoke(null, args));
		} catch (Throwable throwable) {
			warnOnce(flag, "固定伤害兼容：调用状态接口失败（" + method.getName() + "）", throwable);
			return false;
		}
	}

	private static double invokeDouble(AtomicBoolean flag, Method method, Object... args) {
		if (method == null) {
			return 0.0D;
		}
		try {
			Object value = method.invoke(null, args);
			return value instanceof Number number ? number.doubleValue() : 0.0D;
		} catch (Throwable throwable) {
			warnOnce(flag, "固定伤害兼容：读取数值失败（" + method.getName() + "）", throwable);
			return 0.0D;
		}
	}

	private static Object enumValue(Class<?> type, String name) {
		for (Object value : type.getEnumConstants()) {
			if (value instanceof Enum<?> enumValue && enumValue.name().equals(name)) {
				return value;
			}
		}
		return null;
	}

	private static Method method(AtomicBoolean flag, Class<?> type, String name, Class<?>... parameterTypes) {
		try {
			return type.getMethod(name, parameterTypes);
		} catch (Throwable throwable) {
			warnOnce(flag, "固定伤害兼容：找不到 " + type.getSimpleName() + "." + name, throwable);
			return null;
		}
	}

	private static Item item(AtomicBoolean flag, String path) {
		Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("summy-reliquary", path));
		if (item == null) {
			warnOnce(flag, "SR 固定伤害兼容：找不到物品 " + path, null);
		}
		return item;
	}

	private static void warnOnce(AtomicBoolean flag, String message, Throwable throwable) {
		if (!flag.compareAndSet(false, true)) {
			return;
		}
		if (throwable == null) {
			SummyThreat.LOGGER.warn("[Summy Threat] {}", message);
		} else {
			SummyThreat.LOGGER.warn("[Summy Threat] {}", message, throwable);
		}
	}
}
