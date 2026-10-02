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

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * 诡异村民：
 * - 每 5 分钟检查一次
 * - 每个玩家 15 分钟冷却，20% 概率触发
 * - 在玩家远处生成一个不交易、不说话、只盯着玩家的村民
 * - 玩家靠近 8 格内或 30 秒后消失
 */
public class WeirdVillagerManager {

    private final JavaPlugin plugin;
    private final Random random = new Random();

    private static final long CHECK_INTERVAL_MS = 300_000L;
    private static final long PLAYER_COOLDOWN_MS = 900_000L;
    private static final double TRIGGER_CHANCE = 0.2;
    private static final int SPAWN_DISTANCE = 15;
    private static final double DISAPPEAR_DISTANCE = 8.0;
    private static final long LIFETIME_MS = 30_000L;

    private final Map<UUID, Long> lastSpawn = new ConcurrentHashMap<>();
    private final Map<UUID, Villager> active = new ConcurrentHashMap<>();
    private BukkitTask task;

    public WeirdVillagerManager(JavaPlugin plugin) {
        this.plugin = plugin;
        startTask();
    }

    public void shutdown() {
        if (task != null) task.cancel();
        for (Villager v : active.values()) {
            if (v != null && v.isValid()) v.remove();
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

                if (p.getLocation().getBlock().getLightLevel() < 6) continue;
                if (random.nextDouble() > TRIGGER_CHANCE) continue;

                spawnFor(p);
                return;
            }
        }, 6000L, CHECK_INTERVAL_MS / 50);
    }

    private void spawnFor(Player player) {
        Location playerLoc = player.getLocation();
        Vector dir = playerLoc.getDirection().setY(0).normalize();

        // 玩家身后 15 格
        Location behind = playerLoc.clone().subtract(dir.multiply(SPAWN_DISTANCE));
        Location spawnLoc = findSafeGround(behind);

        if (spawnLoc == null) {
            // 备选：随机方向
            for (int i = 0; i < 8; i++) {
                double angle = random.nextDouble() * Math.PI * 2;
                Location test = playerLoc.clone().add(
                        Math.cos(angle) * SPAWN_DISTANCE, 0, Math.sin(angle) * SPAWN_DISTANCE);
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
        lastSpawn.put(player.getUniqueId(), System.currentTimeMillis());

        player.playSound(player.getLocation(), Sound.AMBIENT_CAVE, 1.0f, 0.4f);

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

            // 看向玩家
            Location vLoc = v.getLocation();
            Vector toPlayer = player.getLocation().toVector().subtract(vLoc.toVector()).setY(0);
            if (toPlayer.lengthSquared() > 0.01) {
                toPlayer.normalize();
                float yaw = (float) Math.toDegrees(Math.atan2(-toPlayer.getX(), toPlayer.getZ()));
                v.setRotation(yaw, 0);
            }

            // 距离检查
            double dist = v.getWorld().equals(player.getWorld())
                    ? v.getLocation().distance(player.getLocation())
                    : Double.MAX_VALUE;

            if (dist < DISAPPEAR_DISTANCE
                    || System.currentTimeMillis() - startTime > LIFETIME_MS) {
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