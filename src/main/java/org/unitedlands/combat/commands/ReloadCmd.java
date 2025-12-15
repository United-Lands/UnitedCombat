package org.unitedlands.combat.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.unitedlands.combat.UnitedCombat;
import org.unitedlands.combat.util.MessageProvider;
import org.unitedlands.utils.Messenger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ReloadCmd implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final MessageProvider messageProvider;

    public ReloadCmd(JavaPlugin plugin, MessageProvider messageProvider) {
        this.plugin = plugin;
        this.messageProvider = messageProvider;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        plugin.getConfig();

        // Reload command.
        if (label.equalsIgnoreCase("unitedcombat") && args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("united.combat.admin")) {
                Messenger.sendMessage(sender, messageProvider.get("messages.no-permission"), null, messageProvider.get("messages.prefix"));
                return true;
            }
            plugin.reloadConfig();
            ((UnitedCombat) plugin).reloadPluginConfig();
                Messenger.sendMessage(sender, messageProvider.get("messages.reload"), null, messageProvider.get("messages.prefix"));
            return true;
        }
        // Default invalid command message.
        Messenger.sendMessage(sender, messageProvider.get("invalid-command"), null, messageProvider.get("messages.prefix"));
        return true;
    }

    @Nullable
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String @NotNull [] args) {
        if (!sender.hasPermission("united.combat.admin")) {
            // Return no suggestions if no permission.
            return Collections.emptyList();
        }
        if (args.length == 1) {
            // Provide suggestions for the first argument.
            List<String> suggestions = new ArrayList<>();
            if ("reload".startsWith(args[0].toLowerCase())) {
                suggestions.add("reload");
            }
            return suggestions;
        }
        return Collections.emptyList();
    }

}

