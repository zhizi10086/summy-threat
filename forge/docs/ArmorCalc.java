import java.io.IOException;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.GeneralPath;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

/**
 * 护甲减伤计算器（单文件、零依赖）。
 *
 * <p>公式与 {@code docs/护甲公式计算.txt} 完全一致：
 * <pre>
 *   有效护甲 EA = A + (A × T) / 协同除数     （A=0 时 EA=0）
 *   玩家侧减伤  = clamp(EA / (EA + K), 0, 上限)             —— 玩家侧不截断 EA
 *   怪物侧减伤  = clamp(上限 × √(有效EA / 截断点), 0, 上限)，其中 有效EA = min(EA, 截断点)
 * </pre>
 *
 * <p>运行（JDK 17 及以上，无需编译）：
 * <pre>
 *   java ArmorCalc.java                                 交互模式
 *   java ArmorCalc.java --a 20 --t 8                     单次计算（默认玩家侧）
 *   java ArmorCalc.java --side monster --a 40 --t 10 --damage 30
 *   java ArmorCalc.java --table                          参考表（原版套装 / 怪物层级 / EA 对照）
 *   java ArmorCalc.java --sweep --a-from 0 --a-to 100 --step 2 --t 8
 *   java ArmorCalc.java --solve --side player --t 8 --target 60    反解：要 60% 减伤需要多少 A
 *   java ArmorCalc.java --sweep --side monster --csv curve.csv
 *   java ArmorCalc.java --sweep --png curve.png          导出曲线图（PNG，双击即可看图；另有 --svg）
 * </pre>
 *
 * <p>常量可在命令行覆盖：--synergy 20 --k 20 --player-min 5 --player-max 85
 * --monster-max 80 --monster-cap 100（百分比参数写 5 或 0.05 都可以）。
 */
public final class ArmorCalc {

	// ============================================================
	// 常量区（与 docs/护甲公式计算.txt 的常量一致，命令行可覆盖）
	// ============================================================

	/** EA = A + (A*T)/C 中的除数，越小韧性放大越强 */
	private static double synergyDivisor = 15.0;
	/** 玩家侧：减伤 = EA / (EA + K) */
	private static double playerK = 20.0;
	/** 玩家侧减伤下限（仅在 EA > 0 时生效） */
	private static double playerMin = 0.0;
	/** 玩家侧减伤上限 */
	private static double playerMax = 0.85;
	/** 怪物侧减伤上限 */
	private static double monsterMax = 0.90;
	/** 怪物侧有效 EA 上限：EA 达到此值后不再参与减伤计算（属性本身不受限） */
	private static double monsterCap = 100.0;

	/** 一套护甲的参考点 */
	private record ArmorSet(String name, double armor, double toughness) {
	}

	/**
	 * 玩家侧参考点：原版盔甲套装。
	 *
	 * <p>数值取自 1.20.1 的 {@code net.minecraft.world.item.ArmorMaterials}（已核对字节码）：
	 * 皮甲 {1,2,3,1} 韧性 0、锁链 {1,4,5,2} 韧性 0、铁 {2,5,6,2} 韧性 0、
	 * 金 {1,3,5,2} 韧性 0、钻石 {3,6,8,3} 每件韧性 2（合计 8）、下界合金每件韧性 3（合计 12）。
	 * 数组顺序为 {靴, 护腿, 胸甲, 头盔}，下面是每套的合计值。
	 */
	private static final List<ArmorSet> PLAYER_SETS = List.of(
			new ArmorSet("皮革", 7, 0),
			new ArmorSet("锁链", 12, 0),
			new ArmorSet("金", 11, 0),
			new ArmorSet("铁", 15, 0),
			new ArmorSet("钻石", 20, 8),
			new ArmorSet("下界合金", 20, 12));

	/** 怪物侧参考点：取内部《敌方护甲-TL表.md》的代表档（TL 0/4/8/12/16/20 + 三个特殊单位） */
	private static final List<ArmorSet> MONSTER_SETS = List.of(
			new ArmorSet("TL 0", 0, 0),
			new ArmorSet("TL 4", 3, 1),
			new ArmorSet("TL 8", 8, 5),
			new ArmorSet("TL 12", 16, 12),
			new ArmorSet("TL 16", 25, 20),
			new ArmorSet("TL 20", 38, 28),
			new ArmorSet("区域首领", 60, 40),
			new ArmorSet("Boss", 100, 50),
			new ArmorSet("超级Boss", 200, 80));

	private ArmorCalc() {
	}

	// ============================================================
	// 公式（与 txt 里逐行一致）
	// ============================================================

	/** 有效护甲值：EA = A + (A*T)/C（A=0 时 EA=0） */
	private static double effectiveArmor(double armor, double toughness) {
		return armor + (armor * toughness) / synergyDivisor;
	}

	/** 玩家侧：clamp(EA/(EA+K), 下限, 上限)；默认下限 0，玩家侧不截断 EA */
	private static double playerReduction(double effectiveArmor) {
		if (effectiveArmor <= 0) {
			return 0.0;
		}
		double raw = effectiveArmor / (effectiveArmor + playerK);
		return Math.max(playerMin, Math.min(playerMax, raw));
	}

	/** 怪物侧：先把 EA 截断到 有效EA上限，再 min(上限, 上限 × √(有效EA/上限)) */
	private static double monsterReduction(double effectiveArmor) {
		double cap = Math.max(monsterCap, 1.0);
		double effective = Math.min(Math.max(effectiveArmor, 0.0), cap);
		double raw = monsterMax * Math.sqrt(effective / cap);
		return Math.min(raw, monsterMax);
	}

	/** 按侧别取减伤 */
	private static double reduction(String side, double effectiveArmor) {
		return "monster".equals(side) ? monsterReduction(effectiveArmor) : playerReduction(effectiveArmor);
	}

	/** 玩家侧反解：给定目标减伤，需要多少 EA */
	private static double playerEffectiveArmorFor(double target) {
		if (target <= playerMin) {
			return 0.0;
		}
		if (target >= playerMax) {
			return Double.POSITIVE_INFINITY;
		}
		return playerK * target / (1.0 - target);
	}

	/** 怪物侧反解：给定目标减伤，需要多少 EA */
	private static double monsterEffectiveArmorFor(double target) {
		if (target <= 0.0) {
			return 0.0;
		}
		if (target >= monsterMax) {
			return Double.POSITIVE_INFINITY;
		}
		double ratio = target / monsterMax;
		return monsterCap * ratio * ratio;
	}

	/** 由目标 EA 反解需要的 A（固定韧性 T）：EA = A(1 + T/C) ⇒ A = EA / (1 + T/C) */
	private static double armorFor(double targetEffectiveArmor, double toughness) {
		double factor = 1.0 + toughness / synergyDivisor;
		double armor = targetEffectiveArmor / factor;
		return Math.max(armor, 0.0);
	}

	// ============================================================
	// 输出辅助
	// ============================================================

	private static String pct(double value) {
		return String.format(Locale.ROOT, "%.2f%%", value * 100.0);
	}

	private static String num(double value) {
		return String.format(Locale.ROOT, "%.2f", value);
	}

	/** 整数就按整数打印，避免刻度写成 "24.00" */
	private static String tick(double value) {
		return value == Math.rint(value) && Math.abs(value) < 1e15
				? String.format(Locale.ROOT, "%.0f", value)
				: num(value);
	}

	private static void printFormula() {
		System.out.printf(Locale.ROOT,
				"公式：EA = A + (A×T)/%.0f（协同除数；A=0 时 EA=0）%n"
						+ "      玩家侧减伤 = clamp(EA/(EA+%.0f), %.0f%%, %.0f%%)（不截断 EA）%n"
						+ "      怪物侧减伤 = clamp(%.0f%% × √(有效EA/%.0f), 0, %.0f%%)，有效EA = min(EA, %.0f)%n",
				synergyDivisor, playerK, playerMin * 100, playerMax * 100,
				monsterMax * 100, monsterCap, monsterMax * 100, monsterCap);
	}

	private static void printSingle(String side, double armor, double toughness, double damage) {
		double ea = effectiveArmor(armor, toughness);
		double red = reduction(side, ea);
		System.out.println("---------------------------------------------");
		System.out.printf(Locale.ROOT, "侧别：%s%n", "monster".equals(side) ? "怪物" : "玩家");
		System.out.printf(Locale.ROOT, "护甲 A = %s，韧性 T = %s%n", num(armor), num(toughness));
		System.out.printf(Locale.ROOT, "有效护甲 EA = %s%n", num(ea));
		System.out.printf(Locale.ROOT, "减伤 = %s%n", pct(red));
		if (damage > 0) {
			System.out.printf(Locale.ROOT, "原始伤害 %s → 实际伤害 %s（减免 %s）%n",
					num(damage), num(damage * (1.0 - red)), num(damage * red));
		}
	}

	private static void printTable() {
		printFormula();
		System.out.println();
		System.out.printf(Locale.ROOT, "%-10s %6s %6s %8s %10s%n", "玩家套装", "A", "T", "EA", "减伤");
		for (ArmorSet set : PLAYER_SETS) {
			double ea = effectiveArmor(set.armor(), set.toughness());
			System.out.printf(Locale.ROOT, "%-10s %6s %6s %8s %10s%n",
					set.name(), num(set.armor()), num(set.toughness()), num(ea), pct(playerReduction(ea)));
		}
		System.out.println();
		System.out.printf(Locale.ROOT, "%-10s %6s %6s %8s %10s%n", "怪物层级", "A", "T", "EA", "减伤");
		for (ArmorSet set : MONSTER_SETS) {
			double ea = effectiveArmor(set.armor(), set.toughness());
			System.out.printf(Locale.ROOT, "%-10s %6s %6s %8s %10s%n",
					set.name(), num(set.armor()), num(set.toughness()), num(ea), pct(monsterReduction(ea)));
		}
		System.out.println();
		System.out.printf(Locale.ROOT, "%-10s %10s %10s%n", "EA", "玩家减伤", "怪物减伤");
		for (double ea = 0; ea <= 150.0001; ea += 10) {
			System.out.printf(Locale.ROOT, "%-10s %10s %10s%n",
					num(ea), pct(playerReduction(ea)), pct(monsterReduction(ea)));
		}
	}

	/** 扫表：固定 T，A 从 from 到 to。同时可导出 CSV / SVG */
	private static void printSweep(String side, double toughness, double from, double to, double step,
			String csvPath, String svgPath, String pngPath) throws IOException {
		List<double[]> rows = new ArrayList<>();
		System.out.printf(Locale.ROOT, "侧别：%s，韧性 T = %s%n", "monster".equals(side) ? "怪物" : "玩家", num(toughness));
		System.out.printf(Locale.ROOT, "%8s %10s %12s%n", "A", "EA", "减伤");
		for (double a = from; a <= to + 1e-9; a += step) {
			double ea = effectiveArmor(a, toughness);
			double red = reduction(side, ea);
			rows.add(new double[] { a, ea, red });
			System.out.printf(Locale.ROOT, "%8s %10s %12s%n", num(a), num(ea), pct(red));
		}
		if (csvPath != null) {
			StringBuilder sb = new StringBuilder("A,T,EA,reduction\n");
			for (double[] row : rows) {
				sb.append(String.format(Locale.ROOT, "%s,%s,%s,%s%n",
						num(row[0]), num(toughness), num(row[1]), num(row[2])));
			}
			Files.writeString(Path.of(csvPath), sb.toString(), StandardCharsets.UTF_8);
			System.out.println("已导出 CSV：" + csvPath);
		}
		if (svgPath != null) {
			Files.writeString(Path.of(svgPath), renderSvg(side, toughness, rows), StandardCharsets.UTF_8);
			System.out.println("已导出 SVG：" + svgPath);
		}
		if (pngPath != null) {
			writePng(pngPath, side, toughness, rows);
			System.out.println("已导出 PNG：" + pngPath);
		}
	}

	/** 用 JDK 自带的 AWT 画一张曲线图（零第三方依赖，无显示环境也能画） */
	private static void writePng(String path, String side, double toughness, List<double[]> rows) throws IOException {
		int width = 900;
		int height = 520;
		int left = 70;
		int right = 30;
		int top = 40;
		int bottom = 84;
		double xMax = rows.isEmpty() ? 100 : rows.get(rows.size() - 1)[0];
		double plotW = width - left - right;
		double plotH = height - top - bottom;

		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		// 中文字体：Windows 自带微软雅黑；缺失时回落到默认字体（中文可能显示为方块）
		Font base = new Font("Microsoft YaHei", Font.PLAIN, 13);
		g.setFont(base);
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, width, height);

		// 网格与纵轴刻度（0~100%）
		g.setStroke(new BasicStroke(1f));
		for (int i = 0; i <= 10; i++) {
			int y = (int) Math.round(height - bottom - plotH * i / 10.0);
			g.setColor(new Color(0xEE, 0xEE, 0xEE));
			g.drawLine(left, y, width - right, y);
			g.setColor(Color.DARK_GRAY);
			String label = (i * 10) + "%";
			g.drawString(label, left - 8 - g.getFontMetrics().stringWidth(label), y + 4);
		}
		// 坐标轴
		g.setColor(new Color(0x33, 0x33, 0x33));
		g.drawLine(left, height - bottom, width - right, height - bottom);
		g.drawLine(left, top, left, height - bottom);

		// 横轴刻度（A）
		int ticks = 5;
		for (int i = 0; i <= ticks; i++) {
			double a = xMax * i / ticks;
			int x = (int) Math.round(left + plotW * i / (double) ticks);
			String label = tick(a);
			g.drawString(label, x - g.getFontMetrics().stringWidth(label) / 2, height - bottom + 24);
		}
		g.drawString("护甲值 A", left + (int) (plotW / 2) - 25, height - bottom + 50);

		// 曲线
		g.setColor(new Color(0x2E, 0x86, 0xAB));
		g.setStroke(new BasicStroke(2.5f));
		GeneralPath curve = new GeneralPath();
		boolean first = true;
		for (double[] row : rows) {
			double x = left + plotW * (xMax <= 0 ? 0 : row[0] / xMax);
			double y = height - bottom - plotH * Math.min(row[2], 1.0);
			if (first) {
				curve.moveTo(x, y);
				first = false;
			} else {
				curve.lineTo(x, y);
			}
		}
		g.draw(curve);

		// 参考点
		for (ArmorSet set : "monster".equals(side) ? MONSTER_SETS : PLAYER_SETS) {
			if (set.armor() > xMax) {
				continue;
			}
			double ea = effectiveArmor(set.armor(), set.toughness());
			double red = reduction(side, ea);
			int x = (int) Math.round(left + plotW * (xMax <= 0 ? 0 : set.armor() / xMax));
			int y = (int) Math.round(height - bottom - plotH * Math.min(red, 1.0));
			g.setColor(new Color(0xE6, 0x39, 0x46));
			g.fillOval(x - 4, y - 4, 8, 8);
			g.drawString(set.name() + " " + pct(red), x + 6, y - 6);
		}

		// 标题
		g.setColor(Color.BLACK);
		g.setFont(base.deriveFont(Font.BOLD, 15f));
		g.drawString(String.format(Locale.ROOT, "%s侧减伤曲线（韧性 T=%s，协同除数 %.0f）",
				"monster".equals(side) ? "怪物" : "玩家", num(toughness), synergyDivisor), left, 24);
		// 脚注：参考点用的是各自套装的韧性，和曲线的固定 T 不是一回事，写清楚避免误读
		g.setFont(base.deriveFont(Font.PLAIN, 12f));
		g.setColor(Color.GRAY);
		g.drawString(String.format(Locale.ROOT,
				"注：曲线为固定韧性 T=%s；参考点（红点）按各自套装的韧性单独计算",
				num(toughness)), left, height - 10);
		g.dispose();
		ImageIO.write(image, "png", new File(path));
	}

	/** 反解：给定目标减伤，算所需的 EA 与 A（固定韧性 T） */
	private static void printSolve(String side, double toughness, double target) {
		double ea = "monster".equals(side) ? monsterEffectiveArmorFor(target) : playerEffectiveArmorFor(target);
		System.out.printf(Locale.ROOT, "侧别：%s，目标减伤 %s（韧性 T 固定为 %s）%n",
				"monster".equals(side) ? "怪物" : "玩家", pct(target), num(toughness));
		if (Double.isInfinite(ea)) {
			System.out.printf(Locale.ROOT, "目标超过该侧上限（玩家 %.0f%% / 怪物 %.0f%%），无解%n",
					playerMax * 100, monsterMax * 100);
			return;
		}
		double armor = armorFor(ea, toughness);
		System.out.printf(Locale.ROOT, "需要的有效护甲 EA = %s%n", num(ea));
		System.out.printf(Locale.ROOT, "对应的护甲值 A = %s%n", num(armor));
		// 回代校验，避免反解与正算不一致时静默出错
		double check = reduction(side, effectiveArmor(armor, toughness));
		System.out.printf(Locale.ROOT, "回代校验：A=%s, T=%s → 减伤 %s%n", num(armor), num(toughness), pct(check));
	}

	/** 极简 SVG 折线图：横轴 A，纵轴减伤 */
	private static String renderSvg(String side, double toughness, List<double[]> rows) {
		int width = 900;
		int height = 520;
		int left = 70;
		int right = 30;
		int top = 40;
		int bottom = 84;
		double xMax = rows.isEmpty() ? 100 : rows.get(rows.size() - 1)[0];
		double plotW = width - left - right;
		double plotH = height - top - bottom;
		StringBuilder sb = new StringBuilder();
		sb.append(String.format(Locale.ROOT,
				"<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"%d\" height=\"%d\">%n", width, height));
		sb.append("<style>text{font-family:'Microsoft YaHei',sans-serif;font-size:12px}</style>\n");
		sb.append("<rect width=\"100%\" height=\"100%\" fill=\"white\"/>\n");
		// 坐标轴
		sb.append(String.format(Locale.ROOT,
				"<line x1=\"%d\" y1=\"%d\" x2=\"%d\" y2=\"%d\" stroke=\"#333\"/>%n",
				left, height - bottom, width - right, height - bottom));
		sb.append(String.format(Locale.ROOT,
				"<line x1=\"%d\" y1=\"%d\" x2=\"%d\" y2=\"%d\" stroke=\"#333\"/>%n",
				left, top, left, height - bottom));
		sb.append(String.format(Locale.ROOT,
				"<text x=\"%d\" y=\"%d\" text-anchor=\"middle\">护甲值 A</text>%n",
				(left + width - right) / 2, height - bottom + 50));
		sb.append(String.format(Locale.ROOT,
				"<text x=\"16\" y=\"%d\" transform=\"rotate(-90 16 %d)\" text-anchor=\"middle\">减伤比例</text>%n",
				(top + height - bottom) / 2, (top + height - bottom) / 2));
		// 纵轴刻度 0~100%
		for (int i = 0; i <= 10; i++) {
			double y = height - bottom - plotH * i / 10.0;
			sb.append(String.format(Locale.ROOT,
					"<line x1=\"%d\" y1=\"%.1f\" x2=\"%d\" y2=\"%.1f\" stroke=\"#eee\"/>%n", left, y, width - right, y));
			sb.append(String.format(Locale.ROOT,
					"<text x=\"%d\" y=\"%.1f\" text-anchor=\"end\">%d%%</text>%n", left - 8, y + 4, i * 10));
		}
		// 曲线
		StringBuilder points = new StringBuilder();
		for (double[] row : rows) {
			double x = left + plotW * (xMax <= 0 ? 0 : row[0] / xMax);
			double y = height - bottom - plotH * Math.min(row[2], 1.0);
			points.append(String.format(Locale.ROOT, "%.1f,%.1f ", x, y));
		}
		sb.append(String.format(Locale.ROOT,
				"<polyline fill=\"none\" stroke=\"#2E86AB\" stroke-width=\"2.5\" points=\"%s\"/>%n", points.toString().trim()));
		// 参考点
		for (ArmorSet set : "monster".equals(side) ? MONSTER_SETS : PLAYER_SETS) {
			double ea = effectiveArmor(set.armor(), set.toughness());
			double red = reduction(side, ea);
			if (set.armor() > xMax) {
				continue;
			}
			double x = left + plotW * (xMax <= 0 ? 0 : set.armor() / xMax);
			double y = height - bottom - plotH * Math.min(red, 1.0);
			sb.append(String.format(Locale.ROOT, "<circle cx=\"%.1f\" cy=\"%.1f\" r=\"4\" fill=\"#E63946\"/>%n", x, y));
			sb.append(String.format(Locale.ROOT,
					"<text x=\"%.1f\" y=\"%.1f\" fill=\"#E63946\">%s %s</text>%n", x + 6, y - 6, set.name(), pct(red)));
		}
		sb.append(String.format(Locale.ROOT,
				"<text x=\"%d\" y=\"24\">%s侧减伤曲线（韧性 T=%s，协同除数 %.0f）</text>%n",
				left, "monster".equals(side) ? "怪物" : "玩家", num(toughness), synergyDivisor));
		sb.append(String.format(Locale.ROOT,
				"<text x=\"%d\" y=\"%d\" fill=\"#888\">注：曲线为固定韧性 T=%s；参考点（红点）按各自套装的韧性单独计算</text>%n",
				left, height - 10, num(toughness)));
		sb.append("</svg>\n");
		return sb.toString();
	}

	// ============================================================
	// 交互模式
	// ============================================================

	private static void interactive() {
		printFormula();
		System.out.println("输入 侧别(1=玩家,2=怪物) 护甲A 韧性T [原始伤害]，空行退出：");
		try (Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8)) {
			while (true) {
				System.out.print("> ");
				if (!scanner.hasNextLine()) {
					break;
				}
				String line = scanner.nextLine().trim();
				if (line.isEmpty()) {
					break;
				}
				String[] parts = line.split("[\\s,]+");
				try {
					String side = parts.length > 0 && parts[0].startsWith("2") ? "monster" : "player";
					double armor = parts.length > 1 ? Double.parseDouble(parts[1]) : 0;
					double toughness = parts.length > 2 ? Double.parseDouble(parts[2]) : 0;
					double damage = parts.length > 3 ? Double.parseDouble(parts[3]) : 0;
					printSingle(side, armor, toughness, damage);
				} catch (NumberFormatException ex) {
					System.out.println("解析失败，示例：1 20 8 10");
				}
			}
		}
	}

	// ============================================================
	// 入口
	// ============================================================

	private static void printHelp() {
		System.out.println("""
				护甲减伤计算器（单文件、零依赖）

				单次计算：   java ArmorCalc.java --a 20 --t 8 [--side player|monster] [--damage 30]
				参考表：     java ArmorCalc.java --table
				扫表：       java ArmorCalc.java --sweep [--side ...] [--t 8] [--a-from 0] [--a-to 100] [--step 2]
				反解：       java ArmorCalc.java --solve [--side ...] [--t 8] --target 60
				导出：       追加 --csv 文件名.csv / --png 文件名.png / --svg 文件名.svg（仅扫表时生效）

				覆盖常量：   --synergy 15 --k 20 --player-min 0 --player-max 85 --monster-max 90 --monster-cap 100
				            （百分比参数写 60 或 0.6 都可）
				输入范围：   与图形计算器一致——护甲 A 0~500、韧性 T 0~100
				交互模式：   不带任何参数

				终端中文乱码时：先执行 chcp 65001，或给 java 加 -Dstdout.encoding=UTF-8
				""");
	}

	/** 百分比参数既接受 0~1，也接受 0~100 */
	private static double asFraction(String raw) {
		double value = Double.parseDouble(raw);
		return value > 1.0 ? value / 100.0 : value;
	}

	public static void main(String[] args) throws IOException {
		if (args.length == 0) {
			interactive();
			return;
		}
		String side = "player";
		double armor = Double.NaN;
		double toughness = 0;
		double damage = 0;
		double from = 0;
		double to = 100;
		double step = 2;
		double target = Double.NaN;
		String csvPath = null;
		String svgPath = null;
		String pngPath = null;
		boolean table = false;
		boolean sweep = false;
		boolean solve = false;

		for (int i = 0; i < args.length; i++) {
			String arg = args[i];
			switch (arg) {
				case "--help", "-h" -> {
					printHelp();
					return;
				}
				case "--side" -> side = args[++i].toLowerCase(Locale.ROOT);
				case "--a" -> armor = Double.parseDouble(args[++i]);
				case "--t" -> toughness = Double.parseDouble(args[++i]);
				case "--damage" -> damage = Double.parseDouble(args[++i]);
				case "--a-from" -> from = Double.parseDouble(args[++i]);
				case "--a-to" -> to = Double.parseDouble(args[++i]);
				case "--step" -> step = Double.parseDouble(args[++i]);
				case "--target" -> target = asFraction(args[++i]);
				case "--csv" -> csvPath = args[++i];
				case "--svg" -> svgPath = args[++i];
				case "--png" -> pngPath = args[++i];
				case "--table" -> table = true;
				case "--sweep" -> sweep = true;
				case "--solve" -> solve = true;
				case "--synergy" -> synergyDivisor = Double.parseDouble(args[++i]);
				case "--k" -> playerK = Double.parseDouble(args[++i]);
				case "--player-min" -> playerMin = asFraction(args[++i]);
				case "--player-max" -> playerMax = asFraction(args[++i]);
				case "--monster-max" -> monsterMax = asFraction(args[++i]);
				case "--monster-cap" -> monsterCap = Double.parseDouble(args[++i]);
				default -> {
					System.err.println("未知参数：" + arg + "（--help 看用法）");
					System.exit(2);
				}
			}
		}
		if (table) {
			printTable();
			return;
		}
		if (solve) {
			if (Double.isNaN(target)) {
				System.err.println("--solve 需要 --target（例如 --target 60）");
				System.exit(2);
			}
			printSolve(side, toughness, target);
			return;
		}
		if (sweep) {
			printSweep(side, toughness, from, to, step, csvPath, svgPath, pngPath);
			return;
		}
		if (Double.isNaN(armor)) {
			printHelp();
			return;
		}
		printSingle(side, armor, toughness, damage);
	}
}
