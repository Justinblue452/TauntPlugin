package com.example.tauntplugin;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;

public class TauntListener implements Listener {

    private final JavaPlugin plugin;
    private final TauntManager tauntManager;
    private final GhostManager ghostManager;
    private final GrindManager grindManager;
    private final FriendManager friendManager;

    public TauntListener(JavaPlugin plugin, TauntManager tauntManager,
                         GhostManager ghostManager, GrindManager grindManager,
                         FriendManager friendManager) {
        this.plugin = plugin;
        this.tauntManager = tauntManager;
        this.ghostManager = ghostManager;
        this.grindManager = grindManager;
        this.friendManager = friendManager;
    }

    // ---------- 聊天 ----------
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String plain = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (!plain.startsWith("/")) {
            tauntManager.taunt(player, "chat", (Component) null);
        }
    }

    // ---------- 移动 ----------
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedBlock()) return;
        tauntManager.taunt(event.getPlayer(), "move", (Component) null);
    }

    // ---------- 破坏方块 ----------
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        grindManager.addPoint(player);
        Component blockName = TauntManager.blockName(event.getBlock());
        tauntManager.taunt(player, "break", blockName);
    }

    // ---------- 放置方块 ----------
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        grindManager.addPoint(player);
        Component blockName = TauntManager.blockName(event.getBlock());
        tauntManager.taunt(player, "place", blockName);
    }

    // ---------- 死亡 ----------
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        String cause = event.getDamageSource().getDamageType().getKey().getKey();
        Component causeComp = Component.translatable("death.attack." + cause);

        if (TauntManager.isOwner(player)) {
            tauntManager.ownerDeath(player, causeComp);
            return;
        }
        tauntManager.taunt(player, "death", causeComp);
    }

    // ---------- 加入 ----------
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (TauntManager.isOwner(player)) {
            tauntManager.welcomeOwner(player);
        } else {
            tauntManager.taunt(player, "join", (Component) null);
        }

        ghostManager.onPlayerJoin(player);

        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> grindManager.updatePlayer(player), 5L);

        // ★ 好友上线通知
        friendManager.onPlayerJoin(player);
    }

    // ---------- 离开 ----------
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        tauntManager.taunt(event.getPlayer(), "quit", (Component) null);
        grindManager.onPlayerQuit(event.getPlayer());

        // ★ 好友下线通知
        friendManager.onPlayerQuit(event.getPlayer());
    }

    // ---------- 攻击 ----------
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        Entity target = event.getEntity();
        if (!(target instanceof LivingEntity)) return;
        Component name = TauntManager.entityName(target);
        tauntManager.taunt(player, "attack", name);
    }

    // ---------- 摔落 ----------
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFallDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            tauntManager.taunt(player, "fall", (Component) null);
        }
    }

    // ---------- 吃东西 ----------
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getFoodLevel() > player.getFoodLevel()) {
            tauntManager.taunt(player, "eat", (Component) null);
        }
    }

    // ---------- 交互 ----------
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Component context = event.getClickedBlock() != null
                ? TauntManager.blockName(event.getClickedBlock())
                : Component.text("空气");
        tauntManager.taunt(event.getPlayer(), "interact", context);
    }

    // ---------- 钓鱼 ----------
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            tauntManager.taunt(event.getPlayer(), "fish", (Component) null);
        }
    }

    // ---------- 潜行 ----------
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onToggleSneak(PlayerToggleSneakEvent event) {
        if (event.isSneaking()) {
            tauntManager.taunt(event.getPlayer(), "sneak", (Component) null);
        }
    }

    // ---------- 升级 ----------
    @EventHandler(priority = EventPriority.MONITOR)
    public void onLevelChange(PlayerLevelChangeEvent event) {
        if (event.getNewLevel() > event.getOldLevel()) {
            Component level = Component.text(String.valueOf(event.getNewLevel()));
            tauntManager.taunt(event.getPlayer(), "levelup", level);
        }
    }

    // ---------- 传送 ----------
    @EventHandler(priority = EventPriority.MONITOR)
    public void onTeleport(PlayerTeleportEvent event) {
        tauntManager.taunt(event.getPlayer(), "teleport", (Component) null);
    }
}