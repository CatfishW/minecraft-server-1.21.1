package com.warmpixel.storyadventure.loader;

import com.warmpixel.storyadventure.core.graph.StageGraph;

import java.util.*;

/**
 * Registry for all loaded story definitions.
 */
public class StoryRegistry {
    
    private final Map<String, StageGraph> stories = new HashMap<>();
    
    /**
     * Register a story.
     */
    public void register(StageGraph story) {
        stories.put(story.getStoryId(), story);
    }
    
    /**
     * Unregister a story.
     */
    public void unregister(String storyId) {
        stories.remove(storyId);
    }
    
    /**
     * Get a story by its ID.
     */
    public StageGraph getStory(String storyId) {
        return stories.get(storyId);
    }
    
    /**
     * Check if a story exists.
     */
    public boolean hasStory(String storyId) {
        return stories.containsKey(storyId);
    }
    
    /**
     * Get all registered stories.
     */
    public Collection<StageGraph> getAllStories() {
        return Collections.unmodifiableCollection(stories.values());
    }
    
    /**
     * Get all story IDs.
     */
    public Set<String> getStoryIds() {
        return Collections.unmodifiableSet(stories.keySet());
    }
    
    /**
     * Get the number of registered stories.
     */
    public int getStoryCount() {
        return stories.size();
    }
    
    /**
     * Clear all registered stories.
     */
    public void clear() {
        stories.clear();
    }
}
