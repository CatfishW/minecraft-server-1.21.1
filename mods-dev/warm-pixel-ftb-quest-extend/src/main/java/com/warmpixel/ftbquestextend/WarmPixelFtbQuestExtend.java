package com.warmpixel.ftbquestextend;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public class WarmPixelFtbQuestExtend implements ModInitializer {
    public static final String MOD_ID = "warm_pixel_ftb_quest_extend";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        QuestImportService.ensureImportDir(configDir);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("wpftb")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("import")
                    .then(Commands.argument("file", StringArgumentType.string())
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();
                            String file = StringArgumentType.getString(context, "file");
                            int result = QuestImportService.importSingle(configDir, source, file);
                            if (result > 0) {
                                source.sendSuccess(() -> Component.literal("Imported " + result + " quest pack(s)."), false);
                            }
                            return result;
                        })))
                .then(Commands.literal("import_all")
                    .executes(context -> {
                        CommandSourceStack source = context.getSource();
                        int result = QuestImportService.importAll(configDir, source);
                        if (result > 0) {
                            source.sendSuccess(() -> Component.literal("Imported " + result + " quest pack(s)."), false);
                        }
                        return result;
                    }))
            );
        });

        LOGGER.info("Warm Pixel FTB Quest Extend initialized");
    }
}
