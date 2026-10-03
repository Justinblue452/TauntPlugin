package com.example.tauntplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class ExecutionCommand implements CommandExecutor, TabCompleter {

    private final ExecutionManager manager;

    public ExecutionCommand(ExecutionManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // ★ 修改：只用 OP 判断
        if (!sender.isOp()) {
            sender.sendMessage(Component.text("你没有权限使用此命令。", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        // ===== /chujue near <半径> =====
        if (sub.equals("near")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("near 模式只能由玩家执行", NamedTextColor.RED));
                return true;
            }
            double radius = 10.0;
            if (args.length >= 2) {
                try { radius = Double.parseDouble(args[1]); }
                catch (NumberFormatException e) {
                    sender.sendMessage(Component.text("半径必须是数字", NamedTextColor.RED));
                    return true;
                }
            }
            int count = manager.executeNear(sender, player.getLocation(), radius);
            if (count == 0) {
                sender.sendMessage(Component.text("范围内没有可处决的实体", NamedTextColor.GRAY));
            }
            return true;
        }

        // ===== /chujue type <实体类型> [半径] =====
        if (sub.equals("type")) {
            if (args.length < 2) {
                sender.sendMessage(Component.text("用法: /chujue type <实体类型> [半径]", NamedTextColor.RED));
                return true;
            }
            EntityType type;
            try {
                type = EntityType.valueOf(args[1].toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                sender.sendMessage(Component.text("未知实体类型: " + args[1], NamedTextColor.RED));
                return true;
            }
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("type 模式只能由玩家执行", NamedTextColor.RED));
                return true;
            }
            double radius = 20.0;
            if (args.length >= 3) {
                try { radius = Double.parseDouble(args[2]); }
                catch (NumberFormatException e) {
                    sender.sendMessage(Component.text("半径必须是数字", NamedTextColor.RED));
                    return true;
                }
            }
            int count = manager.executeByType(sender, player.getLocation(), type, radius);
            if (count == 0) {
                sender.sendMessage(Component.text("范围内没有该类型的实体", NamedTextColor.GRAY));
            }
            return true;
        }

        // ===== /chujue look [距离] =====
        if (sub.equals("look")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("look 模式只能由玩家执行", NamedTextColor.RED));
                return true;
            }
            double distance = 30.0;
            if (args.length >= 2) {
                try { distance = Double.parseDouble(args[1]); }
                catch (NumberFormatException e) {
                    sender.sendMessage(Component.text("距离必须是数字", NamedTextColor.RED));
                    return true;
                }
            }
            Entity target = manager.executeLookingAt(sender, player, distance);
            if (target == null) {
                sender.sendMessage(Component.text("你视线内没有实体", NamedTextColor.GRAY));
            }
            return true;
        }

        // ===== /chujue <玩家名> =====
        String targetName = args[0];
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(targetName.toLowerCase())) {
                    target = p;
                    break;
                }
            }
        }

        if (target == null) {
            sender.sendMessage(Component.text("找不到玩家: " + targetName
                    + "（试试 /chujue near、/chujue look、/chujue type）", NamedTextColor.RED));
            return true;
        }

        manager.execute(sender, target);
        return true;
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage(Component.text("═════ /chujue 处决帮助 ═════", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/chujue <玩家名>", NamedTextColor.AQUA)
                .append(Component.text(" - 处决指定玩家", NamedTextColor.YELLOW)));
        sender.sendMessage(Component.text("/chujue near <半径>", NamedTextColor.AQUA)
                .append(Component.text(" - 处决范围内所有实体", NamedTextColor.YELLOW)));
        sender.sendMessage(Component.text("/chujue type <实体类型> [半径]", NamedTextColor.AQUA)
                .append(Component.text(" - 处决指定类型实体", NamedTextColor.YELLOW)));
        sender.sendMessage(Component.text("/chujue look [距离]", NamedTextColor.AQUA)
                .append(Component.text(" - 处决视线内实体", NamedTextColor.YELLOW)));
        sender.sendMessage(Component.text("示例：", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  /chujue Steve", NamedTextColor.DARK_GRAY));
        sender.sendMessage(Component.text("  /chujue near 10", NamedTextColor.DARK_GRAY));
        sender.sendMessage(Component.text("  /chujue type ZOMBIE 30", NamedTextColor.DARK_GRAY));
        sender.sendMessage(Component.text("  /chujue look 50", NamedTextColor.DARK_GRAY));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>(Arrays.asList("near", "type", "look"));
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                    suggestions.add(p.getName());
                }
            }
            return suggestions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("type")) {
                return Arrays.stream(EntityType.values())
                        .filter(EntityType::isSpawnable)
                        .map(Enum::name)
                        .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                        .limit(30)
                        .collect(Collectors.toList());
            }
            if (args[0].equalsIgnoreCase("near") || args[0].equalsIgnoreCase("look")) {
                return Arrays.asList("10", "20", "30", "50").stream()
                        .filter(s -> s.startsWith(args[1]))
                        .collect(Collectors.toList());
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("type")) {
            return Arrays.asList("10", "20", "30", "50").stream()
                    .filter(s -> s.startsWith(args[2]))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}