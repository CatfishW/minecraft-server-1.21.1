package com.warmpixel.storyadventure.core.action;

import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Action that gives items to players.
 */
public class GiveItemAction implements NodeAction {
    
    private final String itemId;
    private final int count;
    private final boolean silent;
    
    public GiveItemAction(String itemId, int count, boolean silent) {
        this.itemId = itemId;
        this.count = count;
        this.silent = silent;
    }

    @Override
    public String getType() {
        return "GIVE_ITEM";
    }

    @Override
    public String getSummary() {
        return "Give Item: " + itemId + " x" + count;
    }
    
    @Override
    public void execute(List<ServerPlayer> players) {
        ResourceLocation rl = ResourceLocation.parse(itemId);
        Item item = BuiltInRegistries.ITEM.get(rl);
        
        if (item == BuiltInRegistries.ITEM.get(BuiltInRegistries.ITEM.getDefaultKey())) {
            // Item not found or is air (default)
            return;
        }
        
        for (ServerPlayer player : players) {
            ItemStack stack = new ItemStack(item, count);
            player.getInventory().add(stack);
            
            if (!silent) {
                player.sendSystemMessage(Component.translatable("text.storyadventure.action.give_item", count, stack.getHoverName()));
            }
        }
    }
    
    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "GIVE_ITEM");
        json.addProperty("item", itemId);
        json.addProperty("count", count);
        json.addProperty("silent", silent);
        return json;
    }
    
    public static GiveItemAction fromJson(JsonObject json) {
        String item = json.has("item") ? json.get("item").getAsString() : "minecraft:stone";
        int count = json.has("count") ? json.get("count").getAsInt() : 1;
        boolean silent = json.has("silent") && json.get("silent").getAsBoolean();
        return new GiveItemAction(item, count, silent);
    }
}
