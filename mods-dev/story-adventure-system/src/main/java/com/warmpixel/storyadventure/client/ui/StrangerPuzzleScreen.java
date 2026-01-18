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

    private static final int CODE_BUTTON_SIZE = 40;
    private static final int CODE_BUTTON_GAP = 12;
    private static final int CODE_DISPLAY_WIDTH = 200;
    private static final int CODE_DISPLAY_HEIGHT = 50;
    private static final int CODE_DISPLAY_GAP = 16;
    
    // Code lock state
    private StringBuilder codeInput = new StringBuilder();
    private int maxCodeLength = 4;

    private int displayX;
    private int displayY;
    private int keypadStartX;
    private int keypadStartY;
    private int gridWidth;
    private int gridHeight;
    
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
            computeCodeLockLayout();
            initCodeLockButtons();
        }
    }
    
    private void computeCodeLockLayout() {
        gridWidth = 3 * CODE_BUTTON_SIZE + 2 * CODE_BUTTON_GAP;
        gridHeight = 4 * CODE_BUTTON_SIZE + 3 * CODE_BUTTON_GAP;
        int totalHeight = CODE_DISPLAY_HEIGHT + CODE_DISPLAY_GAP + gridHeight;
        int topY = (height - totalHeight) / 2;
        int centerX = width / 2;
        displayX = centerX - CODE_DISPLAY_WIDTH / 2;
        displayY = topY;
        keypadStartX = centerX - gridWidth / 2;
        keypadStartY = displayY + CODE_DISPLAY_HEIGHT + CODE_DISPLAY_GAP;
    }

    private void initCodeLockButtons() {
        // Number pad 1-9
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int num = row * 3 + col + 1;
                int x = keypadStartX + col * (CODE_BUTTON_SIZE + CODE_BUTTON_GAP);
                int y = keypadStartY + row * (CODE_BUTTON_SIZE + CODE_BUTTON_GAP);
                
                final int digit = num;
                addStrangerButton(x, y, CODE_BUTTON_SIZE, CODE_BUTTON_SIZE, 
                    Component.literal(String.valueOf(num)),
                    () -> appendDigit(digit));
            }
        }
        
        // 0 button (centered in last row)
        int x = keypadStartX + (CODE_BUTTON_SIZE + CODE_BUTTON_GAP);
        int y = keypadStartY + 3 * (CODE_BUTTON_SIZE + CODE_BUTTON_GAP);
        addStrangerButton(x, y, CODE_BUTTON_SIZE, CODE_BUTTON_SIZE, 
            Component.literal("0"), () -> appendDigit(0));
        
        // Clear and Submit buttons
        addStrangerButton(keypadStartX, y, CODE_BUTTON_SIZE, CODE_BUTTON_SIZE, 
            Component.literal("×"), this::clearCode).setGlowPulse(false); // No pulse for clear
        
        addStrangerButton(keypadStartX + 2 * (CODE_BUTTON_SIZE + CODE_BUTTON_GAP), y, CODE_BUTTON_SIZE, CODE_BUTTON_SIZE, 
            Component.literal("✓"), this::submitCode).setGlowPulse(true); // Pulse for submit
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
        graphics.drawString(font, titleText, (width - titleWidth) / 2, 35, COLOR_TEXT_TITLE);
        
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
        int displayWidth = CODE_DISPLAY_WIDTH;
        int displayHeight = CODE_DISPLAY_HEIGHT;
        
        // LCD-style Background
        int lcdBgColor = 0xFF05080A; 
        graphics.fill(displayX, displayY, displayX + displayWidth, displayY + displayHeight, lcdBgColor);
        
        // Border with simple shine
        graphics.fill(displayX - 1, displayY - 1, displayX + displayWidth + 1, displayY + displayHeight + 1, COLOR_BORDER);
        
        // Inner Glow / Screen effect
        graphics.fill(displayX, displayY, displayX + displayWidth, displayY + 2, 0x403BB6A6); // Top subtle glow
        
        // Display Digits
        int digitWidth = 20;
        int spacing = 15;
        int totalDigitsWidth = maxCodeLength * digitWidth + (maxCodeLength - 1) * spacing;
        int startDigitsX = displayX + (displayWidth - totalDigitsWidth) / 2;
        int digitY = displayY + (displayHeight - 20) / 2;
        
        for (int i = 0; i < maxCodeLength; i++) {
            int digitX = startDigitsX + i * (digitWidth + spacing);
            
            // Draw placeholder/slot background
            graphics.fill(digitX, displayY + displayHeight - 10, digitX + digitWidth, displayY + displayHeight - 8, 0xFF1D252E);
            
            if (i < codeInput.length()) {
                // Draw actual digit or masked char
                String charStr = String.valueOf(codeInput.charAt(i));
                // For secure look, maybe use asterisk, but typically simple puzzles show numbers. 
                // Let's use numbers as it's friendlier, or maybe a filled square style.
                // The original code used dots/circles.
                
                // Let's draw the big number
                graphics.drawCenteredString(font, charStr, digitX + digitWidth / 2, digitY, COLOR_NEON_RED);
            } else {
                // Empty slot indicator
                graphics.drawCenteredString(font, "_", digitX + digitWidth / 2, digitY, COLOR_TEXT_DIM);
            }
        }
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
