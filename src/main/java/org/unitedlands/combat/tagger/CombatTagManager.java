package org.unitedlands.combat.tagger;


import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.unitedlands.combat.UnitedCombat;
import org.unitedlands.combat.util.MessageProvider;
import org.unitedlands.utils.Messenger;

import java.util.*;
import java.util.concurrent.TimeUnit;

public final class CombatTagManager {

    public JavaPlugin getPlugin() { return plugin; }

    private final JavaPlugin plugin;
    private final MessageProvider messageProvider;

    private final Map<UUID, Long> expiryByPlayer =  new HashMap<>();
    private final Map<UUID, UUID> lastOpponent = new HashMap<>();

    // Load config settings.
    private boolean enabled;
    private int durationSeconds;
    private Set<String> blockedCommands;
    private Set<String> blockedWorlds;
    private String bypassPermission;
    private Set<String> unpunishableWorlds;
    private boolean punishmentEscalation;
    private List<List<String>> punishmentSteps;

    public CombatTagManager(UnitedCombat plugin, MessageProvider messageProvider) {
        this.plugin = plugin;
        this.messageProvider = messageProvider;
        reload();
    }

    // Get new values from the config file on plugin reload.
    public void reload() {
        FileConfiguration c = plugin.getConfig();
        enabled = c.getBoolean("combat_tagger.enabled", true);
        durationSeconds = Math.max(1, c.getInt("combat_tagger.duration"));
        blockedCommands = new HashSet<>();
        for (String cmd : c.getStringList("combat_tagger.blocked-commands")) {
            if (cmd != null && !cmd.isEmpty()) {
                blockedCommands.add(cmd.toLowerCase(Locale.ROOT));
            }
        }
        blockedWorlds = new HashSet<>(c.getStringList("combat_tagger.blocked-worlds"));
        bypassPermission = c.getString("combat_tagger.bypass-permission");
        unpunishableWorlds = new HashSet<>(c.getStringList("combat_tagger.unpunishable-worlds"));
        punishmentEscalation = c.getBoolean("combat_tagger.combat-log-punishments.punishment-escalation");
        punishmentSteps = new ArrayList<>();

        org.bukkit.configuration.ConfigurationSection base =
                c.getConfigurationSection("combat_tagger.combat-log-punishments");
        if (base != null) {
            for (String key : base.getKeys(false)) {
                if ("punishment-escalation".equalsIgnoreCase(key)) continue;
                // Only accept numbers.
                try {
                    Integer.parseInt(key);
                    List<String> cmds = c.getStringList("combat_tagger.combat-log-punishments." + key);
                    if (!cmds.isEmpty()) {
                        punishmentSteps.add(new ArrayList<>(cmds));
                    }
                } catch (NumberFormatException ignore) {

                }
            }
            punishmentSteps.sort(Comparator.comparingInt(listKey -> 0));
        }
    }

    // Tag a player.
    public void tag(Player... players) {

        if (!enabled || players == null)
            return;

        final long expiresAt = System.currentTimeMillis()
                + TimeUnit.SECONDS.toMillis(durationSeconds);

        Player victim = players.length > 0 ? players[0] : null;
        Player attacker = players.length > 1 ? players[1] : null;

        for (Player p : players) {

            if (p == null)
                continue;

            if (isBypassed(p) || isWorldBlocked(p))
                continue;

            expiryByPlayer.put(p.getUniqueId(), expiresAt);
        }

        // Record attacker and victim relationship.
        if (victim != null && attacker != null) {
            lastOpponent.put(victim.getUniqueId(), attacker.getUniqueId());
            lastOpponent.put(attacker.getUniqueId(), victim.getUniqueId());
        }

    }

    // Checks if a player is combat tagged.
    public boolean isTagged(Player p) {
        if (!enabled || p == null) return false;
        if (isBypassed(p) || isWorldBlocked(p)) return false;

        final UUID id = p.getUniqueId();
        final Long expiry = expiryByPlayer.get(id);
        if (expiry == null) return false;

        final long now = System.currentTimeMillis();
        if (now >= expiry) {

            // Notify player and log
            Messenger.sendMessage(p, messageProvider.get("messages.combat-tagged-expired"), null, messageProvider.get("messages.prefix"));
            plugin.getLogger().info(String.format(
                    "Combat Tag expired for %s in world %s due to timeout.",
                    p.getName(),
                    p.getWorld().getName()
            ));

            expiryByPlayer.remove(id);
            return false;
        }

        return true;
    }

    //  Check how long the combat tag has left, in seconds.
    public long remainingSeconds(Player p) {
        if (!enabled || p ==  null) return 0L;
        final Long expiry = expiryByPlayer.get(p.getUniqueId());
        if (expiry == null) return 0L;

        long deltaMs = expiry - System.currentTimeMillis();
        if (deltaMs <= 0) return 0L;
        return (long) Math.ceil(deltaMs/1000.0);
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    // Untag the player.
    public void untag(Player p) {
        if (p == null) return;

        if (expiryByPlayer.remove(p.getUniqueId()) != null) {
            Messenger.sendMessage(p, messageProvider.get("messages.combat-tagged-expired"), null, messageProvider.get("messages.prefix"));
            plugin.getLogger().info(String.format(
                    "Combat Tag cleared for %s in world %s due to death",
                    p.getName(),
                    p.getWorld().getName()
            ));
        }

        expiryByPlayer.remove(p.getUniqueId());
    }

    // Untag players without warning, used for special untag cases.
    public void untagSilently(Player p) {
        if (p == null) return;
        expiryByPlayer.remove(p.getUniqueId());
    }

    // Check if a player is running a blacklisted command during combat.
    public boolean shouldBlockCommand(Player p, String rawMessage) {
        if (!enabled || p == null || rawMessage == null) return false;
        if (!isTagged(p)) return false;

        // Normalise by stripping leading slash, taking the first argument as lowercase.
        String msg = rawMessage.startsWith("/") ? rawMessage.substring(1) : rawMessage;
        int space = msg.indexOf(' ');
        String root = (space == -1 ? msg : msg.substring(0, space)).toLowerCase(Locale.ROOT);

        return blockedCommands.contains(root);
    }

    // Run the config defined commands to anyone that logs out during combat.
    public void punishQuitter(Player p) {
        if (!enabled || p == null) return;
        if (punishmentSteps == null || punishmentSteps.isEmpty()) return;

        // Skip punishments in disabled worlds and don't increment counter.
        if (!shouldPunishOnQuit(p)) {
            return;
        }

        // Read current offence count from the player file
        org.unitedlands.combat.player.PvpPlayer pp = new org.unitedlands.combat.player.PvpPlayer(p);
        int current = pp.getCombatLogPunishmentCount();

        // Decide which step to run.
        int maxIndex = punishmentSteps.size() - 1;
        int stepIndex;
        if (punishmentEscalation) {
            stepIndex = Math.min(current, maxIndex);
        } else {
            stepIndex = 0;
        }

        // Execute commands for the chosen step.
        final String name = p.getName();
        for (String template : punishmentSteps.get(stepIndex)) {
            if (template == null || template.isEmpty()) continue;
            String cmd = template.replace("%player_name%", name);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }

        // Only increment if escalation is enabled AND they haven't exceeded the last step.
        if (punishmentEscalation && current < (maxIndex + 1)) {
            pp.setCombatLogPunishmentCount(current + 1);
        }
    }

    // Check if the player has bypass permissions.
    private boolean isBypassed(Player p) {
        return bypassPermission != null
                && !bypassPermission.isEmpty()
                && p.hasPermission(bypassPermission);
    }

    // Check if the player is in an ignored world.
    private boolean isWorldBlocked(Player p) {
        return blockedWorlds != null && blockedWorlds.contains(p.getWorld().getName());
    }

    public Player getLastOpponent(Player p) {
        UUID other = lastOpponent.get(p.getUniqueId());
        return (other == null) ? null : Bukkit.getPlayer(other);
    }

    // Decide if a world has combat logging punishments.
    public boolean shouldPunishOnQuit(Player p) {
        if (!enabled || p == null) return false;
        if (!isTagged(p)) return false;
        String world = p.getWorld().getName();
        return unpunishableWorlds == null || !unpunishableWorlds.contains(world);
    }

}
