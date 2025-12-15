package org.unitedlands.combat.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.unitedlands.combat.UnitedCombat;

public class Utils {

    public static boolean isPvP(EntityDamageByEntityEvent event) {
        final Entity damager = event.getDamager();
        final Entity target = event.getEntity();

        if (target instanceof Player && !target.hasMetadata("NPC")) {
            if (damager instanceof Player && !damager.hasMetadata("NPC"))
                return true;
            if (damager instanceof Projectile) {
                final ProjectileSource projSource = ((Projectile) damager).getShooter();
                if (projSource instanceof Player shooter) {
                    if (!shooter.equals(target) && !shooter.hasMetadata("NPC"))
                        return !(event.getDamage() == 0);
                }
            }
        }
        return false;
    }

    public static UnitedCombat getUnitedPvP() {
        return (UnitedCombat) Bukkit.getPluginManager().getPlugin("UnitedCombat");
    }

}
