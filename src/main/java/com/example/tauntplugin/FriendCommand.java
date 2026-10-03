package com.example.tauntplugin;

import net.kyori.adventure.text.Component;
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
    private final MessageManager messages;   // ★ 消息管理器

    public FriendCommand(FriendManager friendManager, MessageManager messages) {
        this.friendManager = friendManager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "common.player-only");
            return true;
        }

        if (args.length == 0) {
            showHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "add": {
                if (args.length < 2) {
                    messages.send(player, "friend.usage-add");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    messages.send(player, "common.player-not-found",
                            Map.of("{name}", args[1]));
                    return true;
                }
                friendManager.addFriend(player, target);
                return true;
            }

            case "remove":
            case "del": {
                if (args.length < 2) {
                    messages.send(player, "friend.usage-remove");
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
                    messages.send(player, "friend.empty");
                    return true;
                }
                messages.send(player, "friend.header");
                int online = 0;
                for (UUID uuid : list) {
                    Player p = Bukkit.getPlayer(uuid);
                    String name = p != null ? p.getName() : Bukkit.getOfflinePlayer(uuid).getName();
                    if (name == null) name = uuid.toString().substring(0, 8);
                    if (p != null && p.isOnline()) {
                        online++;
                        messages.send(player, "friend.online", Map.of("{name}", name));
                    } else {
                        messages.send(player, "friend.offline", Map.of("{name}", name));
                    }
                }
                messages.send(player, "friend.online-count", Map.of(
                        "{online}", String.valueOf(online),
                        "{total}", String.valueOf(list.size())));
                return true;
            }

            case "tp": {
                if (args.length < 2) {
                    messages.send(player, "friend.usage-tp");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null || !target.isOnline()) {
                    messages.send(player, "friend.target-offline");
                    return true;
                }
                if (!friendManager.areFriends(player, target)) {
                    messages.send(player, "friend.teleport-friend-only");
                    return true;
                }
                Location dest = target.getLocation();
                player.teleport(dest);
                player.playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                messages.send(player, "friend.teleport-success",
                        Map.of("{target}", target.getName()));

                // 成就挂钩
                TauntUtils.unlock(friendManager.getPlugin(), player,
                        AchievementManager.Ach.FRIEND_TP);
                return true;
            }

            default:
                showHelp(player);
                return true;
        }
    }

    private void showHelp(Player player) {
        messages.send(player, "friend.help-header");
        messages.send(player, "friend.help-add");
        messages.send(player, "friend.help-remove");
        messages.send(player, "friend.help-list");
        messages.send(player, "friend.help-tp");
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