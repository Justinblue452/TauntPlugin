package com.example.tauntplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class FriendCommand implements CommandExecutor, TabCompleter {

    private final FriendManager friendManager;

    public FriendCommand(FriendManager friendManager) {
        this.friendManager = friendManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("此命令只能由玩家执行");
            return true;
        }

        if (args.length == 0) {
            showHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "add": {
                if (args.length < 2) {
                    player.sendMessage(Component.text("用法: /friend add <玩家>", NamedTextColor.RED));
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    player.sendMessage(Component.text("找不到在线玩家: " + args[1], NamedTextColor.RED));
                    return true;
                }
                friendManager.addFriend(player, target);
                return true;
            }

            case "remove":
            case "del": {
                if (args.length < 2) {
                    player.sendMessage(Component.text("用法: /friend remove <玩家>", NamedTextColor.RED));
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                UUID uuid;
                String name;
                if (target != null) {
                    uuid = target.getUniqueId();
                    name = target.getName();
                } else {
                    var offline = Bukkit.getOfflinePlayer(args[1]);
                    uuid = offline.getUniqueId();
                    name = offline.getName() != null ? offline.getName() : args[1];
                }
                friendManager.removeFriend(player, uuid, name);
                return true;
            }

            case "list": {
                Set<UUID> list = friendManager.getFriends(player.getUniqueId());
                if (list.isEmpty()) {
                    player.sendMessage(Component.text("你还没有好友", NamedTextColor.GRAY));
                    return true;
                }
                player.sendMessage(Component.text("═════ 好友列表 ═════", NamedTextColor.GOLD));
                int online = 0;
                for (UUID uuid : list) {
                    Player p = Bukkit.getPlayer(uuid);
                    String name = p != null ? p.getName() : Bukkit.getOfflinePlayer(uuid).getName();
                    if (name == null) name = uuid.toString().substring(0, 8);
                    if (p != null && p.isOnline()) {
                        online++;
                        player.sendMessage(Component.text("● ", NamedTextColor.GREEN)
                                .append(Component.text(name, NamedTextColor.WHITE)));
                    } else {
                        player.sendMessage(Component.text("○ ", NamedTextColor.GRAY)
                                .append(Component.text(name, NamedTextColor.DARK_GRAY)));
                    }
                }
                player.sendMessage(Component.text("在线: " + online + "/" + list.size(),
                        NamedTextColor.AQUA));
                return true;
            }

            case "tp": {
                if (args.length < 2) {
                    player.sendMessage(Component.text("用法: /friend tp <玩家>", NamedTextColor.RED));
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null || !target.isOnline()) {
                    player.sendMessage(Component.text("目标不在线", NamedTextColor.RED));
                    return true;
                }
                if (!friendManager.areFriends(player, target)) {
                    player.sendMessage(Component.text("只有好友才能免费传送", NamedTextColor.RED));
                    return true;
                }
                Location dest = target.getLocation();
                player.teleport(dest);
                player.playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                player.sendMessage(Component.text("✅ 已传送到 ", NamedTextColor.GREEN)
                        .append(Component.text(target.getName(), NamedTextColor.AQUA)));
                return true;
            }

            default:
                showHelp(player);
                return true;
        }
    }

    private void showHelp(Player player) {
        player.sendMessage(Component.text("═════ /friend 帮助 ═════", NamedTextColor.GOLD));
        player.sendMessage(Component.text("/friend add <玩家>", NamedTextColor.AQUA)
                .append(Component.text(" - 添加好友", NamedTextColor.YELLOW)));
        player.sendMessage(Component.text("/friend remove <玩家>", NamedTextColor.AQUA)
                .append(Component.text(" - 删除好友", NamedTextColor.YELLOW)));
        player.sendMessage(Component.text("/friend list", NamedTextColor.AQUA)
                .append(Component.text(" - 好友列表", NamedTextColor.YELLOW)));
        player.sendMessage(Component.text("/friend tp <玩家>", NamedTextColor.AQUA)
                .append(Component.text(" - 传送到好友（免费）", NamedTextColor.YELLOW)));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) return Collections.emptyList();

        if (args.length == 1) {
            return Arrays.asList("add", "remove", "list", "tp").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("add") || sub.equals("tp")) {
                return Bukkit.getOnlinePlayers().stream()
                        .filter(p -> !p.equals(player))
                        .map(Player::getName)
                        .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
            if (sub.equals("remove")) {
                List<String> names = new ArrayList<>();
                for (UUID uuid : friendManager.getFriends(player.getUniqueId())) {
                    String name = Bukkit.getOfflinePlayer(uuid).getName();
                    if (name != null && name.toLowerCase().startsWith(args[1].toLowerCase())) {
                        names.add(name);
                    }
                }
                return names;
            }
        }
        return Collections.emptyList();
    }
}