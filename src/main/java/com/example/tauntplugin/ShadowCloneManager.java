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

/**
 * 玩家影分身：
 * - 每 5 分钟检查一次
 * - 每个玩家 10 分钟冷却，25% 概率触发
 * - 玩家在野外时，正前方 20 格生成一个"自己"的影子
 * - 3 秒后消失
 */
public class ShadowCloneManager {

    private final JavaPlugin plugin;
    private final Random random = new Random();

    private static final long CHECK_INTERVAL_MS = 300_000L;
    private static final long PLAYER_COOLDOWN_MS = 600_000L;
    private static final double TRIGGER_CHANCE = 0.25;
    private static final int SPAWN_DISTANCE = 20;
    private static final long LIFETIME_MS = 3_000L;

    private final Map<UUID, Long> lastSpawn = new ConcurrentHashMap<>();
    private final Map<UUID, Mannequin> active = new ConcurrentHashMap<>();
    private BukkitTask task;

    public ShadowCloneManager(JavaPlugin plugin) {
        this.plugin = plugin;
        startTask();
    }

    public void shutdown() {
        if (task != null) task.cancel();
        for (Mannequin m : active.values()) {
            if (m != null && m.isValid()) m.remove();
        }
        active.clear();
    }

    private void startTask() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getGameMode().name().equals("SPECTATOR")) continue;
                if (active.containsKey(p.getUniqueId())) continue;

                Long last = lastSpawn.get(p.getUniqueId());
                if (last != null && now - last < PLAYER_COOLDOWN_MS) continue;

                // 必须在野外（头顶露天）
                if (p.getLocation().add(0, 5, 0).getBlock().getType().isSolid()) continue;

                if (random.nextDouble() > TRIGGER_CHANCE) continue;

                spawnShadow(p);
                return;
            }
        }, 6000L, CHECK_INTERVAL_MS / 50);
    }

    private void spawnShadow(Player player) {
        Location playerLoc = player.getLocation();
        // 玩家正前方 20 格
        Vector dir = playerLoc.getDirection().setY(0).normalize();
        Location target = playerLoc.clone().add(dir.multiply(SPAWN_DISTANCE));

        Location spawnLoc = findSafeGround(target);
        if (spawnLoc == null) return;

        // 影子面向玩家
        Vector toPlayer = playerLoc.toVector().subtract(spawnLoc.toVector()).setY(0).normalize();
        float yaw = (float) Math.toDegrees(Math.atan2(-toPlayer.getX(), toPlayer.getZ()));
        spawnLoc.setYaw(yaw);
        spawnLoc.setPitch(0);

        Mannequin m = player.getWorld().spawn(spawnLoc, Mannequin.class, mq -> {
            // ★ 修正：将 PlayerProfile 转换为 ResolvableProfile
            try {
                mq.setProfile(io.papermc.paper.datacomponent.item.ResolvableProfile
                        .resolvableProfile(player.getPlayerProfile()));
            } catch (Throwable ignored) {
                // 回退：如果上述 API 不可用，尝试仅用 UUID 和名字
                try {
                    mq.setProfile(io.papermc.paper.datacomponent.item.ResolvableProfile
                            .resolvableProfile()
                            .uuid(player.getUniqueId())
                            .name(player.getName())
                            .build());
                } catch (Throwable ignored2) {
                    // 最终回退：使用默认皮肤
                }
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
        lastSpawn.put(player.getUniqueId(), System.currentTimeMillis());

        // 微弱音效提示
        player.playSound(player.getLocation(),
                Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 0.5f);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (m.isValid()) m.remove();
            active.remove(player.getUniqueId());
            if (player.isOnline()) {
                player.playSound(player.getLocation(),
                        Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.5f);
            }
        }, LIFETIME_MS / 50);
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