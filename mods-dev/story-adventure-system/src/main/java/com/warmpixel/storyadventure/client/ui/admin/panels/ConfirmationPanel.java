package com.warmpixel.storyadventure.client.ui.admin.panels;

import com.warmpixel.storyadventure.client.ui.StrangerButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Reusable modal confirmation panel for admin actions.
 * Renders on top of the current screen as an overlay.
 */
public class ConfirmationPanel {
    
    private static final int COLOR_NEON_RED = 0xFFE50914;
    private static final int COLOR_TEXT_BODY = 0xFFCCCCCC;
    private static final int COLOR_TEXT_DIM = 0xFF666666;
    private static final int COLOR_BORDER = 0xFF330011;
    private static final int COLOR_WARNING = 0xFFFFCC00;
    private static final int COLOR_DANGER = 0xFFFF4444;
    
    private final String title;
    private final String description;
    private final boolean isDangerous;
    private final boolean hasInputField;
    private final String inputPlaceholder;
    private final Consumer<String> onConfirm;
    private final Runnable onCancel;
    
    private int x, y, width, height;
    private EditBox inputField;
    private boolean visible = false;
    
    private StrangerButton confirmButton;
    private StrangerButton cancelButton;
    
    public ConfirmationPanel(String title, String description, boolean isDangerous,
                             boolean hasInputField, String inputPlaceholder,
                             Consumer<String> onConfirm, Runnable onCancel) {
        this.title = title;
        this.description = description;
        this.isDangerous = isDangerous;
        this.hasInputField = hasInputField;
        this.inputPlaceholder = inputPlaceholder;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
    }
    
    /**
     * Show the panel centered on screen.
     */
    public void show(int screenWidth, int screenHeight, net.minecraft.client.gui.Font font) {
        this.width = 320;
        this.height = hasInputField ? 160 : 130;
        this.x = (screenWidth - width) / 2;
        this.y = (screenHeight - height) / 2;
        this.visible = true;
        
        if (hasInputField) {
            inputField = new EditBox(font, x + 20, y + 70, width - 40, 20, Component.literal(inputPlaceholder));
            inputField.setMaxLength(200);
            inputField.setHint(Component.literal(inputPlaceholder));
        }
    }
    
    public void hide() {
        this.visible = false;
        this.inputField = null;
    }
    
    public boolean isVisible() {
        return visible;
    }
    
    public void render(GuiGraphics graphics, int mouseX, int mouseY, net.minecraft.client.gui.Font font) {
        if (!visible) return;
        
        // Dim background
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), 0xC0000000);
        
        // Panel background
        graphics.fill(x, y, x + width, y + height, 0xF0101010);
        
        // Border
        int borderColor = isDangerous ? COLOR_DANGER : COLOR_NEON_RED;
        graphics.fill(x, y, x + width, y + 2, borderColor);
        graphics.fill(x, y + height - 2, x + width, y + height, borderColor);
        graphics.fill(x, y, x + 2, y + height, borderColor);
        graphics.fill(x + width - 2, y, x + width, y + height, borderColor);
        
        // Corner accents
        int cs = 10;
        graphics.fill(x, y, x + cs, y + 3, borderColor);
        graphics.fill(x, y, x + 3, y + cs, borderColor);
        graphics.fill(x + width - cs, y, x + width, y + 3, borderColor);
        graphics.fill(x + width - 3, y, x + width, y + cs, borderColor);
        
        // Title
        graphics.drawCenteredString(font, title, x + width / 2, y + 12, borderColor);
        
        // Divider
        graphics.fill(x + 10, y + 28, x + width - 10, y + 29, COLOR_BORDER);
        
        // Description (word wrap)
        List<String> lines = wrapText(description, width - 40, font);
        int lineY = y + 38;
        for (String line : lines) {
            graphics.drawString(font, line, x + 20, lineY, COLOR_TEXT_BODY);
            lineY += 12;
        }
        
        // Input field
        if (hasInputField && inputField != null) {
            inputField.render(graphics, mouseX, mouseY, 0);
        }
        
        // Buttons
        int buttonY = y + height - 35;
        int buttonWidth = 100;
        int buttonHeight = 24;
        int gap = 20;
        
        // Confirm button
        int confirmX = x + width / 2 - buttonWidth - gap / 2;
        boolean confirmHovered = mouseX >= confirmX && mouseX < confirmX + buttonWidth &&
                                  mouseY >= buttonY && mouseY < buttonY + buttonHeight;
        int confirmBg = confirmHovered ? (isDangerous ? 0xFF441111 : 0xFF114411) : 0xFF222222;
        graphics.fill(confirmX, buttonY, confirmX + buttonWidth, buttonY + buttonHeight, confirmBg);
        graphics.fill(confirmX, buttonY, confirmX + buttonWidth, buttonY + 1, isDangerous ? COLOR_DANGER : 0xFF44FF44);
        String confirmText = Component.translatable("gui.storyadventure.admin.panels.confirm").getString();
        graphics.drawCenteredString(font, confirmText, confirmX + buttonWidth / 2, buttonY + 7, 
            isDangerous ? COLOR_DANGER : 0xFF44FF44);
        
        // Cancel button
        int cancelX = x + width / 2 + gap / 2;
        boolean cancelHovered = mouseX >= cancelX && mouseX < cancelX + buttonWidth &&
                                 mouseY >= buttonY && mouseY < buttonY + buttonHeight;
        int cancelBg = cancelHovered ? 0xFF333333 : 0xFF222222;
        graphics.fill(cancelX, buttonY, cancelX + buttonWidth, buttonY + buttonHeight, cancelBg);
        graphics.fill(cancelX, buttonY, cancelX + buttonWidth, buttonY + 1, COLOR_TEXT_DIM);
        graphics.drawCenteredString(font, Component.translatable("gui.storyadventure.admin.panels.cancel").getString(), cancelX + buttonWidth / 2, buttonY + 7, COLOR_TEXT_BODY);
    }
    
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;
        
        // Check input field
        if (hasInputField && inputField != null) {
            inputField.mouseClicked(mouseX, mouseY, button);
        }
        
        int buttonY = y + height - 35;
        int buttonWidth = 100;
        int buttonHeight = 24;
        int gap = 20;
        
        // Confirm button
        int confirmX = x + width / 2 - buttonWidth - gap / 2;
        if (mouseX >= confirmX && mouseX < confirmX + buttonWidth &&
            mouseY >= buttonY && mouseY < buttonY + buttonHeight) {
            String inputValue = hasInputField && inputField != null ? inputField.getValue() : "";
            onConfirm.accept(inputValue);
            hide();
            return true;
        }
        
        // Cancel button
        int cancelX = x + width / 2 + gap / 2;
        if (mouseX >= cancelX && mouseX < cancelX + buttonWidth &&
            mouseY >= buttonY && mouseY < buttonY + buttonHeight) {
            onCancel.run();
            hide();
            return true;
        }
        
        // Click outside to cancel
        if (mouseX < x || mouseX > x + width || mouseY < y || mouseY > y + height) {
            onCancel.run();
            hide();
            return true;
        }
        
        return true; // Consume click to prevent interaction with background
    }
    
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible) return false;
        
        if (hasInputField && inputField != null) {
            return inputField.keyPressed(keyCode, scanCode, modifiers);
        }
        
        // Escape to cancel
        if (keyCode == 256) { // GLFW_KEY_ESCAPE
            onCancel.run();
            hide();
            return true;
        }
        
        // Enter to confirm
        if (keyCode == 257) { // GLFW_KEY_ENTER
            String inputValue = hasInputField && inputField != null ? inputField.getValue() : "";
            onConfirm.accept(inputValue);
            hide();
            return true;
        }
        
        return false;
    }
    
    public boolean charTyped(char chr, int modifiers) {
        if (!visible) return false;
        
        if (hasInputField && inputField != null) {
            return inputField.charTyped(chr, modifiers);
        }
        return false;
    }
    
    private List<String> wrapText(String text, int maxWidth, net.minecraft.client.gui.Font font) {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();
        
        for (String word : words) {
            String test = currentLine.length() > 0 ? currentLine + " " + word : word;
            if (font.width(test) <= maxWidth) {
                if (currentLine.length() > 0) currentLine.append(" ");
                currentLine.append(word);
            } else {
                if (currentLine.length() > 0) {
                    lines.add(currentLine.toString());
                }
                currentLine = new StringBuilder(word);
            }
        }
        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }
        return lines;
    }
    
    // Builder pattern for easier creation
    public static Builder builder(String title) {
        return new Builder(title);
    }
    
    public static class Builder {
        private final String title;
        private String description = "";
        private boolean isDangerous = false;
        private boolean hasInputField = false;
        private String inputPlaceholder = "";
        private Consumer<String> onConfirm = s -> {};
        private Runnable onCancel = () -> {};
        
        public Builder(String title) {
            this.title = title;
        }
        
        public Builder description(String description) {
            this.description = description;
            return this;
        }
        
        public Builder dangerous() {
            this.isDangerous = true;
            return this;
        }
        
        public Builder withInput(String placeholder) {
            this.hasInputField = true;
            this.inputPlaceholder = placeholder;
            return this;
        }
        
        public Builder onConfirm(Consumer<String> callback) {
            this.onConfirm = callback;
            return this;
        }
        
        public Builder onConfirm(Runnable callback) {
            this.onConfirm = s -> callback.run();
            return this;
        }
        
        public Builder onCancel(Runnable callback) {
            this.onCancel = callback;
            return this;
        }
        
        public ConfirmationPanel build() {
            return new ConfirmationPanel(title, description, isDangerous, hasInputField, 
                inputPlaceholder, onConfirm, onCancel);
        }
    }
}
