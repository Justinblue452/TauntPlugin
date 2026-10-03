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

public class PlayerStatusEffectManager implements Listener {

    private final JavaPlugin plugin;
    private final Random random = new Random();

    // ★ 从配置读取
    private static final long TICK_INTERVAL = 5L;

    // 惊恐
    private double panicThreshold;
    private long panicLockMs;
    private double panicDamageMultiplier;
    private double panicKnockbackMultiplier;
    private long heartbeatIntervalMs;
    private long hurtAnimationIntervalMs;

    // 冒汗
    private double sweatHealthThreshold;
    private double hotTempThreshold;
    private long sprintDurationMs;
    private static final long SPRINT_STOP_GRACE_MS = 3_000L;

    // 白雾
    private double coldTempThreshold;
    private long breathCycleMinMs;
    private long breathCycleMaxMs;
    private static final double EXHALE_RATIO = 0.4;
    private static final int EXHALE_PUFFS_MIN = 3;
    private static final int EXHALE_PUFFS_MAX = 5;
    private static final double EXERCISE_BREATH_MULTIPLIER = 0.5;
    private static final double PANIC_BREATH_MULTIPLIER = 0.6;

    // 饥饿
    private int hungerAngryThreshold;

    private final Set<UUID> sweating = ConcurrentHashMap.newKeySet();
    private final Set<UUID> panicking = ConcurrentHashMap.newKeySet();
    private final Set<UUID> hungry = ConcurrentHashMap.newKeySet();

    private final Map<UUID, Long> sprintStart = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastSprintTick = new ConcurrentHashMap<>();
    private final Map<UUID, Long> panicLockedUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastHeartbeat = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastHurtAnim = new ConcurrentHashMap<>();
    private final Map<UUID, BreathState> breathStates = new ConcurrentHashMap<>();

    private BukkitTask tickTask;

    private static class BreathState {
        long cycleStart;
        long cycleDuration;
        int plannedPuffs;
        int emittedPuffs;
        long lastPuffTime;

        BreathState(long start, long duration) {
            this.cycleStart = start;
            this.cycleDuration = duration;
        }
    }

    public PlayerStatusEffectManager(JavaPlugin plugin, ConfigManager config) {
        this.plugin = plugin;

        this.panicThreshold = config.getDouble("player-status.panic.threshold", 0.25);
        this.panicLockMs = config.getLong("player-status.panic.lock-ms", 15000);
        this.panicDamageMultiplier = config.getDouble("player-status.panic.damage-multiplier", 0.7);
        this.panicKnockbackMultiplier = config.getDouble("player-status.panic.knockback-multiplier", 3.0);
        this.heartbeatIntervalMs = config.getLong("player-status.panic.heartbeat-interval-ms", 1000);
        this.hurtAnimationIntervalMs = config.getLong("player-status.panic.hurt-animation-interval-ms", 1500);

        this.sweatHealthThreshold = config.getDouble("player-status.sweat.health-threshold", 0.5);
        this.hotTempThreshold = config.getDouble("player-status.sweat.hot-temp-threshold", 0.9);
        this.sprintDurationMs = config.getLong("player-status.sweat.sprint-duration-ms", 8000);

        this.coldTempThreshold = config.getDouble("player-status.breath.cold-temp-threshold", 0.15);
        this.breathCycleMinMs = config.getLong("player-status.breath.cycle-min-ms", 3000);
        this.breathCycleMaxMs = config.getLong("player-status.breath.cycle-max-ms", 4500);

        this.hungerAngryThreshold = config.getInt("player-status.hungry.food-threshold", 10);

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

    private void startTickTask() {
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                try { tickPlayer(p); } catch (Exception ignored) {}
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

        boolean lowHealth = ratio > 0 && ratio < panicThreshold;
        boolean shouldSweatFromHealth = ratio > 0 && ratio < sweatHealthThreshold;

        updateSprintTracker(player, uuid, now);
        boolean shouldSweatFromExercise = isExercising(uuid, now);
        boolean shouldSweatFromHeat = isHotEnvironment(player);
        boolean shouldSweat = shouldSweatFromHealth || shouldSweatFromExercise || shouldSweatFromHeat;

        if (shouldSweat) sweating.add(uuid);
        else sweating.remove(uuid);

        updatePanicState(player, uuid, lowHealth, now);

        int foodLevel = player.getFoodLevel();
        boolean shouldAngry = foodLevel > 0 && foodLevel < hungerAngryThreshold;
        if (shouldAngry) hungry.add(uuid);
        else hungry.remove(uuid);

        if (shouldSweat) {
            int extra = (shouldSweatFromExercise || shouldSweatFromHeat) ? 1 : 0;
            spawnSweatParticles(player, extra);
        }
        if (panicking.contains(uuid)) spawnPanicParticles(player, now);
        if (shouldAngry) spawnAngryVillagerParticles(player);

        if (isColdBiome(player)) {
            tickBreath(player, uuid, now, shouldSweatFromExercise, panicking.contains(uuid));
        } else {
            breathStates.remove(uuid);
        }

        if (panicking.contains(uuid)) applyPanicEffects(player);
    }

    // ==================== 惊恐状态管理 ====================

    private void updatePanicState(Player player, UUID uuid, boolean lowHealth, long now) {
        boolean isPanicking = panicking.contains(uuid);

        if (lowHealth) {
            if (!isPanicking) {
                panicking.add(uuid);
                onEnterPanic(player);
            }
            panicLockedUntil.put(uuid, now + panicLockMs);
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
        return now - start >= sprintDurationMs;
    }

    private boolean isHotEnvironment(Player player) {
        Location loc = player.getLocation();
        if (loc.getWorld() == null) return false;
        try {
            return loc.getBlock().getTemperature() > hotTempThreshold;
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean isColdBiome(Player player) {
        Location loc = player.getLocation();
        if (loc.getWorld() == null) return false;
        try {
            return loc.getBlock().getTemperature() < coldTempThreshold;
        } catch (Throwable t) {
            return false;
        }
    }

    // ==================== 呼吸 ====================

    private void tickBreath(Player player, UUID uuid, long now, boolean exercising, boolean panicking) {
        BreathState state = breathStates.get(uuid);

        if (state == null || now - state.cycleStart >= state.cycleDuration) {
            state = new BreathState(now, calculateCycleDuration(exercising, panicking));
            state.plannedPuffs = EXHALE_PUFFS_MIN + random.nextInt(EXHALE_PUFFS_MAX - EXHALE_PUFFS_MIN + 1);
            state.emittedPuffs = 0;
            state.lastPuffTime = now;
            breathStates.put(uuid, state);
            return;
        }

        long elapsed = now - state.cycleStart;
        long exhaleDuration = (long) (state.cycleDuration * EXHALE_RATIO);

        if (elapsed < exhaleDuration && state.emittedPuffs < state.plannedPuffs) {
            long puffInterval = exhaleDuration / state.plannedPuffs;
            if (now - state.lastPuffTime >= puffInterval) {
                spawnBreathParticles(player);
                state.lastPuffTime = now;
                state.emittedPuffs++;
            }
        }
    }

    private long calculateCycleDuration(boolean exercising, boolean panicking) {
        long base = breathCycleMinMs
                + (long) (random.nextDouble() * (breathCycleMaxMs - breathCycleMinMs));

        if (exercising && panicking) base = (long) (base * EXERCISE_BREATH_MULTIPLIER * PANIC_BREATH_MULTIPLIER);
        else if (exercising) base = (long) (base * EXERCISE_BREATH_MULTIPLIER);
        else if (panicking) base = (long) (base * PANIC_BREATH_MULTIPLIER);

        return Math.max(1_200L, base);
    }

    // ==================== 粒子 ====================

    private void spawnSweatParticles(Player player, int extra) {
        Location eye = player.getEyeLocation();
        World world = player.getWorld();

        int count = random.nextInt(2) + 1 + extra;
        for (int i = 0; i < count; i++) {
            double offsetX = (random.nextDouble() - 0.5) * 0.7;
            double offsetZ = (random.nextDouble() - 0.5) * 0.7;
            double offsetY = random.nextDouble() * 0.4 + 0.2;
            Location loc = eye.clone().add(offsetX, offsetY, offsetZ);
            world.spawnParticle(Particle.SPLASH, loc, 1, 0.05, 0.05, 0.05, 0.0);
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
        world.spawnParticle(Particle.ANGRY_VILLAGER, loc, 1, 0.0, 0.0, 0.0, 0.0);
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

        try {
            world.spawnParticle(Particle.DUST, loc, 1, 0.1, 0.1, 0.1, 0.0,
                    new Particle.DustOptions(org.bukkit.Color.RED, 0.8f));
        } catch (Throwable ignored) {}

        UUID uuid = player.getUniqueId();

        Long lastBeat = lastHeartbeat.get(uuid);
        if (lastBeat == null || now - lastBeat >= heartbeatIntervalMs) {
            player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 0.8f, 1.4f);
            lastHeartbeat.put(uuid, now);
        }

        Long lastAnim = lastHurtAnim.get(uuid);
        if (lastAnim == null || now - lastAnim >= hurtAnimationIntervalMs) {
            try {
                player.playHurtAnimation(player.getLocation().getYaw());
            } catch (Throwable ignored) {}
            lastHurtAnim.put(uuid, now);
        }
    }

    private void applyPanicEffects(Player player) {
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.SPEED, 40, 0, false, false, false));
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.WEAKNESS, 40, 0, false, false, false));
    }

    private void onEnterPanic(Player player) {
        player.sendActionBar(Component.text("⚠ 你进入了惊恐状态！", NamedTextColor.RED));
        player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.0f, 1.0f);
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

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!panicking.contains(player.getUniqueId())) return;

        event.setDamage(event.getDamage() * panicDamageMultiplier);

        Entity target = event.getEntity();
        if (!(target instanceof LivingEntity)) return;

        Vector direction = target.getLocation().toVector()
                .subtract(player.getLocation().toVector());
        direction.setY(0);
        if (direction.lengthSquared() > 0.001) {
            direction.normalize();
        }
        direction.multiply(panicKnockbackMultiplier);
        direction.setY(0.4);

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (target.isValid()) {
                target.setVelocity(direction);
            }
        });
    }

    private void spawnBreathParticles(Player player) {
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection();
        World world = player.getWorld();

        Location mouth = eye.clone()
                .add(direction.clone().multiply(0.35))
                .subtract(0, 0.12, 0);

        Vector velocity = direction.clone().multiply(0.03);

        try {
            world.spawnParticle(Particle.CLOUD, mouth, 0,
                    velocity.getX(), velocity.getY(), velocity.getZ(), 0.02);
        } catch (Throwable t) {
            world.spawnParticle(Particle.CLOUD, mouth, 1, 0.03, 0.03, 0.03, 0.01);
        }
    }

    public boolean isSweating(UUID uuid) { return sweating.contains(uuid); }
    public boolean isPanicking(UUID uuid) { return panicking.contains(uuid); }
    public boolean isHungry(UUID uuid) { return hungry.contains(uuid); }
}