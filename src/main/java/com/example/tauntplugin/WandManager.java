package com.example.tauntplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.Openable;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class WandManager implements Listener {

    // ═══════════════════════════════════════════════
    //  静态常量
    // ═══════════════════════════════════════════════

    private static final Spell[] SPELLS = Spell.values();
    private static final int SPELL_COUNT = SPELLS.length;
    private static final long SILENCE_CLEANUP_INTERVAL_MS = 30_000L;

    private static final long SHIELD_COOLDOWN_MS = 12_000L;

    private static final float SELF_HEAL_LOOK_DOWN_THRESHOLD = 60f;
    private static final double SELF_HEAL_AMOUNT = 16.0;
    private static final double PROJECTILE_HEAL_AMOUNT = 12.0;

    private static final double REPARO_EXP_PER_DAMAGE = 0.1;
    private static final int REPARO_MIN_EXP = 3;

    private static final int AFFINITY_MAX = 100;
    private static final float REJECTION_EXPLOSION_POWER = 3.0f;

    private static final float BOMBARDA_EXPLOSION_POWER = 3.0f;
    /** 每治疗他人多少次触发 1 级反噬净化 */
    private static final int REDEMPTION_PER_PURGE = 10;

    /** 每次善行净化获得的救赎值 */
    private static final int REDEMPTION_PER_HEAL = 1;

    // ★ 不可饶恕咒惩罚
    private static final int UNFORGIVABLE_HP_LOSS_INTERVAL = 5;
    private static final double UNFORGIVABLE_HP_LOSS = 2.0;
    private static final int MAX_HP_LOSS_COUNT = 7; // 20 - 7*2 = 6 (最低 3 颗心)

    /** 亡灵白名单 */
    private static final Set<EntityType> UNDEAD_TYPES = Set.of(
            EntityType.ZOMBIE,
            EntityType.ZOMBIE_VILLAGER,
            EntityType.HUSK,
            EntityType.DROWNED,
            EntityType.SKELETON,
            EntityType.STRAY,
            EntityType.WITHER_SKELETON,
            EntityType.ZOMBIFIED_PIGLIN,
            EntityType.ZOGLIN,
            EntityType.PHANTOM,
            EntityType.WITHER,
            EntityType.SKELETON_HORSE,
            EntityType.ZOMBIE_HORSE
    );

    /** 可烹饪物品映射表（生 → 熟 / 矿 → 锭 / 其它） */
    private static final Map<Material, Material> SMELT_MAP = buildSmeltMap();

    private static Map<Material, Material> buildSmeltMap() {
        Map<Material, Material> m = new HashMap<>();
        // 生肉 → 熟肉
        m.put(Material.BEEF, Material.COOKED_BEEF);
        m.put(Material.PORKCHOP, Material.COOKED_PORKCHOP);
        m.put(Material.CHICKEN, Material.COOKED_CHICKEN);
        m.put(Material.MUTTON, Material.COOKED_MUTTON);
        m.put(Material.RABBIT, Material.COOKED_RABBIT);
        m.put(Material.COD, Material.COOKED_COD);
        m.put(Material.SALMON, Material.COOKED_SALMON);
        // 矿石 / 原矿 → 锭
        m.put(Material.IRON_ORE, Material.IRON_INGOT);
        m.put(Material.DEEPSLATE_IRON_ORE, Material.IRON_INGOT);
        m.put(Material.RAW_IRON, Material.IRON_INGOT);
        m.put(Material.GOLD_ORE, Material.GOLD_INGOT);
        m.put(Material.DEEPSLATE_GOLD_ORE, Material.GOLD_INGOT);
        m.put(Material.RAW_GOLD, Material.GOLD_INGOT);
        m.put(Material.NETHER_GOLD_ORE, Material.GOLD_INGOT);
        m.put(Material.COPPER_ORE, Material.COPPER_INGOT);
        m.put(Material.DEEPSLATE_COPPER_ORE, Material.COPPER_INGOT);
        m.put(Material.RAW_COPPER, Material.COPPER_INGOT);
        // 食物 / 作物
        m.put(Material.POTATO, Material.BAKED_POTATO);
        m.put(Material.KELP, Material.DRIED_KELP);
        m.put(Material.CHORUS_FRUIT, Material.POPPED_CHORUS_FRUIT);
        m.put(Material.SEA_PICKLE, Material.LIME_DYE);
        m.put(Material.CACTUS, Material.GREEN_DYE);
        // 方块加工
        m.put(Material.SAND, Material.GLASS);
        m.put(Material.RED_SAND, Material.GLASS);
        m.put(Material.COBBLESTONE, Material.STONE);
        m.put(Material.STONE, Material.SMOOTH_STONE);
        m.put(Material.CLAY_BALL, Material.BRICK);
        m.put(Material.CLAY, Material.TERRACOTTA);
        m.put(Material.NETHERRACK, Material.NETHER_BRICK);
        m.put(Material.WET_SPONGE, Material.SPONGE);
        m.put(Material.SANDSTONE, Material.SMOOTH_SANDSTONE);
        m.put(Material.RED_SANDSTONE, Material.SMOOTH_RED_SANDSTONE);
        m.put(Material.QUARTZ_BLOCK, Material.SMOOTH_QUARTZ);
        // 原木 → 木炭
        m.put(Material.OAK_LOG, Material.CHARCOAL);
        m.put(Material.SPRUCE_LOG, Material.CHARCOAL);
        m.put(Material.BIRCH_LOG, Material.CHARCOAL);
        m.put(Material.JUNGLE_LOG, Material.CHARCOAL);
        m.put(Material.ACACIA_LOG, Material.CHARCOAL);
        m.put(Material.DARK_OAK_LOG, Material.CHARCOAL);
        m.put(Material.MANGROVE_LOG, Material.CHARCOAL);
        m.put(Material.CHERRY_LOG, Material.CHARCOAL);
        m.put(Material.OAK_WOOD, Material.CHARCOAL);
        m.put(Material.SPRUCE_WOOD, Material.CHARCOAL);
        m.put(Material.BIRCH_WOOD, Material.CHARCOAL);
        m.put(Material.JUNGLE_WOOD, Material.CHARCOAL);
        m.put(Material.ACACIA_WOOD, Material.CHARCOAL);
        m.put(Material.DARK_OAK_WOOD, Material.CHARCOAL);
        return m;
    }

    // ═══════════════════════════════════════════════
    //  实例字段
    // ═══════════════════════════════════════════════

    private final JavaPlugin plugin;
    private final MessageManager messages;
    private final NamespacedKey wandKey;
    private final NamespacedKey wandStatsKey;
    private final NamespacedKey wandOwnerKey;
    private final NamespacedKey wandAffinityKey;
    private final NamespacedKey projectileKey;
    private final NamespacedKey projectileOwnerKey;
    private final NamespacedKey unforgivableCountKey;
    private final NamespacedKey redemptionKey;

    private final long maxFlightTimeMs;
    private final boolean requireOpForUnforgivable;
    private final int maxActiveProjectilesPerPlayer;
    private final int lumosLightLevel;
    private final long lumosDurationMs;

    private final Map<UUID, Integer> currentSpell = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> usedSpells = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> activeProjectiles = new ConcurrentHashMap<>();
    private final Map<UUID, Long> silenceUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> muteUntil = new ConcurrentHashMap<>();

    private final Set<UUID> shieldActive = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> shieldCooldownUntil = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> shieldParticleTasks = new ConcurrentHashMap<>();

    // ★ 夺魂咒控制的目标
    private final Map<UUID, ImperioTarget> imperioTargets = new ConcurrentHashMap<>();

    private final Set<BukkitTask> activeTasks = ConcurrentHashMap.newKeySet();

    private final CooldownManager cooldowns;

    private static class ImperioTarget {
        final UUID casterId;
        final long endTime;
        ImperioTarget(UUID casterId, long duration) {
            this.casterId = casterId;
            this.endTime = System.currentTimeMillis() + duration;
        }
    }

    // ═══════════════════════════════════════════════
    //  枚举
    // ═══════════════════════════════════════════════

    public enum CastMode { SELF, PROJECTILE, AREA, SHIELD, TARGET_BLOCK }

    public enum TrailStyle {
        STRAIGHT, SPIRAL, HELIX, RING, ORBIT, FLAME, ORB, CASCADE
    }

    public enum SpellCategory {
        ATTACK, PROTECTION, UTILITY, DARK, SPEED, MIND, ALL
    }

    public enum WandWood {
        HOLLY      ("冬青木",   SpellCategory.PROTECTION, "防护类咒语更强"),
        YEW        ("紫杉木",   SpellCategory.DARK,       "黑魔法更强"),
        HAWTHORN   ("山楂木",   SpellCategory.UTILITY,    "辅助类咒语更强"),
        ELDER      ("接骨木",   SpellCategory.ALL,        "所有咒语都有加成"),
        OAK        ("橡木",     SpellCategory.ATTACK,     "攻击类咒语更强"),
        WILLOW     ("柳木",     SpellCategory.UTILITY,    "辅助类咒语更强"),
        BIRCH      ("桦木",     SpellCategory.SPEED,      "敏捷类咒语更强"),
        LAUREL     ("月桂木",   SpellCategory.PROTECTION, "防护类咒语更强"),
        CEDAR      ("雪松木",   SpellCategory.ALL,        "综合型木材"),
        ASH        ("白蜡木",   SpellCategory.ATTACK,     "攻击类咒语更强"),
        VINE       ("葡萄藤木", SpellCategory.UTILITY,    "辅助类咒语更强"),
        CHERRY     ("樱桃木",   SpellCategory.SPEED,      "敏捷类咒语更强"),
        PEAR       ("梨木",     SpellCategory.MIND,       "心灵类咒语更强"),
        WALNUT     ("胡桃木",   SpellCategory.MIND,       "心灵类咒语更强"),
        BLACKTHORN ("荆棘木",   SpellCategory.DARK,       "黑魔法更强");

        public final String display;
        public final SpellCategory category;
        public final String effect;

        WandWood(String display, SpellCategory category, String effect) {
            this.display = display;
            this.category = category;
            this.effect = effect;
        }
    }

    public enum WandCore {
        PHOENIX_FEATHER     ("凤凰羽毛",       SpellCategory.PROTECTION, "施法速度快，射程远"),
        UNICORN_HAIR        ("独角兽毛",       SpellCategory.UTILITY,    "冷却更短"),
        DRAGON_HEARTSTRING  ("龙的心弦",       SpellCategory.ATTACK,     "威力更强"),
        THESTRAL_TAIL_HAIR  ("夜骐尾羽",       SpellCategory.DARK,       "黑魔法威力大增"),
        VEELA_HAIR          ("媚娃头发",       SpellCategory.MIND,       "心灵类效果更持久"),
        THUNDERBIRD_FEATHER ("雷鸟羽毛",       SpellCategory.SPEED,      "施法速度更快"),
        WHITE_RIVER_SPINE   ("怀特河怪背脊刺", SpellCategory.ATTACK,     "击退效果更强"),
        TROLL_WHISKER       ("巨怪胡须",       SpellCategory.ALL,        "综合型杖芯");

        public final String display;
        public final SpellCategory category;
        public final String effect;

        WandCore(String display, SpellCategory category, String effect) {
            this.display = display;
            this.category = category;
            this.effect = effect;
        }
    }

    public enum WandFlexibility {
        BRITTLE  ("易脆",   0.85, 1.30),
        STIFF    ("坚硬",   0.90, 1.20),
        SOLID    ("坚实",   0.95, 1.10),
        SUPPLE   ("柔韧",   1.00, 1.00),
        FLEXIBLE ("易弯曲", 1.10, 0.95),
        SPRINGY  ("弹性",   1.15, 0.90),
        WHIPPY   ("柔软",   1.20, 0.85);

        public final String display;
        public final double cooldownMultiplier;
        public final double damageMultiplier;

        WandFlexibility(String display, double cooldownMultiplier, double damageMultiplier) {
            this.display = display;
            this.cooldownMultiplier = cooldownMultiplier;
            this.damageMultiplier = damageMultiplier;
        }
    }

    public record WandStats(WandWood wood, WandCore core, int lengthInches, WandFlexibility flexibility) {

        public static WandStats random(ThreadLocalRandom rng) {
            WandWood w = WandWood.values()[rng.nextInt(WandWood.values().length)];
            WandCore c = WandCore.values()[rng.nextInt(WandCore.values().length)];
            int len = 8 + rng.nextInt(9);
            WandFlexibility f = WandFlexibility.values()[rng.nextInt(WandFlexibility.values().length)];
            return new WandStats(w, c, len, f);
        }

        public String serialize() {
            return wood.name() + "|" + core.name() + "|" + lengthInches + "|" + flexibility.name();
        }

        public static WandStats deserialize(String s) {
            if (s == null) return null;
            try {
                String[] p = s.split("\\|");
                if (p.length != 4) return null;
                return new WandStats(
                        WandWood.valueOf(p[0]),
                        WandCore.valueOf(p[1]),
                        Integer.parseInt(p[2]),
                        WandFlexibility.valueOf(p[3])
                );
            } catch (Throwable t) {
                return null;
            }
        }

        private static boolean match(SpellCategory affinity, SpellCategory spell) {
            return affinity == SpellCategory.ALL || affinity == spell;
        }

        public double damageMultiplier(Spell spell) {
            double m = flexibility.damageMultiplier;
            if (match(wood.category, spell.category)) m *= 1.30;
            if (match(core.category, spell.category)) m *= 1.20;
            return m;
        }

        public double cooldownMultiplier(Spell spell) {
            double m = flexibility.cooldownMultiplier;
            if (match(wood.category, spell.category)) m *= 0.80;
            if (match(core.category, spell.category)) m *= 0.85;
            return m;
        }

        public double speedMultiplier(Spell spell) {
            double m = 0.90 + (lengthInches - 8) / 8.0 * 0.25;
            if (match(core.category, spell.category)) m *= 1.15;
            return m;
        }

        public double durationMultiplier(Spell spell) {
            double m = 1.0;
            if (match(wood.category, spell.category)) m *= 1.20;
            if (match(core.category, spell.category)) m *= 1.15;
            return m;
        }
    }

    public enum Spell {
        EXPELLIARMUS("除你武器", NamedTextColor.RED,
                "击落目标手中的物品", 3_000L, CastMode.PROJECTILE, 1.4, SpellCategory.PROTECTION,
                TrailStyle.SPIRAL,
                Particle.DUST, Particle.CRIT,
                Color.fromRGB(220, 40, 40), null, 1.2f),

        STUPEFY("昏昏倒地", NamedTextColor.YELLOW,
                "使目标眩晕", 5_000L, CastMode.PROJECTILE, 1.2, SpellCategory.MIND,
                TrailStyle.STRAIGHT,
                Particle.ELECTRIC_SPARK, Particle.DUST,
                null, Color.fromRGB(255, 220, 60), 0.9f),

        PETRIFICUS("统统石化", NamedTextColor.GRAY,
                "冻结目标全身", 10_000L, CastMode.PROJECTILE, 0.9, SpellCategory.MIND,
                TrailStyle.RING,
                Particle.SNOWFLAKE, Particle.DUST,
                null, Color.fromRGB(150, 220, 255), 1.0f),

        INCENDIO("火焰熊熊", NamedTextColor.GOLD,
                "点燃目标与方块，烤熟副手物品", 5_000L, CastMode.PROJECTILE, 1.3, SpellCategory.ATTACK,
                TrailStyle.FLAME,
                Particle.FLAME, Particle.LAVA,
                null, null, 1.0f),

        BOMBARDA("爆炸咒", NamedTextColor.DARK_RED,
                "引发爆炸，破坏方块", 15_000L, CastMode.PROJECTILE, 1.2, SpellCategory.ATTACK,
                TrailStyle.ORB,
                Particle.FLAME, Particle.EXPLOSION,
                null, Color.fromRGB(255, 130, 0), 1.5f),

        DIFFINDO("四分五裂", NamedTextColor.DARK_RED,
                "撕裂目标", 5_000L, CastMode.PROJECTILE, 1.3, SpellCategory.ATTACK,
                TrailStyle.STRAIGHT,
                Particle.DUST, Particle.CRIT,
                Color.fromRGB(150, 0, 0), null, 1.0f),

        ACCIO("飞来咒", NamedTextColor.LIGHT_PURPLE,
                "把目标拉向你", 8_000L, CastMode.PROJECTILE, 1.0, SpellCategory.SPEED,
                TrailStyle.RING,
                Particle.PORTAL, Particle.DUST,
                null, Color.fromRGB(180, 100, 255), 1.0f),

        LEVIOSA("羽加迪姆勒维奥萨", NamedTextColor.GREEN,
                "让目标漂浮", 6_000L, CastMode.PROJECTILE, 0.9, SpellCategory.SPEED,
                TrailStyle.FLAME,
                Particle.CLOUD, Particle.END_ROD,
                null, null, 0.8f),

        LEVICORPUS("倒挂金钟", NamedTextColor.DARK_PURPLE,
                "把目标倒吊起来", 12_000L, CastMode.PROJECTILE, 0.9, SpellCategory.DARK,
                TrailStyle.HELIX,
                Particle.SOUL_FIRE_FLAME, Particle.DUST,
                null, Color.fromRGB(0, 220, 200), 1.1f),

        RICTUSEMPRA("咧嘴呼啦啦", NamedTextColor.LIGHT_PURPLE,
                "让目标笑到失控", 15_000L, CastMode.PROJECTILE, 0.8, SpellCategory.MIND,
                TrailStyle.ORBIT,
                Particle.HEART, Particle.DUST,
                null, Color.fromRGB(255, 150, 200), 1.2f),

        SILENCIO("无声无息", NamedTextColor.DARK_GRAY,
                "让目标短暂失声", 20_000L, CastMode.PROJECTILE, 1.0, SpellCategory.DARK,
                TrailStyle.RING,
                Particle.SCULK_SOUL, Particle.DUST,
                null, Color.fromRGB(40, 80, 90), 1.0f),

        LANGOCK("锁舌封喉", NamedTextColor.DARK_GRAY,
                "封住目标的嘴", 20_000L, CastMode.PROJECTILE, 1.0, SpellCategory.DARK,
                TrailStyle.SPIRAL,
                Particle.WITCH, Particle.DUST,
                null, Color.fromRGB(80, 0, 120), 1.0f),

        // ★ 不可饶恕咒
        AVADA_KEDAVRA("阿瓦达索命", NamedTextColor.DARK_RED,
                "即死咒（不可饶恕）", 60_000L, CastMode.PROJECTILE, 1.5, SpellCategory.DARK,
                TrailStyle.STRAIGHT,
                Particle.DUST, Particle.SOUL_FIRE_FLAME,
                Color.fromRGB(0, 200, 60), null, 1.5f),

        IMPERIO("夺魂咒", NamedTextColor.DARK_PURPLE,
                "操控生物为你而战（不可饶恕）", 30_000L, CastMode.PROJECTILE, 1.0, SpellCategory.DARK,
                TrailStyle.HELIX,
                Particle.WITCH, Particle.DUST,
                null, Color.fromRGB(120, 0, 200), 1.5f),

        CRUCIO("钻心咒", NamedTextColor.DARK_RED,
                "让目标承受极致痛苦（不可饶恕）", 20_000L, CastMode.PROJECTILE, 1.0, SpellCategory.DARK,
                TrailStyle.SPIRAL,
                Particle.DUST, Particle.CRIT,
                Color.fromRGB(200, 0, 0), null, 1.5f),

        PROTEGO("盔甲护身", NamedTextColor.AQUA,
                "召唤临时护盾", 15_000L, CastMode.SELF, 1.0, SpellCategory.PROTECTION,
                TrailStyle.ORB,
                Particle.END_ROD, Particle.DUST,
                null, Color.fromRGB(100, 220, 255), 1.2f),

        AGUAMENTI("清水如泉", NamedTextColor.BLUE,
                "喷出清水，熄灭火焰", 8_000L, CastMode.SELF, 1.0, SpellCategory.UTILITY,
                TrailStyle.CASCADE,
                Particle.SPLASH, Particle.DUST,
                null, Color.fromRGB(60, 140, 255), 1.2f),

        LUMOS("荧光闪烁", NamedTextColor.WHITE,
                "照亮周围", 2_000L, CastMode.SELF, 1.5, SpellCategory.UTILITY,
                TrailStyle.ORB,
                Particle.END_ROD, Particle.DUST,
                null, Color.fromRGB(255, 255, 220), 1.4f),

        ALOHOMORA("阿拉霍洞开", NamedTextColor.GOLD,
                "打开铁门和铁活板门", 3_000L, CastMode.TARGET_BLOCK, 1.0, SpellCategory.UTILITY,
                TrailStyle.ORB,
                Particle.END_ROD, Particle.DUST,
                null, Color.fromRGB(255, 220, 100), 1.0f),

        SHIELD("护身咒", NamedTextColor.AQUA,
                "右键举起护盾，格挡一次攻击后冷却", 500L, CastMode.SHIELD, 1.0, SpellCategory.PROTECTION,
                TrailStyle.ORB,
                Particle.END_ROD, Particle.DUST,
                null, Color.fromRGB(150, 240, 255), 1.2f),

        EPISKEY("愈合如初", NamedTextColor.GREEN,
                "低头右键治疗自己，对非亡灵生物发射可治愈", 12_000L, CastMode.PROJECTILE, 1.0, SpellCategory.PROTECTION,
                TrailStyle.ORBIT,
                Particle.HEART, Particle.DUST,
                null, Color.fromRGB(255, 100, 150), 1.2f),

        REPARO("修复如初", NamedTextColor.LIGHT_PURPLE,
                "修复自己的傀儡或副手物品，消耗经验", 5_000L, CastMode.SELF, 1.0, SpellCategory.UTILITY,
                TrailStyle.ORB,
                Particle.END_ROD, Particle.ENCHANT,
                null, Color.fromRGB(180, 220, 255), 1.2f),

        EXPECT_PATRONUM("呼神护卫", NamedTextColor.AQUA,
                "召唤守护神，驱散怪物", 30_000L, CastMode.AREA, 1.5, SpellCategory.PROTECTION,
                TrailStyle.HELIX,
                Particle.END_ROD, Particle.DUST,
                null, Color.fromRGB(150, 240, 255), 1.5f);

        public final String displayName;
        public final NamedTextColor color;
        public final String description;
        public final long cooldownMs;
        public final CastMode castMode;
        public final double speed;
        public final SpellCategory category;
        public final TrailStyle trailStyle;
        public final Particle primaryParticle;
        public final Particle secondaryParticle;
        public final Color primaryColor;
        public final Color secondaryColor;
        public final float particleSize;

        Spell(String displayName, NamedTextColor color, String description,
              long cooldownMs, CastMode castMode, double speed, SpellCategory category,
              TrailStyle trailStyle,
              Particle primaryParticle, Particle secondaryParticle,
              Color primaryColor, Color secondaryColor, float particleSize) {
            this.displayName = displayName;
            this.color = color;
            this.description = description;
            this.cooldownMs = cooldownMs;
            this.castMode = castMode;
            this.speed = speed;
            this.category = category;
            this.trailStyle = trailStyle;
            this.primaryParticle = primaryParticle;
            this.secondaryParticle = secondaryParticle;
            this.primaryColor = primaryColor;
            this.secondaryColor = secondaryColor;
            this.particleSize = particleSize;
        }

        public Component display() { return Component.text(displayName, color); }

        public String cooldownDisplay() {
            return cooldownMs >= 1000 ? (cooldownMs / 1000) + "s" : cooldownMs + "ms";
        }

        /** ★ 三种不可饶恕咒 */
        public boolean isUnforgivable() {
            return this == AVADA_KEDAVRA || this == IMPERIO || this == CRUCIO;
        }
    }

    // ═══════════════════════════════════════════════
    //  构造 / 关闭
    // ═══════════════════════════════════════════════

    public WandManager(JavaPlugin plugin, MessageManager messages, ConfigManager config) {
        this.plugin = plugin;
        this.messages = messages;
        this.wandKey = new NamespacedKey(plugin, "magic_wand");
        this.wandStatsKey = new NamespacedKey(plugin, "magic_wand_stats");
        this.wandOwnerKey = new NamespacedKey(plugin, "magic_wand_owner");
        this.wandAffinityKey = new NamespacedKey(plugin, "magic_wand_affinity");
        this.projectileKey = new NamespacedKey(plugin, "wand_projectile");
        this.projectileOwnerKey = new NamespacedKey(plugin, "wand_projectile_owner");
        this.unforgivableCountKey = new NamespacedKey(plugin, "unforgivable_count");
        this.redemptionKey = new NamespacedKey(plugin, "unforgivable_redemption");

        this.maxFlightTimeMs = config.getLong("wand.max-flight-time-ms", 30_000L);
        // 默认关闭 OP 限制，全玩家可用
        this.requireOpForUnforgivable = config.getBoolean("wand.require-op-for-unforgivable", false);
        this.maxActiveProjectilesPerPlayer = config.getInt("wand.max-active-projectiles", 3);
        this.lumosLightLevel = config.getInt("wand.lumos-light-level", 15);
        this.lumosDurationMs = config.getLong("wand.lumos-duration-ms", 10_000L);

        this.cooldowns = new CooldownManager(plugin, 1000L, 0);
        registerRecipe();
        startCleanupTask();
        startImperioTask();
    }

    private void startCleanupTask() {
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            silenceUntil.entrySet().removeIf(e -> e.getValue() < now);
            muteUntil.entrySet().removeIf(e -> e.getValue() < now);
            shieldCooldownUntil.entrySet().removeIf(e -> e.getValue() < now - 60_000L);
        }, 600L, SILENCE_CLEANUP_INTERVAL_MS / 50);
        activeTasks.add(task);
    }

    /** 夺魂咒状态更新（每 10 刻） */
    private void startImperioTask() {
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            Iterator<Map.Entry<UUID, ImperioTarget>> iter = imperioTargets.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<UUID, ImperioTarget> entry = iter.next();
                ImperioTarget data = entry.getValue();

                if (now >= data.endTime) {
                    iter.remove();
                    continue;
                }

                Entity e = Bukkit.getEntity(entry.getKey());
                if (!(e instanceof Mob mob) || !mob.isValid() || mob.isDead()) {
                    iter.remove();
                    continue;
                }

                // 保护玩家（不让被夺魂的怪物攻击任何玩家）
                LivingEntity target = mob.getTarget();
                if (target instanceof Player) {
                    mob.setTarget(null);
                }

                // 无目标时攻击附近的怪物
                if (mob.getTarget() == null) {
                    Monster near = findNearbyMonster(mob, 16.0);
                    if (near != null) {
                        mob.setTarget(near);
                    }
                }

                // 粒子效果
                Location loc = mob.getLocation().add(0, 1.5, 0);
                TauntUtils.spawnParticleSafe(mob.getWorld(), Particle.WITCH,
                        loc.getX(), loc.getY(), loc.getZ(), 1, 0.3, 0.2, 0.3, 0.05);
            }
        }, 200L, 10L);
        activeTasks.add(task);
    }

    private Monster findNearbyMonster(Mob source, double radius) {
        for (Entity e : source.getWorld().getNearbyEntities(source.getLocation(), radius, radius, radius)) {
            if (e instanceof Monster m && m.isValid() && !m.isDead() && !m.equals(source)) {
                return m;
            }
        }
        return null;
    }

    public void shutdown() {
        for (BukkitTask task : activeTasks) {
            if (task != null) task.cancel();
        }
        activeTasks.clear();

        for (BukkitTask t : shieldParticleTasks.values()) {
            if (t != null) t.cancel();
        }
        shieldParticleTasks.clear();

        for (World world : Bukkit.getWorlds()) {
            for (Entity e : world.getEntities()) {
                if (e.getPersistentDataContainer().has(projectileKey, PersistentDataType.BYTE)) {
                    e.remove();
                }
            }
        }

        currentSpell.clear();
        usedSpells.clear();
        activeProjectiles.clear();
        silenceUntil.clear();
        muteUntil.clear();
        shieldActive.clear();
        shieldCooldownUntil.clear();
        imperioTargets.clear();
        if (cooldowns != null) cooldowns.shutdown();
    }

    private void registerRecipe() {
        NamespacedKey recipeKey = new NamespacedKey(plugin, "magic_wand_recipe");
        Bukkit.removeRecipe(recipeKey);

        ItemStack wand = createWand();
        ShapedRecipe recipe = new ShapedRecipe(recipeKey, wand);
        recipe.shape("EPE", "GSG", "JDJ");
        recipe.setIngredient('E', Material.ENDER_PEARL);
        recipe.setIngredient('P', Material.BLAZE_POWDER);
        recipe.setIngredient('G', Material.GHAST_TEAR);
        recipe.setIngredient('S', Material.STICK);
        recipe.setIngredient('J', Material.GOLD_INGOT);
        recipe.setIngredient('D', Material.DIAMOND);

        try {
            Bukkit.addRecipe(recipe);
            plugin.getLogger().info("[魔杖] 已注册合成配方");
        } catch (Throwable t) {
            plugin.getLogger().warning("[魔杖] 配方注册失败: " + t.getMessage());
        }
    }

    public ItemStack createWand() {
        return createWand(WandStats.random(ThreadLocalRandom.current()));
    }

    public ItemStack createWand(WandStats stats) {
        ItemStack wand = new ItemStack(Material.FISHING_ROD);
        ItemMeta meta = wand.getItemMeta();
        if (meta == null) return wand;

        meta.displayName(Component.text("✦ 魔杖 ✦", NamedTextColor.GOLD));

        try {
            meta.setItemModel(NamespacedKey.minecraft("stick"));
        } catch (Throwable ignored) {}

        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);

        if (stats != null) {
            meta.lore(buildLore(stats, 0, false));
            meta.getPersistentDataContainer().set(wandStatsKey, PersistentDataType.STRING, stats.serialize());
        }

        meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
        wand.setItemMeta(meta);
        return wand;
    }

    private List<Component> buildLore(WandStats stats, int affinity, boolean bound) {
        NamedTextColor affinityColor;
        String affinityText;
        if (!bound) {
            affinityColor = NamedTextColor.DARK_GRAY;
            affinityText = "未认主";
        } else if (affinity >= AFFINITY_MAX) {
            affinityColor = NamedTextColor.GREEN;
            affinityText = affinity + "% ✦ 完 全 认 主";
        } else if (affinity >= 80) {
            affinityColor = NamedTextColor.GOLD;
            affinityText = affinity + "%";
        } else if (affinity >= 50) {
            affinityColor = NamedTextColor.YELLOW;
            affinityText = affinity + "%";
        } else if (affinity >= 20) {
            affinityColor = NamedTextColor.WHITE;
            affinityText = affinity + "%";
        } else {
            affinityColor = NamedTextColor.GRAY;
            affinityText = affinity + "%";
        }

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("─────────────", NamedTextColor.DARK_GRAY));
        lore.add(Component.text("木材：", NamedTextColor.GRAY)
                .append(Component.text(stats.wood().display, NamedTextColor.AQUA)));
        lore.add(Component.text("杖芯：", NamedTextColor.GRAY)
                .append(Component.text(stats.core().display, NamedTextColor.LIGHT_PURPLE)));
        lore.add(Component.text("长度：", NamedTextColor.GRAY)
                .append(Component.text(stats.lengthInches() + " 英寸", NamedTextColor.YELLOW)));
        lore.add(Component.text("柔韧性：", NamedTextColor.GRAY)
                .append(Component.text(stats.flexibility().display, NamedTextColor.GREEN)));
        lore.add(Component.empty());
        lore.add(Component.text("亲密度：", NamedTextColor.GRAY)
                .append(Component.text(affinityText, affinityColor)));
        lore.add(Component.empty());
        lore.add(Component.text("✦ 亲和：", NamedTextColor.GOLD)
                .append(Component.text(stats.wood().effect, NamedTextColor.GRAY)));
        lore.add(Component.text("  ", NamedTextColor.GRAY)
                .append(Component.text(stats.core().effect, NamedTextColor.GRAY)));
        lore.add(Component.text("─────────────", NamedTextColor.DARK_GRAY));
        lore.add(Component.text("Shift + 右键切换魔咒", NamedTextColor.DARK_GREEN));
        lore.add(Component.text("右键发射魔咒", NamedTextColor.DARK_GREEN));
        return lore;
    }

    public boolean isWand(ItemStack item) {
        if (item == null || item.getType() != Material.FISHING_ROD) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE);
    }

    private WandStats getWandStats(ItemStack item) {
        if (item == null) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        String s = meta.getPersistentDataContainer().get(wandStatsKey, PersistentDataType.STRING);
        return WandStats.deserialize(s);
    }

    public ItemStack reforgeWand(ItemStack oldWand) {
        if (!isWand(oldWand)) return null;
        WandStats newStats = WandStats.random(ThreadLocalRandom.current());
        return createWand(newStats);
    }

    // ═══════════════════════════════════════════════
    //  亲和度系统
    // ═══════════════════════════════════════════════

    private boolean processAffinity(Player player) {
        ItemStack wand = player.getInventory().getItemInMainHand();
        if (!isWand(wand)) return true;

        WandStats stats = getWandStats(wand);
        ItemMeta meta = wand.getItemMeta();
        if (meta == null) return true;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String ownerStr = pdc.get(wandOwnerKey, PersistentDataType.STRING);
        int affinity = pdc.getOrDefault(wandAffinityKey, PersistentDataType.INTEGER, 0);

        if (ownerStr == null) {
            pdc.set(wandOwnerKey, PersistentDataType.STRING, player.getUniqueId().toString());
            pdc.set(wandAffinityKey, PersistentDataType.INTEGER, 1);
            meta.lore(buildLore(stats, 1, true));
            wand.setItemMeta(meta);
            player.getInventory().setItemInMainHand(wand);

            player.sendMessage(Component.text("✦ 魔杖与你建立了联系 —— 亲密度 1%",
                    NamedTextColor.GOLD));
            player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.5f);
            return true;
        }

        UUID owner;
        try {
            owner = UUID.fromString(ownerStr);
        } catch (Throwable t) {
            pdc.set(wandOwnerKey, PersistentDataType.STRING, player.getUniqueId().toString());
            pdc.set(wandAffinityKey, PersistentDataType.INTEGER, 1);
            meta.lore(buildLore(stats, 1, true));
            wand.setItemMeta(meta);
            player.getInventory().setItemInMainHand(wand);
            return true;
        }

        if (!owner.equals(player.getUniqueId())) {
            if (affinity >= AFFINITY_MAX) {
                triggerAffinityRejection(player);
                return false;
            }
            return true;
        }

        if (affinity < AFFINITY_MAX) {
            int newAffinity = Math.min(AFFINITY_MAX, affinity + 1);
            pdc.set(wandAffinityKey, PersistentDataType.INTEGER, newAffinity);
            meta.lore(buildLore(stats, newAffinity, true));
            wand.setItemMeta(meta);
            player.getInventory().setItemInMainHand(wand);

            if (newAffinity == AFFINITY_MAX) {
                player.sendMessage(Component.text("═══════════════════════", NamedTextColor.GOLD));
                player.sendMessage(Component.text("✦ 魔杖与你完全认主！ ✦", NamedTextColor.GOLD));
                player.sendMessage(Component.text("现在其他人使用它将遭到爆炸反噬。",
                        NamedTextColor.RED));
                player.sendMessage(Component.text("═══════════════════════", NamedTextColor.GOLD));
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.5f);
                TauntUtils.increment(plugin, player, "wand_fully_bound", 1);
            }
        }

        return true;
    }

    private void triggerAffinityRejection(Player player) {
        Location loc = player.getLocation();

        player.playSound(loc, Sound.ENTITY_WITHER_SHOOT, 1.0f, 0.8f);
        player.playSound(loc, Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 0.5f);

        for (int i = 0; i < 40; i++) {
            double angle = i * Math.PI / 20;
            Location pl = loc.clone().add(
                    Math.cos(angle) * 1.5,
                    Math.sin(angle * 2) * 0.5,
                    Math.sin(angle) * 1.5);
            TauntUtils.spawnParticleSafe(player.getWorld(), Particle.SOUL_FIRE_FLAME,
                    pl.getX(), pl.getY(), pl.getZ(), 2, 0.1, 0.1, 0.1, 0.05);
        }

        player.getWorld().createExplosion(
                loc.getX(), loc.getY() + 0.5, loc.getZ(),
                REJECTION_EXPLOSION_POWER,
                true,
                true,
                player
        );

        player.sendActionBar(Component.text("✦ 魔杖拒绝了你！爆炸反噬！",
                NamedTextColor.DARK_RED));
        player.sendMessage(Component.text("💥 这根魔杖已经认主，拒绝被你使用。",
                NamedTextColor.DARK_RED));
    }

    // ═══════════════════════════════════════════════
    //  ★ 不可饶恕咒惩罚
    // ═══════════════════════════════════════════════

    /**
     * 施加不可饶恕咒使用惩罚。
     *
     * <p>机制：</p>
     * <ul>
     *   <li>每使用一次不可饶恕咒，计数器 +1（持久化到 PDC）</li>
     *   <li>每累计 {@link #UNFORGIVABLE_HP_LOSS_INTERVAL} 次，最大生命值永久 -{@link #UNFORGIVABLE_HP_LOSS}</li>
     *   <li>最多惩罚 {@link #MAX_HP_LOSS_COUNT} 次，最低降至 6 点生命（3 颗心）</li>
     *   <li>惩罚使用 {@link AttributeModifier} 持久化，重登后自动恢复</li>
     * </ul>
     */
    private void applyUnforgivablePenalty(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();

        // ① 累加使用次数
        int uses = pdc.getOrDefault(unforgivableCountKey, PersistentDataType.INTEGER, 0);
        uses++;
        pdc.set(unforgivableCountKey, PersistentDataType.INTEGER, uses);

        // ② 计算新旧惩罚阶段
        int newLossCount = uses / UNFORGIVABLE_HP_LOSS_INTERVAL;
        int oldLossCount = (uses - 1) / UNFORGIVABLE_HP_LOSS_INTERVAL;

        // ③ 跨过阈值 → 施加新的惩罚
        if (newLossCount > oldLossCount) {
            if (newLossCount > MAX_HP_LOSS_COUNT) {
                // 已达上限，仅提示
                player.sendActionBar(Component.text(
                        "⚠ 你已被黑暗侵蚀至极限...", NamedTextColor.DARK_RED));
                return;
            }

            AttributeInstance maxHp = player.getAttribute(Attribute.MAX_HEALTH);
            if (maxHp == null) return;

            NamespacedKey modifierKey = new NamespacedKey(plugin,
                    "unforgivable_drain_" + newLossCount);

            if (maxHp.getModifier(modifierKey) == null) {
                maxHp.addModifier(new AttributeModifier(
                        modifierKey,
                        -UNFORGIVABLE_HP_LOSS,
                        AttributeModifier.Operation.ADD_NUMBER));

                // 反馈
                double currentMax = maxHp.getValue();
                int hearts = (int) (currentMax / 2);

                player.sendMessage(Component.text("═══════════════════════",
                        NamedTextColor.DARK_RED));
                player.sendMessage(Component.text("⚠ 不可饶恕咒的反噬",
                        NamedTextColor.DARK_RED));
                player.sendMessage(Component.text("  你已经使用了 ")
                        .append(Component.text(uses + " 次", NamedTextColor.RED))
                        .append(Component.text(" 不可饶恕咒", NamedTextColor.GRAY)));
                player.sendMessage(Component.text("  生命上限永久 -")
                        .append(Component.text((int) UNFORGIVABLE_HP_LOSS + " 点", NamedTextColor.RED))
                        .append(Component.text("（现为 " + hearts + " 颗心）", NamedTextColor.GRAY)));
                player.sendMessage(Component.text("═══════════════════════",
                        NamedTextColor.DARK_RED));

                // 视觉/听觉反馈
                player.playSound(player.getLocation(),
                        Sound.ENTITY_WITHER_HURT, 1.0f, 0.5f);
                player.playSound(player.getLocation(),
                        Sound.ENTITY_ENDERMAN_SCREAM, 0.6f, 0.5f);

                Location loc = player.getLocation().add(0, 1, 0);
                for (int i = 0; i < 30; i++) {
                    double angle = i * Math.PI / 15;
                    Location pl = loc.clone().add(
                            Math.cos(angle) * 1.5,
                            Math.sin(angle * 2) * 0.5,
                            Math.sin(angle) * 1.5);
                    TauntUtils.spawnParticleSafe(player.getWorld(),
                            Particle.SOUL_FIRE_FLAME,
                            pl.getX(), pl.getY(), pl.getZ(),
                            1, 0.1, 0.1, 0.1, 0.05);
                }

                // 把当前血量拉低到新上限以下（防止血量比上限高）
                if (player.getHealth() > currentMax) {
                    player.setHealth(currentMax);
                }
            }
        } else if (newLossCount > MAX_HP_LOSS_COUNT) {
            // 超过最大值时的提示
            player.sendActionBar(Component.text(
                    "⚠ 不可饶恕咒使用次数过多！", NamedTextColor.DARK_RED));
        }
    }

    // ═══════════════════════════════════════════════
    //  生命上限恢复（善行净化 / 许愿井净化）
    // ═══════════════════════════════════════════════

    /**
     * 检查玩家是否有不可饶恕咒反噬。
     */
    public boolean hasAnyPenalty(Player player) {
        if (player == null) return false;
        int uses = player.getPersistentDataContainer()
                .getOrDefault(unforgivableCountKey, PersistentDataType.INTEGER, 0);
        return uses >= UNFORGIVABLE_HP_LOSS_INTERVAL;
    }

    /**
     * 获取玩家当前的反噬级数（0 表示无）。
     */
    public int getPenaltyLevel(Player player) {
        if (player == null) return 0;
        int uses = player.getPersistentDataContainer()
                .getOrDefault(unforgivableCountKey, PersistentDataType.INTEGER, 0);
        return Math.min(uses / UNFORGIVABLE_HP_LOSS_INTERVAL, MAX_HP_LOSS_COUNT);
    }

    /**
     * 净化 1 级不可饶恕咒反噬（供外部调用，如许愿井）。
     */
    public boolean purgeOneUnforgivablePenalty(Player player) {
        if (player == null) return false;

        PersistentDataContainer pdc = player.getPersistentDataContainer();
        int uses = pdc.getOrDefault(unforgivableCountKey, PersistentDataType.INTEGER, 0);

        int lossCount = Math.min(uses / UNFORGIVABLE_HP_LOSS_INTERVAL, MAX_HP_LOSS_COUNT);
        if (lossCount <= 0) return false;

        AttributeInstance maxHp = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHp == null) return false;

        NamespacedKey modifierKey = new NamespacedKey(
                plugin, "unforgivable_drain_" + lossCount);
        AttributeModifier mod = maxHp.getModifier(modifierKey);
        if (mod != null) {
            maxHp.removeModifier(mod);
        }

        int newUses = Math.max(0, uses - UNFORGIVABLE_HP_LOSS_INTERVAL);
        pdc.set(unforgivableCountKey, PersistentDataType.INTEGER, newUses);

        double currentMax = maxHp.getValue();
        int hearts = (int) (currentMax / 2.0);

        player.sendMessage(Component.text(
                "═══════════════════════", NamedTextColor.LIGHT_PURPLE));
        player.sendMessage(Component.text(
                "✦ 黑暗的反噬被净化了！", NamedTextColor.LIGHT_PURPLE));
        player.sendMessage(Component.text("  生命上限恢复 ")
                .append(Component.text("+" + ((int) UNFORGIVABLE_HP_LOSS) + " 点",
                        NamedTextColor.GREEN))
                .append(Component.text("（现为 " + hearts + " 颗心）",
                        NamedTextColor.GRAY)));
        player.sendMessage(Component.text(
                "═══════════════════════", NamedTextColor.LIGHT_PURPLE));

        player.playSound(player.getLocation(),
                Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.8f);
        player.playSound(player.getLocation(),
                Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);

        Location loc = player.getLocation().add(0, 1, 0);
        World world = player.getWorld();
        for (int i = 0; i < 40; i++) {
            double angle = i * Math.PI / 20.0;
            double x = loc.getX() + Math.cos(angle) * 1.5;
            double y = loc.getY() + Math.sin(angle * 2) * 0.5;
            double z = loc.getZ() + Math.sin(angle) * 1.5;
            TauntUtils.spawnParticleSafe(world, Particle.END_ROD,
                    x, y, z, 2, 0.1, 0.1, 0.1, 0.1);
            TauntUtils.spawnParticleSafe(world, Particle.HEART,
                    x, y, z, 1, 0.1, 0.1, 0.1, 0.0);
        }

        // 完全净化 → 成就
        if (!hasAnyPenalty(player)) {
            TauntUtils.increment(plugin, player, "purified_count", 1);
        }

        return true;
    }

    /**
     * 增加救赎值（善行累积）。
     */
    public void addRedemption(Player player, int amount) {
        if (player == null || amount <= 0) return;

        PersistentDataContainer pdc = player.getPersistentDataContainer();
        int redemption = pdc.getOrDefault(redemptionKey, PersistentDataType.INTEGER, 0) + amount;

        int guard = 0;
        while (redemption >= REDEMPTION_PER_PURGE && hasAnyPenalty(player) && guard < 10) {
            redemption -= REDEMPTION_PER_PURGE;
            purgeOneUnforgivablePenalty(player);
            guard++;
        }

        pdc.set(redemptionKey, PersistentDataType.INTEGER, redemption);

        if (hasAnyPenalty(player) && redemption > 0) {
            int remaining = REDEMPTION_PER_PURGE - redemption;
            player.sendActionBar(Component.text(
                    "✦ 善行值 " + redemption + "/" + REDEMPTION_PER_PURGE
                            + "（还需 " + remaining + " 次善行可净化 1 级反噬）",
                    NamedTextColor.LIGHT_PURPLE));
        }
    }

    /**
     * 获取玩家的救赎值进度。
     */
    public int getRedemption(Player player) {
        if (player == null) return 0;
        return player.getPersistentDataContainer()
                .getOrDefault(redemptionKey, PersistentDataType.INTEGER, 0);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PersistentDataContainer pdc = player.getPersistentDataContainer();

        int uses = pdc.getOrDefault(unforgivableCountKey, PersistentDataType.INTEGER, 0);
        int lossCount = uses / UNFORGIVABLE_HP_LOSS_INTERVAL;
        if (lossCount > MAX_HP_LOSS_COUNT) {
            lossCount = MAX_HP_LOSS_COUNT;
        }

        AttributeInstance maxHp = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHp == null) return;

        // 恢复所有惩罚 modifier
        for (int i = 1; i <= lossCount; i++) {
            NamespacedKey key = new NamespacedKey(
                    plugin, "unforgivable_drain_" + i);

            if (maxHp.getModifier(key) == null) {
                AttributeModifier modifier = new AttributeModifier(
                        key,
                        -UNFORGIVABLE_HP_LOSS,
                        AttributeModifier.Operation.ADD_NUMBER);
                maxHp.addModifier(modifier);
            }
        }

        // 血量修正
        double currentMax = maxHp.getValue();
        if (player.getHealth() > currentMax) {
            player.setHealth(currentMax);
        }

        // ★ 用 getPenaltyLevel 提示玩家
        int penaltyLevel = getPenaltyLevel(player);
        if (penaltyLevel > 0) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;

                int hearts = (int) (player.getMaxHealth() / 2.0);
                int redemption = getRedemption(player);

                player.sendMessage(Component.text(
                        "═══════════════════════", NamedTextColor.DARK_RED));
                player.sendMessage(Component.text(
                        "⚠ 你身上有不可饶恕咒的反噬", NamedTextColor.DARK_RED));
                player.sendMessage(Component.text("  反噬级数：")
                        .append(Component.text(penaltyLevel + " 级", NamedTextColor.RED))
                        .append(Component.text("（生命上限 " + hearts + " 颗心）",
                                NamedTextColor.GRAY)));
                player.sendMessage(Component.text("  已使用不可饶恕咒：")
                        .append(Component.text(uses + " 次", NamedTextColor.RED)));
                player.sendMessage(Component.text("  净化方式：", NamedTextColor.GRAY));
                player.sendMessage(Component.text("    · 善行值 " + redemption + "/10（治疗他人）",
                        NamedTextColor.LIGHT_PURPLE));
                player.sendMessage(Component.text("    · 下界合金锭 + 许愿井",
                        NamedTextColor.LIGHT_PURPLE));
                player.sendMessage(Component.text(
                        "═══════════════════════", NamedTextColor.DARK_RED));

                player.playSound(player.getLocation(),
                        Sound.BLOCK_BEACON_DEACTIVATE, 0.6f, 0.8f);
            }, 40L);   // 延迟 2 秒，避免刷屏登录消息
        }
    }

    // ═══════════════════════════════════════════════
    //  事件
    // ═══════════════════════════════════════════════

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!isWand(item)) return;

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        event.setCancelled(true);

        if (player.isSneaking()) {
            switchSpell(player);
        } else {
            castSpell(player, item);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onFish(PlayerFishEvent event) {
        Player player = event.getPlayer();
        if (isWand(player.getInventory().getItemInMainHand())
                || isWand(player.getInventory().getItemInOffHand())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof FishHook hook)) return;
        if (!(hook.getShooter() instanceof Player player)) return;

        if (isWand(player.getInventory().getItemInMainHand())
                || isWand(player.getInventory().getItemInOffHand())) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (hook.isValid()) hook.remove();
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (tryBlockWithShield(player, false)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        shieldActive.remove(uuid);
        BukkitTask t = shieldParticleTasks.remove(uuid);
        if (t != null) t.cancel();
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        UUID uuid = event.getEntity().getUniqueId();
        shieldActive.remove(uuid);
        BukkitTask t = shieldParticleTasks.remove(uuid);
        if (t != null) t.cancel();
    }

    // ═══════════════════════════════════════════════
    //  切换魔咒
    // ═══════════════════════════════════════════════

    private void switchSpell(Player player) {
        UUID uuid = player.getUniqueId();
        int next = (currentSpell.getOrDefault(uuid, 0) + 1) % SPELL_COUNT;
        currentSpell.put(uuid, next);

        Spell spell = SPELLS[next];
        ItemStack wand = player.getInventory().getItemInMainHand();
        WandStats stats = getWandStats(wand);

        Component bar = Component.text("✦ ", NamedTextColor.GOLD)
                .append(spell.display())
                .append(Component.text("  §8[CD: " + spell.cooldownDisplay() + "] §7- "
                        + spell.description, NamedTextColor.GRAY));

        if (stats != null
                && (stats.wood().category == spell.category || stats.wood().category == SpellCategory.ALL
                || stats.core().category == spell.category || stats.core().category == SpellCategory.ALL)) {
            bar = bar.append(Component.text("  ✨亲和", NamedTextColor.GOLD));
        }
        if (spell.isUnforgivable()) {
            bar = bar.append(Component.text("  ⚠不可饶恕", NamedTextColor.DARK_RED));
        }
        player.sendActionBar(bar);

        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.5f);

        Location pl = player.getLocation().add(0, 1.5, 0);
        TauntUtils.spawnParticleSafe(player.getWorld(), Particle.ENCHANT,
                pl.getX(), pl.getY(), pl.getZ(), 15, 0.3, 0.3, 0.3, 0.5);
    }

    // ═══════════════════════════════════════════════
    //  施放魔咒
    // ═══════════════════════════════════════════════

    private void castSpell(Player player, ItemStack wandItem) {
        UUID uuid = player.getUniqueId();

        // ① 亲和度
        if (!processAffinity(player)) return;
        wandItem = player.getInventory().getItemInMainHand();

        Spell spell = SPELLS[currentSpell.getOrDefault(uuid, 0)];

        // ② OP 检查（默认关闭）
        if (spell.isUnforgivable() && requireOpForUnforgivable && !player.isOp()) {
            messages.send(player, "wand.spell-unforgivable");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
            return;
        }

        WandStats stats = getWandStats(wandItem);
        if (stats == null) {
            stats = new WandStats(WandWood.CEDAR, WandCore.TROLL_WHISKER, 12, WandFlexibility.SUPPLE);
        }

        // ③ 冷却
        long effectiveCooldown = (long) (spell.cooldownMs * stats.cooldownMultiplier(spell));
        effectiveCooldown = Math.max(500L, effectiveCooldown);

        if (!cooldowns.isReady(uuid, spell.name(), effectiveCooldown)) {
            long remainMs = cooldowns.getRemaining(uuid, spell.name(), effectiveCooldown);
            showCooldownMessage(player, spell, remainMs, effectiveCooldown);
            return;
        }

        // ④ ★ 不可饶恕咒惩罚
        if (spell.isUnforgivable()) {
            applyUnforgivablePenalty(player);
        }

        // ⑤ 统计
        TauntUtils.increment(plugin, player, "spells_cast", 1);
        TauntUtils.unlock(plugin, player, AchievementManager.Ach.FIRST_SPELL);
        Set<String> used = usedSpells.computeIfAbsent(uuid, _ -> ConcurrentHashMap.newKeySet());
        used.add(spell.name());
        TauntUtils.setCounter(plugin, player, "unique_spells", used.size());

        // ⑥ 通用表现
        player.playSound(player.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 1.0f, 1.3f);
        spawnCastingParticles(player);

        // ⑦ 特殊分支
        if (spell == Spell.REPARO) {
            castReparo(player, spell, stats);
            return;
        }
        if (spell == Spell.EPISKEY && isLookingDown(player)) {
            applySelfHeal(player, spell, stats);
            return;
        }
        if (spell.castMode == CastMode.SHIELD) {
            activateShield(player);
            return;
        }
        if (spell.castMode == CastMode.TARGET_BLOCK) {
            castAlohomora(player, spell, stats);
            return;
        }

        // ⑧ ★ 火焰咒额外：烤熟副手物品
        if (spell == Spell.INCENDIO) {
            tryCookOffhand(player);
        }

        // ⑨ 通用分发
        switch (spell.castMode) {
            case SELF -> applySelfEffect(player, spell, stats);
            case AREA -> applyAreaEffect(player, spell, stats);
            case PROJECTILE -> launchProjectile(player, spell, stats);
            case SHIELD, TARGET_BLOCK -> { /* 已处理 */ }
        }
    }

    private void spawnCastingParticles(Player player) {
        Location eyeLoc = player.getEyeLocation();
        Location loc = eyeLoc.clone().add(eyeLoc.getDirection().multiply(1.0));
        TauntUtils.spawnParticleSafe(player.getWorld(), Particle.WITCH,
                loc.getX(), loc.getY(), loc.getZ(), 10, 0.1, 0.1, 0.1, 0.1);
    }

    // ═══════════════════════════════════════════════
    //  ★ 火焰咒烤熟副手
    // ═══════════════════════════════════════════════

    private void tryCookOffhand(Player player) {
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (offHand.getType().isAir()) return;

        Material cookedType = SMELT_MAP.get(offHand.getType());
        if (cookedType == null) return;

        ItemStack cookedStack = new ItemStack(cookedType, offHand.getAmount());
        ItemMeta oldMeta = offHand.getItemMeta();
        if (oldMeta != null && oldMeta.hasDisplayName()) {
            ItemMeta newMeta = cookedStack.getItemMeta();
            if (newMeta != null) {
                newMeta.displayName(oldMeta.displayName());
                cookedStack.setItemMeta(newMeta);
            }
        }

        player.getInventory().setItemInOffHand(cookedStack);

        player.sendActionBar(Component.text("✦ 火焰烤熟了副手的物品！",
                NamedTextColor.GOLD));
        player.playSound(player.getLocation(), Sound.BLOCK_FURNACE_FIRE_CRACKLE, 1.0f, 1.0f);
        player.playSound(player.getLocation(), Sound.ITEM_FIRECHARGE_USE, 0.6f, 1.5f);

        Location loc = player.getLocation().add(0, 1, 0);
        TauntUtils.spawnParticleSafe(player.getWorld(), Particle.FLAME,
                loc.getX(), loc.getY(), loc.getZ(), 15, 0.3, 0.3, 0.3, 0.05);
    }

    // ═══════════════════════════════════════════════
    //  修复如初咒
    // ═══════════════════════════════════════════════

    private void castReparo(Player player, Spell spell, WandStats stats) {
        if (tryRepairPuppet(player, spell, stats)) return;

        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (offHand.getType().isAir()) {
            player.sendActionBar(Component.text("✦ 副手没有物品，也未看向自己的傀儡",
                    NamedTextColor.GRAY));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.5f);
            return;
        }

        ItemMeta meta = offHand.getItemMeta();
        if (!(meta instanceof Damageable damageable)) {
            player.sendActionBar(Component.text("✦ 该物品没有耐久度，无法修复", NamedTextColor.GRAY));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.5f);
            return;
        }

        int currentDamage = damageable.getDamage();
        if (currentDamage <= 0) {
            player.sendActionBar(Component.text("✦ 该物品耐久度已满，无需修复", NamedTextColor.GRAY));
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.4f, 1.8f);
            return;
        }

        double costMultiplier = 1.0;
        if (stats.wood().category == SpellCategory.UTILITY
                || stats.wood().category == SpellCategory.ALL) costMultiplier *= 0.80;
        if (stats.core().category == SpellCategory.UTILITY
                || stats.core().category == SpellCategory.ALL) costMultiplier *= 0.85;

        int expCost = Math.max(REPARO_MIN_EXP,
                (int) Math.ceil(currentDamage * REPARO_EXP_PER_DAMAGE * costMultiplier));

        int totalExp = player.calculateTotalExperiencePoints();
        if (totalExp < expCost) {
            player.sendActionBar(Component.text(
                    "✦ 经验不足！需要 " + expCost + " 点，你只有 " + totalExp + " 点",
                    NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.5f);
            return;
        }

        player.giveExp(-expCost);

        int repairedAmount = currentDamage;
        damageable.setDamage(0);
        offHand.setItemMeta(meta);
        player.getInventory().setItemInOffHand(offHand);

        Location center = player.getLocation().add(0, 1, 0);
        for (int i = 0; i < 5; i++) applyOrb(player.getWorld(), spell, center, i * 4);
        TauntUtils.spawnParticleSafe(player.getWorld(), Particle.END_ROD,
                center.getX(), center.getY(), center.getZ(), 25, 0.6, 0.6, 0.6, 0.1);
        TauntUtils.spawnParticleSafe(player.getWorld(), Particle.ENCHANT,
                center.getX(), center.getY(), center.getZ(), 30, 0.5, 0.5, 0.5, 0.5);

        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.2f);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.5f);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 2.0f);

        player.sendMessage(Component.text("═══════════════════════", NamedTextColor.GOLD));
        player.sendMessage(Component.text("✦ 修复如初 ✦", NamedTextColor.LIGHT_PURPLE));
        player.sendMessage(Component.text("  修复了 ", NamedTextColor.GRAY)
                .append(Component.text(repairedAmount + " 点耐久", NamedTextColor.GREEN)));
        player.sendMessage(Component.text("  消耗了 ", NamedTextColor.GRAY)
                .append(Component.text(expCost + " 点经验", NamedTextColor.YELLOW)));
        player.sendMessage(Component.text("═══════════════════════", NamedTextColor.GOLD));

        player.sendActionBar(Component.text("✦ 修复如初！-" + expCost + " 经验",
                NamedTextColor.LIGHT_PURPLE));

        TauntUtils.increment(plugin, player, "reparo_used", 1);
    }

    private boolean tryRepairPuppet(Player player, Spell spell, WandStats stats) {
        PuppetManager puppetManager = null;
        if (plugin instanceof TauntPlugin tp) {
            puppetManager = tp.getPuppetManager();
        }
        if (puppetManager == null) return false;

        final PuppetManager pm = puppetManager;
        final UUID playerId = player.getUniqueId();

        RayTraceResult result;
        try {
            result = player.getWorld().rayTraceEntities(
                    player.getEyeLocation(),
                    player.getEyeLocation().getDirection(),
                    6.0,
                    0.6,
                    e -> e instanceof LivingEntity le
                            && le.isValid()
                            && !le.isDead()
                            && pm.isOwnedBy(le.getUniqueId(), playerId));
        } catch (Throwable t) {
            return false;
        }

        if (result == null || !(result.getHitEntity() instanceof LivingEntity puppet)) {
            return false;
        }

        double maxHealth = puppet.getMaxHealth();
        double currentHealth = puppet.getHealth();
        double missing = maxHealth - currentHealth;

        if (missing <= 0.5) {
            player.sendActionBar(Component.text("✦ 该傀儡生命值已满", NamedTextColor.GRAY));
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.4f, 1.8f);
            return true;
        }

        double costMultiplier = 1.0;
        if (stats.wood().category == SpellCategory.UTILITY
                || stats.wood().category == SpellCategory.ALL) costMultiplier *= 0.80;
        if (stats.core().category == SpellCategory.UTILITY
                || stats.core().category == SpellCategory.ALL) costMultiplier *= 0.85;

        int expCost = Math.max(REPARO_MIN_EXP,
                (int) Math.ceil(missing * 0.5 * costMultiplier));

        int totalExp = player.calculateTotalExperiencePoints();
        if (totalExp < expCost) {
            player.sendActionBar(Component.text(
                    "✦ 经验不足！需要 " + expCost + " 点，你只有 " + totalExp + " 点",
                    NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.5f);
            return true;
        }

        player.giveExp(-expCost);
        puppet.setHealth(maxHealth);

        Location center = puppet.getLocation().add(0, 1, 0);
        for (int i = 0; i < 5; i++) applyOrb(puppet.getWorld(), spell, center, i * 4);
        TauntUtils.spawnParticleSafe(puppet.getWorld(), Particle.END_ROD,
                center.getX(), center.getY(), center.getZ(), 25, 0.6, 0.8, 0.6, 0.1);
        TauntUtils.spawnParticleSafe(puppet.getWorld(), Particle.ENCHANT,
                center.getX(), center.getY(), center.getZ(), 30, 0.5, 0.7, 0.5, 0.5);

        puppet.getWorld().playSound(puppet.getLocation(),
                Sound.ENTITY_IRON_GOLEM_REPAIR, 1.0f, 1.2f);
        puppet.getWorld().playSound(puppet.getLocation(),
                Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.5f);

        String puppetName = puppet.customName() != null
                ? PlainTextComponentSerializer.plainText().serialize(puppet.customName())
                : "傀儡";

        player.sendMessage(Component.text("═══════════════════════", NamedTextColor.GOLD));
        player.sendMessage(Component.text("✦ 修复如初（傀儡） ✦", NamedTextColor.LIGHT_PURPLE));
        player.sendMessage(Component.text("  修复了 ", NamedTextColor.GRAY)
                .append(Component.text(puppetName, NamedTextColor.YELLOW)));
        player.sendMessage(Component.text("  恢复 ", NamedTextColor.GRAY)
                .append(Component.text((int) Math.ceil(missing) + " 点生命", NamedTextColor.GREEN)));
        player.sendMessage(Component.text("  消耗了 ", NamedTextColor.GRAY)
                .append(Component.text(expCost + " 点经验", NamedTextColor.YELLOW)));
        player.sendMessage(Component.text("═══════════════════════", NamedTextColor.GOLD));

        player.sendActionBar(Component.text("✦ 修复如初！傀儡 -" + expCost + " 经验",
                NamedTextColor.LIGHT_PURPLE));

        TauntUtils.increment(plugin, player, "reparo_used", 1);
        return true;
    }

    // ═══════════════════════════════════════════════
    //  疗愈咒
    // ═══════════════════════════════════════════════

    private boolean isLookingDown(Player player) {
        return player.getLocation().getPitch() > SELF_HEAL_LOOK_DOWN_THRESHOLD;
    }

    private void applySelfHeal(Player player, Spell spell, WandStats stats) {
        double healAmount = SELF_HEAL_AMOUNT * stats.damageMultiplier(spell);
        double maxHealth = player.getMaxHealth();
        double current = player.getHealth();
        double actualHeal = Math.min(healAmount, maxHealth - current);

        if (actualHeal > 0) {
            player.setHealth(Math.min(current + healAmount, maxHealth));
        }

        clearNegativeEffects(player);

        Location center = player.getLocation().add(0, 1, 0);
        for (int i = 0; i < 5; i++) applyOrb(player.getWorld(), spell, center, i * 4);
        TauntUtils.spawnParticleSafe(player.getWorld(), Particle.HEART,
                center.getX(), center.getY(), center.getZ(), 15, 0.6, 0.6, 0.6, 0.1);

        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.5f);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.8f);

        if (actualHeal > 0) {
            player.sendActionBar(Component.text("✦ 愈合如初！恢复了 "
                            + (int) Math.ceil(actualHeal) + " 点生命",
                    NamedTextColor.GREEN));
        } else {
            player.sendActionBar(Component.text("✦ 愈合如初！你已满血",
                    NamedTextColor.GREEN));
        }
    }

    private void hitEpiskey(Player caster, LivingEntity target, WandStats stats, Spell spell) {
        if (isUndead(target)) {
            caster.sendActionBar(Component.text("✦ 愈合如初对亡灵无效", NamedTextColor.GRAY));
            caster.playSound(caster.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.5f);
            return;
        }

        double healAmount = PROJECTILE_HEAL_AMOUNT * stats.damageMultiplier(spell);
        double maxHealth = target.getMaxHealth();
        double current = target.getHealth();
        double actualHeal = Math.min(healAmount, maxHealth - current);

        if (actualHeal > 0) {
            target.setHealth(Math.min(current + healAmount, maxHealth));
        }

        clearNegativeEffects(target);

        Location center = target.getLocation().add(0, 1, 0);
        for (int i = 0; i < 5; i++) applyOrb(target.getWorld(), spell, center, i * 4);
        TauntUtils.spawnParticleSafe(target.getWorld(), Particle.HEART,
                center.getX(), center.getY(), center.getZ(), 15, 0.6, 0.6, 0.6, 0.1);

        target.getWorld().playSound(target.getLocation(),
                Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.5f);
        target.getWorld().playSound(target.getLocation(),
                Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 2.0f);

        String targetName = buildEntityDisplayName(target);

        if (target.equals(caster)) {
            caster.sendActionBar(Component.text("✦ 愈合如初！治愈了自己",
                    NamedTextColor.GREEN));
        } else if (target instanceof Player playerTarget) {
            caster.sendActionBar(Component.text("✦ 愈合如初！治愈了 " + playerTarget.getName()
                            + "（+" + (int) Math.ceil(actualHeal) + "）",
                    NamedTextColor.GREEN));
            playerTarget.sendMessage(Component.text("💚 你被 " + caster.getName()
                            + " 治疗了（+" + (int) Math.ceil(actualHeal) + " 生命）",
                    NamedTextColor.GREEN));

            // ★ 善行 +1
            addRedemption(caster, REDEMPTION_PER_HEAL);
        } else {
            caster.sendActionBar(Component.text("✦ 愈合如初！治愈了 " + targetName
                            + "（+" + (int) Math.ceil(actualHeal) + "）",
                    NamedTextColor.GREEN));

            // ★ 治疗生物也算善行
            addRedemption(caster, REDEMPTION_PER_HEAL);
        }
    }

    private boolean isUndead(LivingEntity entity) {
        if (entity == null) return false;
        return UNDEAD_TYPES.contains(entity.getType());
    }

    private String buildEntityDisplayName(LivingEntity entity) {
        Component custom = entity.customName();
        if (custom != null) {
            return PlainTextComponentSerializer.plainText().serialize(custom);
        }
        EntityType type = entity.getType();
        String fallback = type.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        String[] parts = fallback.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            sb.append(Character.toUpperCase(part.charAt(0)))
                    .append(part.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }

    private void clearNegativeEffects(Player player) {
        clearNegativeEffects((LivingEntity) player);
    }

    private void clearNegativeEffects(LivingEntity entity) {
        PotionEffectType[] negative = {
                PotionEffectType.POISON,
                PotionEffectType.WITHER,
                PotionEffectType.NAUSEA,
                PotionEffectType.BLINDNESS,
                PotionEffectType.SLOWNESS,
                PotionEffectType.WEAKNESS,
                PotionEffectType.MINING_FATIGUE,
                PotionEffectType.HUNGER,
                PotionEffectType.UNLUCK,
                PotionEffectType.BAD_OMEN,
                PotionEffectType.DARKNESS
        };
        for (PotionEffectType type : negative) {
            if (entity.hasPotionEffect(type)) {
                entity.removePotionEffect(type);
            }
        }
    }

    // ═══════════════════════════════════════════════
    //  阿拉霍洞开
    // ═══════════════════════════════════════════════

    private void castAlohomora(Player player, Spell spell, WandStats stats) {
        RayTraceResult result;
        try {
            result = player.rayTraceBlocks(6.0);
        } catch (Throwable t) {
            player.sendActionBar(Component.text("✦ 阿拉霍洞开失败", NamedTextColor.RED));
            return;
        }

        if (result == null || result.getHitBlock() == null) {
            player.sendActionBar(Component.text("✦ 没有看向任何方块", NamedTextColor.GRAY));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.5f);
            return;
        }

        Block block = result.getHitBlock();
        Material type = block.getType();

        if (type != Material.IRON_DOOR && type != Material.IRON_TRAPDOOR) {
            player.sendActionBar(Component.text("✦ 只能打开铁门或铁活板门", NamedTextColor.GRAY));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.5f);
            return;
        }

        if (type == Material.IRON_TRAPDOOR) {
            if (!(block.getBlockData() instanceof Openable openable)) return;
            boolean wasOpen = openable.isOpen();
            openable.setOpen(!wasOpen);
            block.setBlockData(openable, false);

            playDoorSound(player, block, wasOpen);
            playDoorParticles(player, block);
            player.sendActionBar(Component.text("✦ 阿拉霍洞开！", NamedTextColor.GOLD));
            TauntUtils.unlock(plugin, player, AchievementManager.Ach.FIRST_SPELL);
            return;
        }

        if (!(block.getBlockData() instanceof Bisected bisected)) return;

        Block bottomBlock;
        Block topBlock;
        if (bisected.getHalf() == Bisected.Half.BOTTOM) {
            bottomBlock = block;
            topBlock = block.getRelative(BlockFace.UP);
        } else {
            topBlock = block;
            bottomBlock = block.getRelative(BlockFace.DOWN);
        }

        if (bottomBlock.getType() != Material.IRON_DOOR
                || topBlock.getType() != Material.IRON_DOOR) {
            if (!(block.getBlockData() instanceof Openable openable)) return;
            boolean wasOpen = openable.isOpen();
            openable.setOpen(!wasOpen);
            block.setBlockData(openable, false);

            playDoorSound(player, block, wasOpen);
            playDoorParticles(player, block);
            player.sendActionBar(Component.text("✦ 阿拉霍洞开！", NamedTextColor.GOLD));
            TauntUtils.unlock(plugin, player, AchievementManager.Ach.FIRST_SPELL);
            return;
        }

        if (!(bottomBlock.getBlockData() instanceof Openable bottomOpenable)) return;
        boolean wasOpen = bottomOpenable.isOpen();
        boolean newOpen = !wasOpen;

        if (bottomBlock.getBlockData() instanceof Openable bo) {
            bo.setOpen(newOpen);
            bottomBlock.setBlockData(bo, false);
        }
        if (topBlock.getBlockData() instanceof Openable to) {
            to.setOpen(newOpen);
            topBlock.setBlockData(to, false);
        }

        playDoorSound(player, bottomBlock, wasOpen);
        playDoorParticles(player, bottomBlock);

        player.sendActionBar(Component.text("✦ 阿拉霍洞开！", NamedTextColor.GOLD));
        TauntUtils.unlock(plugin, player, AchievementManager.Ach.FIRST_SPELL);
    }

    private void playDoorSound(Player player, Block block, boolean wasOpen) {
        Location loc = block.getLocation().add(0.5, 0.5, 0.5);

        if (block.getType() == Material.IRON_TRAPDOOR) {
            player.getWorld().playSound(loc,
                    wasOpen ? Sound.BLOCK_IRON_TRAPDOOR_CLOSE : Sound.BLOCK_IRON_TRAPDOOR_OPEN,
                    1.0f, 1.0f);
        } else {
            player.getWorld().playSound(loc,
                    wasOpen ? Sound.BLOCK_IRON_DOOR_CLOSE : Sound.BLOCK_IRON_DOOR_OPEN,
                    1.0f, 1.0f);
        }
        player.getWorld().playSound(loc, Sound.BLOCK_ENDER_CHEST_OPEN, 0.4f, 1.8f);
    }

    private void playDoorParticles(Player player, Block block) {
        Location loc = block.getLocation().add(0.5, 0.5, 0.5);
        for (int i = 0; i < 20; i++) {
            double angle = i * Math.PI / 10;
            Location pl = loc.clone().add(
                    Math.cos(angle) * 0.8,
                    Math.sin(angle * 2) * 0.4,
                    Math.sin(angle) * 0.8);
            TauntUtils.spawnParticleSafe(player.getWorld(), Particle.END_ROD,
                    pl.getX(), pl.getY(), pl.getZ(), 1, 0, 0, 0, 0.05);
            TauntUtils.spawnParticleSafe(player.getWorld(), Particle.ENCHANT,
                    pl.getX(), pl.getY(), pl.getZ(), 1, 0, 0, 0, 0.1);
        }
    }

    // ═══════════════════════════════════════════════
    //  护身咒
    // ═══════════════════════════════════════════════

    private void activateShield(Player player) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        Long cd = shieldCooldownUntil.get(uuid);
        if (cd != null && cd > now) {
            long remain = cd - now;
            player.sendActionBar(Component.text(
                    "✦ 护身咒冷却中，还需 " + String.format("%.1f", remain / 1000.0) + " 秒",
                    NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.5f);
            return;
        }

        if (shieldActive.contains(uuid)) {
            player.sendActionBar(Component.text("✦ 护身咒已经激活", NamedTextColor.AQUA));
            return;
        }

        shieldActive.add(uuid);

        player.sendActionBar(Component.text("✦ 护身咒！等待格挡", NamedTextColor.AQUA));
        player.playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.2f, 0.8f);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.5f);

        BukkitTask old = shieldParticleTasks.remove(uuid);
        if (old != null) old.cancel();

        final int[] tick = {0};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            tick[0]++;

            if (!player.isOnline() || !shieldActive.contains(uuid)) {
                BukkitTask self = shieldParticleTasks.remove(uuid);
                if (self != null) self.cancel();
                return;
            }

            Location center = player.getLocation().add(0, 1, 0);
            double rot = tick[0] * 0.15;
            for (int i = 0; i < 8; i++) {
                double angle = i * Math.PI / 4 + rot;
                double r = 1.0;
                Location pl = center.clone().add(
                        Math.cos(angle) * r,
                        Math.sin(tick[0] * 0.3 + i) * 0.2,
                        Math.sin(angle) * r);
                TauntUtils.spawnParticleSafe(player.getWorld(), Particle.END_ROD,
                        pl.getX(), pl.getY(), pl.getZ(), 1, 0, 0, 0, 0);
                if (tick[0] % 2 == 0) {
                    TauntUtils.spawnParticleSafe(player.getWorld(), Particle.ENCHANT,
                            pl.getX(), pl.getY(), pl.getZ(), 1, 0, 0, 0, 0.05);
                }
            }
        }, 0L, 3L);
        shieldParticleTasks.put(uuid, task);
    }

    private boolean tryBlockWithShield(Player player, boolean isAvada) {
        UUID uuid = player.getUniqueId();
        if (!shieldActive.contains(uuid)) return false;

        shieldActive.remove(uuid);
        BukkitTask t = shieldParticleTasks.remove(uuid);
        if (t != null) t.cancel();
        shieldCooldownUntil.put(uuid, System.currentTimeMillis() + SHIELD_COOLDOWN_MS);

        Location loc = player.getLocation().add(0, 1, 0);
        player.sendActionBar(Component.text("✦ 护身咒格挡了伤害！", NamedTextColor.GREEN));
        player.playSound(loc, Sound.ITEM_SHIELD_BLOCK, 1.5f, 1.0f);
        player.playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.5f);
        player.playSound(loc, Sound.BLOCK_ANVIL_LAND, 0.5f, 1.8f);

        for (int i = 0; i < 30; i++) {
            double angle = i * Math.PI / 15;
            Location pl = loc.clone().add(
                    Math.cos(angle) * 1.2,
                    Math.sin(angle * 2) * 0.4,
                    Math.sin(angle) * 1.2);
            TauntUtils.spawnParticleSafe(player.getWorld(), Particle.END_ROD,
                    pl.getX(), pl.getY(), pl.getZ(), 2, 0.1, 0.1, 0.1, 0.1);
            TauntUtils.spawnParticleSafe(player.getWorld(), Particle.CRIT,
                    pl.getX(), pl.getY(), pl.getZ(), 2, 0.1, 0.1, 0.1, 0.1);
        }

        if (isAvada) {
            TauntUtils.increment(plugin, player, "survived_avada_count", 1);
            TauntUtils.unlock(plugin, player, AchievementManager.Ach.SURVIVED_AVADA);
        }

        return true;
    }

    public boolean isShieldActive(UUID uuid) {
        return shieldActive.contains(uuid);
    }

    // ═══════════════════════════════════════════════
    //  投射物
    // ═══════════════════════════════════════════════

    private ArmorStand spawnProjectileMarker(Player caster) {
        Location start = caster.getEyeLocation().clone();
        start.add(start.getDirection().normalize().multiply(1.0));

        try {
            return caster.getWorld().spawn(start, ArmorStand.class, as -> {
                as.setInvisible(true);
                as.setMarker(true);
                as.setGravity(false);
                as.setInvulnerable(true);
                as.setSmall(true);
                as.setSilent(true);
                as.setPersistent(false);
                as.setCollidable(false);
                as.setBasePlate(false);
                as.setArms(false);
                as.getPersistentDataContainer().set(projectileKey, PersistentDataType.BYTE, (byte) 1);
                as.getPersistentDataContainer().set(projectileOwnerKey,
                        PersistentDataType.STRING, caster.getUniqueId().toString());
            });
        } catch (Throwable t) {
            plugin.getLogger().warning("[魔杖] 生成投射物失败: " + t.getMessage());
            return null;
        }
    }

    private void launchProjectile(Player caster, Spell spell, WandStats stats) {
        UUID uuid = caster.getUniqueId();

        int current = activeProjectiles.getOrDefault(uuid, 0);
        if (current >= maxActiveProjectilesPerPlayer) {
            caster.sendActionBar(Component.text("✦ 你施法太快了", NamedTextColor.RED));
            return;
        }
        activeProjectiles.merge(uuid, 1, Integer::sum);

        ArmorStand marker = spawnProjectileMarker(caster);
        if (marker == null) {
            activeProjectiles.merge(uuid, -1, Integer::sum);
            return;
        }

        final long startTime = System.currentTimeMillis();
        final double speed = spell.speed * stats.speedMultiplier(spell);
        final int[] tick = {0};

        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (System.currentTimeMillis() - startTime > maxFlightTimeMs
                        || !caster.isOnline()
                        || !marker.isValid()) {
                    finish();
                    return;
                }

                Location eye = caster.getEyeLocation();
                Vector dir = eye.getDirection().normalize();

                Location from = marker.getLocation();
                Location to = from.clone().add(dir.clone().multiply(speed));

                RayTraceResult entityHit = from.getWorld().rayTraceEntities(
                        from, dir, speed, 0.5,
                        e -> !e.equals(caster)
                                && e.isValid()
                                && !isProjectileMarker(e)
                                && (e instanceof LivingEntity || e instanceof Item));

                RayTraceResult blockHit = from.getWorld().rayTraceBlocks(
                        from, dir, speed, FluidCollisionMode.NEVER, true);

                double blockDist = (blockHit != null)
                        ? blockHit.getHitPosition().distance(from.toVector())
                        : Double.MAX_VALUE;
                double entityDist = (entityHit != null)
                        ? entityHit.getHitPosition().distance(from.toVector())
                        : Double.MAX_VALUE;

                if (entityHit != null && entityDist < blockDist) {
                    Entity hitEntity = entityHit.getHitEntity();
                    if (hitEntity != null) {
                        Location hitLoc = entityHit.getHitPosition().toLocation(from.getWorld());
                        spawnImpactEffect(hitLoc, spell);
                        onProjectileHit(caster, spell, stats, hitEntity, hitLoc);
                        finish();
                        return;
                    }
                }

                if (blockHit != null) {
                    Location hitLoc = blockHit.getHitPosition().toLocation(from.getWorld());

                    if (spell == Spell.INCENDIO) {
                        igniteBlock(hitLoc);
                    }

                    if (spell == Spell.BOMBARDA) {
                        triggerBombarda(caster, hitLoc, stats, spell);
                        finish();
                        return;
                    }

                    spawnImpactEffect(hitLoc, spell);
                    caster.sendActionBar(Component.text("✦ 命中方块", NamedTextColor.GRAY));
                    finish();
                    return;
                }

                marker.teleport(to);
                spawnTrail(marker.getWorld(), spell, to, dir, tick[0]++);
            }

            private void finish() {
                cancel();
                int thisId = getTaskId();
                activeTasks.removeIf(t -> t.getTaskId() == thisId);
                if (marker.isValid()) marker.remove();
                int remain = activeProjectiles.merge(uuid, -1, Integer::sum);
                if (remain <= 0) activeProjectiles.remove(uuid);
            }
        };

        BukkitTask t = task.runTaskTimer(plugin, 0L, 1L);
        activeTasks.add(t);
    }

    private boolean isProjectileMarker(Entity entity) {
        if (entity == null) return false;
        return entity.getPersistentDataContainer().has(projectileKey, PersistentDataType.BYTE);
    }

    // ═══════════════════════════════════════════════
    //  爆炸咒
    // ═══════════════════════════════════════════════

    private void triggerBombarda(Player caster, Location hitLoc, WandStats stats, Spell spell) {
        World world = hitLoc.getWorld();
        if (world == null) return;

        float power = (float) (BOMBARDA_EXPLOSION_POWER * stats.damageMultiplier(spell));

        world.playSound(hitLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.2f);
        world.playSound(hitLoc, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 0.6f, 1.5f);

        for (int i = 0; i < 30; i++) {
            double angle = i * Math.PI / 15;
            Location pl = hitLoc.clone().add(
                    Math.cos(angle) * 1.0,
                    Math.sin(angle * 2) * 0.5,
                    Math.sin(angle) * 1.0);
            TauntUtils.spawnParticleSafe(world, Particle.FLAME,
                    pl.getX(), pl.getY(), pl.getZ(), 2, 0.1, 0.1, 0.1, 0.05);
        }

        world.createExplosion(
                hitLoc.getX(), hitLoc.getY(), hitLoc.getZ(),
                power,
                false,
                true,
                caster
        );

        caster.sendActionBar(Component.text("✦ 爆炸咒！", NamedTextColor.DARK_RED));
        caster.playSound(caster.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.5f);
    }

    // ═══════════════════════════════════════════════
    //  ★ 火焰咒点燃一切可燃方块
    // ═══════════════════════════════════════════════

    private void igniteBlock(Location hitLoc) {
        World world = hitLoc.getWorld();
        if (world == null) return;

        Block base = hitLoc.getBlock();

        // 优先在命中位置点燃
        if (tryPlaceFire(base)) return;

        // 尝试命中位置上方
        Block up = base.getRelative(BlockFace.UP);
        if (tryPlaceFire(up)) return;

        // 尝试周围的易燃方块上方
        BlockFace[] sides = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
        for (BlockFace face : sides) {
            Block side = base.getRelative(face);
            if (side.getType().isBurnable()) {
                Block sideUp = side.getRelative(BlockFace.UP);
                if (tryPlaceFire(sideUp)) return;
            }
        }

        // 最后：如果命中方块本身是可燃的，直接把它点燃
        if (base.getType().isBurnable()) {
            base.setType(Material.FIRE);
            world.playSound(base.getLocation(), Sound.ITEM_FIRECHARGE_USE, 0.8f, 1.5f);
        }
    }

    private boolean tryPlaceFire(Block block) {
        if (!block.getType().isAir()) return false;
        Block below = block.getRelative(BlockFace.DOWN);
        if (below.getType().isSolid() || below.getType().isBurnable()) {
            block.setType(Material.FIRE);
            block.getWorld().playSound(block.getLocation(),
                    Sound.ITEM_FIRECHARGE_USE, 0.8f, 1.5f);
            return true;
        }
        return false;
    }

    // ═══════════════════════════════════════════════
    //  粒子系统
    // ═══════════════════════════════════════════════

    private void spawnTrail(World world, Spell spell, Location loc, Vector dir, int tick) {
        switch (spell.trailStyle) {
            case STRAIGHT -> applyStraight(world, spell, loc, dir);
            case SPIRAL   -> applySpiral(world, spell, loc, dir, tick);
            case HELIX    -> applyHelix(world, spell, loc, dir, tick);
            case RING     -> applyRing(world, spell, loc, dir, tick);
            case ORBIT    -> applyOrbit(world, spell, loc, tick);
            case FLAME    -> applyFlame(world, spell, loc);
            case ORB      -> applyOrb(world, spell, loc, tick);
            case CASCADE  -> applyCascade(world, spell, loc);
        }
    }

    private void applyStraight(World world, Spell spell, Location loc, Vector dir) {
        for (double d = -0.4; d <= 0.4; d += 0.15) {
            Location p = loc.clone().add(dir.clone().multiply(d));
            emit(world, spell.primaryParticle, p, spell.primaryColor, spell.particleSize);
        }
        emit(world, spell.secondaryParticle, loc, spell.secondaryColor, spell.particleSize);
    }

    private void applySpiral(World world, Spell spell, Location loc, Vector dir, int tick) {
        Vector[] basis = perpendicularBasis(dir);
        double angle = tick * 0.55;
        double radius = 0.32;

        for (int i = 0; i < 2; i++) {
            double a = angle + i * Math.PI;
            Location p = loc.clone()
                    .add(basis[0].clone().multiply(Math.cos(a) * radius))
                    .add(basis[1].clone().multiply(Math.sin(a) * radius));
            emit(world, spell.primaryParticle, p, spell.primaryColor, spell.particleSize);
        }
        emit(world, spell.secondaryParticle, loc, spell.secondaryColor, spell.particleSize);
    }

    private void applyHelix(World world, Spell spell, Location loc, Vector dir, int tick) {
        Vector[] basis = perpendicularBasis(dir);
        double angle = tick * 0.5;
        double radius = 0.35;

        for (int strand = 0; strand < 2; strand++) {
            double a = angle + strand * Math.PI;
            Location p = loc.clone()
                    .add(basis[0].clone().multiply(Math.cos(a) * radius))
                    .add(basis[1].clone().multiply(Math.sin(a) * radius));
            emit(world, spell.primaryParticle, p, spell.primaryColor, spell.particleSize);
        }
        emit(world, spell.secondaryParticle, loc, spell.secondaryColor, spell.particleSize);
    }

    private void applyRing(World world, Spell spell, Location loc, Vector dir, int tick) {
        Vector[] basis = perpendicularBasis(dir);
        double radius = 0.45;
        double rotOffset = tick * 0.12;

        for (int i = 0; i < 8; i++) {
            double a = i * (Math.PI * 2 / 8) + rotOffset;
            Location p = loc.clone()
                    .add(basis[0].clone().multiply(Math.cos(a) * radius))
                    .add(basis[1].clone().multiply(Math.sin(a) * radius));
            emit(world, spell.primaryParticle, p, spell.primaryColor, spell.particleSize);
        }
        emit(world, spell.secondaryParticle, loc, spell.secondaryColor, spell.particleSize);
    }

    private void applyOrbit(World world, Spell spell, Location loc, int tick) {
        int count = 6;
        double radius = 0.38;
        double goldenAngle = Math.PI * (1 + Math.sqrt(5));

        for (int i = 0; i < count; i++) {
            double phi = Math.acos(1 - 2 * (i + 0.5) / count);
            double theta = goldenAngle * i + tick * 0.15;
            double x = radius * Math.sin(phi) * Math.cos(theta);
            double y = radius * Math.cos(phi);
            double z = radius * Math.sin(phi) * Math.sin(theta);
            emit(world, spell.primaryParticle, loc.clone().add(x, y, z),
                    spell.primaryColor, spell.particleSize);
        }
        emit(world, spell.secondaryParticle, loc, spell.secondaryColor, spell.particleSize);
    }

    private void applyFlame(World world, Spell spell, Location loc) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int i = 0; i < 4; i++) {
            Location p = loc.clone().add(
                    (rng.nextDouble() - 0.5) * 0.4,
                    rng.nextDouble() * 0.5,
                    (rng.nextDouble() - 0.5) * 0.4);
            emit(world, spell.primaryParticle, p, null, spell.particleSize);
        }
        if (rng.nextDouble() < 0.4) {
            emit(world, spell.secondaryParticle, loc, null, spell.particleSize);
        }
    }

    private void applyOrb(World world, Spell spell, Location loc, int tick) {
        int count = 10;
        double radius = 0.32;
        double goldenAngle = Math.PI * (1 + Math.sqrt(5));

        for (int i = 0; i < count; i++) {
            double phi = Math.acos(1 - 2 * (i + 0.5) / count);
            double theta = goldenAngle * i + tick * 0.1;
            double x = radius * Math.sin(phi) * Math.cos(theta);
            double y = radius * Math.cos(phi);
            double z = radius * Math.sin(phi) * Math.sin(theta);
            emit(world, spell.primaryParticle, loc.clone().add(x, y, z),
                    spell.primaryColor, spell.particleSize);
        }
    }

    private void applyCascade(World world, Spell spell, Location loc) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int i = 0; i < 5; i++) {
            Location p = loc.clone().add(
                    (rng.nextDouble() - 0.5) * 0.5,
                    -rng.nextDouble() * 0.6,
                    (rng.nextDouble() - 0.5) * 0.5);
            emit(world, spell.primaryParticle, p, spell.primaryColor, spell.particleSize);
        }
        if (rng.nextDouble() < 0.5) {
            emit(world, spell.secondaryParticle, loc, spell.secondaryColor, spell.particleSize);
        }
    }

    private void emit(World world, Particle particle, Location loc,
                      Color color, float size) {
        if (world == null || particle == null || loc == null) return;
        try {
            Class<?> dataType = particle.getDataType();
            if (dataType == Particle.DustOptions.class) {
                Color c = (color != null) ? color : Color.WHITE;
                world.spawnParticle(particle, loc, 1, 0, 0, 0, 0,
                        new Particle.DustOptions(c, Math.max(0.5f, size)));
            } else if (dataType == Particle.DustTransition.class) {
                Color c = (color != null) ? color : Color.WHITE;
                world.spawnParticle(particle, loc, 1, 0, 0, 0, 0,
                        new Particle.DustTransition(c, Color.WHITE, Math.max(0.5f, size)));
            } else if (dataType == Void.class) {
                world.spawnParticle(particle, loc, 1, 0, 0, 0, 0);
            }
        } catch (Throwable ignored) {}
    }

    private Vector[] perpendicularBasis(Vector dir) {
        Vector d = dir.clone().normalize();
        Vector reference = new Vector(0, 1, 0);
        if (Math.abs(d.dot(reference)) > 0.9) {
            reference = new Vector(1, 0, 0);
        }
        Vector right = d.clone().crossProduct(reference).normalize();
        Vector up = right.clone().crossProduct(d).normalize();
        return new Vector[]{right, up};
    }

    private void spawnImpactEffect(Location hitLoc, Spell spell) {
        World world = hitLoc.getWorld();
        if (world == null) return;

        for (int i = 0; i < 3; i++) {
            spawnTrail(world, spell, hitLoc, new Vector(0, 1, 0), i * 7);
        }
        emit(world, Particle.END_ROD, hitLoc, null, 1.0f);
        world.playSound(hitLoc, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.8f, 1.5f);
    }

    // ═══════════════════════════════════════════════
    //  命中分派
    // ═══════════════════════════════════════════════

    private void onProjectileHit(Player caster, Spell spell, WandStats stats,
                                 Entity target, Location hitLoc) {
        if (target instanceof Item item) {
            onItemHit(caster, spell, stats, item, hitLoc);
            return;
        }

        if (spell == Spell.EXPELLIARMUS) {
            if (target instanceof Player playerTarget) {
                hitExpelliarmus(caster, playerTarget, hitLoc);
            } else {
                caster.sendActionBar(Component.text("✦ 除你武器对非玩家无效", NamedTextColor.GRAY));
            }
            return;
        }

        if (spell == Spell.EPISKEY) {
            if (target instanceof LivingEntity living) {
                hitEpiskey(caster, living, stats, spell);
            } else {
                caster.sendActionBar(Component.text("✦ 愈合如初只能作用于生物", NamedTextColor.GRAY));
            }
            return;
        }

        if (spell == Spell.BOMBARDA) {
            triggerBombarda(caster, hitLoc, stats, spell);
            return;
        }

        if (!(target instanceof LivingEntity living)) return;

        switch (spell) {
            case ACCIO -> hitAccio(caster, living, stats, spell);
            case STUPEFY -> hitStupefy(caster, living, stats, spell);
            case PETRIFICUS -> hitPetrificus(caster, living, stats, spell);
            case INCENDIO -> hitIncendio(caster, living, stats, spell);
            case DIFFINDO -> hitDiffindo(caster, living, stats, spell);
            case LEVIOSA -> hitLeviosa(caster, living, stats, spell);
            case LEVICORPUS -> hitLevicorpus(caster, living, stats, spell);
            case RICTUSEMPRA -> hitRictusempra(caster, living, stats, spell);
            case SILENCIO -> hitSilencio(caster, living, stats, spell);
            case LANGOCK -> hitLangock(caster, living, stats, spell);
            case AVADA_KEDAVRA -> hitAvadaKedavra(caster, living);
            case IMPERIO -> hitImperio(caster, living, stats, spell);
            case CRUCIO -> hitCrucio(caster, living, stats, spell);
            default -> {}
        }
    }

    // ═══════════════════════════════════════════════
    //  ★ 不可饶恕咒：夺魂咒 / 钻心咒
    // ═══════════════════════════════════════════════

    private void hitImperio(Player caster, LivingEntity target, WandStats stats, Spell spell) {
        // 对玩家：施加控制效果
        if (target instanceof Player p) {
            int dur = scaleDuration(100, stats, spell);
            p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, dur, 1, false, true));
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, dur, 3, false, true));
            p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, dur, 2, false, true));
            p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, dur, 2, false, true));

            p.sendMessage(Component.text("💜 你被 " + caster.getName() + " 的夺魂咒控制了！",
                    NamedTextColor.DARK_PURPLE));
            p.sendActionBar(Component.text("💜 被夺魂咒控制", NamedTextColor.DARK_PURPLE));

            caster.sendActionBar(Component.text("✦ 夺魂咒命中 " + p.getName(),
                    NamedTextColor.DARK_PURPLE));
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_STARE, 1.0f, 0.5f);
            return;
        }

        // 亡灵无效
        if (isUndead(target)) {
            caster.sendActionBar(Component.text("✦ 夺魂咒对亡灵无效", NamedTextColor.GRAY));
            return;
        }

        // 对怪物：控制
        if (target instanceof Mob mob) {
            UUID casterId = caster.getUniqueId();
            long duration = scaleDurationMs(15_000L, stats, spell);
            imperioTargets.put(mob.getUniqueId(), new ImperioTarget(casterId, duration));

            mob.setTarget(null);

            // 加紫色标记
            if (mob.customName() == null) {
                mob.customName(Component.text("💜 ", NamedTextColor.DARK_PURPLE)
                        .append(Component.text(buildEntityDisplayName(mob),
                                NamedTextColor.LIGHT_PURPLE)));
                mob.setCustomNameVisible(true);
            }

            caster.sendActionBar(Component.text("✦ 夺魂咒命中！操控了 "
                            + buildEntityDisplayName(mob),
                    NamedTextColor.DARK_PURPLE));
            mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_ENDERMAN_STARE, 1.0f, 0.5f);
            mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_WITCH_AMBIENT, 1.0f, 0.5f);
            return;
        }

        caster.sendActionBar(Component.text("✦ 夺魂咒只对生物有效", NamedTextColor.GRAY));
    }

    private void hitCrucio(Player caster, LivingEntity target, WandStats stats, Spell spell) {
        int dur = scaleDuration(100, stats, spell); // 5 秒

        target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, dur, 1, false, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, dur, 4, false, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, dur, 3, false, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, dur, 0, false, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, dur, 1, false, true));

        double dmg = 6.0 * stats.damageMultiplier(spell);
        target.damage(dmg, caster);

        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.5f, 0.5f);
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_WITHER_HURT, 0.8f, 1.5f);

        // 疼痛粒子
        Location center = target.getLocation().add(0, 1, 0);
        for (int i = 0; i < 40; i++) {
            double angle = i * Math.PI / 20;
            Location pl = center.clone().add(
                    Math.cos(angle) * 1.2,
                    Math.sin(angle * 3) * 0.5,
                    Math.sin(angle) * 1.2);
            TauntUtils.spawnParticleSafe(target.getWorld(), Particle.DUST,
                    pl.getX(), pl.getY(), pl.getZ(), 1, 0, 0, 0, 0,
                    new Particle.DustOptions(Color.fromRGB(220, 0, 0), 1.5f));
        }

        // 持续疼痛
        final int[] ticks = {0};
        final BukkitTask[] holder = new BukkitTask[1];
        holder[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            ticks[0]++;
            if (ticks[0] > dur || !target.isValid() || target.isDead()) {
                holder[0].cancel();
                activeTasks.remove(holder[0]);
                return;
            }
            if (ticks[0] % 5 == 0) {
                Location loc = target.getLocation().add(0, 1, 0);
                TauntUtils.spawnParticleSafe(target.getWorld(), Particle.DUST,
                        loc.getX(), loc.getY(), loc.getZ(), 10, 0.4, 0.4, 0.4, 0.1,
                        new Particle.DustOptions(Color.fromRGB(220, 0, 0), 1.2f));
                if (target instanceof Player p) {
                    p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.6f, 0.8f);
                }
            }
        }, 0L, 1L);
        activeTasks.add(holder[0]);

        caster.sendActionBar(Component.text("✦ 钻心咒！", NamedTextColor.DARK_RED));
    }

    // ═══════════════════════════════════════════════
    //  物品实体命中处理器
    // ═══════════════════════════════════════════════

    private void onItemHit(Player caster, Spell spell, WandStats stats,
                           Item item, Location hitLoc) {
        World world = item.getWorld();
        Location itemLoc = item.getLocation();
        double power = stats.damageMultiplier(spell);

        switch (spell) {
            case EXPELLIARMUS -> {
                Vector toCaster = caster.getLocation().toVector()
                        .subtract(itemLoc.toVector());
                toCaster.setY(0);
                if (toCaster.lengthSquared() > 0.01) toCaster.normalize();
                toCaster.multiply(1.2 * power);
                toCaster.setY(0.4);
                item.setPickupDelay(0);
                item.setVelocity(toCaster);
                caster.sendActionBar(Component.text("✦ 除你武器！物品被推向了你",
                        NamedTextColor.RED));
            }
            case STUPEFY -> {
                item.setVelocity(new Vector(0, 0, 0));
                item.setGravity(false);
                item.setSilent(true);
                final Item fi = item;
                BukkitTask r = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (fi.isValid()) { fi.setGravity(true); fi.setSilent(false); }
                }, 60L);
                activeTasks.add(r);
                caster.sendActionBar(Component.text("✦ 昏昏倒地！物品被打晕了",
                        NamedTextColor.YELLOW));
            }
            case PETRIFICUS -> {
                item.setVelocity(new Vector(0, 0, 0));
                item.setGravity(false);
                item.setFireTicks(0);
                item.setGlowing(true);
                final Item fi = item;
                BukkitTask r = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (fi.isValid()) { fi.setGravity(true); fi.setGlowing(false); }
                }, 100L);
                activeTasks.add(r);
                caster.sendActionBar(Component.text("✦ 统统石化！物品被冻结了",
                        NamedTextColor.GRAY));
            }
            case INCENDIO -> {
                int fireTicks = (int) (200 * stats.durationMultiplier(spell));
                item.setFireTicks(fireTicks);
                caster.sendActionBar(Component.text("✦ 火焰熊熊！物品着火了",
                        NamedTextColor.GOLD));
            }
            case BOMBARDA -> triggerBombarda(caster, itemLoc, stats, spell);
            case DIFFINDO -> {
                Vector away = itemLoc.toVector().subtract(caster.getLocation().toVector());
                away.setY(0);
                if (away.lengthSquared() > 0.01) away.normalize();
                away.multiply(2.5 * power);
                away.setY(0.6);
                item.setVelocity(away);
                caster.sendActionBar(Component.text("✦ 四分五裂！物品被击飞了",
                        NamedTextColor.DARK_RED));
            }
            case ACCIO -> {
                Location casterLoc = caster.getLocation();
                Vector facing = casterLoc.getDirection().setY(0);
                if (facing.lengthSquared() < 0.01) facing = new Vector(1, 0, 0);
                else facing.normalize();
                Location destLoc = casterLoc.clone().add(facing.clone().multiply(1.0));

                double dx = destLoc.getX() - itemLoc.getX();
                double dz = destLoc.getZ() - itemLoc.getZ();
                double dy = destLoc.getY() - itemLoc.getY();
                double hd = Math.sqrt(dx * dx + dz * dz);

                Vector dir = hd > 0.1 ? new Vector(dx, 0, dz).normalize() : facing.clone();
                double hSpeed = Math.min(0.8 + hd * 0.06, 2.0) * power;
                double vSpeed = 0.4 + Math.min(hd * 0.04, 0.6);
                if (dy > 0) vSpeed += dy * 0.08;

                item.setPickupDelay(0);
                item.setCanMobPickup(false);
                Vector v = dir.multiply(hSpeed);
                v.setY(vSpeed);
                item.setVelocity(v);

                caster.sendActionBar(Component.text("✦ 飞来！物品正飞向你",
                        NamedTextColor.LIGHT_PURPLE));
            }
            case LEVIOSA -> {
                item.setGravity(false);
                item.setVelocity(new Vector(0, 0.5, 0));
                final Item fi = item;
                BukkitTask r = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (fi.isValid()) fi.setGravity(true);
                }, 60L);
                activeTasks.add(r);
                caster.sendActionBar(Component.text("✦ 羽加迪姆勒维奥萨！物品漂浮了",
                        NamedTextColor.GREEN));
            }
            case LEVICORPUS -> {
                item.setGravity(false);
                item.setVelocity(new Vector(0, 0.6, 0));
                startItemJitter(item, 60);
                final Item fi = item;
                BukkitTask r = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (fi.isValid()) fi.setGravity(true);
                }, 60L);
                activeTasks.add(r);
                caster.sendActionBar(Component.text("✦ 倒挂金钟！物品被倒吊起来",
                        NamedTextColor.DARK_PURPLE));
            }
            case RICTUSEMPRA -> {
                startItemJitter(item, 100);
                caster.sendActionBar(Component.text("✦ 咧嘴呼啦啦！物品在疯狂抖动",
                        NamedTextColor.LIGHT_PURPLE));
            }
            case SILENCIO -> {
                item.setVelocity(new Vector(0, 0, 0));
                item.setSilent(true);
                final Item fi = item;
                BukkitTask r = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (fi.isValid()) fi.setSilent(false);
                }, 60L);
                activeTasks.add(r);
                caster.sendActionBar(Component.text("✦ 无声无息！物品安静了",
                        NamedTextColor.DARK_GRAY));
            }
            case LANGOCK -> {
                item.setVelocity(new Vector(0, 0, 0));
                item.setGravity(false);
                final Item fi = item;
                BukkitTask r = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (fi.isValid()) fi.setGravity(true);
                }, 100L);
                activeTasks.add(r);
                caster.sendActionBar(Component.text("✦ 锁舌封喉！物品被锁住了",
                        NamedTextColor.DARK_GRAY));
            }
            case AVADA_KEDAVRA -> {
                TauntUtils.spawnParticleSafe(world, Particle.SOUL_FIRE_FLAME,
                        itemLoc.getX(), itemLoc.getY(), itemLoc.getZ(),
                        40, 0.5, 0.5, 0.5, 0.1);
                world.playSound(itemLoc, Sound.ENTITY_WITHER_HURT, 1.0f, 1.5f);
                item.remove();
                caster.sendActionBar(Component.text("✦ 阿瓦达索命！物品被摧毁了",
                        NamedTextColor.DARK_GREEN));
            }
            case IMPERIO -> {
                // 物品无法被夺魂
                caster.sendActionBar(Component.text("✦ 夺魂咒对物品无效", NamedTextColor.GRAY));
            }
            case CRUCIO -> {
                // 物品痛不欲生
                startItemJitter(item, 100);
                item.setFireTicks(60);
                caster.sendActionBar(Component.text("✦ 钻心咒！物品被折磨",
                        NamedTextColor.DARK_RED));
            }
            default -> {}
        }
    }

    private void startItemJitter(Item item, int durationTicks) {
        final int[] tick = {0};
        final Random rng = new Random();
        final BukkitTask[] holder = new BukkitTask[1];

        holder[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            tick[0]++;
            if (!item.isValid() || tick[0] > durationTicks) {
                holder[0].cancel();
                activeTasks.remove(holder[0]);
                return;
            }
            Vector v = new Vector(
                    (rng.nextDouble() - 0.5) * 0.4,
                    (rng.nextDouble() - 0.3) * 0.3,
                    (rng.nextDouble() - 0.5) * 0.4);
            item.setVelocity(v);
        }, 0L, 2L);
        activeTasks.add(holder[0]);
    }

    // ═══════════════════════════════════════════════
    //  生物版本咒语
    // ═══════════════════════════════════════════════

    private void hitExpelliarmus(Player caster, Player targetPlayer, Location hitLoc) {
        ItemStack item = targetPlayer.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            caster.sendActionBar(Component.text("✦ 目标手中没有物品", NamedTextColor.GRAY));
            return;
        }

        ItemStack dropped = item.clone();
        targetPlayer.getInventory().setItemInMainHand(null);
        targetPlayer.sendMessage(Component.text("⚡ 你的武器被击落了！", NamedTextColor.RED));

        Location spawnLoc = hitLoc.clone();
        Item itemEntity = targetPlayer.getWorld().dropItem(spawnLoc, dropped);
        itemEntity.setPickupDelay(100);
        itemEntity.setCanMobPickup(false);
        itemEntity.setGravity(true);

        Location casterAim = caster.getLocation().add(0, 1.0, 0);
        double dx = casterAim.getX() - spawnLoc.getX();
        double dz = casterAim.getZ() - spawnLoc.getZ();
        double dy = casterAim.getY() - spawnLoc.getY();
        double hd = Math.sqrt(dx * dx + dz * dz);

        Vector horizontalDir = hd > 0.01
                ? new Vector(dx, 0, dz).normalize()
                : caster.getLocation().getDirection().setY(0).normalize();

        double hSpeed = Math.min(0.35 + hd * 0.06, 1.8);
        double vSpeed = 0.45 + Math.max(0, dy) * 0.08 + Math.min(hd * 0.02, 0.3);

        Vector initV = horizontalDir.multiply(hSpeed);
        initV.setY(vSpeed);
        itemEntity.setVelocity(initV);

        startItemHoming(caster, itemEntity);
        caster.sendActionBar(Component.text("✦ 除你武器！", NamedTextColor.RED));
    }

    private void startItemHoming(Player caster, Item itemEntity) {
        final UUID casterId = caster.getUniqueId();
        final long startTime = System.currentTimeMillis();
        final long MAX_FLIGHT_MS = 6_000L;
        final int[] ticks = {0};

        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                ticks[0]++;
                if (!itemEntity.isValid() || itemEntity.isDead()
                        || System.currentTimeMillis() - startTime > MAX_FLIGHT_MS) {
                    cancel();
                    return;
                }
                Player c = Bukkit.getPlayer(casterId);
                if (c == null || !c.isOnline()) { cancel(); return; }

                Location itemLoc = itemEntity.getLocation();
                Location aimLoc = c.getLocation().add(0, 1.0, 0);
                double cx = aimLoc.getX() - itemLoc.getX();
                double cy = aimLoc.getY() - itemLoc.getY();
                double cz = aimLoc.getZ() - itemLoc.getZ();
                double distSq = cx * cx + cy * cy + cz * cz;

                if (distSq < 2.5) {
                    ItemStack stack = itemEntity.getItemStack();
                    itemEntity.remove();
                    Map<Integer, ItemStack> leftover = c.getInventory().addItem(stack);
                    if (!leftover.isEmpty()) {
                        for (ItemStack rest : leftover.values()) {
                            c.getWorld().dropItemNaturally(c.getLocation(), rest);
                        }
                        c.sendActionBar(Component.text("✦ 背包已满", NamedTextColor.YELLOW));
                    } else {
                        c.sendActionBar(Component.text("✦ 除你武器！物品已到手", NamedTextColor.RED));
                        c.playSound(c.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.2f);
                    }
                    TauntUtils.increment(plugin, c, "expelliarmus_steals", 1);
                    TauntUtils.unlock(plugin, c, AchievementManager.Ach.EXPELLIARMUS_STEAL);
                    cancel();
                    return;
                }

                double dist = Math.sqrt(distSq);
                double invD = 1.0 / dist;

                Vector vel = itemEntity.getVelocity();
                double nvx = vel.getX() + cx * invD * 0.06;
                double nvy = vel.getY();
                double nvz = vel.getZ() + cz * invD * 0.06;

                if (cy > 1.5 && nvy < 0.1) nvy += 0.05;

                double spSq = nvx * nvx + nvy * nvy + nvz * nvz;
                if (spSq > 9.0) {
                    double sc = 3.0 / Math.sqrt(spSq);
                    nvx *= sc; nvy *= sc; nvz *= sc;
                }

                itemEntity.setVelocity(new Vector(nvx, nvy, nvz));

                if (ticks[0] % 4 == 0) {
                    TauntUtils.spawnParticleSafe(itemEntity.getWorld(), Particle.WITCH,
                            itemLoc.getX(), itemLoc.getY() + 0.2, itemLoc.getZ(),
                            1, 0.05, 0.05, 0.05, 0.02);
                }
            }
        };
        BukkitTask t = task.runTaskTimer(plugin, 0L, 1L);
        activeTasks.add(t);
    }

    private void hitAccio(Player caster, LivingEntity target, WandStats stats, Spell spell) {
        Location casterLoc = caster.getLocation();

        Vector facing = casterLoc.getDirection().setY(0);
        if (facing.lengthSquared() < 0.01) facing = new Vector(1, 0, 0);
        else facing.normalize();
        Location destLoc = casterLoc.clone().add(facing.clone().multiply(1.0));

        Location targetLoc = target.getLocation();
        double dx = destLoc.getX() - targetLoc.getX();
        double dz = destLoc.getZ() - targetLoc.getZ();
        double dy = destLoc.getY() - targetLoc.getY();
        double hd = Math.sqrt(dx * dx + dz * dz);

        double power = stats.damageMultiplier(spell);

        Vector dir = hd > 0.1 ? new Vector(dx, 0, dz).normalize() : facing.clone();
        double hSpeed = Math.min(1.0 + hd * 0.08, 2.5) * power;
        double vSpeed = 0.5 + Math.min(hd * 0.05, 0.8);
        if (dy > 0) vSpeed += dy * 0.1;

        Vector v = dir.multiply(hSpeed);
        v.setY(vSpeed);
        target.setVelocity(v);
        target.setFallDistance(0);

        Location tl = target.getLocation().add(0, 1, 0);
        TauntUtils.spawnParticleSafe(target.getWorld(), Particle.PORTAL,
                tl.getX(), tl.getY(), tl.getZ(), 40, 0.5, 0.5, 0.5, 0.5);
        target.getWorld().playSound(target.getLocation(),
                Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
        caster.sendActionBar(Component.text("✦ 飞来！目标正在飞向你",
                NamedTextColor.LIGHT_PURPLE));
    }

    private void hitStupefy(Player caster, LivingEntity target, WandStats stats, Spell spell) {
        int dur = scaleDuration(100, stats, spell);
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, dur, 4, false, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, dur, 1, false, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,
                scaleDuration(60, stats, spell), 0, false, true));
        caster.sendActionBar(Component.text("✦ 昏昏倒地！", NamedTextColor.YELLOW));
    }

    private void hitPetrificus(Player caster, LivingEntity target, WandStats stats, Spell spell) {
        int dur = scaleDuration(200, stats, spell);
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, dur, 10, false, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, dur, 128, false, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, dur, 5, false, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, dur, 5, false, true));
        caster.sendActionBar(Component.text("✦ 统统石化！", NamedTextColor.GRAY));
    }

    private void hitIncendio(Player caster, LivingEntity target, WandStats stats, Spell spell) {
        target.setFireTicks(scaleDuration(100, stats, spell));
        double dmg = 4.0 * stats.damageMultiplier(spell);
        target.damage(dmg, caster);
        caster.sendActionBar(Component.text("✦ 火焰熊熊！", NamedTextColor.GOLD));
    }

    private void hitDiffindo(Player caster, LivingEntity target, WandStats stats, Spell spell) {
        double dmg = 3.0 * stats.damageMultiplier(spell);
        target.damage(dmg, caster);
        int dur = scaleDuration(60, stats, spell);
        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, dur, 0, false, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, dur, 0, false, true));
        caster.sendActionBar(Component.text("✦ 四分五裂！", NamedTextColor.DARK_RED));
    }

    private void hitLeviosa(Player caster, LivingEntity target, WandStats stats, Spell spell) {
        target.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION,
                scaleDuration(100, stats, spell), 1, false, true));
        caster.sendActionBar(Component.text("✦ 羽加迪姆勒维奥萨！", NamedTextColor.GREEN));
    }

    private void hitLevicorpus(Player caster, LivingEntity target, WandStats stats, Spell spell) {
        target.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION,
                scaleDuration(60, stats, spell), 1, false, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA,
                scaleDuration(120, stats, spell), 1, false, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,
                scaleDuration(120, stats, spell), 2, false, true));
        caster.sendActionBar(Component.text("✦ 倒挂金钟！", NamedTextColor.DARK_PURPLE));
    }

    private void hitRictusempra(Player caster, LivingEntity target, WandStats stats, Spell spell) {
        int dur = scaleDuration(200, stats, spell);
        target.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, dur, 4, false, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, dur, 1, false, true));
        if (target instanceof Player targetPlayer) {
            final int[] ticks = {0};
            final int maxTicks = dur;
            final Random rng = new Random();
            final BukkitTask[] holder = new BukkitTask[1];
            holder[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                ticks[0]++;
                if (ticks[0] > maxTicks || !targetPlayer.isOnline()) {
                    holder[0].cancel();
                    activeTasks.remove(holder[0]);
                    return;
                }
                Location loc = targetPlayer.getLocation();
                loc.setYaw(loc.getYaw() + (rng.nextFloat() - 0.5f) * 30);
                targetPlayer.teleport(loc);
            }, 0L, 5L);
            activeTasks.add(holder[0]);
        }
        caster.sendActionBar(Component.text("✦ 咧嘴呼啦啦！", NamedTextColor.LIGHT_PURPLE));
    }

    private void hitSilencio(Player caster, LivingEntity target, WandStats stats, Spell spell) {
        if (target instanceof Player p) {
            long durationMs = scaleDurationMs(10_000L, stats, spell);
            long until = System.currentTimeMillis() + durationMs;
            silenceUntil.merge(p.getUniqueId(), until, Math::max);
            p.sendActionBar(Component.text("🔇 你被无声无息封住了声音", NamedTextColor.DARK_GRAY));
        }
        caster.sendActionBar(Component.text("✦ 无声无息！", NamedTextColor.DARK_GRAY));
    }

    private void hitLangock(Player caster, LivingEntity target, WandStats stats, Spell spell) {
        if (target instanceof Player p) {
            long durationMs = scaleDurationMs(5_000L, stats, spell);
            long until = System.currentTimeMillis() + durationMs;
            muteUntil.merge(p.getUniqueId(), until, Math::max);
            p.sendActionBar(Component.text("👅 你的舌头被黏住了", NamedTextColor.DARK_PURPLE));
        }
        caster.sendActionBar(Component.text("✦ 锁舌封喉！", NamedTextColor.DARK_GRAY));
    }

    private void hitAvadaKedavra(Player caster, LivingEntity target) {
        if (target instanceof Player p && tryBlockWithShield(p, true)) {
            caster.sendActionBar(Component.text("✦ 对方的护身咒格挡了阿瓦达索命！",
                    NamedTextColor.AQUA));
            p.sendMessage(Component.text("🏆 你用护身咒抵挡了阿瓦达索命！",
                    NamedTextColor.GOLD));
            return;
        }

        String targetName = (target instanceof Player p) ? p.getName()
                : TauntUtils.getEntityDisplayName(target);
        Bukkit.getServer().broadcast(Component.text("💀 ", NamedTextColor.DARK_RED)
                .append(Component.text(caster.getName(), NamedTextColor.YELLOW))
                .append(Component.text(" 对 ", NamedTextColor.DARK_RED))
                .append(Component.text(targetName, NamedTextColor.RED))
                .append(Component.text(" 施放了 ", NamedTextColor.DARK_RED))
                .append(Component.text("阿瓦达索命", NamedTextColor.DARK_GREEN))
                .append(Component.text("！", NamedTextColor.DARK_RED)));

        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_WITHER_DEATH, 1.5f, 0.5f);
        caster.getWorld().playSound(target.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 0.5f);

        Location tl = target.getLocation().add(0, 1, 0);
        TauntUtils.spawnParticleSafe(target.getWorld(), Particle.SOUL_FIRE_FLAME,
                tl.getX(), tl.getY(), tl.getZ(), 40, 0.5, 1.0, 0.5, 0.1);

        if (target instanceof Player) {
            TauntUtils.increment(plugin, caster, "avada_kills", 1);
            TauntUtils.unlock(plugin, caster, AchievementManager.Ach.AVADA_KILL);
        }
        target.setHealth(0.0);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (target.isValid() && target.getHealth() > 0) {
                target.setHealth(0.0);
                target.damage(Double.MAX_VALUE);
            }
        }, 2L);
        caster.sendActionBar(Component.text("✦ 阿瓦达索命！", NamedTextColor.DARK_GREEN));
    }

    private int scaleDuration(int baseTicks, WandStats stats, Spell spell) {
        return (int) Math.round(baseTicks * stats.durationMultiplier(spell));
    }

    private long scaleDurationMs(long baseMs, WandStats stats, Spell spell) {
        return Math.round(baseMs * stats.durationMultiplier(spell));
    }

    // ═══════════════════════════════════════════════
    //  SELF / AREA
    // ═══════════════════════════════════════════════

    private void applySelfEffect(Player player, Spell spell, WandStats stats) {
        switch (spell) {
            case PROTEGO -> {
                int dur = scaleDuration(240, stats, spell);

                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, dur, 3, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, dur, 4, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, dur, 1, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, dur, 0, false, true));

                Location center = player.getLocation().add(0, 1, 0);
                for (int i = 0; i < 5; i++) applyOrb(player.getWorld(), spell, center, i * 4);
                for (int i = 0; i < 24; i++) {
                    double angle = i * Math.PI / 12;
                    double r = 1.2;
                    Location pl = center.clone().add(
                            Math.cos(angle) * r,
                            Math.sin(angle * 2) * 0.3,
                            Math.sin(angle) * r);
                    TauntUtils.spawnParticleSafe(player.getWorld(), Particle.END_ROD,
                            pl.getX(), pl.getY(), pl.getZ(), 1, 0, 0, 0, 0);
                }
                player.playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.2f, 1.5f);
                player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.8f);
                player.sendActionBar(Component.text("✦ 盔甲护身！护盾全开", NamedTextColor.AQUA));
            }
            case AGUAMENTI -> castAguamenti(player, spell);
            case LUMOS -> castLumos(player, spell, stats);
            default -> {}
        }
    }

    private void castAguamenti(Player player, Spell spell) {
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection();
        for (double d = 0.5; d <= 6; d += 0.35) {
            Location cur = eye.clone().add(dir.clone().multiply(d));
            applyCascade(player.getWorld(), spell, cur);
        }
        if (player.getFireTicks() > 0) {
            player.setFireTicks(0);
            player.sendMessage(Component.text("🌊 清水如泉浇灭了你身上的火。", NamedTextColor.BLUE));
        }
        for (Entity e : player.getNearbyEntities(8, 8, 8)) {
            if (e instanceof LivingEntity living && living.getFireTicks() > 0) living.setFireTicks(0);
            if (e instanceof Item item && item.getFireTicks() > 0) item.setFireTicks(0);
        }
        player.playSound(player.getLocation(), Sound.ITEM_BUCKET_EMPTY, 1.0f, 1.2f);
        player.sendActionBar(Component.text("✦ 清水如泉！", NamedTextColor.BLUE));
    }

    private void castLumos(Player player, Spell spell, WandStats stats) {
        Location eye = player.getEyeLocation();
        for (int i = 0; i < 3; i++) {
            applyOrb(player.getWorld(), spell, eye.clone().add(
                    eye.getDirection().multiply(2)), i * 5);
        }
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.5f);

        Location lightLoc = player.getLocation().getBlock().getLocation();
        Block lightBlock = lightLoc.getBlock();
        Material originalType = lightBlock.getType();

        if (originalType.isAir() || originalType == Material.LIGHT) {
            try {
                BlockData lightData = Material.LIGHT.createBlockData();
                if (lightData instanceof Levelled levelled) {
                    levelled.setLevel(Math.clamp(lumosLightLevel, 0, 15));
                }
                lightBlock.setBlockData(lightData, false);

                final Block fb = lightBlock;
                final Material fo = originalType;
                long actualDurationMs = scaleDurationMs(lumosDurationMs, stats, spell);
                BukkitTask restoreTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (fb.getType() == Material.LIGHT) {
                        fb.setType(fo.isAir() ? Material.AIR : fo, false);
                    }
                }, Math.max(1L, actualDurationMs / 50));
                activeTasks.add(restoreTask);

                player.sendActionBar(Component.text("✦ 荧光闪烁！照亮周围", NamedTextColor.WHITE));
                return;
            } catch (Throwable t) {
                plugin.getLogger().warning("[魔杖] 荧光闪烁放置光源失败: " + t.getMessage());
            }
        }
        player.sendActionBar(Component.text("✦ 荧光闪烁（粒子模式）", NamedTextColor.WHITE));
    }

    private void applyAreaEffect(Player player, Spell spell, WandStats stats) {
        if (spell == Spell.EXPECT_PATRONUM) {
            castExpectoPatronum(player, spell, stats);
        }
    }

    private void castExpectoPatronum(Player player, Spell spell, WandStats stats) {
        Location base = player.getEyeLocation().add(
                player.getEyeLocation().getDirection().multiply(2));

        for (int i = 0; i < 4; i++) {
            applyHelix(player.getWorld(), spell, base, new Vector(0, 1, 0), i * 8);
        }

        player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_RETURN, 1.0f, 1.5f);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 2.0f);

        double radius = 12.0 * (1.0 + (stats.speedMultiplier(spell) - 1.0) * 0.5);
        double dmg = 4.0 * stats.damageMultiplier(spell);

        int repelled = 0;
        for (Entity e : player.getNearbyEntities(radius, radius, radius)) {
            if (e instanceof Monster monster) {
                monster.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,
                        scaleDuration(200, stats, spell), 2, false, true));
                monster.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                        scaleDuration(200, stats, spell), 2, false, true));
                monster.damage(dmg, player);
                Vector push = monster.getLocation().toVector()
                        .subtract(player.getLocation().toVector()).normalize();
                push.multiply(1.5);
                push.setY(0.5);
                monster.setVelocity(push);
                Location ml = monster.getLocation().add(0, 1, 0);
                for (int i = 0; i < 2; i++) applyOrb(monster.getWorld(), spell, ml, i * 4);
                repelled++;
            }
        }
        player.sendActionBar(Component.text("✦ 呼神护卫！驱散了 " + repelled + " 只怪物",
                NamedTextColor.AQUA));

        if (repelled >= 5) {
            TauntUtils.unlock(plugin, player, AchievementManager.Ach.PATRONUS_MASTER);
        }
    }

    // ═══════════════════════════════════════════════
    //  冷却与工具
    // ═══════════════════════════════════════════════

    private void showCooldownMessage(Player player, Spell spell, long remainMs, long totalCooldownMs) {
        final long endTime = System.currentTimeMillis() + remainMs;
        final BukkitTask[] holder = new BukkitTask[1];

        holder[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long remain = endTime - System.currentTimeMillis();
            if (remain <= 0 || !player.isOnline()) {
                holder[0].cancel();
                activeTasks.remove(holder[0]);
                player.sendActionBar(Component.text("§a✦ " + spell.displayName + " 已就绪",
                        NamedTextColor.GREEN));
                player.playSound(player.getLocation(),
                        Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 2.0f);
                return;
            }
            double remainSec = remain / 1000.0;
            int total = 20;
            double progress = 1.0 - (remain / (double) totalCooldownMs);
            int filled = (int) Math.round(progress * total);
            String bar = buildProgressBar(filled, total);

            player.sendActionBar(Component.text("✦ " + spell.displayName + " ", NamedTextColor.GOLD)
                    .append(Component.text(String.format("%.1fs", remainSec), NamedTextColor.RED))
                    .append(Component.text(" ", NamedTextColor.WHITE))
                    .append(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                            .legacySection().deserialize(bar)));
        }, 0L, 10L);
        activeTasks.add(holder[0]);
    }

    private String buildProgressBar(int filled, int total) {
        StringBuilder bar = new StringBuilder("§7[");
        for (int i = 0; i < total; i++) {
            bar.append(i < filled ? "§a█" : "§c░");
        }
        bar.append("§7] ");
        return bar.toString();
    }

    public boolean isSilenced(UUID uuid) {
        Long until = silenceUntil.get(uuid);
        return until != null && until >= System.currentTimeMillis();
    }

    public boolean isMuted(UUID uuid) {
        Long until = muteUntil.get(uuid);
        return until != null && until >= System.currentTimeMillis();
    }
}