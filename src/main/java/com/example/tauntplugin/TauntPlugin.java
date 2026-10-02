package com.example.tauntplugin;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class TauntPlugin extends JavaPlugin {

    // ==================== Manager 字段 ====================
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

    @Override
    public void onEnable() {
        // ========== 基础 Manager ==========
        tauntManager = new TauntManager(this);
        ghostManager = new GhostManager(this);
        mobTauntManager = new MobTauntManager(this);
        grindManager = new GrindManager(this);
        bloodMoonManager = new BloodMoonManager(this);
        skinFetcher = new SkinFetcher(this);
        herobrineManager = new HerobrineManager(this, skinFetcher);
        ghostModeManager = new GhostModeManager(this);
        playerStatusEffectManager = new PlayerStatusEffectManager(this);
        playerTauntManager = new PlayerTauntManager(this);

        // ★ friendManager 必须在 TauntListener 之前创建
        friendManager = new FriendManager(this);

        // ========== 依赖 friendManager 的 Manager ==========
        weirdVillagerManager = new WeirdVillagerManager(this);
        shadowCloneManager = new ShadowCloneManager(this);
        guessNumberManager = new GuessNumberManager(this, grindManager);

        // ★ 猪神 Technoblade
        technobladeManager = new TechnobladeManager(this);

        // ========== 注册事件监听器 ==========
        getServer().getPluginManager().registerEvents(
                new TauntListener(this, tauntManager, ghostManager, grindManager, friendManager),
                this);
        getServer().getPluginManager().registerEvents(bloodMoonManager, this);
        getServer().getPluginManager().registerEvents(herobrineManager, this);
        getServer().getPluginManager().registerEvents(ghostModeManager, this);
        getServer().getPluginManager().registerEvents(playerStatusEffectManager, this);
        getServer().getPluginManager().registerEvents(guessNumberManager, this);
        // ★ 注册猪神监听器
        getServer().getPluginManager().registerEvents(technobladeManager, this);

        // ========== 注册命令 ==========
        // /grind
        PluginCommand grindCmd = getCommand("grind");
        if (grindCmd != null) {
            GrindCommand executor = new GrindCommand(grindManager);
            grindCmd.setExecutor(executor);
            grindCmd.setTabCompleter(executor);
        }

        // /taunt
        PluginCommand tauntCmd = getCommand("taunt");
        if (tauntCmd != null) {
            TauntCommand executor = new TauntCommand(playerTauntManager);
            tauntCmd.setExecutor(executor);
            tauntCmd.setTabCompleter(executor);
        }

        // /friend
        PluginCommand friendCmd = getCommand("friend");
        if (friendCmd != null) {
            FriendCommand executor = new FriendCommand(friendManager);
            friendCmd.setExecutor(executor);
            friendCmd.setTabCompleter(executor);
        }

        // ★ /techno
        PluginCommand technoCmd = getCommand("techno");
        if (technoCmd != null) {
            TechnoCommand executor = new TechnoCommand(technobladeManager);
            technoCmd.setExecutor(executor);
            technoCmd.setTabCompleter(executor);
        }

        getLogger().info("TauntPlugin 已启用：调侃、幽灵、生物、肝度、血月、Herobrine、幽灵模式、状态效果、嘲讽、好友、猜数字、猪神同时上线。");
    }

    @Override
    public void onDisable() {
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
        getLogger().info("TauntPlugin 已禁用。");
    }

    // ==================== Getter ====================
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
}