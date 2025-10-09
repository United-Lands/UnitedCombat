package org.unitedlands.combat.listeners;

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
import org.unitedlands.combat.tagger.CombatTagBossbar;
import org.unitedlands.combat.tagger.CombatTagManager;
import org.unitedlands.combat.util.Utils;

import java.util.Locale;

public class CombatTagListener implements Listener {
    private final CombatTagManager tags;
    private final CombatTagBossbar bossbar;


    public CombatTagListener(CombatTagManager tags, CombatTagBossbar combatTagBossbar) {
        this.tags = tags;
        this.bossbar = combatTagBossbar;
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


        if (!victimWasTagged || !attackerWasTagged) {
            Utils.getUnitedPvP().getLogger().info(String.format(
                    "[CombatTag] %s tagged %s for combat in world %s.",
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
                    "[CombatTag] Player %s tried running forbidden command /%s in world %s during combat.",
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
        tags.punishQuitter(quitter);

        final Player opponent = tags.getLastOpponent(quitter);
        final boolean opponentOnline = opponent != null && opponent.isOnline();
        final String opponentName = opponent != null ? opponent.getName() : "-";
        tags.untagSilently(quitter);

        if (opponent != null) {
            tags.untagSilently(opponent);
        }

        if (opponentOnline) {
            var rep = net.kyori.adventure.text.TextReplacementConfig.builder()
                    .matchLiteral("{0}")
                    .replacement(quitter.getName())
                    .build();
            opponent.sendMessage(org.unitedlands.combat.util.Utils.getMessage("combat-tagged-opponent-logout").replaceText(rep));

            org.unitedlands.combat.UnitedCombat plugin = org.unitedlands.combat.util.Utils.getUnitedPvP();
            plugin.getLogger().info(String.format(
                    "[CombatTag] Player %s logged out during combat while fighting %s in world %s.",
                    quitter.getName(),
                    opponentName,
                    quitter.getWorld().getName()
            ));
        }

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
}
