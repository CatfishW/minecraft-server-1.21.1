# Story Adventure System: Story Design Guide

This guide explains how to create, edit, and implement stories using the Story Adventure System mod.

## 1. Story Structure

A story consists of a directed graph of **Nodes** connected by **Edges**.

- **Nodes**: Represent a state in the story (dialogue, task, cutscene, checkpoint, etc.).
- **Edges**: Represent transitions between nodes, often with conditions.
- **Triggers**: Spatial areas (Trigger Boxes) that can trigger actions or transitions when players enter/exit.
- **Actions**: Discrete events like giving items, spawning NPCs, or executing commands.
- **Clues & Flags**: Data used to track progress and unlock paths.

## 2. Story JSON Schema

Stories are stored in `config/storyadventure/stories/*.json`.

### Basic Information
```json
{
  "id": "my_epic_story",
  "name": "My Epic Story",
  "description": "An amazing adventure in Minecraft",
  "version": "1.0.0",
  "min_players": 1,
  "max_players": 4,
  "entry_node": "start_node"
}
```

### Nodes and Edges
```json
"nodes": {
  "start_node": {
    "type": "checkpoint",
    "data": { "message": "Adventure Begins!" },
    "edges": [
      { "target": "first_dialogue" }
    ]
  },
  "first_dialogue": {
    "type": "dialogue",
    "data": {
      "npc_id": "guide_npc",
      "text": "Welcome, traveler! Can you find the Golden Apple?",
      "choices": [
        { "text": "Yes, I will find it!", "target": "find_apple_task" },
        { "text": "No, I'm busy.", "target": "decline_ending" }
      ]
    }
  }
}
```

### Triggers (Per Node)
You can define triggers inside a node's `data` object:
```json
"find_apple_task": {
  "type": "task",
  "data": {
    "triggers": [
      {
        "id": "secret_cave_trigger",
        "bounds": [-10, 60, -10, 10, 70, 10],
        "on_enter": [
          { "type": "COMMAND", "command": "say You found the secret cave!", "as_op": true }
        ],
        "linked_node": "found_cave_node"
      }
    ]
  }
}
```

## 3. Using the Admin UI

The mod provides several UI panels for admins (requires permission level 2+):

- `/storyadminui`: Main Dashboard.
- `/storyadminui stories`: Manage story definitions, reload, and validate.
- `/storyadminui instances`: View and debug active story instances.
- `/storyadmin triggers`: Manage global trigger boxes using the **Admin Wand**.

### The Admin Wand
Use `/storyadmin wand` to get the wand. 
- **Right-click** two blocks to select a region.
- **Left-click** to open the Trigger Manager UI.

## 4. Best Practices

1. **Checkpoints**: Use Checkpoint nodes frequently so players can resume if they fail or disconnect.
2. **Flags**: Use flags to track long-term progress (e.g., `has_talked_to_king`).
3. **Validation**: Always use the "Validate" button in the Story Manager to check for broken links or missing nodes.
4. **Command Placeholders**: Use `{player}`, `{x}`, `{y}`, `{z}` in Command Actions for dynamic effects.

---
*Created by Antigravity for Story Adventure System.*
