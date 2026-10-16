package com.summy.threat.combat;

import com.summy.threat.Config;
import com.summy.threat.scaling.ScalingState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 护甲减伤公式（口径见 docs/护甲公式计算.txt，改公式请先改那份规格）。
 *
 * <pre>
 *   有效护甲 EA = A + (A*T)/C            （A=护甲值、T=韧性、C=协同除数）
 *   玩家侧减伤  = clamp(EA/(EA+K), 下限, 上限)          —— 玩家侧不截断 EA
 *   怪物侧减伤  = clamp(上限 * sqrt(有效EA/截断点), 0, 上限)，其中 有效EA = min(EA, 截断点)
 *   档位压缩    = 有效EA' = 1 + (有效EA − 1) × r_hp     （§5.0 生态分档；r_hp=1 时与上面完全等价）
 * </pre>
 *
 * <p>默认值与规格一致：C=15、K=20、玩家上限 85%、玩家下限 0%、怪物上限 90%、怪物 EA 截断 100。
 * 这些都可在 SERVER 档配置里改；配置尚未加载时回退到默认值，避免受伤流程里抛异常。
 *
 * <p>侧别判定：{@link Player} 及其子类走玩家曲线，其余生物（模组怪、动物、村民、盔甲座等）
 * 走怪物曲线。
 */
public final class ArmorDamageFormula {
	// 配置未加载时的回退值（= 确认稿默认值）
	private static final double FALLBACK_SYNERGY_DIVISOR = 15.0D;
	private static final double FALLBACK_PLAYER_K = 20.0D;
	private static final double FALLBACK_PLAYER_MIN = 0.0D;
	private static final double FALLBACK_PLAYER_MAX = 0.85D;
	private static final double FALLBACK_MONSTER_MAX = 0.90D;
	private static final double FALLBACK_MONSTER_EFFECTIVE_EA_CAP = 100.0D;

	private ArmorDamageFormula() {
	}

	/** 有效护甲：EA = A + (A*T)/C；C 非法时退化为只看护甲（配置范围下限 1 已挡住，这里是双保险） */
	public static double effectiveArmor(double armor, double toughness, double synergy) {
		if (synergy <= 0.0D) {
			return armor;
		}
		return armor + (armor * toughness) / synergy;
	}

	/** 玩家侧：clamp(EA/(EA+K), 下限, 上限) */
	public static double playerReduction(double effectiveArmor, double k, double min, double max) {
		double raw = positive(effectiveArmor) / (positive(effectiveArmor) + Math.max(k, 0.0D));
		return clamp(raw, min, max);
	}

	/** 怪物侧：先把 EA 截断到 cap，再 clamp(上限 * sqrt(有效EA/cap), 0, 上限) */
	public static double monsterReduction(double effectiveArmor, double max, double cap) {
		double ea = Math.min(positive(effectiveArmor), Math.max(cap, 1.0D));
		double raw = Math.max(max, 0.0D) * Math.sqrt(ea / Math.max(cap, 1.0D));
		return clamp(raw, 0.0D, max);
	}

	/**
	 * 生态档位压缩（交接文档 §5.0）：{@code 有效EA' = 1 + (有效EA − 1) × r_hp}。
	 *
	 * <p>有效 EA 为 0 时保持 0——保证"TL 0 锚定原版"这条不变式（低 TL 怪不会被凭空加上减伤）。
	 */
	public static double compressEffectiveArmor(double effectiveArmor, double cap, double rHp) {
		double ea = Math.min(positive(effectiveArmor), Math.max(cap, 1.0D));
		if (ea <= 0.0D) {
			return 0.0D;
		}
		return Math.max(0.0D, 1.0D + (ea - 1.0D) * clamp(rHp, 0.0D, 1.0D));
	}

	/** 总开关：配置未加载时按启用处理（受伤流程发生在配置加载之后，这里只是兜底） */
	public static boolean enabled() {
		return !Config.SPEC.isLoaded() || Config.ENABLE_CUSTOM_ARMOR_FORMULA.get();
	}

	/** 按实体取 (A, T) 算减伤：玩家走玩家曲线，其余生物走怪物曲线 */
	public static double reductionFor(LivingEntity entity, double armor, double toughness) {
		double ea = effectiveArmor(armor, toughness, cfg(Config.SYNERGY_DIVISOR, FALLBACK_SYNERGY_DIVISOR));
		if (entity instanceof Player) {
			return playerReduction(ea,
					cfg(Config.PLAYER_K, FALLBACK_PLAYER_K),
					cfg(Config.PLAYER_MIN, FALLBACK_PLAYER_MIN),
					cfg(Config.PLAYER_MAX, FALLBACK_PLAYER_MAX));
		}
		double cap = cfg(Config.MONSTER_EFFECTIVE_EA_CAP, FALLBACK_MONSTER_EFFECTIVE_EA_CAP);
		double compressed = compressEffectiveArmor(ea, cap, ScalingState.rHp());
		return monsterReduction(compressed,
				cfg(Config.MONSTER_MAX, FALLBACK_MONSTER_MAX),
				cap);
	}

	/** 用实体自身的属性值算最终伤害：amount * (1 - 减伤) */
	public static double armorAbsorb(LivingEntity entity, double amount) {
		double armor = attributeValue(entity, Attributes.ARMOR);
		double toughness = attributeValue(entity, Attributes.ARMOR_TOUGHNESS);
		return amount * (1.0D - reductionFor(entity, armor, toughness));
	}

	/** 取属性原始 double 值；实体没有该属性时按 0（模组生物可能不带这两个属性） */
	private static double attributeValue(LivingEntity entity, Attribute attribute) {
		return entity.getAttributes().hasAttribute(attribute) ? entity.getAttributeValue(attribute) : 0.0D;
	}

	private static double cfg(ForgeConfigSpec.DoubleValue value, double fallback) {
		return Config.SPEC.isLoaded() ? value.get() : fallback;
	}

	private static double positive(double value) {
		return value > 0.0D ? value : 0.0D;
	}

	private static double clamp(double value, double min, double max) {
		double lo = Math.min(min, max);
		double hi = Math.max(min, max);
		return Math.min(Math.max(value, lo), hi);
	}
}
