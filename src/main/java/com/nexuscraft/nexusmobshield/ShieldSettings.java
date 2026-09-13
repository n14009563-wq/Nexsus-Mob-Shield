package com.nexuscraft.nexusmobshield;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Everything read out of config.yml, refreshed on /nexusmobshield reload. */
public final class ShieldSettings {

    private final JavaPlugin plugin;

    private boolean enabled = true;
    private boolean protectPlayers = true;
    private final Set<String> exemptEntityTypes = new HashSet<>();

    private boolean heartbeatEnabled = true;
    private int heartbeatSummaryIntervalSeconds = 60;

    private boolean recoveryEnabled = true;
    private int recoveryMaxPerMinute = 40;

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

        ConfigurationSection recovery = plugin.getConfig().getConfigurationSection("recovery");
        if (recovery != null) {
            recoveryEnabled = recovery.getBoolean("enabled", true);
            recoveryMaxPerMinute = Math.max(1, recovery.getInt("max-per-minute", 40));
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

    /** Whether killed-anyway mobs (a death whose last damage cause was the KILL we tried to
     * cancel) get a same-type replacement spawned back in as a safety net. */
    public boolean isRecoveryEnabled() {
        return recoveryEnabled;
    }

    /** Ceiling on how many recovery-respawns this instance will perform per rolling minute, so a
     * runaway /kill loop can't turn into a runaway spawn loop. */
    public int recoveryMaxPerMinute() {
        return recoveryMaxPerMinute;
    }

    /** Live snapshot of the exempt entity type names (upper-case), e.g. {"CREEPER"}. */
    public Set<String> exemptEntityTypesSnapshot() {
        return new TreeSet<>(exemptEntityTypes);
    }

    /**
     * Adds an entity type (by Bukkit EntityType name, e.g. "CREEPER") to the permanently-exempt
     * list and persists it to config.yml immediately. Returns false if it was already exempt.
     */
    public boolean addExempt(String entityTypeName) {
        String key = entityTypeName.trim().toUpperCase();
        if (!exemptEntityTypes.add(key)) {
            return false;
        }
        persistExemptList();
        return true;
    }

    /**
     * Removes an entity type from the exempt list and persists it to config.yml immediately.
     * Returns false if it wasn't exempt to begin with.
     */
    public boolean removeExempt(String entityTypeName) {
        String key = entityTypeName.trim().toUpperCase();
        if (!exemptEntityTypes.remove(key)) {
            return false;
        }
        persistExemptList();
        return true;
    }

    private void persistExemptList() {
        plugin.getConfig().set("settings.exempt-entity-types", new ArrayList<>(exemptEntityTypesSnapshot()));
        plugin.saveConfig();
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
