package com.tacz.guns.compat.controllable;

import net.fabricmc.loader.api.FabricLoader;

public class ControllableCompat {
    private static final String MOD_ID = "controllable";

    public static void init() {
        if (FabricLoader.getInstance().isModLoaded(MOD_ID)) {
            ControllableInner.init();
        }
    }
}
