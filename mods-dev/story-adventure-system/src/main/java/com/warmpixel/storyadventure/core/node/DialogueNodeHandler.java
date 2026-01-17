package com.warmpixel.storyadventure.core.node;

import com.google.gson.JsonObject;
import com.warmpixel.storyadventure.StoryAdventureMod;
import com.warmpixel.storyadventure.core.graph.StageNode;
import com.warmpixel.storyadventure.instance.Instance;
import com.warmpixel.storyadventure.network.NetworkHandler;
import com.warmpixel.storyadventure.network.OpenUIPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Handler for DIALOGUE nodes.
 * Integrates with Easy NPC dialog system for rich NPC conversations.
 */
public class DialogueNodeHandler implements NodeHandler {
    
    private boolean dialogueTriggered = false;
    
    @Override
    public void onEnter(Instance instance, StageNode node) {
        String npcTemplate = node.getString("npc_template", "");
        String dialogSet = node.getString("dialog_set", "default");
        
        StoryAdventureMod.LOGGER.info("[DialogueNodeHandler] onEnter: instance={}, node={}, npc='{}', dialogSet='{}'", 
            instance.getInstanceId(), node.getId(), npcTemplate, dialogSet);
        
        dialogueTriggered = false;
        
        // Check if there's a proximity_trigger
        JsonObject data = node.getData();
        if (data.has("proximity_trigger")) {
            // Check if any player is ALREADY within range (common when transitioning from a nearby task)
            JsonObject trigger = data.getAsJsonObject("proximity_trigger");
            double targetX = trigger.has("target_x") ? trigger.get("target_x").getAsDouble() : 0;
            double targetY = trigger.has("target_y") ? trigger.get("target_y").getAsDouble() : 64;
            double targetZ = trigger.has("target_z") ? trigger.get("target_z").getAsDouble() : 0;
            double radius = trigger.has("radius") ? trigger.get("radius").getAsDouble() : 5.0; // Default larger radius
            
            for (UUID memberId : instance.getParty().getMembers()) {
                ServerPlayer player = instance.getServer().getPlayerList().getPlayer(memberId);
                if (player != null) {
                    double dx = player.getX() - targetX;
                    double dy = player.getY() - targetY;
                    double dz = player.getZ() - targetZ;
                    double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    
                    if (distance <= radius) {
                        StoryAdventureMod.LOGGER.info("[DialogueNodeHandler] Player {} already in proximity on enter (distance: {})", 
                            player.getName().getString(), distance);
                        dialogueTriggered = true;
                        openDialogueForAllPlayers(instance, node);
                        return;
                    }
                }
            }
            
            StoryAdventureMod.LOGGER.info("[DialogueNodeHandler] Waiting for proximity trigger in node {}", node.getId());
        } else {
            // No proximity trigger - open dialogue immediately for all players
            openDialogueForAllPlayers(instance, node);
        }
    }
    
    @Override
    public void onTick(Instance instance, StageNode node) {
        // Check proximity trigger if not yet triggered
        if (dialogueTriggered) return;
        
        JsonObject data = node.getData();
        if (!data.has("proximity_trigger")) return;
        
        JsonObject trigger = data.getAsJsonObject("proximity_trigger");
        double targetX = trigger.has("target_x") ? trigger.get("target_x").getAsDouble() : 0;
        double targetY = trigger.has("target_y") ? trigger.get("target_y").getAsDouble() : 64;
        double targetZ = trigger.has("target_z") ? trigger.get("target_z").getAsDouble() : 0;
        double radius = trigger.has("radius") ? trigger.get("radius").getAsDouble() : 5.0;
        
        // Check if any party member is within proximity range
        for (UUID memberId : instance.getParty().getMembers()) {
            ServerPlayer player = instance.getServer().getPlayerList().getPlayer(memberId);
            if (player != null) {
                double dx = player.getX() - targetX;
                double dy = player.getY() - targetY;
                double dz = player.getZ() - targetZ;
                double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                
                if (distance <= radius) {
                    StoryAdventureMod.LOGGER.info("[DialogueNodeHandler] Player {} triggered dialogue proximity (distance: {})", 
                        player.getName().getString(), distance);
                    
                    // Trigger dialogue for ALL party members
                    dialogueTriggered = true;
                    openDialogueForAllPlayers(instance, node);
                    break;
                }
            }
        }
    }
    
    private void openDialogueForAllPlayers(Instance instance, StageNode node) {
        String npcName = node.getString("npc_name", "NPC");
        String title = node.getString("title", npcName);
        
        // Build dialogue JSON from node data
        JsonObject data = node.getData();

        // Update HUD first
        syncHudWithDialogue(instance, node, title);

        StringBuilder dialogueJson = new StringBuilder();
        dialogueJson.append("{");
        dialogueJson.append("\"npcName\":\"").append(escapeJson(npcName)).append("\",");
        
        // Include profile_id if present
        if (data.has("profile_id")) {
            dialogueJson.append("\"profileId\":\"").append(escapeJson(data.get("profile_id").getAsString())).append("\",");
        }

        // Include voiceover if present at root
        if (data.has("voiceover")) {
            dialogueJson.append("\"voiceover\":\"").append(escapeJson(data.get("voiceover").getAsString())).append("\",");
        }
        
        // Get lines from node
        dialogueJson.append("\"lines\":[");
        if (data.has("lines") && data.get("lines").isJsonArray()) {
            var lines = data.getAsJsonArray("lines");
            for (int i = 0; i < lines.size(); i++) {
                if (i > 0) dialogueJson.append(",");
                if (lines.get(i).isJsonPrimitive()) {
                    dialogueJson.append("\"").append(escapeJson(lines.get(i).getAsString())).append("\"");
                } else {
                    // Pass through the object as a string (to be parsed on client)
                    dialogueJson.append(lines.get(i).toString());
                }
            }
        }
        dialogueJson.append("],");
        
        // Get choices from node
        dialogueJson.append("\"choices\":[");
        if (data.has("choices") && data.get("choices").isJsonArray()) {
            var choices = data.getAsJsonArray("choices");
            for (int i = 0; i < choices.size(); i++) {
                if (i > 0) dialogueJson.append(",");
                var choice = choices.get(i).getAsJsonObject();
                String id = choice.has("id") ? choice.get("id").getAsString() : "choice_" + i;
                String text = choice.has("text") ? choice.get("text").getAsString() : "选项 " + (i + 1);
                dialogueJson.append("{");
                dialogueJson.append("\"id\":\"").append(escapeJson(id)).append("\",");
                dialogueJson.append("\"text\":\"").append(escapeJson(text)).append("\"");
                dialogueJson.append("}");
            }
        }
        dialogueJson.append("]}");
        
        // Send dialogue to ALL party members
        for (UUID memberId : instance.getParty().getMembers()) {
            ServerPlayer player = instance.getServer().getPlayerList().getPlayer(memberId);
            if (player != null) {
                NetworkHandler.sendOpenUI(player, OpenUIPayload.SCREEN_DIALOGUE, dialogueJson.toString());
                StoryAdventureMod.LOGGER.info("[DialogueNodeHandler] Opened dialogue for player {}", player.getName().getString());
            }
        }
    }
    
    private void syncHudWithDialogue(Instance instance, StageNode node, String title) {
        // Build HUD data JSON
        StringBuilder hudJson = new StringBuilder();
        hudJson.append("{");
        hudJson.append("\"title\":\"").append(escapeJson(instance.getGraph().getName())).append("\",");
        hudJson.append("\"chapter\":\"").append(escapeJson(title)).append("\",");
        hudJson.append("\"objectives\":[{");
        hudJson.append("\"text\":\"").append(escapeJson("正在与 " + node.getString("npc_name", "NPC") + " 对话")).append("\",");
        hudJson.append("\"complete\":false,");
        hudJson.append("\"current\":true");
        hudJson.append("}]}");
        
        for (UUID memberId : instance.getParty().getMembers()) {
            ServerPlayer player = instance.getServer().getPlayerList().getPlayer(memberId);
            if (player != null) {
                NetworkHandler.sendOpenUI(
                    player, 
                    OpenUIPayload.SCREEN_HUD_SHOW, 
                    hudJson.toString()
                );
            }
        }
    }

    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
    
    @Override
    public void onAction(Instance instance, StageNode node, ServerPlayer player, String action, Object data) {
        StoryAdventureMod.LOGGER.debug("[DialogueNodeHandler] onAction: player={}, action={}, data={}", 
            player.getName().getString(), action, data);

        if ("choice".equals(action) && data instanceof String choice) {
            StoryAdventureMod.LOGGER.info("[DialogueNodeHandler] Player {} made choice: '{}' in node {}", 
                player.getName().getString(), choice, node.getId());

            // Record the dialogue choice
            instance.getState().setLastDialogueChoice(choice);
            
            // Apply relationship impact if defined
            var relationshipImpact = node.getObject("relationship_impact");
            if (relationshipImpact != null) {
                for (String npcId : relationshipImpact.keySet()) {
                    int delta = relationshipImpact.get(npcId).getAsInt();
                    StoryAdventureMod.LOGGER.debug("[DialogueNodeHandler] Applying relationship impact: npc={}, delta={}, player={}", 
                        npcId, delta, player.getName().getString());
                    instance.getState().modifyRelationship(player.getUUID(), npcId, delta);
                }
            }
            
            // Set node result based on choice
            String result = "choice_" + choice;
            instance.getState().setNodeResult(result);
            StoryAdventureMod.LOGGER.debug("[DialogueNodeHandler] Node result set to: {}", result);
            
            // Evaluate transitions
            instance.evaluateAutoTransitions();
        } else {
            StoryAdventureMod.LOGGER.warn("[DialogueNodeHandler] Unknown action or invalid data: action={}, data={}", action, data);
        }
    }
    
    @Override
    public void onExit(Instance instance, StageNode node) {
        StoryAdventureMod.LOGGER.debug("[DialogueNodeHandler] onExit: instance={}, node={}", instance.getInstanceId(), node.getId());
        dialogueTriggered = false;
    }
    
    @Override
    public boolean canComplete(Instance instance, StageNode node) {
        // Dialogue completes when a choice is made
        return instance.getState().getLastDialogueChoice() != null;
    }
}

