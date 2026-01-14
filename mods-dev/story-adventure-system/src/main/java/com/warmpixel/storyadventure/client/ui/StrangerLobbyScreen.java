package com.warmpixel.storyadventure.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Stranger Things themed lobby screen for players to ready up before starting.
 * Shows party members, ready status, and allows configuration.
 */
public class StrangerLobbyScreen extends StrangerScreen {
    
    private static final int MEMBER_ENTRY_HEIGHT = 36;
    
    private String storyName;
    private String storyDescription;
    private int minPlayers;
    private int maxPlayers;
    private int estimatedMinutes;
    
    private List<PartyMember> members = new ArrayList<>();
    private boolean isLeader = false;
    private boolean isReady = false;
    private long countdownStartTime = 0;
    private boolean countdownActive = false;
    
    public StrangerLobbyScreen(String storyName, String storyDescription, 
                                int minPlayers, int maxPlayers, int estimatedMinutes) {
        super(Component.literal("准备大厅"));
        this.storyName = storyName;
        this.storyDescription = storyDescription;
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
        this.estimatedMinutes = estimatedMinutes;
    }
    
    public void setLeader(boolean leader) {
        this.isLeader = leader;
    }
    
    public void setReady(boolean ready) {
        this.isReady = ready;
    }
    
    public void addMember(UUID playerId, String name, boolean ready, boolean isLeader) {
        members.add(new PartyMember(playerId, name, ready, isLeader));
    }
    
    public void clearMembers() {
        members.clear();
    }
    
    public void updateMemberReady(UUID playerId, boolean ready) {
        for (PartyMember m : members) {
            if (m.id.equals(playerId)) {
                members.set(members.indexOf(m), new PartyMember(m.id, m.name, ready, m.isLeader));
                break;
            }
        }
    }
    
    @Override
    protected void init() {
        super.init();
        
        int buttonWidth = 140;
        int buttonHeight = 28;
        int bottomY = height - 50;
        
        // Ready button (for everyone)
        String buttonText = isReady ? "取消准备" : (isLeader ? "准备开始" : "准备就绪");
        addStrangerButton(width / 2 - buttonWidth / 2, bottomY, buttonWidth, buttonHeight,
            Component.literal(buttonText),
            this::toggleReady);
            
        // Start button (for leader, only if enough people ready)
        if (isLeader) {
            long readyCount = members.stream().filter(m -> m.ready).count();
            boolean canStart = readyCount >= minPlayers;
            
            StrangerButton startBtn = addStrangerButton(width / 2 + buttonWidth / 2 + 10, bottomY, 120, buttonHeight,
                Component.literal("开始冒险"),
                this::startAdventure);
            startBtn.active = canStart;
            startBtn.setGlowPulse(canStart);
            
            // Invite button
            addStrangerButton(width / 2 - buttonWidth / 2 - 130, bottomY, 120, buttonHeight,
                Component.literal("邀请玩家"),
                () -> minecraft.setScreen(new StrangerInvitePlayerScreen(this)));
        }
        
        // Cancel/Leave button (bottom right)
        addStrangerButton(width - 100, bottomY, 80, buttonHeight,
            Component.literal(isLeader ? "解散" : "离开"),
            isLeader ? this::disbandParty : this::onClose);
    }
    
    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Story info panel (left side)
        renderStoryInfoPanel(graphics);
        
        // Party members panel (right side)
        renderPartyPanel(graphics, mouseX, mouseY);
        
        // Countdown overlay
        if (countdownActive) {
            renderCountdown(graphics);
        }
    }
    
    private void renderStoryInfoPanel(GuiGraphics graphics) {
        int panelX = 30;
        int panelY = 50;
        int panelWidth = width / 2 - 50;
        int panelHeight = height - 130;
        
        // Background
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xE0080808);
        drawPanelBorder(graphics, panelX, panelY, panelWidth, panelHeight);
        
        int y = panelY + 10;
        
        // Story title
        graphics.drawString(font, "【" + storyName + "】", panelX + 15, y, COLOR_NEON_RED);
        y += 20;
        
        // Description (wrapped)
        List<String> descLines = wrapText(storyDescription, panelWidth - 30);
        for (String line : descLines) {
            graphics.drawString(font, line, panelX + 15, y, COLOR_TEXT_BODY);
            y += 11;
        }
        y += 10;
        
        // Separator
        graphics.fill(panelX + 15, y, panelX + panelWidth - 15, y + 1, COLOR_BORDER);
        y += 15;
        
        // Info items
        graphics.drawString(font, "👥 人数要求: " + minPlayers + "-" + maxPlayers + "人", panelX + 15, y, COLOR_TEXT_DIM);
        y += 14;
        graphics.drawString(font, "⏱ 预计时长: ~" + estimatedMinutes + "分钟", panelX + 15, y, COLOR_TEXT_DIM);
        y += 14;
        graphics.drawString(font, "⚠ 难度: ★★★☆☆", panelX + 15, y, COLOR_TEXT_DIM);
        
        // Tips at bottom
        y = panelY + panelHeight - 40;
        graphics.fill(panelX + 15, y, panelX + panelWidth - 15, y + 1, COLOR_BORDER);
        y += 8;
        graphics.drawString(font, "💡 提示: 建议携带武器和食物", panelX + 15, y, 0xFF888888);
    }
    
    private void renderPartyPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        int panelX = width / 2 + 10;
        int panelY = 50;
        int panelWidth = width / 2 - 40;
        int panelHeight = height - 130;
        
        // Background
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xE0080808);
        drawPanelBorder(graphics, panelX, panelY, panelWidth, panelHeight);
        
        // Panel title
        graphics.drawString(font, "队伍成员 (" + members.size() + "/" + maxPlayers + ")", 
            panelX + 15, panelY + 10, COLOR_NEON_RED);
        
        // Ready count
        long readyCount = members.stream().filter(m -> m.ready).count();
        String readyText = "准备: " + readyCount + "/" + members.size();
        int readyColor = readyCount == members.size() ? 0xFF44FF44 : COLOR_TEXT_DIM;
        graphics.drawString(font, readyText, panelX + panelWidth - font.width(readyText) - 15, 
            panelY + 10, readyColor);
        
        // Member entries
        int y = panelY + 30;
        for (PartyMember member : members) {
            renderMemberEntry(graphics, member, panelX + 10, y, panelWidth - 20, mouseX, mouseY);
            y += MEMBER_ENTRY_HEIGHT + 4;
        }
        
        // Empty slots
        for (int i = members.size(); i < maxPlayers; i++) {
            renderEmptySlot(graphics, panelX + 10, y, panelWidth - 20);
            y += MEMBER_ENTRY_HEIGHT + 4;
        }
    }
    
    private void renderMemberEntry(GuiGraphics graphics, PartyMember member, 
                                    int x, int y, int width, int mouseX, int mouseY) {
        // Background
        int bgColor = member.ready ? 0xFF0A1A0A : 0xFF0A0A0A;
        graphics.fill(x, y, x + width, y + MEMBER_ENTRY_HEIGHT, bgColor);
        
        // Border
        int borderColor = member.ready ? 0xFF44FF44 : COLOR_BORDER;
        graphics.fill(x, y, x + width, y + 1, borderColor);
        graphics.fill(x, y + MEMBER_ENTRY_HEIGHT - 1, x + width, y + MEMBER_ENTRY_HEIGHT, borderColor);
        graphics.fill(x, y, x + 1, y + MEMBER_ENTRY_HEIGHT, borderColor);
        graphics.fill(x + width - 1, y, x + width, y + MEMBER_ENTRY_HEIGHT, borderColor);
        
        // Player icon (placeholder)
        graphics.fill(x + 8, y + 6, x + 30, y + 30, 0xFF333333);
        graphics.drawString(font, "👤", x + 11, y + 12, COLOR_TEXT_BODY);
        
        // Name
        int nameColor = member.isLeader ? COLOR_NEON_RED : COLOR_TEXT_BODY;
        String nameText = member.name + (member.isLeader ? " 👑" : "");
        graphics.drawString(font, nameText, x + 38, y + 8, nameColor);
        
        // Ready status
        String statusText = member.ready ? "✓ 已准备" : "○ 等待中...";
        int statusColor = member.ready ? 0xFF44FF44 : COLOR_TEXT_DIM;
        graphics.drawString(font, statusText, x + 38, y + 22, statusColor);
    }
    
    private void renderEmptySlot(GuiGraphics graphics, int x, int y, int width) {
        // Dashed border style for empty slot
        graphics.fill(x, y, x + width, y + MEMBER_ENTRY_HEIGHT, 0x40080808);
        
        // Draw dashed border
        for (int i = 0; i < width; i += 8) {
            if ((i / 4) % 2 == 0) {
                graphics.fill(x + i, y, Math.min(x + i + 4, x + width), y + 1, COLOR_BORDER);
                graphics.fill(x + i, y + MEMBER_ENTRY_HEIGHT - 1, Math.min(x + i + 4, x + width), y + MEMBER_ENTRY_HEIGHT, COLOR_BORDER);
            }
        }
        
        graphics.drawString(font, "空位 - 等待加入...", x + width / 2 - 45, y + 14, COLOR_TEXT_DIM);
    }
    
    private void renderCountdown(GuiGraphics graphics) {
        long elapsed = System.currentTimeMillis() - countdownStartTime;
        int secondsLeft = Math.max(0, 5 - (int)(elapsed / 1000));
        
        if (secondsLeft == 0) {
            // Start the adventure
            onClose();
            return;
        }
        
        // Dark overlay
        graphics.fill(0, 0, width, height, 0xC0000000);
        
        // Countdown number
        String countText = String.valueOf(secondsLeft);
        float scale = 5.0f;
        int textWidth = (int)(font.width(countText) * scale);
        
        // Pulsing effect
        float pulse = (float)(0.8 + 0.2 * Math.sin(elapsed / 100.0));
        int alpha = (int)(255 * pulse);
        int color = (alpha << 24) | (COLOR_NEON_RED & 0x00FFFFFF);
        
        graphics.drawCenteredString(font, countText, width / 2, height / 2 - 30, color);
        graphics.drawCenteredString(font, "冒险即将开始...", width / 2, height / 2 + 20, COLOR_TEXT_BODY);
    }
    
    private void drawPanelBorder(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + 1, COLOR_BORDER);
        graphics.fill(x, y + h - 1, x + w, y + h, COLOR_BORDER);
        graphics.fill(x, y, x + 1, y + h, COLOR_BORDER);
        graphics.fill(x + w - 1, y, x + w, y + h, COLOR_BORDER);
        
        int cs = 10;
        graphics.fill(x, y, x + cs, y + 2, COLOR_NEON_RED);
        graphics.fill(x, y, x + 2, y + cs, COLOR_NEON_RED);
        graphics.fill(x + w - cs, y, x + w, y + 2, COLOR_NEON_RED);
        graphics.fill(x + w - 2, y, x + w, y + cs, COLOR_NEON_RED);
        graphics.fill(x, y + h - 2, x + cs, y + h, COLOR_NEON_RED);
        graphics.fill(x, y + h - cs, x + 2, y + h, COLOR_NEON_RED);
        graphics.fill(x + w - cs, y + h - 2, x + w, y + h, COLOR_NEON_RED);
        graphics.fill(x + w - 2, y + h - cs, x + w, y + h, COLOR_NEON_RED);
    }
    
    private List<String> wrapText(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            String test = current.toString() + c;
            
            if (font.width(test) <= maxWidth) {
                current.append(c);
            } else {
                if (!current.isEmpty()) lines.add(current.toString());
                current = new StringBuilder(String.valueOf(c));
            }
        }
        
        if (!current.isEmpty()) lines.add(current.toString());
        return lines;
    }
    
    private void toggleReady() {
        isReady = !isReady;
        // Send to server
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
            new com.warmpixel.storyadventure.network.ToggleReadyPayload(isReady)
        );
        // Rebuild will happen when server sends SyncLobby back, 
        // but we can rebuild locally for immediate feedback
        rebuildButtons();
    }
    
    private void startAdventure() {
        // Check if enough players are ready
        long readyCount = members.stream().filter(m -> m.ready).count();
        if (readyCount >= minPlayers) {
            countdownActive = true;
            countdownStartTime = System.currentTimeMillis();
            
            // Send start request
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                new com.warmpixel.storyadventure.network.StoryActionPayload(
                    com.warmpixel.storyadventure.network.StoryActionPayload.Action.START_ADVENTURE,
                    ""
                )
            );
        }
    }
    
    private void disbandParty() {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
            new com.warmpixel.storyadventure.network.StoryActionPayload(
                isLeader ? com.warmpixel.storyadventure.network.StoryActionPayload.Action.DISBAND_PARTY :
                           com.warmpixel.storyadventure.network.StoryActionPayload.Action.LEAVE_PARTY,
                ""
            )
        );
        onClose();
    }
    
    public void rebuildButtons() {
        strangerButtons.clear();
        clearWidgets();
        init();
    }
    
    public void startCountdown() {
        countdownActive = true;
        countdownStartTime = System.currentTimeMillis();
    }
    
    public record PartyMember(UUID id, String name, boolean ready, boolean isLeader) {}
}
