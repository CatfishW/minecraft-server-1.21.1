package com.warmpixel.storyadventure.client.ui.admin.graph;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.warmpixel.storyadventure.client.ui.StrangerScreen;
import com.warmpixel.storyadventure.client.ui.admin.AdminStoryManagerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Main visual story graph editor screen.
 * Provides visualization, editing, and JSON persistence for story nodes.
 */
public class StoryGraphScreen extends StrangerScreen {
    
    private static final int PROPERTY_PANEL_WIDTH = 280;
    
    private final String storyId;
    private final String storyName;
    private String entryNodeId;
    
    private GraphCanvas canvas;
    private NodePropertyPanel propertyPanel;
    
    // Story data
    private JsonObject storyJson;
    private Path storyFilePath;
    private boolean hasUnsavedChanges = false;
    
    public StoryGraphScreen(String storyId, String storyName) {
        super(Component.literal("故事图编辑器 - " + storyName));
        this.storyId = storyId;
        this.storyName = storyName;
    }
    
    private boolean isLoading = true;

    @Override
    protected int getWindowWidth() {
        return width;
    }

    @Override
    protected int getWindowHeight() {
        return height;
    }

    @Override
    protected void init() {
        super.init();
        
        // Initialize canvas
        int canvasWidth = guiWidth - PROPERTY_PANEL_WIDTH - 20;
        if (canvas == null) {
            canvas = new GraphCanvas();
        }
        canvas.setBounds(guiLeft + 10, guiTop + 35, canvasWidth, guiHeight - 85);
        
        // Initialize property panel
        if (propertyPanel == null) {
            propertyPanel = new NodePropertyPanel(this, guiLeft + canvasWidth + 20, guiTop + 35, PROPERTY_PANEL_WIDTH - 10, guiHeight - 85);
        } else {
            propertyPanel.setBounds(guiLeft + canvasWidth + 20, guiTop + 35, PROPERTY_PANEL_WIDTH - 10, guiHeight - 85);
        }
        propertyPanel.init(this);
        
        // Toolbar buttons
        int toolbarX = guiLeft + 10;
        int toolbarY = guiTop + guiHeight - 40;
        int btnWidth = 80;
        int btnHeight = 24;
        int gap = 5;
        
        addStrangerButton(toolbarX, toolbarY, btnWidth, btnHeight,
            Component.literal("💾 保存"), this::saveStory);
        toolbarX += btnWidth + gap;
        
        addStrangerButton(toolbarX, toolbarY, btnWidth, btnHeight,
            Component.literal("↺ 重载"), this::reloadStory);
        toolbarX += btnWidth + gap;
        
        addStrangerButton(toolbarX, toolbarY, btnWidth, btnHeight,
            Component.literal("📐 自动布局"), this::autoLayout);
        toolbarX += btnWidth + gap;
        
        addStrangerButton(toolbarX, toolbarY, btnWidth, btnHeight,
            Component.literal("🔍 居中"), this::centerView);
        toolbarX += btnWidth + gap;
        
        addStrangerButton(toolbarX, toolbarY, btnWidth, btnHeight,
            Component.literal("+ 新节点"), this::addNewNode);
        
        // Back button
        addStrangerButton(guiLeft + guiWidth - 100, toolbarY, 90, btnHeight,
            Component.literal("← 返回"), this::goBack);
        
        // Load story via network
        if (storyJson == null) {
            loadStory();
        }
    }

    private void loadStory() {
        isLoading = true;
        showMessage("§e正在从服务器获取故事数据...");
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
            new com.warmpixel.storyadventure.network.RequestStoryGraphPayload(storyId)
        );
    }
    
    public void onSyncReceived(String json) {
        try {
            JsonElement parsed = com.google.gson.JsonParser.parseString(json);
            if (parsed == null || !parsed.isJsonObject()) {
                showMessage("§c从服务器收到的故事格式错误");
                return;
            }
            
            storyJson = parsed.getAsJsonObject();
            entryNodeId = storyJson.has("entry_node") ? storyJson.get("entry_node").getAsString() : null;
            
            buildGraphFromJson();
            
            // Check if we need auto layout (only if no nodes have positions)
            boolean needLayout = canvas.getNodes().values().stream().allMatch(n -> n.getX() == 0 && n.getY() == 0);
            if (needLayout) {
                canvas.autoLayout(entryNodeId);
            } else {
                canvas.centerView();
            }
            
            isLoading = false;
            showMessage("§a同步成功: " + storyId);
        } catch (Exception e) {
            showMessage("§c处理服务器数据失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void buildGraphFromJson() {
        if (storyJson == null) return;
        canvas.clear();
        
        if (!storyJson.has("nodes")) return;
        
        JsonObject nodes = storyJson.getAsJsonObject("nodes");
        
        // First pass: create nodes
        for (Map.Entry<String, JsonElement> entry : nodes.entrySet()) {
            String nodeId = entry.getKey();
            JsonObject nodeData = entry.getValue().getAsJsonObject();
            
            String type = nodeData.has("type") ? nodeData.get("type").getAsString() : "UNKNOWN";
            
            // Check for saved position
            float x = 0, y = 0;
            if (nodeData.has("_editor")) {
                JsonObject editor = nodeData.getAsJsonObject("_editor");
                x = editor.has("x") ? editor.get("x").getAsFloat() : 0;
                y = editor.has("y") ? editor.get("y").getAsFloat() : 0;
            }
            
            GraphNode node = new GraphNode(nodeId, type, x, y);
            
            // Check for triggers
            node.setHasOnEnter(nodeData.has("on_enter") && nodeData.getAsJsonArray("on_enter").size() > 0);
            node.setHasOnExit(nodeData.has("on_exit") && nodeData.getAsJsonArray("on_exit").size() > 0);
            
            canvas.addNode(node);
        }
        
        // Second pass: create edges
        for (Map.Entry<String, JsonElement> entry : nodes.entrySet()) {
            String nodeId = entry.getKey();
            JsonObject nodeData = entry.getValue().getAsJsonObject();
            
            if (nodeData.has("edges")) {
                JsonArray edges = nodeData.getAsJsonArray("edges");
                for (JsonElement edgeElem : edges) {
                    JsonObject edgeData = edgeElem.getAsJsonObject();
                    String targetId = edgeData.get("target").getAsString();
                    
                    GraphEdge edge = new GraphEdge(nodeId, targetId);
                    
                    // Parse conditions
                    if (edgeData.has("conditions")) {
                        List<String> conditions = new ArrayList<>();
                        for (JsonElement cond : edgeData.getAsJsonArray("conditions")) {
                            JsonObject condObj = cond.getAsJsonObject();
                            conditions.add(condObj.get("type").getAsString());
                        }
                        edge.setConditions(conditions);
                    }
                    
                    canvas.addEdge(edge);
                }
            }
        }
    }
    
    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Title bar with unsaved indicator
        String titleText = storyName + (hasUnsavedChanges ? " *" : "");
        graphics.drawCenteredString(font, titleText, guiLeft + guiWidth / 2, guiTop + 8, 
            hasUnsavedChanges ? 0xFFFFCC44 : COLOR_NEON_RED);
        
        // Story info
        graphics.drawString(font, "ID: " + storyId, guiLeft + 15, guiTop + 22, COLOR_TEXT_DIM);
        if (entryNodeId != null) {
            graphics.drawString(font, "入口: " + entryNodeId, guiLeft + 15, guiTop + 32, COLOR_TEXT_DIM);
        }
        
        if (isLoading) {
            graphics.drawCenteredString(font, "§e请求数据中...", guiLeft + guiWidth / 2, guiTop + guiHeight / 2, 0xFFFFFF00);
            return;
        }

        if (storyJson == null) {
            graphics.drawCenteredString(font, "§c故事数据同步失败", width / 2, height / 2, 0xFFFF5555);
            return;
        }
        
        // Canvas
        canvas.render(graphics, font, mouseX, mouseY);
        
        // Property panel
        propertyPanel.render(graphics, font, mouseX, mouseY, canvas.getSelectedNode());
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (storyJson == null) return super.mouseClicked(mouseX, mouseY, button);
        
        // Property panel first
        if (propertyPanel.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        
        // Canvas
        boolean handled = canvas.mouseClicked(mouseX, mouseY, button);
        
        // Update property panel with selection
        if (canvas.getSelectedNode() != null) {
            propertyPanel.setNode(canvas.getSelectedNode(), getNodeJsonData(canvas.getSelectedNode().getNodeId()));
        } else {
            propertyPanel.setNode(null, null);
        }
        
        return handled || super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (storyJson == null) return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        if (canvas.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            hasUnsavedChanges = true;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }
    
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (storyJson == null) return super.mouseReleased(mouseX, mouseY, button);
        canvas.mouseReleased(mouseX, mouseY, button);
        return super.mouseReleased(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double hAmount, double vAmount) {
        if (storyJson == null) return super.mouseScrolled(mouseX, mouseY, hAmount, vAmount);
        if (canvas.mouseScrolled(mouseX, mouseY, vAmount)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, hAmount, vAmount);
    }
    
    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (storyJson == null) return;
        canvas.mouseMoved(mouseX, mouseY);
    }
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (storyJson == null) return super.keyPressed(keyCode, scanCode, modifiers);
        if (propertyPanel.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        
        // Ctrl+S to save
        if (keyCode == 83 && (modifiers & 2) != 0) { // S with Ctrl
            saveStory();
            return true;
        }
        
        // Delete key
        if (keyCode == 261 && canvas.getSelectedNode() != null) {
            deleteSelectedNode();
            return true;
        }
        
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    
    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (storyJson == null) return super.charTyped(chr, modifiers);
        if (propertyPanel.charTyped(chr, modifiers)) {
            return true;
        }
        return super.charTyped(chr, modifiers);
    }
    
    private JsonObject getNodeJsonData(String nodeId) {
        if (storyJson != null && storyJson.has("nodes")) {
            JsonObject nodes = storyJson.getAsJsonObject("nodes");
            if (nodes.has(nodeId)) {
                return nodes.getAsJsonObject(nodeId);
            }
        }
        return null;
    }
    
    public void onNodePropertyChanged(String nodeId, String key, String value) {
        if (storyJson == null) return;
        hasUnsavedChanges = true;
        
        // Update JSON
        if (storyJson.has("nodes")) {
            JsonObject nodes = storyJson.getAsJsonObject("nodes");
            if (nodes.has(nodeId)) {
                JsonObject nodeData = nodes.getAsJsonObject(nodeId);
                if (nodeData.has("data")) {
                    nodeData.getAsJsonObject("data").addProperty(key, value);
                }
            }
        }
    }

    private void saveStory() {
        if (storyJson == null) {
            showMessage("§c由于加载失败，无法保存。");
            return;
        }
        try {
            // Update node positions in JSON
            JsonObject nodes = storyJson.getAsJsonObject("nodes");
            for (GraphNode node : canvas.getNodes().values()) {
                if (nodes.has(node.getNodeId())) {
                    JsonObject nodeData = nodes.getAsJsonObject(node.getNodeId());
                    JsonObject editor = new JsonObject();
                    editor.addProperty("x", node.getX());
                    editor.addProperty("y", node.getY());
                    nodeData.add("_editor", editor);
                }
            }
            
            // Send to server
            com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
            String jsonStr = gson.toJson(storyJson);
            
            showMessage("§e正在保存到服务器...");
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                new com.warmpixel.storyadventure.network.SaveStoryPayload(storyId, jsonStr)
            );
            
            hasUnsavedChanges = false;
        } catch (Exception e) {
            showMessage("§c保存失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void reloadStory() {
        if (hasUnsavedChanges) {
            showMessage("§e有未保存的更改，正在重新加载...");
        }
        loadStory();
        hasUnsavedChanges = false;
    }
    
    private void autoLayout() {
        if (storyJson == null) return;
        canvas.autoLayout(entryNodeId);
        hasUnsavedChanges = true;
        showMessage("§a自动布局完成");
    }
    
    private void centerView() {
        if (storyJson == null) return;
        canvas.centerView();
    }
    
    private void addNewNode() {
        if (storyJson == null) {
            showMessage("§c无法添加节点：故事数据未加载");
            return;
        }
        String newId = "new_node_" + System.currentTimeMillis() % 10000;
        
        // Add to JSON
        JsonObject nodes = storyJson.getAsJsonObject("nodes");
        JsonObject newNode = new JsonObject();
        newNode.addProperty("type", "CUTSCENE");
        newNode.add("data", new JsonObject());
        newNode.add("edges", new JsonArray());
        nodes.add(newId, newNode);
        
        // Add to canvas
        GraphNode node = new GraphNode(newId, "CUTSCENE", 100, 100);
        canvas.addNode(node);
        canvas.selectNode(node);
        
        propertyPanel.setNode(node, newNode);
        hasUnsavedChanges = true;
        
        showMessage("§a新节点已创建: " + newId);
    }
    
    private void deleteSelectedNode() {
        if (storyJson == null) return;
        GraphNode selected = canvas.getSelectedNode();
        if (selected == null) return;
        
        String nodeId = selected.getNodeId();
        
        // Remove from JSON
        if (storyJson.has("nodes")) {
            storyJson.getAsJsonObject("nodes").remove(nodeId);
        }
        
        // Rebuild graph
        buildGraphFromJson();
        propertyPanel.setNode(null, null);
        hasUnsavedChanges = true;
        
        showMessage("§c节点已删除: " + nodeId);
    }
    
    private void goBack() {
        if (hasUnsavedChanges) {
            showMessage("§e有未保存的更改");
        }
        Minecraft.getInstance().setScreen(new AdminStoryManagerScreen());
    }
    
    private void showMessage(String message) {
        var mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal(message));
        }
    }
}
