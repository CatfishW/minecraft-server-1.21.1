package com.tacz.guns.compat.flan;

import cn.sh1rocu.tacz.api.LogicalSide;
import com.tacz.guns.api.event.common.GunShootEvent;
import com.tacz.guns.api.item.IGun;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;

public class FlanCompat {
    private static Method getClaimAtMethod;
    private static Method getClaimStorageMethod;
    private static boolean initialized = false;

    public static void initCompat() {
        if (initialized) return;
        try {
            // Check if Flan classes exist
            Class<?> claimStorageClass = Class.forName("io.github.flemmli97.flan.claim.ClaimStorage");
            getClaimStorageMethod = claimStorageClass.getMethod("get", ServerLevel.class);
            getClaimAtMethod = claimStorageClass.getMethod("getClaimAt", BlockPos.class);
            initialized = true;

            GunShootEvent.CALLBACK.register(event -> {
                if (event.getLogicalSide() == LogicalSide.CLIENT) return;
                if (!(event.getShooter() instanceof ServerPlayer player)) return;

                IGun iGun = IGun.getIGunOrNull(event.getGunItemStack());
                if (iGun == null) return;

                ResourceLocation gunId = iGun.getGunId(event.getGunItemStack());
                if (gunId.toString().equals("tacz:rpg7")) {
                    if (isInClaim(player)) {
                        event.setCanceled(true);
                        player.displayClientMessage(Component.literal("§cRPGs are disabled in claimed lands!"), true);
                    }
                }
            });
        } catch (ClassNotFoundException e) {
            // Flan not present, ignore
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static boolean isInClaim(ServerPlayer player) {
        try {
             Object storage = getClaimStorageMethod.invoke(null, player.serverLevel());
             Object claim = getClaimAtMethod.invoke(storage, player.blockPosition());
             return claim != null;
        } catch (Exception e) {
            return false;
        }
    }
}
