package com.flaneconomy;

import com.flaneconomy.network.FlanEconomyNetworking;
import com.flaneconomy.service.ClaimSaleService;
import com.flaneconomy.service.FlanClaimService;
import com.flaneconomy.service.NumismaticCurrencyService;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;

public class FlanEconomyMod implements ModInitializer {
    public static final String MOD_ID = "flan_economy";
    public static final long CLAIM_BLOCK_PRICE = 100L;
    public static final long TELEPORT_FEE = 10000L;
    public static final long OWN_TELEPORT_FEE = 888L;

    private static NumismaticCurrencyService currencyService;
    private static FlanClaimService claimService;
    private static ClaimSaleService saleService;

    @Override
    public void onInitialize() {
        FlanEconomyItems.registerItems();
        FlanEconomyNetworking.registerPayloadTypes();
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.SERVER) {
            return;
        }
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> FlanEconomyCommands.register(dispatcher));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            currencyService = new NumismaticCurrencyService(server);
            claimService = new FlanClaimService(server);
            saleService = new ClaimSaleService();
            saleService.load();
            FlanEconomyNetworking.registerServer();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (saleService != null) {
                saleService.save();
            }
        });
    }

    public static NumismaticCurrencyService getCurrencyService() {
        return currencyService;
    }

    public static FlanClaimService getClaimService() {
        return claimService;
    }

    public static ClaimSaleService getSaleService() {
        return saleService;
    }
}
