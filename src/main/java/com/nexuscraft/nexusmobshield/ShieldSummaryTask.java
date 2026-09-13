package com.nexuscraft.nexusmobshield;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Runs on a timer and, only if anything was actually blocked since the last tick, logs ONE
 * console line and ONE NexusHeartbeat nudge summarizing the count -- deliberately never one
 * message per blocked kill, since whatever's spamming /kill can do it fast enough that per-event
 * logging would just become a second console-spam problem on top of the first.
 */
public final class ShieldSummaryTask {

    private final JavaPlugin plugin;
    private final ShieldSettings settings;
    private final BlockedKillLog log;

    private BukkitTask task;

    public ShieldSummaryTask(JavaPlugin plugin, ShieldSettings settings, BlockedKillLog log) {
        this.plugin = plugin;
        this.settings = settings;
        this.log = log;
    }

    public void restart() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        long periodTicks = settings.heartbeatSummaryIntervalSeconds() * 20L;
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::runOnce, periodTicks, periodTicks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void runOnce() {
        long blocked = log.drainSinceLastSummary();
        long recovered = log.drainRecoveredSinceLastSummary();
        if (blocked <= 0 && recovered <= 0) {
            return;
        }
        if (blocked > 0) {
            plugin.getLogger().warning("[NexusMobShield] Blocked " + blocked
                    + " /kill-command attempt(s) in the last " + settings.heartbeatSummaryIntervalSeconds()
                    + "s (total since startup: " + log.totalBlocked() + "). See /nexusmobshield log for detail.");
        }
        if (recovered > 0) {
            plugin.getLogger().warning("[NexusMobShield] Forced " + recovered
                    + " mob(s) back into the game in the last " + settings.heartbeatSummaryIntervalSeconds()
                    + "s after the blocked /kill cause killed them anyway (total since startup: "
                    + log.totalRecovered() + "). This means cancellation alone isn't holding -- worth"
                    + " finding and removing whatever's running /kill (see NexusCmdTracker).");
        }
        if (settings.isHeartbeatEnabled()) {
            StringBuilder summary = new StringBuilder();
            if (blocked > 0) {
                summary.append("Blocked ").append(blocked).append(" unauthorized /kill-command attempt(s)");
            }
            if (recovered > 0) {
                if (summary.length() > 0) {
                    summary.append("; ");
                }
                summary.append("forced ").append(recovered).append(" mob(s) back into the game after /kill anyway");
            }
            summary.append(" in the last ").append(settings.heartbeatSummaryIntervalSeconds()).append(" seconds.");
            HeartbeatBridge.report(plugin, "SECURITY", -2, summary.toString());
        }
    }
}
