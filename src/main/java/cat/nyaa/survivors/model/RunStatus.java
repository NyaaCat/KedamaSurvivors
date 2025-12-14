package cat.nyaa.survivors.model;

/**
 * Represents the current status of a game run.
 */
public enum RunStatus {
    /**
     * Run is starting (countdown phase).
     */
    STARTING,

    /**
     * Run is active (gameplay in progress).
     */
    ACTIVE,

    /**
     * Run is ending (cleanup phase).
     */
    ENDING,

    /**
     * Run has completed.
     */
    COMPLETED
}
