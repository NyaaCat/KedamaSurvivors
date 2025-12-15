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
| **Vault** | External economy integration (optional - plugin has built-in economy) |

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
/vrs admin world create combat_arena "Forest Arena"
/vrs admin world set bounds combat_arena -500 500 -500 500
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

#### Step 1b: Configure Spawn Points

Each combat world needs **at least one spawn point**. Players spawn at random locations from this list.

**Option A: Use admin commands (Recommended):**

```
# Stand at the spawn location and run:
/vrs admin world addspawn combat_arena

# Or specify coordinates with optional rotation:
/vrs admin world addspawn combat_arena 100 64 200 0 0

# Add multiple spawn points for variety:
/vrs admin world addspawn combat_arena -100 64 -200 180 0

# List all spawn points:
/vrs admin world listspawns combat_arena

# Remove a spawn point by index (1-based):
/vrs admin world removespawn combat_arena 2
```

**Option B: Edit `data/worlds.yml` directly:**

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

**Fallback Spawn (optional):**

If random spawn sampling fails, configure a guaranteed safe spawn:

```
/vrs admin world setfallback combat_arena 0 64 0
/vrs admin world clearfallback combat_arena  # Remove fallback
```

---

### Step 2: Set Up Enemy Archetypes

Archetypes define the enemy types that spawn during combat.

**Edit `data/archetypes.yml`:**

```yaml
archetypes:
  zombie:
    enemyType: "minecraft:zombie"
    weight: 3.0
    minSpawnLevel: 1  # Available from start
    spawnCommands:
      - "summon zombie {sx} {sy} {sz} {Tags:[\"vrs_mob\",\"vrs_lvl_{enemyLevel}\",\"vrs_arch_{archetypeId}\"]}"
    rewards:
      xpAmount: 10        # Fixed XP amount
      xpChance: 1.0       # 100% chance to award XP
      coinAmount: 1       # Fixed coin amount
      coinChance: 1.0     # 100% chance to award coins
      permaScoreAmount: 1
      permaScoreChance: 0.01  # 1% chance for perma-score

  skeleton:
    enemyType: "minecraft:skeleton"
    weight: 2.0
    minSpawnLevel: 5  # Only spawns at enemy level 5+
    spawnCommands:
      - "summon skeleton {sx} {sy} {sz} {Tags:[\"vrs_mob\",\"vrs_lvl_{enemyLevel}\",\"vrs_arch_{archetypeId}\"]}"
    rewards:
      xpAmount: 15
      xpChance: 1.0
      coinAmount: 2
      coinChance: 0.8     # 80% chance for coins
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

**Available Placeholders:**

| Placeholder | Description |
|-------------|-------------|
| `{sx}`, `{sy}`, `{sz}` | Spawn coordinates |
| `{enemyLevel}` | Calculated enemy level |
| `{player}` | Target player name |
| `{runWorld}` | Combat world name |
| `{enemyType}` | Entity type from archetype config |
| `{archetypeId}` | Archetype ID (use in Tags for reward lookup) |

**Level Gating:** The `minSpawnLevel` property controls when an archetype becomes available. Only archetypes where `minSpawnLevel <= currentEnemyLevel` are considered for spawning.

**Important:** Enemies must have the `vrs_mob` tag to be tracked by the plugin. Include `vrs_arch_{archetypeId}` tag for proper reward lookup.

---

### Step 3: Capture and Configure Starter Equipment

Players select their starting weapon and helmet before entering combat.

#### 3a: Capture Item Templates

First, create equipment groups, then add items to them by holding the item in your hand:

```
# Create equipment groups first
/vrs admin equipment group create weapon sword "Sword Path"
/vrs admin equipment group create helmet light "Light Armor"

# Hold the item you want to add, then run:
/vrs admin equipment item add weapon <groupId> <level>
/vrs admin equipment item add helmet <groupId> <level>
```

**Examples:**

```
# Hold an iron sword, then run:
/vrs admin equipment item add weapon sword 1

# Hold a leather helmet, then run:
/vrs admin equipment item add helmet light 1
```

This captures the item's NBT data and auto-generates a template ID.

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

### Step 6: (Optional) Configure Economy

The plugin supports three economy modes for handling coins:

**Edit `config.yml` economy section:**

```yaml
economy:
  # Mode: VAULT (external economy), INTERNAL (plugin-managed per-player), ITEM (physical items)
  mode: INTERNAL

  coin:
    material: EMERALD
    customModelData: 0
    displayName: "§e金币"
    nbtTag: "vrs_coin"           # NBT tag to identify VRS coins (for ITEM mode)
```

**Economy Modes:**

| Mode | Description |
|------|-------------|
| `VAULT` | Uses external Vault-compatible economy plugin. Falls back to INTERNAL if Vault not installed. |
| `INTERNAL` | Plugin stores coin balance per-player internally (default). |
| `ITEM` | Physical coin items in player inventory. Coins have NBT tag for identification. |

---

### Step 7: (Optional) Customize Merchants

Merchants appear as animated armor stands that float and spin. They spawn periodically during runs and offer trades for coins.

#### 7a: Create Merchant Item Pools

Merchant stock is managed through item pools. Each pool contains weighted items with prices.

**Using Admin Commands (Recommended):**

```bash
# 1. Create a merchant pool
/vrs admin merchant pool create common_items

# 2. Hold the item you want to sell in your hand
# 3. Add it to the pool with price and weight
/vrs admin merchant pool additem common_items 25 1.0
# Result: Adds held item for 25 coins with weight 1.0

# 4. Add more items by holding different items
/vrs admin merchant pool additem common_items 50 0.5

# 5. List pool contents
/vrs admin merchant pool list common_items
```

**Manual Configuration (Alternative):**

Edit `data/merchant_pools.yml`:

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

#### 7b: Configure Merchant Behavior

Merchants use invisible armor stands with floating/spinning animation. Configure their behavior in `config.yml`:

```yaml
merchants:
  enabled: true

  wandering:
    spawnIntervalSeconds: 120    # How often to attempt spawning
    spawnChance: 0.5             # Probability of spawn per interval (0-1)
    stayTime:
      minSeconds: 60             # Minimum stay duration
      maxSeconds: 120            # Maximum stay duration
    distance:
      min: 20.0                  # Min distance from players
      max: 50.0                  # Max distance from players

  stock:
    limited: true                # Items disappear when purchased
    minItems: 3                  # Min items in shop
    maxItems: 6                  # Max items in shop

  display:
    rotationSpeed: 3.0           # Degrees per tick (spinning)
    bobHeight: 0.15              # Floating amplitude (blocks)
    bobSpeed: 0.01               # Floating speed
```

#### 7c: Create Merchant Templates (Legacy)

You can also create named merchant templates with specific trades:

```bash
# Create a merchant template
/vrs admin merchant template create potions "§dPotion Merchant"

# Hold an item and add as trade (25 coins, max 5 uses)
/vrs admin merchant trade add potions 25 5

# List trades
/vrs admin merchant trade list potions
```

---

## Minimal Viable Setup Checklist

At minimum, you need:

- [ ] **1 combat world** configured in `data/worlds.yml` with **at least 1 spawn point**
- [ ] **1+ enemy archetype** configured in `data/archetypes.yml`
- [ ] **1+ starter weapon** in `data/starters.yml` with matching item template
- [ ] **1+ starter helmet** in `data/starters.yml` with matching item template
- [ ] **Equipment groups** in `data/equipment/weapons.yml` and `helmets.yml`
- [ ] **Teleport commands** configured in `config.yml` (optional if using default API teleport)
- [ ] **Economy mode** configured in `config.yml` (defaults to INTERNAL)
- [ ] **Merchant pools** configured in `data/merchant_pools.yml` (optional, for merchant trades)

---

## Starting Your First Game

### As an Admin

1. Start the server with the plugin installed
2. Verify configuration loaded: `/vrs reload`
3. Check worlds are registered: `/vrs admin world list`
4. Enable game entry: `/vrs admin join enable`

### As a Player

1. **Select equipment**: `/vrs starter`
   - Choose your starting weapon from the GUI
   - Choose your starting helmet from the GUI

2. **Create or join a team**:
   - Solo: `/vrs team create MyTeam`
   - Join existing: `/vrs team accept TeamName` (after receiving invite)

3. **Ready up**: `/vrs ready`
   - All team members must be ready
   - 5-second countdown begins

4. **Fight!**
   - You're teleported to the combat world
   - Kill enemies to gain XP and coins
   - When XP bar fills, choose weapon or helmet upgrade
   - Survive as long as possible!

5. **After death**:
   - All deaths return player to prep area (regardless of teammate status)
   - 60-second cooldown applies
   - Player must re-select equipment and ready up to rejoin
   - If teammates are still alive, player can rejoin the same run after cooldown

---

## Troubleshooting

| Issue | Solution |
|-------|----------|
| "No combat worlds available" | Check `data/worlds.yml` has at least one enabled world with spawn points |
| "No spawn points configured" | Add spawn points via `/vrs admin world addspawn <name>` |
| "Cannot ready" | Ensure you have selected starter equipment and are in a team |
| Enemies not spawning | Verify `data/archetypes.yml` has entries and spawner is not paused |
| Items not giving | Check item templates exist in `data/items/` directory |
| Players spawning in wrong location | Configure fallback spawn with `/vrs admin world setfallback` |

For more details, see:
- [Configuration Reference](Configuration-Reference.md)
- [Commands Reference](Commands-Reference.md)
- [Design Overview](Design-Overview.md)
