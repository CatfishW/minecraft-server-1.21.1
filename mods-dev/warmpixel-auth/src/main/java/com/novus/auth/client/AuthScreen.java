package com.novus.auth.client;

import com.novus.auth.networking.AuthPackets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class AuthScreen extends Screen {
    private final boolean registered;
    private EditBox passwordField;

    public AuthScreen(boolean registered) {
        super(Component.translatable(registered ? "novus_auth.gui.login.title" : "novus_auth.gui.register.title"));
        this.registered = registered;
    }

    @Override
    protected void init() {
        super.init();
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        this.passwordField = new EditBox(this.font, centerX - 100, centerY - 10, 200, 20, Component.translatable("novus_auth.gui.prompt"));
        this.passwordField.setMaxLength(32);
        this.addRenderableWidget(this.passwordField);

        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> {
            String password = this.passwordField.getValue();
            if (!password.isEmpty()) {
                ClientPlayNetworking.send(new AuthPackets.AuthActionPayload(password));
                this.onClose();
            }
        }).bounds(centerX - 100, centerY + 20, 200, 20).build());

        this.setInitialFocus(this.passwordField);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        this.renderBackground(graphics, mouseX, mouseY, delta);
        super.render(graphics, mouseX, mouseY, delta);
        
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        graphics.drawCenteredString(this.font, this.title, centerX, centerY - 60, 0xFFFFFF);
        
        Component prompt = Component.translatable(registered ? "novus_auth.gui.login.prompt" : "novus_auth.gui.register.prompt");
        graphics.drawCenteredString(this.font, prompt, centerX, centerY - 40, 0xAAAAAA);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
