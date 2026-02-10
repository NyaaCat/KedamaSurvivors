# KedamaSurvivors

KedamaSurvivors is a Paper 1.21.8+ co-op roguelite plugin built around **segmented runs** instead of endless survival.

The current core loop is:

1. Team up and pick starter weapon/helmet in prep area.
2. `/vrs ready` to enter a stage group ("World Fragment").
3. Fight, locate batteries, charge objectives, extract.
4. Return to prep with progress preserved.
5. Re-enter to continue the next stage group.
6. Final stage clear grants final bonus, then the team is auto-disbanded.

If the team wipes/disbands/loses all valid members, progression is reset.

## Documentation

- [Quick Start](Docs/Quick-Start.md)
- [Quick Start (zh_CN)](Docs/Quick-Start.zh_CN.md)
- [Commands Reference](Docs/Commands-Reference.md)
- [Commands Reference (zh_CN)](Docs/Commands-Reference.zh_CN.md)
- [Configuration Reference](Docs/Configuration-Reference.md)
- [Configuration Reference (zh_CN)](Docs/Configuration-Reference.zh_CN.md)
- [Design Overview](Docs/Design-Overview.md)
- [Development Spec](Docs/Development-Spec.md)
- [TODO](Docs/TODO.md)

## What Changed (Current Gameplay Model)

- Endless score farming was replaced by **stage progression** (`stageProgression.groups`).
- Each stage group defines:
  - stage id/display name
  - world list (can contain multiple worlds)
  - start enemy level
  - required battery count
  - clear rewards (coins/perma-score)
- A combat world can only belong to **one** stage group (validated in code, load and runtime update).
- Battery objective is now a first-class run objective:
  - low-probability spawn at a distance from players
  - charge radius with progress state (idle/charging/blocked/full)
  - charging pauses if VRS mobs are inside the radius
  - extra players in radius increase charge speed
  - when full, all online in-run members must interact to complete one battery objective
- Stage clear returns team to prep area without resetting progression.
- Final stage clear grants extra bonus (`stageProgression.finalBonus`) and auto-disbands the team.
- Team progression lock prevents changing starters/inviting new members after progression advances.

## High-Level Runtime Flow

### Team and Progression

- `TeamState.stageIndex` decides which stage group the next run uses.
- `TeamState.progressionLocked` blocks recruit + starter changes once progression has advanced.
- On stage clear:
  - stage reward granted
  - next stage unlocked
  - run ends back to prep
- On final clear:
  - final bonus granted
  - team progression reset
  - team disbanded
- On wipe/disconnect-timeout/forced failure:
  - run ends as failed
  - team progression reset

### World Selection

- With stage groups enabled, world selection comes from the stage group world set.
- World selection strategy is load-aware:
  - prefer worlds with zero in-run players
  - if all occupied, weight by spawn-point capacity and active player load

### Stats and Persistence

Persistent data is stored in `plugins/KedamaSurvivors/data/runtime`:

- `players/<uuid>.json`: player state + stats
- `teams.json`: team membership + stage progression state
- `fixed_merchants.json`: fixed merchant runtime data

Tracked progression stats include (non-exhaustive):

- run count / failed run count
- total batteries completed
- total stage clears / highest stage cleared
- campaign completions
- total stage reward coins / perma-score

## Commands (Short Index)

Player:

- `/vrs starter [weapon|helmet|world|status|clear]`
- `/vrs ready [solo]`
- `/vrs team ...`
- `/vrs quit`
- `/vrs status`
- `/vrs upgrade <power|defense>`

Admin:

- `/vrs admin ...` (status/run control/world/spawner/merchant/starter/equipment/config/economy)
- `/vrs reload`

See full syntax: [Commands Reference](Docs/Commands-Reference.md)

## Hot Update Capabilities

The following are designed for runtime updates via commands:

- Stage dynamic fields (including world list)
  - `/vrs admin config set stage.<groupId>.worlds <w1,w2,...>`
- Battery parameters
  - `/vrs admin config set battery...`
  - battery tasks are rebound at runtime for active runs
- Spawner archetype world restrictions (multi-world list)
  - `/vrs admin spawner archetype set worlds <id> <world1[,world2...] ...|any>`
- Multi-category config listing:
  - `/vrs admin config list <category...>`

## Build

Requirements:

- Java 21
- Paper 1.21.8 API target

Build:

```bash
./gradlew build
```

Primary artifact:

- `build/libs/KedamaSurvivors-<version>.jar`

## License

MIT
