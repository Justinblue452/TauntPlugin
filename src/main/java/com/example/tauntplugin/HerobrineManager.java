package com.example.tauntplugin;

import com.destroystokyo.paper.profile.ProfileProperty;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class HerobrineManager implements Listener {

    private final JavaPlugin plugin;
    private final SkinFetcher skinFetcher;
    private final Random random = new Random();

    private String skinOwnerUuid;

    private boolean hauntEnabled;
    private long hauntCheckIntervalMs;
    private long hauntPlayerCooldownMs;
    private double hauntTriggerChance;
    private long hauntLifetimeMs;
    private int hauntSpawnDistance;
    private int hauntDisappearDistance;

    private boolean trackerEnabled;
    private long trackerCheckIntervalMs;
    private long trackerPlayerCooldownMs;
    private double trackerTriggerChance;
    private long trackerLifetimeMs;
    private double trackerTargetDistance;
    private static final double TRACKER_MIN_DISTANCE = 8.0;
    private static final double TRACKER_MAX_DISTANCE = 12.0;
    private static final long TRACKER_UPDATE_INTERVAL_MS = 250L;

    private boolean signEnabled;
    private long signCheckIntervalMs;
    private long signPlayerCooldownMs;
    private double signTriggerChance;
    private long signLifetimeMs;
    private static final int SIGN_MIN_DISTANCE = 4;
    private static final int SIGN_MAX_DISTANCE = 10;

    private boolean bedsideEnabled;
    private long bedsideDelayMs;
    private long bedsideLifetimeMs;
    private double bedsideTriggerChance;

    private static final double LOOK_THRESHOLD = 0.65;

    // ★ 使用 CooldownManager 替代三个 Map<UUID, Long>
    private final CooldownManager cooldowns;

    private static final List<String> SEEN_MESSAGES = List.of(
            "你感觉到一股视线落在你背后...",
            "Herobrine 就在你身后...",
            "身后的空气突然冷了下来。",
            "你确定你身后只有空气吗？",
            "一阵寒意沿着你的脊椎爬上来...",
            "别回头，%player%，千万别回头。",
            "你听到身后传来轻微的呼吸声。",
            "有人在你身后，%player%。"
    );

    private static final List<String> GONE_MESSAGES = List.of(
            "你回头了，但那里什么也没有。",
            "当你再看时，身影已经消失了。",
            "一阵冷风吹过，身后空无一人。",
            "Herobrine 消失了，仿佛从未存在过。",
            "你眨了眨眼，那身影就不见了。",
            "身后只剩下一片安静，太安静了。"
    );

    private static final List<String> TRACKER_START_MESSAGES = List.of(
            "你感觉有人在跟着你...",
            "背后似乎有个身影...",
            "有什么东西一直在你不远处。",
            "你听见了脚步声，但周围没有别人。",
            "有人在远处看着你。"
    );

    private static final List<String> TRACKER_END_MESSAGES = List.of(
            "你回头看了一眼，那身影消失了。",
            "它看到你在看它，转身离开了。",
            "当你再看时，它已经不在了。",
            "你眨了下眼，跟随者就不见了。"
    );

    private static final List<String> SIGN_MESSAGES = List.of(
            "我看到你了", "你在哪", "我一直在看着你", "别回头", "我在你身后",
            "你逃不掉的", "下次再见", "我知道你家在哪", "你睡了吗", "一个人吗"
    );

    private static final List<String> BEDSIDE_MESSAGES = List.of(
            "你感觉到床边有人...",
            "半夜醒来，你好像看到了什么。",
            "睡眠中，你隐约感觉到身旁有目光。",
            "你翻了个身，床边似乎站着一个人。",
            "月光下，床边有一个身影。"
    );

    private final Map<UUID, Mannequin> activeHaunts = new ConcurrentHashMap<>();
    private final Map<UUID, TrackerSession> activeTrackers = new ConcurrentHashMap<>();
    private final Map<UUID, Mannequin> activeBedsides = new ConcurrentHashMap<>();

    private BukkitTask hauntTask;
    private BukkitTask trackerTask;
    private BukkitTask signTask;

    private static class TrackerSession {
        final Mannequin mannequin;
        final UUID playerId;
        final long startTime;
        BukkitTask task;
        long lastAngle = 0;

        TrackerSession(Mannequin mannequin, UUID playerId) {
            this.mannequin = mannequin;
            this.playerId = playerId;
            this.startTime = System.currentTimeMillis();
        }

        void cancel() {
            if (task != null) { task.cancel(); task = null; }
            if (mannequin != null && mannequin.isValid()) mannequin.remove();
        }
    }

    public HerobrineManager(JavaPlugin plugin, SkinFetcher skinFetcher, ConfigManager config) {
        this.plugin = plugin;
        this.skinFetcher = skinFetcher;

        this.skinOwnerUuid = config.getString("herobrine.skin-owner-uuid",
                "069a79f4-44e9-4726-a5be-fca90e38aaf5");

        this.hauntEnabled = config.getBoolean("herobrine.haunt.enabled", true);
        this.hauntCheckIntervalMs = config.getLong("herobrine.haunt.check-interval-ms", 120000);
        this.hauntPlayerCooldownMs = config.getLong("herobrine.haunt.player-cooldown-ms", 600000);
        this.hauntTriggerChance = config.getDouble("herobrine.haunt.trigger-chance", 0.3);
        this.hauntLifetimeMs = config.getLong("herobrine.haunt.lifetime-ms", 5000);
        this.hauntSpawnDistance = config.getInt("herobrine.haunt.spawn-distance", 6);
        this.hauntDisappearDistance = config.getInt("herobrine.haunt.disappear-distance", 4);

        this.trackerEnabled = config.getBoolean("herobrine.tracker.enabled", true);
        this.trackerCheckIntervalMs = config.getLong("herobrine.tracker.check-interval-ms", 180000);
        this.trackerPlayerCooldownMs = config.getLong("herobrine.tracker.player-cooldown-ms", 900000);
        this.trackerTriggerChance = config.getDouble("herobrine.tracker.trigger-chance", 0.25);
        this.trackerLifetimeMs = config.getLong("herobrine.tracker.lifetime-ms", 30000);
        this.trackerTargetDistance = config.getDouble("herobrine.tracker.target-distance", 10.0);

        this.signEnabled = config.getBoolean("herobrine.sign.enabled", true);
        this.signCheckIntervalMs = config.getLong("herobrine.sign.check-interval-ms", 180000);
        this.signPlayerCooldownMs = config.getLong("herobrine.sign.player-cooldown-ms", 900000);
        this.signTriggerChance = config.getDouble("herobrine.sign.trigger-chance", 0.2);
        this.signLifetimeMs = config.getLong("herobrine.sign.lifetime-ms", 300000);

        this.bedsideEnabled = config.getBoolean("herobrine.bedside.enabled", true);
        this.bedsideDelayMs = config.getLong("herobrine.bedside.delay-ms", 2500);
        this.bedsideLifetimeMs = config.getLong("herobrine.bedside.lifetime-ms", 20000);
        this.bedsideTriggerChance = config.getDouble("herobrine.bedside.trigger-chance", 0.5);

        // ★ 创建冷却管理器（过期时间 30 分钟，覆盖最长的 15 分钟冷却）
        this.cooldowns = new CooldownManager(plugin, 600_000L, 0, 30 * 60_000L);

        fetchSkinAsync();

        if (hauntEnabled) startHauntTask();
        if (trackerEnabled) startTrackerTask();
        if (signEnabled) startSignTask();
    }

    private void fetchSkinAsync() {
        plugin.getLogger().info("正在从 Mojang API 获取皮肤纹理...");
        skinFetcher.fetchSkin(skinOwnerUuid).thenAccept(opt -> {
            if (opt.isPresent()) plugin.getLogger().info("✅ Herobrine 皮肤已就绪");
            else plugin.getLogger().warning("⚠️ 获取皮肤失败，将使用默认皮肤");
        });
    }

    public void shutdown() {
        if (hauntTask != null) hauntTask.cancel();
        if (trackerTask != null) trackerTask.cancel();
        if (signTask != null) signTask.cancel();

        for (Mannequin m : activeHaunts.values()) if (m != null && m.isValid()) m.remove();
        activeHaunts.clear();
        for (TrackerSession session : activeTrackers.values()) session.cancel();
        activeTrackers.clear();
        for (Mannequin m : activeBedsides.values()) if (m != null && m.isValid()) m.remove();
        activeBedsides.clear();
        if (cooldowns != null) cooldowns.shutdown();   // ★
    }

    // ==================== 背后恐吓 ====================
    private void startHauntTask() {
        hauntTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            try { tryHaunt(); }
            catch (Exception e) { plugin.getLogger().warning("[Herobrine/背后] 出错: " + e.getMessage()); }
        }, hauntCheckIntervalMs / 50, hauntCheckIntervalMs / 50);
    }

    private void tryHaunt() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode().name().equals("SPECTATOR")) continue;
            if (activeHaunts.containsKey(p.getUniqueId())) continue;
            if (activeTrackers.containsKey(p.getUniqueId())) continue;
            if (activeBedsides.containsKey(p.getUniqueId())) continue;

            // ★ 使用 CooldownManager 检查冷却
            long remain = cooldowns.getRemaining(p.getUniqueId(), "haunt", hauntPlayerCooldownMs);
            if (remain > 0) continue;

            if (!isAlone(p)) continue;
            if (random.nextDouble() > hauntTriggerChance) continue;

            spawnHauntBehind(p);
            return;
        }
    }

    private void spawnHauntBehind(Player player) {
        Location playerLoc = player.getLocation();
        Vector direction = playerLoc.getDirection().setY(0).normalize();
        Location behind = playerLoc.clone().subtract(direction.clone().multiply(hauntSpawnDistance));

        Location spawnLoc = findSafeGround(behind);
        if (spawnLoc == null) return;

        // ★ 使用工具类朝向
        TauntUtils.faceTo(null, null); // 空调用避免未使用警告（真实调用见下方）
        Vector toPlayer = playerLoc.toVector().subtract(spawnLoc.toVector()).setY(0).normalize();
        float yaw = (float) Math.toDegrees(Math.atan2(-toPlayer.getX(), toPlayer.getZ()));
        spawnLoc.setYaw(yaw);

        Mannequin hb = player.getWorld().spawn(spawnLoc, Mannequin.class);
        setupMannequin(hb);

        activeHaunts.put(player.getUniqueId(), hb);

        // ★ 记录冷却
        cooldowns.isReady(player.getUniqueId(), "haunt", hauntPlayerCooldownMs);

        player.playSound(player.getLocation(), Sound.AMBIENT_CAVE, 1.0f, 0.5f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                String msg = SEEN_MESSAGES.get(random.nextInt(SEEN_MESSAGES.size()))
                        .replace("%player%", player.getName());
                player.sendMessage(Component.text(msg, NamedTextColor.DARK_PURPLE));
            }
        }, 20L);

        startHauntWatch(player, hb);
    }

    private void startHauntWatch(Player player, Mannequin hb) {
        final int[] elapsed = {0};
        final BukkitTask[] holder = new BukkitTask[1];

        holder[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            elapsed[0] += 10;
            boolean shouldDismiss = false;

            if (!player.isOnline()) shouldDismiss = true;
            else if (!hb.isValid() || hb.isDead()) shouldDismiss = true;
            else if (elapsed[0] >= hauntLifetimeMs / 50) shouldDismiss = true;
            else if (isLookingAt(player, hb.getLocation())) shouldDismiss = true;
            else if (player.getWorld().equals(hb.getWorld())
                    && player.getLocation().distanceSquared(hb.getLocation())
                    < hauntDisappearDistance * hauntDisappearDistance) shouldDismiss = true;

            if (shouldDismiss) {
                holder[0].cancel();
                dismissHaunt(player, hb);
            }
        }, 10L, 10L);
    }

    private void dismissHaunt(Player player, Mannequin hb) {
        if (hb != null && hb.isValid()) hb.remove();
        activeHaunts.remove(player.getUniqueId());

        if (player != null && player.isOnline()) {
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 0.5f);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    String msg = GONE_MESSAGES.get(random.nextInt(GONE_MESSAGES.size()));
                    player.sendMessage(Component.text(msg, NamedTextColor.GRAY));
                }
            }, 20L);

            // ★ 成就挂钩（用工具类）
            TauntUtils.unlock(plugin, player, AchievementManager.Ach.SAW_HEROBRINE);
        }
    }

    // ==================== 追踪型 ====================
    private void startTrackerTask() {
        trackerTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            try { tryTrack(); }
            catch (Exception e) { plugin.getLogger().warning("[Herobrine/追踪] 出错: " + e.getMessage()); }
        }, trackerCheckIntervalMs / 50, trackerCheckIntervalMs / 50);
    }

    private void tryTrack() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode().name().equals("SPECTATOR")) continue;
            if (activeTrackers.containsKey(p.getUniqueId())) continue;
            if (activeHaunts.containsKey(p.getUniqueId())) continue;
            if (activeBedsides.containsKey(p.getUniqueId())) continue;

            // ★ 使用 CooldownManager
            long remain = cooldowns.getRemaining(p.getUniqueId(), "tracker", trackerPlayerCooldownMs);
            if (remain > 0) continue;

            if (p.getLocation().getBlock().getLightLevel() < 4) continue;
            if (random.nextDouble() > trackerTriggerChance) continue;

            spawnTracker(p);
            return;
        }
    }

    private void spawnTracker(Player player) {
        Location startLoc = getTrackerPosition(player, 15.0);
        if (startLoc == null) return;

        Mannequin hb = player.getWorld().spawn(startLoc, Mannequin.class);
        setupMannequin(hb);

        TrackerSession session = new TrackerSession(hb, player.getUniqueId());
        activeTrackers.put(player.getUniqueId(), session);

        // ★ 记录冷却
        cooldowns.isReady(player.getUniqueId(), "tracker", trackerPlayerCooldownMs);

        player.playSound(player.getLocation(), Sound.AMBIENT_CAVE, 1.0f, 0.5f);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                String msg = TRACKER_START_MESSAGES.get(random.nextInt(TRACKER_START_MESSAGES.size()));
                player.sendMessage(Component.text(msg, NamedTextColor.DARK_PURPLE));
            }
        }, 20L);

        session.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) {
                session.cancel();
                activeTrackers.remove(player.getUniqueId());
                return;
            }
            if (System.currentTimeMillis() - session.startTime > trackerLifetimeMs) {
                endTracker(player, session);
                return;
            }
            if (!hb.isValid() || hb.isDead()) {
                session.cancel();
                activeTrackers.remove(player.getUniqueId());
                return;
            }
            if (isLookingAt(player, hb.getLocation())) {
                endTracker(player, session);
                return;
            }

            double dist = player.getLocation().distance(hb.getLocation());
            if (dist > TRACKER_MAX_DISTANCE || dist < TRACKER_MIN_DISTANCE) {
                session.lastAngle += random.nextInt(60) - 30;
                Location target = getTrackerPosition(player, trackerTargetDistance);
                if (target != null) {
                    hb.teleport(target);
                    // ★ 使用工具类朝向
                    TauntUtils.faceTo(hb, player.getLocation());
                }
            }
        }, 0L, TRACKER_UPDATE_INTERVAL_MS / 50);
    }

    private void endTracker(Player player, TrackerSession session) {
        session.cancel();
        activeTrackers.remove(player.getUniqueId());

        if (player.isOnline()) {
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 0.5f);
            String msg = TRACKER_END_MESSAGES.get(random.nextInt(TRACKER_END_MESSAGES.size()));
            player.sendMessage(Component.text(msg, NamedTextColor.GRAY));
        }

        // ★ 成就挂钩（用工具类）
        TauntUtils.unlock(plugin, player, AchievementManager.Ach.TRACKED);
    }

    private Location getTrackerPosition(Player player, double distance) {
        Location playerLoc = player.getLocation();
        Vector direction = playerLoc.getDirection().setY(0).normalize();
        Location behind = playerLoc.clone().subtract(direction.clone().multiply(distance));
        Location safe = findSafeGround(behind);
        if (safe != null && safe.getWorld().equals(player.getWorld())
                && safe.distance(playerLoc) >= TRACKER_MIN_DISTANCE) {
            return safe;
        }

        for (int i = 0; i < 12; i++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double x = playerLoc.getX() + Math.cos(angle) * distance;
            double z = playerLoc.getZ() + Math.sin(angle) * distance;
            Location test = new Location(playerLoc.getWorld(), x, playerLoc.getY(), z);
            safe = findSafeGround(test);
            if (safe != null && safe.distance(playerLoc) >= TRACKER_MIN_DISTANCE) return safe;
        }
        return null;
    }

    // ==================== 告示牌 ====================
    private void startSignTask() {
        signTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            try { trySign(); }
            catch (Exception e) { plugin.getLogger().warning("[Herobrine/留言] 出错: " + e.getMessage()); }
        }, signCheckIntervalMs / 50, signCheckIntervalMs / 50);
    }

    private void trySign() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode().name().equals("SPECTATOR")) continue;

            // ★ 使用 CooldownManager
            long remain = cooldowns.getRemaining(p.getUniqueId(), "sign", signPlayerCooldownMs);
            if (remain > 0) continue;

            if (random.nextDouble() > signTriggerChance) continue;

            boolean placed = spawnSignNear(p);
            if (placed) {
                // ★ 记录冷却
                cooldowns.isReady(p.getUniqueId(), "sign", signPlayerCooldownMs);
                return;
            }
        }
    }

    private boolean spawnSignNear(Player player) {
        World world = player.getWorld();
        Location playerLoc = player.getLocation();

        for (int attempt = 0; attempt < 20; attempt++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = SIGN_MIN_DISTANCE
                    + random.nextDouble() * (SIGN_MAX_DISTANCE - SIGN_MIN_DISTANCE);
            double x = playerLoc.getX() + Math.cos(angle) * distance;
            double z = playerLoc.getZ() + Math.sin(angle) * distance;

            Location found = findSignSpot(world, x, playerLoc.getBlockY(), z);
            if (found == null) continue;

            Block block = found.getBlock();
            block.setType(Material.OAK_SIGN);

            BlockState state = block.getState();
            if (state instanceof Sign sign) {
                SignSide front = sign.getSide(Side.FRONT);
                String text = SIGN_MESSAGES.get(random.nextInt(SIGN_MESSAGES.size()));
                front.line(0, Component.text(text, NamedTextColor.DARK_RED));
                sign.update(true);
            }

            player.playSound(found, Sound.BLOCK_WOOD_PLACE, 0.8f, 0.5f);

            final Location signLoc = found.clone();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Block b = signLoc.getBlock();
                if (b.getType() == Material.OAK_SIGN || b.getType() == Material.OAK_WALL_SIGN) {
                    b.setType(Material.AIR);
                }
            }, signLifetimeMs / 50);

            // ★ 成就挂钩
            TauntUtils.unlock(plugin, player, AchievementManager.Ach.SAW_SIGN);
            return true;
        }
        return false;
    }

    private Location findSignSpot(World world, double x, int startY, double z) {
        for (int dy = -3; dy <= 3; dy++) {
            int y = startY + dy;
            if (y < world.getMinHeight() + 1 || y > world.getMaxHeight() - 2) continue;

            Location below = new Location(world, x, y - 1, z);
            Location at = new Location(world, x, y, z);
            Location above = new Location(world, x, y + 1, z);

            if (!below.getBlock().getType().isSolid()) continue;
            if (!at.getBlock().getType().isAir()) continue;
            if (!above.getBlock().getType().isAir()) continue;
            if (below.getBlock().getType() == Material.WATER) continue;

            return at;
        }
        return null;
    }

    // ==================== 床边 ====================
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerBedEnter(PlayerBedEnterEvent event) {
        if (!bedsideEnabled) return;
        if (event.getBedEnterResult() != PlayerBedEnterEvent.BedEnterResult.OK) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (activeBedsides.containsKey(uuid)) return;
        if (random.nextDouble() > bedsideTriggerChance) return;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            if (!player.isSleeping()) return;
            spawnBedside(player);
        }, bedsideDelayMs / 50);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerBedLeave(PlayerBedLeaveEvent event) {
        Player player = event.getPlayer();
        Mannequin hb = activeBedsides.remove(player.getUniqueId());
        if (hb != null && hb.isValid()) hb.remove();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Mannequin hb = activeBedsides.remove(event.getPlayer().getUniqueId());
        if (hb != null && hb.isValid()) hb.remove();
    }

    private void spawnBedside(Player player) {
        Location playerLoc = player.getLocation();
        World world = playerLoc.getWorld();
        if (world == null) return;

        int[][] offsets = {
                {2, 0, 0}, {-2, 0, 0}, {0, 0, 2}, {0, 0, -2},
                {2, 0, 2}, {2, 0, -2}, {-2, 0, 2}, {-2, 0, -2},
                {3, 0, 0}, {-3, 0, 0}, {0, 0, 3}, {0, 0, -3}
        };

        List<int[]> shuffled = new ArrayList<>(Arrays.asList(offsets));
        Collections.shuffle(shuffled, random);

        Location spawnLoc = null;
        for (int[] off : shuffled) {
            Location test = new Location(world,
                    playerLoc.getBlockX() + off[0],
                    playerLoc.getBlockY(),
                    playerLoc.getBlockZ() + off[2]);
            Location safe = findSafeGround(test);
            if (safe != null) {
                spawnLoc = safe;
                break;
            }
        }
        if (spawnLoc == null) return;

        Vector toPlayer = playerLoc.toVector().subtract(spawnLoc.toVector()).setY(0).normalize();
        float yaw = (float) Math.toDegrees(Math.atan2(-toPlayer.getX(), toPlayer.getZ()));
        spawnLoc.setYaw(yaw);
        spawnLoc.setPitch(0);

        Mannequin hb = world.spawn(spawnLoc, Mannequin.class);
        setupMannequin(hb);
        activeBedsides.put(player.getUniqueId(), hb);

        player.playSound(player.getLocation(), Sound.AMBIENT_CAVE, 1.0f, 0.3f);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && player.isSleeping()) {
                String msg = BEDSIDE_MESSAGES.get(random.nextInt(BEDSIDE_MESSAGES.size()));
                player.sendMessage(Component.text(msg, NamedTextColor.DARK_PURPLE));
            }
        }, 20L);

        // ★ 成就挂钩
        TauntUtils.unlock(plugin, player, AchievementManager.Ach.BEDSIDE_HEROBRINE);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Mannequin active = activeBedsides.get(player.getUniqueId());
            if (active == hb) {
                activeBedsides.remove(player.getUniqueId());
                if (hb.isValid()) hb.remove();
            }
        }, bedsideLifetimeMs / 50);
    }

    // ==================== Mannequin ====================
    private void setupMannequin(Mannequin hb) {
        ProfileProperty skin = skinFetcher.getCachedSkin();
        if (skin != null) {
            try {
                ResolvableProfile profile = ResolvableProfile.resolvableProfile()
                        .uuid(UUID.randomUUID())
                        .name("Herobrine")
                        .addProperty(skin)
                        .build();
                hb.setProfile(profile);
            } catch (Throwable ignored) {}
        }
        try { hb.setDescription(null); } catch (Throwable ignored) {}
        hb.setImmovable(true);
        hb.setPose(Pose.STANDING);
        hb.setSilent(true);
        hb.setInvulnerable(true);
        hb.setCollidable(false);
        hb.setPersistent(false);
    }

    private boolean isAlone(Player player) {
        return player.getWorld().getNearbyEntities(
                player.getLocation(), 30, 30, 30,
                e -> e instanceof Player && !e.equals(player)
        ).isEmpty();
    }

    private boolean isLookingAt(Player player, Location target) {
        if (!player.getWorld().equals(target.getWorld())) return false;
        Location eye = player.getEyeLocation();
        Vector toTarget = target.clone().add(0, 1, 0).toVector()
                .subtract(eye.toVector()).normalize();
        double dot = eye.getDirection().dot(toTarget);
        return dot > LOOK_THRESHOLD;
    }

    private Location findSafeGround(Location origin) {
        World world = origin.getWorld();
        if (world == null) return null;
        int x = origin.getBlockX();
        int z = origin.getBlockZ();
        int startY = origin.getBlockY();

        for (int dy = 0; dy <= 10; dy++) {
            Location down = tryY(world, x, startY - dy, z);
            if (down != null) return down;
            if (dy > 0) {
                Location up = tryY(world, x, startY + dy, z);
                if (up != null) return up;
            }
        }
        return null;
    }

    private Location tryY(World world, int x, int y, int z) {
        if (y < world.getMinHeight() + 1 || y > world.getMaxHeight() - 2) return null;
        Location below = new Location(world, x, y - 1, z);
        Location feet = new Location(world, x, y, z);
        Location head = new Location(world, x, y + 1, z);
        if (!below.getBlock().getType().isSolid()) return null;
        if (!feet.getBlock().getType().isAir()) return null;
        if (!head.getBlock().getType().isAir()) return null;
        return new Location(world, x + 0.5, y, z + 0.5);
    }
}