package com.warmpixel.bleedgore;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_NAME = "bleed_gore.json";

    private ConfigManager() {
    }

    public static BleedGoreConfig load(Logger logger) {
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_NAME);
        BleedGoreConfig config = new BleedGoreConfig();
        if (Files.exists(configPath)) {
            try (BufferedReader reader = Files.newBufferedReader(configPath)) {
                BleedGoreConfig loaded = GSON.fromJson(reader, BleedGoreConfig.class);
                if (loaded != null) {
                    config = loaded;
                }
            } catch (IOException e) {
                logger.warn("Failed to read bleed gore config, using defaults.", e);
            }
        }
        save(logger, configPath, config);
        return config;
    }

    private static void save(Logger logger, Path configPath, BleedGoreConfig config) {
        try {
            Files.createDirectories(configPath.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(configPath)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            logger.warn("Failed to write bleed gore config.", e);
        }
    }
}
