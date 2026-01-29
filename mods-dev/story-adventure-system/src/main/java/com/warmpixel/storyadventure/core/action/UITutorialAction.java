package com.warmpixel.storyadventure.core.action;

import com.google.gson.JsonObject;
import com.warmpixel.storyadventure.StoryAdventureMod;
import com.warmpixel.storyadventure.network.packet.UITutorialPacket;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Action to display interactive UI tutorial highlights.
 * Supports highlighting UI elements like hotbar slots, inventory slots,
 * and custom screen regions with animated indicators and helpful messages.
 * 
 * JSON Usage Examples:
 * 
 * Highlight a hotbar slot with key hint:
 * {
 *   "type": "UI_TUTORIAL",
 *   "action": "show",
 *   "id": "hotbar_1_tutorial",
 *   "element_type": "hotbar",
 *   "element_index": 0,
 *   "message": "这是你的武器槽位",
 *   "key_hint": "1",
 *   "color": "#00FFFF",
 *   "show_arrow": true,
 *   "show_pulse": true,
 *   "duration_ticks": 100
 * }
 * 
 * Show a key press hint:
 * {
 *   "type": "UI_TUTORIAL",
 *   "action": "show",
 *   "id": "reload_tutorial",
 *   "element_type": "key_hint",
 *   "message": "按 R 换弹",
 *   "key_hint": "R",
 *   "screen_x": 50,
 *   "screen_y": 70,
 *   "color": "#FFD700",
 *   "show_pulse": true
 * }
 * 
 * Highlight a screen region:
 * {
 *   "type": "UI_TUTORIAL",
 *   "action": "show",
 *   "id": "health_bar_tutorial",
 *   "element_type": "screen_region",
 *   "screen_x": 5,
 *   "screen_y": 95,
 *   "width": 20,
 *   "height": 5,
 *   "message": "这是你的生命值",
 *   "color": "#FF4444",
 *   "show_arrow": true
 * }
 * 
 * Hide specific tutorial:
 * {
 *   "type": "UI_TUTORIAL",
 *   "action": "hide",
 *   "id": "hotbar_1_tutorial"
 * }
 * 
 * Clear all tutorials:
 * {
 *   "type": "UI_TUTORIAL",
 *   "action": "clear"
 * }
 */
public class UITutorialAction implements NodeAction {
    
    public enum ActionType {
        SHOW, HIDE, CLEAR
    }
    
    public enum ElementType {
        HOTBAR,           // Hotbar slot (element_index = 0-8)
        INVENTORY,        // Inventory slot
        SCREEN_REGION,    // Custom screen region
        KEY_HINT,         // Floating key hint
        CROSSHAIR,        // Crosshair area
        HEALTH_BAR,       // Health bar
        ARMOR_BAR,        // Armor bar
        EXPERIENCE_BAR,   // Experience bar
        FTB_QUESTS_INVENTORY_BUTTON, // Button in inventory to open quest book
        FTB_QUESTS_CLAIM_ALL,        // Claim all rewards button in quest screen
        FTB_QUESTS_CHAPTERS          // Chapters panel in quest screen
    }
    
    private final ActionType actionType;
    private final String id;
    private final ElementType elementType;
    private final int elementIndex;
    private final int screenX;
    private final int screenY;
    private final int width;
    private final int height;
    private final String message;
    private final String keyHint;
    private final int color;
    private final boolean showArrow;
    private final boolean showPulse;
    private final boolean showClickHint;
    private final int durationTicks;
    private final int delayTicks;
    private final boolean requireClick;
    
    public UITutorialAction(ActionType actionType, String id, ElementType elementType,
                           int elementIndex, int screenX, int screenY, int width, int height,
                           String message, String keyHint, int color, boolean showArrow,
                           boolean showPulse, boolean showClickHint, int durationTicks, int delayTicks) {
        this(actionType, id, elementType, elementIndex, screenX, screenY, width, height,
             message, keyHint, color, showArrow, showPulse, showClickHint, durationTicks, delayTicks, false);
    }

    public UITutorialAction(ActionType actionType, String id, ElementType elementType,
                           int elementIndex, int screenX, int screenY, int width, int height,
                           String message, String keyHint, int color, boolean showArrow,
                           boolean showPulse, boolean showClickHint, int durationTicks, int delayTicks,
                           boolean requireClick) {
        this.actionType = actionType;
        this.id = id;
        this.elementType = elementType;
        this.elementIndex = elementIndex;
        this.screenX = screenX;
        this.screenY = screenY;
        this.width = width;
        this.height = height;
        this.message = message;
        this.keyHint = keyHint;
        this.color = color;
        this.showArrow = showArrow;
        this.showPulse = showPulse;
        this.showClickHint = showClickHint;
        this.durationTicks = durationTicks;
        this.delayTicks = delayTicks;
        this.requireClick = requireClick;
    }
    
    
    @Override
    public String getType() {
        return "UI_TUTORIAL";
    }
    
    @Override
    public void execute(List<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            UITutorialPacket packet = new UITutorialPacket(
                actionType.name().toLowerCase(),
                id != null ? id : "default",
                elementType != null ? elementType.name().toLowerCase() : "key_hint",
                elementIndex,
                screenX,
                screenY,
                width,
                height,
                message != null ? message : "",
                keyHint != null ? keyHint : "",
                color,
                showArrow,
                showPulse,
                showClickHint,
                durationTicks,
                requireClick
            );
            ServerPlayNetworking.send(player, packet);
            StoryAdventureMod.LOGGER.debug("[UITutorialAction] Sent {} tutorial '{}' to {} (element: {}, message: '{}')", 
                actionType, id, player.getName().getString(), elementType, message);
        }
    }
    
    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "UI_TUTORIAL");
        json.addProperty("action", actionType.name().toLowerCase());
        
        if (actionType == ActionType.SHOW) {
            json.addProperty("id", id);
            json.addProperty("element_type", elementType.name().toLowerCase());
            if (elementType == ElementType.HOTBAR || elementType == ElementType.INVENTORY) {
                json.addProperty("element_index", elementIndex);
            }
            if (elementType == ElementType.SCREEN_REGION || elementType == ElementType.KEY_HINT) {
                json.addProperty("screen_x", screenX);
                json.addProperty("screen_y", screenY);
                if (width > 0) json.addProperty("width", width);
                if (height > 0) json.addProperty("height", height);
            }
            if (message != null && !message.isEmpty()) {
                json.addProperty("message", message);
            }
            if (keyHint != null && !keyHint.isEmpty()) {
                json.addProperty("key_hint", keyHint);
            }
            json.addProperty("color", String.format("#%06X", color & 0xFFFFFF));
            json.addProperty("show_arrow", showArrow);
            json.addProperty("show_pulse", showPulse);
            json.addProperty("show_click_hint", showClickHint);
            if (durationTicks > 0) {
                json.addProperty("duration_ticks", durationTicks);
            }
            if (delayTicks > 0) {
                json.addProperty("delay_ticks", delayTicks);
            }
            if (requireClick) {
                json.addProperty("require_click", true);
            }
        } else if (actionType == ActionType.HIDE) {
            json.addProperty("id", id);
        }
        
        return json;
    }
    
    @Override
    public String getSummary() {
        return switch (actionType) {
            case SHOW -> String.format("显示UI教程 '%s' (%s): %s", id, elementType, message);
            case HIDE -> String.format("隐藏UI教程 '%s'", id);
            case CLEAR -> "清除所有UI教程";
        };
    }
    
    /**
     * Parse a UITutorialAction from JSON.
     */
    public static UITutorialAction fromJson(JsonObject json) {
        String actionStr = json.has("action") ? json.get("action").getAsString().toUpperCase() : "SHOW";
        ActionType actionType;
        try {
            actionType = ActionType.valueOf(actionStr);
        } catch (IllegalArgumentException e) {
            actionType = ActionType.SHOW;
        }
        
        String id = json.has("id") ? json.get("id").getAsString() : "default";
        
        String elementTypeStr = json.has("element_type") ? json.get("element_type").getAsString().toUpperCase() : "KEY_HINT";
        ElementType elementType;
        try {
            elementType = ElementType.valueOf(elementTypeStr);
        } catch (IllegalArgumentException e) {
            elementType = ElementType.KEY_HINT;
        }
        
        int elementIndex = json.has("element_index") ? json.get("element_index").getAsInt() : 0;
        int screenX = json.has("screen_x") ? json.get("screen_x").getAsInt() : 50;
        int screenY = json.has("screen_y") ? json.get("screen_y").getAsInt() : 50;
        int width = json.has("width") ? json.get("width").getAsInt() : 0;
        int height = json.has("height") ? json.get("height").getAsInt() : 0;
        String message = json.has("message") ? json.get("message").getAsString() : "";
        String keyHint = json.has("key_hint") ? json.get("key_hint").getAsString() : "";
        boolean showArrow = json.has("show_arrow") && json.get("show_arrow").getAsBoolean();
        boolean showPulse = !json.has("show_pulse") || json.get("show_pulse").getAsBoolean();
        boolean showClickHint = json.has("show_click_hint") && json.get("show_click_hint").getAsBoolean();
        int durationTicks = json.has("duration_ticks") ? json.get("duration_ticks").getAsInt() : 0;
        int delayTicks = json.has("delay_ticks") ? json.get("delay_ticks").getAsInt() : 0;
        boolean requireClick = json.has("require_click") && json.get("require_click").getAsBoolean();
        
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
        
        return new UITutorialAction(actionType, id, elementType, elementIndex, screenX, screenY,
            width, height, message, keyHint, color, showArrow, showPulse, showClickHint, 
            durationTicks, delayTicks, requireClick);
    }
}
