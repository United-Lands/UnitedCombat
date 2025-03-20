package org.unitedlands.combat;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.unitedlands.combat.commands.PvPCmd;
import org.unitedlands.combat.commands.ReloadCmd;
import org.unitedlands.combat.hooks.Placeholders;
import org.unitedlands.combat.listeners.PlayerListener;
import org.unitedlands.combat.listeners.TownyListener;

import java.util.Objects;

public final class UnitedCombat extends JavaPlugin {

    public void unregisterListeners() {
        HandlerList.unregisterAll(this);
    }

    // Helper method to register all commands with an executor and tab completer.
    private void registerCommand(CommandExecutor executor, TabCompleter completer) {
        Objects.requireNonNull(getCommand("unitedcombat"), "Command " + "unitedcombat" + " is not defined in plugin.yml.").setExecutor(executor);
        Objects.requireNonNull(getCommand("unitedcombat")).setTabCompleter(completer);
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        registerListeners();
        // Register the reload command using ReloadCmd as its executor & tab completer.
        registerCommand(new ReloadCmd(this), new ReloadCmd(this));
    }

    private void registerListeners() {

        PluginManager pluginManager = Bukkit.getServer().getPluginManager();
        pluginManager.registerEvents(new PlayerListener(this), this);
        pluginManager.registerEvents(new TownyListener(this), this);
        Objects.requireNonNull(getCommand("pvp")).setExecutor(new PvPCmd());

        // PlaceholderAPI Expansion Register
        if (pluginManager.getPlugin("PlaceholderAPI") != null) {
            new Placeholders().register();
        }

    }

    public void reloadPluginConfig() {
        // Reapply config on reload.
        reloadConfig();
        unregisterListeners();
        PluginManager pluginManager = Bukkit.getServer().getPluginManager();
        pluginManager.registerEvents(new PlayerListener(this), this);
        pluginManager.registerEvents(new TownyListener(this), this);
        getLogger().info("Plugin configuration reloaded.");
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic.
    }

}
