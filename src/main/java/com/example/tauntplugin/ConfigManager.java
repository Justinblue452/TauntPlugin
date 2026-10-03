package com.example.tauntplugin;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class ConfigManager {

    private final JavaPlugin plugin;
    private FileConfiguration config;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        try {
            plugin.saveDefaultConfig();
            plugin.reloadConfig();
            this.config = plugin.getConfig();
        } catch (Exception e) {
            plugin.getLogger().warning("[配置] 重载失败: " + e.getMessage());
            this.config = plugin.getConfig();
        }
    }

    public boolean isEnabled(String path) {
        return config.getBoolean(path + ".enabled", true);
    }

    public boolean getBoolean(String path, boolean def) { return config.getBoolean(path, def); }
    public int getInt(String path, int def) { return config.getInt(path, def); }
    public long getLong(String path, long def) { return config.getLong(path, def); }
    public double getDouble(String path, double def) { return config.getDouble(path, def); }
    public String getString(String path, String def) { return config.getString(path, def); }
    public ConfigurationSection getSection(String path) { return config.getConfigurationSection(path); }
    public FileConfiguration getRaw() { return config; }
}