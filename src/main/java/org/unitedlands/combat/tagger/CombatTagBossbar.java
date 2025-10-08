package org.unitedlands.combat.tagger;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public final class CombatTagBossbar {
    private final JavaPlugin plugin;
    private final CombatTagManager tags;

    private final Map<UUID, BossBar> bars = new HashMap<>();
    private final Map<UUID, Long> fadeUntil = new HashMap<>();
    private BukkitTask task;

    private boolean enabled;
    private String titleTemplate;
    private BarColor colour;
    private BarStyle style;
    private long linger;


    public CombatTagBossbar(JavaPlugin plugin, CombatTagManager tags) {
        this.plugin = plugin;
        this.tags = tags;
        reload();
    }

    public void reload() {
        var c = plugin.getConfig();
        enabled = c.getBoolean("combat_tagger.bossbar.enabled");
        titleTemplate = c.getString("combat_tagger.bossbar.title");
        String colourName = c.getString("combat_tagger.bossbar.colour");
        String styleName = c.getString("combat_tagger.bossbar.style");
        linger = Math.max(0, c.getLong("combat_tagger.bossbar.linger"));

        try { colour = BarColor.valueOf(Objects.requireNonNull(colourName).toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) { colour = BarColor.RED; }


        try { style = BarStyle.valueOf(Objects.requireNonNull(styleName).toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) { style = BarStyle.SOLID; }

        stop();
        if (enabled) start();
    }

    public void start() {
        if (task != null) return;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 0L, 10L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (BossBar bar : bars.values()) bar.removeAll();
        bars.clear();
    }

    public void showFor(Player... players) {
        if (!enabled || players == null) return;
        for (Player p : players) {
            if (p == null) continue;
            if (!tags.isTagged(p)) continue;

            BossBar bar = bars.get(p.getUniqueId());
            if (bar == null) {
                bar = Bukkit.createBossBar(formatTitle(tags.remainingSeconds(p)), colour, style);
                bar.addPlayer(p);
                bars.put(p.getUniqueId(), bar);
            } else if (!bar.getPlayers().contains(p)) {
                bar.addPlayer(p);
            }
            fadeUntil.remove(p.getUniqueId());
        }
    }

    private void tick() {
        if (!enabled) return;

        final long now = System.currentTimeMillis();
        var it = bars.entrySet().iterator();

        while (it.hasNext()) {
            var entry = it.next();
            UUID id = entry.getKey();
            BossBar bar = entry.getValue();

            Player p = Bukkit.getPlayer(id);
            boolean online = p != null && p.isOnline();
            boolean tagged = online && tags.isTagged(p);

            if (tagged) {
                long remaining = tags.remainingSeconds(p);
                bar.setTitle(formatTitle(remaining));
                bar.setProgress(progressFor(p));
                continue;
            }

            Long until = fadeUntil.get(id);
            if (online && until == null && linger > 0) {
                fadeUntil.put(id, now + linger);
                until = fadeUntil.get(id);
            }

            if (until != null) {
                double pct = Math.max(0.0, Math.min(1.0, (until - now) / (double) linger));
                bar.setTitle(formatTitle(0));
                bar.setProgress(pct * bar.getProgress());
                if (now >= until) {
                    bar.removeAll();
                    it.remove();
                    fadeUntil.remove(id);
                }
                continue;
            }

            bar.removeAll();
            it.remove();
            fadeUntil.remove(id);
        }
    }

    private String formatTitle(long remainingSeconds) {
        return titleTemplate.replace("%seconds%", String.valueOf(remainingSeconds));
    }

    private double progressFor(Player p) {
        int total = Math.max(1, tags.getDurationSeconds());
        double remaining = Math.max(0, Math.min(total, tags.remainingSeconds(p)));
        return remaining / total;
    }

}
