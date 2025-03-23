package org.unitedlands.combat.listeners;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.RespawnAnchor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Player;
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

    // Cancel charging of respawn anchors if already at or above 4 charges, so they can't explode.
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Ensure the interact event to just charging anchors.
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null || clickedBlock.getType() != Material.RESPAWN_ANCHOR) return;
        if (event.getItem() == null || event.getItem().getType() != Material.GLOWSTONE) return;
        if (clickedBlock.getWorld().getEnvironment() == World.Environment.NETHER) return;

        // Retrieve the respawn anchor block data.
        BlockData data = clickedBlock.getBlockData();
        if (!(data instanceof RespawnAnchor anchor)) return;

        // If the anchor is already charged with 4 or more charges, cancel.
        if (anchor.getCharges() >= 4) {
            event.setCancelled(true);
        }
    }
}
