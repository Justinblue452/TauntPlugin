package com.example.tauntplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Set;

public class AchievementCommand implements CommandExecutor {

    private final AchievementManager manager;

    public AchievementCommand(AchievementManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("此命令只能由玩家执行");
            return true;
        }

        // /ach — 显示自己的成就进度
        if (args.length == 0) {
            int unlocked = manager.getUnlockedCount(player);
            int total = manager.getTotalCount();

            player.sendMessage(Component.text("═════ 🏆 成就进度 ═════", NamedTextColor.GOLD));
            player.sendMessage(Component.text("已解锁: ", NamedTextColor.YELLOW)
                    .append(Component.text(unlocked + " / " + total, NamedTextColor.GREEN)));

            // 进度条
            int barTotal = 30;
            int filled = (int) Math.round(unlocked / (double) total * barTotal);
            StringBuilder bar = new StringBuilder();
            for (int i = 0; i < barTotal; i++) {
                bar.append(i < filled ? "§a█" : "§7░");
            }
            player.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                    .legacySection().deserialize("§7[" + bar + "§7]"));

            player.sendMessage(Component.empty());
            player.sendMessage(Component.text("输入 /ach list 查看所有成就", NamedTextColor.GRAY));
            return true;
        }

        // /ach list — 列出所有成就
        if (args[0].equalsIgnoreCase("list")) {
            Set<String> unlockedIds = manager.getUnlockedIds(player);
            player.sendMessage(Component.text("═════ 🏆 全部成就 ═════", NamedTextColor.GOLD));

            AchievementManager.Ach[] values = AchievementManager.Ach.values();
            for (int i = 0; i < values.length; i++) {
                AchievementManager.Ach ach = values[i];
                boolean isUnlocked = unlockedIds.contains(ach.id);

                Component icon = isUnlocked
                        ? Component.text("✅ ", NamedTextColor.GREEN)
                        : Component.text("⬜ ", NamedTextColor.DARK_GRAY);

                Component name = isUnlocked
                        ? Component.text(ach.displayName, NamedTextColor.GOLD)
                        : Component.text(ach.displayName, NamedTextColor.DARK_GRAY);

                Component desc = Component.text(" - " + ach.description,
                        isUnlocked ? NamedTextColor.YELLOW : NamedTextColor.DARK_GRAY);

                player.sendMessage(icon.append(name).append(desc));

                // 每 10 条暂停一下（避免刷屏）
                if ((i + 1) % 10 == 0 && i < values.length - 1) {
                    player.sendMessage(Component.empty());
                }
            }
            return true;
        }

        // /ach stats — 查看计数器
        if (args[0].equalsIgnoreCase("stats")) {
            player.sendMessage(Component.text("═════ 📊 你的统计 ═════", NamedTextColor.AQUA));
            player.sendMessage(Component.text("挖方块: ", NamedTextColor.YELLOW)
                    .append(Component.text(manager.getCounter(player, "blocks_broken"), NamedTextColor.GREEN)));
            player.sendMessage(Component.text("放方块: ", NamedTextColor.YELLOW)
                    .append(Component.text(manager.getCounter(player, "blocks_placed"), NamedTextColor.GREEN)));
            player.sendMessage(Component.text("死亡次数: ", NamedTextColor.YELLOW)
                    .append(Component.text(manager.getCounter(player, "deaths"), NamedTextColor.GREEN)));
            player.sendMessage(Component.text("嘲讽次数: ", NamedTextColor.YELLOW)
                    .append(Component.text(manager.getCounter(player, "taunts_given"), NamedTextColor.GREEN)));
            player.sendMessage(Component.text("施法次数: ", NamedTextColor.YELLOW)
                    .append(Component.text(manager.getCounter(player, "spells_cast"), NamedTextColor.GREEN)));
            player.sendMessage(Component.text("许愿次数: ", NamedTextColor.YELLOW)
                    .append(Component.text(manager.getCounter(player, "wishes"), NamedTextColor.GREEN)));
            player.sendMessage(Component.text("猜中数字: ", NamedTextColor.YELLOW)
                    .append(Component.text(manager.getCounter(player, "guess_wins"), NamedTextColor.GREEN)));
            return true;
        }

        // /ach <玩家> — 查看别人的成就
        if (args.length >= 1) {
            Player target = org.bukkit.Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                player.sendMessage(Component.text("找不到玩家: " + args[0], NamedTextColor.RED));
                return true;
            }
            int unlocked = manager.getUnlockedCount(target);
            int total = manager.getTotalCount();
            player.sendMessage(Component.text("═════ 🏆 " + target.getName() + " 的成就 ═════",
                    NamedTextColor.GOLD));
            player.sendMessage(Component.text("已解锁: ", NamedTextColor.YELLOW)
                    .append(Component.text(unlocked + " / " + total, NamedTextColor.GREEN)));
            return true;
        }

        return true;
    }
}