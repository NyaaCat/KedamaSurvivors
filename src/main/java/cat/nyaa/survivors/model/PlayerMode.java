package cat.nyaa.survivors.model;

/**
 * Represents the current game state of a player.
 */
public enum PlayerMode {
    /**
     * Player is in the preparation area, not queued for a run.
     */
    LOBBY,

    /**
     * Player has marked ready, waiting for team/countdown.
     */
    READY,

    /**
     * Countdown is in progress, about to teleport.
     */
    COUNTDOWN,

    /**
     * Player is actively in a combat run.
     */
    IN_RUN,

    /**
     * Player died and is waiting for cooldown to expire.
     */
    COOLDOWN,

    /**
     * Global join switch was disabled, player is being ejected.
     */
    GRACE_EJECT,

    /**
     * Player disconnected during a run, within grace period.
     */
    DISCONNECTED
}
