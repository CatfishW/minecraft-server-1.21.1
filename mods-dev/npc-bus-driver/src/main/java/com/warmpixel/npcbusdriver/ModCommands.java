package com.warmpixel.npcbusdriver;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.warmpixel.npcbusdriver.item.PathWandItem;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.List;
import java.util.Optional;

public class ModCommands {

    public static void register(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher, net.minecraft.commands.CommandBuildContext registryAccess, net.minecraft.commands.Commands.CommandSelection environment) {
        dispatcher.register(Commands.literal("npcbusdriver")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("save_path")
                .then(Commands.argument("name", StringArgumentType.string())
                    .executes(ModCommands::savePath)))
            .then(Commands.literal("setup_bus_driver")
                .then(Commands.argument("path_name", StringArgumentType.string())
                    .then(Commands.argument("bus_type", StringArgumentType.string())
                         .executes(ModCommands::setupBusDriver))))
        );
    }

    private static int savePath(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            ItemStack stack = player.getMainHandItem();
            if (!(stack.getItem() instanceof PathWandItem)) {
                context.getSource().sendFailure(Component.literal("Hold the Path Wand to save the path!"));
                return 0;
            }

            String name = StringArgumentType.getString(context, "name");
            List<BlockPos> points = ((PathWandItem) stack.getItem()).getPoints(stack);
            
            if (points.isEmpty()) {
                context.getSource().sendFailure(Component.literal("Path is empty!"));
                return 0;
            }

            PathManager.savePath(name, points);
            context.getSource().sendSuccess(() -> Component.literal("Path '" + name + "' saved with " + points.size() + " points."), true);
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Error saving path: " + e.getMessage()));
            e.printStackTrace();
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int setupBusDriver(CommandContext<CommandSourceStack> context) {
        try {
            Entity executor = context.getSource().getEntity();
            String pathName = StringArgumentType.getString(context, "path_name");
            String busTypeStr = StringArgumentType.getString(context, "bus_type");

            if (executor == null) {
                context.getSource().sendFailure(Component.literal("This command must be executed by an entity (NPC)."));
                return 0;
            }

            List<BlockPos> path = PathManager.loadPath(pathName);
            if (path == null || path.isEmpty()) {
                context.getSource().sendFailure(Component.literal("Path '" + pathName + "' not found or empty."));
                return 0;
            }

            // Spawn Vehicle
            Entity vehicle = null;
            Optional<EntityType<?>> typeOpt = EntityType.byString(busTypeStr);
            if (typeOpt.isPresent()) {
                 vehicle = typeOpt.get().spawn(context.getSource().getLevel(), BlockPos.containing(executor.position()), MobSpawnType.COMMAND);
            } else {
                 // Try as Item (specifically Automobility)
                 ResourceLocation itemId = ResourceLocation.parse(busTypeStr);
                 if (BuiltInRegistries.ITEM.containsKey(itemId)) {
                     net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.get(itemId);
                     // Check if it's an AutomobileItem via reflection/class check
                     if (item.getClass().getName().equals("io.github.foundationgames.automobility.item.AutomobileItem")) {
                          // Spawn generic automobile
                          Optional<EntityType<?>> autoType = EntityType.byString("automobility:automobile");
                          if (autoType.isPresent()) {
                              vehicle = autoType.get().spawn(context.getSource().getLevel(), BlockPos.containing(executor.position()), MobSpawnType.COMMAND);
                              if (vehicle != null) {
                                  try {
                                      // Reflection to set data
                                      // AutomobileItem methods: getFrame, getWheel, getEngine
                                      Object frame = item.getClass().getMethod("getFrame").invoke(item);
                                      Object wheel = item.getClass().getMethod("getWheel").invoke(item);
                                      Object engine = item.getClass().getMethod("getEngine").invoke(item);
                                      
                                      // AutomobileData constructor: (Frame, Wheel, Engine)
                                      Class<?> frameClass = Class.forName("io.github.foundationgames.automobility.automobile.AutomobileFrame");
                                      Class<?> wheelClass = Class.forName("io.github.foundationgames.automobility.automobile.AutomobileWheel");
                                      Class<?> engineClass = Class.forName("io.github.foundationgames.automobility.automobile.AutomobileEngine");
                                      Class<?> dataClass = Class.forName("io.github.foundationgames.automobility.automobile.AutomobileData");
                                      
                                      Object data = dataClass.getConstructor(frameClass, wheelClass, engineClass).newInstance(frame, wheel, engine);
                                      
                                      // AutomobileEntity.setData(AutomobileData)
                                      vehicle.getClass().getMethod("setAutomobileData", dataClass).invoke(vehicle, data);
                                  } catch (Exception ex) {
                                      System.out.println("Failed to configure Automobility vehicle: " + ex.getMessage());
                                      ex.printStackTrace();
                                  }
                              }
                          }
                     }
                 }
            }

            if (vehicle == null) {
                context.getSource().sendFailure(Component.literal("Failed to resolve or spawn vehicle: " + busTypeStr));
                return 0;
            }

            // Mount NPC
            executor.startRiding(vehicle);
            
            // Start Driver Logic
            // We need to attach a ticker to the vehicle or the NPC
            // Since we can't easily modify the entity class, we can track it in a global manager in our mod
            BusDriverManager.startDriving(vehicle, path);
            
            context.getSource().sendSuccess(() -> Component.literal("Setup bus driver on path '" + pathName + "'"), true);

        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Error setting up bus driver: " + e.getMessage()));
            e.printStackTrace();
        }
        return Command.SINGLE_SUCCESS;
    }
}
