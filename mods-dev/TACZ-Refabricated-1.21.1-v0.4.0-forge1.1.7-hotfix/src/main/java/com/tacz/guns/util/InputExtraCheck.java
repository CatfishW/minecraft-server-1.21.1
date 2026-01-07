package com.tacz.guns.util;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public final class InputExtraCheck {
    public static boolean isInGame() {
        Minecraft mc = Minecraft.getInstance();
        // 不能是加载界面
        if (mc.getOverlay() != null) {
            return false;
        }
        // 不能打开任何 GUI
        if (mc.screen != null) {
            return false;
        }
        // 当前窗口捕获鼠标操作
        if (!mc.mouseHandler.isMouseGrabbed()) {
            return false;
        }
        // 选择了当前窗口
        return mc.isWindowActive();
    }

    /**
     * Checks if a key is currently pressed, bypassing Minecraft's key conflict detection.
     * This allows TACZ keys to work even when conflicting with vanilla keybindings.
     * Uses direct field access via access widener.
     */
    public static boolean isKeyDown(KeyMapping keyMapping) {
        Minecraft mc = Minecraft.getInstance();
        long window = mc.getWindow().getWindow();
        // Access the key field directly via access widener
        InputConstants.Key key = keyMapping.key;
        
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        } else if (key.getType() == InputConstants.Type.KEYSYM) {
            return GLFW.glfwGetKey(window, key.getValue()) == GLFW.GLFW_PRESS;
        }
        return false;
    }
}
