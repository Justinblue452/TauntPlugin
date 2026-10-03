package com.example.tauntplugin;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CooldownManager {

    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> globalCooldowns = new ConcurrentHashMap<>();
    private final long defaultCooldownMs;
    private final long globalCooldownMs;
    private final long entryExpireMs;
    private final BukkitTask cleanupTask;

    private static final long CLEANUP_INTERVAL_MS = 60_000L;

    /** 使用默认过期时间（5 分钟） */
    public CooldownManager(JavaPlugin plugin, long defaultCooldownMs, long globalCooldownMs) {
        this(plugin, defaultCooldownMs, globalCooldownMs, 5 * 60_000L);
    }

    /**
     * @param defaultCooldownMs 默认冷却（毫秒）
     * @param globalCooldownMs  全局冷却（毫秒），0 表示不启用
     * @param entryExpireMs     空闲多久后清理条目（毫秒），建议大于最长冷却时间
     */
    public CooldownManager(JavaPlugin plugin, long defaultCooldownMs, long globalCooldownMs, long entryExpireMs) {
        this.defaultCooldownMs = defaultCooldownMs;
        this.globalCooldownMs = globalCooldownMs;
        this.entryExpireMs = entryExpireMs;

        this.cleanupTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(
                plugin, this::cleanup,
                CLEANUP_INTERVAL_MS / 50, CLEANUP_INTERVAL_MS / 50);
    }

    private void cleanup() {
        long now = System.currentTimeMillis();
        cooldowns.entrySet().removeIf(entry -> {
            entry.getValue().entrySet().removeIf(e -> now - e.getValue() > entryExpireMs);
            return entry.getValue().isEmpty();
        });
        globalCooldowns.entrySet().removeIf(e -> now - e.getValue() > entryExpireMs);
    }

    public void shutdown() {
        if (cleanupTask != null) cleanupTask.cancel();
        cooldowns.clear();
        globalCooldowns.clear();
    }

    public boolean isReady(UUID uuid, String key) {
        return isReady(uuid, key, defaultCooldownMs);
    }

    public boolean isReady(UUID uuid, String key, long cooldownMs) {
        long now = System.currentTimeMillis();

        if (globalCooldownMs > 0) {
            Long globalLast = globalCooldowns.get(uuid);
            if (globalLast != null && now - globalLast < globalCooldownMs) {
                return false;
            }
        }

        Map<String, Long> playerCd = cooldowns.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        Long last = playerCd.get(key);
        if (last != null && now - last < cooldownMs) {
            return false;
        }

        playerCd.put(key, now);
        globalCooldowns.put(uuid, now);
        return true;
    }

    public long getRemaining(UUID uuid, String key, long cooldownMs) {
        Map<String, Long> playerCd = cooldowns.get(uuid);
        if (playerCd == null) return 0;
        Long last = playerCd.get(key);
        if (last == null) return 0;
        long remain = cooldownMs - (System.currentTimeMillis() - last);
        return Math.max(0, remain);
    }

    public void clear(UUID uuid, String key) {
        Map<String, Long> playerCd = cooldowns.get(uuid);
        if (playerCd != null) playerCd.remove(key);
    }

    public void clearAll(UUID uuid) {
        cooldowns.remove(uuid);
        globalCooldowns.remove(uuid);
    }
}