package com.example.tauntplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ExecutionManager implements Listener {

    private final JavaPlugin plugin;
    private final MessageManager messages;

    private int playerPhase1End;
    private int playerPhase2End;
    private int playerPhase3End;
    private double playerLiftHeight;

    private int mobExecTicks;
    private double mobLiftHeight;

    private boolean autoExecuteOnKill;

    private final Map<UUID, Long> recentlyExecuted = new ConcurrentHashMap<>();
    private static final long DEDUP_MS = 3_000L;

    private final Map<UUID, BukkitTask> activeExecutions = new ConcurrentHashMap<>();
    private static final Set<UUID> executingPlayers = ConcurrentHashMap.newKeySet();

    public static boolean isBeingExecuted(UUID uuid) {
        return executingPlayers.contains(uuid);
    }

    public ExecutionManager(JavaPlugin plugin, MessageManager messages, ConfigManager config) {
        this.plugin = plugin;
        this.messages = messages;

        this.playerPhase1End = config.getInt("execution.player-phase1-end", 15);
        this.playerPhase2End = config.getInt("execution.player-phase2-end", 40);
        this.playerPhase3End = config.getInt("execution.player-phase3-end", 70);
        this.playerLiftHeight = config.getDouble("execution.player-lift-height", 2.5);

        this.mobExecTicks = config.getInt("execution.mob-exec-ticks", 30);
        this.mobLiftHeight = config.getDouble("execution.mob-lift-height", 1.5);

        this.autoExecuteOnKill = config.getBoolean("execution.auto-execute-on-kill", true);
    }

    public void shutdown() {
        for (BukkitTask task : activeExecutions.values()) {
            if (task != null) task.cancel();
        }
        activeExecutions.clear();
        recentlyExecuted.clear();
        executingPlayers.clear();
    }

    // ==================== 单个实体 ====================
    public boolean execute(CommandSender sender, Entity target) {
        if (target == null || !target.isValid()) return false;

        if (isSelf(sender, target)) {
            messages.send(sender, "execution.self-target");
            return false;
        }

        String executorName = sender instanceof Player p ? p.getName() : "服务器";
        String targetName = TauntUtils.getEntityDisplayName(target);

        recentlyExecuted.put(target.getUniqueId(), System.currentTimeMillis());

        messages.broadcast("execution.executed", Map.of(
                "{executor}", executorName,
                "{target}", targetName
        ));

        dispatchExecution(target);
        return true;
    }

    // ==================== 批量 ====================
    public int executeNear(CommandSender sender, Location center, double radius) {
        if (center.getWorld() == null) return 0;
        List<Entity> targets = new ArrayList<>();
        for (Entity e : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (e instanceof Player p && p.getGameMode().name().equals("SPECTATOR")) continue;
            if (isSelf(sender, e)) continue;
            targets.add(e);
        }
        return executeBatch(sender, targets, "范围内 " + targets.size() + " 个实体");
    }

    public int executeByType(CommandSender sender, Location center, EntityType type, double radius) {
        if (center.getWorld() == null) return 0;
        List<Entity> targets = new ArrayList<>();
        for (Entity e : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (e.getType() != type) continue;
            if (e instanceof Player p && p.getGameMode().name().equals("SPECTATOR")) continue;
            if (isSelf(sender, e)) continue;
            targets.add(e);
        }
        return executeBatch(sender, targets, type.name() + " × " + targets.size());
    }

    public Entity executeLookingAt(CommandSender sender, Player player, double maxDistance) {
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection();
        RayTraceResult result = eye.getWorld().rayTraceEntities(
                eye, direction, maxDistance,
                e -> !e.equals(player) && e.isValid());
        if (result == null || result.getHitEntity() == null) return null;
        Entity target = result.getHitEntity();
        if (isSelf(sender, target)) return null;
        execute(sender, target);
        return target;
    }

    private int executeBatch(CommandSender sender, List<Entity> targets, String summary) {
        if (targets.isEmpty()) return 0;
        targets.removeIf(e -> isSelf(sender, e));
        if (targets.isEmpty()) return 0;

        String executorName = sender instanceof Player p ? p.getName() : "服务器";

        messages.broadcast("execution.batch", Map.of(
                "{executor}", executorName,
                "{summary}", summary
        ));

        for (Entity target : targets) {
            recentlyExecuted.put(target.getUniqueId(), System.currentTimeMillis());
            dispatchExecution(target);
        }
        return targets.size();
    }

    private boolean isSelf(CommandSender sender, Entity target) {
        if (!(sender instanceof Player player)) return false;
        return player.getUniqueId().equals(target.getUniqueId());
    }

    private void dispatchExecution(Entity target) {
        if (target instanceof Player player) {
            startPlayerExecution(player);
        } else if (target instanceof LivingEntity living) {
            startMobExecution(living);
        } else {
            playExecutionEffect(target);
            target.remove();
        }
    }

    // ==================== 玩家处决 ====================
    private void startPlayerExecution(Player victim) {
        UUID uuid = victim.getUniqueId();
        if (activeExecutions.containsKey(uuid)) return;

        executingPlayers.add(uuid);

        final Location startLoc = victim.getLocation().clone();
        final double startHealth = victim.getHealth();

        victim.setInvulnerable(true);

        final int[] tick = {0};
        final BukkitTask[] holder = new BukkitTask[1];

        holder[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!victim.isOnline()) {
                holder[0].cancel();
                activeExecutions.remove(uuid);
                executingPlayers.remove(uuid);
                return;
            }

            int t = tick[0]++;

            if (t == 0) {
                victim.playSound(victim.getLocation(), Sound.ENTITY_ENDERMAN_STARE, 1.0f, 0.5f);
                victim.playSound(victim.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.0f, 0.5f);
            }

            if (t <= playerPhase1End) {
                lockView(victim, startLoc);
            }

            if (t > playerPhase1End && t <= playerPhase2End) {
                double progress = (t - playerPhase1End) / (double) (playerPhase2End - playerPhase1End);
                Location loc = startLoc.clone();
                loc.add(0, playerLiftHeight * progress, 0);
                lockView(victim, loc);
                if (t % 3 == 0) {
                    Location vl = victim.getLocation();
                    TauntUtils.spawnParticleSafe(victim.getWorld(), Particle.CLOUD,
                            vl.getX(), vl.getY() + 0.2, vl.getZ(),
                            5, 0.3, 0.1, 0.3, 0.02);
                }
            }

            if (t > playerPhase2End && t <= playerPhase3End) {
                double progress = (t - playerPhase2End) / (double) (playerPhase3End - playerPhase2End);
                Location loc = startLoc.clone();
                loc.add(0, playerLiftHeight, 0);
                lockView(victim, loc);
                double newHealth = startHealth * (1.0 - progress);
                if (newHealth < 0.5) newHealth = 0.5;
                try { victim.setHealth(newHealth); } catch (Throwable ignored) {}

                if (t % 5 == 0) {
                    try { victim.playHurtAnimation(victim.getLocation().getYaw()); } catch (Throwable ignored) {}
                    Location vl = victim.getLocation();
                    TauntUtils.spawnParticleSafe(victim.getWorld(), Particle.DAMAGE_INDICATOR,
                            vl.getX(), vl.getY() + 1.0, vl.getZ(),
                            5, 0.5, 0.5, 0.5, 0.1);
                    victim.playSound(victim.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.8f, 1.0f);
                }
            }

            if (t >= playerPhase3End) {
                playExecutionEffect(victim);
                killPlayer(victim);

                TauntUtils.increment(plugin, victim, "executions_received", 1);
                TauntUtils.unlock(plugin, victim, AchievementManager.Ach.EXECUTED);

                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!p.equals(victim)) {
                        TauntUtils.increment(plugin, p, "executions_witnessed", 1);
                        TauntUtils.unlock(plugin, p, AchievementManager.Ach.WITNESS_EXECUTION);
                    }
                }

                holder[0].cancel();
                activeExecutions.remove(uuid);
                executingPlayers.remove(uuid);
            }
        }, 0L, 1L);

        activeExecutions.put(uuid, holder[0]);
    }

    private void lockView(Player player, Location base) {
        Location loc = base.clone();
        loc.setYaw(base.getYaw());
        loc.setPitch(base.getPitch());
        player.teleport(loc);
    }

    private void killPlayer(Player victim) {
        TauntUtils.killPlayer(plugin, victim);
    }

    // ==================== 生物处决 ====================
    private void startMobExecution(LivingEntity mob) {
        UUID uuid = mob.getUniqueId();
        if (activeExecutions.containsKey(uuid)) return;

        final Location startLoc = mob.getLocation().clone();
        final double startHealth = mob.getHealth();

        if (mob instanceof org.bukkit.entity.Mob m) m.setAI(false);
        mob.setInvulnerable(true);

        final int[] tick = {0};
        final BukkitTask[] holder = new BukkitTask[1];

        holder[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!mob.isValid() || mob.isDead()) {
                holder[0].cancel();
                activeExecutions.remove(uuid);
                return;
            }

            int t = tick[0]++;

            if (t <= mobExecTicks) {
                double progress = t / (double) mobExecTicks;
                Location loc = startLoc.clone();
                loc.add(0, mobLiftHeight * progress, 0);
                loc.setYaw(startLoc.getYaw());
                loc.setPitch(startLoc.getPitch());
                mob.teleport(loc);

                double newHealth = startHealth * (1.0 - progress);
                if (newHealth < 0.5) newHealth = 0.5;
                try { mob.setHealth(newHealth); } catch (Throwable ignored) {}

                if (t % 2 == 0) {
                    Location ml = mob.getLocation();
                    TauntUtils.spawnParticleSafe(mob.getWorld(), Particle.CLOUD,
                            ml.getX(), ml.getY() + 0.5, ml.getZ(),
                            3, 0.3, 0.2, 0.3, 0.02);
                    TauntUtils.spawnParticleSafe(mob.getWorld(), Particle.DAMAGE_INDICATOR,
                            ml.getX(), ml.getY() + 0.5, ml.getZ(),
                            3, 0.3, 0.3, 0.3, 0.1);
                }
            }

            if (t >= mobExecTicks) {
                playExecutionEffect(mob);
                mob.setInvulnerable(false);
                mob.setHealth(0.0);

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (mob.isValid() && !mob.isDead() && mob.getHealth() > 0) {
                        mob.setInvulnerable(false);
                        mob.damage(Double.MAX_VALUE);
                    }
                }, 2L);

                holder[0].cancel();
                activeExecutions.remove(uuid);
            }
        }, 0L, 1L);

        activeExecutions.put(uuid, holder[0]);
    }

    // ==================== 被玩家击杀时自动处决 ====================
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!autoExecuteOnKill) return;

        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null) return;

        Long lastExec = recentlyExecuted.get(victim.getUniqueId());
        if (lastExec != null && System.currentTimeMillis() - lastExec < DEDUP_MS) {
            return;
        }

        recentlyExecuted.put(victim.getUniqueId(), System.currentTimeMillis());

        playExecutionEffect(victim);

        messages.broadcast("execution.killed-by-player", Map.of(
                "{killer}", killer.getName(),
                "{victim}", victim.getName()
        ));

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.equals(victim) && !p.equals(killer)) {
                TauntUtils.increment(plugin, p, "executions_witnessed", 1);
                TauntUtils.unlock(plugin, p, AchievementManager.Ach.WITNESS_EXECUTION);
            }
        }
    }

    // ==================== 特效 ====================
    public void playExecutionEffect(Entity target) {
        Location loc = target.getLocation();
        World world = target.getWorld();

        TauntUtils.spawnParticleSafe(world, Particle.SOUL_FIRE_FLAME,
                loc.getX(), loc.getY(), loc.getZ(), 60, 1.0, 1.5, 1.0, 0.1);
        TauntUtils.spawnParticleSafe(world, Particle.DUST,
                loc.getX(), loc.getY(), loc.getZ(), 50, 0.8, 1.5, 0.8, 0.1,
                new Particle.DustOptions(Color.RED, 2.0f));
        TauntUtils.spawnParticleSafe(world, Particle.LARGE_SMOKE,
                loc.getX(), loc.getY(), loc.getZ(), 30, 1.0, 1.5, 1.0, 0.05);
        TauntUtils.spawnParticleSafe(world, Particle.EXPLOSION,
                loc.getX(), loc.getY(), loc.getZ(), 3, 0.5, 1.0, 0.5, 0);
        TauntUtils.spawnParticleSafe(world, Particle.END_ROD,
                loc.getX(), loc.getY(), loc.getZ(), 25, 0.5, 1.5, 0.5, 0.1);

        world.strikeLightningEffect(loc);
        world.playSound(loc, Sound.ENTITY_WITHER_DEATH, 1.0f, 0.5f);
        world.playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f);
        world.playSound(loc, Sound.ENTITY_PLAYER_DEATH, 1.0f, 0.5f);
    }

    public boolean wasRecentlyExecuted(UUID uuid) {
        Long last = recentlyExecuted.get(uuid);
        return last != null && System.currentTimeMillis() - last < DEDUP_MS;
    }
}