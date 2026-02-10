# KedamaSurvivors TODO (Current Backlog)

This file tracks **remaining work** after the segmented "World Fragment" campaign refactor.

## 1. High Priority

- [ ] Add integration tests for `RunService.completeBatteryObjective` stage transitions:
  - stage clear -> next stage index
  - final clear -> final bonus + team disband + progression reset
- [ ] Add integration tests for run failure reset paths (`WIPE`, `FORCED`, disconnect timeout) and verify team progression reset behavior.
- [ ] Add persistence tests for `TeamStateData` segmented fields (`stageIndex`, `progressionLocked`) round-trip.
- [ ] Add operator-facing command to inspect campaign progression/stats in a structured way (currently mostly via debug dumps).

## 2. Gameplay and UX

- [ ] Move battery display/status strings (currently hardcoded in `BatteryService`) into i18n keys.
- [ ] Add configurable battery UI channel options (nameplate-only vs actionbar/bossbar).
- [ ] Decide and implement whether to enforce a minimum stage group count (e.g. `>= 5`) at config validation level.
- [ ] Add explicit stage group admin CRUD/reorder commands (today supports runtime field edits, not group lifecycle management).

## 3. Data and Operations

- [ ] Add a safe migration helper for legacy endless-mode servers (command or one-shot tool with dry-run).
- [ ] Make backup retention count configurable (`PersistenceService` is currently hardcoded to keep 10 backups).
- [ ] Improve `/vrs reload` runtime behavior documentation and, if needed, add optional task rebind hooks for more subsystems.

## 4. Testing and Quality

- [ ] Add end-to-end tests covering multi-player battery interaction gate (all online in-run players must interact).
- [ ] Add tests for stage-world uniqueness edge cases with mixed-case and whitespace inputs through command path.
- [ ] Add tests around player quit/disconnect timeout progression detachment semantics.

## 5. Documentation

- [ ] Provide a production-grade sample campaign pack (5+ stage groups, non-overlapping worlds, tuned rewards).
- [ ] Add a dedicated operator runbook for hot-updating stage groups, battery settings, and archetype world restrictions.
- [ ] Add a troubleshooting matrix for common campaign issues (no battery spawn, stage cannot advance, world candidate exhaustion).

## Recently Completed (Reference)

- [x] Segmented stage progression with per-stage rewards and final bonus flow.
- [x] Battery objective integrated as stage completion condition.
- [x] Stage world uniqueness validation (load + runtime update).
- [x] World selection improved to prefer empty worlds and then weighted load distribution.
- [x] Expanded persistent stats for campaign metrics and reward totals.
- [x] Runtime hot-update support for stage fields, battery parameters, and multi-world archetype restrictions.
