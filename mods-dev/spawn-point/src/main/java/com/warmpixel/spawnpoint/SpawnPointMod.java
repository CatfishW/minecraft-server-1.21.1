package com.warmpixel.spawnpoint;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpawnPointMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("spawnpoint");
    public static SpawnPointConfig config;

    @Override
    public void onInitialize() {
        config = SpawnPointConfig.load();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("sp")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.literal("set")
                            .executes(context -> {
                                CommandSourceStack source = context.getSource();
                                ServerPlayer player = source.getPlayer();
                                if (player != null) {
                                    config.world = player.level().dimension().location().toString();
                                    config.x = player.getX();
                                    config.y = player.getY();
                                    config.z = player.getZ();
                                    config.yaw = player.getYRot();
                                    config.pitch = player.getXRot();
                                    config.enabled = true;
                                    config.save();
                                    source.sendSuccess(() -> Component.literal("Default spawn point set to current location."), true);
                                }
                                return 1;
                            })
                    )
            );
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            
            // Check if player is new
            // Stats.PLAY_ONE_MINUTE is 0 for new players.
            int playTime = player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME));
            
            if (playTime == 0 && config.enabled) {
                LOGGER.info("Teleporting new player {} to default spawn point", player.getGameProfile().getName());
                teleportToSpawn(player, server);
            }
        });

        net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (config.enabled) {
                LOGGER.info("Teleporting respawned player {} to default spawn point", newPlayer.getGameProfile().getName());
                teleportToSpawn(newPlayer, newPlayer.getServer());
            }
        });
    }

    private void teleportToSpawn(ServerPlayer player, net.minecraft.server.MinecraftServer server) {
        try {
            ResourceLocation worldId = ResourceLocation.parse(config.world);
            ServerLevel world = server.getLevel(ResourceKey.create(Registries.DIMENSION, worldId));
            
            if (world != null) {
                player.teleportTo(world, config.x, config.y, config.z, config.yaw, config.pitch);
            } else {
                LOGGER.error("Could not find world: {}", config.world);
            }
        } catch (Exception e) {
            LOGGER.error("Error teleporting player to spawn", e);
        }
    }
}
