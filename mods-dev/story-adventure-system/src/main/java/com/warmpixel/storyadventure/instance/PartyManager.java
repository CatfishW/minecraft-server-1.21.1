package com.warmpixel.storyadventure.instance;

import com.warmpixel.storyadventure.StoryAdventureMod;

import java.util.*;

/**
 * Manages party creation, membership, and player-party associations.
 */
public class PartyManager {
    
    // All active parties
    private final Map<UUID, Party> parties = new HashMap<>();
    
    // Player to party mapping
    private final Map<UUID, UUID> playerPartyMap = new HashMap<>();
    
    /**
     * Create a new party with the given player as leader.
     */
    public Party createParty(UUID leaderId, int maxSize) {
        StoryAdventureMod.LOGGER.info("[PartyManager] Creating new party: leaderId={}, maxSize={}", leaderId, maxSize);
        
        // Leave existing party if any
        leaveParty(leaderId);
        
        UUID partyId = UUID.randomUUID();
        Party party = new Party(partyId, leaderId, maxSize);
        parties.put(partyId, party);
        playerPartyMap.put(leaderId, partyId);
        
        StoryAdventureMod.LOGGER.info("[PartyManager] Party created successfully: partyId={}, leaderId={}", partyId, leaderId);
        
        return party;
    }
    
    /**
     * Get the party a player is in.
     */
    public Party getPlayerParty(UUID playerId) {
        UUID partyId = playerPartyMap.get(playerId);
        return partyId != null ? parties.get(partyId) : null;
    }
    
    /**
     * Get a party by its ID.
     */
    public Party getParty(UUID partyId) {
        return parties.get(partyId);
    }
    
    /**
     * Invite a player to join a party.
     * 
     * @return true if the player was successfully added
     */
    public boolean joinParty(UUID playerId, UUID partyId) {
        StoryAdventureMod.LOGGER.debug("[PartyManager] Player {} attempting to join party {}", playerId, partyId);
        
        Party party = parties.get(partyId);
        if (party == null) {
            StoryAdventureMod.LOGGER.warn("[PartyManager] Join FAILED: Party {} not found", partyId);
            return false;
        }
        
        if (party.isFull()) {
            StoryAdventureMod.LOGGER.warn("[PartyManager] Join FAILED: Party {} is full ({} members)", partyId, party.getMemberCount());
            return false;
        }
        
        // Leave existing party if any
        leaveParty(playerId);
        
        if (party.addMember(playerId)) {
            playerPartyMap.put(playerId, partyId);
            StoryAdventureMod.LOGGER.info("[PartyManager] Player {} successfully joined party {}", playerId, partyId);
            return true;
        }
        
        StoryAdventureMod.LOGGER.warn("[PartyManager] Join FAILED: addMember returned false for player {} and party {}", playerId, partyId);
        return false;
    }
    
    /**
     * Remove a player from their current party.
     */
    public void leaveParty(UUID playerId) {
        UUID partyId = playerPartyMap.remove(playerId);
        if (partyId != null) {
            Party party = parties.get(partyId);
            if (party != null) {
                StoryAdventureMod.LOGGER.info("[PartyManager] Player {} is leaving party {}", playerId, partyId);
                party.removeMember(playerId);
                
                // Disband empty party
                if (party.isEmpty()) {
                    parties.remove(partyId);
                    StoryAdventureMod.LOGGER.info("[PartyManager] Party {} disbanded because it is now empty", partyId);
                }
            }
        }
    }
    
    /**
     * Disband a party entirely.
     */
    public void disbandParty(UUID partyId) {
        StoryAdventureMod.LOGGER.info("[PartyManager] Disbanding party {}", partyId);
        Party party = parties.remove(partyId);
        if (party != null) {
            for (UUID memberId : party.getMembers()) {
                playerPartyMap.remove(memberId);
            }
            StoryAdventureMod.LOGGER.info("[PartyManager] Party {} disbanded successfully", partyId);
        } else {
            StoryAdventureMod.LOGGER.warn("[PartyManager] Disband FAILED: Party {} not found", partyId);
        }
    }
    
    /**
     * Transfer leadership to another party member.
     */
    public boolean transferLeadership(UUID partyId, UUID newLeaderId) {
        Party party = parties.get(partyId);
        if (party != null && party.hasMember(newLeaderId)) {
            party.setLeaderId(newLeaderId);
            return true;
        }
        return false;
    }
    
    /**
     * Get all active parties.
     */
    public Collection<Party> getAllParties() {
        return Collections.unmodifiableCollection(parties.values());
    }
    
    /**
     * Check if a player is in any party.
     */
    public boolean isInParty(UUID playerId) {
        return playerPartyMap.containsKey(playerId);
    }
}
