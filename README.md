# TauntPlugin

一个功能丰富的 Minecraft Paper 服务器插件，集调侃、魔法、恐怖、社交、成就于一体。

支持版本：**Paper 1.21.4+**（开发目标 Paper 26.3）

---

## 📖 目录

- [功能总览](#功能总览)
- [安装](#安装)
- [命令](#命令)
- [权限](#权限)
- [配置文件](#配置文件)
- [玩法指南](#玩法指南)
- [开发者 API](#开发者-api)
- [常见问题](#常见问题)

---

## 功能总览

TauntPlugin 包含 **19 个独立模块**，所有模块可通过 `config.yml` 单独开关。

| 模块 | 说明 |
|------|------|
| **调侃系统** | 玩家聊天、移动、挖矿、死亡等行为触发调侃文案 |
| **服主的幽灵** | 幽灵随机偷走玩家物品，30 秒后归还 |
| **生物吐槽** | 30+ 种生物开口对玩家说话 |
| **肝度排行榜** | 按挖矿/放置方块统计肝度，周榜肝帝 |
| **血月事件** | 夜晚随机触发，怪物增强，全服公告 |
| **Herobrine** | 背后凝视、远处跟踪、床边出现、留言告示牌 |
| **幽灵模式** | 死亡时进入 30 秒幽灵状态，物品掉落在死亡地点 |
| **玩家状态效果** | 惊恐、冒汗、寒冷白雾呼吸、饥饿愤怒 |
| **玩家嘲讽** | `/taunt` 命令，支持自定义嘲讽文案 |
| **诡异村民** | 短暂出现的诡异村民跟踪玩家 |
| **影分身** | 玩家前方出现自己的分身 |
| **好友系统** | 添加好友、好友上线提示、免费传送 |
| **猜数字游戏** | 聊天栏输入数字猜答案，赢取肝度 |
| **猪神 Technoblade** | 传奇猪神降临，可与玩家互动 |
| **处决系统** | `/chujue` 处决玩家/实体/范围 |
| **魔杖系统** | **16+ 种魔咒，属性系统，亲和度，不可饶恕咒** |
| **许愿井** | 投入金属抽奖，可重铸魔杖 |
| **私人傀儡** | 用玩家 ID 命名的南瓜头搭建专属傀儡 |
| **成就系统** | 50+ 个成就，全服广播 |

---

## 安装

### 环境要求

| 项目 | 版本 |
|------|------|
| 服务端 | Paper 1.21.4+ |
| Java | 21+ |
| 必需依赖 | 无（Paper API 自带） |

### 安装步骤

1. 下载 `TauntPlugin.jar`
2. 放入服务端 `plugins/` 文件夹
3. 启动服务器，插件自动生成配置
4. 编辑 `plugins/TauntPlugin/config.yml` 按需调整
5. 重启服务器或执行 `/taunt reload`

### 资源包（可选）

魔杖使用 `ItemModel` 让钓竿显示为木棍，**不需要资源包**。所有功能在无资源包环境下也能正常工作。

---

## 命令

### `/taunt` — 玩家嘲讽

| 命令 | 说明 |
|------|------|
| `/taunt` | 嘲讽最近的玩家 |
| `/taunt <玩家名>` | 嘲讽指定玩家 |
| `/taunt set <文案>` | 添加自定义嘲讽文案 |
| `/taunt list` | 查看自定义文案 |
| `/taunt remove <序号>` | 删除指定文案 |
| `/taunt clear` | 清空自定义文案 |
| `/taunt reload` | 重载配置和消息 |

### `/wand` — 获取魔杖

| 命令 | 说明 |
|------|------|
| `/wand` | 获取一根随机属性的魔杖 |

**魔杖操作**：
- **Shift + 右键** — 切换魔咒
- **右键** — 发射当前魔咒

### `/puppet` — 私人傀儡

| 命令 | 说明 |
|------|------|
| `/puppet <玩家名>` | 获得效忠指定玩家的傀儡南瓜头 |

### `/techno` — 猪神

| 命令 | 说明 |
|------|------|
| `/techno spawn` | 在当前位置生成猪神 |
| `/techno remove` | 移除猪神 |
| `/techno status` | 查看猪神状态 |

### `/chujue` — 处决

| 命令 | 说明 |
|------|------|
| `/chujue <玩家名>` | 处决指定玩家 |
| `/chujue near <半径>` | 处决范围内所有实体 |
| `/chujue type <实体类型> [半径]` | 处决指定类型实体 |
| `/chujue look [距离]` | 处决视线内实体 |

### `/friend` — 好友

| 命令 | 说明 |
|------|------|
| `/friend add <玩家>` | 添加好友 |
| `/friend remove <玩家>` | 删除好友 |
| `/friend list` | 好友列表 |
| `/friend tp <玩家>` | 传送到好友（免费） |

### `/grind` — 肝度排行

| 命令 | 说明 |
|------|------|
| `/grind` | 查看自己的肝度 |
| `/grind top [数量]` | 总榜前 N 名 |
| `/grind weekly [数量]` | 周榜前 N 名 |

### `/ach` — 成就

| 命令 | 说明 |
|------|------|
| `/ach` | 查看自己的成就进度 |
| `/ach list` | 列出所有成就 |
| `/ach stats` | 查看统计数据 |
| `/ach <玩家名>` | 查看他人成就 |

---

## 权限

| 权限节点 | 说明 | 默认 |
|---------|------|------|
| `tauntplugin.reload` | 允许重载配置 | OP |
| `tauntplugin.techno` | 允许管理猪神 | OP |
| `tauntplugin.chujue` | 允许处决玩家 | OP |
| `tauntplugin.puppet` | 允许使用私人傀儡 | 所有玩家 |

---

## 配置文件

`plugins/TauntPlugin/config.yml` 结构（默认值）：

```yaml
general:
  owner-name: "Justin_Yan"

taunt:
  enabled: true
  global-cooldown-ms: 3000
  actions:
    chat:      { cooldown: 25000, chance: 0.35 }
    move:      { cooldown: 60000, chance: 0.25 }
    break:     { cooldown: 20000, chance: 0.4  }
    # ... 更多行为

ghost:
  enabled: true
  steal-interval-min-ms: 60000
  steal-interval-max-ms: 180000
  return-delay-ms: 30000
  player-cooldown-ms: 300000

mob-taunt:
  enabled: true
  check-interval-ms: 20000
  player-cooldown-ms: 120000
  trigger-chance: 0.7
  scan-radius: 16.0

grind:
  enabled: true
  show-name-tag: true

blood-moon:
  enabled: true
  check-interval-ms: 60000
  trigger-chance: 0.15
  duration-ms: 300000

herobrine:
  enabled: true
  skin-owner-uuid: "f84c6a79-0a4e-45e0-879b-cd49ebd4c4e2"
  haunt:
    enabled: true
    trigger-chance: 0.3
  tracker:
    enabled: true
    trigger-chance: 0.25
  sign:
    enabled: true
    trigger-chance: 0.2
  bedside:
    enabled: true
    trigger-chance: 0.5

ghost-mode:
  enabled: true
  duration-ms: 30000
  sphere-radius: 2.0

player-status:
  enabled: true
  panic:
    threshold: 0.25
    lock-ms: 15000
  sweat:
    health-threshold: 0.5
  breath:
    cold-temp-threshold: 0.15
  hungry:
    food-threshold: 10

player-taunt:
  enabled: true
  cooldown-ms: 5000
  nearest-max-distance: 30.0
  targeted-max-distance: 100.0
  max-custom-length: 80
  max-custom-count: 10

weird-villager:
  enabled: true
  check-interval-ms: 300000
  trigger-chance: 0.2

shadow-clone:
  enabled: true
  check-interval-ms: 300000
  trigger-chance: 0.25

friend:
  enabled: true

guess-number:
  enabled: true
  round-interval-ms: 300000
  round-duration-ms: 180000
  min-number: 1
  max-number: 100
  reward-grind: 100

technoblade:
  enabled: true
  follow-search-radius: 50.0
  follow-teleport-distance: 25.0

execution:
  enabled: true
  auto-execute-on-kill: true

wand:
  enabled: true
  max-flight-time-ms: 30000
  require-op-for-unforgivable: false   # ★ 默认全玩家可用
  max-active-projectiles: 3
  lumos-light-level: 15
  lumos-duration-ms: 10000

wish-well:
  enabled: true
  cooldown-ms: 3000
  reforge-cooldown-ms: 30000

puppet:
  enabled: true
  protect-radius: 12.0
  detect-radius: 3.0
  follow-owner: true
  follow-distance: 8.0
  immunity-from-owner: true
  tick-interval: 20
```

---

## 玩法指南

### 🪄 魔杖系统

魔杖是插件最复杂的系统，包含：

#### 属性系统

每根魔杖随机生成 4 个属性：

| 属性 | 取值范围 | 影响 |
|------|---------|------|
| **木材** | 15 种 | 亲和某一咒语分类，该类咒语伤害 ×1.3 |
| **杖芯** | 8 种 | 亲和某一咒语分类，该类咒语速度/时长 ×1.15 |
| **长度** | 8-16 英寸 | 影响投射物速度 |
| **柔韧性** | 7 档 | 影响全局冷却与伤害 |

#### 咒语列表

**投射型**：除你武器、昏昏倒地、统统石化、火焰熊熊、爆炸咒、四分五裂、飞来咒、羽加迪姆勒维奥萨、倒挂金钟、咧嘴呼啦啦、无声无息、锁舌封喉、**阿瓦达索命**、**夺魂咒**、**钻心咒**

**自身型**：盔甲护身、清水如泉、荧光闪烁

**目标型**：阿拉霍洞开（开锁铁门）

**范围型**：呼神护卫

**特殊**：护身咒、愈合如初、修复如初

#### 不可饶恕咒

阿瓦达索命、夺魂咒、钻心咒属于**不可饶恕咒**：

- ✅ 默认所有玩家可用
- ⚠️ **每使用 5 次，最大生命值永久 -2**
- ⚠️ 最低降至 **3 颗心**
- 💊 可通过**善行**（治疗他人 10 次）或**下界合金锭 + 许愿井**净化

#### 亲和度系统

- 魔杖未认主时，第一次使用会**绑定当前玩家**
- 每次使用亲密度 +1%
- 达到 100% 时，**其他人使用会触发爆炸反噬**

### 🤖 私人傀儡

1. 用 `/puppet <玩家名>` 获得一个带附魔光效的"XXX的傀儡"南瓜头
2. 用南瓜头 + 2 雪块 / 4 铁块搭建傀儡
3. 傀儡自动：
   - 攻击主人附近的敌对生物
   - 反击攻击主人的任何实体
   - 跟随主人（可配置）
   - 免疫主人伤害

### 🏆 成就系统

50+ 成就，覆盖所有插件功能。包括：

- 挖矿类（初次挖掘 → 肝帝之王）
- 死亡类（初尝死亡 → 死士）
- 魔杖类（初学魔法 → 霍格沃茨毕业生）
- 不可饶恕咒类（**大难不死的孩子**、**人杖合一**）
- 诡异事件类（Herobrine 相关）
- 硬核类（万事通、传说玩家）

---

## 开发者 API

### 事件

所有事件定义在 `TauntEvents` 类：

```java
// 血月开始
@EventHandler
public void onBloodMoonStart(TauntEvents.BloodMoonStartEvent event) {
    long duration = event.getDurationMs();
}

// 血月结束
@EventHandler
public void onBloodMoonEnd(TauntEvents.BloodMoonEndEvent event) { }

// 玩家进入幽灵模式
@EventHandler
public void onGhostMode(TauntEvents.PlayerEnterGhostModeEvent event) {
    Player player = event.getPlayer();
}

// 玩家被处决
@EventHandler
public void onExecuted(TauntEvents.PlayerExecutedEvent event) {
    Player victim = event.getVictim();
    CommandSender executor = event.getExecutor();
}
```

### 工具类 `TauntUtils`

```java
// 成就挂钩
TauntUtils.unlock(plugin, player, AchievementManager.Ach.FIRST_SPELL);
TauntUtils.increment(plugin, player, "spells_cast", 1);
TauntUtils.setCounter(plugin, player, "unique_spells", count);

// 实体朝向
TauntUtils.faceTo(entity, targetLocation);

// 粒子安全发射
TauntUtils.spawnParticleSafe(world, Particle.FLAME, x, y, z, 10, 0.1, 0.1, 0.1, 0.05);

// 玩家检索
Player found = TauntUtils.findPlayerByName("Steve");
List<Player> nearby = TauntUtils.getNearbyPlayers(loc, 10.0, excludePlayer);

// 安全击杀
TauntUtils.killPlayer(plugin, victim);
```

### 主类 Getter

```java
TauntPlugin plugin = (TauntPlugin) Bukkit.getPluginManager().getPlugin("TauntPlugin");

plugin.getWandManager();       // 魔杖系统
plugin.getPuppetManager();     // 傀儡系统
plugin.getFriendManager();     // 好友系统
plugin.getAchievementManager();// 成就系统
plugin.getGrindManager();      // 肝度系统
plugin.getMessageManager();    // 消息系统
// ... 等
```

### 消息系统

`messages.yml` 支持：
- `§` 颜色代码
- `{key}` 字符串占位符
- 消息池随机抽取
- 热重载

```java
MessageManager messages = plugin.getMessageManager();

// 发送单条消息
messages.send(player, "friend.added", Map.of("{target}", "Steve"));

// 随机消息池
messages.random("taunt.pools.chat", Map.of("{player}", player.getName()));
```

---

## 常见问题

### Q: 魔杖右键没反应？

**A**: 检查 `config.yml` 中 `wand.enabled: true`。如果还是不行，在 `WandManager.onInteract` 第一行加调试日志：

```java
plugin.getLogger().info("[魔杖调试] 事件触发，" + player.getName());
```

### Q: 中文客户端显示英文？

**A**: `Component.translatable()` 依赖客户端语言包。如果服务端自定义渲染或客户端未加载语言文件，会显示 fallback 英文名。

### Q: 不可饶恕咒的惩罚能撤销吗？

**A**: 可以，三种方式：
1. 用 `/wand` 施放**愈合如初**治疗其他玩家 10 次
2. 用**下界合金锭**对许愿井右键
3. 管理员手动移除 `AttributeModifier`（需要 OP 权限）

### Q: 傀儡不攻击敌人？

**A**: 检查：
- `config.yml` 中 `puppet.enabled: true`
- 傀儡与主人是否在同一世界
- 敌人是否在 `protect-radius`（默认 12 格）范围内

### Q: 修改配置后为什么不生效？

**A**: 执行 `/taunt reload` 或重启服务器。部分开关类修改（如 `xxx.enabled`）需要完全重启。

### Q: 如何完全禁用某个模块？

**A**: 在 `config.yml` 里把对应模块的 `enabled` 改为 `false`，重启服务器。

### Q: 数据存在哪里？

**A**: 各模块数据保存在 `plugins/TauntPlugin/` 下：

```
plugins/TauntPlugin/
├── config.yml              # 主配置
├── messages.yml            # 消息配置
├── achievements.yml        # 成就数据
├── friends.yml             # 好友数据
├── grind.yml               # 肝度数据
├── custom_taunts.yml       # 自定义嘲讽
├── ForcePack/              # ForcePack 资源包（如果使用）
│   └── wand-rp.zip
└── logs/                   # 日志
```

---

## 构建

```bash
git clone <repository>
cd TauntPlugin
mvn clean package
```

生成的 `TauntPlugin-x.x.x.jar` 位于 `target/`。

---

## 许可

本项目采用 MIT 许可证。

---

## 贡献

欢迎提交 Issue 和 Pull Request。

**代码风格**：
- Java 21 语法（`switch` 表达式、`record`、`instanceof` 模式匹配）
- Paper API 优先于 Bukkit API
- 所有公开 API 加 Javadoc
- 每个 Manager 独立处理自己的事件和任务，通过 `TauntPlugin` 的 Getter 互相通信

---

## 致谢

- 灵感来源：Technoblade、哈利波特系列、Minecraft 原版机制
- 使用的 API：Paper、Adventure、Gson

**Technoblade never dies.**
- by Justin_Yan and Deepseek