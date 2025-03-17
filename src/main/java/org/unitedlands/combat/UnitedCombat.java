package org.unitedlands.combat;

import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.unitedlands.combat.commands.PvPCmd;
import org.unitedlands.combat.hooks.Placeholders;
import org.unitedlands.combat.listeners.PlayerListener;
import org.unitedlands.combat.listeners.TownyListener;

public final class UnitedCombat extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        registerListeners();
    }

    private void registerListeners() {

        PluginManager pluginManager = Bukkit.getServer().getPluginManager();
        pluginManager.registerEvents(new PlayerListener(this), this);
        pluginManager.registerEvents(new TownyListener(this), this);
        getCommand("pvp").setExecutor(new PvPCmd());

        // PlaceholderAPI Expansion Register
        if (pluginManager.getPlugin("PlaceholderAPI") != null) {
            new Placeholders().register();
        }

    }


}
