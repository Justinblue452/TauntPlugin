package com.example.tauntplugin;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Random;

/**
 * 猜数字：
 * - 每 5 分钟自动开启一局
 * - 服务器随机想一个 1~100 的数
 * - 玩家在聊天栏直接输入数字即可猜
 * - 猜中获奖（默认给肝度）
 */
public class GuessNumberManager implements Listener {

    private final JavaPlugin plugin;
    private final GrindManager grindManager;
    private final Random random = new Random();

    // ==================== 参数 ====================
    private static final long ROUND_INTERVAL_MS = 300_000L;   // 5 分钟一局
    private static final long ROUND_DURATION_MS = 180_000L;   // 每局 3 分钟
    private static final int MIN_NUMBER = 1;
    private static final int MAX_NUMBER = 100;
    private static final int REWARD_GRIND = 100;              // 猜中奖励肝度

    // ==================== 状态 ====================
    private volatile boolean active = false;
    private volatile int target = 0;
    private volatile long endTime = 0;
    private BukkitTask roundTask;

    public GuessNumberManager(JavaPlugin plugin, GrindManager grindManager) {
        this.plugin = plugin;
        this.grindManager = grindManager;
        startSchedule();
    }

    public void shutdown() {
        if (roundTask != null) roundTask.cancel();
        active = false;
    }

    private void startSchedule() {
        // 插件启动后 30 秒开第一局，之后每 5 分钟一局
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            startRound();
            roundTask = Bukkit.getScheduler().runTaskTimer(plugin,
                    this::startRound, ROUND_INTERVAL_MS / 50, ROUND_INTERVAL_MS / 50);
        }, 600L);
    }

    private void startRound() {
        if (active) return;

        active = true;
        target = random.nextInt(MAX_NUMBER - MIN_NUMBER + 1) + MIN_NUMBER;
        endTime = System.currentTimeMillis() + ROUND_DURATION_MS;

        Bukkit.getServer().broadcast(Component.text("═══════════════════════", NamedTextColor.GOLD));
        Bukkit.getServer().broadcast(Component.text("🎲 猜数字游戏开始！", NamedTextColor.YELLOW));
        Bukkit.getServer().broadcast(Component.text("我想了一个 " + MIN_NUMBER + "~" + MAX_NUMBER
                + " 之间的数字", NamedTextColor.AQUA));
        Bukkit.getServer().broadcast(Component.text("在聊天栏直接输入数字即可猜，猜中奖励 "
                + REWARD_GRIND + " 肝度", NamedTextColor.GREEN));
        Bukkit.getServer().broadcast(Component.text("限时 3 分钟，加油！", NamedTextColor.GRAY));
        Bukkit.getServer().broadcast(Component.text("═══════════════════════", NamedTextColor.GOLD));

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.5f);
        }

        // 到时结束
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (active) endRound(false);
        }, ROUND_DURATION_MS / 50);
    }

    private void endRound(boolean guessed) {
        if (!active) return;
        active = false;

        if (!guessed) {
            Bukkit.getServer().broadcast(Component.text("⏰ 猜数字游戏结束！答案是 ", NamedTextColor.RED)
                    .append(Component.text(String.valueOf(target), NamedTextColor.GOLD)));
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            }
        }
    }

    // ==================== 聊天监听 ====================

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!active) return;

        Player player = event.getPlayer();
        String msg = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();

        if (msg.startsWith("/")) return;

        // 尝试解析为数字
        int guess;
        try {
            guess = Integer.parseInt(msg);
        } catch (NumberFormatException e) {
            return; // 不是数字，不处理
        }

        // 范围检查
        if (guess < MIN_NUMBER || guess > MAX_NUMBER) {
            player.sendMessage(Component.text("数字必须在 " + MIN_NUMBER + "~" + MAX_NUMBER
                    + " 之间", NamedTextColor.RED));
            return;
        }

        // 主线程处理
        final int finalGuess = guess;
        Bukkit.getScheduler().runTask(plugin, () -> handleGuess(player, finalGuess));
    }

    private void handleGuess(Player player, int guess) {
        if (!active) return;

        if (guess == target) {
            // 猜中
            player.sendMessage(Component.text("🎉 恭喜你猜中了！答案是 " + target, NamedTextColor.GOLD));
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);

            // 发奖励
            grindManager.addPointsDirect(player, REWARD_GRIND);

            Bukkit.getServer().broadcast(Component.text("🎉 ", NamedTextColor.GOLD)
                    .append(Component.text(player.getName(), NamedTextColor.AQUA))
                    .append(Component.text(" 猜中了数字 " + target + "，获得 "
                            + REWARD_GRIND + " 肝度奖励！", NamedTextColor.GREEN)));

            active = false;
        } else if (guess < target) {
            player.sendMessage(Component.text("⬆ 太小了，再大一点", NamedTextColor.YELLOW));
        } else {
            player.sendMessage(Component.text("⬇ 太大了，再小一点", NamedTextColor.YELLOW));
        }
    }
}