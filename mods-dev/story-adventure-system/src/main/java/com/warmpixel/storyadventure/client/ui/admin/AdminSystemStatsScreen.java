package com.warmpixel.storyadventure.client.ui.admin;

import com.warmpixel.storyadventure.client.ui.StrangerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Admin UI showing system statistics and server health.
 */
public class AdminSystemStatsScreen extends StrangerScreen {
    
    private int activeInstances = 0;
    private int totalPlayers = 0;
    private int loadedStories = 0;
    private double serverTps = 20.0;
    private long serverMemoryUsed = 0;
    private long serverMemoryMax = 0;
    
    private final List<ActivityEntry> recentActivity = new ArrayList<>();
    private long lastUpdateTime = 0;
    
    public AdminSystemStatsScreen() {
        super(Component.translatable("gui.storyadventure.admin.stats.title"));
    }
    
    public void setStats(int activeInstances, int totalPlayers, int loadedStories, 
                         double serverTps, long memoryUsed, long memoryMax) {
        this.activeInstances = activeInstances;
        this.totalPlayers = totalPlayers;
        this.loadedStories = loadedStories;
        this.serverTps = serverTps;
        this.serverMemoryUsed = memoryUsed;
        this.serverMemoryMax = memoryMax;
    }
    
    public void addActivity(String message, long timestamp) {
        recentActivity.add(0, new ActivityEntry(message, timestamp));
        if (recentActivity.size() > 20) {
            recentActivity.remove(recentActivity.size() - 1);
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
        
        // Refresh button
        addStrangerButton(guiLeft + guiWidth - 135, guiTop + 15, 120, 24,
            Component.translatable("gui.storyadventure.admin.stats.refresh"), this::refreshStats);
        
        // Back button
        addStrangerButton(guiLeft + 15, guiTop + guiHeight - 35, 100, 24,
            Component.translatable("gui.storyadventure.admin.stats.back"), this::goBack);
        
        // Close button
        addStrangerButton(guiLeft + guiWidth / 2 - 60, guiTop + guiHeight - 35, 120, 24,
            Component.translatable("gui.storyadventure.admin.stats.close"), this::onClose);
        
        // Request initial stats
        refreshStats();
    }
    
    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int panelWidth = (guiWidth - 80) / 3;
        int panelHeight = (guiHeight - 160) / 2;
        int gap = 15;
        int startX = guiLeft + 20;
        int startY = guiTop + 50;
        
        // === Instance Stats Panel ===
        renderStatPanel(graphics, startX, startY, panelWidth, panelHeight, 
            Component.translatable("gui.storyadventure.admin.stats.panel.instances").getString(), 
            new String[]{
            Component.translatable("gui.storyadventure.admin.stats.active_instances", activeInstances).getString(),
            Component.translatable("gui.storyadventure.admin.stats.waiting_teams").getString(),
            Component.translatable("gui.storyadventure.admin.stats.completed_today").getString(),
            Component.translatable("gui.storyadventure.admin.stats.failed_today").getString()
        }, new int[]{
            activeInstances > 0 ? 0xFF44FF44 : COLOR_TEXT_BODY,
            COLOR_TEXT_BODY,
            COLOR_TEXT_BODY,
            COLOR_TEXT_DIM
        });
        
        // === Player Stats Panel ===
        renderStatPanel(graphics, startX + panelWidth + gap, startY, panelWidth, panelHeight, 
            Component.translatable("gui.storyadventure.admin.stats.panel.players").getString(), 
            new String[]{
            Component.translatable("gui.storyadventure.admin.stats.players_in_adventure", totalPlayers).getString(),
            Component.translatable("gui.storyadventure.admin.stats.total_online", (Minecraft.getInstance().getConnection() != null ? 
                Minecraft.getInstance().getConnection().getOnlinePlayers().size() : 0)).getString(),
            Component.translatable("gui.storyadventure.admin.stats.waiting_players").getString()
        }, new int[]{
            totalPlayers > 0 ? COLOR_NEON_RED : COLOR_TEXT_BODY,
            COLOR_TEXT_BODY,
            COLOR_TEXT_DIM
        });
        
        // === Server Health Panel ===
        int healthColor = serverTps >= 19 ? 0xFF44FF44 : (serverTps >= 15 ? 0xFFFFCC00 : 0xFFFF4444);
        String tpsStr = Component.translatable("gui.storyadventure.admin.stats.tps", serverTps).getString();
        long memMB = serverMemoryUsed / (1024 * 1024);
        long maxMB = serverMemoryMax / (1024 * 1024);
        String memStr = Component.translatable("gui.storyadventure.admin.stats.memory", memMB, maxMB).getString();
        String statusNormal = Component.translatable("gui.storyadventure.admin.stats.status", Component.translatable("gui.storyadventure.admin.stats.status.normal").getString()).getString();
        
        renderStatPanel(graphics, startX, startY + panelHeight + gap, panelWidth, panelHeight, 
            Component.translatable("gui.storyadventure.admin.stats.panel.health").getString(), 
            new String[]{
            tpsStr,
            memStr,
            statusNormal
        }, new int[]{
            healthColor,
            COLOR_TEXT_BODY,
            0xFF44FF44
        });
        
        // === Story Stats Panel ===
        renderStatPanel(graphics, startX + panelWidth + gap, startY + panelHeight + gap, panelWidth, panelHeight, 
            Component.translatable("gui.storyadventure.admin.stats.panel.stories").getString(), 
            new String[]{
            Component.translatable("gui.storyadventure.admin.stats.loaded_stories", loadedStories).getString(),
            Component.translatable("gui.storyadventure.admin.stats.valid_stories", loadedStories).getString(),
            Component.translatable("gui.storyadventure.admin.stats.error_stories").getString()
        }, new int[]{
            COLOR_TEXT_BODY,
            0xFF44FF44,
            COLOR_TEXT_DIM
        });
        
        // === Recent Activity Panel ===
        int activityX = startX + (panelWidth + gap) * 2;
        int activityWidth = guiLeft + guiWidth - activityX - 20;
        int activityHeight = guiHeight - 120;
        
        graphics.fill(activityX, startY, activityX + activityWidth, startY + activityHeight, 0xE0080808);
        drawPanelBorder(graphics, activityX, startY, activityWidth, activityHeight);
        
        graphics.drawString(font, Component.translatable("gui.storyadventure.admin.stats.panel.activity"), activityX + 10, startY + 8, COLOR_NEON_RED);
        graphics.fill(activityX + 5, startY + 22, activityX + activityWidth - 5, startY + 23, COLOR_BORDER);
        
        int actY = startY + 30;
        if (recentActivity.isEmpty()) {
            graphics.drawString(font, Component.translatable("gui.storyadventure.admin.stats.no_activity"), activityX + 10, actY, COLOR_TEXT_DIM);
        } else {
            for (int i = 0; i < Math.min(recentActivity.size(), 15); i++) {
                ActivityEntry entry = recentActivity.get(i);
                String timeAgo = formatTimeAgo(entry.timestamp);
                graphics.drawString(font, "§7" + timeAgo + " §f" + entry.message, activityX + 10, actY, COLOR_TEXT_BODY);
                actY += 14;
            }
        }
        
        // Progress bars for memory
        int barX = startX + 10;
        int barY = startY + panelHeight + gap + panelHeight - 25;
        int barWidth = panelWidth - 20;
        int barHeight = 8;
        
        // Memory bar background
        graphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF333333);
        // Memory bar fill
        double memPercent = serverMemoryMax > 0 ? (double) serverMemoryUsed / serverMemoryMax : 0;
        int fillWidth = (int)(barWidth * memPercent);
        int memBarColor = memPercent < 0.7 ? 0xFF44FF44 : (memPercent < 0.9 ? 0xFFFFCC00 : 0xFFFF4444);
        graphics.fill(barX, barY, barX + fillWidth, barY + barHeight, memBarColor);
        
        // Last update time
        if (lastUpdateTime > 0) {
            String updateStr = Component.translatable("gui.storyadventure.admin.stats.last_update", formatTimeAgo(lastUpdateTime)).getString();
            graphics.drawString(font, updateStr, guiLeft + 20, guiTop + guiHeight - 65, COLOR_TEXT_DIM);
        }
    }
    
    private void renderStatPanel(GuiGraphics graphics, int x, int y, int w, int h, 
                                  String title, String[] lines, int[] colors) {
        graphics.fill(x, y, x + w, y + h, 0xE0080808);
        drawPanelBorder(graphics, x, y, w, h);
        
        graphics.drawString(font, title, x + 10, y + 8, COLOR_NEON_RED);
        graphics.fill(x + 5, y + 22, x + w - 5, y + 23, COLOR_BORDER);
        
        int lineY = y + 32;
        for (int i = 0; i < lines.length; i++) {
            int color = i < colors.length ? colors[i] : COLOR_TEXT_BODY;
            graphics.drawString(font, lines[i], x + 10, lineY, color);
            lineY += 16;
        }
    }
    
    private void drawPanelBorder(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + 1, COLOR_BORDER);
        graphics.fill(x, y + h - 1, x + w, y + h, COLOR_BORDER);
        graphics.fill(x, y, x + 1, y + h, COLOR_BORDER);
        graphics.fill(x + w - 1, y, x + w, y + h, COLOR_BORDER);
        
        int cs = 6;
        graphics.fill(x, y, x + cs, y + 2, COLOR_NEON_RED);
        graphics.fill(x, y, x + 2, y + cs, COLOR_NEON_RED);
        graphics.fill(x + w - cs, y, x + w, y + 2, COLOR_NEON_RED);
        graphics.fill(x + w - 2, y, x + w, y + cs, COLOR_NEON_RED);
    }
    
    private String formatTimeAgo(long timestamp) {
        long diff = System.currentTimeMillis() - timestamp;
        long seconds = diff / 1000;
        if (seconds < 60) return Component.translatable("gui.storyadventure.admin.stats.time.seconds", seconds).getString();
        long minutes = seconds / 60;
        if (minutes < 60) return Component.translatable("gui.storyadventure.admin.stats.time.minutes", minutes).getString();
        long hours = minutes / 60;
        return Component.translatable("gui.storyadventure.admin.stats.time.hours", hours).getString();
    }
    
    private void refreshStats() {
        lastUpdateTime = System.currentTimeMillis();
        
        // Get local stats from client cache
        var instances = com.warmpixel.storyadventure.network.ClientNetworkHandler.getLastSyncedInstances();
        activeInstances = instances.size();
        totalPlayers = instances.stream().mapToInt(i -> i.playerCount()).sum();
        
        // Memory stats (client-side only)
        Runtime runtime = Runtime.getRuntime();
        serverMemoryUsed = runtime.totalMemory() - runtime.freeMemory();
        serverMemoryMax = runtime.maxMemory();
        serverTps = 20.0; // Placeholder - would need server sync
        
        loadedStories = 2; // Placeholder
        
        var mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.connection.sendCommand("storyadmin stats");
            mc.player.sendSystemMessage(Component.translatable("command.storyadventure.admin.stats.refreshing"));
        }
    }
    
    private void goBack() {
        Minecraft.getInstance().setScreen(new AdminDashboardScreen());
    }
    
    public record ActivityEntry(String message, long timestamp) {}
}
