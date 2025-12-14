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
```

**Permission:** `vrs.player` (default: true)

**Requirements:**
- Join switch must be enabled
- Not on cooldown (or have bypass permission)
- Starter weapon and helmet must be selected
- Not already in a run

**Behavior:**
- **Solo:** Starts countdown immediately when ready
- **Team:** Waits for all online team members to be ready, then starts countdown
- **Toggle:** Running again while ready will cancel ready status

**Examples:**
```
/vrs ready    # Mark yourself ready (or unready if already ready)
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

### `/vrs team join <team>`

Joins a team you've been invited to.

**Usage:**
```
/vrs team join <team>
```

**Permission:** `vrs.team.join` (default: true)

**Arguments:**
| Argument | Description |
|----------|-------------|
| `team` | Team name to join |

**Requirements:**
- Must have valid invite to the team
- Team must not be full
- Must not be in a team already

**Examples:**
```
/vrs team join Alpha
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

### `/vrs join enable|disable`

Toggles the global join switch.

**Usage:**
```
/vrs join enable
/vrs join disable
```

**Permission:** `vrs.admin.join` (default: op)

**Behavior on disable:**
- New players cannot enter combat
- Active players receive grace warning
- After grace period, players are ejected to prep area

---

### `/vrs world enable|disable <world>`

Enables or disables a combat world.

**Usage:**
```
/vrs world enable <world>
/vrs world disable <world>
/vrs world list
```

**Permission:** `vrs.admin.world` (default: op)

**Arguments:**
| Argument | Description |
|----------|-------------|
| `world` | World name from config |

**Behavior:**
- Disabled worlds are not selected for new runs
- Existing runs in disabled worlds continue normally

**Examples:**
```
/vrs world enable arena_forest
/vrs world disable arena_nether
/vrs world list
```

---

### `/vrs reload`

Reloads plugin configuration.

**Usage:**
```
/vrs reload
/vrs reload config
/vrs reload lang
/vrs reload items
/vrs reload all
```

**Permission:** `vrs.admin.reload` (default: op)

**Subcommands:**
| Subcommand | Description |
|------------|-------------|
| *(none)* | Reload all configurations |
| `config` | Reload config.yml only |
| `lang` | Reload language files only |
| `items` | Reload item templates only |
| `all` | Same as no argument |

---

### `/vrs force start <player|team>`

Force starts a run for a player or team.

**Usage:**
```
/vrs force start <target> [flags]
```

**Permission:** `vrs.admin.force` (default: op)

**Arguments:**
| Argument | Description |
|----------|-------------|
| `target` | Player name or team name |

**Flags:**
| Flag | Description |
|------|-------------|
| `--bypassCooldown` | Ignore cooldown check |
| `--bypassJoinSwitch` | Ignore join switch status |
| `--world=<name>` | Force specific world |

**Examples:**
```
/vrs force start Steve
/vrs force start Alpha --bypassCooldown
/vrs force start Steve --world=arena_forest
```

---

### `/vrs force stop <player|team>`

Force stops a run for a player or team.

**Usage:**
```
/vrs force stop <target> [flags]
```

**Permission:** `vrs.admin.force` (default: op)

**Arguments:**
| Argument | Description |
|----------|-------------|
| `target` | Player name or team name |

**Flags:**
| Flag | Description |
|------|-------------|
| `--noPenalty` | Don't apply death penalty |
| `--noCooldown` | Don't apply cooldown |

**Examples:**
```
/vrs force stop Steve
/vrs force stop Alpha --noPenalty
```

---

### `/vrs merchant spawn [world] [template]`

Manually spawns a merchant.

**Usage:**
```
/vrs merchant spawn [world] [template]
```

**Permission:** `vrs.admin.merchant` (default: op)

**Arguments:**
| Argument | Description | Default |
|----------|-------------|---------|
| `world` | Target world | Current world |
| `template` | Merchant template ID | Random |

**Examples:**
```
/vrs merchant spawn
/vrs merchant spawn arena_forest basic
/vrs merchant spawn arena_forest advanced
```

---

### `/vrs merchant clear [world]`

Removes all merchants.

**Usage:**
```
/vrs merchant clear [world]
```

**Permission:** `vrs.admin.merchant` (default: op)

**Arguments:**
| Argument | Description | Default |
|----------|-------------|---------|
| `world` | Target world | All worlds |

---

### `/vrs spawner pause|resume [world]`

Pauses or resumes enemy spawning.

**Usage:**
```
/vrs spawner pause [world]
/vrs spawner resume [world]
```

**Permission:** `vrs.admin.spawner` (default: op)

**Arguments:**
| Argument | Description | Default |
|----------|-------------|---------|
| `world` | Target world | All worlds |

---

### `/vrs item capture <type> <group> <level> <templateId>`

Captures held item as a template.

**Usage:**
```
/vrs item capture weapon <group> <level> <templateId>
/vrs item capture helmet <group> <level> <templateId>
```

**Permission:** `vrs.admin.capture` (default: op)

**Arguments:**
| Argument | Description |
|----------|-------------|
| `type` | `weapon` or `helmet` |
| `group` | Equipment group ID |
| `level` | Level number |
| `templateId` | Unique template ID |

**Behavior:**
- Captures NBT of item in main hand
- Saves to `data/items/weapons.yml` or `data/items/helmets.yml`
- Can be used in equipment pools

**Examples:**
```
# Hold diamond sword, then:
/vrs item capture weapon sword 3 sword_diamond_3

# Hold iron helmet, then:
/vrs item capture helmet medium 2 helmet_iron_2
```

---

### `/vrs admin merchant template create <templateId> [displayName]`

Creates a new merchant template.

**Usage:**
```
/vrs admin merchant template create <templateId> [displayName]
```

**Permission:** `vrs.admin` (default: op)

**Arguments:**
| Argument | Description |
|----------|-------------|
| `templateId` | Unique template identifier (alphanumeric + underscore) |
| `displayName` | Display name for the merchant (optional, supports color codes) |

**Examples:**
```
/vrs admin merchant template create potions "§dPotion Merchant"
/vrs admin merchant template create basic_supplies "§eBasic Supplies"
```

---

### `/vrs admin merchant template delete <templateId>`

Deletes a merchant template.

**Usage:**
```
/vrs admin merchant template delete <templateId>
```

**Permission:** `vrs.admin` (default: op)

---

### `/vrs admin merchant template list`

Lists all merchant templates.

**Usage:**
```
/vrs admin merchant template list
```

**Permission:** `vrs.admin` (default: op)

---

### `/vrs admin merchant trade add <templateId> <costAmount> [maxUses]`

Adds a trade to a merchant template using the item in your main hand.

**Usage:**
```
/vrs admin merchant trade add <templateId> <costAmount> [maxUses]
```

**Permission:** `vrs.admin` (default: op)

**Arguments:**
| Argument | Description | Default |
|----------|-------------|---------|
| `templateId` | Target merchant template | - |
| `costAmount` | Number of coins required | - |
| `maxUses` | Maximum times this trade can be used | 10 |

**Behavior:**
- Captures the NBT of the item in your main hand
- Creates an item template in `data/items/`
- Adds the trade to the merchant template
- Cost is always in coins (emeralds)

**Examples:**
```
# Hold a health potion, then:
/vrs admin merchant trade add potions 25 5
# Result: Trade for health potion, costs 25 coins, max 5 uses

# Hold a golden apple:
/vrs admin merchant trade add basic_supplies 15 3
```

---

### `/vrs admin merchant trade remove <templateId> <index>`

Removes a trade from a merchant template by index.

**Usage:**
```
/vrs admin merchant trade remove <templateId> <index>
```

**Permission:** `vrs.admin` (default: op)

**Arguments:**
| Argument | Description |
|----------|-------------|
| `templateId` | Target merchant template |
| `index` | Trade index (0-based, shown in trade list) |

---

### `/vrs admin merchant trade list <templateId>`

Lists all trades in a merchant template.

**Usage:**
```
/vrs admin merchant trade list <templateId>
```

**Permission:** `vrs.admin` (default: op)

---

### `/vrs setperma <player> <amount>`

Sets a player's perma-score.

**Usage:**
```
/vrs setperma <player> <amount>
/vrs setperma <player> +<amount>
/vrs setperma <player> -<amount>
```

**Permission:** `vrs.admin` (default: op)

**Arguments:**
| Argument | Description |
|----------|-------------|
| `player` | Target player |
| `amount` | Score value (prefix +/- for relative) |

**Examples:**
```
/vrs setperma Steve 1000        # Set to 1000
/vrs setperma Steve +100        # Add 100
/vrs setperma Steve -50         # Remove 50
```

---

## 4. Debug Commands

### `/vrs debug player <player>`

Shows detailed player state information.

**Usage:**
```
/vrs debug player <player>
```

**Permission:** `vrs.admin.debug` (default: op)

**Output includes:**
- All PlayerState fields
- Team membership details
- Active run details
- Pending rewards count
- Internal timers

---

### `/vrs debug perf`

Shows performance statistics.

**Usage:**
```
/vrs debug perf
```

**Permission:** `vrs.admin.debug` (default: op)

**Output includes:**
- Command queue size
- Spawn queue size
- Active VRS mob counts per world
- Tick budget usage
- Average operation times

---

### `/vrs debug templates <name> <player>`

Tests template expansion with a player's context.

**Usage:**
```
/vrs debug templates <templateName> <player>
```

**Permission:** `vrs.admin.debug` (default: op)

**Examples:**
```
/vrs debug templates enterCommand Steve
```

**Output:**
```
Template: tp ${player} ${world} ${x} ${y} ${z}
Expanded: tp Steve arena_forest 100 64 100
```

---

### `/vrs debug run <runId>`

Shows detailed run state information.

**Usage:**
```
/vrs debug run <runId>
/vrs debug run list
```

**Permission:** `vrs.admin.debug` (default: op)

---

## Permission Summary

| Permission | Description | Default |
|------------|-------------|---------|
| `vrs.player` | Basic player commands | true |
| `vrs.team` | Team-related commands | true |
| `vrs.team.create` | Create teams | true |
| `vrs.team.invite` | Invite to team | true |
| `vrs.team.join` | Join teams | true |
| `vrs.team.leave` | Leave teams | true |
| `vrs.team.kick` | Kick from team | true |
| `vrs.team.disband` | Disband teams | true |
| `vrs.admin` | All admin commands | op |
| `vrs.admin.reload` | Reload configuration | op |
| `vrs.admin.world` | World management | op |
| `vrs.admin.join` | Join switch control | op |
| `vrs.admin.force` | Force start/stop | op |
| `vrs.admin.capture` | Item/trade capture | op |
| `vrs.admin.debug` | Debug commands | op |
| `vrs.admin.merchant` | Merchant control | op |
| `vrs.admin.spawner` | Spawner control | op |
| `vrs.cooldown.bypass` | Bypass death cooldown | op |

---

## Tab Completion

All commands support tab completion:

- Player names auto-complete for team/admin commands
- Team names auto-complete for join/force commands
- World names auto-complete for world commands
- Template IDs auto-complete for capture commands
- Subcommands auto-complete at each level

---

## Command Aliases

| Alias | Full Command |
|-------|--------------|
| `/vrs s` | `/vrs starter` |
| `/vrs r` | `/vrs ready` |
| `/vrs t` | `/vrs team` |
| `/vrs st` | `/vrs status` |
