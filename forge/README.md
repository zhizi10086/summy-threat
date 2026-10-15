# Summy Threat

Forge 1.20.1 的"属性与怪物机制改造"模组，承载三块内容：

1. **属性上限抬升**（本次已实现）
2. 护甲减伤公式替换（后续，等公式文本定稿）
3. 怪物刷新逻辑与怪物属性强化（后续）

这套方案原本挂在 Summy Enchantment（原 Enchantment Reforged）上，现独立成模组；**Summy Enchantment 与 Summy Reliquary 的代码不动。**

- 仓库：<https://github.com/zhizi10086/summy-threat>
- 身份：`mod_id=summy_threat`、包 `com.summy.threat`、`archives_base_name=summy-threat`、版本 `0.1.0`、许可 MIT、作者 Summy

## 环境

| 项目 | 版本 / 说明 |
| --- | --- |
| Minecraft / Forge | 1.20.1 / 47.4.10（`loaderVersion = "[47,)"`） |
| 构建 | ForgeGradle 6 + MixinGradle 0.7.38 + MixinExtras 0.5.5（jar-in-jar）+ 官方（Mojang）映射 |
| Gradle / JDK | Gradle 8.8（wrapper）；JDK 17（由全局 `~/.gradle/gradle.properties` 的 `org.gradle.java.home` 指定） |
| 构建命令 | `gradlew build` |
| 产物 | `build/libs/summy-threat-0.1.0.jar`（薄包）与 `summy-threat-0.1.0-all.jar`（含嵌套 MixinExtras）；可发布的是后者，构建结束后自动复制并重命名为 `../JAR/summy-threat-forge/summy-threat-0.1.0.jar` |

## 本次实现：属性上限抬升

原版把属性值夹在 `RangedAttribute` 自带的 min/max 上（1.20.1 实测：`generic.armor` = 0~30、`generic.max_health` = 0~1024），超出部分会被静默丢弃。本模组只抬**上限**、不动下限：

- 走 Mixin accessor 改写 `RangedAttribute#maxValue`（`src/main/java/com/summy/threat/mixin/RangedAttributeAccessor.java`）。不能改用反射：生产环境字段名是 SRG（`maxValue` 对应 `f_22308_`），按字面名反射在正式包里必然失效。
- 只动 `generic.armor` 与 `generic.max_health` 两个属性；`generic.armor_toughness`(20)、`generic.attack_damage`(2048) 保持原样。
- 改的是属性注册表里的**单例对象**，因此对玩家、原版怪、模组怪一视同仁 —— 后续"怪物属性强化"可以直接受益。
- **只抬不压**：应用值取 `max(原版上限, 配置值)`，配置项的下界也钉在原版上限上；关闭总开关会把上限写回 30 / 1024（运行期可逆）。
- 生效时机由 `ModConfigEvent`（MOD 总线）统一驱动，一次覆盖"世界首次加载 / 客户端收到服务端同步 / 运行期重载"三种情形。

## 配置

Forge 原生 **SERVER 档**（不是自研 JSON），由 Forge 自动同步给客户端，双端口径天然一致：

| 键 | 类型 | 默认 | 范围 |
| --- | --- | --- | --- |
| `attribute_caps.enable_attribute_cap_override` | boolean | `true` | 无 |
| `attribute_caps.armor_max_value` | double | `100.0` | 30.0 ~ 1000000.0 |
| `attribute_caps.max_health_max_value` | double | `100000.0` | 1024.0 ~ 10000000.0 |

文件位置：存档内 `serverconfig/summy_threat-server.toml`（SERVER 档按世界存放）。

Forge 1.20.1 **没有** `defaultconfigs/` 目录机制（那是 NeoForge 的特性，本机 1.21.1 工程里才看得到）。若要让多个世界共用同一套值，只能把已生成的 `summy_threat-server.toml` 复制进各个世界的 `serverconfig/`。

### 与 AttributeFix 的关系

AttributeFix 只做同一件事（抬高 `RangedAttribute` 的 min/max，默认把 5 个原版属性抬到 1000000），**不改变减伤公式**（原版护甲减伤另有"有效护甲最多 20 点 / 最多减 80%"的夹取，AttributeFix 碰不到）。两者改的是同一个字段、谁后应用谁说了算，所以检测到 `attributefix` 已加载时本模组**直接让位**并打一条 WARN。

结论：不需要把 AttributeFix 设为前置，也不建议同时安装。

## 构建期自检

`verifyMixinTargets`（挂在 `check` 上，`gradlew build` 自动跑）做两道检查，任何一处失败都会中断构建：

1. mixin 注解里引用的每个 Minecraft 类都真实存在；
2. 每个 `method` / `@At target` 字符串都能在生成的 refmap 里查到映射。

Summy Enchantment 曾因为注解里的包路径写错、被 dev 环境的宽松匹配静默吞掉，直到生产才崩；这道自检就是那个教训的产物。

若日后 mixin 需要注入**接口继承成员**（例如 `Level#addFreshEntity`、`Component#translatable`），注解处理器在 tsrg2 里定位不到它们，需要照 Summy Enchantment 的做法补 `patchRefmap` / `patchSrgForDev` 两个任务（见 `summy-enchantment-forge/build.gradle`）。当前没有这类注入，故未搬，避免空转。

## 开发期运行要点

- `runClient` / `runServer` 带 `mixin.env.remapRefMap=true` 与 `mixin.env.refMapRemappingFile=build/createSrgToMcp/output.srg`：dev 环境里依赖模组自带的 refmap 是 SRG 名，不重映射时任何"自带 mixin 的依赖模组"都会在装载期抛 `NoSuchFieldError`（实测 AttributeFix 正是如此）。生产环境是 SRG 运行，不需要这两项。
- 用第三方**生产 jar** 做冒烟时不能直接丢进 `run/mods`（它的普通类里硬引用 SRG 字段，dev 下会崩），要用 `fg.deobf` 生成 dev 副本：把 jar 放进 `libs/`（已在 `.gitignore` 里排除），执行 `gradlew copyAttributeFixDevJar` 复制到 `run/mods`。
- dev 不接标准输入：`gradlew runServer` 控制台**收不到**手敲的指令（Gradle 的 JavaExec 未接 stdin）。要跑指令请在 `run/server.properties` 里开 RCON，或用客户端连接。

## 后续路线图

### ② 护甲减伤公式替换（等公式文本）

设计期工具（放在 `docs/` 下，不算产物、Gradle 不编译）：

- `docs/护甲公式计算.txt`：公式与参数的**规格说明**（唯一口径，不含代码）。
- `docs/护甲减伤计算器.py`：图形界面计算器（Python + tkinter + matplotlib）——左侧调参与常量、
  右侧实时曲线与预设点位（总开关 / 分组开关 / 逐项勾选三级控制，文字标签默认关闭以免遮挡）、
  下方单点结果 / 反解 / 预设表，可导出 PNG 与 CSV；加 `--selftest` 走无窗口自检。
- `docs/ArmorCalc.java`：同一口径的 Java 命令行版本（`java docs/ArmorCalc.java --help`），
  支持单次计算、参考表、扫表、反解、CSV / PNG / SVG 输出，供脚本化或没有 Python 的环境使用。

三者口径必须一致：**改公式先改 txt，再同步 py 与 java**。

- 挂点：`LivingEntity#getDamageAfterArmorAbsorb` 内对 `CombatRules.getDamageAfterAbsorb` 的那次调用，用 MixinExtras 的 `@ModifyExpressionValue` 包住 —— `hurtArmor`（护甲耐久）与 `bypasses_armor` 闸门自动保留。
- 顺序天然是"护甲 → 保护附魔 → 其它"，`getDamageAfterMagicAbsorb` 与 `hurt` 维持原样。
- 注意：原版对"有效护甲"另有最多 20 点的夹取，换公式时若想突破 80% 天花板，必须在这层一并处理。

### ③ 怪物刷新与属性强化

- 刷新链路（Forge 官方事件）：`LevelEvent.PotentialSpawns`（候选怪与权重）→ `SpawnPlacementRegisterEvent` / `MobSpawnEvent.SpawnPlacementCheck`（刷新规则）→ `MobSpawnEvent.PositionCheck`（落点）→ `MobSpawnEvent.FinalizeSpawn`（初始化时加属性/标记）→ `EntityJoinLevelEvent`（兜底）。
- 属性：所有生物默认就带 `MAX_HEALTH` / `ATTACK_DAMAGE` / `ARMOR` / `ARMOR_TOUGHNESS`，可直接挂修饰符；注意永久修饰符会随 NBT 存档，重复施加会叠加，需要"只挂一次"的标记。
- "不穿盔甲也生效保护附魔"：用 `@ModifyExpressionValue` 包住 `EnchantmentHelper.getDamageProtection` 的返回值追加虚拟保护点数 —— 这样自动继承原版的 `bypasses_effects` / `bypasses_enchantments` 闸门与 20 点上限。不要挂在 `getDamageAfterMagicAbsorb` 的 RETURN 上，否则会给"无视附魔"的伤害也减伤。

## 身份（沿用 Summy Enchantment 的命名惯例）

| 项目 | 取值 |
| --- | --- |
| mod_id | `summy_threat` |
| 包名 / maven_group | `com.summy.threat` |
| 归档名 archives_base_name | `summy-threat` |
| 显示名 | `Summy Threat` |
| 工程目录 | `summy-threat-forge/` |
| mixin 配置 / refmap | `summy_threat.mixins.json` / `summy-threat.refmap.json` |
| 资源命名空间 | `assets/summy_threat/`，配置翻译键 `summy_threat.configuration.*` |
| 发布目录 | `../JAR/summy-threat-forge/` |
| 版本 / 许可 / 作者 | `0.1.0` / MIT / Summy |
| 仓库 | <https://github.com/zhizi10086/summy-threat>（工作副本 `_github/summy-threat`，其中 `forge/` 对应本工程） |

## 验证记录（本次）

2026-10-15 实测（Forge 47.4.10 / JDK 17，指令经 RCON 下发）：

| 项 | 结果 |
| --- | --- |
| `gradlew build` | 成功；`verifyMixinTargets` 通过；refmap 生成 `RangedAttributeAccessor → "maxValue": "f_22308_:D"`（与预期 SRG 名一致，无需 `patchRefmap`） |
| 启动抬升 | `[Summy Threat] 属性上限已调整：generic.armor 30.0 -> 100.0` / `generic.max_health 1024.0 -> 100000.0`，零注入失败 |
| 属性值实测（开关开） | 盔甲座 `generic.armor` 设 40 → `get` 返回 **40.0**；`generic.max_health` 设 5000 → 返回 **5000.0**（原版会夹到 30 / 1024） |
| 自定义上限 | 配置 `armor_max_value = 1000` 后重启：日志 `30.0 -> 1000.0`，设 999 → 返回 **999.0** |
| 开关关闭回退 | `enable_attribute_cap_override = false` 后重启：不写上限；设 40 → 返回 **30.0**、设 5000 → 返回 **1024.0**（夹取回到原版） |
| AttributeFix 互斥 | 装入 AttributeFix 21.0.5（dev 用 `fg.deobf` 副本）：日志出现本模组 WARN、**不再**写上限；AttributeFix 生效（设 5000 → 返回 **5000.0**，即它的 1e6 上限） |
| **未通过**：运行期热改配置 | 外部编辑 `world/serverconfig/summy_threat-server.toml`（含原地覆盖写）后等 30s+，Forge 的文件监听没有触发重载（日志无 `changed, sending notifies`、无异常）。**结论：改 SERVER 配置后需重启世界/服务端**；`ModConfigEvent` 入口在启动装载时已验证生效 |
| 未做：图形客户端 HUD | 无头环境跑不了 `runClient`；两侧读同一份 SERVER 档（服务端同步值），未做人工核对 |

### 定名 Summy Threat 后的复验（2026-10-15）

| 项 | 结果 |
| --- | --- |
| `gradlew clean build` | 通过；`verifyMixinTargets` 输出 `[Summy Threat] mixin 注入目标校验通过（1 个文件）` |
| refmap | 生成 `summy-threat.refmap.json`，内容为 `com/summy/threat/mixin/RangedAttributeAccessor → "maxValue": "f_22308_:D"` |
| 产物 | `summy-threat-0.1.0.jar`（9.3 KB）与 `summy-threat-0.1.0-all.jar`（642 KB，含嵌套 MixinExtras），发布副本落到 `JAR/summy-threat-forge/` |
| jar 内命名 | `summy_threat.mixins.json` / `summy-threat.refmap.json` / `assets/summy_threat/lang/*` 均在位 |
| 启动装载 | `ModLauncher ... --mixin.config, summy_threat.mixins.json`；日志前缀 `[summy_threat/]` |
| 启动抬升 | `[Summy Threat] 属性上限已调整：generic.armor 30.0 -> 100.0` / `generic.max_health 1024.0 -> 100000.0` |
| 配置文件 | 存档内新生成 `world/serverconfig/summy_threat-server.toml`（旧的 `examplemod-server.toml` 是改名前的残留，可手删） |
| RCON 实测 | 盔甲座 armor 设 40 → 返回 **40.0**；max_health 设 5000 → 返回 **5000.0** |
| 互斥分支 | 之前装 AttributeFix 冒烟副本时走到"检测到 AttributeFix → 让位"分支，说明该逻辑也随新身份正常工作 |

> 冒烟用的 AttributeFix dev 副本已从 `run/mods/` 移到 `run/calc/attributefix-dev-keep.jar`，默认不再随服务端加载；
> 需要复测互斥时把它移回 `run/mods/` 即可（`run/` 已在 `.gitignore` 内，不影响仓库）。
