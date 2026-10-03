package com.example.tauntplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WeirdVillagerManager {

    private final JavaPlugin plugin;
    private final Random random = new Random();

    private long checkIntervalMs;
    private long playerCooldownMs;
    private double triggerChance;
    private int spawnDistance;
    private double disappearDistance;
    private long lifetimeMs;

    // ★ 使用 CooldownManager 替代 Map<UUID, Long> lastSpawn
    private final CooldownManager cooldowns;

    private final Map<UUID, Villager> active = new ConcurrentHashMap<>();
    private BukkitTask task;

    public WeirdVillagerManager(JavaPlugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.checkIntervalMs = config.getLong("weird-villager.check-interval-ms", 300000);
        this.playerCooldownMs = config.getLong("weird-villager.player-cooldown-ms", 900000);
        this.triggerChance = config.getDouble("weird-villager.trigger-chance", 0.2);
        this.spawnDistance = config.getInt("weird-villager.spawn-distance", 15);
        this.disappearDistance = config.getDouble("weird-villager.disappear-distance", 8.0);
        this.lifetimeMs = config.getLong("weird-villager.lifetime-ms", 30000);

        // ★ 创建冷却管理器（过期时间 30 分钟，因为冷却本身 15 分钟）
        this.cooldowns = new CooldownManager(plugin, playerCooldownMs, 0, playerCooldownMs * 2);

        startTask();
    }

    public void shutdown() {
        if (task != null) task.cancel();
        for (Villager v : active.values()) if (v != null && v.isValid()) v.remove();
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

                if (p.getLocation().getBlock().getLightLevel() < 6) continue;
                if (random.nextDouble() > triggerChance) continue;

                spawnFor(p);
                return;
            }
        }, 6000L, checkIntervalMs / 50);
    }

    private void spawnFor(Player player) {
        Location playerLoc = player.getLocation();
        Vector dir = playerLoc.getDirection().setY(0).normalize();
        Location behind = playerLoc.clone().subtract(dir.multiply(spawnDistance));
        Location spawnLoc = findSafeGround(behind);

        if (spawnLoc == null) {
            for (int i = 0; i < 8; i++) {
                double angle = random.nextDouble() * Math.PI * 2;
                Location test = playerLoc.clone().add(
                        Math.cos(angle) * spawnDistance, 0, Math.sin(angle) * spawnDistance);
                spawnLoc = findSafeGround(test);
                if (spawnLoc != null) break;
            }
        }
        if (spawnLoc == null) return;

        Villager v = (Villager) player.getWorld().spawnEntity(spawnLoc, EntityType.VILLAGER);
        v.setAI(false);
        v.setSilent(true);
        v.setInvulnerable(true);
        v.setCollidable(false);
        v.setPersistent(false);
        v.setRemoveWhenFarAway(false);
        v.customName(Component.text("???", NamedTextColor.DARK_GRAY));
        v.setCustomNameVisible(false);
        v.setProfession(Villager.Profession.NONE);
        v.setVillagerType(Villager.Type.PLAINS);
        v.setVillagerLevel(1);

        active.put(player.getUniqueId(), v);

        // ★ 记录冷却
        cooldowns.isReady(player.getUniqueId(), "spawn", playerCooldownMs);

        player.playSound(player.getLocation(), Sound.AMBIENT_CAVE, 1.0f, 0.4f);

        // ★ 成就挂钩（用工具类）
        TauntUtils.unlock(plugin, player, AchievementManager.Ach.WEIRD_VILLAGER);

        final long startTime = System.currentTimeMillis();
        final BukkitTask[] followTask = new BukkitTask[1];

        followTask[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!v.isValid() || v.isDead()) {
                followTask[0].cancel();
                active.remove(player.getUniqueId());
                return;
            }
            if (!player.isOnline()) {
                v.remove();
                followTask[0].cancel();
                active.remove(player.getUniqueId());
                return;
            }

            // ★ 使用工具类朝向
            TauntUtils.faceTo(v, player.getLocation());

            double dist = v.getWorld().equals(player.getWorld())
                    ? v.getLocation().distance(player.getLocation())
                    : Double.MAX_VALUE;
            if (dist < disappearDistance
                    || System.currentTimeMillis() - startTime > lifetimeMs) {
                v.remove();
                active.remove(player.getUniqueId());
                player.playSound(player.getLocation(),
                        Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 0.5f);
                followTask[0].cancel();
            }
        }, 20L, 20L);
    }

    private Location findSafeGround(Location origin) {
        World world = origin.getWorld();
        if (world == null) return null;
        for (int dy = -3; dy <= 5; dy++) {
            int y = origin.getBlockY() + dy;
            if (y < world.getMinHeight() + 1 || y > world.getMaxHeight() - 2) continue;
            Location below = new Location(world, origin.getX(), y - 1, origin.getZ());
            Location feet = new Location(world, origin.getX(), y, origin.getZ());
            Location head = new Location(world, origin.getX(), y + 1, origin.getZ());
            if (below.getBlock().getType().isSolid()
                    && feet.getBlock().getType().isAir()
                    && head.getBlock().getType().isAir()) {
                return new Location(world, origin.getX(), y, origin.getZ());
            }
        }
        return null;
    }
}