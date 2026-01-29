package com.warmpixel.storyadventure.core.node;

import com.warmpixel.storyadventure.StoryAdventureMod;
import com.warmpixel.storyadventure.core.graph.StageNode;
import com.warmpixel.storyadventure.instance.Instance;
import net.minecraft.server.level.ServerPlayer;

/**
 * Handler for PUZZLE nodes.
 * Supports code locks, fingerprint, pipe connection, snake, and hex search.
 */
public class PuzzleNodeHandler implements NodeHandler {
    
    @Override
    public void onEnter(Instance instance, StageNode node) {
        String puzzleType = node.getString("puzzle_type", "CODE_LOCK");
        int maxAttempts = node.getInt("max_attempts", 3);
        
        StoryAdventureMod.LOGGER.info("[PuzzleNodeHandler] onEnter: instance={}, node={}, type={}, maxAttempts={}", 
            instance.getInstanceId(), node.getId(), puzzleType, maxAttempts);
        
        // Reset attempt count for this puzzle session
        String stateKey = "puzzle_attempts_" + node.getId();
        instance.getState().getMetadata().addProperty(stateKey, 0);
    }
    
    @Override
    public void onTick(Instance instance, StageNode node) {
    }
    
    @Override
    public void onAction(Instance instance, StageNode node, ServerPlayer player, String action, Object data) {
        switch (action) {
            case "submit_answer" -> handleSubmit(instance, node, player, data);
            case "request_hint" -> handleHint(instance, node, player);
            case "reset" -> handleReset(instance, node);
            default -> StoryAdventureMod.LOGGER.warn("[PuzzleNodeHandler] Unknown action: {}", action);
        }
    }
    
    private void handleSubmit(Instance instance, StageNode node, ServerPlayer player, Object data) {
        String puzzleType = node.getString("puzzle_type", "CODE_LOCK");
        String solution = node.getString("solution", "");
        String answer = data != null ? data.toString() : "";
        
        String stateKey = "puzzle_attempts_" + node.getId();
        int currentAttempts = 0;
        if (instance.getState().getMetadata().has(stateKey)) {
            currentAttempts = instance.getState().getMetadata().get(stateKey).getAsInt();
        }
        
        boolean solved = false;
        switch (puzzleType) {
            case "CODE_LOCK" -> solved = solution.equals(answer);
            case "FINGERPRINT" -> solved = "fingerprint_matched".equals(answer);
            case "PIPE_CONNECT" -> solved = "pipe_solved".equals(answer);
            case "SNAKE" -> solved = "snake_won".equals(answer);
            case "HEX_SEARCH" -> solved = "hex_found".equals(answer);
            case "MEMORY_SEQUENCE" -> solved = "memory_solved".equals(answer);
            case "SIGNAL_TUNING" -> solved = "signal_tuned".equals(answer);
            default -> solved = solution.equals(answer);
        }
        
        if (solved) {
            instance.getState().setNodeResult("solved");
            closePuzzleForParty(instance, true);
            instance.evaluateAutoTransitions();
        } else {
            currentAttempts++;
            instance.getState().getMetadata().addProperty(stateKey, currentAttempts);
            int maxAttempts = node.getInt("max_attempts", 3);
            
            if (currentAttempts >= maxAttempts) {
                instance.getState().setNodeResult("failed");
                closePuzzleForParty(instance, false);
                instance.evaluateAutoTransitions();
            }
        }
    }
    
    private void closePuzzleForParty(Instance instance, boolean success) {
        for (var memberId : instance.getParty().getMembers()) {
            var member = instance.getServer().getPlayerList().getPlayer(memberId);
            if (member != null) {
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                    member,
                    new com.warmpixel.storyadventure.network.PuzzleResultPayload(success)
                );
            }
        }
    }
    
    private void handleHint(Instance instance, StageNode node, ServerPlayer player) {
        var data = node.getData();
        if (data.has("hints")) {
            var hints = data.getAsJsonArray("hints");
            for (var hint : hints) {
                String hintClue = hint.getAsString();
                if (instance.getState().hasDiscoveredClue(hintClue)) {
                    // reveal hint to player logic
                }
            }
        }
    }
    
    private void handleReset(Instance instance, StageNode node) {
        String stateKey = "puzzle_attempts_" + node.getId();
        instance.getState().getMetadata().addProperty(stateKey, 0);
    }
    
    @Override
    public void onExit(Instance instance, StageNode node) {
    }
    
    @Override
    public boolean canComplete(Instance instance, StageNode node) {
        return instance.getState().isCurrentNodeCompleteWith("solved") ||
               instance.getState().isCurrentNodeCompleteWith("failed");
    }
}
