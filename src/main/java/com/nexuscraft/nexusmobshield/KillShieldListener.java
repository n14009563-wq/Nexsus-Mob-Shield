package com.nexuscraft.nexusmobshield;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The actual override: cancels the specific damage the vanilla /kill command produces
 * (EntityDamageEvent.DamageCause.KILL -- Bukkit fires this, and is supposed to respect
 * cancelling it, for every /kill @e[...]-style command, whether it's run from console, a player,
 * or a command block). Every other damage cause is left completely alone -- normal combat, fall
 * damage, drowning, this plugin's own other systems, etc. all work exactly as before. Only /kill
 * itself stops working against protected entities.
 *
 * Registered at MONITOR priority, not HIGHEST. Bukkit only guarantees that MONITOR runs after
 * every other priority tier -- it does NOT guarantee execution order between two listeners
 * registered at the same tier. A HIGHEST-priority handler can therefore still lose a race against
 * some other HIGHEST listener (including one that re-applies damage or re-triggers the kill after
 * us), which is the most likely reason mobs kept dying even with this plugin installed. MONITOR is
 * the only tier where "runs last, full stop" is actually guaranteed. Both sides of the outcome are
 * forced explicitly here (cancelled AND zeroed damage), rather than relying on cancellation alone.
 *
 * Belt-and-braces on top of that: this class ALSO watches EntityDeathEvent. If a protected,
 * non-exempt mob dies anyway and its last recorded damage cause was exactly the KILL we tried to
 * stop, that means the cancellation didn't hold -- so a same-type replacement is spawned back in
 * at the same spot a tick later ("forced back into the game"). This only ever engages for deaths
 * actually caused by the blocked /kill; real combat, fall damage, drowning, lava, etc. all still
 * result in a normal, permanent death exactly as vanilla intends. CREEPER (and anything else
 * listed in settings.exempt-entity-types) is completely excluded from both the cancellation and
 * the recovery net -- those deaths are always allowed to stick.
 */
public final class KillShieldListener implements Listener {

    private final JavaPlugin plugin;
    private final ShieldSettings settings;
    private final BlockedKillLog log;

    /** Rolling one-minute window of recovery timestamps, used to cap recoveries/minute so a
     * runaway /kill loop can never turn into a runaway spawn loop. */
    private final Deque<Long> recoveryTimestamps = new ArrayDeque<>();

    public KillShieldListener(JavaPlugin plugin, ShieldSettings settings, BlockedKillLog log) {
        this.plugin = plugin;
        this.settings = settings;
        this.log = log;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.KILL) {
            return;
        }
        if (!settings.isEnabled() || settings.isBypassActive()) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity living)) {
            return;
        }
        boolean isPlayer = living instanceof Player;
        if (isPlayer && !settings.protectPlayers()) {
            return;
        }
        String typeName = living.getType().name();
        if (!isPlayer && settings.isExempt(typeName)) {
            return;
        }

        // Force both sides of the outcome explicitly -- don't rely on cancellation alone holding.
        event.setCancelled(true);
        event.setDamage(0);

        String targetName = isPlayer ? ((Player) living).getName() : null;
        String world = living.getLocation().getWorld() != null ? living.getLocation().getWorld().getName() : "?";
        log.record(typeName, targetName, world, living.getLocation().getBlockX(),
                living.getLocation().getBlockY(), living.getLocation().getBlockZ());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead == null || dead instanceof Player) {
            // Recovery is a mobs-only safety net -- players are never revived by this plugin.
            return;
        }
        if (!settings.isEnabled() || settings.isBypassActive() || !settings.isRecoveryEnabled()) {
            return;
        }
        String typeName = dead.getType().name();
        if (settings.isExempt(typeName)) {
            // CREEPER (by default) and anything else on the exempt list: allowed to stay dead.
            return;
        }

        EntityDamageEvent lastDamage = dead.getLastDamageCause();
        if (lastDamage == null || lastDamage.getCause() != EntityDamageEvent.DamageCause.KILL) {
            // This death was NOT caused by the /kill we're trying to block (real combat, fall,
            // drowning, lava, etc.) -- leave it alone, exactly as before.
            return;
        }

        Location location = dead.getLocation();
        World world = location != null ? location.getWorld() : null;
        if (world == null) {
            return;
        }
        if (!allowRecoveryNow()) {
            plugin.getLogger().warning("[NexusMobShield] Recovery rate limit hit ("
                    + settings.recoveryMaxPerMinute() + "/min) -- not respawning this " + typeName
                    + ". Whatever is running /kill is doing it very fast; consider finding and"
                    + " removing it (see NexusCmdTracker) rather than relying on recovery alone.");
            return;
        }

        EntityType type = dead.getType();
        Location spawnAt = location.clone();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> world.spawnEntity(spawnAt, type), 1L);

        log.recordRecovery();
        plugin.getLogger().warning("[NexusMobShield] A " + typeName + " was killed anyway (via the"
                + " blocked /kill cause) at " + world.getName() + " " + spawnAt.getBlockX() + ","
                + spawnAt.getBlockY() + "," + spawnAt.getBlockZ() + " -- the cancellation didn't"
                + " hold, so a replacement is being spawned back in. See /nexusmobshield log.");
    }

    private synchronized boolean allowRecoveryNow() {
        long now = System.currentTimeMillis();
        long windowStart = now - 60_000L;
        while (!recoveryTimestamps.isEmpty() && recoveryTimestamps.peekFirst() < windowStart) {
            recoveryTimestamps.pollFirst();
        }
        if (recoveryTimestamps.size() >= settings.recoveryMaxPerMinute()) {
            return false;
        }
        recoveryTimestamps.addLast(now);
        return true;
    }
}
