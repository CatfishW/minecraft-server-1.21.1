package com.warmpixel.npcbusdriver;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.core.BlockPos;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class PathManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH_DIR = Paths.get("config", "npcbusdriver", "paths");

    public static class PathPoint {
        public int x, y, z;
        public PathPoint(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
    }

    public static void savePath(String name, List<BlockPos> points) throws IOException {
        if (!Files.exists(PATH_DIR)) Files.createDirectories(PATH_DIR);
        
        List<PathPoint> serializablePoints = new ArrayList<>();
        for (BlockPos p : points) serializablePoints.add(new PathPoint(p.getX(), p.getY(), p.getZ()));
        
        Path file = PATH_DIR.resolve(name + ".json");
        try (Writer writer = Files.newBufferedWriter(file)) {
            GSON.toJson(serializablePoints, writer);
        }
    }

    public static List<BlockPos> loadPath(String name) {
        Path file = PATH_DIR.resolve(name + ".json");
        if (!Files.exists(file)) return null;
        try (Reader reader = Files.newBufferedReader(file)) {
            PathPoint[] points = GSON.fromJson(reader, PathPoint[].class);
            List<BlockPos> blockPoints = new ArrayList<>();
            for (PathPoint p : points) blockPoints.add(new BlockPos(p.x, p.y, p.z));
            return blockPoints;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
