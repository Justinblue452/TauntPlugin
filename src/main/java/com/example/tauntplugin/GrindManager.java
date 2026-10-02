package com.example.tauntplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class GrindManager {

    private final JavaPlugin plugin;
    private final File dataFile;

    private final Map<UUID, Integer> grindPoints = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> weeklyPoints = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerNames = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> rankings = new ConcurrentHashMap<>();

    private volatile UUID weekKing = null;
    private volatile long weekStartTime = 0;

    private volatile boolean dirty = false;

    private BukkitTask nameTagTask;
    private BukkitTask sortTask;
    private BukkitTask saveTask;
    private BukkitTask weekCheckTask;

    private static final String TEAM_NAME = "tp_grind_display";

    public GrindManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "grind.yml");

        load();
        checkWeeklyReset();
        startTasks();
    }

    public void addPoint(Player player) {
        UUID uuid = player.getUniqueId();
        grindPoints.merge(uuid, 1, Integer::sum);
        weeklyPoints.merge(uuid, 1, Integer::sum);
        playerNames.put(uuid, player.getName());
        dirty = true;
    }

    /** ★ 直接给玩家加肝度（用于猜数字等奖励） */
    public void addPointsDirect(Player player, int amount) {
        if (player == null || amount <= 0) return;
        UUID uuid = player.getUniqueId();
        grindPoints.merge(uuid, amount, Integer::sum);
        weeklyPoints.merge(uuid, amount, Integer::sum);
        playerNames.put(uuid, player.getName());
        dirty = true;
    }

    public int getPoints(UUID uuid) { return grindPoints.getOrDefault(uuid, 0); }
    public int getWeeklyPoints(UUID uuid) { return weeklyPoints.getOrDefault(uuid, 0); }
    public int getRank(UUID uuid) { return rankings.getOrDefault(uuid, -1); }
    public UUID getWeekKing() { return weekKing; }
    public boolean isWeekKing(UUID uuid) { return weekKing != null && weekKing.equals(uuid); }

    public String getPlayerName(UUID uuid) {
        String cached = playerNames.get(uuid);
        if (cached != null) return cached;
        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        String name = op.getName();
        return name != null ? name : "未知";
    }

    public List<Map.Entry<UUID, Integer>> getTopPlayers(int limit) {
        return grindPoints.entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                .limit(limit).collect(Collectors.toList());
    }

    public List<Map.Entry<UUID, Integer>> getWeeklyTopPlayers(int limit) {
        return weeklyPoints.entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                .limit(limit).collect(Collectors.toList());
    }

    private void updateRankings() {
        List<Map.Entry<UUID, Integer>> sorted = grindPoints.entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                .collect(Collectors.toList());
        rankings.clear();
        for (int i = 0; i < sorted.size(); i++) {
            rankings.put(sorted.get(i).getKey(), i + 1);
        }
    }

    private long getCurrentMondayStart() {
        Calendar cal = Calendar.getInstance();
        cal.setFirstDayOfWeek(Calendar.MONDAY);
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private void checkWeeklyReset() {
        long thisMonday = getCurrentMondayStart();
        if (weekStartTime == 0) { weekStartTime = thisMonday; return; }
        if (thisMonday > weekStartTime) {
            settleWeek();
            weeklyPoints.clear();
            weekStartTime = thisMonday;
            dirty = true;
            updateAllNameTags();
        }
    }

    private void settleWeek() {
        UUID winner = null;
        int maxPoints = 0;
        for (Map.Entry<UUID, Integer> entry : weeklyPoints.entrySet()) {
            if (entry.getValue() > maxPoints) {
                maxPoints = entry.getValue();
                winner = entry.getKey();
            }
        }

        if (winner == null || maxPoints <= 0) {
            plugin.getLogger().info("[肝度] 上周无人挖矿，本周暂无肝帝");
            weekKing = null;
            return;
        }

        weekKing = winner;
        final String finalName = getPlayerName(winner);
        final int finalPoints = maxPoints;

        Bukkit.getScheduler().runTask(plugin, () -> {
            Bukkit.getServer().broadcast(
                    Component.text("═══════════════════════", NamedTextColor.GOLD));
            Bukkit.getServer().broadcast(
                    Component.text("👑 本周肝帝诞生！", NamedTextColor.GOLD));
            Bukkit.getServer().broadcast(
                    Component.text("  " + finalName + " ", NamedTextColor.YELLOW)
                            .append(Component.text("以 ", NamedTextColor.GRAY))
                            .append(Component.text(finalPoints + " 肝度", NamedTextColor.GREEN))
                            .append(Component.text(" 登顶！", NamedTextColor.GRAY)));
            Bukkit.getServer().broadcast(
                    Component.text("  称号 [👑肝帝] 已生效，为期一周", NamedTextColor.AQUA));
            Bukkit.getServer().broadcast(
                    Component.text("═══════════════════════", NamedTextColor.GOLD));
        });

        plugin.getLogger().info("[肝度] 本周肝帝: " + finalName + " (" + finalPoints + " 肝度)");
    }

    private void updateAllNameTags() {
        Collection<? extends Player> online = Bukkit.getOnlinePlayers();
        if (online.isEmpty()) return;
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = sb.getTeam(TEAM_NAME);
        if (team == null) {
            try { team = sb.registerNewTeam(TEAM_NAME); }
            catch (IllegalArgumentException e) { team = sb.getTeam(TEAM_NAME); }
        }
        if (team == null) return;
        for (Player p : online) updateNameTag(p, team);
    }

    private void updateNameTag(Player player, Team team) {
        String entry = player.getName();
        if (!team.hasEntry(entry)) team.addEntry(entry);
        team.prefix(buildPrefix(player));
    }

    public void updatePlayer(Player player) {
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = sb.getTeam(TEAM_NAME);
        if (team == null) {
            try { team = sb.registerNewTeam(TEAM_NAME); }
            catch (IllegalArgumentException e) { team = sb.getTeam(TEAM_NAME); }
        }
        if (team != null) updateNameTag(player, team);
    }

    private Component buildPrefix(Player player) {
        int rank = rankings.getOrDefault(player.getUniqueId(), -1);
        double health = player.getHealth();
        double maxHealth = player.getMaxHealth();

        NamedTextColor rankColor;
        if (rank == 1) rankColor = NamedTextColor.GOLD;
        else if (rank == 2) rankColor = NamedTextColor.GRAY;
        else if (rank == 3) rankColor = NamedTextColor.RED;
        else rankColor = NamedTextColor.DARK_GRAY;

        String rankText = rank > 0 ? "[#" + rank + "] " : "[--] ";

        NamedTextColor healthColor;
        double ratio = maxHealth > 0 ? health / maxHealth : 0;
        if (ratio > 0.66) healthColor = NamedTextColor.GREEN;
        else if (ratio > 0.33) healthColor = NamedTextColor.YELLOW;
        else healthColor = NamedTextColor.RED;

        String healthText = "❤" + (int) Math.ceil(health) + " ";

        Component result = Component.empty();
        if (isWeekKing(player.getUniqueId())) {
            result = result.append(Component.text("[👑肝帝] ", NamedTextColor.GOLD));
        }
        return result
                .append(Component.text(rankText, rankColor))
                .append(Component.text(healthText, healthColor));
    }

    public void onPlayerQuit(Player player) {
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = sb.getTeam(TEAM_NAME);
        if (team != null && team.hasEntry(player.getName())) {
            team.removeEntry(player.getName());
        }
    }

    private void startTasks() {
        nameTagTask = Bukkit.getScheduler().runTaskTimer(
                plugin, this::updateAllNameTags, 20L, 20L);
        sortTask = Bukkit.getScheduler().runTaskTimer(
                plugin, this::updateRankings, 40L, 100L);
        saveTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (dirty) { save(); dirty = false; }
        }, 6000L, 6000L);
        weekCheckTask = Bukkit.getScheduler().runTaskTimer(
                plugin, this::checkWeeklyReset, 6000L, 6000L);
    }

    public void shutdown() {
        if (nameTagTask != null) nameTagTask.cancel();
        if (sortTask != null) sortTask.cancel();
        if (saveTask != null) saveTask.cancel();
        if (weekCheckTask != null) weekCheckTask.cancel();

        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = sb.getTeam(TEAM_NAME);
        if (team != null) {
            for (String entry : new HashSet<>(team.getEntries())) {
                team.removeEntry(entry);
            }
            team.unregister();
        }
        save();
    }

    public void load() {
        if (!dataFile.exists()) {
            plugin.getLogger().info("[肝度] 无历史数据");
            return;
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(dataFile);

        ConfigurationSection section = config.getConfigurationSection("players");
        if (section != null) {
            for (String uuidStr : section.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    ConfigurationSection p = section.getConfigurationSection(uuidStr);
                    if (p == null) continue;
                    grindPoints.put(uuid, p.getInt("points", 0));
                    playerNames.put(uuid, p.getString("name", "未知"));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("[肝度] 无效 UUID: " + uuidStr);
                }
            }
        }

        this.weekStartTime = config.getLong("weekly.weekStartTime", 0);
        String kingStr = config.getString("weekly.weekKing", null);
        if (kingStr != null) {
            try { this.weekKing = UUID.fromString(kingStr); }
            catch (IllegalArgumentException ignored) {}
        }

        ConfigurationSection weekSection = config.getConfigurationSection("weekly.points");
        if (weekSection != null) {
            for (String uuidStr : weekSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    weeklyPoints.put(uuid, weekSection.getInt(uuidStr, 0));
                } catch (IllegalArgumentException ignored) {}
            }
        }

        plugin.getLogger().info("[肝度] 已加载 " + grindPoints.size() + " 位玩家的数据");
        updateRankings();
    }

    public void save() {
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            FileConfiguration config = new YamlConfiguration();

            for (Map.Entry<UUID, Integer> entry : grindPoints.entrySet()) {
                String path = "players." + entry.getKey().toString();
                config.set(path + ".points", entry.getValue());
                config.set(path + ".name", playerNames.getOrDefault(entry.getKey(), "未知"));
            }

            config.set("weekly.weekStartTime", weekStartTime);
            config.set("weekly.weekKing", weekKing != null ? weekKing.toString() : null);
            for (Map.Entry<UUID, Integer> entry : weeklyPoints.entrySet()) {
                config.set("weekly.points." + entry.getKey().toString(), entry.getValue());
            }

            config.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("[肝度] 保存失败: " + e.getMessage());
        }
    }
}