package com.warmpixel.npcbusdriver;

import com.warmpixel.npcbusdriver.item.PathWandItem;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import java.util.UUID;
import java.util.List;

public class NPCBusDriverMod implements ModInitializer {
	public static final String MOD_ID = "npcbusdriver";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final Item PATH_WAND = new PathWandItem(new Item.Properties());

	// Creative Tab
	public static final net.minecraft.world.item.CreativeModeTab NPC_TAB = net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup.builder()
		.title(net.minecraft.network.chat.Component.literal("NPC Bus Driver"))
		.icon(() -> new ItemStack(PATH_WAND))
		.displayItems((context, entries) -> {
			entries.accept(PATH_WAND);
            
            // Register City Buses using Reflection for Automobility compatibility
            try {
                // Classes
                Class<?> dataClass = Class.forName("io.github.foundationgames.automobility.automobile.AutomobileData");
                Class<?> itemsClass = Class.forName("io.github.foundationgames.automobility.item.AutomobilityItems");
                
                // Component Type
                Object componentObj = itemsClass.getField("COMPONENT_AUTOMOBILE_DATA").get(null);
                Object typeObj = componentObj.getClass().getMethod("require").invoke(componentObj);
                net.minecraft.core.component.DataComponentType<Object> type = (net.minecraft.core.component.DataComponentType<Object>) typeObj;
                
                // Helper to create bus stack
                String[] colors = {"blue", "green", "pink", "red", "yellow"};
                for (String color : colors) {
                    // Keys
                    net.minecraft.resources.ResourceKey<net.minecraft.core.Registry<Object>> frameRegistryKey = net.minecraft.resources.ResourceKey.createRegistryKey(net.minecraft.resources.ResourceLocation.parse("automobility:automobile_frame"));
                    net.minecraft.resources.ResourceKey<net.minecraft.core.Registry<Object>> wheelRegistryKey = net.minecraft.resources.ResourceKey.createRegistryKey(net.minecraft.resources.ResourceLocation.parse("automobility:automobile_wheel"));
                    net.minecraft.resources.ResourceKey<net.minecraft.core.Registry<Object>> engineRegistryKey = net.minecraft.resources.ResourceKey.createRegistryKey(net.minecraft.resources.ResourceLocation.parse("automobility:automobile_engine"));

                    net.minecraft.resources.ResourceKey<?> frameKey = net.minecraft.resources.ResourceKey.create(
                        frameRegistryKey,
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cityvehicles", color + "_bus")
                    );
                    net.minecraft.resources.ResourceKey<?> wheelKey = net.minecraft.resources.ResourceKey.create(
                        wheelRegistryKey, 
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cityvehicles", "bus")
                    );
                    net.minecraft.resources.ResourceKey<?> engineKey = net.minecraft.resources.ResourceKey.create(
                        engineRegistryKey,
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("automobility", "iron")
                    );
                    
                    Object dataInstance = dataClass.getConstructor(java.util.Optional.class, net.minecraft.resources.ResourceKey.class, net.minecraft.resources.ResourceKey.class, net.minecraft.resources.ResourceKey.class)
                        .newInstance(java.util.Optional.empty(), frameKey, wheelKey, engineKey);
                    
                    // Create Item Stack
                    net.minecraft.resources.ResourceLocation autoItemRes = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("automobility", "automobile");
                    if (BuiltInRegistries.ITEM.containsKey(autoItemRes)) {
                        ItemStack busStack = new ItemStack(BuiltInRegistries.ITEM.get(autoItemRes));
                        busStack.set(type, dataInstance);
                        
                        String name = color.substring(0, 1).toUpperCase() + color.substring(1) + " City Bus";
                        busStack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal(name));
                        
                        entries.accept(busStack);
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Failed to register City Buses to Creative Tab", e);
            }
		})
		.build();

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing NPC Bus Driver Mod");

		// Register Item
		Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(MOD_ID, "path_wand"), PATH_WAND);

        // Register Tab
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, ResourceLocation.fromNamespaceAndPath(MOD_ID, "tab"), NPC_TAB);

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
                         wand.savePoints(stack, points);
                     }
                 } else if (payload.actionType() == 1) { // Spawn
                      wand.spawnDriver(player.serverLevel(), player, stack, payload.vehicleId());
                 }
             });
        });

		// Register particle ticker for visualization
		ServerTickEvents.END_SERVER_TICK.register(server -> {
            // Bus Driver Logic Tick
            try {
                BusDriverManager.tick();
            } catch (Exception e) {
                LOGGER.error("Error in bus driver logic tick", e);
            }
            
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

        // Ensure players only enter as passengers on managed buses
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClientSide) return InteractionResult.PASS;
            
            UUID vehicleUuid = BusDriverManager.getActualVehicleUUID(entity);
            if (vehicleUuid != null) {
                // Resolve the actual automobile entity
                Entity vehicle;
                if (BusDriverManager.isManagedVehicle(entity.getUUID())) {
                    vehicle = entity;
                } else {
                    try {
                        vehicle = (Entity) entity.getClass().getMethod("automobile").invoke(entity);
                    } catch (Exception e) {
                        return InteractionResult.PASS;
                    }
                }
                
                if (vehicle == null) return InteractionResult.PASS;

                // Handle maintenance tools / Shift-interaction
                ItemStack tool = player.getItemInHand(hand);
                String toolId = BuiltInRegistries.ITEM.getKey(tool.getItem()).toString();
                if (player.isShiftKeyDown() || toolId.equals("automobility:crowbar") || toolId.contains("path_wand")) {
                    return InteractionResult.PASS;
                }

                if (player.getVehicle() == vehicle) return InteractionResult.PASS;

                // 1. Ensure managed driver is seated in Index 0
                BusDriverManager.forceDriverIntoSeat(vehicle);
                
                // 2. Validate that our managed driver is actually riding (seat order is not reliable).
                List<Entity> passengers = vehicle.getPassengers();
                Entity managedDriver = BusDriverManager.getManagedDriver(vehicle.getUUID());
                if (managedDriver == null) {
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal("§eWaiting for driver..."), true);
                    return InteractionResult.FAIL;
                }

                if (!passengers.contains(managedDriver)) {
                    // Try one more time, maybe teleport them
                    BusDriverManager.forceDriverIntoSeat(vehicle);
                    passengers = vehicle.getPassengers();
                }

                // If our driver is still not riding, don't allow boarding.
                if (!passengers.contains(managedDriver)) {
                    NPCBusDriverMod.LOGGER.warn("Blocking {} from bus {}: Managed driver not seated.", 
                        player.getName().getString(), vehicle.getUUID());
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal("§eWaiting for driver..."), true);
                    return InteractionResult.FAIL;
                }

                // 3. Mount the player as passenger.
                // Since Slot 0 is occupied by the driver, startRiding(vehicle, false) will take Slot 1+.
                boolean success = player.startRiding(vehicle, false);
                if (success) {
                    BusDriverManager.requestStop(vehicle, 3);
                    return InteractionResult.SUCCESS;
                } else {
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal("§cNo seats available!"), true);
                    return InteractionResult.FAIL;
                }
            }
            return InteractionResult.PASS;
        });
	}
}
