# Summy Threat

Minecraft **1.20.1**（Forge）的“属性与怪物机制改造”模组。当前已实现属性上限抬升、护甲减伤公式替换、
TL 数值缩放、PCF/CDF、卓越者与 8 个专属原型、回响下界合金与玩家卓越护盾；完整刷新链路、
领袖 / Boss / 超级 Boss 专属能力池、穿甲削甲与 SR 背叛仍在开发中。作者：**Summy**。

**当前状态（v2.38）**：TL / PCF / CDF、6 个预设档 + 手动 free 档、卓越护盾、TL 能力、8 个卓越者原型、
回响下界合金四件、玩家护盾 HUD、SR 固定伤害兼容（默认关闭）已实现；领袖 / Boss / 超级 Boss 能力池、
龙 / 凋灵 / 古守攻击接管、穿甲削甲、SR 背叛、卓越者精华获取与回响套正式贴图仍待完成。
SR 1.8.7 起 `isDivine` 与 `isExactDamage` 都会被 SR 跳过，ST 对 7 项固定伤害统一补核心乘区，
含思想/咒印、玄秘魔眼与恶魔王冠条件层；
SE 复仇保持近战专属，SRA 凋亡保留受伤 +10% 但排除固定伤害的推条/斩杀副作用。

| 目录 | 平台 | 版本 | 说明 |
| --- | --- | --- | --- |
| [`forge/`](forge/README.md) | Forge 47.4.10（ForgeGradle 6 + MixinExtras） | `0.1.0` | 当前实现：属性上限、护甲公式、TL/PCF/CDF、卓越者核心、回响套、SR 固定伤害兼容与测量命令 |

## 构建

需要 **JDK 17**。

```bash
cd forge
./gradlew build
# 产物会复制到 JAR/summy-threat-forge/summy-threat-0.1.0.jar
```

`gradlew build` 自带两道构建期保障：

- `verifyMixinTargets`：校验 Mixin 注解里引用的 Minecraft 类真实存在、注入目标都能在 refmap 里查到映射
  ——用来拦住"dev 环境宽松匹配掩盖错误、到生产才崩"这类问题。
- 离线依赖：`forge/libs-m2/` 内置 Mixin 注解处理器，无需访问被墙的仓库也能编译。

## 设计期工具

都在 `forge/docs/` 下（不算产物、Gradle 不编译）：

- `护甲公式计算.txt`：护甲减伤公式与参数的规格说明（唯一口径）。
- `护甲减伤计算器.py`：图形界面计算器（tkinter + matplotlib），可实时调参看曲线、反解与导出。
- `ArmorCalc.java`：同一口径的命令行计算器，供脚本化或无 Python 环境使用。
