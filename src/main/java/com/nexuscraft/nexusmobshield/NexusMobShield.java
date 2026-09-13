package com.nexuscraft.nexusmobshield;

import org.bukkit.plugin.java.JavaPlugin;

public final class NexusMobShield extends JavaPlugin {

    private ShieldSettings settings;
    private BlockedKillLog log;
    private ShieldSummaryTask summaryTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.settings = new ShieldSettings(this);
        settings.load();

        this.log = new BlockedKillLog();
        this.summaryTask = new ShieldSummaryTask(this, settings, log);

        getServer().getPluginManager().registerEvents(new KillShieldListener(this, settings, log), this);

        ShieldCommandExecutor executor = new ShieldCommandExecutor(this, settings, log, summaryTask);
        getCommand("nexusmobshield").setExecutor(executor);

        summaryTask.restart();

        getLogger().warning("NexusMobShield enabled -- /kill is now blocked against "
                + (settings.protectPlayers() ? "players and mobs" : "mobs") + " server-wide,"
                + " from every source including command blocks, until you say otherwise.");
    }

    @Override
    public void onDisable() {
        if (summaryTask != null) {
            summaryTask.stop();
        }
        getLogger().info("NexusMobShield disabled.");
    }
}
