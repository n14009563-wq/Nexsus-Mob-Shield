package com.nexuscraft.nexusmobshield;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Set;

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
                if (settings.isSilenceCommandBlockOutput()) {
                    CommandBlockQuieter.applyToAllWorlds(plugin, true);
                }
                sender.sendMessage("§aNexusMobShield config reloaded.");
                return true;
            }
            case "exempt": {
                return handleExempt(sender, args);
            }
            case "quiet": {
                return handleQuiet(sender, args);
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
        Set<String> exempt = settings.exemptEntityTypesSnapshot();
        sender.sendMessage("§7Exempt (allowed to be killed): " + (exempt.isEmpty() ? "§7none" : "§f" + String.join(", ", exempt)));
        sender.sendMessage("§7Blocked total since startup: §f" + log.totalBlocked());
        if (log.totalRecovered() > 0) {
            sender.sendMessage("§eForced back into the game (cancellation didn't hold): §f" + log.totalRecovered()
                    + " §e-- see /nexusmobshield log and your console for detail.");
        }
        sender.sendMessage("§7Silencing command block console spam: " + (settings.isSilenceCommandBlockOutput() ? "§ayes" : "§7no"));
    }

    private boolean handleQuiet(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§7Command block output silencing is currently: "
                    + (settings.isSilenceCommandBlockOutput() ? "§aON" : "§cOFF"));
            sender.sendMessage("§7Usage: /nexusmobshield quiet <on|off>");
            return true;
        }
        boolean value;
        switch (args[1].toLowerCase()) {
            case "on": value = true; break;
            case "off": value = false; break;
            default:
                sender.sendMessage("§cUsage: /nexusmobshield quiet <on|off>");
                return true;
        }
        settings.setSilenceCommandBlockOutput(value);
        CommandBlockQuieter.applyToAllWorlds(plugin, value);
        sender.sendMessage(value
                ? "§aCommand block console spam (commandBlockOutput) is now silenced on every loaded world."
                : "§7Command block console output restored to vanilla behavior on every loaded world.");
        return true;
    }

    private boolean handleExempt(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /nexusmobshield exempt <list|add <TYPE>|remove <TYPE>>");
            return true;
        }
        switch (args[1].toLowerCase()) {
            case "list": {
                Set<String> exempt = settings.exemptEntityTypesSnapshot();
                sender.sendMessage(exempt.isEmpty()
                        ? "§7No entity types are exempt -- everything is protected."
                        : "§6Exempt (allowed to be killed): §f" + String.join(", ", exempt));
                return true;
            }
            case "add": {
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /nexusmobshield exempt add <TYPE>");
                    return true;
                }
                EntityType type = parseEntityType(sender, args[2]);
                if (type == null) {
                    return true;
                }
                boolean added = settings.addExempt(type.name());
                sender.sendMessage(added
                        ? "§a" + type.name() + " is now exempt -- /kill will work against it again."
                        : "§7" + type.name() + " was already exempt.");
                return true;
            }
            case "remove": {
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /nexusmobshield exempt remove <TYPE>");
                    return true;
                }
                EntityType type = parseEntityType(sender, args[2]);
                if (type == null) {
                    return true;
                }
                boolean removed = settings.removeExempt(type.name());
                sender.sendMessage(removed
                        ? "§a" + type.name() + " is protected again -- /kill will be blocked against it."
                        : "§7" + type.name() + " wasn't exempt.");
                return true;
            }
            default: {
                sender.sendMessage("§cUsage: /nexusmobshield exempt <list|add <TYPE>|remove <TYPE>>");
                return true;
            }
        }
    }

    private EntityType parseEntityType(CommandSender sender, String name) {
        try {
            return EntityType.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            sender.sendMessage("§cUnknown entity type: " + name + " -- use the exact Bukkit EntityType name, e.g. CREEPER.");
            return null;
        }
    }
}
