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

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Technoblade 猪神实体：
 * - 全局唯一
 * - 右键对话讲述猪神故事
 * - 喂土豆触发感谢
 * - 跟随拿土豆的玩家
 * - 被玩家攻击时，凝视玩家并慢慢撕碎他（★ 服主也不豁免）
 * - 生成时全服广播坐标
 */
public class TechnobladeManager implements Listener {

    private final JavaPlugin plugin;
    private final Random random = new Random();

    private final NamespacedKey TECHNO_KEY;

    private volatile UUID technoUuid = null;

    private final Map<UUID, Long> lastTalk = new ConcurrentHashMap<>();
    private static final long TALK_COOLDOWN_MS = 3_000L;

    // ==================== 跟随参数 ====================
    private static final double FOLLOW_SEARCH_RADIUS = 50.0;
    private static final double FOLLOW_TELEPORT_DISTANCE = 25.0;
    private static final double FOLLOW_STOP_DISTANCE = 4.0;
    private static final double FOLLOW_SPEED = 0.28;
    private static final long FOLLOW_INTERVAL_TICKS = 10L;

    private BukkitTask followTask;

    private final Map<UUID, BukkitTask> punishingPlayers = new ConcurrentHashMap<>();

    /** ★ 记录被惩罚玩家的原始游戏模式，重生后恢复 */
    private final Map<UUID, GameMode> pendingRespawnModes = new ConcurrentHashMap<>();

    // ==================== 惩罚流程参数（tick 数）====================
    private static final int PHASE1_END = 20;
    private static final int PHASE2_END = 50;
    private static final int PHASE3_END = 110;
    private static final double LIFT_HEIGHT = 3.0;

    // ==================== 猪神故事池 ====================
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

    public TechnobladeManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.TECHNO_KEY = new NamespacedKey(plugin, "technoblade_entity");
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
                if (player != null) {
                    player.sendMessage(Component.text("猪神已经存在于世界上，无需重复生成。",
                            NamedTextColor.YELLOW));
                }
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
        broadcastSpawn(techno, player);
        return techno;
    }

    private Pig findTechno() {
        if (technoUuid == null) return null;
        Entity entity = Bukkit.getEntity(technoUuid);
        if (entity instanceof Pig pig && pig.isValid()) {
            return pig;
        }
        return null;
    }

    private void broadcastSpawn(Pig techno, Player summoner) {
        Location loc = techno.getLocation();
        String coords = String.format("(%d, %d, %d)",
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        String worldName = loc.getWorld() != null ? loc.getWorld().getName() : "未知";

        Component broadcast = Component.text("👑 ", NamedTextColor.GOLD)
                .append(Component.text("猪神 Technoblade ", NamedTextColor.GOLD))
                .append(Component.text("降临了世界！\n", NamedTextColor.YELLOW))
                .append(Component.text("📍 位置: ", NamedTextColor.GRAY))
                .append(Component.text(coords + " @ " + worldName, NamedTextColor.AQUA))
                .append(Component.text("\nTechnoblade never dies.", NamedTextColor.DARK_GRAY));

        Bukkit.getServer().broadcast(broadcast);

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.5f);
        }
    }

    // ==================== 跟随拿土豆的玩家 ====================
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

            Vector toPlayer = targetLoc.toVector().subtract(technoLoc.toVector()).setY(0);
            if (toPlayer.lengthSquared() > 0.01) {
                toPlayer.normalize();
                float yaw = (float) Math.toDegrees(Math.atan2(-toPlayer.getX(), toPlayer.getZ()));
                techno.setRotation(yaw, 0);
            }

            if (dist > FOLLOW_TELEPORT_DISTANCE) {
                Vector dir = targetLoc.getDirection().setY(0);
                if (dir.lengthSquared() > 0.01) dir.normalize();
                Location behind = targetLoc.clone().subtract(dir.multiply(2.5));
                techno.teleport(behind);
                return;
            }

            if (dist > FOLLOW_STOP_DISTANCE) {
                Vector direction = targetLoc.toVector().subtract(technoLoc.toVector());
                direction.setY(0);
                if (direction.lengthSquared() > 0.01) {
                    direction.normalize().multiply(FOLLOW_SPEED);
                    techno.setVelocity(new Vector(
                            direction.getX(),
                            techno.getVelocity().getY(),
                            direction.getZ()
                    ));
                }
            } else {
                techno.setVelocity(new Vector(0, techno.getVelocity().getY(), 0));
            }
        }, 0L, FOLLOW_INTERVAL_TICKS);
    }

    private Player findNearestPotatoHolder(Pig techno) {
        Player nearest = null;
        double nearestDistSq = FOLLOW_SEARCH_RADIUS * FOLLOW_SEARCH_RADIUS;

        for (Player p : techno.getWorld().getPlayers()) {
            if (p.getGameMode().name().equals("SPECTATOR")) continue;
            if (!isHoldingPotato(p)) continue;

            double distSq = p.getLocation().distanceSquared(techno.getLocation());
            if (distSq < nearestDistSq) {
                nearestDistSq = distSq;
                nearest = p;
            }
        }
        return nearest;
    }

    private boolean isHoldingPotato(Player player) {
        if (player.getInventory().getItemInMainHand().getType() == Material.POTATO) return true;
        if (player.getInventory().getItemInOffHand().getType() == Material.POTATO) return true;
        return false;
    }

    // ==================== 被玩家攻击时的惩罚 ====================
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onTechnoDamaged(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Pig pig)) return;
        if (!pig.getPersistentDataContainer().has(TECHNO_KEY, PersistentDataType.BYTE)) return;

        event.setCancelled(true);

        if (event instanceof EntityDamageByEntityEvent byEntity) {
            Entity damager = byEntity.getDamager();
            if (damager instanceof Player attacker) {
                // ★ 只跳过旁观模式（旁观无法正常攻击，且无实体）
                if (attacker.getGameMode() == GameMode.SPECTATOR) return;

                // ★ 服主和创造模式玩家不再豁免
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

    /**
     * ★ 玩家重生时恢复原始游戏模式。
     */
    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        GameMode original = pendingRespawnModes.remove(uuid);
        if (original == null) return;

        final Player player = event.getPlayer();
        // 延迟 1 tick，等待玩家完全重生再恢复
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                player.setGameMode(original);
                player.sendMessage(Component.text("已恢复你原本的游戏模式（"
                        + original.name() + "）。", NamedTextColor.GRAY));
            }
        }, 1L);
    }

    /**
     * 开始对攻击者进行"凝视惩罚"。
     */
    private void startPunishment(Player attacker, Pig techno) {
        UUID uuid = attacker.getUniqueId();

        if (punishingPlayers.containsKey(uuid)) return;

        // ★ 记录原始游戏模式，如果是创造/冒险，临时切到生存，让伤害和击杀生效
        GameMode originalMode = attacker.getGameMode();
        if (originalMode != GameMode.SURVIVAL) {
            pendingRespawnModes.put(uuid, originalMode);
            attacker.setGameMode(GameMode.SURVIVAL);
            attacker.sendMessage(Component.text("你触怒了猪神，游戏模式被强制切换为生存。",
                    NamedTextColor.DARK_RED));
        }

        Component broadcast = Component.text("⚔ ", NamedTextColor.DARK_RED)
                .append(Component.text(attacker.getName(), NamedTextColor.YELLOW))
                .append(Component.text(" 攻击了猪神 ", NamedTextColor.RED))
                .append(Component.text("Technoblade", NamedTextColor.GOLD))
                .append(Component.text("，猪神开始凝视他……", NamedTextColor.DARK_RED));
        Bukkit.getServer().broadcast(broadcast);

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
                // 恢复游戏模式
                GameMode orig = pendingRespawnModes.remove(uuid);
                if (orig != null) attacker.setGameMode(orig);
                return;
            }

            int t = tick[0]++;

            // ===== T+0：凝视开始 =====
            if (t == 0) {
                Vector toPlayer = attacker.getLocation().toVector()
                        .subtract(pig.getLocation().toVector()).setY(0).normalize();
                float yaw = (float) Math.toDegrees(Math.atan2(-toPlayer.getX(), toPlayer.getZ()));
                pig.setRotation(yaw, 0);

                pig.getWorld().playSound(pig.getLocation(),
                        Sound.ENTITY_PIG_AMBIENT, 1.0f, 0.5f);
                attacker.playSound(attacker.getLocation(),
                        Sound.ENTITY_ENDERMAN_STARE, 1.0f, 0.5f);
                attacker.playSound(attacker.getLocation(),
                        Sound.ENTITY_WARDEN_HEARTBEAT, 1.0f, 0.5f);
            }

            // ===== 阶段1（0~20）：凝视 + 视角锁定 =====
            if (t <= PHASE1_END) {
                lockViewToTechno(attacker, pig, startLoc);
            }

            // ===== 阶段2（20~50）：抬升 3 格 =====
            if (t > PHASE1_END && t <= PHASE2_END) {
                double progress = (t - PHASE1_END) / (double) (PHASE2_END - PHASE1_END);
                Location loc = startLoc.clone();
                loc.add(0, LIFT_HEIGHT * progress, 0);
                lockViewToTechno(attacker, pig, loc);

                if (t % 3 == 0) {
                    try {
                        attacker.getWorld().spawnParticle(Particle.CLOUD,
                                attacker.getLocation().add(0, 0.2, 0),
                                5, 0.3, 0.1, 0.3, 0.02);
                    } catch (Throwable ignored) {}
                }
            }

            // ===== 阶段3（50~110）：慢慢扣血 =====
            if (t > PHASE2_END && t <= PHASE3_END) {
                double progress = (t - PHASE2_END) / (double) (PHASE3_END - PHASE2_END);
                Location loc = startLoc.clone();
                loc.add(0, LIFT_HEIGHT, 0);
                lockViewToTechno(attacker, pig, loc);

                double newHealth = startHealth * (1.0 - progress);
                if (newHealth < 0.5) newHealth = 0.5;
                try {
                    attacker.setHealth(newHealth);
                } catch (Throwable ignored) {}

                if (t % 5 == 0) {
                    try {
                        attacker.playHurtAnimation(attacker.getLocation().getYaw());
                    } catch (Throwable ignored) {}

                    try {
                        attacker.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR,
                                attacker.getLocation().add(0, 1.0, 0),
                                5, 0.5, 0.5, 0.5, 0.1);
                    } catch (Throwable ignored) {}

                    attacker.playSound(attacker.getLocation(),
                            Sound.ENTITY_PLAYER_HURT, 0.8f, 1.0f);
                }
            }

            // ===== 阶段4（110）：撕裂 =====
            if (t >= PHASE3_END) {
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

    /**
     * 撕裂效果：多重粒子 + 音效 + 秒杀（多重保险机制）。
     */
    private void tearApart(Player attacker, Pig techno) {
        Location loc = attacker.getLocation();
        World world = attacker.getWorld();

        try {
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 60, 1.0, 1.5, 1.0, 0.1);
        } catch (Throwable ignored) {}

        try {
            world.spawnParticle(Particle.DUST, loc, 50, 0.8, 1.5, 0.8, 0.1,
                    new Particle.DustOptions(org.bukkit.Color.RED, 2.0f));
        } catch (Throwable ignored) {}

        try {
            world.spawnParticle(Particle.LARGE_SMOKE, loc, 30, 1.0, 1.5, 1.0, 0.05);
        } catch (Throwable ignored) {}

        try {
            world.spawnParticle(Particle.EXPLOSION, loc, 3, 0.5, 1.0, 0.5, 0);
        } catch (Throwable ignored) {}

        try {
            world.spawnParticle(Particle.END_ROD, loc, 25, 0.5, 1.5, 0.5, 0.1);
        } catch (Throwable ignored) {}

        world.strikeLightningEffect(loc);
        world.playSound(loc, Sound.ENTITY_WITHER_DEATH, 1.0f, 0.5f);
        world.playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f);
        world.playSound(loc, Sound.ENTITY_PLAYER_DEATH, 1.0f, 0.5f);

        techno.getWorld().playSound(techno.getLocation(),
                Sound.ENTITY_PIG_AMBIENT, 1.0f, 0.6f);

        // ★ 击杀多重保险机制
        attacker.setInvulnerable(false);
        attacker.setNoDamageTicks(0);
        attacker.setHealth(0.0);

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (attacker.isOnline() && attacker.getHealth() > 0) {
                attacker.setInvulnerable(false);
                attacker.setNoDamageTicks(0);
                attacker.setHealth(0.0);
            }
        });

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (attacker.isOnline() && attacker.getHealth() > 0) {
                attacker.setInvulnerable(false);
                attacker.setNoDamageTicks(0);
                attacker.damage(Double.MAX_VALUE);
            }
        }, 2L);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (attacker.isOnline() && attacker.getHealth() > 0) {
                attacker.setInvulnerable(false);
                attacker.setNoDamageTicks(0);
                attacker.setHealth(0.0);
                if (attacker.getHealth() > 0) {
                    attacker.damage(Double.MAX_VALUE);
                }
            }
        }, 5L);

        Bukkit.getServer().broadcast(Component.text("💀 ", NamedTextColor.DARK_RED)
                .append(Component.text(attacker.getName(), NamedTextColor.YELLOW))
                .append(Component.text(" 被猪神 ", NamedTextColor.RED))
                .append(Component.text("Technoblade", NamedTextColor.GOLD))
                .append(Component.text(" 撕碎了。", NamedTextColor.RED)));
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

        long now = System.currentTimeMillis();
        Long last = lastTalk.get(player.getUniqueId());
        if (last != null && now - last < TALK_COOLDOWN_MS) {
            player.sendMessage(Component.text("猪神正在思考……稍后再试。", NamedTextColor.GRAY));
            return;
        }
        lastTalk.put(player.getUniqueId(), now);

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

        player.sendMessage(Component.text("🥔 你喂了猪神一颗土豆……", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("👑 Technoblade: ", NamedTextColor.GOLD)
                .append(Component.text(thanks, NamedTextColor.WHITE)));

        player.playSound(player.getLocation(), Sound.ENTITY_PIG_AMBIENT, 1.0f, 1.2f);

        Location loc = pig.getLocation().add(0, 1.5, 0);
        try {
            pig.getWorld().spawnParticle(Particle.ITEM, loc, 15, 0.3, 0.3, 0.3, 0.1,
                    new ItemStack(Material.POTATO));
        } catch (Throwable ignored) {}
        try {
            pig.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc, 10, 0.5, 0.5, 0.5, 0.0);
        } catch (Throwable ignored) {}

        pig.setVelocity(pig.getVelocity().setY(0.3));
    }

    private void handleStory(Player player, Pig pig) {
        String story = TECHNO_STORIES.get(ThreadLocalRandom.current().nextInt(TECHNO_STORIES.size()));

        player.sendMessage(Component.text("═══════════════════════", NamedTextColor.GOLD));
        player.sendMessage(Component.text("👑 Technoblade: ", NamedTextColor.GOLD)
                .append(Component.text(story, NamedTextColor.WHITE)));
        player.sendMessage(Component.text("═══════════════════════", NamedTextColor.GOLD));

        player.playSound(player.getLocation(), Sound.ENTITY_PIG_AMBIENT, 0.8f, 1.0f);
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_TRADE, 0.5f, 1.5f);

        Location pigLoc = pig.getLocation();
        Vector toPlayer = player.getLocation().toVector()
                .subtract(pigLoc.toVector()).setY(0);
        if (toPlayer.lengthSquared() > 0.01) {
            toPlayer.normalize();
            float yaw = (float) Math.toDegrees(Math.atan2(-toPlayer.getX(), toPlayer.getZ()));
            pig.setRotation(yaw, 0);
        }
    }

    // ==================== 查询 ====================
    public boolean isTechnoAlive() {
        return findTechno() != null;
    }

    public UUID getTechnoUuid() {
        return technoUuid;
    }

    public Pig getTechno() {
        return findTechno();
    }

    public void removeTechnoblade() {
        Pig pig = findTechno();
        if (pig != null) {
            pig.remove();
        }
        technoUuid = null;
    }

    public Pig summonFor(Player player, Location location) {
        return spawnTechnoblade(player, location);
    }
}