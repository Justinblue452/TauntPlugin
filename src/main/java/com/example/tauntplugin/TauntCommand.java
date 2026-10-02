package com.example.tauntplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class TauntCommand implements CommandExecutor, TabCompleter {

    private final PlayerTauntManager tauntManager;

    private static final List<String> SUBCOMMANDS = List.of(
            "set", "list", "remove", "clear", "help"
    );

    public TauntCommand(PlayerTauntManager tauntManager) {
        this.tauntManager = tauntManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("此命令只能由玩家执行");
            return true;
        }

        // /taunt
        if (args.length == 0) {
            tauntManager.tauntNearest(player);
            return true;
        }

        String sub = args[0];

        // 子命令：set / list / remove / clear / help
        switch (sub.toLowerCase()) {
            case "help":
                tauntManager.showHelp(player);
                return true;

            case "set": {
                if (args.length < 2) {
                    player.sendMessage(Component.text("用法: /taunt set <文案>", NamedTextColor.RED));
                    return true;
                }
                String content = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                tauntManager.addCustomTaunt(player, content);
                return true;
            }

            case "list":
                tauntManager.listCustomTaunts(player);
                return true;

            case "remove":
            case "del":
            case "delete": {
                if (args.length < 2) {
                    player.sendMessage(Component.text("用法: /taunt remove <序号>", NamedTextColor.RED));
                    return true;
                }
                try {
                    int index = Integer.parseInt(args[1]);
                    tauntManager.removeCustomTaunt(player, index);
                } catch (NumberFormatException e) {
                    player.sendMessage(Component.text("请输入有效数字", NamedTextColor.RED));
                }
                return true;
            }

            case "clear":
                tauntManager.clearCustomTaunts(player);
                return true;

            default:
                // 不是子命令 → 当作玩家名处理
                tauntManager.tauntTarget(player, sub);
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) return Collections.emptyList();

        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();

            // 子命令
            for (String sub : SUBCOMMANDS) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    suggestions.add(sub);
                }
            }

            // 在线玩家名（排除自己）
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.equals(player)) continue;
                if (p.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                    suggestions.add(p.getName());
                }
            }

            return suggestions;
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("set")) {
                return Collections.singletonList("<文案>");
            }
            if (args[0].equalsIgnoreCase("remove")) {
                return Collections.singletonList("<序号>");
            }
        }

        return Collections.emptyList();
    }
}