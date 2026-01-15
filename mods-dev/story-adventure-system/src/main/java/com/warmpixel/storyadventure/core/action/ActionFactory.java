package com.warmpixel.storyadventure.core.action;

import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Factory for creating NodeAction instances from JSON.
 */
public class ActionFactory {
    
    private static final Map<String, Function<JsonObject, NodeAction>> PARSERS = new HashMap<>();
    
    static {
        // Register all action type parsers
        register("COMMAND", CommandAction::fromJson);
        register("MESSAGE", MessageAction::fromJson);
        register("TITLE", TitleAction::fromJson);
        register("PLAY_SOUND", PlaySoundAction::fromJson);
        register("PLAY_VOICEOVER", PlayVoiceoverAction::fromJson);
        register("SET_FLAG", SetFlagAction::fromJson);
        register("TELEPORT", TeleportAction::fromJson);
        register("GIVE_ITEM", GiveItemAction::fromJson);
        register("SPAWN_NPC", SpawnNPCAction::fromJson);
    }
    
    public static void register(String type, Function<JsonObject, NodeAction> parser) {
        PARSERS.put(type.toUpperCase(), parser);
    }
    
    /**
     * Create a NodeAction from JSON data.
     * @param json The action JSON object
     * @return The NodeAction instance, or null if type unknown
     */
    public static NodeAction fromJson(JsonObject json) {
        if (!json.has("type")) return null;
        
        String type = json.get("type").getAsString().toUpperCase();
        Function<JsonObject, NodeAction> parser = PARSERS.get(type);
        
        if (parser != null) {
            return parser.apply(json);
        }
        
        return null;
    }
    
    /**
     * Get all available action types.
     */
    public static String[] getActionTypes() {
        return PARSERS.keySet().toArray(new String[0]);
    }
    
    /**
     * Get a display name for an action type.
     */
    public static String getTypeDisplayName(String type) {
        return switch (type.toUpperCase()) {
            case "COMMAND" -> "执行命令";
            case "MESSAGE" -> "发送消息";
            case "TITLE" -> "显示标题";
            case "PLAY_SOUND" -> "播放音效";
            case "PLAY_VOICEOVER" -> "播放语音";
            case "SET_FLAG" -> "设置标记";
            case "TELEPORT" -> "传送玩家";
            case "SPAWN_NPC" -> "生成NPC";
            case "GIVE_ITEM" -> "给予物品";
            case "EFFECT" -> "添加效果";
            case "PARTICLE" -> "播放粒子";
            default -> type;
        };
    }
    
    /**
     * Get a template JSON for a new action of the given type.
     */
    public static JsonObject getTemplate(String type) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", type.toUpperCase());
        
        switch (type.toUpperCase()) {
            case "COMMAND" -> obj.addProperty("command", "say Hello!");
            case "MESSAGE" -> obj.addProperty("text", "消息内容");
            case "TITLE" -> {
                obj.addProperty("title", "标题");
                obj.addProperty("subtitle", "副标题");
            }
            case "PLAY_SOUND" -> obj.addProperty("sound", "minecraft:entity.experience_orb.pickup");
            case "PLAY_VOICEOVER" -> {
                obj.addProperty("sound", "story_id/character/line_001");
                obj.addProperty("character", "narrator");
            }
            case "SET_FLAG" -> {
                obj.addProperty("flag", "flag_name");
                obj.addProperty("value", true);
            }
            case "TELEPORT" -> {
                obj.addProperty("dimension", "minecraft:overworld");
                obj.addProperty("x", 0);
                obj.addProperty("y", 64);
                obj.addProperty("z", 0);
            }
            case "GIVE_ITEM" -> {
                obj.addProperty("item", "minecraft:diamond");
                obj.addProperty("count", 1);
            }
            case "SPAWN_NPC" -> {
                obj.addProperty("npc_template", "guide_npc");
                obj.addProperty("x", 0);
                obj.addProperty("y", 64);
                obj.addProperty("z", 0);
            }
        }
        
        return obj;
    }
}
