package org.unitedlands.combat.listeners;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class ExplosionListener implements Listener {
    private final FileConfiguration config;

    public ExplosionListener(FileConfiguration config) {
        this.config = config;
    }

    // End Crystals
    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // Check if a player is damaged by an End Crystal
        if (event.getEntity() instanceof Player && event.getDamager() instanceof EnderCrystal) {
            // Check if nerf-crystal-damage is enabled
            if (config.getBoolean("end-crystals.nerf-crystal-damage", true)) {
                double damageMultiplier = config.getDouble("end-crystals.crystal-damage-percent", 100) / 100.0;
                // Apply custom damage multiplier
                event.setDamage(event.getDamage() * damageMultiplier);
            }
        } else {
            // If nerf-crystal-damage is false, ensure vanilla behavior, intentionally left blank
        }
    }

}
