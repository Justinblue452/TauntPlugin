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
import org.bukkit.Material;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.Locale;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GhostManager {

    private final JavaPlugin plugin;
    private final Random random = new Random();

    private long returnDelayMs;
    private long stealIntervalMinMs;
    private long stealIntervalMaxMs;
    private long playerStealCooldownMs;

    // ★ 使用 CooldownManager 替代 Map<UUID, Long> lastStolen
    private final CooldownManager cooldowns;

    private static final Component GHOST_PREFIX =
            Component.text("[服主的幽灵] ", NamedTextColor.DARK_PURPLE);

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

    private BukkitTask stealTask;

    private static class StolenItem {
        final ItemStack item;
        final int slot;
        StolenItem(ItemStack item, int slot) { this.item = item; this.slot = slot; }
    }

    public GhostManager(JavaPlugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.returnDelayMs = config.getLong("ghost.return-delay-ms", 30000);
        this.stealIntervalMinMs = config.getLong("ghost.steal-interval-min-ms", 60000);
        this.stealIntervalMaxMs = config.getLong("ghost.steal-interval-max-ms", 180000);
        this.playerStealCooldownMs = config.getLong("ghost.player-cooldown-ms", 300000);

        // ★ 创建冷却管理器（过期时间设为最长冷却 2 倍）
        this.cooldowns = new CooldownManager(plugin, playerStealCooldownMs, 0, playerStealCooldownMs * 2);

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
        if (cooldowns != null) cooldowns.shutdown();   // ★
    }

    private void scheduleNextSteal() {
        long delayMs = stealIntervalMinMs
                + (long) (random.nextDouble() * (stealIntervalMaxMs - stealIntervalMinMs));
        stealTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try { trySteal(); } catch (Exception e) {
                plugin.getLogger().warning("幽灵偷窃出错: " + e.getMessage());
            }
            scheduleNextSteal();
        }, delayMs / 50);
    }

    private void trySteal() {
        List<Player> candidates = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            String mode = p.getGameMode().name();
            if (mode.equals("CREATIVE") || mode.equals("SPECTATOR")) continue;

            // ★ 使用 CooldownManager 检查冷却（不记录，后续 stealFrom 才记录）
            long remain = cooldowns.getRemaining(p.getUniqueId(), "steal", playerStealCooldownMs);
            if (remain > 0) continue;

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

        // ★ 标记冷却（无论是否偷到都记录）
        cooldowns.isReady(player.getUniqueId(), "steal", playerStealCooldownMs);

        if (filled.isEmpty()) {
            broadcast(EMPTY_MESSAGES, player, null);
            return;
        }

        int slot = filled.get(random.nextInt(filled.size()));
        ItemStack stolen = inv.getItem(slot).clone();
        inv.setItem(slot, null);

        StolenItem si = new StolenItem(stolen, slot);
        pendingReturns.put(player.getUniqueId(), si);

        broadcast(STEAL_MESSAGES, player, itemComponent(stolen));

        // ★ 成就挂钩（用工具类）
        TauntUtils.increment(plugin, player, "ghost_steals", 1);
        TauntUtils.unlock(plugin, player, AchievementManager.Ach.GHOST_STOLE);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pendingReturns.remove(player.getUniqueId());
            returnTo(player.getUniqueId(), si);
        }, returnDelayMs / 50);
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
        if (stack == null || stack.getType().isAir()) {
            return Component.text("未知物品");
        }

        Component name;

        // ① 优先用自定义名字
        ItemMeta meta = stack.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            Component custom = meta.displayName();
            name = (custom != null) ? custom : buildDefaultItemName(stack);
        } else {
            name = buildDefaultItemName(stack);
        }

        // ② 数量后缀
        if (stack.getAmount() > 1) {
            name = name.append(Component.text(" x" + stack.getAmount()));
        }

        return name;
    }

    /**
     * 用翻译键 + fallback 构建原版物品名。
     *
     * <p>例如钻石剑：{@code Component.translatable("item.minecraft.diamond_sword", "Diamond Sword")}</p>
     * <ul>
     *   <li>中文客户端 → "钻石剑"</li>
     *   <li>英文客户端 → "Diamond Sword"</li>
     *   <li>找不到翻译键 → "Diamond Sword"（fallback）</li>
     * </ul>
     */
    private Component buildDefaultItemName(ItemStack stack) {
        Material material = stack.getType();
        NamespacedKey key = material.getKey();
        String translationKey = "item." + key.getNamespace() + "." + key.getKey();
        String fallback = formatMaterialName(material);
        return Component.translatable(translationKey, fallback);
    }

    /**
     * 把 Material 枚举名格式化为"人类可读"的英文名。
     * 例如 DIAMOND_SWORD → "Diamond Sword"，OAK_LOG → "Oak Log"。
     */
    private String formatMaterialName(Material material) {
        String name = material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        String[] parts = name.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            sb.append(Character.toUpperCase(part.charAt(0)))
                    .append(part.substring(1))
                    .append(' ');
        }
        return sb.toString().trim();
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