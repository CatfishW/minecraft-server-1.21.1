package com.warmpixel.storyadventure.core.condition;

import com.google.gson.JsonObject;
import com.warmpixel.storyadventure.instance.Instance;
import net.minecraft.server.level.ServerPlayer;

/**
 * Condition that checks party vote results.
 */
public class VoteCondition implements EdgeCondition {
    public static final String TYPE = "VOTE";
    
    private final String voteId;
    private final String expectedChoice;
    private final VoteRequirement requirement;
    
    public enum VoteRequirement {
        /** Majority voted for this choice */
        MAJORITY,
        /** Unanimous vote for this choice */
        UNANIMOUS,
        /** Leader voted for this choice */
        LEADER,
        /** At least one member voted for this choice */
        ANY
    }
    
    public VoteCondition(String voteId, String expectedChoice, VoteRequirement requirement) {
        this.voteId = voteId;
        this.expectedChoice = expectedChoice;
        this.requirement = requirement;
    }
    
    @Override
    public boolean evaluate(Instance instance, ServerPlayer player) {
        var voteResult = instance.getState().getVoteResult(voteId);
        if (voteResult == null) return false;
        
        return switch (requirement) {
            case MAJORITY -> voteResult.getMajorityChoice().equals(expectedChoice);
            case UNANIMOUS -> voteResult.isUnanimous(expectedChoice);
            case LEADER -> voteResult.getLeaderChoice().equals(expectedChoice);
            case ANY -> voteResult.hasAnyVoteFor(expectedChoice);
        };
    }
    
    @Override
    public String getType() {
        return TYPE;
    }
    
    @Override
    public JsonObject serialize() {
        JsonObject json = new JsonObject();
        json.addProperty("type", TYPE);
        json.addProperty("vote_id", voteId);
        json.addProperty("choice", expectedChoice);
        json.addProperty("requirement", requirement.name());
        return json;
    }
    
    @Override
    public String getDescription() {
        return requirement.name().toLowerCase() + " vote for '" + expectedChoice + "'";
    }
    
    public static VoteCondition fromJson(JsonObject json) {
        String voteId = json.get("vote_id").getAsString();
        String choice = json.get("choice").getAsString();
        VoteRequirement req = json.has("requirement") ? 
            VoteRequirement.valueOf(json.get("requirement").getAsString().toUpperCase()) : 
            VoteRequirement.MAJORITY;
        return new VoteCondition(voteId, choice, req);
    }
}
