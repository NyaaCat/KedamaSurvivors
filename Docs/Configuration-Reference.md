# KedamaSurvivors - Configuration Reference

Complete reference for all configuration options in `config.yml`.

---

## Table of Contents

1. [Plugin Settings](#1-plugin-settings)
2. [Join Switch](#2-join-switch)
3. [Ready & Countdown](#3-ready--countdown)
4. [Cooldown](#4-cooldown)
5. [Disconnect](#5-disconnect)
6. [Respawn & PVP](#6-respawn--pvp)
7. [Combat Worlds](#7-combat-worlds)
8. [Teleport](#8-teleport)
9. [Starter Selection](#9-starter-selection)
10. [Equipment Pools](#10-equipment-pools)
11. [Inventory Rules](#11-inventory-rules)
12. [Progression](#12-progression)
13. [Spawning](#13-spawning)
14. [Rewards](#14-rewards)
15. [Merchants](#15-merchants)
16. [Economy](#16-economy)
17. [Scoreboard](#17-scoreboard)
18. [Persistence](#18-persistence)
19. [Templates](#19-templates)
20. [Debug](#20-debug)

---

## 1. Plugin Settings

```yaml
plugin:
  # Language file to use (without .yml extension)
  # File should be in plugins/KedamaSurvivors/lang/
  language: zh_CN

  # Prefix for all plugin messages
  # Supports color codes with § or &
  prefix: "§8[§6武§e生§8] §7"

  # Enable verbose logging for debugging
  verbose: false
```

---

## 2. Join Switch

Global switch to enable/disable new game entries.

```yaml
joinSwitch:
  # Whether players can currently join games
  # Can be toggled with /vrs join enable|disable
  enabled: true

  # Grace period before ejecting players when switch is disabled (seconds)
  graceEjectSeconds: 60

  # Message interval during grace period (seconds)
  graceWarningInterval: 15
```

---

## 3. Ready & Countdown

Settings for the ready check and countdown system.

```yaml
ready:
  # Countdown duration before teleporting to combat (seconds)
  countdownSeconds: 5

  # Show countdown in action bar
  showActionBar: true

  # Show countdown as title
  showTitle: true

  # Play sound during countdown
  countdownSound: BLOCK_NOTE_BLOCK_PLING

  # Sound pitch (0.5 - 2.0)
  countdownSoundPitch: 1.0

  # Final countdown sound (on teleport)
  teleportSound: ENTITY_ENDERMAN_TELEPORT
```

---

## 4. Cooldown

Death cooldown settings.

```yaml
cooldown:
  # Cooldown duration after death before rejoining (seconds)
  deathCooldownSeconds: 60

  # Cooldown duration for voluntary quit (seconds)
  # Set to 0 to disable quit cooldown
  quitCooldownSeconds: 30

  # Show cooldown in action bar
  showCooldownBar: true

  # Update interval for cooldown display (ticks)
  displayUpdateTicks: 20
```

---

## 5. Disconnect

Disconnect handling during active runs.

```yaml
disconnect:
  # Grace period for reconnecting players (seconds)
  # Players who return within this time rejoin their run
  # After grace expires, treated as death
  graceSeconds: 300  # 5 minutes

  # How often to check for expired grace periods (ticks)
  checkIntervalTicks: 200  # 10 seconds

  # Notify team when a player disconnects
  notifyTeam: true

  # Notify team when disconnected player's grace expires
  notifyGraceExpired: true
```

---

## 6. Respawn & PVP

Respawn mechanics and player damage settings.

```yaml
respawn:
  # Invulnerability duration after respawn (seconds)
  invulnerabilitySeconds: 3

  # Whether invulnerable players can deal damage
  canDealDamageDuringInvul: false

  # Visual effect during invulnerability
  invulEffect: GLOWING

  # PVP damage control
  pvp: false  # Whether players can damage each other (false = PVP disabled)
```

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `invulnerabilitySeconds` | Integer | `3` | Duration of invulnerability after respawn |
| `canDealDamageDuringInvul` | Boolean | `false` | Allow invulnerable players to deal damage |
| `invulEffect` | String | `GLOWING` | Visual effect during invulnerability period |
| `pvp` | Boolean | `false` | Enable player-vs-player damage. When false, damage between players is set to 0. |

---

## 7. Combat Worlds

Configuration for combat worlds (arenas).

```yaml
worlds:
  # List of combat worlds
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
      # Spawn points (at least one required)
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
      # Fallback spawn (optional) - used when random sampling fails
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
      enabled: false  # Disabled - won't be selected
      weight: 0.5     # Lower weight when enabled
      spawnBounds:
        minX: -200
        maxX: 200
        minZ: -200
        maxZ: 200
      spawnPoints:
        - x: 0
          y: 64
          z: 0

  # Spawn location sampling settings
  spawnSampling:
    # Maximum attempts to find safe spawn
    maxAttempts: 50

    # Blocks to avoid spawning on
    unsafeBlocks:
      - LAVA
      - WATER
      - CACTUS
      - MAGMA_BLOCK
      - FIRE
      - SOUL_FIRE
      - CAMPFIRE
      - SOUL_CAMPFIRE

    # Minimum headroom required (blocks)
    requiredHeadroom: 2
```

**World Configuration Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `name` | String | Yes | Bukkit world name (must exist) |
| `displayName` | String | No | Name shown to players (supports color codes) |
| `enabled` | Boolean | Yes | Whether this world can be selected |
| `weight` | Double | No | Selection probability (higher = more likely, default 1.0) |
| `spawnBounds` | Object | Yes | Rectangle defining valid spawn area |
| `spawnPoints` | List | Yes | List of spawn points (at least one required) |
| `fallbackX/Y/Z/Yaw/Pitch` | Numbers | No | Fallback spawn when random sampling fails |

**Spawn Point Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `x`, `y`, `z` | Double | Yes | Spawn coordinates |
| `yaw` | Float | No | Player facing direction (0-360, default 0) |
| `pitch` | Float | No | Player head pitch (-90 to 90, default 0) |

---

## 8. Teleport

Teleport command templates.

```yaml
teleport:
  # Command to teleport player to prep area (lobby)
  # Variables: ${player}
  prepCommand: "tp ${player} world 0 64 0"

  # Command to teleport player to combat world
  # Variables: ${player}, ${world}, ${x}, ${y}, ${z}, ${yaw}, ${pitch}
  enterCommand: "tp ${player} ${world} ${x} ${y} ${z} ${yaw} ${pitch}"

  # Command to teleport player to teammate (respawn)
  # Variables: ${player}, ${target}, ${world}, ${x}, ${y}, ${z}
  respawnCommand: "tp ${player} ${world} ${x} ${y} ${z}"
```

---

## 9. Starter Selection

Starter weapon and helmet configuration.

```yaml
starterSelection:
  # Require weapon selection before helmet
  requireWeaponFirst: true

  # Automatically open helmet GUI after weapon selection
  autoOpenHelmetGui: true

  # Starter weapon options
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

  # Starter helmet options
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

---

## 10. Equipment Pools

Equipment group definitions for progression.

```yaml
equipmentPools:
  # Weapon groups
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

  # Helmet groups
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

---

## 11. Inventory Rules

Item handling rules during gameplay.

```yaml
inventoryRules:
  # Prevent item drops in these modes
  preventDropModes:
    - READY
    - COUNTDOWN
    - IN_RUN

  # Prevent item pickup in these modes
  preventPickupModes:
    - READY
    - COUNTDOWN
    - IN_RUN

  # Lock equipment slots (prevent moving weapon/helmet)
  lockEquipmentSlots: true

  # Slot index for weapon (main hand hotbar slot)
  weaponSlotIndex: 0

  # Direct-to-inventory reward handling
  rewards:
    # How to handle inventory overflow
    # PENDING_QUEUE: Store for later delivery
    # CONVERT_TO_COIN: Convert to coin value
    # DISCARD: Drop on ground (not recommended)
    overflowMode: PENDING_QUEUE

    # Interval to attempt delivering pending rewards (ticks)
    pendingFlushInterval: 100

    # Maximum pending items per player
    maxPendingItems: 100
```

---

## 12. Progression

XP and leveling configuration.

```yaml
progression:
  # Base XP required for first level up
  baseXpRequired: 100

  # XP increase per level (additive)
  xpPerLevelIncrease: 50

  # XP increase multiplier per level
  xpMultiplierPerLevel: 1.1

  # Formula: xpRequired = baseXpRequired + (level * xpPerLevelIncrease) * xpMultiplierPerLevel^level

  # Weight of weapon level in player level calculation
  weaponLevelWeight: 1

  # Weight of helmet level in player level calculation
  helmetLevelWeight: 1

  # Player level = weaponLevel * weaponWeight + helmetLevel * helmetWeight

  # Overflow XP handling (when at max level for both slots)
  overflow:
    # Enable overflow XP -> perma-score conversion
    enabled: true

    # XP required per perma-score point
    xpPerPermaScore: 1000

    # Notify player when perma-score is gained
    notifyPlayer: true

  # Max level upgrade behavior (when no next level exists)
  maxLevelBehavior:
    # What happens when upgrading at max level
    # GRANT_PERMA_SCORE: Grant perma-score
    # REROLL_SAME_LEVEL: Reroll with different item at same level
    # NOOP: Do nothing
    mode: GRANT_PERMA_SCORE

    # Perma-score to grant when at max level
    permaScoreReward: 10
```

---

## 13. Spawning

Enemy spawning configuration.

```yaml
spawning:
  # Main spawn loop settings
  loop:
    # Interval between spawn ticks (ticks)
    tickInterval: 20  # 1 second

    # Enable spawning
    enabled: true

    # Block natural mob spawns in combat worlds
    blockNaturalSpawns: true

  # Spawn caps and limits
  limits:
    # Target number of VRS mobs per player
    targetMobsPerPlayer: 10

    # Max spawns per player per tick
    maxSpawnsPerPlayerPerTick: 3

    # Max total spawns per tick (global)
    maxSpawnsPerTick: 20

    # Max command dispatches per tick
    maxCommandsPerTick: 50

    # Radius to count existing mobs
    mobCountRadius: 30.0

  # Spawn position sampling
  positioning:
    # Minimum distance from player to spawn
    minSpawnDistance: 8.0

    # Maximum distance from player to spawn
    maxSpawnDistance: 25.0

    # Max attempts to find valid spawn position
    maxSampleAttempts: 10

  # Enemy level calculation
  levelCalculation:
    # Radius to sample player levels
    levelSamplingRadius: 50.0

    # Multiplier for average player level
    avgLevelMultiplier: 1.0

    # Bonus per player in radius
    playerCountMultiplier: 0.2

    # Base level offset
    levelOffset: 0

    # Minimum enemy level
    minLevel: 1

    # Maximum enemy level
    maxLevel: 100

    # Time-based scaling
    timeScaling:
      # Enable time-based level increase
      enabled: true

      # Seconds per time step
      timeStepSeconds: 60

      # Level increase per time step
      levelPerStep: 1

  # Enemy archetypes
  archetypes:
    zombie:
      enemyType: "minecraft:zombie"
      weight: 3.0
      minSpawnLevel: 1  # Available from start
      spawnCommands:
        - "summon zombie {sx} {sy} {sz} {Tags:[\"vrs_mob\",\"vrs_lvl_{enemyLevel}\",\"vrs_arch_{archetypeId}\"]}"
      rewards:
        xpAmount: 10        # Fixed XP reward
        xpChance: 1.0       # Probability (0-1) to award XP
        coinAmount: 1       # Fixed coin reward
        coinChance: 1.0     # Probability (0-1) to award coins
        permaScoreAmount: 1 # Fixed perma-score reward
        permaScoreChance: 0.01  # Probability (0-1) to award perma-score

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
        coinChance: 0.8
        permaScoreAmount: 1
        permaScoreChance: 0.02

    spider:
      enemyType: "minecraft:spider"
      weight: 1.5
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

    creeper:
      enemyType: "minecraft:creeper"
      weight: 0.5
      minSpawnLevel: 10  # Boss-tier, appears later
      spawnCommands:
        - "summon creeper {sx} {sy} {sz} {Tags:[\"vrs_mob\",\"vrs_lvl_{enemyLevel}\",\"vrs_arch_{archetypeId}\"],ExplosionRadius:0}"
      rewards:
        xpAmount: 25
        xpChance: 1.0
        coinAmount: 5
        coinChance: 1.0
        permaScoreAmount: 2
        permaScoreChance: 0.05

  # Mob identification
  mobIdentification:
    # Tag that identifies VRS mobs
    mobTag: "vrs_mob"

    # Tag pattern for enemy level
    levelTagPattern: "vrs_lvl_(\\d+)"

    # Default level if parsing fails
    defaultLevel: 1
```

**Level Gating:** The `minSpawnLevel` property controls when an archetype becomes available. Only archetypes where `minSpawnLevel <= currentEnemyLevel` are considered for spawning. The weighted pool is dynamically recalculated from eligible archetypes.

**Reward System:** Each reward type (XP, coins, perma-score) is rolled independently:
- Roll `random(0,1) < chance` → award the fixed amount
- No level scaling - rewards are fixed values with probability

**Available Spawn Command Placeholders:**

| Placeholder | Description |
|-------------|-------------|
| `{sx}`, `{sy}`, `{sz}` | Spawn coordinates |
| `{enemyLevel}` | Calculated enemy level |
| `{runWorld}` | Combat world name |
| `{enemyType}` | Entity type from archetype config |
| `{archetypeId}` | Archetype ID (use in Tags for reward lookup) |

---

## 14. Rewards

XP and coin reward settings.

```yaml
rewards:
  # XP sharing with nearby players
  xpShare:
    # Enable XP sharing
    enabled: true

    # Radius to share XP
    radius: 20.0

    # Percentage of killer's XP to share (0.0 - 1.0)
    sharePercent: 0.25

  # Coin item settings
  coin:
    # Material used for coins
    material: EMERALD

    # Custom model data (for resource pack integration)
    customModelData: 0

    # Display name
    displayName: "§e金币"
```

---

## 15. Merchants

Wandering merchant configuration. Merchants appear as animated armor stands with floating/spinning effects.

```yaml
merchants:
  # Enable merchant system
  enabled: true

  # Wandering merchant spawning behavior
  wandering:
    spawnIntervalSeconds: 120    # How often to attempt spawning
    spawnChance: 0.5             # Probability of spawn per interval (0-1)
    stayTime:
      minSeconds: 60             # Minimum stay duration
      maxSeconds: 120            # Maximum stay duration
    distance:
      min: 20.0                  # Min distance from players
      max: 50.0                  # Max distance from players
    particles:
      spawn: true                # Show particles when merchant appears
      despawn: true              # Show particles when merchant leaves

  # Merchant stock settings
  stock:
    limited: true                # Items disappear when purchased (true) or respawn (false)
    minItems: 3                  # Min items in shop (for multi-type)
    maxItems: 6                  # Max items in shop (for multi-type)

  # Display settings (armor stand animation)
  display:
    rotationSpeed: 3.0           # Degrees per tick (spinning)
    bobHeight: 0.15              # Floating bob amplitude (blocks)
    bobSpeed: 0.01               # Floating bob speed
    headItemCycleIntervalTicks: 200  # How often head item changes (for multi-type)
```

**Merchant Settings:**

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `enabled` | Boolean | `true` | Enable merchant system |
| `wandering.spawnIntervalSeconds` | Integer | `120` | Seconds between spawn attempts |
| `wandering.spawnChance` | Double | `0.5` | Probability of spawn per interval |
| `wandering.stayTime.minSeconds` | Integer | `60` | Minimum merchant stay duration |
| `wandering.stayTime.maxSeconds` | Integer | `120` | Maximum merchant stay duration |
| `wandering.distance.min` | Double | `20.0` | Minimum distance from players to spawn |
| `wandering.distance.max` | Double | `50.0` | Maximum distance from players to spawn |
| `wandering.particles.spawn` | Boolean | `true` | Show particles on merchant spawn |
| `wandering.particles.despawn` | Boolean | `true` | Show particles on merchant despawn |
| `stock.limited` | Boolean | `true` | Whether items disappear when purchased |
| `stock.minItems` | Integer | `3` | Minimum items in shop |
| `stock.maxItems` | Integer | `6` | Maximum items in shop |
| `display.rotationSpeed` | Float | `3.0` | Armor stand rotation speed (degrees/tick) |
| `display.bobHeight` | Double | `0.15` | Floating animation amplitude |
| `display.bobSpeed` | Double | `0.01` | Floating animation speed |
| `display.headItemCycleIntervalTicks` | Integer | `200` | Head item cycle interval |

**Note:** Merchant item pools are stored in `data/merchant_pools.yml`. See [Quick Start Guide](Quick-Start.md) for pool configuration.

---

## 16. Economy

Virtual economy settings supporting multiple modes.

```yaml
economy:
  # Mode: VAULT (external economy), INTERNAL (plugin-managed per-player), ITEM (physical items)
  mode: INTERNAL

  # Coin item settings (used for ITEM mode and visual display)
  coin:
    material: EMERALD
    customModelData: 0
    displayName: "§e金币"
    nbtTag: "vrs_coin"           # NBT tag to identify VRS coins

  # Perma-score settings
  permaScore:
    # Scoreboard objective name (for cross-server compatibility)
    objectiveName: "vrs_perma"

    # Display name for scoreboard
    displayName: "永久积分"
```

**Economy Modes:**

| Mode | Description |
|------|-------------|
| `VAULT` | Uses external Vault-compatible economy plugin (e.g., EssentialsX Economy). Falls back to INTERNAL if Vault is not installed. |
| `INTERNAL` | Plugin stores coin balance per-player internally. Balance persists across sessions in `players.json`. Default mode. |
| `ITEM` | Physical coin items in player inventory. Coins are identified by the NBT tag specified in `coin.nbtTag`. |

**Economy Settings:**

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `mode` | String | `INTERNAL` | Economy mode: VAULT, INTERNAL, or ITEM |
| `coin.material` | Material | `EMERALD` | Material for coin items |
| `coin.customModelData` | Integer | `0` | Custom model data for resource packs |
| `coin.displayName` | String | `§e金币` | Display name for coin items |
| `coin.nbtTag` | String | `vrs_coin` | NBT tag to identify VRS coin items |
| `permaScore.objectiveName` | String | `vrs_perma` | Scoreboard objective name |
| `permaScore.displayName` | String | `永久积分` | Display name for perma-score |

**Note:** When using VAULT mode and Vault is not installed, the plugin automatically falls back to INTERNAL mode and logs a warning.

---

## 17. Scoreboard

Sidebar display configuration.

```yaml
scoreboard:
  # Enable sidebar display
  enabled: true

  # Sidebar title
  title: "§6§l武 生"

  # Update interval (ticks)
  updateInterval: 10

  # Lines to display (in order, top to bottom)
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

## 18. Persistence

Data saving configuration.

```yaml
persistence:
  # Auto-save interval (seconds)
  saveIntervalSeconds: 300  # 5 minutes

  # Save on player quit
  saveOnQuit: true

  # Save on run end
  saveOnRunEnd: true

  # Data paths (relative to plugin folder)
  paths:
    items: "data/items"
    runtime: "data/runtime"

  # Backup settings
  backup:
    # Enable automatic backups
    enabled: true

    # Backup interval (hours)
    intervalHours: 6

    # Maximum backups to keep
    maxBackups: 10
```

---

## 19. Templates

Command template configuration.

```yaml
templates:
  # Placeholder escaping
  escaping:
    # Enable placeholder escaping to prevent injection
    enabled: true

    # Characters to escape
    escapeChars: ";&|`$\\"

  # Missing placeholder behavior
  missingPlaceholder:
    # What to do when a placeholder is missing
    # ERROR: Log error and skip command
    # EMPTY: Replace with empty string
    # LITERAL: Keep placeholder text as-is
    mode: ERROR

  # Custom command templates (for reuse)
  custom:
    # Example: teleport with effects
    fancy_teleport:
      - "particle minecraft:portal ${x} ${y} ${z} 1 1 1 0.1 50"
      - "playsound minecraft:entity.enderman.teleport player ${player}"
      - "tp ${player} ${x} ${y} ${z}"
```

---

## 20. Debug

Debug and development settings.

```yaml
debug:
  # Enable debug mode
  enabled: false

  # Log level (INFO, DEBUG, TRACE)
  logLevel: INFO

  # Log to separate file
  separateLogFile: true

  # Performance monitoring
  performance:
    # Enable performance tracking
    enabled: true

    # Log slow operations (milliseconds)
    slowOperationThreshold: 50

    # Include in /vrs debug perf output
    showInDebugCommand: true
```

---

## Complete Example Configuration

```yaml
# KedamaSurvivors Configuration
# Version: 1.0.0

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

# ... (rest of configuration)
```

---

## Hot Reload

All configuration can be reloaded without server restart using:

```
/vrs reload
```

**Safe to reload during gameplay:**
- Language files
- Spawn rates and scaling
- Merchant templates
- World enable/disable status
- Progression curves
- Reward values

**Requires no active runs:**
- Equipment pool structure changes
- Starter option changes

**Never requires restart:**
- Any configuration change
