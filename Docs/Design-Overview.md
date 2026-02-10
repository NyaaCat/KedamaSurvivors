# KedamaSurvivors Design Overview

## 1. Product Intent

KedamaSurvivors is a cooperative roguelite plugin for Paper that runs as a self-contained game loop inside Minecraft worlds.

Current design target:

- **Segmented campaign** instead of endless survival
- map progression by stage groups ("World Fragments")
- objective-driven extraction via battery charging
- persistent team/player progression state between stage attempts

## 2. Core Gameplay Loop

1. Team forms in prep area.
2. Players choose starter weapon/helmet.
3. Team starts run via ready countdown.
4. Run enters one stage group and picks a world for that stage.
5. Enemies spawn around players.
6. Battery objective appears at distance.
7. Team holds battery radius, kills interference mobs, fills charge.
8. Full-team interact completes one battery objective.
9. Stage required battery count reached -> return to prep and advance stage.
10. Final stage clear -> final bonus, team disband, progression reset.

## 3. State Model

## 3.1 Player (`PlayerState`)

Persistent and runtime fields include:

- mode (`LOBBY`, `COUNTDOWN`, `IN_RUN`, `COOLDOWN`, etc.)
- team/run links
- starter selections
- run equipment + XP state
- economy/perma score
- persistent statistics

## 3.2 Team (`TeamState`)

Tracks:

- members, invites, leader
- active run binding
- stage progression (`stageIndex`)
- progression lock (`progressionLocked`)

Lock behavior:

- after progression advances, team cannot invite and members cannot change starters

## 3.3 Run (`RunState`)

Tracks:

- world and participants
- alive/dead participant sets
- stage snapshot (`stageStartEnemyLevel`, `stageRequiredBatteries`, completed count)
- aggregate run metrics

## 4. Stage Group System

Configured at `config.yml -> stageProgression.groups`.

Each group defines:

- stage identity and name
- optional world subset
- starting enemy level floor
- required battery objectives
- clear rewards

Global final bonus is configured at `stageProgression.finalBonus`.

Validation invariant:

- one world can belong to only one stage group (enforced case-insensitively)

## 5. World Selection Strategy

When selecting run world:

1. If stage group has explicit world list, only those worlds are candidates.
2. Otherwise use globally enabled combat worlds.
3. Selection policy:
   - prefer worlds with zero in-run players
   - if all occupied, pick by weighted score:
     - spawn-point capacity
     - current run player load
     - configured world weight

This improves team spreading and reduces clustering.

## 6. Battery Objective Design

`BatteryService` lifecycle per active run:

- spawn task: periodic chance-based spawn attempt
- update task: charge progression and status updates

Battery interaction model:

- players in charge radius increase charge speed
- VRS mobs inside radius block charging
- optional surge waves add pressure while charging
- when charge reaches 100%, all online in-run participants must interact

Completion effect:

- increments run battery-completion count
- stage clear logic triggered when required count is reached

## 7. Success/Failure Transitions

## 7.1 Stage Clear

- apply stage reward
- advance `TeamState.stageIndex`
- keep team and progression lock
- return players to prep

## 7.2 Final Clear

- apply final bonus
- mark campaign completion stats
- reset team progression
- disband team

## 7.3 Failure

On wipe/disconnect timeout/forced failure:

- end run as failed
- apply cooldown where appropriate
- reset team progression (`stageIndex -> 0`, unlock)

## 8. Player Exit Semantics

Single-player effective exit (`/vrs quit` or disconnect grace expiry):

- remove player from current team/run
- reset that player's campaign attachment (starter selections/team link)
- persistent economy and long-term stats remain

## 9. Reward and Progression Architecture

Kill rewards are chance-based per archetype:

- XP
- coins
- perma-score

Extra systems:

- proximity XP share
- damage-contribution XP share
- optional reward multiplier mode

XP overflow can convert into perma-score at max-level behavior.

## 10. Persistence and Recoverability

Persistence service stores:

- player runtime+stats (`data/runtime/players/*.json`)
- team progression and membership (`data/runtime/teams.json`)
- fixed merchant runtime (`data/runtime/fixed_merchants.json`)

Design goals:

- async file I/O
- atomic write pattern
- corrupt-file quarantine
- periodic backup rotation

## 11. Runtime Operations and Hot Updates

Runtime config updates are command-driven through admin modules.

Notable hot-update paths:

- stage dynamic fields (`stage.<groupId>.*`)
- battery parameters (with active-task rebind)
- archetype world restrictions (`set worlds` with multi-value input)
- multi-category config listing for ops (`config list <cat...>`)

## 12. Observability

Operational visibility is provided by:

- `/vrs status`
- `/vrs admin status`
- `/vrs admin debug player`
- `/vrs admin debug run`
- `/vrs admin debug perf`

Persistent stats include campaign-related KPIs (battery, stage clear, campaign completion totals).

## 13. Non-Goals (Current)

- no external mandatory dependencies (Vault is optional)
- no built-in map generation
- no separate public stats leaderboard UI command yet
