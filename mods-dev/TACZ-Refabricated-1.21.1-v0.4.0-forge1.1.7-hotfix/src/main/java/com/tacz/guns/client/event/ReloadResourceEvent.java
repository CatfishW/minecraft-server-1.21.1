package com.tacz.guns.client.event;

import cn.sh1rocu.tacz.api.event.TextureStitchEvent;
import com.tacz.guns.client.resource.InternalAssetLoader;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.ResourceLocation;

@Environment(EnvType.CLIENT)
public class ReloadResourceEvent {
    public static final ResourceLocation BLOCK_ATLAS_TEXTURE = ResourceLocation.parse("textures/atlas/blocks.png");

    public static void onTextureStitchEventPost(TextureStitchEvent.Post event) {
        if (BLOCK_ATLAS_TEXTURE.equals(event.getAtlas().location())) {
            // InternalAssetLoader 需要加载一些默认的动画、模型，需要先于枪包加载。
            InternalAssetLoader.onResourceReload();
//            ClientReloadManager.reloadAllPack();
        }
    }
}
