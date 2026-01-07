package com.novus.pay;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class PaymentConfig {
    private static final File CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("novus_pay.json").toFile();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static ConfigData DATA = new ConfigData();

    public static class ConfigData {
        public String internalApiUrl = "http://localhost:8000";
        public String apiKey = "novus-secure-setup-key-123";
        public double exchangeRate = 1.0; // 1 CNY = X Coins
    }

    public static void load() {
        if (!CONFIG_FILE.exists()) {
            save(); // Save defaults
            return;
        }

        try (FileReader reader = new FileReader(CONFIG_FILE)) {
            DATA = GSON.fromJson(reader, ConfigData.class);
        } catch (IOException e) {
            NovusPayMod.LOGGER.error("Failed to load config", e);
        }
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(DATA, writer);
        } catch (IOException e) {
            NovusPayMod.LOGGER.error("Failed to save config", e);
        }
    }
}
