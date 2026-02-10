# KedamaSurvivors Commands Reference

This reference describes the **current implemented command tree**.

Root command:

```text
/vrs
```

## 1. Permission Model (Current Behavior)

- `/vrs` is registered with base permission `vrs.player`.
- `/vrs admin ...` is guarded by `vrs.admin` at the top-level admin subcommand.
- `/vrs reload` is also admin-only.

Note: nested admin modules have dedicated permission strings in code, but entry is still via `/vrs admin`.

## 2. Player Commands

## 2.1 Starter

```text
/vrs starter
/vrs starter weapon [optionId]
/vrs starter helmet [optionId]
/vrs starter world
/vrs starter status
/vrs starter clear
```

Behavior:

- `weapon` / `helmet` without id opens GUI.
- `world` opens world-selection GUI only if enabled and player is team leader.
- starter changes are blocked when team progression is locked.

## 2.2 Ready

```text
/vrs ready
/vrs ready solo
```

Behavior:

- `solo` auto-creates a solo team if needed.
- team countdown starts when all members are ready.

## 2.3 Team

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

Notes:

- progression-locked teams cannot invite/accept new members.
- leaving/kicking resets that player's campaign attachment.

## 2.4 Run Control

```text
/vrs quit
/vrs status
/vrs upgrade <power|defense>
```

- `quit` works in `COUNTDOWN` and `IN_RUN`.
- `upgrade` only works when upgrade is pending.

## 3. Core Admin Commands

```text
/vrs admin status
/vrs admin endrun [team]
/vrs admin forcestart <team>
/vrs admin kick <player>
/vrs admin reset <player>
/vrs admin setperma <player> <amount>   # compatibility alias -> perma set
/vrs admin join <enable|disable>
/vrs admin multiplier [on|off|set <n>|perma <on|off>]
/vrs admin debug <player|perf|templates|run> ...
```

Also available:

```text
/vrs reload
```

## 4. Admin Module Commands

## 4.1 Coin

```text
/vrs admin coin add <player> <amount>
/vrs admin coin set <player> <amount>
/vrs admin coin get <player>
```

## 4.2 Perma Score

```text
/vrs admin perma add <player> <amount>
/vrs admin perma set <player> <amount>
/vrs admin perma get <player>
```

## 4.3 World Management

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

## 4.4 Starter Option Management

```text
/vrs admin starter create <weapon|helmet> <optionId> <templateId> <group> <level> [displayName]
/vrs admin starter delete <weapon|helmet> <optionId>
/vrs admin starter list [weapon|helmet]
/vrs admin starter set displayname <weapon|helmet> <optionId> <displayName>
/vrs admin starter set template <weapon|helmet> <optionId> <templateId>
/vrs admin starter set group <weapon|helmet> <optionId> <group>
/vrs admin starter set level <weapon|helmet> <optionId> <level>
```

## 4.5 Equipment Group/Pool Management

```text
/vrs admin equipment group create <weapon|helmet> <groupId> [displayName]
/vrs admin equipment group delete <weapon|helmet> <groupId>
/vrs admin equipment group list [weapon|helmet]
/vrs admin equipment group set displayname <weapon|helmet> <groupId> <displayName>

/vrs admin equipment item add <weapon|helmet> <groupId> <level>
/vrs admin equipment item remove <weapon|helmet> <groupId> <level> <index>
/vrs admin equipment item list <weapon|helmet> <groupId> [level]
```

`item add` captures the item in main hand.

## 4.6 Spawner / Archetype Management

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

`set worlds` accepts comma-separated and/or space-separated world lists.

## 4.7 Merchant Management

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

## 4.8 Runtime Config Management

```text
/vrs admin config get <property>
/vrs admin config set <property> <value>
/vrs admin config list [category...]
```

Supported `list` categories:

- `teleport`, `timing`, `spawning`, `rewards`, `stage`, `battery`, `progression`, `teams`, `merchants`, `upgrade`, `scoreboard`, `worldSelection`, `overheadDisplay`, `feedback`, `all`

### Stage Dynamic Properties

Format:

```text
stage.<groupId>.<field>
```

Supported fields:

- `displayName`
- `worlds`
- `startEnemyLevel`
- `requiredBatteries`
- `clearRewardCoins`
- `clearRewardPermaScore`

Examples:

```text
/vrs admin config get stage.fragment_2.worlds
/vrs admin config set stage.fragment_2.worlds arena_x,arena_y arena_z
/vrs admin config set stage.fragment_2.requiredBatteries 4
```

`worlds` supports multi-arg values and accepts `any`/`none` to clear list.

## 5. Debug Commands

```text
/vrs admin debug player <player>
/vrs admin debug perf
/vrs admin debug templates <templateText> <player>
/vrs admin debug run [list|<runIdPrefix>]
```

## 6. Typical Operator Snippets

Toggle multiplier mode:

```text
/vrs admin multiplier on
/vrs admin multiplier set 3
/vrs admin multiplier perma on
```

Hot-update battery pressure:

```text
/vrs admin config set batterySpawnChance 0.06
/vrs admin config set batterySurgeMobCount 10
```

Inspect stage + battery categories together:

```text
/vrs admin config list stage battery
```
