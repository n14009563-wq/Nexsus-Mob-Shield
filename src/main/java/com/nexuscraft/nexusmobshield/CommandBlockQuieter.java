package com.nexuscraft.nexusmobshield;

import org.bukkit.GameRule;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * A backstop for exactly what prompted it: the vanilla /gamerule command failing (or not being
 * where an admin expected it) on a live server, while the console kept getting flooded with a
 * rogue command block's own "Killed N entities" / "Successfully cloned 1 block(s)" feedback every
 * tick. Rather than depend on that console command working, this sets the same gamerule directly
 * through the Bukkit API -- on every currently-loaded world at startup/reload, and again whenever
 * a new world loads -- so /nexusmobshield alone is enough to make the noise stop.
 *
 * This only silences the command block's own broadcast of what it did; it does not change
 * anything about whether the command itself runs, or whether NexusMobShield blocks/recovers from
 * it. Purely a console-noise fix.
 */
public final class CommandBlockQuieter {
    private CommandBlockQuieter() {
    }

    public static void applyToAllWorlds(JavaPlugin plugin, boolean silence) {
        Server server = plugin.getServer();
        for (World world : server.getWorlds()) {
            applyTo(world, silence);
        }
    }

    public static void applyTo(World world, boolean silence) {
        if (world == null) {
            return;
        }
        // silence == true means we turn OFF the vanilla broadcast (commandBlockOutput=false).
        world.setGameRule(GameRule.COMMAND_BLOCK_OUTPUT, !silence);
    }

    /** Applies the same setting to worlds that load after startup (a datapack/plugin adding a
     * new dimension, a multiverse-style setup, etc.). */
    public static final class WorldLoadListener implements Listener {
        private final ShieldSettings settings;

        public WorldLoadListener(ShieldSettings settings) {
            this.settings = settings;
        }

        @EventHandler
        public void onWorldLoad(WorldLoadEvent event) {
            if (settings.isSilenceCommandBlockOutput()) {
                applyTo(event.getWorld(), true);
            }
        }
    }
}
