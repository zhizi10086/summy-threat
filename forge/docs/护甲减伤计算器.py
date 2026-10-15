#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
护甲减伤计算器（GUI）

公式与参数说明见同目录的《护甲公式计算.txt》。本文件只做实现，两者口径必须一致：

    有效护甲   EA = A + (A*T)/C          （A=0 时 EA=0；T 是护甲的放大系数）
    玩家侧减伤 = EA==0 ? 0% : clamp(EA/(EA+K), 下限, 上限)
    怪物侧减伤 = min(上限, 上限 * sqrt(EA/EA上限))

用法：
    python 护甲减伤计算器.py              打开窗口
    python 护甲减伤计算器.py --selftest   不建窗口，跑一遍数据并导出 armor_calc_selftest.png

界面：左侧调参数（改完立刻重算），右侧两张曲线（参考点 + 当前工作点），下方是结果/反解/参考表。
"""

import math
import sys
from dataclasses import dataclass

if "--selftest" in sys.argv:
    import matplotlib
    matplotlib.use("Agg")

import numpy as np
from matplotlib.figure import Figure


# ============================================================
# 参数与公式（唯一口径，改这里就等于改公式）
# ============================================================

@dataclass
class Params:
    """全部可调常量，默认值取自《护甲公式计算.txt》的常量区。"""

    synergy: float = 15.0      # C：EA = A + (A*T)/C 的除数，越小韧性放大越强
    player_k: float = 20.0     # K：玩家侧 EA/(EA+K)
    player_min: float = 0.05   # 玩家侧减伤下限（EA>0 时才生效）
    player_max: float = 0.85   # 玩家侧减伤上限
    monster_max: float = 0.80  # 怪物侧减伤上限
    monster_cap: float = 100.0  # 怪物侧 EA 上限（到顶即封顶）


DEFAULT_PARAMS = Params()


def effective_armor(a, t, p):
    """有效护甲 EA = A + (A*T)/C；A=0 时 EA=0。"""
    return a + (a * t) / p.synergy


def player_reduction(ea, p):
    """玩家侧减伤：EA=0 时为 0，否则 clamp(EA/(EA+K), 下限, 上限)。"""
    if ea <= 0:
        return 0.0
    return min(max(ea / (ea + p.player_k), p.player_min), p.player_max)


def monster_reduction(ea, p):
    """怪物侧减伤：min(上限, 上限 * sqrt(EA/EA上限))。"""
    return min(p.monster_max * math.sqrt(max(ea, 0.0) / p.monster_cap), p.monster_max)


def reduction(side, ea, p):
    return monster_reduction(ea, p) if side == "monster" else player_reduction(ea, p)


def solve_ea(side, target, p):
    """反解：给定目标减伤（0~1），返回所需 EA；超过该侧上限返回 None。"""
    if target <= 0:
        return 0.0
    if side == "monster":
        if target >= p.monster_max:
            return None
        return p.monster_cap * (target / p.monster_max) ** 2
    if target >= p.player_max:
        return None
    if target <= p.player_min:
        return 0.0
    return p.player_k * target / (1.0 - target)


def solve_armor(ea, t, p):
    """反解：EA = A*(1 + T/C)  =>  A = EA / (1 + T/C)。"""
    return max(0.0, ea / (1.0 + t / p.synergy))


def armor_needed(side, target, t, p):
    """给定目标减伤与韧性，返回所需护甲 A；无解返回 None。"""
    ea = solve_ea(side, target, p)
    if ea is None:
        return None
    return solve_armor(ea, t, p)


# ============================================================
# 预设点位
# ============================================================

@dataclass
class Preset:
    """一个预设参考点。enabled 就是"子开关"：关掉的项不画在图上（数据仍保留）。"""

    name: str
    armor: float
    toughness: float
    enabled: bool = True


def clone_presets(presets):
    return [Preset(x.name, x.armor, x.toughness, x.enabled) for x in presets]


# 原版套装：数值取自 1.20.1 ArmorMaterials（皮{1,2,3,1}/韧性0、锁链{1,4,5,2}/0、铁{2,5,6,2}/0、
# 金{1,3,5,2}/0、钻石{3,6,8,3}/2、下界合金{3,6,8,3}/3，数组顺序为 靴/护腿/胸甲/头盔，
# 每套韧性按 单件 x 4 计）
DEFAULT_PLAYER_SETS = [
    Preset("皮革", 7, 0),
    Preset("锁链", 12, 0),
    Preset("金", 11, 0),
    Preset("铁", 15, 0),
    Preset("钻石", 20, 8),
    Preset("下界合金", 20, 12),
]

# 怪物按威胁层级（TL）填的预设点位，可在界面里改
DEFAULT_MONSTER_SETS = [
    Preset("TL 0-3", 0, 0),
    Preset("TL 4-7", 5, 0),
    Preset("TL 8-11", 10, 1),
    Preset("TL 12-15", 16, 2),
    Preset("TL 16-20", 24, 4),
    Preset("首领", 40, 10),
]


# ============================================================
# 绘图（GUI 与自检共用）
# ============================================================

def matplotlib_rc():
    import matplotlib
    matplotlib.rcParams["font.sans-serif"] = ["Microsoft YaHei", "SimHei", "DejaVu Sans"]
    matplotlib.rcParams["axes.unicode_minus"] = False


def _decorate(ax, xmax):
    ax.set_xlabel("有效护甲 EA", fontsize=9)
    ax.set_ylabel("减伤 (%)", fontsize=9)
    ax.set_xlim(0, xmax)
    ax.set_ylim(0, 92)
    ax.grid(alpha=0.3)
    ax.tick_params(labelsize=8)
    ax.legend(loc="lower right", fontsize=8)


def build_figure(p, player_presets, monster_presets, current=None, side="player", sweep=120.0,
                 show_points=True, show_labels=False, show_player=True, show_monster=True):
    """返回一张 Figure：左玩家、右怪物。

    预设点位受三级开关控制：show_points（总开关）→ show_player/show_monster（组开关）
    → 每个 Preset.enabled（子开关）；show_labels 决定是否画文字标签（关掉只留圆点，画面干净）。
    """
    matplotlib_rc()
    fig = Figure(figsize=(6.6, 3.0), dpi=100)
    axes = [fig.add_subplot(1, 2, 1), fig.add_subplot(1, 2, 2)]
    ea_axis = np.linspace(0, sweep, 400)

    def draw_presets(ax, presets, reduction_fn, dot_color, text_color, group_on):
        if not (show_points and group_on):
            return
        for idx, preset in enumerate(presets):
            if not preset.enabled:
                continue
            ea = effective_armor(preset.armor, preset.toughness, p)
            if ea > sweep * 1.2:
                continue
            red = reduction_fn(ea, p) * 100
            ax.scatter([ea], [red], color=dot_color, s=40, zorder=5)
            if show_labels:
                # 交错偏移，尽量别让相邻标签叠在一起
                offset = (6, 6) if idx % 2 == 0 else (6, -14)
                ax.annotate("%s %.1f%%" % (preset.name, red), xy=(ea, red), xytext=offset,
                            textcoords="offset points", fontsize=7.5, color=text_color)

    ax = axes[0]
    ax.plot(ea_axis, [player_reduction(e, p) * 100 for e in ea_axis],
            color="#2E86AB", linewidth=2.4, label="玩家减伤")
    ax.axhline(p.player_max * 100, color="gray", linestyle="--", linewidth=1,
               label="上限 %.0f%%" % (p.player_max * 100))
    ax.axhline(p.player_min * 100, color="gray", linestyle=":", linewidth=1,
               label="下限 %.0f%%" % (p.player_min * 100))
    draw_presets(ax, player_presets, player_reduction, "#E63946", "#B02A37", show_player)
    ax.set_title("玩家侧（C=%.1f, K=%.0f）" % (p.synergy, p.player_k), fontsize=10)
    _decorate(ax, ea_axis[-1])

    ax = axes[1]
    ax.plot(ea_axis, [monster_reduction(e, p) * 100 for e in ea_axis],
            color="#A23B72", linewidth=2.4, label="怪物减伤")
    ax.axhline(p.monster_max * 100, color="gray", linestyle="--", linewidth=1,
               label="上限 %.0f%%" % (p.monster_max * 100))
    ax.axvline(p.monster_cap, color="gray", linestyle=":", linewidth=1,
               label="EA 上限 %.0f" % p.monster_cap)
    draw_presets(ax, monster_presets, monster_reduction, "#F18F01", "#A5661B", show_monster)
    ax.set_title("怪物侧（%.0f%% x sqrt(EA/%.0f)）" % (p.monster_max * 100, p.monster_cap), fontsize=10)
    _decorate(ax, ea_axis[-1])

    if current is not None:
        a, t = current
        ea = effective_armor(a, t, p)
        red = reduction(side, ea, p) * 100
        target_ax = axes[1] if side == "monster" else axes[0]
        target_ax.scatter([ea], [red], marker="*", s=240, color="#1B998B", zorder=6,
                          label="当前 A=%.1f T=%.1f -> %.1f%%" % (a, t, red))
        target_ax.legend(loc="lower right", fontsize=8)

    fig.tight_layout()
    return fig


# ============================================================
# 自检模式（无窗口，命令行核验用）
# ============================================================

def run_selftest(p, player_presets, monster_presets, out_png="armor_calc_selftest.png"):
    print("公式：EA = A + (A*T)/%.2f ；玩家 = clamp(EA/(EA+%.2f), %.0f%%, %.0f%%)（EA=0 时为 0%%）；"
          "怪物 = min(%.0f%%, %.0f%%*sqrt(EA/%.0f))"
          % (p.synergy, p.player_k, p.player_min * 100, p.player_max * 100,
             p.monster_max * 100, p.monster_max * 100, p.monster_cap))

    print("\n[玩家侧]（开关列 = 是否画在图上）")
    print("%-10s %8s %8s %10s %10s  %s" % ("套装", "A", "T", "EA", "减伤", "开关"))
    for preset in player_presets:
        ea = effective_armor(preset.armor, preset.toughness, p)
        print("%-10s %8.2f %8.2f %10.2f %9.2f%%  %s"
              % (preset.name, preset.armor, preset.toughness, ea,
                 player_reduction(ea, p) * 100, "开" if preset.enabled else "关"))

    print("\n[怪物侧]（开关列 = 是否画在图上）")
    print("%-10s %8s %8s %10s %10s  %s" % ("层级", "A", "T", "EA", "减伤", "开关"))
    for preset in monster_presets:
        ea = effective_armor(preset.armor, preset.toughness, p)
        print("%-10s %8.2f %8.2f %10.2f %9.2f%%  %s"
              % (preset.name, preset.armor, preset.toughness, ea,
                 monster_reduction(ea, p) * 100, "开" if preset.enabled else "关"))

    print("\n[EA -> 减伤 对照]")
    print("%6s %10s %10s" % ("EA", "玩家", "怪物"))
    for ea in range(0, 121, 10):
        print("%6d %9.2f%% %9.2f%%" % (ea, player_reduction(ea, p) * 100, monster_reduction(ea, p) * 100))

    reach = p.player_k * p.player_max / (1 - p.player_max)
    print("\n[边界]")
    print("  EA=0       玩家 %.2f%% / 怪物 %.2f%%" % (player_reduction(0, p) * 100, monster_reduction(0, p) * 100))
    print("  玩家侧封顶 EA=%.2f（护甲 %.2f，韧性 0）" % (reach, reach))
    print("  怪物侧封顶 EA=%.2f" % p.monster_cap)

    print("\n[反解示例]（韧性按 8）")
    for side, target in (("player", 0.60), ("player", 0.75), ("monster", 0.50), ("monster", 0.80)):
        need = armor_needed(side, target, 8, p)
        label = "玩家" if side == "player" else "怪物"
        if need is None:
            print("  %s侧 %.0f%% -> 超过该侧上限，无解" % (label, target * 100))
        else:
            print("  %s侧 %.0f%% -> EA %.2f，需要护甲 A=%.2f（T=8）"
                  % (label, target * 100, effective_armor(need, 8, p), need))

    # 自检出的图带标签，方便直接当参考图看
    fig = build_figure(p, player_presets, monster_presets, show_labels=True)
    fig.savefig(out_png, dpi=140, bbox_inches="tight")
    print("\n曲线已导出：" + out_png)


# ============================================================
# GUI
# ============================================================

def run_gui():
    import tkinter as tk
    from tkinter import ttk, filedialog
    from matplotlib.backends.backend_tkagg import FigureCanvasTkAgg

    state = {
        "params": Params(),
        "player_presets": clone_presets(DEFAULT_PLAYER_SETS),
        "monster_presets": clone_presets(DEFAULT_MONSTER_SETS),
    }
    const_names = ("synergy", "player_k", "player_min", "player_max", "monster_max", "monster_cap")
    const_labels = {
        "synergy": "协同除数 C",
        "player_k": "玩家 K",
        "player_min": "玩家下限 %",
        "player_max": "玩家上限 %",
        "monster_max": "怪物上限 %",
        "monster_cap": "怪物 EA 上限",
    }

    root = tk.Tk()
    root.title("护甲减伤计算器")
    # 默认尺寸按 1080p 也放得下设计（小屏可自行拖大或最大化）；
    # minsize 必须显式给一个很小的值，否则 Tk 会把"内容自然尺寸"当最小尺寸，窗口会被撑到屏幕外
    root.geometry("1060x640")
    root.minsize(320, 240)
    root.columnconfigure(1, weight=1)
    root.rowconfigure(0, weight=1)

    side_var = tk.StringVar(value="player")
    armor_var = tk.DoubleVar(value=20.0)
    tough_var = tk.DoubleVar(value=8.0)
    damage_var = tk.StringVar(value="30")
    target_var = tk.StringVar(value="60")
    status_var = tk.StringVar(value="就绪")
    const_vars = {name: tk.StringVar(value="%g" % getattr(state["params"], name)) for name in const_names}
    # 预设点位的三级开关：总开关 -> 分组开关 -> 每个点位自己的勾选（在"参考点..."里）
    show_points_var = tk.BooleanVar(value=True)
    show_labels_var = tk.BooleanVar(value=False)   # 默认不画文字标签，避免遮挡曲线
    show_player_var = tk.BooleanVar(value=True)
    show_monster_var = tk.BooleanVar(value=True)

    # ---------- 左栏：输入与常量 ----------
    left = ttk.Frame(root, padding=10)
    left.grid(row=0, column=0, sticky="nsw")

    def row(label_text):
        """一行："标签 + 控件"，标签固定宽度保证纵向对齐。"""
        line = ttk.Frame(left)
        line.pack(anchor="w", fill="x", pady=3)
        ttk.Label(line, text=label_text, width=15, anchor="w").pack(side="left")
        return line

    line = row("侧别")
    ttk.Radiobutton(line, text="玩家", value="player", variable=side_var).pack(side="left")
    ttk.Radiobutton(line, text="怪物", value="monster", variable=side_var).pack(side="left", padx=(8, 0))

    line = row("护甲 A")
    ttk.Scale(line, from_=0, to=120, variable=armor_var, orient="horizontal", length=130).pack(side="left")
    ttk.Spinbox(line, from_=0, to=120, increment=1.0, textvariable=armor_var, width=7).pack(side="left", padx=(6, 0))

    line = row("韧性 T")
    ttk.Scale(line, from_=0, to=40, variable=tough_var, orient="horizontal", length=130).pack(side="left")
    ttk.Spinbox(line, from_=0, to=40, increment=1.0, textvariable=tough_var, width=7).pack(side="left", padx=(6, 0))

    line = row("原始伤害")
    ttk.Entry(line, textvariable=damage_var, width=10).pack(side="left")
    ttk.Label(line, text="（可空）").pack(side="left")

    line = row("反解目标减伤 %")
    ttk.Entry(line, textvariable=target_var, width=10).pack(side="left")

    line = row("预设点位")
    ttk.Checkbutton(line, text="总开关", variable=show_points_var).pack(side="left")
    ttk.Checkbutton(line, text="标签", variable=show_labels_var).pack(side="left", padx=(8, 0))
    line = row("")
    ttk.Checkbutton(line, text="玩家侧", variable=show_player_var).pack(side="left")
    ttk.Checkbutton(line, text="怪物侧", variable=show_monster_var).pack(side="left", padx=(8, 0))

    ttk.Separator(left, orient="horizontal").pack(fill="x", pady=8)
    ttk.Label(left, text="常量（改完立即生效）", font=("", 10, "bold")).pack(anchor="w")
    const_box = ttk.Frame(left)
    const_box.pack(anchor="w", fill="x")
    for i, name in enumerate(const_names):
        r, c = divmod(i, 2)          # 两列三行，省纵向空间
        ttk.Label(const_box, text=const_labels[name], width=13, anchor="w").grid(
            row=r, column=c * 2, sticky="w", pady=2)
        ttk.Entry(const_box, textvariable=const_vars[name], width=7).grid(
            row=r, column=c * 2 + 1, sticky="w", padx=(0, 10))

    btns = ttk.Frame(left)
    btns.pack(anchor="w", pady=(10, 0))
    # 2x2 摆放：一行四个按钮会把整窗撑宽，超出小屏
    ttk.Button(btns, text="参考点...", command=lambda: edit_sets()).grid(row=0, column=0, padx=(0, 6), pady=3)
    ttk.Button(btns, text="重置常量", command=lambda: reset_consts()).grid(row=0, column=1, pady=3)
    ttk.Button(btns, text="导出 PNG", command=lambda: export_png()).grid(row=1, column=0, padx=(0, 6), pady=3)
    ttk.Button(btns, text="导出 CSV", command=lambda: export_csv()).grid(row=1, column=1, pady=3)

    # ---------- 右栏：曲线 + 结果 ----------
    right = ttk.Frame(root, padding=(0, 10, 10, 6))
    right.grid(row=0, column=1, sticky="nsew")
    canvas_holder = ttk.Frame(right)
    canvas_holder.pack(fill="x")
    result = tk.Text(right, height=11, width=68, wrap="none", font=("Consolas", 9))
    result.pack(fill="both", expand=True, pady=(8, 0))
    ttk.Label(right, textvariable=status_var, foreground="#555").pack(anchor="w")
    canvas = {"widget": None, "figure": None}

    # ---------- 逻辑 ----------
    def collect_params():
        p = state["params"]
        for name, var in const_vars.items():
            try:
                value = float(var.get().strip())
            except ValueError:
                status_var.set("常量「%s」不是数字，暂时沿用上一个值" % const_labels[name])
                continue
            if name in ("player_min", "player_max", "monster_max") and value > 1.0:
                value /= 100.0
            setattr(p, name, value)
        if p.synergy <= 0:
            p.synergy = 1e-6
        return p

    def refresh():
        p = collect_params()
        side = side_var.get()
        try:
            a = float(armor_var.get())
            t = float(tough_var.get())
        except (ValueError, tk.TclError):
            return
        ea = effective_armor(a, t, p)
        red = reduction(side, ea, p)
        label = "玩家" if side == "player" else "怪物"

        fig = build_figure(p, state["player_presets"], state["monster_presets"], current=(a, t), side=side,
                           show_points=show_points_var.get(), show_labels=show_labels_var.get(),
                           show_player=show_player_var.get(), show_monster=show_monster_var.get())
        if canvas["widget"] is not None:
            canvas["widget"].get_tk_widget().destroy()
        canvas["widget"] = FigureCanvasTkAgg(fig, master=canvas_holder)
        canvas["widget"].draw()
        canvas["widget"].get_tk_widget().pack(fill="both", expand=True)
        canvas["figure"] = fig

        lines = ["【当前】%s侧  A=%.2f  T=%.2f" % (label, a, t),
                 "  有效护甲 EA = %.2f" % ea,
                 "  减伤 = %.2f%%" % (red * 100)]
        dmg = damage_var.get().strip()
        if dmg:
            try:
                d = float(dmg)
                lines.append("  原始伤害 %.2f -> 实际 %.2f（减免 %.2f）" % (d, d * (1 - red), d * red))
            except ValueError:
                lines.append("  原始伤害不是数字，已忽略")
        lines.append("")
        tgt = target_var.get().strip()
        if tgt:
            try:
                target = float(tgt)
                if target > 1.0:
                    target /= 100.0
                need = armor_needed(side, target, t, p)
                if need is None:
                    lines.append("【反解】%s侧 %.2f%% 超过该侧上限，无解" % (label, target * 100))
                else:
                    lines.append("【反解】%s侧做到 %.2f%% 需要 A=%.2f（韧性固定 %.2f）"
                                 % (label, target * 100, need, t))
            except ValueError:
                lines.append("【反解】目标减伤不是数字，已忽略")
        lines.append("")
        lines.append("【玩家侧预设】（[x]=画在图上，可在“参考点...”里改）")
        for preset in state["player_presets"]:
            sea = effective_armor(preset.armor, preset.toughness, p)
            lines.append("  [%s] %-10s A=%-5g T=%-5g EA=%-8.2f 减伤=%.2f%%"
                         % ("x" if preset.enabled else " ", preset.name, preset.armor, preset.toughness,
                            sea, player_reduction(sea, p) * 100))
        lines.append("")
        lines.append("【怪物侧预设】")
        for preset in state["monster_presets"]:
            sea = effective_armor(preset.armor, preset.toughness, p)
            lines.append("  [%s] %-10s A=%-5g T=%-5g EA=%-8.2f 减伤=%.2f%%"
                         % ("x" if preset.enabled else " ", preset.name, preset.armor, preset.toughness,
                            sea, monster_reduction(sea, p) * 100))

        result.delete("1.0", "end")
        result.insert("1.0", "\n".join(lines))
        if not status_var.get().startswith("常量"):
            status_var.set("EA = A + (A*T)/%.2f ；玩家 %.0f%%~%.0f%% ；怪物上限 %.0f%%（EA>=%.0f 封顶）"
                           % (p.synergy, p.player_min * 100, p.player_max * 100,
                              p.monster_max * 100, p.monster_cap))

    def reset_consts():
        for name in const_names:
            const_vars[name].set("%g" % getattr(DEFAULT_PARAMS, name))
        status_var.set("常量已重置为默认值")
        refresh()

    def edit_sets():
        win = tk.Toplevel(root)
        win.title("预设点位（左侧勾选 = 画在图上）")
        win.geometry("470x520")

        def make_table(parent, presets, title):
            """一行一个点位：勾选框 + 名称 + A + T。"""
            ttk.Label(parent, text=title, font=("", 10, "bold")).pack(anchor="w", padx=8, pady=(8, 2))
            holder = ttk.Frame(parent)
            holder.pack(fill="x", padx=8)
            head = ttk.Frame(holder)
            head.grid(row=0, column=0, sticky="w")
            for col, text in enumerate(("显示", "名称", "护甲 A", "韧性 T")):
                ttk.Label(head, text=text, width=(5, 12, 8, 8)[col]).grid(row=0, column=col, sticky="w")
            rows = []

            def add_row(preset=None):
                r = len(rows) + 1
                on = tk.BooleanVar(value=True if preset is None else preset.enabled)
                name = tk.StringVar(value="新点位" if preset is None else preset.name)
                a_var = tk.StringVar(value="0" if preset is None else "%g" % preset.armor)
                t_var = tk.StringVar(value="0" if preset is None else "%g" % preset.toughness)
                ttk.Checkbutton(holder, variable=on).grid(row=r, column=0, sticky="w")
                ttk.Entry(holder, textvariable=name, width=12).grid(row=r, column=1, sticky="w")
                ttk.Entry(holder, textvariable=a_var, width=8).grid(row=r, column=2, sticky="w")
                ttk.Entry(holder, textvariable=t_var, width=8).grid(row=r, column=3, sticky="w")
                rows.append((on, name, a_var, t_var))

            for preset in presets:
                add_row(preset)
            ttk.Button(parent, text="加一行", command=lambda: add_row()).pack(anchor="w", padx=8, pady=4)
            return rows

        player_rows = make_table(win, state["player_presets"], "玩家侧预设")
        monster_rows = make_table(win, state["monster_presets"], "怪物侧预设")

        def apply_sets():
            def collect(rows):
                out = []
                for on, name, a_var, t_var in rows:
                    try:
                        out.append(Preset(name.get().strip() or "未命名",
                                          float(a_var.get()), float(t_var.get()), bool(on.get())))
                    except ValueError:
                        continue
                return out

            new_player = collect(player_rows)
            new_monster = collect(monster_rows)
            if new_player:
                state["player_presets"] = new_player
            if new_monster:
                state["monster_presets"] = new_monster
            win.destroy()
            refresh()

        ttk.Button(win, text="保存并重算", command=apply_sets).pack(pady=10)

    def export_png():
        path = filedialog.asksaveasfilename(defaultextension=".png", filetypes=[("PNG 图片", "*.png")],
                                            initialfile="armor_reduction_curves.png")
        if not path:
            return
        if canvas["figure"] is None:
            refresh()
        canvas["figure"].savefig(path, dpi=140, bbox_inches="tight")
        status_var.set("已导出曲线：" + path)

    def export_csv():
        path = filedialog.asksaveasfilename(defaultextension=".csv", filetypes=[("CSV 表格", "*.csv")],
                                            initialfile="armor_reduction.csv")
        if not path:
            return
        p = collect_params()
        try:
            a = float(armor_var.get())
            t = float(tough_var.get())
        except (ValueError, tk.TclError):
            a, t = 0.0, 0.0
        limit = max(a, 40.0)
        step = max(0.5, limit / 120.0)
        with open(path, "w", encoding="utf-8-sig", newline="") as fh:
            fh.write("A,T,EA,player_reduction,monster_reduction\n")
            x = 0.0
            while x <= limit + 1e-9:
                ea = effective_armor(x, t, p)
                fh.write("%.3f,%.3f,%.3f,%.5f,%.5f\n"
                         % (x, t, ea, player_reduction(ea, p), monster_reduction(ea, p)))
                x += step
        status_var.set("已导出 CSV：" + path)

    # 任何输入变化都重算（数据量很小，直接重画最省心）
    for var in (side_var, armor_var, tough_var, damage_var, target_var,
                show_points_var, show_labels_var, show_player_var, show_monster_var):
        var.trace_add("write", lambda *_: refresh())
    for var in const_vars.values():
        var.trace_add("write", lambda *_: refresh())

    refresh()
    root.mainloop()


def main():
    if "--selftest" in sys.argv:
        run_selftest(DEFAULT_PARAMS, DEFAULT_PLAYER_SETS, DEFAULT_MONSTER_SETS)
        return
    try:
        run_gui()
    except ImportError as exc:
        print("启动失败：%s" % exc)
        print("请确认已安装 matplotlib（python -m pip install matplotlib），且这套 Python 带 tkinter。")


if __name__ == "__main__":
    main()
