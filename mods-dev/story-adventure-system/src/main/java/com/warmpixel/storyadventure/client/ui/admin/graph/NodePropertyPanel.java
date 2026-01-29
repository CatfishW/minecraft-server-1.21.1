package com.warmpixel.storyadventure.client.ui.admin.graph;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.*;

/**
 * Side panel for editing selected node properties.
 * Shows type-specific fields and trigger configuration.
 */
public class NodePropertyPanel {
    
    private static final int COLOR_BG = 0xE0101010;
    private static final int COLOR_BORDER = 0xFF330011;
    private static final int COLOR_HEADER = 0xFFE50914;
    private static final int COLOR_TEXT = 0xFFCCCCCC;
    private static final int COLOR_TEXT_DIM = 0xFF888888;
    private static final int COLOR_SECTION = 0xFF666666;
    
    private final StoryGraphScreen parent;
    private int x, y, width, height;
    
    private GraphNode currentNode;
    private JsonObject currentNodeData;
    
    // Edit boxes for properties
    private final Map<String, EditBox> editBoxes = new LinkedHashMap<>();
    private int scrollOffset = 0;
    private int contentHeight = 0;
    
    // Trigger editors
    private List<TriggerEntry> onEnterTriggers = new ArrayList<>();
    private List<TriggerEntry> onExitTriggers = new ArrayList<>();
    
    public NodePropertyPanel(StoryGraphScreen parent, int x, int y, int width, int height) {
        this.parent = parent;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }
    
    public void init(Screen screen) {
        // Called when screen initializes
    }
    
    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        rebuildEditBoxes();
    }
    
    public void setNode(GraphNode node, JsonObject nodeData) {
        this.currentNode = node;
        this.currentNodeData = nodeData;
        this.scrollOffset = 0;
        rebuildEditBoxes();
        parseTriggers();
    }
    
    private void rebuildEditBoxes() {
        editBoxes.clear();
        
        if (currentNode == null || currentNodeData == null) return;
        
        Font font = Minecraft.getInstance().font;
        int fieldY = y + 80;
        int fieldWidth = width - 30;
        int fieldHeight = 18;
        int gap = 25;
        
        // Add data fields based on type
        JsonObject data = currentNodeData.has("data") ? currentNodeData.getAsJsonObject("data") : new JsonObject();
        
        String[] fields = getFieldsForType(currentNode.getNodeType());
        for (String field : fields) {
            String value = data.has(field) ? getJsonValueAsString(data.get(field)) : "";
            
            EditBox box = new EditBox(font, x + 15, fieldY, fieldWidth, fieldHeight, Component.literal(field));
            box.setMaxLength(500);
            box.setValue(value);
            box.setTextColor(0xFFCCCCCC);
            box.setResponder(newValue -> {
                parent.onNodePropertyChanged(currentNode.getNodeId(), field, newValue);
            });
            
            editBoxes.put(field, box);
            fieldY += gap;
        }
        
        contentHeight = fieldY + 200; // Extra space for triggers
    }
    
    private String[] getFieldsForType(String type) {
        return switch (type.toUpperCase()) {
            case "CUTSCENE" -> new String[]{"duration_ticks", "title", "subtitle", "message", "fade_in"};
            case "DIALOGUE" -> new String[]{"npc_template", "npc_name", "dialog_set", "vote_required", "vote_id"};
            case "TASK" -> new String[]{"task_type", "title", "description", "time_limit_seconds", "stealth_required"};
            case "PUZZLE" -> new String[]{"puzzle_type", "title", "description", "solution", "max_attempts"};
            case "COMBAT" -> new String[]{"combat_type", "title", "description", "arena_radius", "escape_available"};
            case "CHECKPOINT" -> new String[]{"rewind_anchor", "save_inventory", "message"};
            default -> new String[]{};
        };
    }
    
    private String getJsonValueAsString(JsonElement elem) {
        if (elem.isJsonPrimitive()) {
            return elem.getAsString();
        } else if (elem.isJsonArray()) {
            return "[" + elem.getAsJsonArray().size() + " items]";
        } else {
            return elem.toString();
        }
    }
    
    private void parseTriggers() {
        onEnterTriggers.clear();
        onExitTriggers.clear();
        
        if (currentNodeData == null) return;
        
        if (currentNodeData.has("on_enter")) {
            for (JsonElement elem : currentNodeData.getAsJsonArray("on_enter")) {
                onEnterTriggers.add(parseTriggerEntry(elem.getAsJsonObject()));
            }
        }
        
        if (currentNodeData.has("on_exit")) {
            for (JsonElement elem : currentNodeData.getAsJsonArray("on_exit")) {
                onExitTriggers.add(parseTriggerEntry(elem.getAsJsonObject()));
            }
        }
    }
    
    private TriggerEntry parseTriggerEntry(JsonObject obj) {
        String type = obj.has("type") ? obj.get("type").getAsString() : "UNKNOWN";
        String summary = type;
        
        // Build summary based on type
        switch (type.toUpperCase()) {
            case "COMMAND" -> summary = "命令: " + (obj.has("command") ? obj.get("command").getAsString() : "?");
            case "MESSAGE" -> summary = "消息: " + (obj.has("text") ? truncate(obj.get("text").getAsString(), 20) : "?");
            case "TELEPORT" -> summary = "传送: " + (obj.has("location") ? obj.get("location").getAsString() : "?");
            case "SET_FLAG" -> summary = "设置标记: " + (obj.has("flag") ? obj.get("flag").getAsString() : "?");
            case "SPAWN_NPC" -> summary = "生成NPC: " + (obj.has("template") ? obj.get("template").getAsString() : "?");
            case "GIVE_ITEM" -> summary = "给予物品: " + (obj.has("item") ? obj.get("item").getAsString() : "?");
            case "PLAY_SOUND" -> summary = "播放音效: " + (obj.has("sound") ? obj.get("sound").getAsString() : "?");
            case "TITLE" -> summary = "显示标题: " + (obj.has("title") ? truncate(obj.get("title").getAsString(), 15) : "?");
        }
        
        return new TriggerEntry(type, summary, obj);
    }
    
    private String truncate(String text, int max) {
        return text.length() > max ? text.substring(0, max - 2) + ".." : text;
    }
    
    public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY, GraphNode selectedNode) {
        // Background
        graphics.fill(x, y, x + width, y + height, COLOR_BG);
        
        // Border
        graphics.fill(x, y, x + width, y + 1, COLOR_BORDER);
        graphics.fill(x, y + height - 1, x + width, y + height, COLOR_BORDER);
        graphics.fill(x, y, x + 1, y + height, COLOR_BORDER);
        graphics.fill(x + width - 1, y, x + width, y + height, COLOR_BORDER);
        
        // Header
        graphics.fill(x + 1, y + 1, x + width - 1, y + 25, 0xFF1A0A0A);
        graphics.drawCenteredString(font, "节点属性", x + width / 2, y + 8, COLOR_HEADER);
        
        if (currentNode == null) {
            graphics.drawCenteredString(font, "选择一个节点", x + width / 2, y + height / 2, COLOR_TEXT_DIM);
            return;
        }
        
        // Node info
        int infoY = y + 32;
        graphics.drawString(font, "ID:", x + 10, infoY, COLOR_TEXT_DIM);
        graphics.drawString(font, currentNode.getNodeId(), x + 30, infoY, COLOR_TEXT);
        infoY += 14;
        
        graphics.drawString(font, "类型:", x + 10, infoY, COLOR_TEXT_DIM);
        graphics.drawString(font, getTypeLabel(currentNode.getNodeType()), x + 40, infoY, getTypeColor(currentNode.getNodeType()));
        infoY += 20;
        
        // Fields section
        graphics.drawString(font, "━━ 属性 ━━", x + 10, infoY, COLOR_SECTION);
        infoY += 15;
        
        // Render edit boxes with labels
        for (Map.Entry<String, EditBox> entry : editBoxes.entrySet()) {
            String label = getFieldLabel(entry.getKey());
            EditBox box = entry.getValue();
            
            graphics.drawString(font, label, x + 10, box.getY() - 10, COLOR_TEXT_DIM);
            box.render(graphics, mouseX, mouseY, 0);
        }
        
        // Triggers section
        int triggerY = y + 80 + editBoxes.size() * 25 + 20;
        renderTriggersSection(graphics, font, triggerY, mouseX, mouseY);
    }
    
    private void renderTriggersSection(GuiGraphics graphics, Font font, int startY, int mouseX, int mouseY) {
        int sectionY = startY;
        
        // On Enter triggers
        graphics.drawString(font, "━━ 进入触发 (on_enter) ━━", x + 10, sectionY, COLOR_SECTION);
        sectionY += 14;
        
        if (onEnterTriggers.isEmpty()) {
            graphics.drawString(font, "无触发器", x + 15, sectionY, COLOR_TEXT_DIM);
            sectionY += 12;
        } else {
            for (TriggerEntry trigger : onEnterTriggers) {
                graphics.fill(x + 10, sectionY, x + width - 15, sectionY + 18, 0xFF1A1A1A);
                graphics.fill(x + 10, sectionY, x + 13, sectionY + 18, 0xFF44FF44); // Green bar
                graphics.drawString(font, trigger.summary, x + 18, sectionY + 5, COLOR_TEXT);
                sectionY += 20;
            }
        }
        
        // Add trigger button
        boolean addHovered = mouseX >= x + 10 && mouseX < x + 80 && mouseY >= sectionY && mouseY < sectionY + 16;
        graphics.fill(x + 10, sectionY, x + 80, sectionY + 16, addHovered ? 0xFF333333 : 0xFF222222);
        graphics.drawString(font, "+ 添加", x + 15, sectionY + 4, 0xFF44FF44);
        sectionY += 25;
        
        // On Exit triggers
        graphics.drawString(font, "━━ 退出触发 (on_exit) ━━", x + 10, sectionY, COLOR_SECTION);
        sectionY += 14;
        
        if (onExitTriggers.isEmpty()) {
            graphics.drawString(font, "无触发器", x + 15, sectionY, COLOR_TEXT_DIM);
            sectionY += 12;
        } else {
            for (TriggerEntry trigger : onExitTriggers) {
                graphics.fill(x + 10, sectionY, x + width - 15, sectionY + 18, 0xFF1A1A1A);
                graphics.fill(x + 10, sectionY, x + 13, sectionY + 18, 0xFFFF4444); // Red bar
                graphics.drawString(font, trigger.summary, x + 18, sectionY + 5, COLOR_TEXT);
                sectionY += 20;
            }
        }
        
        // Add trigger button
        addHovered = mouseX >= x + 10 && mouseX < x + 80 && mouseY >= sectionY && mouseY < sectionY + 16;
        graphics.fill(x + 10, sectionY, x + 80, sectionY + 16, addHovered ? 0xFF333333 : 0xFF222222);
        graphics.drawString(font, "+ 添加", x + 15, sectionY + 4, 0xFFFF4444);
    }
    
    private String getFieldLabel(String field) {
        return switch (field) {
            case "duration_ticks" -> "持续时间(tick)";
            case "title" -> "标题";
            case "subtitle" -> "副标题";
            case "message" -> "消息";
            case "fade_in" -> "淡入效果";
            case "npc_template" -> "NPC模板";
            case "npc_name" -> "NPC名称";
            case "dialog_set" -> "对话集";
            case "vote_required" -> "需要投票";
            case "vote_id" -> "投票ID";
            case "task_type" -> "任务类型";
            case "description" -> "描述";
            case "time_limit_seconds" -> "时间限制(秒)";
            case "stealth_required" -> "需要潜行";
            case "puzzle_type" -> "谜题类型";
            case "solution" -> "答案";
            case "max_attempts" -> "最大尝试";
            case "combat_type" -> "战斗类型";
            case "arena_radius" -> "竞技场半径";
            case "escape_available" -> "可逃跑";
            case "rewind_anchor" -> "重试锚点";
            case "save_inventory" -> "保存背包";
            default -> field;
        };
    }
    
    private String getTypeLabel(String type) {
        return switch (type.toUpperCase()) {
            case "DIALOGUE" -> "对话";
            case "TASK" -> "任务";
            case "PUZZLE" -> "谜题";
            case "COMBAT" -> "战斗";
            case "CUTSCENE" -> "过场";
            case "CHECKPOINT" -> "存档点";
            default -> type;
        };
    }
    
    private int getTypeColor(String type) {
        return switch (type.toUpperCase()) {
            case "DIALOGUE" -> 0xFF4488FF;
            case "TASK" -> 0xFF44FF44;
            case "PUZZLE" -> 0xFFFF8844;
            case "COMBAT" -> 0xFFFF4444;
            case "CUTSCENE" -> 0xFFCC44FF;
            case "CHECKPOINT" -> 0xFFFFCC44;
            default -> COLOR_TEXT_DIM;
        };
    }
    
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseX < x || mouseX > x + width || mouseY < y || mouseY > y + height) {
            return false;
        }
        
        // Check edit boxes
        for (EditBox box : editBoxes.values()) {
            if (box.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        
        return true;
    }
    
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (EditBox box : editBoxes.values()) {
            if (box.isFocused() && box.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return false;
    }
    
    public boolean charTyped(char chr, int modifiers) {
        for (EditBox box : editBoxes.values()) {
            if (box.isFocused() && box.charTyped(chr, modifiers)) {
                return true;
            }
        }
        return false;
    }
    
    public record TriggerEntry(String type, String summary, JsonObject data) {}
}
