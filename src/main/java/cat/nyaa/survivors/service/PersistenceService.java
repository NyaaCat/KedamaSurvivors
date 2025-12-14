package cat.nyaa.survivors.service;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;
import cat.nyaa.survivors.config.ConfigService;
import cat.nyaa.survivors.model.PlayerMode;
import cat.nyaa.survivors.model.PlayerState;
import cat.nyaa.survivors.model.TeamState;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Bukkit;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Handles persistence of player and team state to disk.
 * All file I/O is performed asynchronously to avoid blocking the main thread.
 */
public class PersistenceService {

    private static final String PLAYERS_FILE = "players.json";
    private static final String TEAMS_FILE = "teams.json";
    private static final DateTimeFormatter BACKUP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final KedamaSurvivorsPlugin plugin;
    private final ConfigService config;
    private final StateService state;
    private final Gson gson;

    private Path runtimePath;
    private Path backupPath;
    private int autoSaveTaskId = -1;
    private int backupTaskId = -1;

    public PersistenceService(KedamaSurvivorsPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigService();
        this.state = plugin.getStateService();
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .disableHtmlEscaping()
                .create();
    }

    /**
     * Initializes directories and loads all data.
     */
    public void initialize() {
        initializeDirectories();
        loadAll();
    }

    /**
     * Starts the auto-save and backup tasks.
     */
    public void start() {
        // Auto-save task
        int saveIntervalTicks = config.getSaveIntervalSeconds() * 20;
        if (saveIntervalTicks > 0) {
            autoSaveTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(
                    plugin, this::saveAllAsync, saveIntervalTicks, saveIntervalTicks
            ).getTaskId();
            plugin.getLogger().info("Auto-save task started (interval: " + config.getSaveIntervalSeconds() + "s)");
        }

        // Backup task (every 6 hours by default)
        int backupIntervalTicks = 6 * 60 * 60 * 20; // 6 hours in ticks
        backupTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin, this::createBackup, backupIntervalTicks, backupIntervalTicks
        ).getTaskId();
        plugin.getLogger().info("Backup task started (interval: 6 hours)");
    }

    /**
     * Stops all tasks and saves data synchronously.
     */
    public void stop() {
        if (autoSaveTaskId != -1) {
            Bukkit.getScheduler().cancelTask(autoSaveTaskId);
            autoSaveTaskId = -1;
        }
        if (backupTaskId != -1) {
            Bukkit.getScheduler().cancelTask(backupTaskId);
            backupTaskId = -1;
        }

        // Final synchronous save
        saveAllSync();
    }

    private void initializeDirectories() {
        File dataFolder = plugin.getDataFolder();
        runtimePath = dataFolder.toPath().resolve(config.getRuntimePath());
        backupPath = dataFolder.toPath().resolve("backups");

        try {
            Files.createDirectories(runtimePath);
            Files.createDirectories(backupPath);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create data directories", e);
        }
    }

    // ==================== Load Operations ====================

    /**
     * Loads all persisted data (players and teams).
     */
    public void loadAll() {
        loadPlayers();
        loadTeams();
        linkTeamMembers();
    }

    private void loadPlayers() {
        Path file = runtimePath.resolve(PLAYERS_FILE);
        if (!Files.exists(file)) {
            plugin.getLogger().info("No player data file found, starting fresh");
            return;
        }

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Type type = new TypeToken<List<PlayerStateData>>() {}.getType();
            List<PlayerStateData> dataList = gson.fromJson(reader, type);

            if (dataList == null) {
                plugin.getLogger().warning("Player data file is empty or invalid");
                return;
            }

            int loaded = 0;
            for (PlayerStateData data : dataList) {
                PlayerState player = data.toPlayerState();
                state.registerPlayer(player);
                loaded++;
            }

            plugin.getLogger().info("Loaded " + loaded + " player states from disk");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load player data", e);
            handleCorruptFile(file);
        }
    }

    private void loadTeams() {
        Path file = runtimePath.resolve(TEAMS_FILE);
        if (!Files.exists(file)) {
            plugin.getLogger().info("No team data file found, starting fresh");
            return;
        }

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Type type = new TypeToken<List<TeamStateData>>() {}.getType();
            List<TeamStateData> dataList = gson.fromJson(reader, type);

            if (dataList == null) {
                plugin.getLogger().warning("Team data file is empty or invalid");
                return;
            }

            int loaded = 0;
            for (TeamStateData data : dataList) {
                TeamState team = data.toTeamState();
                state.registerTeam(team);
                loaded++;
            }

            plugin.getLogger().info("Loaded " + loaded + " team states from disk");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load team data", e);
            handleCorruptFile(file);
        }
    }

    /**
     * Links team members' teamId after loading both players and teams.
     */
    private void linkTeamMembers() {
        for (TeamState team : state.getAllTeams()) {
            for (UUID memberId : team.getMembers()) {
                state.getPlayer(memberId).ifPresent(player -> {
                    player.setTeamId(team.getTeamId());
                });
            }
        }
    }

    private void handleCorruptFile(Path file) {
        try {
            Path corruptPath = file.resolveSibling(file.getFileName() + ".corrupt." + System.currentTimeMillis());
            Files.move(file, corruptPath, StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogger().warning("Corrupt file renamed to: " + corruptPath.getFileName());
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to rename corrupt file", e);
        }
    }

    // ==================== Save Operations ====================

    /**
     * Saves all data asynchronously.
     */
    public CompletableFuture<Void> saveAllAsync() {
        return CompletableFuture.runAsync(this::saveAllSync);
    }

    /**
     * Saves all data synchronously.
     */
    public void saveAllSync() {
        savePlayers();
        saveTeams();
        if (config.isVerbose()) {
            plugin.getLogger().info("All data saved to disk");
        }
    }

    /**
     * Saves a single player's state asynchronously.
     */
    public CompletableFuture<Void> savePlayerAsync(UUID playerId) {
        return CompletableFuture.runAsync(() -> {
            // We save all players since we use a single file
            // This could be optimized to use per-player files if needed
            savePlayers();
        });
    }

    private void savePlayers() {
        List<PlayerStateData> dataList = state.getAllPlayers().stream()
                .map(PlayerStateData::fromPlayerState)
                .collect(Collectors.toList());

        Path file = runtimePath.resolve(PLAYERS_FILE);
        writeJsonFile(file, dataList);
    }

    private void saveTeams() {
        List<TeamStateData> dataList = state.getAllTeams().stream()
                .map(TeamStateData::fromTeamState)
                .collect(Collectors.toList());

        Path file = runtimePath.resolve(TEAMS_FILE);
        writeJsonFile(file, dataList);
    }

    private void writeJsonFile(Path file, Object data) {
        Path tempFile = file.resolveSibling(file.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
            gson.toJson(data, writer);
            writer.flush();
            // Atomic rename
            Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save data to " + file.getFileName(), e);
            // Clean up temp file
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException ignored) {}
        }
    }

    // ==================== Backup Operations ====================

    /**
     * Creates a backup of all data files.
     */
    public void createBackup() {
        String timestamp = LocalDateTime.now().format(BACKUP_FORMAT);
        Path backupDir = backupPath.resolve("backup_" + timestamp);

        try {
            Files.createDirectories(backupDir);

            // Copy runtime files to backup
            Path playersFile = runtimePath.resolve(PLAYERS_FILE);
            Path teamsFile = runtimePath.resolve(TEAMS_FILE);

            if (Files.exists(playersFile)) {
                Files.copy(playersFile, backupDir.resolve(PLAYERS_FILE), StandardCopyOption.REPLACE_EXISTING);
            }
            if (Files.exists(teamsFile)) {
                Files.copy(teamsFile, backupDir.resolve(TEAMS_FILE), StandardCopyOption.REPLACE_EXISTING);
            }

            plugin.getLogger().info("Backup created: " + backupDir.getFileName());

            // Rotate old backups
            rotateBackups();
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to create backup", e);
        }
    }

    private void rotateBackups() {
        int maxBackups = 10; // Could be made configurable

        try (Stream<Path> backups = Files.list(backupPath)) {
            List<Path> sortedBackups = backups
                    .filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().startsWith("backup_"))
                    .sorted(Comparator.comparing(Path::getFileName).reversed())
                    .collect(Collectors.toList());

            // Delete backups exceeding max count
            for (int i = maxBackups; i < sortedBackups.size(); i++) {
                Path oldBackup = sortedBackups.get(i);
                deleteDirectory(oldBackup);
                plugin.getLogger().info("Deleted old backup: " + oldBackup.getFileName());
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to rotate backups", e);
        }
    }

    private void deleteDirectory(Path dir) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            plugin.getLogger().warning("Failed to delete: " + path);
                        }
                    });
        }
    }

    // ==================== Data Transfer Objects ====================

    /**
     * Data transfer object for player state serialization.
     */
    public static class PlayerStateData {
        public String uuid;
        public String name;
        public String mode;
        public String starterWeaponOptionId;
        public String starterHelmetOptionId;
        public long cooldownUntilMillis;

        public static PlayerStateData fromPlayerState(PlayerState player) {
            PlayerStateData data = new PlayerStateData();
            data.uuid = player.getUuid().toString();
            data.name = player.getName();

            // Only persist LOBBY or COOLDOWN modes
            PlayerMode currentMode = player.getMode();
            if (currentMode == PlayerMode.COOLDOWN && player.isOnCooldown()) {
                data.mode = PlayerMode.COOLDOWN.name();
                data.cooldownUntilMillis = player.getCooldownUntilMillis();
            } else {
                data.mode = PlayerMode.LOBBY.name();
                data.cooldownUntilMillis = 0;
            }

            data.starterWeaponOptionId = player.getStarterWeaponOptionId();
            data.starterHelmetOptionId = player.getStarterHelmetOptionId();

            return data;
        }

        public PlayerState toPlayerState() {
            PlayerState player = new PlayerState(UUID.fromString(uuid), name);

            // Restore mode (only LOBBY or COOLDOWN)
            if ("COOLDOWN".equals(mode) && cooldownUntilMillis > System.currentTimeMillis()) {
                player.setMode(PlayerMode.COOLDOWN);
                player.setCooldownUntilMillis(cooldownUntilMillis);
            } else {
                player.setMode(PlayerMode.LOBBY);
            }

            player.setStarterWeaponOptionId(starterWeaponOptionId);
            player.setStarterHelmetOptionId(starterHelmetOptionId);

            return player;
        }
    }

    /**
     * Data transfer object for team state serialization.
     */
    public static class TeamStateData {
        public String teamId;
        public String name;
        public String leaderId;
        public List<String> members;
        public long createdAtMillis;

        public static TeamStateData fromTeamState(TeamState team) {
            TeamStateData data = new TeamStateData();
            data.teamId = team.getTeamId().toString();
            data.name = team.getName();
            data.leaderId = team.getLeaderId().toString();
            data.members = team.getMembers().stream()
                    .map(UUID::toString)
                    .collect(Collectors.toList());
            data.createdAtMillis = team.getCreatedAtMillis();
            return data;
        }

        public TeamState toTeamState() {
            UUID teamUuid = UUID.fromString(teamId);
            UUID leaderUuid = UUID.fromString(leaderId);

            TeamState team = new TeamState(teamUuid, name, leaderUuid);

            // Add members (leader is already added in constructor)
            for (String memberStr : members) {
                UUID memberId = UUID.fromString(memberStr);
                if (!memberId.equals(leaderUuid)) {
                    team.addMember(memberId);
                }
            }

            return team;
        }
    }
}
