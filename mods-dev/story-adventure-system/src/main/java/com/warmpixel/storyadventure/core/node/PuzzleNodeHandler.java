package com.warmpixel.storyadventure.core.node;

import com.warmpixel.storyadventure.StoryAdventureMod;
import com.warmpixel.storyadventure.core.graph.StageNode;
import com.warmpixel.storyadventure.instance.Instance;
import net.minecraft.server.level.ServerPlayer;

/**
 * Handler for PUZZLE nodes.
 * Supports code locks, wiring, symbol matching, and clue board puzzles.
 */
public class PuzzleNodeHandler implements NodeHandler {
    
    @Override
    public void onEnter(Instance instance, StageNode node) {
        String puzzleType = node.getString("puzzle_type", "CODE_LOCK");
        int maxAttempts = node.getInt("max_attempts", 3);
        
        StoryAdventureMod.LOGGER.info("[PuzzleNodeHandler] onEnter: instance={}, node={}, type={}, maxAttempts={}", 
            instance.getInstanceId(), node.getId(), puzzleType, maxAttempts);
        
        // Initialize puzzle state
        // Store attempt count, etc.
    }
    
    @Override
    public void onTick(Instance instance, StageNode node) {
        // Puzzles don't auto-tick - they wait for player input
    }
    
    @Override
    public void onAction(Instance instance, StageNode node, ServerPlayer player, String action, Object data) {
        String puzzleType = node.getString("puzzle_type", "CODE_LOCK");
        StoryAdventureMod.LOGGER.debug("[PuzzleNodeHandler] onAction: player={}, action={}, data={}", 
            player.getName().getString(), action, data);
            
        switch (action) {
            case "submit_answer" -> handleSubmit(instance, node, player, data);
            case "request_hint" -> handleHint(instance, node, player);
            case "reset" -> handleReset(instance, node);
            default -> StoryAdventureMod.LOGGER.warn("[PuzzleNodeHandler] Unknown action: {}", action);
        }
    }
    
    private void handleSubmit(Instance instance, StageNode node, ServerPlayer player, Object data) {
        String solution = node.getString("solution", "");
        String answer = data != null ? data.toString() : "";
        
        // Get puzzle-specific state key
        String stateKey = "puzzle_attempts_" + node.getId();
        int currentAttempts = 0;
        if (instance.getState().getMetadata().has(stateKey)) {
            currentAttempts = instance.getState().getMetadata().get(stateKey).getAsInt();
        }
        
        StoryAdventureMod.LOGGER.debug("[PuzzleNodeHandler] Checking answer from player {}. Input='{}', currentAttempts={}", 
            player.getName().getString(), answer, currentAttempts);
        
        if (solution.equals(answer)) {
            // Puzzle solved!
            StoryAdventureMod.LOGGER.info("[PuzzleNodeHandler] Puzzle solved by player {}", player.getName().getString());
            instance.getState().setNodeResult("solved");
            
            // Clean up state
            instance.getState().getMetadata().remove(stateKey);

            // Close puzzle UI on clients
            for (var memberId : instance.getParty().getMembers()) {
                var member = instance.getServer().getPlayerList().getPlayer(memberId);
                if (member != null) {
                    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                        member,
                        new com.warmpixel.storyadventure.network.PuzzleResultPayload(true)
                    );
                }
            }
            
            instance.evaluateAutoTransitions();
        } else {
            // Wrong answer - track attempt
            currentAttempts++;
            instance.getState().getMetadata().addProperty(stateKey, currentAttempts);
            
            int maxAttempts = node.getInt("max_attempts", 3);
            StoryAdventureMod.LOGGER.info("[PuzzleNodeHandler] Wrong answer from player {}. (Attempts: {}/{})", 
                player.getName().getString(), currentAttempts, maxAttempts);
            
            if (currentAttempts >= maxAttempts) {
                StoryAdventureMod.LOGGER.info("[PuzzleNodeHandler] Max attempts reached for puzzle {}", node.getId());
                instance.getState().setNodeResult("failed");
                instance.evaluateAutoTransitions();
            } else {
                // Send feedback to player? The UI tracks its own attempts locally, but for sync
                // we might want a packet. For now, we rely on local UI tracking + server verification.
                // But if the server says "failed", the instance will transition and likely close the UI.
            }
        }
    }
    
    private void handleHint(Instance instance, StageNode node, ServerPlayer player) {
        StoryAdventureMod.LOGGER.debug("[PuzzleNodeHandler] Player {} requested hint", player.getName().getString());
        // Parse hints from node data
        var data = node.getData();
        if (data.has("hints")) {
            var hints = data.getAsJsonArray("hints");
            // Reveal hints that the player has discovered as clues
            for (var hint : hints) {
                String hintClue = hint.getAsString();
                if (instance.getState().hasDiscoveredClue(hintClue)) {
                    StoryAdventureMod.LOGGER.debug("[PuzzleNodeHandler] Revealing hint clue: {}", hintClue);
                    // Send hint to player
                }
            }
        }
    }
    
    private void handleReset(Instance instance, StageNode node) {
        StoryAdventureMod.LOGGER.info("[PuzzleNodeHandler] Reset requested for puzzle {}", node.getId());
        // Reset puzzle state (if allowed)
    }
    
    @Override
    public void onExit(Instance instance, StageNode node) {
        StoryAdventureMod.LOGGER.debug("[PuzzleNodeHandler] onExit: Cleaning up puzzle state");
        // Clean up puzzle state
    }
    
    @Override
    public boolean canComplete(Instance instance, StageNode node) {
        return instance.getState().isCurrentNodeCompleteWith("solved") ||
               instance.getState().isCurrentNodeCompleteWith("failed");
    }
}
