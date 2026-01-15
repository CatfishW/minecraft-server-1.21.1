package com.warmpixel.storyadventure.instance;

import com.warmpixel.storyadventure.StoryAdventureMod;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;

import java.util.*;

/**
 * Tracks all state for a running instance: flags, clues, relationships,
 * timers, votes, and node results.
 */
public class InstanceState {
    private final Instance instance;
    
    // Story flags (boolean state variables)
    private final Map<String, Boolean> flags = new HashMap<>();
    
    // Discovered clues
    private final Set<String> discoveredClues = new HashSet<>();
    
    // Player-NPC relationships: playerUUID -> (npcId -> relationship value)
    private final Map<UUID, Map<String, Integer>> relationships = new HashMap<>();
    
    // Active timers
    private final Map<String, TimerState> timers = new HashMap<>();
    
    // Vote results
    private final Map<String, VoteResult> votes = new HashMap<>();
    
    // Checkpoint saved states
    private final Map<String, CheckpointState> checkpoints = new HashMap<>();
    
    // Current node result (for edge conditions)
    private String currentNodeResult = null;
    
    // Last dialogue choice made
    private String lastDialogueChoice = null;
    
    // Node visit history
    private final List<String> nodeHistory = new ArrayList<>();
    
    // Custom metadata for node handlers
    private final JsonObject metadata = new JsonObject();
    
    public InstanceState(Instance instance) {
        this.instance = instance;
    }
    
    // === Flags ===
    
    public boolean getFlag(String flagId) {
        return flags.getOrDefault(flagId, false);
    }
    
    public void setFlag(String flagId, boolean value) {
        StoryAdventureMod.LOGGER.debug("[InstanceState] Flag '{}' set to {} (instance: {})", flagId, value, instance.getInstanceId());
        flags.put(flagId, value);
    }
    
    public void toggleFlag(String flagId) {
        boolean newValue = !getFlag(flagId);
        StoryAdventureMod.LOGGER.debug("[InstanceState] Flag '{}' toggled to {} (instance: {})", flagId, newValue, instance.getInstanceId());
        flags.put(flagId, newValue);
    }
    
    // === Clues ===
    
    public boolean hasDiscoveredClue(String clueId) {
        return discoveredClues.contains(clueId);
    }
    
    public void discoverClue(String clueId) {
        if (discoveredClues.add(clueId)) {
            StoryAdventureMod.LOGGER.info("[InstanceState] Clue discovered: '{}' (instance: {})", clueId, instance.getInstanceId());
        }
    }
    
    public Set<String> getDiscoveredClues() {
        return Collections.unmodifiableSet(discoveredClues);
    }
    
    // === Relationships ===
    
    public int getRelationship(UUID playerId, String npcId) {
        return relationships.getOrDefault(playerId, Map.of()).getOrDefault(npcId, 0);
    }
    
    public void modifyRelationship(UUID playerId, String npcId, int delta) {
        int newValue = relationships.computeIfAbsent(playerId, k -> new HashMap<>())
            .merge(npcId, delta, Integer::sum);
        StoryAdventureMod.LOGGER.debug("[InstanceState] Relationship modified: player={}, npc={}, delta={}, newValue={} (instance: {})", 
            playerId, npcId, delta, newValue, instance.getInstanceId());
    }
    
    public void setRelationship(UUID playerId, String npcId, int value) {
        StoryAdventureMod.LOGGER.debug("[InstanceState] Relationship set: player={}, npc={}, value={} (instance: {})", 
            playerId, npcId, value, instance.getInstanceId());
        relationships.computeIfAbsent(playerId, k -> new HashMap<>())
            .put(npcId, value);
    }
    
    // === Timers ===
    
    public TimerState getTimer(String timerId) {
        return timers.get(timerId);
    }
    
    public void startTimer(String timerId, long durationMillis) {
        StoryAdventureMod.LOGGER.info("[InstanceState] Timer started: '{}' for {}ms (instance: {})", timerId, durationMillis, instance.getInstanceId());
        timers.put(timerId, new TimerState(System.currentTimeMillis(), durationMillis));
    }
    
    public void stopTimer(String timerId) {
        TimerState timer = timers.get(timerId);
        if (timer != null) {
            StoryAdventureMod.LOGGER.info("[InstanceState] Timer stopped: '{}' (instance: {})", timerId, instance.getInstanceId());
            timer.stop();
        }
    }
    
    // === Votes ===
    
    public VoteResult getVoteResult(String voteId) {
        return votes.get(voteId);
    }
    
    public void recordVote(String voteId, UUID playerId, String choice) {
        StoryAdventureMod.LOGGER.debug("[InstanceState] Vote recorded: id={}, player={}, choice={} (instance: {})", 
            voteId, playerId, choice, instance.getInstanceId());
        votes.computeIfAbsent(voteId, k -> new VoteResult(instance.getParty().getLeaderId()))
            .addVote(playerId, choice);
    }
    
    // === Node Results ===
    
    public void setNodeResult(String result) {
        StoryAdventureMod.LOGGER.debug("[InstanceState] Current node result set to: '{}' (instance: {})", result, instance.getInstanceId());
        this.currentNodeResult = result;
    }
    
    public void clearNodeResult() {
        StoryAdventureMod.LOGGER.debug("[InstanceState] Current node result cleared (instance: {})", instance.getInstanceId());
        this.currentNodeResult = null;
    }
    
    public boolean isCurrentNodeCompleteWith(String result) {
        return result.equals(currentNodeResult);
    }
    
    // === Dialogue ===
    
    public String getLastDialogueChoice() {
        return lastDialogueChoice;
    }
    
    public void setLastDialogueChoice(String choice) {
        StoryAdventureMod.LOGGER.debug("[InstanceState] Last dialogue choice set to: '{}' (instance: {})", choice, instance.getInstanceId());
        this.lastDialogueChoice = choice;
    }
    
    // === Node History ===
    
    public void recordNodeEntry(String nodeId) {
        StoryAdventureMod.LOGGER.debug("[InstanceState] Node history entry: '{}' (instance: {})", nodeId, instance.getInstanceId());
        nodeHistory.add(nodeId);
    }
    
    public void recordNodeExit(String nodeId) {
        StoryAdventureMod.LOGGER.debug("[InstanceState] Node history exit: '{}' (instance: {})", nodeId, instance.getInstanceId());
        // Can be extended for analytics
    }
    
    public boolean hasVisitedNode(String nodeId) {
        return nodeHistory.contains(nodeId);
    }
    
    public List<String> getNodeHistory() {
        return Collections.unmodifiableList(nodeHistory);
    }
    
    public JsonObject getMetadata() {
        return metadata;
    }
    
    // === Checkpoints ===
    
    public void saveCheckpoint(String checkpointId, CheckpointState checkpoint) {
        StoryAdventureMod.LOGGER.info("[InstanceState] Checkpoint saved: '{}' (instance: {})", checkpointId, instance.getInstanceId());
        checkpoints.put(checkpointId, checkpoint);
    }
    
    public boolean hasReachedCheckpoint(String checkpointId) {
        return checkpoints.containsKey(checkpointId);
    }
    
    public void restoreFromCheckpoint(String checkpointId) {
        CheckpointState checkpoint = checkpoints.get(checkpointId);
        if (checkpoint != null) {
            StoryAdventureMod.LOGGER.info("[InstanceState] Restoring from checkpoint: '{}' (instance: {})", checkpointId, instance.getInstanceId());
            checkpoint.restore(this);
        } else {
            StoryAdventureMod.LOGGER.warn("[InstanceState] Failed to restore from checkpoint: '{}' (not found) (instance: {})", checkpointId, instance.getInstanceId());
        }
    }
    
    // === Serialization ===
    
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        
        // Save flags
        CompoundTag flagsTag = new CompoundTag();
        flags.forEach(flagsTag::putBoolean);
        tag.put("Flags", flagsTag);
        
        // Save clues
        CompoundTag cluesTag = new CompoundTag();
        int i = 0;
        for (String clue : discoveredClues) {
            cluesTag.putString("clue_" + i++, clue);
        }
        cluesTag.putInt("count", discoveredClues.size());
        tag.put("Clues", cluesTag);
        
        // Save last dialogue choice
        if (lastDialogueChoice != null) {
            tag.putString("LastDialogueChoice", lastDialogueChoice);
        }
        
        // Save current node result
        if (currentNodeResult != null) {
            tag.putString("CurrentNodeResult", currentNodeResult);
        }
        
        return tag;
    }
    
    public void load(CompoundTag tag) {
        // Load flags
        if (tag.contains("Flags")) {
            CompoundTag flagsTag = tag.getCompound("Flags");
            for (String key : flagsTag.getAllKeys()) {
                flags.put(key, flagsTag.getBoolean(key));
            }
        }
        
        // Load clues
        if (tag.contains("Clues")) {
            CompoundTag cluesTag = tag.getCompound("Clues");
            int count = cluesTag.getInt("count");
            for (int j = 0; j < count; j++) {
                discoveredClues.add(cluesTag.getString("clue_" + j));
            }
        }
        
        // Load last dialogue choice
        if (tag.contains("LastDialogueChoice")) {
            lastDialogueChoice = tag.getString("LastDialogueChoice");
        }
        
        // Load current node result
        if (tag.contains("CurrentNodeResult")) {
            currentNodeResult = tag.getString("CurrentNodeResult");
        }
    }
    
    // === Inner Classes ===
    
    /**
     * Timer state tracking.
     */
    public static class TimerState {
        private final long startTime;
        private final long duration;
        private boolean stopped = false;
        private long stoppedAt = 0;
        
        public TimerState(long startTime, long duration) {
            this.startTime = startTime;
            this.duration = duration;
        }
        
        public boolean isExpired() {
            if (stopped) return stoppedAt > startTime + duration;
            return System.currentTimeMillis() > startTime + duration;
        }
        
        public boolean isActive() {
            return !stopped && !isExpired();
        }
        
        public long getRemainingMillis() {
            if (stopped) return Math.max(0, (startTime + duration) - stoppedAt);
            return Math.max(0, (startTime + duration) - System.currentTimeMillis());
        }
        
        public void stop() {
            if (!stopped) {
                stopped = true;
                stoppedAt = System.currentTimeMillis();
            }
        }
    }
    
    /**
     * Vote result tracking.
     */
    public static class VoteResult {
        private final UUID leaderId;
        private final Map<UUID, String> votes = new HashMap<>();
        
        public VoteResult(UUID leaderId) {
            this.leaderId = leaderId;
        }
        
        public void addVote(UUID playerId, String choice) {
            votes.put(playerId, choice);
        }
        
        public String getMajorityChoice() {
            Map<String, Integer> counts = new HashMap<>();
            for (String choice : votes.values()) {
                counts.merge(choice, 1, Integer::sum);
            }
            return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("");
        }
        
        public String getLeaderChoice() {
            return votes.getOrDefault(leaderId, "");
        }
        
        public boolean isUnanimous(String choice) {
            return votes.values().stream().allMatch(choice::equals);
        }
        
        public boolean hasAnyVoteFor(String choice) {
            return votes.containsValue(choice);
        }
    }
    
    /**
     * Checkpoint saved state.
     */
    public static class CheckpointState {
        private final Map<String, Boolean> savedFlags;
        private final Set<String> savedClues;
        
        public CheckpointState(InstanceState state) {
            this.savedFlags = new HashMap<>(state.flags);
            this.savedClues = new HashSet<>(state.discoveredClues);
        }
        
        public void restore(InstanceState state) {
            state.flags.clear();
            state.flags.putAll(savedFlags);
            state.discoveredClues.clear();
            state.discoveredClues.addAll(savedClues);
            state.clearNodeResult();
        }
    }
}
