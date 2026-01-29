package com.warmpixel.storyadventure.core.action;

import com.google.gson.JsonObject;
import com.warmpixel.storyadventure.StoryAdventureMod;
import com.warmpixel.storyadventure.network.packet.ItemBlockIndicatorAddPacket;
import com.warmpixel.storyadventure.network.packet.ItemBlockIndicatorRemovePacket;
import com.warmpixel.storyadventure.network.packet.ItemBlockIndicatorClearPacket;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Action to show/hide visual indicators on items or blocks.
 * 
 * JSON Usage Examples:
 * 
 * Show indicator at coordinates:
 * {
 *   "type": "INDICATOR",
 *   "action": "show",
 *   "id": "objective_chest",
 *   "x": 100.5,
 *   "y": 65.0,
 *   "z": -200.5,
 *   "label": "打开宝箱",
 *   "color": "#00FFFF",
 *   "radius": 1.5,
 *   "show_arrow": true,
 *   "show_circle": true
 * }
 * 
 * Hide specific indicator:
 * {
 *   "type": "INDICATOR",
 *   "action": "hide",
 *   "id": "objective_chest"
 * }
 * 
 * Clear all indicators:
 * {
 *   "type": "INDICATOR",
 *   "action": "clear"
 * }
 */
public class IndicatorAction implements NodeAction {
    
    public enum ActionType {
        SHOW, HIDE, CLEAR
    }
    
    private final ActionType actionType;
    private final String id;
    private final double x, y, z;
    private final int color;
    private final String label;
    private final float radius;
    private final boolean showArrow;
    private final boolean showCircle;
    
    public IndicatorAction(ActionType actionType, String id, double x, double y, double z,
                          int color, String label, float radius, boolean showArrow, boolean showCircle) {
        this.actionType = actionType;
        this.id = id;
        this.x = x;
        this.y = y;
        this.z = z;
        this.color = color;
        this.label = label;
        this.radius = radius;
        this.showArrow = showArrow;
        this.showCircle = showCircle;
    }
    
    @Override
    public String getType() {
        return "INDICATOR";
    }
    
    @Override
    public void execute(List<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            switch (actionType) {
                case SHOW -> {
                    ItemBlockIndicatorAddPacket packet = new ItemBlockIndicatorAddPacket(
                        id, x, y, z, color, label != null ? label : "", radius, showArrow, showCircle
                    );
                    ServerPlayNetworking.send(player, packet);
                    StoryAdventureMod.LOGGER.debug("[IndicatorAction] Sent SHOW indicator '{}' to {} at ({}, {}, {})", 
                        id, player.getName().getString(), x, y, z);
                }
                case HIDE -> {
                    ItemBlockIndicatorRemovePacket packet = new ItemBlockIndicatorRemovePacket(id);
                    ServerPlayNetworking.send(player, packet);
                    StoryAdventureMod.LOGGER.debug("[IndicatorAction] Sent HIDE indicator '{}' to {}", 
                        id, player.getName().getString());
                }
                case CLEAR -> {
                    ItemBlockIndicatorClearPacket packet = new ItemBlockIndicatorClearPacket();
                    ServerPlayNetworking.send(player, packet);
                    StoryAdventureMod.LOGGER.debug("[IndicatorAction] Sent CLEAR indicators to {}", 
                        player.getName().getString());
                }
            }
        }
    }
    
    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "INDICATOR");
        json.addProperty("action", actionType.name().toLowerCase());
        
        if (actionType == ActionType.SHOW) {
            json.addProperty("id", id);
            json.addProperty("x", x);
            json.addProperty("y", y);
            json.addProperty("z", z);
            if (label != null && !label.isEmpty()) {
                json.addProperty("label", label);
            }
            json.addProperty("color", String.format("#%06X", color & 0xFFFFFF));
            json.addProperty("radius", radius);
            json.addProperty("show_arrow", showArrow);
            json.addProperty("show_circle", showCircle);
        } else if (actionType == ActionType.HIDE) {
            json.addProperty("id", id);
        }
        
        return json;
    }
    
    @Override
    public String getSummary() {
        return switch (actionType) {
            case SHOW -> String.format("显示指示器 '%s' 于 (%.1f, %.1f, %.1f)", id, x, y, z);
            case HIDE -> String.format("隐藏指示器 '%s'", id);
            case CLEAR -> "清除所有指示器";
        };
    }
    
    /**
     * Parse an IndicatorAction from JSON.
     */
    public static IndicatorAction fromJson(JsonObject json) {
        String actionStr = json.has("action") ? json.get("action").getAsString().toUpperCase() : "SHOW";
        ActionType actionType;
        try {
            actionType = ActionType.valueOf(actionStr);
        } catch (IllegalArgumentException e) {
            actionType = ActionType.SHOW;
        }
        
        String id = json.has("id") ? json.get("id").getAsString() : "default";
        double x = json.has("x") ? json.get("x").getAsDouble() : 0;
        double y = json.has("y") ? json.get("y").getAsDouble() : 0;
        double z = json.has("z") ? json.get("z").getAsDouble() : 0;
        String label = json.has("label") ? json.get("label").getAsString() : "";
        float radius = json.has("radius") ? json.get("radius").getAsFloat() : 1.0f;
        boolean showArrow = !json.has("show_arrow") || json.get("show_arrow").getAsBoolean();
        boolean showCircle = !json.has("show_circle") || json.get("show_circle").getAsBoolean();
        
        // Parse color - supports "#RRGGBB" or integer
        int color = 0x00FFFF; // Default cyan
        if (json.has("color")) {
            var colorEl = json.get("color");
            if (colorEl.isJsonPrimitive()) {
                var prim = colorEl.getAsJsonPrimitive();
                if (prim.isString()) {
                    String colorStr = prim.getAsString();
                    if (colorStr.startsWith("#")) {
                        try {
                            color = Integer.parseInt(colorStr.substring(1), 16);
                        } catch (NumberFormatException ignored) {}
                    }
                } else if (prim.isNumber()) {
                    color = prim.getAsInt();
                }
            }
        }
        
        return new IndicatorAction(actionType, id, x, y, z, color, label, radius, showArrow, showCircle);
    }
}
