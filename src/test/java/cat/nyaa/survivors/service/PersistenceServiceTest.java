package cat.nyaa.survivors.service;

import cat.nyaa.survivors.model.PlayerMode;
import cat.nyaa.survivors.model.PlayerState;
import cat.nyaa.survivors.model.TeamState;
import cat.nyaa.survivors.service.PersistenceService.PlayerStateData;
import cat.nyaa.survivors.service.PersistenceService.TeamStateData;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.*;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PersistenceService data serialization.
 * Note: File I/O tests are not included as they require Bukkit environment.
 */
@DisplayName("PersistenceService Tests")
class PersistenceServiceTest {

    private Gson gson;

    @BeforeEach
    void setUp() {
        gson = new GsonBuilder()
                .setPrettyPrinting()
                .disableHtmlEscaping()
                .create();
    }

    @Nested
    @DisplayName("PlayerStateData Serialization")
    class PlayerStateDataTests {

        @Test
        @DisplayName("Should convert PlayerState to PlayerStateData")
        void toPlayerStateData() {
            UUID uuid = UUID.randomUUID();
            PlayerState player = new PlayerState(uuid, "TestPlayer");
            player.setStarterWeaponOptionId("starter_sword");
            player.setStarterHelmetOptionId("starter_helmet");
            player.setMode(PlayerMode.LOBBY);

            PlayerStateData data = PlayerStateData.fromPlayerState(player);

            assertEquals(uuid.toString(), data.uuid);
            assertEquals("TestPlayer", data.name);
            assertEquals("LOBBY", data.mode);
            assertEquals("starter_sword", data.starterWeaponOptionId);
            assertEquals("starter_helmet", data.starterHelmetOptionId);
            assertEquals(0, data.cooldownUntilMillis);
        }

        @Test
        @DisplayName("Should convert PlayerStateData to PlayerState")
        void toPlayerState() {
            PlayerStateData data = new PlayerStateData();
            data.uuid = UUID.randomUUID().toString();
            data.name = "TestPlayer";
            data.mode = "LOBBY";
            data.starterWeaponOptionId = "starter_sword";
            data.starterHelmetOptionId = "starter_helmet";
            data.cooldownUntilMillis = 0;

            PlayerState player = data.toPlayerState();

            assertEquals(UUID.fromString(data.uuid), player.getUuid());
            assertEquals("TestPlayer", player.getName());
            assertEquals(PlayerMode.LOBBY, player.getMode());
            assertEquals("starter_sword", player.getStarterWeaponOptionId());
            assertEquals("starter_helmet", player.getStarterHelmetOptionId());
        }

        @Test
        @DisplayName("Should preserve cooldown when in COOLDOWN mode")
        void cooldownPreserved() {
            UUID uuid = UUID.randomUUID();
            PlayerState player = new PlayerState(uuid, "CooldownPlayer");
            long cooldownTime = System.currentTimeMillis() + 60000; // 60 seconds from now
            player.setMode(PlayerMode.COOLDOWN);
            player.setCooldownUntilMillis(cooldownTime);

            PlayerStateData data = PlayerStateData.fromPlayerState(player);

            assertEquals("COOLDOWN", data.mode);
            assertEquals(cooldownTime, data.cooldownUntilMillis);
        }

        @Test
        @DisplayName("Should reset non-persistent modes to LOBBY")
        void nonPersistentModesReset() {
            UUID uuid = UUID.randomUUID();
            PlayerState player = new PlayerState(uuid, "InRunPlayer");
            player.setMode(PlayerMode.IN_RUN);

            PlayerStateData data = PlayerStateData.fromPlayerState(player);

            assertEquals("LOBBY", data.mode);
        }

        @Test
        @DisplayName("Should reset expired cooldown to LOBBY on load")
        void expiredCooldownReset() {
            PlayerStateData data = new PlayerStateData();
            data.uuid = UUID.randomUUID().toString();
            data.name = "ExpiredCooldown";
            data.mode = "COOLDOWN";
            data.cooldownUntilMillis = System.currentTimeMillis() - 10000; // 10 seconds ago

            PlayerState player = data.toPlayerState();

            assertEquals(PlayerMode.LOBBY, player.getMode());
        }

        @Test
        @DisplayName("Should handle null starter selections")
        void nullStarterSelections() {
            UUID uuid = UUID.randomUUID();
            PlayerState player = new PlayerState(uuid, "NoStarters");

            PlayerStateData data = PlayerStateData.fromPlayerState(player);
            PlayerState restored = data.toPlayerState();

            assertNull(restored.getStarterWeaponOptionId());
            assertNull(restored.getStarterHelmetOptionId());
        }

        @Test
        @DisplayName("Should serialize and deserialize via JSON")
        void jsonRoundTrip() {
            UUID uuid = UUID.randomUUID();
            PlayerState player = new PlayerState(uuid, "JsonTest");
            player.setStarterWeaponOptionId("sword_group");
            player.setStarterHelmetOptionId("helmet_group");

            PlayerStateData data = PlayerStateData.fromPlayerState(player);
            String json = gson.toJson(data);
            PlayerStateData restored = gson.fromJson(json, PlayerStateData.class);

            assertEquals(data.uuid, restored.uuid);
            assertEquals(data.name, restored.name);
            assertEquals(data.starterWeaponOptionId, restored.starterWeaponOptionId);
            assertEquals(data.starterHelmetOptionId, restored.starterHelmetOptionId);
        }

        @Test
        @DisplayName("Should persist extended segmented progression stats")
        void segmentedStatsRoundTrip() {
            UUID uuid = UUID.randomUUID();
            PlayerState player = new PlayerState(uuid, "SegmentStats");

            player.getStats().setRunCount(12);
            player.getStats().setFailedRunCount(5);
            player.getStats().setTotalBatteriesCompleted(27);
            player.getStats().setTotalStageClears(9);
            player.getStats().setHighestStageCleared(5);
            player.getStats().setCampaignCompletions(2);
            player.getStats().setTotalStageRewardCoins(4321);
            player.getStats().setTotalStageRewardPermaScore(321);

            PlayerStateData data = PlayerStateData.fromPlayerState(player);
            String json = gson.toJson(data);
            PlayerStateData restoredData = gson.fromJson(json, PlayerStateData.class);
            PlayerState restored = restoredData.toPlayerState();

            assertEquals(12, restored.getStats().getRunCount());
            assertEquals(5, restored.getStats().getFailedRunCount());
            assertEquals(27, restored.getStats().getTotalBatteriesCompleted());
            assertEquals(9, restored.getStats().getTotalStageClears());
            assertEquals(5, restored.getStats().getHighestStageCleared());
            assertEquals(2, restored.getStats().getCampaignCompletions());
            assertEquals(4321, restored.getStats().getTotalStageRewardCoins());
            assertEquals(321, restored.getStats().getTotalStageRewardPermaScore());
        }
    }

    @Nested
    @DisplayName("TeamStateData Serialization")
    class TeamStateDataTests {

        @Test
        @DisplayName("Should convert TeamState to TeamStateData")
        void toTeamStateData() {
            UUID teamId = UUID.randomUUID();
            UUID leaderId = UUID.randomUUID();
            TeamState team = new TeamState(teamId, "TestTeam", leaderId);
            UUID member2 = UUID.randomUUID();
            team.addMember(member2);

            TeamStateData data = TeamStateData.fromTeamState(team);

            assertEquals(teamId.toString(), data.teamId);
            assertEquals("TestTeam", data.name);
            assertEquals(leaderId.toString(), data.leaderId);
            assertEquals(2, data.members.size());
            assertTrue(data.members.contains(leaderId.toString()));
            assertTrue(data.members.contains(member2.toString()));
        }

        @Test
        @DisplayName("Should convert TeamStateData to TeamState")
        void toTeamState() {
            UUID teamId = UUID.randomUUID();
            UUID leaderId = UUID.randomUUID();
            UUID member2 = UUID.randomUUID();

            TeamStateData data = new TeamStateData();
            data.teamId = teamId.toString();
            data.name = "TestTeam";
            data.leaderId = leaderId.toString();
            data.members = Arrays.asList(leaderId.toString(), member2.toString());
            data.createdAtMillis = System.currentTimeMillis();

            TeamState team = data.toTeamState();

            assertEquals(teamId, team.getTeamId());
            assertEquals("TestTeam", team.getName());
            assertEquals(leaderId, team.getLeaderId());
            assertEquals(2, team.getMemberCount());
            assertTrue(team.isMember(leaderId));
            assertTrue(team.isMember(member2));
        }

        @Test
        @DisplayName("Should not duplicate leader in members")
        void noDuplicateLeader() {
            UUID teamId = UUID.randomUUID();
            UUID leaderId = UUID.randomUUID();

            TeamStateData data = new TeamStateData();
            data.teamId = teamId.toString();
            data.name = "SoloTeam";
            data.leaderId = leaderId.toString();
            data.members = Arrays.asList(leaderId.toString());
            data.createdAtMillis = System.currentTimeMillis();

            TeamState team = data.toTeamState();

            assertEquals(1, team.getMemberCount());
        }

        @Test
        @DisplayName("Should serialize and deserialize via JSON")
        void jsonRoundTrip() {
            UUID teamId = UUID.randomUUID();
            UUID leaderId = UUID.randomUUID();
            TeamState team = new TeamState(teamId, "JsonTeam", leaderId);

            TeamStateData data = TeamStateData.fromTeamState(team);
            String json = gson.toJson(data);
            TeamStateData restored = gson.fromJson(json, TeamStateData.class);

            assertEquals(data.teamId, restored.teamId);
            assertEquals(data.name, restored.name);
            assertEquals(data.leaderId, restored.leaderId);
        }

        @Test
        @DisplayName("Should handle team with multiple members")
        void multipleMembers() {
            UUID teamId = UUID.randomUUID();
            UUID leaderId = UUID.randomUUID();
            TeamState team = new TeamState(teamId, "BigTeam", leaderId);

            // Add 4 more members
            for (int i = 0; i < 4; i++) {
                team.addMember(UUID.randomUUID());
            }

            TeamStateData data = TeamStateData.fromTeamState(team);

            assertEquals(5, data.members.size());

            TeamState restored = data.toTeamState();
            assertEquals(5, restored.getMemberCount());
        }
    }

    @Nested
    @DisplayName("List Serialization")
    class ListSerializationTests {

        @Test
        @DisplayName("Should serialize list of PlayerStateData")
        void playerStateDataList() {
            PlayerStateData data1 = new PlayerStateData();
            data1.uuid = UUID.randomUUID().toString();
            data1.name = "Player1";
            data1.mode = "LOBBY";

            PlayerStateData data2 = new PlayerStateData();
            data2.uuid = UUID.randomUUID().toString();
            data2.name = "Player2";
            data2.mode = "COOLDOWN";
            data2.cooldownUntilMillis = System.currentTimeMillis() + 30000;

            List<PlayerStateData> list = Arrays.asList(data1, data2);
            String json = gson.toJson(list);

            assertNotNull(json);
            assertTrue(json.contains("Player1"));
            assertTrue(json.contains("Player2"));
        }

        @Test
        @DisplayName("Should serialize list of TeamStateData")
        void teamStateDataList() {
            TeamStateData team1 = new TeamStateData();
            team1.teamId = UUID.randomUUID().toString();
            team1.name = "Team1";
            team1.leaderId = UUID.randomUUID().toString();
            team1.members = Arrays.asList(team1.leaderId);
            team1.createdAtMillis = System.currentTimeMillis();

            TeamStateData team2 = new TeamStateData();
            team2.teamId = UUID.randomUUID().toString();
            team2.name = "Team2";
            team2.leaderId = UUID.randomUUID().toString();
            team2.members = Arrays.asList(team2.leaderId);
            team2.createdAtMillis = System.currentTimeMillis();

            List<TeamStateData> list = Arrays.asList(team1, team2);
            String json = gson.toJson(list);

            assertNotNull(json);
            assertTrue(json.contains("Team1"));
            assertTrue(json.contains("Team2"));
        }

        @Test
        @DisplayName("Should handle empty lists")
        void emptyLists() {
            List<PlayerStateData> playerList = Arrays.asList();
            List<TeamStateData> teamList = Arrays.asList();

            String playerJson = gson.toJson(playerList);
            String teamJson = gson.toJson(teamList);

            assertEquals("[]", playerJson);
            assertEquals("[]", teamJson);
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle special characters in team names")
        void specialCharactersInTeamName() {
            UUID teamId = UUID.randomUUID();
            UUID leaderId = UUID.randomUUID();
            TeamState team = new TeamState(teamId, "Team & <Special> \"Name\"", leaderId);

            TeamStateData data = TeamStateData.fromTeamState(team);
            String json = gson.toJson(data);
            TeamStateData restored = gson.fromJson(json, TeamStateData.class);

            assertEquals("Team & <Special> \"Name\"", restored.name);
        }

        @Test
        @DisplayName("Should handle special characters in player names")
        void specialCharactersInPlayerName() {
            UUID uuid = UUID.randomUUID();
            PlayerState player = new PlayerState(uuid, "Player_123 With Spaces");

            PlayerStateData data = PlayerStateData.fromPlayerState(player);
            String json = gson.toJson(data);
            PlayerStateData restored = gson.fromJson(json, PlayerStateData.class);

            assertEquals("Player_123 With Spaces", restored.name);
        }

        @Test
        @DisplayName("Should handle unicode characters")
        void unicodeCharacters() {
            UUID teamId = UUID.randomUUID();
            UUID leaderId = UUID.randomUUID();
            TeamState team = new TeamState(teamId, "队伍名称", leaderId);

            TeamStateData data = TeamStateData.fromTeamState(team);
            String json = gson.toJson(data);
            TeamStateData restored = gson.fromJson(json, TeamStateData.class);

            assertEquals("队伍名称", restored.name);
        }

        @Test
        @DisplayName("Should handle READY mode as non-persistent")
        void readyModeNotPersisted() {
            UUID uuid = UUID.randomUUID();
            PlayerState player = new PlayerState(uuid, "ReadyPlayer");
            player.setMode(PlayerMode.READY);

            PlayerStateData data = PlayerStateData.fromPlayerState(player);

            assertEquals("LOBBY", data.mode);
        }

        @Test
        @DisplayName("Should handle COUNTDOWN mode as non-persistent")
        void countdownModeNotPersisted() {
            UUID uuid = UUID.randomUUID();
            PlayerState player = new PlayerState(uuid, "CountdownPlayer");
            player.setMode(PlayerMode.COUNTDOWN);

            PlayerStateData data = PlayerStateData.fromPlayerState(player);

            assertEquals("LOBBY", data.mode);
        }

        @Test
        @DisplayName("Should handle DISCONNECTED mode as non-persistent")
        void disconnectedModeNotPersisted() {
            UUID uuid = UUID.randomUUID();
            PlayerState player = new PlayerState(uuid, "DisconnectedPlayer");
            player.setMode(PlayerMode.DISCONNECTED);

            PlayerStateData data = PlayerStateData.fromPlayerState(player);

            assertEquals("LOBBY", data.mode);
        }

        @Test
        @DisplayName("Should handle GRACE_EJECT mode as non-persistent")
        void graceEjectModeNotPersisted() {
            UUID uuid = UUID.randomUUID();
            PlayerState player = new PlayerState(uuid, "GraceEjectPlayer");
            player.setMode(PlayerMode.GRACE_EJECT);

            PlayerStateData data = PlayerStateData.fromPlayerState(player);

            assertEquals("LOBBY", data.mode);
        }
    }

    @Nested
    @DisplayName("Per-Player File Operations")
    class PerPlayerFileTests {

        @Test
        @DisplayName("Should serialize single player to JSON correctly")
        void singlePlayerJson() {
            UUID uuid = UUID.randomUUID();
            PlayerState player = new PlayerState(uuid, "SinglePlayer");
            player.setStarterWeaponOptionId("weapon_1");
            player.setPermaScore(100);
            player.setBalance(500);

            PlayerStateData data = PlayerStateData.fromPlayerState(player);
            String json = gson.toJson(data);

            assertNotNull(json);
            assertTrue(json.contains(uuid.toString()));
            assertTrue(json.contains("SinglePlayer"));
            assertTrue(json.contains("weapon_1"));
            assertTrue(json.contains("100"));
            assertTrue(json.contains("500"));
        }

        @Test
        @DisplayName("Should deserialize single player from JSON correctly")
        void singlePlayerFromJson() {
            UUID uuid = UUID.randomUUID();
            String json = """
                {
                  "uuid": "%s",
                  "name": "JsonPlayer",
                  "mode": "LOBBY",
                  "starterWeaponOptionId": "starter_bow",
                  "starterHelmetOptionId": null,
                  "cooldownUntilMillis": 0,
                  "permaScore": 42,
                  "balance": 999
                }
                """.formatted(uuid.toString());

            PlayerStateData data = gson.fromJson(json, PlayerStateData.class);
            PlayerState player = data.toPlayerState();

            assertEquals(uuid, player.getUuid());
            assertEquals("JsonPlayer", player.getName());
            assertEquals(PlayerMode.LOBBY, player.getMode());
            assertEquals("starter_bow", player.getStarterWeaponOptionId());
            assertNull(player.getStarterHelmetOptionId());
            assertEquals(42, player.getPermaScore());
            assertEquals(999, player.getBalance());
        }

        @Test
        @DisplayName("Should handle UUID as filename correctly")
        void uuidFilename() {
            UUID uuid = UUID.randomUUID();
            String filename = uuid.toString() + ".json";
            String extracted = filename.substring(0, filename.length() - 5); // Remove .json

            assertEquals(uuid, UUID.fromString(extracted));
        }

        @Test
        @DisplayName("Should correctly identify invalid UUID filename")
        void invalidUuidFilename() {
            String invalidFilename = "not-a-valid-uuid.json";
            String extracted = invalidFilename.substring(0, invalidFilename.length() - 5);

            assertThrows(IllegalArgumentException.class, () -> UUID.fromString(extracted));
        }

        @Test
        @DisplayName("Should handle complete player data round-trip")
        void completePlayerRoundTrip() {
            UUID uuid = UUID.randomUUID();
            PlayerState original = new PlayerState(uuid, "RoundTripPlayer");
            original.setStarterWeaponOptionId("sword_group");
            original.setStarterHelmetOptionId("helmet_group");
            original.setPermaScore(1500);
            original.setBalance(2500);
            original.setMode(PlayerMode.LOBBY);

            // Serialize
            PlayerStateData data = PlayerStateData.fromPlayerState(original);
            String json = gson.toJson(data);

            // Deserialize
            PlayerStateData restoredData = gson.fromJson(json, PlayerStateData.class);
            PlayerState restored = restoredData.toPlayerState();

            // Verify all fields
            assertEquals(original.getUuid(), restored.getUuid());
            assertEquals(original.getName(), restored.getName());
            assertEquals(original.getMode(), restored.getMode());
            assertEquals(original.getStarterWeaponOptionId(), restored.getStarterWeaponOptionId());
            assertEquals(original.getStarterHelmetOptionId(), restored.getStarterHelmetOptionId());
            assertEquals(original.getPermaScore(), restored.getPermaScore());
            assertEquals(original.getBalance(), restored.getBalance());
        }

        @Test
        @DisplayName("Should preserve permaScore and balance through serialization")
        void preserveEconomyData() {
            UUID uuid = UUID.randomUUID();
            PlayerState player = new PlayerState(uuid, "EconomyPlayer");
            player.setPermaScore(Integer.MAX_VALUE);
            player.setBalance(Integer.MAX_VALUE);

            PlayerStateData data = PlayerStateData.fromPlayerState(player);
            String json = gson.toJson(data);
            PlayerStateData restored = gson.fromJson(json, PlayerStateData.class);
            PlayerState restoredPlayer = restored.toPlayerState();

            assertEquals(Integer.MAX_VALUE, restoredPlayer.getPermaScore());
            assertEquals(Integer.MAX_VALUE, restoredPlayer.getBalance());
        }

        @Test
        @DisplayName("Should handle player with all null optional fields")
        void playerWithNullOptionalFields() {
            UUID uuid = UUID.randomUUID();
            PlayerState player = new PlayerState(uuid, "MinimalPlayer");
            // Don't set any optional fields

            PlayerStateData data = PlayerStateData.fromPlayerState(player);
            String json = gson.toJson(data);
            PlayerStateData restored = gson.fromJson(json, PlayerStateData.class);
            PlayerState restoredPlayer = restored.toPlayerState();

            assertEquals(uuid, restoredPlayer.getUuid());
            assertEquals("MinimalPlayer", restoredPlayer.getName());
            assertEquals(PlayerMode.LOBBY, restoredPlayer.getMode());
            assertNull(restoredPlayer.getStarterWeaponOptionId());
            assertNull(restoredPlayer.getStarterHelmetOptionId());
            assertEquals(0, restoredPlayer.getPermaScore());
            assertEquals(0, restoredPlayer.getBalance());
        }
    }
}
