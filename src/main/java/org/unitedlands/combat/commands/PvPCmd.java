package org.unitedlands.combat.commands;

import net.kyori.adventure.text.TextReplacementConfig;
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
import org.unitedlands.combat.util.Utils;

import static org.unitedlands.combat.util.Utils.getMessage;
import static org.unitedlands.combat.util.Utils.sendMessageList;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class PvPCmd implements CommandExecutor, TabCompleter {

    private final List<String> subcommandCompletes = Arrays.asList("status", "degrade", "mute", "on");
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
            options = subcommandCompletes;
        } else if (args.length == 2) {
            // Only degrade needs additional options
            if (args[0].equalsIgnoreCase("degrade"))
                options = toggleCompletes;
        }

        List<String> completions = null;
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
        if (args.length == 0) {
            sendMessageList(player, "messages.help-message");
            return true;
        }

        if (args.length == 1) {
            if (args[0].equalsIgnoreCase("status")) {
                returnPvPStatus(player, player);
                return true;
            }
            if (args[0].equalsIgnoreCase("mute")) {
                setNotification(player, !hasNotification(player));
            }

            if (args[0].equals("on")) {
                if (pvpPlayer.isImmune()) {
                    pvpPlayer.expireImmunity();
                    player.sendMessage(getMessage("immunity-removed"));
                    return true;
                }
                player.sendMessage(getMessage("you-are-not-immune"));
            }
        } else if (args.length == 2) {
            if (args[0].equals("degrade")) {
                if (args[1].equals("on")) {
                    pvpPlayer.setDegradable(true);
                    player.sendMessage(getMessage("pvp-degrade-enabled"));
                    return true;
                }
                if (args[1].equals("off")) {
                    pvpPlayer.setDegradable(false);
                    player.sendMessage(getMessage("pvp-degrade-disabled"));
                    return true;
                }
            } else if (args[0].equals("status")) {
                // Get the status of another player
                var otherPlayer = Bukkit.getOfflinePlayer(args[1]);
                if (!otherPlayer.hasPlayedBefore()) {
                    sender.sendMessage(Utils.getMessage("unknown-player"));
                    return true;
                }
                returnPvPStatus(player, otherPlayer);
                return true;
            }
        }

        return false;
    }

    private void returnPvPStatus(Player sender, OfflinePlayer player) {
        PvpPlayer pvpPlayer = new PvpPlayer(player);
        String status = pvpPlayer.getStatus().name().toLowerCase();

        TextReplacementConfig nameReplacer = TextReplacementConfig.builder()
                .match("<name>")
                .replacement(player.getName())
                .build();
        TextReplacementConfig statusReplacer = TextReplacementConfig.builder()
                .match("<status>")
                .replacement(status)
                .build();
        TextReplacementConfig hostilityReplacer = TextReplacementConfig.builder()
                .match("<hostility>")
                .replacement(String.valueOf(pvpPlayer.getHostility()))
                .build();

        sender.sendMessage(getMessage("pvp-status")
                .replaceText(nameReplacer)
                .replaceText(statusReplacer)
                .replaceText(hostilityReplacer));
    }

    private void setNotification(Player player, boolean value) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey(Utils.getUnitedPvP(), "neutrality-notif");
        pdc.set(key, PersistentDataType.BYTE, value ? (byte) 0 : (byte) 1);
        if (value)
            player.sendMessage(Utils.getMessage("muted-notif"));
        else
            player.sendMessage(Utils.getMessage("unmuted-notif"));
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
