package cat.nyaa.survivors.command;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Interface for subcommands of /vrs.
 */
public interface SubCommand {

    /**
     * Executes the subcommand.
     *
     * @param sender the command sender
     * @param args   arguments after the subcommand name
     */
    void execute(CommandSender sender, String[] args);

    /**
     * Returns tab completions for this subcommand.
     *
     * @param sender the command sender
     * @param args   arguments after the subcommand name
     * @return list of completions
     */
    default List<String> tabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }

    /**
     * Returns the permission required to use this subcommand.
     *
     * @return permission node, or null if no permission required
     */
    @Nullable
    default String getPermission() {
        return null;
    }

    /**
     * Returns whether this subcommand can only be executed by players.
     *
     * @return true if player-only
     */
    default boolean isPlayerOnly() {
        return false;
    }
}
