# 毛玉幸存者 - 命令参考手册

本文档为 KedamaSurvivors 插件所有命令的完整参考手册。

---

## 命令结构

所有命令都使用根命令 `/vrs`（"Vampire Rogue Survivors" 的缩写）。

```
/vrs <子命令> [参数]
```

---

## 目录

1. [玩家命令](#1-玩家命令)
2. [队伍命令](#2-队伍命令)
3. [管理员命令](#3-管理员命令)
4. [调试命令](#4-调试命令)
5. [权限汇总](#权限汇总)
6. [命令别名](#命令别名)

---

## 1. 玩家命令

### `/vrs starter`

打开初始装备选择界面。

**用法:**
```
/vrs starter
/vrs starter weapon
/vrs starter helmet
/vrs starter clear
```

**子命令:**
| 子命令 | 说明 |
|--------|------|
| *(无)* | 打开武器选择界面（之后自动打开护甲选择） |
| `weapon` | 仅打开武器选择界面 |
| `helmet` | 仅打开护甲选择界面 |
| `clear` | 清除当前选择并移除已装备的初始装备 |

**权限:** `vrs.player`（默认: 所有玩家）

**前置条件:**
- 必须处于大厅模式（LOBBY）
- 选择护甲前需要先选择武器（可配置）

**示例:**
```
/vrs starter           # 开始选择流程
/vrs starter weapon    # 重新选择武器
/vrs starter helmet    # 重新选择护甲
/vrs starter clear     # 清除选择重新开始
```

---

### `/vrs ready`

切换准备状态以进入战斗。

**用法:**
```
/vrs ready
/vrs ready solo
```

**参数:**
| 参数 | 说明 |
|------|------|
| *(无)* | 切换准备状态（需要先加入队伍） |
| `solo` | 自动创建单人队伍并标记为准备 |

**权限:** `vrs.player`（默认: 所有玩家）

**前置条件:**
- 游戏入口开关必须开启
- 不在冷却中（或拥有绕过权限）
- 已选择初始武器和护甲
- 不在进行中的游戏里
- 必须在队伍中（使用 `solo` 参数自动创建队伍）

**行为说明:**
- **单人模式 (`/vrs ready solo`):** 创建名为 `玩家名-XXXX`（4位随机数字）的队伍并标记准备
- **队伍模式:** 等待所有在线队员准备就绪，然后开始倒计时
- **切换:** 准备状态下再次执行将取消准备

**示例:**
```
/vrs ready         # 标记准备（需要队伍）
/vrs ready solo    # 创建单人队伍并准备
```

---

### `/vrs quit`

主动退出当前游戏。

**用法:**
```
/vrs quit
```

**权限:** `vrs.player`（默认: 所有玩家）

**前置条件:**
- 必须在进行中的游戏里（IN_RUN 模式）

**效果:**
- **不会**应用死亡惩罚（保留装备）
- 返回准备区域
- 可能应用退出冷却（可配置）

**示例:**
```
/vrs quit    # 退出当前游戏并返回准备区
```

---

### `/vrs status`

显示你当前的游戏状态。

**用法:**
```
/vrs status
```

**权限:** `vrs.player`（默认: 所有玩家）

**显示内容:**
- 当前模式（LOBBY、READY、IN_RUN 等）
- 游戏世界（如果在游戏中）
- 队伍名称和成员（如果在队伍中）
- 剩余冷却时间（如果在冷却中）
- 武器组和等级
- 护甲组和等级
- 经验进度和持有经验
- 金币余额
- 永久积分

**输出示例:**
```
§7========== §6武生状态 §7==========
§7模式: §aIN_RUN
§7世界: §f森林竞技场
§7队伍: §fAlpha §7(3/5 在线)
§6武器: §f剑系 Lv.3
§b护甲: §f中甲系 Lv.2
§a经验: §f150/300 (50%)
§e金币: §f42
§d永久积分: §f1,250
```

---

### `/vrs upgrade`

在游戏中选择力量或防御升级。

**用法:**
```
/vrs upgrade power
/vrs upgrade defense
```

**权限:** `vrs.player`（默认: 所有玩家）

**参数:**
| 参数 | 说明 |
|------|------|
| `power` | 选择武器升级 |
| `defense` | 选择护甲升级 |

**前置条件:**
- 必须在进行中的游戏里（IN_RUN 模式）
- 必须有待处理的升级提示

**示例:**
```
/vrs upgrade power     # 升级武器
/vrs upgrade defense   # 升级护甲
```

---

## 2. 队伍命令

### `/vrs team create [名称]`

创建新队伍。

**用法:**
```
/vrs team create [名称]
```

**权限:** `vrs.team.create`（默认: 所有玩家）

**参数:**
| 参数 | 说明 |
|------|------|
| `名称` | 队伍名称（可选 - 不提供则自动生成） |

**队伍名称规则:**
- 不提供名称时，自动生成：`玩家名-XXXX`（4位随机字母数字）
- 有效字符：字母、数字、连字符（`-`）、下划线（`_`）
- 队伍名称不允许空格

**前置条件:**
- 不能已经在队伍中
- 必须处于大厅模式

**示例:**
```
/vrs team create Alpha        # 创建名为 "Alpha" 的队伍
/vrs team create              # 自动生成名称如 "Steve-A1B2"
/vrs team create my_team_1    # 使用下划线的有效名称
```

---

### `/vrs team invite <玩家>`

邀请玩家加入你的队伍。

**用法:**
```
/vrs team invite <玩家>
```

**权限:** `vrs.team.invite`（默认: 所有玩家）

**参数:**
| 参数 | 说明 |
|------|------|
| `玩家` | 目标玩家名称 |

**前置条件:**
- 必须是队长
- 队伍不能已满（默认最多5人）
- 目标必须不在任何队伍中

**行为说明:**
- 邀请在60秒后过期（可配置）
- 目标收到可点击的加入消息

**示例:**
```
/vrs team invite Steve
/vrs team invite Notch
```

---

### `/vrs team accept <队伍>`

接受收到的队伍邀请。

**用法:**
```
/vrs team accept <队伍>
```

**权限:** `vrs.team.join`（默认: 所有玩家）

**参数:**
| 参数 | 说明 |
|------|------|
| `队伍` | 要接受邀请的队伍名称 |

**前置条件:**
- 必须有该队伍的有效邀请
- 队伍不能已满
- 不能已经在队伍中

**示例:**
```
/vrs team accept Alpha
```

---

### `/vrs team decline <队伍>`

拒绝队伍邀请。

**用法:**
```
/vrs team decline <队伍名>
```

**权限:** `vrs.team`（默认: 所有玩家）

**参数:**
| 参数 | 说明 |
|------|------|
| `队伍` | 要拒绝邀请的队伍名称 |

**前置条件:**
- 必须有该队伍的有效邀请

**示例:**
```
/vrs team decline Alpha
```

---

### `/vrs team leave`

离开当前队伍。

**用法:**
```
/vrs team leave
```

**权限:** `vrs.team.leave`（默认: 所有玩家）

**前置条件:**
- 必须在队伍中
- 如果是队长，必须先转移队长或解散队伍

**行为说明:**
- 如果在游戏中，立即触发重生检查
- 通知队伍成员

**示例:**
```
/vrs team leave
```

---

### `/vrs team kick <玩家>`

将玩家踢出队伍。

**用法:**
```
/vrs team kick <玩家>
```

**权限:** `vrs.team.kick`（默认: 所有玩家）

**参数:**
| 参数 | 说明 |
|------|------|
| `玩家` | 要踢出的玩家 |

**前置条件:**
- 必须是队长
- 目标必须在你的队伍中

**示例:**
```
/vrs team kick Steve
```

---

### `/vrs team disband`

解散队伍。

**用法:**
```
/vrs team disband
```

**权限:** `vrs.team.disband`（默认: 所有玩家）

**前置条件:**
- 必须是队长
- 不应在进行中的游戏里（如果在游戏中会警告）

**行为说明:**
- 所有成员被移出队伍
- 如果在游戏中，游戏对每个成员继续作为单人模式

**示例:**
```
/vrs team disband
```

---

### `/vrs team list`

列出队伍成员及其状态。

**用法:**
```
/vrs team list
```

**权限:** `vrs.team`（默认: 所有玩家）

**显示内容:**
- 队伍名称
- 队长
- 成员列表及在线/准备状态
- 活动游戏信息（如适用）

---

### `/vrs team transfer <玩家>`

将队长转移给其他成员。

**用法:**
```
/vrs team transfer <玩家>
```

**权限:** `vrs.team`（默认: 所有玩家）

**参数:**
| 参数 | 说明 |
|------|------|
| `玩家` | 新队长（必须是队伍成员） |

**前置条件:**
- 必须是队长
- 目标必须在队伍中

---

## 3. 管理员命令

所有管理员命令在 `/vrs admin` 或 `/vrs reload` 下。

### `/vrs reload`

重新加载所有插件配置。

**用法:**
```
/vrs reload
```

**权限:** `vrs.admin`（默认: 管理员）

**行为说明:**
- 重新加载 config.yml、语言文件、物品模板和所有数据文件
- 活动的游戏不受影响

---

### `/vrs admin`

所有管理功能的根命令。

**用法:**
```
/vrs admin <子命令>
```

**权限:** `vrs.admin`（默认: 管理员）

**可用子命令:**
| 子命令 | 说明 |
|--------|------|
| `status` | 显示服务器状态 |
| `endrun` | 结束活动游戏 |
| `forcestart` | 强制开始队伍的游戏 |
| `kick` | 将玩家踢出游戏 |
| `reset` | 重置玩家状态 |
| `setperma` | 设置玩家的永久积分 |
| `join` | 切换全局游戏入口开关 |
| `world` | 世界管理 |
| `starter` | 初始装备选项管理 |
| `equipment` | 装备组管理 |
| `spawner` | 敌人原型管理 |
| `merchant` | 商人模板管理 |
| `config` | 运行时配置管理 |
| `debug` | 调试命令 |

---

### `/vrs admin status`

显示服务器游戏状态。

**用法:**
```
/vrs admin status
```

**显示内容:**
- 跟踪的玩家总数
- 活动队伍数
- 活动游戏数
- 当前在游戏中的玩家数

---

### `/vrs admin join enable|disable`

切换全局游戏入口开关。

**用法:**
```
/vrs admin join enable
/vrs admin join disable
/vrs admin join
```

**权限:** `vrs.admin`（默认: 管理员）

**行为说明:**
- 无参数：显示当前状态
- `enable`：允许新玩家进入战斗
- `disable`：阻止新游戏，活动玩家收到宽限期警告并被移出

---

### `/vrs admin forcestart <队伍>`

强制开始队伍的游戏。

**用法:**
```
/vrs admin forcestart <队伍名>
```

**权限:** `vrs.admin`（默认: 管理员）

**参数:**
| 参数 | 说明 |
|------|------|
| `队伍名` | 要开始的队伍名称 |

**前置条件:**
- 队伍必须存在
- 队伍不能已经在游戏中

**示例:**
```
/vrs admin forcestart Alpha
```

---

### `/vrs admin endrun [队伍]`

结束活动游戏。

**用法:**
```
/vrs admin endrun
/vrs admin endrun <队伍名>
```

**权限:** `vrs.admin`（默认: 管理员）

**行为说明:**
- 无参数：结束**所有**活动游戏
- 有队伍名：仅结束该队伍的游戏

**示例:**
```
/vrs admin endrun           # 结束所有游戏
/vrs admin endrun Alpha     # 结束 Alpha 队的游戏
```

---

### `/vrs admin kick <玩家>`

将玩家踢出游戏系统。

**用法:**
```
/vrs admin kick <玩家>
```

**权限:** `vrs.admin`（默认: 管理员）

**行为说明:**
- 将玩家从队伍移除
- 重置所有玩家状态

---

### `/vrs admin reset <玩家>`

重置玩家的游戏状态。

**用法:**
```
/vrs admin reset <玩家>
```

**权限:** `vrs.admin`（默认: 管理员）

**行为说明:**
- 重置玩家状态为默认值
- 不会将玩家从队伍移除

---

### `/vrs admin setperma <玩家> <数值>`

设置玩家的永久积分。

**用法:**
```
/vrs admin setperma <玩家> <数值>
```

**权限:** `vrs.admin`（默认: 管理员）

**参数:**
| 参数 | 说明 |
|------|------|
| `玩家` | 目标玩家 |
| `数值` | 积分值（整数） |

**示例:**
```
/vrs admin setperma Steve 1000
```

---

### `/vrs admin world`

管理战斗世界配置。

**子命令:**
```
/vrs admin world list
/vrs admin world create <名称> [显示名]
/vrs admin world delete <名称>
/vrs admin world enable <名称>
/vrs admin world disable <名称>
/vrs admin world set displayname <名称> <显示名>
/vrs admin world set weight <名称> <权重>
/vrs admin world set bounds <名称> <最小X> <最大X> <最小Z> <最大Z>

# 出生点管理
/vrs admin world addspawn <名称> [x y z [偏航角 俯仰角]]
/vrs admin world removespawn <名称> <索引>
/vrs admin world listspawns <名称>
/vrs admin world clearspawns <名称>

# 备用出生点
/vrs admin world setfallback <名称> [x y z [偏航角 俯仰角]]
/vrs admin world clearfallback <名称>
```

**权限:** `vrs.admin`（默认: 管理员）

**出生点命令:**
| 命令 | 说明 |
|------|------|
| `addspawn <名称> [坐标]` | 添加出生点（不提供坐标则使用玩家位置） |
| `removespawn <名称> <索引>` | 按索引移除出生点（从1开始） |
| `listspawns <名称>` | 列出世界的所有出生点 |
| `clearspawns <名称>` | 移除所有出生点 |
| `setfallback <名称> [坐标]` | 设置采样失败时的备用出生点 |
| `clearfallback <名称>` | 移除备用出生点 |

**示例:**
```
/vrs admin world create combat_arena "森林竞技场"
/vrs admin world set bounds combat_arena -500 500 -500 500
/vrs admin world enable combat_arena
/vrs admin world list

# 出生点管理
/vrs admin world addspawn arena                    # 在当前位置添加
/vrs admin world addspawn arena 100 64 200 0 0    # 指定坐标添加
/vrs admin world listspawns arena                  # 列出所有出生点
/vrs admin world removespawn arena 2               # 移除第2个出生点
/vrs admin world setfallback arena 0 64 0          # 设置备用出生点
```

---

### `/vrs admin starter`

管理初始装备选项。

**子命令:**
```
/vrs admin starter create <weapon|helmet> <选项ID> <模板ID> <组> <等级> [显示名]
/vrs admin starter delete <weapon|helmet> <选项ID>
/vrs admin starter list [weapon|helmet]
/vrs admin starter set displayname <weapon|helmet> <选项ID> <显示名>
/vrs admin starter set template <weapon|helmet> <选项ID> <模板ID>
/vrs admin starter set group <weapon|helmet> <选项ID> <组>
/vrs admin starter set level <weapon|helmet> <选项ID> <等级>
```

**权限:** `vrs.admin`（默认: 管理员）

---

### `/vrs admin equipment`

管理装备组和物品。

**组子命令:**
```
/vrs admin equipment group create <weapon|helmet> <组ID> [显示名]
/vrs admin equipment group delete <weapon|helmet> <组ID>
/vrs admin equipment group list [weapon|helmet]
/vrs admin equipment group set displayname <weapon|helmet> <组ID> <显示名>
```

**物品子命令:**
```
/vrs admin equipment item add <weapon|helmet> <组ID> <等级>
/vrs admin equipment item remove <weapon|helmet> <组ID> <等级> <索引>
/vrs admin equipment item list <weapon|helmet> <组ID> [等级]
```

**权限:** `vrs.admin.capture`（默认: 管理员）

**注意:** `item add` 命令会捕获你手中物品的 NBT 数据并自动生成模板 ID。

**示例:**
```
# 创建剑装备组
/vrs admin equipment group create weapon sword "剑系"

# 手持铁剑，添加到等级1
/vrs admin equipment item add weapon sword 1

# 列出剑组中的物品
/vrs admin equipment item list weapon sword
```

---

### `/vrs admin spawner`

管理敌人原型。

**子命令:**
```
/vrs admin spawner archetype list
/vrs admin spawner archetype create <ID> <实体类型> [权重]
/vrs admin spawner archetype delete <ID>
/vrs admin spawner archetype addcommand <ID> <命令...>
/vrs admin spawner archetype removecommand <ID> <索引>
/vrs admin spawner archetype set weight <ID> <权重>
/vrs admin spawner archetype set entitytype <ID> <实体类型>
/vrs admin spawner archetype set minspawnlevel <ID> <等级>
/vrs admin spawner archetype set worlds <ID> <世界1,世界2,...|any>
/vrs admin spawner archetype reward <ID> <经验数量> <经验概率> <金币数量> <金币概率> <永久积分数量> <永久积分概率>
```

**权限:** `vrs.admin.spawner`（默认: 管理员）

**设置命令:**
| 命令 | 说明 |
|------|------|
| `set weight <ID> <权重>` | 设置生成权重（选择概率） |
| `set entitytype <ID> <类型>` | 设置实体类型 |
| `set minspawnlevel <ID> <等级>` | 设置生成所需的最低敌人等级 |
| `set worlds <ID> <世界>` | 设置允许生成的战斗世界（逗号分隔或 "any"） |

**奖励命令参数:**
| 参数 | 说明 |
|------|------|
| `经验数量` | 固定经验奖励数量 |
| `经验概率` | 奖励经验的概率（0.0-1.0） |
| `金币数量` | 固定金币奖励数量 |
| `金币概率` | 奖励金币的概率（0.0-1.0） |
| `永久积分数量` | 固定永久积分奖励数量 |
| `永久积分概率` | 奖励永久积分的概率（0.0-1.0） |

**示例:**
```
# 创建僵尸原型
/vrs admin spawner archetype create zombie ZOMBIE 3.0

# 添加带原型ID标签的生成命令（用于奖励查找）
/vrs admin spawner archetype addcommand zombie summon zombie {sx} {sy} {sz} {Tags:["vrs_mob","vrs_lvl_{enemyLevel}","vrs_arch_{archetypeId}"]}

# 设置最低生成等级（仅在敌人等级5+时生成）
/vrs admin spawner archetype set minspawnlevel skeleton 5

# 限制原型到特定世界（逗号分隔）
/vrs admin spawner archetype set worlds wither_skeleton arena_nether,arena_hell

# 允许原型在所有战斗世界生成
/vrs admin spawner archetype set worlds zombie any

# 设置奖励（经验数量 经验概率 金币数量 金币概率 永久积分数量 永久积分概率）
/vrs admin spawner archetype reward zombie 10 1.0 1 1.0 1 0.01
```

---

### `/vrs admin merchant`

管理商人 - 向玩家出售物品的盔甲架。

**权限:** `vrs.admin`（默认: 管理员）

#### 商人系统概述

商人显示为带有浮动/旋转动画的隐形盔甲架。有两种商人**类型**和两种**行为**：

**商人类型:**

| 类型 | 说明 |
|------|------|
| `MULTI` | 打开包含多个物品的商店界面。玩家点击物品购买。库存从池中随机选择。 |
| `SINGLE` | 直接购买 - 右键点击立即购买。显示一个物品漂浮在商人上方。 |

**商人行为:**

| 行为 | 说明 |
|------|------|
| `FIXED` | 固定位置的永久商人。通过管理员命令创建。不会自动消失。 |
| `WANDERING` | 游戏期间随机出现在玩家附近的临时商人。配置时间后消失。 |

**库存模式:**

| 模式 | 说明 |
|------|------|
| `limited` | 物品购买后消失。所有物品售出后商人变空。 |
| `unlimited` | 物品购买后刷新。商人永不缺货。 |

---

#### 生成子命令

生成、移除和列出活动商人的命令：

```
/vrs admin merchant spawn <池ID> <multi|single> [limited|unlimited] [all|random]
/vrs admin merchant despawn [半径]
/vrs admin merchant active
```

| 命令 | 说明 |
|------|------|
| `spawn <池ID> <类型> [库存] [物品]` | 使用指定池在你的位置生成固定商人 |
| `despawn [半径]` | 移除半径内最近的商人（默认：5格） |
| `active` | 列出所有活动商人及其类型、行为、池和位置 |

**生成参数:**
| 参数 | 选项 | 说明 |
|------|------|------|
| `池ID` | 池名称 | 此商人使用的物品池 |
| `类型` | `multi` / `single` | `multi` = 多物品商店界面，`single` = 直接购买 |
| `库存` | `limited` / `unlimited` | 物品购买后是否消失（默认：配置值） |
| `物品` | `all` / `random` | `all` = 显示所有池物品，`random` = 使用最小/最大物品配置（默认：`random`） |

**生成示例:**
```
# 使用 "common_shop" 池生成多物品商店商人
/vrs admin merchant spawn common_shop multi

# 生成无限库存的单物品商人
/vrs admin merchant spawn rare_items single unlimited

# 生成有限库存（购买后物品消失）
/vrs admin merchant spawn potions multi limited

# 生成显示池中所有物品的完整商店
/vrs admin merchant spawn weapons_pool multi all

# 生成有限库存和随机选择
/vrs admin merchant spawn rare_items multi limited random

# 生成无限库存和所有物品
/vrs admin merchant spawn consumables multi unlimited all

# 移除最近的商人
/vrs admin merchant despawn

# 移除20格内的商人
/vrs admin merchant despawn 20

# 列出所有活动商人
/vrs admin merchant active
```

---

#### 池子命令

池定义商人提取库存的加权物品集合：

```
/vrs admin merchant pool create <池ID>
/vrs admin merchant pool delete <池ID>
/vrs admin merchant pool list [池ID]
/vrs admin merchant pool additem <池ID> <价格> [权重]
/vrs admin merchant pool removeitem <池ID> <索引>
```

| 命令 | 说明 |
|------|------|
| `pool create <池ID>` | 创建新的空池 |
| `pool delete <池ID>` | 删除池及其所有物品 |
| `pool list` | 列出所有池 |
| `pool list <池ID>` | 列出特定池中的物品 |
| `pool additem <池ID> <价格> [权重]` | 添加手持物品到池（权重默认1.0） |
| `pool removeitem <池ID> <索引>` | 按索引移除物品（从1开始） |

**注意:** `pool additem` 命令捕获你手中物品的 NBT 数据。权重越高 = 被选中的概率越高。

**池示例:**
```
# 创建商人池
/vrs admin merchant pool create common_shop

# 手持金苹果，添加价格25权重1.0
/vrs admin merchant pool additem common_shop 25 1.0

# 手持速度药水，添加价格50权重0.5（较少见）
/vrs admin merchant pool additem common_shop 50 0.5

# 列出所有池
/vrs admin merchant pool list

# 列出池中的物品
/vrs admin merchant pool list common_shop

# 移除索引2的物品
/vrs admin merchant pool removeitem common_shop 2
```

---

#### 模板子命令（旧版）

模板用于具有预定义交易的固定交易商人：

```
/vrs admin merchant template create <模板ID> [显示名]
/vrs admin merchant template delete <模板ID>
/vrs admin merchant template list
/vrs admin merchant template set displayname <模板ID> <显示名>
```

**交易子命令:**
```
/vrs admin merchant trade add <模板ID> <花费数量> [最大使用次数]
/vrs admin merchant trade remove <模板ID> <索引>
/vrs admin merchant trade list <模板ID>
```

**模板示例:**
```
# 创建商人模板
/vrs admin merchant template create potions "§d药水商人"

# 手持治疗药水，添加为25金币交易，最多5次使用
/vrs admin merchant trade add potions 25 5

# 列出交易
/vrs admin merchant trade list potions
```

---

#### 完整商人设置流程

**步骤 1: 创建池**
```
/vrs admin merchant pool create weapons_shop
```

**步骤 2: 添加物品到池**（手持每个物品）
```
# 钻石剑，100金币，权重0.5（稀有）
/vrs admin merchant pool additem weapons_shop 100 0.5

# 铁剑，50金币，权重1.0（常见）
/vrs admin merchant pool additem weapons_shop 50 1.0

# 弓，75金币，权重0.8
/vrs admin merchant pool additem weapons_shop 75 0.8
```

**步骤 3: 生成商人**
```
# 有限库存和随机选择的多物品商店
/vrs admin merchant spawn weapons_shop multi limited random

# 或生成显示所有物品的完整商店
/vrs admin merchant spawn weapons_shop multi limited all
```

**步骤 4: 管理商人**
```
# 查看所有活动商人
/vrs admin merchant active

# 移除附近的商人
/vrs admin merchant despawn
```

**步骤 5: 启用流浪商人**（可选）

流浪商人需要显式池配置。默认情况下禁用。

```
# 设置流浪商人的池（启用必需）
/vrs admin config set wanderingMerchantPoolId weapons_shop

# 设置流浪商人类型（single 或 multi，默认：single）
/vrs admin config set wanderingMerchantType single

# 设置生成间隔为60秒
/vrs admin config set merchantSpawnInterval 60

# 设置生成概率为75%
/vrs admin config set merchantSpawnChance 0.75

# 查看所有商人配置选项
/vrs admin config list merchants
```

**注意:** 如果 `wanderingMerchantPoolId` 为空，游戏期间不会生成流浪商人。

---

### `/vrs admin config`

管理运行时配置值。

**子命令:**
```
/vrs admin config get <属性>
/vrs admin config set <属性> <值>
/vrs admin config list [分类]
```

**权限:** `vrs.admin.config`（默认: 管理员）

**可用分类和属性:**

**传送相关:**
- `lobbyWorld`（字符串）, `lobbyX`, `lobbyY`, `lobbyZ`（数值）
- `prepCommand`, `enterCommand`, `respawnCommand`（字符串）

**时间相关:**
- `deathCooldownSeconds`, `respawnInvulnerabilitySeconds`, `disconnectGraceSeconds`, `countdownSeconds`（整数）

**生成相关:**
- `minSpawnDistance`, `maxSpawnDistance`（数值）
- `maxSampleAttempts`, `spawnTickInterval`, `targetMobsPerPlayer`, `maxSpawnsPerTick`（整数）

**奖励相关:**
- `xpShareRadius`, `xpSharePercent`（数值）

**进度相关:**
- `baseXpRequired`, `xpPerLevelIncrease`, `weaponLevelWeight`, `helmetLevelWeight`（整数）
- `xpMultiplierPerLevel`（数值）

**队伍相关:**
- `maxTeamSize`, `inviteExpirySeconds`（整数）

**商人相关:**
- `merchantsEnabled`（布尔值）
- `merchantSpawnInterval`, `merchantLifetime`（整数）
- `merchantMinDistance`, `merchantMaxDistance`（数值）

**升级相关:**
- `upgradeTimeoutSeconds`, `upgradeReminderIntervalSeconds`（整数）

**记分板相关:**
- `scoreboardEnabled`（布尔值）
- `scoreboardTitle`（字符串）
- `scoreboardUpdateInterval`（整数）

**示例:**
```
/vrs admin config list timing
/vrs admin config get deathCooldownSeconds
/vrs admin config set deathCooldownSeconds 120
```

---

## 4. 调试命令

所有调试命令在 `/vrs admin debug` 下。

### `/vrs admin debug player <玩家>`

显示详细的玩家状态信息。

**用法:**
```
/vrs admin debug player <玩家>
```

**权限:** `vrs.admin`（默认: 管理员）

**显示内容:**
- 所有 PlayerState 字段
- 队伍成员详情
- 活动游戏详情
- 待处理奖励数量
- 内部计时器

---

### `/vrs admin debug perf`

显示性能统计。

**用法:**
```
/vrs admin debug perf
```

**权限:** `vrs.admin`（默认: 管理员）

**显示内容:**
- 跟踪的玩家、队伍、活动游戏
- 内存使用
- TPS

---

### `/vrs admin debug templates <名称> <玩家>`

使用玩家上下文测试模板展开。

**用法:**
```
/vrs admin debug templates <模板名> <玩家>
```

**权限:** `vrs.admin`（默认: 管理员）

**示例:**
```
/vrs admin debug templates enterCommand Steve
```

**输出:**
```
Template: tp ${player} ${world} ${x} ${y} ${z}
Expanded: tp Steve arena_forest 100 64 100
```

---

### `/vrs admin debug run [运行ID|list]`

显示详细的游戏状态信息。

**用法:**
```
/vrs admin debug run list
/vrs admin debug run <运行ID>
```

**权限:** `vrs.admin`（默认: 管理员）

---

## 权限汇总

| 权限 | 说明 | 默认 |
|------|------|------|
| `vrs.player` | 基本玩家命令 | 所有玩家 |
| `vrs.team` | 队伍相关命令 | 所有玩家 |
| `vrs.team.create` | 创建队伍 | 所有玩家 |
| `vrs.team.invite` | 邀请加入队伍 | 所有玩家 |
| `vrs.team.join` | 接受队伍邀请 | 所有玩家 |
| `vrs.team.leave` | 离开队伍 | 所有玩家 |
| `vrs.team.kick` | 踢出队伍成员 | 所有玩家 |
| `vrs.team.disband` | 解散队伍 | 所有玩家 |
| `vrs.admin` | 所有管理员命令 | 管理员 |
| `vrs.admin.world` | 世界管理 | 管理员 |
| `vrs.admin.spawner` | 敌人原型管理 | 管理员 |
| `vrs.admin.starter` | 初始装备选项管理 | 管理员 |
| `vrs.admin.config` | 运行时配置 | 管理员 |
| `vrs.admin.capture` | 装备捕获/管理 | 管理员 |
| `vrs.cooldown.bypass` | 绕过死亡冷却 | 管理员 |

---

## Tab 自动补全

所有命令支持 Tab 补全：

- 队伍/管理员命令自动补全玩家名
- 接受/强制开始命令自动补全队伍名
- 世界命令自动补全世界名
- 每级子命令自动补全

---

## 命令别名

| 别名 | 完整命令 |
|------|----------|
| `/vrs s` | `/vrs starter` |
| `/vrs r` | `/vrs ready` |
| `/vrs t` | `/vrs team` |
| `/vrs st` | `/vrs status` |
| `/vrs u` | `/vrs upgrade` |
