package com.warmpixel.storyadventure.client.ui.admin;

import com.warmpixel.storyadventure.client.ui.StrangerButton;
import com.warmpixel.storyadventure.client.ui.StrangerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Admin UI for managing players across all active story instances.
 */
public class AdminPlayerManagerScreen extends StrangerScreen {
    
    private static final int PLAYER_ENTRY_HEIGHT = 40;
    
    private final List<PlayerInfo> players = new ArrayList<>();
    private int selectedIndex = -1;
    private int scrollOffset = 0;
    
    public AdminPlayerManagerScreen() {
        super(Component.translatable("gui.storyadventure.admin.players.title"));
    }
    
    public void addPlayer(UUID uuid, String name, String instanceName, String currentNode, boolean isLeader) {
        players.add(new PlayerInfo(uuid, name, instanceName, currentNode, isLeader));
    }
    
    public void clearPlayers() {
        players.clear();
        selectedIndex = -1;
    }
    
    @Override
    protected void init() {
        super.init();
        
        int buttonWidth = 140;
        int buttonHeight = 24;
        int rightX = width - 170;
        int y = 80;
        
        // Teleport to player
        addStrangerButton(rightX, y, buttonWidth, buttonHeight,
            Component.translatable("gui.storyadventure.admin.players.tp"), this::teleportToPlayer);
        y += buttonHeight + 8;
        
        // Kick from instance
        addStrangerButton(rightX, y, buttonWidth, buttonHeight,
            Component.translatable("gui.storyadventure.admin.players.kick"), this::kickPlayer);
        y += buttonHeight + 8;
        
        // Send message
        addStrangerButton(rightX, y, buttonWidth, buttonHeight,
            Component.translatable("gui.storyadventure.admin.players.message"), this::sendMessageToPlayer);
        y += buttonHeight + 8;
        
        // View player details
        addStrangerButton(rightX, y, buttonWidth, buttonHeight,
            Component.translatable("gui.storyadventure.admin.players.details"), this::viewPlayerDetails);
        y += buttonHeight + 20;
        
        // Refresh button
        addStrangerButton(rightX, y, buttonWidth, buttonHeight,
            Component.translatable("gui.storyadventure.admin.players.refresh"), this::refreshList);
        
        // Back button
        addStrangerButton(30, height - 45, 100, 28,
            Component.translatable("gui.storyadventure.admin.players.back"), this::goBack);
        
        // Close button
        addStrangerButton(width / 2 - 60, height - 45, 120, 28,
            Component.translatable("gui.storyadventure.admin.players.close"), this::onClose);
        
        // Request player data
        refreshList();
    }
    
    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Player list panel
        int listX = 30;
        int listY = 50;
        int listWidth = width - 230;
        int listHeight = height - 110;
        
        graphics.fill(listX, listY, listX + listWidth, listY + listHeight, 0xE0080808);
        drawPanelBorder(graphics, listX, listY, listWidth, listHeight);
        
        // Headers
        graphics.drawString(font, Component.translatable("gui.storyadventure.admin.players.col.player"), listX + 10, listY + 8, COLOR_NEON_RED);
        graphics.drawString(font, Component.translatable("gui.storyadventure.admin.players.col.instance"), listX + 150, listY + 8, COLOR_NEON_RED);
        graphics.drawString(font, Component.translatable("gui.storyadventure.admin.players.col.node"), listX + 320, listY + 8, COLOR_NEON_RED);
        graphics.drawString(font, Component.translatable("gui.storyadventure.admin.players.col.role"), listX + 450, listY + 8, COLOR_NEON_RED);
        
        graphics.fill(listX + 5, listY + 22, listX + listWidth - 5, listY + 23, COLOR_BORDER);
        
        // Player entries
        int y = listY + 28;
        int visibleCount = (listHeight - 30) / PLAYER_ENTRY_HEIGHT;
        int visibleEnd = Math.min(scrollOffset + visibleCount, players.size());
        
        for (int i = scrollOffset; i < visibleEnd; i++) {
            renderPlayerEntry(graphics, players.get(i), listX + 5, y, listWidth - 10, 
                i == selectedIndex, mouseX, mouseY);
            y += PLAYER_ENTRY_HEIGHT;
        }
        
        if (players.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("gui.storyadventure.admin.players.empty"), listX + listWidth / 2, listY + listHeight / 2, COLOR_TEXT_DIM);
        }
        
        // Sidebar details
        renderSidebar(graphics);
        
        // Stats bar
        renderStatsBar(graphics);
    }
    
    private void renderPlayerEntry(GuiGraphics graphics, PlayerInfo info, 
                                    int x, int y, int width, boolean selected, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + PLAYER_ENTRY_HEIGHT - 2;
        
        int bgColor = selected ? 0xFF1A0808 : (hovered ? 0xFF100505 : 0xFF0A0A0A);
        graphics.fill(x, y, x + width, y + PLAYER_ENTRY_HEIGHT - 2, bgColor);
        
        // Status indicator
        int statusColor = info.isLeader ? COLOR_NEON_RED : 0xFF44FF44;
        graphics.fill(x, y, x + 3, y + PLAYER_ENTRY_HEIGHT - 2, statusColor);
        
        // Player name
        graphics.drawString(font, info.name, x + 10, y + 10, COLOR_TEXT_BODY);
        
        // Instance name
        String instanceDisplay = info.instanceName.isEmpty() ? "-" : truncate(info.instanceName, 18);
        graphics.drawString(font, instanceDisplay, x + 150, y + 10, COLOR_TEXT_BODY);
        
        // Current node
        String nodeDisplay = info.currentNode.isEmpty() ? "-" : truncate(info.currentNode, 16);
        graphics.drawString(font, nodeDisplay, x + 320, y + 10, COLOR_TEXT_DIM);
        
        // Role
        String roleText = info.isLeader ? Component.translatable("gui.storyadventure.admin.players.role.leader").getString() 
                                      : Component.translatable("gui.storyadventure.admin.players.role.member").getString();
        graphics.drawString(font, roleText, x + 450, y + 10, info.isLeader ? COLOR_NEON_RED : COLOR_TEXT_DIM);
    }
    
    private void renderSidebar(GuiGraphics graphics) {
        int sidebarX = width - 170;
        int sidebarY = 50;
        int sidebarWidth = 150;
        
        graphics.fill(sidebarX, sidebarY, sidebarX + sidebarWidth, sidebarY + 25, 0xE0080808);
        drawPanelBorder(graphics, sidebarX, sidebarY, sidebarWidth, 25);
        
        graphics.drawCenteredString(font, Component.translatable("gui.storyadventure.admin.instances.actions"), sidebarX + sidebarWidth / 2, sidebarY + 8, COLOR_NEON_RED);
        
        // Selected player info
        if (selectedIndex >= 0 && selectedIndex < players.size()) {
            PlayerInfo info = players.get(selectedIndex);
            int infoY = 260;
            
            graphics.fill(sidebarX, infoY, sidebarX + sidebarWidth, infoY + 80, 0xE0080808);
            drawPanelBorder(graphics, sidebarX, infoY, sidebarWidth, 80);
            
            graphics.drawString(font, Component.translatable("gui.storyadventure.admin.instances.selected"), sidebarX + 5, infoY + 8, COLOR_TEXT_DIM);
            graphics.drawString(font, info.name, sidebarX + 5, infoY + 22, COLOR_TEXT_BODY);
            graphics.drawString(font, "UUID:", sidebarX + 5, infoY + 40, COLOR_TEXT_DIM);
            String shortUuid = info.uuid.toString().substring(0, 8);
            graphics.drawString(font, shortUuid, sidebarX + 5, infoY + 54, COLOR_TEXT_DIM);
        }
    }
    
    private void renderStatsBar(GuiGraphics graphics) {
        int barY = height - 70;
        
        graphics.fill(30, barY, width - 30, barY + 20, 0xC0080808);
        
        int inInstanceCount = (int) players.stream().filter(p -> !p.instanceName.isEmpty()).count();
        Component stats = Component.translatable("gui.storyadventure.admin.players.stats",
            players.size(),
            inInstanceCount,
            players.stream().filter(p -> p.isLeader).count());
        
        graphics.drawString(font, stats, 40, barY + 6, COLOR_TEXT_DIM);
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
    
    private String truncate(String text, int maxLen) {
        return text.length() > maxLen ? text.substring(0, maxLen - 2) + ".." : text;
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int listX = 30;
        int listY = 50;
        int listWidth = width - 230;
        int listHeight = height - 110;
        
        if (mouseX >= listX && mouseX < listX + listWidth && 
            mouseY >= listY + 28 && mouseY < listY + listHeight) {
            int relY = (int)mouseY - listY - 28;
            int clickedIndex = scrollOffset + relY / PLAYER_ENTRY_HEIGHT;
            if (clickedIndex < players.size()) {
                selectedIndex = clickedIndex;
                return true;
            }
        }
        
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double hAmount, double vAmount) {
        int visibleCount = (height - 110 - 30) / PLAYER_ENTRY_HEIGHT;
        if (vAmount > 0 && scrollOffset > 0) {
            scrollOffset--;
            return true;
        } else if (vAmount < 0 && scrollOffset + visibleCount < players.size()) {
            scrollOffset++;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, hAmount, vAmount);
    }
    
    private void sendCommand(String command) {
        var mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.connection.sendCommand(command.startsWith("/") ? command.substring(1) : command);
        }
    }
    
    private void showMessage(String message) {
        var mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal(message));
        }
    }
    
    private void teleportToPlayer() {
        if (selectedIndex >= 0 && selectedIndex < players.size()) {
            PlayerInfo info = players.get(selectedIndex);
            sendCommand("tp @s " + info.name);
            showMessage(Component.translatable("command.storyadventure.admin.players.tping", info.name).getString());
            onClose();
        } else {
            showMessage(Component.translatable("command.storyadventure.admin.stories.select_first").getString());
        }
    }
    
    private void kickPlayer() {
        if (selectedIndex >= 0 && selectedIndex < players.size()) {
            PlayerInfo info = players.get(selectedIndex);
            sendCommand("storyadmin kick " + info.name);
            showMessage(Component.translatable("command.storyadventure.admin.players.kicking", info.name).getString());
        } else {
            showMessage(Component.translatable("command.storyadventure.admin.stories.select_first").getString());
        }
    }
    
    private void sendMessageToPlayer() {
        if (selectedIndex >= 0 && selectedIndex < players.size()) {
            PlayerInfo info = players.get(selectedIndex);
            showMessage(Component.translatable("command.storyadventure.admin.players.msg_hint", info.name).getString());
        } else {
            showMessage(Component.translatable("command.storyadventure.admin.stories.select_first").getString());
        }
    }
    
    private void viewPlayerDetails() {
        if (selectedIndex >= 0 && selectedIndex < players.size()) {
            PlayerInfo info = players.get(selectedIndex);
            showMessage(Component.translatable("gui.storyadventure.admin.players.details_title").getString());
            showMessage(Component.translatable("gui.storyadventure.admin.players.details_name", info.name).getString());
            showMessage(Component.translatable("gui.storyadventure.admin.players.details_uuid", info.uuid).getString());
            showMessage(Component.translatable("gui.storyadventure.admin.players.details_instance", (info.instanceName.isEmpty() ? Component.translatable("gui.storyadventure.admin.players.none").getString() : info.instanceName)).getString());
            showMessage(Component.translatable("gui.storyadventure.admin.players.details_node", (info.currentNode.isEmpty() ? Component.translatable("gui.storyadventure.admin.players.none").getString() : info.currentNode)).getString());
            showMessage(Component.translatable("gui.storyadventure.admin.players.details_role", (info.isLeader ? Component.translatable("gui.storyadventure.admin.players.role.leader").getString() : Component.translatable("gui.storyadventure.admin.players.role.member").getString())).getString());
        } else {
            showMessage(Component.translatable("command.storyadventure.admin.stories.select_first").getString());
        }
    }
    
    private void refreshList() {
        sendCommand("storyadmin players");
        showMessage(Component.translatable("command.storyadventure.admin.players.refreshing").getString());
    }
    
    private void goBack() {
        Minecraft.getInstance().setScreen(new AdminDashboardScreen());
    }
    
    public record PlayerInfo(UUID uuid, String name, String instanceName, String currentNode, boolean isLeader) {}
}
