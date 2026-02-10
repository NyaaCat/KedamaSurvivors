# KedamaSurvivors 命令参考

本文档以当前代码实现为准。

根命令：

```text
/vrs
```

## 1. 权限模型（当前实现）

- `/vrs` 根命令注册权限为 `vrs.player`。
- `/vrs admin ...` 在顶层由 `vrs.admin` 控制。
- `/vrs reload` 也属于管理员命令。

说明：管理员子模块虽然各自有权限节点字符串，但入口仍然是 `/vrs admin`。

## 2. 玩家命令

## 2.1 初始装备

```text
/vrs starter
/vrs starter weapon [optionId]
/vrs starter helmet [optionId]
/vrs starter world
/vrs starter status
/vrs starter clear
```

行为说明：

- `weapon`/`helmet` 不带 id 会打开 GUI。
- `world` 仅在选图功能开启且你是队长时可用。
- 队伍进度被锁定后不能再改 starter。

## 2.2 准备命令

```text
/vrs ready
/vrs ready solo
```

行为说明：

- `solo` 会在无队伍时自动建单人队。
- 队伍全员 ready 后进入倒计时。

## 2.3 队伍命令

```text
/vrs team
/vrs team create [name]
/vrs team invite <player>
/vrs team accept <team>
/vrs team decline <team>
/vrs team leave
/vrs team kick <player>
/vrs team disband
/vrs team transfer <player>
/vrs team list
```

说明：

- 阶段进度锁定的队伍不能再邀请/接收新成员。
- leave/kick 会重置该玩家当前战役归属。

## 2.4 战斗控制

```text
/vrs quit
/vrs status
/vrs upgrade <power|defense>
```

- `quit` 仅在 `COUNTDOWN` / `IN_RUN` 生效。
- `upgrade` 仅在存在待选升级时生效。

## 3. 核心管理员命令

```text
/vrs admin status
/vrs admin endrun [team]
/vrs admin forcestart <team>
/vrs admin kick <player>
/vrs admin reset <player>
/vrs admin setperma <player> <amount>   # 兼容别名，内部转 perma set
/vrs admin join <enable|disable>
/vrs admin multiplier [on|off|set <n>|perma <on|off>]
/vrs admin debug <player|perf|templates|run> ...
```

以及：

```text
/vrs reload
```

## 4. 管理员模块命令

## 4.1 金币

```text
/vrs admin coin add <player> <amount>
/vrs admin coin set <player> <amount>
/vrs admin coin get <player>
```

## 4.2 永久积分

```text
/vrs admin perma add <player> <amount>
/vrs admin perma set <player> <amount>
/vrs admin perma get <player>
```

## 4.3 世界管理

```text
/vrs admin world list
/vrs admin world create <name> [displayName]
/vrs admin world delete <name>
/vrs admin world enable <name>
/vrs admin world disable <name>
/vrs admin world set displayname <name> <displayName>
/vrs admin world set weight <name> <weight>
/vrs admin world set bounds <name> <minX> <maxX> <minZ> <maxZ>
/vrs admin world set cost <name> <cost>
/vrs admin world addspawn <name> [x y z [yaw pitch]]
/vrs admin world removespawn <name> <index>
/vrs admin world listspawns <name>
/vrs admin world clearspawns <name>
```

## 4.4 初始装备选项管理

```text
/vrs admin starter create <weapon|helmet> <optionId> <templateId> <group> <level> [displayName]
/vrs admin starter delete <weapon|helmet> <optionId>
/vrs admin starter list [weapon|helmet]
/vrs admin starter set displayname <weapon|helmet> <optionId> <displayName>
/vrs admin starter set template <weapon|helmet> <optionId> <templateId>
/vrs admin starter set group <weapon|helmet> <optionId> <group>
/vrs admin starter set level <weapon|helmet> <optionId> <level>
```

## 4.5 装备组与物品池管理

```text
/vrs admin equipment group create <weapon|helmet> <groupId> [displayName]
/vrs admin equipment group delete <weapon|helmet> <groupId>
/vrs admin equipment group list [weapon|helmet]
/vrs admin equipment group set displayname <weapon|helmet> <groupId> <displayName>

/vrs admin equipment item add <weapon|helmet> <groupId> <level>
/vrs admin equipment item remove <weapon|helmet> <groupId> <level> <index>
/vrs admin equipment item list <weapon|helmet> <groupId> [level]
```

`item add` 会捕获主手物品。

## 4.6 刷怪原型管理

```text
/vrs admin spawner archetype create <id> <entityType> [weight]
/vrs admin spawner archetype delete <id>
/vrs admin spawner archetype list
/vrs admin spawner archetype addcommand <id> <command...>
/vrs admin spawner archetype removecommand <id> <index>
/vrs admin spawner archetype reward <id> <xpAmount> <xpChance> <coinAmount> <coinChance> <permaAmount> <permaChance>
/vrs admin spawner archetype set weight <id> <weight>
/vrs admin spawner archetype set entitytype <id> <entityType>
/vrs admin spawner archetype set minspawnlevel <id> <level>
/vrs admin spawner archetype set worlds <id> <world1[,world2...] [world3...]|any>
```

`set worlds` 同时支持逗号分隔和空格分隔的多 world 参数。

## 4.7 商店管理

```text
/vrs admin merchant template create <templateId> [displayName]
/vrs admin merchant template delete <templateId>
/vrs admin merchant template list
/vrs admin merchant template set displayname <templateId> <displayName>

/vrs admin merchant trade add <templateId> <costAmount> [maxUses]
/vrs admin merchant trade remove <templateId> <index>
/vrs admin merchant trade list <templateId>

/vrs admin merchant pool create <poolId>
/vrs admin merchant pool delete <poolId>
/vrs admin merchant pool additem <poolId> <price> [weight]
/vrs admin merchant pool removeitem <poolId> <index>
/vrs admin merchant pool list [poolId]

/vrs admin merchant spawn <poolId> <multi|single> [limited|unlimited] [all|random]
/vrs admin merchant despawn [radius]
/vrs admin merchant active
```

## 4.8 运行时配置管理

```text
/vrs admin config get <property>
/vrs admin config set <property> <value>
/vrs admin config list [category...]
```

`list` 支持分类：

- `teleport`, `timing`, `spawning`, `rewards`, `stage`, `battery`, `progression`, `teams`, `merchants`, `upgrade`, `scoreboard`, `worldSelection`, `overheadDisplay`, `feedback`, `all`

### 阶段动态属性

格式：

```text
stage.<groupId>.<field>
```

支持字段：

- `displayName`
- `worlds`
- `startEnemyLevel`
- `requiredBatteries`
- `clearRewardCoins`
- `clearRewardPermaScore`

示例：

```text
/vrs admin config get stage.fragment_2.worlds
/vrs admin config set stage.fragment_2.worlds arena_x,arena_y arena_z
/vrs admin config set stage.fragment_2.requiredBatteries 4
```

`worlds` 支持多参数；`any`/`none` 可清空列表。

## 5. 调试命令

```text
/vrs admin debug player <player>
/vrs admin debug perf
/vrs admin debug templates <templateText> <player>
/vrs admin debug run [list|<runIdPrefix>]
```

## 6. 运维常用片段

倍率模式：

```text
/vrs admin multiplier on
/vrs admin multiplier set 3
/vrs admin multiplier perma on
```

热更电池压力：

```text
/vrs admin config set batterySpawnChance 0.06
/vrs admin config set batterySurgeMobCount 10
```

一次查看多个分类：

```text
/vrs admin config list stage battery
```
