package com.example.tauntplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 统一消息管理器（改进版）：
 * - 从 messages.yml 读取
 * - 支持 § 颜色代码
 * - 支持 {key} 字符串占位符
 * - 支持 Component 占位符（保留原颜色）
 * - 支持消息池随机抽取
 * - 支持热重载
 */
public class MessageManager {

    private final JavaPlugin plugin;
    private final File messageFile;
    private FileConfiguration messages;

    private final Map<String, Component> componentCache = new ConcurrentHashMap<>();

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    public MessageManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.messageFile = new File(plugin.getDataFolder(), "messages.yml");
        reload();
    }

    public void reload() {
        if (!messageFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }

        this.messages = YamlConfiguration.loadConfiguration(messageFile);

        try (InputStream in = plugin.getResource("messages.yml")) {
            if (in != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(in, StandardCharsets.UTF_8));
                messages.setDefaults(defaults);
                messages.options().copyDefaults(true);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[消息] 加载默认 messages.yml 失败: " + e.getMessage());
        }

        componentCache.clear();
        plugin.getLogger().info("[消息] messages.yml 已加载");
    }

    // ==================== 基础读取 ====================

    public String getRaw(String key) {
        return messages.getString(key, "§c缺失消息: " + key);
    }

    public Component get(String key) {
        return componentCache.computeIfAbsent(key, k -> LEGACY.deserialize(getRaw(k)));
    }

    /** 字符串占位符替换（常用） */
    public Component get(String key, Map<String, String> placeholders) {
        String raw = getRaw(key);
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            raw = raw.replace(e.getKey(), e.getValue());
        }
        return LEGACY.deserialize(raw);
    }

    /** Component 占位符替换（保留颜色） */
    public Component getRich(String key, Map<String, Component> placeholders) {
        String raw = getRaw(key);
        // 用标记替换，再解析为 Component 后追加
        Component result = LEGACY.deserialize(raw);
        for (Map.Entry<String, Component> e : placeholders.entrySet()) {
            result = result.replaceText(cfg ->
                    cfg.matchLiteral(e.getKey()).replacement(e.getValue()));
        }
        return result;
    }

    /** 获取消息池中的随机一条 */
    public String randomRaw(String path) {
        List<String> list = messages.getStringList(path);
        if (list.isEmpty()) return "§c缺失消息池: " + path;
        return list.get(ThreadLocalRandom.current().nextInt(list.size()));
    }

    /** 获取消息池中的随机一条并替换占位符 */
    public Component random(String path, Map<String, String> placeholders) {
        String raw = randomRaw(path);
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            raw = raw.replace(e.getKey(), e.getValue());
        }
        return LEGACY.deserialize(raw);
    }

    /** 获取消息池中的随机一条，支持 Component 占位符 */
    public Component randomRich(String path, Map<String, Component> placeholders) {
        String raw = randomRaw(path);
        Component result = LEGACY.deserialize(raw);
        for (Map.Entry<String, Component> e : placeholders.entrySet()) {
            result = result.replaceText(cfg ->
                    cfg.matchLiteral(e.getKey()).replacement(e.getValue()));
        }
        return result;
    }

    // ==================== 发送 ====================

    public void send(CommandSender sender, String key) {
        sender.sendMessage(get(key));
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        sender.sendMessage(get(key, placeholders));
    }

    public void sendRich(CommandSender sender, String key, Map<String, Component> placeholders) {
        sender.sendMessage(getRich(key, placeholders));
    }

    /** 发送带前缀的消息 */
    public void sendPrefixed(CommandSender sender, String prefixKey, String key) {
        sender.sendMessage(get(prefixKey).append(get(key)));
    }

    public void sendPrefixed(CommandSender sender, String prefixKey, String key,
                             Map<String, String> placeholders) {
        sender.sendMessage(get(prefixKey).append(get(key, placeholders)));
    }

    // ==================== 广播 ====================

    public void broadcast(String key) {
        org.bukkit.Bukkit.getServer().broadcast(get(key));
    }

    public void broadcast(String key, Map<String, String> placeholders) {
        org.bukkit.Bukkit.getServer().broadcast(get(key, placeholders));
    }

    public void broadcastRich(String key, Map<String, Component> placeholders) {
        org.bukkit.Bukkit.getServer().broadcast(getRich(key, placeholders));
    }

    public void broadcastPrefixed(String prefixKey, String key,
                                  Map<String, String> placeholders) {
        org.bukkit.Bukkit.getServer().broadcast(get(prefixKey).append(get(key, placeholders)));
    }

    // ==================== 工具 ====================

    public List<String> getStringList(String key) {
        return messages.getStringList(key);
    }

    public boolean has(String key) {
        return messages.contains(key);
    }

    public FileConfiguration getRaw() {
        return messages;
    }
}