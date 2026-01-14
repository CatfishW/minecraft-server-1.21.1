package com.warmpixel.storyadventure.core.condition;

import com.google.gson.JsonObject;
import com.warmpixel.storyadventure.instance.Instance;
import net.minecraft.server.level.ServerPlayer;

/**
 * Condition that checks time-related constraints (time pressure, limits).
 */
public class TimeCondition implements EdgeCondition {
    public static final String TYPE = "TIME";
    
    private final TimeCheck check;
    private final String timerId;
    
    public enum TimeCheck {
        /** Timer has not expired */
        WITHIN_LIMIT,
        /** Timer has expired */
        EXPIRED,
        /** Timer exists and is running */
        ACTIVE
    }
    
    public TimeCondition(String timerId, TimeCheck check) {
        this.timerId = timerId;
        this.check = check;
    }
    
    @Override
    public boolean evaluate(Instance instance, ServerPlayer player) {
        var timerState = instance.getState().getTimer(timerId);
        if (timerState == null) {
            return check == TimeCheck.EXPIRED; // No timer = expired
        }
        
        return switch (check) {
            case WITHIN_LIMIT -> !timerState.isExpired();
            case EXPIRED -> timerState.isExpired();
            case ACTIVE -> timerState.isActive();
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
        json.addProperty("timer", timerId);
        json.addProperty("check", check.name());
        return json;
    }
    
    @Override
    public String getDescription() {
        return "Timer '" + timerId + "' is " + check.name().toLowerCase().replace("_", " ");
    }
    
    public static TimeCondition fromJson(JsonObject json) {
        String timerId = json.has("timer") ? json.get("timer").getAsString() : "default";
        TimeCheck check = json.has("check") ? 
            TimeCheck.valueOf(json.get("check").getAsString().toUpperCase()) : 
            TimeCheck.WITHIN_LIMIT;
        return new TimeCondition(timerId, check);
    }
}
