package com.example.tauntplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class PlayerTauntManager {

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final File dataFile;

    private long cooldownMs;
    private double nearestMaxDistance;
    private double targetedMaxDistance;
    private int maxCustomLength;
    private int maxCustomCount;

    // ★ 使用 CooldownManager 替代 Map<UUID, Long>
    private final CooldownManager cooldowns;

    private static final List<String> BANNED_KEYWORDS = List.of(
            "操你", "草泥马", "傻逼", "妈的", "fuck", "shit"
    );

    private static final List<String> DEFAULT_TAUNT_MESSAGES = List.of(
            "%player% 对 %target% 说：你的操作比我的猫还差。",
            "%player% 嘲讽 %target%：装备挺好看，可惜人不行。",
            "%player% 对 %target% 说：就这？我还以为多强呢。",
            "%player% 嘲讽 %target%：你是来玩的还是来送人头的？",
            "%player% 对 %target% 说：你背后的苦力怕都比你强。",
            "%player% 嘲讽 %target%：你的准头是闭着眼练的吧？",
            "%player% 对 %target% 说：这身装备，穿在你身上可惜了。",
            "%player% 嘲讽 %target%：你走路的声音，比你打架的声音还大。",
            "%player% 对 %target% 说：你的操作，让我怀疑你是不是用脚玩的。",
            "%player% 嘲讽 %target%：你在游戏里的存在感，比空气还低。",
            "%player% 对 %target% 说：你这个人，连僵尸都懒得追你。",
            "%player% 嘲讽 %target%：你的血条，大概全靠药水撑着吧。",
            "%player% 对 %target% 说：别怕，我不会像怪物那样追你。",
            "%player% 嘲讽 %target%：你是迷路了吗？还是本来就不知道路？",
            "%player% 对 %target% 说：你的意识，好像还停留在新手村。",
            "%player% 嘲讽 %target%：你的背包，比你的操作更让人好奇。",
            "%player% 对 %target% 说：你的名字，我记不住，因为不值得。",
            "%player% 嘲讽 %target%：你的装备，是从别人尸体上扒的吧？",
            "%player% 对 %target% 说：你可以去和平模式，那里更适合你。",
            "%player% 嘲讽 %target%：你不是在玩 Minecraft，你是在被 Minecraft 玩。",
            "%player% 对 %target% 说：你连羊都能被顶下悬崖吧？",
            "%player% 嘲讽 %target%：你的操作水平，堪称服务器一绝（倒数）。",
            "%player% 对 %target% 说：我建议你把难度调成和平。",
            "%player% 嘲讽 %target%：你的存在，就是为了衬托别人。",
            "%player% 对 %target% 说：你的战斗力，还不如一只鸡。",
            "%player% 嘲讽 %target%：你玩游戏的样子，像极了新手教程。",
            "%player% 对 %target% 说：你手里的剑，好像从来没沾过血。",
            "%player% 嘲讽 %target%：你的建筑水平，比你的战斗水平还差。",
            "%player% 对 %target% 说：你在服务器里的作用，就是给别人送装备。",
            "%player% 嘲讽 %target%：你的反应速度，大概跟树懒有得一拼。",
            "%player% 对 %target% 说：你不如去跟村民做生意，至少不会死。",
            "%player% 嘲讽 %target%：你的战略，就是没有战略。",
            "%player% 对 %target% 说：你的操作，让敌人都不忍心打你。",
            "%player% 嘲讽 %target%：你下线吧，服务器会清净很多。",
            "%player% 对 %target% 说：你是不是把设置里的鼠标灵敏度调成 0 了？",
            "%player% 嘲讽 %target%：你的死亡次数，比你的击杀次数多吧？",
            "%player% 对 %target% 说：你的技能点，全点在挨打上了。",
            "%player% 嘲讽 %target%：你的角色名应该改成\"移动靶子\"。"
    );

    private final Map<UUID, List<String>> customTaunts = new ConcurrentHashMap<>();

    public PlayerTauntManager(JavaPlugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        this.dataFile = new File(plugin.getDataFolder(), "custom_taunts.yml");

        this.cooldownMs = config.getLong("player-taunt.cooldown-ms", 5000);
        this.nearestMaxDistance = config.getDouble("player-taunt.nearest-max-distance", 30.0);
        this.targetedMaxDistance = config.getDouble("player-taunt.targeted-max-distance", 100.0);
        this.maxCustomLength = config.getInt("player-taunt.max-custom-length", 80);
        this.maxCustomCount = config.getInt("player-taunt.max-custom-count", 10);

        // ★ 创建冷却管理器（不启用全局冷却，因为嘲讽本身就是独立冷却）
        this.cooldowns = new CooldownManager(plugin, cooldownMs, 0);

        load();
    }

    public void shutdown() {
        save();
        if (cooldowns != null) cooldowns.shutdown();   // ★
    }

    // ==================== 嘲讽触发 ====================

    public boolean tauntNearest(Player player) {
        if (!checkCooldown(player)) return false;

        Player nearest = findNearestPlayer(player);
        if (nearest == null) {
            player.sendMessage(Component.text("附近没有可嘲讽的玩家", NamedTextColor.GRAY));
            return false;
        }

        performTaunt(player, nearest);
        return true;
    }

    public boolean tauntTarget(Player player, String targetName) {
        if (!checkCooldown(player)) return false;

        Player target = TauntUtils.findPlayerByName(targetName);   // ★ 使用工具类
        if (target == null) {
            player.sendMessage(Component.text("找不到玩家: " + targetName, NamedTextColor.RED));
            return false;
        }
        if (target.equals(player)) {
            player.sendMessage(Component.text("你不能嘲讽自己", NamedTextColor.RED));
            return false;
        }
        if (target.getGameMode().name().equals("SPECTATOR")) {
            player.sendMessage(Component.text("不能嘲讽旁观模式的玩家", NamedTextColor.RED));
            return false;
        }
        if (!target.getWorld().equals(player.getWorld())) {
            player.sendMessage(Component.text("目标不在同一世界", NamedTextColor.RED));
            return false;
        }

        if (targetedMaxDistance > 0) {
            double dist = target.getLocation().distance(player.getLocation());
            if (dist > targetedMaxDistance) {
                player.sendMessage(Component.text(
                        String.format("目标距离太远（%.1f 格，上限 %.0f 格）", dist, targetedMaxDistance),
                        NamedTextColor.RED));
                return false;
            }
        }

        performTaunt(player, target);
        return true;
    }

    // ★ 简化版冷却检查
    private boolean checkCooldown(Player player) {
        if (!cooldowns.isReady(player.getUniqueId(), "taunt")) {
            long remainMs = cooldowns.getRemaining(player.getUniqueId(), "taunt", cooldownMs);
            long remainSec = remainMs / 1000 + 1;
            player.sendMessage(Component.text("嘲讽冷却中，还需 " + remainSec + " 秒",
                    NamedTextColor.GRAY));
            return false;
        }
        return true;
    }

    private void performTaunt(Player player, Player target) {
        String template = pickTemplate(player);

        Component playerName = Component.text(player.getName(), NamedTextColor.AQUA);
        Component targetName = Component.text(target.getName(), NamedTextColor.RED);

        Component message = Component.text("[嘲讽] ", NamedTextColor.GOLD)
                .append(Component.text(template, NamedTextColor.YELLOW)
                        .replaceText(cfg -> cfg.matchLiteral("%player%").replacement(playerName))
                        .replaceText(cfg -> cfg.matchLiteral("%target%").replacement(targetName)));

        Bukkit.getServer().broadcast(message);

        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.2f);
        target.playSound(target.getLocation(), Sound.ENTITY_VILLAGER_HURT, 1.0f, 0.8f);

        player.getWorld().spawnParticle(
                Particle.ANGRY_VILLAGER,
                player.getLocation().add(0, 2.3, 0),
                5, 0.3, 0.2, 0.3, 0.0
        );
        target.getWorld().spawnParticle(
                Particle.CLOUD,
                target.getLocation().add(0, 2.3, 0),
                5, 0.3, 0.2, 0.3, 0.01
        );

        // 成就挂钩（用工具类，1 行搞定）
        TauntUtils.increment(plugin, player, "taunts_given", 1);
        TauntUtils.unlock(plugin, player, AchievementManager.Ach.FIRST_TAUNT);
    }

    private String pickTemplate(Player player) {
        List<String> custom = customTaunts.get(player.getUniqueId());
        if (custom != null && !custom.isEmpty() && ThreadLocalRandom.current().nextDouble() < 0.7) {
            return custom.get(ThreadLocalRandom.current().nextInt(custom.size()));
        }
        return DEFAULT_TAUNT_MESSAGES.get(
                ThreadLocalRandom.current().nextInt(DEFAULT_TAUNT_MESSAGES.size()));
    }

    private Player findNearestPlayer(Player player) {
        Player nearest = null;
        double nearestDistSq = nearestMaxDistance * nearestMaxDistance;

        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(player)) continue;
            if (other.getGameMode().name().equals("SPECTATOR")) continue;
            if (!other.getWorld().equals(player.getWorld())) continue;

            double distSq = other.getLocation().distanceSquared(player.getLocation());
            if (distSq < nearestDistSq) {
                nearestDistSq = distSq;
                nearest = other;
            }
        }
        return nearest;
    }

    // ==================== 自定义文案管理 ====================

    public void addCustomTaunt(Player player, String content) {
        if (content.length() > maxCustomLength) {
            player.sendMessage(Component.text("文案太长，最多 " + maxCustomLength + " 个字符",
                    NamedTextColor.RED));
            return;
        }

        String lower = content.toLowerCase();
        for (String banned : BANNED_KEYWORDS) {
            if (lower.contains(banned)) {
                player.sendMessage(Component.text("文案包含违规词，禁止使用", NamedTextColor.RED));
                return;
            }
        }

        if (content.contains("%player%")) {
            player.sendMessage(Component.text("自定义文案不支持 %player% 占位符，将使用你的名字代替",
                    NamedTextColor.YELLOW));
        }

        List<String> list = customTaunts.computeIfAbsent(
                player.getUniqueId(), k -> new ArrayList<>());

        if (list.size() >= maxCustomCount) {
            player.sendMessage(Component.text("你最多只能保存 " + maxCustomCount + " 条自定义文案",
                    NamedTextColor.RED));
            return;
        }

        list.add(content);
        save();

        player.sendMessage(Component.text("✅ 已添加自定义嘲讽文案：", NamedTextColor.GREEN)
                .append(Component.text(content, NamedTextColor.YELLOW)));
        player.sendMessage(Component.text("当前共 " + list.size() + "/" + maxCustomCount + " 条",
                NamedTextColor.GRAY));

        // 成就挂钩
        TauntUtils.unlock(plugin, player, AchievementManager.Ach.CUSTOM_TAUNT);
    }

    public void clearCustomTaunts(Player player) {
        List<String> removed = customTaunts.remove(player.getUniqueId());
        if (removed == null || removed.isEmpty()) {
            player.sendMessage(Component.text("你没有设置任何自定义文案", NamedTextColor.GRAY));
            return;
        }
        save();
        player.sendMessage(Component.text("✅ 已清除 " + removed.size() + " 条自定义嘲讽文案",
                NamedTextColor.GREEN));
    }

    public void listCustomTaunts(Player player) {
        List<String> list = customTaunts.get(player.getUniqueId());
        if (list == null || list.isEmpty()) {
            player.sendMessage(Component.text("你还没有设置自定义嘲讽文案", NamedTextColor.GRAY));
            player.sendMessage(Component.text("用法: /taunt set <文案>", NamedTextColor.YELLOW));
            return;
        }

        player.sendMessage(Component.text("═════ 你的自定义文案 ═════", NamedTextColor.GOLD));
        for (int i = 0; i < list.size(); i++) {
            player.sendMessage(Component.text("#" + (i + 1) + " ", NamedTextColor.AQUA)
                    .append(Component.text(list.get(i), NamedTextColor.YELLOW)));
        }
        player.sendMessage(Component.text("共 " + list.size() + "/" + maxCustomCount + " 条",
                NamedTextColor.GRAY));
    }

    public void removeCustomTaunt(Player player, int index) {
        List<String> list = customTaunts.get(player.getUniqueId());
        if (list == null || list.isEmpty()) {
            player.sendMessage(Component.text("你还没有设置自定义嘲讽文案", NamedTextColor.GRAY));
            return;
        }
        if (index < 1 || index > list.size()) {
            player.sendMessage(Component.text("无效的序号，范围 1~" + list.size(), NamedTextColor.RED));
            return;
        }

        String removed = list.remove(index - 1);
        if (list.isEmpty()) customTaunts.remove(player.getUniqueId());
        save();
        player.sendMessage(Component.text("✅ 已删除：", NamedTextColor.GREEN)
                .append(Component.text(removed, NamedTextColor.YELLOW)));
    }

    public void showHelp(Player player) {
        player.sendMessage(Component.text("═════ /taunt 帮助 ═════", NamedTextColor.GOLD));
        player.sendMessage(Component.text("/taunt ", NamedTextColor.AQUA)
                .append(Component.text("- 嘲讽最近的玩家", NamedTextColor.YELLOW)));
        player.sendMessage(Component.text("/taunt <玩家名> ", NamedTextColor.AQUA)
                .append(Component.text("- 嘲讽指定玩家", NamedTextColor.YELLOW)));
        player.sendMessage(Component.text("/taunt set <文案> ", NamedTextColor.AQUA)
                .append(Component.text("- 添加自定义文案", NamedTextColor.YELLOW)));
        player.sendMessage(Component.text("/taunt list ", NamedTextColor.AQUA)
                .append(Component.text("- 查看你的自定义文案", NamedTextColor.YELLOW)));
        player.sendMessage(Component.text("/taunt remove <序号> ", NamedTextColor.AQUA)
                .append(Component.text("- 删除指定文案", NamedTextColor.YELLOW)));
        player.sendMessage(Component.text("/taunt clear ", NamedTextColor.AQUA)
                .append(Component.text("- 清除所有自定义文案", NamedTextColor.YELLOW)));
        player.sendMessage(Component.text("提示：自定义文案支持 %target% 占位符", NamedTextColor.GRAY));
    }

    // ==================== 持久化 ====================

    private void load() {
        if (!dataFile.exists()) return;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(dataFile);
        for (String uuidStr : cfg.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                List<String> list = cfg.getStringList(uuidStr);
                if (!list.isEmpty()) customTaunts.put(uuid, new ArrayList<>(list));
            } catch (IllegalArgumentException ignored) {}
        }
        plugin.getLogger().info("[嘲讽] 已加载 " + customTaunts.size() + " 位玩家的自定义文案");
    }

    private void save() {
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            FileConfiguration cfg = new YamlConfiguration();
            for (Map.Entry<UUID, List<String>> entry : customTaunts.entrySet()) {
                cfg.set(entry.getKey().toString(), entry.getValue());
            }
            cfg.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("[嘲讽] 保存失败: " + e.getMessage());
        }
    }
}