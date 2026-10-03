package com.example.tauntplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class WishWellManager implements Listener {

    private final JavaPlugin plugin;
    private final MessageManager messages;
    private final WandManager wandManager;   // ★ 新增（可能为 null）
    private final Random random = new Random();
    private final CooldownManager cooldowns;

    private final long cooldownMs;
    private final long reforgeCooldownMs;    // ★ 新增

    private static final int MAX_SCAN = 4;
    private static final int MIN_SIZE = 3;
    private static final int MAX_SIZE = 9;

    // ═══════════════════════════════════════════════
    //  奖励池
    // ═══════════════════════════════════════════════

    private static final List<Material> SMALL_REWARDS = List.of(
            Material.BREAD, Material.APPLE, Material.TORCH,
            Material.ARROW, Material.STRING, Material.BONE,
            Material.LEATHER, Material.WHEAT, Material.WHEAT_SEEDS
    );

    private static final List<Material> MEDIUM_REWARDS = List.of(
            Material.IRON_INGOT, Material.GOLD_INGOT, Material.IRON_SWORD,
            Material.IRON_HELMET, Material.GOLDEN_APPLE, Material.ENDER_PEARL,
            Material.BLAZE_POWDER, Material.EXPERIENCE_BOTTLE, Material.BOOK
    );

    private static final List<Material> LARGE_REWARDS = List.of(
            Material.DIAMOND, Material.DIAMOND_SWORD, Material.DIAMOND_CHESTPLATE,
            Material.ENCHANTED_BOOK, Material.GOLDEN_APPLE,
            Material.ENDER_EYE, Material.NETHERITE_SCRAP, Material.TOTEM_OF_UNDYING
    );

    private static final List<Material> LEGENDARY_REWARDS = List.of(
            Material.NETHERITE_INGOT, Material.NETHERITE_CHESTPLATE,
            Material.ELYTRA, Material.ENCHANTED_GOLDEN_APPLE,
            Material.DRAGON_EGG, Material.NETHER_STAR, Material.BEACON
    );

    private static final List<Material> JUNK_REWARDS = List.of(
            Material.DIRT, Material.ROTTEN_FLESH, Material.POISONOUS_POTATO,
            Material.SLIME_BALL, Material.BONE_MEAL, Material.COAL
    );

    private record DrawResult(double smallChance, double mediumChance,
                              double largeChance, double legendChance,
                              double junkChance, double punishChance) {}

    // ═══════════════════════════════════════════════
    //  构造 / 关闭
    // ═══════════════════════════════════════════════

    /**
     * @param wandManager 用于重铸魔杖属性；如果为 null 则禁用重铸功能
     */
    public WishWellManager(JavaPlugin plugin, MessageManager messages,
                           ConfigManager config, WandManager wandManager) {
        this.plugin = plugin;
        this.messages = messages;
        this.wandManager = wandManager;
        this.cooldownMs = config.getLong("wish-well.cooldown-ms", 3000);
        this.reforgeCooldownMs = config.getLong("wish-well.reforge-cooldown-ms", 30_000L);
        this.cooldowns = new CooldownManager(plugin, cooldownMs, 0);
        plugin.getLogger().info("[许愿井] 已启用"
                + (wandManager != null ? "（含魔杖重铸）" : ""));
    }

    public void shutdown() {
        if (cooldowns != null) cooldowns.shutdown();
    }

    // ═══════════════════════════════════════════════
    //  事件
    // ═══════════════════════════════════════════════

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) return;

        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();

        // ═══════════════════════════════════════════
        //  ★ 优先处理：魔杖重铸
        // ═══════════════════════════════════════════
        if (wandManager != null && wandManager.isWand(hand)) {
            Block clicked = event.getClickedBlock();
            if (clicked == null || (clicked.getType() != Material.WATER
                    && clicked.getType() != Material.WATER_CAULDRON)) {
                clicked = rayTraceForWater(player);
            }
            if (clicked == null) return;

            Material clickedType = clicked.getType();
            Location wellLoc = null;

            if (clickedType == Material.WATER_CAULDRON) {
                wellLoc = clicked.getLocation().add(0.5, 0.9, 0.5);
            } else if (clickedType == Material.WATER) {
                wellLoc = detectWishWell(clicked, player);
            }
            if (wellLoc == null) return;

            event.setCancelled(true);
            performWandReforge(player, wellLoc);
            return;
        }

        // ═══════════════════════════════════════════
        //  原有逻辑：金属许愿
        // ═══════════════════════════════════════════
        if (!isMetal(hand.getType())) return;

        Block clicked = event.getClickedBlock();
        if (clicked == null || (clicked.getType() != Material.WATER
                && clicked.getType() != Material.WATER_CAULDRON)) {
            clicked = rayTraceForWater(player);
        }
        if (clicked == null) return;

        Material clickedType = clicked.getType();
        Location wellLoc = null;

        if (clickedType == Material.WATER_CAULDRON) {
            wellLoc = clicked.getLocation().add(0.5, 0.9, 0.5);
        } else if (clickedType == Material.WATER) {
            wellLoc = detectWishWell(clicked, player);
        }
        if (wellLoc == null) return;

        event.setCancelled(true);

        if (!cooldowns.isReady(player.getUniqueId(), "wish", cooldownMs)) {
            long remainMs = cooldowns.getRemaining(player.getUniqueId(), "wish", cooldownMs);
            player.sendActionBar(Component.text("✦ 许愿井需要 " +
                            String.format("%.1f", remainMs / 1000.0) + " 秒后才能再次许愿",
                    NamedTextColor.GRAY));
            return;
        }

        hand.setAmount(hand.getAmount() - 1);
        performWish(player, wellLoc, hand.getType());
    }

    // ═══════════════════════════════════════════════
    //  ★ 魔杖重铸
    // ═══════════════════════════════════════════════

    /**
     * 手持魔杖对着许愿井右键 → 重铸属性。
     * 独立冷却，不消耗物品。
     */
    private void performWandReforge(Player player, Location wellLoc) {
        UUID uuid = player.getUniqueId();

        if (!cooldowns.isReady(uuid, "reforge", reforgeCooldownMs)) {
            long remainMs = cooldowns.getRemaining(uuid, "reforge", reforgeCooldownMs);
            player.sendActionBar(Component.text(
                    "✦ 许愿井需要 " + String.format("%.1f", remainMs / 1000.0)
                            + " 秒后才能再次重铸魔杖",
                    NamedTextColor.GRAY));
            return;
        }

        ItemStack oldWand = player.getInventory().getItemInMainHand();
        ItemStack newWand = wandManager.reforgeWand(oldWand);
        if (newWand == null) {
            player.sendActionBar(Component.text("✦ 这不是魔杖", NamedTextColor.RED));
            return;
        }

        // 替换主手物品
        player.getInventory().setItemInMainHand(newWand);

        // ═══ 特效 ═══
        World world = wellLoc.getWorld();
        if (world != null) {
            world.playSound(wellLoc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.2f);
            world.playSound(wellLoc, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);
            world.playSound(wellLoc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.8f);

            TauntUtils.spawnParticleSafe(world, Particle.ENCHANT,
                    wellLoc.getX(), wellLoc.getY() + 0.5, wellLoc.getZ(),
                    80, 0.8, 0.8, 0.8, 1.0);
            TauntUtils.spawnParticleSafe(world, Particle.WITCH,
                    wellLoc.getX(), wellLoc.getY() + 0.5, wellLoc.getZ(),
                    30, 0.5, 0.5, 0.5, 0.1);
            TauntUtils.spawnParticleSafe(world, Particle.END_ROD,
                    wellLoc.getX(), wellLoc.getY() + 0.5, wellLoc.getZ(),
                    20, 0.4, 0.6, 0.4, 0.05);
        }

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
        player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_RETURN, 0.8f, 1.2f);

        // ═══ 消息 ═══
        player.sendMessage(Component.text("═══════════════════════", NamedTextColor.GOLD));
        player.sendMessage(Component.text("✦ 许愿井重铸了你的魔杖 ✦", NamedTextColor.GOLD));
        player.sendMessage(Component.text("新的属性已在物品信息中显示", NamedTextColor.GRAY));
        player.sendMessage(Component.text("═══════════════════════", NamedTextColor.GOLD));

        player.sendActionBar(Component.text("✦ 魔杖属性已重铸！", NamedTextColor.LIGHT_PURPLE));

        // 成就计数
        TauntUtils.increment(plugin, player, "wands_reforged", 1);
    }

    // ═══════════════════════════════════════════════
    //  原有：金属许愿逻辑
    // ═══════════════════════════════════════════════

    private Block rayTraceForWater(Player player) {
        try {
            RayTraceResult result = player.rayTraceBlocks(6.0);
            if (result != null && result.getHitBlock() != null) {
                Block hit = result.getHitBlock();
                if (hit.getType() == Material.WATER
                        || hit.getType() == Material.WATER_CAULDRON) {
                    return hit;
                }
                for (int i = 1; i <= 2; i++) {
                    Block near = hit.getRelative(result.getHitBlockFace() != null
                            ? result.getHitBlockFace()
                            : org.bukkit.block.BlockFace.UP);
                    if (near.getType() == Material.WATER
                            || near.getType() == Material.WATER_CAULDRON) {
                        return near;
                    }
                    hit = near;
                }
            }
        } catch (Throwable ignored) {}

        Block below = player.getLocation().getBlock().getRelative(0, -1, 0);
        if (below.getType() == Material.WATER) return below;

        Vector dir = player.getEyeLocation().getDirection();
        Location checkLoc = player.getEyeLocation().clone();
        for (double d = 0.5; d <= 5; d += 0.5) {
            Location test = checkLoc.clone().add(dir.clone().multiply(d));
            Block b = test.getBlock();
            if (b.getType() == Material.WATER
                    || b.getType() == Material.WATER_CAULDRON) {
                return b;
            }
        }
        return null;
    }

    private Location detectWishWell(Block waterBlock, Player player) {
        World world = waterBlock.getWorld();
        int cx = waterBlock.getX();
        int cy = waterBlock.getY();
        int cz = waterBlock.getZ();

        Block below = world.getBlockAt(cx, cy - 1, cz);
        if (!below.getType().isSolid()) return null;

        int left = scanWater(world, cx, cy, cz, -1, 0);
        int right = scanWater(world, cx, cy, cz, 1, 0);
        int back = scanWater(world, cx, cy, cz, 0, -1);
        int front = scanWater(world, cx, cy, cz, 0, 1);

        int minX = cx - left;
        int maxX = cx + right;
        int minZ = cz - back;
        int maxZ = cz + front;

        int width = maxX - minX + 1;
        int depth = maxZ - minZ + 1;

        if (width != depth) return null;
        if (width < MIN_SIZE || width > MAX_SIZE) return null;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (world.getBlockAt(x, cy, z).getType() != Material.WATER) return null;
            }
        }

        for (int x = minX; x <= maxX; x++) {
            if (!isWellWall(world.getBlockAt(x, cy, minZ - 1))) return null;
        }
        for (int x = minX; x <= maxX; x++) {
            if (!isWellWall(world.getBlockAt(x, cy, maxZ + 1))) return null;
        }
        for (int z = minZ; z <= maxZ; z++) {
            if (!isWellWall(world.getBlockAt(minX - 1, cy, z))) return null;
        }
        for (int z = minZ; z <= maxZ; z++) {
            if (!isWellWall(world.getBlockAt(maxX + 1, cy, z))) return null;
        }

        if (width >= 5 && player != null) {
            TauntUtils.unlock(plugin, player, AchievementManager.Ach.WELL_BUILDER);
        }

        double centerX = (minX + maxX) / 2.0 + 0.5;
        double centerZ = (minZ + maxZ) / 2.0 + 0.5;
        return new Location(world, centerX, cy + 0.5, centerZ);
    }

    private int scanWater(World world, int x, int y, int z, int dx, int dz) {
        int count = 0;
        for (int i = 1; i <= MAX_SCAN; i++) {
            Block b = world.getBlockAt(x + dx * i, y, z + dz * i);
            if (b.getType() == Material.WATER) count++;
            else break;
        }
        return count;
    }

    private boolean isWellWall(Block block) {
        Material m = block.getType();
        if (!m.isSolid()) return false;
        if (m == Material.WATER) return false;

        String name = m.name();
        if (name.contains("LEAVES")) return false;
        if (name.contains("LOG")) return false;
        if (name.contains("WOOD")) return false;
        if (name.contains("PLANKS")) return false;
        if (name.contains("DIRT")) return false;
        if (name.contains("GRASS_BLOCK")) return false;
        if (name.contains("SAND") && !name.contains("SANDSTONE")) return false;
        if (name.contains("GRAVEL")) return false;
        if (name.contains("SNOW")) return false;
        if (name.contains("CACTUS")) return false;
        if (name.contains("BAMBOO")) return false;
        if (name.contains("HAY")) return false;
        if (name.contains("SPONGE")) return false;
        if (name.contains("WOOL")) return false;
        if (name.contains("CARPET")) return false;
        if (name.contains("MOSS")) return false;
        return true;
    }

    private boolean isMetal(Material m) {
        return switch (m) {
            case GOLD_NUGGET, IRON_NUGGET,
                 IRON_INGOT, GOLD_INGOT, COPPER_INGOT,
                 DIAMOND, NETHERITE_INGOT, COAL, CHARCOAL -> true;
            default -> false;
        };
    }

    // ═══════════════════════════════════════════════
    //  许愿核心
    // ═══════════════════════════════════════════════

    private void performWish(Player player, Location wellLoc, Material metal) {
        World world = wellLoc.getWorld();
        if (world != null) {
            world.playSound(wellLoc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
            world.playSound(wellLoc, Sound.BLOCK_WATER_AMBIENT, 1.0f, 1.2f);
            TauntUtils.spawnParticleSafe(world, Particle.SPLASH,
                    wellLoc.getX(), wellLoc.getY(), wellLoc.getZ(),
                    20, 0.3, 0.2, 0.3, 0.05);
            TauntUtils.spawnParticleSafe(world, Particle.ENCHANT,
                    wellLoc.getX(), wellLoc.getY() + 0.5, wellLoc.getZ(),
                    15, 0.3, 0.3, 0.3, 0.5);
        }

        TauntUtils.increment(plugin, player, "wishes", 1);
        TauntUtils.unlock(plugin, player, AchievementManager.Ach.FIRST_WISH);

        DrawResult draw = getDrawResult(metal);
        double roll = random.nextDouble();
        double cum = 0;

        cum += draw.legendChance();
        if (roll < cum) {
            grantReward(player, wellLoc, LEGENDARY_REWARDS, "传说", NamedTextColor.GOLD, true);
            return;
        }
        cum += draw.largeChance();
        if (roll < cum) {
            grantReward(player, wellLoc, LARGE_REWARDS, "稀有", NamedTextColor.LIGHT_PURPLE, true);
            return;
        }
        cum += draw.mediumChance();
        if (roll < cum) {
            grantReward(player, wellLoc, MEDIUM_REWARDS, "优良", NamedTextColor.AQUA, false);
            return;
        }
        cum += draw.smallChance();
        if (roll < cum) {
            grantReward(player, wellLoc, SMALL_REWARDS, "普通", NamedTextColor.WHITE, false);
            return;
        }
        cum += draw.junkChance();
        if (roll < cum) {
            grantReward(player, wellLoc, JUNK_REWARDS, "垃圾", NamedTextColor.GRAY, false);
            return;
        }

        grantPunishment(player, wellLoc);
    }

    private DrawResult getDrawResult(Material metal) {
        return switch (metal) {
            case GOLD_NUGGET -> new DrawResult(0.50, 0.10, 0.02, 0.00, 0.30, 0.08);
            case IRON_NUGGET -> new DrawResult(0.40, 0.08, 0.02, 0.00, 0.25, 0.25);
            case COPPER_INGOT -> new DrawResult(0.30, 0.05, 0.00, 0.00, 0.40, 0.25);
            case IRON_INGOT -> new DrawResult(0.30, 0.15, 0.05, 0.00, 0.25, 0.25);
            case COAL, CHARCOAL -> new DrawResult(0.05, 0.00, 0.00, 0.00, 0.35, 0.60);
            case GOLD_INGOT -> new DrawResult(0.20, 0.45, 0.15, 0.03, 0.12, 0.05);
            case DIAMOND -> new DrawResult(0.05, 0.20, 0.55, 0.15, 0.05, 0.00);
            case NETHERITE_INGOT -> new DrawResult(0.00, 0.05, 0.25, 0.68, 0.02, 0.00);
            default -> new DrawResult(0.50, 0.10, 0.00, 0.00, 0.30, 0.10);
        };
    }

    // ═══════════════════════════════════════════════
    //  奖励发放
    // ═══════════════════════════════════════════════

    private void grantReward(Player player, Location wellLoc,
                             List<Material> pool, String quality,
                             NamedTextColor color, boolean broadcast) {
        Material reward = pool.get(random.nextInt(pool.size()));
        int amount = 1;
        if (quality.equals("普通")) amount = 1 + random.nextInt(3);
        else if (quality.equals("优良")) amount = 1 + random.nextInt(2);

        ItemStack stack = new ItemStack(reward, amount);
        startFlyingItem(wellLoc, player, stack);

        player.sendActionBar(Component.text("✦ 许愿井涌出 ", NamedTextColor.GOLD)
                .append(Component.text("[" + quality + "] ", color))
                .append(Component.text(formatMaterialName(reward), NamedTextColor.YELLOW)));

        player.playSound(player.getLocation(),
                quality.equals("传说") || quality.equals("稀有")
                        ? Sound.UI_TOAST_CHALLENGE_COMPLETE
                        : Sound.BLOCK_NOTE_BLOCK_PLING,
                1.0f, quality.equals("传说") ? 1.5f : 1.0f);

        if (broadcast) {
            Component bc = Component.text("✦ ", NamedTextColor.GOLD)
                    .append(Component.text(player.getName(), NamedTextColor.YELLOW))
                    .append(Component.text(" 从许愿井中获得了 ", NamedTextColor.GRAY))
                    .append(Component.text("[" + quality + "] ", color))
                    .append(Component.text(formatMaterialName(reward), NamedTextColor.WHITE))
                    .append(amount > 1 ? Component.text(" x" + amount, NamedTextColor.GRAY)
                            : Component.empty());
            plugin.getServer().broadcast(bc);
        }

        if (quality.equals("传说")) {
            TauntUtils.unlock(plugin, player, AchievementManager.Ach.LEGENDARY_WISH);
        } else if (quality.equals("垃圾")) {
            TauntUtils.increment(plugin, player, "junk_wishes", 1);
        }
    }

    private void grantPunishment(Player player, Location wellLoc) {
        int type = random.nextInt(5);
        switch (type) {
            case 0 -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 1, false, true));
                messages.send(player, "wish-well.punishment-poison");
            }
            case 1 -> {
                player.setFireTicks(60);
                messages.send(player, "wish-well.punishment-fire");
            }
            case 2 -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 160, 2, false, true));
                messages.send(player, "wish-well.punishment-slowness");
            }
            case 3 -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 160, 2, false, true));
                messages.send(player, "wish-well.punishment-nausea");
            }
            case 4 -> {
                Vector push = player.getLocation().toVector().subtract(wellLoc.toVector());
                push.setY(0.6);
                if (push.lengthSquared() > 0.01) push.normalize().multiply(1.5);
                player.setVelocity(push);
                messages.send(player, "wish-well.punishment-knockback");
            }
        }
        World world = wellLoc.getWorld();
        if (world != null) {
            TauntUtils.spawnParticleSafe(world, Particle.LARGE_SMOKE,
                    wellLoc.getX(), wellLoc.getY(), wellLoc.getZ(),
                    20, 0.3, 0.3, 0.3, 0.05);
            world.playSound(wellLoc, Sound.ENTITY_BLAZE_HURT, 1.0f, 0.5f);
        }
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
    }

    // ═══════════════════════════════════════════════
    //  物品飞向玩家
    // ═══════════════════════════════════════════════

    private void startFlyingItem(Location wellLoc, Player player, ItemStack stack) {
        World world = wellLoc.getWorld();
        if (world == null) return;

        Location spawnLoc = wellLoc.clone().add(0, 0.5, 0);
        Item itemEntity = world.dropItem(spawnLoc, stack);
        itemEntity.setPickupDelay(100);
        itemEntity.setCanMobPickup(false);
        itemEntity.setGravity(true);
        itemEntity.setVelocity(new Vector(0, 0.3, 0));

        final UUID playerUUID = player.getUniqueId();
        final long startTime = System.currentTimeMillis();
        final long MAX_FLIGHT_MS = 6_000L;
        final int[] ticks = {0};
        final BukkitTask[] taskHolder = new BukkitTask[1];

        taskHolder[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            ticks[0]++;

            if (!itemEntity.isValid() || itemEntity.isDead()
                    || System.currentTimeMillis() - startTime > MAX_FLIGHT_MS) {
                taskHolder[0].cancel();
                return;
            }

            Player p = plugin.getServer().getPlayer(playerUUID);
            if (p == null || !p.isOnline()) {
                taskHolder[0].cancel();
                return;
            }

            Location itemLoc = itemEntity.getLocation();
            Location playerLoc = p.getLocation().add(0, 1.0, 0);

            double dx = playerLoc.getX() - itemLoc.getX();
            double dy = playerLoc.getY() - itemLoc.getY();
            double dz = playerLoc.getZ() - itemLoc.getZ();
            double distSq = dx * dx + dy * dy + dz * dz;

            if (distSq < 2.25) {
                ItemStack picked = itemEntity.getItemStack();
                itemEntity.remove();
                Map<Integer, ItemStack> leftover = p.getInventory().addItem(picked);
                if (!leftover.isEmpty()) {
                    for (ItemStack rest : leftover.values()) {
                        p.getWorld().dropItemNaturally(p.getLocation(), rest);
                    }
                } else {
                    p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.2f);
                }
                taskHolder[0].cancel();
                return;
            }

            double dist = Math.sqrt(distSq);
            double invDist = 1.0 / dist;
            Vector vel = itemEntity.getVelocity();
            double nvx = vel.getX() + dx * invDist * 0.15;
            double nvy = vel.getY() + dy * invDist * 0.15 + 0.02;
            double nvz = vel.getZ() + dz * invDist * 0.15;

            double speedSq = nvx * nvx + nvy * nvy + nvz * nvz;
            if (speedSq > 9.0) {
                double scale = 3.0 / Math.sqrt(speedSq);
                nvx *= scale;
                nvy *= scale;
                nvz *= scale;
            }

            itemEntity.setVelocity(new Vector(nvx, nvy, nvz));
            if (ticks[0] % 3 == 0) {
                TauntUtils.spawnParticleSafe(itemEntity.getWorld(), Particle.END_ROD,
                        itemLoc.getX(), itemLoc.getY() + 0.2, itemLoc.getZ(),
                        1, 0.05, 0.05, 0.05, 0);
            }
        }, 0L, 1L);
    }

    private String formatMaterialName(Material m) {
        String name = m.name().toLowerCase().replace("_", " ");
        String[] parts = name.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            sb.append(Character.toUpperCase(part.charAt(0)))
                    .append(part.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }
}