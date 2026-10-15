# Summy Threat

Minecraft **1.20.1**（Forge）的"属性与怪物机制改造"模组：先把原版被硬编码封顶的属性救回来——
`generic.armor` 与 `generic.max_health` 的上限改由配置驱动（默认 100 / 100000，可关闭回到原版
的 30 / 1024）；后续接入护甲减伤公式替换与怪物刷新/属性强化。作者：**Summy**。

| 目录 | 平台 | 版本 | 说明 |
| --- | --- | --- | --- |
| [`forge/`](forge/README.md) | Forge 47.4.10（ForgeGradle 6 + MixinExtras） | `0.1.0` | 当前唯一实现：属性上限抬升，含设计期计算器（`docs/`） |

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
