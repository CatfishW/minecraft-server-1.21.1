package com.warmpixel.imagedisplayer.client;

import com.warmpixel.imagedisplayer.ImageDisplayerMod;
import com.warmpixel.imagedisplayer.client.renderer.BillboardBlockEntityRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

public class ImageDisplayerClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BlockEntityRenderers.register(ImageDisplayerMod.BILLBOARD_BLOCK_ENTITY, BillboardBlockEntityRenderer::new);
    }
}
