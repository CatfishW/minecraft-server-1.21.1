package com.warmpixel.storyadventure.core.condition;

import com.google.gson.JsonObject;
import com.warmpixel.storyadventure.StoryAdventureMod;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Factory for creating EdgeCondition instances from JSON definitions.
 */
public class ConditionFactory {
    
    private static final Map<String, Function<JsonObject, EdgeCondition>> CONDITION_PARSERS = new HashMap<>();
    
    static {
        // Register built-in condition types
        registerConditionType("INVENTORY", InventoryCondition::fromJson);
        registerConditionType("FLAG", FlagCondition::fromJson);
        registerConditionType("CLUE", ClueCondition::fromJson);
        registerConditionType("RELATIONSHIP", RelationshipCondition::fromJson);
        registerConditionType("TIME", TimeCondition::fromJson);
        registerConditionType("VOTE", VoteCondition::fromJson);
        registerConditionType("DIALOGUE_CHOICE", DialogueChoiceCondition::fromJson);
        registerConditionType("TASK_COMPLETE", TaskCompleteCondition::fromJson);
        registerConditionType("TASK_FAILED", TaskFailedCondition::fromJson);
        registerConditionType("PUZZLE_SOLVED", PuzzleSolvedCondition::fromJson);
        registerConditionType("PUZZLE_FAILED", PuzzleFailedCondition::fromJson);
        registerConditionType("COMBAT_WON", CombatWonCondition::fromJson);
        registerConditionType("COMBAT_LOST", CombatLostCondition::fromJson);
        registerConditionType("COMBAT_ESCAPED", CombatEscapedCondition::fromJson);
    }
    
    /**
     * Register a custom condition type parser.
     */
    public static void registerConditionType(String type, Function<JsonObject, EdgeCondition> parser) {
        CONDITION_PARSERS.put(type.toUpperCase(), parser);
    }
    
    /**
     * Parse an EdgeCondition from JSON.
     * 
     * @param json The JSON object containing the condition definition
     * @return The parsed condition, or null if the type is unknown
     */
    public static EdgeCondition fromJson(JsonObject json) {
        if (!json.has("type")) {
            StoryAdventureMod.LOGGER.warn("Condition missing 'type' field: {}", json);
            return null;
        }
        
        String type = json.get("type").getAsString().toUpperCase();
        Function<JsonObject, EdgeCondition> parser = CONDITION_PARSERS.get(type);
        
        if (parser == null) {
            StoryAdventureMod.LOGGER.warn("Unknown condition type: {}", type);
            return null;
        }
        
        try {
            return parser.apply(json);
        } catch (Exception e) {
            StoryAdventureMod.LOGGER.error("Failed to parse condition of type {}: {}", type, e.getMessage());
            return null;
        }
    }
}
