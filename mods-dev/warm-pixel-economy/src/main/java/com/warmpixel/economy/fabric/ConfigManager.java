package com.warmpixel.economy.fabric;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE = "warm-pixel-economy.json";

    private ConfigManager() {
    }

    public static EconomyConfig load() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path configPath = configDir.resolve(CONFIG_FILE);

        if (!Files.exists(configPath)) {
            EconomyConfig config = new EconomyConfig();
            save(config, configPath);
            return config;
        }

        try (BufferedReader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            EconomyConfig config = GSON.fromJson(reader, EconomyConfig.class);
            if (config == null) {
                config = new EconomyConfig();
            }
            return config;
        } catch (IOException e) {
            EconomyConfig config = new EconomyConfig();
            save(config, configPath);
            return config;
        }
    }

    public static void save(EconomyConfig config) {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path configPath = configDir.resolve(CONFIG_FILE);
        save(config, configPath);
    }

    private static void save(EconomyConfig config, Path configPath) {
        try {
            Files.createDirectories(configPath.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException ignored) {
            // Keep defaults if config cannot be saved.
        }
    }
}
