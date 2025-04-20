package org.unitedlands.combat.commands;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.unitedlands.combat.UnitedCombat;
import org.unitedlands.combat.player.PvpPlayer;
import org.unitedlands.combat.util.Utils;

import static org.unitedlands.combat.util.Utils.getMessage;
import static org.unitedlands.combat.util.Utils.sendMessageList;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class AdminCmd implements CommandExecutor, TabCompleter {

    private final UnitedCombat plugin;

    public AdminCmd(UnitedCombat plugin) {
        this.plugin = plugin;
    }

    private final List<String> subcommandCompletes = Arrays.asList("forcepvp", "degrade", "sethostility");
    private final List<String> toggleCompletes = Arrays.asList("on", "off");

    @Nullable
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias,
            @NotNull String @NotNull [] args) {
        if (!sender.hasPermission("united.combat.admin")) {
            // Return no suggestions if no permission.
            return Collections.emptyList();
        }

        List<String> options = null;
        String input = args[args.length - 1];

        if (args.length == 1) {
            // First argument is always a player name
            options = Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
        } else if (args.length == 2) {
            options = subcommandCompletes;
        } else if (args.length == 3) {
            if (args[1].equalsIgnoreCase("degrade"))
                options = toggleCompletes;
            else if (args[1].equalsIgnoreCase("sethostility")) {
                options = Arrays.asList("[<value 1-21>]");
            }
        }

        // Send a list with an empty string to prevent Minecraft showing a list of
        // player names by default
        List<String> completions = Arrays.asList("");
        if (options != null) {
            completions = options.stream().filter(s -> s.toLowerCase().startsWith(input.toLowerCase()))
                    .collect(Collectors.toList());
            Collections.sort(completions);
        }
        return completions;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String @NotNull [] args) {
        plugin.getConfig();

        if (!((Player) sender).hasPermission("united.combat.admin")) {
            sender.sendMessage(Utils.getMessage("no-permission"));
            return true;
        }

        // We need at least one username and one subcommand
        if (args.length < 2) {
            sendMessageList((Player) sender, "messages.admin-help-message");
            return true;
        }

        // Get the subject player the command is used on
        OfflinePlayer player = Bukkit.getOfflinePlayer(args[0]);
        if (!player.hasPlayedBefore()) {
            sender.sendMessage(Utils.getMessage("unknown-player"));
            return true;
        }

        PvpPlayer pvpPlayer = new PvpPlayer(player);

        if (args.length == 3) {
            if (args[1].equalsIgnoreCase("sethostility")) {
                var newLevel = Integer.parseInt(args[2]);
                pvpPlayer.setHostility(newLevel);
                sender.sendMessage(getMessage("admin-hostility-updated"));
                return true;
            } else if (args[1].equalsIgnoreCase("degrade")) {
                if (args[2].equalsIgnoreCase("on")) {
                    pvpPlayer.setDegradable(true);
                    sender.sendMessage(getMessage("admin-pvp-degrade-enabled"));
                    return true;
                } else if (args[2].equalsIgnoreCase("off")) {
                    pvpPlayer.setDegradable(false);
                    sender.sendMessage(getMessage("admin-pvp-degrade-disabled"));
                    return true;
                } else {
                    sendMessageList((Player) sender, "messages.admin-help-message");
                    return true;
                }
            } else {
                sendMessageList((Player)sender, "messages.admin-help-message");
                return true;    
            }
        } else if (args.length == 2) {
            if (args[1].equals("forcepvp")) {
                if (pvpPlayer.isImmune()) {
                    pvpPlayer.expireImmunity();
                    sender.sendMessage(getMessage("admin-immunity-removed"));
                    return true;
                } else {
                    sender.sendMessage(getMessage("admin-player-not-immune"));
                    return true;
                }
            } else {
                sendMessageList((Player)sender, "messages.admin-help-message");
                return true;    
            }
        } else {
            sendMessageList((Player) sender, "messages.admin-help-message");
            return true;
        }
    }
}
