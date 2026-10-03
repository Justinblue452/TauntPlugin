package com.example.tauntplugin;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * 通用工具类，消除各 Manager 里的重复样板代码。
 */
public final class TauntUtils {

    private TauntUtils() {}

    // ==================== 成就挂钩 ====================

    public static void unlock(JavaPlugin plugin, Player player, AchievementManager.Ach ach) {
        if (player == null || ach == null) return;
        if (plugin instanceof TauntPlugin tp && tp.getAchievementManager() != null) {
            tp.getAchievementManager().unlock(player, ach);
        }
    }

    public static void increment(JavaPlugin plugin, Player player, String counter, int amount) {
        if (player == null || counter == null || amount <= 0) return;
        if (plugin instanceof TauntPlugin tp && tp.getAchievementManager() != null) {
            tp.getAchievementManager().increment(player, counter, amount);
        }
    }

    public static void setCounter(JavaPlugin plugin, Player player, String counter, int value) {
        if (player == null || counter == null) return;
        if (plugin instanceof TauntPlugin tp && tp.getAchievementManager() != null) {
            tp.getAchievementManager().setCounter(player, counter, value);
        }
    }

    // ==================== 朝向 ====================

    public static void faceTo(Entity entity, Location target) {
        if (entity == null || target == null) return;
        if (!entity.getWorld().equals(target.getWorld())) return;

        Vector dir = target.toVector().subtract(entity.getLocation().toVector()).setY(0);
        if (dir.lengthSquared() > 0.01) {
            dir.normalize();
            float yaw = (float) Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ()));
            entity.setRotation(yaw, 0);
        }
    }

    public static void lookAt(Entity entity, Location target) {
        if (entity == null || target == null) return;
        if (!entity.getWorld().equals(target.getWorld())) return;

        Vector dir = target.toVector().subtract(entity.getLocation().toVector());
        if (dir.lengthSquared() > 0.01) {
            Location tmp = entity.getLocation().clone();
            tmp.setDirection(dir);
            entity.setRotation(tmp.getYaw(), tmp.getPitch());
        }
    }

    // ==================== 玩家检索 ====================

    public static List<Player> getNearbyPlayers(Location center, double radius, Player exclude) {
        List<Player> result = new ArrayList<>();
        if (center.getWorld() == null) return result;
        double r2 = radius * radius;
        for (Player p : center.getWorld().getPlayers()) {
            if (p.equals(exclude)) continue;
            if (p.getGameMode().name().equals("SPECTATOR")) continue;
            if (p.getLocation().distanceSquared(center) <= r2) {
                result.add(p);
            }
        }
        return result;
    }

    public static Player findPlayerByName(String name) {
        if (name == null || name.isEmpty()) return null;
        Player exact = org.bukkit.Bukkit.getPlayerExact(name);
        if (exact != null && exact.isOnline()) return exact;

        String lower = name.toLowerCase();
        List<Player> matches = new ArrayList<>();
        for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (p.getName().toLowerCase().contains(lower)) matches.add(p);
        }
        if (matches.size() == 1) return matches.get(0);
        return null;
    }

    // ==================== 安全操作 ====================

    public static void killPlayer(JavaPlugin plugin, Player victim) {
        if (victim == null || !victim.isOnline()) return;

        victim.setInvulnerable(false);
        victim.setNoDamageTicks(0);
        victim.setHealth(0.0);

        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            if (victim.isOnline() && victim.getHealth() > 0) {
                victim.setInvulnerable(false);
                victim.setNoDamageTicks(0);
                victim.setHealth(0.0);
            }
        });

        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (victim.isOnline() && victim.getHealth() > 0) {
                victim.setInvulnerable(false);
                victim.setNoDamageTicks(0);
                victim.damage(Double.MAX_VALUE);
            }
        }, 2L);
    }

    public static boolean isHolding(Player player, org.bukkit.Material material) {
        if (player == null) return false;
        if (player.getInventory().getItemInMainHand().getType() == material) return true;
        return player.getInventory().getItemInOffHand().getType() == material;
    }

    // ==================== 粒子安全 ====================

    public static void spawnParticleSafe(org.bukkit.World world, org.bukkit.Particle particle,
                                         double x, double y, double z,
                                         int count, double ox, double oy, double oz, double speed) {
        try {
            world.spawnParticle(particle, x, y, z, count, ox, oy, oz, speed);
        } catch (Throwable ignored) {}
    }

    public static <T> void spawnParticleSafe(org.bukkit.World world, org.bukkit.Particle particle,
                                             double x, double y, double z,
                                             int count, double ox, double oy, double oz, double speed,
                                             T data) {
        try {
            world.spawnParticle(particle, x, y, z, count, ox, oy, oz, speed, data);
        } catch (Throwable ignored) {}
    }

    // ==================== 显示名 ====================

    public static String getEntityDisplayName(Entity entity) {
        if (entity instanceof Player p) return p.getName();
        Component custom = entity.customName();
        if (custom != null) {
            return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                    .plainText().serialize(custom);
        }
        return entity.getType().name();
    }
}