package cat.nyaa.survivors.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;

/**
 * A queue for executing commands with tick budgets to prevent lag spikes.
 * Commands are queued and executed up to a configurable limit per tick.
 */
public class CommandQueue {

    private final Plugin plugin;
    private final Queue<String> pendingCommands = new ConcurrentLinkedQueue<>();
    private final Supplier<Integer> maxCommandsPerTickSupplier;
    private int taskId = -1;

    /**
     * Creates a new CommandQueue.
     *
     * @param plugin                     the owning plugin
     * @param maxCommandsPerTickSupplier supplier for the max commands per tick config value
     */
    public CommandQueue(Plugin plugin, Supplier<Integer> maxCommandsPerTickSupplier) {
        this.plugin = plugin;
        this.maxCommandsPerTickSupplier = maxCommandsPerTickSupplier;
    }

    /**
     * Starts the command queue processing task.
     */
    public void start() {
        if (taskId != -1) {
            return; // Already running
        }

        taskId = Bukkit.getScheduler().runTaskTimer(plugin, this::processTick, 1, 1).getTaskId();
        plugin.getLogger().info("Command queue started");
    }

    /**
     * Stops the command queue processing task.
     */
    public void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }

        // Execute remaining commands immediately on shutdown
        while (!pendingCommands.isEmpty()) {
            String command = pendingCommands.poll();
            if (command != null) {
                executeImmediately(command);
            }
        }

        plugin.getLogger().info("Command queue stopped");
    }

    /**
     * Queues a command for execution.
     * The command will be executed as console when the queue processes it.
     *
     * @param command the command to execute (without leading /)
     */
    public void queue(String command) {
        if (command != null && !command.isEmpty()) {
            pendingCommands.add(command);
        }
    }

    /**
     * Executes a command immediately without queueing.
     * Use sparingly - prefer queue() for normal operations.
     *
     * @param command the command to execute
     */
    public void executeImmediately(String command) {
        if (command != null && !command.isEmpty()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        }
    }

    /**
     * Processes queued commands for this tick.
     */
    private void processTick() {
        int maxCommands = maxCommandsPerTickSupplier.get();
        int executed = 0;

        while (executed < maxCommands && !pendingCommands.isEmpty()) {
            String command = pendingCommands.poll();
            if (command != null) {
                executeImmediately(command);
                executed++;
            }
        }
    }

    /**
     * Gets the number of pending commands in the queue.
     */
    public int getPendingCount() {
        return pendingCommands.size();
    }

    /**
     * Checks if the queue is running.
     */
    public boolean isRunning() {
        return taskId != -1;
    }

    /**
     * Clears all pending commands without executing them.
     */
    public void clear() {
        pendingCommands.clear();
    }
}
