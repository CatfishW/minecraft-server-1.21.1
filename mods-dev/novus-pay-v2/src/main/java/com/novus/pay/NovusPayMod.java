package com.novus.pay;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NovusPayMod implements ModInitializer {
    public static final String MOD_ID = "novus_pay";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Novus Pay...");
        
        PaymentConfig.load();
        PaymentManager.init();
        
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            PayCommand.register(dispatcher);
        });
        
        LOGGER.info("Novus Pay Initialized.");
    }
}
