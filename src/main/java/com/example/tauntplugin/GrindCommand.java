package com.example.tauntplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class GrindCommand implements CommandExecutor, TabCompleter {

    private final GrindManager grindManager;

    public GrindCommand(GrindManager grindManager) {
        this.grindManager = grindManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("此命令只能由玩家执行");
            return true;
        }

        if (args.length == 0) {
            int points = grindManager.getPoints(player.getUniqueId());
            int weekly = grindManager.getWeeklyPoints(player.getUniqueId());
            int rank = grindManager.getRank(player.getUniqueId());
            boolean isKing = grindManager.isWeekKing(player.getUniqueId());

            player.sendMessage(Component.text("═══════ 肝度统计 ═══════", NamedTextColor.GOLD));
            player.sendMessage(Component.text("总肝度: ", NamedTextColor.YELLOW)
                    .append(Component.text(points, NamedTextColor.GREEN)));
            player.sendMessage(Component.text("本周肝度: ", NamedTextColor.YELLOW)
                    .append(Component.text(weekly, NamedTextColor.AQUA)));
            player.sendMessage(Component.text("总排名: ", NamedTextColor.YELLOW)
                    .append(Component.text(rank > 0 ? "#" + rank : "未上榜", NamedTextColor.AQUA)));
            if (isKing) {
                player.sendMessage(Component.text("当前称号: ", NamedTextColor.YELLOW)
                        .append(Component.text("[👑肝帝]", NamedTextColor.GOLD)));
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("top")) {
            int limit = 10;
            if (args.length >= 2) {
                try { limit = Math.min(50, Math.max(1, Integer.parseInt(args[1]))); }
                catch (NumberFormatException ignored) {}
            }
            List<Map.Entry<UUID, Integer>> top = grindManager.getTopPlayers(limit);
            player.sendMessage(Component.text("═══════ 肝度排行榜 TOP " + limit + " ═══════",
                    NamedTextColor.GOLD));
            if (top.isEmpty()) {
                player.sendMessage(Component.text("还没有人上榜，快去挖方块吧！", NamedTextColor.GRAY));
                return true;
            }
            int i = 1;
            for (Map.Entry<UUID, Integer> entry : top) {
                printRankLine(player, i, entry.getKey(), entry.getValue());
                i++;
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("weekly")) {
            int limit = 10;
            if (args.length >= 2) {
                try { limit = Math.min(50, Math.max(1, Integer.parseInt(args[1]))); }
                catch (NumberFormatException ignored) {}
            }
            List<Map.Entry<UUID, Integer>> top = grindManager.getWeeklyTopPlayers(limit);
            player.sendMessage(Component.text("═══════ 本周肝度 TOP " + limit + " ═══════",
                    NamedTextColor.GOLD));
            if (top.isEmpty()) {
                player.sendMessage(Component.text("本周还没有人挖方块，快去肝！", NamedTextColor.GRAY));
                return true;
            }
            int i = 1;
            for (Map.Entry<UUID, Integer> entry : top) {
                printRankLine(player, i, entry.getKey(), entry.getValue());
                i++;
            }
            UUID king = grindManager.getWeekKing();
            if (king != null) {
                player.sendMessage(Component.text("当前肝帝: ", NamedTextColor.YELLOW)
                        .append(Component.text(grindManager.getPlayerName(king) + " 👑",
                                NamedTextColor.GOLD)));
            } else {
                player.sendMessage(Component.text("本周暂未结算出肝帝", NamedTextColor.GRAY));
            }
            return true;
        }

        player.sendMessage(Component.text("用法: /grind [top|weekly] [数量]", NamedTextColor.RED));
        return true;
    }

    private void printRankLine(Player receiver, int rank, UUID uuid, int points) {
        String name = grindManager.getPlayerName(uuid);
        NamedTextColor color;
        String medal;
        if (rank == 1) { color = NamedTextColor.GOLD; medal = "🥇"; }
        else if (rank == 2) { color = NamedTextColor.GRAY; medal = "🥈"; }
        else if (rank == 3) { color = NamedTextColor.RED; medal = "🥉"; }
        else { color = NamedTextColor.WHITE; medal = "  "; }

        Component line = Component.text("#" + rank + " " + medal + " ", color)
                .append(Component.text(name, NamedTextColor.YELLOW));
        if (grindManager.isWeekKing(uuid)) {
            line = line.append(Component.text(" [👑肝帝]", NamedTextColor.GOLD));
        }
        line = line.append(Component.text(" — ", NamedTextColor.DARK_GRAY))
                .append(Component.text(points + " 肝度", NamedTextColor.GREEN));
        receiver.sendMessage(line);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("top", "weekly").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}