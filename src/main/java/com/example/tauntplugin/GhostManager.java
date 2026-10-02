package com.example.tauntplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GhostManager {

    private final JavaPlugin plugin;
    private final Random random = new Random();

    private static final Component GHOST_PREFIX =
            Component.text("[服主的幽灵] ", NamedTextColor.DARK_PURPLE);

    private static final long RETURN_DELAY_MS = 30_000L;
    private static final long STEAL_INTERVAL_MIN_MS = 60_000L;
    private static final long STEAL_INTERVAL_MAX_MS = 180_000L;
    private static final long PLAYER_STEAL_COOLDOWN_MS = 300_000L;

    private static final List<String> STEAL_MESSAGES = List.of(
            "服主的幽灵飘过，顺手拿走了 %player% 的 %context%...",
            "一阵阴风吹过，%player% 的 %context% 不见了！30 秒后归还。",
            "服主的幽灵出现了：%context% 我借走了，%player% 别急。",
            "幽灵来访，%player% 的 %context% 暂时归我了。",
            "嗯？%player% 的 %context% 去哪了？哦，在我这。",
            "%player% 的 %context% 被服主的幽灵拿走了，30 秒后还。",
            "服主的幽灵：%context% 不错，借我玩玩。",
            "幽灵飘过，%player% 的 %context% 不翼而飞。",
            "别找了 %player%，你的 %context% 在我这里。",
            "服主的幽灵顺手牵羊，%player% 的 %context% 遭殃了。"
    );

    private static final List<String> RETURN_MESSAGES = List.of(
            "服主的幽灵悄悄把 %context% 还给了 %player%。",
            "%player% 的 %context% 回来了，幽灵说话算话。",
            "物归原主，%player% 的 %context% 已被归还。",
            "服主的幽灵：%context% 还你，谢谢配合。",
            "%player% 的 %context% 回来了，下次还借。",
            "幽灵归还了 %player% 的 %context%，别担心。",
            "完璧归赵，%player% 的 %context% 还你了。",
            "服主的幽灵信守承诺，%context% 归还 %player%。",
            "%player% 的 %context% 完好无损地回来了。",
            "幽灵把 %context% 放回了 %player% 的背包。"
    );

    private static final List<String> EMPTY_MESSAGES = List.of(
            "服主的幽灵飘到 %player% 身边，发现他穷得叮当响，摇了摇头走了。",
            "%player% 背包比脸还干净，幽灵都懒得动手。",
            "幽灵想偷 %player% 的东西，结果发现没什么可偷的。",
            "服主的幽灵看了 %player% 的背包，叹了口气离开了。",
            "幽灵翻遍了 %player% 的背包，一无所获。"
    );

    private final Map<UUID, StolenItem> pendingReturns = new ConcurrentHashMap<>();
    private final Map<UUID, List<StolenItem>> offlinePending = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastStolen = new ConcurrentHashMap<>();

    private BukkitTask stealTask;

    private static class StolenItem {
        final ItemStack item;
        final int slot;
        StolenItem(ItemStack item, int slot) { this.item = item; this.slot = slot; }
    }

    public GhostManager(JavaPlugin plugin) {
        this.plugin = plugin;
        scheduleNextSteal();
    }

    public void shutdown() {
        if (stealTask != null) stealTask.cancel();
        for (Map.Entry<UUID, StolenItem> entry : pendingReturns.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline()) {
                giveBack(player, entry.getValue());
            } else {
                offlinePending.computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                        .add(entry.getValue());
            }
        }
        pendingReturns.clear();
    }

    private void scheduleNextSteal() {
        long delayMs = STEAL_INTERVAL_MIN_MS
                + (long) (random.nextDouble() * (STEAL_INTERVAL_MAX_MS - STEAL_INTERVAL_MIN_MS));
        stealTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try { trySteal(); } catch (Exception e) {
                plugin.getLogger().warning("幽灵偷窃出错: " + e.getMessage());
            }
            scheduleNextSteal();
        }, delayMs / 50);
    }

    private void trySteal() {
        long now = System.currentTimeMillis();
        List<Player> candidates = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            String mode = p.getGameMode().name();
            if (mode.equals("CREATIVE") || mode.equals("SPECTATOR")) continue;
            Long last = lastStolen.get(p.getUniqueId());
            if (last != null && now - last < PLAYER_STEAL_COOLDOWN_MS) continue;
            candidates.add(p);
        }
        if (candidates.isEmpty()) return;
        stealFrom(candidates.get(random.nextInt(candidates.size())));
    }

    private void stealFrom(Player player) {
        PlayerInventory inv = player.getInventory();
        List<Integer> filled = new ArrayList<>();
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getItem(i);
            if (s != null && !s.getType().isAir()) filled.add(i);
        }

        if (filled.isEmpty()) {
            broadcast(EMPTY_MESSAGES, player, null);
            lastStolen.put(player.getUniqueId(), System.currentTimeMillis());
            return;
        }

        int slot = filled.get(random.nextInt(filled.size()));
        ItemStack stolen = inv.getItem(slot).clone();
        inv.setItem(slot, null);

        StolenItem si = new StolenItem(stolen, slot);
        pendingReturns.put(player.getUniqueId(), si);
        lastStolen.put(player.getUniqueId(), System.currentTimeMillis());

        broadcast(STEAL_MESSAGES, player, itemComponent(stolen));

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pendingReturns.remove(player.getUniqueId());
            returnTo(player.getUniqueId(), si);
        }, RETURN_DELAY_MS / 50);
    }

    private void returnTo(UUID playerId, StolenItem si) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            offlinePending.computeIfAbsent(playerId, k -> new ArrayList<>()).add(si);
            return;
        }
        giveBack(player, si);
        broadcast(RETURN_MESSAGES, player, itemComponent(si.item));
    }

    private void giveBack(Player player, StolenItem si) {
        PlayerInventory inv = player.getInventory();
        ItemStack existing = inv.getItem(si.slot);
        if (existing == null || existing.getType().isAir()) {
            inv.setItem(si.slot, si.item);
        } else {
            Map<Integer, ItemStack> leftover = inv.addItem(si.item);
            for (ItemStack drop : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
        }
    }

    public void onPlayerJoin(Player player) {
        List<StolenItem> pending = offlinePending.remove(player.getUniqueId());
        if (pending == null || pending.isEmpty()) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            for (StolenItem si : pending) {
                giveBack(player, si);
                broadcast(RETURN_MESSAGES, player, itemComponent(si.item));
            }
        }, 40L);
    }

    private Component itemComponent(ItemStack stack) {
        NamespacedKey key = stack.getType().getKey();
        Component name = Component.translatable("item." + key.getNamespace() + "." + key.getKey());
        if (stack.getAmount() > 1) name = name.append(Component.text(" x" + stack.getAmount()));
        return name;
    }

    private void broadcast(List<String> pool, Player player, Component context) {
        if (pool.isEmpty()) return;
        String template = pool.get(random.nextInt(pool.size()));
        Component playerName = Component.text(player.getName(), NamedTextColor.AQUA);
        Component ctx = context != null ? context : Component.empty();
        Component message = Component.text(template, NamedTextColor.LIGHT_PURPLE)
                .replaceText(cfg -> cfg.matchLiteral("%player%").replacement(playerName))
                .replaceText(cfg -> cfg.matchLiteral("%context%").replacement(ctx));
        Bukkit.getServer().broadcast(GHOST_PREFIX.append(message));
    }
}