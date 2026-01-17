package com.warmpixel.storyadventure.item;

import com.warmpixel.storyadventure.client.ui.admin.AdminDashboardScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin Wand - A special item for story administrators.
 * Right-click opens the admin dashboard UI.
 * Shift+Right-click creates trigger boxes (2-click selection).
 */
public class AdminWandItem extends Item {
    
    // Track pending trigger box corners per player
    private static final Map<UUID, Vec3> pendingCorner1 = new HashMap<>();
    
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
                player.sendSystemMessage(Component.translatable("item.storyadventure.admin_wand.no_permission"));
            }
            return InteractionResultHolder.fail(stack);
        }
        
        // Shift+Right Click = Creation Mode
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                // Check if Ctrl is also held (check via sneaking + crouching state)
                // For now, Shift+RClick = Trigger Box, we'll add waypoint via command
                handleTriggerBoxCreation(player);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        
        // Normal Right Click = Open Admin UI
        if (level.isClientSide) {
            openAdminUI();
        } else {
            player.sendSystemMessage(Component.translatable("item.storyadventure.admin_wand.opening_dashboard"));
            
            // Also sync trigger boxes to this player for gizmo rendering
            if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                syncTriggerBoxesToPlayer(sp);
            }
        }
        
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
    
    /**
     * Handle shift+right-click on living entities to display NBT/tag data.
     */
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!player.hasPermissions(2)) {
            return InteractionResult.PASS;
        }
        
        // Shift+Right Click on entity = inspect NBT and tags
        if (player.isShiftKeyDown() && !player.level().isClientSide) {
            inspectEntity(player, target);
            return InteractionResult.SUCCESS;
        }
        
        return InteractionResult.PASS;
    }
    
    /**
     * Inspect any entity (including non-living like vehicles) and display info in chat.
     * Called from server-side only.
     */
    public static void inspectEntity(Player player, Entity entity) {
        player.sendSystemMessage(Component.literal("═══════════════════════════════════").withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.literal("Entity Inspector").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        player.sendSystemMessage(Component.literal("═══════════════════════════════════").withStyle(ChatFormatting.GOLD));
        
        // Basic info
        player.sendSystemMessage(Component.literal("Type: ").withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(entity.getType().toString()).withStyle(ChatFormatting.WHITE)));
        player.sendSystemMessage(Component.literal("UUID: ").withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(entity.getUUID().toString()).withStyle(ChatFormatting.GRAY)));
        player.sendSystemMessage(Component.literal("Name: ").withStyle(ChatFormatting.YELLOW)
            .append(entity.getDisplayName().copy().withStyle(ChatFormatting.WHITE)));
        
        // Position
        player.sendSystemMessage(Component.literal("Position: ").withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(String.format("%.2f, %.2f, %.2f", entity.getX(), entity.getY(), entity.getZ())).withStyle(ChatFormatting.WHITE)));
        
        // Tags
        var tags = entity.getTags();
        if (tags.isEmpty()) {
            player.sendSystemMessage(Component.literal("Tags: ").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal("(none)").withStyle(ChatFormatting.GRAY)));
        } else {
            player.sendSystemMessage(Component.literal("Tags (" + tags.size() + "):").withStyle(ChatFormatting.YELLOW));
            for (String tag : tags) {
                player.sendSystemMessage(Component.literal("  • ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(tag).withStyle(ChatFormatting.GREEN)));
            }
        }
        
        // NBT Data
        player.sendSystemMessage(Component.literal("NBT Data:").withStyle(ChatFormatting.YELLOW));
        CompoundTag nbt = new CompoundTag();
        entity.saveWithoutId(nbt);
        
        // Print key NBT fields (truncated to prevent spam)
        int count = 0;
        for (String key : nbt.getAllKeys()) {
            if (count >= 15) {
                player.sendSystemMessage(Component.literal("  ... and " + (nbt.getAllKeys().size() - 15) + " more fields").withStyle(ChatFormatting.GRAY));
                break;
            }
            String value = nbt.get(key).toString();
            if (value.length() > 50) {
                value = value.substring(0, 47) + "...";
            }
            player.sendSystemMessage(Component.literal("  " + key + ": ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(value).withStyle(ChatFormatting.WHITE)));
            count++;
        }
        
        player.sendSystemMessage(Component.literal("═══════════════════════════════════").withStyle(ChatFormatting.GOLD));
    }
    
    /**
     * Handle trigger box creation (server-side).
     * First click: Set corner 1
     * Second click: Set corner 2 and create box
     */
    private void handleTriggerBoxCreation(Player player) {
        UUID playerId = player.getUUID();
        Vec3 currentPos = player.position();
        
        if (pendingCorner1.containsKey(playerId)) {
            // Second click - create the box
            Vec3 corner1 = pendingCorner1.remove(playerId);
            Vec3 corner2 = currentPos;
            
            // Generate unique ID
            String boxId = "trigger_" + System.currentTimeMillis() % 100000;
            
            // Log the creation
            player.sendSystemMessage(Component.translatable("item.storyadventure.admin_wand.trigger_created", 
                boxId, corner1.x, corner1.y, corner1.z, corner2.x, corner2.y, corner2.z));
            
            // Create and save the trigger box on server
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                var manager = com.warmpixel.storyadventure.core.waypoint.TriggerBoxManager.getInstance();
                if (manager != null) {
                    var bounds = new net.minecraft.world.phys.AABB(corner1, corner2);
                    manager.createBox(boxId, bounds, Component.translatable("gui.storyadventure.admin.triggers.new_trigger").getString());
                    
                    // Sync all boxes to this player for gizmo rendering
                    syncTriggerBoxesToPlayer(serverPlayer);
                }
            }
            
            // Store in temporary registry for editor UI
            PendingTriggerBoxes.store(playerId, boxId, corner1, corner2);
            
        } else {
            // First click - store corner 1
            pendingCorner1.put(playerId, currentPos);
            player.sendSystemMessage(Component.translatable("item.storyadventure.admin_wand.corner1_set", 
                currentPos.x, currentPos.y, currentPos.z));
        }
    }
    
    /**
     * Cancel pending box creation for a player.
     */
    public static void cancelPending(UUID playerId) {
        pendingCorner1.remove(playerId);
    }
    
    /**
     * Check if a player has a pending corner.
     */
    public static boolean hasPendingCorner(UUID playerId) {
        return pendingCorner1.containsKey(playerId);
    }
    
    @net.fabricmc.api.Environment(net.fabricmc.api.EnvType.CLIENT)
    private void openAdminUI() {
        Minecraft.getInstance().setScreen(new AdminDashboardScreen());
    }
    
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.storyadventure.admin_wand.tooltip.title").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.translatable("item.storyadventure.admin_wand.tooltip.right_click").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("item.storyadventure.admin_wand.tooltip.shift_right_click").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.literal("Shift+Right-Click Entity: ").withStyle(ChatFormatting.YELLOW)
            .append(Component.literal("Inspect NBT/Tags").withStyle(ChatFormatting.AQUA)));
        tooltip.add(Component.translatable("item.storyadventure.admin_wand.tooltip.waypoint").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.storyadventure.admin_wand.tooltip.admin_only").withStyle(ChatFormatting.RED));
    }
    
    @Override
    public boolean isFoil(ItemStack stack) {
        return true; // Always show enchantment glint
    }
    
    /**
     * Sync all trigger boxes to a player for gizmo rendering.
     */
    public static void syncTriggerBoxesToPlayer(net.minecraft.server.level.ServerPlayer player) {
        var manager = com.warmpixel.storyadventure.core.waypoint.TriggerBoxManager.getInstance();
        if (manager == null) return;
        
        java.util.List<com.warmpixel.storyadventure.network.SyncTriggerBoxesPayload.TriggerBoxData> boxes = 
            new java.util.ArrayList<>();
        
        for (var box : manager.getAllBoxes()) {
            var bounds = box.getBounds();
            boxes.add(new com.warmpixel.storyadventure.network.SyncTriggerBoxesPayload.TriggerBoxData(
                box.getId(), box.getLabel(),
                bounds.minX, bounds.minY, bounds.minZ,
                bounds.maxX, bounds.maxY, bounds.maxZ,
                !box.getPlayersInside().isEmpty()
            ));
        }
        
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, 
            new com.warmpixel.storyadventure.network.SyncTriggerBoxesPayload(boxes));
    }
    
    /**
     * Temporary storage for pending trigger boxes awaiting editor.
     */
    public static class PendingTriggerBoxes {
        private static final Map<UUID, PendingBox> pending = new HashMap<>();
        
        public static void store(UUID playerId, String boxId, Vec3 corner1, Vec3 corner2) {
            pending.put(playerId, new PendingBox(boxId, corner1, corner2));
        }
        
        public static PendingBox get(UUID playerId) {
            return pending.get(playerId);
        }
        
        public static PendingBox remove(UUID playerId) {
            return pending.remove(playerId);
        }
        
        public record PendingBox(String id, Vec3 corner1, Vec3 corner2) {}
    }
}
