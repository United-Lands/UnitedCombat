package org.unitedlands.combat.listeners;

import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.unitedlands.combat.listeners.interfaces.IGraveListener;
import org.unitedlands.utils.Logger;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;

import de.jeff_media.angelchest.AngelChest;
import de.jeff_media.angelchest.events.AngelChestSpawnEvent;

public class AngelChestGraveListener implements IGraveListener, Listener {

    @EventHandler

    public void onGraveSpawn(AngelChestSpawnEvent event) {
        AngelChest chest = event.getAngelChest();
        Location graveLocation = chest.getBlock().getLocation();
        OfflinePlayer player = chest.getPlayer();
        if (isInOutlawTown(player, graveLocation)) {
            Logger.log("[UnitedCombat] Player is outlaw in town, unlocking grave.");
            chest.setProtected(false);
        }
    }

    private boolean isInOutlawTown(OfflinePlayer player, Location graveLocation) {
        var towny = TownyAPI.getInstance();
        Resident resident = towny.getResident(player.getUniqueId());
        if (resident == null)
            return false;
        // Make sure they're not in the wilderness
        if (towny.isWilderness(graveLocation))
            return false;
        Town town = towny.getTownBlock(graveLocation).getTownOrNull();
        // This shouldn't happen but adding it to be safe.
        if (town == null)
            return false;

        return town.hasOutlaw(resident);
    }
}
