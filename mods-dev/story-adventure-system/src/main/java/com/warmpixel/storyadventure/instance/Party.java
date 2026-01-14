package com.warmpixel.storyadventure.instance;

import java.util.*;

/**
 * Represents a party of players participating in a story instance.
 */
public class Party {
    private final UUID partyId;
    private UUID leaderId;
    private final Set<UUID> members;
    private final Map<UUID, Boolean> readyStatus;
    private final int maxSize;
    private String selectedStoryId;
    
    public Party(UUID partyId, UUID leaderId, int maxSize) {
        this.partyId = partyId;
        this.leaderId = leaderId;
        this.members = new HashSet<>();
        this.readyStatus = new HashMap<>();
        this.maxSize = maxSize;
        
        if (leaderId != null) {
            members.add(leaderId);
            readyStatus.put(leaderId, false);
        }
    }
    
    public UUID getPartyId() {
        return partyId;
    }
    
    public UUID getLeaderId() {
        return leaderId;
    }
    
    public void setLeaderId(UUID leaderId) {
        if (members.contains(leaderId)) {
            this.leaderId = leaderId;
        }
    }

    public String getSelectedStoryId() {
        return selectedStoryId;
    }

    public void setSelectedStoryId(String selectedStoryId) {
        this.selectedStoryId = selectedStoryId;
    }
    
    public boolean hasMember(UUID playerId) {
        return members.contains(playerId);
    }
    
    public boolean addMember(UUID playerId) {
        if (members.size() >= maxSize) {
            return false;
        }
        if (members.add(playerId)) {
            readyStatus.put(playerId, false);
            return true;
        }
        return false;
    }
    
    public boolean removeMember(UUID playerId) {
        boolean removed = members.remove(playerId);
        readyStatus.remove(playerId);
        
        // If leader left, promote someone else
        if (removed && playerId.equals(leaderId) && !members.isEmpty()) {
            leaderId = members.iterator().next();
        }
        
        return removed;
    }
    
    public Set<UUID> getMembers() {
        return Collections.unmodifiableSet(members);
    }
    
    public int getMemberCount() {
        return members.size();
    }
    
    public int getMaxSize() {
        return maxSize;
    }
    
    public boolean isFull() {
        return members.size() >= maxSize;
    }
    
    public boolean isEmpty() {
        return members.isEmpty();
    }
    
    public boolean isLeader(UUID playerId) {
        return playerId.equals(leaderId);
    }
    
    public void setReady(UUID playerId, boolean ready) {
        if (members.contains(playerId)) {
            readyStatus.put(playerId, ready);
        }
    }
    
    public boolean isReady(UUID playerId) {
        return readyStatus.getOrDefault(playerId, false);
    }
    
    @Override
    public String toString() {
        return String.format("Party{id=%s, leader=%s, members=%d/%d}", 
            partyId, leaderId, members.size(), maxSize);
    }
}
