package cat.nyaa.survivors.util;

/**
 * Exception for game logic errors that should be displayed to players.
 * The message key is used for i18n lookup.
 */
public class GameException extends RuntimeException {

    private final String messageKey;
    private final Object[] args;

    public GameException(String messageKey) {
        super(messageKey);
        this.messageKey = messageKey;
        this.args = new Object[0];
    }

    public GameException(String messageKey, Object... args) {
        super(messageKey);
        this.messageKey = messageKey;
        this.args = args;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public Object[] getArgs() {
        return args;
    }
}
