package com.warmpixel.spawnpoint;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

public class SpawnPointConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("spawnpoint.json");

    public String world = "minecraft:overworld";
    public double x = 0;
    public double y = 100;
    public double z = 0;
    public float yaw = 0;
    public float pitch = 0;
    public boolean enabled = false;

    public void save() {
        try (FileWriter writer = new FileWriter(CONFIG_PATH.toFile())) {
            GSON.toJson(this, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static SpawnPointConfig load() {
        File file = CONFIG_PATH.toFile();
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                return GSON.fromJson(reader, SpawnPointConfig.class);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return new SpawnPointConfig();
    }
}
