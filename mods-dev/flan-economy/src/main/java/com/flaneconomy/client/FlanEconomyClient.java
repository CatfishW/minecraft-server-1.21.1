package com.flaneconomy.client;

import com.flaneconomy.network.FlanEconomyNetworking;
import net.fabricmc.api.ClientModInitializer;

public class FlanEconomyClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        FlanEconomyNetworking.registerPayloadTypes();
        FlanEconomyClientNetworking.registerClient();
    }
}
