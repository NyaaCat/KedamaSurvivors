# KedamaSurvivors Quick Start

This guide sets up the current segmented gameplay model:

- stage groups ("World Fragments")
- battery extraction objective
- progression rewards and final bonus reset

If you are migrating from old endless mode configs, start from this guide.

## 1. Requirements

- Paper `1.21.8+`
- Java `21`
- Plugin jar built from this repository

## 2. Install

1. Put the plugin jar into `plugins/`.
2. Start server once.
3. Stop server.

This creates default files:

- `plugins/KedamaSurvivors/config.yml`
- `plugins/KedamaSurvivors/data/worlds.yml`
- `plugins/KedamaSurvivors/data/starters.yml`
- `plugins/KedamaSurvivors/data/archetypes.yml`
- `plugins/KedamaSurvivors/data/equipment/*.yml`

## 3. Prepare Combat Worlds

Register each world and add spawn points:

```text
/vrs admin world create <world>
/vrs admin world addspawn <world>
/vrs admin world set bounds <world> <minX> <maxX> <minZ> <maxZ>
```

Repeat for every world you want in rotation.

## 4. Configure Stage Groups (World Fragments)

Edit `config.yml` -> `stageProgression.groups`.

Recommended baseline:

- at least 5 groups
- names like `World Fragment - Extension 1`
- each group uses unique worlds (one world cannot appear in multiple groups)

Example:

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
    - id: "fragment_2"
      displayName: "World Fragment - Extension 2"
      worlds: ["arena_c"]
      startEnemyLevel: 5
      requiredBatteries: 3
      clearRewardCoins: 300
      clearRewardPermaScore: 30
  finalBonus:
    coins: 1000
    permaScore: 100
```

Important:

- Duplicate world assignment across groups is rejected at load/runtime update.
- If a group `worlds` is empty, it can draw from global enabled worlds.

## 5. Configure Battery Objective

In `config.yml` -> `battery`:

- spawn interval/chance/distance
- charge radius and speed
- display model and name
- surge interval/count

Defaults already implement:

- base `1%/s`
- extra `+0.1%/s` per additional player in radius
- charging pauses when VRS mobs are inside radius

## 6. Create Starter and Upgrade Data

Use admin commands to configure content:

- equipment groups/items
- starter weapon/helmet choices
- archetypes and spawn commands

Typical order:

```text
/vrs admin equipment group create weapon <group>
/vrs admin equipment group create helmet <group>
/vrs admin equipment item add weapon <group> <level>
/vrs admin equipment item add helmet <group> <level>
/vrs admin starter create weapon <optionId> <templateId> <group> <level>
/vrs admin starter create helmet <optionId> <templateId> <group> <level>
/vrs admin spawner archetype create <id> <entityType> [weight]
/vrs admin spawner archetype addcommand <id> <command...>
```

## 7. Smoke Test the Flow

1. Create team and select starters:
   - `/vrs team create`
   - `/vrs starter`
2. Start run:
   - `/vrs ready`
3. Verify in-run behavior:
   - mobs spawn near players
   - battery appears after spawn checks
   - battery blocks charging when mobs enter radius
4. Finish battery objective and return to prep.
5. Re-enter and confirm next stage group world is used.
6. Clear final group and confirm:
   - final bonus granted
   - team auto-disbanded
   - progression reset for next campaign

## 8. Runtime Hot Update Examples

### Update stage world list

```text
/vrs admin config set stage.fragment_2.worlds arena_x,arena_y
```

### Update battery behavior

```text
/vrs admin config set batterySpawnChance 0.08
/vrs admin config set batteryChargeRadius 10
```

### Restrict archetype to multiple worlds

```text
/vrs admin spawner archetype set worlds elite arena_x,arena_y arena_z
```

### List multiple config categories in one command

```text
/vrs admin config list stage battery rewards
```

## 9. Progression Reset Rules (Operational)

Progress resets for campaign progression when:

- team wipe
- team disband
- team has no valid remaining members
- final stage clear (campaign complete)

A single player is detached/reset from team progression when they effectively leave:

- `/vrs quit` during run
- disconnect grace timeout

Their persistent currencies and long-term stats remain saved.

## 10. Persistence Checklist

Verify these after restart:

- `players/<uuid>.json` keeps per-player stats and balances
- `teams.json` keeps stage index and lock state for active teams
- stage reward/battery/campaign stats continue accumulating

If needed, run:

```text
/vrs status
/vrs admin debug player <name>
/vrs admin debug run list
```
