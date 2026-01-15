package com.warmpixel.storyadventure.client.ui;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Victory screen displayed when a story instance is completed successfully.
 * Shows congratulations, rewards, and countdown to teleport back to spawn.
 */
public class StrangerVictoryScreen extends StrangerScreen {
    
    private static final int COLOR_GOLD = 0xFFFFD700;
    private static final int COLOR_SUCCESS = 0xFF44FF44;
    private static final int COUNTDOWN_SECONDS = 10;
    
    private final String storyName;
    private final long completionTimeMs;
    private final List<RewardEntry> rewards;
    private final long screenOpenTime;
    private int countdownSeconds;
    private long lastCountdownUpdate;
    private boolean confirmed = false;
    private Runnable onConfirm;
    
    public StrangerVictoryScreen(String storyName, long completionTimeMs, List<RewardEntry> rewards) {
        super(Component.literal("任务完成"));
        this.storyName = storyName;
        this.completionTimeMs = completionTimeMs;
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
        
        int buttonWidth = 160;
        int buttonHeight = 30;
        int buttonX = (width - buttonWidth) / 2;
        int buttonY = height - 60;
        
        addStrangerButton(buttonX, buttonY, buttonWidth, buttonHeight,
            Component.literal("确认返回"), this::onConfirmClick);
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
            // Send confirm packet to server
            ClientPlayNetworking.send(
                new com.warmpixel.storyadventure.network.VictoryConfirmPayload()
            );
            
            // Close the screen
            this.onClose();
        }
    }
    
    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int centerX = width / 2;
        int y = 50;
        
        // Pulsing star effect
        float pulse = (float)(0.7 + 0.3 * Math.sin((System.currentTimeMillis() - screenOpenTime) / 300.0));
        int starAlpha = (int)(255 * pulse);
        int starColor = (starAlpha << 24) | (COLOR_GOLD & 0x00FFFFFF);
        
        // Draw congratulations header with stars
        String congrats = "★ 任务完成！ ★";
        int congratsWidth = font.width(congrats);
        graphics.drawString(font, congrats, centerX - congratsWidth / 2, y, starColor);
        y += 25;
        
        // Draw story name
        String storyText = storyName;
        int storyWidth = font.width(storyText);
        graphics.drawString(font, storyText, centerX - storyWidth / 2, y, COLOR_TEXT_TITLE);
        y += 15;
        
        // Draw separator
        int sepWidth = 180;
        graphics.fill(centerX - sepWidth / 2, y, centerX + sepWidth / 2, y + 1, COLOR_BORDER);
        y += 15;
        
        // Draw completion time
        String timeStr = formatTime(completionTimeMs);
        String timeLabel = "完成时间: " + timeStr;
        int timeWidth = font.width(timeLabel);
        graphics.drawString(font, timeLabel, centerX - timeWidth / 2, y, COLOR_TEXT_BODY);
        y += 25;
        
        // Draw rewards section
        if (!rewards.isEmpty()) {
            // Rewards box
            int boxWidth = 220;
            int boxHeight = 20 + rewards.size() * 18;
            int boxX = centerX - boxWidth / 2;
            int boxY = y;
            
            // Draw box background
            graphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xC0101010);
            
            // Draw box border
            graphics.fill(boxX, boxY, boxX + boxWidth, boxY + 1, COLOR_BORDER);
            graphics.fill(boxX, boxY + boxHeight - 1, boxX + boxWidth, boxY + boxHeight, COLOR_BORDER);
            graphics.fill(boxX, boxY, boxX + 1, boxY + boxHeight, COLOR_BORDER);
            graphics.fill(boxX + boxWidth - 1, boxY, boxX + boxWidth, boxY + boxHeight, COLOR_BORDER);
            
            // Draw rewards header
            String rewardsHeader = "— 奖励 —";
            int headerWidth = font.width(rewardsHeader);
            graphics.drawString(font, rewardsHeader, centerX - headerWidth / 2, boxY + 5, COLOR_GOLD);
            
            // Draw each reward
            int rewardY = boxY + 20;
            for (RewardEntry reward : rewards) {
                String rewardText = "◆ " + reward.description();
                graphics.drawString(font, rewardText, boxX + 15, rewardY, COLOR_SUCCESS);
                rewardY += 18;
            }
            
            y = boxY + boxHeight + 20;
        }
        
        // Draw countdown
        String countdownText = "(" + countdownSeconds + " 秒后自动返回)";
        int countdownWidth = font.width(countdownText);
        int countdownY = height - 35;
        
        // Flash when low
        int countdownColor = countdownSeconds <= 3 ? 
            (System.currentTimeMillis() % 500 < 250 ? 0xFFFF4444 : COLOR_TEXT_DIM) : 
            COLOR_TEXT_DIM;
        
        graphics.drawString(font, countdownText, centerX - countdownWidth / 2, countdownY, countdownColor);
    }
    
    private String formatTime(long ms) {
        long seconds = ms / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
    
    @Override
    public boolean shouldCloseOnEsc() {
        return false; // Prevent accidental closing
    }
    
    /**
     * Represents a reward to display.
     */
    public record RewardEntry(String type, String description, int amount) {
        public static RewardEntry experience(int amount) {
            return new RewardEntry("EXPERIENCE", amount + " 经验值", amount);
        }
        
        public static RewardEntry item(String itemName, int count) {
            String desc = count > 1 ? count + "x " + itemName : itemName;
            return new RewardEntry("ITEM", desc, count);
        }
    }
}
