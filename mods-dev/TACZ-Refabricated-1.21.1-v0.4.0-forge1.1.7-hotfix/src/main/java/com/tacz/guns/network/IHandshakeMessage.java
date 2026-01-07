package com.tacz.guns.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public interface IHandshakeMessage extends CustomPacketPayload {
    void handle(ClientConfigurationNetworking.Context context);

    interface IResponsePacket extends CustomPacketPayload {
        void handle(ServerConfigurationNetworking.Context context);
    }
}
