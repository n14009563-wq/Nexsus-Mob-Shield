package com.nexuscraft.nexusmobshield;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Everything read out of config.yml, refreshed on /nexusmobshield reload. */
public final class ShieldSettings {

    private final JavaPlugin plugin;

    private boolean enabled = true;
    private boolean protectPlayers = true;
    private final Set<String> exemptEntityTypes = new HashSet<>();

    private boolean heartbeatEnabled = true;
    private int heartbeatSummaryIntervalSeconds = 60;

    /** While System.currentTimeMillis() < this, protection is fully suspended (a deliberate,
     * temporary bypass -- see /nexusmobshield allow). 0 means no active bypass. */
    private volatile long bypassUntilMillis = 0L;

    public ShieldSettings(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.reloadConfig();
        ConfigurationSection settings = plugin.getConfig().getConfigurationSection("settings");
        if (settings != null) {
            enabled = settings.getBoolean("enabled", true);
            protectPlayers = settings.getBoolean("protect-players", true);
        }

        exemptEntityTypes.clear();
        List<String> exempt = plugin.getConfig().getStringList("settings.exempt-entity-types");
        for (String s : exempt) {
            exemptEntityTypes.add(s.trim().toUpperCase());
        }

        ConfigurationSection heartbeat = plugin.getConfig().getConfigurationSection("heartbeat");
        if (heartbeat != null) {
            heartbeatEnabled = heartbeat.getBoolean("enabled", true);
            heartbeatSummaryIntervalSeconds = Math.max(10, heartbeat.getInt("summary-interval-seconds", 60));
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean protectPlayers() {
        return protectPlayers;
    }

    public boolean isExempt(String entityTypeName) {
        return exemptEntityTypes.contains(entityTypeName);
    }

    public boolean isHeartbeatEnabled() {
        return heartbeatEnabled;
    }

    public int heartbeatSummaryIntervalSeconds() {
        return heartbeatSummaryIntervalSeconds;
    }

    /** True while a deliberate, temporary /nexusmobshield allow bypass is active. */
    public boolean isBypassActive() {
        return System.currentTimeMillis() < bypassUntilMillis;
    }

    public void setBypassSeconds(int seconds) {
        this.bypassUntilMillis = seconds <= 0 ? 0L : System.currentTimeMillis() + seconds * 1000L;
    }

    public void clearBypass() {
        this.bypassUntilMillis = 0L;
    }

    public long bypassRemainingSeconds() {
        long remaining = bypassUntilMillis - System.currentTimeMillis();
        return Math.max(0L, remaining / 1000L);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        plugin.getConfig().set("settings.enabled", enabled);
        plugin.saveConfig();
    }
}
