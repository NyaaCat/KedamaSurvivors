# KedamaSurvivors Development Spec (Current Implementation)

This document describes how the codebase currently works. It is not a design wishlist.

## 1. Target Platform

- Java `21`
- Paper API target: `1.21.8`
- Build: Gradle with `paperweight` + `shadowJar` + `reobfJar`
- Optional external integration: Vault economy
- Bundled runtime library: FastBoard (relocated package)

## 2. Runtime Initialization Order

Main class: `src/main/java/cat/nyaa/survivors/KedamaSurvivorsPlugin.java`

Service initialization order is important for dependencies:

1. `ConfigService`
2. `AdminConfigService`
3. `I18nService`
4. `StateService`
5. `EconomyService`
6. `CommandQueue` + `TemplateEngine`
7. `PersistenceService`
8. `StatsService`
9. gameplay/runtime services (`World`, `Starter`, `Merchant`, `Battery`, `Ready`, `Run`, `Reward`, `Upgrade`, `Spawner`, etc.)

Listeners:

- `PlayerListener`
- `InventoryListener`
- `CombatListener`
- `SpawnListener`
- `MerchantListener`
- `BatteryListener`

## 3. Core State Models

## 3.1 Player (`PlayerState`)

Key responsibilities:

- mode state machine (`LOBBY`, `READY`, `COUNTDOWN`, `IN_RUN`, `COOLDOWN`, `GRACE_EJECT`, `DISCONNECTED`)
- starter selections
- run-scoped equipment/XP state
- economy/perma score
- persistent statistics (`PlayerStats`)

Reset behavior:

- `resetRunState()` clears run-only fields and keeps starter selections.
- `resetAll()` clears run state + starters + cooldown/disconnect/invul tracking.

## 3.2 Team (`TeamState`)

Key responsibilities:

- team identity, members, invites, leader
- ready/disconnect tracking
- run binding
- segmented progression fields:
  - `stageIndex`
  - `progressionLocked`

Progression helpers:

- `resetForNewRun()` keeps campaign stage progression.
- `resetProgression()` resets to stage 0 and unlocks progression.

## 3.3 Run (`RunState`)

Run snapshot includes:

- participants/alive sets
- stage snapshot fields (`stageIndex`, `stageGroupId`, start level, required/completed batteries)
- spawn points and round-robin/random access
- aggregate stats (kills/coins/xp/time)

Objective helper:

- `isStageObjectiveComplete()`

## 3.4 Persistent Stats (`PlayerStats`)

Includes legacy combat metrics and segmented campaign metrics:

- run count / failed run count
- total batteries completed
- total stage clears
- highest stage cleared
- campaign completions
- total stage reward coins/perma score

## 4. Config and Data Sources

## 4.1 `config.yml`

`ConfigService` owns runtime gameplay settings, including:

- stage progression (`stageProgression.groups`, `stageProgression.finalBonus`)
- battery objective (`battery.*`)
- spawning/rewards/progression/team/timing/economy settings

## 4.2 `data/*.yml`

`AdminConfigService` owns mutable content data:

- `data/worlds.yml`
- `data/starters.yml`
- `data/archetypes.yml`
- `data/equipment/weapons.yml`
- `data/equipment/helmets.yml`
- `data/merchants.yml`
- `data/merchant_pools.yml`
- `data/items/*.yml`

On startup, admin data is synced back into `ConfigService` runtime caches.

## 5. Segmented Campaign Rules

## 5.1 Stage Group Resolution

Entry point: `RunService.startRunAsync`.

- Team stage is resolved by `team.stageIndex`.
- If index is out of range, it is reset to `0`.
- If stage index is greater than `0`, `progressionLocked` is enabled.

## 5.2 Stage Group Config Fields

Per group:

- `id`
- `displayName`
- `worlds`
- `startEnemyLevel`
- `requiredBatteries`
- `clearRewardCoins`
- `clearRewardPermaScore`

Global:

- `stageProgression.finalBonus.coins`
- `stageProgression.finalBonus.permaScore`

## 5.3 World Uniqueness Invariant

Validation is enforced in two paths:

- load-time (`loadStageProgression` + `validateAndRegisterStageWorlds`)
- runtime update (`setStageGroupWorldNames`)

Invariant: one world can appear in only one stage group (case-insensitive).

## 5.4 Stage Completion Outcomes

Battery objective complete count reaches required amount:

1. stage clear reward is granted to all team members
2. player stats record stage clear + reward totals
3. if next stage exists:
   - increment `team.stageIndex`
   - keep team and lock progression
   - run ends as `STAGE_CLEAR`
4. if no next stage:
   - grant final bonus
   - record campaign completion
   - run ends as `FINAL_CLEAR`

`FINAL_CLEAR` post-processing:

- reset team progression
- disband team
- clear members' starter selections and run state

## 6. Run Lifecycle

## 6.1 Ready -> Countdown -> Start

`ReadyService` validates:

- mode allows ready (`LOBBY`/`COOLDOWN`)
- cooldown expired
- in a team
- starter selections complete
- join switch enabled

All-ready team starts countdown, then `RunService.startRunAsync(team)`.

## 6.2 Run End Paths

`RunService.endRun(run, reason)` handles:

- player stat finalization (`recordRunEnd` or `recordRunFailure`)
- per-player state reset policy
- teleport to lobby/prep location
- merchant/battery runtime cleanup
- persistence save scheduling
- team progression reset for failure reasons

Progression reset is applied for:

- `WIPE`
- `DEATH`
- `DISCONNECT`
- `FORCED`

## 6.3 Effective Player Exit

Cases:

- `/vrs quit` while `IN_RUN`
- disconnect grace timeout (`DisconnectChecker`)

Behavior:

- player run failure is recorded
- player is removed from current team
- starter selections are cleared
- player no longer follows previous team progression

## 7. Battery Objective Engine

Core class: `src/main/java/cat/nyaa/survivors/service/BatteryService.java`

Per run tasks:

- spawn task (`battery.spawn.intervalSeconds`)
- update task (`battery.charge.updateTicks`)

Charge logic:

- requires players in radius
- charging pauses if any VRS mobs are in radius
- speed:
  - base `battery.charge.basePercentPerSecond`
  - plus `(playersInRange - 1) * battery.charge.extraPlayerPercentPerSecond`

Completion:

- charge reaches 100%
- all online in-run participants interact with the battery
- then one battery objective is counted for stage progression

Runtime rebind:

- `BatteryService.reloadRuntimeConfig()` rebinds active run tasks after battery config command updates.

## 8. World Selection and Team Distribution

Core class: `src/main/java/cat/nyaa/survivors/service/WorldService.java`

Selection flow:

1. get candidate worlds (stage world set if present, else global enabled worlds)
2. compute per-world metrics:
   - in-run player count
   - spawn point count
   - configured weight
3. prefer worlds with zero in-run players
4. if all occupied, weighted score:
   - `score = (spawnPointCount / (inRunPlayers + 1)) * weight`

## 9. Spawner Gating and Stage Difficulty Floor

Core class: `src/main/java/cat/nyaa/survivors/service/SpawnerService.java`

Enemy level calculation uses:

- average team level
- nearby player factor
- time scaling
- global min/max clamp
- stage min floor (`run.stageStartEnemyLevel`)

Archetype eligibility requires:

- `minSpawnLevel <= calculatedEnemyLevel`
- `allowedWorlds` match (`any` or explicit world list)

## 10. Command Surface (Current)

Root command:

- `/vrs`

Top-level subcommands:

- player: `starter`, `team`, `ready`, `quit`, `status`, `upgrade`
- admin entry: `admin`
- reload: `reload`

Important admin runtime controls:

- `/vrs admin config set stage.<groupId>.<field> ...`
- `/vrs admin config set battery...`
- `/vrs admin spawner archetype set worlds <id> ...`
- `/vrs admin config list <category...>` (supports multiple categories)

## 11. Persistence Schema

Runtime files under `plugins/KedamaSurvivors/data/runtime`:

- `players/<uuid>.json`
- `teams.json`
- `fixed_merchants.json`

Persisted progression-critical fields:

- player stats campaign fields (battery/stage/campaign/reward totals)
- team `stageIndex`
- team `progressionLocked`

Write model:

- async writes
- temp-file then move (atomic when supported)
- corrupt file quarantine rename
- periodic backup rotation

## 12. Test Coverage (Relevant to New Campaign Model)

Current focused tests include:

- stage world uniqueness load/runtime validation
- world distribution selection behavior
- segmented stat persistence round-trip (`PersistenceServiceTest`)

Run all tests:

```bash
./gradlew test
```

## 13. Known Constraints and Caveats

- Stage progression lock currently blocks starter changes and invite/accept flow; it is not a universal team action lock.
- `/vrs reload` refreshes config/data/language, but does not globally re-create all active runtime tasks; battery task rebinding is explicit via battery config command hot-update path.
- There is no hardcoded minimum stage group count enforcement in code (recommendation is operational/document-level).
