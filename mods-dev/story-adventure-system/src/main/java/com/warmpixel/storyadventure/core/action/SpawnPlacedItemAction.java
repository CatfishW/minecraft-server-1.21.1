package com.warmpixel.storyadventure.core.action;

import com.google.gson.JsonObject;
import com.warmpixel.storyadventure.StoryAdventureMod;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Action to spawn a placed item that can only be picked up by players in this instance.
 * Uses the admin-item-placer mod's PlacedItemEntity.
 * 
 * JSON Usage:
 * {
 *   "type": "SPAWN_PLACED_ITEM",
 *   "item": "tacz:modern_kinetic_gun{GunId:\"tacz:m1911\"}",
 *   "x": -120.0,
 *   "y": 90.0,
 *   "z": 70.0,
 *   "count": 1
 * }
 */
public class SpawnPlacedItemAction implements NodeAction {

    private final String itemId;
    private final String nbt;
    private final double x, y, z;
    private final int count;

    public SpawnPlacedItemAction(String itemId, String nbt, double x, double y, double z, int count) {
        this.itemId = itemId;
        this.nbt = nbt;
        this.x = x;
        this.y = y;
        this.z = z;
        this.count = count > 0 ? count : 1;
    }

    @Override
    public String getType() {
        return "SPAWN_PLACED_ITEM";
    }

    @Override
    public void execute(List<ServerPlayer> players) {
        if (players.isEmpty()) return;
        
        ServerPlayer firstPlayer = players.get(0);
        var server = firstPlayer.getServer();
        if (server == null) return;
        
        // Get instance ID for this player
        String instanceId = "";
        try {
            var instanceManager = StoryAdventureMod.getInstance().getInstanceManager();
            var instance = instanceManager.getPlayerInstance(firstPlayer.getUUID());
            if (instance != null) {
                instanceId = instance.getInstanceId().toString();
            }
        } catch (Exception e) {
            StoryAdventureMod.LOGGER.warn("[SpawnPlacedItemAction] Could not get instance ID: {}", e.getMessage());
        }
        
        // Build the command to spawn the placed item
        // We use a command because PlacedItemEntity might be in a different mod
        String fullItemId = nbt != null && !nbt.isEmpty() ? itemId + nbt : itemId;
        
        try {
            // Try to use the PlacedItemEntity directly via reflection
            Class<?> entityClass = Class.forName("com.warmpixel.itemplacer.entity.PlacedItemEntity");
            Class<?> modClass = Class.forName("com.warmpixel.itemplacer.AdminItemPlacerMod");
            
            // Get the entity type
            Object entityType = modClass.getField("PLACED_ITEM_ENTITY").get(null);
            
            // Create the item stack using give command parsing
            ItemStack itemStack = parseItemStack(server, fullItemId);
            if (itemStack.isEmpty()) {
                StoryAdventureMod.LOGGER.warn("[SpawnPlacedItemAction] Failed to parse item: {}", fullItemId);
                return;
            }
            
            // Create and spawn the entity
            var level = firstPlayer.serverLevel();
            var constructor = entityClass.getConstructor(net.minecraft.world.level.Level.class, double.class, double.class, double.class);
            Object placedItem = constructor.newInstance(level, x, y + 0.1, z);
            
            // Set the item
            entityClass.getMethod("setItem", ItemStack.class).invoke(placedItem, itemStack);
            entityClass.getMethod("setCount", int.class).invoke(placedItem, count);
            
            // Set story instance ID to lock it to this instance
            if (!instanceId.isEmpty()) {
                entityClass.getMethod("setStoryInstanceId", String.class).invoke(placedItem, instanceId);
            }
            
            // Add entity tag for cleanup
            ((net.minecraft.world.entity.Entity) placedItem).addTag("story_entity");
            ((net.minecraft.world.entity.Entity) placedItem).addTag("story_placed_item");
            
            // Spawn the entity
            level.addFreshEntity((net.minecraft.world.entity.Entity) placedItem);
            
            StoryAdventureMod.LOGGER.info("[SpawnPlacedItemAction] Spawned {} x{} at ({}, {}, {}) for instance {}", 
                itemStack.getHoverName().getString(), count, x, y, z, instanceId);
            
        } catch (ClassNotFoundException e) {
            StoryAdventureMod.LOGGER.error("[SpawnPlacedItemAction] admin-item-placer mod not found!");
        } catch (Exception e) {
            StoryAdventureMod.LOGGER.error("[SpawnPlacedItemAction] Failed to spawn item: {}", e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Parse an item string like "tacz:modern_kinetic_gun{GunId:\"tacz:m1911\"}" into an ItemStack.
     */
    private ItemStack parseItemStack(net.minecraft.server.MinecraftServer server, String itemString) {
        try {
            // Use the give command's item parser
            String giveCmd = "give @p " + itemString + " 1";
            
            // Parse just the item part
            String itemPart = itemString;
            String nbtPart = "";
            
            int braceIdx = itemString.indexOf('{');
            if (braceIdx > 0) {
                itemPart = itemString.substring(0, braceIdx);
                nbtPart = itemString.substring(braceIdx);
            }
            
            // Get the item from registry
            ResourceLocation itemLoc = ResourceLocation.tryParse(itemPart);
            if (itemLoc == null) {
                return ItemStack.EMPTY;
            }
            
            var itemOpt = BuiltInRegistries.ITEM.getOptional(itemLoc);
            if (itemOpt.isEmpty()) {
                StoryAdventureMod.LOGGER.warn("[SpawnPlacedItemAction] Item not found in registry: {}", itemPart);
                return ItemStack.EMPTY;
            }
            
            ItemStack stack = new ItemStack(itemOpt.get());
            
            // If there's NBT, we need to parse it
            if (!nbtPart.isEmpty()) {
                try {
                    var nbtTag = net.minecraft.nbt.TagParser.parseTag(nbtPart);
                    // Apply custom data component
                    stack.applyComponents(
                        net.minecraft.core.component.DataComponentPatch.builder()
                            .set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, 
                                 net.minecraft.world.item.component.CustomData.of(nbtTag))
                            .build()
                    );
                } catch (Exception e) {
                    StoryAdventureMod.LOGGER.warn("[SpawnPlacedItemAction] Failed to parse NBT: {}", nbtPart);
                }
            }
            
            return stack;
        } catch (Exception e) {
            StoryAdventureMod.LOGGER.error("[SpawnPlacedItemAction] Error parsing item: {}", e.getMessage());
            return ItemStack.EMPTY;
        }
    }

    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "SPAWN_PLACED_ITEM");
        json.addProperty("item", itemId + (nbt != null ? nbt : ""));
        json.addProperty("x", x);
        json.addProperty("y", y);
        json.addProperty("z", z);
        json.addProperty("count", count);
        return json;
    }

    @Override
    public String getSummary() {
        return String.format("生成可拾取物品 %s x%d 于 (%.1f, %.1f, %.1f)", itemId, count, x, y, z);
    }

    /**
     * Parse from JSON.
     */
    public static SpawnPlacedItemAction fromJson(JsonObject json) {
        String itemFull = json.has("item") ? json.get("item").getAsString() : "minecraft:diamond";
        
        // Split item id and nbt
        String itemId = itemFull;
        String nbt = "";
        int braceIdx = itemFull.indexOf('{');
        if (braceIdx > 0) {
            itemId = itemFull.substring(0, braceIdx);
            nbt = itemFull.substring(braceIdx);
        }
        
        double x = json.has("x") ? json.get("x").getAsDouble() : 0;
        double y = json.has("y") ? json.get("y").getAsDouble() : 64;
        double z = json.has("z") ? json.get("z").getAsDouble() : 0;
        int count = json.has("count") ? json.get("count").getAsInt() : 1;
        
        return new SpawnPlacedItemAction(itemId, nbt, x, y, z, count);
    }
}
