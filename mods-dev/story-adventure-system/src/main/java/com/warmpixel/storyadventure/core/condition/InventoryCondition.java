package com.warmpixel.storyadventure.core.condition;

import com.google.gson.JsonObject;
import com.warmpixel.storyadventure.instance.Instance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Condition that checks if a player has a specific item in their inventory.
 */
public class InventoryCondition implements EdgeCondition {
    public static final String TYPE = "INVENTORY";
    
    private final String itemId;
    private final int minCount;
    private final boolean consumeOnTransition;
    
    public InventoryCondition(String itemId, int minCount, boolean consumeOnTransition) {
        this.itemId = itemId;
        this.minCount = minCount;
        this.consumeOnTransition = consumeOnTransition;
    }
    
    @Override
    public boolean evaluate(Instance instance, ServerPlayer player) {
        if (player == null) return false;
        
        ResourceLocation itemLocation = ResourceLocation.tryParse(itemId);
        if (itemLocation == null) return false;
        
        Item item = BuiltInRegistries.ITEM.get(itemLocation);
        
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() == item) {
                count += stack.getCount();
            }
        }
        
        return count >= minCount;
    }
    
    @Override
    public String getType() {
        return TYPE;
    }
    
    @Override
    public JsonObject serialize() {
        JsonObject json = new JsonObject();
        json.addProperty("type", TYPE);
        json.addProperty("item", itemId);
        json.addProperty("count", minCount);
        json.addProperty("consume", consumeOnTransition);
        return json;
    }
    
    @Override
    public String getDescription() {
        return "Has " + minCount + "x " + itemId;
    }
    
    public boolean shouldConsume() {
        return consumeOnTransition;
    }
    
    public static InventoryCondition fromJson(JsonObject json) {
        String itemId = json.get("item").getAsString();
        int count = json.has("count") ? json.get("count").getAsInt() : 1;
        boolean consume = json.has("consume") && json.get("consume").getAsBoolean();
        return new InventoryCondition(itemId, count, consume);
    }
}
