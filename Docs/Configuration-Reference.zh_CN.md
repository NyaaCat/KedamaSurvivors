# KedamaSurvivors 配置参考

本文档以当前代码实现为准。

主要配置来源：

- `config.yml`（运行时与玩法参数）
- `data/worlds.yml`（战斗地图）
- `data/starters.yml`（初始装备选项）
- `data/archetypes.yml`（刷怪原型）
- `data/equipment/weapons.yml`、`data/equipment/helmets.yml`（装备池）
- `data/merchants.yml`、`data/merchant_pools.yml`（商店模板与商品池）

## 1. 主配置（`config.yml`）

## 1.1 plugin

```yaml
plugin:
  language: zh_CN
  verbose: false
```

- `language`：语言文件键（`lang/` 下）
- `verbose`：详细日志开关

## 1.2 joinSwitch

```yaml
joinSwitch:
  enabled: true
  graceEjectSeconds: 60
  graceWarningInterval: 15
```

全局入场开关与维护踢出宽限参数。

## 1.3 ready

进入战斗前倒计时参数。

关键字段：

- `countdownSeconds`
- `showActionBar`
- `showTitle`
- `countdownSound`
- `countdownSoundPitch`
- `teleportSound`

## 1.4 cooldown

- `deathCooldownSeconds`
- `quitCooldownSeconds`
- `showCooldownBar`
- `displayUpdateTicks`

## 1.5 disconnect

- `graceSeconds`
- `checkIntervalTicks`
- `notifyTeam`
- `notifyGraceExpired`

## 1.6 teams

- `maxSize`
- `inviteExpirySeconds`

## 1.7 respawn

- `invulnerabilitySeconds`
- `canDealDamageDuringInvul`
- `invulEffect`
- `pvp`

## 1.8 worlds（仅采样参数）

`config.yml` 中的 `worlds` 只保留采样默认值；地图清单在 `data/worlds.yml`。

## 1.9 teleport

- `lobbyWorld`, `lobbyX`, `lobbyY`, `lobbyZ`
- `prepCommand`, `enterCommand`, `respawnCommand`
- `teamSpawnOffsetRange`

## 1.10 starterSelection

- `requireWeaponFirst`
- `autoOpenHelmetGui`

## 1.11 worldSelection

- `enabled`
- `autoOpenAfterHelmet`

注意：启用阶段推进后，实际进图优先使用阶段组 world 规则。

## 1.12 stageProgression（核心战役系统）

```yaml
stageProgression:
  groups:
    - id: "fragment_1"
      displayName: "世界碎片 - 扩展区 1"
      worlds: ["arena_a", "arena_b"]
      startEnemyLevel: 1
      requiredBatteries: 1
      clearRewardCoins: 100
      clearRewardPermaScore: 10
  finalBonus:
    coins: 300
    permaScore: 30
```

每组字段：

- `id`：组唯一标识（用于运行时命令）
- `displayName`：展示名
- `worlds`：可配置多个 world；为空表示不限制
- `startEnemyLevel`：本组怪物起始等级
- `requiredBatteries`：撤离所需电池数量
- `clearRewardCoins` / `clearRewardPermaScore`：过组奖励

最终奖励字段：

- `finalBonus.coins`
- `finalBonus.permaScore`

校验规则：

- 同一 world 只能属于一个阶段组（忽略大小写）
- 重复分配会在加载和热更新时被拒绝

## 1.13 battery（电池撤离目标）

```yaml
battery:
  enabled: true
  spawn:
    intervalSeconds: 20
    chance: 0.05
    minDistance: 35.0
    maxDistance: 90.0
    verticalRange: 10
  charge:
    radius: 8.0
    basePercentPerSecond: 1.0
    extraPlayerPercentPerSecond: 0.1
    updateTicks: 10
  display:
    material: BEACON
    customModelData: 0
    name: "§b能量电池"
    showRangeParticles: true
  surge:
    enabled: true
    intervalSeconds: 5
    mobCount: 6
```

行为映射：

- 电池在距离玩家一定范围刷新，不会贴脸刷
- 充能推进条件：
  - 圈内至少 1 名 `IN_RUN` 玩家
  - 圈内没有 VRS 怪物
- 充能速度：
  - `basePercentPerSecond + (playersInRange - 1) * extraPlayerPercentPerSecond`
- 充满后：
  - 所有在线且在 run 内的队员都要交互
  - 完成 1 次电池目标计数

## 1.14 spawnTracking / overheadDisplay

- `spawnTracking.defaultRadiusBlocks`
- `overheadDisplay.enabled`
- `overheadDisplay.yOffset`
- `overheadDisplay.updateIntervalTicks`

## 1.15 inventoryRules

实际限制主要由监听器硬实现：

- 非大厅模式禁丢弃/禁拾取
- 限制模式下保护关键装备槽
- 非大厅禁开末影箱

`inventoryRules` 保留了锁槽和奖励溢出策略参数。

## 1.16 progression

- 经验曲线：`baseXpRequired`, `xpPerLevelIncrease`, `xpMultiplierPerLevel`
- 权重：`weaponLevelWeight`, `helmetLevelWeight`
- 溢出经验转化：`overflow.*`
- 满级行为：`maxLevelBehavior.*`

## 1.17 upgrade

- `timeoutSeconds`
- `reminderIntervalSeconds`
- `bothMaxPermaReward`

## 1.18 spawning

主要分组：

- `loop`：主循环开关与周期、自然刷怪拦截
- `limits`：目标怪量与预算上限
- `positioning`：刷怪距离/尝试次数/垂直范围/LOS
- `levelCalculation`：敌人等级公式与时间缩放
- `mobIdentification`：标记识别参数

具体 archetype 在 `data/archetypes.yml`。

## 1.19 rewards

- `xpShare.*`
- `damageContribution.*`
- `multiplier.*`（运行时奖励倍率）
- coin 展示字段

## 1.20 merchants

流浪商店生成与展示参数：

- 出现周期/概率/数量上限
- 停留时间与距离
- 商品数量限制
- 动画参数

商店模板和商品池在 `data/merchants.yml` 与 `data/merchant_pools.yml`。

## 1.21 economy

模式：

- `VAULT`（不可用时回退到 INTERNAL）
- `INTERNAL`
- `ITEM`

同时定义 coin 物品识别与 perma-score 计分板目标。

## 1.22 scoreboard

- `enabled`
- `title`
- `updateInterval`

## 1.23 persistence

- 自动保存间隔与触发点
- runtime/items 路径
- 备份参数（当前备份轮转逻辑在 `PersistenceService`）

## 1.24 templates

命令模板转义和缺失占位符策略。

## 1.25 feedback

- 奖励显示模式（`ACTIONBAR` / `CHAT`）
- 奖励堆叠窗口
- 升级提醒模式（`CHAT` / `SCOREBOARD`）
- 音效表（`"sound volume pitch"`）

## 1.26 debug

调试日志相关开关。

## 2. 数据文件说明

## 2.1 `data/worlds.yml`

每个 world 项：

- `name`, `displayName`, `enabled`, `weight`, `cost`
- `spawnBounds.minX/maxX/minZ/maxZ`
- `spawnPoints[]`（`x y z [yaw pitch]`）

## 2.2 `data/starters.yml`

顶层：

- `weapons[]`
- `helmets[]`

每项字段：

- `optionId`, `displayName`, `templateId`, `group`, `level`
- 可选 `displayItem`（GUI 图标）

## 2.3 `data/archetypes.yml`

每个 archetype：

- `entityType`, `weight`
- `minSpawnLevel`
- `allowedWorlds`（支持多个 world 或 `any`）
- `spawnCommands[]`
- 奖励元组：
  - `xpAmount`, `xpChance`
  - `coinAmount`, `coinChance`
  - `permaScoreAmount`, `permaScoreChance`

## 2.4 装备池文件

- `data/equipment/weapons.yml`
- `data/equipment/helmets.yml`

结构：组 -> 等级 -> 模板 id 列表。

## 2.5 商店数据文件

- `data/merchants.yml`：商店模板与交易
- `data/merchant_pools.yml`：权重商品池

## 3. 热更新矩阵

## 3.1 无需重启（命令级热更）

- `/vrs admin world ...`（地图数据）
- `/vrs admin starter ...`（starter）
- `/vrs admin equipment ...`（装备池）
- `/vrs admin spawner archetype ...`（含多 world 限制）
- `/vrs admin merchant ...`（商店模板/池）
- `/vrs admin config set ...`（主配置字段）

## 3.2 阶段/电池热更

示例：

```text
/vrs admin config set stage.fragment_3.worlds arena_4,arena_5
/vrs admin config set stage.fragment_3.requiredBatteries 4
/vrs admin config set batterySpawnChance 0.07
```

电池参数热更会对活跃 run 任务进行重绑。

## 3.3 多参数列表输入

- `admin config list` 支持一次多个分类
- `stage.<id>.worlds` 支持多参数 world 列表
- `spawner archetype set worlds` 支持多 world 参数

## 4. 运行时持久化 JSON 结构

`data/runtime/players/<uuid>.json` 包含：

- 玩家经济/永久积分
- starter 选择
- 冷却状态（仍有效时）
- 扩展统计（`runCount`、`failedRunCount`、电池/阶段/战役指标等）

`data/runtime/teams.json` 包含：

- `teamId`, `leaderId`, members
- `stageIndex`
- `progressionLocked`

## 5. 校验与保护规则

- 阶段 world 重复分配会被拒绝
- 阶段数值由运行时 setter 进行下限保护（`>=1` 或 `>=0`）
- 电池概率/比例会被裁剪到合法范围
- 实际选图只会选加载且启用的 world

## 6. 阶段配置建议

生产环境建议：

- 至少 5 个阶段组
- 各组 world 不重叠
- 逐组提高 `startEnemyLevel` 和 `requiredBatteries`
- 合理配置 `clearReward*` 与 `finalBonus`
