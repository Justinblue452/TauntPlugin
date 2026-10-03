package com.example.tauntplugin;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
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
    private final WandManager wandManager;

    public TauntListener(JavaPlugin plugin, TauntManager tauntManager,
                         GhostManager ghostManager, GrindManager grindManager,
                         FriendManager friendManager, WandManager wandManager) {
        this.plugin = plugin;
        this.tauntManager = tauntManager;
        this.ghostManager = ghostManager;
        this.grindManager = grindManager;
        this.friendManager = friendManager;
        this.wandManager = wandManager;
    }

    // ══════════════════════════════════════════════
    //  聊天
    // ══════════════════════════════════════════════

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String plain = PlainTextComponentSerializer.plainText().serialize(event.message());

        // 命令不会走这个事件，但保险起见保留判断
        if (plain.startsWith("/")) return;

        // ① 无声无息：禁止普通聊天
        if (wandManager != null && wandManager.isSilenced(player.getUniqueId())) {
            event.setCancelled(true);
            // ★ AsyncChatEvent 是异步的，sendActionBar 必须回主线程
            Bukkit.getScheduler().runTask(plugin, () ->
                    player.sendActionBar(Component.text("🔇 你被无声无息封住了声音",
                            NamedTextColor.DARK_GRAY)));
            return;
        }

        // ② 锁舌封喉：禁止所有聊天
        if (wandManager != null && wandManager.isMuted(player.getUniqueId())) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () ->
                    player.sendActionBar(Component.text("👅 你的舌头被黏住了，无法说话",
                            NamedTextColor.DARK_PURPLE)));
            return;
        }

        tauntManager.taunt(player, "chat", (Component) null);
    }

    // ══════════════════════════════════════════════
    //  命令（锁舌封喉期间禁止执行命令）
    //  不需要此限制可整段删除
    // ══════════════════════════════════════════════

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (wandManager == null) return;

        Player player = event.getPlayer();
        if (!wandManager.isMuted(player.getUniqueId())) return;

        event.setCancelled(true);
        player.sendActionBar(Component.text("👅 你的舌头被黏住了，无法说话",
                NamedTextColor.DARK_PURPLE));
    }

    // ══════════════════════════════════════════════
    //  移动
    // ══════════════════════════════════════════════

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedBlock()) return;
        tauntManager.taunt(event.getPlayer(), "move", (Component) null);
    }

    // ══════════════════════════════════════════════
    //  方块
    // ══════════════════════════════════════════════

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (grindManager != null) grindManager.addPoint(player);
        Component blockName = TauntManager.blockName(event.getBlock());
        tauntManager.taunt(player, "break", blockName);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (grindManager != null) grindManager.addPoint(player);
        Component blockName = TauntManager.blockName(event.getBlock());
        tauntManager.taunt(player, "place", blockName);
    }

    // ══════════════════════════════════════════════
    //  死亡
    // ══════════════════════════════════════════════

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        String cause = event.getDamageSource().getDamageType().getKey().getKey();
        Component causeComp = Component.translatable("death.attack." + cause);

        if (tauntManager.isOwner(player)) {
            tauntManager.ownerDeath(player, causeComp);
            return;
        }
        tauntManager.taunt(player, "death", causeComp);
    }

    // ══════════════════════════════════════════════
    //  加入 / 离开
    // ══════════════════════════════════════════════

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (tauntManager.isOwner(player)) {
            tauntManager.welcomeOwner(player);
        } else {
            tauntManager.taunt(player, "join", (Component) null);
        }

        if (ghostManager != null) ghostManager.onPlayerJoin(player);

        if (grindManager != null) {
            plugin.getServer().getScheduler().runTaskLater(plugin,
                    () -> grindManager.updatePlayer(player), 5L);
        }

        if (friendManager != null) friendManager.onPlayerJoin(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        tauntManager.taunt(event.getPlayer(), "quit", (Component) null);
        if (grindManager != null) grindManager.onPlayerQuit(event.getPlayer());
        if (friendManager != null) friendManager.onPlayerQuit(event.getPlayer());
    }

    // ══════════════════════════════════════════════
    //  战斗
    // ══════════════════════════════════════════════

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        Entity target = event.getEntity();
        if (!(target instanceof LivingEntity)) return;
        Component name = TauntManager.entityName(target);
        tauntManager.taunt(player, "attack", name);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFallDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            tauntManager.taunt(player, "fall", (Component) null);
        }
    }

    // ══════════════════════════════════════════════
    //  生活行为
    // ══════════════════════════════════════════════

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getFoodLevel() > player.getFoodLevel()) {
            tauntManager.taunt(player, "eat", (Component) null);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Component context = event.getClickedBlock() != null
                ? TauntManager.blockName(event.getClickedBlock())
                : Component.text("空气");
        tauntManager.taunt(event.getPlayer(), "interact", context);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            tauntManager.taunt(event.getPlayer(), "fish", (Component) null);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onToggleSneak(PlayerToggleSneakEvent event) {
        if (event.isSneaking()) {
            tauntManager.taunt(event.getPlayer(), "sneak", (Component) null);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLevelChange(PlayerLevelChangeEvent event) {
        if (event.getNewLevel() > event.getOldLevel()) {
            Component level = Component.text(String.valueOf(event.getNewLevel()));
            tauntManager.taunt(event.getPlayer(), "levelup", level);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTeleport(PlayerTeleportEvent event) {
        tauntManager.taunt(event.getPlayer(), "teleport", (Component) null);
    }
}