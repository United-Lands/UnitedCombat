package org.unitedlands.combat.player;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.unitedlands.combat.UnitedCombat;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class PvpPlayer {
    private final UnitedCombat unitedCombat = getPlugin();
    private final OfflinePlayer player;
    private final File file;
    private final FileConfiguration playerConfig;

    public PvpPlayer(@NotNull Player player) {
        this.player = player;
        file = null;
        playerConfig = getFileConfiguration();
    }

    public PvpPlayer(OfflinePlayer player) {
        this.player = player;
        file = null;
        playerConfig = getFileConfiguration();
    }

    public OfflinePlayer getPlayer() {
        return player;
    }

    private Map<String, Integer> getThresholds() {
        FileConfiguration cfg = unitedCombat.getConfig();
        Map<String, Integer> map = new LinkedHashMap<>();
        if (cfg.isConfigurationSection("hostility-thresholds")) {
            for (String key : Objects.requireNonNull(cfg.getConfigurationSection("hostility-thresholds")).getKeys(false)) {
                int start = Math.max(1, cfg.getInt("hostility-thresholds." + key));
                map.put(key.toLowerCase(Locale.ROOT), start);
            }
        }
        return map;
    }

    private String getFirstStatusKey() {
        return getThresholds().entrySet().stream()
                .min(Comparator.comparingInt(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .orElse("defensive");
    }

    private String resolveStatusKeyForHostility(int hostility) {
        return getThresholds().entrySet().stream()
                .filter(e -> hostility >= e.getValue())
                .max(Comparator.comparingInt(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .orElse(getFirstStatusKey());
    }

    public void createFile() {
        File playerDataFile = getPlayerFile();
        File parent = playerDataFile.getParentFile();
        if (parent != null && !parent.exists()) {
            boolean ok = parent.mkdirs();
            if (!ok && !parent.exists()) {
                unitedCombat.getLogger().log(Level.SEVERE,
                        "Failed to create parent directory for " + playerDataFile.getAbsolutePath());
                throw new IllegalStateException("Cannot create player data directory: " + parent.getAbsolutePath());
            }
        }

        // Create the file if missing (usually for new players) and report failures.
        if (!playerDataFile.exists()) {
            try {
                boolean created = playerDataFile.createNewFile();
                if (!created && !playerDataFile.exists()) {
                    unitedCombat.getLogger().log(Level.SEVERE,
                            "Failed to create data file: " + playerDataFile.getAbsolutePath());
                    throw new IllegalStateException("Cannot create player data file: " + playerDataFile.getAbsolutePath());
                }
            } catch (IOException e) {
                unitedCombat.getLogger().log(Level.SEVERE,
                        "IOException while creating player data file: " + playerDataFile.getAbsolutePath(), e);
                throw new RuntimeException(e);
            }
        }

        FileConfiguration fileConfiguration = new YamlConfiguration();
        try {
            fileConfiguration.load(playerDataFile);
            fileConfiguration.set("name", player.getName());
            fileConfiguration.set("hostility", 1);
            fileConfiguration.set("status", getFirstStatusKey());
            fileConfiguration.set("last-hostility-change-time", System.currentTimeMillis());
            fileConfiguration.set("can-degrade", true);
            fileConfiguration.set("immunity-time", System.currentTimeMillis());
            fileConfiguration.set("combat-log-punishments", 0);
            saveConfig(fileConfiguration);
        } catch (IOException | InvalidConfigurationException e) {
            unitedCombat.getLogger().log(Level.SEVERE,
                    "Failed to initialise player data file: " + playerDataFile.getAbsolutePath(), e);
            throw new RuntimeException(e);
        }
    }

    private String getFilePath() {
        return File.separator + "players" + File.separator + player.getUniqueId() + ".yml";
    }

    public void saveConfig(FileConfiguration fileConfig) {
        try {
            fileConfig.save(getPlayerFile());
        } catch (IOException e) {
            unitedCombat.getLogger().log(Level.SEVERE,
                    "Failed to save player data file: " + getPlayerFile().getAbsolutePath(), e);
            throw new RuntimeException(e);
        }
    }

    public FileConfiguration getFileConfiguration() {
        File playerStatsFile = getPlayerFile();
        FileConfiguration fileConfiguration = new YamlConfiguration();
        try {
            fileConfiguration.load(playerStatsFile);
            return fileConfiguration;
        } catch (IOException | InvalidConfigurationException e) {
            // Log the failure, try to recreate a fresh file, then load again once.
            unitedCombat.getLogger().log(Level.WARNING,
                    "Failed to load player data file, attempting to recreate: " + playerStatsFile.getAbsolutePath(), e);
            createFile();
            try {
                fileConfiguration.load(playerStatsFile);
                return fileConfiguration;
            } catch (IOException | InvalidConfigurationException e2) {
                unitedCombat.getLogger().log(Level.SEVERE,
                        "Failed to reload player data file after recreation: " + playerStatsFile.getAbsolutePath(), e2);
                throw new RuntimeException(e2);
            }
        }
    }

    public void updatePlayerHostility() {
        if (isHostile()) {
            setStatusKey(getStatusKey()); // touch + sync
            updateLastHostilityChangeTime();
            return;
        }
        if (isDegradable()) {
            if (isAggressive()) {
                setHostility(getHostility() - 1);
                setStatusKey(getStatusKey());
                return;
            }
            if (isDefensive()) {
                if (getHostility() == 1) {
                    return;
                }
                setHostility(getHostility() - 1);
                setStatusKey(getStatusKey());
            }
        }
    }
    public int getHostility() {
        return playerConfig.getInt("hostility");
    }

    public void setHostility(int value) {
        if (value >= 21) {
            playerConfig.set("hostility", 21);
            saveConfig(playerConfig);
            return;
        }
        // Don't go lower than 1
        playerConfig.set("hostility", Math.max(value, 1));
        // Update the time.
        updateLastHostilityChangeTime();
        saveConfig(playerConfig);
    }

    public void setStatusKey(String statusKey) {
        playerConfig.set("status", statusKey.toLowerCase(Locale.ROOT));
        saveConfig(playerConfig);
    }

    public String getStatusKey() {
        String computed = resolveStatusKeyForHostility(getHostility());
        if (!computed.equalsIgnoreCase(playerConfig.getString("status"))) {
            setStatusKey(computed);
        }
        return computed;
    }

    public boolean isDefensive() { return getStatusKey().equals("defensive"); }
    public boolean isHostile()   { return getStatusKey().equals("hostile"); }
    public boolean isAggressive(){ return getStatusKey().equals("aggressive"); }

    public String getIconHex(int hostility) {
        FileConfiguration config = unitedCombat.getConfig();
        return config.getString("hostility-colour-stages." + hostility);
    }

    public String getStatusIcon() {
        FileConfiguration cfg = unitedCombat.getConfig();
        String key = getStatusKey();
        String icon = cfg.getString("hostility-icons." + key);
        return (icon != null) ? icon : "";
    }

    public boolean isDegradable() {
        return playerConfig.getBoolean("can-degrade");
    }

    public void setDegradable(boolean value) {
        playerConfig.set("can-degrade", value);
        saveConfig(playerConfig);
    }
    public static UnitedCombat getPlugin() {
        return (UnitedCombat) Bukkit.getPluginManager().getPlugin("UnitedCombat");
    }

    @NotNull
    public File getPlayerFile() {
        if (this.file != null) {
            return file;
        }
        return new File(unitedCombat.getDataFolder(), getFilePath());
    }

    public void updateLastHostilityChangeTime() {
        playerConfig.set("last-hostility-change-time", System.currentTimeMillis());
        saveConfig(playerConfig);
    }

    public boolean isImmune() {
        // If the time is 0, it was force set by the plugin
        // so its disabled.
        if (getImmunityTime() == 0) {
            return false;
        }
        // if the time passed is bigger than 1 day (in millis)
        // then their immunity stamp is no longer valid.
        return getImmunityTime() < TimeUnit.DAYS.toMillis(1);
    }

    public long getImmunityTime() {
        return System.currentTimeMillis() - playerConfig.getLong("immunity-time");
    }
    
    public void expireImmunity() {
        playerConfig.set("immunity-time", 0);
        saveConfig(playerConfig);
    }

    public long getLastHostilityChangeTime() {
        return playerConfig.getLong("last-hostility-change-time");
    }

    public int getCombatLogPunishmentCount() {
        return playerConfig.getInt("combat-log-punishments", 0);
    }

    public void setCombatLogPunishmentCount(int value) {
        playerConfig.set("combat-log-punishments", Math.max(0, value));
        saveConfig(playerConfig);
    }

}
