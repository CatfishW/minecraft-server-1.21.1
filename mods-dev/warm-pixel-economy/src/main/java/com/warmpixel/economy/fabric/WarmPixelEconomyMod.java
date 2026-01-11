package com.warmpixel.economy.fabric;

import com.warmpixel.economy.storage.EconomyStorage;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

public class WarmPixelEconomyMod implements ModInitializer {
    public static final String MOD_ID = "warm_pixel_economy";
    private static EconomyContext context;

    @Override
    public void onInitialize() {
        ShopScreenHandlers.register();
        AuctionScreenHandlers.register();
        ShopNetworking.registerPayloadTypes();

        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.SERVER) {
            return;
        }

        EconomyConfig config = ConfigManager.load();
        Path storagePath = FabricLoader.getInstance().getConfigDir().resolve("warm-pixel-economy").resolve("storage.json");
        EconomyStorage storage = new EconomyStorage(storagePath);
        context = new EconomyContext(config, storage);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            EconomyCommands.register(dispatcher);
            ShopCommands.register(dispatcher);
            AuctionCommands.register(dispatcher);
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> context.onServerStarted(server));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> context.shutdown());
        ShopNetworking.registerServer();

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (context != null && context.deliveryService() != null) {
                context.deliveryService().claimDeliveries(handler.getPlayer(), 50, context.config().defaultCurrency);
            }
        });
    }

    public static EconomyContext getContext() {
        return context;
    }
}
