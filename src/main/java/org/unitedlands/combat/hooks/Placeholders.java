package org.unitedlands.combat.hooks;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.unitedlands.combat.player.PvpPlayer;

public class Placeholders extends PlaceholderExpansion {

    @Override
    public @NotNull String getAuthor() {
        return "Maroon28";
    }

    @Override
    public @NotNull String getIdentifier() {
        return "unitedpvp";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player.getPlayer() != null) {
            PvpPlayer pvpPlayer = new PvpPlayer((Player) player);
            if (params.equalsIgnoreCase("status")) {
                int hostility = pvpPlayer.getHostility();
                return pvpPlayer.getIconHex(hostility) + pvpPlayer.getStatusIcon();
            } else if (params.equalsIgnoreCase("status-string")) {
                pvpPlayer.getStatusKey();
            } else if (params.equalsIgnoreCase("is-immune")) {
                return String.valueOf(pvpPlayer.isImmune());
            }
        }
        return null;
    }
}
