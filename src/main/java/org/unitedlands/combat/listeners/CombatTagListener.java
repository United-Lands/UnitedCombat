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
import org.unitedlands.combat.util.Utils;

import java.util.Locale;

public class CombatTagListener implements Listener {
    private final CombatTagManager tags;
    private final CombatTagBossbar bossbar;
    private final FlightListener flight;

    public CombatTagListener(CombatTagManager tags, CombatTagBossbar combatTagBossbar, FlightListener flight) {
        this.tags = tags;
        this.bossbar = combatTagBossbar;
        this.flight = flight;
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
            victim.sendMessage(Utils.getMessage("combat-tagged"));
        }
        if (!attackerWasTagged && tags.isTagged(attacker)) {
            attacker.sendMessage(Utils.getMessage("combat-tagged"));
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
            p.sendMessage(Utils.getMessage("combat-tagged-blocked-command"));
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
            var rep = net.kyori.adventure.text.TextReplacementConfig.builder()
                    .matchLiteral("{0}")
                    .replacement(quitter.getName())
                    .build();
            opponent.sendMessage(org.unitedlands.combat.util.Utils.getMessage("combat-tagged-opponent-logout").replaceText(rep));
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
                player.sendMessage(Utils.getMessage("combat-tagged-blocked-command"));
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
