package cat.nyaa.survivors.i18n;

/**
 * Type-safe message keys for internationalization.
 * Maps to keys in the language YAML files.
 */
public enum MessageKey {

    // ============================================================
    // General
    // ============================================================
    PREFIX("prefix"),

    // ============================================================
    // Info Messages
    // ============================================================
    INFO_XP_GAINED("info.xp_gained"),
    INFO_XP_SHARED("info.xp_shared"),
    INFO_COIN_GAINED("info.coin_gained"),
    INFO_PERMA_SCORE_GAINED("info.perma_score_gained"),
    INFO_REWARD_OVERFLOW("info.reward_overflow"),
    INFO_REWARDS_DELIVERED("info.rewards_delivered"),
    INFO_READY("info.ready"),
    INFO_UNREADY("info.unready"),
    INFO_TEAM_WAITING("info.team_waiting"),
    INFO_COUNTDOWN_START("info.countdown_start"),
    INFO_COUNTDOWN_TICK("info.countdown_tick"),
    INFO_RUN_STARTING("info.run_starting"),
    INFO_RUN_STARTED("info.run_started"),
    INFO_RUN_ENDED("info.run_ended"),
    INFO_DIED("info.died"),
    INFO_DIED_COOLDOWN("info.died_cooldown"),
    INFO_RESPAWNED_TO_TEAM("info.respawned_to_team"),
    INFO_INVULNERABLE("info.invulnerable"),
    INFO_COOLDOWN_REMAINING("info.cooldown_remaining"),
    INFO_COOLDOWN_EXPIRED("info.cooldown_expired"),
    INFO_RECONNECTED("info.reconnected"),
    INFO_DISCONNECT_GRACE("info.disconnect_grace"),
    INFO_DISCONNECT_EXPIRED("info.disconnect_expired"),
    INFO_GRACE_EJECT("info.grace_eject"),
    INFO_EJECTED_MAINTENANCE("info.ejected_maintenance"),
    INFO_JOIN_DISABLED("info.join_disabled"),
    INFO_UPGRADE_AVAILABLE("info.upgrade_available"),
    INFO_UPGRADE_WEAPON("info.upgrade_weapon"),
    INFO_UPGRADE_HELMET("info.upgrade_helmet"),
    INFO_MAX_LEVEL_REWARD("info.max_level_reward"),
    INFO_OVERFLOW_CONVERT("info.overflow_convert"),

    // ============================================================
    // Upgrade Messages
    // ============================================================
    UPGRADE_WEAPON_UPGRADED("upgrade.weapon_upgraded"),
    UPGRADE_HELMET_UPGRADED("upgrade.helmet_upgraded"),
    UPGRADE_MAX_LEVEL_REWARD("upgrade.max_level_reward"),
    UPGRADE_ALREADY_MAX_LEVEL("upgrade.already_max_level"),
    UPGRADE_MAX_LEVEL("upgrade.max_level"),
    UPGRADE_PROMPT_HEADER("upgrade.prompt_header"),
    UPGRADE_PROMPT_POWER("upgrade.prompt_power"),
    UPGRADE_PROMPT_DEFENSE("upgrade.prompt_defense"),
    UPGRADE_PROMPT_POWER_HIGHLIGHT("upgrade.prompt_power_highlight"),
    UPGRADE_PROMPT_DEFENSE_HIGHLIGHT("upgrade.prompt_defense_highlight"),
    UPGRADE_PROMPT_POWER_MAX("upgrade.prompt_power_max"),
    UPGRADE_PROMPT_DEFENSE_MAX("upgrade.prompt_defense_max"),
    UPGRADE_PROMPT_POWER_MAX_HIGHLIGHT("upgrade.prompt_power_max_highlight"),
    UPGRADE_PROMPT_DEFENSE_MAX_HIGHLIGHT("upgrade.prompt_defense_max_highlight"),
    UPGRADE_REMINDER("upgrade.reminder"),
    UPGRADE_AUTO_SELECTED_POWER("upgrade.auto_selected_power"),
    UPGRADE_AUTO_SELECTED_DEFENSE("upgrade.auto_selected_defense"),
    UPGRADE_AUTO_SELECTED_POWER_MAX("upgrade.auto_selected_power_max"),
    UPGRADE_AUTO_SELECTED_DEFENSE_MAX("upgrade.auto_selected_defense_max"),
    UPGRADE_BOTH_MAX_INSTANT("upgrade.both_max_instant"),
    UPGRADE_NOT_PENDING("upgrade.not_pending"),
    UPGRADE_INVALID_CHOICE("upgrade.invalid_choice"),

    // ============================================================
    // Item Messages
    // ============================================================
    ITEM_LEVEL_INDICATOR("item.level_indicator"),
    ITEM_WEAPON_EQUIPPED("item.weapon_equipped"),
    ITEM_HELMET_EQUIPPED("item.helmet_equipped"),

    // ============================================================
    // Starter Messages
    // ============================================================
    STARTER_WEAPON_SELECTED("starter.weapon_selected"),
    STARTER_HELMET_SELECTED("starter.helmet_selected"),
    STARTER_SELECTION_COMPLETE("starter.selection_complete"),
    STARTER_HELP("starter.help"),
    STARTER_NONE("starter.none"),
    STARTER_CLEARED("starter.cleared"),
    STARTER_WEAPON_HEADER("starter.weapon_header"),
    STARTER_HELMET_HEADER("starter.helmet_header"),
    STARTER_STATUS("starter.status"),

    // ============================================================
    // Success Messages
    // ============================================================
    SUCCESS_READY("success.ready"),
    SUCCESS_TEAM_CREATED("success.team_created"),
    SUCCESS_TEAM_JOINED("success.team_joined"),
    SUCCESS_TEAM_LEFT("success.team_left"),
    SUCCESS_TEAM_DISBANDED("success.team_disbanded"),
    SUCCESS_PLAYER_INVITED("success.player_invited"),
    SUCCESS_PLAYER_KICKED("success.player_kicked"),
    SUCCESS_CONFIG_RELOADED("success.config_reloaded"),
    SUCCESS_WORLD_ENABLED("success.world_enabled"),
    SUCCESS_WORLD_DISABLED("success.world_disabled"),
    SUCCESS_ITEM_CAPTURED("success.item_captured"),
    SUCCESS_TRADE_CAPTURED("success.trade_captured"),
    SUCCESS_SPAWNER_PAUSED("success.spawner_paused"),
    SUCCESS_SPAWNER_RESUMED("success.spawner_resumed"),
    SUCCESS_MERCHANT_SPAWNED("success.merchant_spawned"),
    SUCCESS_MERCHANTS_CLEARED("success.merchants_cleared"),

    // ============================================================
    // Error Messages
    // ============================================================
    ERROR_NO_PERMISSION("error.no_permission"),
    ERROR_PLAYER_NOT_FOUND("error.player_not_found"),
    ERROR_INVALID_ARGUMENT("error.invalid_argument"),
    ERROR_COMMAND_FAILED("error.command_failed"),
    ERROR_UNKNOWN_COMMAND("error.unknown_command"),
    ERROR_PLAYER_ONLY("error.player_only"),
    ERROR_SPECIFY_PLAYER("error.specify_player"),
    ERROR_INVALID_NUMBER("error.invalid_number"),
    ERROR_NOT_IN_LOBBY("error.not_in_lobby"),
    ERROR_NOT_IN_RUN("error.not_in_run"),
    ERROR_NOT_IN_GAME("error.not_in_game"),
    ERROR_ALREADY_IN_RUN("error.already_in_run"),
    ERROR_ALREADY_READY("error.already_ready"),
    ERROR_NOT_READY("error.not_ready"),
    ERROR_ON_COOLDOWN("error.on_cooldown"),
    ERROR_JOIN_DISABLED("error.join_disabled"),
    ERROR_JOIN_DISABLED_ERROR("error.join_disabled_error"),
    ERROR_CANNOT_QUIT_NOW("error.cannot_quit_now"),
    ERROR_RUN_NOT_FOUND("error.run_not_found"),
    ERROR_NO_WEAPON_SELECTED("error.no_weapon_selected"),
    ERROR_NO_HELMET_SELECTED("error.no_helmet_selected"),
    ERROR_SELECT_WEAPON_FIRST("error.select_weapon_first"),
    ERROR_SELECT_STARTERS_FIRST("error.select_starters_first"),
    ERROR_WEAPON_FIRST("error.weapon_first"),
    ERROR_INVALID_SELECTION("error.invalid_selection"),
    ERROR_INVALID_OPTION("error.invalid_option"),
    ERROR_INVALID_GROUP("error.invalid_group"),
    ERROR_NO_ITEMS_AT_LEVEL("error.no_items_at_level"),
    ERROR_ALREADY_IN_TEAM("error.already_in_team"),
    ERROR_NOT_IN_TEAM("error.not_in_team"),
    ERROR_NOT_TEAM_OWNER("error.not_team_owner"),
    ERROR_NOT_LEADER("error.not_leader"),
    ERROR_TEAM_FULL("error.team_full"),
    ERROR_TEAM_NOT_FOUND("error.team_not_found"),
    ERROR_TEAM_NAME_TAKEN("error.team_name_taken"),
    ERROR_NO_VALID_INVITE("error.no_valid_invite"),
    ERROR_NO_INVITE("error.no_invite"),
    ERROR_INVITE_EXPIRED("error.invite_expired"),
    ERROR_PLAYER_ALREADY_IN_TEAM("error.player_already_in_team"),
    ERROR_PLAYER_IN_TEAM("error.player_in_team"),
    ERROR_CANNOT_KICK_SELF("error.cannot_kick_self"),
    ERROR_CANNOT_KICK_OWNER("error.cannot_kick_owner"),
    ERROR_CANNOT_INVITE_SELF("error.cannot_invite_self"),
    ERROR_CANNOT_LEAVE_IN_RUN("error.cannot_leave_in_run"),
    ERROR_CANNOT_DISBAND_IN_RUN("error.cannot_disband_in_run"),
    ERROR_PLAYER_NOT_IN_TEAM("error.player_not_in_team"),
    ERROR_SPECIFY_TEAM("error.specify_team"),
    ERROR_NO_COMBAT_WORLDS("error.no_combat_worlds"),
    ERROR_WORLD_NOT_FOUND("error.world_not_found"),
    ERROR_WORLD_NOT_LOADED("error.world_not_loaded"),
    ERROR_CAPTURE_NO_ITEM("error.capture_no_item"),
    ERROR_TEMPLATE_EXISTS("error.template_exists"),

    // ============================================================
    // Quit Messages
    // ============================================================
    QUIT_CANCELLED_COUNTDOWN("quit.cancelled_countdown"),
    QUIT_LEFT_RUN("quit.left_run"),

    // ============================================================
    // Team Messages
    // ============================================================
    TEAM_INVITE_RECEIVED("team.invite_received"),
    TEAM_CLICK_TO_JOIN("team.click_to_join"),
    TEAM_PLAYER_JOINED("team.player_joined"),
    TEAM_PLAYER_LEFT("team.player_left"),
    TEAM_PLAYER_KICKED("team.player_kicked"),
    TEAM_OWNERSHIP_TRANSFERRED("team.ownership_transferred"),
    TEAM_DISBANDED_NOTIFY("team.team_disbanded_notify"),
    TEAM_PLAYER_READY("team.player_ready"),
    TEAM_PLAYER_UNREADY("team.player_unready"),
    TEAM_ALL_READY("team.all_ready"),
    TEAM_PLAYER_DISCONNECTED("team.player_disconnected"),
    TEAM_PLAYER_RECONNECTED("team.player_reconnected"),
    TEAM_LIST_HEADER("team.list_header"),
    TEAM_LIST_OWNER("team.list_owner"),
    TEAM_LIST_MEMBER("team.list_member"),
    TEAM_LIST_STATUS_ONLINE("team.list_status_online"),
    TEAM_LIST_STATUS_READY("team.list_status_ready"),
    TEAM_LIST_STATUS_OFFLINE("team.list_status_offline"),
    TEAM_LIST_STATUS_IN_RUN("team.list_status_in_run"),
    TEAM_CREATED("team.created"),
    TEAM_INVITE_SENT("team.invite_sent"),
    TEAM_JOINED("team.joined"),
    TEAM_INVITE_DECLINED("team.invite_declined"),
    TEAM_LEFT("team.left"),
    TEAM_MEMBER_LEFT("team.member_left"),
    TEAM_MEMBER_JOINED("team.member_joined"),
    TEAM_KICKED("team.kicked"),
    TEAM_WAS_KICKED("team.was_kicked"),
    TEAM_DISBANDED("team.disbanded"),
    TEAM_LEADERSHIP_TRANSFERRED("team.leadership_transferred"),
    TEAM_BECAME_LEADER("team.became_leader"),
    TEAM_NO_TEAMS("team.no_teams"),
    TEAM_NOT_IN_TEAM("team.not_in_team"),
    TEAM_HELP("team.help"),
    TEAM_STATUS_HEADER("team.status_header"),
    TEAM_MEMBER_ENTRY("team.member_entry"),
    TEAM_LIST_TEAMS_HEADER("team.list_teams_header"),
    TEAM_LIST_ENTRY("team.list_entry"),

    // ============================================================
    // GUI Messages
    // ============================================================
    GUI_SELECT_WEAPON("gui.select_weapon"),
    GUI_SELECT_HELMET("gui.select_helmet"),
    GUI_UPGRADE_TITLE("gui.upgrade_title"),
    GUI_STARTER_WEAPON_TITLE("gui.starter_weapon_title"),
    GUI_STARTER_HELMET_TITLE("gui.starter_helmet_title"),
    GUI_ITEM_PREFIX("gui.item_prefix"),
    GUI_CURRENTLY_SELECTED("gui.currently_selected"),
    GUI_CLICK_TO_SELECT("gui.click_to_select"),
    GUI_BACK("gui.back"),
    GUI_CONFIRM("gui.confirm"),
    GUI_CANCEL("gui.cancel"),
    GUI_UPGRADE_CURRENT("gui.upgrade_current"),
    GUI_UPGRADE_NEXT("gui.upgrade_next"),
    GUI_CLICK_TO_UPGRADE("gui.click_to_upgrade"),
    GUI_ALREADY_MAX("gui.already_max"),

    // ============================================================
    // Sidebar/Scoreboard Messages
    // ============================================================
    SIDEBAR_TITLE("sidebar.title"),
    SCOREBOARD_TITLE("scoreboard.title"),
    SCOREBOARD_WEAPON_LEVEL("scoreboard.weapon_level"),
    SCOREBOARD_HELMET_LEVEL("scoreboard.helmet_level"),
    SCOREBOARD_XP("scoreboard.xp"),
    SCOREBOARD_COINS("scoreboard.coins"),
    SCOREBOARD_PERMA_SCORE("scoreboard.perma_score"),
    SCOREBOARD_TEAM("scoreboard.team"),
    SCOREBOARD_TIME("scoreboard.time"),
    SCOREBOARD_UPGRADE_COUNTDOWN("scoreboard.upgrade_countdown"),

    // ============================================================
    // Status Messages
    // ============================================================
    STATUS_HEADER("status.header"),
    STATUS_FOOTER("status.footer"),
    STATUS_MODE("status.mode"),
    STATUS_WORLD("status.world"),
    STATUS_TEAM("status.team"),
    STATUS_NO_TEAM("status.no_team"),
    STATUS_STARTERS("status.starters"),
    STATUS_READY("status.ready"),
    STATUS_READY_YES("status.ready_yes"),
    STATUS_READY_NO("status.ready_no"),
    STATUS_ROLE_LEADER("status.role_leader"),
    STATUS_ROLE_MEMBER("status.role_member"),
    STATUS_RUN("status.run"),
    STATUS_EQUIPMENT("status.equipment"),
    STATUS_WEAPON("status.weapon"),
    STATUS_HELMET("status.helmet"),
    STATUS_XP("status.xp"),
    STATUS_XP_HELD("status.xp_held"),
    STATUS_COINS("status.coins"),
    STATUS_PERMA_SCORE("status.perma_score"),
    STATUS_COOLDOWN("status.cooldown"),
    STATUS_RUN_TIME("status.run_time"),
    STATUS_NONE_SELECTED("status.none_selected"),
    STATUS_MODE_LOBBY("status.mode_lobby"),
    STATUS_MODE_READY("status.mode_ready"),
    STATUS_MODE_COUNTDOWN("status.mode_countdown"),
    STATUS_MODE_IN_RUN("status.mode_in_run"),
    STATUS_MODE_COOLDOWN("status.mode_cooldown"),
    STATUS_MODE_GRACE_EJECT("status.mode_grace_eject"),
    STATUS_MODE_DISCONNECTED("status.mode_disconnected"),

    // ============================================================
    // Countdown Messages
    // ============================================================
    COUNTDOWN_TITLE("countdown.title"),
    COUNTDOWN_SUBTITLE("countdown.subtitle"),
    COUNTDOWN_FINAL_TITLE("countdown.final_title"),
    COUNTDOWN_FINAL_SUBTITLE("countdown.final_subtitle"),
    COUNTDOWN_CANCELLED("countdown.cancelled"),
    COUNTDOWN_CANCELLED_DISCONNECT("countdown.cancelled_disconnect"),
    COUNTDOWN_STARTING("countdown.starting"),

    // ============================================================
    // Disconnect Messages
    // ============================================================
    DISCONNECT_GRACE_EXPIRED("disconnect.grace_expired"),
    DISCONNECT_RECONNECTED("disconnect.reconnected"),
    DISCONNECT_TEAMMATE_RECONNECTED("disconnect.teammate_reconnected"),
    DISCONNECT_TEAMMATE_DISCONNECTED("disconnect.teammate_disconnected"),

    // ============================================================
    // Ready Messages
    // ============================================================
    READY_ALL_READY("ready.all_ready"),
    READY_NOW_READY("ready.now_ready"),
    READY_NO_LONGER_READY("ready.no_longer_ready"),

    // ============================================================
    // Respawn Messages
    // ============================================================
    RESPAWN_INVULNERABILITY_START("respawn.invulnerability_start"),

    // ============================================================
    // Run Messages
    // ============================================================
    RUN_TEAM_WIPED("run.team_wiped"),

    // ============================================================
    // Actionbar Messages
    // ============================================================
    ACTIONBAR_COOLDOWN("actionbar.cooldown"),
    ACTIONBAR_INVULNERABLE("actionbar.invulnerable"),
    ACTIONBAR_XP_PROGRESS("actionbar.xp_progress"),
    ACTIONBAR_GRACE_EJECT("actionbar.grace_eject"),

    // ============================================================
    // Help Messages
    // ============================================================
    HELP_HEADER("help.header"),
    HELP_FOOTER("help.footer"),
    HELP_COMMAND_STARTER("help.command.starter"),
    HELP_COMMAND_TEAM("help.command.team"),
    HELP_COMMAND_READY("help.command.ready"),
    HELP_COMMAND_QUIT("help.command.quit"),
    HELP_COMMAND_STATUS("help.command.status"),
    HELP_COMMAND_UPGRADE("help.command.upgrade"),
    HELP_COMMAND_ADMIN("help.command.admin"),
    HELP_COMMAND_RELOAD("help.command.reload"),

    // ============================================================
    // Debug Messages
    // ============================================================
    DEBUG_PLAYER_HEADER("debug.player_header"),
    DEBUG_PERF_HEADER("debug.perf_header"),
    DEBUG_COMMAND_QUEUE("debug.command_queue"),
    DEBUG_SPAWN_QUEUE("debug.spawn_queue"),
    DEBUG_ACTIVE_MOBS("debug.active_mobs"),
    DEBUG_TICK_BUDGET("debug.tick_budget");

    private final String key;

    MessageKey(String key) {
        this.key = key;
    }

    /**
     * Gets the YAML key path for this message.
     */
    public String getKey() {
        return key;
    }

    @Override
    public String toString() {
        return key;
    }
}
