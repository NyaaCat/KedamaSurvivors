package cat.nyaa.survivors;

import cat.nyaa.survivors.command.VrsCommand;
import cat.nyaa.survivors.config.ConfigService;
import cat.nyaa.survivors.i18n.I18nService;
import cat.nyaa.survivors.listener.CombatListener;
import cat.nyaa.survivors.listener.InventoryListener;
import cat.nyaa.survivors.listener.PlayerListener;
import cat.nyaa.survivors.scoreboard.ScoreboardService;
import cat.nyaa.survivors.service.AdminConfigService;
import cat.nyaa.survivors.service.DeathService;
import cat.nyaa.survivors.service.PersistenceService;
import cat.nyaa.survivors.service.ReadyService;
import cat.nyaa.survivors.service.RewardService;
import cat.nyaa.survivors.service.RunService;
import cat.nyaa.survivors.service.JoinSwitchService;
import cat.nyaa.survivors.service.MerchantService;
import cat.nyaa.survivors.service.SpawnerService;
import cat.nyaa.survivors.service.StarterService;
import cat.nyaa.survivors.service.StateService;
import cat.nyaa.survivors.service.UpgradeService;
import cat.nyaa.survivors.service.WorldService;
import cat.nyaa.survivors.task.CooldownDisplay;
import cat.nyaa.survivors.task.DisconnectChecker;
import cat.nyaa.survivors.task.UpgradeReminderTask;
import cat.nyaa.survivors.util.CommandQueue;
import cat.nyaa.survivors.util.TemplateEngine;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * Main plugin class for KedamaSurvivors.
 * Handles plugin lifecycle and service initialization.
 */
public final class KedamaSurvivorsPlugin extends JavaPlugin {

    private static KedamaSurvivorsPlugin instance;

    private ConfigService configService;
    private AdminConfigService adminConfigService;
    private I18nService i18nService;
    private StateService stateService;
    private TemplateEngine templateEngine;
    private ScoreboardService scoreboardService;
    private WorldService worldService;
    private StarterService starterService;
    private ReadyService readyService;
    private RunService runService;
    private RewardService rewardService;
    private UpgradeService upgradeService;
    private DeathService deathService;
    private SpawnerService spawnerService;
    private JoinSwitchService joinSwitchService;
    private DisconnectChecker disconnectChecker;
    private CooldownDisplay cooldownDisplay;
    private UpgradeReminderTask upgradeReminderTask;
    private MerchantService merchantService;
    private PersistenceService persistenceService;
    private CommandQueue commandQueue;

    @Override
    public void onEnable() {
        instance = this;

        try {
            initializeServices();
            registerCommands();
            registerListeners();
            startTasks();

            getLogger().info("KedamaSurvivors enabled successfully!");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to enable KedamaSurvivors", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        stopTasks();
        saveData();

        instance = null;
        getLogger().info("KedamaSurvivors disabled.");
    }

    private void initializeServices() {
        // Config must be first
        configService = new ConfigService(this);
        configService.loadConfig();

        // Admin config for equipment/archetype data (depends on configService)
        adminConfigService = new AdminConfigService(this);
        adminConfigService.loadAll();

        // I18n depends on config for language selection
        i18nService = new I18nService(this, configService);
        i18nService.loadLanguage();

        // Command queue for rate-limited command execution
        commandQueue = new CommandQueue(this, configService::getMaxCommandsPerTick);

        // Template engine for command placeholders
        templateEngine = new TemplateEngine();
        templateEngine.setLogger(getLogger());
        templateEngine.setCommandQueue(commandQueue);

        // State service manages all game state
        stateService = new StateService();

        // Persistence service for saving/loading state (must be after stateService)
        persistenceService = new PersistenceService(this);
        persistenceService.initialize();

        // Scoreboard service for sidebar display
        scoreboardService = new ScoreboardService(this);

        // World service for combat world management (must be before ReadyService and RunService)
        worldService = new WorldService(this);

        // Starter service for equipment selection and granting
        starterService = new StarterService(this);

        // Ready service for ready/countdown logic
        readyService = new ReadyService(this);

        // Run service for run lifecycle management
        runService = new RunService(this);

        // Reward service for XP, coins, perma-score
        rewardService = new RewardService(this);

        // Upgrade service for equipment upgrades
        upgradeService = new UpgradeService(this);

        // Death service for death/respawn handling
        deathService = new DeathService(this);

        // Spawner service for enemy spawning
        spawnerService = new SpawnerService(this);

        // Join switch service for global entry control
        joinSwitchService = new JoinSwitchService(this);

        // Disconnect checker for grace period expiry
        disconnectChecker = new DisconnectChecker(this);

        // Cooldown display for actionbar
        cooldownDisplay = new CooldownDisplay(this);

        // Upgrade reminder task for chat-based upgrades
        upgradeReminderTask = new UpgradeReminderTask(this);

        // Merchant service for villager traders
        merchantService = new MerchantService(this);
    }

    private void registerCommands() {
        VrsCommand vrsCommand = new VrsCommand(this);
        PluginCommand command = getCommand("vrs");
        if (command != null) {
            command.setExecutor(vrsCommand);
            command.setTabCompleter(vrsCommand);
        } else {
            getLogger().warning("Failed to register /vrs command - is it defined in plugin.yml?");
        }
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new InventoryListener(this), this);
        getServer().getPluginManager().registerEvents(new CombatListener(this), this);
    }

    private void startTasks() {
        // Start persistence service (auto-save and backup tasks)
        if (persistenceService != null) {
            persistenceService.start();
        }

        // Start scoreboard updates
        if (scoreboardService != null) {
            scoreboardService.start();
        }

        // Start spawner service
        if (spawnerService != null) {
            spawnerService.start();
        }

        // Start disconnect checker
        if (disconnectChecker != null) {
            disconnectChecker.start();
        }

        // Start cooldown display
        if (cooldownDisplay != null) {
            cooldownDisplay.start();
        }

        // Start upgrade reminder task
        if (upgradeReminderTask != null) {
            upgradeReminderTask.start();
        }

        // Start merchant service
        if (merchantService != null) {
            merchantService.start();
        }

        // Start command queue
        if (commandQueue != null) {
            commandQueue.start();
        }
    }

    private void stopTasks() {
        // Clear ready service countdowns
        if (readyService != null) {
            readyService.clearAll();
        }

        // Stop persistence service (saves all data)
        if (persistenceService != null) {
            persistenceService.stop();
        }

        // Stop scoreboard service
        if (scoreboardService != null) {
            scoreboardService.stop();
        }

        // Stop spawner service
        if (spawnerService != null) {
            spawnerService.stop();
        }

        // Stop disconnect checker
        if (disconnectChecker != null) {
            disconnectChecker.stop();
        }

        // Stop cooldown display
        if (cooldownDisplay != null) {
            cooldownDisplay.stop();
        }

        // Stop upgrade reminder task
        if (upgradeReminderTask != null) {
            upgradeReminderTask.stop();
        }

        // Shutdown join switch service
        if (joinSwitchService != null) {
            joinSwitchService.shutdown();
        }

        // Stop merchant service
        if (merchantService != null) {
            merchantService.stop();
        }

        // Stop command queue (executes remaining commands)
        if (commandQueue != null) {
            commandQueue.stop();
        }

        // Cancel all scheduled tasks
        getServer().getScheduler().cancelTasks(this);
    }

    private void saveData() {
        // Final save is handled by persistenceService.stop() in stopTasks()
        // Clear runtime state after save
        if (stateService != null) {
            stateService.clearAll();
        }
    }

    /**
     * Reloads all plugin configuration.
     */
    public void reload() {
        configService.loadConfig();
        adminConfigService.loadAll();
        i18nService.loadLanguage();
        getLogger().info("Configuration reloaded.");
    }

    // ==================== Getters ====================

    public static KedamaSurvivorsPlugin getInstance() {
        return instance;
    }

    public ConfigService getConfigService() {
        return configService;
    }

    public AdminConfigService getAdminConfigService() {
        return adminConfigService;
    }

    public I18nService getI18nService() {
        return i18nService;
    }

    public StateService getStateService() {
        return stateService;
    }

    public TemplateEngine getTemplateEngine() {
        return templateEngine;
    }

    public ScoreboardService getScoreboardService() {
        return scoreboardService;
    }

    public WorldService getWorldService() {
        return worldService;
    }

    public ReadyService getReadyService() {
        return readyService;
    }

    public RunService getRunService() {
        return runService;
    }

    public StarterService getStarterService() {
        return starterService;
    }

    public RewardService getRewardService() {
        return rewardService;
    }

    public UpgradeService getUpgradeService() {
        return upgradeService;
    }

    public DeathService getDeathService() {
        return deathService;
    }

    public SpawnerService getSpawnerService() {
        return spawnerService;
    }

    public JoinSwitchService getJoinSwitchService() {
        return joinSwitchService;
    }

    public MerchantService getMerchantService() {
        return merchantService;
    }

    public PersistenceService getPersistenceService() {
        return persistenceService;
    }

    public UpgradeReminderTask getUpgradeReminderTask() {
        return upgradeReminderTask;
    }

    public CommandQueue getCommandQueue() {
        return commandQueue;
    }
}
