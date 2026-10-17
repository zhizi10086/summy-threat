# Summy Threat

Forge 1.20.1 的"属性与怪物机制改造"模组，承载三块内容：

1. **属性上限抬升**（已实现）
2. **护甲减伤公式替换**（已实现）
3. **基础数值表**：怪物侧 TL 缩放（护甲 / 生命 / 攻击）、区域首领与 Boss 倍率、生态分档、
   玩家护甲重填、测量命令（已实现，2026-10-16）；**刷新规则、PCF/CDF、技能池（卓越者 / TL / 领袖 / Boss 能力）、
   破甲削甲仍未实装**
4. **回响下界合金（强化下界合金）**：锻造台升级的 4 件护甲 + 卓越者精华（已实现，2026-10-16；
   贴图/模型复用原版下界合金，每件的独特能力待补）

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

## 已实现①：属性上限抬升

原版把属性值夹在 `RangedAttribute` 自带的 min/max 上（1.20.1 实测：`generic.armor` = 0~30、
`generic.armor_toughness` = 0~20、`generic.max_health` = 0~1024），超出部分会被静默丢弃。
本模组只抬**上限**、不动下限：

- 走 Mixin accessor 改写 `RangedAttribute#maxValue`（`src/main/java/com/summy/threat/mixin/RangedAttributeAccessor.java`）。不能改用反射：生产环境字段名是 SRG（`maxValue` 对应 `f_22308_`），按字面名反射在正式包里必然失效。
- 抬 `generic.armor`（默认 500）、`generic.armor_toughness`（默认 100）、`generic.max_health`（默认 100 万）三个属性；`generic.attack_damage`(2048) 等保持原样。
- 改的是属性注册表里的**单例对象**，因此对玩家、原版怪、模组怪一视同仁。
- **只抬不压**：应用值取 `max(原版上限, 配置值)`，配置项的下界也钉在原版上限上；关闭总开关会把三个上限写回 30 / 20 / 1024（运行期可逆）。
- 生效时机由 `ModConfigEvent`（MOD 总线）统一驱动，一次覆盖"世界首次加载 / 客户端收到服务端同步 / 运行期重载"三种情形。

## 已实现②：护甲减伤公式替换

口径写在 `docs/护甲公式计算.txt`（唯一来源），实现在 `src/main/java/com/summy/threat/combat/ArmorDamageFormula.java`：

```
EA = A + (A×T)/15
玩家侧：减伤 = clamp(EA/(EA+20), 0, 0.85)                     （不截断 EA）
怪物侧：有效EA = min(EA, 100)；减伤 = clamp(0.90×√(有效EA/100), 0, 0.90)
```

- 挂点：`LivingEntity#getDamageAfterArmorAbsorb` 内对 `CombatRules.getDamageAfterAbsorb` 的**那一次调用**，用 MixinExtras 的 `@ModifyExpressionValue` 包住（`mixin/LivingEntityMixin.java`）。
  - `hurtArmor`（护甲耐久）与 `DamageTypeTags.BYPASSES_ARMOR` 闸门都在 hook 之外，行为完全保持原版；`bypasses_armor` 类伤害（如狱火、圣光）根本不进公式。
  - 不改 `CombatRules` 本体 —— 与其它改 CombatRules 的模组冲突面最小；后续的保护附魔、抗性、伤害吸收、无敌帧全部照原版。
- 侧别判定：`Player` 及其子类走玩家曲线，其余生物（模组怪、动物、村民、盔甲座）走怪物曲线。
- 输入取属性原始 double 值（`Attributes.ARMOR` / `ARMOR_TOUGHNESS`），不取 `getArmorValue()` 的取整结果。
- 截断只作用于"减伤计算"：怪物护甲可以堆到 500（配合基于护甲的效果），减伤只认到 EA=100（90%）。
- 总开关 `damage_reduction.enable_custom_armor_formula` 关闭后完全走原版公式。

## 已实现③：基础数值表 + 生态分档 + 测量命令

口径唯一来源是内部《Summy Threat — 核心数值系统交接文档》v2.17 的 §4~§8。本轮**只实装数值**：
技能池（卓越者 / TL / 领袖 / Boss 能力）、PCF/CDF、刷新规则、破甲削甲均未实装。

**TL 驱动（临时）**：正式 TL 由后续 PCF/CDF 计算；本轮用服务器级"全局 TL"——
`/summythreat tl <0-20>`（`off` 关闭；**默认关闭**，配置键 `scaling_debug.global_tl` 可预设）。
开启后**新刷的敌对生物**（`Enemy` 接口的怪 + 末影龙）会按该 TL 数值化。

| 项 | 取值 |
| --- | --- |
| 护甲 / 韧性 | 怪物护甲表（TL0 = 0/0 … TL20 = 38/28）；卓越者再 +15 / +10；区域首领 60/40、Boss 100/50 |
| 生命 | M(TL)（1.0 → 48.0，v2.7）× E(TL)（1.20 → 2.20，卓越者）× 首领 / Boss 固定倍率 |
| 攻击 | A(TL)（1.0 → 16.44，v2.9）× 卓越者 1.25；区域首领 21.93 / 9.40 / 8.0（古守倍率当前无效）；Boss 3.29 / 4.0 / 3.0（末影龙 / 凋灵倍率当前无效） |
| 移速 / 经验 | 卓越者 +5%；经验 ×2（`LivingExperienceDropEvent`） |
| Jade 提示（可选） | 装 Jade 11.13+ 时两行显示：`[Summy Threat] TL 20 卓越者`（普通单位不显示类型）/ `迅捷·力量`（能力名按解锁 TL 阶段上色：4-7 白、8-11 蓝、12-15 紫、16-20 金）；未装零影响 |
| Boss 缩放 | **TL 曲线**（TL0 = 原版、TL20 = 现口径；监守者 TL0/10/20 = 500 / 7,620 / 35,000 血） |
| TL 能力 | 按 TL 区间抽 0/1/2/3/4 个（卓越者 +1），14 个效果；`/summythreat spawn … <ability>` 覆盖、`/summythreat ability` 挂载 |
| 比例型能力 | 吸血 / 荆棘 / 熔火 / 腐蚀以**减伤前伤害**为基数（护甲与吸收盾之前）；吸血每次至少回复自身最大生命 0.5% |
| 投射物与荆棘冷却 | 吸血 / 熔火 / 腐蚀对**近战与投射物**（所有 `Projectile`，排除投掷药水）主命中生效；荆棘按**攻击者全局 0.25 秒**冷却 |
| 尸爆 | 自身最大生命 **100%** 的魔法伤害（无视护甲）；对玩家上限 **85% 最大生命**；允许连锁，带灵魂爆发特效 |
| 卓越护盾（卓越者） | 最大生命 × 等级曲线 `f1`（TL20 = **75%**，TL0/5/10/15 ≈ 0.31%/0.60%/4.98%/23.94%）；不可再生；护甲/附魔/吸收后、生命前承伤；负面 MobEffect、腐蚀与属性下降免疫（属性快照） |
| 自然卓越者 | 自然类生成（NATURAL / CHUNK_GENERATION / STRUCTURE / PATROL / JOCKEY / REINFORCEMENT）5%；刷怪笼、命令、召唤、刷怪蛋等 0% |

**生态分档（§5.0）**：检测 `summy_enchantment` / `summy_reliquary` / `summy_reliquary_apocrypha`
（SRA 强制依赖 SR，共 6 档）；两维系数 `r_hp`（生命 / 护甲）与 `r_atk`（攻击 / 特殊伤害）：
`none` 短路；`sr` 0.35/0.35、`se` 0.55/0.55、`sr_apocrypha` 0.55/**0.35**、`se_sr` 0.75/0.75、`full` 1.00/1.00。
缩放公式 `v' = 1 + (v − 1) × r`；怪物护甲先把 EA 截断到 100，再压 `有效EA' = 1 + (有效EA − 1) × r_hp`
（`r_hp = 1` 时与原来的减伤完全等价，设计期计算器的对照不受影响）。

**玩家护甲重填（§3.1）**：`ItemAttributeModifierEvent` 把皮革 / 锁链 / 黄金 / 铁 / 钻石 / 下界合金（基础）
四件的护甲与韧性改成设计值（如铁 3/6/4/2、钻石 5/9/7/4）；「升级后」= 回响下界合金（本模组物品）。

**测量命令**（OP 2 级，配合实机校准）：

```
/summythreat tier [mode]        查看 / 强制档位（auto / none / sr / se / sr_apocrypha / se_sr / full）
/summythreat tl [0-20|off]      查看 / 设置全局 TL
/summythreat spawn <实体ID> <tl> [eximus|leader] [pcf]     定点召唤一只已数值化的怪
/summythreat eximus <实体ID> [tl] [ability]               直接召唤卓越者（默认当前全局 TL，未开启则 20）
/summythreat stats [玩家]       查看计量：有效 DPS / 击杀耗时 / 无视护甲占比 / 护盾吸收 / 承伤 / 致死次数
```

每次击杀被数值化的怪会在服务端日志输出一行摘要（含有效 DPS 与无视护甲占比），便于 RCON 脚本抓取。

玩家承伤同样可落日志：`combat_log.log_player_taken`（**默认开**，效仿 SRA 的 `log_trigger`）——
每次受击一行（伤害 / 攻击者 / TL / 扣血前生命与剩余吸收）+ 被 TL 怪击杀时一行会话汇总；
关闭后不再逐击记录，但仍计入 `/summythreat stats`。

## 已实现④：回响下界合金（强化下界合金）

把交接文档 §3.1 的「下界合金（升级后）」落成正式装备——**回响下界合金**（`summy_threat:echo_netherite_*`）：

- **四件护甲**：头盔 6/4、胸甲 11/6、护腿 8/5、靴子 5/3（护甲合计 **30**、韧性合计 **18**，与 §3.1 一致）；
  自定义 `ArmorMaterial` 实现，`getName()` 返回 `netherite` → **穿戴渲染复用原版甲片贴图**，
  物品图标 `parent: minecraft:item/netherite_*` **复用原版模型**（正式贴图/模型列为待补）；
  耐久倍率 37、附魔值 15、击退抗性 0.1、修复材料下界合金锭。
- **卓越者精华**（`summy_threat:eximus_essence`）：本轮**只注册、无获取途径**（来源随"卓越者刷新"轮落地，
  测试用 `/give`）；贴图暂用**原版下界之星**占位。
- **锻造台配方**（4 条 `minecraft:smithing_transform`）：**模板槽 = 回响碎片（`minecraft:echo_shard`）**、
  **基座槽 = 对应下界合金件**、**附加槽 = 卓越者精华**，四件一一对应；`show_notification = false`。
- 四个护甲件与精华已加入原版「战斗 / 材料」创造页，便于测试；**每件的独特能力待补**。

## 配置

Forge 原生 **SERVER 档**（不是自研 JSON），由 Forge 自动同步给客户端，双端口径天然一致：

| 键 | 类型 | 默认 | 范围 |
| --- | --- | --- | --- |
| `attribute_caps.enable_attribute_cap_override` | boolean | `true` | 无 |
| `attribute_caps.armor_max_value` | double | `500.0` | 30.0 ~ 1000000.0 |
| `attribute_caps.armor_toughness_max_value` | double | `100.0` | 20.0 ~ 1000000.0 |
| `attribute_caps.max_health_max_value` | double | `1000000.0` | 1024.0 ~ 10000000.0 |
| `damage_reduction.enable_custom_armor_formula` | boolean | `true` | 无 |
| `damage_reduction.synergy_divisor` | double | `15.0` | 1.0 ~ 1000.0 |
| `damage_reduction.player_k` | double | `20.0` | 0.0 ~ 10000.0 |
| `damage_reduction.player_max` | double | `0.85` | 0.0 ~ 1.0 |
| `damage_reduction.player_min` | double | `0.0` | 0.0 ~ 1.0 |
| `damage_reduction.monster_max` | double | `0.90` | 0.0 ~ 1.0 |
| `damage_reduction.monster_effective_ea_cap` | double | `100.0` | 0.0 ~ 10000.0 |
| `player_armor.enabled` | boolean | `true` | 无 |
| `health_multiplier_per_tl.tl0` ~ `tl20` | double | v2.7 表（`1.0` → `48.0`） | 0.0 ~ 10000.0 |
| `attack_multiplier_per_tl.tl0` ~ `tl20` | double | v2.9 表（`1.0` → `16.44`） | 0.0 ~ 10000.0 |
| `eximus_health_multiplier_per_tl.tl0` ~ `tl20` | double | v2.6 表（`1.20` → `2.20`） | 0.0 ~ 10000.0 |
| `eximus.armor_bonus` / `toughness_bonus` | double | `15.0` / `10.0` | 0.0 ~ 10000.0 |
| `eximus.damage_multiplier` / `speed_bonus` / `xp_multiplier` | double | `1.25` / `0.05` / `2.0` | 见配置文件 |
| `eximus.eximus_shield_tl20_ratio` | double | `0.75`（TL20 卓越护盾占最大生命） | 0.0 ~ 10.0 |
| `eximus.natural_spawn_chance` | double | `0.05`（自然卓越者概率） | 0.0 ~ 1.0 |
| `regional_boss_health.leader_zombie` / `piglin_brute` / `elder_guardian` | double | `1050` / `420` / `263` | 1.0 ~ 10000.0 |
| `regional_boss_health.*_attack` | double | `21.93` / `9.40` / `8.0`（古守当前无效） | 0.0 ~ 10000.0 |
| `regional_boss_health.pcf_base_factor` / `pcf_scale_divisor` | double | `0.3` / `28.0` | 见配置文件 |
| `boss_health.warden` / `ender_dragon` / `wither` | double | `70` / `175` / `117` | 1.0 ~ 10000.0 |
| `boss_health.warden_melee_attack` / `ender_dragon_attack` / `wither_attack` | double | `3.29` / `4.0` / `3.0`（龙 / 凋灵当前无效） | 0.0 ~ 1000.0 |
| `boss_health.default_effective_dps` | double | `700.0` | 1.0 ~ 100000.0 |
| `environment_tier.mode` | enum | `auto` | auto / none / sr / se / sr_apocrypha / se_sr / full |
| `environment_tier.hp_scale_*` / `atk_scale_*` | double | `0.35` / `0.55` / `0.55`(**攻击 0.35**) / `0.75` / `1.00` | 0.0 ~ 1.0 |
| `scaling_debug.global_tl` | int | `-1`（关闭接管） | -1 ~ 20 |
| `combat_log.log_player_taken` | boolean | `true` | 无 |
| `tl_abilities.enable_tl_abilities` | boolean | `true` | 无 |

文件位置：存档内 `serverconfig/summy_threat-server.toml`（SERVER 档按世界存放）。

**可选依赖 Jade**：`libs/jade-11.13.3.jar`（MIT）只作**编译期 API**；运行时由 Jade 扫描 `@WailaPlugin` 加载，
未安装 Jade 时零影响。dev 客户端跑 `runClient` 会自动把 fg.deobf 副本放进 `run/mods` 供冒烟。

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

### ② 护甲减伤公式替换（已实现，见上方"已实现②"）

口径唯一来源是 `docs/护甲公式计算.txt`；改公式请先改它，再同步下面两个工具与模组实现。

设计期工具（放在 `docs/` 下，不算产物、Gradle 不编译）：

- `docs/护甲公式计算.txt`：公式与参数的**规格说明**（唯一口径，不含代码）。
- `docs/护甲减伤计算器.py`：图形界面计算器（Python + tkinter + matplotlib）——左侧调参与常量、
  右侧实时曲线与预设点位（总开关 / 分组开关 / 逐项勾选三级控制，文字标签默认关闭以免遮挡）、
  下方单点结果 / 反解 / 预设表，可导出 PNG 与 CSV；加 `--selftest` 走无窗口自检。
- `docs/ArmorCalc.java`：同一口径的 Java 命令行版本（`java docs/ArmorCalc.java --help`），
  支持单次计算、参考表、扫表、反解、CSV / PNG / SVG 输出，供脚本化或没有 Python 的环境使用。

三者口径必须一致：**改公式先改 txt，再同步 py 与 java**。

### ③ 怪物刷新与属性强化（数值部分已实现，刷新链路与技能池待做）

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

## 验证记录

### 卓越护盾更名 + 亡灵增援同 TL（2026-10-17，v2.17，RCON 无头实测）

| 项 | 结果 |
| --- | --- |
| `gradlew clean build` + `verifyMixinTargets` | 通过；产物 725,704 字节 |
| 更名 | `Overguard` → `EximusShield`、`OverguardFloor` → `EximusShieldFloor`；ForgeData → `summy_threat:eximus_shield` / `_max`；配置键 → `eximus.eximus_shield_tl20_ratio`；Jade → `summy_threat.jade.eximus_shield`；显示文本统一为“卓越护盾 / Eximus Shield” |
| 旧标签迁移 | 旧 `summy_threat:overguard` / `_max` 只读兼容；重缩放时迁移到新标签并删除旧标签；两段式测试通过（1,084 / 1,584 保留） |
| 亡灵增援同 TL | 全局 TL 关闭时 owner 命令 TL20 → 召唤体 960 血、`TAG_TL=20`、同类型 zombie；全局 TL16 时召唤体仍 960（不是 548） |
| 召唤体能力 | 召唤体有 4 个正常 TL 能力，但 `TAG_ABILITIES` 不含 `necro_reinforcement`；非卓越者、无卓越护盾、不会再连锁 |
| Boss/领袖排除 | 领袖僵尸与凋灵即使命令强制挂载亡灵增援，反复受击也不产生召唤体 |
| 回归 | `rcon_eximus_test`、`rcon_necro_test`、`rcon_ability_test`、`rcon_scaling_test`（21 项）、`rcon_damage_test`（5 例）、`rcon_armor_check`（28 项）全绿 |
| 部署 | jar 已进实例（725,704 字节）；旧配置键被忽略，新键 `eximus_shield_tl20_ratio` 默认 0.75 |
| 未做（需你实机） | Jade 新名称“卓越护盾”显示；自然 5% 刷新（`natural_spawn_chance=1.0` 测试后恢复）；护盾吸收/快照实机手感 |

### 卓越护盾 + 5% 自然刷新（2026-10-17，v2.16，RCON 无头实测）

| 项 | 结果 |
| --- | --- |
| `gradlew clean build` + `verifyMixinTargets` | 通过；产物 725,120 字节 |
| 等级曲线 | TL0/5/10/15/20 护盾比例实测 **0.311% / 0.603% / 4.979% / 23.943% / 75.000%**，与 `f1(d)=1+0.0015d⁴`、`f1(20)=241` 归一化一致 |
| 卓越护盾数值 | TL20 卓越者 2,112 血 → **1,584 护盾**；魔法 100 → 护盾 1,484、生命不变；魔法 1,484 → 护盾 0、生命不变；破盾后物理 100 → 生命 2,102；3 秒后仍 0（不可再生） |
| 属性快照 | 护盾 >0 时负向 `attribute modifier (-10)` 不改变护甲 53；破盾后护甲恢复为 43；负面 MobEffect（slowness）护盾期间被拒绝、破盾后可生效 |
| 腐蚀交互 | 护盾下腐蚀不叠层（护甲 53）；破盾后腐蚀 1 层 → 47.7 |
| 跨存档持久化 | 打掉 500 护盾（1,084/1,584）→ save-all → 关服 → 重启 → 仍为 1,084 / 1,584（不全满） |
| 自然刷新 | 5% 钩子已实现（自然类生成；刷怪笼/命令/召唤 0%）；无玩家 headless 环境无法自动验证自然生成，需实机把 `natural_spawn_chance` 临时设 1.0 观察 |
| 调试指令 | `/summythreat eximus minecraft:zombie` 默认按当前全局 TL（未开启 20）生成卓越者；显式 `15 none` → TL15、23.943% 护盾，实测 OK |
| 回归 | `rcon_eximus_test`、`rcon_ability_test`、`rcon_scaling_test`（21 项）、`rcon_damage_test`（5 例）、`rcon_armor_check`（28 项）、5 个文档脚本、`cross_check.py` 全绿 |
| 尸爆连锁说明 | 卓越者 2,112 魔法爆发打不穿另一只卓越者（1,584 护盾 + 2,112 生命），所以卓越者尸爆不连锁；`rcon_ability_test` 的连锁断言改用普通怪（2 × 960 = 1,920） |
| 部署 | jar 已进实例（725,120 字节）；新增两个 eximus 配置键，旧 serverconfig 自动补键 |
| 未做（需你实机） | ① Jade 卓越护盾行 + 粒子/破盾特效；② 自然 5% 刷新（`natural_spawn_chance = 1.0` 验证后恢复 0.05）；③ `/summythreat stats` 的护盾吸收与有效 DPS；④ 实机高频攻击下的快照手感 |

### 投射物兼容 + 荆棘按攻击者 0.25 秒冷却（2026-10-17，v2.14，RCON 无头实测）

| 项 | 结果 |
| --- | --- |
| `gradlew clean build` + `verifyMixinTargets` | 通过；产物 714,914 字节 |
| 投射物兼容 | 攻击方分支改为“近战直击或 `Projectile.getOwner() == 攻击者`”，排除 `ThrownPotion`；吸血 / 熔火 / 腐蚀复用名义伤害缓存 |
| 投射物无头断言 | 带吸血骷髅射 100 伤害箭 → 骷髅 +15；带熔火骷髅射 100 箭 → 僵尸掉 20（10 物理 + 10 火焰）；带腐蚀骷髅连射 3 箭 → 监守者护甲 100→70；全绿 |
| 荆棘冷却 | 按攻击者 UUID 全局 5 tick（0.25 秒）；同一攻击者 0.25 秒内打两只荆棘怪只反弹一次（掉约 1.0），1.2 秒后再打恢复反弹 |
| 回归 | `rcon_ability_test`、`rcon_scaling_test`（21 项）、`rcon_damage_test`（5 例）、`rcon_armor_check`（28 项）、5 个文档脚本、`cross_check.py` 全绿 |
| 部署 | jar 已进实例（714,914 字节）；无新增配置键，未重置配置 |
| 未做（需你实机） | ① SRA 高频攻击荆棘怪不再瞬间自杀（最多 4 次反弹/秒）；② 骷髅/烈焰人带腐蚀/吸血/熔火射击时生效；③ 投掷药水不触发这三项；④ 玩家护甲被腐蚀后 6 秒恢复 |

### 比例型能力减伤前基数 + 尸爆 100% 魔法（2026-10-17，v2.13，RCON 无头实测）

| 项 | 结果 |
| --- | --- |
| `gradlew clean build` + `verifyMixinTargets` | 通过；产物 714,586 字节 |
| 比例型能力基数 | 新增 `LivingHurtEvent` 名义伤害缓存（护甲 / 吸收之前），吸血 / 荆棘 / 熔火 / 腐蚀统一改用；吸血 = `max(15% 名义伤害, 0.5% 自身最大生命)` |
| 无头断言 | 吸血名义 100 → +15、名义 10 → +4.8（下限）；熔火名义 100 → 10 物理 + 10 火焰；荆棘反射约 1.0；腐蚀 3 层 → 护甲 100→70；全绿 |
| 尸爆 | 自身最大生命 100% 的 `indirect_magic`（无视护甲）；对玩家上限 85% 最大生命；允许连锁；灵魂爆发特效（EXPLOSION_EMITTER + SOUL / SOUL_FIRE_FLAME + 低沉爆炸音） |
| 尸爆无头断言 | 卓越者 2,112 血 → 监守者掉 2,112；两只卓越者连锁 → 共掉 4,224；全绿 |
| 回归 | `rcon_scaling_test`（21 项）、`rcon_ability_test`、`rcon_damage_test`（5 例）、`rcon_armor_check`（28 项）、5 个文档脚本全绿 |
| 部署 | jar 已进实例（714,586 字节）；无新增配置键，未重置配置 |
| 未做（需你实机） | ① 满血 102、无吸收时被 960 血尸爆怪炸 → 86.7 伤害；② 带吸收盾时吸血 / 腐蚀仍生效；③ 灵魂粒子与音效；④ 两只尸爆怪相邻连续爆炸 |

### Jade 两行格式 + 阶段配色 + 亡灵池限制 + 荆棘无击退（2026-10-17，v2.12）

| 项 | 结果 |
| --- | --- |
| `gradlew build` + `verifyMixinTargets` | 通过 |
| Jade 两行格式 | 第一行 `[Summy Threat] TL 20 卓越者`（普通单位走 `status_normal`，不显示类型）；第二行能力名以 `·` 连接、按解锁 TL 阶段上色（4-7 白 / 8-11 蓝 / 12-15 紫 / 16-20 金）；命令输出复用同一组件，不再 `getString()` 拍平 |
| 能力按阶段上色 | `AbilityId.stage()`：4-7→2、8-11→3、12-15→4、16-20→5（0-3→1 灰占位）；色值集中在 `AbilityTexts` |
| 亡灵增援入池限制 | `rcon_ability_test` 新增：TL17 蜘蛛（非亡灵）16 次抽取均无 `necro_reinforcement`；TL17 僵尸（亡灵）16 次至少出现一次——全绿 |
| 荆棘无击退 | `damageExtra(..., suppressKnockback=true)`：结算前保存目标速度、`hurt` 后还原并置 `hurtMarked`；熔火 / 尸爆保持默认击退 |
| 回归 | `rcon_scaling_test`（含 Boss TL0/10/20）、`rcon_ability_test`（31 项）、`rcon_damage_test`（5 例）、5 个文档脚本全绿 |
| 部署 | jar 已进实例（本轮无新增配置键，未重置存档配置） |
| 未做（需你实机） | ① Jade 两行与颜色显示；② 普通怪第一行无类型；③ 荆棘反弹无击退（熔火 / 尸爆仍有击退） |

### Boss TL 曲线 + TL 能力落地（2026-10-17，v2.11，RCON 无头实测）

| 项 | 结果 |
| --- | --- |
| `gradlew clean build` + `verifyMixinTargets` | 通过；产物 **710,328 字节**（含 14 个 TL 能力 + Boss TL 曲线） |
| Boss 的 TL 曲线 | 监守者 tl0 / tl10 / tl20 → **500 / 7,620 / 35,000** 血；攻击属性 30.0 / 37.6 / 98.7（`rcon_scaling_test` 新增断言全绿） |
| TL 能力数量与抽取 | TL3/5/9/13/17 → **0/1/2/3/4** 个；卓越者 TL9 → 3、TL3 → 0；id 合法、同一只怪不重复、解锁TL ≤ 怪物TL（`rcon_ability_test` 全绿） |
| 能力效果（无头 RCON） | 迅捷 0.2645（0.23×1.15）、力量 64.12、狂怒低血 64.12、坚韧 200→掉 18、抗火 0、韧皮 200→掉 14、再生 2 秒 +2%、相位低血隐身、尸爆击杀邻近鸡——全部 OK |
| 命令覆盖 | `spawn … <ability>` 强制单个；`/summythreat ability … clear` / `<id>` 挂载清除 OK |
| 回归 | `rcon_damage_test` 5 例全绿；5 个文档脚本全绿（`verify_config_keys` 已含 `[tl_abilities]`） |
| 配置 | 生成 `[tl_abilities] enable_tl_abilities = true` |
| 部署 | jar 已进实例（710,328 字节），两个存档旧 serverconfig 已清（进世界按新默认重建） |
| 未做（需你实机） | ① 吸血 / 荆棘 / 熔火 / 腐蚀 / 亡灵增援（需 AI 互殴）；② TL 8/12/16 的怪实际拿到 2/3/4 个能力并在 Jade 看到名字；③ Boss 不同 TL 的血量手感 |

### TL 能力表定稿 + 玩家承伤日志 + Jade 提示（2026-10-17，v2.10）

| 项 | 结果 |
| --- | --- |
| `gradlew clean build` + `verifyMixinTargets` | 通过（含 Jade 11.13.3 的 compileOnly / fg.deobf 解析）；产物 697,836 字节；jar 内 **不含** `snownee/jade` 类，只含 `com/summy/threat/compat/jade/*` |
| TL 能力表（§6.5） | 定稿：TL18 只留尸爆（删除生命链接）、荆棘/熔火按"本次伤害"10%、腐蚀 6 秒、亡灵增援备注（仅亡灵 / 半径 8 格）；追加段无敌帧规则（进清前帧、不清后帧）写入 §6.5 注与 §13-15 |
| 玩家承伤日志 | `combat_log.log_player_taken`（默认开）：逐击 INFO（伤害 / 攻击者 / TL / 扣血前生命与剩余吸收）+ 死亡会话汇总；关闭后仍计入 `/summythreat stats` |
| Jade 提示（可选） | `@WailaPlugin("summy_threat")`：服务端写 TL / 类别 / 能力（预留 `summy_threat:abilities`），客户端显示 `[Summy Threat] TL 20 · 卓越者`；`libs/jade-11.13.3.jar`（MIT）仅编译期，`runClient` 自动放 dev 副本 |
| 回归（无头 RCON，脚本需串行跑） | `rcon_scaling_test.py` 21 项全绿；`rcon_damage_test.py` 5 例全绿；配置生成 `[combat_log] log_player_taken = true` |
| 文档脚本 | `verify_scaling` / `verify_handover` / `verify_config_keys` / `verify_pcf_cdf` / `verify_player_armor` 全绿（`verify_config_keys` 已含 `[combat_log]` 组） |
| 部署 | `deploy.ps1 -SkipBuild -ResetWorldConfig`：jar 已进实例（697,836 字节），两个存档旧 serverconfig 已清（进世界按新默认重建） |
| 未做（需你实机） | ① 受击逐击日志 + 死亡汇总；关闭开关后无逐击行；② 装 Jade 指向 TL20 怪显示 `TL 20 · 卓越者`（能力行待能力系统）；③ 卸掉 Jade 不崩溃 |

### 攻击侧重标定 + 计量残影 / 残留怪修复（2026-10-17，v2.9，RCON 无头实测）

| 项 | 结果 |
| --- | --- |
| `gradlew clean build` | 通过；`verifyMixinTargets` 全绿；产物 `summy-threat-0.1.0.jar`（692,064 字节）落到 `JAR/summy-threat-forge/` |
| 攻击侧重标定（v2.9） | 按"满配一口血 168.5（护甲段 71.5% + 生命 84 + 吸收 84.5）"、困难难度 ×1.5 反推：A(TL) 整表 k=0.772（TL20 21.0 → **16.44**）、监守者 1.5 → **3.29**（4 次致死）、领袖僵尸 12 → **21.93**、猪灵蛮兵 4 → **9.40**（6 次致死）；猪灵蛮兵原版攻击属性实为 **7.0**（旧文档按 13.0 推算系误算，已校正） |
| 数值核验（`run/calc/rcon_scaling_test.py`，21 项全绿） | sr 档僵尸攻击 19.21；full 档：普通 49.32 / 卓越者 61.65 / 领袖僵尸 65.79 / 猪灵蛮兵 65.80 / 监守者 98.70；血量与减伤项同表全绿 |
| 硬编码伤害单位（v2.9 查实） | **末影龙**没有 `ATTACK_DAMAGE` 属性；**凋灵**有（Monster 默认 2.0，被倍率改成 6.0）但实际伤害（爆炸 / 头颅）不读它；**远古守卫者**有（8.0 → 131.52）但实际伤害是 thorns 2.0 + 激光。三者倍率键当前对实际伤害无效，待"攻击接管"轮 |
| 护甲重填回归（`rcon_armor_check.py`，28 项全绿） | 含回响套：单件靴 7 / 3、单件胸 13 / 6、全套 32 / 18 |
| 减伤回归（`rcon_damage_test.py`，5 例全绿） | 该脚本已显式切 full 档：A=100→90%、A=500→90%（截断）、A=50→63.64%、A=100/T=20→90%、A=0→0% |
| 计量残影清理 | `onDamage` 跳过已死亡实体 + `onDeath` 要求 `dealt > 0`：击杀瞬间的二次事件不再产生"0.00s / DPS −1.00"残影行，击杀数不再翻倍 |
| 残留怪重缩放（`rcon_rescale_test.py`，两段式 4 项全绿） | 起服 → TL20 僵尸 960 血 → save-all → 关服 → 再起服：日志出现「重新数值化残留实体」且该怪 max_health / Health = 960 |
| 文档脚本 | `verify_scaling` / `verify_handover` / `verify_config_keys` / `verify_pcf_cdf` / `verify_player_armor` 五个脚本全绿（含新 A 表、§5.0 一击列 28.8 / 42.7 / 56.6 / 74.0） |
| 部署 | `_st-test/deploy.ps1 -SkipBuild -ResetWorldConfig`：jar 已进实例（692,064 字节）；`saves\测试` 与 `saves\恶魔` 的旧 `serverconfig/summy_threat-server.toml` 已删（下次进世界按新默认重建） |
| 未做（需你实机） | ① TL20 监守者击杀日志每次仅 1 行（无残影）、`/summythreat stats` 击杀数不翻倍；② 世界重载后残留怪自动重缩放并打日志；③ 被 TL20 僵尸打 ≈8 次致死 / 监守者近战 ≈4 次 / 领袖僵尸 ≈6 次（保护附魔与吸收再生会让实际更耐打，偏差大再按同公式微调） |

### 基础数值表落地后（2026-10-16，Forge 47.4.10 / JDK 17，指令经 RCON 下发）

| 项 | 结果 |
| --- | --- |
| `gradlew clean build` | 通过；`verifyMixinTargets` 输出"[Summy Threat] mixin 注入目标校验通过（2 个文件）" |
| 生态档位检测 | 装入 SR 1.8.5（dev 映射）+ Curios 后起服：日志 `[Summy Threat] 生态档位：SR（r_hp=0.35，r_atk=0.35）；全局 TL 关闭（可用 /summythreat tl <0-20> 开启）` |
| TL 驱动 | `/summythreat tl 20` → `全局 TL 已设为 20；新刷的敌对生物将按此 TL 数值化` |
| 数值化实测（`run/calc/rcon_scaling_test.py`，**18 项全绿**） | sr 档 TL20 僵尸：255.2 血 / 护甲 38 / 韧性 28 / 攻击 24 / 减伤 53.7%；full 档：普通 692 血·63 攻击·90% 减伤；卓越者 1522.4 血·53 甲·38 韧·78.75 攻击；区域首领（PCF20）3042.9 血·60 甲·40 韧·36 攻击；监守者 10000 血·100 甲·50 韧·45 攻击 |
| 减伤回归（r_hp = 1） | 既有 `rcon_damage_test.py` 全绿（A=100→90%、A=500→90%、A=50→63.64%、A=100/T=20→90%、A=0→0%）——确认档位压缩在 r=1 时与原实现完全等价 |
| 攻击口径注记 | 僵尸 `ATTACK_DAMAGE` 属性基值是 **3.0**；文档 §7 / §8 的"基础攻击 4.5"是**困难难度**口径（原版在命中时再 ×1.5），硬难度实际命中伤害 = 3.0 × 1.5 × A(TL)。**v2.9**：攻击侧以"满配一口血 168.5（护甲段 71.5% + 生命 84 + 吸收 84.5）"重标定，目标 TL20 普通怪 8 次 / 首领 6 次 / 监守者 4 次致死 |
| 硬编码伤害单位（v2.9 查实） | **末影龙**（啃咬硬编码 10f/5f，连 `ATTACK_DAMAGE` 属性都没有）、**凋灵**（爆炸 + 头颅投射物；属性存在但默认 2.0，不驱动伤害）、**远古守卫者**（thorns 2.0 接触 + 激光；属性 8.0 不驱动伤害）——`ender_dragon_attack` / `wither_attack` / `elder_guardian_attack` 三个倍率键当前**对实际伤害不生效**（占位待"攻击接管"轮） |
| 配置文件 | `world2/serverconfig/summy_threat-server.toml` 生成全部新块（`player_armor` / 三张 tl 表 / `eximus` / `regional_boss_health` / `boss_health` / `environment_tier` / `scaling_debug`），取值与 v2.6 一致（tl20 = 34.6 / 21.0 / 2.2，warden = 20，mode = AUTO） |
| 文档脚本 | `verify_scaling` / `verify_handover` / `verify_pcf_cdf` / `verify_config_keys` / `verify_player_armor` 五个脚本全绿 |
| 未做（需客户端） | 玩家护甲重填实机核对：无头服务端没有玩家；需在单人世界穿全套铁，`armor` 应为 **15**（3/6/4/2） |
| 未做（需真人） | 计量命令实机验证：打 TL 怪后 `/summythreat stats` 应输出有效 DPS / 击杀耗时 / 无视护甲占比；服务端日志同步出现一行击杀摘要 |
| 未包含 | 技能池（卓越者 / TL / 领袖 / Boss 能力）、PCF/CDF、刷新规则与 5% 卓越者概率、破甲/削甲、超级Boss |

### 玩家护甲重填的 UUID 撞车修复（2026-10-16，客户端实测暴露）

| 项 | 结果 |
| --- | --- |
| 现象（你的实测） | 全套铁只显示 **6**（≈单件值）：四件护甲件共用了同一个修饰符 UUID，原版装备变更"按 UUID 先移除再加回"会互相顶掉，最终只剩一件生效 |
| 根因确认 | 原版 `ArmorItem` 用 `ARMOR_MODIFIER_UUID_PER_TYPE`——**按部位各一个 UUID**，正是为了规避这一点；本模组改为 护甲/韧性 × 四个部位 = 8 个稳定 UUID，命名带部位（`summy_threat:armor_refill/helmet` 等） |
| 无头复验（`run/calc/rcon_armor_check.py`，**10 项全绿**） | 给僵尸 **NBT 生成时带装备**（`/item replace` 在该场景不触发装备变更结算，实测踩过）：空装备 2/0；1~4 件铁逐件累加 **4 / 8 / 14 / 17**（韧性 1 / 3 / 6 / 8）——即 3+6+4+2 四件全部生效；老 bug 下全套只会是 **8** |
| 新增验收命令 | `/summythreat armor [玩家]`：打印 重填开关 / 护甲 / 韧性 / EA / 玩家侧减伤 + 四件装备逐件的护甲贡献，供客户端穿脱对照 |
| 回归 | 同一次起服内复跑 `rcon_damage_test.py`（减伤公式）与 `rcon_scaling_test.py`（18 项数值）全绿 |

### 护甲栏位过滤 + 计量溢出封顶（2026-10-16，客户端截图暴露）

| 项 | 结果 |
| --- | --- |
| 现象（你的截图） | 铁靴子提示把 "+2 护甲 / +1 韧性" 在**主手 / 副手 / 脚上 / 腿上 / 身上 / 头上**六个栏位各列了一遍 |
| 根因 | `ItemAttributeModifierEvent` 按 **(物品, 栏位)** 逐次触发，原版 `ArmorItem#getDefaultAttributeModifiers` 只在自己那个栏位返回修正符；本模组此前无条件注入 → 提示全列，而且**手持/错位栏位会真的加护甲**（拿在手里的铁胸甲白给 6 点） |
| 修复 | 事件入口加 `event.getSlotType() != armor.getType().getSlot()` 判定：只在该件自己的栏位注入，其它栏位既不删也不加 |
| 无头复验（`rcon_armor_check.py` 扩到 **22 项全绿**） | 栏位三组对照：靴子放**脚上** 2+2=**4**、韧性 1 ✓；靴子放**胸口**（错位）**不给**（2 / 0）✓；**主手**持铁胸甲**不给**（2 / 0）✓。累加：铁 17 / 8、钻石 27 / 5、下界合金（基础）28 / 12 |
| 计量溢出封顶 | 玩家→TL 怪 的累计伤害改为 `min(事件伤害, 目标当前生命)`（已用 Forge 补丁确认 `LivingDamageEvent` 触发时"吸收已结算、生命未扣减"），总伤害 / 有效 DPS / 无视护甲占比三处同口径，避免斩杀类溢出把 A12 实测值抬高（22:33 那条 1418.42 明显含溢出，需修复后重测） |
| 回归 | `rcon_damage_test.py` 与 `rcon_scaling_test.py` 再次全绿 |

### 回响下界合金落地（2026-10-16，v2.8）

| 项 | 结果 |
| --- | --- |
| 物品与配方 | 5 个新物品（4 件护甲 + 卓越者精华）与 4 条锻造配方；jar 内含 `data/summy_threat/recipes/*`、`assets/summy_threat/models/item/*` 与 5 条中英物品名 |
| 护甲数值（`rcon_armor_check.py`，**28 项全绿**） | 僵尸 NBT 带装备：单件靴子 2+5=**7**（韧性 3）、单件胸甲 2+11=**13**（韧性 6）、全套回响 2+30=**32**（韧性 **18**）；原有 6 套对照不受影响 |
| 配方加载 | 起服 + `/reload`：**零** "Parsing error loading recipe" 等报错；物品注册日志正常（仅 Forge 的"未拿到期望 ID"提示，属注册顺序噪声） |
| 回归 | `rcon_scaling_test.py`（18 项）与 `rcon_damage_test.py`（减伤 5 例）全绿；五个文档脚本全绿 |
| 待你验收（客户端） | ① 创造页看到 4 件护甲与精华（回响套外观应与下界合金一致、精华显示下界之星贴图）；② 锻造台放"回响碎片 + 下界合金件 + 卓越者精华"应产出对应回响件（四件各试一次） |

> **踩坑记录（2026-10-16，客户端反馈"创造页找不到物品"）**：`BuildCreativeModeTabContentsEvent` 带
> `implements IModBusEvent`，必须挂在**模组总线**（`modBus.addListener(...)`）；第一版挂在 Forge 总线上，
> 处理器从未执行——物品注册成功（日志能看到 ID 分配），却不在任何创造页里。修复后注入到位，
> 并在首次注入时各打一条 INFO 日志（`已向原版「战斗」创造页注入 4 件回响下界合金护甲` / `…「材料」页注入卓越者精华`），
> 便于以后从日志核对。注意：无玩家时服务端不会构建创造页，这条日志只在客户端出现。

### 按实测二次重标定（2026-10-16，v2.7）

| 项 | 结果 |
| --- | --- |
| 实测依据（你的客户端两批样本） | 僵尸 n=14 中位有效 DPS **139.7**、P25 109.8；监守者 n=2 **699 / 787**；**无视护甲占比 76~96%**（印证 90% 物理减伤下的通道依赖） |
| 普通怪 | M(TL) 整表改为 `1 + (源表 − 1) × 3.22`；TL20 20×692 → **960** 血（@140 DPS ≈ 6.9 秒） |
| 区域首领 | 统一名义血池 3,000 → **21,000**（倍率 1050 / 420 / 263；PCF 20 档 ≈21,300；目标 30 秒） |
| Boss | 统一血池 10,000 → **35,000**（倍率 70 / 175 / 117；目标 **50 秒**）；Mod Boss 反推默认 TTK 50 秒 / 有效 DPS 700 ⇒ `35,000 / 基础生命` |
| 六档对照 | 假设 DPS 同比放大为 56 / 84 / 84 / 112 / 140（首领·Boss 280 / 420 / 420 / 560 / 700）；各档 TL20 普通怪 TTK 6.2~6.9 秒、监守者 44.9~50.0 秒 |
| 无头复验 | `rcon_scaling_test.py`（18 项：sr 档 349 血 / 53.7%、full 档 960 / 2112 / 21,300 / 35,000 全对）、`rcon_armor_check.py`（22 项）、`rcon_damage_test.py`（减伤 5 例）全绿 |
| 文档脚本 | `verify_scaling` / `verify_handover` / `verify_pcf_cdf` / `verify_config_keys` / `verify_player_armor` 五个脚本全绿 |
| 部署 | `_st-test\deploy.ps1 -ResetWorldConfig`：替换 jar **并删除存档内旧的 `serverconfig/summy_threat-server.toml`**（Forge 只补缺键不覆盖已有键，不删就永远是旧数值） |
| 待你复测 | TL20 僵尸应约 **7 秒**（原 4 秒）、监守者应约 **50 秒**（原 13 秒）；顺手补一条"被 TL20 怪打"的数据供 A11/攻击表使用 |

### 你的实例部署方式（`_st-test/deploy.ps1`）

`powershell -File _st-test\deploy.ps1`（或 `-SkipBuild` 只部署）会构建并把 `JAR/summy-threat-forge/summy-threat-0.1.0.jar`
（= 含嵌套 MixinExtras 的 `-all` 产物）复制进 `E:\MC\PCL2快照版\.minecraft\versions\1.20.1-Forge_47.4.23-test\mods\`，
只清理 `summy-threat-*.jar` 旧文件，不动实例里的其它模组。

### 护甲公式落地后（2026-10-15，默认参数 C=15 / K=20 / 玩家 85% / 怪物 90% / 截断 100）

| 项 | 结果 |
| --- | --- |
| `gradlew clean build` | 通过；`verifyMixinTargets` 输出"mixin 注入目标校验通过（2 个文件）"；refmap 出现 `LivingEntityMixin → CombatRules.getDamageAfterAbsorb(FFF)F = m_19272_(FFF)F` |
| 启动抬升（新世界） | `generic.armor 30.0 -> 500.0`、`generic.armor_toughness 20.0 -> 100.0`、`generic.max_health 1024.0 -> 1000000.0`，零注入失败 |
| 配置文件 | 新世界生成 `world2/serverconfig/summy_threat-server.toml`，含 `attribute_caps`（500 / 100 / 1000000）与 `damage_reduction`（15 / 20 / 0.85 / 0 / 0.90 / 100）两组默认值 |
| 实机减伤（僵尸，`/damage` 用 `minecraft:mob_attack`） | A=100 → 打 50 掉 5.00（90.00%）；A=500 → 掉 5.00（90.00%，截断生效）；A=50 → 掉 18.18（63.64%）；A=100/T=20（EA=233）→ 掉 5.00（90.00%）；A=0 → 掉 10.00（0.00%） |
| 总开关回退 | `enable_custom_armor_formula=false` 重启后，A=100 打 50 → 掉 10.00（原版公式的 80%），确认可逆 |
| 跨实现比对 | Python ↔ Java 26 个点（20 正算 + 6 反解）全部一致；含 怪物 `60/10`→EA 100→90.00%、`80/4`→EA 101.33→90.00%（截断） |
| 文档三方一致 | 规格 txt 的参考表 / Python `--selftest` / Java `--table` 三处逐行核对一致（新增"封顶 100/0 → 90.00%"行） |
| 未做 | 玩家侧实机曲线：无头服务端没有玩家实体，需在单人世界人工核对一次（代码与怪物侧共用，仅分支不同） |

### 定名 Summy Threat 之前（历史：默认值 100 / 100000 时期）

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
