package com.warmpixel.npcbusdriver.client;

import com.warmpixel.npcbusdriver.network.WandActionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.List;

public class BusDriverScreen extends Screen {
    private static final int PANEL_WIDTH = 320;
    private static final int PANEL_HEIGHT = 220;
    
    private final List<BlockPos> points;
    private int vehicleIndex = 0;
    private EditBox customVehicleId;
    
    // Default sensible vehicle options
    private static final List<String> PRESETS = Arrays.asList(
        "cityvehicles:blue_bus",
        "cityvehicles:red_bus",
        "cityvehicles:green_bus",
        "cityvehicles:yellow_bus",
        "cityvehicles:pink_bus",
        "automobility:bejeweled_motorcar",
        "automobility:copper_motorcar",
        "automobility:wooden_motorcar",
        "automobility:standard_white",
        "custom"
    );

    // Scrolling for point list
    private int scrollOffset = 0;

    public BusDriverScreen(List<BlockPos> points) {
        super(Component.literal("NPC Bus Driver"));
        this.points = points;
    }

    @Override
    protected void init() {
        super.init();
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = (this.height - PANEL_HEIGHT) / 2;

        // --- Right Side: Config ---
        int rightX = left + 160;
        
        // Vehicle Cycle
        this.addRenderableWidget(Button.builder(Component.literal("Type: " + getVehicleLabel(vehicleIndex)), button -> {
            vehicleIndex = (vehicleIndex + 1) % PRESETS.size();
            button.setMessage(Component.literal("Type: " + getVehicleLabel(vehicleIndex)));
            customVehicleId.visible = "custom".equals(PRESETS.get(vehicleIndex));
        }).bounds(rightX, top + 40, 140, 20).build());

        // Custom ID Input
        customVehicleId = new EditBox(this.font, rightX, top + 65, 140, 20, Component.literal("Custom ID"));
        customVehicleId.setMaxLength(64);
        customVehicleId.setVisible("custom".equals(PRESETS.get(vehicleIndex)));
        this.addRenderableWidget(customVehicleId);

        // Spawn Button
        this.addRenderableWidget(Button.builder(Component.literal("Spawn Driver"), button -> {
            spawnDriver();
        }).bounds(rightX, top + 150, 140, 20).build());

        // --- Left Side: Point List Handled in Render ---
        // Scroll Buttons (if needed, but mouse wheel is better)
    }
    
    private String getVehicleLabel(int index) {
        String id = PRESETS.get(index);
        if (id.equals("custom")) return "Custom...";
        if (id.contains(":")) return id.split(":")[1].replace("_", " "); // Simple formatted name
        return id;
    }

    private void spawnDriver() {
        String vid = PRESETS.get(vehicleIndex);
        if ("custom".equals(vid)) {
            vid = customVehicleId.getValue();
        }
        if (vid.isEmpty()) return;
        
        ClientPlayNetworking.send(new WandActionPayload(1, 0, vid));
        this.onClose();
    }
    
    private void removePoint(int index) {
        ClientPlayNetworking.send(new WandActionPayload(0, index, ""));
        // Optimistically remove locally to update UI immediately
        if (index >= 0 && index < points.size()) {
            points.remove(index);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        this.renderBackground(graphics, mouseX, mouseY, delta);
        
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = (this.height - PANEL_HEIGHT) / 2;
        
        // Background
        graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xEE202020);
        graphics.renderOutline(left, top, PANEL_WIDTH, PANEL_HEIGHT, 0xFFFFFFFF);
        
        // Title
        graphics.drawCenteredString(this.font, this.title, this.width / 2, top + 10, 0xFFFFFF);
        
        // --- Left Side: Points List ---
        int listX = left + 10;
        int listY = top + 40;
        int listW = 140;
        int listH = 160;
        
        graphics.fill(listX, listY, listX + listW, listY + listH, 0xFF000000); // Inner list bg
        
        // Masking would be ideal but Scissor is annoying in raw handle.
        // Just render points that fit
        int itemH = 14;
        int maxItems = listH / itemH;
        
        for (int i = 0; i < maxItems; i++) {
            int idx = i + scrollOffset;
            if (idx >= points.size()) break;
            
            BlockPos p = points.get(idx);
            int y = listY + i * itemH + 2;
            
            String txt = (idx + 1) + ". " + p.getX() + ", " + p.getY() + ", " + p.getZ();
            graphics.drawString(this.font, txt, listX + 4, y, 0xAAAAAA, false);
            
            // Draw "X" button rect
            int xBtnX = listX + listW - 12;
            boolean hovered = mouseX >= xBtnX && mouseX < xBtnX + 8 && mouseY >= y && mouseY < y + 8;
            graphics.drawString(this.font, "x", xBtnX, y, hovered ? 0xFF5555 : 0xAA0000, false);
        }
        
        // Setup details text
        graphics.drawString(this.font, "Control Points: " + points.size(), listX, listY - 12, 0xAAAAAA, false);
        
        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = (this.height - PANEL_HEIGHT) / 2;
        int listX = left + 10;
        int listY = top + 40;
        int listW = 140;
        int listH = 160;
        
        // Check list clicks
        if (mouseX >= listX && mouseX < listX + listW && mouseY >= listY && mouseY < listY + listH) {
            int itemH = 14;
            int relY = (int)mouseY - listY;
            int idx = (relY / itemH) + scrollOffset;
            
            if (idx < points.size()) {
                // Check X button
                int xBtnX = listX + listW - 12;
                if (mouseX >= xBtnX && mouseX < xBtnX + 12) { // wider hitbox
                    removePoint(idx);
                    return true;
                }
            }
        }
        return false;
    }
    
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        // Disable default background rendering (blur/darken)
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
