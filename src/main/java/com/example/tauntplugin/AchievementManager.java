package com.example.tauntplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 成就系统：
 * - 50+ 个成就，覆盖插件所有功能
 * - 数据保存在 plugins/TauntPlugin/achievements.yml
 * - 解锁时全服广播 + 音效 + 标题
 */
public class AchievementManager {

    private final JavaPlugin plugin;
    private final File dataFile;

    /** UUID → 玩家成就数据 */
    private final Map<UUID, PlayerData> playerData = new ConcurrentHashMap<>();

    private volatile boolean dirty = false;

    // ==================== 成就定义 ====================
    public enum Ach {
        // ══════ 挖矿/肝度类 ══════
        FIRST_BREAK("first_break", "初次挖掘", "挖下你的第一块方块", Material.WOODEN_PICKAXE, "blocks_broken", 1),
        MINER_100("miner_100", "小有成就", "累计挖 100 个方块", Material.STONE_PICKAXE, "blocks_broken", 100),
        MINER_1000("miner_1000", "资深矿工", "累计挖 1000 个方块", Material.IRON_PICKAXE, "blocks_broken", 1000),
        MINER_10000("miner_10000", "挖穿地球", "累计挖 10000 个方块", Material.DIAMOND_PICKAXE, "blocks_broken", 10000),
        MINER_100000("miner_100000", "肝帝之王", "累计挖 100000 个方块", Material.NETHERITE_PICKAXE, "blocks_broken", 100000),

        PLACER_1000("placer_1000", "建筑工人", "累计放置 1000 个方块", Material.BRICKS, "blocks_placed", 1000),
        PLACER_10000("placer_10000", "建筑师", "累计放置 10000 个方块", Material.CHISELED_STONE_BRICKS, "blocks_placed", 10000),

        // ══════ 死亡类 ══════
        FIRST_DEATH("first_death", "初尝死亡", "死一次吧，没关系的", Material.SKELETON_SKULL, "deaths", 1),
        DEATH_10("death_10", "习以为常", "死 10 次", Material.ZOMBIE_HEAD, "deaths", 10),
        DEATH_100("death_100", "死士", "死 100 次", Material.WITHER_SKELETON_SKULL, "deaths", 100),
        FALL_DEATH_10("fall_death_10", "牛顿的敌人", "摔死 10 次", Material.FEATHER, "fall_deaths", 10),

        // ══════ 嘲讽类 ══════
        FIRST_TAUNT("first_taunt", "初次开口", "第一次嘲讽别人", Material.VILLAGER_SPAWN_EGG, "taunts_given", 1),
        TAUNT_10("taunt_10", "嘴强王者", "嘲讽别人 10 次", Material.BELL, "taunts_given", 10),
        TAUNT_100("taunt_100", "毒舌之王", "嘲讽别人 100 次", Material.WITHER_ROSE, "taunts_given", 100),
        CUSTOM_TAUNT("custom_taunt", "原创大师", "设置自定义嘲讽文案", Material.WRITABLE_BOOK, "custom_taunts_set", 1),
        APOLOGIZED("apologized", "惹了大人物", "调侃服主后被系统道歉", Material.GOLDEN_HELMET, "apologies_received", 1),

        // ══════ 猪神类 ══════
        MEET_TECHNO("meet_techno", "遇见了传说", "第一次遇见猪神 Technoblade", Material.GOLDEN_APPLE, "techno_met", 1),
        FEED_TECHNO("feed_techno", "献上土豆", "喂猪神一颗土豆", Material.POTATO, "techno_fed", 1),
        FEED_TECHNO_10("feed_techno_10", "土豆收藏家", "喂猪神 10 颗土豆", Material.BAKED_POTATO, "techno_fed", 10),
        KILLED_BY_TECHNO("killed_by_techno", "被神惩罚", "攻击猪神后被撕碎", Material.NETHERITE_SWORD, "killed_by_techno", 1),
        TECHNO_FOLLOWER("techno_follower", "猪神随从", "让猪神跟随你", Material.LEAD, "techno_followed", 1),

        // ══════ 魔杖类 ══════
        FIRST_SPELL("first_spell", "初学魔法", "第一次施展魔咒", Material.STICK, "spells_cast", 1),
        ALL_SPELLS("all_spells", "霍格沃茨毕业生", "使用全部魔杖咒语", Material.ENCHANTED_BOOK, "unique_spells", 23),
        EXPELLIARMUS_STEAL("expelliarmus_steal", "缴械专家", "用除你武器抢到物品", Material.DIAMOND_SWORD, "expelliarmus_steals", 1),
        AVADA_KILL("avada_kill", "不可饶恕", "用阿瓦达索命击杀玩家", Material.WITHER_SKELETON_SKULL, "avada_kills", 1),
        PATRONUS_MASTER("patronus_master", "守护神大师", "用呼神护卫一次驱散 5 只怪物", Material.HEART_OF_THE_SEA, "patronus_max", 5),
        // ★ 新增：护身咒抵挡阿瓦达索命
        SURVIVED_AVADA("survived_avada", "大难不死的孩子", "用护身咒抵挡了阿瓦达索命",
                Material.TOTEM_OF_UNDYING, "survived_avada_count", 1),
        WAND_REFORGED("wand_reforged", "魔杖匠人", "在许愿井重铸魔杖 5 次",
                Material.SMITHING_TABLE, "wands_reforged", 5),
        WAND_FULLY_BOUND("wand_fully_bound", "人杖合一", "让一根魔杖完全认你为主",
                Material.FISHING_ROD, "wand_fully_bound", 1),
        UNFORGIVABLE_MASTER("unforgivable_master", "黑魔王", "使用不可饶恕咒 10 次",
                Material.WITHER_SKELETON_SKULL, "unforgivable_uses", 10),

        // ══════ 许愿井类 ══════
        FIRST_WISH("first_wish", "初次许愿", "第一次向许愿井投入金属", Material.GOLD_INGOT, "wishes", 1),
        WISH_10("wish_10", "许愿常客", "许愿 10 次", Material.DIAMOND, "wishes", 10),
        LEGENDARY_WISH("legendary_wish", "天选之人", "从许愿井获得传说级物品", Material.DRAGON_EGG, "legendary_wishes", 1),
        JUNK_10("junk_10", "人傻钱多", "从许愿井抽到 10 次垃圾", Material.DIRT, "junk_wishes", 10),
        WELL_BUILDER("well_builder", "水利工程师", "搭出 5×5 以上的水井", Material.WATER_BUCKET, "well_built", 1),

        // ══════ 好友类 ══════
        FIRST_FRIEND("first_friend", "交个朋友", "添加第一个好友", Material.PLAYER_HEAD, "friends_added", 1),
        SOCIAL_10("social_10", "社交达人", "拥有 10 个好友", Material.PLAYER_HEAD, "friends_count", 10),
        FRIEND_TP("friend_tp", "瞬移高手", "传送到好友身边", Material.ENDER_PEARL, "friend_tps", 1),

        // ══════ 猜数字类 ══════
        FIRST_GUESS_WIN("first_guess_win", "猜对了", "猜中一次数字", Material.TARGET, "guess_wins", 1),
        GUESS_5("guess_5", "神机妙算", "猜中数字 5 次", Material.DIAMOND, "guess_wins", 5),

        // ══════ 诡异事件类 ══════
        SAW_HEROBRINE("saw_herobrine", "我看到了...", "目击 Herobrine", Material.ENDER_EYE, "herobrine_seen", 1),
        TRACKED("tracked", "被跟踪", "被 Herobrine 追踪", Material.ENDERMAN_SPAWN_EGG, "herobrine_tracked", 1),
        SAW_SIGN("saw_sign", "他留下的信息", "发现 Herobrine 的告示牌", Material.OAK_SIGN, "herobrine_signs", 1),
        BEDSIDE_HEROBRINE("bedside_herobrine", "床边有鬼", "睡觉时被 Herobrine 凝视", Material.RED_BED, "bedside_haunted", 1),
        WEIRD_VILLAGER("weird_villager", "那个村民不太对劲", "目击诡异村民", Material.VILLAGER_SPAWN_EGG, "weird_villagers", 1),
        SAW_CLONE("saw_clone", "另一个我", "目击自己的影分身", Material.ARMOR_STAND, "shadow_clones", 1),

        // ══════ 幽灵/幽灵模式类 ══════
        GHOST_STOLE("ghost_stole", "被偷了", "被幽灵偷走物品", Material.CHEST, "ghost_steals", 1),
        ENTER_GHOST("enter_ghost", "灵魂出窍", "第一次进入幽灵模式", Material.SOUL_LANTERN, "ghost_mode_entered", 1),

        // ══════ 血月类 ══════
        BLOOD_MOON_SURVIVOR("blood_moon", "血月幸存者", "经历血月事件", Material.REDSTONE, "blood_moons", 1),

        // ══════ 处决类 ══════
        WITNESS_EXECUTION("witness_execution", "旁观者", "目睹一次处决", Material.NETHERITE_AXE, "executions_witnessed", 1),
        EXECUTED("executed", "被处决", "自己被处决", Material.IRON_SWORD, "executions_received", 1),

        // ══════ 硬核类 ══════
        POLYGLOT("polyglot", "万事通", "解锁 20 个成就", Material.KNOWLEDGE_BOOK, "achievements_unlocked", 20),
        LEGENDARY_PLAYER("legendary", "传说玩家", "解锁全部成就", Material.NETHER_STAR, "achievements_unlocked", 999);

        public final String id;
        public final String displayName;
        public final String description;
        public final Material icon;
        /** 关联的计数器（用于累积型成就） */
        public final String counter;
        /** 达到多少次解锁 */
        public final int threshold;

        Ach(String id, String displayName, String description, Material icon, String counter, int threshold) {
            this.id = id;
            this.displayName = displayName;
            this.description = description;
            this.icon = icon;
            this.counter = counter;
            this.threshold = threshold;
        }

        public static Ach byId(String id) {
            for (Ach a : values()) {
                if (a.id.equals(id)) return a;
            }
            return null;
        }
    }

    // ==================== 玩家数据 ====================
    private static class PlayerData {
        final Set<String> unlocked = ConcurrentHashMap.newKeySet();
        final Map<String, Integer> counters = new ConcurrentHashMap<>();

        int getCounter(String key) {
            return counters.getOrDefault(key, 0);
        }

        void increment(String key, int amount) {
            counters.merge(key, amount, Integer::sum);
        }

        void setCounter(String key, int value) {
            counters.put(key, value);
        }
    }

    public AchievementManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "achievements.yml");
        load();

        // 定时保存
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (dirty) { save(); dirty = false; }
        }, 6000L, 6000L);
    }

    public void shutdown() {
        save();
    }

    // ==================== 解锁 API ====================

    /**
     * 直接解锁成就（用于一次性成就）。
     */
    public void unlock(Player player, Ach ach) {
        if (player == null || ach == null) return;
        PlayerData data = playerData.computeIfAbsent(player.getUniqueId(), k -> new PlayerData());
        if (data.unlocked.contains(ach.id)) return;

        data.unlocked.add(ach.id);
        dirty = true;

        // 广播
        broadcastUnlock(player, ach);

        // 检查"万事通"和"传说玩家"
        checkMetaAchievements(player, data);
    }

    /**
     * 递增计数器，并检查是否有成就解锁。
     */
    public void increment(Player player, String counter, int amount) {
        if (player == null || counter == null || amount <= 0) return;
        PlayerData data = playerData.computeIfAbsent(player.getUniqueId(), k -> new PlayerData());
        data.increment(counter, amount);
        dirty = true;

        checkAchievements(player, data, counter);
    }

    /**
     * 设置计数器值（用于好友数这种直接赋值的）。
     */
    public void setCounter(Player player, String counter, int value) {
        if (player == null || counter == null) return;
        PlayerData data = playerData.computeIfAbsent(player.getUniqueId(), k -> new PlayerData());
        data.setCounter(counter, value);
        dirty = true;

        checkAchievements(player, data, counter);
    }

    private void checkAchievements(Player player, PlayerData data, String counter) {
        int value = data.getCounter(counter);

        for (Ach ach : Ach.values()) {
            if (data.unlocked.contains(ach.id)) continue;
            if (!ach.counter.equals(counter)) continue;
            if (value >= ach.threshold) {
                data.unlocked.add(ach.id);
                broadcastUnlock(player, ach);
            }
        }

        checkMetaAchievements(player, data);
    }

    private void checkMetaAchievements(Player player, PlayerData data) {
        // 万事通：解锁 20 个成就（排除自身和传说玩家）
        long count = data.unlocked.stream()
                .filter(id -> !id.equals(Ach.POLYGLOT.id) && !id.equals(Ach.LEGENDARY_PLAYER.id))
                .count();

        if (count >= 20 && !data.unlocked.contains(Ach.POLYGLOT.id)) {
            data.unlocked.add(Ach.POLYGLOT.id);
            broadcastUnlock(player, Ach.POLYGLOT);
        }

        // 传说玩家：解锁全部其他成就
        long total = Arrays.stream(Ach.values())
                .filter(a -> a != Ach.LEGENDARY_PLAYER)
                .count();
        if (count >= total - 1 && !data.unlocked.contains(Ach.LEGENDARY_PLAYER.id)) {
            data.unlocked.add(Ach.LEGENDARY_PLAYER.id);
            broadcastUnlock(player, Ach.LEGENDARY_PLAYER);
        }
    }

    private void broadcastUnlock(Player player, Ach ach) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Component msg = Component.text("🏆 ", NamedTextColor.GOLD)
                    .append(Component.text(player.getName(), NamedTextColor.YELLOW))
                    .append(Component.text(" 解锁了成就 ", NamedTextColor.GRAY))
                    .append(Component.text("[" + ach.displayName + "]", NamedTextColor.GOLD))
                    .append(Component.text(" - " + ach.description, NamedTextColor.DARK_GRAY));

            Bukkit.getServer().broadcast(msg);

            // 音效
            player.playSound(player.getLocation(),
                    Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);

            // 标题
            Title title = Title.title(
                    Component.text("🏆 成就解锁", NamedTextColor.GOLD),
                    Component.text(ach.displayName, NamedTextColor.YELLOW),
                    Title.Times.times(
                            Duration.ofMillis(300),
                            Duration.ofMillis(2500),
                            Duration.ofMillis(500))
            );
            player.showTitle(title);
        });
    }

    // ==================== 查询 ====================

    public boolean hasUnlocked(Player player, Ach ach) {
        PlayerData data = playerData.get(player.getUniqueId());
        return data != null && data.unlocked.contains(ach.id);
    }

    public int getUnlockedCount(Player player) {
        PlayerData data = playerData.get(player.getUniqueId());
        return data == null ? 0 : data.unlocked.size();
    }

    public int getTotalCount() {
        return Ach.values().length;
    }

    public int getCounter(Player player, String counter) {
        PlayerData data = playerData.get(player.getUniqueId());
        return data == null ? 0 : data.getCounter(counter);
    }

    public Set<String> getUnlockedIds(Player player) {
        PlayerData data = playerData.get(player.getUniqueId());
        return data == null ? Collections.emptySet() : new HashSet<>(data.unlocked);
    }

    // ==================== 持久化 ====================

    private void load() {
        if (!dataFile.exists()) return;

        FileConfiguration cfg = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection section = cfg.getConfigurationSection("players");
        if (section == null) return;

        for (String uuidStr : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                ConfigurationSection pd = section.getConfigurationSection(uuidStr);
                if (pd == null) continue;

                PlayerData data = new PlayerData();
                List<String> unlocked = pd.getStringList("unlocked");
                data.unlocked.addAll(unlocked);

                ConfigurationSection counters = pd.getConfigurationSection("counters");
                if (counters != null) {
                    for (String key : counters.getKeys(false)) {
                        data.counters.put(key, counters.getInt(key));
                    }
                }

                playerData.put(uuid, data);
            } catch (IllegalArgumentException ignored) {}
        }

        plugin.getLogger().info("[成就] 已加载 " + playerData.size() + " 位玩家的成就数据");
    }

    private void save() {
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();

            FileConfiguration cfg = new YamlConfiguration();
            for (Map.Entry<UUID, PlayerData> entry : playerData.entrySet()) {
                String base = "players." + entry.getKey().toString();
                cfg.set(base + ".unlocked", new ArrayList<>(entry.getValue().unlocked));
                for (Map.Entry<String, Integer> c : entry.getValue().counters.entrySet()) {
                    cfg.set(base + ".counters." + c.getKey(), c.getValue());
                }
            }
            cfg.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("[成就] 保存失败: " + e.getMessage());
        }
    }
}