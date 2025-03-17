package org.unitedlands.combat.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.jetbrains.annotations.NotNull;
import org.unitedlands.combat.UnitedCombat;

import java.util.List;
import java.util.Objects;

public class Utils {

    private final UnitedCombat unitedPVP;
    public static final MiniMessage miniMessage = MiniMessage.miniMessage();

    public Utils(UnitedCombat unitedPVP) {
        this.unitedPVP = unitedPVP;
    }

    public static boolean isPvP(EntityDamageByEntityEvent event) {
        final Entity damager = event.getDamager();
        final Entity target = event.getEntity();

        if (target instanceof Player && !target.hasMetadata("NPC")) {
            if (damager instanceof Player && !damager.hasMetadata("NPC"))
                return true;
            if (damager instanceof Projectile) {
                final ProjectileSource projSource = ((Projectile) damager).getShooter();
                if (projSource instanceof Player) {
                    final Entity shooter = (Entity) projSource;
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


    @NotNull
    public static Component getMessage(String message) {
        FileConfiguration config = getUnitedPvP().getConfig();
        String prefix = config.getString("messages.prefix");
        String configuredMessage = prefix + config.getString("messages." + message);
        return miniMessage.deserialize(Objects.requireNonNullElseGet(configuredMessage, () -> "<red>Message <yellow>" + message + "<red> could not be found in the config file!"));
    }
    public static void sendMessageList(Player player, String listName) {
        FileConfiguration config = getUnitedPvP().getConfig();
        @NotNull List<String> configuredMessage = config.getStringList(listName);
        for (String line: configuredMessage) {
            player.sendMessage(miniMessage.deserialize(Objects.requireNonNull(line)));
        }
    }

}
