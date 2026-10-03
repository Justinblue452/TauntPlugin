package com.example.tauntplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class TauntManager implements Listener {   // ★ 实现 Listener

    private final JavaPlugin plugin;
    private final ConfigManager config;

    private String ownerName;
    private long globalPlayerCooldownMs;
    private final Map<String, Long> cooldownByAction = new HashMap<>();
    private final Map<String, Double> chanceByAction = new HashMap<>();

    // ★ 血月状态改为私有字段（不再是 public static）
    private volatile boolean bloodMoonActive = false;

    // ==================== 静态前缀 ====================
    private static final Component PREFIX = Component.text("[服务器] ", NamedTextColor.GOLD);
    private static final Component OWNER_PREFIX = Component.text("[服主公告] ", NamedTextColor.GOLD);
    private static final Component OWNER_DEATH_PREFIX = Component.text("[服主讣告] ", NamedTextColor.DARK_RED);
    private static final Component APOLOGY_PREFIX = Component.text("[服务器] ", NamedTextColor.RED);

    // ==================== 静态消息池 ====================
    private static final Map<String, List<String>> MESSAGE_POOLS = buildMessagePools();

    private static final List<String> OWNER_WELCOME_MESSAGES = List.of(
            "⭐ 恭迎服主 %player% 大人降临服务器！全体起立！",
            "👑 服主 %player% 上线了，服务器蓬荜生辉！",
            "⚡ %player% 大人来了，大家快打招呼！",
            "🌟 服务器的主人 %player% 驾到，闲杂人等退散！",
            "🎉 欢迎服主 %player% 回归，世界都亮了！",
            "💎 服主 %player% 降临，服务器延迟都感动得降了 10ms。",
            "🔥 %player% 大人来了，今天的服务器注定不平凡。",
            "👑 恭迎 %player% 服主，请各位玩家献上最热烈的掌声！",
            "⭐ 服主 %player% 上线，服务器正式进入黄金时代。",
            "⚜️ 是 %player% 大人！全体肃静，聆听圣谕！"
    );

    private static final List<String> OWNER_DEATH_MESSAGES = List.of(
            "💀 服主 %player% 竟然死了！全体默哀！",
            "⚰️ 天哪，服主 %player% 倒下了！服务器都在颤抖。",
            "🪦 服主 %player% 陨落于 %context%，历史会记住这一刻。",
            "😱 服主 %player% 被 %context% 干掉了，这不可能！",
            "🕯️ 沉痛悼念服主 %player%，死于 %context% 之手。",
            "🚨 警报！服主 %player% 阵亡，服务器进入紧急状态！",
            "💔 服主 %player% 倒下了，全服玩家的心都碎了。",
            "🔥 服主 %player% 被 %context% 灭了，快来人报仇！"
    );

    private static final List<String> APOLOGY_MESSAGES = List.of(
            "等等……%player% 是服主？！对不起对不起，我刚才什么都没说！",
            "哎呀，不小心调侃了服主 %player%，我错了，请原谅我！",
            "完了完了，我调侃了 %player% 大人，我这就去写检讨……",
            "对不起 %player% 服主！我刚刚是鬼迷心窍，请高抬贵手！",
            "？？？我竟然调侃了服主 %player%，我还有救吗？",
            "抱歉 %player% 服主，我收回刚才的话，当我没说！",
            "啊啊啊！我得罪了 %player% 大人，服务器要塌了！",
            "对不起对不起，%player% 服主，我刚才嘴瓢了……",
            "我不是故意的 %player% 大人，求放过！",
            "冷静，%player% 服主，我刚刚只是程序故障，对，程序故障！",
            "滴——检测到服主 %player% 被调侃，系统进入紧急道歉模式。",
            "我这条命是服主 %player% 给的，怎么敢调侃您呢？",
            "%player% 大人，您大人不记小人过，饶了小的吧！",
            "完了，我把服主 %player% 给得罪了，这服务器怕是待不下去了……",
            "系统提示：刚刚的调侃是临时工发的，与本人无关，%player% 服主明鉴！"
    );

    private static final List<String> BLOOD_MOON_DEATH_MESSAGES = List.of(
            "💀 %player% 在血月的注视下死在了 %context% 手里，鲜血染红了月光……",
            "🌑 血月之下，%player% 被 %context% 撕碎，连惨叫都被夜色吞没。",
            "🩸 %player% 的鲜血洒在血月之下，%context% 满意地笑了。",
            "☠️ 血月见证了 %player% 的死亡，%context% 是这场献祭的刽子手。",
            "🕯️ %player% 倒在了 %context% 面前，月光变得比血还红。",
            "⚰️ 又一具尸体倒在血月下，%player% 死于 %context%。",
            "🦴 %context% 在血月下变得更强了，%player% 成了它的战利品。",
            "🌕 %player% 死了，但血月不会为他悲伤，只会更贪婪。",
            "💉 %player% 的血液融入了血月的红光，%context% 大快朵颐。",
            "🔪 在血月之下，%player% 被 %context% 一刀毙命。"
    );

    private static final long APOLOGY_DELAY_MS = 1_500L;
    private static final long CLEANUP_INTERVAL_MS = 60_000;
    private static final long ENTRY_EXPIRE_MS = 5 * 60_000;

    private final Map<UUID, PlayerCooldown> cooldowns = new ConcurrentHashMap<>();

    private static class PlayerCooldown {
        final Map<String, Long> actions = new ConcurrentHashMap<>();
        volatile long lastActive = System.currentTimeMillis();
        volatile long lastGlobalTaunt = 0L;
    }

    // ==================== 构造函数 ====================

    public TauntManager(JavaPlugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        loadConfig();

        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::cleanup,
                CLEANUP_INTERVAL_MS / 50, CLEANUP_INTERVAL_MS / 50);
    }

    private void loadConfig() {
        ownerName = config.getString("general.owner-name", "Justin_Yan");
        globalPlayerCooldownMs = config.getLong("taunt.global-cooldown-ms", 3000);

        cooldownByAction.clear();
        chanceByAction.clear();

        ConfigurationSection actions = config.getSection("taunt.actions");
        if (actions != null) {
            for (String key : actions.getKeys(false)) {
                ConfigurationSection s = actions.getConfigurationSection(key);
                if (s == null) continue;
                cooldownByAction.put(key, s.getLong("cooldown", 15000));
                chanceByAction.put(key, s.getDouble("chance", 1.0));
            }
        }
    }

    // ==================== ★ 血月事件监听 ====================

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBloodMoonStart(TauntEvents.BloodMoonStartEvent event) {
        this.bloodMoonActive = true;
        plugin.getLogger().info("[调侃] 收到血月开始事件，死亡文案已切换");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBloodMoonEnd(TauntEvents.BloodMoonEndEvent event) {
        this.bloodMoonActive = false;
        plugin.getLogger().info("[调侃] 收到血月结束事件，死亡文案已恢复");
    }

    /** ★ 公开方法，供其他模块查询血月状态 */
    public boolean isBloodMoonActive() {
        return bloodMoonActive;
    }

    // ==================== 清理任务 ====================

    private void cleanup() {
        long now = System.currentTimeMillis();
        cooldowns.entrySet().removeIf(entry ->
                now - entry.getValue().lastActive > ENTRY_EXPIRE_MS);
    }

    // ==================== 主入口 ====================

    public void taunt(Player player, String action, String context) {
        taunt(player, action, context != null ? Component.text(context) : null);
    }

    public void taunt(Player player, String action, Component context) {
        long now = System.currentTimeMillis();

        PlayerCooldown state = cooldowns.computeIfAbsent(player.getUniqueId(), k -> new PlayerCooldown());
        state.lastActive = now;

        if (now - state.lastGlobalTaunt < globalPlayerCooldownMs) return;

        long cooldown = cooldownByAction.getOrDefault(action, 15_000L);
        Long last = state.actions.get(action);
        if (last != null && now - last < cooldown) return;

        double chance = chanceByAction.getOrDefault(action, 1.0);
        if (chance < 1.0 && ThreadLocalRandom.current().nextDouble() > chance) return;

        List<String> pool;
        // ★ 使用实例字段血月状态
        if ("death".equals(action) && bloodMoonActive) {
            pool = BLOOD_MOON_DEATH_MESSAGES;
        } else {
            pool = MESSAGE_POOLS.get(action);
        }
        if (pool == null || pool.isEmpty()) return;

        state.lastGlobalTaunt = now;
        state.actions.put(action, now);

        String template = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        Component playerName = Component.text(player.getName(), NamedTextColor.AQUA);
        Component ctx = context != null ? context : Component.empty();

        Component message = Component.text(template, NamedTextColor.YELLOW)
                .replaceText(cfg -> cfg.matchLiteral("%player%").replacement(playerName))
                .replaceText(cfg -> cfg.matchLiteral("%context%").replacement(ctx));

        Bukkit.getServer().broadcast(PREFIX.append(message));

        if (isOwner(player)) {
            scheduleApology(player);
        }
    }

    // ==================== 服主功能 ====================

    public boolean isOwner(Player player) {
        return player != null && ownerName.equalsIgnoreCase(player.getName());
    }

    public void welcomeOwner(Player owner) {
        if (owner == null || !owner.isOnline()) return;

        String template = OWNER_WELCOME_MESSAGES.get(
                ThreadLocalRandom.current().nextInt(OWNER_WELCOME_MESSAGES.size()));
        Component ownerNameComp = Component.text(owner.getName(), NamedTextColor.GOLD);
        Component message = Component.text(template, NamedTextColor.YELLOW)
                .replaceText(cfg -> cfg.matchLiteral("%player%").replacement(ownerNameComp));
        Bukkit.getServer().broadcast(OWNER_PREFIX.append(message));

        Title.Times times = Title.Times.times(
                Duration.ofMillis(500), Duration.ofMillis(2500), Duration.ofMillis(500));
        Title title = Title.title(
                Component.text("服主驾到", NamedTextColor.GOLD),
                Component.text("Justin_Yan 上线了", NamedTextColor.YELLOW),
                times);

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showTitle(title);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
        }

        owner.playSound(owner.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (owner.isOnline()) spawnOwnerFireworks(owner);
        }, 20L);
    }

    private void spawnOwnerFireworks(Player owner) {
        double[][] offsets = {{0, 0, 0}, {2, 1, 0}, {-2, 1, 1}};
        for (double[] offset : offsets) {
            owner.getWorld().spawn(
                    owner.getLocation().clone().add(offset[0], offset[1], offset[2]),
                    Firework.class,
                    fw -> {
                        FireworkMeta meta = fw.getFireworkMeta();
                        meta.addEffect(FireworkEffect.builder()
                                .withColor(Color.YELLOW, Color.ORANGE)
                                .withFade(Color.RED)
                                .with(FireworkEffect.Type.BALL_LARGE)
                                .trail(true).flicker(true).build());
                        meta.setPower(1);
                        fw.setFireworkMeta(meta);
                    });
        }
        owner.getWorld().spawnParticle(Particle.FLAME,
                owner.getLocation().add(0, 2, 0), 60, 1.5, 1.5, 1.5, 0.02);
    }

    public void ownerDeath(Player owner, Component cause) {
        if (owner == null) return;
        String template = OWNER_DEATH_MESSAGES.get(
                ThreadLocalRandom.current().nextInt(OWNER_DEATH_MESSAGES.size()));
        Component ownerNameComp = Component.text(owner.getName(), NamedTextColor.GOLD);
        Component ctx = cause != null ? cause : Component.text("未知原因");

        Component message = Component.text(template, NamedTextColor.RED)
                .replaceText(cfg -> cfg.matchLiteral("%player%").replacement(ownerNameComp))
                .replaceText(cfg -> cfg.matchLiteral("%context%").replacement(ctx));
        Bukkit.getServer().broadcast(OWNER_DEATH_PREFIX.append(message));

        Title.Times times = Title.Times.times(
                Duration.ofMillis(500), Duration.ofMillis(2500), Duration.ofMillis(500));
        Title title = Title.title(
                Component.text("服主陨落", NamedTextColor.DARK_RED),
                Component.text("全体默哀三秒", NamedTextColor.RED),
                times);

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showTitle(title);
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.6f, 0.5f);
        }
    }

    private void scheduleApology(Player owner) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!owner.isOnline()) return;
            String template = APOLOGY_MESSAGES.get(
                    ThreadLocalRandom.current().nextInt(APOLOGY_MESSAGES.size()));
            Component ownerNameComp = Component.text(owner.getName(), NamedTextColor.GOLD);
            Component apologyMsg = Component.text(template, NamedTextColor.RED)
                    .replaceText(cfg -> cfg.matchLiteral("%player%").replacement(ownerNameComp));
            Bukkit.getServer().broadcast(APOLOGY_PREFIX.append(apologyMsg));
        }, APOLOGY_DELAY_MS / 50);
    }

    // ==================== 翻译工具（static 保持）====================

    public static Component blockName(Block block) {
        return blockName(block.getType());
    }

    public static Component blockName(Material material) {
        NamespacedKey key = material.getKey();
        return Component.translatable("block." + key.getNamespace() + "." + key.getKey());
    }

    public static Component entityName(Entity entity) {
        Component custom = entity.customName();
        if (custom != null) return custom;
        NamespacedKey key = entity.getType().getKey();
        return Component.translatable("entity." + key.getNamespace() + "." + key.getKey());
    }

    public static Component itemName(ItemStack stack) {
        NamespacedKey key = stack.getType().getKey();
        return Component.translatable("item." + key.getNamespace() + "." + key.getKey());
    }

    // ==================== 消息池构建（保持 static）====================

    private static Map<String, List<String>> buildMessagePools() {
        Map<String, List<String>> pools = new HashMap<>();

        pools.put("chat", List.of(
                "%player% 又在聊天了，是没人陪你说话吗？",
                "这条消息的意义是什么，%player%？",
                "%player% 的键盘是不是只剩回车键了？",
                "%player% 又在发表高见了，大家快记笔记。",
                "打字这么快，现实里说话也这么利索吗，%player%？",
                "%player% 这条消息，我建议编辑一下再发。",
                "服务器不需要你刷屏，%player%。",
                "%player% 是不是把聊天框当日记本了？",
                "%player% 一开口，服务器就安静了三秒。",
                "说得很有道理，但下次别说了，%player%。",
                "%player% 的聊天频率和智商成反比。",
                "这话说出来之前，%player% 自己信吗？"
        ));

        pools.put("move", List.of(
                "%player% 你是在梦游吗？",
                "走得这么慢，是在等谁？",
                "%player% 逛了这么久，找到人生的方向了吗？",
                "%player% 又在瞎溜达，没事干就去挖矿。",
                "这步伐，是醉了吗，%player%？",
                "%player% 走了半天，还不如原地站着。",
                "悠着点，%player%，地皮不是你家地毯。",
                "%player% 的步数，怕是能绕地球三圈了。",
                "走位这么飘，%player% 是在跳探戈？",
                "%player% 是不是把 Minecraft 当散步模拟器了？",
                "再走两步，%player% 就要走遍全图了。",
                "%player% 的运动量，比他一整天的脑力活动还多。"
        ));

        pools.put("break", List.of(
                "%player% 挖了 %context%，是想盖别墅还是拆家？",
                "又破坏一个方块，%player% 你赔得起吗？",
                "%context% 做错了什么，要被 %player% 这样对待？",
                "%player% 对 %context% 下手了，真是无情。",
                "恭喜 %player% 收获 %context% 一块，值得庆祝。",
                "%context%：我招谁惹谁了？",
                "%player% 又在拆服务器，管理员看着呢。",
                "挖 %context% 有什么用，%player% 你自己心里没点数吗？",
                "%player% 的镐子，比他的脑子转得还快。",
                "%context% 被 %player% 无情抛弃。",
                "%player% 对 %context% 的爱，只持续了一镐。",
                "又一块 %context% 惨遭毒手，凶手是 %player%。"
        ));

        pools.put("place", List.of(
                "%player% 放了 %context%，是打算盖什么？",
                "又摆了一块 %context%，%player% 你是建筑师吗？",
                "%context% 被 %player% 摆在了奇怪的位置。",
                "%player% 的审美，从 %context% 的摆法就能看出来。",
                "%player% 开始搞建设了，请大家远离施工现场。",
                "这个 %context% 的位置，堪称艺术品（反话）。",
                "%player% 又开始了他的\"伟大工程\"。",
                "%context% 表示：我不想待在这里。",
                "%player% 摆 %context% 的姿势很专业，可惜位置不对。",
                "又一块 %context% 被 %player% 安排了。"
        ));

        pools.put("death", List.of(
                "%player% 死在了 %context% 手里，真是精彩。",
                "又死了？%player% 你这是在刷死亡次数吗？",
                "%context% 表示：这波不亏。",
                "%player% 用生命诠释了什么叫\"送\"。",
                "死于 %context%，%player% 你可真行。",
                "%player% 的死法，可以进教科书了。",
                "%context% 都没有用力，%player% 就倒了。",
                "安息吧 %player%，下次记得躲开 %context%。",
                "%player% 又给 %context% 送人头了。",
                "这一死，%player% 死得很有创意。",
                "%context% 一脸问号：我根本没想杀他。",
                "%player% 的墓碑上会写：死于 %context%。",
                "又一次死亡，%player% 已经对 %context% 心服口服了。"
        ));

        pools.put("join", List.of(
                "欢迎 %player% 回到这个充满伤害的地方。",
                "%player% 来了，服务器平均智商又要下降了。",
                "看看谁来了，是 %player% 啊。",
                "热烈欢迎 %player%，掌声响起来（并没有）。",
                "%player% 又上线了，大家小心点。",
                "欢迎回来，%player%，服务器因你而热闹（或更吵）。",
                "%player% 上线，服务器延迟+1。",
                "%player% 的出现，让服务器多了一份不确定性。"
        ));

        pools.put("quit", List.of(
                "%player% 走了，服务器终于安静了。",
                "再见 %player%，记得下次还来。",
                "%player% 下线了，世界清净了 0.1 秒。",
                "%player% 溜了，是去写作业了吗？",
                "下次见，%player%，别太久。",
                "%player% 走了，服务器平均智商回升了。",
                "%player% 终于走了，管理员松了一口气。",
                "送别 %player%，希望他回来时能变强一点。"
        ));

        pools.put("attack", List.of(
                "%player% 在攻击 %context%，脾气不小啊。",
                "打 %context% 有什么用？%player% 你打得过吗？",
                "%player% 对 %context% 下手了，真残忍。",
                "%context% 被打得莫名其妙，%player% 你倒是说句话啊。",
                "%player% 和 %context% 打起来了，围观群众散了。",
                "打 %context% 算什么本事，%player% 你找软柿子捏。",
                "%player% 的攻击，对 %context% 造成了 0.5 点精神伤害。",
                "%context% 表示：你打你的，我死我的。",
                "%player% 又对 %context% 施暴了。",
                "%player% 这一拳，看得出他对 %context% 有意见。"
        ));

        pools.put("interact", List.of(
                "%player% 对着 %context% 按了半天，有什么发现吗？",
                "你点 %context% 是想让它做什么，%player%？",
                "%context%：别点了，再点也不会变。",
                "%player% 和 %context% 的互动，毫无意义。",
                "点 %context% 有用吗，%player%？试试挖它。",
                "%player% 的右键，点在了 %context% 的痛处。",
                "%context% 默默承受着 %player% 的骚扰。",
                "%player% 对 %context% 的爱，隔着屏幕都能感受到。",
                "%context% 已经被 %player% 点麻了。",
                "%player% 是不是觉得点 %context% 能出奇迹？"
        ));

        pools.put("eat", List.of(
                "%player% 又饿了，吃饱了才有力气送。",
                "吃得这么香，%player% 你是来度假的吗？",
                "%player% 在吃 %context%，看得我也饿了。",
                "%player% 吃这么多，是想跑得更快吗？",
                "%player% 的食量，堪比末影人。",
                "吃饱了，%player% 又要去送死了。",
                "%player% 又开始疯狂进食，是准备打 BOSS 吗？",
                "这一口下去，%player% 的体重+1。",
                "%player% 是不是把食物当水喝了？",
                "%player% 的胃，比他的背包还能装。"
        ));

        pools.put("fish", List.of(
                "%player% 在钓鱼，是打算钓上来什么？",
                "钓鱼这种事，交给 %player% 就对了。",
                "%player% 又在摸鱼（字面意思）。",
                "%player% 的耐心真让人佩服，可惜大概率钓上来垃圾。",
                "%player% 钓鱼的姿势，比他的战斗力强多了。",
                "恭喜 %player% 钓上来一条（大概率是垃圾）。",
                "%player% 和鱼的缘分，比和玩家的缘分深。",
                "%player% 一天钓鱼的时间，比打怪时间还长。",
                "%player% 又去当渔夫了。",
                "%player% 的鱼竿，比他的剑还忙。"
        ));

        pools.put("levelup", List.of(
                "%player% 升到了 %context% 级，恭喜（但还是菜）。",
                "等级+1，%player% 的实力+0。",
                "%player% 升级了，可惜这不能让你少死几次。",
                "又升级了，%player% 你确定是升级不是刷经验？",
                "%player% 的等级上去了，意识还停在原地。",
                "恭喜 %player% 到达 %context% 级，接下来可以继续送。",
                "%player% 升级的速度，赶不上他死亡的速度。",
                "%context% 级了，%player% 你的操作什么时候也跟上？"
        ));

        pools.put("teleport", List.of(
                "%player% 瞬移了，是用了什么见不得人的手段？",
                "%player% 一眨眼就不见了，是去逃命吗？",
                "%player% 说走就走，比他的意识还快。",
                "%player% 又去别的地方浪了。",
                "%player% 的传送，比他走路还勤快。",
                "这么喜欢传送，%player% 是不是懒得走路？",
                "%player% 又跑了，是去祸害别的区域吗？",
                "传送不是逃跑的理由，%player%。"
        ));

        pools.put("fall", List.of(
                "%player% 从高处摔下来了，这就是重力吗？",
                "落地姿势不错，%player%，下次注意高度。",
                "%player% 又挑战重力失败了。",
                "%player% 和重力的关系，一直不太友好。",
                "这一摔，%player% 的骨头应该记住了。",
                "%player% 又把高空当平地了。",
                "%player% 从这么高摔下来，居然还活着？",
                "%player% 的降落方式，堪称灾难级。",
                "重力又赢了，%player% 又输了。",
                "%player% 下次记得带水桶。"
        ));

        pools.put("sneak", List.of(
                "%player% 潜行了，是怕被人发现吗？",
                "鬼鬼祟祟的，%player% 在做什么？",
                "%player% 以为潜行就没人看见他，真天真。",
                "潜行也藏不住 %player% 的存在感。",
                "%player% 的潜行，是给谁看的？",
                "潜行没用，%player%，聊天栏已经出卖了你。",
                "%player% 又开始偷偷摸摸了。",
                "%player% 的潜行姿势，已经被全服看到了。"
        ));

        return Collections.unmodifiableMap(pools);
    }
}