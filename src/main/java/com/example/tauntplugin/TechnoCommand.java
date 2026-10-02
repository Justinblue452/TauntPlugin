package com.example.tauntplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class TechnoCommand implements CommandExecutor, TabCompleter {

    private final TechnobladeManager manager;

    public TechnoCommand(TechnobladeManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "spawn": {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("控制台无法使用此命令");
                    return true;
                }
                Pig pig = manager.spawnTechnoblade(player, player.getLocation().add(2, 0, 0));
                if (pig == null) {
                    player.sendMessage(Component.text("猪神已经存在于世界上。", NamedTextColor.YELLOW));
                }
                return true;
            }

            case "remove":
            case "kill": {
                if (manager.isTechnoAlive()) {
                    manager.removeTechnoblade();
                    sender.sendMessage(Component.text("✅ 已移除猪神 Technoblade。", NamedTextColor.GREEN));
                } else {
                    sender.sendMessage(Component.text("猪神当前不存在。", NamedTextColor.GRAY));
                }
                return true;
            }

            case "status":
            case "info": {
                if (manager.isTechnoAlive()) {
                    Pig pig = manager.getTechno();
                    if (pig != null) {
                        sender.sendMessage(Component.text("👑 猪神 Technoblade 存活中", NamedTextColor.GOLD));
                        sender.sendMessage(Component.text("📍 位置: " + pig.getLocation().getBlockX()
                                        + ", " + pig.getLocation().getBlockY()
                                        + ", " + pig.getLocation().getBlockZ(),
                                NamedTextColor.AQUA));
                        sender.sendMessage(Component.text("🌍 世界: "
                                + pig.getWorld().getName(), NamedTextColor.AQUA));
                    }
                } else {
                    sender.sendMessage(Component.text("猪神当前不存在。", NamedTextColor.GRAY));
                }
                return true;
            }

            default:
                showHelp(sender);
                return true;
        }
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage(Component.text("═════ /techno 帮助 ═════", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/techno spawn ", NamedTextColor.AQUA)
                .append(Component.text("- 在当前位置生成猪神", NamedTextColor.YELLOW)));
        sender.sendMessage(Component.text("/techno remove ", NamedTextColor.AQUA)
                .append(Component.text("- 移除猪神", NamedTextColor.YELLOW)));
        sender.sendMessage(Component.text("/techno status ", NamedTextColor.AQUA)
                .append(Component.text("- 查看猪神状态", NamedTextColor.YELLOW)));
        sender.sendMessage(Component.text("提示：拿着土豆靠近猪神，它会跟着你走。", NamedTextColor.GRAY));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("spawn", "remove", "status").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}