package com.example.tauntplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GhostModeManager implements Listener {

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final Map<UUID, GhostSession> sessions = new ConcurrentHashMap<>();

    private long ghostDurationMs;
    private double sphereRadius;
    private double rotationSpeed;

    public GhostModeManager(JavaPlugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;

        this.ghostDurationMs = config.getLong("ghost-mode.duration-ms", 30000);
        this.sphereRadius = config.getDouble("ghost-mode.sphere-radius", 2.0);
        this.rotationSpeed = config.getDouble("ghost-mode.rotation-speed", 0.03);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFatalDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (sessions.containsKey(player.getUniqueId())) return;
        if (event.getFinalDamage() < player.getHealth()) return;
        if (hasTotemOfUndying(player)) return;

        // 正在被处决的玩家不进入幽灵模式
        if (ExecutionManager.isBeingExecuted(player.getUniqueId())) return;

        // 被玩家击杀时不进入幽灵模式
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            Entity damager = byEntity.getDamager();
            if (damager instanceof Player) return;
        }

        event.setCancelled(true);
        enterGhostMode(player);
    }

    private boolean hasTotemOfUndying(Player player) {
        if (player.getInventory().getItemInMainHand().getType() == Material.TOTEM_OF_UNDYING) return true;
        return player.getInventory().getItemInOffHand().getType() == Material.TOTEM_OF_UNDYING;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        GhostSession session = sessions.get(event.getPlayer().getUniqueId());
        if (session != null) endGhostMode(session);
    }

    private void enterGhostMode(Player player) {
        UUID uuid = player.getUniqueId();
        GameMode originalMode = player.getGameMode();
        Location deathLoc = player.getLocation().clone();

        List<ItemStack> items = new ArrayList<>();
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && !stack.getType().isAir()) items.add(stack.clone());
        }
        for (ItemStack stack : player.getInventory().getArmorContents()) {
            if (stack != null && !stack.getType().isAir()) items.add(stack.clone());
        }
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand != null && !offhand.getType().isAir()) items.add(offhand.clone());

        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(null);

        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }

        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setFireTicks(0);
        player.setFallDistance(0);

        player.setGameMode(GameMode.SPECTATOR);

        List<ItemDisplay> displays = createSphere(player, items);

        GhostSession session = new GhostSession(
                uuid, originalMode, deathLoc, items, displays,
                System.currentTimeMillis() + ghostDurationMs
        );
        sessions.put(uuid, session);

        Title title = Title.title(
                Component.text("幽灵模式", NamedTextColor.DARK_PURPLE),
                Component.text("30 秒后重生", NamedTextColor.GRAY),
                Title.Times.times(
                        Duration.ofMillis(300),
                        Duration.ofMillis(2000),
                        Duration.ofMillis(500))
        );
        player.showTitle(title);

        player.playSound(net.kyori.adventure.sound.Sound.sound(
                org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT,
                net.kyori.adventure.sound.Sound.Source.PLAYER,
                1.0f, 0.5f));

        startFollowTask(session);

        // ★ 成就挂钩：灵魂出窍
        if (plugin instanceof TauntPlugin tp && tp.getAchievementManager() != null) {
            tp.getAchievementManager().unlock(player, AchievementManager.Ach.ENTER_GHOST);
        }

        plugin.getLogger().info("[幽灵模式] " + player.getName() + " 进入幽灵模式");
    }

    private List<ItemDisplay> createSphere(Player player, List<ItemStack> items) {
        List<ItemDisplay> displays = new ArrayList<>();
        if (items.isEmpty()) return displays;

        World world = player.getWorld();
        Location base = player.getLocation();
        int n = items.size();
        double golden = Math.PI * (1 + Math.sqrt(5));

        for (int i = 0; i < n; i++) {
            double[] pos = spherePoint(i, n, golden, 0);
            Location loc = base.clone().add(pos[0], pos[1] + 1.0, pos[2]);
            ItemStack item = items.get(i);

            ItemDisplay display = world.spawn(loc, ItemDisplay.class, d -> {
                d.setItemStack(item);
                d.setBillboard(Display.Billboard.CENTER);
                d.setGravity(false);
                d.setInvulnerable(true);
                d.setPersistent(false);
            });
            displays.add(display);
        }
        return displays;
    }

    private double[] spherePoint(int i, int n, double golden, double angleOffset) {
        double phi = Math.acos(1 - 2 * (i + 0.5) / n);
        double theta = golden * i + angleOffset;
        double x = sphereRadius * Math.sin(phi) * Math.cos(theta);
        double y = sphereRadius * Math.cos(phi);
        double z = sphereRadius * Math.sin(phi) * Math.sin(theta);
        return new double[]{x, y, z};
    }

    private void startFollowTask(GhostSession session) {
        final double[] angle = {0};

        session.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long remain = session.endTime - System.currentTimeMillis();
            if (remain <= 0) { endGhostMode(session); return; }

            Player p = Bukkit.getPlayer(session.playerId);
            if (p == null || !p.isOnline()) { endGhostMode(session); return; }
            if (p.getGameMode() != GameMode.SPECTATOR) { endGhostMode(session); return; }

            int remainSec = (int) Math.ceil(remain / 1000.0);
            updateCountdown(p, session, remainSec);

            angle[0] += rotationSpeed;
            Location base = p.getLocation();
            int n = session.displays.size();
            if (n == 0) return;
            double golden = Math.PI * (1 + Math.sqrt(5));

            for (int i = 0; i < n; i++) {
                ItemDisplay d = session.displays.get(i);
                if (d == null || !d.isValid()) continue;
                double[] pos = spherePoint(i, n, golden, angle[0]);
                Location loc = base.clone().add(pos[0], pos[1] + 1.0, pos[2]);
                d.teleport(loc);
            }
        }, 0L, 1L);
    }

    private void updateCountdown(Player player, GhostSession session, int remainSec) {
        NamedTextColor color;
        if (remainSec > 20) color = NamedTextColor.GREEN;
        else if (remainSec > 10) color = NamedTextColor.YELLOW;
        else if (remainSec > 5) color = NamedTextColor.GOLD;
        else color = NamedTextColor.RED;

        Component message = Component.text("👻 幽灵模式 · 剩余 ", NamedTextColor.DARK_PURPLE)
                .append(Component.text(remainSec + " 秒", color));

        player.sendActionBar(message);

        if (remainSec != session.lastDisplayedSecond) {
            session.lastDisplayedSecond = remainSec;

            if (remainSec <= 10 && remainSec > 0) {
                float pitch = 0.8f + (10 - remainSec) * 0.1f;
                player.playSound(net.kyori.adventure.sound.Sound.sound(
                        org.bukkit.Sound.BLOCK_NOTE_BLOCK_HAT,
                        net.kyori.adventure.sound.Sound.Source.PLAYER,
                        0.6f, pitch));
            }

            if (remainSec <= 3 && remainSec > 0) {
                player.playSound(net.kyori.adventure.sound.Sound.sound(
                        org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS,
                        net.kyori.adventure.sound.Sound.Source.PLAYER,
                        0.8f, 0.5f));
            }
        }
    }

    private void endGhostMode(GhostSession session) {
        if (session.task != null) {
            session.task.cancel();
            session.task = null;
        }
        sessions.remove(session.playerId);

        for (ItemDisplay d : session.displays) {
            if (d != null && d.isValid()) d.remove();
        }
        session.displays.clear();

        Player player = Bukkit.getPlayer(session.playerId);

        Location dropLocation;
        if (player != null && player.isOnline()) {
            dropLocation = player.getLocation().clone();
        } else {
            dropLocation = session.deathLocation;
        }

        dropItemsAt(dropLocation, session.items);

        if (player == null || !player.isOnline()) {
            plugin.getLogger().info("[幽灵模式] 玩家离线，物品已掉落在 "
                    + formatLocation(dropLocation));
            return;
        }

        player.sendActionBar(Component.empty());
        player.setGameMode(session.originalMode);

        Location respawn = null;
        try {
            respawn = player.getRespawnLocation();
        } catch (Throwable ignored) {}
        if (respawn == null) respawn = player.getWorld().getSpawnLocation();

        player.teleport(respawn);

        Title title = Title.title(
                Component.text("重生", NamedTextColor.GOLD),
                Component.text("你的物品掉落在了死亡地点", NamedTextColor.YELLOW),
                Title.Times.times(
                        Duration.ofMillis(300),
                        Duration.ofMillis(2000),
                        Duration.ofMillis(500))
        );
        player.showTitle(title);
        player.playSound(net.kyori.adventure.sound.Sound.sound(
                org.bukkit.Sound.ENTITY_PLAYER_LEVELUP,
                net.kyori.adventure.sound.Sound.Source.PLAYER,
                1.0f, 1.5f));

        plugin.getLogger().info("[幽灵模式] " + player.getName()
                + " 已重生，物品掉落在 " + formatLocation(dropLocation));
    }

    private void dropItemsAt(Location location, List<ItemStack> items) {
        if (items.isEmpty()) return;
        if (location == null || location.getWorld() == null) return;

        World world = location.getWorld();

        if (location.getY() < world.getMinHeight() + 1) {
            location = world.getSpawnLocation();
            plugin.getLogger().warning("[幽灵模式] 掉落位置过低，改到世界出生点");
        }

        final Location dropLoc = location.clone();
        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir()) {
                world.dropItemNaturally(dropLoc, item);
            }
        }
    }

    private String formatLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) return "未知位置";
        return String.format("%s (%.1f, %.1f, %.1f)",
                loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());
    }

    public void shutdown() {
        for (GhostSession session : new ArrayList<>(sessions.values())) {
            if (session.task != null) session.task.cancel();
            for (ItemDisplay d : session.displays) {
                if (d != null && d.isValid()) d.remove();
            }
            dropItemsAt(session.deathLocation, session.items);
        }
        sessions.clear();
    }

    private static class GhostSession {
        final UUID playerId;
        final GameMode originalMode;
        final Location deathLocation;
        final List<ItemStack> items;
        final List<ItemDisplay> displays;
        final long endTime;
        BukkitTask task;
        int lastDisplayedSecond = -1;

        GhostSession(UUID playerId, GameMode originalMode, Location deathLocation,
                     List<ItemStack> items, List<ItemDisplay> displays, long endTime) {
            this.playerId = playerId;
            this.originalMode = originalMode;
            this.deathLocation = deathLocation;
            this.items = items;
            this.displays = displays;
            this.endTime = endTime;
        }
    }
}