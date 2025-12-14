# KedamaSurvivors - Implementation TODO

Living document tracking implementation progress. Mark tasks with `[x]` when completed.

---

## Phase 1: Foundation

### 1.1 Project Setup
- [x] Create main plugin class `KedamaSurvivorsPlugin`
- [x] Set up dependency injection / service locator pattern
- [x] Configure logging utility
- [x] Create base exception classes (`GameException`, `ConfigException`)

### 1.2 Configuration System
- [x] `ConfigService` - YAML config loading with hot reload
- [x] `PluginConfig` - Type-safe config access (integrated into ConfigService)
- [x] Config validation on load
- [x] Default config generation

### 1.3 Internationalization (i18n)
- [x] `I18nService` - Language file loading
- [x] Placeholder support in messages (`{key}`)
- [x] Color code parsing (`§` and `&`)
- [x] Clickable message components
- [x] String-based message keys with validation tests

### 1.4 Template Engine
- [x] `TemplateEngine` - Placeholder expansion (`{key}`)
- [x] Context builder with helper methods
- [x] Variable escaping for command injection prevention
- [x] Missing placeholder handling (ERROR/EMPTY/KEEP modes)
- [x] Command queue with tick budgets

---

## Phase 2: Core Data Models

### 2.1 Player State
- [x] `PlayerState` class with all fields
- [x] `PlayerMode` enum (LOBBY, READY, COUNTDOWN, IN_RUN, COOLDOWN, GRACE_EJECT, DISCONNECTED)
- [x] Computed properties (playerLevel, isAtMaxLevel, isWithinGracePeriod)

### 2.2 Team State
- [x] `TeamState` class
- [x] Invite management with expiry
- [x] Ready member tracking
- [x] Disconnect member tracking
- [x] Team wipe detection logic

### 2.3 Run State
- [x] `RunState` class
- [x] `RunStatus` enum (STARTING, ACTIVE, ENDING, COMPLETED)
- [x] Spawn point management
- [x] Participant tracking
- [x] Run statistics

### 2.4 Equipment Models
- [x] `EquipmentGroup` class with leveled item pools
- [x] `ItemTemplate` class for item creation
- [x] `EquipmentType` enum (WEAPON, HELMET)

### 2.5 State Service
- [x] `StateService` - Central state repository
- [x] Player state CRUD operations
- [x] Team state CRUD operations
- [x] Run state CRUD operations
- [x] Thread-safe access patterns

---

## Phase 3: Commands

### 3.1 Command Framework
- [x] Root `/vrs` command handler
- [x] Subcommand routing
- [x] Tab completion support
- [x] Permission checking
- [x] `SubCommand` interface

### 3.2 Player Commands
- [x] `/vrs starter` - Equipment selection
- [x] `/vrs starter weapon` - Weapon selection
- [x] `/vrs starter helmet` - Helmet selection
- [x] `/vrs starter clear` - Clear selections
- [x] `/vrs ready` - Toggle ready status
- [x] `/vrs quit` - Leave current run
- [x] `/vrs status` - Show player status
- [x] `/vrs upgrade <power|defense>` - Chat-based upgrade selection

### 3.3 Team Commands
- [x] `/vrs team create <name>` - Create team
- [x] `/vrs team invite <player>` - Invite player
- [x] `/vrs team accept <team>` - Accept invite
- [x] `/vrs team decline <team>` - Decline invite
- [x] `/vrs team leave` - Leave team
- [x] `/vrs team kick <player>` - Kick player
- [x] `/vrs team disband` - Disband team
- [x] `/vrs team list` - List teams
- [x] `/vrs team transfer <player>` - Transfer ownership

### 3.4 Admin Commands
- [x] `/vrs join enable|disable` - Global join switch
- [x] `/vrs world enable|disable|list <world>` - World management
- [x] `/vrs reload` - Reload config
- [x] `/vrs admin status` - Server status
- [x] `/vrs admin endrun` - End runs
- [x] `/vrs admin forcestart` - Force start
- [x] `/vrs admin kick` - Kick player from game
- [x] `/vrs admin reset` - Reset player state
- [x] `/vrs admin setperma` - Set perma-score
- [x] `/vrs admin equipment group create|delete|list` - Equipment group management
- [x] `/vrs admin equipment item add|remove|list` - Equipment item management (NBT capture)
- [x] `/vrs admin spawner archetype create|delete|list` - Enemy archetype management
- [x] `/vrs admin spawner archetype addcommand|removecommand|reward` - Archetype commands and rewards
- [x] `/vrs admin merchant template create|delete|list` - Merchant template management
- [x] `/vrs admin merchant trade add|remove|list` - Merchant trade management (NBT capture)

### 3.5 Debug Commands
- [x] `/vrs debug player <player>` - Player state dump
- [x] `/vrs debug perf` - Performance stats
- [x] `/vrs debug templates <name> <player>` - Test template expansion
- [x] `/vrs debug run <runId|list>` - Run state dump

---

## Phase 4: Game Services

### 4.1 Starter Selection Service
- [x] `StarterService` class
- [x] Weapon selection GUI
- [x] Helmet selection GUI
- [x] Item granting with PDC markers
- [x] Re-selection (remove previous, grant new)
- [x] Auto-open helmet after weapon

### 4.2 Ready & Countdown Service
- [x] `ReadyService` class
- [x] Ready toggle logic
- [x] Eligibility checks (cooldown, selections, join switch)
- [x] Team ready aggregation
- [x] Countdown task with title/actionbar/sound
- [x] Countdown cancellation on unready

### 4.3 World Service
- [x] `WorldService` class
- [x] Combat world registry from config
- [x] Weighted random world selection
- [x] Safe spawn location sampling
- [x] World bounds validation
- [x] Enable/disable without affecting active runs

### 4.4 Team Service
- [x] `TeamService` class (integrated into StateService)
- [x] Create team
- [x] Invite with expiry
- [x] Join team validation
- [x] Leave team handling
- [x] Kick player
- [x] Disband team
- [x] Ownership transfer
- [x] Disconnect handling
- [x] Reconnect handling
- [x] Team wipe detection

### 4.5 Run Lifecycle Service
- [x] `RunService` class
- [x] Run creation and initialization
- [x] Player teleportation to arena
- [x] Run ending (normal, wipe, force)
- [x] Grace eject handling (implemented in JoinSwitchService)

---

## Phase 5: Gameplay Systems

### 5.1 Spawner Service
- [x] `SpawnerService` class
- [x] Spawn loop with tick interval
- [x] Phase A: Main thread context collection
- [x] Phase B: Async spawn planning
- [x] Phase C: Main thread command execution
- [x] Enemy level calculation (avg level, player count, time)
- [x] Archetype weighted selection
- [x] Spawn position sampling
- [x] Mob count tracking with `vrs_mob` tag
- [x] Per-tick spawn/command budgets
- [x] Pause/resume per world

### 5.2 Reward Service
- [x] `RewardService` class
- [x] XP award calculation
- [x] XP application with hold logic
- [x] XP sharing to nearby players
- [x] Overflow XP → perma-score conversion
- [x] Coin award (vanilla item to inventory)
- [x] Perma-score award (scoreboard update)
- [x] Pending reward queue for overflow

### 5.3 Upgrade Service
- [x] `UpgradeService` class
- [x] Chat-based upgrade prompt (non-intrusive, replaces GUI)
- [x] Clickable power/defense options
- [x] Randomly highlighted suggested upgrade
- [x] Weapon upgrade option (power)
- [x] Helmet upgrade option (defense)
- [x] Equipment replacement with PDC
- [x] Max level handling (grant perma-score)
- [x] Both-max-level instant reward (no prompt)
- [x] Upgrade countdown on scoreboard
- [x] Periodic chat reminders (configurable interval)
- [x] Auto-selection on timeout (configurable)
- [x] `UpgradeReminderTask` - countdown and auto-selection
- [x] Held XP resolution (may chain upgrades)
- [x] XP required recalculation

### 5.4 Death & Respawn Service
- [x] Death event handler
- [x] Drop cancellation
- [x] Team respawn anchor finding (for rejoin flow)
- [x] Death sends player to prep location (no immediate teammate respawn)
- [x] Invulnerability application (3s) on rejoin
- [x] Death penalty (clear equipment, reset XP, clear starter selections)
- [x] Cooldown application (always enforced, no bypass)
- [x] Team wipe check on death
- [x] Rejoin flow via ready system after cooldown

### 5.5 Merchant Service
- [x] `MerchantService` class
- [x] Merchant spawn location sampling
- [x] Villager entity creation and configuration
- [x] Trade recipe building from templates
- [x] Merchant rotation/despawn scheduling
- [x] Merchant clearing

---

## Phase 6: UI & Display

### 6.1 Scoreboard Service
- [x] `ScoreboardService` class
- [x] Perma-score objective creation (`vrs_perma`)
- [x] Perma-score get/set operations
- [x] Cross-server compatible (vanilla scoreboard)

### 6.2 Sidebar Manager
- [x] Per-player scoreboard creation
- [x] Sidebar objective setup
- [x] Line building (weapon/helmet level, XP bar, coins, perma-score, team, time)
- [x] XP progress bar rendering
- [x] Periodic update task
- [x] Show/hide sidebar

### 6.3 GUI System
- [x] Base GUI holder class
- [x] Starter weapon GUI
- [x] Starter helmet GUI
- [x] Upgrade selection GUI
- [x] Click event handling
- [x] GUI close handling

---

## Phase 7: Event Listeners

### 7.1 Inventory Listener
- [x] `InventoryListener` class
- [x] Drop prevention in restricted modes
- [x] Pickup prevention in restricted modes (prevents pickup while GUI is open)
- [x] Equipment slot locking (weapon, helmet)
- [x] GUI click handling (cancels all interactions including number key swaps)

### 7.2 Player Listener
- [x] `PlayerListener` class
- [x] Join event - state initialization, reconnect handling
- [x] Quit event - disconnect handling, state save
- [x] Death event - forward to death service
- [x] Respawn event - invulnerability, spawn location
- [x] Damage event - invulnerability check

### 7.3 Combat Listener
- [x] `CombatListener` class
- [x] Invulnerability damage cancellation
- [x] Optional: prevent damage dealing during invul
- [x] VRS mob death handling - reward distribution
- [x] PVP damage prevention (configurable via `respawn.pvp`, default false)

### 7.4 Entity Listener
- [x] VRS mob identification (`vrs_mob` tag)
- [x] Enemy level parsing (`vrs_lvl_N` tag)
- [x] Mob death reward processing (integrated into CombatListener)

---

## Phase 8: Persistence

### 8.1 Item Storage
- [x] Item template YAML structure
- [x] NBT serialization/deserialization
- [x] Item capture command implementation
- [x] Template loading on startup/reload

### 8.2 Runtime Persistence
- [x] Player state JSON serialization
- [x] Team state JSON serialization
- [x] Periodic auto-save task
- [x] Save on player quit
- [x] Save on run end
- [x] Load on startup

### 8.3 Backup System
- [x] Backup task scheduling
- [x] Backup file rotation
- [x] Max backups limit

---

## Phase 9: Global Systems

### 9.1 Join Switch Service
- [x] `JoinSwitchService` class
- [x] Enable/disable toggle
- [x] Grace eject initiation for active players
- [x] Grace warning messages
- [x] Eject execution after grace period

### 9.2 Disconnect Checker
- [x] `DisconnectChecker` runnable
- [x] Periodic check for expired grace periods
- [x] Death penalty application for expired players
- [x] Team wipe check after expiry

### 9.3 Cooldown Display
- [x] Actionbar cooldown display
- [x] Periodic update task

---

## Phase 10: Testing

### 10.1 Unit Tests
- [x] `PlayerStateTest` - state transitions, computed properties
- [x] `TeamStateTest` - member management, wipe detection
- [x] `TemplateEngineTest` - placeholder expansion
- [x] `StateServiceTest` - state management
- [x] `EquipmentGroupTest` - level lookup, random selection
- [x] `RunStateTest` - run state management
- [x] `PersistenceServiceTest` - player/team data serialization
- [ ] `ConfigServiceTest` - config loading, validation (requires Bukkit mocking)
- [ ] `I18nServiceTest` - message loading, placeholders (requires Bukkit mocking)

### 10.2 Integration Tests
- [ ] Command execution tests
- [ ] Ready/countdown flow test
- [ ] Team creation/join flow test
- [ ] XP award and upgrade flow test
- [ ] Death and respawn flow test

### 10.3 Build Verification
- [x] `./gradlew build` succeeds
- [ ] Plugin loads on test server
- [ ] Basic command execution works
- [ ] No errors in server log

---

## Phase 11: Polish & Documentation

### 11.1 Code Quality
- [ ] Consistent code style
- [ ] Javadoc on public APIs
- [ ] Remove debug logging
- [ ] Error handling review

### 11.2 Performance
- [ ] Async operation verification
- [ ] Tick budget tuning
- [ ] Memory leak check
- [ ] Performance profiling

### 11.3 Documentation
- [ ] Update README with final instructions
- [ ] Configuration examples
- [ ] Admin guide
- [ ] API documentation (if applicable)

---

## Progress Summary

| Phase | Status | Completion |
|-------|--------|------------|
| 1. Foundation | Complete | 100% |
| 2. Core Data Models | Complete | 100% |
| 3. Commands | Complete | 100% |
| 4. Game Services | Complete | 100% |
| 5. Gameplay Systems | Complete | 100% |
| 6. UI & Display | Complete | 100% |
| 7. Event Listeners | Complete | 100% |
| 8. Persistence | Complete | 100% |
| 9. Global Systems | Complete | 100% |
| 10. Testing | In Progress | 75% |
| 11. Polish | Not Started | 0% |

**Overall: ~97%**

---

## Notes

- All Bukkit API calls must be on main thread
- Use async for computation, file I/O, NBT parsing
- Test after each major feature
- Keep commits atomic and well-documented
- Build command: `./gradlew build`
- Run tests: `./gradlew test`
