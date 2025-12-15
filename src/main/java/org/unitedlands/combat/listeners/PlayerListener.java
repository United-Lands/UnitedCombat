package org.unitedlands.combat.listeners;

import com.google.common.collect.Multimap;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.event.player.PlayerKilledPlayerEvent;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;

import org.apache.commons.lang3.time.DurationFormatUtils;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Arrow;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.unitedlands.combat.UnitedCombat;
import org.unitedlands.combat.player.PvpPlayer;
import org.unitedlands.combat.util.MessageProvider;
import org.unitedlands.combat.util.Utils;
import org.unitedlands.utils.Messenger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class PlayerListener implements Listener {
    private final UnitedCombat unitedCombat;
    private final MessageProvider messageProvider;
    private final TownyAPI towny = TownyAPI.getInstance();
    HashMap<EnderCrystal, UUID> crystalMap = new HashMap<>();

    public PlayerListener(UnitedCombat unitedCombat, MessageProvider messageProvider) {
        this.unitedCombat = unitedCombat;
        this.messageProvider = messageProvider;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PvpPlayer pvpPlayer = new PvpPlayer(player);
        // Create the file if they haven't played before, or don't have a file.
        if (!player.hasPlayedBefore() || !pvpPlayer.getPlayerFile().exists()) {
            pvpPlayer.createFile();
            return; // No need to run the rest of the logic here, so just move on.
        }

        tryNeutralityRemoval(player);

        long lastChangeTime = pvpPlayer.getLastHostilityChangeTime();
        int dayDifference = getDaysPassed(lastChangeTime);

        if (dayDifference == 0)
            return;

        // Update the hostility for each full day we log.
        for (int i = 0; i < dayDifference; i++) {
            pvpPlayer.updatePlayerHostility();
        }

    }

    private void tryNeutralityRemoval(Player player) {
        Town town = towny.getResident(player.getUniqueId()).getTownOrNull();
        // Player doesn't have a town
        if (town == null)
            return;
        PvpPlayer pvpPlayer = new PvpPlayer(player);
        // Method was called, but player in question was not hostile.
        if (!pvpPlayer.isHostile() && !pvpPlayer.isAggressive())
            return;

        // Kick them out and notify them
        if (town.isNeutral()) {
            town.setNeutral(false);
            Messenger.sendMessage(player, messageProvider.get("messages.kicked-out-of-neutrality"), null, messageProvider.get("messages.prefix"));
            // Notify the mayor if they're online
            Resident mayor = town.getMayor();
            if (mayor.isOnline()) {
                Messenger.sendMessage(mayor.getPlayer(), messageProvider.get("messages.kicked-out-of-neutrality-mayor"), null, messageProvider.get("messages.prefix"));
            }
        }
        if (town.hasNation()) {
            Nation nation = town.getNationOrNull();
            if (nation.isNeutral()) {
                nation.setNeutral(false);
                Player king = nation.getKing().getPlayer();
                if (king != null) {
                    Messenger.sendMessage(king, messageProvider.get("messages.kicked-out-of-neutrality-mayor"), null, messageProvider.get("messages.prefix"));
                }
            }
        }
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {

        if (Utils.isPvP(event)) {

            Player target = (Player) event.getEntity();
            Player damager = getAttacker(event.getDamager());

            PvpPlayer pvpDamager = new PvpPlayer(damager);
            PvpPlayer pvpTarget = new PvpPlayer(target);

            if (pvpTarget.isImmune()) {
                event.setCancelled(true);
                Messenger.sendMessage(damager, messageProvider.get("messages.target-immune"), null, messageProvider.get("messages.prefix"));
            }

            if (pvpDamager.isImmune()) {
                event.setCancelled(true);
                Messenger.sendMessage(damager, messageProvider.get("messages.you-are-immune"), Map.of("time", DurationFormatUtils
                                .formatDuration(TimeUnit.DAYS.toMillis(1) - pvpDamager.getImmunityTime(), "HH:mm:ss")), messageProvider.get("messages.prefix"));
            }

            if (damager.getInventory().getItemInMainHand().getType() == Material.MACE) {
                if (unitedCombat.getConfig().getBoolean("mace.nerf-mace-damage")) {
                    var max = unitedCombat.getConfig().getDouble("mace.mace_max_damage", Double.MAX_VALUE);
                    if (event.getFinalDamage() > max)
                        event.setDamage(max);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerKillPlayer(PlayerKilledPlayerEvent event) {
        Player killer = event.getKiller();
        Player victim = event.getVictim();

        // Don't increase hostility in exempted worlds.
        var exempt = unitedCombat.getConfig().getStringList("hostility-exempt-worlds");
        String worldName = killer.getWorld().getName();
        if (exempt.contains(worldName))
            return;

        PvpPlayer killerPvP = new PvpPlayer(killer);
        PvpPlayer victimPvP = new PvpPlayer(victim);
        // If both players are town mates or nation mates, it was likely just a small
        // quarrel or a duel
        // No need to do anything.
        if (areRelated(killer, victim))
            return;

        // If an outlaw is killed, don't increase the hostility
        if (isOutlawKill(event))
            return;

        // If both are defensive, the killer is being hostile. Increase their hostility.
        if (killerPvP.isDefensive() && victimPvP.isDefensive()) {
            killerPvP.setHostility(killerPvP.getHostility() + 1);
        }

        // If the killer is already hostile/aggressive, and they kill a defensive player
        // that signifies a higher level of hostility, therefore increase by 2 points.
        if ((killerPvP.isAggressive() || killerPvP.isHostile()) && victimPvP.isDefensive()) {
            killerPvP.setHostility(killerPvP.getHostility() + 2);
            tryNeutralityRemoval(killer);
            return;
        }
        // if the killer is aggressive or hostile, killer becomes more hostile.
        if (killerPvP.isAggressive() || killerPvP.isHostile()) {
            killerPvP.setHostility(killerPvP.getHostility() + 1);
            tryNeutralityRemoval(killer);
        }
    }

    private boolean isOutlawKill(PlayerKilledPlayerEvent event) {
        var killerRes = event.getKillerRes();
        var victimRes = event.getVictimRes();

        var killerTown = killerRes.getTownOrNull();
        if (killerTown == null)
            return false;

        return killerTown.hasOutlaw(victimRes);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!event.getAction().equals(Action.RIGHT_CLICK_BLOCK))
            return;
        if (!event.getClickedBlock().getType().equals(Material.OBSIDIAN))
            return;
        // The player clicked an obsidian but didn't have a crystal
        if (!event.getMaterial().equals(Material.END_CRYSTAL))
            return;

        Bukkit.getScheduler().runTask(unitedCombat, () -> {
            List<Entity> entities = event.getPlayer().getNearbyEntities(4, 4, 4);
            for (Entity entity : entities) {
                // Get the nearest crystal
                if (entity instanceof EnderCrystal crystal) {
                    Block belowCrystal = crystal.getLocation().getBlock().getRelative(BlockFace.DOWN);
                    // Check if the block below the newly spawned crystal is the same as the block
                    // the player
                    // clicked in the event.
                    if (event.getClickedBlock().equals(belowCrystal)) {
                        // Save it.
                        crystalMap.put(crystal, event.getPlayer().getUniqueId());
                        break;
                    }
                }
            }
        });
    }

    @EventHandler
    public void onPlayerDeathByCrystal(PlayerDeathEvent event) {
        Player victim = event.getEntity();

        // Was the last damage caused by an entity?
        var last = victim.getLastDamageCause();
        if (!(last instanceof EntityDamageByEntityEvent edbe))
            return;

        // Was that entity an ender crystal?
        if (!(edbe.getDamager() instanceof EnderCrystal crystal))
            return;

        // Exempt defined worlds
        var exempt = unitedCombat.getConfig().getStringList("hostility-exempt-worlds");
        if (exempt.contains(victim.getWorld().getName()))
            return;

        // Is the crystal registered to a placer?
        UUID placerId = crystalMap.get(crystal);
        if (placerId == null)
            return;

        Player placer = Bukkit.getPlayer(placerId);
        if (placer == null || placer.equals(victim))
            return; // ignore suicide or offline placer.

        // Increase hostility.
        PvpPlayer pvpPlacer = new PvpPlayer(placer);
        pvpPlacer.setHostility(pvpPlacer.getHostility() + 1);
    }

    @EventHandler
    public void onCrystalExplode(EntityCombustEvent event) {
        if (event.getEntity() instanceof EnderCrystal crystal) {
            // Remove the crystal from the map a second after it explodes, to have time to
            // detect player deaths etc.
            unitedCombat.getServer().getScheduler().runTaskLater(unitedCombat, () -> crystalMap.remove(crystal), 20L);
        }
    }

    @EventHandler
    public void onLavaPlace(PlayerBucketEmptyEvent event) {
        if (!event.getBucket().equals(Material.LAVA_BUCKET))
            return;
        Player player = event.getPlayer();
        var nearby = event.getBlock().getLocation().getNearbyEntities(2, 2, 2);
        for (Entity entity : nearby) {
            if (entity instanceof Player nearbyPlayer) {
                PvpPlayer pvpPlayer = new PvpPlayer(nearbyPlayer);
                if (pvpPlayer.isImmune()) {
                    event.setCancelled(true);
                    Messenger.sendMessage(player, messageProvider.get("messages.target-immune"), null, messageProvider.get("messages.prefix"));
                }
            }
        }
    }

    @EventHandler
    public void onArrowShoot(EntityShootBowEvent event) {
        if (!(event.getProjectile() instanceof Arrow arrow))
            return;
        if (arrow.getCustomEffects().size() > 1)
            arrow.clearCustomEffects();
    }

    // Attribute swap fix (taken from https://github.com/AutumnVN/hit-swap-fix/)

    // Listens to changes to the item held by a player and refreshes attributes to
    // prevent the exploit in bug MC-28289

    @EventHandler
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        if (event.getNewSlot() >= 0 && event.getNewSlot() < 9 && event.getNewSlot() != event.getPreviousSlot()) {
            event.getPlayer().resetCooldown();
            refreshAttributes(event.getPlayer(), event.getPlayer().getInventory().getItem(event.getNewSlot()));
        }
    }

    @EventHandler
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        event.getPlayer().resetCooldown();
        refreshAttributes(event.getPlayer(), event.getMainHandItem());
    }

    private void refreshAttributes(Player player, ItemStack itemStack) {
        if (itemStack == null)
            return;

        var whitelist = unitedCombat.getConfig().getStringList("attr-swap-items");
        if (!whitelist.contains(itemStack.getType().toString())) {
            return;
        }

        Multimap<Attribute, AttributeModifier> itemModifiers;

        if (itemStack.getItemMeta() != null && itemStack.getItemMeta().getAttributeModifiers() != null) {
            itemModifiers = itemStack.getItemMeta().getAttributeModifiers();
        } else {
            itemModifiers = itemStack.getType().getDefaultAttributeModifiers();
        }

        for (Attribute attribute : Registry.ATTRIBUTE) {
            AttributeInstance attributeInstance = player.getAttribute(attribute);
            if (attributeInstance != null) {
                itemModifiers.get(attribute).stream().findFirst().ifPresent(modifier -> {
                    attributeInstance.removeModifier(modifier);
                    attributeInstance.addTransientModifier(modifier);
                });
            }
        }
    }

    // Helper methods

    private Player getAttacker(Entity damager) {
        if (damager instanceof Projectile) {
            return (Player) ((Projectile) damager).getShooter();
        }
        return (Player) damager;
    }

    private int getDaysPassed(long playerTime) {
        // Player time should always have a value, else it wasn't registered.
        if (playerTime == 0) {
            return 0;
        }
        long timeDifference = System.currentTimeMillis() - playerTime;
        return Math.toIntExact(TimeUnit.MILLISECONDS.toDays(timeDifference));
    }

    private boolean areRelated(Player first, Player second) {
        var firstTown = towny.getResident(first).getTownOrNull();
        var secondTown = towny.getResident(second).getTownOrNull();
        if (firstTown == null || secondTown == null)
            return false;
        if (firstTown.hasNation() && secondTown.hasNation()) {
            return firstTown.getNationOrNull().equals(secondTown.getNationOrNull());
        }
        return firstTown.equals(secondTown);
    }

}
