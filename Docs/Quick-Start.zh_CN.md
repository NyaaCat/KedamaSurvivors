# 快速入门指南

本指南将帮助你从零开始设置 KedamaSurvivors 并开始你的第一场游戏。

## 前置条件

### 必需

- **准备区域（大厅）**：任何玩家可以在进入战斗前加入并选择装备的世界。可以是你服务器的主出生世界。
- **战斗世界**：至少一个定义了出生边界的世界，用于进行 roguelite 游戏。

### 可选插件

| 插件 | 用途 |
|------|------|
| **Multiverse-Core** | 轻松管理多个战斗世界 |
| **RPGItems-reloaded** | 创建具有特殊能力的自定义武器和护甲 |
| **EssentialsX** | 便捷的出生点/传送管理 |
| **Vault** | 外部经济集成（可选 - 插件有内置经济系统） |

---

## 分步设置

### 第一步：配置战斗世界

战斗世界是玩家与敌人战斗的地方。你至少需要配置一个。

**方式A：直接编辑 `data/worlds.yml`：**

```yaml
worlds:
  - name: "combat_arena"
    displayName: "§2森林竞技场"
    enabled: true
    weight: 1.0
    spawnBounds:
      minX: -500
      maxX: 500
      minZ: -500
      maxZ: 500
```

**方式B：使用管理员命令：**

```
/vrs admin world create combat_arena "森林竞技场"
/vrs admin world set bounds combat_arena -500 500 -500 500
/vrs admin world enable combat_arena
```

**配置字段说明：**

| 字段 | 说明 |
|------|------|
| `name` | Bukkit 世界名称（必须存在） |
| `displayName` | 显示给玩家的名称（支持颜色代码） |
| `enabled` | 此世界是否可被选中 |
| `weight` | 选择概率（越高越可能被选中） |
| `spawnBounds` | 定义有效出生区域的矩形（minX, maxX, minZ, maxZ） |

#### 第1b步：配置出生点

每个战斗世界需要**至少一个出生点**。玩家从此列表中随机位置出生。

**方式A：使用管理员命令（推荐）：**

```
# 站在出生位置运行：
/vrs admin world addspawn combat_arena

# 或指定坐标和可选的旋转角度：
/vrs admin world addspawn combat_arena 100 64 200 0 0

# 添加多个出生点以增加多样性：
/vrs admin world addspawn combat_arena -100 64 -200 180 0

# 列出所有出生点：
/vrs admin world listspawns combat_arena

# 按索引移除出生点（从1开始）：
/vrs admin world removespawn combat_arena 2
```

**方式B：直接编辑 `data/worlds.yml`：**

```yaml
worlds:
  - name: "combat_arena"
    displayName: "§2森林竞技场"
    enabled: true
    weight: 1.0
    spawnBounds:
      minX: -500
      maxX: 500
      minZ: -500
      maxZ: 500
    spawnPoints:
      - x: 100
        y: 64
        z: 200
        yaw: 0
        pitch: 0
      - x: -100
        y: 64
        z: -200
        yaw: 180
        pitch: 0
```

**备用出生点（可选）：**

如果随机出生采样失败，配置一个保证安全的出生点：

```
/vrs admin world setfallback combat_arena 0 64 0
/vrs admin world clearfallback combat_arena  # 移除备用出生点
```

---

### 第二步：设置敌人原型

原型定义战斗期间生成的敌人类型。

**编辑 `data/archetypes.yml`：**

```yaml
archetypes:
  zombie:
    enemyType: "minecraft:zombie"
    weight: 3.0
    minSpawnLevel: 1  # 从开始就可用
    spawnCommands:
      - "summon zombie {sx} {sy} {sz} {Tags:[\"vrs_mob\",\"vrs_lvl_{enemyLevel}\",\"vrs_arch_{archetypeId}\"]}"
    rewards:
      xpAmount: 10        # 固定经验数量
      xpChance: 1.0       # 100% 概率获得经验
      coinAmount: 1       # 固定金币数量
      coinChance: 1.0     # 100% 概率获得金币
      permaScoreAmount: 1
      permaScoreChance: 0.01  # 1% 概率获得永久积分

  skeleton:
    enemyType: "minecraft:skeleton"
    weight: 2.0
    minSpawnLevel: 5  # 仅在敌人等级 5+ 时生成
    spawnCommands:
      - "summon skeleton {sx} {sy} {sz} {Tags:[\"vrs_mob\",\"vrs_lvl_{enemyLevel}\",\"vrs_arch_{archetypeId}\"]}"
    rewards:
      xpAmount: 15
      xpChance: 1.0
      coinAmount: 2
      coinChance: 0.8     # 80% 概率获得金币
      permaScoreAmount: 1
      permaScoreChance: 0.02

  spider:
    enemyType: "minecraft:spider"
    weight: 2.0
    minSpawnLevel: 1
    spawnCommands:
      - "summon spider {sx} {sy} {sz} {Tags:[\"vrs_mob\",\"vrs_lvl_{enemyLevel}\",\"vrs_arch_{archetypeId}\"]}"
    rewards:
      xpAmount: 12
      xpChance: 1.0
      coinAmount: 1
      coinChance: 1.0
      permaScoreAmount: 1
      permaScoreChance: 0.01
```

**可用占位符：**

| 占位符 | 说明 |
|--------|------|
| `{sx}`, `{sy}`, `{sz}` | 生成坐标 |
| `{enemyLevel}` | 计算的敌人等级 |
| `{player}` | 目标玩家名称 |
| `{runWorld}` | 战斗世界名称 |
| `{enemyType}` | 原型配置中的实体类型 |
| `{archetypeId}` | 原型 ID（用于 Tags 进行奖励查找） |

**等级门控：** `minSpawnLevel` 属性控制原型何时可用。只有 `minSpawnLevel <= 当前敌人等级` 的原型才会被考虑生成。

**重要：** 敌人必须有 `vrs_mob` 标签才能被插件跟踪。包含 `vrs_arch_{archetypeId}` 标签以便正确查找奖励。

---

### 第三步：捕获并配置装备系统（详细指南）

这是设置游戏的核心步骤。玩家在进入战斗前选择初始武器和护甲，游戏中通过升级获得更强的装备。

以下示例将创建 **3组武器**（剑、弓、斧）和 **2组护甲**（轻甲、重甲），每组的1级物品作为初始装备选项。

#### 3a：创建装备组

首先需要为武器和护甲创建装备组。

**创建武器组（3组）：**

```
/vrs admin equipment group create weapon sword "剑系"
/vrs admin equipment group create weapon bow "弓系"
/vrs admin equipment group create weapon axe "斧系"
```

**创建护甲组（2组）：**

```
/vrs admin equipment group create helmet light "轻甲系"
/vrs admin equipment group create helmet heavy "重甲系"
```

**验证创建结果：**

```
/vrs admin equipment group list weapon
/vrs admin equipment group list helmet
```

#### 3b：添加装备物品

**重要：** 添加物品时，你需要**手持对应的物品**，命令会自动捕获物品的NBT数据。

**添加剑系武器（sword）：**

```
# 手持铁剑
/vrs admin equipment item add weapon sword 1

# 手持附魔铁剑（锋利I）
/vrs admin equipment item add weapon sword 2

# 手持钻石剑
/vrs admin equipment item add weapon sword 3

# 手持附魔钻石剑（锋利II）
/vrs admin equipment item add weapon sword 4

# 手持下界合金剑（锋利III + 火焰附加）
/vrs admin equipment item add weapon sword 5
```

**添加弓系武器（bow）：**

```
# 手持普通弓
/vrs admin equipment item add weapon bow 1

# 手持附魔弓（力量I）
/vrs admin equipment item add weapon bow 2

# 手持附魔弓（力量II + 火矢）
/vrs admin equipment item add weapon bow 3

# 手持附魔弓（力量III + 无限）
/vrs admin equipment item add weapon bow 4
```

**添加斧系武器（axe）：**

```
# 手持铁斧
/vrs admin equipment item add weapon axe 1

# 手持钻石斧
/vrs admin equipment item add weapon axe 2

# 手持附魔钻石斧（锋利II）
/vrs admin equipment item add weapon axe 3

# 手持下界合金斧（锋利III）
/vrs admin equipment item add weapon axe 4
```

**添加轻甲系护甲（light）：**

```
# 手持皮革头盔
/vrs admin equipment item add helmet light 1

# 手持附魔皮革头盔（保护I）
/vrs admin equipment item add helmet light 2

# 手持附魔金头盔（保护II）
/vrs admin equipment item add helmet light 3

# 手持附魔皮革头盔（保护III + 水下呼吸）
/vrs admin equipment item add helmet light 4
```

**添加重甲系护甲（heavy）：**

```
# 手持铁头盔
/vrs admin equipment item add helmet heavy 1

# 手持附魔铁头盔（保护I）
/vrs admin equipment item add helmet heavy 2

# 手持钻石头盔
/vrs admin equipment item add helmet heavy 3

# 手持附魔钻石头盔（保护II）
/vrs admin equipment item add helmet heavy 4

# 手持下界合金头盔（保护III）
/vrs admin equipment item add helmet heavy 5
```

**验证添加结果：**

```
# 查看剑系所有等级物品
/vrs admin equipment item list weapon sword

# 查看剑系1级物品
/vrs admin equipment item list weapon sword 1

# 查看轻甲系所有等级物品
/vrs admin equipment item list helmet light
```

#### 3c：获取模板ID

添加物品后，系统会自动生成模板ID。你需要记录每组1级物品的模板ID用于创建初始装备。

```
/vrs admin equipment item list weapon sword 1
```

输出示例：
```
§8========== §6sword 等级1 物品 §8==========
§7[1] §fsword_iron_001 §8- §7IRON_SWORD
```

记录下每组1级物品的模板ID（如 `sword_iron_001`）。

假设获得的模板ID如下：

| 组 | 等级 | 模板ID |
|----|------|--------|
| sword | 1 | `sword_iron_001` |
| bow | 1 | `bow_basic_001` |
| axe | 1 | `axe_iron_001` |
| light | 1 | `helmet_leather_001` |
| heavy | 1 | `helmet_iron_001` |

#### 3d：创建初始装备选项

使用记录的模板ID创建初始装备选项（starter）。

**创建武器初始选项（3个）：**

```
/vrs admin starter create weapon starter_sword sword_iron_001 sword 1 "§f铁剑"
/vrs admin starter create weapon starter_bow bow_basic_001 bow 1 "§f弓"
/vrs admin starter create weapon starter_axe axe_iron_001 axe 1 "§f铁斧"
```

**参数说明：**
- `weapon` - 类型（武器）
- `starter_sword` - 选项ID（唯一标识符）
- `sword_iron_001` - 模板ID（第3c步获得的）
- `sword` - 装备组名
- `1` - 起始等级
- `"§f铁剑"` - 显示名称

**创建护甲初始选项（2个）：**

```
/vrs admin starter create helmet starter_light helmet_leather_001 light 1 "§f皮革帽"
/vrs admin starter create helmet starter_heavy helmet_iron_001 heavy 1 "§f铁头盔"
```

**验证初始装备：**

```
/vrs admin starter list weapon
/vrs admin starter list helmet
```

输出示例：
```
§8========== §6初始装备选项 (weapon) §8==========
§7- §fstarter_sword §8(§7§f铁剑§8) §7模板: §fsword_iron_001 §7组: §fsword §7等级: §f1
§7- §fstarter_bow §8(§7§f弓§8) §7模板: §fbow_basic_001 §7组: §fbow §7等级: §f1
§7- §fstarter_axe §8(§7§f铁斧§8) §7模板: §faxe_iron_001 §7组: §faxe §7等级: §f1
```

#### 3e：完整命令汇总

```bash
# ===== 创建装备组 =====
/vrs admin equipment group create weapon sword "剑系"
/vrs admin equipment group create weapon bow "弓系"
/vrs admin equipment group create weapon axe "斧系"
/vrs admin equipment group create helmet light "轻甲系"
/vrs admin equipment group create helmet heavy "重甲系"

# ===== 添加物品（手持物品执行） =====
# 剑系 1-5 级
/vrs admin equipment item add weapon sword 1
/vrs admin equipment item add weapon sword 2
/vrs admin equipment item add weapon sword 3
/vrs admin equipment item add weapon sword 4
/vrs admin equipment item add weapon sword 5

# 弓系 1-4 级
/vrs admin equipment item add weapon bow 1
/vrs admin equipment item add weapon bow 2
/vrs admin equipment item add weapon bow 3
/vrs admin equipment item add weapon bow 4

# 斧系 1-4 级
/vrs admin equipment item add weapon axe 1
/vrs admin equipment item add weapon axe 2
/vrs admin equipment item add weapon axe 3
/vrs admin equipment item add weapon axe 4

# 轻甲系 1-4 级
/vrs admin equipment item add helmet light 1
/vrs admin equipment item add helmet light 2
/vrs admin equipment item add helmet light 3
/vrs admin equipment item add helmet light 4

# 重甲系 1-5 级
/vrs admin equipment item add helmet heavy 1
/vrs admin equipment item add helmet heavy 2
/vrs admin equipment item add helmet heavy 3
/vrs admin equipment item add helmet heavy 4
/vrs admin equipment item add helmet heavy 5

# ===== 查看模板ID =====
/vrs admin equipment item list weapon sword 1
/vrs admin equipment item list weapon bow 1
/vrs admin equipment item list weapon axe 1
/vrs admin equipment item list helmet light 1
/vrs admin equipment item list helmet heavy 1

# ===== 创建初始装备选项（用实际获得的模板ID替换） =====
/vrs admin starter create weapon starter_sword sword_iron_001 sword 1 "§f铁剑"
/vrs admin starter create weapon starter_bow bow_basic_001 bow 1 "§f弓"
/vrs admin starter create weapon starter_axe axe_iron_001 axe 1 "§f铁斧"
/vrs admin starter create helmet starter_light helmet_leather_001 light 1 "§f皮革帽"
/vrs admin starter create helmet starter_heavy helmet_iron_001 heavy 1 "§f铁头盔"

# ===== 验证 =====
/vrs admin starter list
/vrs admin equipment group list
```

#### 数据存储位置

完成上述操作后，数据会存储在：
- `plugins/KedamaSurvivors/data/equipment/weapons.yml` - 武器组配置
- `plugins/KedamaSurvivors/data/equipment/helmets.yml` - 护甲组配置
- `plugins/KedamaSurvivors/data/starters.yml` - 初始装备选项
- `plugins/KedamaSurvivors/data/items/` - 物品NBT模板文件

---

### 第四步：配置传送命令

配置玩家如何在区域之间传送。

**编辑 `config.yml` 的 teleport 部分：**

```yaml
teleport:
  # 大厅位置
  lobbyWorld: "world"
  lobbyX: 0
  lobbyY: 64
  lobbyZ: 0

  # 进入战斗前运行的命令（如清空背包、治疗）
  prepCommand: "tp ${player} world 0 64 0"

  # 传送玩家到战斗世界的命令
  enterCommand: "tp ${player} ${world} ${x} ${y} ${z} ${yaw} ${pitch}"

  # 重生玩家到队友的命令
  respawnCommand: "tp ${player} ${world} ${x} ${y} ${z}"
```

**对于 Multiverse：**

```yaml
teleport:
  prepCommand: "mv tp ${player} lobby"
  enterCommand: "mv tp ${player} ${world}:${x},${y},${z}"
  respawnCommand: "mv tp ${player} ${world}:${x},${y},${z}"
```

**可用占位符：**

| 占位符 | 说明 |
|--------|------|
| `${player}` | 玩家名称 |
| `${world}` | 目标世界名称 |
| `${x}`, `${y}`, `${z}` | 坐标 |
| `${yaw}`, `${pitch}` | 旋转角度（仅 enter 命令） |

---

### 第五步：（可选）配置经济系统

插件支持三种经济模式来处理金币：

**编辑 `config.yml` 的 economy 部分：**

```yaml
economy:
  # 模式: VAULT（外部经济）, INTERNAL（插件内部管理）, ITEM（实体物品）
  mode: INTERNAL

  coin:
    material: EMERALD
    customModelData: 0
    displayName: "§e金币"
    nbtTag: "vrs_coin"           # 识别 VRS 金币的 NBT 标签（用于 ITEM 模式）
```

**经济模式：**

| 模式 | 说明 |
|------|------|
| `VAULT` | 使用外部 Vault 兼容经济插件。如果 Vault 未安装则回退到 INTERNAL。 |
| `INTERNAL` | 插件在内部存储每玩家的金币余额（默认）。 |
| `ITEM` | 玩家背包中的实体金币物品。金币通过 NBT 标签识别。 |

---

### 第六步：（可选）配置通知和音效

自定义玩家如何接收奖励通知和音效。

**编辑 `config.yml` 的 feedback 部分：**

```yaml
feedback:
  rewards:
    displayMode: ACTIONBAR    # ACTIONBAR（堆叠）或 CHAT（单独）
    stacking:
      timeoutSeconds: 3       # 3秒内堆叠奖励

  upgradeReminder:
    displayMode: CHAT         # CHAT（可点击）或 SCOREBOARD（闪烁行）

  sounds:
    xpGained: "minecraft:entity.experience_orb.pickup 0.5 1.2"
    coinGained: "minecraft:entity.item.pickup 0.6 1.0"
    upgradeAvailable: "minecraft:block.note_block.pling 1.0 1.2"
```

**显示模式：**

| 模式 | 说明 |
|------|------|
| `ACTIONBAR` | 奖励堆叠并显示聚合总计（如 "+50 经验 | +5 金币"）。减少聊天刷屏。 |
| `CHAT` | 每个奖励单独发送消息（旧版行为）。 |

**升级提醒模式：**

| 模式 | 说明 |
|------|------|
| `CHAT` | 带有 [力量] 和 [防御] 选项的可点击聊天消息 |
| `SCOREBOARD` | 记分板上闪烁的 ">>> 升级可用 <<<" 行。更隐蔽。 |

**音效格式：** `"音效名 音量 音调"` - 留空（`""`）禁用音效。

---

### 第七步：（可选）配置商人

商人显示为带有浮动和旋转动画的盔甲架。它们在游戏期间定期生成并提供金币交易。

#### 7a：创建商人物品池

商人库存通过物品池管理。每个池包含带价格的加权物品。

**使用管理员命令（推荐）：**

```bash
# 1. 创建商人池
/vrs admin merchant pool create common_items

# 2. 手持你想出售的物品
# 3. 添加到池中，指定价格和权重
/vrs admin merchant pool additem common_items 25 1.0
# 结果：添加手持物品，价格25金币，权重1.0

# 4. 通过手持不同物品添加更多物品
/vrs admin merchant pool additem common_items 50 0.5

# 5. 列出池内容
/vrs admin merchant pool list common_items
```

**手动配置（备选）：**

编辑 `data/merchant_pools.yml`：

```yaml
pools:
  common_items:
    items:
      - templateId: "golden_apple_1"
        weight: 10.0
        price: 25
      - templateId: "speed_potion_1"
        weight: 5.0
        price: 50

  rare_items:
    items:
      - templateId: "enchanted_apple_1"
        weight: 1.0
        price: 100
```

#### 7b：配置商人行为

商人使用带有浮动/旋转动画的隐形盔甲架。在 `config.yml` 中配置其行为：

```yaml
merchants:
  enabled: true

  wandering:
    spawnIntervalSeconds: 120    # 尝试生成的间隔
    spawnChance: 0.5             # 每次间隔生成的概率（0-1）
    stayTime:
      minSeconds: 60             # 最短停留时间
      maxSeconds: 120            # 最长停留时间
    distance:
      min: 20.0                  # 距玩家最小距离
      max: 50.0                  # 距玩家最大距离

  stock:
    limited: true                # 购买后物品消失
    minItems: 3                  # 商店最少物品数
    maxItems: 6                  # 商店最多物品数

  display:
    rotationSpeed: 3.0           # 每刻旋转角度（旋转）
    bobHeight: 0.15              # 浮动幅度（方块）
    bobSpeed: 0.01               # 浮动速度
```

#### 7c：创建商人模板（旧版）

你也可以创建带有特定交易的命名商人模板：

```bash
# 创建商人模板
/vrs admin merchant template create potions "§d药水商人"

# 手持物品并添加为交易（25金币，最多5次使用）
/vrs admin merchant trade add potions 25 5

# 列出交易
/vrs admin merchant trade list potions
```

---

## 最小可行设置清单

至少需要：

- [ ] **1个战斗世界**在 `data/worlds.yml` 中配置，并有**至少1个出生点**
- [ ] **1+敌人原型**在 `data/archetypes.yml` 中配置
- [ ] **1+初始武器**在 `data/starters.yml` 中配置，并有匹配的物品模板
- [ ] **1+初始护甲**在 `data/starters.yml` 中配置，并有匹配的物品模板
- [ ] **装备组**在 `data/equipment/weapons.yml` 和 `helmets.yml` 中配置
- [ ] **传送命令**在 `config.yml` 中配置（如果使用默认API传送则可选）
- [ ] **经济模式**在 `config.yml` 中配置（默认为 INTERNAL）
- [ ] **商人池**在 `data/merchant_pools.yml` 中配置（可选，用于商人交易）

---

## 开始你的第一场游戏

### 作为管理员

1. 安装插件后启动服务器
2. 验证配置已加载：`/vrs reload`
3. 检查世界已注册：`/vrs admin world list`
4. 启用游戏入口：`/vrs admin join enable`

### 作为玩家

1. **选择装备**：`/vrs starter`
   - 从界面选择你的初始武器
   - 从界面选择你的初始护甲

2. **创建或加入队伍**：
   - 单人：`/vrs team create 我的队伍`
   - 加入现有：`/vrs team accept 队伍名`（收到邀请后）

3. **准备就绪**：`/vrs ready`
   - 所有队员必须准备就绪
   - 5秒倒计时开始

4. **战斗！**
   - 你被传送到战斗世界
   - 击杀敌人获得经验和金币
   - 当经验条填满时，选择武器或护甲升级
   - 尽可能长时间存活！

5. **死亡后**：
   - 所有死亡都会将玩家传送回准备区域（无论队友状态）
   - 60秒冷却时间
   - 玩家必须重新选择装备并准备才能重新加入
   - 如果队友仍然存活，玩家可以在冷却后重新加入同一场游戏

---

## 故障排除

| 问题 | 解决方案 |
|------|----------|
| "没有可用的战斗世界" | 检查 `data/worlds.yml` 有至少一个启用的世界并有出生点 |
| "未配置出生点" | 通过 `/vrs admin world addspawn <名称>` 添加出生点 |
| "无法准备" | 确保你已选择初始装备并在队伍中 |
| 敌人不生成 | 验证 `data/archetypes.yml` 有条目且刷怪器未暂停 |
| 物品不发放 | 检查物品模板存在于 `data/items/` 目录 |
| 玩家生成在错误位置 | 通过 `/vrs admin world setfallback` 配置备用出生点 |

更多详情请参阅：
- [配置参考](Configuration-Reference.zh_CN.md)
- [命令参考](Commands-Reference.zh_CN.md)
- [设计概述](Design-Overview.md)
