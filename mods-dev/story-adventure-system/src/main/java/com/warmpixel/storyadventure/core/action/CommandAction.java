package com.warmpixel.storyadventure.core.action;

import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;

import net.minecraft.commands.CommandSourceStack;
import java.util.UUID;
import java.util.List;

/**
 * Action that executes a server command for each player.
 */
public class CommandAction implements NodeAction {
    
    private final String command;
    private final boolean asOp;
    
    public CommandAction(String command) {
        this(command, false);
    }
    
    public CommandAction(String command, boolean asOp) {
        this.command = command;
        this.asOp = asOp;
    }
    
    @Override
    public String getType() {
        return "COMMAND";
    }
    
    private UUID instanceId;

    public void setInstanceId(UUID instanceId) {
        this.instanceId = instanceId;
    }
    
    @Override
    public void execute(List<ServerPlayer> players) {
        if (players.isEmpty()) return;
        
        var server = players.get(0).getServer();
        if (server == null) return;

        for (ServerPlayer player : players) {
            String processedCmd = command
                .replace("{player}", player.getName().getString())
                .replace("{uuid}", player.getUUID().toString())
                .replace("{x}", String.format("%.2f", player.getX()))
                .replace("{y}", String.format("%.2f", player.getY()))
                .replace("{z}", String.format("%.2f", player.getZ()));
                
            if (instanceId != null) {
                processedCmd = processedCmd.replace("{instance_id}", instanceId.toString());
            }
            
            CommandSourceStack source = asOp ? server.createCommandSourceStack() : player.createCommandSourceStack();
            // Suppress output to prevent chat spam
            source = source.withSuppressedOutput();
            // Ensure permission level if OP
            if (asOp) {
                source = source.withPermission(2);
            }
            
            server.getCommands().performPrefixedCommand(source, processedCmd);
        }
    }
    
    @Override
    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "COMMAND");
        obj.addProperty("command", command);
        if (asOp) obj.addProperty("as_op", true);
        return obj;
    }
    
    @Override
    public String getSummary() {
        return "命令: " + (command.length() > 30 ? command.substring(0, 28) + ".." : command);
    }
    
    public static CommandAction fromJson(JsonObject obj) {
        String cmd = obj.has("command") ? obj.get("command").getAsString() : "";
        boolean asOp = obj.has("as_op") && obj.get("as_op").getAsBoolean();
        return new CommandAction(cmd, asOp);
    }
}
