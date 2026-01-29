package com.warmpixel.storyadventure.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.systems.RenderSystem;

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
    private int countdownSecondsLeft = -1;

    private int missionScrollOffset = 0;
    private int missionScrollMax = 0;
    private int missionPanelX = 0;
    private int missionPanelY = 0;
    private int missionPanelW = 0;
    private int missionPanelH = 0;
    
    public StrangerLobbyScreen(String storyName, String storyDescription, 
                                int minPlayers, int maxPlayers, int estimatedMinutes) {
        super(Component.translatable("gui.storyadventure.title.lobby"));
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
    protected int getWindowWidth() {
        return Math.min(width - 20, 640);
    }

    @Override
    protected int getWindowHeight() {
        return Math.min(height - 20, 420);
    }

    @Override
    protected void init() {
        super.init();
        missionScrollOffset = 0;
        
        int bottomY = guiTop + guiHeight - 42;
        int centerX = guiLeft + guiWidth / 2;
        
        // --- Action Buttons (Bottom Center) ---
        // Responsive button sizing based on window width
        int minButtonWidth = 80;
        int preferredButtonWidth = 120;
        int buttonHeight = 28;
        int minGap = 4;
        int preferredGap = 8;
        
        // Calculate available width for buttons
        int availableWidth = guiWidth - 40; 
        
        int buttonWidth;
        int gap;
        
        if (isLeader) {
            int requiredForPreferred = (preferredButtonWidth * 3) + (preferredGap * 2);
            if (availableWidth >= requiredForPreferred) {
                buttonWidth = preferredButtonWidth;
                gap = preferredGap;
            } else {
                int requiredForMin = (minButtonWidth * 3) + (minGap * 2);
                if (availableWidth >= requiredForMin) {
                    gap = minGap;
                    buttonWidth = (availableWidth - gap * 2) / 3;
                    buttonWidth = Math.min(buttonWidth, preferredButtonWidth);
                } else {
                    buttonWidth = minButtonWidth;
                    gap = minGap;
                }
            }
        } else {
            buttonWidth = Math.min(preferredButtonWidth, availableWidth / 2);
            gap = preferredGap;
        }

        int totalButtonRowWidth = isLeader ? (buttonWidth * 3 + gap * 2) : buttonWidth;
        int buttonRowStartX = centerX - totalButtonRowWidth / 2;

        if (isLeader) {
            // Invite Button
            addStrangerButton(buttonRowStartX, bottomY, buttonWidth, buttonHeight,
                Component.translatable("gui.storyadventure.button.invite_player"),
                () -> minecraft.setScreen(new StrangerInvitePlayerScreen(this)));
            
            // Ready Button
            int readyX = buttonRowStartX + buttonWidth + gap;
            Component readyText = isReady ? Component.translatable("gui.storyadventure.button.cancel_ready") : Component.translatable("gui.storyadventure.button.ready");
            StrangerButton readyBtn = addStrangerButton(readyX, bottomY, buttonWidth, buttonHeight,
                readyText,
                this::toggleReady);
            if (!isReady) readyBtn.setGlowPulse(true);
            
            // Start Button
            long readyCount = members.stream().filter(m -> m.ready).count();
            boolean canStart = readyCount >= minPlayers;
            
            int startX = buttonRowStartX + (buttonWidth + gap) * 2;
            StrangerButton startBtn = addStrangerButton(startX, bottomY, buttonWidth, buttonHeight,
                Component.translatable("gui.storyadventure.button.start_adventure"),
                this::startAdventure);
            startBtn.active = canStart;
            startBtn.setGlowPulse(canStart);
            
            // Disband
            int smallWidth = 70;
            int smallHeight = 18;
            int disbandX = guiLeft + guiWidth - smallWidth - 10;
            int disbandY = guiTop + 8;
            addStrangerButton(disbandX, disbandY, smallWidth, smallHeight,
                Component.translatable("gui.storyadventure.button.disband"),
                this::disbandParty);
            
        } else {
            // Ready Button
            Component readyText = isReady ? Component.translatable("gui.storyadventure.button.cancel_ready") : Component.translatable("gui.storyadventure.button.ready");
            StrangerButton readyBtn = addStrangerButton(centerX - buttonWidth / 2, bottomY, buttonWidth, buttonHeight,
                readyText,
                this::toggleReady);
            if (!isReady) readyBtn.setGlowPulse(true);
            
            // Leave
            int smallWidth = 70;
            addStrangerButton(guiLeft + guiWidth - smallWidth - 10, guiTop + 8, smallWidth, 18,
                Component.translatable("gui.storyadventure.button.leave"),
                this::onClose);
        }
    }
    
    @Override
    protected void renderTitle(GuiGraphics graphics, int mouseX, int mouseY) {
         int centerX = guiLeft + guiWidth / 2;
         int topY = guiTop + 15;
         
         // Tab 1: STORIES
         Component tab1Text = Component.translatable("gui.storyadventure.tab.story_select");
         int tab1W = font.width(tab1Text) + 20;
         int tab1X = centerX - tab1W - 5;
         renderTopTab(graphics, tab1Text.getString(), tab1X, topY, false, mouseX, mouseY);
         
         // Tab 2: LOBBY (Active)
         Component tab2Text = Component.translatable("gui.storyadventure.tab.lobby");
         int tab2W = font.width(tab2Text) + 20;
         int tab2X = centerX + 5;
         renderTopTab(graphics, tab2Text.getString(), tab2X, topY, true, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        
        // Handle Tab Clicks
        int centerX = guiLeft + guiWidth / 2;
        int topY = guiTop + 15;
        
        Component tab1Text = Component.translatable("gui.storyadventure.tab.story_select");
        int tab1W = font.width(tab1Text) + 20;
        int tab1H = 24;
        int tab1X = centerX - tab1W - 5;
        
        if (mouseX >= tab1X && mouseX < tab1X + tab1W && mouseY >= topY && mouseY < topY + tab1H) {
             minecraft.setScreen(new StrangerStoryListScreen());
             return true;
        }
        
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double hAmount, double vAmount) {
        if (isInMissionPanel(mouseX, mouseY) && missionScrollMax > 0) {
            int delta = (int)(-vAmount * 12);
            missionScrollOffset = Math.max(0, Math.min(missionScrollOffset + delta, missionScrollMax));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, hAmount, vAmount);
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Layout Config - within window bounds
        int margin = 12;
        int headerHeight = 35;
        int footerHeight = 45;
        int contentHeight = guiHeight - headerHeight - footerHeight - margin;
        
        int splitGap = 10;
        int availablePanelWidth = guiWidth - margin * 2 - splitGap;
        
        int leftPanelWidth = availablePanelWidth * 42 / 100;
        int rightPanelWidth = availablePanelWidth - leftPanelWidth;
        
        int leftPanelX = guiLeft + margin;
        int rightPanelX = leftPanelX + leftPanelWidth + splitGap;
        int panelY = guiTop + headerHeight + 5;
        
        // --- Left Panel: Mission Intel ---
        missionPanelX = leftPanelX;
        missionPanelY = panelY;
        missionPanelW = leftPanelWidth;
        missionPanelH = contentHeight;
        renderMissionPanel(graphics, leftPanelX, panelY, leftPanelWidth, contentHeight);
        
        // --- Right Panel: Squad Roster ---
        renderSquadPanel(graphics, rightPanelX, panelY, rightPanelWidth, contentHeight, mouseX, mouseY);

        // Countdown Overlay (relative to window)
        if (countdownActive) {
            renderCountdown(graphics);
        }
    }
    
    private void renderMissionPanel(GuiGraphics graphics, int x, int y, int w, int h) {
        // Glass Background
        graphics.fill(x, y, x + w, y + h, 0x80080A0E); // Semi-transparent dark
        renderRectOutline(graphics, x, y, w, h, COLOR_BORDER);
        
        // Header
        graphics.fill(x, y, x + w, y + 24, 0xFF151B22);
        graphics.fill(x, y + 23, x + w, y + 24, COLOR_NEON_RED);
        graphics.drawString(font, Component.translatable("gui.storyadventure.header.mission_intel"), x + 10, y + 8, COLOR_NEON_RED);
        
        int contentTop = y + 35;
        int contentBottom = y + h - 12;
        int contentY = contentTop - missionScrollOffset;
        int px = x + 15;
        int pw = w - 30;

        // Clip content so briefing can scroll
        graphics.enableScissor(x + 1, contentTop, x + w - 1, contentBottom);
        
        // Title (Big)
        graphics.pose().pushPose();
        graphics.pose().translate(px, contentY, 0);
        graphics.pose().scale(1.1f, 1.1f, 1.0f); // Slightly smaller scale to fit
        graphics.drawString(font, storyName, 0, 0, 0xFFFFFFFF);
        graphics.pose().popPose();
        contentY += 25;
        
        // Meta Data Grid
        drawInfoRow(graphics, px, contentY, Component.translatable("gui.storyadventure.label.difficulty").getString(), 
            Component.translatable("gui.storyadventure.difficulty.normal").getString());
        drawInfoRow(graphics, px + w/2, contentY, Component.translatable("gui.storyadventure.label.est_time").getString(), 
            Component.translatable("gui.storyadventure.admin.stats.time.minutes", estimatedMinutes).getString());
        contentY += 30;
        drawInfoRow(graphics, px, contentY, Component.translatable("gui.storyadventure.label.squad_size").getString(), 
            minPlayers + "-" + maxPlayers + " " + Component.translatable("gui.storyadventure.label.people").getString());
        contentY += 35;
        
        // Divider
        graphics.fill(px, contentY, px + pw, contentY + 1, 0xFF2D3640);
        contentY += 10;
        
        // Description
        graphics.drawString(font, Component.translatable("gui.storyadventure.label.briefing"), px, contentY, COLOR_TEXT_DIM);
        contentY += 15;
        
        List<String> descLines = wrapText(storyDescription, pw);
        for (String line : descLines) {
            graphics.drawString(font, line, px, contentY, COLOR_TEXT_BODY);
            contentY += 12;
        }

        graphics.disableScissor();

        // Update scroll bounds
        int contentHeight = contentY - (contentTop - missionScrollOffset);
        int viewportHeight = contentBottom - contentTop;
        missionScrollMax = Math.max(0, contentHeight - viewportHeight);
        if (missionScrollOffset > missionScrollMax) {
            missionScrollOffset = missionScrollMax;
        }

        // Scrollbar indicator
        if (missionScrollMax > 0) {
            int barHeight = Math.max(18, (int)((float)viewportHeight * viewportHeight / (viewportHeight + missionScrollMax)));
            int barY = contentTop + (int)((float)missionScrollOffset / missionScrollMax * (viewportHeight - barHeight));
            int barX = x + w - 6;
            graphics.fill(barX, contentTop, barX + 2, contentBottom, 0xFF1C252F);
            graphics.fill(barX, barY, barX + 2, barY + barHeight, 0xFF3BB6A6);
        }
    }

    private boolean isInMissionPanel(double mouseX, double mouseY) {
        return mouseX >= missionPanelX && mouseX <= missionPanelX + missionPanelW
            && mouseY >= missionPanelY && mouseY <= missionPanelY + missionPanelH;
    }
    
    private void drawInfoRow(GuiGraphics graphics, int x, int y, String label, String value) {
        graphics.drawString(font, label, x, y, COLOR_TEXT_DIM);
        graphics.drawString(font, value, x, y + 10, COLOR_NEON_PINK);
    }
    
    private void renderSquadPanel(GuiGraphics graphics, int x, int y, int w, int h, int mouseX, int mouseY) {
         // Glass Background
        graphics.fill(x, y, x + w, y + h, 0x80080A0E);
        renderRectOutline(graphics, x, y, w, h, COLOR_BORDER);
        
        // Header
        graphics.fill(x, y, x + w, y + 24, 0xFF151B22);
        long readyCount = members.stream().filter(m -> m.ready).count();
        int headerColor = (readyCount >= minPlayers) ? 0xFF3BB6A6 : 0xFFCF3838; // Teal if ready to start, else Red
        graphics.fill(x, y + 23, x + w, y + 24, headerColor);
        
        graphics.drawString(font, Component.translatable("gui.storyadventure.header.squad_status"), x + 10, y + 8, headerColor);
        String statusText = String.format("%s: %d/%d", Component.translatable("gui.storyadventure.label.ready").getString(), readyCount, members.size());
        graphics.drawString(font, statusText, x + w - font.width(statusText) - 10, y + 8, headerColor);
        
        int listTop = y + 30;
        int listBottom = y + h - 10;
        int listX = x + 10;
        int listW = w - 20;
        int itemH = 40;
        int listY = listTop + 5;

        graphics.enableScissor(listX, listTop, listX + listW, listBottom);

        for (PartyMember member : members) {
            if (listY + itemH > listBottom) {
                break;
            }
            renderSquadMember(graphics, member, listX, listY, listW, itemH);
            listY += itemH + 5;
        }

        for (int i = members.size(); i < maxPlayers; i++) {
            if (listY + itemH > listBottom) {
                break;
            }
            renderEmptySlot(graphics, listX, listY, listW, itemH);
            listY += itemH + 5;
        }

        graphics.disableScissor();
    }
    
    private void renderSquadMember(GuiGraphics graphics, PartyMember member, int x, int y, int w, int h) {
        // Card bg
        int bg = member.ready ? 0x403BB6A6 : 0x40151B22;
        graphics.fill(x, y, x + w, y + h, bg);
        renderRectOutline(graphics, x, y, w, h, member.ready ? 0xFF3BB6A6 : 0xFF2D3640);
        
        // Avatar (Player Head)
        int headSize = h - 10;
        int headX = x + 5;
        int headY = y + 5;
        
        renderPlayerHead(graphics, member.id, headX, headY, headSize);
        renderRectOutline(graphics, headX, headY, headSize, headSize, member.isLeader ? 0xFFFFD700 : 0xFF444444);
        
        // Info
        int textX = x + h + 5;
        graphics.drawString(font, member.name, textX, y + 8, 0xFFFFFFFF);
        
        String role = member.isLeader ? Component.translatable("gui.storyadventure.role.leader").getString() : Component.translatable("gui.storyadventure.role.operative").getString();
        graphics.drawString(font, role, textX, y + 22, member.isLeader ? 0xFFFFD700 : COLOR_TEXT_DIM);
        
        // Status Icon/Text (Right aligned)
        String status = member.ready ? Component.translatable("gui.storyadventure.status.ready").getString() : Component.translatable("gui.storyadventure.status.waiting").getString();
        int statusColor = member.ready ? 0xFF3BB6A6 : 0xFF888888;
        graphics.drawString(font, status, x + w - font.width(status) - 10, y + 16, statusColor);
    }
    
    private void renderPlayerHead(GuiGraphics graphics, UUID playerId, int x, int y, int size) {
        ResourceLocation skinLocation = DefaultPlayerSkin.get(playerId).texture();
        
        if (minecraft.getConnection() != null) {
            PlayerInfo info = minecraft.getConnection().getPlayerInfo(playerId);
            if (info != null) {
                skinLocation = info.getSkin().texture();
            }
        }
        
        RenderSystem.setShaderTexture(0, skinLocation);
        // Draw main face (u=8, v=8, size=8x8)
        graphics.blit(skinLocation, x, y, size, size, 8.0F, 8.0F, 8, 8, 64, 64);
        // Draw hat layer (u=40, v=8, size=8x8) - usually adds nice depth
        graphics.blit(skinLocation, x, y, size, size, 40.0F, 8.0F, 8, 8, 64, 64);
    }
    
    private void renderEmptySlot(GuiGraphics graphics, int x, int y, int w, int h) {
        // Dashed border logic is expensive to draw manually every frame, simpler: low alpha solid
        graphics.fill(x, y, x + w, y + h, 0x20000000);
        renderRectOutline(graphics, x, y, w, h, 0xFF2D3640);
        
        graphics.drawCenteredString(font, Component.translatable("gui.storyadventure.empty_slot"), x + w/2, y + h/2 - 4, 0xFF444444);
    }

    private void renderCountdown(GuiGraphics graphics) {
        if (countdownSecondsLeft < 0) return;
        
        long elapsedSinceLastSync = System.currentTimeMillis() - countdownStartTime;
        // We pulse based on time, but use the seconds from server
        
        // Dark overlay
        graphics.fill(0, 0, width, height, 0xE0000000); // Darker
        
        // Countdown number
        String countText = String.valueOf(countdownSecondsLeft);
        
        // Pulsing effect
        float pulse = (float)(0.8 + 0.2 * Math.sin(System.currentTimeMillis() / 100.0));
        int alpha = (int)(255 * pulse);
        int color = (alpha << 24) | (0xFF0000); // Pure Red for danger/urgency
        
        // Big Scaling
        graphics.pose().pushPose();
        graphics.pose().translate(width / 2f, height / 2f - 40, 0);
        graphics.pose().scale(6.0f, 6.0f, 1.0f);
        graphics.drawCenteredString(font, countText, 0, 0, color);
        graphics.pose().popPose();
        
        graphics.drawCenteredString(font, Component.translatable("gui.storyadventure.text.deploying", storyName), width / 2, height / 2 + 30, 0xFFFFFFFF);
    }
    
    private void drawPanelBorder(GuiGraphics graphics, int x, int y, int w, int h) {
        renderRectOutline(graphics, x, y, w, h, COLOR_BORDER);
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
    
    public void startCountdown(int seconds) {
        this.countdownActive = seconds >= 0;
        this.countdownSecondsLeft = seconds;
        this.countdownStartTime = System.currentTimeMillis();
    }
    
    public record PartyMember(UUID id, String name, boolean ready, boolean isLeader) {}
}
