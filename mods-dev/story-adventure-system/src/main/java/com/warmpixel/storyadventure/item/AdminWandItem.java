package com.warmpixel.storyadventure.item;

import com.warmpixel.storyadventure.client.ui.admin.AdminDashboardScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Admin Wand - A special item for story administrators.
 * Right-click opens the admin dashboard UI.
 */
public class AdminWandItem extends Item {
    
    public AdminWandItem() {
        super(new Properties()
            .stacksTo(1)
            .rarity(Rarity.EPIC)
        );
    }
    
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        
        // Check if player has permission (OP level 2+)
        if (!player.hasPermissions(2)) {
            if (!level.isClientSide) {
                player.sendSystemMessage(Component.literal("§c你没有权限使用管理员法杖！"));
            }
            return InteractionResultHolder.fail(stack);
        }
        
        // Open admin UI on client side
        if (level.isClientSide) {
            openAdminUI();
        } else {
            player.sendSystemMessage(Component.literal("§a正在打开管理员控制台..."));
        }
        
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
    
    @net.fabricmc.api.Environment(net.fabricmc.api.EnvType.CLIENT)
    private void openAdminUI() {
        Minecraft.getInstance().setScreen(new AdminDashboardScreen());
    }
    
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§7故事冒险系统管理工具"));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("§e右键§7打开管理员控制台"));
        tooltip.add(Component.literal("§c需要管理员权限"));
    }
    
    @Override
    public boolean isFoil(ItemStack stack) {
        return true; // Always show enchantment glint
    }
}
