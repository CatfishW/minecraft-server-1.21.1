package com.warmpixel.economy.fabric;

import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;

public final class PermissionHelper {
    private static Method permissionsCheck;

    private PermissionHelper() {
    }

    public static boolean hasPermission(ServerPlayer player, String node, int fallbackLevel) {
        if (player == null) {
            return false;
        }
        if (permissionsCheck == null) {
            try {
                Class<?> permissionsClass = Class.forName("me.lucko.fabric.api.permissions.v0.Permissions");
                permissionsCheck = permissionsClass.getMethod("check", ServerPlayer.class, String.class, boolean.class);
            } catch (Exception ignored) {
                permissionsCheck = null;
            }
        }

        if (permissionsCheck != null) {
            try {
                Object result = permissionsCheck.invoke(null, player, node, false);
                if (result instanceof Boolean value) {
                    return value;
                }
            } catch (Exception ignored) {
                // fall through
            }
        }

        return player.hasPermissions(fallbackLevel);
    }
}
