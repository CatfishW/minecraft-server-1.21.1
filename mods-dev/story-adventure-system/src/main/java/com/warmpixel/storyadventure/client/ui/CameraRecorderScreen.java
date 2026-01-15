package com.warmpixel.storyadventure.client.ui;

import com.google.gson.GsonBuilder;
import com.warmpixel.storyadventure.client.cinematic.CameraPath;
import com.warmpixel.storyadventure.client.cinematic.CameraRecording;
import com.warmpixel.storyadventure.client.cinematic.CinematicCameraController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Camera Recorder Screen - Compact UI for recording camera positions and rotations.
 * Uses the Stranger Things theme for consistent styling.
 */
public class CameraRecorderScreen extends StrangerScreen {
    
    private static final int PANEL_WIDTH = 340;
    private static final int PANEL_HEIGHT = 320;
    
    // Additional colors
    private static final int COLOR_PANEL_BG = 0xE8101018;
    private static final int COLOR_SECTION_BG = 0xCC1A1A2A;
    private static final int COLOR_ACCENT_CYAN = 0xFF00D9FF;
    private static final int COLOR_SUCCESS = 0xFF4CAF50;
    private static final int COLOR_WARNING = 0xFFFF9800;
    
    private final CameraRecording recording;
    
    // UI state
    private EditBox durationBox;
    private int selectedEasingIndex = 3;
    private final String[] easingOptions = {"LINEAR", "EASE_IN", "EASE_OUT", "EASE_IN_OUT", "CUBIC_IN_OUT", "SMOOTH_STEP"};
    
    private int scrollOffset = 0;
    private int selectedKeyframe = -1;
    
    private String statusMessage = "";
    private long statusMessageTime = 0;
    private int statusColor = COLOR_SUCCESS;
    
    public CameraRecorderScreen() {
        super(Component.literal("📹 摄像机录制器"));
        this.recording = new CameraRecording("New Recording");
    }
    
    @Override
    protected void init() {
        super.init();
        strangerButtons.clear();
        
        int panelX = (width - PANEL_WIDTH) / 2;
        int panelY = (height - PANEL_HEIGHT) / 2;
        
        // Duration input box
        durationBox = new EditBox(font, panelX + 110, panelY + 118, 50, 14, Component.literal("Duration"));
        durationBox.setValue("60");
        durationBox.setMaxLength(5);
        durationBox.setFilter(s -> s.matches("\\d*"));
        durationBox.setTextColor(0xFFFFFFFF);
        durationBox.setBordered(true);
        addRenderableWidget(durationBox);
        
        // === Recording Controls Row ===
        int ctrlY = panelY + 135;
        
        addStrangerButton(panelX + 15, ctrlY, 120, 18, 
            Component.literal("📍 录制关键帧"), this::recordKeyframe);
        
        addStrangerButton(panelX + 175, ctrlY, 150, 18,
            Component.literal(easingOptions[selectedEasingIndex]), this::cycleEasing);
        
        // === Bottom Action Buttons ===
        int btnY = panelY + PANEL_HEIGHT - 32;
        int btnWidth = 60;
        int btnGap = 5;
        int btnX = panelX + 15;
        
        addStrangerButton(btnX, btnY, btnWidth, 18, 
            Component.literal("💾 保存"), this::saveRecording);
        btnX += btnWidth + btnGap;
        
        addStrangerButton(btnX, btnY, btnWidth, 18,
            Component.literal("📂 加载"), this::loadRecording);
        btnX += btnWidth + btnGap;
        
        addStrangerButton(btnX, btnY, btnWidth, 18,
            Component.literal("🗑 清空"), () -> {
                recording.clearKeyframes();
                setStatus("已清空", COLOR_WARNING);
            });
        btnX += btnWidth + btnGap;
        
        addStrangerButton(btnX, btnY, btnWidth, 18,
            Component.literal("▶ 预览"), this::previewPath);
        btnX += btnWidth + btnGap;
        
        addStrangerButton(btnX, btnY, btnWidth, 18,
            Component.literal("📋 复制"), this::copyToClipboard);
    }
    
    private void cycleEasing() {
        selectedEasingIndex = (selectedEasingIndex + 1) % easingOptions.length;
        if (strangerButtons.size() > 1) {
            strangerButtons.get(1).setMessage(Component.literal(easingOptions[selectedEasingIndex]));
        }
    }
    
    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int panelX = (width - PANEL_WIDTH) / 2;
        int panelY = (height - PANEL_HEIGHT) / 2;
        
        // Main panel
        renderPanel(graphics, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT);
        
        int y = panelY + 30;
        
        // === Current Camera Section ===
        renderSectionBackground(graphics, panelX + 10, y, PANEL_WIDTH - 20, 45);
        graphics.drawString(font, "§e当前摄像机", panelX + 15, y + 3, COLOR_TEXT_TITLE);
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.gameRenderer != null) {
            var camera = mc.gameRenderer.getMainCamera();
            var pos = camera.getPosition();
            float yaw = camera.getYRot();
            float pitch = camera.getXRot();
            
            graphics.drawString(font, String.format("§7位置: §fX:%.1f Y:%.1f Z:%.1f", pos.x, pos.y, pos.z), 
                panelX + 15, y + 16, COLOR_TEXT_BODY);
            graphics.drawString(font, String.format("§7旋转: §f偏航:%.1f° 俯仰:%.1f° §7FOV:§f%.0f", yaw, pitch, mc.options.fov().get().doubleValue()), 
                panelX + 15, y + 28, COLOR_TEXT_BODY);
        }
        
        y += 50;
        
        // === Recording Controls Section ===
        renderSectionBackground(graphics, panelX + 10, y, PANEL_WIDTH - 20, 40);
        graphics.drawString(font, "§e录制控制", panelX + 15, y + 3, COLOR_TEXT_TITLE);
        graphics.drawString(font, "时长(tick):", panelX + 15, y + 20, COLOR_TEXT_DIM);
        graphics.drawString(font, "缓动:", panelX + 175, y + 20, COLOR_TEXT_DIM);
        
        y += 60;
        
        // === Keyframes List Section ===
        String listTitle = String.format("§e关键帧 (%d)", recording.getKeyframeCount());
        renderSectionBackground(graphics, panelX + 10, y, PANEL_WIDTH - 20, 85);
        graphics.drawString(font, listTitle, panelX + 15, y + 3, COLOR_TEXT_TITLE);
        
        int listY = y + 15;
        int listItemHeight = 16;
        int maxVisible = 4;
        
        List<CameraRecording.RecordedKeyframe> keyframes = recording.getKeyframes();
        
        if (keyframes.isEmpty()) {
            graphics.drawCenteredString(font, "§8暂无关键帧", panelX + PANEL_WIDTH / 2, listY + 25, COLOR_TEXT_DIM);
        } else {
            for (int i = scrollOffset; i < Math.min(keyframes.size(), scrollOffset + maxVisible); i++) {
                CameraRecording.RecordedKeyframe kf = keyframes.get(i);
                int itemY = listY + (i - scrollOffset) * listItemHeight;
                
                if (i == selectedKeyframe) {
                    graphics.fill(panelX + 12, itemY, panelX + PANEL_WIDTH - 12, itemY + listItemHeight - 1, 0x30FFFFFF);
                }
                
                if (mouseX >= panelX + 12 && mouseX <= panelX + PANEL_WIDTH - 12 &&
                    mouseY >= itemY && mouseY < itemY + listItemHeight) {
                    graphics.fill(panelX + 12, itemY, panelX + PANEL_WIDTH - 12, itemY + listItemHeight - 1, 0x15FFFFFF);
                }
                
                graphics.drawString(font, String.format("§b#%d", i + 1), panelX + 15, itemY + 3, COLOR_ACCENT_CYAN);
                graphics.drawString(font, String.format("§7(%.0f,%.0f,%.0f)", kf.x(), kf.y(), kf.z()), panelX + 35, itemY + 3, COLOR_TEXT_BODY);
                graphics.drawString(font, String.format("§f%.0f°/%.0f°", kf.yaw(), kf.pitch()), panelX + 155, itemY + 3, COLOR_TEXT_BODY);
                graphics.drawString(font, String.format("§e%dt", kf.durationTicks()), panelX + 230, itemY + 3, COLOR_TEXT_BODY);
                graphics.drawString(font, "§c✕", panelX + PANEL_WIDTH - 25, itemY + 3, 0xFFFF5555);
            }
            
            if (scrollOffset > 0) graphics.drawString(font, "§7▲", panelX + PANEL_WIDTH - 22, y + 3, 0xAAAAAA);
            if (scrollOffset + maxVisible < keyframes.size()) graphics.drawString(font, "§7▼", panelX + PANEL_WIDTH - 22, y + 75, 0xAAAAAA);
        }
        
        y += 90;
        
        // === Status Message ===
        if (!statusMessage.isEmpty() && System.currentTimeMillis() - statusMessageTime < 2500) {
            graphics.drawCenteredString(font, statusMessage, panelX + PANEL_WIDTH / 2, y, statusColor);
        }
    }
    
    private void renderPanel(GuiGraphics graphics, int x, int y, int w, int h) {
        // Glow
        for (int i = 3; i >= 1; i--) {
            int glowColor = ((8 + i * 4) << 24) | (COLOR_NEON_RED & 0x00FFFFFF);
            graphics.fill(x - i, y - i, x + w + i, y + h + i, glowColor);
        }
        
        graphics.fill(x, y, x + w, y + h, COLOR_PANEL_BG);
        
        // Border
        graphics.fill(x, y, x + w, y + 1, COLOR_NEON_RED);
        graphics.fill(x, y + h - 1, x + w, y + h, COLOR_NEON_RED);
        graphics.fill(x, y, x + 1, y + h, COLOR_NEON_RED);
        graphics.fill(x + w - 1, y, x + w, y + h, COLOR_NEON_RED);
        
        // Header
        graphics.fill(x + 1, y + 1, x + w - 1, y + 24, 0xFF1A0808);
        graphics.fill(x + 1, y + 24, x + w - 1, y + 25, COLOR_BORDER);
        
        graphics.drawCenteredString(font, title, x + w / 2, y + 8, COLOR_ACCENT_CYAN);
        graphics.drawString(font, "§8[ESC]", x + w - 35, y + 8, COLOR_TEXT_DIM);
    }
    
    private void renderSectionBackground(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, COLOR_SECTION_BG);
        graphics.fill(x, y, x + w, y + 1, 0x25FFFFFF);
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizAmount, double vertAmount) {
        int maxScroll = Math.max(0, recording.getKeyframeCount() - 4);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) vertAmount));
        return true;
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int panelX = (width - PANEL_WIDTH) / 2;
        int panelY = (height - PANEL_HEIGHT) / 2;
        
        int listY = panelY + 30 + 50 + 60 + 15;
        int listItemHeight = 16;
        
        if (mouseX >= panelX + 12 && mouseX <= panelX + PANEL_WIDTH - 12) {
            for (int i = 0; i < Math.min(recording.getKeyframeCount() - scrollOffset, 4); i++) {
                int itemY = listY + i * listItemHeight;
                if (mouseY >= itemY && mouseY < itemY + listItemHeight) {
                    int keyframeIndex = scrollOffset + i;
                    
                    if (mouseX >= panelX + PANEL_WIDTH - 30) {
                        recording.removeKeyframe(keyframeIndex);
                        setStatus("已删除 #" + (keyframeIndex + 1), COLOR_WARNING);
                        if (selectedKeyframe >= recording.getKeyframeCount()) selectedKeyframe = -1;
                        return true;
                    }
                    
                    selectedKeyframe = keyframeIndex;
                    return true;
                }
            }
        }
        
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    private void recordKeyframe() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameRenderer == null) return;
        
        var camera = mc.gameRenderer.getMainCamera();
        var pos = camera.getPosition();
        float yaw = camera.getYRot();
        float pitch = camera.getXRot();
        float fov = mc.options.fov().get().floatValue();
        
        int duration = 60;
        try { duration = Integer.parseInt(durationBox.getValue()); } catch (NumberFormatException ignored) {}
        if (recording.getKeyframeCount() == 0) duration = 0;
        
        recording.addKeyframe(pos.x, pos.y, pos.z, yaw, pitch, fov, duration, easingOptions[selectedEasingIndex]);
        setStatus("已录制 #" + recording.getKeyframeCount(), COLOR_SUCCESS);
    }
    
    private void saveRecording() {
        if (recording.getKeyframeCount() == 0) { setStatus("无关键帧", COLOR_NEON_RED); return; }
        try {
            Path savedPath = recording.saveToFile();
            setStatus("已保存: " + savedPath.getFileName(), COLOR_SUCCESS);
        } catch (IOException e) { setStatus("保存失败", COLOR_NEON_RED); }
    }
    
    private void loadRecording() {
        List<Path> files = CameraRecording.listRecordingFiles();
        if (files.isEmpty()) { setStatus("无文件", COLOR_WARNING); return; }
        
        try {
            CameraRecording loaded = CameraRecording.loadFromFile(files.get(files.size() - 1));
            recording.clearKeyframes();
            for (var kf : loaded.getKeyframes()) recording.addKeyframe(kf);
            setStatus("已加载 " + loaded.getKeyframeCount() + " 帧", COLOR_SUCCESS);
        } catch (IOException e) { setStatus("加载失败", COLOR_NEON_RED); }
    }
    
    private void previewPath() {
        if (recording.getKeyframeCount() < 2) { setStatus("需≥2帧", COLOR_WARNING); return; }
        
        CameraPath path = recording.toCameraPath();
        CinematicCameraController.CutsceneConfig config = new CinematicCameraController.CutsceneConfig()
            .setSkippable(true).setLetterboxEnabled(true)
            .setOnComplete(() -> Minecraft.getInstance().setScreen(new CameraRecorderScreen()));
        
        CinematicCameraController.getInstance().startCutscene(path, config);
        this.onClose();
    }
    
    private void copyToClipboard() {
        if (recording.getKeyframeCount() == 0) { setStatus("无关键帧", COLOR_WARNING); return; }
        String json = new GsonBuilder().setPrettyPrinting().create().toJson(recording.toCameraPathJson());
        Minecraft.getInstance().keyboardHandler.setClipboard(json);
        setStatus("已复制JSON", COLOR_SUCCESS);
    }
    
    private void setStatus(String message, int color) {
        this.statusMessage = message;
        this.statusMessageTime = System.currentTimeMillis();
        this.statusColor = color;
    }
}
