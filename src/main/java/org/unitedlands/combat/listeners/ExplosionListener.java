package org.unitedlands.combat.listeners;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.RespawnAnchor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Player;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public class ExplosionListener implements Listener {
    private final FileConfiguration config;

    public ExplosionListener(FileConfiguration config) {
        this.config = config;
    }

    // End Crystals
    @EventHandler
    public void onEntityDamageByCrystal(EntityDamageByEntityEvent event) {
        // Check if a player is damaged by an End Crystal.
        if (event.getEntity() instanceof Player && event.getDamager() instanceof EnderCrystal) {
            // Check if nerf-crystal-damage is enabled.
            if (config.getBoolean("end-crystals.nerf-crystal-damage", true)) {
                double damageMultiplier = config.getDouble("end-crystals.crystal-damage-percent", 100) / 100.0;
                // Apply custom damage multiplier.
                event.setDamage(event.getDamage() * damageMultiplier);
            }
        } else {
            // If nerf-crystal-damage is false, ensure vanilla behavior, intentionally left blank.
        }
    }

    // Respawn Anchors.
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Ensure the interact event to just charging anchors.
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null || clickedBlock.getType() != Material.RESPAWN_ANCHOR) return;
        if (event.getItem() == null || event.getItem().getType() != Material.GLOWSTONE) return;
        if (clickedBlock.getWorld().getEnvironment() == World.Environment.NETHER) return;
        if (config.getBoolean("respawn-anchors.nerf-anchor-damage", false)) return;

        // Retrieve the respawn anchor block data.
        BlockData data = clickedBlock.getBlockData();
        if (!(data instanceof RespawnAnchor)) return;

        event.setCancelled(true);
    }

    // TNT Minecarts
    @EventHandler
    public void onEntityDamageByMinecart(EntityDamageByEntityEvent event) {
        // Ensure the damaged entity is a player.
        if (!(event.getEntity() instanceof Player)) return;
        // Ensure the damager is a TNT minecart.
        if (!(event.getDamager() instanceof ExplosiveMinecart)) return;
        // Check if nerf-minecart-damage is enabled.
        if (config.getBoolean("minecarts.nerf-minecart-damage", true)) {
            double damageMultiplier = config.getDouble("minecarts.minecart-damage-percent", 100) / 100.0;
            event.setDamage(event.getDamage() * damageMultiplier);
        } else {
            // If nerf-minecart-damage is false, ensure vanilla behavior, intentionally left blank.
        }
    }
}
