package com.warmpixel.ftbquestextend;

import com.google.gson.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

public final class QuestImportService {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String IMPORT_DIR_NAME = "warm-pixel-ftb-quest-extend/import";
    private static final String FTB_QUESTS_DIR = "ftbquests/quests";

    private QuestImportService() {}

    public static void ensureImportDir(Path configDir) {
        try {
            Path importDir = configDir.resolve(IMPORT_DIR_NAME);
            Files.createDirectories(importDir);
        } catch (IOException e) {
            WarmPixelFtbQuestExtend.LOGGER.warn("Failed to create import directory", e);
        }
    }

    public static int importSingle(Path configDir, CommandSourceStack source, String fileArg) {
        Path importDir = configDir.resolve(IMPORT_DIR_NAME);
        Path filePath = resolveFile(importDir, fileArg);
        if (!filePath.toString().endsWith(".json")) {
            filePath = filePath.resolveSibling(filePath.getFileName().toString() + ".json");
        }

        if (!Files.exists(filePath)) {
            source.sendFailure(Component.literal("Quest import file not found: " + filePath));
            return 0;
        }

        try {
            importFile(configDir, source, filePath);
            return 1;
        } catch (Exception e) {
            WarmPixelFtbQuestExtend.LOGGER.error("Failed to import quests from {}", filePath, e);
            source.sendFailure(Component.literal("Quest import failed: " + e.getMessage()));
            return 0;
        }
    }

    public static int importAll(Path configDir, CommandSourceStack source) {
        Path importDir = configDir.resolve(IMPORT_DIR_NAME);
        if (!Files.exists(importDir)) {
            source.sendFailure(Component.literal("Import directory not found: " + importDir));
            return 0;
        }

        int count = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(importDir, "*.json")) {
            for (Path file : stream) {
                try {
                    importFile(configDir, source, file);
                    count++;
                } catch (Exception e) {
                    WarmPixelFtbQuestExtend.LOGGER.error("Failed to import quests from {}", file, e);
                    source.sendFailure(Component.literal("Quest import failed for " + file.getFileName() + ": " + e.getMessage()));
                }
            }
        } catch (IOException e) {
            WarmPixelFtbQuestExtend.LOGGER.error("Failed to read import directory", e);
            source.sendFailure(Component.literal("Quest import failed: " + e.getMessage()));
        }
        return count;
    }

    private static void importFile(Path configDir, CommandSourceStack source, Path filePath) throws IOException {
        JsonObject root = readJson(filePath);
        JsonArray chaptersArray = getArray(root, "chapters");
        if (chaptersArray == null || chaptersArray.isEmpty()) {
            throw new IllegalArgumentException("No chapters found in JSON");
        }

        Path ftbDir = configDir.resolve(FTB_QUESTS_DIR);
        Path chaptersDir = ftbDir.resolve("chapters");
        Path langDir = ftbDir.resolve("lang");
        Path chapterGroupsPath = ftbDir.resolve("chapter_groups.snbt");
        Files.createDirectories(chaptersDir);
        Files.createDirectories(langDir);

        CompoundTag zhLang = readOrCreate(langDir.resolve("zh_cn.snbt"));
        CompoundTag enLang = readOrCreate(langDir.resolve("en_us.snbt"));
        CompoundTag chapterGroups = readOrCreate(chapterGroupsPath);

        String groupId = resolveChapterGroup(root, zhLang, enLang, chapterGroups);

        int chapterCount = 0;
        int questCount = 0;

        for (JsonElement chapterElement : chaptersArray) {
            if (!chapterElement.isJsonObject()) {
                continue;
            }

            JsonObject chapterObj = chapterElement.getAsJsonObject();
            String chapterKey = getString(chapterObj, "id", "chapter_" + chapterCount);
            String chapterFileName = sanitizeKey(chapterKey);
            if (chapterFileName.isEmpty()) {
                chapterFileName = "wp_" + toHexId(chapterKey);
            }

            String chapterId = toHexId(chapterKey);
            String iconId = getString(chapterObj, "icon", "minecraft:book");
            int orderIndex = getInt(chapterObj, "order", chapterCount);

            LocalizedText chapterTitle = parseLocalizedText(chapterObj.get("title"), chapterKey);
            LocalizedText chapterSubtitle = parseLocalizedText(chapterObj.get("subtitle"), "");

            updateLang(enLang, "chapters." + chapterFileName, chapterTitle.en);
            updateLang(zhLang, "chapters." + chapterFileName, chapterTitle.zh);
            if (!chapterSubtitle.en.isEmpty()) {
                updateLang(enLang, "chapters." + chapterFileName + ".subtitle", chapterSubtitle.en);
            }
            if (!chapterSubtitle.zh.isEmpty()) {
                updateLang(zhLang, "chapters." + chapterFileName + ".subtitle", chapterSubtitle.zh);
            }

            JsonArray questsArray = getArray(chapterObj, "quests");
            if (questsArray == null) {
                questsArray = new JsonArray();
            }

            List<QuestSeed> questSeeds = parseQuestSeeds(questsArray);
            Map<String, String> questIdMap = new HashMap<>();
            for (QuestSeed seed : questSeeds) {
                questIdMap.put(seed.key, toHexId(chapterKey + ":" + seed.key));
            }

            applyAutoLayout(questSeeds);

            ListTag questList = new ListTag();
            int questIndex = 0;
            for (QuestSeed seed : questSeeds) {
                String questId = questIdMap.get(seed.key);
                CompoundTag questTag = new CompoundTag();
                questTag.putString("id", questId);

                double x = seed.x != null ? seed.x : questIndex * 6.0;
                double y = seed.y != null ? seed.y : 0.0;
                questTag.put("x", DoubleTag.valueOf(x));
                questTag.put("y", DoubleTag.valueOf(y));

                String shape = getString(seed.source, "shape", "circle");
                double size = getDouble(seed.source, "size", 2.0);
                questTag.putString("shape", shape);
                questTag.put("size", DoubleTag.valueOf(size));

                boolean invisible = getBoolean(seed.source, "invisible", false);
                if (invisible) {
                    questTag.putBoolean("invisible", true);
                }

                List<String> deps = seed.dependencies;
                if (!deps.isEmpty()) {
                    ListTag depList = new ListTag();
                    for (String depKey : deps) {
                        String depId = questIdMap.get(depKey);
                        if (depId != null) {
                            depList.add(StringTag.valueOf(depId));
                        }
                    }
                    if (!depList.isEmpty()) {
                        questTag.put("dependencies", depList);
                    }
                }

                ListTag tasks = buildTasks(seed, chapterKey, questId);
                if (!tasks.isEmpty()) {
                    questTag.put("tasks", tasks);
                }

                ListTag rewards = buildRewards(seed, chapterKey, questId);
                if (!rewards.isEmpty()) {
                    questTag.put("rewards", rewards);
                }

                questList.add(questTag);
                questIndex++;
                questCount++;

                writeQuestLang(seed, questId, enLang, zhLang);
            }

            CompoundTag chapterTag = new CompoundTag();
            chapterTag.putString("filename", chapterFileName);
            chapterTag.putString("id", chapterId);
            chapterTag.putString("group", groupId);
            chapterTag.putInt("order_index", orderIndex);
            chapterTag.putBoolean("default_hide_dependency_lines", false);
            chapterTag.putString("default_quest_shape", "");
            chapterTag.put("quest_links", new ListTag());
            chapterTag.put("quests", questList);

            CompoundTag iconTag = new CompoundTag();
            iconTag.putString("id", iconId);
            chapterTag.put("icon", iconTag);

            Path chapterPath = chaptersDir.resolve(chapterFileName + ".snbt");
            writeSnbt(chapterPath, chapterTag);
            chapterCount++;
        }

        writeSnbt(langDir.resolve("zh_cn.snbt"), zhLang);
        writeSnbt(langDir.resolve("en_us.snbt"), enLang);
        writeSnbt(chapterGroupsPath, chapterGroups);

        reloadQuests(source);

        String message = "Imported " + chapterCount + " chapters, " + questCount + " quests from " + filePath.getFileName();
        source.sendSuccess(() -> Component.literal(message), false);
    }

    private static List<QuestSeed> parseQuestSeeds(JsonArray questsArray) {
        List<QuestSeed> seeds = new ArrayList<>();
        for (JsonElement questElement : questsArray) {
            if (!questElement.isJsonObject()) {
                continue;
            }
            JsonObject questObj = questElement.getAsJsonObject();
            String questKey = getString(questObj, "id", "quest_" + seeds.size());
            List<String> deps = readStringArray(questObj.get("dependencies"));
            Double x = questObj.has("x") ? questObj.get("x").getAsDouble() : null;
            Double y = questObj.has("y") ? questObj.get("y").getAsDouble() : null;
            seeds.add(new QuestSeed(questKey, questObj, deps, x, y));
        }
        return seeds;
    }

    private static void applyAutoLayout(List<QuestSeed> seeds) {
        Map<String, QuestSeed> seedMap = seeds.stream().collect(Collectors.toMap(seed -> seed.key, seed -> seed));
        Map<String, Integer> depthMap = new HashMap<>();

        for (QuestSeed seed : seeds) {
            computeDepth(seed, seedMap, depthMap, new HashSet<>());
        }

        Map<Integer, Integer> rowCounts = new HashMap<>();
        for (QuestSeed seed : seeds) {
            if (seed.x != null && seed.y != null) {
                continue;
            }
            int depth = depthMap.getOrDefault(seed.key, 0);
            int rowIndex = rowCounts.getOrDefault(depth, 0);
            rowCounts.put(depth, rowIndex + 1);

            if (seed.x == null) {
                seed.x = depth * 6.0;
            }
            if (seed.y == null) {
                seed.y = rowIndex * 3.0;
            }
        }
    }

    private static int computeDepth(QuestSeed seed, Map<String, QuestSeed> seedMap, Map<String, Integer> depthMap, Set<String> stack) {
        if (depthMap.containsKey(seed.key)) {
            return depthMap.get(seed.key);
        }
        if (!stack.add(seed.key)) {
            depthMap.put(seed.key, 0);
            return 0;
        }
        int depth = 0;
        for (String depKey : seed.dependencies) {
            QuestSeed depSeed = seedMap.get(depKey);
            if (depSeed != null) {
                depth = Math.max(depth, 1 + computeDepth(depSeed, seedMap, depthMap, stack));
            }
        }
        stack.remove(seed.key);
        depthMap.put(seed.key, depth);
        return depth;
    }

    private static ListTag buildTasks(QuestSeed seed, String chapterKey, String questId) {
        JsonArray tasksArray = getArray(seed.source, "tasks");
        if (tasksArray == null) {
            return new ListTag();
        }

        ListTag tasks = new ListTag();
        int taskIndex = 0;
        for (JsonElement taskElement : tasksArray) {
            if (!taskElement.isJsonObject()) {
                continue;
            }
            JsonObject taskObj = taskElement.getAsJsonObject();
            String type = getString(taskObj, "type", "checkmark");
            CompoundTag taskTag = new CompoundTag();
            taskTag.putString("id", toHexId(chapterKey + ":" + questId + ":task:" + taskIndex));
            taskTag.putString("type", type);

            if ("item".equals(type)) {
                String item = getString(taskObj, "item", "minecraft:stone");
                int count = getInt(taskObj, "count", 1);
                CompoundTag itemTag = new CompoundTag();
                itemTag.putString("id", item);
                itemTag.putInt("count", count);
                taskTag.put("item", itemTag);
            } else if ("kill".equals(type)) {
                String entity = getString(taskObj, "entity", "minecraft:zombie");
                long value = getLong(taskObj, "count", 1L);
                taskTag.putString("entity", entity);
                taskTag.put("value", LongTag.valueOf(value));
            } else if ("xp".equals(type)) {
                int xp = getInt(taskObj, "xp", 10);
                taskTag.putInt("xp", xp);
            }

            tasks.add(taskTag);
            taskIndex++;
        }

        return tasks;
    }

    private static ListTag buildRewards(QuestSeed seed, String chapterKey, String questId) {
        JsonArray rewardsArray = getArray(seed.source, "rewards");
        if (rewardsArray == null) {
            return new ListTag();
        }

        ListTag rewards = new ListTag();
        int rewardIndex = 0;
        for (JsonElement rewardElement : rewardsArray) {
            if (!rewardElement.isJsonObject()) {
                continue;
            }
            JsonObject rewardObj = rewardElement.getAsJsonObject();
            String type = getString(rewardObj, "type", "item");
            CompoundTag rewardTag = new CompoundTag();
            rewardTag.putString("id", toHexId(chapterKey + ":" + questId + ":reward:" + rewardIndex));
            rewardTag.putString("type", type);

            if ("item".equals(type)) {
                String item = getString(rewardObj, "item", "minecraft:stone");
                int count = getInt(rewardObj, "count", 1);
                CompoundTag itemTag = new CompoundTag();
                itemTag.putString("id", item);
                itemTag.putInt("count", count);
                rewardTag.put("item", itemTag);
            } else if ("xp".equals(type)) {
                int xp = getInt(rewardObj, "xp", 10);
                rewardTag.putInt("xp", xp);
            }

            rewards.add(rewardTag);
            rewardIndex++;
        }

        return rewards;
    }

    private static void writeQuestLang(QuestSeed seed, String questId, CompoundTag enLang, CompoundTag zhLang) {
        LocalizedText title = parseLocalizedText(seed.source.get("title"), seed.key);
        LocalizedText subtitle = parseLocalizedText(seed.source.get("subtitle"), "");
        LocalizedLines desc = parseLocalizedLines(seed.source.get("description"), "");

        updateLang(enLang, "quests." + questId + ".title", title.en);
        updateLang(zhLang, "quests." + questId + ".title", title.zh);

        if (!subtitle.en.isEmpty()) {
            updateLang(enLang, "quests." + questId + ".subtitle", subtitle.en);
        }
        if (!subtitle.zh.isEmpty()) {
            updateLang(zhLang, "quests." + questId + ".subtitle", subtitle.zh);
        }

        if (!desc.en.isEmpty()) {
            enLang.put("quests." + questId + ".description", toStringTagList(desc.en));
        }
        if (!desc.zh.isEmpty()) {
            zhLang.put("quests." + questId + ".description", toStringTagList(desc.zh));
        }
    }

    private static void reloadQuests(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        try {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput().withPermission(4), "ftbquests reload");
        } catch (Exception e) {
            WarmPixelFtbQuestExtend.LOGGER.warn("Failed to run ftbquests reload", e);
        }
    }

    private static JsonObject readJson(Path filePath) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
            JsonElement element = JsonParser.parseReader(reader);
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("Root JSON must be an object");
            }
            return element.getAsJsonObject();
        }
    }

    private static String resolveChapterGroup(JsonObject root, CompoundTag zhLang, CompoundTag enLang, CompoundTag chapterGroups) {
        JsonObject groupObj = getObject(root, "chapter_group");
        if (groupObj == null) {
            String fallback = firstGroupId(chapterGroups);
            if (fallback != null) {
                return fallback;
            }
            String defaultId = toHexId("wp_default_group");
            addChapterGroup(chapterGroups, defaultId);
            updateLang(enLang, "chapter_group." + defaultId + ".title", "AI Import");
            updateLang(zhLang, "chapter_group." + defaultId + ".title", "AI Import");
            return defaultId;
        }

        String groupKey = getString(groupObj, "id", "wp_import_group");
        String groupId = toHexId(groupKey);
        addChapterGroup(chapterGroups, groupId);

        LocalizedText title = parseLocalizedText(groupObj.get("title"), "AI Import");
        updateLang(enLang, "chapter_group." + groupId + ".title", title.en);
        updateLang(zhLang, "chapter_group." + groupId + ".title", title.zh);

        return groupId;
    }

    private static void addChapterGroup(CompoundTag chapterGroups, String groupId) {
        ListTag groups = chapterGroups.getList("chapter_groups", 10);
        for (int i = 0; i < groups.size(); i++) {
            CompoundTag entry = groups.getCompound(i);
            if (groupId.equals(entry.getString("id"))) {
                chapterGroups.put("chapter_groups", groups);
                return;
            }
        }
        CompoundTag newGroup = new CompoundTag();
        newGroup.putString("id", groupId);
        groups.add(newGroup);
        chapterGroups.put("chapter_groups", groups);
    }

    private static String firstGroupId(CompoundTag chapterGroups) {
        ListTag groups = chapterGroups.getList("chapter_groups", 10);
        if (!groups.isEmpty()) {
            return groups.getCompound(0).getString("id");
        }
        return null;
    }

    private static CompoundTag readOrCreate(Path path) {
        if (Files.exists(path)) {
            try {
                String content = Files.readString(path, StandardCharsets.UTF_8);
                Tag tag = TagParser.parseTag(content);
                if (tag instanceof CompoundTag compound) {
                    return compound;
                }
            } catch (Exception e) {
                WarmPixelFtbQuestExtend.LOGGER.warn("Failed to parse SNBT: {}", path, e);
            }
        }
        return new CompoundTag();
    }

    private static void writeSnbt(Path path, CompoundTag tag) throws IOException {
        String content = tag.toString();
        Files.writeString(path, content + System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static void updateLang(CompoundTag lang, String key, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        lang.putString(key, value);
    }

    private static ListTag toStringTagList(List<String> lines) {
        ListTag list = new ListTag();
        for (String line : lines) {
            if (!line.isEmpty()) {
                list.add(StringTag.valueOf(line));
            }
        }
        return list;
    }

    private static String toHexId(String seed) {
        if (seed == null) {
            return "0000000000000000";
        }
        String trimmed = seed.trim();
        if (trimmed.matches("[0-9a-fA-F]{16}")) {
            return trimmed.toUpperCase(Locale.ROOT);
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(trimmed.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02X", b));
            }
            return sb.substring(0, 16);
        } catch (Exception e) {
            String fallback = Integer.toHexString(trimmed.hashCode()).toUpperCase(Locale.ROOT);
            if (fallback.length() < 16) {
                return String.format("%16s", fallback).replace(' ', '0');
            }
            return fallback.substring(0, 16);
        }
    }

    private static String sanitizeKey(String key) {
        if (key == null) {
            return "";
        }
        String sanitized = key.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
        if (sanitized.isEmpty()) {
            return "";
        }
        if (!sanitized.startsWith("wp_")) {
            sanitized = "wp_" + sanitized;
        }
        return sanitized;
    }

    private static Path resolveFile(Path importDir, String fileArg) {
        Path path = Paths.get(fileArg);
        if (path.isAbsolute()) {
            return path;
        }
        return importDir.resolve(fileArg);
    }

    private static JsonObject getObject(JsonObject obj, String key) {
        if (obj != null && obj.has(key) && obj.get(key).isJsonObject()) {
            return obj.getAsJsonObject(key);
        }
        return null;
    }

    private static JsonArray getArray(JsonObject obj, String key) {
        if (obj != null && obj.has(key) && obj.get(key).isJsonArray()) {
            return obj.getAsJsonArray(key);
        }
        return null;
    }

    private static String getString(JsonObject obj, String key, String fallback) {
        if (obj != null && obj.has(key) && obj.get(key).isJsonPrimitive()) {
            return obj.get(key).getAsString();
        }
        return fallback;
    }

    private static int getInt(JsonObject obj, String key, int fallback) {
        if (obj != null && obj.has(key) && obj.get(key).isJsonPrimitive()) {
            return obj.get(key).getAsInt();
        }
        return fallback;
    }

    private static long getLong(JsonObject obj, String key, long fallback) {
        if (obj != null && obj.has(key) && obj.get(key).isJsonPrimitive()) {
            return obj.get(key).getAsLong();
        }
        return fallback;
    }

    private static double getDouble(JsonObject obj, String key, double fallback) {
        if (obj != null && obj.has(key) && obj.get(key).isJsonPrimitive()) {
            return obj.get(key).getAsDouble();
        }
        return fallback;
    }

    private static boolean getBoolean(JsonObject obj, String key, boolean fallback) {
        if (obj != null && obj.has(key) && obj.get(key).isJsonPrimitive()) {
            return obj.get(key).getAsBoolean();
        }
        return fallback;
    }

    private static List<String> readStringArray(JsonElement element) {
        List<String> list = new ArrayList<>();
        if (element == null) {
            return list;
        }
        if (element.isJsonArray()) {
            for (JsonElement entry : element.getAsJsonArray()) {
                if (entry.isJsonPrimitive()) {
                    list.add(entry.getAsString());
                }
            }
        } else if (element.isJsonPrimitive()) {
            list.add(element.getAsString());
        }
        return list;
    }

    private static LocalizedText parseLocalizedText(JsonElement element, String fallback) {
        if (element == null || element.isJsonNull()) {
            return new LocalizedText(fallback, fallback);
        }
        if (element.isJsonPrimitive()) {
            String value = element.getAsString();
            return new LocalizedText(value, value);
        }
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            String zh = getString(obj, "zh_cn", fallback);
            String en = getString(obj, "en_us", fallback);
            return new LocalizedText(zh, en);
        }
        return new LocalizedText(fallback, fallback);
    }

    private static LocalizedLines parseLocalizedLines(JsonElement element, String fallback) {
        if (element == null || element.isJsonNull()) {
            return new LocalizedLines(Collections.emptyList(), Collections.emptyList());
        }
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            List<String> zh = parseLines(obj.get("zh_cn"), fallback);
            List<String> en = parseLines(obj.get("en_us"), fallback);
            return new LocalizedLines(zh, en);
        }
        List<String> lines = parseLines(element, fallback);
        return new LocalizedLines(lines, lines);
    }

    private static List<String> parseLines(JsonElement element, String fallback) {
        if (element == null || element.isJsonNull()) {
            return fallback.isEmpty() ? Collections.emptyList() : Collections.singletonList(fallback);
        }
        if (element.isJsonPrimitive()) {
            String value = element.getAsString();
            if (value.isEmpty()) {
                return Collections.emptyList();
            }
            return Collections.singletonList(value);
        }
        if (element.isJsonArray()) {
            List<String> lines = new ArrayList<>();
            for (JsonElement entry : element.getAsJsonArray()) {
                if (entry.isJsonPrimitive()) {
                    String value = entry.getAsString();
                    if (!value.isEmpty()) {
                        lines.add(value);
                    }
                }
            }
            return lines;
        }
        return fallback.isEmpty() ? Collections.emptyList() : Collections.singletonList(fallback);
    }

    private record LocalizedText(String zh, String en) {}

    private record LocalizedLines(List<String> zh, List<String> en) {}

    private static final class QuestSeed {
        private final String key;
        private final JsonObject source;
        private final List<String> dependencies;
        private Double x;
        private Double y;

        private QuestSeed(String key, JsonObject source, List<String> dependencies, Double x, Double y) {
            this.key = key;
            this.source = source;
            this.dependencies = dependencies;
            this.x = x;
            this.y = y;
        }
    }
}
