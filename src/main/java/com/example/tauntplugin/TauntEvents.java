package com.example.tauntplugin;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * TauntPlugin 的自定义事件集合。
 * 用于 Manager 之间的解耦通信，避免双向依赖。
 */
public final class TauntEvents {

    private TauntEvents() {}

    // ═══════════════════════════════════════════════
    //  血月事件
    // ═══════════════════════════════════════════════

    /**
     * 血月开始时触发。
     * 由 BloodMoonManager 触发，TauntManager 监听以更新死亡文案。
     */
    public static class BloodMoonStartEvent extends Event {
        private static final HandlerList HANDLERS = new HandlerList();

        private final long durationMs;

        public BloodMoonStartEvent(long durationMs) {
            this.durationMs = durationMs;
        }

        public long getDurationMs() { return durationMs; }

        @Override
        public HandlerList getHandlers() { return HANDLERS; }

        public static HandlerList getHandlerList() { return HANDLERS; }
    }

    /**
     * 血月结束时触发。
     */
    public static class BloodMoonEndEvent extends Event {
        private static final HandlerList HANDLERS = new HandlerList();

        public BloodMoonEndEvent() {}

        @Override
        public HandlerList getHandlers() { return HANDLERS; }

        public static HandlerList getHandlerList() { return HANDLERS; }
    }

    // ═══════════════════════════════════════════════
    //  未来可扩展的事件（占位）
    // ═══════════════════════════════════════════════

    /**
     * 玩家进入幽灵模式事件。
     */
    public static class PlayerEnterGhostModeEvent extends Event {
        private static final HandlerList HANDLERS = new HandlerList();

        private final org.bukkit.entity.Player player;
        private final long durationMs;

        public PlayerEnterGhostModeEvent(org.bukkit.entity.Player player, long durationMs) {
            this.player = player;
            this.durationMs = durationMs;
        }

        public org.bukkit.entity.Player getPlayer() { return player; }
        public long getDurationMs() { return durationMs; }

        @Override
        public HandlerList getHandlers() { return HANDLERS; }

        public static HandlerList getHandlerList() { return HANDLERS; }
    }

    /**
     * 玩家被处决事件。
     */
    public static class PlayerExecutedEvent extends Event {
        private static final HandlerList HANDLERS = new HandlerList();

        private final org.bukkit.entity.Player victim;
        private final org.bukkit.command.CommandSender executor;

        public PlayerExecutedEvent(org.bukkit.entity.Player victim,
                                   org.bukkit.command.CommandSender executor) {
            this.victim = victim;
            this.executor = executor;
        }

        public org.bukkit.entity.Player getVictim() { return victim; }
        public org.bukkit.command.CommandSender getExecutor() { return executor; }

        @Override
        public HandlerList getHandlers() { return HANDLERS; }

        public static HandlerList getHandlerList() { return HANDLERS; }
    }
}