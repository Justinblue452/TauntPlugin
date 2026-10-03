package com.example.tauntplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class MobTauntManager {

    private final JavaPlugin plugin;

    private long checkIntervalMs;
    private long playerCooldownMs;
    private double triggerChance;
    private double scanRadius;

    // ★ 使用 CooldownManager 替代 Map<UUID, Long> lastTaunted
    private final CooldownManager cooldowns;

    private BukkitTask checkTask;

    private static final Map<EntityType, List<String>> MOB_MESSAGES = buildMobMessages();

    public MobTauntManager(JavaPlugin plugin, ConfigManager config) {
        this.plugin = plugin;

        this.checkIntervalMs = config.getLong("mob-taunt.check-interval-ms", 20000);
        this.playerCooldownMs = config.getLong("mob-taunt.player-cooldown-ms", 120000);
        this.triggerChance = config.getDouble("mob-taunt.trigger-chance", 0.7);
        this.scanRadius = config.getDouble("mob-taunt.scan-radius", 16.0);

        // ★ 创建冷却管理器
        this.cooldowns = new CooldownManager(plugin, playerCooldownMs, 0, playerCooldownMs * 2);

        plugin.getLogger().info("[生物调侃] 已加载 " + MOB_MESSAGES.size() + " 种生物");
        scheduleNextScan();
    }

    public void shutdown() {
        if (checkTask != null) {
            checkTask.cancel();
            checkTask = null;
        }
        if (cooldowns != null) cooldowns.shutdown();
    }

    private void scheduleNextScan() {
        long intervalTicks = checkIntervalMs / 50;
        checkTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            try { scan(); } catch (Exception ignored) {}
        }, intervalTicks, intervalTicks);
    }

    private void scan() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getGameMode().name().equals("SPECTATOR")) continue;

            // ★ 使用 CooldownManager 检查冷却
            long remain = cooldowns.getRemaining(player.getUniqueId(), "mob", playerCooldownMs);
            if (remain > 0) continue;

            List<LivingEntity> candidates = new ArrayList<>();
            for (Entity e : player.getNearbyEntities(scanRadius, scanRadius, scanRadius)) {
                if (!(e instanceof LivingEntity living)) continue;
                if (living instanceof Player) continue;
                if (!MOB_MESSAGES.containsKey(living.getType())) continue;
                candidates.add(living);
            }

            if (candidates.isEmpty()) continue;
            if (ThreadLocalRandom.current().nextDouble() > triggerChance) continue;

            LivingEntity mob = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
            taunt(player, mob);

            // ★ 记录冷却
            cooldowns.isReady(player.getUniqueId(), "mob", playerCooldownMs);
        }
    }

    private void taunt(Player player, LivingEntity mob) {
        List<String> pool = MOB_MESSAGES.get(mob.getType());
        if (pool == null || pool.isEmpty()) return;

        String template = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));

        Component playerName = Component.text(player.getName(), NamedTextColor.AQUA);
        Component message = Component.text(template, NamedTextColor.GRAY)
                .replaceText(cfg -> cfg.matchLiteral("%player%").replacement(playerName));

        NamespacedKey key = mob.getType().getKey();
        Component mobName = Component.translatable(
                "entity." + key.getNamespace() + "." + key.getKey());
        Component prefix = Component.text("[", NamedTextColor.DARK_GRAY)
                .append(mobName.color(NamedTextColor.DARK_GREEN))
                .append(Component.text("] ", NamedTextColor.DARK_GRAY));

        Bukkit.getServer().broadcast(prefix.append(message));
    }

    // ==================== 消息池 ====================
    private static Map<EntityType, List<String>> buildMobMessages() {
        Map<EntityType, List<String>> map = new EnumMap<>(EntityType.class);

        map.put(EntityType.VILLAGER, List.of(
                "嗯……%player%，要不要交易？绿宝石换白菜，童叟无欺。",
                "哼……%player%，你看起来不像有绿宝石的人。",
                "%player%，别打我，我上有老下有小，还有一头羊驼。",
                "%player%，今天工作台又涨了，日子不好过啊。",
                "%player% 又来砍价了，我这已经是最低价了。",
                "嗯哼！%player%，你是来买东西还是来拆台的？",
                "%player%，我告诉你，昨天有个玩家拿木棍跟我换钻石，被我赶走了。",
                "%player%，看见那个铁傀儡了吗？它只听我的。",
                "哼，%player%，你上次欠我的 3 个绿宝石还没还呢。",
                "%player%，别站在我床前，我老婆会误会的。",
                "嗯……%player%，你身上有僵尸的味道。",
                "%player%，你要是再打我一下，我就涨价了。",
                "哼！%player%，抢劫啊！铁傀儡！有人抢劫！",
                "%player%，绿宝石不够？你可以拿小麦来换。",
                "嗯……%player%，今天生意不错，要不要也来点？",
                "%player%，别碰我的钟，那是村庄的命根子。",
                "%player% 你踩到我的田地了，这是要赔的。"
        ));

        map.put(EntityType.PILLAGER, List.of(
                "%player%，交出你的绿宝石！不然我们就屠村。",
                "%player%，村子归我们了，你只是个路过的。",
                "%player%，弩箭比弓箭酷多了，试试？",
                "%player%，我们是灾厄巡逻队，专门来找茬的。",
                "%player%，你家的门我们已标记了，今晚就来。",
                "%player%，举起双手！不对，你手上是钻石剑……",
                "%player%，上次那个村庄是我烧的，怎么了？",
                "%player%，我抢过的东西比你见过的还多。",
                "%player%，加入我们吧，管吃管住管抢劫。",
                "%player%，你别用那种眼神看着我，我只是想抢点东西。",
                "%player%，你这盾牌不错，归我了。",
                "%player%，我数三下，不交东西就射你。",
                "一、二……不对，%player% 你跑什么？",
                "%player%，我们是文明人，只抢不杀。",
                "%player%，下次袭击我请你去，看你吓得那样。"
        ));

        map.put(EntityType.VINDICATOR, List.of(
                "%player%，我的斧头需要见点血。",
                "%player%，卫道士来收保护费了，交不交？",
                "%player%，跑得掉算我输。",
                "%player%，我砍过比你强的人，你猜怎么着？",
                "%player%，你身上穿的是铁甲？那就更好砍了。",
                "%player%，我和掠夺者不一样，我不讲道理。",
                "%player%，上次有个玩家跟我比速度，他的头现在挂在我家墙上。",
                "%player%，斧头比剑帅，你不服？",
                "%player%，别跑，砍完就放你走。",
                "%player%，你的护甲在我眼里就是纸糊的。",
                "%player%，我不抢劫，我只负责清场。",
                "%player%，你以为你是谁？武僧吗？",
                "%player%，我的斧头是钻石的，你的呢？",
                "%player%，反抗只会让我更兴奋。",
                "%player%，砍人是我唯一的爱好。"
        ));

        map.put(EntityType.EVOKER, List.of(
                "%player%，看我召唤尖牙！……哦，怎么没出来。",
                "%player%，恼鬼会一直缠着你，直到你崩溃。",
                "%player%，不死图腾我有很多，你猜怎么来的？",
                "%player%，我念咒语的时候别打断我，很没礼貌。",
                "%player%，你能跑过尖牙吗？试试看。",
                "%player%，我手下有一群恼鬼，它们都听我的。",
                "%player%，你以为你杀得了我？我死了图腾会救我。",
                "%player%，我的魔法可不是变戏法。",
                "%player%，召唤个尖牙送你上路。",
                "%player%，你知不知道我为什么叫唤魔者？因为我能唤醒你的噩梦。",
                "%player%，别站在地面上，那里不安全。",
                "%player%，我有 3 条命，你有几条？",
                "%player%，看好了，这才是真正的魔法。",
                "%player%，不死图腾，你要不要？打死我就给你。",
                "%player%，尖牙阵，启动！……又失败了。"
        ));

        map.put(EntityType.RAVAGER, List.of(
                "%player%，我撞过来的时候别躲。",
                "%player%，这头野兽我骑着，你跑不掉的。",
                "%player%，感受一下真正的冲击力。",
                "%player%，我踩过的庄稼比你种的还多。",
                "%player%，撞一下够你飞半张地图。",
                "%player%，我的角很尖，你的盾够硬吗？",
                "%player%，掠夺者骑我，我踩人，我们各司其职。",
                "%player%，我其实很温顺，只是训练的时候被打了。",
                "%player%，你挡路了，让开。",
                "%player%，冲啊！……哦，撞到树了。",
                "%player%，我的鞍是掠夺者专属的，你买不到。",
                "%player%，别想着驯服我，我不是马。",
                "%player%，我在灾厄巡逻队里是最快的。",
                "%player%，你要不要试试被我追的感觉？",
                "%player%，我撞过的玩家，现在都在重生点。"
        ));

        map.put(EntityType.PIGLIN, List.of(
                "%player%，金锭有吗？没有就走开。",
                "%player%，用金锭跟我交易，我给你好东西。",
                "%player%，你身上的金色装备很眼熟啊，抢的？",
                "%player%，别在我面前挖金矿，那是我的。",
                "%player%，不穿金装还敢来下界？胆子不小。",
                "%player%，我们猪灵只认金子，其他免谈。",
                "%player%，我换你一个金锭，给你一把破剑，公平吧？",
                "%player%，你看起来不像有金锭的人，滚。",
                "%player%，别乱翻我的箱子，里面有我的私房钱。",
                "%player%，下界是我们的地盘，你只是客人。",
                "%player%，金锭金锭金锭，你听懂了没有？",
                "%player%，交易一次我就放过你，怎么样？",
                "%player%，我很喜欢你的靴子，可惜不是金的。",
                "%player%，如果你穿上金靴子，我们就是朋友。",
                "%player%，快走快走，不然我叫猪灵蛮兵了。",
                "%player%，我有个秘密：我其实不太会打架。",
                "%player%，金锭换东西，不接受砍价。"
        ));

        map.put(EntityType.PIGLIN_BRUTE, List.of(
                "%player%，我不交易，我只打架。",
                "%player%，斧头在哪？让我砍两下。",
                "%player%，猪灵蛮兵，不戴金装，专门砍人。",
                "%player%，你穿金甲也没用，我照样砍。",
                "%player%，别拿金锭诱惑我，我不吃那套。",
                "%player%，我是猪灵里的战斗狂，来者不拒。",
                "%player%，我砍过的玩家比你见过的都多。",
                "%player%，你拿钻石剑很了不起？我照样打。",
                "%player%，别跑！让我砍完再说。",
                "%player%，我跟猪灵不一样，我从不手软。",
                "%player%，你以为金甲能保护你？试试看。",
                "%player%，我唯一的爱好就是砍人。",
                "%player%，下界堡垒是我的地盘，你也敢来？",
                "%player%，我打人从不打招呼，因为我不会说话。",
                "%player%，来战！"
        ));

        map.put(EntityType.HOGLIN, List.of(
                "%player%，别看我，我只是一头长着獠牙的猪。",
                "%player%，我什么都不认，只认揍。",
                "%player%，你身上有诡异菌的味道，我不喜欢。",
                "%player%，下界疣是我最爱吃的零食。",
                "%player%，猪灵都骑我，你觉得我开心吗？",
                "%player%，我不说话，但不代表我不恨你。",
                "%player%，又来了一个想骑我的。",
                "%player%，你手里的诡异菌钓竿，别拿过来。"
        ));

        map.put(EntityType.ZOMBIFIED_PIGLIN, List.of(
                "%player%，呃……我虽然僵尸化了，但我还是想要金子。",
                "%player%，金锭……金锭……",
                "%player%，别打我，我只是一只路过的僵尸猪灵。",
                "%player%，我已经死了，你为什么还要砍我？",
                "%player%，下界到处都是我们，你打不完的。",
                "%player%，我明明已经死了，为什么还这么想要金子……"
        ));

        map.put(EntityType.ZOMBIE, List.of(
                "%player% 的脑子看起来很新鲜……",
                "别跑 %player%，我只是想尝尝你的脑子。",
                "%player%，你闻起来像铁傀儡。",
                "又是一个活人，%player%，今天有口福了。",
                "%player%，你门建得再好也没用，我会拆门。",
                "%player%，白天的我烧得慌，晚上可就不一样了。",
                "%player%，你以为戴个南瓜头我就不认识你了？",
                "%player%，别躲在泥土房子里，我闻得到你。",
                "%player%，我死过一次了，你怕不怕？",
                "%player%，我想跟你做朋友，就做一晚上的朋友。",
                "%player%，铁傀儡来了，我先撤，下次再来找你。",
                "%player%，你手上有金苹果吗？我就问问，不会给钱的。",
                "%player%，我走路慢，但我有耐心。",
                "%player%，看见我的兄弟了吗？他们就在你家门口。",
                "%player%，你不睡觉的吗？那我可要来了。",
                "%player%，我想吃你的宠物，可以吗？",
                "%player%，僵尸不怕太阳，怕的是你手里那把剑。"
        ));

        map.put(EntityType.SKELETON, List.of(
                "%player%，站住别动，让我瞄准一下。",
                "%player% 的膝盖看起来很脆弱……",
                "再来一箭，%player%。",
                "%player%，骨头也可以扔给你的狗。",
                "%player%，你的盾牌挡得住箭雨吗？",
                "%player%，我射不准是因为你在动，别怪我没提醒。",
                "%player%，白天我怕太阳，晚上我可不怕。",
                "%player%，你看我这把弓，是不是很眼熟？",
                "%player%，别躲树后面，我射得准。",
                "%player%，我死了会掉骨头，狗最喜欢了。",
                "%player%，我射箭从来不用看，全靠天赋。",
                "%player%，你穿的是钻甲？那太好了，我射你更快。",
                "%player%，别问我为什么没有肉，我只有骨头。",
                "%player%，你听过弓箭的声音吗？咻——",
                "%player%，我不吃你，我只射你。"
        ));

        map.put(EntityType.CREEPER, List.of(
                "%player%……嘶嘶嘶……",
                "%player%，别回头……",
                "我喜欢 %player% 的样子，尤其是爆炸的那一刻。",
                "%player%，我可以做你的朋友吗？(嘶嘶)",
                "%player%，你知道我最喜欢什么吗？你的后脑勺。",
                "%player%，猫来了，我先走了。",
                "%player%，你越怕我，我越兴奋。",
                "%player%，你听到嘶嘶声了吗？那是我们的暗号。",
                "%player%，我不出声是有礼貌，不代表我走了。",
                "%player%，你的房子造得不错，可惜要没了。",
                "%player%，你身上有猫的味道，离我远点。",
                "%player%，我炸过的东西比你见过的还多。",
                "%player%，我不需要武器，我自己就是武器。",
                "%player%，你猜我现在离你几格？",
                "%player%，下次见面，我们就一起消失吧。",
                "%player%，我最喜欢背后偷袭，没别的本事。"
        ));

        map.put(EntityType.SPIDER, List.of(
                "%player%，我可以在你头上织网吗？",
                "%player% 的墙角看起来很舒服。",
                "%player%，你怕蜘蛛吗？应该怕。",
                "%player%，八条腿比两条腿快哦。",
                "%player%，白天的我很温顺，晚上可不是。",
                "%player%，我会爬墙，你能吗？",
                "%player%，你听到窸窸窣窣的声音了吗？那是我。",
                "%player%，你家的天花板缝隙，我钻得进去。",
                "%player%，我的眼睛很多，看你看得很清楚。",
                "%player%，别想着用火把赶我，我不怕光，只是不喜欢。",
                "%player%，我吐丝的速度比你跑得快。",
                "%player%，我咬过很多玩家，你的味道会排第几呢？",
                "%player%，洞穴蜘蛛是我表弟，它更毒。",
                "%player%，你头顶上可能就有我一只。",
                "%player%，别踩我的网，我织了好久。"
        ));

        map.put(EntityType.ENDERMAN, List.of(
                "%player%，别用那种眼神看着我。",
                "%player%，再看一眼，我就不客气了。",
                "我不是在瞪你，%player%，我只是天生这样。",
                "%player%，末影珍珠要吗？拿南瓜来换。",
                "%player%，我可以瞬移，你追得到我吗？",
                "%player%，我讨厌水，别拿水桶吓我。",
                "%player%，你知不知道我为什么讨厌被看？因为害羞。",
                "%player%，末地是我的家，那里可没有你。",
                "%player%，我搬方块的速度比你放得快。",
                "%player%，你戴着南瓜头，我勉强跟你说话。",
                "%player%，我从来不主动攻击，除非你先看我。",
                "%player%，你的末影珍珠掉了，哦不，是我偷的。",
                "%player%，别站在我旁边，我的身高让你不舒服。",
                "%player%，我瞬移过一次，可以瞬移第二次。",
                "%player%，末影螨是我唯一的敌人。"
        ));

        map.put(EntityType.WITCH, List.of(
                "%player%，来尝尝我新调制的药水？",
                "%player% 的脸色不太好，要不要来点治疗药？哦不，是伤害药。",
                "%player%，我可以把你变成一只青蛙。",
                "别跑 %player%，我还没给你下毒呢。",
                "%player%，我的药水从不失手，只是偶尔配方错了。",
                "%player%，你看起来需要一瓶虚弱药水。",
                "%player%，我有剧毒、缓慢、虚弱，你想要哪个？",
                "%player%，我的鼻子上有疣，你有吗？",
                "%player%，你来追我啊，我边跑边扔药水。",
                "%player%，我在沼泽住惯了，你闻不惯这味道吧？",
                "%player%，我的药水是自制的，不加防腐剂。",
                "%player%，你猜我下一瓶扔什么？猜对了也不告诉你。",
                "%player%，我和村民是同行，但我的生意更好。",
                "%player%，来，喝一口，我保证不会太痛。",
                "%player%，你的运气不错，我今天只带了 3 瓶药水。"
        ));

        map.put(EntityType.SLIME, List.of(
                "%player%，要不要一起蹦？",
                "%player% 看起来比我还黏。",
                "弹弹弹，%player% 你也来。",
                "%player%，我的史莱姆球给你玩。",
                "%player%，我分裂成小的，你打得完吗？",
                "%player%，我住在沼泽和洞穴，那里很潮。",
                "%player%，你踩我一脚试试，我会弹回来。",
                "%player%，我的身体是透明的，你能看穿我吗？",
                "%player%，大史莱姆分裂成小史莱姆，小史莱姆还会分裂吗？不会。",
                "%player%，我最怕岩浆，那里我活不了。",
                "%player%，我跳得不高，但很黏。",
                "%player%，你身上有史莱姆球的味道，是你杀了我兄弟？",
                "%player%，我有时候会掉黏液球，但我不介意。"
        ));

        map.put(EntityType.MAGMA_CUBE, List.of(
                "%player%，我比史莱姆热情多了。",
                "%player%，来握个手？会有点烫。",
                "%player%，我的热情像岩浆一样。",
                "%player%，我在下界到处蹦，你追不上我。",
                "%player%，我一蹦一跳，岩浆球掉一地。",
                "%player%，你穿防火药水了吗？我猜没有。",
                "%player%，我不怕火，你怕吗？",
                "%player%，我弹起来会烧到你，小心。",
                "%player%，岩浆是我的床，我睡得很香。",
                "%player%，别用雪球打我，那会让我很难受。"
        ));

        map.put(EntityType.BLAZE, List.of(
                "%player%，你冷吗？我可以帮你点把火。",
                "%player%，来下界玩吗？这里暖和。",
                "%player%，烈焰棒要吗？打死我就给你。",
                "%player%，我飘在半空，你打得着我吗？",
                "%player%，我吐三个火球，你躲得开吗？",
                "%player%，下界要塞是我家，你别乱闯。",
                "%player%，我讨厌雪球，非常讨厌。",
                "%player%，我的火球会把你烤熟。",
                "%player%，你穿防火药水也没用，我打的是物理伤害。",
                "%player%，我是下界的守卫，你走吧。",
                "%player%，我的烈焰棒能造末地传送门，你想不想要？"
        ));

        map.put(EntityType.GHAST, List.of(
                "%player%，我哭不是因为我难过，是因为我想打你。",
                "%player%，看我的火球！……哦，又没打中。",
                "%player%，下界很孤独，陪我聊聊天？",
                "%player%，我的哭声在很远的地方都能听到。",
                "%player%，我很大，但胆子很小，你别吓我。",
                "%player%，我的火球一发能炸掉你的房子。",
                "%player%，我飘在天上，你够不着我。",
                "%player%，你听到婴儿哭声了吗？那是我。",
                "%player%，我打自己人比打你准。",
                "%player%，泪腺太发达，我控制不住。"
        ));

        map.put(EntityType.WITHER_SKELETON, List.of(
                "%player%，石剑要不要？我可以送你一把。",
                "%player%，凋灵骷髅头，听起来很酷对吧。",
                "%player%，下界要塞是我的地盘。",
                "%player%，我的剑会让你凋零，慢慢死。",
                "%player%，我比普通骷髅更黑，也更狠。",
                "%player%，你打死我有 2.5% 概率掉头，运气不错。",
                "%player%，我不怕太阳，因为下界没有太阳。",
                "%player%，你身上有凋零效果了，感觉怎么样？"
        ));

        map.put(EntityType.DROWNED, List.of(
                "%player%，下水玩吗？我保证不咬你（骗你的）。",
                "%player%，三叉戟要不要？水里见。",
                "%player% 的泳姿一定很丑。",
                "%player%，我在水里速度比你快。",
                "%player%，我在水下等你很久了。",
                "%player%，来海底神殿附近玩，那里很热闹。",
                "%player%，你的氧气快没了，我知道。",
                "%player%，我把你拖下水，你就跟我一样了。"
        ));

        map.put(EntityType.HUSK, List.of(
                "%player%，沙漠里很热，要不要一起干尸？",
                "%player%，我不怕太阳，你怕吗？",
                "%player%，沙漠的夜晚很冷，但我没感觉。",
                "%player%，我咬你一口，你会有饥饿效果。",
                "%player%，我不像僵尸那样白天燃烧。",
                "%player%，沙漠是我的地盘，你走错路了。",
                "%player%，我在沙子里埋了很久，刚出来活动。"
        ));

        map.put(EntityType.STRAY, List.of(
                "%player%，我的箭会冻住你。",
                "%player%，雪地里迷路了吗？",
                "%player%，要不要来杯冰镇药水？",
                "%player%，我住在冰原，那里很冷。",
                "%player%，我的箭有缓慢效果，你跑不掉。",
                "%player%，你的护甲不错，可惜跑得慢。",
                "%player%，我在暴风雪里也能瞄准你。"
        ));

        map.put(EntityType.PHANTOM, List.of(
                "%player%，你多久没睡觉了？我闻到你的疲惫。",
                "%player%，熬夜对身体不好，所以我来了。",
                "%player%，我在天上看着你哦。",
                "%player%，你越困，我越兴奋。",
                "%player%，我从天上俯冲下来，你躲得开吗？",
                "%player%，你睡一觉我就消失了，可惜你不会。",
                "%player%，我是熬夜玩家最好的闹钟。",
                "%player%，你的头顶，是我最喜欢的攻击角度。",
                "%player%，我只有你失眠的时候才会出现。"
        ));

        map.put(EntityType.WANDERING_TRADER, List.of(
                "%player%，来看看我的货？都是好东西。",
                "%player%，买卖不成仁义在。",
                "%player%，最后一天，明天就走了。",
                "%player%，我有稀有种子，要不要看看？",
                "%player%，我跟村民不是一伙的，我更喜欢流浪。",
                "%player%，我的羊驼会吐口水，别惹它。",
                "%player%，你出价太低，我走了。",
                "%player%，我的蓝冰和珊瑚块，别的地方买不到。"
        ));

        map.put(EntityType.IRON_GOLEM, List.of(
                "%player%，我守护村民，也守护你。",
                "%player%，需要帮忙吗？我力气大。",
                "%player%，村里的花是我放的。",
                "%player%，谁欺负村民，我就揍谁。",
                "%player%，我从来不收小费。",
                "%player%，别看我不说话，我一直在看你。",
                "%player%，你打村民一下试试，我秒到。",
                "%player%，我掉铁锭，但需要你先打败我。",
                "%player%，我在村庄里比钟还重要。"
        ));

        map.put(EntityType.SNOW_GOLEM, List.of(
                "%player%，来打雪仗吗？",
                "%player%，我在南瓜头里其实很热。",
                "%player%，别看我小，我雪球准得很。",
                "%player%，下界和沙漠是我的噩梦。",
                "%player%，我踩过的地方都留下雪。",
                "%player%，我掉的雪球，你可以拿去堆雪人。"
        ));

        map.put(EntityType.PIG, List.of(
                "哼哼，%player%，有胡萝卜吗？",
                "%player%，骑上我，我带你飞（并不会）。",
                "哼……%player%，鞍在哪？",
                "哼哼哼，%player%，我是一头快乐的猪。",
                "%player%，你给我胡萝卜，我就跟你走。",
                "%player%，我掉猪排，但请不要杀我。",
                "%player%，猪排加胡萝卜，我可以跟你换。"
        ));

        map.put(EntityType.COW, List.of(
                "哞……%player%，要不要来点牛奶？",
                "%player%，我的皮做书很好用。",
                "哞，%player%，你饿吗？",
                "%player%，我有哞菇的表哥，在下界蘑菇岛。",
                "%player%，你拿小麦喂我，我就跟你走。",
                "%player%，我掉牛皮和牛肉，但请让我活得久一点。"
        ));

        map.put(EntityType.SHEEP, List.of(
                "咩……%player%，我的毛又长长了。",
                "%player%，剪刀带了吗？",
                "咩咩，%player%，粉色羊毛要吗？",
                "%player%，我吃草就能长毛，你羡慕吗？",
                "%player%，我染过色，现在是彩虹羊。",
                "%player%，你剪我毛的时候轻点。"
        ));

        map.put(EntityType.CHICKEN, List.of(
                "咯咯，%player%，鸡蛋要吗？",
                "%player%，我虽然不会飞，但掉得很快。",
                "咯咯咯，%player%，你有种子吗？",
                "%player%，我下的蛋可以孵小鸡，神奇吧。",
                "%player%，我走路的姿势是不是很好笑？",
                "%player%，鸡腿是我的全部。"
        ));

        map.put(EntityType.RABBIT, List.of(
                "%player%，来抓我呀！",
                "%player%，我跳得比你高。",
                "%player%，胡萝卜在哪？",
                "%player%，我吃蒲公英就能变迷你兔。",
                "%player%，兔子脚是好东西，但我不想给你。",
                "%player%，杀手兔是我表哥，它很凶。"
        ));

        map.put(EntityType.WOLF, List.of(
                "汪！%player%，骨头呢？",
                "%player%，你手上有肉吗？",
                "汪呜……%player%，我们做朋友吧。",
                "%player%，你驯服我，我就跟你出生入死。",
                "%player%，我打骷髅最厉害了。",
                "%player%，你打我的孩子试试？"
        ));

        map.put(EntityType.CAT, List.of(
                "喵……%player%，摸我一下试试？",
                "%player%，你身上有鱼的味道。",
                "喵呜，%player%，我不理你是我的权利。",
                "%player%，我会赶走苦力怕和幻翼。",
                "%player%，我可以坐在你的箱子上，不让你打开。",
                "%player%，你驯服我，我就跟你回家。",
                "%player%，我从来不听你的话，我只是允许你摸我。"
        ));

        map.put(EntityType.PARROT, List.of(
                "%player%，听我模仿苦力怕——嘶嘶嘶！",
                "%player%，跳舞吗？我只会原地蹦。",
                "%player%，海盗的故事听过吗？",
                "%player%，我可以坐在你肩膀上。",
                "%player%，你喂我种子，我就跟你走。",
                "%player%，我能模仿各种声音，包括你的脚步声。"
        ));

        map.put(EntityType.FOX, List.of(
                "%player%，你的东西我可以拿走吗？",
                "%player%，我藏在雪地里的东西被你看到了？",
                "%player%，狐狸不狡猾，是聪明。",
                "%player%，我叼着东西跑得比你快。",
                "%player%，雪狐是我亲戚，它比我白。",
                "%player%，我可以偷你的鸡，你拦得住吗？"
        ));

        map.put(EntityType.PANDA, List.of(
                "嗯……%player%，竹子要吗？",
                "%player%，我在吃竹子，勿扰。",
                "%player%，我滚起来很好看。",
                "%player%，我有 7 种不同的性格，你猜我是哪种？",
                "%player%，我懒惰是因为我是国宝。",
                "%player%，我生宝宝的时候很温柔。"
        ));

        map.put(EntityType.BEE, List.of(
                "嗡嗡嗡……%player%，别惹我。",
                "%player%，我的蜂蜜很甜，但我很凶。",
                "%player%，蜇了你我会死的，所以你别逼我。",
                "%player%，蜂巢里的蜜是我的，不许偷。",
                "%player%，你采蜜的样子比我笨。",
                "%player%，我有 3 根刺，3 条命。"
        ));

        map.put(EntityType.DOLPHIN, List.of(
                "咿咿咿！%player%，来游泳！",
                "%player%，跟着我，我能带你找宝藏。",
                "%player%，陆地动物真可怜。",
                "%player%，我在水下速度比你快十倍。",
                "%player%，你喂我鱼，我就带你玩。",
                "%player%，海洋是我家，你是客人。"
        ));

        map.put(EntityType.SQUID, List.of(
                "%player%，我喷你一脸墨水。",
                "%player%，深海很黑，我习惯了。",
                "%player%，墨囊要吗？写日记用得上。",
                "%player%，我浮在水面的时候，你不要吓我。",
                "%player%，鱿鱼须不是什么好东西，别吃。"
        ));

        map.put(EntityType.BAT, List.of(
                "%player%，我住在山洞里，你也是吗？",
                "%player%，倒挂着睡觉最舒服了。",
                "%player%，我长得丑但很温柔。",
                "%player%，你挖矿的时候我就飞出来吓你。",
                "%player%，我其实没什么用，就是很吵。"
        ));

        map.put(EntityType.POLAR_BEAR, List.of(
                "%player%，别靠近我的宝宝。",
                "%player%，雪地里我是老大。",
                "%player%，你看起来很好吃。",
                "%player%，我游泳比你快。",
                "%player%，冰原是我的地盘，你小心点。",
                "%player%，我冬眠的时候别打扰我。"
        ));

        map.put(EntityType.GOAT, List.of(
                "%player%，我撞你一下试试？",
                "%player%，山上的风景不错吧。",
                "%player%，我跳得比你高，撞得比你狠。",
                "%player%，我掉的山羊角可以做号角。",
                "%player%，你别站在悬崖边，我会撞你下去。",
                "%player%，我在雪地里是攀岩冠军。"
        ));

        map.put(EntityType.AXOLOTL, List.of(
                "%player%，我可爱吗？",
                "%player%，来水下玩，我保护你。",
                "%player%，粉色的我是最稀有的。",
                "%player%，我假装装死，你就以为我死了。",
                "%player%，打海底神殿带上我，我能帮你。"
        ));

        map.put(EntityType.FROG, List.of(
                "呱！%player%，小史莱姆吃过吗？",
                "%player%，荷叶上坐坐？",
                "呱呱呱，%player%，要下暴雨了。",
                "%player%，我吃小史莱姆会掉黏液球。",
                "%player%，我的卵叫蝌蚪，你见过吗？"
        ));

        map.put(EntityType.ALLAY, List.of(
                "%player%，给你一朵小花！",
                "%player%，我在找东西，别打扰我。",
                "%player%，跳舞吗？",
                "%player%，我可以帮你捡东西，但你得先找到我。",
                "%player%，我住在掠夺者前哨站的笼子里，救我。"
        ));

        map.put(EntityType.CAMEL, List.of(
                "%player%，两个人一起骑我才划算。",
                "%player%，沙漠里我最靠谱。",
                "%player%，我坐下的时候你上得来吗？",
                "%player%，我冲刺的时候会把你甩下去。",
                "%player%，我过沙丘如履平地。"
        ));

        map.put(EntityType.LLAMA, List.of(
                "呸！%player%！",
                "%player%，我吐你一脸。",
                "%player%，别摸我的货箱。",
                "%player%，我跟流浪商人是一伙的。",
                "%player%，我组队的时候会打架。",
                "%player%，我吐口水的准头比弓箭还准。"
        ));

        map.put(EntityType.WARDEN, List.of(
                "%player%，你不该来这里。",
                "%player%，我听到了你的心跳。",
                "%player%，再走近一步试试。",
                "%player%，深渊之下，只有我一个声音。",
                "%player%，你的潜行对我没用，我靠的是听觉。",
                "%player%，你放下的羊毛，我已经踩过了。",
                "%player%，我打你一下，你可能会飞出去一个区块。",
                "%player%，你确定要跟我打吗？我劝你再想想。"
        ));

        return Collections.unmodifiableMap(map);
    }
}