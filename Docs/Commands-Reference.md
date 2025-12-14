# KedamaSurvivors - Commands Reference

Complete reference for all commands in the KedamaSurvivors plugin.

---

## Command Structure

All commands use the root command `/vrs` (short for "Vampire Rogue Survivors").

```
/vrs <subcommand> [arguments]
```

---

## Table of Contents

1. [Player Commands](#1-player-commands)
2. [Team Commands](#2-team-commands)
3. [Admin Commands](#3-admin-commands)
4. [Debug Commands](#4-debug-commands)

---

## 1. Player Commands

### `/vrs starter`

Opens the starter equipment selection GUI.

**Usage:**
```
/vrs starter
/vrs starter weapon
/vrs starter helmet
/vrs starter clear
```

**Subcommands:**
| Subcommand | Description |
|------------|-------------|
| *(none)* | Opens weapon selection GUI (then automatically opens helmet) |
| `weapon` | Opens weapon selection GUI only |
| `helmet` | Opens helmet selection GUI only |
| `clear` | Clears current selections and removes starter items |

**Permission:** `vrs.player` (default: true)

**Requirements:**
- Must be in LOBBY mode
- Helmet selection requires weapon selection first (configurable)

**Examples:**
```
/vrs starter           # Start selection process
/vrs starter weapon    # Re-select weapon only
/vrs starter helmet    # Re-select helmet only
/vrs starter clear     # Clear selections to start over
```

---

### `/vrs ready`

Toggles ready status for entering combat.

**Usage:**
```
/vrs ready
/vrs ready solo
```

**Arguments:**
| Argument | Description |
|----------|-------------|
| *(none)* | Toggle ready status (requires existing team) |
| `solo` | Auto-create a solo team and toggle ready |

**Permission:** `vrs.player` (default: true)

**Requirements:**
- Join switch must be enabled
- Not on cooldown (or have bypass permission)
- Starter weapon and helmet must be selected
- Not already in a run
- Must be in a team (use `solo` argument to auto-create)

**Behavior:**
- **Solo (`/vrs ready solo`):** Creates a team named `Team_{PlayerName}_{random4digits}` and marks ready
- **Team:** Waits for all online team members to be ready, then starts countdown
- **Toggle:** Running again while ready will cancel ready status

**Examples:**
```
/vrs ready         # Mark yourself ready (requires team)
/vrs ready solo    # Create solo team and mark ready
```

---

### `/vrs quit`

Voluntarily leaves the current run.

**Usage:**
```
/vrs quit
```

**Permission:** `vrs.player` (default: true)

**Requirements:**
- Must be in an active run (IN_RUN mode)

**Effects:**
- Does NOT apply death penalty (equipment kept)
- Returns to prep area
- May apply quit cooldown (configurable)

**Examples:**
```
/vrs quit    # Leave current run and return to prep area
```

---

### `/vrs status`

Shows your current game status.

**Usage:**
```
/vrs status
```

**Permission:** `vrs.player` (default: true)

**Output includes:**
- Current mode (LOBBY, READY, IN_RUN, etc.)
- Run world (if in run)
- Team name and members (if in team)
- Cooldown remaining (if on cooldown)
- Weapon group and level
- Helmet group and level
- XP progress and held XP
- Coin balance
- Perma-score

**Example output:**
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

## 2. Team Commands

### `/vrs team create <name>`

Creates a new team.

**Usage:**
```
/vrs team create <name>
```

**Permission:** `vrs.team.create` (default: true)

**Arguments:**
| Argument | Description |
|----------|-------------|
| `name` | Team name (alphanumeric, max 16 characters) |

**Requirements:**
- Must not be in a team already
- Must be in LOBBY mode

**Examples:**
```
/vrs team create Alpha
/vrs team create 队伍一
```

---

### `/vrs team invite <player>`

Invites a player to your team.

**Usage:**
```
/vrs team invite <player>
```

**Permission:** `vrs.team.invite` (default: true)

**Arguments:**
| Argument | Description |
|----------|-------------|
| `player` | Target player name |

**Requirements:**
- Must be team owner
- Team must not be full (max 5 by default)
- Target must not be in a team

**Behavior:**
- Invite expires after 60 seconds (configurable)
- Target receives clickable message to join

**Examples:**
```
/vrs team invite Steve
/vrs team invite Notch
```

---

### `/vrs team accept <team>`

Accepts a team invitation you've received.

**Usage:**
```
/vrs team accept <team>
```

**Permission:** `vrs.team.join` (default: true)

**Arguments:**
| Argument | Description |
|----------|-------------|
| `team` | Team name to accept invitation for |

**Requirements:**
- Must have valid invite to the team
- Team must not be full
- Must not be in a team already

**Examples:**
```
/vrs team accept Alpha
```

---

### `/vrs team leave`

Leaves your current team.

**Usage:**
```
/vrs team leave
```

**Permission:** `vrs.team.leave` (default: true)

**Requirements:**
- Must be in a team
- If owner, must transfer ownership first or disband

**Behavior:**
- If in run, immediately triggers respawn check
- Team is notified

**Examples:**
```
/vrs team leave
```

---

### `/vrs team kick <player>`

Kicks a player from your team.

**Usage:**
```
/vrs team kick <player>
```

**Permission:** `vrs.team.kick` (default: true)

**Arguments:**
| Argument | Description |
|----------|-------------|
| `player` | Player to kick |

**Requirements:**
- Must be team owner
- Target must be in your team

**Examples:**
```
/vrs team kick Steve
```

---

### `/vrs team disband`

Disbands your team.

**Usage:**
```
/vrs team disband
```

**Permission:** `vrs.team.disband` (default: true)

**Requirements:**
- Must be team owner
- Should not be in active run (warning if so)

**Behavior:**
- All members removed from team
- If in run, run continues as solo for each member

**Examples:**
```
/vrs team disband
```

---

### `/vrs team list`

Lists your team members and their status.

**Usage:**
```
/vrs team list
```

**Permission:** `vrs.team` (default: true)

**Output includes:**
- Team name
- Owner
- Member list with online/ready status
- Active run info (if applicable)

---

### `/vrs team transfer <player>`

Transfers team ownership to another member.

**Usage:**
```
/vrs team transfer <player>
```

**Permission:** `vrs.team` (default: true)

**Arguments:**
| Argument | Description |
|----------|-------------|
| `player` | New owner (must be team member) |

**Requirements:**
- Must be team owner
- Target must be in the team

---

## 3. Admin Commands

All admin commands are under `/vrs admin` or `/vrs reload`.

### `/vrs reload`

Reloads all plugin configuration.

**Usage:**
```
/vrs reload
```

**Permission:** `vrs.admin` (default: op)

**Behavior:**
- Reloads config.yml, language files, item templates, and all data files
- Active runs are not affected

---

### `/vrs admin`

Root command for all administration functions.

**Usage:**
```
/vrs admin <subcommand>
```

**Permission:** `vrs.admin` (default: op)

**Available Subcommands:**
| Subcommand | Description |
|------------|-------------|
| `status` | Show server status |
| `endrun` | End active runs |
| `forcestart` | Force start a team's run |
| `kick` | Kick player from game |
| `reset` | Reset player state |
| `setperma` | Set player's perma-score |
| `join` | Toggle global join switch |
| `world` | World management |
| `starter` | Starter option management |
| `equipment` | Equipment group management |
| `spawner` | Enemy archetype management |
| `merchant` | Merchant template management |
| `config` | Runtime config management |
| `debug` | Debug commands |

---

### `/vrs admin status`

Shows server game status.

**Usage:**
```
/vrs admin status
```

**Output includes:**
- Total players tracked
- Active teams
- Active runs
- Players currently in runs

---

### `/vrs admin join enable|disable`

Toggles the global join switch.

**Usage:**
```
/vrs admin join enable
/vrs admin join disable
/vrs admin join
```

**Permission:** `vrs.admin` (default: op)

**Behavior:**
- Without argument: shows current status
- `enable`: allows new players to enter combat
- `disable`: prevents new games, active players receive grace warning and are ejected

---

### `/vrs admin forcestart <team>`

Force starts a run for a team.

**Usage:**
```
/vrs admin forcestart <teamName>
```

**Permission:** `vrs.admin` (default: op)

**Arguments:**
| Argument | Description |
|----------|-------------|
| `teamName` | Name of the team to start |

**Requirements:**
- Team must exist
- Team must not already be in a run

**Examples:**
```
/vrs admin forcestart Alpha
```

---

### `/vrs admin endrun [team]`

Ends active runs.

**Usage:**
```
/vrs admin endrun
/vrs admin endrun <teamName>
```

**Permission:** `vrs.admin` (default: op)

**Behavior:**
- Without argument: ends ALL active runs
- With team name: ends only that team's run

**Examples:**
```
/vrs admin endrun           # End all runs
/vrs admin endrun Alpha     # End Alpha team's run
```

---

### `/vrs admin kick <player>`

Kicks a player from the game system.

**Usage:**
```
/vrs admin kick <player>
```

**Permission:** `vrs.admin` (default: op)

**Behavior:**
- Removes player from their team
- Resets all player state

---

### `/vrs admin reset <player>`

Resets a player's game state.

**Usage:**
```
/vrs admin reset <player>
```

**Permission:** `vrs.admin` (default: op)

**Behavior:**
- Resets player state to defaults
- Does not remove from team

---

### `/vrs admin setperma <player> <amount>`

Sets a player's perma-score.

**Usage:**
```
/vrs admin setperma <player> <amount>
```

**Permission:** `vrs.admin` (default: op)

**Arguments:**
| Argument | Description |
|----------|-------------|
| `player` | Target player |
| `amount` | Score value (integer) |

**Examples:**
```
/vrs admin setperma Steve 1000
```

---

### `/vrs admin world`

Manages combat world configurations.

**Subcommands:**
```
/vrs admin world create <name> [displayName]
/vrs admin world delete <name>
/vrs admin world list
/vrs admin world enable <name>
/vrs admin world disable <name>
/vrs admin world set displayname <name> <displayName>
/vrs admin world set weight <name> <weight>
/vrs admin world set bounds <name> <minX> <maxX> <minZ> <maxZ>
```

**Permission:** `vrs.admin` (default: op)

**Examples:**
```
/vrs admin world create combat_arena "Forest Arena"
/vrs admin world set bounds combat_arena -500 500 -500 500
/vrs admin world enable combat_arena
/vrs admin world list
```

---

### `/vrs admin starter`

Manages starter equipment options.

**Subcommands:**
```
/vrs admin starter create <weapon|helmet> <optionId> <templateId> <group> <level> [displayName]
/vrs admin starter delete <weapon|helmet> <optionId>
/vrs admin starter list [weapon|helmet]
/vrs admin starter set displayname <weapon|helmet> <optionId> <displayName>
/vrs admin starter set template <weapon|helmet> <optionId> <templateId>
/vrs admin starter set group <weapon|helmet> <optionId> <group>
/vrs admin starter set level <weapon|helmet> <optionId> <level>
```

**Permission:** `vrs.admin` (default: op)

---

### `/vrs admin equipment`

Manages equipment groups and items.

**Group Subcommands:**
```
/vrs admin equipment group create <weapon|helmet> <groupId> [displayName]
/vrs admin equipment group delete <weapon|helmet> <groupId>
/vrs admin equipment group list [weapon|helmet]
/vrs admin equipment group set displayname <weapon|helmet> <groupId> <displayName>
```

**Item Subcommands:**
```
/vrs admin equipment item add <weapon|helmet> <groupId> <level>
/vrs admin equipment item remove <weapon|helmet> <groupId> <level> <index>
/vrs admin equipment item list <weapon|helmet> <groupId> [level]
```

**Permission:** `vrs.admin` (default: op)

**Note:** The `item add` command captures the NBT of the item in your main hand and auto-generates a template ID.

**Examples:**
```
# Create a sword equipment group
/vrs admin equipment group create weapon sword "Sword Path"

# Hold an iron sword, then add it to level 1
/vrs admin equipment item add weapon sword 1

# List items in the sword group
/vrs admin equipment item list weapon sword
```

---

### `/vrs admin spawner`

Manages enemy archetypes.

**Subcommands:**
```
/vrs admin spawner archetype create <id> <entityType> [weight]
/vrs admin spawner archetype delete <id>
/vrs admin spawner archetype list
/vrs admin spawner archetype addcommand <id> <command...>
/vrs admin spawner archetype removecommand <id> <index>
/vrs admin spawner archetype reward <id> <xpBase> <xpPerLevel> <coinBase> <coinPerLevel> <permaChance>
/vrs admin spawner archetype set weight <id> <weight>
/vrs admin spawner archetype set entitytype <id> <entityType>
```

**Permission:** `vrs.admin` (default: op)

**Examples:**
```
# Create a zombie archetype
/vrs admin spawner archetype create zombie ZOMBIE 3.0

# Add spawn command
/vrs admin spawner archetype addcommand zombie summon zombie ${sx} ${sy} ${sz} {Tags:["vrs_mob","vrs_lvl_${enemyLevel}"]}

# Set rewards
/vrs admin spawner archetype reward zombie 10 5 1 1 0.01
```

---

### `/vrs admin merchant`

Manages merchant templates and trades.

**Template Subcommands:**
```
/vrs admin merchant template create <templateId> [displayName]
/vrs admin merchant template delete <templateId>
/vrs admin merchant template list
/vrs admin merchant template set displayname <templateId> <displayName>
```

**Trade Subcommands:**
```
/vrs admin merchant trade add <templateId> <costAmount> [maxUses]
/vrs admin merchant trade remove <templateId> <index>
/vrs admin merchant trade list <templateId>
```

**Permission:** `vrs.admin` (default: op)

**Note:** The `trade add` command captures the NBT of the item in your main hand.

**Examples:**
```
# Create a merchant template
/vrs admin merchant template create potions "§dPotion Merchant"

# Hold a health potion, add as trade for 25 coins, max 5 uses
/vrs admin merchant trade add potions 25 5

# List trades
/vrs admin merchant trade list potions
```

---

### `/vrs admin config`

Manages runtime configuration values.

**Subcommands:**
```
/vrs admin config get <property>
/vrs admin config set <property> <value>
/vrs admin config list [category]
```

**Permission:** `vrs.admin` (default: op)

**Available Categories:** teleport, timing, spawning, rewards, progression, teams, merchants, upgrade, scoreboard

**Examples:**
```
/vrs admin config list timing
/vrs admin config get deathCooldownSeconds
/vrs admin config set deathCooldownSeconds 120
```

---

## 4. Debug Commands

All debug commands are under `/vrs admin debug`.

### `/vrs admin debug player <player>`

Shows detailed player state information.

**Usage:**
```
/vrs admin debug player <player>
```

**Permission:** `vrs.admin` (default: op)

**Output includes:**
- All PlayerState fields
- Team membership details
- Active run details
- Pending rewards count
- Internal timers

---

### `/vrs admin debug perf`

Shows performance statistics.

**Usage:**
```
/vrs admin debug perf
```

**Permission:** `vrs.admin` (default: op)

**Output includes:**
- Players tracked, teams, active runs
- Memory usage
- TPS

---

### `/vrs admin debug templates <name> <player>`

Tests template expansion with a player's context.

**Usage:**
```
/vrs admin debug templates <templateName> <player>
```

**Permission:** `vrs.admin` (default: op)

**Examples:**
```
/vrs admin debug templates enterCommand Steve
```

**Output:**
```
Template: tp ${player} ${world} ${x} ${y} ${z}
Expanded: tp Steve arena_forest 100 64 100
```

---

### `/vrs admin debug run [runId|list]`

Shows detailed run state information.

**Usage:**
```
/vrs admin debug run list
/vrs admin debug run <runId>
```

**Permission:** `vrs.admin` (default: op)

---

## Permission Summary

| Permission | Description | Default |
|------------|-------------|---------|
| `vrs.player` | Basic player commands | true |
| `vrs.team` | Team-related commands | true |
| `vrs.team.create` | Create teams | true |
| `vrs.team.invite` | Invite to team | true |
| `vrs.team.join` | Accept team invitations | true |
| `vrs.team.leave` | Leave teams | true |
| `vrs.team.kick` | Kick from team | true |
| `vrs.team.disband` | Disband teams | true |
| `vrs.admin` | All admin commands | op |
| `vrs.cooldown.bypass` | Bypass death cooldown | op |

---

## Tab Completion

All commands support tab completion:

- Player names auto-complete for team/admin commands
- Team names auto-complete for accept/forcestart commands
- World names auto-complete for world commands
- Subcommands auto-complete at each level

---

## Command Aliases

| Alias | Full Command |
|-------|--------------|
| `/vrs s` | `/vrs starter` |
| `/vrs r` | `/vrs ready` |
| `/vrs t` | `/vrs team` |
| `/vrs st` | `/vrs status` |
