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

import java.util.Map;
import java.util.Random;

public class GuessNumberManager implements Listener {

    private final JavaPlugin plugin;
    private final MessageManager messages;
    private final GrindManager grindManager;
    private final Random random = new Random();

    private long roundIntervalMs;
    private long roundDurationMs;
    private int minNumber;
    private int maxNumber;
    private int rewardGrind;

    private volatile boolean active = false;
    private volatile int target = 0;
    private volatile long endTime = 0;
    private BukkitTask roundTask;

    public GuessNumberManager(JavaPlugin plugin, MessageManager messages,
                              GrindManager grindManager, ConfigManager config) {
        this.plugin = plugin;
        this.messages = messages;
        this.grindManager = grindManager;

        this.roundIntervalMs = config.getLong("guess-number.round-interval-ms", 300000);
        this.roundDurationMs = config.getLong("guess-number.round-duration-ms", 180000);
        this.minNumber = config.getInt("guess-number.min-number", 1);
        this.maxNumber = config.getInt("guess-number.max-number", 100);
        this.rewardGrind = config.getInt("guess-number.reward-grind", 100);

        startSchedule();
    }

    public void shutdown() {
        if (roundTask != null) roundTask.cancel();
        active = false;
    }

    private void startSchedule() {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            startRound();
            roundTask = Bukkit.getScheduler().runTaskTimer(plugin,
                    this::startRound, roundIntervalMs / 50, roundIntervalMs / 50);
        }, 600L);
    }

    private void startRound() {
        if (active) return;

        active = true;
        target = random.nextInt(maxNumber - minNumber + 1) + minNumber;
        endTime = System.currentTimeMillis() + roundDurationMs;

        // ★ 消息接入
        messages.broadcast("guess-number.header");
        messages.broadcast("guess-number.start");
        messages.broadcast("guess-number.range", Map.of(
                "{min}", String.valueOf(minNumber),
                "{max}", String.valueOf(maxNumber)
        ));
        messages.broadcast("guess-number.hint", Map.of(
                "{reward}", String.valueOf(rewardGrind)
        ));
        messages.broadcast("guess-number.time-limit");
        messages.broadcast("guess-number.footer");

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.5f);
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (active) endRound(false);
        }, roundDurationMs / 50);
    }

    private void endRound(boolean guessed) {
        if (!active) return;
        active = false;

        if (!guessed) {
            messages.broadcast("guess-number.timeout", Map.of(
                    "{number}", String.valueOf(target)
            ));
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!active) return;

        Player player = event.getPlayer();
        String msg = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        if (msg.startsWith("/")) return;

        int guess;
        try {
            guess = Integer.parseInt(msg);
        } catch (NumberFormatException e) {
            return;
        }

        if (guess < minNumber || guess > maxNumber) {
            messages.send(player, "guess-number.out-of-range", Map.of(
                    "{min}", String.valueOf(minNumber),
                    "{max}", String.valueOf(maxNumber)
            ));
            return;
        }

        final int finalGuess = guess;
        Bukkit.getScheduler().runTask(plugin, () -> handleGuess(player, finalGuess));
    }

    private void handleGuess(Player player, int guess) {
        if (!active) return;

        if (guess == target) {
            messages.send(player, "guess-number.win", Map.of(
                    "{number}", String.valueOf(target)
            ));
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);

            if (grindManager != null) {
                grindManager.addPointsDirect(player, rewardGrind);
            }

            messages.broadcast("guess-number.win-broadcast", Map.of(
                    "{player}", player.getName(),
                    "{number}", String.valueOf(target),
                    "{reward}", String.valueOf(rewardGrind)
            ));

            TauntUtils.increment(plugin, player, "guess_wins", 1);
            TauntUtils.unlock(plugin, player, AchievementManager.Ach.FIRST_GUESS_WIN);

            active = false;
        } else if (guess < target) {
            messages.send(player, "guess-number.too-small");
        } else {
            messages.send(player, "guess-number.too-big");
        }
    }
}