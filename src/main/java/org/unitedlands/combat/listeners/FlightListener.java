package org.unitedlands.combat.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.unitedlands.combat.tagger.CombatTagManager;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class FlightListener implements Listener {

    private final CombatTagManager tags;
    private final Set<UUID> softLanding = new HashSet<>();

    private boolean pluginFlightEnabled;
    private boolean elytraFlightEnabled;
    private boolean softLandingEnabled;

    public FlightListener(CombatTagManager tags) {
        this.tags = tags;
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
        }
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
