package org.unitedlands.combat.commands;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.unitedlands.combat.player.PvpPlayer;
import org.unitedlands.combat.util.MessageProvider;
import org.unitedlands.combat.util.Utils;
import org.unitedlands.utils.Messenger;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PvPCmd implements CommandExecutor, TabCompleter {

    private final List<String> subcommandCompletes = Arrays.asList("status", "degrade", "mute", "on");
    private final List<String> toggleCompletes = Arrays.asList("on", "off");
    private final MessageProvider messageProvider;

    public PvPCmd(MessageProvider messageProvider) {
        this.messageProvider = messageProvider;
    }

    @Nullable
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias,
            @NotNull String @NotNull [] args) {

        List<String> options = null;
        String input = args[args.length - 1];

        if (args.length == 1) {
            options = subcommandCompletes;
        } else if (args.length == 2) {
            // Only degrade needs additional options
            if (args[0].equalsIgnoreCase("degrade"))
                options = toggleCompletes;
        }

        // Send a list with an empty string to prevent Minecraft showing a list of player names by default
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
            String[] args) {

        Player player = (Player) sender;
        PvpPlayer pvpPlayer = new PvpPlayer(player);

        if (args.length == 1) {
            if (args[0].equalsIgnoreCase("status")) {
                returnPvPStatus(player, player);
                return true;
            } else if (args[0].equalsIgnoreCase("mute")) {
                setNotification(player, !hasNotification(player));
            } else if (args[0].equals("on")) {
                if (pvpPlayer.isImmune()) {
                    pvpPlayer.expireImmunity();
                    Messenger.sendMessage(sender, messageProvider.get("messages.immunity-removed"), null, messageProvider.get("messages.prefix"));
                    return true;
                }
                Messenger.sendMessage(sender, messageProvider.get("messages.you-are-not-immune"), null, messageProvider.get("messages.prefix"));
            }
            else {
                Messenger.sendMessage(sender, messageProvider.getList("messages.help-message"));
                return true;
            }
        } else if (args.length == 2) {
            if (args[0].equals("degrade")) {
                if (args[1].equals("on")) {
                    pvpPlayer.setDegradable(true);
                    Messenger.sendMessage(sender, messageProvider.get("messages.pvp-degrade-enabled"), null, messageProvider.get("messages.prefix"));
                    return true;
                } else if (args[1].equals("off")) {
                    pvpPlayer.setDegradable(false);
                    Messenger.sendMessage(sender, messageProvider.get("messages.pvp-degrade-disabled"), null, messageProvider.get("messages.prefix"));
                    return true;
                }
                else {
                    Messenger.sendMessage(sender, messageProvider.getList("messages.help-message"));
                    return true;
                }
            } else if (args[0].equals("status")) {
                // Get the status of another player
                var otherPlayer = Bukkit.getOfflinePlayer(args[1]);
                if (!otherPlayer.hasPlayedBefore()) {
                    Messenger.sendMessage(sender, messageProvider.get("messages.unknown-player"), null, messageProvider.get("messages.prefix"));
                    return true;
                }
                returnPvPStatus(player, otherPlayer);
                return true;
            } else {
                Messenger.sendMessage(sender, messageProvider.getList("messages.help-message"));
                return true;
            }
        } else {
            Messenger.sendMessage(sender, messageProvider.getList("messages.help-message"));
            return true;
        }

        return false;
    }

    private void returnPvPStatus(Player sender, OfflinePlayer player) {
        PvpPlayer pvpPlayer = new PvpPlayer(player);
        String status = pvpPlayer.getStatusKey();
        Messenger.sendMessage(sender, messageProvider.get("messages.unknown-player"), 
            Map.of("name", player.getName(), "status", status, "hostility", String.valueOf(pvpPlayer.getHostility())), 
            messageProvider.get("messages.prefix"));

    }

    private void setNotification(Player player, boolean value) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey(Utils.getUnitedPvP(), "neutrality-notif");
        pdc.set(key, PersistentDataType.BYTE, value ? (byte) 0 : (byte) 1);
        if (value)
            Messenger.sendMessage(player, messageProvider.get("messages.muted-notif"), null, messageProvider.get("messages.prefix"));
        else
            Messenger.sendMessage(player, messageProvider.get("messages.unmuted-notif"), null, messageProvider.get("messages.prefix"));
    }

    private boolean hasNotification(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey(Utils.getUnitedPvP(), "neutrality-notif");
        if (pdc.has(key)) {
            byte stored = pdc.get(key, PersistentDataType.BYTE);
            return stored == 1; // 1 is on, 0 is off.
        }
        return true; // true by default.
    }
}
