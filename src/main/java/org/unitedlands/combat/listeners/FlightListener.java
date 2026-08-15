package org.unitedlands.combat.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.*;
import org.unitedlands.combat.tagger.CombatTagManager;
import org.unitedlands.combat.util.MessageProvider;
import org.unitedlands.utils.Messenger;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class FlightListener implements Listener {

    private final CombatTagManager tags;
    private final Set<UUID> softLanding = new HashSet<>();

    private boolean pluginFlightEnabled;
    private boolean elytraFlightEnabled;
    private boolean softLandingEnabled;

    private final MessageProvider messageProvider;

    public FlightListener(CombatTagManager tags, MessageProvider messageProvider) {
        this.tags = tags;
        this.messageProvider = messageProvider;
        reload();
    }

    public void reload() {
        var c = tags.getPlugin().getConfig();
        pluginFlightEnabled  = c.getBoolean("combat_tagger.flight.plugin-flight", true);
        elytraFlightEnabled = c.getBoolean("combat_tagger.flight.elytra-flight", true);
        softLandingEnabled = c.getBoolean("combat_tagger.flight.soft-landing", true);
        softLanding.clear();
    }

    public void disableFlight(Player... players) {
       if (players == null) return;
       for (Player p : players) {
           if (p == null) continue;
           if (!tags.isTagged(p)) continue;

           // Command/plugin flight
           if (pluginFlightEnabled && (p.isFlying() || p.getAllowFlight())) {
               p.setFlying(false);
               p.setAllowFlight(false);
               if (softLandingEnabled) {
                   softLanding.add(p.getUniqueId());
               }
           }

           // Elytra (active glide)
           if (elytraFlightEnabled && p.isGliding()) {
               p.setGliding(false); // immediately closes wings
               if (softLandingEnabled) {
                   softLanding.add(p.getUniqueId());
               }
           }
       }
    }

    // Block non-command based flight enablers.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onToggleFlight(PlayerToggleFlightEvent e) {
        Player p = e.getPlayer();

        if (e.isFlying() && tags.isTagged(p)) {
            e.setCancelled(true);
        }
    }

    // Block starting elytra gliding while tagged.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onToggleGlide(EntityToggleGlideEvent e) {
        if (!elytraFlightEnabled) return;
        if (!(e.getEntity() instanceof Player p)) return;
        if (e.isGliding() && tags.isTagged(p)) {
            e.setCancelled(true);
            Messenger.sendMessage(p, messageProvider.get("messages.combat-tagged-blocked-elytra"), null, messageProvider.get("messages.prefix"));
        }
    }

    // Cancel riptides and send warning.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRiptide(PlayerRiptideEvent e) {
        if (!elytraFlightEnabled) return;
        Player p = e.getPlayer();
        if (!tags.isTagged(p)) return;

        Messenger.sendMessage(p, messageProvider.get("messages.combat-tagged-blocked-trident"), null, messageProvider.get("messages.prefix"));

        if (p.isGliding()) p.setGliding(false);
        if (softLandingEnabled) {
            softLanding.add(p.getUniqueId());
        }
    }

    // Lock player movement during attempted riptide.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRiptideMove(PlayerMoveEvent e) {
        if (!elytraFlightEnabled) return;
        Player p = e.getPlayer();
        if (!p.isRiptiding()) return;
        if (!tags.isTagged(p)) return;
        e.setCancelled(true);
    }

    // Cancel any initial damage from falling.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFallDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (e.getCause() != EntityDamageEvent.DamageCause.FALL) return;

        UUID id = p.getUniqueId();
        if (softLanding.remove(id)) {
            e.setCancelled(true);
        }
    }

    // Remove entries on logout.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onQuit(PlayerQuitEvent e) {
        softLanding.remove(e.getPlayer().getUniqueId());
    }

}
