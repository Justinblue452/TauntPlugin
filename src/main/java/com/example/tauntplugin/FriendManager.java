package com.example.tauntplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
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
    private final File dataFile;

    /** UUID -> 好友 UUID 集合 */
    private final Map<UUID, Set<UUID>> friends = new ConcurrentHashMap<>();

    public FriendManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "friends.yml");
        load();
    }

    public void shutdown() {
        save();
    }

    // ==================== 好友操作 ====================

    public boolean addFriend(Player player, Player target) {
        if (player.equals(target)) {
            player.sendMessage(Component.text("不能添加自己为好友", NamedTextColor.RED));
            return false;
        }
        if (areFriends(player, target)) {
            player.sendMessage(Component.text("你们已经是好友了", NamedTextColor.YELLOW));
            return false;
        }

        friends.computeIfAbsent(player.getUniqueId(), k -> ConcurrentHashMap.newKeySet())
                .add(target.getUniqueId());
        friends.computeIfAbsent(target.getUniqueId(), k -> ConcurrentHashMap.newKeySet())
                .add(player.getUniqueId());
        save();

        player.sendMessage(Component.text("✅ 已添加 ", NamedTextColor.GREEN)
                .append(Component.text(target.getName(), NamedTextColor.AQUA))
                .append(Component.text(" 为好友", NamedTextColor.GREEN)));
        target.sendMessage(Component.text("💚 ", NamedTextColor.GREEN)
                .append(Component.text(player.getName(), NamedTextColor.AQUA))
                .append(Component.text(" 把你添加为好友了", NamedTextColor.GREEN)));
        return true;
    }

    public boolean removeFriend(Player player, UUID targetUuid, String targetName) {
        Set<UUID> set = friends.get(player.getUniqueId());
        if (set == null || !set.remove(targetUuid)) {
            player.sendMessage(Component.text("你们不是好友", NamedTextColor.YELLOW));
            return false;
        }
        Set<UUID> otherSet = friends.get(targetUuid);
        if (otherSet != null) otherSet.remove(player.getUniqueId());
        save();

        player.sendMessage(Component.text("✅ 已移除好友 ", NamedTextColor.GREEN)
                .append(Component.text(targetName, NamedTextColor.AQUA)));
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
            if (p != null && p.isOnline()) {
                list.add(uuid);
            }
        }
        return list;
    }

    /**
     * 玩家上线时通知其在线好友。
     */
    public void onPlayerJoin(Player player) {
        for (UUID uuid : getFriends(player.getUniqueId())) {
            Player friend = Bukkit.getPlayer(uuid);
            if (friend != null && friend.isOnline()) {
                friend.sendMessage(Component.text("💚 好友 ", NamedTextColor.GREEN)
                        .append(Component.text(player.getName(), NamedTextColor.AQUA))
                        .append(Component.text(" 上线了", NamedTextColor.GREEN)));
            }
        }
    }

    /**
     * 玩家下线时通知其在线好友。
     */
    public void onPlayerQuit(Player player) {
        for (UUID uuid : getFriends(player.getUniqueId())) {
            Player friend = Bukkit.getPlayer(uuid);
            if (friend != null && friend.isOnline()) {
                friend.sendMessage(Component.text("💔 好友 ", NamedTextColor.GRAY)
                        .append(Component.text(player.getName(), NamedTextColor.AQUA))
                        .append(Component.text(" 下线了", NamedTextColor.GRAY)));
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
                    try {
                        set.add(UUID.fromString(s));
                    } catch (IllegalArgumentException ignored) {}
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