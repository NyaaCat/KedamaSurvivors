package cat.nyaa.survivors.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TeamState.
 */
class TeamStateTest {

    private UUID teamId;
    private UUID leaderId;
    private TeamState team;

    @BeforeEach
    void setUp() {
        teamId = UUID.randomUUID();
        leaderId = UUID.randomUUID();
        team = new TeamState(teamId, "TestTeam", leaderId);
    }

    @Nested
    @DisplayName("Initialization")
    class Initialization {

        @Test
        @DisplayName("should initialize with correct ID and name")
        void shouldInitializeWithCorrectIdAndName() {
            assertEquals(teamId, team.getTeamId());
            assertEquals("TestTeam", team.getName());
        }

        @Test
        @DisplayName("should set leader as first member")
        void shouldSetLeaderAsFirstMember() {
            assertTrue(team.isMember(leaderId));
            assertTrue(team.isLeader(leaderId));
            assertEquals(1, team.getMemberCount());
        }
    }

    @Nested
    @DisplayName("Member Management")
    class MemberManagement {

        @Test
        @DisplayName("should add member successfully")
        void shouldAddMemberSuccessfully() {
            UUID newMember = UUID.randomUUID();
            assertTrue(team.addMember(newMember));
            assertTrue(team.isMember(newMember));
            assertEquals(2, team.getMemberCount());
        }

        @Test
        @DisplayName("should not add duplicate member")
        void shouldNotAddDuplicateMember() {
            assertFalse(team.addMember(leaderId));
            assertEquals(1, team.getMemberCount());
        }

        @Test
        @DisplayName("should remove member successfully")
        void shouldRemoveMemberSuccessfully() {
            UUID member = UUID.randomUUID();
            team.addMember(member);
            assertTrue(team.removeMember(member));
            assertFalse(team.isMember(member));
        }

        @Test
        @DisplayName("should return immutable member set")
        void shouldReturnImmutableMemberSet() {
            Set<UUID> members = team.getMembers();
            assertThrows(UnsupportedOperationException.class, () -> members.add(UUID.randomUUID()));
        }
    }

    @Nested
    @DisplayName("Ready State")
    class ReadyState {

        @Test
        @DisplayName("should track ready state per member")
        void shouldTrackReadyStatePerMember() {
            assertFalse(team.isReady(leaderId));
            team.setReady(leaderId, true);
            assertTrue(team.isReady(leaderId));
        }

        @Test
        @DisplayName("should detect when all members are ready")
        void shouldDetectWhenAllMembersAreReady() {
            UUID member2 = UUID.randomUUID();
            team.addMember(member2);

            assertFalse(team.isAllReady());

            team.setReady(leaderId, true);
            assertFalse(team.isAllReady());

            team.setReady(member2, true);
            assertTrue(team.isAllReady());
        }

        @Test
        @DisplayName("should clear all ready states")
        void shouldClearAllReadyStates() {
            team.setReady(leaderId, true);
            team.clearReady();
            assertFalse(team.isReady(leaderId));
        }
    }

    @Nested
    @DisplayName("Disconnect Tracking")
    class DisconnectTracking {

        @Test
        @DisplayName("should mark member as disconnected")
        void shouldMarkMemberAsDisconnected() {
            assertFalse(team.isDisconnected(leaderId));
            team.markDisconnected(leaderId);
            assertTrue(team.isDisconnected(leaderId));
        }

        @Test
        @DisplayName("should mark member as reconnected")
        void shouldMarkMemberAsReconnected() {
            team.markDisconnected(leaderId);
            team.markReconnected(leaderId);
            assertFalse(team.isDisconnected(leaderId));
        }

        @Test
        @DisplayName("should not mark non-member as disconnected")
        void shouldNotMarkNonMemberAsDisconnected() {
            UUID nonMember = UUID.randomUUID();
            team.markDisconnected(nonMember);
            assertFalse(team.isDisconnected(nonMember));
        }

        @Test
        @DisplayName("should purge expired disconnects")
        void shouldPurgeExpiredDisconnects() throws InterruptedException {
            UUID member = UUID.randomUUID();
            team.addMember(member);
            team.markDisconnected(member);

            // Wait a bit to ensure time has passed
            Thread.sleep(50);

            // Purge with very short grace period
            Set<UUID> purged = team.purgeExpiredDisconnects(10);

            assertTrue(purged.contains(member));
            assertFalse(team.isMember(member));
        }

        @Test
        @DisplayName("should not purge disconnects within grace period")
        void shouldNotPurgeDisconnectsWithinGracePeriod() {
            UUID member = UUID.randomUUID();
            team.addMember(member);
            team.markDisconnected(member);

            // Purge with long grace period
            Set<UUID> purged = team.purgeExpiredDisconnects(300000);

            assertTrue(purged.isEmpty());
            assertTrue(team.isMember(member));
        }
    }

    @Nested
    @DisplayName("Invite Management")
    class InviteManagement {

        @Test
        @DisplayName("should track pending invite")
        void shouldTrackPendingInvite() {
            UUID invitee = UUID.randomUUID();
            team.addInvite(invitee, System.currentTimeMillis() + 60000);
            assertTrue(team.hasInvite(invitee));
        }

        @Test
        @DisplayName("should expire old invites")
        void shouldExpireOldInvites() {
            UUID invitee = UUID.randomUUID();
            team.addInvite(invitee, System.currentTimeMillis() - 1000);
            assertFalse(team.hasInvite(invitee));
        }

        @Test
        @DisplayName("should remove invite")
        void shouldRemoveInvite() {
            UUID invitee = UUID.randomUUID();
            team.addInvite(invitee, System.currentTimeMillis() + 60000);
            team.removeInvite(invitee);
            assertFalse(team.hasInvite(invitee));
        }
    }

    @Nested
    @DisplayName("Team Wipe Detection")
    class TeamWipeDetection {

        @Test
        @DisplayName("should detect team wipe when no alive players")
        void shouldDetectTeamWipeWhenNoAlivePlayers() {
            assertTrue(team.isWiped(Set.of(), 300000));
        }

        @Test
        @DisplayName("should not detect wipe when member is alive")
        void shouldNotDetectWipeWhenMemberIsAlive() {
            assertFalse(team.isWiped(Set.of(leaderId), 300000));
        }

        @Test
        @DisplayName("should detect wipe when only disconnected member is alive")
        void shouldDetectWipeWhenOnlyDisconnectedMemberIsAlive() throws InterruptedException {
            team.markDisconnected(leaderId);
            Thread.sleep(50);
            assertTrue(team.isWiped(Set.of(leaderId), 10)); // Very short grace period
        }
    }

    @Nested
    @DisplayName("Leadership")
    class Leadership {

        @Test
        @DisplayName("should transfer leadership successfully")
        void shouldTransferLeadershipSuccessfully() {
            UUID newLeader = UUID.randomUUID();
            team.addMember(newLeader);

            assertTrue(team.transferLeadership(newLeader));
            assertTrue(team.isLeader(newLeader));
            assertFalse(team.isLeader(leaderId));
        }

        @Test
        @DisplayName("should not transfer leadership to non-member")
        void shouldNotTransferLeadershipToNonMember() {
            UUID nonMember = UUID.randomUUID();
            assertFalse(team.transferLeadership(nonMember));
            assertTrue(team.isLeader(leaderId));
        }

        @Test
        @DisplayName("should auto-select new leader")
        void shouldAutoSelectNewLeader() {
            UUID member2 = UUID.randomUUID();
            team.addMember(member2);
            team.removeMember(leaderId);

            UUID newLeader = team.autoSelectLeader();
            assertEquals(member2, newLeader);
            assertTrue(team.isLeader(member2));
        }
    }

    @Nested
    @DisplayName("State Reset")
    class StateReset {

        @Test
        @DisplayName("should reset for new run")
        void shouldResetForNewRun() {
            UUID member = UUID.randomUUID();
            team.addMember(member);
            team.setReady(leaderId, true);
            team.setReady(member, true);
            team.markDisconnected(member);
            team.setRunId(UUID.randomUUID());

            team.resetForNewRun();

            assertFalse(team.isReady(leaderId));
            assertFalse(team.isDisconnected(member));
            assertNull(team.getRunId());
        }
    }
}
