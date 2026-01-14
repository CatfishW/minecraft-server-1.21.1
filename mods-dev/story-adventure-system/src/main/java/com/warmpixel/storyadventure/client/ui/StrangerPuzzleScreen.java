package com.warmpixel.storyadventure.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Stranger Things themed puzzle interaction screen.
 * Used for code locks, wiring puzzles, symbol matching, etc.
 */
public class StrangerPuzzleScreen extends StrangerScreen {
    
    private final String puzzleType;
    private final String hint;
    private final int maxAttempts;
    private int currentAttempts = 0;
    
    // Code lock state
    private StringBuilder codeInput = new StringBuilder();
    private int maxCodeLength = 4;
    
    public StrangerPuzzleScreen(String puzzleType, String hint, int maxAttempts) {
        super(Component.literal("解谜"));
        this.puzzleType = puzzleType;
        this.hint = hint;
        this.maxAttempts = maxAttempts;
    }
    
    @Override
    protected void init() {
        super.init();
        
        if ("CODE_LOCK".equals(puzzleType)) {
            initCodeLockButtons();
        }
    }
    
    private void initCodeLockButtons() {
        int buttonSize = 40;
        int gap = 8;
        int startX = (width - (3 * buttonSize + 2 * gap)) / 2;
        int startY = height / 2 - 20;
        
        // Number pad 1-9
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int num = row * 3 + col + 1;
                int x = startX + col * (buttonSize + gap);
                int y = startY + row * (buttonSize + gap);
                
                final int digit = num;
                addStrangerButton(x, y, buttonSize, buttonSize, 
                    Component.literal(String.valueOf(num)),
                    () -> appendDigit(digit));
            }
        }
        
        // 0 button
        int x = startX + (buttonSize + gap);
        int y = startY + 3 * (buttonSize + gap);
        addStrangerButton(x, y, buttonSize, buttonSize, 
            Component.literal("0"), () -> appendDigit(0));
        
        // Clear and Submit buttons
        addStrangerButton(startX, y, buttonSize, buttonSize, 
            Component.literal("×"), this::clearCode);
        addStrangerButton(startX + 2 * (buttonSize + gap), y, buttonSize, buttonSize, 
            Component.literal("✓"), this::submitCode);
    }
    
    private void appendDigit(int digit) {
        if (codeInput.length() < maxCodeLength) {
            codeInput.append(digit);
        }
    }
    
    private void clearCode() {
        codeInput.setLength(0);
    }
    
    private void submitCode() {
        currentAttempts++;
        String input = codeInput.toString();
        
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
            new com.warmpixel.storyadventure.network.PuzzleInputPayload(input)
        );
        
        codeInput.setLength(0);
    }
    
    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Draw puzzle title
        String titleText = getPuzzleTitle();
        int titleWidth = font.width(titleText);
        graphics.drawString(font, titleText, (width - titleWidth) / 2, 45, COLOR_TEXT_BODY);
        
        // Draw code display
        if ("CODE_LOCK".equals(puzzleType)) {
            renderCodeDisplay(graphics);
        }
        
        // Draw hint
        if (!hint.isEmpty()) {
            renderHint(graphics);
        }
        
        // Draw attempt counter
        String attemptsText = String.format("尝试次数: %d / %d", currentAttempts, maxAttempts);
        int attemptsColor = currentAttempts >= maxAttempts - 1 ? 0xFFFF4444 : COLOR_TEXT_DIM;
        graphics.drawString(font, attemptsText, width - font.width(attemptsText) - 20, height - 30, attemptsColor);
    }
    
    private void renderCodeDisplay(GuiGraphics graphics) {
        int displayWidth = 150;
        int displayHeight = 36;
        int displayX = (width - displayWidth) / 2;
        int displayY = height / 2 - 80;
        
        // Background
        graphics.fill(displayX, displayY, displayX + displayWidth, displayY + displayHeight, 0xFF0A0A0A);
        
        // Border
        graphics.fill(displayX, displayY, displayX + displayWidth, displayY + 1, COLOR_NEON_RED);
        graphics.fill(displayX, displayY + displayHeight - 1, displayX + displayWidth, displayY + displayHeight, COLOR_NEON_RED);
        graphics.fill(displayX, displayY, displayX + 1, displayY + displayHeight, COLOR_NEON_RED);
        graphics.fill(displayX + displayWidth - 1, displayY, displayX + displayWidth, displayY + displayHeight, COLOR_NEON_RED);
        
        // Code display with asterisks
        StringBuilder display = new StringBuilder();
        for (int i = 0; i < maxCodeLength; i++) {
            if (i < codeInput.length()) {
                display.append("● ");
            } else {
                display.append("○ ");
            }
        }
        
        String displayText = display.toString().trim();
        int textWidth = font.width(displayText);
        graphics.drawString(font, displayText, displayX + (displayWidth - textWidth) / 2, displayY + 14, COLOR_NEON_RED);
    }
    
    private void renderHint(GuiGraphics graphics) {
        int hintY = height - 60;
        String hintLabel = "💡 提示: ";
        graphics.drawString(font, hintLabel + hint, 20, hintY, COLOR_TEXT_DIM);
    }
    
    private String getPuzzleTitle() {
        return switch (puzzleType) {
            case "CODE_LOCK" -> "密码锁";
            case "WIRING" -> "接线谜题";
            case "SYMBOL_MATCH" -> "符号匹配";
            default -> "解谜";
        };
    }
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Number keys 0-9
        if (keyCode >= 48 && keyCode <= 57) {
            appendDigit(keyCode - 48);
            return true;
        }
        // Numpad 0-9
        if (keyCode >= 320 && keyCode <= 329) {
            appendDigit(keyCode - 320);
            return true;
        }
        // Backspace
        if (keyCode == 259 && codeInput.length() > 0) {
            codeInput.deleteCharAt(codeInput.length() - 1);
            return true;
        }
        // Enter
        if (keyCode == 257) {
            submitCode();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
