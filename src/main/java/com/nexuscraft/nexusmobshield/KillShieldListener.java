package com.nexuscraft.nexusmobshield;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * The actual override: cancels the specific damage the vanilla /kill command produces
 * (EntityDamageEvent.DamageCause.KILL -- Bukkit fires this, and respects cancelling it, for
 * every /kill @e[...]-style command, whether it's run from console, a player, or a command
 * block). Every other damage cause is left completely alone -- normal combat, fall damage,
 * drowning, this plugin's own other systems, etc. all work exactly as before. Only /kill itself
 * stops working against protected entities, no matter who or what runs it or where it's hidden.
 *
 * Registered at HIGHEST priority (not MONITOR) so it still runs before any plugin that only
 * listens at MONITOR, but after everything else -- giving it close to the final say without
 * technically mutating an event at the priority Bukkit says shouldn't be mutated.
 */
public final class KillShieldListener implements Listener {

    private final ShieldSettings settings;
    private final BlockedKillLog log;

    public KillShieldListener(ShieldSettings settings, BlockedKillLog log) {
        this.settings = settings;
        this.log = log;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
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

        event.setCancelled(true);

        String targetName = isPlayer ? ((Player) living).getName() : null;
        String world = living.getLocation().getWorld() != null ? living.getLocation().getWorld().getName() : "?";
        log.record(typeName, targetName, world, living.getLocation().getBlockX(),
                living.getLocation().getBlockY(), living.getLocation().getBlockZ());
    }
}
