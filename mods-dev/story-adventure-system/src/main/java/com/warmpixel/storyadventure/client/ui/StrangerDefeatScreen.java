package com.warmpixel.storyadventure.client.ui;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Defeat screen displayed when a story instance fails (e.g., too many deaths).
 * Shows failure message, consolation rewards, and countdown to return to spawn.
 */
public class StrangerDefeatScreen extends StrangerScreen {
    
    private static final int COLOR_RED = 0xFFFF4444;
    private static final int COLOR_DARK_RED = 0xFF882222;
    private static final int COLOR_CONSOLE = 0xFF888888;
    private static final int COUNTDOWN_SECONDS = 10;
    
    private final String storyName;
    private final String failureReason;
    private final int deathCount;
    private final int maxDeaths;
    private final List<RewardEntry> rewards;
    private final long screenOpenTime;
    private int countdownSeconds;
    private long lastCountdownUpdate;
    private boolean confirmed = false;
    private Runnable onConfirm;
    
    public StrangerDefeatScreen(String storyName, String failureReason, int deathCount, int maxDeaths, List<RewardEntry> rewards) {
        super(Component.literal("任务失败"));
        this.storyName = storyName;
        this.failureReason = failureReason != null ? failureReason : "未知原因";
        this.deathCount = deathCount;
        this.maxDeaths = maxDeaths;
        this.rewards = rewards != null ? rewards : new ArrayList<>();
        this.screenOpenTime = System.currentTimeMillis();
        this.countdownSeconds = COUNTDOWN_SECONDS;
        this.lastCountdownUpdate = System.currentTimeMillis();
    }
    
    public void setOnConfirm(Runnable onConfirm) {
        this.onConfirm = onConfirm;
    }
    
    @Override
    protected void init() {
        super.init();
        
        int buttonWidth = 120;
        int buttonHeight = 26;
        int buttonX = guiLeft + (guiWidth - buttonWidth) / 2;
        int buttonY = guiTop + guiHeight - 40;
        
        addStrangerButton(buttonX, buttonY, buttonWidth, buttonHeight,
            Component.literal("返回重生点"), this::onConfirmClick);
    }
    
    @Override
    public void tick() {
        super.tick();
        
        if (confirmed) return;
        
        // Update countdown
        long now = System.currentTimeMillis();
        if (now - lastCountdownUpdate >= 1000) {
            lastCountdownUpdate = now;
            countdownSeconds--;
            
            if (countdownSeconds <= 0) {
                onConfirmClick();
            }
        }
    }
    
    private void onConfirmClick() {
        if (!confirmed) {
            confirmed = true;
            if (onConfirm != null) {
                onConfirm.run();
            }
            
            // Close the screen
            this.onClose();
        }
    }
    
    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int centerX = guiLeft + guiWidth / 2;
        int y = guiTop + 45;
        
        // Pulsing skull effect
        float pulse = (float)(0.5 + 0.5 * Math.sin((System.currentTimeMillis() - screenOpenTime) / 400.0));
        int skullAlpha = (int)(255 * pulse);
        int skullColor = (skullAlpha << 24) | (COLOR_RED & 0x00FFFFFF);
        
        // Draw defeat header with skull symbols
        String defeatHeader = "☠ 任务失败 ☠";
        int headerWidth = font.width(defeatHeader);
        graphics.drawString(font, defeatHeader, centerX - headerWidth / 2, y, skullColor);
        y += 20;
        
        // Draw story name
        String storyText = storyName;
        int storyWidth = font.width(storyText);
        graphics.drawString(font, storyText, centerX - storyWidth / 2, y, COLOR_TEXT_TITLE);
        y += 12;
        
        // Draw separator
        int sepWidth = 140;
        graphics.fill(centerX - sepWidth / 2, y, centerX + sepWidth / 2, y + 1, COLOR_DARK_RED);
        y += 12;
        
        // Draw failure reason
        String reasonLabel = "失败原因: " + failureReason;
        int reasonWidth = font.width(reasonLabel);
        graphics.drawString(font, reasonLabel, centerX - reasonWidth / 2, y, COLOR_RED);
        y += 15;
        
        // Draw death count
        String deathStr = String.format("团队死亡次数: %d / %d", deathCount, maxDeaths);
        int deathWidth = font.width(deathStr);
        graphics.drawString(font, deathStr, centerX - deathWidth / 2, y, COLOR_TEXT_BODY);
        y += 20;
        
        // Draw consolation rewards section
        if (!rewards.isEmpty()) {
            // Rewards box
            int boxWidth = 180;
            int boxHeight = 20 + rewards.size() * 18;
            int boxX = centerX - boxWidth / 2;
            int boxY = y;
            
            // Draw box background
            graphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xC0101010);
            
            // Draw box border (dark red)
            renderRectOutline(graphics, boxX, boxY, boxWidth, boxHeight, COLOR_DARK_RED);
            
            // Draw rewards header
            String rewardsHeader = "— 安慰奖励 —";
            int headerWidth2 = font.width(rewardsHeader);
            graphics.drawString(font, rewardsHeader, centerX - headerWidth2 / 2, boxY + 5, COLOR_CONSOLE);
            
            // Draw each reward
            int rewardY = boxY + 20;
            for (RewardEntry reward : rewards) {
                String rewardText = "◆ " + reward.description();
                graphics.drawString(font, rewardText, boxX + 15, rewardY, COLOR_TEXT_BODY);
                rewardY += 18;
            }
            
            y = boxY + boxHeight + 15;
        }
        
        // Draw countdown
        String countdownText = "(" + countdownSeconds + " 秒后自动返回)";
        int countdownWidth = font.width(countdownText);
        int countdownY = guiTop + guiHeight - 15;
        
        // Flash when low
        int countdownColor = countdownSeconds <= 3 ? 
            (System.currentTimeMillis() % 500 < 250 ? COLOR_RED : COLOR_TEXT_DIM) : 
            COLOR_TEXT_DIM;
        
        graphics.drawString(font, countdownText, centerX - countdownWidth / 2, countdownY, countdownColor);
    }
    
    @Override
    public boolean shouldCloseOnEsc() {
        return false; // Prevent accidental closing
    }
    
    /**
     * Represents a consolation reward to display.
     */
    public record RewardEntry(String type, String description, int amount) {
        public static RewardEntry experience(int amount) {
            return new RewardEntry("EXPERIENCE", amount + " 经验值", amount);
        }
    }
}
