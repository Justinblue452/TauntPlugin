package com.example.tauntplugin;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class TauntPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private MessageManager messageManager;
    private CooldownManager tauntCooldown;

    private TauntManager tauntManager;
    private GhostManager ghostManager;
    private MobTauntManager mobTauntManager;
    private GrindManager grindManager;
    private BloodMoonManager bloodMoonManager;
    private SkinFetcher skinFetcher;
    private HerobrineManager herobrineManager;
    private GhostModeManager ghostModeManager;
    private PlayerStatusEffectManager playerStatusEffectManager;
    private PlayerTauntManager playerTauntManager;
    private WeirdVillagerManager weirdVillagerManager;
    private ShadowCloneManager shadowCloneManager;
    private FriendManager friendManager;
    private GuessNumberManager guessNumberManager;
    private TechnobladeManager technobladeManager;
    private ExecutionManager executionManager;
    private WandManager wandManager;
    private WishWellManager wishWellManager;
    private AchievementManager achievementManager;
    private PuppetManager puppetManager;

    @Override
    public void onEnable() {
        // ═══════════════ 基础设施 ═══════════════
        configManager = new ConfigManager(this);
        messageManager = new MessageManager(this);
        tauntCooldown = new CooldownManager(this, 15000L, 3000L);
        achievementManager = new AchievementManager(this);

        // ═══════════════ 各功能 Manager ═══════════════
        if (configManager.isEnabled("taunt")) {
            tauntManager = new TauntManager(this, configManager);
        }
        if (configManager.isEnabled("ghost")) {
            ghostManager = new GhostManager(this, configManager);
        }
        if (configManager.isEnabled("mob-taunt")) {
            mobTauntManager = new MobTauntManager(this, configManager);
        }
        if (configManager.isEnabled("grind")) {
            grindManager = new GrindManager(this, configManager);
        }

        skinFetcher = new SkinFetcher(this);

        if (configManager.isEnabled("herobrine")) {
            herobrineManager = new HerobrineManager(this, skinFetcher, configManager);
        }
        if (configManager.isEnabled("ghost-mode")) {
            ghostModeManager = new GhostModeManager(this, configManager);
        }
        if (configManager.isEnabled("player-status")) {
            playerStatusEffectManager = new PlayerStatusEffectManager(this, configManager);
        }
        if (configManager.isEnabled("player-taunt")) {
            playerTauntManager = new PlayerTauntManager(this, configManager);
        }
        // ★ FriendManager：接收 messageManager
        if (configManager.isEnabled("friend")) {
            friendManager = new FriendManager(this, messageManager);
        }
        if (configManager.isEnabled("weird-villager")) {
            weirdVillagerManager = new WeirdVillagerManager(this, configManager);
        }
        if (configManager.isEnabled("shadow-clone")) {
            shadowCloneManager = new ShadowCloneManager(this, configManager);
        }
        // ★ GuessNumberManager：接收 messageManager + grindManager
        if (configManager.isEnabled("guess-number")) {
            if (grindManager != null) {
                guessNumberManager = new GuessNumberManager(this, messageManager, grindManager, configManager);
            } else {
                getLogger().warning("[猜数字] 需要先启用 grind 模块，已跳过");
            }
        }
        // ★ TechnobladeManager：接收 messageManager
        if (configManager.isEnabled("technoblade")) {
            technobladeManager = new TechnobladeManager(this, messageManager, configManager);
        }
        // ★ ExecutionManager：接收 messageManager
        if (configManager.isEnabled("execution")) {
            executionManager = new ExecutionManager(this, messageManager, configManager);
        }
        // ★ WandManager：接收 messageManager
        if (configManager.isEnabled("wand")) {
            wandManager = new WandManager(this, messageManager, configManager);
        }
        //私人傀儡
        if (configManager.isEnabled("puppet")) {
            puppetManager = new PuppetManager(this, configManager, messageManager);
        }
        // ★ WishWellManager：接收 messageManager
        if (configManager.isEnabled("wish-well")) {
            wishWellManager = new WishWellManager(this, messageManager, configManager, wandManager);
        }
        // BloodMoonManager：只接收 configManager
        if (configManager.isEnabled("blood-moon")) {
            bloodMoonManager = new BloodMoonManager(this, configManager);
        }

        // ═══════════════ 注册事件监听器 ═══════════════
        if (puppetManager != null) {
            getServer().getPluginManager().registerEvents(puppetManager, this);
        }
        if (tauntManager != null) {
            getServer().getPluginManager().registerEvents(tauntManager, this);
        }

        if (tauntManager != null && ghostManager != null
                && grindManager != null && friendManager != null) {
            getServer().getPluginManager().registerEvents(
                    new TauntListener(this, tauntManager, ghostManager, grindManager,
                            friendManager, wandManager), this);
        }

        getServer().getPluginManager().registerEvents(
                new AchievementListener(this, achievementManager), this);

        if (bloodMoonManager != null) getServer().getPluginManager().registerEvents(bloodMoonManager, this);
        if (herobrineManager != null) getServer().getPluginManager().registerEvents(herobrineManager, this);
        if (ghostModeManager != null) getServer().getPluginManager().registerEvents(ghostModeManager, this);
        if (playerStatusEffectManager != null) getServer().getPluginManager().registerEvents(playerStatusEffectManager, this);
        if (guessNumberManager != null) getServer().getPluginManager().registerEvents(guessNumberManager, this);
        if (technobladeManager != null) getServer().getPluginManager().registerEvents(technobladeManager, this);
        if (executionManager != null) getServer().getPluginManager().registerEvents(executionManager, this);
        if (wandManager != null) getServer().getPluginManager().registerEvents(wandManager, this);
        if (wishWellManager != null) getServer().getPluginManager().registerEvents(wishWellManager, this);

        // ═══════════════ 注册命令 ═══════════════
        //puppet
        PluginCommand puppetCmd = getCommand("puppet");
        if (puppetCmd != null && puppetManager != null) {
            puppetCmd.setExecutor(puppetManager);
            puppetCmd.setTabCompleter(puppetManager);
        }
        // /grind
        PluginCommand grindCmd = getCommand("grind");
        if (grindCmd != null && grindManager != null) {
            GrindCommand e = new GrindCommand(grindManager);
            grindCmd.setExecutor(e);
            grindCmd.setTabCompleter(e);
        }

        // /taunt（含 reload 子命令）
        PluginCommand tauntCmd = getCommand("taunt");
        if (tauntCmd != null && playerTauntManager != null) {
            TauntCommand delegate = new TauntCommand(playerTauntManager);
            tauntCmd.setExecutor((sender, command, label, args) -> {
                if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
                    if (!sender.hasPermission("tauntplugin.reload") && !sender.isOp()) {
                        messageManager.send(sender, "common.no-permission");
                        return true;
                    }
                    configManager.reload();
                    messageManager.reload();
                    messageManager.send(sender, "common.reload-success");
                    messageManager.send(sender, "common.reload-warn");
                    return true;
                }
                return delegate.onCommand(sender, command, label, args);
            });
            tauntCmd.setTabCompleter(delegate);
        }

        // /friend
        PluginCommand friendCmd = getCommand("friend");
        if (friendCmd != null && friendManager != null) {
            FriendCommand e = new FriendCommand(friendManager, messageManager);
            friendCmd.setExecutor(e);
            friendCmd.setTabCompleter(e);
        }

        // /techno
        PluginCommand technoCmd = getCommand("techno");
        if (technoCmd != null && technobladeManager != null) {
            TechnoCommand e = new TechnoCommand(technobladeManager);
            technoCmd.setExecutor(e);
            technoCmd.setTabCompleter(e);
        }

        // /chujue
        PluginCommand chujueCmd = getCommand("chujue");
        if (chujueCmd != null && executionManager != null) {
            ExecutionCommand e = new ExecutionCommand(executionManager);
            chujueCmd.setExecutor(e);
            chujueCmd.setTabCompleter(e);
        }

        // /wand
        PluginCommand wandCmd = getCommand("wand");
        if (wandCmd != null && wandManager != null) {
            wandCmd.setExecutor(new WandCommand(wandManager));
        }

        // /ach
        PluginCommand achCmd = getCommand("ach");
        if (achCmd != null) {
            achCmd.setExecutor(new AchievementCommand(achievementManager));
        }

        getLogger().info("TauntPlugin 已启用。");
    }

    @Override
    public void onDisable() {
        if (puppetManager != null) puppetManager.shutdown();
        if (ghostManager != null) ghostManager.shutdown();
        if (mobTauntManager != null) mobTauntManager.shutdown();
        if (grindManager != null) grindManager.shutdown();
        if (bloodMoonManager != null) bloodMoonManager.shutdown();
        if (herobrineManager != null) herobrineManager.shutdown();
        if (ghostModeManager != null) ghostModeManager.shutdown();
        if (playerStatusEffectManager != null) playerStatusEffectManager.shutdown();
        if (playerTauntManager != null) playerTauntManager.shutdown();
        if (weirdVillagerManager != null) weirdVillagerManager.shutdown();
        if (shadowCloneManager != null) shadowCloneManager.shutdown();
        if (friendManager != null) friendManager.shutdown();
        if (guessNumberManager != null) guessNumberManager.shutdown();
        if (technobladeManager != null) technobladeManager.shutdown();
        if (executionManager != null) executionManager.shutdown();
        if (wandManager != null) wandManager.shutdown();
        if (wishWellManager != null) wishWellManager.shutdown();
        if (achievementManager != null) achievementManager.shutdown();
        if (tauntCooldown != null) tauntCooldown.shutdown();
        getLogger().info("TauntPlugin 已禁用。");
    }

    // ═══════════════ Getter ═══════════════
    public PuppetManager getPuppetManager() { return puppetManager; }
    public ConfigManager getConfigManager() { return configManager; }
    public MessageManager getMessageManager() { return messageManager; }
    public CooldownManager getTauntCooldown() { return tauntCooldown; }
    public TauntManager getTauntManager() { return tauntManager; }
    public GhostManager getGhostManager() { return ghostManager; }
    public MobTauntManager getMobTauntManager() { return mobTauntManager; }
    public GrindManager getGrindManager() { return grindManager; }
    public BloodMoonManager getBloodMoonManager() { return bloodMoonManager; }
    public SkinFetcher getSkinFetcher() { return skinFetcher; }
    public HerobrineManager getHerobrineManager() { return herobrineManager; }
    public GhostModeManager getGhostModeManager() { return ghostModeManager; }
    public PlayerStatusEffectManager getPlayerStatusEffectManager() { return playerStatusEffectManager; }
    public PlayerTauntManager getPlayerTauntManager() { return playerTauntManager; }
    public FriendManager getFriendManager() { return friendManager; }
    public TechnobladeManager getTechnobladeManager() { return technobladeManager; }
    public ExecutionManager getExecutionManager() { return executionManager; }
    public WandManager getWandManager() { return wandManager; }
    public WishWellManager getWishWellManager() { return wishWellManager; }
    public AchievementManager getAchievementManager() { return achievementManager; }
}