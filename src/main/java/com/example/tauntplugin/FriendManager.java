package com.example.tauntplugin;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class FriendManager {

    private final JavaPlugin plugin;
    private final MessageManager messages;   // ★ 消息管理器
    private final File dataFile;

    private final Map<UUID, Set<UUID>> friends = new ConcurrentHashMap<>();

    public FriendManager(JavaPlugin plugin, MessageManager messages) {
        this.plugin = plugin;
        this.messages = messages;
        this.dataFile = new File(plugin.getDataFolder(), "friends.yml");
        load();
    }

    public JavaPlugin getPlugin() {
        return plugin;
    }

    public void shutdown() {
        save();
    }

    // ==================== 好友操作 ====================

    public boolean addFriend(Player player, Player target) {
        if (player.equals(target)) {
            messages.send(player, "friend.self-add");
            return false;
        }
        if (areFriends(player, target)) {
            messages.send(player, "friend.already-friend");
            return false;
        }

        friends.computeIfAbsent(player.getUniqueId(), k -> ConcurrentHashMap.newKeySet())
                .add(target.getUniqueId());
        friends.computeIfAbsent(target.getUniqueId(), k -> ConcurrentHashMap.newKeySet())
                .add(player.getUniqueId());
        save();

        // ★ 用消息系统替代硬编码
        messages.send(player, "friend.added", Map.of("{target}", target.getName()));
        messages.send(target, "friend.added-notify", Map.of("{player}", player.getName()));

        // 成就挂钩
        TauntUtils.increment(plugin, player, "friends_added", 1);
        TauntUtils.unlock(plugin, player, AchievementManager.Ach.FIRST_FRIEND);

        int count = getFriends(player.getUniqueId()).size();
        TauntUtils.setCounter(plugin, player, "friends_count", count);
        if (count >= 10) {
            TauntUtils.unlock(plugin, player, AchievementManager.Ach.SOCIAL_10);
        }
        return true;
    }

    public boolean removeFriend(Player player, UUID targetUuid, String targetName) {
        Set<UUID> set = friends.get(player.getUniqueId());
        if (set == null || !set.remove(targetUuid)) {
            messages.send(player, "friend.not-friend");
            return false;
        }
        Set<UUID> otherSet = friends.get(targetUuid);
        if (otherSet != null) otherSet.remove(player.getUniqueId());
        save();

        messages.send(player, "friend.removed", Map.of("{target}", targetName));
        return true;
    }

    public boolean areFriends(Player a, Player b) {
        return areFriends(a.getUniqueId(), b.getUniqueId());
    }

    public boolean areFriends(UUID a, UUID b) {
        Set<UUID> set = friends.get(a);
        return set != null && set.contains(b);
    }

    public Set<UUID> getFriends(UUID uuid) {
        return friends.getOrDefault(uuid, Collections.emptySet());
    }

    public List<UUID> getOnlineFriends(Player player) {
        List<UUID> list = new ArrayList<>();
        for (UUID uuid : getFriends(player.getUniqueId())) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) list.add(uuid);
        }
        return list;
    }

    public void onPlayerJoin(Player player) {
        for (UUID uuid : getFriends(player.getUniqueId())) {
            Player friend = Bukkit.getPlayer(uuid);
            if (friend != null && friend.isOnline()) {
                messages.send(friend, "friend.friend-online",
                        Map.of("{player}", player.getName()));
            }
        }
    }

    public void onPlayerQuit(Player player) {
        for (UUID uuid : getFriends(player.getUniqueId())) {
            Player friend = Bukkit.getPlayer(uuid);
            if (friend != null && friend.isOnline()) {
                messages.send(friend, "friend.friend-offline",
                        Map.of("{player}", player.getName()));
            }
        }
    }

    // ==================== 持久化 ====================

    private void load() {
        if (!dataFile.exists()) return;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(dataFile);
        for (String uuidStr : cfg.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                List<String> list = cfg.getStringList(uuidStr);
                Set<UUID> set = ConcurrentHashMap.newKeySet();
                for (String s : list) {
                    try { set.add(UUID.fromString(s)); } catch (IllegalArgumentException ignored) {}
                }
                if (!set.isEmpty()) friends.put(uuid, set);
            } catch (IllegalArgumentException ignored) {}
        }
        plugin.getLogger().info("[好友] 已加载 " + friends.size() + " 位玩家的好友数据");
    }

    private void save() {
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            FileConfiguration cfg = new YamlConfiguration();
            for (Map.Entry<UUID, Set<UUID>> entry : friends.entrySet()) {
                List<String> list = new ArrayList<>();
                for (UUID f : entry.getValue()) list.add(f.toString());
                cfg.set(entry.getKey().toString(), list);
            }
            cfg.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("[好友] 保存失败: " + e.getMessage());
        }
    }
}