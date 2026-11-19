# Summy Threat

Minecraft **1.20.1**（Forge）的“属性与怪物机制改造”模组。当前已实现属性上限抬升、护甲减伤公式替换、
TL 数值缩放、PCF/CDF、卓越者与 8 个专属原型、区域首领、Boss 硬编码攻击接管、TL 魔法抗性，
以及卓越精华装备升级与星渊棱晶双槽内部预览。Boss 常规能力与传奇体系仍在开发中。作者：**Summy**。

**当前状态（B15 阶段 3 内部预览）**：TL / PCF / CDF、6 个预设档 + 手动 `free` 档、TL 能力、8 个卓越者原型、
区域首领与基础传奇抗性、末影龙 / 凋灵 / 古守硬编码攻击接管、穿甲/削甲、KubeJS 高可配 API、
Mod Boss 反推、卓越者精华 / 星渊棱晶掉落、A37 TL 魔法抗性、两段卓越升级、棱晶双槽、
34 项星渊词条与神化 / 匠魂 / Tetra 数据互操作已实现。
SR 固定伤害兼容层已在 SR 2.0 转正后从 ST 移除，增伤与 SE 死神祝福统一由 SR 接管。
Boss 常规能力、传奇动作/升级抗性仍待完成；B15/A35 处于跨模组验收中。内部 RC `0.1.0` 已归档，但尚未推送远端。

| 目录 | 平台 | 版本 | 说明 |
| --- | --- | --- | --- |
| [`forge/`](forge/README.md) | Forge 47.4.23（ForgeGradle 6 + MixinExtras） | `0.1.0` | 当前实现：属性上限、护甲公式、穿甲/削甲、TL/PCF/CDF、卓越者核心、Boss 攻击接管、区域首领、TL 魔法抗性与卓越装备内部预览 |

KubeJS 集成见 [`docs/kubejs.md`](docs/kubejs.md)。

## 卓越装备与星渊棱晶（内部预览）

- `summy_threat:eximus_essence` 保持原 ID，用于两段卓越升级、装备刷新与 SR 慈善包兼容掉落。
- `summy_threat:abyssal_star_prism` 为 Boss 专属高阶材料；过主手揭示三系潜在词条，支持标准/高级刷新。
- 升级 I 开放槽位 1，升级 II 开放槽位 2；镶嵌自动选择最小空槽，拆卸始终同步清空双槽。
- 盔甲 12 项、近战 11 项、远程 11 项词条均已接入；武器伤害快照、附伤清帧、共享标记、追猎与轮转状态已实现。
- 神化 7.4.8 的双向 NBT 保留、匠魂 3.12.1.231 与 Tetra 6.17.0 可选桥接已通过隔离矩阵。
- SE 1.4.2 与神化 7.4.8 分别通过兼容验收；两者同装时存在其自身铁砧 Mixin 冲突，不属于 ST 兼容层错误。

## 构建

需要 **JDK 17**。

```bash
cd forge
./gradlew build
./gradlew archiveReleaseJar  # 显式归档重混淆成品
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
