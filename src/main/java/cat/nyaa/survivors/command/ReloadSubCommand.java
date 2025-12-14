package cat.nyaa.survivors.command;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;
import cat.nyaa.survivors.i18n.I18nService;
import org.bukkit.command.CommandSender;

/**
 * Handles /vrs reload command to reload plugin configuration.
 */
public class ReloadSubCommand implements SubCommand {

    private final KedamaSurvivorsPlugin plugin;
    private final I18nService i18n;

    public ReloadSubCommand(KedamaSurvivorsPlugin plugin) {
        this.plugin = plugin;
        this.i18n = plugin.getI18nService();
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        try {
            plugin.reload();
            i18n.send(sender, "admin.reload_success");
        } catch (Exception e) {
            i18n.send(sender, "admin.reload_failed", "error", e.getMessage());
            plugin.getLogger().severe("Failed to reload configuration: " + e.getMessage());
        }
    }

    @Override
    public String getPermission() {
        return "vrs.admin";
    }
}
