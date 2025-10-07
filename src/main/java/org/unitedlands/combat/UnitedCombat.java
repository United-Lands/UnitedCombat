package org.unitedlands.combat;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.unitedlands.combat.commands.AdminCmd;
import org.unitedlands.combat.commands.PvPCmd;
import org.unitedlands.combat.commands.ReloadCmd;
import org.unitedlands.combat.hooks.Placeholders;
import org.unitedlands.combat.listeners.ExplosionListener;
import org.unitedlands.combat.listeners.PlayerListener;
import org.unitedlands.combat.listeners.TownyListener;
import org.unitedlands.combat.tagger.CombatTagManager;

import java.util.Objects;

public final class UnitedCombat extends JavaPlugin {

    public void unregisterListeners() {
        HandlerList.unregisterAll(this);
    }

    // Helper method to register all commands with an executor and tab completer.
    private void registerCommand(String commandName, CommandExecutor executor, TabCompleter completer) {
        Objects.requireNonNull(getCommand(commandName), "Command " + commandName + " is not defined in plugin.yml.").setExecutor(executor);
        Objects.requireNonNull(getCommand(commandName)).setTabCompleter(completer);
    }

    private CombatTagManager combatTagManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        combatTagManager = new CombatTagManager(this);
        registerListeners();

        ReloadCmd reloadCmd = new ReloadCmd(this);
        registerCommand("unitedcombat", reloadCmd, reloadCmd);

        AdminCmd adminCmd = new AdminCmd(this);
        registerCommand("combatadmin", adminCmd, adminCmd);

        PvPCmd pvpCmd = new PvPCmd();
        registerCommand("pvp", pvpCmd, pvpCmd);

    }

    private void registerListeners() {

        PluginManager pluginManager = Bukkit.getServer().getPluginManager();
        pluginManager.registerEvents(new PlayerListener(this), this);
        pluginManager.registerEvents(new TownyListener(this), this);
        pluginManager.registerEvents(new ExplosionListener(getConfig()), this);

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
        pluginManager.registerEvents(new ExplosionListener(getConfig()), this);
        combatTagManager.reload();
        getLogger().info("Plugin configuration reloaded.");
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic.
    }

}
