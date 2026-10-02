package com.example.tauntplugin;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.Random;

public class BloodMoonManager implements Listener {

    private final JavaPlugin plugin;
    private final Random random = new Random();

    private static final long CHECK_INTERVAL_MS = 60_000L;
    private static final double TRIGGER_CHANCE = 0.15;
    private static final long DURATION_MS = 5 * 60_000L;

    private volatile boolean active = false;
    private volatile long endTime = 0L;
    private BossBar bossBar;
    private BukkitTask checkTask;
    private BukkitTask bossBarTask;

    public BloodMoonManager(JavaPlugin plugin) {
        this.plugin = plugin;
        startCheckTask();
    }

    public boolean isActive() { return active; }

    public void shutdown() {
        if (checkTask != null) checkTask.cancel();
        if (bossBarTask != null) bossBarTask.cancel();
        if (bossBar != null) {
            Bukkit.getOnlinePlayers().forEach(bossBar::removeViewer);
            bossBar = null;
        }
        active = false;
        TauntManager.bloodMoonActive = false;
    }

    private void startCheckTask() {
        checkTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (active) return;
            World world = Bukkit.getWorlds().get(0);
            long time = world.getTime();
            if (time < 13000 || time > 23000) return;
            if (random.nextDouble() > TRIGGER_CHANCE) return;
            trigger();
        }, 1200L, CHECK_INTERVAL_MS / 50);
    }

    private void trigger() {
        active = true;
        endTime = System.currentTimeMillis() + DURATION_MS;
        TauntManager.bloodMoonActive = true;

        broadcast("═══════════════════════════", NamedTextColor.DARK_RED);
        broadcast("🌑 血月降临 🌑", NamedTextColor.RED);
        broadcast("怪物变得更加强大，小心你的背后...", NamedTextColor.DARK_RED);
        broadcast("═══════════════════════════", NamedTextColor.DARK_RED);

        bossBar = BossBar.bossBar(
                Component.text("🌑 血月降临 · 剩余 5:00", NamedTextColor.DARK_RED),
                1.0f, BossBar.Color.RED, BossBar.Overlay.NOTCHED_10);
        for (Player p : Bukkit.getOnlinePlayers()) bossBar.addViewer(p);

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.7f, 0.4f);
            p.playSound(p.getLocation(), Sound.AMBIENT_CAVE, 1.0f, 0.5f);
        }

        plugin.getLogger().info("[血月] 血月已触发，持续 " + (DURATION_MS / 1000) + " 秒");

        bossBarTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!active || bossBar == null) return;
            long remain = endTime - System.currentTimeMillis();
            if (remain <= 0) return;
            float progress = Math.max(0f, Math.min(1f, remain / (float) DURATION_MS));
            int sec = (int) (remain / 1000);
            int min = sec / 60;
            int s = sec % 60;
            bossBar.name(Component.text(
                    String.format("🌑 血月降临 · 剩余 %d:%02d", min, s),
                    NamedTextColor.DARK_RED));
            bossBar.progress(progress);
        }, 20L, 20L);

        Bukkit.getScheduler().runTaskLater(plugin, this::end, DURATION_MS / 50);
    }

    private void end() {
        if (!active) return;
        active = false;
        TauntManager.bloodMoonActive = false;

        if (bossBar != null) {
            for (Player p : Bukkit.getOnlinePlayers()) bossBar.removeViewer(p);
            bossBar = null;
        }
        if (bossBarTask != null) {
            bossBarTask.cancel();
            bossBarTask = null;
        }

        broadcast("═══════════════════════════", NamedTextColor.GOLD);
        broadcast("🌕 血月已退去，黎明即将到来", NamedTextColor.YELLOW);
        broadcast("═══════════════════════════", NamedTextColor.GOLD);

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.5f);
        }

        plugin.getLogger().info("[血月] 血月已结束");
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!active) return;
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof Monster)) return;

        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        if (reason == CreatureSpawnEvent.SpawnReason.CUSTOM) return;

        AttributeInstance maxHealthAttr = entity.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttr != null) {
            double newMax = maxHealthAttr.getBaseValue() * 2.0;
            maxHealthAttr.setBaseValue(newMax);
            entity.setHealth((float) newMax);
        }

        AttributeInstance speedAttr = entity.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speedAttr != null) speedAttr.setBaseValue(speedAttr.getBaseValue() * 1.3);

        AttributeInstance attackAttr = entity.getAttribute(Attribute.ATTACK_DAMAGE);
        if (attackAttr != null) attackAttr.setBaseValue(attackAttr.getBaseValue() * 1.3);

        entity.customName(Component.text("🌑 ", NamedTextColor.DARK_RED)
                .append(Component.translatable(
                        "entity.minecraft." + entity.getType().getKey().getKey(),
                        NamedTextColor.RED)));
        entity.setCustomNameVisible(true);

        for (Player p : entity.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(entity.getLocation()) < 100) {
                p.addPotionEffect(new PotionEffect(
                        PotionEffectType.DARKNESS, 100, 0, true, false, false));
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (active && bossBar != null) {
            bossBar.addViewer(event.getPlayer());
            event.getPlayer().playSound(event.getPlayer().getLocation(),
                    Sound.AMBIENT_CAVE, 1.0f, 0.5f);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (bossBar != null) bossBar.removeViewer(event.getPlayer());
    }

    private void broadcast(String text, NamedTextColor color) {
        Bukkit.getServer().broadcast(Component.text(text, color));
    }
}