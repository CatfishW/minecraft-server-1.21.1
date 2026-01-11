package com.warmpixel.npcbusdriver;

import com.warmpixel.npcbusdriver.item.PathWandItem;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NPCBusDriverMod implements ModInitializer {
	public static final String MOD_ID = "npcbusdriver";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final Item PATH_WAND = new PathWandItem(new Item.Properties());

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing NPC Bus Driver Mod");

		// Register Item
		Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(MOD_ID, "path_wand"), PATH_WAND);

        // Register Commands
        CommandRegistrationCallback.EVENT.register(ModCommands::register);

        // Register Payloads
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C().register(com.warmpixel.npcbusdriver.network.OpenWandGuiPayload.ID, com.warmpixel.npcbusdriver.network.OpenWandGuiPayload.CODEC);
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playC2S().register(com.warmpixel.npcbusdriver.network.WandActionPayload.ID, com.warmpixel.npcbusdriver.network.WandActionPayload.CODEC);

        // Handle Packet
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(com.warmpixel.npcbusdriver.network.WandActionPayload.ID, (payload, context) -> {
             context.server().execute(() -> {
                 ServerPlayer player = context.player();
                 ItemStack stack = player.getMainHandItem();
                 if (!(stack.getItem() instanceof PathWandItem)) return;
                 PathWandItem wand = (PathWandItem) stack.getItem();
                 
                 if (payload.actionType() == 0) { // Remove Point
                     int index = payload.index();
                     java.util.List<net.minecraft.core.BlockPos> points = wand.getPoints(stack);
                     if (index >= 0 && index < points.size()) {
                         points.remove(index);
                         wand.savePoints(stack, points); // This is public/package private now? Need to check access.
                         // Reflecting update?
                         // Ideally we rely on Container sync or explicit set.
                     }
                 } else if (payload.actionType() == 1) { // Spawn
                      wand.spawnDriver(player.serverLevel(), player, stack, payload.vehicleId());
                 }
             });
        });

		// Register particle ticker for visualization
		ServerTickEvents.END_SERVER_TICK.register(server -> {
            // Bus Driver Logic Tick
            BusDriverManager.tick();
            
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				ItemStack mainHand = player.getMainHandItem();
				ItemStack offHand = player.getOffhandItem();

				if (mainHand.getItem() instanceof PathWandItem) {
					((PathWandItem) mainHand.getItem()).visualizePath(player.serverLevel(), player, mainHand);
				} else if (offHand.getItem() instanceof PathWandItem) {
					((PathWandItem) offHand.getItem()).visualizePath(player.serverLevel(), player, offHand);
				}
			}
		});
	}
}
