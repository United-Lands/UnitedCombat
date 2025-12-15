package org.unitedlands.combat.listeners;

import dev.geco.gsit.api.event.PrePlayerPlayerSitEvent;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.unitedlands.combat.tagger.CombatTagBossbar;
import org.unitedlands.combat.tagger.CombatTagManager;
import org.unitedlands.combat.util.MessageProvider;
import org.unitedlands.combat.util.Utils;
import org.unitedlands.utils.Messenger;

import java.util.Locale;
import java.util.Map;

public class CombatTagListener implements Listener {
    private final CombatTagManager tags;
    private final CombatTagBossbar bossbar;
    private final FlightListener flight;

    private final MessageProvider messageProvider;

    public CombatTagListener(CombatTagManager tags, CombatTagBossbar combatTagBossbar, FlightListener flight, MessageProvider messageProvider) {
        this.tags = tags;
        this.bossbar = combatTagBossbar;
        this.flight = flight;
        this.messageProvider = messageProvider;
    }

    // Tag for combat when taking PvP damage, attacker and victim.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {

        if (!Utils.isPvP(e)) return;

        final Entity ent = e.getEntity();
        if (!(ent instanceof Player victim)) return;

        Player attacker = null;
        final Entity damager = e.getDamager();
        if (damager instanceof Player p) {
            attacker = p;
        } else if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            attacker = p;
        }

        if (attacker == null) return;

        final boolean victimWasTagged = tags.isTagged(victim);
        final boolean attackerWasTagged = tags.isTagged(attacker);

        tags.tag(victim, attacker);
        bossbar.showFor(victim, attacker);
        flight.disableFlight(victim, attacker);

        if (!victimWasTagged || !attackerWasTagged) {
            Utils.getUnitedPvP().getLogger().info(String.format(
                    "%s tagged %s for combat in world %s.",
                    attacker.getName(),
                    victim.getName(),
                    victim.getWorld().getName()
            ));
        }

        if (!victimWasTagged && tags.isTagged(victim)) {
            Messenger.sendMessage(victim, messageProvider.get("messages.combat-tagged"), null, messageProvider.get("messages.prefix"));

        }
        if (!attackerWasTagged && tags.isTagged(attacker)) {
            Messenger.sendMessage(attacker, messageProvider.get("messages.combat-tagged"), null, messageProvider.get("messages.prefix"));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        final Player p = e.getPlayer();
        if (tags.shouldBlockCommand(p, e.getMessage())) {

            // Find out the command for the logs.
            String msg = e.getMessage().startsWith("/") ? e.getMessage().substring(1) : e.getMessage();
            int space = msg.indexOf(' ');
            String root = (space == -1 ? msg : msg.substring(0, space)).toLowerCase(Locale.ROOT);

            Utils.getUnitedPvP().getLogger().info(String.format(
                    "Player %s tried running forbidden command /%s in world %s during combat.",
                    p.getName(),
                    root,
                    p.getWorld().getName()
            ));

            e.setCancelled(true);
            Messenger.sendMessage(p, messageProvider.get("messages.combat-tagged-blocked-command"), null, messageProvider.get("messages.prefix"));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void OnQuit(PlayerQuitEvent e) {
        final Player quitter = e.getPlayer();
        if (!tags.isTagged(quitter)) return;

        final Player opponent = tags.getLastOpponent(quitter);
        final boolean opponentOnline = opponent != null && opponent.isOnline();
        final String opponentName = opponent != null ? opponent.getName() : "-";

        // Punish only if the logout world is not in unpunishable list
        final boolean punished = tags.shouldPunishOnQuit(quitter);
        if (punished) {
            tags.punishQuitter(quitter);
        }

        if (opponent != null) {
            tags.untagSilently(opponent);
        }

        if (opponentOnline) {
            Messenger.sendMessage(opponent, messageProvider.get("messages.combat-tagged-opponent-logout"), Map.of("0", quitter.getName()), messageProvider.get("messages.prefix"));
        }

        Utils.getUnitedPvP().getLogger().info(String.format(
                "Player %s logged out while fighting opponent %s in world %s and was %spunished.",
                quitter.getName(),
                opponentName,
                quitter.getWorld().getName(),
                punished ? "" : "not "
        ));

    }

    // Clear combat tags for players if one dies.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent e) {
        final Player victim = e.getEntity();

        // Untag the victim
        tags.untag(victim);

        // If they were killed by another player, untag the killer too
        Player killer = victim.getKiller();
        if (killer != null) {
            tags.untag(killer);
        }
    }

    // Fallback protections to block warping away during combat.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();

        if (!Utils.getUnitedPvP().getConfig().getBoolean("combat_tagger.disable-warping"))
            return;

        if (!tags.isTagged(player))
            return;

        // Only block command or plugin-based teleports.
        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        switch (cause) {
            case COMMAND:
            case PLUGIN:
                event.setCancelled(true);
                Messenger.sendMessage(player, messageProvider.get("messages.combat-tagged-blocked-command"), null, messageProvider.get("messages.prefix"));
                break;
            default:
                // Allow other teleport types like ender pearls, portals, death, etc.
                break;
        }
    }

    // Blocks sitting on other players during combat.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrePlayerPlayerSit(PrePlayerPlayerSitEvent event) {
        if (!Utils.getUnitedPvP().getConfig().getBoolean("combat_tagger.disable-sitting")) {
            return;
        }
        Player p = event.getPlayer();
        if (tags.isTagged(p)) {
            event.setCancelled(true);
        }
    }

}
