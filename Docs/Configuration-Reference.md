# KedamaSurvivors Configuration Reference

This document matches the **current implementation**.

Primary sources:

- `config.yml` (runtime/gameplay settings)
- `data/worlds.yml` (combat worlds)
- `data/starters.yml` (starter options)
- `data/archetypes.yml` (spawn archetypes)
- `data/equipment/weapons.yml`, `data/equipment/helmets.yml` (equipment pools)
- `data/merchants.yml`, `data/merchant_pools.yml` (merchant templates/pools)

## 1. Main Config (`config.yml`)

## 1.1 plugin

```yaml
plugin:
  language: zh_CN
  verbose: false
```

- `language`: i18n file key under `lang/`
- `verbose`: enables additional runtime logs

## 1.2 joinSwitch

```yaml
joinSwitch:
  enabled: true
  graceEjectSeconds: 60
  graceWarningInterval: 15
```

Global new-entry switch and maintenance grace eject timing.

## 1.3 ready

Countdown before run entry.

Key fields:

- `countdownSeconds`
- `showActionBar`
- `showTitle`
- `countdownSound`
- `countdownSoundPitch`
- `teleportSound`

## 1.4 cooldown

- `deathCooldownSeconds`
- `quitCooldownSeconds`
- `showCooldownBar`
- `displayUpdateTicks`

## 1.5 disconnect

- `graceSeconds`
- `checkIntervalTicks`
- `notifyTeam`
- `notifyGraceExpired`

## 1.6 teams

- `maxSize`
- `inviteExpirySeconds`

## 1.7 respawn

- `invulnerabilitySeconds`
- `canDealDamageDuringInvul`
- `invulEffect`
- `pvp`

## 1.8 worlds (sampling only)

`config.yml` keeps global spawn sampling defaults; actual world list is in `data/worlds.yml`.

## 1.9 teleport

- `lobbyWorld`, `lobbyX`, `lobbyY`, `lobbyZ`
- `prepCommand`, `enterCommand`, `respawnCommand`
- `teamSpawnOffsetRange`

## 1.10 starterSelection

- `requireWeaponFirst`
- `autoOpenHelmetGui`

## 1.11 worldSelection

- `enabled`
- `autoOpenAfterHelmet`

Important: if stage progression is active, stage world selection takes priority.

## 1.12 stageProgression (Core Campaign System)

```yaml
stageProgression:
  groups:
    - id: "fragment_1"
      displayName: "World Fragment - Extension 1"
      worlds: ["arena_a", "arena_b"]
      startEnemyLevel: 1
      requiredBatteries: 1
      clearRewardCoins: 100
      clearRewardPermaScore: 10
  finalBonus:
    coins: 300
    permaScore: 30
```

Per-group fields:

- `id`: stable group key used by runtime config commands
- `displayName`: user-facing stage label
- `worlds`: world name list; empty means no group-specific restriction
- `startEnemyLevel`: minimum enemy level baseline for that stage
- `requiredBatteries`: extraction objective count
- `clearRewardCoins` / `clearRewardPermaScore`: reward on clearing this stage

Global final bonus fields:

- `finalBonus.coins`
- `finalBonus.permaScore`

Validation:

- a world can be assigned to only one stage group (case-insensitive)
- duplicate assignment is rejected on load and on runtime update

## 1.13 battery (Extraction Objective)

```yaml
battery:
  enabled: true
  spawn:
    intervalSeconds: 20
    chance: 0.05
    minDistance: 35.0
    maxDistance: 90.0
    verticalRange: 10
  charge:
    radius: 8.0
    basePercentPerSecond: 1.0
    extraPlayerPercentPerSecond: 0.1
    updateTicks: 10
  display:
    material: BEACON
    customModelData: 0
    name: "§bEnergy Battery"
    showRangeParticles: true
  surge:
    enabled: true
    intervalSeconds: 5
    mobCount: 6
```

Behavior mapping:

- battery spawns near run participants but not too close
- charge only progresses when:
  - at least one in-run player inside radius
  - no VRS mobs inside radius
- charge speed:
  - `basePercentPerSecond + (playersInRange - 1) * extraPlayerPercentPerSecond`
- when charged:
  - all online in-run participants must interact
  - completes one battery objective

## 1.14 spawnTracking / overheadDisplay

- `spawnTracking.defaultRadiusBlocks`
- `overheadDisplay.enabled`
- `overheadDisplay.yOffset`
- `overheadDisplay.updateIntervalTicks`

## 1.15 inventoryRules

Runtime behavior is enforced in listeners:

- all non-lobby modes block drop and pickup
- equipment slots are protected in restricted modes
- ender chest access is blocked outside lobby

`inventoryRules` keeps compatibility/config knobs for lock and reward overflow policy.

## 1.16 progression

- XP curve: `baseXpRequired`, `xpPerLevelIncrease`, `xpMultiplierPerLevel`
- weight factors: `weaponLevelWeight`, `helmetLevelWeight`
- overflow conversion: `overflow.*`
- max level handling: `maxLevelBehavior.*`

## 1.17 upgrade

- `timeoutSeconds`
- `reminderIntervalSeconds`
- `bothMaxPermaReward`

## 1.18 spawning

Main groups:

- `loop`: tick interval, enable switch, natural spawn block
- `limits`: mob target and per-tick budgets
- `positioning`: spawn distance/attempt/vertical range/LOS validation
- `levelCalculation`: enemy-level formula and time scaling
- `mobIdentification`: tag/pattern defaults

Enemy archetype definitions are loaded from `data/archetypes.yml`.

## 1.19 rewards

- `xpShare.*`
- `damageContribution.*`
- `multiplier.*` (runtime reward multiplier)
- coin display fields

## 1.20 merchants

Runtime merchant behavior and visuals:

- wandering spawn interval/chance/count
- stay duration and distance
- stock limits
- display animation

Merchant template data is in `data/merchants.yml` and `data/merchant_pools.yml`.

## 1.21 economy

Modes:

- `VAULT` (falls back to INTERNAL if unavailable)
- `INTERNAL`
- `ITEM`

Also defines coin item identity and perma score objective.

## 1.22 scoreboard

- `enabled`
- `title`
- `updateInterval`

## 1.23 persistence

- autosave interval and save hooks
- runtime/items paths
- backup settings (current backup rotation is implemented in `PersistenceService`)

## 1.24 templates

Template escaping and missing-placeholder policy for command expansion.

## 1.25 feedback

- reward display mode (`ACTIONBAR` / `CHAT`)
- reward stack timing
- upgrade reminder mode (`CHAT` / `SCOREBOARD`)
- sound table (`"sound volume pitch"`)

## 1.26 debug

Debug/log configuration switches.

## 2. Data File References

## 2.1 `data/worlds.yml`

Each world entry:

- `name`, `displayName`, `enabled`, `weight`, `cost`
- `spawnBounds.minX/maxX/minZ/maxZ`
- `spawnPoints[]` with `x y z [yaw pitch]`

## 2.2 `data/starters.yml`

Top-level lists:

- `weapons[]`
- `helmets[]`

Each option:

- `optionId`, `displayName`, `templateId`, `group`, `level`
- optional `displayItem` for GUI icon

## 2.3 `data/archetypes.yml`

Per archetype:

- `entityType`, `weight`
- `minSpawnLevel`
- `allowedWorlds` (supports multiple worlds or `any`)
- `spawnCommands[]`
- reward tuple:
  - `xpAmount`, `xpChance`
  - `coinAmount`, `coinChance`
  - `permaScoreAmount`, `permaScoreChance`

## 2.4 Equipment Pools

- `data/equipment/weapons.yml`
- `data/equipment/helmets.yml`

Group -> level -> template id list.

## 2.5 Merchant Data

- `data/merchants.yml`: merchant templates and trades
- `data/merchant_pools.yml`: weighted item pools

## 3. Runtime Hot Update Matrix

## 3.1 Command-driven updates (no restart)

- `/vrs admin world ...` (world data)
- `/vrs admin starter ...` (starter options)
- `/vrs admin equipment ...` (equipment pools)
- `/vrs admin spawner archetype ...` (archetypes, including multi-world restrictions)
- `/vrs admin merchant ...` (merchant templates/pools)
- `/vrs admin config set ...` (core config fields)

## 3.2 Stage/Battery runtime updates

Examples:

```text
/vrs admin config set stage.fragment_3.worlds arena_4,arena_5
/vrs admin config set stage.fragment_3.requiredBatteries 4
/vrs admin config set batterySpawnChance 0.07
```

Battery runtime config updates trigger task rebind for active runs.

## 3.3 Multi-parameter list support

- `admin config list` accepts multiple categories in one call
- `stage.<id>.worlds` accepts multi-arg list input
- `spawner archetype set worlds` accepts multi-world input

## 4. Persistence Schema (Runtime JSON)

`data/runtime/players/<uuid>.json` includes:

- persistent player economy/perma score
- selected starter ids
- cooldown if still active
- extended stats (`runCount`, `failedRunCount`, battery/stage/campaign metrics, etc.)

`data/runtime/teams.json` includes:

- `teamId`, `leaderId`, members
- `stageIndex`
- `progressionLocked`

## 5. Validation and Safety Rules

- duplicate stage-world assignment is rejected
- stage values are clamped by runtime setters (`>= 1` / `>= 0` depending on field)
- battery percentages and chances are clamped
- world selection uses only loaded + enabled worlds

## 6. Recommended Stage Setup

For production campaign pacing, use:

- at least 5 stage groups
- non-overlapping world sets
- gradually increasing `startEnemyLevel` and `requiredBatteries`
- meaningful `clearReward*` and `finalBonus`
