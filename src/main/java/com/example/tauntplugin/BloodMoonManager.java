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
    private final ConfigManager config;
    private final Random random = new Random();

    private long checkIntervalMs;
    private double triggerChance;
    private long durationMs;
    private double healthMultiplier;
    private double speedMultiplier;
    private double attackMultiplier;

    private volatile boolean active = false;
    private volatile long endTime = 0L;
    private BossBar bossBar;
    private BukkitTask checkTask;
    private BukkitTask bossBarTask;

    // ★ 构造函数：不再接收 TauntManager
    public BloodMoonManager(JavaPlugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;

        this.checkIntervalMs = config.getLong("blood-moon.check-interval-ms", 60000);
        this.triggerChance = config.getDouble("blood-moon.trigger-chance", 0.15);
        this.durationMs = config.getLong("blood-moon.duration-ms", 300000);
        this.healthMultiplier = config.getDouble("blood-moon.health-multiplier", 2.0);
        this.speedMultiplier = config.getDouble("blood-moon.speed-multiplier", 1.3);
        this.attackMultiplier = config.getDouble("blood-moon.attack-multiplier", 1.3);

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

        // ★ 卸载时通知外界
        Bukkit.getPluginManager().callEvent(new TauntEvents.BloodMoonEndEvent());
    }

    private void startCheckTask() {
        checkTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (active) return;
            World world = Bukkit.getWorlds().get(0);
            long time = world.getTime();
            if (time < 13000 || time > 23000) return;
            if (random.nextDouble() > triggerChance) return;
            trigger();
        }, 1200L, checkIntervalMs / 50);
    }

    private void trigger() {
        active = true;
        endTime = System.currentTimeMillis() + durationMs;

        // ★ 触发事件（替代原来直接操作 tauntManager.bloodMoonActive）
        Bukkit.getPluginManager().callEvent(new TauntEvents.BloodMoonStartEvent(durationMs));

        broadcast("═══════════════════════════", NamedTextColor.DARK_RED);
        broadcast("🌑 血月降临 🌑", NamedTextColor.RED);
        broadcast("怪物变得更加强大，小心你的背后...", NamedTextColor.DARK_RED);
        broadcast("═══════════════════════════", NamedTextColor.DARK_RED);

        bossBar = BossBar.bossBar(
                Component.text("🌑 血月降临 · 剩余 5:00", NamedTextColor.DARK_RED),
                1.0f, BossBar.Color.RED, BossBar.Overlay.NOTCHED_10);

        for (Player p : Bukkit.getOnlinePlayers()) {
            bossBar.addViewer(p);
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.7f, 0.4f);
            p.playSound(p.getLocation(), Sound.AMBIENT_CAVE, 1.0f, 0.5f);

            // 成就挂钩
            if (plugin instanceof TauntPlugin tp && tp.getAchievementManager() != null) {
                tp.getAchievementManager().increment(p, "blood_moons", 1);
                tp.getAchievementManager().unlock(p, AchievementManager.Ach.BLOOD_MOON_SURVIVOR);
            }
        }

        plugin.getLogger().info("[血月] 血月已触发，持续 " + (durationMs / 1000) + " 秒");

        bossBarTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!active || bossBar == null) return;
            long remain = endTime - System.currentTimeMillis();
            if (remain <= 0) return;
            float progress = Math.max(0f, Math.min(1f, remain / (float) durationMs));
            int sec = (int) (remain / 1000);
            int min = sec / 60;
            int s = sec % 60;
            bossBar.name(Component.text(
                    String.format("🌑 血月降临 · 剩余 %d:%02d", min, s),
                    NamedTextColor.DARK_RED));
            bossBar.progress(progress);
        }, 20L, 20L);

        Bukkit.getScheduler().runTaskLater(plugin, this::end, durationMs / 50);
    }

    private void end() {
        if (!active) return;
        active = false;

        // ★ 触发结束事件
        Bukkit.getPluginManager().callEvent(new TauntEvents.BloodMoonEndEvent());

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
            double newMax = maxHealthAttr.getBaseValue() * healthMultiplier;
            maxHealthAttr.setBaseValue(newMax);
            entity.setHealth((float) newMax);
        }

        AttributeInstance speedAttr = entity.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speedAttr != null) speedAttr.setBaseValue(speedAttr.getBaseValue() * speedMultiplier);

        AttributeInstance attackAttr = entity.getAttribute(Attribute.ATTACK_DAMAGE);
        if (attackAttr != null) attackAttr.setBaseValue(attackAttr.getBaseValue() * attackMultiplier);

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

            if (plugin instanceof TauntPlugin tp && tp.getAchievementManager() != null) {
                tp.getAchievementManager().increment(event.getPlayer(), "blood_moons", 1);
                tp.getAchievementManager().unlock(event.getPlayer(),
                        AchievementManager.Ach.BLOOD_MOON_SURVIVOR);
            }
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