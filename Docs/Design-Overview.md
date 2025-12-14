# Detailed Development Spec: Command-Driven Roguelite Minigame (Paper 1.21.8, Java ≥ 17)

## 0. Summary

A standalone Paper/Spigot plugin implementing a roguelite “run” game mode inspired by Vampire Survivors + CS Arm Race. The plugin owns all game state (teams, runs, XP/levels, scaling, cooldowns, spawns, merchants, upgrades, persistence). Interop with other plugins is optional and done **only** via configurable **command templates with placeholders**. Enemy spawning and other integrations may also be command-based; the plugin itself must work without any other plugins installed.

Core rules:

* Players must select starter weapon then starter helmet via plugin command/UI.
* Players enter combat via `/vrs ready` (solo or team). When all required members are ready, countdown runs, then teleport into a selected combat world at a random safe location.
* Players cannot drop or pick up items anywhere; rewards are inserted directly into inventory or stored in a pending reward queue.
* Enemies spawn around players. Enemy level is derived from **average player level** and **player count** within radius.
* Killing VRS enemies awards XP and coin; nearby players also receive a percentage of XP.
* When XP reaches threshold, an upgrade prompt appears: choose weapon or helmet upgrade. While upgrade is unresolved, further XP is **held** (buffered), not lost. On resolution, held XP is applied and may trigger chained upgrades.
* Merchants are vanilla trade-capable entities (default villager) with recipes built from predefined ItemStacks (captured NBT/components).
* Death resets run XP and equipped weapon/helmet, preserves configured persistent resources; cooldown blocks re-entry.
* Global join switch supports maintenance: disables new entry and grace-ejects active players.

---

## 1. Target Environment & Non-Functional Requirements

### 1.1 Platform

* Server: Paper 1.21.8 (Spigot API compatible)
* Java: 17+
* Build: Gradle, shaded JAR (no mandatory external libs)

### 1.2 Performance

* All Bukkit API calls and command dispatch MUST occur on the main thread.
* Async threads MAY be used for computation-only planning:

  * spawn planning
  * placeholder expansion
  * reward calculations
  * persistence I/O
* Implement tick budgets and batching:

  * global `maxCommandsPerTick`
  * per-world `maxSummonsPerTick`
  * per-player spawn caps
* Provide `/vrs debug perf` output showing current budgets, queue sizes, and active entity counts.

### 1.3 Runtime dynamics

* No server restarts required for:

  * enabling/disabling combat worlds
  * adjusting spawn rates and scaling
  * changing join switch
  * merchant templates and trades
  * progression curves
* `/vrs reload` must hot-apply config safely.

### 1.4 Safety

* Placeholder engine must avoid injection hazards:

  * escaping policy configurable (default enabled)
  * logs redact sensitive expansions
* All entities created/managed by plugin must be identifiable by tags/PDC.

---

## 2. Core Data Model

### 2.1 PlayerState (in-memory; persisted partially)

Fields:

* `uuid`, `name`
* `mode`: LOBBY | READY | COUNTDOWN | IN_RUN | COOLDOWN | GRACE_EJECT
* `teamId` (nullable)
* `runId` (nullable)
* `cooldownUntilMillis` (0 if none)
* `ready`: boolean
* Starter selections:

  * `starterWeaponOptionId` (nullable)
  * `starterHelmetOptionId` (nullable)
* Equipment tracking (authoritative):

  * `weaponGroup`, `weaponLevel`
  * `helmetGroup`, `helmetLevel`
* XP system:

  * `xpProgress` (0..xpRequired)
  * `xpHeld` (>=0)
  * `xpRequired`
  * `upgradePending` boolean
* Economy:

  * `coinBalance` (virtual mode only)
  * `permaScore`
* Reward overflow:

  * `pendingRewards`: list of ItemStack (or abstract RewardGrant)

### 2.2 TeamState

* `teamId` (UUID or short id)
* `name`
* `ownerUuid`
* `memberUuids` set
* `invites` (uuid -> expiry)
* `readyMembers` set (for countdown)
* `activeRunId` (nullable)

### 2.3 RunState

* `runId`
* `teamId`
* `worldName`
* `spawnOrigin` (x,y,z,yaw,pitch)
* `startedAtMillis`
* `status`: STARTING | ACTIVE | ENDING
* Spawn system runtime:

  * per-player spawn counters
  * per-world active mob count
  * spawn plan queues
* Merchant runtime:

  * active merchant entity ids
  * next rotation times

---

## 3. Commands, Permissions, and Outputs

### 3.1 Root command

`/vrs ...`

### 3.2 Player commands

* `/vrs starter`
  Opens guided UI: choose weapon -> then helmet.

* `/vrs starter weapon`
  Opens weapon options UI.

* `/vrs starter helmet`
  Opens helmet options UI (requires weapon if `requireWeaponThenHelmet=true`).

* `/vrs starter clear`
  Clears selections and removes issued starter items if configured.

* `/vrs ready`
  Toggles ready. If solo or team ready complete, starts countdown.

* `/vrs quit`
  Ends run (if in run), teleports to lobby return, applies quit cooldown optionally.

* `/vrs team create <name>`

* `/vrs team invite <player>`

* `/vrs team join <team>`

* `/vrs team leave`

* `/vrs team disband`

* `/vrs team kick <player>`

* `/vrs status`
  Shows: mode, run/world, cooldown remaining, weapon/helmet group+level, XP progress/held, coin/perma-score.

### 3.3 Admin commands

* `/vrs join enable|disable`
* `/vrs world enable <world>` / `/vrs world disable <world>`
* `/vrs world list`
* `/vrs reload`
* `/vrs force start <player|team> [--bypassCooldown] [--bypassJoinSwitch]`
* `/vrs force stop <player|team>`
* `/vrs merchant spawn [world] [templateId]`
* `/vrs merchant clear [world]`
* `/vrs spawner pause|resume [world]`
* Capture commands:

  * `/vrs item capture weapon <group> <level> <templateId>`
  * `/vrs item capture helmet <group> <level> <templateId>`
  * `/vrs trade capture buyA <tradeId> <templateId>`
  * `/vrs trade capture buyB <tradeId> <templateId>`
  * `/vrs trade capture sell <tradeId> <templateId>`
* Debug:

  * `/vrs debug player <player>`
  * `/vrs debug perf`
  * `/vrs debug templates <templateName> <player>`

### 3.4 Permissions (suggested)

* `vrs.player` (default true)
* `vrs.team.*`
* `vrs.admin` (all admin)
* `vrs.admin.reload`
* `vrs.admin.world`
* `vrs.admin.join`
* `vrs.admin.force`
* `vrs.admin.capture`
* `vrs.admin.debug`
* `vrs.cooldown.bypass`

---

## 4. Placeholder & Command Template Engine

### 4.1 Template syntax

* Placeholder: `${key}`
* Optional transforms:

  * `${key|lower}`, `${key|upper}`
  * `${key|int}`, `${key|float:2}`
  * `${key|escape}` (default applied if enabled)
* Missing placeholder policy: `ERROR` (default), `EMPTY`, `LITERAL`

### 4.2 Variable sets

Required variables:

* Player: `${player}`, `${uuid}`
* Team: `${teamId}`, `${teamName}`
* Run: `${runId}`, `${runWorld}`
* Locations: `${x}`,`${y}`,`${z}`,`${yaw}`,`${pitch}` and spawn/anchor variants
* Progression: `${playerLevel}`,`${weaponGroup}`,`${weaponLevel}`,`${helmetGroup}`,`${helmetLevel}`
* Rewards: `${xpGained}`,`${coinGained}`,`${permaScoreGained}`
* Spawn: `${enemyArchetype}`,`${enemyType}`,`${enemyLevel}`,`${sx}`,`${sy}`,`${sz}`,`${spawnCount}`

### 4.3 Execution

* Commands stored without leading `/` in config; dispatched as console by default.
* Execution is queued and drained under tick budgets.
* Failed command policy:

  * log warning with template id and key vars
  * optionally apply fallback (configurable per template group)

---

## 5. Inventory Rules & Reward Delivery

### 5.1 No drop / no pickup

While `mode ∈ {READY, COUNTDOWN, IN_RUN}` (configurable scope):

* Cancel `PlayerDropItemEvent`
* Cancel `EntityPickupItemEvent` for players
* Cancel `PlayerAttemptPickupItemEvent` (Paper)
* Optional: prevent moving locked equipment out of slots via `InventoryClickEvent` and `InventoryDragEvent`.

### 5.2 Direct-to-inventory rewards

All rewards must be inserted directly:

* `PlayerInventory#addItem`
* Overflow handling modes:

  1. `PENDING_QUEUE` (default): store leftover ItemStacks in `pendingRewards`, attempt flush every `flushIntervalTicks`.
  2. `CONVERT_TO_VIRTUAL`: convert to virtual coin/perma-score (mapping configurable).
  3. `DISCARD` (not recommended).

### 5.3 Equipment slot locking (recommended)

Lock these slots while in run:

* weapon slot (MAIN_HAND or configured hotbar index)
* helmet slot
* Optional: lock entire inventory to prevent hoarding/abuse (configurable)

---

## 6. Starter Selection System

### 6.1 Requirements

* Player must select starter weapon then starter helmet (configurable order enforcement).
* Before `/vrs ready`, player may reselect; latest selection overrides previous.
* Selection grants actual items into required slots and updates authoritative group/level state.

### 6.2 Starter option definition

Each option defines:

* `optionId`
* `displayItem` (for GUI)
* `group`, `level` (usually 1)
* grant method:

  * `API_ITEMSTACK` via captured templateId
  * or `COMMAND` via template string

### 6.3 UI flow

* `/vrs starter` opens weapon GUI.
* After weapon choice:

  * grant weapon and record selection
  * automatically open helmet GUI
* After helmet choice:

  * grant helmet and record selection
  * player becomes eligible for `/vrs ready`

### 6.4 Override semantics

On reselect:

* Remove previously issued starter item from target slot if it matches previous template fingerprint (templateId marker in PDC recommended).
* Grant new item into same slot.
* Update authoritative group/level.

---

## 7. Run Entry via `/vrs ready`

### 7.1 Eligibility checks

On toggling ready ON:

* join switch enabled
* player not on cooldown (unless bypass perm)
* starter weapon and helmet selected
* player is not already in run

### 7.2 Team readiness

* If no team: solo pseudo-team.
* If team:

  * all online members must be ready to start countdown (offline policy configurable).
  * countdown cancels if any member unreadies, leaves team, goes offline (per config), or join switch disables.

### 7.3 Countdown

* Duration `ready.countdownSeconds`
* Sends actionbar/title updates
* On completion:

  * allocate RunState
  * select combat world
  * select spawn origin
  * teleport all eligible members via command templates

---

## 8. Combat Worlds Management

### 8.1 World list

Configured list of world names with:

* enabled flag
* weight
* spawn strategy:

  * bounded random
  * predefined points list

### 8.2 Selection

* Select among enabled worlds using weighted random (or uniform).
* Disabling a world:

  * affects only future selections
  * does not move existing run players

### 8.3 Safe spawn selection

For bounded random:

* sample positions within bounds
* validate:

  * chunk loaded or loadable
  * safe ground and headroom
  * not in excluded blocks/liquids unless allowed
* Use async planning for sampling; final validation + chunk ops on main thread.

---

## 9. Progression, XP Hold, Upgrades

### 9.1 Player combat level

Authoritative player level computed from internal state:

* `playerLevel = weaponLevel * weaponWeight + helmetLevel * helmetWeight`
* default values used only if state missing.

### 9.2 XP awarding on kill + share

On VRS mob death:

* Identify killer player (direct killer or last damager tracking, configurable).
* Compute base XP by enemy archetype and `enemyLevel`.
* Award to killer:

  * if `upgradePending=false`: apply to `xpProgress` until threshold; overflow goes to held; may trigger prompt
  * if `upgradePending=true`: add all XP to `xpHeld`
* Nearby share:

  * find players within `xpShareRadius` in same world
  * each (or split) receives `sharePercent` of killer XP
  * share XP obeys same hold logic per recipient

### 9.3 XP hold algorithm (normative)

Variables: `xpProgress`, `xpHeld`, `xpRequired`, `upgradePending`

Award XP `delta`:

* if `upgradePending`:

  * `xpHeld += delta`; return
* else:

  * `xpProgress += delta`
  * if `xpProgress >= xpRequired`:

    * `overflow = xpProgress - xpRequired`
    * `xpProgress = xpRequired`
    * `xpHeld += overflow`
    * set `upgradePending = true`
    * open upgrade UI (weapon vs helmet)

On resolution:

* `upgradePending = false`
* `xpProgress = 0`
* update `xpRequired` for next level
* apply held:

  * `xpProgress += xpHeld`
  * `xpHeld = 0`
* if `xpProgress >= xpRequired`: re-trigger upgrade again; enforce `maxAutoUpgradeChain` per tick; excess stays in `xpHeld`.

### 9.4 Upgrade choice

Player selects:

* `UPGRADE_WEAPON` or `UPGRADE_HELMET`

Upgrade mechanics:

* Determine current group `G` and level `L` for chosen slot.
* Next level `L+1`:

  * if exists: randomly select one candidate item entry in group+next level
  * if not: apply configured max-level behavior:

    * `CAP_AND_REWARD`: keep item, grant coin/perma-score
    * `REROLL_SAME_LEVEL`: replace with random candidate at same level
    * `NOOP`: do nothing
* Replace item in original slot.
* Update authoritative `weaponLevel/helmetLevel`.

---

## 10. Enemy Spawning (Command-Based)

### 10.1 Goals

* Continuously spawn enemies around players with bounded caps.
* Enemy level uses:

  * average player level in `levelSamplingRadius`
  * player count in radius
* Spawns executed via command templates; plugin can also spawn via API if configured, but command mode must be supported.

### 10.2 Spawn loop

* Executes every `spawning.loop.tickInterval` ticks.
* Phase A (main thread snapshot):

  * collect active run players by world
  * read their locations and playerLevel
  * compute counts of active VRS mobs (tagged) near each player (API query)
* Phase B (async plan):

  * for each player needing spawns, compute spawnCount to reach cap
  * compute enemyLevel for that player region
  * select archetypes via weights
  * sample candidate positions within [minDist,maxDist] and within spawnRadius
* Phase C (main thread execute):

  * drain spawn command queue under budgets:

    * `maxCommandsPerTick`
    * `maxSummonsPerTick`
    * per-player and per-world caps
  * dispatch configured summon commands with variables:

    * `${runWorld}`, `${sx}`, `${sy}`, `${sz}`, `${enemyLevel}`, `${runId}`

### 10.3 Enemy level formula

Given players within `levelSamplingRadius`:

* `avg = mean(playerLevel)`
* `n = count(players)`
* `enemyLevel = floor(avg*avgMultiplier + n*countMultiplier + levelOffset)`
* clamp to `[minLevel, maxLevel]`
* optional time scaling:

  * `enemyLevel += floor(runSeconds / timeStepSeconds) * addLevelPerStep`

### 10.4 Enemy archetypes

Each archetype defines:

* `enemyType` (string for template; e.g., `minecraft:zombie`)
* `spawnCommands[]` (1..N)
* `postSpawnCommands[]` (optional)
* rewards model:

  * `xpBase`, `xpPerLevel`
  * `coinBase`, `coinPerLevel`
  * perma-score chance modifiers (optional)
* `weight`

### 10.5 VRS mob identification (mandatory)

Spawn commands MUST include at least one stable identifier:

* scoreboard tag `vrs_mob`
* plus run tag `vrs_run_${runId}`
* plus level tag `vrs_lvl_${enemyLevel}` (or store level in PDC via API post-step if using API)

On entity death, plugin checks for `vrs_mob` tag; then parses enemyLevel from tag pattern `vrs_lvl_(\d+)` (configurable regex) or uses fallback.

---

## 11. Merchants (Vanilla Trade UI)

### 11.1 Merchant entities

* Default: Villager
* Must have:

  * custom name
  * tags: `vrs_merchant`, and optionally `vrs_world_${world}`

### 11.2 Recipe construction

Trade definitions are built from ItemStacks captured in config:

* `buyA` (required), `buyB` (optional), `sell` (required)
* `maxUses`, `villagerXp`, `priceMultiplier` etc.

### 11.3 Spawn and rotation

* Spawn interval `intervalSeconds`
* Lifetime `lifetimeSeconds`
* Spawn location selection:

  * random safe point in combat world bounds, with `minDistanceFromPlayers`
* Rotation policy:

  * despawn + respawn new merchant
  * or re-roll trades on same entity (configurable)

---

## 12. Death, Respawn, Cooldown, Persistence

### 12.1 Death in run

On player death while `IN_RUN`:

* End run participation for that player (or keep them in run based on respawn policy).
* Remove/clear weapon and helmet slots (or replace with “empty” placeholders).
* Reset run XP:

  * `xpProgress=0`, `xpHeld=0`, `upgradePending=false`
* Apply cooldown: `deathCooldownSeconds`
* Teleport policy:

  * `RESPAWN_TO_TEAM`: teleport to living teammate anchor (mid-run respawn)
  * `RETURN_TO_LOBBY`: return to lobby return point
  * `SPECTATE_UNTIL_RUN_END`: optional

### 12.2 Team respawn

If enabled:

* Choose anchor teammate:

  * nearest living teammate, else leader, else none
* Safe teleport near anchor using configured command template

### 12.3 Cooldown enforcement

* Entry denied if now < `cooldownUntilMillis`, unless bypass permission.
* `/vrs status` shows remaining seconds.

### 12.4 Persistence

Persist:

* `permaScore` (always)
* `coinBalance` (if virtual mode)
  Optional:
* aggregate stats

Storage:

* SQLite by default; MySQL optional.
* Save on:

  * player quit
  * periodic flush (`saveIntervalSeconds`)
  * run end events

---

## 13. Global Join Switch (Maintenance)

* `/vrs join disable`:

  * prevents new countdown completion and new ready toggles (configurable strictness)
  * all `IN_RUN` players enter `GRACE_EJECT` with timer `graceEjectSeconds`
* At grace expiry:

  * end run state for affected players/teams
  * execute lobby return commands

---

## 14. Configuration Specification (Normative)

### 14.1 `config.yml` top-level keys

* `plugin`
* `joinSwitch`
* `ready`
* `cooldown`
* `worlds`
* `teleport`
* `starterSelection`
* `inventoryRules`
* `progression`
* `equipmentPools`
* `spawning`
* `merchants`
* `economy`
* `persistence`
* `templates` (optional named templates)

### 14.2 Required config sections (minimum viable)

* At least one enabled combat world
* At least one starter weapon option and one starter helmet option
* At least one enemy archetype
* Lobby return commands
* Enter-run teleport commands

---

## 15. Module/Package Structure (Implementation Contract)

### 15.1 Modules

* `VrsPlugin` (bootstrap, command registration, listeners, scheduler)
* `ConfigService` (reloadable immutable config snapshot)
* `TemplateEngine` (placeholder expansion + escaping)
* `CommandQueue` (tick-budgeted command dispatch)
* `StateService` (PlayerState/TeamState/RunState repositories)
* `StarterService` (UI + grant + slot enforcement)
* `ReadyService` (ready toggle + countdown + run start)
* `WorldService` (world pool + spawn origin selection)
* `SpawnerService` (snapshot -> async plan -> main execute)
* `MobTagService` (tag parsing/encoding utilities)
* `RewardService` (XP/coin/perma-score; share logic; pending rewards)
* `UpgradeService` (UI + replacement + XP hold resolution)
* `MerchantService` (spawn + recipes + rotation)
* `PersistenceService` (DAO + periodic flush)

### 15.2 Main loop scheduling

Single scheduler tick (e.g., every 1–10 ticks) drives:

* command queue draining
* spawn execution
* pending rewards flush
* countdown timers
* grace eject timers
* merchant rotations

---

## 16. Acceptance Criteria (Definition of Done)

1. Plugin runs standalone with no external plugins.
2. Players must choose starter weapon then helmet; can reselect until ready.
3. `/vrs ready` (solo/team) starts countdown then teleports into enabled combat world with safe random spawn.
4. Players cannot drop or pick up items while in run; rewards are inserted directly; overflow handled by queue.
5. Enemies spawn around players using command templates; scaling uses avg player level and player count in radius.
6. Killing tagged enemies grants XP+coin; nearby players receive configured XP share.
7. When XP reaches threshold, upgrade UI appears; additional XP is held, not lost; no duplicate prompts; held XP applies after resolution.
8. Merchants are vanilla villager-based with configured recipes built from captured ItemStacks; rotate on timer.
9. Death resets run XP and removes weapon/helmet; persistent resources remain; cooldown enforced.
10. Global join switch disables new entry and grace-ejects active players; no restart required.
11. `/vrs reload` hot-applies config changes without breaking active runs.

---
