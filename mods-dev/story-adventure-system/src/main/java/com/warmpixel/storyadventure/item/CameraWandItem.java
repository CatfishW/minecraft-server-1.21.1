package com.warmpixel.storyadventure.item;

import com.warmpixel.storyadventure.client.ui.CameraRecorderScreen;
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
import net.minecraft.ChatFormatting;

import java.util.List;

/**
 * Camera Recording Wand - A tool for recording camera positions and rotations.
 * Right-click opens the Camera Recorder UI panel.
 * Used to create camera paths for cutscenes.
 */
public class CameraWandItem extends Item {
    
    public CameraWandItem() {
        super(new Properties()
            .stacksTo(1)
            .rarity(Rarity.RARE)
        );
    }
    
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        
        // Check if player has permission (OP level 2+)
        if (!player.hasPermissions(2)) {
            if (!level.isClientSide) {
                player.sendSystemMessage(Component.literal("§c你需要管理员权限才能使用摄像机魔杖"));
            }
            return InteractionResultHolder.fail(stack);
        }
        
        // Open Camera Recorder UI on client
        if (level.isClientSide) {
            openCameraRecorderUI();
        }
        
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
    
    @net.fabricmc.api.Environment(net.fabricmc.api.EnvType.CLIENT)
    private void openCameraRecorderUI() {
        Minecraft.getInstance().setScreen(new CameraRecorderScreen());
    }
    
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("摄像机录制魔杖").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("右键 - 打开录制面板").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.literal("用于录制过场动画的摄像机路径").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("仅限管理员").withStyle(ChatFormatting.RED));
    }
    
    @Override
    public boolean isFoil(ItemStack stack) {
        return true; // Always show enchantment glint
    }
}
