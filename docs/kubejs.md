# Summy Threat KubeJS API

> 适用版本：Minecraft 1.20.1 Forge，KubeJS `2001.6.5-build.16` 及以上。
> KubeJS 是可选依赖；未安装时 ST 行为不变，也不会加载 KubeJS 类。

## 入口

- `server_scripts`：全局 `SummyThreat`、`SummyThreatConfig`、`SummyThreatEvents`。
- `client_scripts`：只读 `SummyThreat`，用于查询已同步的自定义能力/原型名称和颜色。
- ST 的 Java API 位于 `com.summy.threat.api`。

## 服务端 SummyThreat

读取：

```js
SummyThreat.tier()
SummyThreat.globalTl()
SummyThreat.rHp()
SummyThreat.rAtk()
SummyThreat.tl(entity)
SummyThreat.kind(entity)
SummyThreat.isScaled(entity)
SummyThreat.abilities(entity)
SummyThreat.hasAbility(entity, 'pack:ability')
SummyThreat.archetype(entity)
SummyThreat.pcf(player)
SummyThreat.cdf(level, pos)
SummyThreat.playerShield(player)
SummyThreat.eximusShield(entity)
```

写入：

```js
SummyThreat.setTier('free')
SummyThreat.setGlobalTl(20)
SummyThreat.setPcf(player, 12.5)
SummyThreat.setCdf(level, pos, 8.0)
SummyThreat.setPlayerShield(player, 50)
SummyThreat.scale(entity, 20, 'normal')
SummyThreat.setAbilities(entity, ['swift', 'pack:ability'])
SummyThreat.addAbility(entity, 'pack:ability')
SummyThreat.removeAbility(entity, 'pack:ability')
SummyThreat.clearAbilities(entity)
SummyThreat.setArchetype(entity, 'pack:archetype')
SummyThreat.hurtWithArmorPenetration(target, source, 100.0, 0.30)
SummyThreat.applyCorrosion(target)
SummyThreat.clearCorrosion(target)
```

`spawn` 同时支持完整目标参数：

```js
const zombie = SummyThreat.spawn(
  event.server.overworld(),
  'minecraft:zombie',
  100, 70, 100,
  20,
  'eximus',
  20,
  ['pack:manual'],
  'pack:custom'
)
```

## 自定义能力

```js
SummyThreat.registerAbility(
  'pack:afterimage', // 必须 namespace:path
  12,                // unlockTl
  '残影',             // displayName
  0x55AAFF,          // color
  false,             // rollable，默认应为 false
  1,                 // weight
  false              // eximusOnly
)

SummyThreat.unregisterAbility('pack:afterimage')
SummyThreat.customAbilities()
```

自定义能力只注册元数据。技能行为由标准 KubeJS 事件读取实体状态后实现：

```js
EntityEvents.hurt('minecraft:zombie', event => {
  if (SummyThreat.hasAbility(event.entity, 'pack:afterimage')) {
    // 自定义行为
  }
})
```

## 自定义原型

```js
SummyThreat.registerArchetype('pack:custom', '自定义原型', 0x33CC99, false, 1)
SummyThreat.unregisterArchetype('pack:custom')
SummyThreat.customArchetypes()
```

自定义原型不会自动执行任何内置 8 原型技能。脚本应通过 `SummyThreat.archetype(entity)` 判断目标原型。

## 事件

KubeJS 事件名采用扁平形式：

```js
SummyThreatEvents.preScale(event => {
  if (SummyThreat.archetype(event.entity) === 'pack:custom') {
    event.tl = Math.min(event.tl, 12)
    // event.canceled = true 可取消整个数值化
  }
})

SummyThreatEvents.postScale(event => {
  console.info(`${event.kind} TL ${event.tl}`)
})

SummyThreatEvents.abilityChanged(event => {
  console.info(`${event.oldAbilities} -> ${event.newAbilities}`)
})

SummyThreatEvents.archetypeChanged(event => {
  console.info(`${event.oldArchetype} -> ${event.newArchetype}`)
})

SummyThreatEvents.configChanged(event => {
  console.info(`${event.source}: ${Object.keys(event.newValues)}`)
})
```

## SummyThreatConfig

`SummyThreatConfig` 仅在服务端脚本可用。

```js
SummyThreatConfig.isLoaded()
SummyThreatConfig.groups()
SummyThreatConfig.keys('eximus')
SummyThreatConfig.type('eximus.natural_spawn_chance')
SummyThreatConfig.get('eximus.natural_spawn_chance')
SummyThreatConfig.defaultValue('eximus.natural_spawn_chance')
SummyThreatConfig.set('eximus.natural_spawn_chance', 0.10)
SummyThreatConfig.reset('eximus.natural_spawn_chance')
SummyThreatConfig.snapshot()
```

批量写入：

```js
const changes = {}
changes['player_challenge_factor.resource_weight'] = 0.31
changes['region_tl_cap.normal_biome'] = 8
SummyThreatConfig.setMany(changes)
```

注意：

- 脚本加载阶段只能读取配置默认值；写配置必须放在 `ServerEvents.loaded` 之后。
- `setMany` 先验证全部键，成功后才写入并保存。
- 配置立即作用于之后的生成和计算，不回溯已有实体。
- `configChanged` 内再次写配置会被拒绝，防止递归。

## 重载与持久性

- 每次 server scripts 重载前清除自定义能力/原型定义，再由脚本重新注册。
- 实体 NBT 中的旧自定义 ID 会保留；定义缺失时行为暂停，重新注册同名 ID 后恢复。
- 自定义 ID 必须为小写 `namespace:path`；内置能力和原型 ID 不能覆盖。
- 自定义能力/原型默认不进入随机池，只有 `rollable=true` 才参与加权随机。
