package com.warmpixel.storyadventure.core.node;

import com.warmpixel.storyadventure.core.graph.NodeType;
import java.util.EnumMap;
import java.util.Map;

/**
 * Registry for NodeHandlers.
 */
public class NodeHandlers {
    
    private static final Map<NodeType, NodeHandler> HANDLERS = new EnumMap<>(NodeType.class);
    
    static {
        register(NodeType.DIALOGUE, new DialogueNodeHandler());
        register(NodeType.TASK, new TaskNodeHandler());
        register(NodeType.PUZZLE, new PuzzleNodeHandler());
        register(NodeType.COMBAT, new CombatNodeHandler());
        register(NodeType.CUTSCENE, new CutsceneNodeHandler());
        register(NodeType.CHECKPOINT, new CheckpointNodeHandler());
        register(NodeType.ITEM_INTERACTION, new ItemInteractionNodeHandler());
    }
    
    public static void register(NodeType type, NodeHandler handler) {
        HANDLERS.put(type, handler);
    }
    
    public static NodeHandler getHandler(NodeType type) {
        return HANDLERS.get(type);
    }
}
