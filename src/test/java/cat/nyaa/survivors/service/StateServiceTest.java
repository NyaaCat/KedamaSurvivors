package cat.nyaa.survivors.service;

import cat.nyaa.survivors.model.PlayerMode;
import cat.nyaa.survivors.model.PlayerState;
import cat.nyaa.survivors.model.RunState;
import cat.nyaa.survivors.model.TeamState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for StateService.
 */
class StateServiceTest {

    private StateService service;

    @BeforeEach
    void setUp() {
        service = new StateService();
    }

    @Nested
    @DisplayName("Player State Management")
    class PlayerStateManagement {

        @Test
        @DisplayName("should create player state if not exists")
        void shouldCreatePlayerStateIfNotExists() {
            UUID playerId = UUID.randomUUID();
            PlayerState state = service.getOrCreatePlayer(playerId, "TestPlayer");

            assertNotNull(state);
            assertEquals(playerId, state.getUuid());
            assertEquals("TestPlayer", state.getName());
        }

        @Test
        @DisplayName("should return existing player state")
        void shouldReturnExistingPlayerState() {
            UUID playerId = UUID.randomUUID();
            PlayerState first = service.getOrCreatePlayer(playerId, "TestPlayer");
            PlayerState second = service.getOrCreatePlayer(playerId, "DifferentName");

            assertSame(first, second);
            assertEquals("TestPlayer", second.getName()); // Name shouldn't change
        }

        @Test
        @DisplayName("should get player state as Optional")
        void shouldGetPlayerStateAsOptional() {
            UUID playerId = UUID.randomUUID();
            assertTrue(service.getPlayer(playerId).isEmpty());

            service.getOrCreatePlayer(playerId, "TestPlayer");
            assertTrue(service.getPlayer(playerId).isPresent());
        }

        @Test
        @DisplayName("should remove player state")
        void shouldRemovePlayerState() {
            UUID playerId = UUID.randomUUID();
            service.getOrCreatePlayer(playerId, "TestPlayer");
            service.removePlayer(playerId);

            assertTrue(service.getPlayer(playerId).isEmpty());
        }
    }

    @Nested
    @DisplayName("Team Management")
    class TeamManagement {

        @Test
        @DisplayName("should create team with leader")
        void shouldCreateTeamWithLeader() {
            UUID leaderId = UUID.randomUUID();
            service.getOrCreatePlayer(leaderId, "Leader");

            TeamState team = service.createTeam("TestTeam", leaderId);

            assertNotNull(team);
            assertEquals("TestTeam", team.getName());
            assertTrue(team.isLeader(leaderId));
            assertTrue(team.isMember(leaderId));
        }

        @Test
        @DisplayName("should update player state when creating team")
        void shouldUpdatePlayerStateWhenCreatingTeam() {
            UUID leaderId = UUID.randomUUID();
            PlayerState playerState = service.getOrCreatePlayer(leaderId, "Leader");

            TeamState team = service.createTeam("TestTeam", leaderId);

            assertEquals(team.getTeamId(), playerState.getTeamId());
        }

        @Test
        @DisplayName("should find team by name")
        void shouldFindTeamByName() {
            UUID leaderId = UUID.randomUUID();
            service.getOrCreatePlayer(leaderId, "Leader");
            service.createTeam("UniqueTeamName", leaderId);

            Optional<TeamState> found = service.findTeamByName("UniqueTeamName");
            assertTrue(found.isPresent());
            assertEquals("UniqueTeamName", found.get().getName());
        }

        @Test
        @DisplayName("should find team by name case-insensitively")
        void shouldFindTeamByNameCaseInsensitively() {
            UUID leaderId = UUID.randomUUID();
            service.getOrCreatePlayer(leaderId, "Leader");
            service.createTeam("TestTeam", leaderId);

            assertTrue(service.findTeamByName("testteam").isPresent());
            assertTrue(service.findTeamByName("TESTTEAM").isPresent());
        }

        @Test
        @DisplayName("should add player to team")
        void shouldAddPlayerToTeam() {
            UUID leaderId = UUID.randomUUID();
            UUID memberId = UUID.randomUUID();
            service.getOrCreatePlayer(leaderId, "Leader");
            service.getOrCreatePlayer(memberId, "Member");

            TeamState team = service.createTeam("TestTeam", leaderId);
            assertTrue(service.addPlayerToTeam(memberId, team.getTeamId()));

            assertTrue(team.isMember(memberId));
            assertEquals(team.getTeamId(), service.getPlayer(memberId).get().getTeamId());
        }

        @Test
        @DisplayName("should remove player from team")
        void shouldRemovePlayerFromTeam() {
            UUID leaderId = UUID.randomUUID();
            UUID memberId = UUID.randomUUID();
            service.getOrCreatePlayer(leaderId, "Leader");
            service.getOrCreatePlayer(memberId, "Member");

            TeamState team = service.createTeam("TestTeam", leaderId);
            service.addPlayerToTeam(memberId, team.getTeamId());
            service.removePlayerFromTeam(memberId);

            assertFalse(team.isMember(memberId));
            assertNull(service.getPlayer(memberId).get().getTeamId());
        }

        @Test
        @DisplayName("should disband team and update all members")
        void shouldDisbandTeamAndUpdateAllMembers() {
            UUID leaderId = UUID.randomUUID();
            UUID memberId = UUID.randomUUID();
            service.getOrCreatePlayer(leaderId, "Leader");
            service.getOrCreatePlayer(memberId, "Member");

            TeamState team = service.createTeam("TestTeam", leaderId);
            service.addPlayerToTeam(memberId, team.getTeamId());

            service.disbandTeam(team.getTeamId());

            assertTrue(service.getTeam(team.getTeamId()).isEmpty());
            assertNull(service.getPlayer(leaderId).get().getTeamId());
            assertNull(service.getPlayer(memberId).get().getTeamId());
        }
    }

    @Nested
    @DisplayName("Run Management")
    class RunManagement {

        @Test
        @DisplayName("should create run for team")
        void shouldCreateRunForTeam() {
            UUID leaderId = UUID.randomUUID();
            service.getOrCreatePlayer(leaderId, "Leader");
            TeamState team = service.createTeam("TestTeam", leaderId);

            RunState run = service.createRun(team.getTeamId(), "combat_world");

            assertNotNull(run);
            assertEquals(team.getTeamId(), run.getTeamId());
            assertEquals("combat_world", run.getWorldName());
            assertTrue(run.isParticipant(leaderId));
        }

        @Test
        @DisplayName("should update player and team state when creating run")
        void shouldUpdatePlayerAndTeamStateWhenCreatingRun() {
            UUID leaderId = UUID.randomUUID();
            PlayerState playerState = service.getOrCreatePlayer(leaderId, "Leader");
            TeamState team = service.createTeam("TestTeam", leaderId);

            RunState run = service.createRun(team.getTeamId(), "combat_world");

            assertEquals(run.getRunId(), playerState.getRunId());
            assertEquals(run.getRunId(), team.getRunId());
            assertEquals(PlayerMode.IN_RUN, playerState.getMode());
        }

        @Test
        @DisplayName("should end run and reset player states")
        void shouldEndRunAndResetPlayerStates() {
            UUID leaderId = UUID.randomUUID();
            PlayerState playerState = service.getOrCreatePlayer(leaderId, "Leader");
            TeamState team = service.createTeam("TestTeam", leaderId);
            RunState run = service.createRun(team.getTeamId(), "combat_world");

            service.endRun(run.getRunId());

            assertEquals(PlayerMode.LOBBY, playerState.getMode());
            assertNull(playerState.getRunId());
            assertTrue(service.getTeamRun(team.getTeamId()).isEmpty());
        }

        @Test
        @DisplayName("should track players in runs")
        void shouldTrackPlayersInRuns() {
            UUID leaderId = UUID.randomUUID();
            service.getOrCreatePlayer(leaderId, "Leader");
            TeamState team = service.createTeam("TestTeam", leaderId);

            assertFalse(service.isInRun(leaderId));

            service.createRun(team.getTeamId(), "combat_world");

            assertTrue(service.isInRun(leaderId));
        }
    }

    @Nested
    @DisplayName("Utility Methods")
    class UtilityMethods {

        @Test
        @DisplayName("should track if player is in team")
        void shouldTrackIfPlayerIsInTeam() {
            UUID playerId = UUID.randomUUID();
            service.getOrCreatePlayer(playerId, "TestPlayer");

            assertFalse(service.isInTeam(playerId));

            service.createTeam("TestTeam", playerId);

            assertTrue(service.isInTeam(playerId));
        }

        @Test
        @DisplayName("should clear all state")
        void shouldClearAllState() {
            UUID playerId = UUID.randomUUID();
            service.getOrCreatePlayer(playerId, "TestPlayer");
            service.createTeam("TestTeam", playerId);

            service.clearAll();

            assertTrue(service.getPlayer(playerId).isEmpty());
            assertEquals(0, service.getActiveTeamCount());
        }

        @Test
        @DisplayName("should count players in runs")
        void shouldCountPlayersInRuns() {
            UUID leaderId = UUID.randomUUID();
            UUID member2 = UUID.randomUUID();
            service.getOrCreatePlayer(leaderId, "Leader");
            service.getOrCreatePlayer(member2, "Member2");
            TeamState team = service.createTeam("TestTeam", leaderId);
            service.addPlayerToTeam(member2, team.getTeamId());

            assertEquals(0, service.getPlayersInRunCount());

            service.createRun(team.getTeamId(), "combat_world");

            assertEquals(2, service.getPlayersInRunCount());
        }
    }
}
