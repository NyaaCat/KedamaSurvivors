# 毛玉幸存者 - 配置参考手册

本文档为 `config.yml` 中所有配置选项的完整参考手册。

---

## 目录

1. [插件设置](#1-插件设置)
2. [游戏入口开关](#2-游戏入口开关)
3. [准备与倒计时](#3-准备与倒计时)
4. [冷却时间](#4-冷却时间)
5. [断线处理](#5-断线处理)
6. [重生与PVP](#6-重生与pvp)
7. [战斗世界](#7-战斗世界)
8. [传送设置](#8-传送设置)
9. [初始装备选择](#9-初始装备选择)
10. [装备池](#10-装备池)
11. [背包规则](#11-背包规则)
12. [进度系统](#12-进度系统)
13. [敌人生成](#13-敌人生成)
14. [奖励系统](#14-奖励系统)
15. [商人系统](#15-商人系统)
16. [经济系统](#16-经济系统)
17. [记分板](#17-记分板)
18. [数据持久化](#18-数据持久化)
19. [命令模板](#19-命令模板)
20. [调试选项](#20-调试选项)
21. [反馈与通知](#21-反馈与通知)

---

## 1. 插件设置

```yaml
plugin:
  # 使用的语言文件（不含 .yml 扩展名）
  # 文件应位于 plugins/KedamaSurvivors/lang/ 目录下
  language: zh_CN

  # 所有插件消息的前缀
  # 支持使用 § 或 & 的颜色代码
  prefix: "§8[§6武§e生§8] §7"

  # 启用详细日志用于调试
  verbose: false
```

---

## 2. 游戏入口开关

控制是否允许新玩家进入游戏的全局开关。

```yaml
joinSwitch:
  # 玩家当前是否可以加入游戏
  # 可通过 /vrs join enable|disable 切换
  enabled: true

  # 禁用时将玩家传送出去前的宽限期（秒）
  graceEjectSeconds: 60

  # 宽限期内的消息间隔（秒）
  graceWarningInterval: 15
```

---

## 3. 准备与倒计时

准备检查和倒计时系统的设置。

```yaml
ready:
  # 传送到战斗前的倒计时时长（秒）
  countdownSeconds: 5

  # 在动作栏显示倒计时
  showActionBar: true

  # 以标题形式显示倒计时
  showTitle: true

  # 倒计时期间播放的声音
  countdownSound: BLOCK_NOTE_BLOCK_PLING

  # 声音音调（0.5 - 2.0）
  countdownSoundPitch: 1.0

  # 传送时的最终声音
  teleportSound: ENTITY_ENDERMAN_TELEPORT
```

---

## 4. 冷却时间

死亡冷却设置。

```yaml
cooldown:
  # 死亡后重新加入前的冷却时长（秒）
  deathCooldownSeconds: 60

  # 主动退出的冷却时长（秒）
  # 设为 0 禁用退出冷却
  quitCooldownSeconds: 30

  # 在动作栏显示冷却
  showCooldownBar: true

  # 冷却显示的更新间隔（游戏刻）
  displayUpdateTicks: 20
```

---

## 5. 断线处理

活动游戏期间的断线处理。

```yaml
disconnect:
  # 重连玩家的宽限期（秒）
  # 在此时间内返回的玩家可重新加入游戏
  # 宽限期过后，视为死亡
  graceSeconds: 300  # 5分钟

  # 检查过期宽限期的频率（游戏刻）
  checkIntervalTicks: 200  # 10秒

  # 玩家断线时通知队伍
  notifyTeam: true

  # 断线玩家宽限期过期时通知队伍
  notifyGraceExpired: true
```

---

## 6. 重生与PVP

重生机制和玩家伤害设置。

```yaml
respawn:
  # 重生后的无敌时长（秒）
  invulnerabilitySeconds: 3

  # 无敌玩家是否可以造成伤害
  canDealDamageDuringInvul: false

  # 无敌期间的视觉效果
  invulEffect: GLOWING

  # PVP 伤害控制
  pvp: false  # 玩家是否可以相互伤害（false = 禁用PVP）
```

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `invulnerabilitySeconds` | 整数 | `3` | 重生后无敌持续时间 |
| `canDealDamageDuringInvul` | 布尔值 | `false` | 允许无敌玩家造成伤害 |
| `invulEffect` | 字符串 | `GLOWING` | 无敌期间的视觉效果 |
| `pvp` | 布尔值 | `false` | 启用玩家间伤害。为 false 时，玩家间伤害设为 0。 |

---

## 7. 战斗世界

战斗世界（竞技场）的配置。

```yaml
worlds:
  # 战斗世界列表
  list:
    - name: "arena_forest"
      displayName: "§2森林竞技场"
      enabled: true
      weight: 1.0
      spawnBounds:
        minX: -500
        maxX: 500
        minZ: -500
        maxZ: 500
      # 出生点（至少需要一个）
      spawnPoints:
        - x: 100
          y: 64
          z: 100
          yaw: 0
          pitch: 0
        - x: -100
          y: 64
          z: -100
          yaw: 180
          pitch: 0
      # 备用出生点（可选）- 随机采样失败时使用
      fallbackX: 0
      fallbackY: 64
      fallbackZ: 0
      fallbackYaw: 0
      fallbackPitch: 0

    - name: "arena_desert"
      displayName: "§6沙漠竞技场"
      enabled: true
      weight: 1.0
      spawnBounds:
        minX: -300
        maxX: 300
        minZ: -300
        maxZ: 300
      spawnPoints:
        - x: 0
          y: 64
          z: 0

    - name: "arena_nether"
      displayName: "§c地狱竞技场"
      enabled: false  # 禁用 - 不会被选中
      weight: 0.5     # 启用时权重较低
      spawnBounds:
        minX: -200
        maxX: 200
        minZ: -200
        maxZ: 200
      spawnPoints:
        - x: 0
          y: 64
          z: 0

  # 出生位置采样设置
  spawnSampling:
    # 寻找安全出生点的最大尝试次数
    maxAttempts: 50

    # 避免生成在上面的方块
    unsafeBlocks:
      - LAVA
      - WATER
      - CACTUS
      - MAGMA_BLOCK
      - FIRE
      - SOUL_FIRE
      - CAMPFIRE
      - SOUL_CAMPFIRE

    # 所需的最小头顶空间（方块数）
    requiredHeadroom: 2
```

**世界配置字段:**

| 字段 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `name` | 字符串 | 是 | Bukkit 世界名称（必须存在） |
| `displayName` | 字符串 | 否 | 显示给玩家的名称（支持颜色代码） |
| `enabled` | 布尔值 | 是 | 此世界是否可被选中 |
| `weight` | 数值 | 否 | 选择概率（越高越可能被选中，默认 1.0） |
| `spawnBounds` | 对象 | 是 | 定义有效出生区域的矩形 |
| `spawnPoints` | 列表 | 是 | 出生点列表（至少需要一个） |
| `fallbackX/Y/Z/Yaw/Pitch` | 数值 | 否 | 随机采样失败时的备用出生点 |

**出生点字段:**

| 字段 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `x`, `y`, `z` | 数值 | 是 | 出生坐标 |
| `yaw` | 浮点数 | 否 | 玩家朝向（0-360，默认 0） |
| `pitch` | 浮点数 | 否 | 玩家俯仰角（-90 到 90，默认 0） |

---

## 8. 传送设置

传送命令模板。

```yaml
teleport:
  # 大厅位置 - 退出或死亡后玩家去往的地方
  lobbyWorld: "world"
  lobbyX: 0
  lobbyY: 64
  lobbyZ: 0

  # 可选命令 - API 处理传送，这些仅用于额外设置
  # 使用 {player}, {world}, {x}, {y}, {z}, {yaw}, {pitch} 占位符
  prepCommand: ""
  enterCommand: ""
  respawnCommand: ""

  # 队伍出生位置分散的最大偏移量（方块）（防止玩家堆叠）
  teamSpawnOffsetRange: 2.0
```

**传送命令变量:**

| 变量 | 说明 |
|------|------|
| `${player}` | 玩家名称 |
| `${world}` | 世界名称 |
| `${x}`, `${y}`, `${z}` | 坐标 |
| `${yaw}`, `${pitch}` | 旋转角度 |

---

## 9. 初始装备选择

初始武器和护甲配置。

```yaml
starterSelection:
  # 选择护甲前需要先选择武器
  requireWeaponFirst: true

  # 武器选择后自动打开护甲界面
  autoOpenHelmetGui: true

  # 初始武器选项
  weapons:
    - optionId: "starter_sword"
      displayName: "§f铁剑"
      displayItem:
        material: IRON_SWORD
        name: "§f铁剑"
        lore:
          - "§7基础近战武器"
          - "§7适合新手使用"
      templateId: "sword_iron_1"
      group: "sword"
      level: 1

    - optionId: "starter_bow"
      displayName: "§f弓"
      displayItem:
        material: BOW
        name: "§f弓"
        lore:
          - "§7基础远程武器"
          - "§7需要箭矢"
      templateId: "bow_basic_1"
      group: "bow"
      level: 1

    - optionId: "starter_axe"
      displayName: "§f铁斧"
      displayItem:
        material: IRON_AXE
        name: "§f铁斧"
        lore:
          - "§7高伤害近战武器"
          - "§7攻击较慢"
      templateId: "axe_iron_1"
      group: "axe"
      level: 1

  # 初始护甲选项
  helmets:
    - optionId: "starter_leather"
      displayName: "§f皮革帽"
      displayItem:
        material: LEATHER_HELMET
        name: "§f皮革帽"
        lore:
          - "§7基础防护"
          - "§7轻便灵活"
      templateId: "helmet_leather_1"
      group: "light"
      level: 1

    - optionId: "starter_iron"
      displayName: "§f铁头盔"
      displayItem:
        material: IRON_HELMET
        name: "§f铁头盔"
        lore:
          - "§7中等防护"
          - "§7平衡选择"
      templateId: "helmet_iron_1"
      group: "medium"
      level: 1

    - optionId: "starter_chain"
      displayName: "§f锁链头盔"
      displayItem:
        material: CHAINMAIL_HELMET
        name: "§f锁链头盔"
        lore:
          - "§7特殊防护"
          - "§7抗击退增强"
      templateId: "helmet_chain_1"
      group: "chain"
      level: 1
```

**注意:** 初始装备选项现在存储在 `data/starters.yml` 中。

---

## 10. 装备池

进度系统的装备组定义。

```yaml
equipmentPools:
  # 武器组
  weapons:
    sword:
      displayName: "剑系"
      levels:
        1:
          - "sword_iron_1"
        2:
          - "sword_iron_2"
          - "sword_stone_sharp_2"
        3:
          - "sword_diamond_3"
          - "sword_iron_fire_3"
        4:
          - "sword_diamond_sharp_4"
          - "sword_netherite_4"
        5:
          - "sword_netherite_max_5"

    bow:
      displayName: "弓系"
      levels:
        1:
          - "bow_basic_1"
        2:
          - "bow_power_2"
        3:
          - "bow_power_flame_3"
          - "bow_infinity_3"
        4:
          - "bow_power_punch_4"

    axe:
      displayName: "斧系"
      levels:
        1:
          - "axe_iron_1"
        2:
          - "axe_diamond_2"
        3:
          - "axe_diamond_sharp_3"
        4:
          - "axe_netherite_4"

  # 护甲组
  helmets:
    light:
      displayName: "轻甲系"
      levels:
        1:
          - "helmet_leather_1"
        2:
          - "helmet_leather_prot_2"
        3:
          - "helmet_gold_prot_3"
        4:
          - "helmet_leather_max_4"

    medium:
      displayName: "中甲系"
      levels:
        1:
          - "helmet_iron_1"
        2:
          - "helmet_iron_prot_2"
        3:
          - "helmet_diamond_3"
        4:
          - "helmet_diamond_prot_4"
        5:
          - "helmet_netherite_5"

    chain:
      displayName: "锁链系"
      levels:
        1:
          - "helmet_chain_1"
        2:
          - "helmet_chain_prot_2"
        3:
          - "helmet_chain_thorns_3"
```

**注意:** 装备池现在存储在 `data/equipment/weapons.yml` 和 `data/equipment/helmets.yml` 中。

---

## 11. 背包规则

游戏期间的物品处理规则。

```yaml
inventoryRules:
  # 锁定装备栏位（防止移动武器/护甲）
  lockEquipmentSlots: true

  # 武器栏位索引（主手快捷栏位置）
  weaponSlotIndex: 0

  # 直接入背包奖励处理
  rewards:
    # 如何处理背包溢出
    # PENDING_QUEUE: 存储待后续发放
    # CONVERT_TO_COIN: 转换为金币价值
    # DISCARD: 掉落在地上（不推荐）
    overflowMode: PENDING_QUEUE

    # 尝试发放待处理奖励的间隔（游戏刻）
    pendingFlushInterval: 100

    # 每个玩家最大待处理物品数
    maxPendingItems: 100
```

**注意:** 物品丢弃/拾取阻止现在已硬编码适用于除 LOBBY 外的所有模式。

---

## 12. 进度系统

经验值和升级配置。

```yaml
progression:
  # 首次升级所需的基础经验值
  baseXpRequired: 100

  # 每级经验值增加量（累加）
  xpPerLevelIncrease: 50

  # 每级经验值乘数
  xpMultiplierPerLevel: 1.1

  # 公式: 所需经验 = 基础经验 + (等级 × 每级增加量) × 乘数^等级

  # 武器等级在玩家等级计算中的权重
  weaponLevelWeight: 1

  # 护甲等级在玩家等级计算中的权重
  helmetLevelWeight: 1

  # 玩家等级 = 武器等级 × 武器权重 + 护甲等级 × 护甲权重

  # 溢出经验处理（当两个栏位都达到最高等级时）
  overflow:
    # 启用溢出经验 -> 永久积分转换
    enabled: true

    # 每点永久积分所需经验
    xpPerPermaScore: 1000

    # 获得永久积分时通知玩家
    notifyPlayer: true

  # 最高等级升级行为（当不存在下一等级时）
  maxLevelBehavior:
    # 最高等级时升级的处理方式
    # GRANT_PERMA_SCORE: 给予永久积分
    # REROLL_SAME_LEVEL: 用同等级不同物品重新roll
    # NOOP: 不做任何操作
    mode: GRANT_PERMA_SCORE

    # 最高等级时给予的永久积分
    permaScoreReward: 10
```

---

## 13. 敌人生成

敌人生成配置。

```yaml
spawning:
  # 主生成循环设置
  loop:
    # 生成刻之间的间隔（游戏刻）
    tickInterval: 20  # 1秒

    # 启用生成
    enabled: true

    # 在战斗世界阻止自然怪物生成
    blockNaturalSpawns: true

  # 生成上限和限制
  limits:
    # 每玩家目标 VRS 怪物数量
    targetMobsPerPlayer: 10

    # 每刻每玩家最大生成数
    maxSpawnsPerPlayerPerTick: 3

    # 每刻最大总生成数（全局）
    maxSpawnsPerTick: 20

    # 每刻最大命令派发数
    maxCommandsPerTick: 50

    # 计算现有怪物的半径
    mobCountRadius: 30.0

  # 生成位置采样
  positioning:
    # 距玩家的最小生成距离
    minSpawnDistance: 8.0

    # 距玩家的最大生成距离
    maxSpawnDistance: 25.0

    # 寻找有效生成位置的最大尝试次数
    maxSampleAttempts: 10

  # 敌人等级计算
  levelCalculation:
    # 采样玩家等级的半径
    levelSamplingRadius: 50.0

    # 平均玩家等级乘数
    avgLevelMultiplier: 1.0

    # 每个半径内玩家的加成
    playerCountMultiplier: 0.2

    # 基础等级偏移
    levelOffset: 0

    # 最低敌人等级
    minLevel: 1

    # 最高敌人等级
    maxLevel: 100

    # 时间基础缩放
    timeScaling:
      # 启用基于时间的等级增加
      enabled: true

      # 每个时间步长的秒数
      timeStepSeconds: 60

      # 每个时间步长的等级增加
      levelPerStep: 1

  # 敌人原型
  archetypes:
    zombie:
      enemyType: "minecraft:zombie"
      weight: 3.0
      minSpawnLevel: 1  # 从开始就可用
      allowedWorlds:    # 此原型可生成的战斗世界
        - "any"         # "any" = 所有战斗世界（默认）
      spawnCommands:
        - "summon zombie {sx} {sy} {sz} {Tags:[\"vrs_mob\",\"vrs_lvl_{enemyLevel}\",\"vrs_arch_{archetypeId}\"]}"
      rewards:
        xpAmount: 10        # 固定经验奖励
        xpChance: 1.0       # 奖励经验的概率（0-1）
        coinAmount: 1       # 固定金币奖励
        coinChance: 1.0     # 奖励金币的概率（0-1）
        permaScoreAmount: 1 # 固定永久积分奖励
        permaScoreChance: 0.01  # 奖励永久积分的概率（0-1）

    skeleton:
      enemyType: "minecraft:skeleton"
      weight: 2.0
      minSpawnLevel: 5  # 仅在敌人等级 5+ 时生成
      allowedWorlds:
        - "any"
      spawnCommands:
        - "summon skeleton {sx} {sy} {sz} {Tags:[\"vrs_mob\",\"vrs_lvl_{enemyLevel}\",\"vrs_arch_{archetypeId}\"]}"
      rewards:
        xpAmount: 15
        xpChance: 1.0
        coinAmount: 2
        coinChance: 0.8
        permaScoreAmount: 1
        permaScoreChance: 0.02

    spider:
      enemyType: "minecraft:spider"
      weight: 1.5
      minSpawnLevel: 1
      allowedWorlds:
        - "any"
      spawnCommands:
        - "summon spider {sx} {sy} {sz} {Tags:[\"vrs_mob\",\"vrs_lvl_{enemyLevel}\",\"vrs_arch_{archetypeId}\"]}"
      rewards:
        xpAmount: 12
        xpChance: 1.0
        coinAmount: 1
        coinChance: 1.0
        permaScoreAmount: 1
        permaScoreChance: 0.01

    creeper:
      enemyType: "minecraft:creeper"
      weight: 0.5
      minSpawnLevel: 10  # Boss级，较晚出现
      allowedWorlds:
        - "any"
      spawnCommands:
        - "summon creeper {sx} {sy} {sz} {Tags:[\"vrs_mob\",\"vrs_lvl_{enemyLevel}\",\"vrs_arch_{archetypeId}\"],ExplosionRadius:0}"
      rewards:
        xpAmount: 25
        xpChance: 1.0
        coinAmount: 5
        coinChance: 1.0
        permaScoreAmount: 2
        permaScoreChance: 0.05

    wither_skeleton:
      enemyType: "minecraft:wither_skeleton"
      weight: 1.0
      minSpawnLevel: 15  # 稀有，等级 15+ 出现
      allowedWorlds:     # 仅在地狱主题世界生成
        - "arena_nether"
        - "arena_hell"
      spawnCommands:
        - "summon wither_skeleton {sx} {sy} {sz} {Tags:[\"vrs_mob\",\"vrs_lvl_{enemyLevel}\",\"vrs_arch_{archetypeId}\"]}"
      rewards:
        xpAmount: 30
        xpChance: 1.0
        coinAmount: 5
        coinChance: 1.0
        permaScoreAmount: 2
        permaScoreChance: 0.05

  # 怪物识别
  mobIdentification:
    # 识别 VRS 怪物的标签
    mobTag: "vrs_mob"

    # 敌人等级的标签模式
    levelTagPattern: "vrs_lvl_(\\d+)"

    # 解析失败时的默认等级
    defaultLevel: 1
```

**等级门控:** `minSpawnLevel` 属性控制原型何时可用。只有 `minSpawnLevel <= 当前敌人等级` 的原型才会被考虑生成。加权池从符合条件的原型动态重新计算。

**世界限制:** `allowedWorlds` 属性控制原型可在哪些战斗世界生成：
- 使用 `["any"]`（默认）允许在所有战斗世界生成
- 使用特定世界名（如 `["arena_nether", "arena_hell"]`）限制仅在这些世界生成
- 世界名匹配不区分大小写
- 示例：地狱主题怪物如 wither_skeleton 可限制在地狱风格竞技场

**奖励系统:** 每种奖励类型（经验、金币、永久积分）独立roll：
- 判定 `random(0,1) < 概率` → 给予固定数量
- 无等级缩放 - 奖励是带概率的固定值

**可用生成命令占位符:**

| 占位符 | 说明 |
|--------|------|
| `{sx}`, `{sy}`, `{sz}` | 生成坐标 |
| `{enemyLevel}` | 计算的敌人等级 |
| `{runWorld}` | 战斗世界名称 |
| `{enemyType}` | 原型配置中的实体类型 |
| `{archetypeId}` | 原型 ID（用于 Tags 进行奖励查找） |

**注意:** 原型现在存储在 `data/archetypes.yml` 中。

---

## 14. 奖励系统

经验和金币奖励设置。

```yaml
rewards:
  # 与附近玩家共享经验
  xpShare:
    # 启用经验共享
    enabled: true

    # 共享经验的半径
    radius: 20.0

    # 共享击杀者经验的百分比（0.0 - 1.0）
    sharePercent: 0.25

  # 伤害贡献经验共享：对怪物造成过伤害的玩家获得经验
  # 与 xpShare 独立工作 - 玩家可以同时从两个系统获得经验
  damageContribution:
    enabled: true
    sharePercent: 0.10  # 基础经验的 10% 给予每个贡献者

  # 金币物品设置
  coin:
    # 金币使用的材质
    material: EMERALD

    # 自定义模型数据（用于资源包集成）
    customModelData: 0

    # 显示名称
    displayName: "§e金币"
```

---

## 15. 商人系统

商人系统配置。商人显示为带有浮动/旋转动画的隐形盔甲架，在头部显示物品。

### 商人类型和行为

**类型:**

| 类型 | 说明 |
|------|------|
| `MULTI` | 打开包含多个物品的商店界面。玩家点击物品购买。库存根据权重从池中随机选择。 |
| `SINGLE` | 直接购买 - 右键点击商人立即购买。上方显示一个漂浮物品。 |

**行为:**

| 行为 | 说明 |
|------|------|
| `FIXED` | 固定位置的永久商人。通过 `/vrs admin merchant spawn` 创建。不会自动消失。 |
| `WANDERING` | 游戏期间在玩家附近随机生成的临时商人。配置时间后消失。 |

**库存模式:**

| 模式 | 说明 |
|------|------|
| `limited` | 物品购买后消失。所有物品售出后商人变空。 |
| `unlimited` | 物品购买后刷新。商人永不缺货。 |

### 配置

```yaml
merchants:
  # 启用商人系统
  enabled: true

  # 流浪商人生成行为
  wandering:
    spawnIntervalSeconds: 120    # 尝试生成的间隔
    spawnChance: 0.5             # 每次间隔生成的概率（0-1）
    maxCount: 3                  # 同时存在的最大流浪商人数（0 = 无限）
    stayTime:
      minSeconds: 60             # 最短停留时间
      maxSeconds: 120            # 最长停留时间
    distance:
      min: 20.0                  # 距玩家最小距离
      max: 50.0                  # 距玩家最大距离
    particles:
      spawn: true                # 商人出现时显示粒子
      despawn: true              # 商人离开时显示粒子

  # 商人库存设置
  stock:
    limited: true                # 物品购买后消失（true）或刷新（false）
    minItems: 3                  # 商店最少物品数（multi类型）
    maxItems: 6                  # 商店最多物品数（multi类型）

  # 显示设置（盔甲架动画）
  display:
    rotationSpeed: 3.0           # 每刻旋转角度
    bobHeight: 0.15              # 浮动动画幅度（方块）
    bobSpeed: 0.01               # 浮动动画速度
    headItemCycleIntervalTicks: 200  # 头部物品切换间隔（multi类型循环显示物品）
```

### 设置参考

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `enabled` | 布尔值 | `true` | 启用商人系统 |
| `wandering.spawnIntervalSeconds` | 整数 | `120` | 生成尝试间隔秒数 |
| `wandering.spawnChance` | 数值 | `0.5` | 每次间隔生成概率（0-1） |
| `wandering.maxCount` | 整数 | `3` | 同时最大流浪商人数量 |
| `wandering.stayTime.minSeconds` | 整数 | `60` | 流浪商人最短停留时间 |
| `wandering.stayTime.maxSeconds` | 整数 | `120` | 流浪商人最长停留时间 |
| `wandering.distance.min` | 数值 | `20.0` | 距玩家最小生成距离 |
| `wandering.distance.max` | 数值 | `50.0` | 距玩家最大生成距离 |
| `wandering.particles.spawn` | 布尔值 | `true` | 商人生成时显示粒子 |
| `wandering.particles.despawn` | 布尔值 | `true` | 商人消失时显示粒子 |
| `stock.limited` | 布尔值 | `true` | 默认库存模式（购买后物品消失） |
| `stock.minItems` | 整数 | `3` | MULTI商店最少物品数（随机选择模式） |
| `stock.maxItems` | 整数 | `6` | MULTI商店最多物品数（随机选择模式） |
| `display.rotationSpeed` | 浮点数 | `3.0` | 盔甲架旋转速度（角度/刻） |
| `display.bobHeight` | 数值 | `0.15` | 浮动动画幅度（方块） |
| `display.bobSpeed` | 数值 | `0.01` | 浮动动画速度 |
| `display.headItemCycleIntervalTicks` | 整数 | `200` | 头部物品循环间隔（MULTI类型循环显示物品） |

### 启用流浪商人

流浪商人需要显式池配置。要启用：

1. **创建池：**
   ```
   /vrs admin merchant pool create wandering_pool
   ```

2. **添加物品到池**（手持物品）：
   ```
   /vrs admin merchant pool additem wandering_pool 50 1.0
   ```

3. **配置流浪商人：**
   ```
   /vrs admin config set wanderingMerchantPoolId wandering_pool
   /vrs admin config set wanderingMerchantType single
   ```

### 运行时配置

所有商人设置可通过命令在运行时修改：

```
/vrs admin config list merchants           # 列出所有商人设置
/vrs admin config get merchantSpawnChance  # 获取特定值
/vrs admin config set merchantSpawnChance 0.75  # 设置值
/vrs admin config set wanderingMerchantPoolId my_pool  # 设置流浪池
/vrs admin config set wanderingMerchantType multi      # 设置流浪类型
```

### 物品池

商人物品池存储在 `data/merchant_pools.yml`。池定义商人可出售的加权物品集合。

**池结构：**
```yaml
pools:
  common_shop:
    items:
      - templateId: "golden_apple_001"
        weight: 1.0
        price: 25
      - templateId: "speed_potion_001"
        weight: 0.5
        price: 50
```

详见[命令参考](Commands-Reference.zh_CN.md#vrs-admin-merchant)的池管理命令。

---

## 16. 经济系统

支持多种模式的虚拟经济设置。

```yaml
economy:
  # 模式: VAULT（外部经济）, INTERNAL（插件内部每玩家管理）, ITEM（实体物品）
  mode: INTERNAL

  # 金币物品设置（用于 ITEM 模式和视觉显示）
  coin:
    material: EMERALD
    customModelData: 0
    displayName: "§e金币"
    nbtTag: "vrs_coin"           # 识别 VRS 金币的 NBT 标签

  permaScore:
    objectiveName: "vrs_perma"
    displayName: "永久积分"
```

**经济模式：**

| 模式 | 说明 |
|------|------|
| `VAULT` | 使用外部 Vault 兼容经济插件（如 EssentialsX Economy）。Vault 未安装时回退到 INTERNAL。 |
| `INTERNAL` | 插件在内部存储每玩家的金币余额。余额在 `players.json` 中跨会话持久化。默认模式。 |
| `ITEM` | 玩家背包中的实体金币物品。金币通过 `coin.nbtTag` 指定的 NBT 标签识别。 |

**经济设置：**

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `mode` | 字符串 | `INTERNAL` | 经济模式：VAULT、INTERNAL 或 ITEM |
| `coin.material` | 材质 | `EMERALD` | 金币物品的材质 |
| `coin.customModelData` | 整数 | `0` | 资源包的自定义模型数据 |
| `coin.displayName` | 字符串 | `§e金币` | 金币物品的显示名称 |
| `coin.nbtTag` | 字符串 | `vrs_coin` | 识别 VRS 金币物品的 NBT 标签 |
| `permaScore.objectiveName` | 字符串 | `vrs_perma` | 记分板目标名称 |
| `permaScore.displayName` | 字符串 | `永久积分` | 永久积分的显示名称 |

**注意：** 使用 VAULT 模式但 Vault 未安装时，插件自动回退到 INTERNAL 模式并记录警告。

---

## 17. 记分板

侧边栏显示配置。

```yaml
scoreboard:
  # 启用侧边栏显示
  enabled: true

  # 侧边栏标题
  title: "§6§l毛 玉 幸 存 者"

  # 更新间隔（游戏刻）
  updateInterval: 10

  # 显示的行（按顺序，从上到下）
  lines:
    - type: BLANK
    - type: WEAPON_LEVEL
      format: "§6武器等级 §f{level}"
    - type: HELMET_LEVEL
      format: "§b护甲等级 §f{level}"
    - type: XP_BAR
      format: "§a经验 §f{bar} {percent}%"
      barLength: 10
      barFilled: "▰"
      barEmpty: "▱"
    - type: COINS
      format: "§e金币 §f{amount}"
    - type: PERMA_SCORE
      format: "§d永久积分 §f{amount}"
    - type: BLANK
    - type: TEAM
      format: "§7队伍 §f{name} ({online}/{total})"
      hideIfSolo: true
    - type: RUN_TIME
      format: "§7时间 §f{time}"
```

---

## 18. 数据持久化

数据保存配置。

```yaml
persistence:
  # 自动保存间隔（秒）
  saveIntervalSeconds: 300  # 5分钟

  # 玩家退出时保存
  saveOnQuit: true

  # 游戏结束时保存
  saveOnRunEnd: true

  # 数据路径（相对于插件文件夹）
  paths:
    items: "data/items"
    runtime: "data/runtime"

  # 备份设置
  backup:
    # 启用自动备份
    enabled: true

    # 备份间隔（小时）
    intervalHours: 6

    # 保留的最大备份数
    maxBackups: 10
```

---

## 19. 命令模板

命令模板配置。

```yaml
templates:
  # 占位符转义
  escaping:
    # 启用占位符转义以防止注入
    enabled: true

    # 需要转义的字符
    escapeChars: ";&|`$\\"

  # 缺失占位符行为
  missingPlaceholder:
    # 占位符缺失时的处理方式
    # ERROR: 记录错误并跳过命令
    # EMPTY: 替换为空字符串
    # LITERAL: 保留占位符文本原样
    mode: ERROR

  # 自定义命令模板（可复用）
  custom:
    # 示例：带特效的传送
    fancy_teleport:
      - "particle minecraft:portal ${x} ${y} ${z} 1 1 1 0.1 50"
      - "playsound minecraft:entity.enderman.teleport player ${player}"
      - "tp ${player} ${x} ${y} ${z}"
```

---

## 20. 调试选项

调试和开发设置。

```yaml
debug:
  # 启用调试模式
  enabled: false

  # 日志级别（INFO, DEBUG, TRACE）
  logLevel: INFO

  # 记录到单独文件
  separateLogFile: true

  # 性能监控
  performance:
    # 启用性能跟踪
    enabled: true

    # 记录慢操作的阈值（毫秒）
    slowOperationThreshold: 50

    # 包含在 /vrs debug perf 输出中
    showInDebugCommand: true
```

---

## 21. 反馈与通知

奖励显示、升级提醒和音效设置。

### 奖励显示

```yaml
feedback:
  rewards:
    # 显示模式: ACTIONBAR（堆叠动作栏）或 CHAT（单独消息）
    displayMode: ACTIONBAR
    stacking:
      enabled: true
      # 时间窗口秒数 - 奖励在此期间内聚合
      timeoutSeconds: 3
```

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `displayMode` | 字符串 | `ACTIONBAR` | `ACTIONBAR` = 堆叠动作栏显示，`CHAT` = 单独聊天消息 |
| `stacking.enabled` | 布尔值 | `true` | 启用奖励堆叠（仅 ACTIONBAR 模式） |
| `stacking.timeoutSeconds` | 整数 | `3` | 奖励堆叠的时间窗口 |

**注意：** 堆叠仅适用于 ACTIONBAR 模式。CHAT 模式为每个奖励发送单独消息，无聚合。

### 升级提醒

```yaml
feedback:
  upgradeReminder:
    # 显示模式: CHAT（可点击消息）或 SCOREBOARD（闪烁行）
    displayMode: CHAT
    # 记分板闪烁间隔（游戏刻）（仅 SCOREBOARD 模式）
    flashIntervalTicks: 10
```

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `displayMode` | 字符串 | `CHAT` | `CHAT` = 可点击消息，`SCOREBOARD` = 闪烁记分板行 |
| `flashIntervalTicks` | 整数 | `10` | 记分板模式的闪烁间隔（游戏刻） |

**升级提醒模式：**
- `CHAT`：在聊天中发送可点击的 [力量] 和 [防御] 选项。提醒按 `upgrade.reminderIntervalSeconds` 定义的间隔发送。
- `SCOREBOARD`：在记分板上显示闪烁的 ">>> 升级可用 <<<" 行。更隐蔽，减少聊天刷屏。

### 音效

```yaml
feedback:
  sounds:
    # 格式: "音效名 音量 音调" 或空字符串禁用
    # 使用原版 Minecraft 音效格式（如 minecraft:entity.experience_orb.pickup）

    # 奖励音效
    xpGained: "minecraft:entity.experience_orb.pickup 0.5 1.2"
    coinGained: "minecraft:entity.item.pickup 0.6 1.0"
    permaScoreGained: "minecraft:entity.player.levelup 0.8 1.0"
    killReward: ""  # 空 = 禁用（经验音效已覆盖）

    # 升级音效
    upgradeAvailable: "minecraft:block.note_block.pling 1.0 1.2"
    upgradeSelected: "minecraft:entity.player.levelup 1.0 1.0"

    # 游戏事件音效
    countdownTick: "minecraft:block.note_block.pling 0.8 1.0"
    teleport: "minecraft:entity.enderman.teleport 0.8 1.0"
    death: "minecraft:entity.wither.death 0.5 1.0"
    runStart: "minecraft:entity.player.levelup 1.0 1.0"
```

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `xpGained` | 字符串 | （见上） | 获得经验时的音效 |
| `coinGained` | 字符串 | （见上） | 获得金币时的音效 |
| `permaScoreGained` | 字符串 | （见上） | 获得永久积分时的音效 |
| `killReward` | 字符串 | `""` | 击杀时的音效（空 = 禁用） |
| `upgradeAvailable` | 字符串 | （见上） | 升级可用时的音效 |
| `upgradeSelected` | 字符串 | （见上） | 玩家选择升级时的音效 |
| `countdownTick` | 字符串 | （见上） | 准备倒计时期间的音效 |
| `teleport` | 字符串 | （见上） | 传送到竞技场时的音效 |
| `death` | 字符串 | （见上） | 玩家死亡时的音效 |
| `runStart` | 字符串 | （见上） | 游戏开始时的音效 |

**音效格式：** `"命名空间:音效.路径 音量 音调"` 其中：
- `命名空间:音效.路径` - 原版 Minecraft 音效标识符（如 `minecraft:entity.player.levelup`）
- `音量` - 音效音量（0.0-1.0）
- `音调` - 音效音调（0.5-2.0）
- 留空（`""`）禁用音效

---

## 完整配置示例

```yaml
# 毛玉幸存者配置
# 版本: 1.0.0

plugin:
  language: zh_CN
  prefix: "§8[§6武§e生§8] §7"
  verbose: false

joinSwitch:
  enabled: true
  graceEjectSeconds: 60
  graceWarningInterval: 15

ready:
  countdownSeconds: 5
  showActionBar: true
  showTitle: true
  countdownSound: BLOCK_NOTE_BLOCK_PLING
  countdownSoundPitch: 1.0
  teleportSound: ENTITY_ENDERMAN_TELEPORT

cooldown:
  deathCooldownSeconds: 60
  quitCooldownSeconds: 30
  showCooldownBar: true
  displayUpdateTicks: 20

disconnect:
  graceSeconds: 300
  checkIntervalTicks: 200
  notifyTeam: true
  notifyGraceExpired: true

teams:
  maxSize: 5
  inviteExpirySeconds: 60

respawn:
  invulnerabilitySeconds: 3
  canDealDamageDuringInvul: false
  invulEffect: GLOWING
  pvp: false

# ... （其余配置）
```

---

## 热重载

所有配置可以在不重启服务器的情况下重载：

```
/vrs reload
```

**游戏进行中可安全重载：**
- 语言文件
- 生成速率和缩放
- 商人模板
- 世界启用/禁用状态
- 进度曲线
- 奖励值

**需要无活动游戏：**
- 装备池结构变更
- 初始装备选项变更

**永远不需要重启：**
- 任何配置变更
