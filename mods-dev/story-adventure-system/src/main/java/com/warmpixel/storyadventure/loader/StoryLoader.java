package com.warmpixel.storyadventure.loader;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.warmpixel.storyadventure.StoryAdventureMod;
import com.warmpixel.storyadventure.core.graph.StageGraph;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Loads story definitions from JSON files.
 */
public class StoryLoader {
    
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    private final Path storiesPath;
    private final StoryRegistry registry;
    
    public StoryLoader(Path storiesPath, StoryRegistry registry) {
        this.storiesPath = storiesPath;
        this.registry = registry;
    }
    
    /**
     * Load all stories from the stories directory.
     */
    public void loadAllStories() {
        registry.clear();
        
        if (!Files.exists(storiesPath)) {
            try {
                Files.createDirectories(storiesPath);
                StoryAdventureMod.LOGGER.info("Created stories directory: {}", storiesPath);
                
                // Create example story
                createExampleStory();
            } catch (IOException e) {
                StoryAdventureMod.LOGGER.error("Failed to create stories directory", e);
            }
            return;
        }
        
        try (Stream<Path> paths = Files.walk(storiesPath)) {
            paths.filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".json"))
                .forEach(this::loadStory);
        } catch (IOException e) {
            StoryAdventureMod.LOGGER.error("Failed to scan stories directory", e);
        }
    }
    
    /**
     * Load a single story from a JSON file.
     */
    public boolean loadStory(Path storyPath) {
        StoryAdventureMod.LOGGER.debug("[StoryLoader] Attempting to load story from: {}", storyPath);
        try {
            String content = Files.readString(storyPath);
            JsonObject json = JsonParser.parseString(content).getAsJsonObject();
            
            StageGraph story = StageGraph.fromJson(json);
            StoryAdventureMod.LOGGER.debug("[StoryLoader] Parsed JSON into StageGraph: id='{}', name='{}'", story.getStoryId(), story.getName());
            
            // Validate the story
            StoryValidator validator = new StoryValidator();
            var errors = validator.validate(story);
            
            if (!errors.isEmpty()) {
                StoryAdventureMod.LOGGER.error("[StoryLoader] Story '{}' (from {}) has validation errors:", story.getStoryId(), storyPath);
                for (String error : errors) {
                    StoryAdventureMod.LOGGER.error("  - {}", error);
                }
                return false;
            }
            
            registry.register(story);
            StoryAdventureMod.LOGGER.info("[StoryLoader] Successfully loaded and registered story: {} ({} nodes, id={})", 
                story.getName(), story.getNodeCount(), story.getStoryId());
            
            return true;
        } catch (Exception e) {
            StoryAdventureMod.LOGGER.error("[StoryLoader] Failed to load story from " + storyPath, e);
            return false;
        }
    }
    
    /**
     * Reload all stories.
     */
    public void reload() {
        StoryAdventureMod.LOGGER.info("[StoryLoader] Reloading all stories...");
        loadAllStories();
    }
    
    /**
     * Save a story to disk.
     */
    public boolean saveStory(StageGraph graph) {
        Path storyPath = storiesPath.resolve(graph.getStoryId() + ".json");
        try {
            JsonObject json = graph.toJson();
            String content = GSON.toJson(json);
            Files.writeString(storyPath, content);
            StoryAdventureMod.LOGGER.info("[StoryLoader] Saved story '{}' to {}", graph.getStoryId(), storyPath);
            return true;
        } catch (Exception e) {
            StoryAdventureMod.LOGGER.error("[StoryLoader] Failed to save story '{}' to {}: {}", 
                graph.getStoryId(), storyPath, e.getMessage());
            return false;
        }
    }
    
    /**
     * Create an example story file for reference.
     */
    private void createExampleStory() {
        String exampleStory = """
            {
              "id": "example_story",
              "name": "示例故事",
              "description": "这是一个示例故事，展示了Stage Graph系统的基本用法。",
              "version": "1.0.0",
              "min_players": 1,
              "max_players": 4,
              "estimated_duration_minutes": 15,
              "entry_node": "start",
              
              "locations": {
                "start": {
                  "dimension": "minecraft:overworld",
                  "x": 0.0,
                  "y": 64.0,
                  "z": 0.0,
                  "yaw": 0.0,
                  "pitch": 0.0
                }
              },
              
              "nodes": {
                "start": {
                  "type": "CUTSCENE",
                  "data": {
                    "duration_ticks": 100,
                    "message": "欢迎来到示例故事..."
                  },
                  "edges": [
                    {"target": "first_dialogue", "conditions": []}
                  ]
                },
                
                "first_dialogue": {
                  "type": "DIALOGUE",
                  "data": {
                    "npc_template": "guide_npc",
                    "dialog_set": "introduction"
                  },
                  "edges": [
                    {
                      "target": "accept_quest",
                      "conditions": [{"type": "DIALOGUE_CHOICE", "value": "accept"}]
                    },
                    {
                      "target": "decline_ending",
                      "conditions": [{"type": "DIALOGUE_CHOICE", "value": "decline"}]
                    }
                  ]
                },
                
                "accept_quest": {
                  "type": "TASK",
                  "data": {
                    "task_type": "FETCH",
                    "objectives": [
                      {"type": "COLLECT_ITEM", "item": "minecraft:diamond", "count": 1}
                    ]
                  },
                  "edges": [
                    {"target": "victory", "conditions": [{"type": "TASK_COMPLETE"}]}
                  ]
                },
                
                "victory": {
                  "type": "CUTSCENE",
                  "data": {
                    "message": "恭喜！你完成了示例故事！",
                    "is_ending": true,
                    "ending_type": "success"
                  },
                  "edges": []
                },
                
                "decline_ending": {
                  "type": "CUTSCENE",
                  "data": {
                    "message": "也许下次再见...",
                    "is_ending": true,
                    "ending_type": "declined"
                  },
                  "edges": []
                }
              },
              
              "flags": {
                "quest_accepted": {"default": false, "persistent": true}
              },
              
              "clues": {}
            }
            """;
        
        try {
            Path examplePath = storiesPath.resolve("example_story.json");
            Files.writeString(examplePath, exampleStory);
            StoryAdventureMod.LOGGER.info("Created example story at {}", examplePath);
        } catch (IOException e) {
            StoryAdventureMod.LOGGER.error("Failed to create example story", e);
        }
    }
    
    public Path getStoriesPath() {
        return storiesPath;
    }
}
