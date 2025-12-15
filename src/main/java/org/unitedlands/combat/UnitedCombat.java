package org.unitedlands.combat;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.unitedlands.combat.commands.AdminCmd;
import org.unitedlands.combat.commands.PvPCmd;
import org.unitedlands.combat.commands.ReloadCmd;
import org.unitedlands.combat.hooks.Placeholders;
import org.unitedlands.combat.listeners.*;
import org.unitedlands.combat.listeners.interfaces.IGraveListener;
import org.unitedlands.combat.tagger.CombatTagManager;
import org.unitedlands.combat.util.MessageProvider;
import org.unitedlands.utils.Logger;
import org.unitedlands.combat.tagger.CombatTagBossbar;

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
    private CombatTagBossbar combatTagBossbar;
    private FlightListener flightListener;

    private MessageProvider messageProvider;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        messageProvider = new MessageProvider(getConfig());

        combatTagManager = new CombatTagManager(this, messageProvider);
        combatTagBossbar = new CombatTagBossbar(this, combatTagManager);
        flightListener = new FlightListener(combatTagManager);
        registerListeners();

        ReloadCmd reloadCmd = new ReloadCmd(this, messageProvider);
        registerCommand("unitedcombat", reloadCmd, reloadCmd);

        AdminCmd adminCmd = new AdminCmd(this, messageProvider);
        registerCommand("combatadmin", adminCmd, adminCmd);

        PvPCmd pvpCmd = new PvPCmd(messageProvider);
        registerCommand("pvp", pvpCmd, pvpCmd);

    }

    private void registerListeners() {

        PluginManager pluginManager = Bukkit.getServer().getPluginManager();
        pluginManager.registerEvents(new PlayerListener(this, messageProvider), this);
        pluginManager.registerEvents(new TownyListener(this, messageProvider), this);
        pluginManager.registerEvents(new ExplosionListener(getConfig()), this);
        pluginManager.registerEvents(new CombatTagListener(combatTagManager, combatTagBossbar, flightListener, messageProvider), this);
        pluginManager.registerEvents(flightListener, this);

        IGraveListener gravesListener = null;
        Plugin angelChest = Bukkit.getPluginManager().getPlugin("AngelChest");
        if (angelChest != null && angelChest.isEnabled()) {
            Logger.log("[UnitedCombat] AngelChest found, enabling integration.");
            gravesListener = new AngelChestGraveListener();
        }
        Plugin axGraves = Bukkit.getPluginManager().getPlugin("AxGraves");
        if (axGraves != null && axGraves.isEnabled()) {
            Logger.log("[UnitedCombat] AxGraves found, enabling integration.");
            gravesListener = new AxGravesListener();
        }

        if (gravesListener != null)
        {
            pluginManager.registerEvents((Listener)gravesListener, this);
        }


        // PlaceholderAPI Expansion Register
        if (pluginManager.getPlugin("PlaceholderAPI") != null) {
            new Placeholders().register();
        }
    }

    public void reloadPluginConfig() {
        // Reapply config on reload.
        reloadConfig();
        combatTagManager.reload();
        combatTagBossbar.reload();
        unregisterListeners();
        registerListeners();

        messageProvider.reload(getConfig());

        getLogger().info("Plugin configuration reloaded.");
    }

    public MessageProvider getMessageProvider() {
        return messageProvider;
    }


    @Override
    public void onDisable() {
        // Plugin shutdown logic.
        if (combatTagBossbar != null) combatTagBossbar.stop();
    }

}
