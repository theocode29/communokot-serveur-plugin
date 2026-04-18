package io.communokot.progress.command;

import io.communokot.progress.CommunokotProgressPlugin;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

public final class CommunokotCommand implements TabExecutor {
    private final CommunokotProgressPlugin plugin;

    public CommunokotCommand(CommunokotProgressPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("Usage: /communokot <export|reload>");
            return true;
        }

        String subcommand = args[0].toLowerCase();
        switch (subcommand) {
            case "export" -> {
                plugin.triggerManualExport(sender);
                return true;
            }
            case "reload" -> {
                plugin.reloadRuntimeSettings(sender);
                return true;
            }
            default -> {
                sender.sendMessage("Unknown subcommand. Use: /communokot <export|reload>");
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("export", "reload");
        }
        return List.of();
    }
}
