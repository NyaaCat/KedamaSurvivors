# Quick Start Guide

This guide walks you through setting up KedamaSurvivors from scratch and starting your first game.

## Prerequisites

### Required

- **Prep Area (Lobby)**: Any world where players can join and select equipment before entering combat. This can be your server's main spawn world.
- **Combat World(s)**: At least one world with defined spawn boundaries where the roguelite gameplay takes place.

### Optional Plugins

| Plugin | Benefit |
|--------|---------|
| **Multiverse-Core** | Manage multiple combat worlds easily |
| **RPGItems-reloaded** | Create custom weapons and armor with special abilities |
| **EssentialsX** | Convenient spawn/teleport management |

---

## Step-by-Step Setup

### Step 1: Configure Combat World(s)

Combat worlds are where players fight enemies. You need at least one configured.

**Option A: Edit `data/worlds.yml` directly:**

```yaml
worlds:
  - name: "combat_arena"
    displayName: "§2Forest Arena"
    enabled: true
    weight: 1.0
    spawnBounds:
      minX: -500
      maxX: 500
      minZ: -500
      maxZ: 500
```

**Option B: Use admin commands:**

```
/vrs admin world add combat_arena
/vrs admin world bounds combat_arena -500 500 -500 500
/vrs admin world enable combat_arena
```

**Configuration Fields:**

| Field | Description |
|-------|-------------|
| `name` | Bukkit world name (must exist) |
| `displayName` | Name shown to players (supports color codes) |
| `enabled` | Whether this world can be selected |
| `weight` | Selection probability (higher = more likely) |
| `spawnBounds` | Rectangle defining valid spawn area (minX, maxX, minZ, maxZ) |

---

### Step 2: Set Up Enemy Archetypes

Archetypes define the enemy types that spawn during combat.

**Edit `data/archetypes.yml`:**

```yaml
archetypes:
  zombie:
    enemyType: "minecraft:zombie"
    weight: 3.0
    spawnCommands:
      - "summon zombie ${sx} ${sy} ${sz} {Tags:[\"vrs_mob\",\"vrs_lvl_${enemyLevel}\"]}"
    rewards:
      xpBase: 10
      xpPerLevel: 5
      coinBase: 1
      coinPerLevel: 1
      permaScoreChance: 0.01

  skeleton:
    enemyType: "minecraft:skeleton"
    weight: 2.0
    spawnCommands:
      - "summon skeleton ${sx} ${sy} ${sz} {Tags:[\"vrs_mob\",\"vrs_lvl_${enemyLevel}\"]}"
    rewards:
      xpBase: 15
      xpPerLevel: 7
      coinBase: 2
      coinPerLevel: 1
      permaScoreChance: 0.02

  spider:
    enemyType: "minecraft:spider"
    weight: 2.0
    spawnCommands:
      - "summon spider ${sx} ${sy} ${sz} {Tags:[\"vrs_mob\",\"vrs_lvl_${enemyLevel}\"]}"
    rewards:
      xpBase: 12
      xpPerLevel: 6
      coinBase: 1
      coinPerLevel: 1
      permaScoreChance: 0.01
```

**Available Placeholders:**

| Placeholder | Description |
|-------------|-------------|
| `${sx}`, `${sy}`, `${sz}` | Spawn coordinates |
| `${enemyLevel}` | Calculated enemy level |
| `${player}` | Target player name |

**Important:** Enemies must have the `vrs_mob` tag to be tracked by the plugin.

---

### Step 3: Capture and Configure Starter Equipment

Players select their starting weapon and helmet before entering combat.

#### 3a: Capture Item Templates

Hold the item you want to use and run:

```
/vrs item capture weapon <group> <level> <templateId>
/vrs item capture helmet <group> <level> <templateId>
```

**Examples:**

```
# Hold an iron sword, then run:
/vrs item capture weapon sword 1 sword_iron_1

# Hold a leather helmet, then run:
/vrs item capture helmet light 1 helmet_leather_1
```

This saves the item's NBT data to `data/items/<templateId>.yml`.

#### 3b: Configure Starter Options

**Edit `data/starters.yml`:**

```yaml
weapons:
  - optionId: "starter_sword"
    displayName: "§fIron Sword"
    displayItem:
      material: IRON_SWORD
      name: "§fIron Sword"
      lore:
        - "§7Basic melee weapon"
        - "§7Good for beginners"
    templateId: "sword_iron_1"
    group: "sword"
    level: 1

  - optionId: "starter_bow"
    displayName: "§fBow"
    displayItem:
      material: BOW
      name: "§fBow"
      lore:
        - "§7Ranged weapon"
        - "§7Keep your distance"
    templateId: "bow_basic_1"
    group: "bow"
    level: 1

helmets:
  - optionId: "starter_leather"
    displayName: "§fLeather Cap"
    displayItem:
      material: LEATHER_HELMET
      name: "§fLeather Cap"
      lore:
        - "§7Light armor"
        - "§7Low protection, high mobility"
    templateId: "helmet_leather_1"
    group: "light"
    level: 1

  - optionId: "starter_chain"
    displayName: "§7Chainmail Helmet"
    displayItem:
      material: CHAINMAIL_HELMET
      name: "§7Chainmail Helmet"
      lore:
        - "§7Medium armor"
        - "§7Balanced protection"
    templateId: "helmet_chain_1"
    group: "chain"
    level: 1
```

---

### Step 4: Set Up Equipment Progression

Define the equipment pools for each level of progression.

**Edit `data/equipment/weapons.yml`:**

```yaml
groups:
  sword:
    displayName: "Sword"
    levelItems:
      1:
        - "sword_iron_1"
      2:
        - "sword_iron_2"
        - "sword_diamond_1"
      3:
        - "sword_diamond_2"
      4:
        - "sword_diamond_3"
      5:
        - "sword_netherite_1"

  bow:
    displayName: "Bow"
    levelItems:
      1:
        - "bow_basic_1"
      2:
        - "bow_power_1"
      3:
        - "bow_power_2"
      4:
        - "bow_flame_1"
      5:
        - "bow_infinity_1"
```

**Edit `data/equipment/helmets.yml`:**

```yaml
groups:
  light:
    displayName: "Light Armor"
    levelItems:
      1:
        - "helmet_leather_1"
      2:
        - "helmet_leather_2"
      3:
        - "helmet_gold_1"
      4:
        - "helmet_gold_2"
      5:
        - "helmet_gold_3"

  chain:
    displayName: "Chain Armor"
    levelItems:
      1:
        - "helmet_chain_1"
      2:
        - "helmet_chain_2"
      3:
        - "helmet_iron_1"
      4:
        - "helmet_iron_2"
      5:
        - "helmet_diamond_1"
```

**Note:** Each `templateId` must have a corresponding item file in `data/items/` (created via `/vrs item capture`).

---

### Step 5: Configure Teleport Commands

Configure how players are teleported between areas.

**Edit `config.yml` teleport section:**

```yaml
teleport:
  # Command to run before entering combat (e.g., clear inventory, heal)
  prepCommand: "tp ${player} world 0 64 0"

  # Command to teleport player into combat world
  enterCommand: "tp ${player} ${world} ${x} ${y} ${z} ${yaw} ${pitch}"

  # Command to respawn player to teammate
  respawnCommand: "tp ${player} ${world} ${x} ${y} ${z}"
```

**For Multiverse:**

```yaml
teleport:
  prepCommand: "mv tp ${player} lobby"
  enterCommand: "mv tp ${player} ${world}:${x},${y},${z}"
  respawnCommand: "mv tp ${player} ${world}:${x},${y},${z}"
```

**Available Placeholders:**

| Placeholder | Description |
|-------------|-------------|
| `${player}` | Player name |
| `${world}` | Target world name |
| `${x}`, `${y}`, `${z}` | Coordinates |
| `${yaw}`, `${pitch}` | Rotation (enter command only) |

---

### Step 6: (Optional) Customize Merchants

Merchants spawn periodically during runs and offer trades for coins.

Default templates are already configured in `data/merchants.yml`:

```yaml
templates:
  basic_supplies:
    displayName: "§eWandering Trader"
    trades:
      - resultItem: "GOLDEN_APPLE"
        resultAmount: 1
        costItem: "coin"
        costAmount: 15
        maxUses: 3
      - resultItem: "ARROW"
        resultAmount: 16
        costItem: "coin"
        costAmount: 5
        maxUses: 10

  rare_goods:
    displayName: "§dRare Merchant"
    trades:
      - resultItem: "ENCHANTED_GOLDEN_APPLE"
        resultAmount: 1
        costItem: "coin"
        costAmount: 50
        maxUses: 1
```

You can add custom item templates as `resultItem` by using their template IDs.

---

## Minimal Viable Setup Checklist

At minimum, you need:

- [ ] **1 combat world** configured in `data/worlds.yml`
- [ ] **1+ enemy archetype** configured in `data/archetypes.yml`
- [ ] **1+ starter weapon** in `data/starters.yml` with matching item template
- [ ] **1+ starter helmet** in `data/starters.yml` with matching item template
- [ ] **Equipment groups** in `data/equipment/weapons.yml` and `helmets.yml`
- [ ] **Teleport commands** configured in `config.yml`

---

## Starting Your First Game

### As an Admin

1. Start the server with the plugin installed
2. Verify configuration loaded: `/vrs reload`
3. Check worlds are registered: `/vrs world list`
4. Enable game entry: `/vrs join enable`

### As a Player

1. **Select equipment**: `/vrs starter`
   - Choose your starting weapon from the GUI
   - Choose your starting helmet from the GUI

2. **Create or join a team**:
   - Solo: `/vrs team create MyTeam`
   - Join existing: `/vrs team join TeamName`

3. **Ready up**: `/vrs ready`
   - All team members must be ready
   - 5-second countdown begins

4. **Fight!**
   - You're teleported to the combat world
   - Kill enemies to gain XP and coins
   - When XP bar fills, choose weapon or helmet upgrade
   - Survive as long as possible!

5. **After death**:
   - 60-second cooldown applies
   - Return to step 1 for another run

---

## Troubleshooting

| Issue | Solution |
|-------|----------|
| "No combat worlds available" | Check `data/worlds.yml` has at least one enabled world |
| "Cannot ready" | Ensure you have selected starter equipment and are in a team |
| Enemies not spawning | Verify `data/archetypes.yml` has entries and spawner is not paused |
| Items not giving | Check item templates exist in `data/items/` directory |

For more details, see:
- [Configuration Reference](Configuration-Reference.md)
- [Commands Reference](Commands-Reference.md)
- [Design Overview](Design-Overview.md)
