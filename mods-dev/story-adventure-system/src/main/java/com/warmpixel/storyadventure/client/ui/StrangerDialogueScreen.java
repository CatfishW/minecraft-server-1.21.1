package com.warmpixel.storyadventure.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Stranger Things themed dialogue screen for NPC conversations.
 * Features typewriter text effect, neon accents, and custom choice buttons.
 */
public class StrangerDialogueScreen extends StrangerScreen {
    
    private static final int PANEL_MARGIN = 40;
    private static final int DIALOGUE_BOX_HEIGHT = 120;
    private static final int CHOICE_BUTTON_HEIGHT = 24;
    private static final int CHOICE_BUTTON_MARGIN = 4;
    
    private String npcName;
    private String dialogueText;
    private List<DialogueChoice> choices = new ArrayList<>();
    
    // Typewriter effect
    private int displayedCharacters = 0;
    private long lastCharTime = 0;
    private static final long CHAR_DELAY_MS = 30;
    private boolean textComplete = false;
    
    public StrangerDialogueScreen(String npcName, String dialogueText) {
        super(Component.literal(npcName));
        this.npcName = npcName;
        this.dialogueText = dialogueText;
    }
    
    public void addChoice(String choiceId, String choiceText, Runnable onSelect) {
        choices.add(new DialogueChoice(choiceId, choiceText, () -> {
            // Send network packet
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                new com.warmpixel.storyadventure.network.DialogueChoicePayload(choiceId)
            );
            // Run custom callback (e.g. close screen)
            if (onSelect != null) onSelect.run();
        }));
    }
    
    @Override
    protected void init() {
        super.init();
        
        // Initialize choice buttons (hidden until text complete)
        int choiceY = height - PANEL_MARGIN - DIALOGUE_BOX_HEIGHT + 50;
        int choiceWidth = width - PANEL_MARGIN * 4;
        
        for (int i = 0; i < choices.size(); i++) {
            DialogueChoice choice = choices.get(i);
            int y = choiceY + i * (CHOICE_BUTTON_HEIGHT + CHOICE_BUTTON_MARGIN);
            
            StrangerButton button = addStrangerButton(
                width / 2 - choiceWidth / 2,
                y,
                choiceWidth,
                CHOICE_BUTTON_HEIGHT,
                Component.literal(choice.text),
                choice.onSelect
            );
            button.visible = false; // Hidden until text completes
            button.setGlowPulse(false);
        }
    }
    
    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Update typewriter effect
        updateTypewriter();
        
        // Render dialogue box
        int boxX = PANEL_MARGIN;
        int boxY = height - PANEL_MARGIN - DIALOGUE_BOX_HEIGHT;
        int boxWidth = width - PANEL_MARGIN * 2;
        
        // Draw dialogue box background
        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + DIALOGUE_BOX_HEIGHT, 0xE0080808);
        
        // Draw box border
        drawBoxBorder(graphics, boxX, boxY, boxWidth, DIALOGUE_BOX_HEIGHT);
        
        // Draw NPC name badge
        int nameWidth = font.width(npcName) + 16;
        graphics.fill(boxX + 10, boxY - 8, boxX + 10 + nameWidth, boxY + 4, 0xFF0A0A0A);
        graphics.fill(boxX + 10, boxY - 8, boxX + 10 + nameWidth, boxY - 6, COLOR_NEON_RED);
        graphics.drawString(font, npcName, boxX + 18, boxY - 6, COLOR_NEON_RED);
        
        // Draw dialogue text with typewriter effect
        String displayText = dialogueText.substring(0, Math.min(displayedCharacters, dialogueText.length()));
        drawWrappedText(graphics, displayText, boxX + 15, boxY + 12, boxWidth - 30, COLOR_TEXT_BODY);
        
        // Show blinking cursor if not complete
        if (!textComplete) {
            long blink = (System.currentTimeMillis() / 500) % 2;
            if (blink == 0) {
                String cursorText = displayText + "▌";
                int cursorX = boxX + 15 + font.width(getLastLine(displayText));
                int cursorY = boxY + 12 + getLineCount(displayText, boxWidth - 30) * 10;
                graphics.drawString(font, "▌", cursorX, cursorY, COLOR_NEON_RED);
            }
        }
        
        // Show/hide choice buttons based on text completion
        for (int i = 0; i < strangerButtons.size(); i++) {
            strangerButtons.get(i).visible = textComplete;
        }
    }
    
    private void updateTypewriter() {
        if (displayedCharacters < dialogueText.length()) {
            long now = System.currentTimeMillis();
            if (now - lastCharTime >= CHAR_DELAY_MS) {
                displayedCharacters++;
                lastCharTime = now;
            }
        } else {
            textComplete = true;
        }
    }
    
    private void drawBoxBorder(GuiGraphics graphics, int x, int y, int w, int h) {
        // Neon red border with corner accents
        graphics.fill(x, y, x + w, y + 1, COLOR_BORDER);
        graphics.fill(x, y + h - 1, x + w, y + h, COLOR_BORDER);
        graphics.fill(x, y, x + 1, y + h, COLOR_BORDER);
        graphics.fill(x + w - 1, y, x + w, y + h, COLOR_BORDER);
        
        // Corner highlights
        int cs = 8;
        graphics.fill(x, y, x + cs, y + 2, COLOR_NEON_RED);
        graphics.fill(x, y, x + 2, y + cs, COLOR_NEON_RED);
        graphics.fill(x + w - cs, y, x + w, y + 2, COLOR_NEON_RED);
        graphics.fill(x + w - 2, y, x + w, y + cs, COLOR_NEON_RED);
        graphics.fill(x, y + h - 2, x + cs, y + h, COLOR_NEON_RED);
        graphics.fill(x, y + h - cs, x + 2, y + h, COLOR_NEON_RED);
        graphics.fill(x + w - cs, y + h - 2, x + w, y + h, COLOR_NEON_RED);
        graphics.fill(x + w - 2, y + h - cs, x + w, y + h, COLOR_NEON_RED);
    }
    
    private void drawWrappedText(GuiGraphics graphics, String text, int x, int y, int maxWidth, int color) {
        List<String> lines = wrapText(text, maxWidth);
        for (int i = 0; i < lines.size(); i++) {
            graphics.drawString(font, lines.get(i), x, y + i * 10, color);
        }
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
    
    private String getLastLine(String text) {
        int lastNewline = text.lastIndexOf('\n');
        return lastNewline >= 0 ? text.substring(lastNewline + 1) : text;
    }
    
    private int getLineCount(String text, int maxWidth) {
        return wrapText(text, maxWidth).size();
    }
    
    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Skip text on any key
        if (!textComplete) {
            displayedCharacters = dialogueText.length();
            textComplete = true;
            return true;
        }
        
        // Prevent closing on ESC by returning true without calling super
        if (keyCode == 256) { // GLFW_KEY_ESCAPE is 256
            return true;
        }
        
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Skip text on click
        if (!textComplete) {
            displayedCharacters = dialogueText.length();
            textComplete = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    public record DialogueChoice(String id, String text, Runnable onSelect) {}
}
