package com.example.tauntplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowman;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 私人傀儡系统：
 * - 玩家用 /puppet &lt;玩家名&gt; 获得一个带附魔光效的"&lt;玩家名&gt;的傀儡"南瓜头
 * - 用该南瓜头搭建的雪傀儡/铁傀儡会效忠该玩家
 * - 傀儡会自动攻击主人附近的敌对生物
 * - 主人被攻击时，附近的傀儡会反击攻击者
 * - 傀儡默认免疫主人的伤害
 * - 傀儡可配置跟随主人
 */
public class PuppetManager implements Listener, CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final MessageManager messages;

    private final NamespacedKey puppetMasterKey;
    private final NamespacedKey puppetMarkKey;

    /** 玩家刚放置的南瓜位置 → 待处理的傀儡信息 */
    private final Map<Location, PendingPumpkin> pendingPumpkins = new ConcurrentHashMap<>();
    /** 傀儡实体 UUID → 主人 UUID */
    private final Map<UUID, UUID> puppetMasters = new ConcurrentHashMap<>();

    private final double detectRadius;
    private final double protectRadius;
    private final boolean followOwner;
    private final double followDistance;
    private final boolean immunityFromOwner;
    private final int tickInterval;

    private BukkitTask tickTask;
    private BukkitTask cleanupTask;

    private static final String PUPPET_SUFFIX = "的傀儡";
    private static final long PENDING_EXPIRE_MS = 10_000L;

    private record PendingPumpkin(UUID masterId, long createTime) {}

    public PuppetManager(JavaPlugin plugin, ConfigManager config, MessageManager messages) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;

        this.puppetMasterKey = new NamespacedKey(plugin, "puppet_master");
        this.puppetMarkKey = new NamespacedKey(plugin, "puppet_mark");

        this.detectRadius = config.getDouble("puppet.detect-radius", 3.0);
        this.protectRadius = config.getDouble("puppet.protect-radius", 12.0);
        this.followOwner = config.getBoolean("puppet.follow-owner", true);
        this.followDistance = config.getDouble("puppet.follow-distance", 8.0);
        this.immunityFromOwner = config.getBoolean("puppet.immunity-from-owner", true);
        this.tickInterval = config.getInt("puppet.tick-interval", 20);

        restoreExistingPuppets();
        startTasks();
    }

    public void shutdown() {
        if (tickTask != null) tickTask.cancel();
        if (cleanupTask != null) cleanupTask.cancel();
        pendingPumpkins.clear();
        puppetMasters.clear();
    }

    // ═══════════════════════════════════════════════
    //  初始化 / 任务
    // ═══════════════════════════════════════════════

    private void startTasks() {
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickPuppets,
                tickInterval, tickInterval);
        cleanupTask = Bukkit.getScheduler().runTaskTimer(plugin, this::cleanupPending,
                100L, 100L);
    }

    /** 服务器重启后恢复已有傀儡 */
    private void restoreExistingPuppets() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity e : world.getEntities()) {
                if (!(e instanceof Snowman) && !(e instanceof IronGolem)) continue;
                String masterStr = e.getPersistentDataContainer()
                        .get(puppetMasterKey, PersistentDataType.STRING);
                if (masterStr == null) continue;
                try {
                    UUID masterId = UUID.fromString(masterStr);
                    puppetMasters.put(e.getUniqueId(), masterId);
                } catch (Throwable ignored) {}
            }
        }
        if (!puppetMasters.isEmpty()) {
            plugin.getLogger().info("[傀儡] 已恢复 " + puppetMasters.size() + " 个傀儡");
        }
    }

    // ═══════════════════════════════════════════════
    //  命令 /puppet <玩家名>
    // ═══════════════════════════════════════════════

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "common.player-only");
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(Component.text("用法: /puppet <玩家名>", NamedTextColor.RED));
            player.sendMessage(Component.text("  → 获得一个效忠该玩家的傀儡南瓜头",
                    NamedTextColor.GRAY));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().equalsIgnoreCase(args[0])) {
                    target = p;
                    break;
                }
            }
        }
        if (target == null) {
            messages.send(player, "common.player-not-found", Map.of("{name}", args[0]));
            return true;
        }

        // 每个玩家限一个"自己的傀儡南瓜"
        ItemStack pumpkin = createPuppetPumpkin(target.getUniqueId());

        Map<Integer, ItemStack> leftover = player.getInventory().addItem(pumpkin);
        if (!leftover.isEmpty()) {
            for (ItemStack rest : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), rest);
            }
            player.sendMessage(Component.text("✦ 背包已满，南瓜头掉在脚下",
                    NamedTextColor.YELLOW));
        }

        player.sendMessage(Component.text("═══════════════════════", NamedTextColor.GOLD));
        player.sendMessage(Component.text("✦ 私人傀儡南瓜头 ✦", NamedTextColor.GOLD));
        player.sendMessage(Component.text("  主人：", NamedTextColor.GRAY)
                .append(Component.text(target.getName(), NamedTextColor.YELLOW)));
        player.sendMessage(Component.text("  用法：", NamedTextColor.GRAY)
                .append(Component.text("用 2 雪块 / 4 铁块 + 这个南瓜搭建傀儡",
                        NamedTextColor.GREEN)));
        player.sendMessage(Component.text("═══════════════════════", NamedTextColor.GOLD));

        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.5f);

        // 如果 player 和 target 不同，也通知 target
        if (!player.equals(target)) {
            target.sendMessage(Component.text("✦ " + player.getName() + " 获得了效忠你的傀儡南瓜头",
                    NamedTextColor.AQUA));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                    names.add(p.getName());
                }
            }
            return names;
        }
        return Collections.emptyList();
    }

    // ═══════════════════════════════════════════════
    //  南瓜头物品创建
    // ═══════════════════════════════════════════════

    /** 生成一个带附魔光效 + 主人 PDC 的傀儡南瓜头 */
    public ItemStack createPuppetPumpkin(UUID masterId) {
        ItemStack pumpkin = new ItemStack(Material.CARVED_PUMPKIN);
        ItemMeta meta = pumpkin.getItemMeta();
        if (meta == null) return pumpkin;

        String masterName = resolvePlayerName(masterId);

        meta.displayName(Component.text(masterName + PUPPET_SUFFIX, NamedTextColor.GOLD));
        // 附魔光效（隐藏附魔名）
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        meta.getPersistentDataContainer().set(puppetMasterKey,
                PersistentDataType.STRING, masterId.toString());
        meta.getPersistentDataContainer().set(puppetMarkKey,
                PersistentDataType.BYTE, (byte) 1);

        pumpkin.setItemMeta(meta);
        return pumpkin;
    }
    // ═══════════════════════════════════════════════
//  查询 API（供 WandManager 调用）
// ═══════════════════════════════════════════════

    /** 该实体是否是私人傀儡 */
    public boolean isPuppet(UUID entityId) {
        return puppetMasters.containsKey(entityId);
    }

    /** 获取傀儡的主人 UUID（非傀儡返回 null） */
    public UUID getPuppetMaster(UUID entityId) {
        return puppetMasters.get(entityId);
    }

    /** 该实体是否是指定玩家的傀儡 */
    public boolean isOwnedBy(UUID entityId, UUID masterId) {
        UUID owner = puppetMasters.get(entityId);
        return owner != null && owner.equals(masterId);
    }

    private String resolvePlayerName(UUID id) {
        Player p = Bukkit.getPlayer(id);
        if (p != null) return p.getName();
        String n = Bukkit.getOfflinePlayer(id).getName();
        return n != null ? n : "未知";
    }

    /** 从物品里提取主人 UUID（如果没有则返回 null） */
    private UUID extractMaster(ItemStack item) {
        if (item == null || item.getType().isAir()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        String s = meta.getPersistentDataContainer()
                .get(puppetMasterKey, PersistentDataType.STRING);
        if (s == null) return null;
        try {
            return UUID.fromString(s);
        } catch (Throwable t) {
            return null;
        }
    }

    // ═══════════════════════════════════════════════
    //  铁砧重命名支持
    // ═══════════════════════════════════════════════

    /**
     * 玩家在铁砧里把南瓜重命名为 "&lt;玩家名&gt;的傀儡" 时，
     * 自动给结果物品加附魔光效 + PDC。
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        ItemStack result = event.getResult();
        if (result == null || result.getType().isAir()) return;

        Material type = result.getType();
        if (type != Material.PUMPKIN && type != Material.CARVED_PUMPKIN) return;

        ItemMeta meta = result.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return;

        Component dn = meta.displayName();
        if (dn == null) return;

        String plain = PlainTextComponentSerializer.plainText().serialize(dn).trim();
        if (!plain.endsWith(PUPPET_SUFFIX)) return;

        String masterName = plain.substring(0, plain.length() - PUPPET_SUFFIX.length()).trim();
        if (masterName.isEmpty()) return;

        // 只认在线玩家，避免离线 UUID 不可信
        Player master = Bukkit.getPlayerExact(masterName);
        if (master == null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().equalsIgnoreCase(masterName)) {
                    master = p;
                    break;
                }
            }
        }
        if (master == null) return;

        // 应用 PDC + 附魔光效
        meta.displayName(Component.text(master.getName() + PUPPET_SUFFIX, NamedTextColor.GOLD));
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(puppetMasterKey,
                PersistentDataType.STRING, master.getUniqueId().toString());
        meta.getPersistentDataContainer().set(puppetMarkKey,
                PersistentDataType.BYTE, (byte) 1);

        result.setItemMeta(meta);
        event.setResult(result);
    }

    // ═══════════════════════════════════════════════
    //  南瓜放置 / 破坏
    // ═══════════════════════════════════════════════

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Material type = event.getBlock().getType();
        if (type != Material.PUMPKIN && type != Material.CARVED_PUMPKIN) return;

        // 获取放置时手里拿的物品
        ItemStack item;
        if (event.getHand() == EquipmentSlot.OFF_HAND) {
            item = event.getPlayer().getInventory().getItemInOffHand();
        } else {
            item = event.getItemInHand();
        }

        UUID master = extractMaster(item);
        if (master == null) return;

        pendingPumpkins.put(
                event.getBlock().getLocation().clone(),
                new PendingPumpkin(master, System.currentTimeMillis()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        pendingPumpkins.remove(event.getBlock().getLocation());
    }

    // ═══════════════════════════════════════════════
    //  傀儡生成
    // ═══════════════════════════════════════════════

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        if (reason != CreatureSpawnEvent.SpawnReason.BUILD_SNOWMAN
                && reason != CreatureSpawnEvent.SpawnReason.BUILD_IRONGOLEM) return;

        Entity entity = event.getEntity();
        if (!(entity instanceof Snowman) && !(entity instanceof IronGolem)) return;

        Location loc = entity.getLocation();
        Location nearest = null;
        double nearestDistSq = detectRadius * detectRadius;

        // 找最近的待处理南瓜
        for (Location pLoc : pendingPumpkins.keySet()) {
            if (pLoc.getWorld() == null || !pLoc.getWorld().equals(loc.getWorld())) continue;
            double distSq = pLoc.distanceSquared(loc);
            if (distSq < nearestDistSq) {
                nearestDistSq = distSq;
                nearest = pLoc;
            }
        }

        if (nearest == null) return;

        PendingPumpkin pending = pendingPumpkins.remove(nearest);
        if (pending == null) return;

        UUID masterId = pending.masterId();

        // 记录 + PDC
        puppetMasters.put(entity.getUniqueId(), masterId);
        entity.getPersistentDataContainer().set(puppetMasterKey,
                PersistentDataType.STRING, masterId.toString());

        // 设置名字
        String masterName = resolvePlayerName(masterId);
        entity.customName(Component.text(masterName + PUPPET_SUFFIX, NamedTextColor.GOLD));
        entity.setCustomNameVisible(true);

        // 防消失
        if (entity instanceof Mob mob) {
            mob.setRemoveWhenFarAway(false);
            mob.setPersistent(true);
        }

        // 通知主人
        Player master = Bukkit.getPlayer(masterId);
        if (master != null && master.isOnline()) {
            master.sendMessage(Component.text("✦ 你的傀儡已生成！", NamedTextColor.GOLD));
            master.playSound(master.getLocation(), Sound.ENTITY_IRON_GOLEM_REPAIR, 1.0f, 1.5f);
        }
    }

    // ═══════════════════════════════════════════════
    //  主人伤害免疫
    // ═══════════════════════════════════════════════

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPuppetDamaged(EntityDamageByEntityEvent event) {
        if (!immunityFromOwner) return;
        if (!(event.getEntity() instanceof Mob puppet)) return;

        UUID masterId = puppetMasters.get(puppet.getUniqueId());
        if (masterId == null) return;

        if (!(event.getDamager() instanceof Player player)) return;
        if (!masterId.equals(player.getUniqueId())) return;

        event.setCancelled(true);
        player.sendActionBar(Component.text("✦ 这是你的傀儡，无法伤害", NamedTextColor.GRAY));
    }

    // ═══════════════════════════════════════════════
    //  主人被攻击 → 傀儡反击
    // ═══════════════════════════════════════════════

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOwnerDamaged(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player owner)) return;
        if (!(event instanceof EntityDamageByEntityEvent byEntity)) return;

        Entity damager = byEntity.getDamager();
        if (!(damager instanceof LivingEntity attacker)) return;
        if (damager.equals(owner)) return;

        UUID ownerId = owner.getUniqueId();

        for (Map.Entry<UUID, UUID> entry : puppetMasters.entrySet()) {
            if (!entry.getValue().equals(ownerId)) continue;

            Entity puppetEntity = Bukkit.getEntity(entry.getKey());
            if (!(puppetEntity instanceof Mob puppet)) continue;
            if (!puppet.getWorld().equals(owner.getWorld())) continue;
            if (puppet.getLocation().distanceSquared(owner.getLocation())
                    > protectRadius * protectRadius) continue;

            puppet.setTarget(attacker);
        }
    }

    // ═══════════════════════════════════════════════
    //  傀儡 AI tick
    // ═══════════════════════════════════════════════

    private void tickPuppets() {
        if (puppetMasters.isEmpty()) return;

        // 复制快照，避免 ConcurrentModificationException
        Map<UUID, UUID> snapshot = new HashMap<>(puppetMasters);

        for (Map.Entry<UUID, UUID> entry : snapshot.entrySet()) {
            UUID puppetId = entry.getKey();
            UUID masterId = entry.getValue();

            Entity puppetEntity = Bukkit.getEntity(puppetId);
            if (!(puppetEntity instanceof Mob puppet) || !puppet.isValid() || puppet.isDead()) {
                puppetMasters.remove(puppetId);
                continue;
            }

            Player master = Bukkit.getPlayer(masterId);
            if (master == null || !master.isOnline()) continue;
            if (!puppet.getWorld().equals(master.getWorld())) continue;

            // 找主人附近最近的敌对生物
            LivingEntity target = findNearestEnemy(puppet, master);
            if (target != null) {
                // 若当前目标不是这个敌人，则切换
                if (puppet.getTarget() == null || puppet.getTarget().isDead()
                        || !puppet.getTarget().equals(target)) {
                    puppet.setTarget(target);
                }
                continue;
            }

            // 无敌人 → 可选：跟随主人
            if (followOwner) {
                double distSq = puppet.getLocation().distanceSquared(master.getLocation());
                if (distSq > followDistance * followDistance) {
                    Location dest = master.getLocation().clone()
                            .add(master.getLocation().getDirection().setY(0).normalize().multiply(-2));
                    dest.setYaw(master.getLocation().getYaw());
                    puppet.teleport(dest);
                }
            }
        }
    }

    private LivingEntity findNearestEnemy(Mob puppet, Player master) {
        Location center = master.getLocation();
        World world = center.getWorld();
        if (world == null) return null;

        LivingEntity nearest = null;
        double nearestDistSq = protectRadius * protectRadius;

        for (Entity e : world.getNearbyEntities(center,
                protectRadius, protectRadius, protectRadius)) {
            if (!(e instanceof Monster monster)) continue;
            if (!monster.isValid() || monster.isDead()) continue;
            if (monster.equals(puppet)) continue;

            double distSq = monster.getLocation().distanceSquared(center);
            if (distSq < nearestDistSq) {
                nearestDistSq = distSq;
                nearest = monster;
            }
        }
        return nearest;
    }

    private void cleanupPending() {
        long now = System.currentTimeMillis();
        pendingPumpkins.entrySet().removeIf(e ->
                now - e.getValue().createTime() > PENDING_EXPIRE_MS);
    }
}