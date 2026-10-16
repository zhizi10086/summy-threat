package com.summy.threat.scaling;

import com.summy.threat.Config;
import net.minecraftforge.fml.ModList;

/**
 * 运行期缩放状态：生态档位、两维系数（r_hp / r_atk）、全局 TL，以及数值表读取。
 *
 * <p>口径见内部《Summy Threat — 核心数值系统交接文档》：
 * <ul>
 *     <li>§5.0 生态分档：none / sr / se / sr_apocrypha / se_sr / full；none 档怪物侧整体短路。</li>
 *     <li>缩放公式 {@code v' = 1 + (v − 1) × r}；TL 0 恒为原版（M(0) = A(0) = 1.0）。</li>
 *     <li>§4 怪物护甲表（按 TL）——数值稳定，直接以常量保存。</li>
 * </ul>
 *
 * <p>TL 来源：本轮只有"全局 TL"（命令 / 配置），后续由 PCF/CDF 替换。
 */
public final class ScalingState {
	/** 三件套档位；SRA 强制依赖 SR，故"仅 SRA"不存在 */
	public enum Tier { NONE, SR, SE, SR_APOCRYPHA, SE_SR, FULL }

	/** 怪物护甲表（交接文档 §4）：索引 = TL，[0] = A，[1] = T */
	private static final double[][] MONSTER_ARMOR = {
			{0, 0}, {0, 0}, {1, 0}, {2, 0}, {3, 1}, {4, 2}, {5, 3}, {6, 4}, {8, 5}, {10, 6}, {12, 8},
			{14, 10}, {16, 12}, {18, 14}, {20, 16}, {22, 18}, {25, 20}, {28, 22}, {31, 24}, {34, 26}, {38, 28}
	};

	private static boolean initialized;
	private static Tier tier = Tier.NONE;
	private static double rHp = 1.0D;
	private static double rAtk = 1.0D;
	private static int globalTl = -1;

	private ScalingState() {
	}

	/** 配置加载 / 重载 / 档位命令后调用：重判档位、刷新系数与全局 TL 默认值 */
	public static void refresh() {
		if (!Config.SPEC.isLoaded()) {
			// 配置尚未加载：保持默认（none / 系数 1.0 / TL 关闭），避免受伤流程里抛异常
			initialized = false;
			return;
		}
		Config.TierMode mode = Config.ENVIRONMENT_TIER_MODE.get();
		tier = (mode == Config.TierMode.AUTO) ? detect() : Tier.valueOf(mode.name());
		switch (tier) {
			case SR -> {
				rHp = Config.HP_SCALE_SR.get();
				rAtk = Config.ATK_SCALE_SR.get();
			}
			case SE -> {
				rHp = Config.HP_SCALE_SE.get();
				rAtk = Config.ATK_SCALE_SE.get();
			}
			case SR_APOCRYPHA -> {
				rHp = Config.HP_SCALE_SR_APOCRYPHA.get();
				rAtk = Config.ATK_SCALE_SR_APOCRYPHA.get();
			}
			case SE_SR -> {
				rHp = Config.HP_SCALE_SE_SR.get();
				rAtk = Config.ATK_SCALE_SE_SR.get();
			}
			case FULL -> {
				rHp = Config.HP_SCALE_FULL.get();
				rAtk = Config.ATK_SCALE_FULL.get();
			}
			case NONE -> {
				// 短路档：系数不参与计算（保持 1.0，避免误用）
				rHp = 1.0D;
				rAtk = 1.0D;
			}
		}
		globalTl = Config.SCALING_GLOBAL_TL.get();
		initialized = true;
	}

	/** 事件里调用；配置尚未加载时先补一次（例如世界加载早于 ServerStartedEvent） */
	public static void ensureInit() {
		if (!initialized) {
			refresh();
		}
	}

	private static Tier detect() {
		boolean se = isLoaded("summy_enchantment");
		boolean sr = isLoaded("summy_reliquary");
		boolean sra = isLoaded("summy_reliquary_apocrypha");
		if (!se && !sr && !sra) {
			return Tier.NONE;
		}
		if (se && sr && sra) {
			return Tier.FULL;
		}
		if (se && sr) {
			return Tier.SE_SR;
		}
		if (se) {
			return Tier.SE;
		}
		if (sr && sra) {
			return Tier.SR_APOCRYPHA;
		}
		return Tier.SR;
	}

	private static boolean isLoaded(String modId) {
		ModList list = ModList.get();
		return list != null && list.isLoaded(modId);
	}

	public static Tier tier() {
		ensureInit();
		return tier;
	}

	public static double rHp() {
		ensureInit();
		return rHp;
	}

	public static double rAtk() {
		ensureInit();
		return rAtk;
	}

	public static int globalTl() {
		ensureInit();
		return globalTl;
	}

	public static void setGlobalTl(int tl) {
		ensureInit();
		globalTl = Math.max(-1, Math.min(20, tl));
	}

	/** 是否接管怪物侧：档位不是 none，且全局 TL 已设置（≥ 0） */
	public static boolean enabled() {
		ensureInit();
		return tier != Tier.NONE && globalTl >= 0;
	}

	/** 缩放公式 v' = 1 + (v − 1) × r */
	public static double scale(double v, double r) {
		return 1.0D + (v - 1.0D) * r;
	}

	public static int clampTl(int tl) {
		return Math.max(0, Math.min(20, tl));
	}

	// ================= 数值表读取（已含档位缩放） =================

	public static double healthMultiplier(int tl) {
		return scale(Config.HEALTH_MULTIPLIER_PER_TL[clampTl(tl)].get(), rHp());
	}

	public static double attackMultiplier(int tl) {
		return scale(Config.ATTACK_MULTIPLIER_PER_TL[clampTl(tl)].get(), rAtk());
	}

	public static double eximusMultiplier(int tl) {
		return scale(Config.EXIMUS_HEALTH_MULTIPLIER_PER_TL[clampTl(tl)].get(), rHp());
	}

	/** 怪物护甲表值（未缩放——档位压缩在减伤计算里做，见 ArmorDamageFormula） */
	public static double armorValue(int tl) {
		return MONSTER_ARMOR[clampTl(tl)][0];
	}

	public static double toughnessValue(int tl) {
		return MONSTER_ARMOR[clampTl(tl)][1];
	}
}
