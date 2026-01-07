package com.tacz.guns.init;

import cn.sh1rocu.tacz.api.event.EntityRemoveEvent;
import com.tacz.guns.entity.sync.core.DataHolderCapabilityProvider;
import com.tacz.guns.entity.sync.core.SyncedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.entity.EntityComponentFactoryRegistry;
import org.ladysnake.cca.api.v3.entity.EntityComponentInitializer;

public class CapabilityRegistry implements EntityComponentInitializer {
    @Override
    public void registerEntityComponentFactories(@NotNull EntityComponentFactoryRegistry registry) {
        registry.beginRegistration(Entity.class, DataHolderCapabilityProvider.CAPABILITY)
                .filter(SyncedEntityData.instance()::hasSyncedDataKey)
                .end(entity -> new DataHolderCapabilityProvider());
    }

    public static void init() {
        EntityRemoveEvent.EVENT.register(event -> {
            var entity = event.getEntity();
            if (!(entity instanceof ServerPlayer)) {
                DataHolderCapabilityProvider.CAPABILITY.maybeGet(entity).ifPresent(DataHolderCapabilityProvider::invalidate);
            }
        });
    }
}