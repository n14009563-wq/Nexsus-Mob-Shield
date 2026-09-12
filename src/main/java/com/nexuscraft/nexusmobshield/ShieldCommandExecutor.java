package com.nexuscraft.nexusmobshield;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class ShieldCommandExecutor implements CommandExecutor {

    private final JavaPlugin plugin;
    private final ShieldSettings settings;
    private final BlockedKillLog log;
    private final ShieldSummaryTask summaryTask;

    public ShieldCommandExecutor(JavaPlugin plugin, ShieldSettings settings, BlockedKillLog log, ShieldSummaryTask summaryTask) {
        this.plugin = plugin;
        this.settings = settings;
        this.log = log;
        this.summaryTask = summaryTask;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            showStatus(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "status": {
                showStatus(sender);
                return true;
            }
            case "toggle": {
                settings.setEnabled(!settings.isEnabled());
                sender.sendMessage("§a/kill protection is now " + (settings.isEnabled() ? "§aON" : "§cOFF") + "§a.");
                return true;
            }
            case "allow": {
                int seconds = 30;
                if (args.length >= 2) {
                    try {
                        seconds = Integer.parseInt(args[1]);
                    } catch (NumberFormatException e) {
                        sender.sendMessage("§cUsage: /nexusmobshield allow [seconds]");
                        return true;
                    }
                }
                settings.setBypassSeconds(seconds);
                sender.sendMessage("§ePausing /kill protection for " + seconds
                        + " second(s) -- run your own /kill now if you need to. It re-enables automatically.");
                plugin.getLogger().info("[NexusMobShield] " + sender.getName() + " opened a " + seconds + "s bypass window.");
                return true;
            }
            case "log": {
                int n = 15;
                if (args.length >= 2) {
                    try {
                        n = Integer.parseInt(args[1]);
                    } catch (NumberFormatException ignored) {
                        // keep default
                    }
                }
                List<BlockedKillLog.Entry> entries = log.recent(n);
                if (entries.isEmpty()) {
                    sender.sendMessage("§7Nothing blocked yet.");
                    return true;
                }
                sender.sendMessage("§6Last " + entries.size() + " blocked /kill attempt(s) (of "
                        + log.totalBlocked() + " total since startup):");
                for (BlockedKillLog.Entry e : entries) {
                    sender.sendMessage("§7- §f" + e.format());
                }
                sender.sendMessage("§7Tip: if these cluster around the same coordinates, that's likely where the"
                        + " command block itself is -- NexusCmdTracker can help you scan for and remove it.");
                return true;
            }
            case "reload": {
                settings.load();
                summaryTask.restart();
                sender.sendMessage("§aNexusMobShield config reloaded.");
                return true;
            }
            default: {
                showStatus(sender);
                return true;
            }
        }
    }

    private void showStatus(CommandSender sender) {
        sender.sendMessage("§6NexusMobShield status:");
        sender.sendMessage("§7Protection: " + (settings.isEnabled() ? "§aON" : "§cOFF"));
        if (settings.isBypassActive()) {
            sender.sendMessage("§eBypass active for " + settings.bypassRemainingSeconds() + " more second(s) -- /kill will work right now.");
        }
        sender.sendMessage("§7Protecting players: " + (settings.protectPlayers() ? "§ayes" : "§7no"));
        sender.sendMessage("§7Blocked total since startup: §f" + log.totalBlocked());
    }
}
