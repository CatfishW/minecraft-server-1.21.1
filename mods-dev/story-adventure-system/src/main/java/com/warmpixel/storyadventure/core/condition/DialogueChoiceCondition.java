package com.warmpixel.storyadventure.core.condition;

import com.warmpixel.storyadventure.StoryAdventureMod;
import com.google.gson.JsonObject;
import com.warmpixel.storyadventure.instance.Instance;
import net.minecraft.server.level.ServerPlayer;

/**
 * Condition that checks if a specific dialogue choice was made.
 */
public class DialogueChoiceCondition implements EdgeCondition {
    public static final String TYPE = "DIALOGUE_CHOICE";
    
    private final String choiceId;
    
    public DialogueChoiceCondition(String choiceId) {
        this.choiceId = choiceId;
    }
    
    @Override
    public boolean evaluate(Instance instance, ServerPlayer player) {
        String lastChoice = instance.getState().getLastDialogueChoice();
        boolean result = choiceId.equals(lastChoice);
        com.warmpixel.storyadventure.StoryAdventureMod.LOGGER.debug("[DialogueChoiceCondition] Evaluating: required='{}', last='{}'. Result={}", 
            choiceId, lastChoice, result);
        return result;
    }
    
    @Override
    public String getType() {
        return TYPE;
    }
    
    @Override
    public JsonObject serialize() {
        JsonObject json = new JsonObject();
        json.addProperty("type", TYPE);
        json.addProperty("value", choiceId);
        return json;
    }
    
    @Override
    public String getDescription() {
        return "Chose '" + choiceId + "'";
    }
    
    public static DialogueChoiceCondition fromJson(JsonObject json) {
        String choice = json.get("value").getAsString();
        return new DialogueChoiceCondition(choice);
    }
}
