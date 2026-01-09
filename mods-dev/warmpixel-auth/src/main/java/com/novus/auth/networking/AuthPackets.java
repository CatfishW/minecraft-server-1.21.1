package com.novus.auth.networking;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.novus.auth.NovusAuth;

public class AuthPackets {
    public static final ResourceLocation OPEN_AUTH_SCREEN_ID = ResourceLocation.fromNamespaceAndPath(NovusAuth.MOD_ID, "open_auth_screen");
    public static final ResourceLocation AUTH_ACTION_ID = ResourceLocation.fromNamespaceAndPath(NovusAuth.MOD_ID, "auth_action");

    public record OpenAuthScreenPayload(boolean registered) implements CustomPacketPayload {
        public static final Type<OpenAuthScreenPayload> TYPE = new Type<>(OPEN_AUTH_SCREEN_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenAuthScreenPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, OpenAuthScreenPayload::registered,
            OpenAuthScreenPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record AuthActionPayload(String password) implements CustomPacketPayload {
        public static final Type<AuthActionPayload> TYPE = new Type<>(AUTH_ACTION_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, AuthActionPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(256), AuthActionPayload::password,
            AuthActionPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
