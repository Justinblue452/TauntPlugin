package com.example.tauntplugin;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ShadowCloneManager {

    private final JavaPlugin plugin;
    private final Random random = new Random();

    private long checkIntervalMs;
    private long playerCooldownMs;
    private double triggerChance;
    private int spawnDistance;
    private long lifetimeMs;

    // ★ 使用 CooldownManager 替代 Map<UUID, Long> lastSpawn
    private final CooldownManager cooldowns;

    private final Map<UUID, Mannequin> active = new ConcurrentHashMap<>();
    private BukkitTask task;

    public ShadowCloneManager(JavaPlugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.checkIntervalMs = config.getLong("shadow-clone.check-interval-ms", 300000);
        this.playerCooldownMs = config.getLong("shadow-clone.player-cooldown-ms", 600000);
        this.triggerChance = config.getDouble("shadow-clone.trigger-chance", 0.25);
        this.spawnDistance = config.getInt("shadow-clone.spawn-distance", 20);
        this.lifetimeMs = config.getLong("shadow-clone.lifetime-ms", 3000);

        // ★ 创建冷却管理器
        this.cooldowns = new CooldownManager(plugin, playerCooldownMs, 0, playerCooldownMs * 2);

        startTask();
    }

    public void shutdown() {
        if (task != null) task.cancel();
        for (Mannequin m : active.values()) if (m != null && m.isValid()) m.remove();
        active.clear();
        if (cooldowns != null) cooldowns.shutdown();   // ★
    }

    private void startTask() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getGameMode().name().equals("SPECTATOR")) continue;
                if (active.containsKey(p.getUniqueId())) continue;

                // ★ 使用 CooldownManager
                long remain = cooldowns.getRemaining(p.getUniqueId(), "spawn", playerCooldownMs);
                if (remain > 0) continue;

                if (p.getLocation().add(0, 5, 0).getBlock().getType().isSolid()) continue;
                if (random.nextDouble() > triggerChance) continue;

                spawnShadow(p);
                return;
            }
        }, 6000L, checkIntervalMs / 50);
    }

    private void spawnShadow(Player player) {
        Location playerLoc = player.getLocation();
        Vector dir = playerLoc.getDirection().setY(0).normalize();
        Location target = playerLoc.clone().add(dir.multiply(spawnDistance));
        Location spawnLoc = findSafeGround(target);
        if (spawnLoc == null) return;

        Vector toPlayer = playerLoc.toVector().subtract(spawnLoc.toVector()).setY(0).normalize();
        float yaw = (float) Math.toDegrees(Math.atan2(-toPlayer.getX(), toPlayer.getZ()));
        spawnLoc.setYaw(yaw);
        spawnLoc.setPitch(0);

        Mannequin m = player.getWorld().spawn(spawnLoc, Mannequin.class, mq -> {
            try {
                mq.setProfile(io.papermc.paper.datacomponent.item.ResolvableProfile
                        .resolvableProfile(player.getPlayerProfile()));
            } catch (Throwable ignored) {
                try {
                    mq.setProfile(io.papermc.paper.datacomponent.item.ResolvableProfile
                            .resolvableProfile()
                            .uuid(player.getUniqueId())
                            .name(player.getName())
                            .build());
                } catch (Throwable ignored2) {}
            }
            mq.setDescription(null);
            mq.setImmovable(true);
            mq.setPose(Pose.STANDING);
            mq.setSilent(true);
            mq.setInvulnerable(true);
            mq.setCollidable(false);
            mq.setPersistent(false);
        });

        active.put(player.getUniqueId(), m);

        // ★ 记录冷却
        cooldowns.isReady(player.getUniqueId(), "spawn", playerCooldownMs);

        player.playSound(player.getLocation(),
                Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 0.5f);

        // ★ 成就挂钩（用工具类）
        TauntUtils.unlock(plugin, player, AchievementManager.Ach.SAW_CLONE);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (m.isValid()) m.remove();
            active.remove(player.getUniqueId());
            if (player.isOnline()) {
                player.playSound(player.getLocation(),
                        Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.5f);
            }
        }, lifetimeMs / 50);
    }

    private Location findSafeGround(Location origin) {
        if (origin.getWorld() == null) return null;
        for (int dy = -3; dy <= 5; dy++) {
            Location test = origin.clone().add(0, dy, 0);
            if (test.getBlock().getType().isSolid()) {
                Location above = test.clone().add(0, 1, 0);
                if (above.getBlock().getType().isAir()
                        && above.clone().add(0, 1, 0).getBlock().getType().isAir()) {
                    above.setYaw(origin.getYaw());
                    above.setPitch(0);
                    return above;
                }
            }
        }
        return null;
    }
}