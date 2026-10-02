package com.example.tauntplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家状态效果：
 * - 饱食度 < 50%：冒村民生气粒子
 * - 血量 < 50%：冒汗
 * - 血量 < 25%：惊恐状态（带锁定 + 心跳持续 + 受伤动画不卡攻击）
 * - 炎热环境：冒汗
 * - 长时间疾跑：冒汗
 * - 寒冷生物群系：嘴部呼出白雾（★ 真实呼吸节奏）
 */
public class PlayerStatusEffectManager implements Listener {

    private final JavaPlugin plugin;
    private final Random random = new Random();

    // ==================== 可调参数 ====================
    private static final long TICK_INTERVAL = 5L;
    private static final double PANIC_DAMAGE_MULTIPLIER = 0.7;
    private static final double PANIC_KNOCKBACK_MULTIPLIER = 3.0;
    private static final double COLD_TEMPERATURE_THRESHOLD = 0.15;
    private static final double HOT_TEMPERATURE_THRESHOLD = 0.9;
    private static final long SPRINT_TO_SWEAT_THRESHOLD_MS = 8_000L;
    private static final long SPRINT_STOP_GRACE_MS = 3_000L;
    private static final int HUNGER_ANGRY_THRESHOLD = 10;

    private static final long PANIC_LOCK_MS = 15_000L;
    private static final long HEARTBEAT_INTERVAL_MS = 1_000L;
    private static final long HURT_ANIMATION_INTERVAL_MS = 1_500L;

    // ==================== ★ 呼吸相关参数 ====================
    /** 一个完整呼吸周期的时长范围（毫秒）—— 3 秒到 4.5 秒 */
    private static final long BREATH_CYCLE_MIN_MS = 3_000L;
    private static final long BREATH_CYCLE_MAX_MS = 4_500L;
    /** 呼气阶段占周期的比例（40% 呼气，60% 吸气 + 停顿） */
    private static final double EXHALE_RATIO = 0.4;
    /** 呼气阶段喷出的粒子总数范围 */
    private static final int EXHALE_PUFFS_MIN = 3;
    private static final int EXHALE_PUFFS_MAX = 5;
    /** 运动后呼吸加快的周期倍率（0.5 = 呼吸频率翻倍） */
    private static final double EXERCISE_BREATH_MULTIPLIER = 0.5;
    /** 惊恐状态呼吸倍率 */
    private static final double PANIC_BREATH_MULTIPLIER = 0.6;

    // ==================== 状态 ====================
    private final Set<UUID> sweating = ConcurrentHashMap.newKeySet();
    private final Set<UUID> panicking = ConcurrentHashMap.newKeySet();
    private final Set<UUID> hungry = ConcurrentHashMap.newKeySet();

    private final Map<UUID, Long> sprintStart = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastSprintTick = new ConcurrentHashMap<>();

    private final Map<UUID, Long> panicLockedUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastHeartbeat = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastHurtAnim = new ConcurrentHashMap<>();

    // ★ 呼吸状态追踪
    private final Map<UUID, BreathState> breathStates = new ConcurrentHashMap<>();

    private BukkitTask tickTask;

    /**
     * 单个玩家的呼吸状态。
     */
    private static class BreathState {
        /** 当前呼吸周期开始时间 */
        long cycleStart;
        /** 当前周期的总时长（毫秒） */
        long cycleDuration;
        /** 本次周期计划的呼气粒子数 */
        int plannedPuffs;
        /** 本次周期已喷出的粒子数 */
        int emittedPuffs;
        /** 上次喷粒子的时间 */
        long lastPuffTime;

        BreathState(long start, long duration) {
            this.cycleStart = start;
            this.cycleDuration = duration;
        }
    }

    public PlayerStatusEffectManager(JavaPlugin plugin) {
        this.plugin = plugin;
        startTickTask();
    }

    public void shutdown() {
        if (tickTask != null) tickTask.cancel();

        for (UUID uuid : panicking) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) cleanupPanic(p);
        }
        panicking.clear();
        sweating.clear();
        hungry.clear();
        sprintStart.clear();
        lastSprintTick.clear();
        panicLockedUntil.clear();
        lastHeartbeat.clear();
        lastHurtAnim.clear();
        breathStates.clear();
    }

    // ==================== 主任务 ====================

    private void startTickTask() {
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                try {
                    tickPlayer(p);
                } catch (Exception ignored) {}
            }
        }, 0L, TICK_INTERVAL);
    }

    private void tickPlayer(Player player) {
        if (player.getGameMode().name().equals("SPECTATOR")) return;

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        double health = player.getHealth();
        double maxHealth = player.getMaxHealth();
        if (maxHealth <= 0) return;
        double ratio = health / maxHealth;

        boolean lowHealth = ratio > 0 && ratio < 0.25;
        boolean shouldSweatFromHealth = ratio > 0 && ratio < 0.5;

        updateSprintTracker(player, uuid, now);
        boolean shouldSweatFromExercise = isExercising(uuid, now);
        boolean shouldSweatFromHeat = isHotEnvironment(player);
        boolean shouldSweat = shouldSweatFromHealth
                || shouldSweatFromExercise
                || shouldSweatFromHeat;

        if (shouldSweat) sweating.add(uuid);
        else sweating.remove(uuid);

        updatePanicState(player, uuid, lowHealth, now);

        int foodLevel = player.getFoodLevel();
        boolean shouldAngry = foodLevel > 0 && foodLevel < HUNGER_ANGRY_THRESHOLD;
        if (shouldAngry) hungry.add(uuid);
        else hungry.remove(uuid);

        if (shouldSweat) {
            int extra = (shouldSweatFromExercise || shouldSweatFromHeat) ? 1 : 0;
            spawnSweatParticles(player, extra);
        }
        if (panicking.contains(uuid)) spawnPanicParticles(player, now);
        if (shouldAngry) spawnAngryVillagerParticles(player);

        // ★ 呼吸白雾
        if (isColdBiome(player)) {
            tickBreath(player, uuid, now, shouldSweatFromExercise, panicking.contains(uuid));
        } else {
            // 离开寒冷区域，清理呼吸状态
            breathStates.remove(uuid);
        }

        if (panicking.contains(uuid)) applyPanicEffects(player);
    }

    // ==================== ★ 真实呼吸白雾 ====================

    /**
     * 模拟真实呼吸：
     * - 每个周期时长 3~4.5 秒（运动/惊恐时缩短）
     * - 周期的前 40% 是呼气阶段，喷出 3~5 个粒子
     * - 后 60% 是吸气 + 停顿，不喷粒子
     */
    private void tickBreath(Player player, UUID uuid, long now,
                            boolean exercising, boolean panicking) {
        BreathState state = breathStates.get(uuid);

        // 首次进入寒冷区域，或上一个周期结束 → 开启新周期
        if (state == null || now - state.cycleStart >= state.cycleDuration) {
            state = new BreathState(now, calculateCycleDuration(exercising, panicking));
            state.plannedPuffs = EXHALE_PUFFS_MIN
                    + random.nextInt(EXHALE_PUFFS_MAX - EXHALE_PUFFS_MIN + 1);
            state.emittedPuffs = 0;
            state.lastPuffTime = now;
            breathStates.put(uuid, state);
            return;
        }

        // 计算当前是否处于呼气阶段
        long elapsed = now - state.cycleStart;
        long exhaleDuration = (long) (state.cycleDuration * EXHALE_RATIO);

        if (elapsed < exhaleDuration && state.emittedPuffs < state.plannedPuffs) {
            // 呼气阶段：把粒子均匀分布在呼气时间里
            long puffInterval = exhaleDuration / state.plannedPuffs;

            if (now - state.lastPuffTime >= puffInterval) {
                spawnBreathParticles(player);
                state.lastPuffTime = now;
                state.emittedPuffs++;
            }
        }
        // 吸气阶段：不喷粒子
    }

    /**
     * 计算本次呼吸周期时长。
     * 运动或惊恐状态下呼吸加快。
     */
    private long calculateCycleDuration(boolean exercising, boolean panicking) {
        long base = BREATH_CYCLE_MIN_MS
                + (long) (random.nextDouble() * (BREATH_CYCLE_MAX_MS - BREATH_CYCLE_MIN_MS));

        if (exercising && panicking) {
            base = (long) (base * EXERCISE_BREATH_MULTIPLIER * PANIC_BREATH_MULTIPLIER);
        } else if (exercising) {
            base = (long) (base * EXERCISE_BREATH_MULTIPLIER);
        } else if (panicking) {
            base = (long) (base * PANIC_BREATH_MULTIPLIER);
        }

        // 保证下限，避免过于频繁
        return Math.max(1_200L, base);
    }

    /**
     * 生成单个呼气粒子。
     * 位置在嘴前，带轻微前向速度，模拟呼出的白雾。
     */
    private void spawnBreathParticles(Player player) {
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection();
        World world = player.getWorld();

        // 嘴部位置：眼睛前方 0.35 格，向下 0.12 格
        Location mouth = eye.clone()
                .add(direction.clone().multiply(0.35))
                .subtract(0, 0.12, 0);

        // 粒子速度：沿视线方向轻微前移
        Vector velocity = direction.clone().multiply(0.03);

        try {
            world.spawnParticle(
                    Particle.CLOUD,
                    mouth,
                    0,                     // count = 0 时使用 data 参数精确控制速度
                    velocity.getX(),
                    velocity.getY(),
                    velocity.getZ(),
                    0.02
            );
        } catch (Throwable t) {
            // 回退：某些版本不支持 count=0 + velocity，用普通写法
            world.spawnParticle(
                    Particle.CLOUD,
                    mouth,
                    1,
                    0.03, 0.03, 0.03,
                    0.01
            );
        }
    }

    // ==================== 惊恐状态管理 ====================

    private void updatePanicState(Player player, UUID uuid, boolean lowHealth, long now) {
        boolean isPanicking = panicking.contains(uuid);

        if (lowHealth) {
            if (!isPanicking) {
                panicking.add(uuid);
                onEnterPanic(player);
            }
            panicLockedUntil.put(uuid, now + PANIC_LOCK_MS);
        } else {
            if (isPanicking) {
                Long lockedUntil = panicLockedUntil.get(uuid);
                if (lockedUntil == null || now >= lockedUntil) {
                    panicking.remove(uuid);
                    panicLockedUntil.remove(uuid);
                    onExitPanic(player);
                }
            }
        }
    }

    // ==================== 运动追踪 ====================

    private void updateSprintTracker(Player player, UUID uuid, long now) {
        if (player.isSprinting()) {
            lastSprintTick.put(uuid, now);
            sprintStart.putIfAbsent(uuid, now);
        } else {
            Long last = lastSprintTick.get(uuid);
            if (last == null || now - last > SPRINT_STOP_GRACE_MS) {
                sprintStart.remove(uuid);
                lastSprintTick.remove(uuid);
            }
        }
    }

    private boolean isExercising(UUID uuid, long now) {
        Long start = sprintStart.get(uuid);
        if (start == null) return false;
        return now - start >= SPRINT_TO_SWEAT_THRESHOLD_MS;
    }

    // ==================== 环境检测 ====================

    private boolean isHotEnvironment(Player player) {
        Location loc = player.getLocation();
        if (loc.getWorld() == null) return false;
        try {
            return loc.getBlock().getTemperature() > HOT_TEMPERATURE_THRESHOLD;
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean isColdBiome(Player player) {
        Location loc = player.getLocation();
        if (loc.getWorld() == null) return false;
        try {
            return loc.getBlock().getTemperature() < COLD_TEMPERATURE_THRESHOLD;
        } catch (Throwable t) {
            return false;
        }
    }

    // ==================== 其他粒子 ====================

    private void spawnSweatParticles(Player player, int extra) {
        Location eye = player.getEyeLocation();
        World world = player.getWorld();

        int count = random.nextInt(2) + 1 + extra;
        for (int i = 0; i < count; i++) {
            double offsetX = (random.nextDouble() - 0.5) * 0.7;
            double offsetZ = (random.nextDouble() - 0.5) * 0.7;
            double offsetY = random.nextDouble() * 0.4 + 0.2;

            Location loc = eye.clone().add(offsetX, offsetY, offsetZ);

            world.spawnParticle(
                    Particle.SPLASH,
                    loc,
                    1,
                    0.05, 0.05, 0.05,
                    0.0
            );
        }
    }

    private void spawnAngryVillagerParticles(Player player) {
        World world = player.getWorld();
        Location eye = player.getEyeLocation();

        Location loc = eye.clone().add(
                (random.nextDouble() - 0.5) * 0.6,
                0.3 + random.nextDouble() * 0.3,
                (random.nextDouble() - 0.5) * 0.6
        );

        world.spawnParticle(
                Particle.ANGRY_VILLAGER,
                loc,
                1,
                0.0, 0.0, 0.0,
                0.0
        );
    }

    private void spawnPanicParticles(Player player, long now) {
        Location eye = player.getEyeLocation();
        World world = player.getWorld();

        double angle = random.nextDouble() * Math.PI * 2;
        double radius = 0.6;
        Location loc = eye.clone().add(
                Math.cos(angle) * radius,
                (random.nextDouble() - 0.3) * 0.5,
                Math.sin(angle) * radius
        );

        world.spawnParticle(
                Particle.DUST,
                loc,
                1,
                0.1, 0.1, 0.1,
                0.0,
                new Particle.DustOptions(org.bukkit.Color.RED, 0.8f)
        );

        UUID uuid = player.getUniqueId();

        Long lastBeat = lastHeartbeat.get(uuid);
        if (lastBeat == null || now - lastBeat >= HEARTBEAT_INTERVAL_MS) {
            player.playSound(player.getLocation(),
                    Sound.ENTITY_WARDEN_HEARTBEAT, 0.8f, 1.4f);
            lastHeartbeat.put(uuid, now);
        }

        Long lastAnim = lastHurtAnim.get(uuid);
        if (lastAnim == null || now - lastAnim >= HURT_ANIMATION_INTERVAL_MS) {
            player.playHurtAnimation(player.getLocation().getYaw());
            lastHurtAnim.put(uuid, now);
        }
    }

    private void applyPanicEffects(Player player) {
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.SPEED, 40, 0, false, false, false));
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.WEAKNESS, 40, 0, false, false, false));
    }

    // ==================== 状态进入/退出 ====================

    private void onEnterPanic(Player player) {
        player.sendActionBar(Component.text("⚠ 你进入了惊恐状态！", NamedTextColor.RED));
        player.playSound(player.getLocation(),
                Sound.ENTITY_WARDEN_HEARTBEAT, 1.0f, 1.0f);
    }

    private void onExitPanic(Player player) {
        player.sendActionBar(Component.text("你冷静下来了...", NamedTextColor.GREEN));
        cleanupPanic(player);
    }

    private void cleanupPanic(Player player) {
        if (player.hasPotionEffect(PotionEffectType.SPEED)) {
            PotionEffect effect = player.getPotionEffect(PotionEffectType.SPEED);
            if (effect != null && effect.getAmplifier() == 0 && effect.getDuration() <= 40) {
                player.removePotionEffect(PotionEffectType.SPEED);
            }
        }
        if (player.hasPotionEffect(PotionEffectType.WEAKNESS)) {
            PotionEffect effect = player.getPotionEffect(PotionEffectType.WEAKNESS);
            if (effect != null && effect.getAmplifier() == 0 && effect.getDuration() <= 40) {
                player.removePotionEffect(PotionEffectType.WEAKNESS);
            }
        }
        lastHeartbeat.remove(player.getUniqueId());
        lastHurtAnim.remove(player.getUniqueId());
    }

    // ==================== 惊恐攻击处理 ====================

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!panicking.contains(player.getUniqueId())) return;

        event.setDamage(event.getDamage() * PANIC_DAMAGE_MULTIPLIER);

        Entity target = event.getEntity();
        if (!(target instanceof LivingEntity)) return;

        Vector direction = target.getLocation().toVector()
                .subtract(player.getLocation().toVector());
        direction.setY(0);
        if (direction.lengthSquared() > 0.001) {
            direction.normalize();
        }
        direction.multiply(PANIC_KNOCKBACK_MULTIPLIER);
        direction.setY(0.4);

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (target.isValid()) {
                target.setVelocity(direction);
            }
        });
    }

    // ==================== 供其他模块查询 ====================

    public boolean isSweating(UUID uuid) { return sweating.contains(uuid); }
    public boolean isPanicking(UUID uuid) { return panicking.contains(uuid); }
    public boolean isHungry(UUID uuid) { return hungry.contains(uuid); }
}