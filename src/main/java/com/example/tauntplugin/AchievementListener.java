package com.example.tauntplugin;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerJoinEvent;

public class AchievementListener implements Listener {

    private final AchievementManager manager;
    private final TauntPlugin plugin;

    public AchievementListener(TauntPlugin plugin, AchievementManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    // ══════ 挖方块 ══════
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        manager.increment(event.getPlayer(), "blocks_broken", 1);
    }

    // ══════ 放方块 ══════
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        manager.increment(event.getPlayer(), "blocks_placed", 1);
    }

    // ══════ 死亡 ══════
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        manager.increment(player, "deaths", 1);

        // 摔死检查
        if (event.getDamageSource().getDamageType().getKey().getKey().equals("fall")) {
            manager.increment(player, "fall_deaths", 1);
        }
    }

    // ══════ 钓鱼 ══════
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            manager.increment(event.getPlayer(), "fish_caught", 1);
        }
    }

    // ══════ 上线时初始化计数器（社交数等） ══════
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        // 好友数从 FriendManager 同步
        if (plugin.getFriendManager() != null) {
            int count = plugin.getFriendManager().getFriends(event.getPlayer().getUniqueId()).size();
            manager.setCounter(event.getPlayer(), "friends_count", count);
        }
    }
}