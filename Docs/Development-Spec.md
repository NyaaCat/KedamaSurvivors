# KedamaSurvivors - Technical Development Specification

## 1. Overview

KedamaSurvivors is a standalone Paper/Spigot plugin implementing a roguelite "run" game mode inspired by Vampire Survivors and CS Arms Race. The plugin manages all game state internally while interacting with external plugins (RPGItems, Multiverse, EssentialsX) via configurable command templates.

### 1.1 Target Environment

| Requirement | Value |
|-------------|-------|
| Server | Paper 1.21.8 (Spigot API compatible) |
| Java | 21+ |
| Build | Gradle with paperweight-userdev |
| Dependencies | None (standalone) |

### 1.2 Design Principles

1. **Command-Based Integration**: All external plugin interaction via command templates with placeholders
2. **NBT-First Item Handling**: Items stored as NBT data, not plugin dependencies
3. **Thread Safety**: Async computation where possible, main thread for Bukkit API only
4. **Hot Reload**: All configuration changes without server restart
5. **Internationalization**: All player-facing text translatable

---

## 2. Core Data Models

### 2.1 PlayerState

In-memory representation of a player's game state.

```java
public class PlayerState {
    // Identity
    private final UUID uuid;
    private String name;

    // State Machine
    private PlayerMode mode; // LOBBY, READY, COUNTDOWN, IN_RUN, COOLDOWN, GRACE_EJECT, DISCONNECTED

    // Team & Run
    private UUID teamId;           // null if solo
    private UUID runId;            // null if not in run
    private UUID lastTeamId;       // For reconnection matching

    // Timing
    private long cooldownUntilMillis;      // 0 if none
    private long disconnectedAtMillis;     // 0 if connected
    private long invulnerableUntilMillis;  // 0 if none

    // Ready State
    private boolean ready;

    // Starter Selections (before run)
    private String starterWeaponOptionId;  // null if not selected
    private String starterHelmetOptionId;  // null if not selected

    // Equipment Tracking (authoritative during run)
    private String weaponGroup;
    private int weaponLevel;
    private String helmetGroup;
    private int helmetLevel;

    // XP System
    private int xpProgress;        // 0..xpRequired
    private int xpHeld;            // Buffered XP during upgrade pending
    private int xpRequired;        // Threshold for next level
    private boolean upgradePending;

    // Overflow (when at max level)
    private int overflowXpAccumulated;

    // Economy (coin is vanilla item, perma-score is scoreboard)
    private int permaScore;        // Synced to scoreboard objective

    // Pending Rewards
    private final List<ItemStack> pendingRewards = new ArrayList<>();

    // Computed Properties
    public int getPlayerLevel() {
        return weaponLevel + helmetLevel;
    }

    public boolean isAtMaxLevel() {
        // Determined by checking if both weapon and helmet have no next level
        return weaponAtMax && helmetAtMax;
    }

    public boolean isWithinGracePeriod(long graceMillis) {
        if (disconnectedAtMillis == 0) return false;
        return System.currentTimeMillis() - disconnectedAtMillis < graceMillis;
    }
}
```

### 2.2 PlayerMode Enum

```java
public enum PlayerMode {
    LOBBY,          // In prep area, not queued
    READY,          // Marked ready, waiting for team/countdown
    COUNTDOWN,      // Countdown in progress
    IN_RUN,         // Active in combat world
    COOLDOWN,       // Died, waiting for cooldown to expire
    GRACE_EJECT,    // Global switch disabled, being ejected
    DISCONNECTED    // Disconnected during run, within grace period
}
```

### 2.3 TeamState

```java
public class TeamState {
    private final UUID teamId;
    private String name;
    private UUID ownerUuid;

    private final Set<UUID> memberUuids = new HashSet<>();
    private final Map<UUID, Long> invites = new HashMap<>(); // uuid -> expiryMillis
    private final Set<UUID> readyMembers = new HashSet<>();

    // Disconnect tracking
    private final Map<UUID, Long> disconnectedMembers = new HashMap<>(); // uuid -> disconnectedAt

    // Active run
    private UUID activeRunId;

    // Configuration
    private static final int MAX_TEAM_SIZE = 5; // Configurable
    private static final long INVITE_EXPIRY_MS = 60_000; // 1 minute

    public boolean isAllReady() {
        return readyMembers.containsAll(getOnlineMembers());
    }

    public Set<UUID> getOnlineMembers() {
        return memberUuids.stream()
            .filter(uuid -> !disconnectedMembers.containsKey(uuid))
            .collect(Collectors.toSet());
    }

    public boolean isWiped() {
        // All members either dead or disconnected past grace
        // Implementation checks PlayerState for each member
    }

    public int getOnlineMemberCount() {
        return getOnlineMembers().size();
    }
}
```

### 2.4 RunState

```java
public class RunState {
    private final UUID runId;
    private final UUID teamId;          // Solo players get pseudo-team
    private final String worldName;
    private final Location spawnOrigin;

    private long startedAtMillis;
    private RunStatus status;           // STARTING, ACTIVE, ENDING

    // Spawn System Runtime
    private final Map<UUID, Integer> playerSpawnCounters = new HashMap<>();
    private int worldActiveMobCount;
    private final Queue<SpawnPlan> spawnPlanQueue = new ConcurrentLinkedQueue<>();

    // Merchant Runtime
    private final Set<UUID> activeMerchantEntityIds = new HashSet<>();
    private long nextMerchantRotationMillis;

    // Statistics
    private int totalKills;
    private int totalXpEarned;
    private int totalCoinEarned;

    public long getRunDurationSeconds() {
        return (System.currentTimeMillis() - startedAtMillis) / 1000;
    }
}
```

### 2.5 EquipmentGroup

```java
public class EquipmentGroup {
    private final String groupId;
    private final String displayName;
    private final EquipmentSlot slot;  // WEAPON or HELMET

    // Level -> List of item template IDs
    private final Map<Integer, List<String>> levelItems = new HashMap<>();

    public int getMaxLevel() {
        return levelItems.keySet().stream().mapToInt(i -> i).max().orElse(1);
    }

    public boolean hasNextLevel(int currentLevel) {
        return levelItems.containsKey(currentLevel + 1);
    }

    public String getRandomItemAtLevel(int level) {
        List<String> items = levelItems.get(level);
        if (items == null || items.isEmpty()) return null;
        return items.get(ThreadLocalRandom.current().nextInt(items.size()));
    }
}
```

### 2.6 ItemTemplate

```java
public class ItemTemplate {
    private final String templateId;
    private final String nbtString;      // Base64 or SNBT
    private final ItemStack cachedItem;  // Parsed on load

    // Metadata for tracking
    private final String group;
    private final int level;
    private final EquipmentSlot slot;
}
```

---

## 3. Scoreboard Integration

### 3.1 Perma-Score Objective

The plugin creates and manages a scoreboard objective for cross-server perma-score tracking.

```java
public class ScoreboardService {
    private static final String PERMA_SCORE_OBJECTIVE = "vrs_perma";
    private static final String PERMA_SCORE_DISPLAY = "永久积分";

    public void ensureObjectiveExists() {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Objective obj = board.getObjective(PERMA_SCORE_OBJECTIVE);
        if (obj == null) {
            obj = board.registerNewObjective(
                PERMA_SCORE_OBJECTIVE,
                Criteria.DUMMY,
                Component.text(PERMA_SCORE_DISPLAY)
            );
        }
    }

    public void setPermaScore(Player player, int score) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Objective obj = board.getObjective(PERMA_SCORE_OBJECTIVE);
        if (obj != null) {
            obj.getScoreFor(player).setScore(score);
        }
    }

    public int getPermaScore(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Objective obj = board.getObjective(PERMA_SCORE_OBJECTIVE);
        if (obj != null) {
            return obj.getScoreFor(player).getScore();
        }
        return 0;
    }
}
```

### 3.2 Sidebar Display

The sidebar shows real-time player statistics during gameplay.

```java
public class SidebarManager {
    private static final String SIDEBAR_OBJECTIVE = "vrs_sidebar";

    private final Map<UUID, Scoreboard> playerScoreboards = new HashMap<>();

    public void showSidebar(Player player, PlayerState state) {
        Scoreboard board = getOrCreateScoreboard(player);
        Objective sidebar = board.getObjective(SIDEBAR_OBJECTIVE);

        if (sidebar == null) {
            sidebar = board.registerNewObjective(
                SIDEBAR_OBJECTIVE,
                Criteria.DUMMY,
                i18n.get("sidebar.title") // §6§l武生
            );
            sidebar.setDisplaySlot(DisplaySlot.SIDEBAR);
        }

        // Clear old scores and set new ones
        updateSidebarLines(player, sidebar, state);
    }

    private void updateSidebarLines(Player player, Objective sidebar, PlayerState state) {
        List<Component> lines = buildSidebarLines(state);
        // Score values determine order (higher = higher on screen)
        int score = lines.size();
        for (Component line : lines) {
            // Use unique invisible characters to prevent merging
            sidebar.getScoreFor(formatLine(line, score)).setScore(score);
            score--;
        }
    }

    private List<Component> buildSidebarLines(PlayerState state) {
        List<Component> lines = new ArrayList<>();

        // Weapon Level
        lines.add(i18n.format("sidebar.weapon_level", state.getWeaponLevel()));

        // Helmet Level
        lines.add(i18n.format("sidebar.helmet_level", state.getHelmetLevel()));

        // XP Progress Bar
        lines.add(buildXpBar(state));

        // Coin Count (from inventory)
        lines.add(i18n.format("sidebar.coins", countCoins(state)));

        // Perma Score
        lines.add(i18n.format("sidebar.perma_score", state.getPermaScore()));

        // Team Info (if applicable)
        if (state.getTeamId() != null) {
            TeamState team = stateService.getTeam(state.getTeamId());
            lines.add(i18n.format("sidebar.team", team.getName(),
                team.getOnlineMemberCount(), team.getMemberUuids().size()));
        }

        // Run Time
        if (state.getRunId() != null) {
            RunState run = stateService.getRun(state.getRunId());
            lines.add(i18n.format("sidebar.time", formatDuration(run.getRunDurationSeconds())));
        }

        return lines;
    }

    private Component buildXpBar(PlayerState state) {
        int progress = state.getXpProgress();
        int required = state.getXpRequired();
        int percentage = required > 0 ? (progress * 100 / required) : 0;
        int filled = percentage / 10;

        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            bar.append(i < filled ? "▰" : "▱");
        }

        return i18n.format("sidebar.xp", bar.toString(), percentage);
    }
}
```

---

## 4. XP System & Upgrade Flow

### 4.1 XP Award Algorithm

```java
public class RewardService {

    public void awardKillXp(Player killer, int enemyLevel, Location deathLocation) {
        PlayerState state = stateService.getPlayerState(killer.getUniqueId());

        // Calculate base XP
        int baseXp = config.getXpBase() + (enemyLevel * config.getXpPerLevel());

        // Award to killer
        applyXp(state, baseXp);

        // Share with nearby players
        double shareRadius = config.getXpShareRadius();
        double sharePercent = config.getXpSharePercent();

        deathLocation.getWorld().getNearbyPlayers(deathLocation, shareRadius).stream()
            .filter(p -> !p.equals(killer))
            .filter(p -> isInSameRun(p, killer))
            .forEach(p -> {
                PlayerState nearbyState = stateService.getPlayerState(p.getUniqueId());
                int shareXp = (int) (baseXp * sharePercent);
                applyXp(nearbyState, shareXp);
            });
    }

    private void applyXp(PlayerState state, int delta) {
        // Check if at max level for both slots
        if (state.isAtMaxLevel()) {
            applyOverflowXp(state, delta);
            return;
        }

        // If upgrade pending, buffer XP
        if (state.isUpgradePending()) {
            state.setXpHeld(state.getXpHeld() + delta);
            return;
        }

        // Apply to progress
        state.setXpProgress(state.getXpProgress() + delta);

        // Check for level up
        if (state.getXpProgress() >= state.getXpRequired()) {
            int overflow = state.getXpProgress() - state.getXpRequired();
            state.setXpProgress(state.getXpRequired());
            state.setXpHeld(state.getXpHeld() + overflow);
            state.setUpgradePending(true);

            // Open upgrade UI
            upgradeService.showUpgradePrompt(state);
        }

        // Update sidebar
        sidebarManager.updateSidebar(state);
    }

    private void applyOverflowXp(PlayerState state, int delta) {
        state.setOverflowXpAccumulated(state.getOverflowXpAccumulated() + delta);

        int xpPerScore = config.getOverflowXpPerPermaScore();
        while (state.getOverflowXpAccumulated() >= xpPerScore) {
            state.setOverflowXpAccumulated(state.getOverflowXpAccumulated() - xpPerScore);
            state.setPermaScore(state.getPermaScore() + 1);

            // Update scoreboard
            Player player = Bukkit.getPlayer(state.getUuid());
            if (player != null) {
                scoreboardService.setPermaScore(player, state.getPermaScore());
                i18n.send(player, "info.perma_score_gained", 1);
            }
        }
    }
}
```

### 4.2 Upgrade Resolution

```java
public class UpgradeService {

    public void showUpgradePrompt(PlayerState state) {
        Player player = Bukkit.getPlayer(state.getUuid());
        if (player == null) return;

        // Create upgrade GUI
        Inventory gui = Bukkit.createInventory(null, 9, i18n.get("gui.upgrade_title"));

        // Weapon upgrade option (slot 2)
        ItemStack weaponOption = createUpgradeOption(state, EquipmentSlot.WEAPON);
        gui.setItem(2, weaponOption);

        // Helmet upgrade option (slot 6)
        ItemStack helmetOption = createUpgradeOption(state, EquipmentSlot.HELMET);
        gui.setItem(6, helmetOption);

        player.openInventory(gui);
    }

    public void processUpgradeChoice(PlayerState state, EquipmentSlot slot) {
        Player player = Bukkit.getPlayer(state.getUuid());
        if (player == null) return;

        String group;
        int currentLevel;

        if (slot == EquipmentSlot.WEAPON) {
            group = state.getWeaponGroup();
            currentLevel = state.getWeaponLevel();
        } else {
            group = state.getHelmetGroup();
            currentLevel = state.getHelmetLevel();
        }

        EquipmentGroup equipGroup = configService.getEquipmentGroup(group);

        if (equipGroup.hasNextLevel(currentLevel)) {
            // Upgrade to next level
            int nextLevel = currentLevel + 1;
            String templateId = equipGroup.getRandomItemAtLevel(nextLevel);

            // Replace item
            replaceEquipment(player, state, slot, templateId, nextLevel);

            // Update state
            if (slot == EquipmentSlot.WEAPON) {
                state.setWeaponLevel(nextLevel);
            } else {
                state.setHelmetLevel(nextLevel);
            }
        } else {
            // At max level - grant perma-score reward
            int reward = config.getMaxLevelUpgradeReward();
            state.setPermaScore(state.getPermaScore() + reward);
            scoreboardService.setPermaScore(player, state.getPermaScore());
            i18n.send(player, "info.max_level_reward", reward);
        }

        // Resolve upgrade pending
        state.setUpgradePending(false);
        state.setXpProgress(0);

        // Recalculate XP required for next level
        state.setXpRequired(calculateXpRequired(state.getPlayerLevel()));

        // Apply held XP (may trigger another upgrade)
        int held = state.getXpHeld();
        state.setXpHeld(0);

        if (held > 0) {
            applyXp(state, held);
        }

        // Close GUI
        player.closeInventory();
    }

    private void replaceEquipment(Player player, PlayerState state,
                                   EquipmentSlot slot, String templateId, int level) {
        ItemTemplate template = configService.getItemTemplate(templateId);
        ItemStack newItem = template.getCachedItem().clone();

        // Add PDC marker for tracking
        ItemMeta meta = newItem.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(VRS_TEMPLATE_KEY, PersistentDataType.STRING, templateId);
        pdc.set(VRS_LEVEL_KEY, PersistentDataType.INTEGER, level);
        newItem.setItemMeta(meta);

        PlayerInventory inv = player.getInventory();

        if (slot == EquipmentSlot.WEAPON) {
            // Replace main hand
            inv.setItemInMainHand(newItem);
        } else {
            // Replace helmet
            inv.setHelmet(newItem);
        }
    }
}
```

---

## 5. Team System

### 5.1 Team Management

```java
public class TeamService {

    public TeamState createTeam(Player owner, String name) {
        PlayerState ownerState = stateService.getPlayerState(owner.getUniqueId());

        // Validation
        if (ownerState.getTeamId() != null) {
            throw new GameException("error.already_in_team");
        }

        if (ownerState.getMode() != PlayerMode.LOBBY) {
            throw new GameException("error.not_in_lobby");
        }

        // Create team
        TeamState team = new TeamState(UUID.randomUUID(), name, owner.getUniqueId());
        team.getMemberUuids().add(owner.getUniqueId());

        // Update player state
        ownerState.setTeamId(team.getTeamId());

        // Store
        stateService.registerTeam(team);

        return team;
    }

    public void invitePlayer(TeamState team, Player inviter, Player target) {
        // Validation
        if (!team.getOwnerUuid().equals(inviter.getUniqueId())) {
            throw new GameException("error.not_team_owner");
        }

        if (team.getMemberUuids().size() >= config.getMaxTeamSize()) {
            throw new GameException("error.team_full");
        }

        PlayerState targetState = stateService.getPlayerState(target.getUniqueId());
        if (targetState.getTeamId() != null) {
            throw new GameException("error.player_already_in_team");
        }

        // Add invite
        long expiry = System.currentTimeMillis() + config.getInviteExpiryMs();
        team.getInvites().put(target.getUniqueId(), expiry);

        // Notify
        i18n.send(target, "team.invite_received", team.getName(), inviter.getName());
        i18n.sendClickable(target, "team.click_to_join",
            "/vrs team join " + team.getName());
    }

    public void joinTeam(Player player, TeamState team) {
        PlayerState state = stateService.getPlayerState(player.getUniqueId());

        // Validation
        Long inviteExpiry = team.getInvites().get(player.getUniqueId());
        if (inviteExpiry == null || inviteExpiry < System.currentTimeMillis()) {
            throw new GameException("error.no_valid_invite");
        }

        if (team.getMemberUuids().size() >= config.getMaxTeamSize()) {
            throw new GameException("error.team_full");
        }

        // Join
        team.getInvites().remove(player.getUniqueId());
        team.getMemberUuids().add(player.getUniqueId());
        state.setTeamId(team.getTeamId());

        // Notify team
        broadcastToTeam(team, "team.player_joined", player.getName());
    }

    public void handleDisconnect(Player player) {
        PlayerState state = stateService.getPlayerState(player.getUniqueId());

        if (state.getMode() == PlayerMode.IN_RUN) {
            state.setDisconnectedAtMillis(System.currentTimeMillis());
            state.setMode(PlayerMode.DISCONNECTED);

            if (state.getTeamId() != null) {
                TeamState team = stateService.getTeam(state.getTeamId());
                team.getDisconnectedMembers().put(player.getUniqueId(),
                    System.currentTimeMillis());

                // Check for team wipe
                checkTeamWipe(team);
            }
        }
    }

    public void handleReconnect(Player player) {
        PlayerState state = stateService.getPlayerState(player.getUniqueId());

        if (state.getMode() == PlayerMode.DISCONNECTED) {
            long graceMs = config.getDisconnectGraceMs();

            if (state.isWithinGracePeriod(graceMs)) {
                // Restore to run
                state.setDisconnectedAtMillis(0);
                state.setMode(PlayerMode.IN_RUN);

                if (state.getTeamId() != null) {
                    TeamState team = stateService.getTeam(state.getTeamId());
                    team.getDisconnectedMembers().remove(player.getUniqueId());
                }

                // Teleport back to run
                RunState run = stateService.getRun(state.getRunId());
                teleportToRun(player, run);

                i18n.send(player, "info.reconnected");
            } else {
                // Grace expired - treat as death
                handleDeathPenalty(player, state);
            }
        }
    }

    private void checkTeamWipe(TeamState team) {
        boolean anyAlive = team.getMemberUuids().stream()
            .map(stateService::getPlayerState)
            .anyMatch(state ->
                state.getMode() == PlayerMode.IN_RUN &&
                !state.isWithinGracePeriod(config.getDisconnectGraceMs())
            );

        if (!anyAlive) {
            // Team wipe - end run for all
            endTeamRun(team, "team.all_dead");
        }
    }
}
```

### 5.2 Disconnect Grace Period Checker

```java
public class DisconnectChecker implements Runnable {

    @Override
    public void run() {
        long now = System.currentTimeMillis();
        long graceMs = config.getDisconnectGraceMs();

        stateService.getAllPlayers().stream()
            .filter(state -> state.getMode() == PlayerMode.DISCONNECTED)
            .filter(state -> now - state.getDisconnectedAtMillis() >= graceMs)
            .forEach(state -> {
                // Grace expired
                handleGraceExpired(state);
            });
    }

    private void handleGraceExpired(PlayerState state) {
        // Apply death penalty
        state.setXpProgress(0);
        state.setXpHeld(0);
        state.setUpgradePending(false);
        state.setWeaponGroup(null);
        state.setWeaponLevel(0);
        state.setHelmetGroup(null);
        state.setHelmetLevel(0);

        // Set cooldown
        state.setCooldownUntilMillis(System.currentTimeMillis() +
            config.getDeathCooldownMs());
        state.setMode(PlayerMode.COOLDOWN);

        // Remove from team tracking
        if (state.getTeamId() != null) {
            TeamState team = stateService.getTeam(state.getTeamId());
            team.getDisconnectedMembers().remove(state.getUuid());

            // Check team wipe
            teamService.checkTeamWipe(team);
        }
    }
}
```

---

## 6. Death & Respawn System

### 6.1 Death Handler

```java
public class DeathHandler implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        PlayerState state = stateService.getPlayerState(player.getUniqueId());

        if (state.getMode() != PlayerMode.IN_RUN) return;

        // Cancel drops
        event.getDrops().clear();
        event.setDroppedExp(0);

        // Check for team respawn
        if (state.getTeamId() != null) {
            TeamState team = stateService.getTeam(state.getTeamId());
            Optional<PlayerState> anchor = findRespawnAnchor(team, state);

            if (anchor.isPresent()) {
                // Respawn to teammate
                scheduleTeamRespawn(player, state, anchor.get());
                return;
            }
        }

        // No respawn available - apply death penalty
        applyDeathPenalty(player, state);
    }

    private Optional<PlayerState> findRespawnAnchor(TeamState team, PlayerState dying) {
        return team.getMemberUuids().stream()
            .filter(uuid -> !uuid.equals(dying.getUuid()))
            .map(stateService::getPlayerState)
            .filter(state -> state.getMode() == PlayerMode.IN_RUN)
            .filter(state -> Bukkit.getPlayer(state.getUuid()) != null)
            .findFirst();
    }

    private void scheduleTeamRespawn(Player player, PlayerState state,
                                      PlayerState anchor) {
        Player anchorPlayer = Bukkit.getPlayer(anchor.getUuid());

        // Respawn after short delay
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Teleport near anchor
            Location respawnLoc = findSafeLocationNear(anchorPlayer.getLocation());
            player.spigot().respawn();
            player.teleport(respawnLoc);

            // Apply invulnerability
            long invulMs = config.getRespawnInvulnerabilityMs();
            state.setInvulnerableUntilMillis(System.currentTimeMillis() + invulMs);

            // Visual effect
            player.addPotionEffect(new PotionEffect(
                PotionEffectType.GLOWING,
                (int) (invulMs / 50), // ticks
                0,
                false,
                false
            ));

            i18n.send(player, "info.respawned_to_team", anchorPlayer.getName());

        }, 20L); // 1 second delay
    }

    private void applyDeathPenalty(Player player, PlayerState state) {
        // Clear equipment
        player.getInventory().setItemInMainHand(null);
        player.getInventory().setHelmet(null);

        // Reset run state
        state.setXpProgress(0);
        state.setXpHeld(0);
        state.setUpgradePending(false);
        state.setWeaponGroup(null);
        state.setWeaponLevel(0);
        state.setHelmetGroup(null);
        state.setHelmetLevel(0);
        state.setOverflowXpAccumulated(0);

        // Keep coins and consumables (in inventory)
        // Perma-score is always kept (scoreboard)

        // Set cooldown
        state.setCooldownUntilMillis(System.currentTimeMillis() +
            config.getDeathCooldownMs());
        state.setMode(PlayerMode.COOLDOWN);
        state.setRunId(null);

        // Teleport to prep area
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            player.spigot().respawn();
            teleportService.teleportToPrep(player);
            i18n.send(player, "info.died_cooldown",
                config.getDeathCooldownMs() / 1000);
        }, 20L);

        // Check team wipe
        if (state.getTeamId() != null) {
            TeamState team = stateService.getTeam(state.getTeamId());
            teamService.checkTeamWipe(team);
        }
    }
}
```

### 6.2 Invulnerability Handler

```java
public class InvulnerabilityHandler implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        PlayerState state = stateService.getPlayerState(player.getUniqueId());

        if (state.getInvulnerableUntilMillis() > System.currentTimeMillis()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // Prevent invulnerable players from dealing damage (optional)
        Player damager = getDamagerPlayer(event);
        if (damager == null) return;

        PlayerState state = stateService.getPlayerState(damager.getUniqueId());

        if (config.isInvulnerableCannotDealDamage() &&
            state.getInvulnerableUntilMillis() > System.currentTimeMillis()) {
            event.setCancelled(true);
        }
    }
}
```

---

## 7. Enemy Spawning System

### 7.1 Spawn Loop

```java
public class SpawnerService {

    private final ScheduledExecutorService asyncExecutor =
        Executors.newSingleThreadScheduledExecutor();

    public void startSpawnLoop() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::executeSpawnTick,
            20L, config.getSpawnTickInterval());
    }

    private void executeSpawnTick() {
        // Phase A: Main thread snapshot
        Map<UUID, SpawnContext> contexts = collectSpawnContexts();

        // Phase B: Async planning
        asyncExecutor.submit(() -> {
            List<SpawnPlan> plans = planSpawns(contexts);

            // Phase C: Main thread execution
            Bukkit.getScheduler().runTask(plugin, () -> executeSpawnPlans(plans));
        });
    }

    private Map<UUID, SpawnContext> collectSpawnContexts() {
        Map<UUID, SpawnContext> contexts = new HashMap<>();

        stateService.getActiveRuns().forEach(run -> {
            World world = Bukkit.getWorld(run.getWorldName());
            if (world == null) return;

            run.getParticipants().forEach(uuid -> {
                Player player = Bukkit.getPlayer(uuid);
                if (player == null || player.getWorld() != world) return;

                PlayerState state = stateService.getPlayerState(uuid);
                if (state.getMode() != PlayerMode.IN_RUN) return;

                // Count nearby VRS mobs
                int nearbyMobs = countVrsMobsNear(player.getLocation(),
                    config.getMobCountRadius());

                // Calculate average player level in radius
                double avgLevel = calculateAveragePlayerLevel(
                    player.getLocation(), config.getLevelSamplingRadius());

                int playerCount = countPlayersInRadius(
                    player.getLocation(), config.getLevelSamplingRadius());

                contexts.put(uuid, new SpawnContext(
                    player.getLocation().clone(),
                    state.getPlayerLevel(),
                    avgLevel,
                    playerCount,
                    nearbyMobs,
                    run.getRunDurationSeconds()
                ));
            });
        });

        return contexts;
    }

    private List<SpawnPlan> planSpawns(Map<UUID, SpawnContext> contexts) {
        List<SpawnPlan> plans = new ArrayList<>();

        contexts.forEach((uuid, ctx) -> {
            int currentMobs = ctx.nearbyMobs();
            int targetMobs = config.getTargetMobsPerPlayer();
            int toSpawn = Math.min(
                targetMobs - currentMobs,
                config.getMaxSpawnsPerPlayerPerTick()
            );

            if (toSpawn <= 0) return;

            // Calculate enemy level
            int enemyLevel = calculateEnemyLevel(ctx);

            for (int i = 0; i < toSpawn; i++) {
                // Select archetype
                EnemyArchetype archetype = selectArchetype();

                // Select spawn position
                Location spawnLoc = sampleSpawnLocation(ctx.playerLocation());
                if (spawnLoc == null) continue;

                plans.add(new SpawnPlan(uuid, archetype, spawnLoc, enemyLevel));
            }
        });

        return plans;
    }

    private int calculateEnemyLevel(SpawnContext ctx) {
        double avgLevel = ctx.averagePlayerLevel();
        int playerCount = ctx.playerCount();
        long runSeconds = ctx.runDurationSeconds();

        double level = avgLevel * config.getAvgLevelMultiplier()
            + playerCount * config.getPlayerCountMultiplier()
            + config.getLevelOffset();

        // Time scaling
        level += (runSeconds / config.getTimeStepSeconds()) *
            config.getLevelPerTimeStep();

        return Math.max(config.getMinEnemyLevel(),
            Math.min(config.getMaxEnemyLevel(), (int) level));
    }

    private void executeSpawnPlans(List<SpawnPlan> plans) {
        int commandsThisTick = 0;
        int spawnsThisTick = 0;

        for (SpawnPlan plan : plans) {
            if (commandsThisTick >= config.getMaxCommandsPerTick()) break;
            if (spawnsThisTick >= config.getMaxSpawnsPerTick()) break;

            // Execute spawn commands
            for (String cmdTemplate : plan.archetype().getSpawnCommands()) {
                String cmd = templateEngine.expand(cmdTemplate, Map.of(
                    "sx", String.valueOf(plan.location().getBlockX()),
                    "sy", String.valueOf(plan.location().getBlockY()),
                    "sz", String.valueOf(plan.location().getBlockZ()),
                    "runWorld", plan.location().getWorld().getName(),
                    "enemyLevel", String.valueOf(plan.enemyLevel()),
                    "enemyType", plan.archetype().getEnemyType()
                ));

                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                commandsThisTick++;
            }

            spawnsThisTick++;
        }
    }

    private int countVrsMobsNear(Location loc, double radius) {
        return (int) loc.getWorld().getNearbyEntities(loc, radius, radius, radius)
            .stream()
            .filter(e -> e.getScoreboardTags().contains("vrs_mob"))
            .count();
    }
}
```

### 7.2 VRS Mob Death Handler

```java
public class MobDeathHandler implements Listener {

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();

        // Check if VRS mob
        if (!entity.getScoreboardTags().contains("vrs_mob")) return;

        // Parse enemy level from tag
        int enemyLevel = parseEnemyLevel(entity);

        // Find killer
        Player killer = entity.getKiller();
        if (killer == null) return;

        PlayerState killerState = stateService.getPlayerState(killer.getUniqueId());
        if (killerState.getMode() != PlayerMode.IN_RUN) return;

        // Award rewards
        EnemyArchetype archetype = identifyArchetype(entity);

        // XP
        int xp = archetype.getXpBase() + (enemyLevel * archetype.getXpPerLevel());
        rewardService.awardKillXp(killer, enemyLevel, entity.getLocation());

        // Coin
        int coin = archetype.getCoinBase() + (enemyLevel * archetype.getCoinPerLevel());
        rewardService.awardCoin(killer, coin);

        // Perma-score chance
        if (ThreadLocalRandom.current().nextDouble() < archetype.getPermaScoreChance()) {
            rewardService.awardPermaScore(killer, 1);
        }

        // Clear drops (rewards are direct to inventory)
        event.getDrops().clear();
        event.setDroppedExp(0);
    }

    private int parseEnemyLevel(LivingEntity entity) {
        for (String tag : entity.getScoreboardTags()) {
            if (tag.startsWith("vrs_lvl_")) {
                try {
                    return Integer.parseInt(tag.substring(8));
                } catch (NumberFormatException ignored) {}
            }
        }
        return config.getDefaultEnemyLevel();
    }
}
```

---

## 8. Merchant System

### 8.1 Merchant Spawning

```java
public class MerchantService {

    public void spawnMerchant(World world, MerchantTemplate template) {
        Location loc = sampleMerchantLocation(world);
        if (loc == null) return;

        Villager villager = (Villager) world.spawnEntity(loc, EntityType.VILLAGER);

        // Configure
        villager.customName(Component.text(template.getDisplayName()));
        villager.setCustomNameVisible(true);
        villager.setAI(false);
        villager.setInvulnerable(true);
        villager.setProfession(Villager.Profession.WANDERING_TRADER);

        // Tags for identification
        villager.addScoreboardTag("vrs_merchant");
        villager.addScoreboardTag("vrs_world_" + world.getName());

        // Set trades
        List<MerchantRecipe> recipes = buildRecipes(template);
        villager.setRecipes(recipes);

        // Track
        RunState run = findRunForWorld(world);
        if (run != null) {
            run.getActiveMerchantEntityIds().add(villager.getUniqueId());
        }

        // Schedule despawn/rotation
        Bukkit.getScheduler().runTaskLater(plugin,
            () -> rotateMerchant(villager, world, template),
            config.getMerchantLifetimeTicks());
    }

    private List<MerchantRecipe> buildRecipes(MerchantTemplate template) {
        List<MerchantRecipe> recipes = new ArrayList<>();

        for (TradeDefinition trade : template.getTrades()) {
            ItemStack buyA = itemService.getItemStack(trade.getBuyATemplateId());
            ItemStack buyB = trade.getBuyBTemplateId() != null
                ? itemService.getItemStack(trade.getBuyBTemplateId())
                : null;
            ItemStack sell = itemService.getItemStack(trade.getSellTemplateId());

            MerchantRecipe recipe = new MerchantRecipe(
                sell,
                trade.getMaxUses()
            );
            recipe.addIngredient(buyA);
            if (buyB != null) {
                recipe.addIngredient(buyB);
            }

            recipes.add(recipe);
        }

        return recipes;
    }
}
```

---

## 9. Inventory Rules

### 9.1 Drop/Pickup Prevention

```java
public class InventoryListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        PlayerState state = stateService.getPlayerState(
            event.getPlayer().getUniqueId());

        if (isRestrictedMode(state.getMode())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerAttemptPickupItem(PlayerAttemptPickupItemEvent event) {
        PlayerState state = stateService.getPlayerState(
            event.getPlayer().getUniqueId());

        if (isRestrictedMode(state.getMode())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        PlayerState state = stateService.getPlayerState(player.getUniqueId());
        if (!isRestrictedMode(state.getMode())) return;

        // Prevent moving equipment slots
        int slot = event.getSlot();
        if (event.getClickedInventory() == player.getInventory()) {
            if (slot == player.getInventory().getHeldItemSlot() ||  // Weapon slot
                slot == 39) {  // Helmet slot
                event.setCancelled(true);
            }
        }
    }

    private boolean isRestrictedMode(PlayerMode mode) {
        return mode == PlayerMode.READY
            || mode == PlayerMode.COUNTDOWN
            || mode == PlayerMode.IN_RUN;
    }
}
```

---

## 10. Ready & Countdown System

### 10.1 Ready Toggle

```java
public class ReadyService {

    public void toggleReady(Player player) {
        PlayerState state = stateService.getPlayerState(player.getUniqueId());

        // Validation
        if (!config.isJoinEnabled()) {
            throw new GameException("error.join_disabled");
        }

        if (state.getMode() == PlayerMode.COOLDOWN) {
            long remaining = state.getCooldownUntilMillis() - System.currentTimeMillis();
            if (remaining > 0) {
                throw new GameException("error.on_cooldown", remaining / 1000);
            }
        }

        if (state.getStarterWeaponOptionId() == null) {
            throw new GameException("error.no_weapon_selected");
        }

        if (state.getStarterHelmetOptionId() == null) {
            throw new GameException("error.no_helmet_selected");
        }

        if (state.isReady()) {
            // Unready
            state.setReady(false);
            state.setMode(PlayerMode.LOBBY);

            if (state.getTeamId() != null) {
                TeamState team = stateService.getTeam(state.getTeamId());
                team.getReadyMembers().remove(player.getUniqueId());
                cancelCountdownIfActive(team);
            }

            i18n.send(player, "info.unreadied");
        } else {
            // Ready
            state.setReady(true);
            state.setMode(PlayerMode.READY);

            if (state.getTeamId() != null) {
                TeamState team = stateService.getTeam(state.getTeamId());
                team.getReadyMembers().add(player.getUniqueId());

                if (team.isAllReady()) {
                    startCountdown(team);
                } else {
                    int ready = team.getReadyMembers().size();
                    int total = team.getOnlineMemberCount();
                    i18n.send(player, "info.team_waiting", ready, total);
                }
            } else {
                // Solo - start countdown immediately
                startSoloCountdown(state);
            }
        }
    }

    private void startCountdown(TeamState team) {
        team.getMemberUuids().stream()
            .map(Bukkit::getPlayer)
            .filter(Objects::nonNull)
            .forEach(p -> {
                PlayerState s = stateService.getPlayerState(p.getUniqueId());
                s.setMode(PlayerMode.COUNTDOWN);
            });

        // Run countdown
        new CountdownTask(team, config.getCountdownSeconds(), this::onCountdownComplete)
            .runTaskTimer(plugin, 0L, 20L);
    }

    private void onCountdownComplete(TeamState team) {
        // Select world and spawn
        CombatWorld world = worldService.selectRandomWorld();
        Location spawn = worldService.selectSpawnLocation(world);

        // Create run
        RunState run = new RunState(
            UUID.randomUUID(),
            team.getTeamId(),
            world.getWorldName(),
            spawn
        );
        run.setStatus(RunStatus.ACTIVE);
        run.setStartedAtMillis(System.currentTimeMillis());

        stateService.registerRun(run);
        team.setActiveRunId(run.getRunId());

        // Teleport all members
        team.getMemberUuids().stream()
            .map(Bukkit::getPlayer)
            .filter(Objects::nonNull)
            .forEach(p -> {
                PlayerState s = stateService.getPlayerState(p.getUniqueId());
                s.setMode(PlayerMode.IN_RUN);
                s.setRunId(run.getRunId());

                p.teleport(spawn);
                sidebarManager.showSidebar(p, s);

                i18n.send(p, "info.run_started", world.getDisplayName());
            });
    }
}
```

---

## 11. Global Join Switch

```java
public class JoinSwitchService {

    private volatile boolean joinEnabled = true;

    public void setJoinEnabled(boolean enabled) {
        this.joinEnabled = enabled;
        config.setJoinEnabled(enabled);

        if (!enabled) {
            // Start grace eject for all in-run players
            stateService.getAllPlayers().stream()
                .filter(s -> s.getMode() == PlayerMode.IN_RUN)
                .forEach(this::startGraceEject);
        }
    }

    private void startGraceEject(PlayerState state) {
        state.setMode(PlayerMode.GRACE_EJECT);

        Player player = Bukkit.getPlayer(state.getUuid());
        if (player != null) {
            i18n.send(player, "info.grace_eject", config.getGraceEjectSeconds());
        }

        // Schedule eject
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (state.getMode() == PlayerMode.GRACE_EJECT) {
                executeGraceEject(state);
            }
        }, config.getGraceEjectSeconds() * 20L);
    }

    private void executeGraceEject(PlayerState state) {
        Player player = Bukkit.getPlayer(state.getUuid());

        // End run participation
        state.setRunId(null);
        state.setMode(PlayerMode.LOBBY);
        state.setReady(false);

        // Keep progress - this is maintenance, not death
        // Just teleport back

        if (player != null) {
            teleportService.teleportToPrep(player);
            sidebarManager.hideSidebar(player);
            i18n.send(player, "info.ejected_maintenance");
        }
    }
}
```

---

## 12. Starter Selection System

```java
public class StarterService {

    public void openStarterGui(Player player) {
        PlayerState state = stateService.getPlayerState(player.getUniqueId());

        if (state.getMode() != PlayerMode.LOBBY) {
            throw new GameException("error.not_in_lobby");
        }

        // Open weapon selection first
        openWeaponGui(player);
    }

    public void openWeaponGui(Player player) {
        List<StarterOption> weapons = config.getStarterWeapons();
        Inventory gui = createStarterGui(
            i18n.get("gui.select_weapon"),
            weapons,
            EquipmentSlot.WEAPON
        );
        player.openInventory(gui);
    }

    public void openHelmetGui(Player player) {
        PlayerState state = stateService.getPlayerState(player.getUniqueId());

        if (config.isRequireWeaponFirst() && state.getStarterWeaponOptionId() == null) {
            throw new GameException("error.select_weapon_first");
        }

        List<StarterOption> helmets = config.getStarterHelmets();
        Inventory gui = createStarterGui(
            i18n.get("gui.select_helmet"),
            helmets,
            EquipmentSlot.HELMET
        );
        player.openInventory(gui);
    }

    public void selectStarter(Player player, StarterOption option, EquipmentSlot slot) {
        PlayerState state = stateService.getPlayerState(player.getUniqueId());

        // Remove previous if exists
        if (slot == EquipmentSlot.WEAPON && state.getStarterWeaponOptionId() != null) {
            removeStarterFromSlot(player, state, EquipmentSlot.WEAPON);
        } else if (slot == EquipmentSlot.HELMET && state.getStarterHelmetOptionId() != null) {
            removeStarterFromSlot(player, state, EquipmentSlot.HELMET);
        }

        // Grant new item
        ItemTemplate template = itemService.getTemplate(option.getTemplateId());
        ItemStack item = template.getCachedItem().clone();

        // Mark with PDC
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(VRS_STARTER_KEY, PersistentDataType.BOOLEAN, true);
        pdc.set(VRS_TEMPLATE_KEY, PersistentDataType.STRING, option.getTemplateId());
        item.setItemMeta(meta);

        // Place in correct slot
        if (slot == EquipmentSlot.WEAPON) {
            player.getInventory().setItemInMainHand(item);
            state.setStarterWeaponOptionId(option.getOptionId());
            state.setWeaponGroup(option.getGroup());
            state.setWeaponLevel(option.getLevel());
        } else {
            player.getInventory().setHelmet(item);
            state.setStarterHelmetOptionId(option.getOptionId());
            state.setHelmetGroup(option.getGroup());
            state.setHelmetLevel(option.getLevel());
        }

        i18n.send(player, "info.starter_selected", option.getDisplayName());

        // Auto-open helmet GUI after weapon selection
        if (slot == EquipmentSlot.WEAPON && config.isAutoOpenHelmetGui()) {
            Bukkit.getScheduler().runTaskLater(plugin,
                () -> openHelmetGui(player), 1L);
        }
    }

    private void removeStarterFromSlot(Player player, PlayerState state,
                                        EquipmentSlot slot) {
        ItemStack current;
        if (slot == EquipmentSlot.WEAPON) {
            current = player.getInventory().getItemInMainHand();
        } else {
            current = player.getInventory().getHelmet();
        }

        if (current != null && current.hasItemMeta()) {
            PersistentDataContainer pdc = current.getItemMeta().getPersistentDataContainer();
            if (pdc.has(VRS_STARTER_KEY, PersistentDataType.BOOLEAN)) {
                // Clear the slot
                if (slot == EquipmentSlot.WEAPON) {
                    player.getInventory().setItemInMainHand(null);
                } else {
                    player.getInventory().setHelmet(null);
                }
            }
        }
    }
}
```

---

## 13. World Management

```java
public class WorldService {

    public CombatWorld selectRandomWorld() {
        List<CombatWorld> enabled = config.getCombatWorlds().stream()
            .filter(CombatWorld::isEnabled)
            .toList();

        if (enabled.isEmpty()) {
            throw new GameException("error.no_combat_worlds");
        }

        // Weighted random selection
        double totalWeight = enabled.stream()
            .mapToDouble(CombatWorld::getWeight)
            .sum();

        double random = ThreadLocalRandom.current().nextDouble() * totalWeight;
        double cumulative = 0;

        for (CombatWorld world : enabled) {
            cumulative += world.getWeight();
            if (random < cumulative) {
                return world;
            }
        }

        return enabled.get(enabled.size() - 1);
    }

    public Location selectSpawnLocation(CombatWorld combatWorld) {
        World world = Bukkit.getWorld(combatWorld.getWorldName());
        if (world == null) {
            throw new GameException("error.world_not_loaded", combatWorld.getWorldName());
        }

        // Sample random location within bounds
        BoundingBox bounds = combatWorld.getSpawnBounds();

        for (int attempts = 0; attempts < 50; attempts++) {
            double x = bounds.getMinX() +
                ThreadLocalRandom.current().nextDouble() * bounds.getWidthX();
            double z = bounds.getMinZ() +
                ThreadLocalRandom.current().nextDouble() * bounds.getWidthZ();

            // Find safe Y
            int y = world.getHighestBlockYAt((int) x, (int) z);
            Location loc = new Location(world, x, y + 1, z);

            if (isSafeSpawn(loc)) {
                return loc;
            }
        }

        // Fallback to world spawn
        return world.getSpawnLocation();
    }

    private boolean isSafeSpawn(Location loc) {
        Block feet = loc.getBlock();
        Block head = feet.getRelative(BlockFace.UP);
        Block ground = feet.getRelative(BlockFace.DOWN);

        return feet.isPassable()
            && head.isPassable()
            && ground.getType().isSolid()
            && !ground.isLiquid();
    }
}
```

---

## 14. Threading Model

### 14.1 Thread Assignment

| Operation | Thread |
|-----------|--------|
| Bukkit API calls | Main thread only |
| Command dispatch | Main thread only |
| Entity spawning | Main thread only |
| Teleportation | Main thread only |
| Spawn planning | Async |
| Placeholder expansion | Async |
| Reward calculation | Async |
| File I/O | Async |
| NBT parsing | Async |
| Database operations | Async |

### 14.2 Async Utilities

```java
public class AsyncUtils {

    private static final ExecutorService COMPUTE_POOL =
        Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "VRS-Compute");
            t.setDaemon(true);
            return t;
        });

    public static <T> CompletableFuture<T> computeAsync(Supplier<T> task) {
        return CompletableFuture.supplyAsync(task, COMPUTE_POOL);
    }

    public static void runOnMain(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    public static <T> void computeThenMain(Supplier<T> compute, Consumer<T> mainTask) {
        computeAsync(compute).thenAccept(result ->
            runOnMain(() -> mainTask.accept(result)));
    }
}
```

---

## 15. Data Storage

### 15.1 File Structure

```
plugins/KedamaSurvivors/
├── config.yml           # Main configuration
├── lang/
│   └── zh_CN.yml       # Language file
├── data/
│   ├── items/
│   │   ├── weapons.yml  # Weapon item NBT
│   │   ├── helmets.yml  # Helmet item NBT
│   │   └── trades.yml   # Trade item NBT
│   └── runtime/
│       ├── players.json # Player states (periodic save)
│       └── teams.json   # Team states (periodic save)
└── logs/
    └── vrs.log         # Plugin-specific logging
```

### 15.2 Item NBT Storage

```yaml
# data/items/weapons.yml
templates:
  sword_iron_1:
    nbt: "{id:'minecraft:iron_sword',components:{...}}"
    group: "sword"
    level: 1
    slot: WEAPON

  sword_diamond_2:
    nbt: "{id:'minecraft:diamond_sword',components:{...}}"
    group: "sword"
    level: 2
    slot: WEAPON
```

### 15.3 Runtime Data Persistence

```java
public class PersistenceService {

    private final ScheduledExecutorService saveExecutor =
        Executors.newSingleThreadScheduledExecutor();

    public void startAutoSave() {
        saveExecutor.scheduleAtFixedRate(
            this::saveAll,
            config.getSaveIntervalSeconds(),
            config.getSaveIntervalSeconds(),
            TimeUnit.SECONDS
        );
    }

    public void saveAll() {
        // Snapshot on main thread
        List<PlayerState> players = new ArrayList<>(stateService.getAllPlayers());
        List<TeamState> teams = new ArrayList<>(stateService.getAllTeams());

        // Write async
        saveExecutor.submit(() -> {
            savePlayersToFile(players);
            saveTeamsToFile(teams);
        });
    }

    private void savePlayersToFile(List<PlayerState> players) {
        // Only save persistent data
        JsonArray array = new JsonArray();
        for (PlayerState state : players) {
            JsonObject obj = new JsonObject();
            obj.addProperty("uuid", state.getUuid().toString());
            obj.addProperty("permaScore", state.getPermaScore());
            // Add other persistent fields
            array.add(obj);
        }

        Path file = dataPath.resolve("runtime/players.json");
        Files.writeString(file, gson.toJson(array));
    }
}
```

---

## 16. Module Dependencies

```
┌─────────────────────────────────────────────────────────────┐
│                    KedamaSurvivorsPlugin                    │
│                     (Bootstrap & DI)                        │
└─────────────────────────────────────────────────────────────┘
                              │
        ┌─────────────────────┼─────────────────────┐
        ▼                     ▼                     ▼
┌───────────────┐     ┌───────────────┐     ┌───────────────┐
│ ConfigService │     │  I18nService  │     │TemplateEngine │
│  (Reloadable) │     │ (Translations)│     │ (Placeholders)│
└───────────────┘     └───────────────┘     └───────────────┘
        │                     │                     │
        └─────────────────────┼─────────────────────┘
                              ▼
                    ┌─────────────────┐
                    │  StateService   │
                    │(Player/Team/Run)│
                    └─────────────────┘
                              │
        ┌──────────┬──────────┼──────────┬──────────┐
        ▼          ▼          ▼          ▼          ▼
┌─────────────┐ ┌────────┐ ┌────────┐ ┌────────┐ ┌────────────┐
│StarterServ  │ │ReadySrv│ │WorldSrv│ │TeamSrv │ │ScoreboardSv│
└─────────────┘ └────────┘ └────────┘ └────────┘ └────────────┘
        │          │          │          │          │
        └──────────┴──────────┼──────────┴──────────┘
                              ▼
        ┌──────────┬──────────┴──────────┬──────────┐
        ▼          ▼                     ▼          ▼
┌─────────────┐ ┌────────────┐   ┌────────────┐ ┌──────────┐
│SpawnerServ  │ │RewardServ  │   │UpgradeServ │ │MerchantSv│
└─────────────┘ └────────────┘   └────────────┘ └──────────┘
```

---

## 17. Acceptance Criteria

1. ✅ Plugin runs standalone without external plugin dependencies
2. ✅ Players must select starter weapon then helmet via GUI
3. ✅ `/vrs ready` starts countdown, teleports to random safe spawn in enabled world
4. ✅ Players cannot drop/pickup items during run
5. ✅ Enemies spawn via command templates, scaling by avg player level and count
6. ✅ Kill rewards XP + coin; nearby players get XP share
7. ✅ XP threshold triggers upgrade UI; overflow XP held, not lost
8. ✅ At max level, XP converts to perma-score
9. ✅ Perma-score stored in vanilla scoreboard objective
10. ✅ Sidebar displays real-time stats
11. ✅ Merchants are vanilla villagers with configured recipes
12. ✅ Death resets XP/equipment; keeps coins/consumables/perma-score
13. ✅ 3-second respawn invulnerability
14. ✅ All teammates dead = run ends
15. ✅ 5-minute disconnect grace period
16. ✅ Global join switch with grace eject
17. ✅ `/vrs reload` hot-applies config
18. ✅ All text translatable with Chinese default
