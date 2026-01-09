package com.tacz.guns.compat.iris;

import com.tacz.guns.init.CompatRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.irisshaders.batchedentityrendering.impl.FullyBufferedMultiBufferSource;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.shadows.ShadowRenderingState;
import net.minecraft.client.renderer.MultiBufferSource;

import java.util.function.Function;
import java.util.function.Supplier;

public final class IrisCompat {
    private static Function<MultiBufferSource.BufferSource, Boolean> END_BATCH_FUNCTION;
    private static Supplier<Boolean> IS_RENDER_SHADOW_SUPPER;

    public static void initCompat() {
        FabricLoader.getInstance().getModContainer(CompatRegistry.IRIS).ifPresent(mod -> {
            END_BATCH_FUNCTION = IrisCompat::endBatchInner;
            IS_RENDER_SHADOW_SUPPER = IrisCompat::isRenderShadowInner;
        });
    }

    public static boolean isPackInUseQuick() {
        if (FabricLoader.getInstance().isModLoaded(CompatRegistry.IRIS)) {
            // Use IrisApi instead of Iris.isPackInUseQuick() which may not exist in all Iris versions
            return IrisApi.getInstance().isShaderPackInUse();
        }
        return false;
    }

    public static boolean isRenderShadow() {
        if (FabricLoader.getInstance().isModLoaded(CompatRegistry.IRIS)) {
            return IS_RENDER_SHADOW_SUPPER.get();
        }
        return false;
    }

    public static boolean isUsingRenderPack() {
        if (FabricLoader.getInstance().isModLoaded(CompatRegistry.IRIS)) {
            return IrisApi.getInstance().isShaderPackInUse();
        }
        return false;
    }

    public static boolean endBatch(MultiBufferSource.BufferSource bufferSource) {
        if (FabricLoader.getInstance().isModLoaded(CompatRegistry.IRIS)) {
            return END_BATCH_FUNCTION.apply(bufferSource);
        }
        return false;
    }

    private static boolean isRenderShadowInner() {
        return ShadowRenderingState.areShadowsCurrentlyBeingRendered();
    }

    private static boolean endBatchInner(MultiBufferSource.BufferSource bufferSource) {
        if (bufferSource instanceof FullyBufferedMultiBufferSource fullyBufferedMultiBufferSource) {
            fullyBufferedMultiBufferSource.endBatch();
            return true;
        }
        return false;
    }
}
