package com.example.tauntplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class WandCommand implements CommandExecutor {

    private final WandManager wandManager;

    public WandCommand(WandManager wandManager) {
        this.wandManager = wandManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("此命令只能由玩家执行");
            return true;
        }

        ItemStack wand = wandManager.createWand();
        player.getInventory().addItem(wand);

        player.sendMessage(Component.text("═══════════════════════", NamedTextColor.GOLD));
        player.sendMessage(Component.text("✦ 你获得了魔杖 ✦", NamedTextColor.GOLD));
        player.sendMessage(Component.text("右键切换魔咒", NamedTextColor.GRAY));
        player.sendMessage(Component.text("左键发射魔咒", NamedTextColor.GRAY));
        player.sendMessage(Component.text("═══════════════════════", NamedTextColor.GOLD));

        player.playSound(player.getLocation(),
                org.bukkit.Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.2f);

        return true;
    }
}