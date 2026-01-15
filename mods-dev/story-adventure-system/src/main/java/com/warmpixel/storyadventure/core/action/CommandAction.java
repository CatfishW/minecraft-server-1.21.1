package com.warmpixel.storyadventure.core.action;

import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;

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
            
            server.getCommands().performPrefixedCommand(
                asOp ? server.createCommandSourceStack() : player.createCommandSourceStack(),
                processedCmd
            );
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
