package com.example.tauntplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class TechnobladeManager implements Listener {

    private final JavaPlugin plugin;
    private final MessageManager messages;
    private final Random random = new Random();
    private final NamespacedKey TECHNO_KEY;

    private volatile UUID technoUuid = null;

    private final CooldownManager cooldowns;
    private static final long TALK_COOLDOWN_MS = 3_000L;

    private double followSearchRadius;
    private double followTeleportDistance;
    private double followStopDistance;
    private double followSpeed;
    private long followIntervalTicks;

    private int punishPhase1End;
    private int punishPhase2End;
    private int punishPhase3End;
    private double punishLiftHeight;

    private BukkitTask followTask;

    private final Map<UUID, BukkitTask> punishingPlayers = new ConcurrentHashMap<>();
    private final Map<UUID, GameMode> pendingRespawnModes = new ConcurrentHashMap<>();
    private final Set<UUID> followedPlayers = ConcurrentHashMap.newKeySet();

    // ==================== 故事池（业务数据，保留在代码中）====================
    private static final List<String> TECHNO_STORIES = List.of(
            "你知道吗？我曾经在 Hypixel 起床战争里创下过 1818 连胜的纪录。倒数第二局，队友全倒了，我一个人单挑对面一整队，赢了。最后一局，我直接搭高台，邀请敌人来拆床，然后我们四个人一起跳下虚空。那才是真正的谢幕。",
            "我种过 21 亿个土豆。不是为了吃，是为了赢。那场「伟大的土豆战争」打了几个月，我和 Squid Kid 约定谁先种到 5 亿颗谁就赢。我边看《辉夜大小姐》边种，最后我赢了。",
            "有人问我为什么戴王冠。因为皇冠蒙蔽了我的双眼，但没蒙蔽我的剑。",
            "我在 MrBeast 安排的 PVP 对决中，以 6:4 击败了 Dream。他很强，但那天我更强。",
            "一次空岛战争，我出生时没拿到武器，只有一把镐子。我用镐子突破了 8 个人的包围，拿下八连杀。我边打边喊：「还有谁要来？」",
            "MCC 锦标赛里，我几乎从未跌出前十。有一次建筑比赛，我提前一晚研究了所有可能的建筑和材料，比赛时让队友全关直播，按我的清单分工。前一半时间我们得 0 分，全场都在质疑。然后我们的分数开始飙升，最终远超第二名几百分。官方为此改了规则——建筑不再可以提前预知。",
            "锦标赛的空岛中心原本没有虚空，很适合 PVP。我席卷了全场，后来官方改了地图。Bingo 游戏里我开局就去杀敌人，把合成竞速玩成了 PVP，官方又改了规则。他们为我改了很多规则，但我从没抱怨过。",
            "Technoblade 这个名字直译是「技术之刃」，不是「血神」。血神是后来在 Dream SMP 里大家给我起的。我在那里扮演了一个角色，说了一句「Technoblade never dies」。现在这句话还在流传。",
            "我生前最后一段视频是我父亲替我念的遗言。我说：「如果我还有一百次生命，我想我每次都会选择再次成为 Technoblade，因为那是我生命中最快乐的岁月。我希望你们喜欢我的内容，我让你们中的一些人开心。我希望你们都能长寿、美满和幸福地生活下去。」",
            "如果你正在看这个视频，我已经死了。但没关系，Technoblade never dies。"
    );

    private static final List<String> POTATO_THANKS = List.of(
            "土豆……好久没吃到这么好的土豆了。谢谢你，朋友。",
            "你知道吗，我见过 21 亿颗土豆，但你这颗，我记住了。",
            "这颗土豆，比我当年种的那些还香。Technoblade 永远不会忘记你。",
            "谢谢你，我正需要这个。当年种土豆是为了赢，现在吃土豆是为了……开心。",
            "一颗土豆，一份心意。你比 Squid Kid 大方多了。",
            "嗯……不错。要是再加点附魔金苹果就更好了。",
            "我收下了。作为回报，我把「Technoblade never dies」送给你。",
            "拿着土豆的人，就是我的朋友。跟着我，一起去冒险吧。"
    );

    public TechnobladeManager(JavaPlugin plugin, MessageManager messages, ConfigManager config) {
        this.plugin = plugin;
        this.messages = messages;
        this.TECHNO_KEY = new NamespacedKey(plugin, "technoblade_entity");

        this.cooldowns = new CooldownManager(plugin, TALK_COOLDOWN_MS, 0);

        this.followSearchRadius = config.getDouble("technoblade.follow-search-radius", 50.0);
        this.followTeleportDistance = config.getDouble("technoblade.follow-teleport-distance", 25.0);
        this.followStopDistance = config.getDouble("technoblade.follow-stop-distance", 4.0);
        this.followSpeed = config.getDouble("technoblade.follow-speed", 0.28);
        this.followIntervalTicks = config.getLong("technoblade.follow-interval-ticks", 10);

        this.punishPhase1End = config.getInt("technoblade.punish-phase1-end", 20);
        this.punishPhase2End = config.getInt("technoblade.punish-phase2-end", 50);
        this.punishPhase3End = config.getInt("technoblade.punish-phase3-end", 110);
        this.punishLiftHeight = config.getDouble("technoblade.punish-lift-height", 3.0);

        loadExistingTechno();
        startFollowTask();
    }

    public void shutdown() {
        if (followTask != null) followTask.cancel();
        for (BukkitTask task : punishingPlayers.values()) {
            if (task != null) task.cancel();
        }
        punishingPlayers.clear();
        pendingRespawnModes.clear();
        followedPlayers.clear();
        if (cooldowns != null) cooldowns.shutdown();
    }

    // ==================== 加载已有实体 ====================
    private void loadExistingTechno() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Pig pig
                        && pig.getPersistentDataContainer().has(TECHNO_KEY, PersistentDataType.BYTE)) {
                    technoUuid = pig.getUniqueId();
                    plugin.getLogger().info("[猪神] 已加载现有 Technoblade 实体，UUID: " + technoUuid);
                    return;
                }
            }
        }
    }

    // ==================== 生成 ====================
    public Pig spawnTechnoblade(Player player, Location location) {
        if (technoUuid != null) {
            Pig existing = findTechno();
            if (existing != null && existing.isValid()) {
                if (player != null) messages.send(player, "technoblade.already-exists");
                return null;
            } else {
                technoUuid = null;
            }
        }

        World world = location.getWorld();
        if (world == null) return null;

        Pig techno = world.spawn(location, Pig.class, pig -> {
            pig.customName(Component.text("Technoblade", NamedTextColor.GOLD));
            pig.setCustomNameVisible(true);
            pig.getPersistentDataContainer().set(TECHNO_KEY, PersistentDataType.BYTE, (byte) 1);
            pig.setPersistent(true);
            pig.setRemoveWhenFarAway(false);
            pig.setCollidable(false);
            pig.setSilent(false);
            pig.setAI(false);
            pig.setSaddle(false);
            pig.addScoreboardTag("technoblade");
        });

        technoUuid = techno.getUniqueId();
        broadcastSpawn(techno);
        return techno;
    }

    private Pig findTechno() {
        if (technoUuid == null) return null;
        Entity entity = Bukkit.getEntity(technoUuid);
        if (entity instanceof Pig pig && pig.isValid()) return pig;
        return null;
    }

    private void broadcastSpawn(Pig techno) {
        Location loc = techno.getLocation();
        String coords = String.format("(%d, %d, %d)",
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        String worldName = loc.getWorld() != null ? loc.getWorld().getName() : "未知";

        messages.broadcast("technoblade.spawn-broadcast", Map.of(
                "{coords}", coords,
                "{world}", worldName
        ));

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.5f);
        }
    }

    // ==================== 跟随 ====================
    private void startFollowTask() {
        followTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Pig techno = findTechno();
            if (techno == null || !techno.isValid()) return;

            Player target = findNearestPotatoHolder(techno);
            if (target == null) return;

            Location technoLoc = techno.getLocation();
            Location targetLoc = target.getLocation();

            if (!technoLoc.getWorld().equals(targetLoc.getWorld())) {
                techno.teleport(targetLoc);
                return;
            }

            double dist = technoLoc.distance(targetLoc);

            TauntUtils.faceTo(techno, targetLoc);

            if (dist < followTeleportDistance && followedPlayers.add(target.getUniqueId())) {
                TauntUtils.unlock(plugin, target, AchievementManager.Ach.TECHNO_FOLLOWER);
            }

            if (dist > followTeleportDistance) {
                Vector dir = targetLoc.getDirection().setY(0);
                if (dir.lengthSquared() > 0.01) dir.normalize();
                Location behind = targetLoc.clone().subtract(dir.multiply(2.5));
                techno.teleport(behind);
                return;
            }

            if (dist > followStopDistance) {
                Vector direction = targetLoc.toVector().subtract(technoLoc.toVector());
                direction.setY(0);
                if (direction.lengthSquared() > 0.01) {
                    direction.normalize().multiply(followSpeed);
                    techno.setVelocity(new Vector(
                            direction.getX(),
                            techno.getVelocity().getY(),
                            direction.getZ()
                    ));
                }
            } else {
                techno.setVelocity(new Vector(0, techno.getVelocity().getY(), 0));
            }
        }, 0L, followIntervalTicks);
    }

    private Player findNearestPotatoHolder(Pig techno) {
        Player nearest = null;
        double nearestDistSq = followSearchRadius * followSearchRadius;

        for (Player p : techno.getWorld().getPlayers()) {
            if (p.getGameMode().name().equals("SPECTATOR")) continue;
            if (!TauntUtils.isHolding(p, Material.POTATO)) continue;

            double distSq = p.getLocation().distanceSquared(techno.getLocation());
            if (distSq < nearestDistSq) {
                nearestDistSq = distSq;
                nearest = p;
            }
        }
        return nearest;
    }

    // ==================== 惩罚 ====================
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onTechnoDamaged(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Pig pig)) return;
        if (!pig.getPersistentDataContainer().has(TECHNO_KEY, PersistentDataType.BYTE)) return;

        event.setCancelled(true);

        if (event instanceof EntityDamageByEntityEvent byEntity) {
            Entity damager = byEntity.getDamager();
            if (damager instanceof Player attacker) {
                if (attacker.getGameMode() == GameMode.SPECTATOR) return;
                startPunishment(attacker, pig);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        BukkitTask task = punishingPlayers.remove(event.getPlayer().getUniqueId());
        if (task != null) task.cancel();
        pendingRespawnModes.remove(event.getPlayer().getUniqueId());
        event.getPlayer().setInvulnerable(false);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        GameMode original = pendingRespawnModes.remove(uuid);
        if (original == null) return;

        final Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                player.setGameMode(original);
                messages.send(player, "technoblade.mode-restored",
                        Map.of("{mode}", original.name()));
            }
        }, 1L);
    }

    private void startPunishment(Player attacker, Pig techno) {
        UUID uuid = attacker.getUniqueId();
        if (punishingPlayers.containsKey(uuid)) return;

        GameMode originalMode = attacker.getGameMode();
        if (originalMode != GameMode.SURVIVAL) {
            pendingRespawnModes.put(uuid, originalMode);
            attacker.setGameMode(GameMode.SURVIVAL);
            messages.send(attacker, "technoblade.mode-forced");
        }

        messages.broadcast("technoblade.punish-start", Map.of(
                "{player}", attacker.getName()
        ));

        final Location startLoc = attacker.getLocation().clone();
        final double startHealth = attacker.getHealth();

        attacker.setInvulnerable(true);

        final int[] tick = {0};
        final BukkitTask[] taskHolder = new BukkitTask[1];

        taskHolder[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!attacker.isOnline()) {
                taskHolder[0].cancel();
                punishingPlayers.remove(uuid);
                pendingRespawnModes.remove(uuid);
                return;
            }

            Pig pig = findTechno();
            if (pig == null || !pig.isValid()) {
                attacker.setInvulnerable(false);
                taskHolder[0].cancel();
                punishingPlayers.remove(uuid);
                GameMode orig = pendingRespawnModes.remove(uuid);
                if (orig != null) attacker.setGameMode(orig);
                return;
            }

            int t = tick[0]++;

            if (t == 0) {
                TauntUtils.faceTo(pig, attacker.getLocation());
                pig.getWorld().playSound(pig.getLocation(), Sound.ENTITY_PIG_AMBIENT, 1.0f, 0.5f);
                attacker.playSound(attacker.getLocation(), Sound.ENTITY_ENDERMAN_STARE, 1.0f, 0.5f);
                attacker.playSound(attacker.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.0f, 0.5f);
            }

            if (t <= punishPhase1End) {
                lockViewToTechno(attacker, pig, startLoc);
            }

            if (t > punishPhase1End && t <= punishPhase2End) {
                double progress = (t - punishPhase1End) / (double) (punishPhase2End - punishPhase1End);
                Location loc = startLoc.clone();
                loc.add(0, punishLiftHeight * progress, 0);
                lockViewToTechno(attacker, pig, loc);

                if (t % 3 == 0) {
                    Location al = attacker.getLocation();
                    TauntUtils.spawnParticleSafe(attacker.getWorld(), Particle.CLOUD,
                            al.getX(), al.getY() + 0.2, al.getZ(),
                            5, 0.3, 0.1, 0.3, 0.02);
                }
            }

            if (t > punishPhase2End && t <= punishPhase3End) {
                double progress = (t - punishPhase2End) / (double) (punishPhase3End - punishPhase2End);
                Location loc = startLoc.clone();
                loc.add(0, punishLiftHeight, 0);
                lockViewToTechno(attacker, pig, loc);

                double newHealth = startHealth * (1.0 - progress);
                if (newHealth < 0.5) newHealth = 0.5;
                try { attacker.setHealth(newHealth); } catch (Throwable ignored) {}

                if (t % 5 == 0) {
                    try { attacker.playHurtAnimation(attacker.getLocation().getYaw()); } catch (Throwable ignored) {}
                    Location al = attacker.getLocation();
                    TauntUtils.spawnParticleSafe(attacker.getWorld(), Particle.DAMAGE_INDICATOR,
                            al.getX(), al.getY() + 1.0, al.getZ(),
                            5, 0.5, 0.5, 0.5, 0.1);
                    attacker.playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.8f, 1.0f);
                }
            }

            if (t >= punishPhase3End) {
                tearApart(attacker, pig);
                taskHolder[0].cancel();
                punishingPlayers.remove(uuid);
            }
        }, 0L, 1L);

        punishingPlayers.put(uuid, taskHolder[0]);
    }

    private void lockViewToTechno(Player player, Pig techno, Location base) {
        Location loc = base.clone();
        Location eye = player.getEyeLocation();
        Vector fromEye = loc.clone().add(0, eye.getY() - player.getLocation().getY(), 0).toVector();
        Vector toTechno = techno.getEyeLocation().toVector().subtract(fromEye);
        if (toTechno.lengthSquared() > 0.01) {
            loc.setDirection(toTechno);
        }
        player.teleport(loc);
    }

    private void tearApart(Player attacker, Pig techno) {
        Location loc = attacker.getLocation();
        World world = attacker.getWorld();

        TauntUtils.spawnParticleSafe(world, Particle.SOUL_FIRE_FLAME,
                loc.getX(), loc.getY(), loc.getZ(), 60, 1.0, 1.5, 1.0, 0.1);
        TauntUtils.spawnParticleSafe(world, Particle.DUST,
                loc.getX(), loc.getY(), loc.getZ(), 50, 0.8, 1.5, 0.8, 0.1,
                new Particle.DustOptions(org.bukkit.Color.RED, 2.0f));
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

        techno.getWorld().playSound(techno.getLocation(), Sound.ENTITY_PIG_AMBIENT, 1.0f, 0.6f);

        TauntUtils.killPlayer(plugin, attacker);

        messages.broadcast("technoblade.punish-kill", Map.of(
                "{player}", attacker.getName()
        ));

        TauntUtils.unlock(plugin, attacker, AchievementManager.Ach.KILLED_BY_TECHNO);
    }

    // ==================== 交互 ====================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Entity clicked = event.getRightClicked();
        if (!(clicked instanceof Pig pig)) return;
        if (!pig.getPersistentDataContainer().has(TECHNO_KEY, PersistentDataType.BYTE)) return;

        Player player = event.getPlayer();
        event.setCancelled(true);

        if (!cooldowns.isReady(player.getUniqueId(), "talk")) {
            messages.send(player, "technoblade.thinking");
            return;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();

        if (hand.getType() == Material.POTATO) {
            handlePotatoFeed(player, pig, hand);
        } else {
            handleStory(player, pig);
        }
    }

    private void handlePotatoFeed(Player player, Pig pig, ItemStack hand) {
        hand.setAmount(hand.getAmount() - 1);

        String thanks = POTATO_THANKS.get(ThreadLocalRandom.current().nextInt(POTATO_THANKS.size()));

        messages.send(player, "technoblade.potato-prefix");
        messages.send(player, "technoblade.potato-thanks", Map.of("{text}", thanks));

        player.playSound(player.getLocation(), Sound.ENTITY_PIG_AMBIENT, 1.0f, 1.2f);

        Location loc = pig.getLocation().add(0, 1.5, 0);
        TauntUtils.spawnParticleSafe(pig.getWorld(), Particle.ITEM,
                loc.getX(), loc.getY(), loc.getZ(), 15, 0.3, 0.3, 0.3, 0.1,
                new ItemStack(Material.POTATO));
        TauntUtils.spawnParticleSafe(pig.getWorld(), Particle.HAPPY_VILLAGER,
                loc.getX(), loc.getY(), loc.getZ(), 10, 0.5, 0.5, 0.5, 0.0);

        pig.setVelocity(pig.getVelocity().setY(0.3));

        TauntUtils.unlock(plugin, player, AchievementManager.Ach.MEET_TECHNO);
        TauntUtils.increment(plugin, player, "techno_fed", 1);
        TauntUtils.unlock(plugin, player, AchievementManager.Ach.FEED_TECHNO);
    }

    private void handleStory(Player player, Pig pig) {
        String story = TECHNO_STORIES.get(ThreadLocalRandom.current().nextInt(TECHNO_STORIES.size()));

        messages.send(player, "technoblade.story-header");
        messages.send(player, "technoblade.story-prefix", Map.of("{text}", story));
        messages.send(player, "technoblade.story-footer");

        player.playSound(player.getLocation(), Sound.ENTITY_PIG_AMBIENT, 0.8f, 1.0f);
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_TRADE, 0.5f, 1.5f);

        TauntUtils.faceTo(pig, player.getLocation());

        TauntUtils.unlock(plugin, player, AchievementManager.Ach.MEET_TECHNO);
    }

    // ==================== 查询 ====================
    public boolean isTechnoAlive() { return findTechno() != null; }
    public UUID getTechnoUuid() { return technoUuid; }
    public Pig getTechno() { return findTechno(); }

    public void removeTechnoblade() {
        Pig pig = findTechno();
        if (pig != null) pig.remove();
        technoUuid = null;
    }

    public Pig summonFor(Player player, Location location) {
        return spawnTechnoblade(player, location);
    }
}