package cat.nyaa.survivors.service;

import cat.nyaa.survivors.model.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Central service for managing all game state.
 * Thread-safe for concurrent access.
 */
public class StateService {

    // Player states indexed by UUID
    private final Map<UUID, PlayerState> playerStates = new ConcurrentHashMap<>();

    // Team states indexed by team ID
    private final Map<UUID, TeamState> teamStates = new ConcurrentHashMap<>();

    // Run states indexed by run ID
    private final Map<UUID, RunState> runStates = new ConcurrentHashMap<>();

    // Indexes for fast lookup
    private final Map<UUID, UUID> playerToTeam = new ConcurrentHashMap<>();  // player UUID -> team UUID
    private final Map<UUID, UUID> playerToRun = new ConcurrentHashMap<>();   // player UUID -> run UUID
    private final Map<UUID, UUID> teamToRun = new ConcurrentHashMap<>();     // team UUID -> run UUID

    // ==================== Player State Management ====================

    /**
     * Gets or creates a player state.
     */
    public PlayerState getOrCreatePlayer(UUID playerId, String name) {
        return playerStates.computeIfAbsent(playerId, id -> new PlayerState(id, name));
    }

    /**
     * Gets a player state if it exists.
     */
    public Optional<PlayerState> getPlayer(UUID playerId) {
        return Optional.ofNullable(playerStates.get(playerId));
    }

    /**
     * Gets a player state, throwing if not found.
     */
    public PlayerState requirePlayer(UUID playerId) {
        PlayerState state = playerStates.get(playerId);
        if (state == null) {
            throw new IllegalStateException("Player state not found: " + playerId);
        }
        return state;
    }

    /**
     * Removes a player state (on permanent leave).
     */
    public void removePlayer(UUID playerId) {
        playerStates.remove(playerId);
        playerToTeam.remove(playerId);
        playerToRun.remove(playerId);
    }

    /**
     * Gets all player states.
     */
    public Collection<PlayerState> getAllPlayers() {
        return Collections.unmodifiableCollection(playerStates.values());
    }

    /**
     * Gets players by mode.
     */
    public List<PlayerState> getPlayersByMode(PlayerMode mode) {
        return playerStates.values().stream()
                .filter(p -> p.getMode() == mode)
                .collect(Collectors.toList());
    }

    // ==================== Team State Management ====================

    /**
     * Creates a new team.
     */
    public TeamState createTeam(String name, UUID leaderId) {
        UUID teamId = UUID.randomUUID();
        TeamState team = new TeamState(teamId, name, leaderId);
        teamStates.put(teamId, team);
        playerToTeam.put(leaderId, teamId);

        // Update player state
        PlayerState playerState = playerStates.get(leaderId);
        if (playerState != null) {
            playerState.setTeamId(teamId);
        }

        return team;
    }

    /**
     * Gets a team by ID.
     */
    public Optional<TeamState> getTeam(UUID teamId) {
        return Optional.ofNullable(teamStates.get(teamId));
    }

    /**
     * Gets a team by ID, throwing if not found.
     */
    public TeamState requireTeam(UUID teamId) {
        TeamState team = teamStates.get(teamId);
        if (team == null) {
            throw new IllegalStateException("Team not found: " + teamId);
        }
        return team;
    }

    /**
     * Gets the team a player belongs to.
     */
    public Optional<TeamState> getPlayerTeam(UUID playerId) {
        UUID teamId = playerToTeam.get(playerId);
        if (teamId == null) return Optional.empty();
        return Optional.ofNullable(teamStates.get(teamId));
    }

    /**
     * Adds a player to a team.
     */
    public boolean addPlayerToTeam(UUID playerId, UUID teamId) {
        TeamState team = teamStates.get(teamId);
        if (team == null) return false;

        // Remove from old team if any
        UUID oldTeamId = playerToTeam.get(playerId);
        if (oldTeamId != null && !oldTeamId.equals(teamId)) {
            TeamState oldTeam = teamStates.get(oldTeamId);
            if (oldTeam != null) {
                oldTeam.removeMember(playerId);
            }
        }

        team.addMember(playerId);
        playerToTeam.put(playerId, teamId);

        // Update player state
        PlayerState playerState = playerStates.get(playerId);
        if (playerState != null) {
            playerState.setTeamId(teamId);
            playerState.setLastTeamId(teamId);
        }

        return true;
    }

    /**
     * Removes a player from their team.
     */
    public void removePlayerFromTeam(UUID playerId) {
        UUID teamId = playerToTeam.remove(playerId);
        if (teamId != null) {
            TeamState team = teamStates.get(teamId);
            if (team != null) {
                team.removeMember(playerId);

                // If team is empty, remove it
                if (team.isEmpty()) {
                    teamStates.remove(teamId);
                    teamToRun.remove(teamId);
                } else if (team.isLeader(playerId)) {
                    // Auto-select new leader
                    team.autoSelectLeader();
                }
            }
        }

        // Update player state
        PlayerState playerState = playerStates.get(playerId);
        if (playerState != null) {
            playerState.setTeamId(null);
        }
    }

    /**
     * Disbands a team.
     */
    public void disbandTeam(UUID teamId) {
        TeamState team = teamStates.remove(teamId);
        if (team == null) return;

        teamToRun.remove(teamId);

        // Remove all members from team tracking
        for (UUID memberId : team.getMembers()) {
            playerToTeam.remove(memberId);
            PlayerState playerState = playerStates.get(memberId);
            if (playerState != null) {
                playerState.setTeamId(null);
            }
        }
    }

    /**
     * Gets all teams.
     */
    public Collection<TeamState> getAllTeams() {
        return Collections.unmodifiableCollection(teamStates.values());
    }

    /**
     * Finds a team by name (case-insensitive).
     */
    public Optional<TeamState> findTeamByName(String name) {
        return teamStates.values().stream()
                .filter(t -> t.getName().equalsIgnoreCase(name))
                .findFirst();
    }

    // ==================== Run State Management ====================

    /**
     * Creates a new run for a team.
     */
    public RunState createRun(UUID teamId, String worldName) {
        TeamState team = teamStates.get(teamId);
        if (team == null) {
            throw new IllegalStateException("Cannot create run for non-existent team: " + teamId);
        }

        UUID runId = UUID.randomUUID();
        RunState run = new RunState(runId, teamId, worldName);
        runStates.put(runId, run);
        teamToRun.put(teamId, runId);
        team.setRunId(runId);

        // Add team members as participants
        for (UUID memberId : team.getMembers()) {
            run.addParticipant(memberId);
            playerToRun.put(memberId, runId);

            PlayerState playerState = playerStates.get(memberId);
            if (playerState != null) {
                playerState.setRunId(runId);
                playerState.setMode(PlayerMode.IN_RUN);
            }
        }

        return run;
    }

    /**
     * Gets a run by ID.
     */
    public Optional<RunState> getRun(UUID runId) {
        return Optional.ofNullable(runStates.get(runId));
    }

    /**
     * Gets the run a player is in.
     */
    public Optional<RunState> getPlayerRun(UUID playerId) {
        UUID runId = playerToRun.get(playerId);
        if (runId == null) return Optional.empty();
        return Optional.ofNullable(runStates.get(runId));
    }

    /**
     * Gets the run for a team.
     */
    public Optional<RunState> getTeamRun(UUID teamId) {
        UUID runId = teamToRun.get(teamId);
        if (runId == null) return Optional.empty();
        return Optional.ofNullable(runStates.get(runId));
    }

    /**
     * Ends a run.
     */
    public void endRun(UUID runId) {
        RunState run = runStates.get(runId);
        if (run == null) return;

        run.end();

        // Clear player run associations
        for (UUID playerId : run.getParticipants()) {
            playerToRun.remove(playerId);
            PlayerState playerState = playerStates.get(playerId);
            if (playerState != null) {
                playerState.resetRunState();
                playerState.setMode(PlayerMode.LOBBY);
            }
        }

        // Clear team run association
        teamToRun.remove(run.getTeamId());
        TeamState team = teamStates.get(run.getTeamId());
        if (team != null) {
            team.resetForNewRun();
        }
    }

    /**
     * Completes and removes a run (after cleanup).
     */
    public void removeRun(UUID runId) {
        RunState run = runStates.remove(runId);
        if (run != null) {
            run.complete();
        }
    }

    /**
     * Gets all active runs.
     */
    public Collection<RunState> getActiveRuns() {
        return runStates.values().stream()
                .filter(RunState::isActive)
                .collect(Collectors.toList());
    }

    /**
     * Gets all runs.
     */
    public Collection<RunState> getAllRuns() {
        return Collections.unmodifiableCollection(runStates.values());
    }

    // ==================== Utility Methods ====================

    /**
     * Gets the total number of players in runs.
     */
    public int getPlayersInRunCount() {
        return playerToRun.size();
    }

    /**
     * Gets the total number of active teams.
     */
    public int getActiveTeamCount() {
        return teamStates.size();
    }

    /**
     * Gets the total number of active runs.
     */
    public int getActiveRunCount() {
        return (int) runStates.values().stream()
                .filter(RunState::isActive)
                .count();
    }

    /**
     * Clears all state (for reload/shutdown).
     */
    public void clearAll() {
        playerStates.clear();
        teamStates.clear();
        runStates.clear();
        playerToTeam.clear();
        playerToRun.clear();
        teamToRun.clear();
    }

    /**
     * Gets players currently in a specific run.
     */
    public List<PlayerState> getPlayersInRun(UUID runId) {
        return playerToRun.entrySet().stream()
                .filter(e -> e.getValue().equals(runId))
                .map(e -> playerStates.get(e.getKey()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Checks if a player is in any run.
     */
    public boolean isInRun(UUID playerId) {
        return playerToRun.containsKey(playerId);
    }

    /**
     * Checks if a player is in any team.
     */
    public boolean isInTeam(UUID playerId) {
        return playerToTeam.containsKey(playerId);
    }
}
