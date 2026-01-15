# Story Adventure System - Verified Features

This document summarizes all verified and tested features for story JSON definitions.

---

## Node Types

### 1. `checkpoint` ✅
A savepoint that can be rewound to. Auto-advances immediately.

```json
{
  "type": "checkpoint",
  "data": {
    "rewind_anchor": true,
    "save_inventory": true,
    "message": "欢迎来到霍金斯镇"
  },
  "on_enter": [...],
  "edges": [...]
}
```

**Verified Features:**
- ✅ Saves checkpoint state for rewinding
- ✅ Auto-advances to next node
- ✅ Executes `on_enter` actions for all party members

---

### 2. `task` ✅
Player objectives like reaching locations, collecting items, etc.

```json
{
  "type": "task",
  "data": {
    "task_type": "INVESTIGATE",
    "title": "寻找乔伊斯",
    "description": "跟随标记找到乔伊斯·拜尔斯",
    "waypoint": {
      "id": "joyce_location",
      "label": "乔伊斯·拜尔斯",
      "x": 1029.863,
      "y": 409.0,
      "z": -1116.588,
      "icon": "npc",
      "color": "0xFFFFCC00"
    },
    "objectives": [
      {
        "type": "REACH_LOCATION",
        "target_x": 1029.863,
        "target_y": 409.0,
        "target_z": -1116.588,
        "radius": 3.0,
        "description": "到达乔伊斯的位置"
      }
    ],
    "time_limit_seconds": 0
  },
  "on_enter": [...],
  "edges": [...]
}
```

**Verified Features:**
- ✅ Parses waypoints from JSON and syncs to all party members
- ✅ On/off screen waypoint indicators work
- ✅ `REACH_LOCATION` objective checks player proximity every tick
- ✅ Clears waypoints on objective completion
- ✅ Syncs HUD with task title, description, and objectives
- ✅ Supports time limits with timer display

**Objective Types:**
| Type | Description |
|------|-------------|
| `REACH_LOCATION` | Player must reach target coordinates within radius |
| `COLLECT_ITEM` | Player must collect specific items |
| `INTERACT` | Player must interact with something |

---

### 3. `dialogue` ✅
NPC conversations with choices.

```json
{
  "type": "dialogue",
  "data": {
    "npc_template": "npc_07_joyce_byers",
    "dialog_set": "main",
    "npc_name": "乔伊斯·拜尔斯",
    "proximity_trigger": {
      "radius": 2.0,
      "target_x": 1029.863,
      "target_y": 409.0,
      "target_z": -1116.588
    },
    "lines": [
      "求求你！你一定要帮帮我！",
      "威尔...我的儿子威尔失踪了！"
    ],
    "choices": [
      { "id": "help", "text": "我会帮你找到威尔！" },
      { "id": "where", "text": "我应该去哪里找线索？" }
    ]
  },
  "edges": [
    {
      "target": "next_node",
      "conditions": [
        { "type": "DIALOGUE_CHOICE", "value": "help" }
      ]
    }
  ]
}
```

**Verified Features:**
- ✅ `proximity_trigger` monitors player positions
- ✅ Opens dialogue for **ALL** party members when triggered
- ✅ Parses `lines` and `choices` from JSON
- ✅ Records choice for edge conditions (`DIALOGUE_CHOICE`)

---

### 4. `combat` ✅
Wave-based or boss combat encounters.

```json
{
  "type": "combat",
  "data": {
    "combat_type": "WAVE",
    "title": "魔怪来袭！",
    "description": "魔怪大军从阴影中涌出！",
    "enemies": [
      {
        "type": "minecraft:zombie",
        "count": 50,
        "spawn_radius": 10
      }
    ],
    "arena_bounds": {
      "center": { "x": 1023.965, "y": 412.0, "z": -1132.124 },
      "radius": 30
    },
    "escape_available": false
  },
  "on_enter": [...],
  "edges": [...]
}
```

**Verified Features:**
- ✅ Spawns enemies around player using `/summon` command
- ✅ Uses `spawn_radius` for random distribution
- ✅ Tracks enemy count and kills
- ✅ Victory condition: all enemies killed
- ✅ Defeat condition: all players dead
- ✅ Progress notifications to all party members

---

### 5. `cutscene` ✅
Scripted sequences and endings.

```json
{
  "type": "cutscene",
  "data": {
    "duration_ticks": 200,
    "message": "你成功击退了魔怪大军！\\n\\n霍金斯镇暂时安全了...",
    "is_ending": true,
    "ending_type": "success",
    "rewards": [
      { "type": "EXPERIENCE", "amount": 500 }
    ]
  },
  "on_enter": [...]
}
```

**Verified Features:**
- ✅ Waits for `duration_ticks` before completing
- ✅ Supports `is_ending` flag to trigger instance completion
- ✅ `ending_type: "success"` calls `instance.complete()` → Victory Screen
- ✅ Parses `rewards` array for victory screen display

---

## on_enter Actions ✅

Actions executed when entering a node. **All actions execute for ALL party members.**

### Available Action Types

| Type | Description | Example |
|------|-------------|---------|
| `TELEPORT` | Teleport players to coordinates | `{"type":"TELEPORT","dimension":"minecraft:overworld","x":100,"y":64,"z":200}` |
| `TITLE` | Show title/subtitle | `{"type":"TITLE","title":"标题","subtitle":"副标题"}` |
| `MESSAGE` | Send chat message | `{"type":"MESSAGE","text":"§e[任务] §f目标描述"}` |
| `COMMAND` | Execute server command | `{"type":"COMMAND","command":"storyui hud show","as_op":true}` |
| `PLAY_SOUND` | Play sound effect | `{"type":"PLAY_SOUND","sound":"minecraft:entity.wither.spawn"}` |
| `SET_FLAG` | Set instance flag | `{"type":"SET_FLAG","flag":"visited_lab","value":true}` |
| `GIVE_ITEM` | Give item to players | `{"type":"GIVE_ITEM","item":"minecraft:diamond","count":1}` |
| `SPAWN_NPC` | Spawn NPC from template | `{"type":"SPAWN_NPC","npc_template":"npc_07_joyce_byers","x":100,"y":64,"z":200}` |

---

## Edge Conditions

Conditions that control node transitions.

| Type | Description | Example |
|------|-------------|---------|
| `TASK_COMPLETE` | Task objectives completed | `{"type":"TASK_COMPLETE"}` |
| `DIALOGUE_CHOICE` | Specific dialogue choice made | `{"type":"DIALOGUE_CHOICE","value":"help"}` |
| `COMBAT_VICTORY` | Combat completed successfully | `{"type":"COMBAT_VICTORY"}` |
| `COMBAT_DEFEAT` | Combat failed | `{"type":"COMBAT_DEFEAT"}` |
| `FLAG_SET` | Flag has specific value | `{"type":"FLAG_SET","flag":"key_found","value":true}` |

---

## HUD System ✅

The story HUD automatically syncs:
- **Title**: From `graph.name` (story title)
- **Chapter**: From task node's `title` field
- **Objectives**: From task node's `objectives[].description`
- **Timer**: From task node's `time_limit_seconds`

HUD is shown via:
1. `Instance.start()` - shows initial HUD
2. `TaskNodeHandler.onEnter()` - updates with task-specific data
3. `on_enter` action: `{"type":"COMMAND","command":"storyui hud show","as_op":true}`

---

## Waypoint System ✅

Waypoints show on-screen markers and off-screen indicators.

**Waypoint Icons:**
- `objective` - General objective
- `npc` - NPC location
- `clue` - Investigation point
- `danger` - Hazard marker
- `exit` - Exit point

**Color Format:** `"0xAARRGGBB"` (e.g., `"0xFFFFCC00"` for gold)

---

## Victory Screen ✅

Triggered by `instance.complete()` when cutscene has `is_ending: true` and `ending_type: "success"`.

**Features:**
- Displays story name and completion time
- Shows rewards list (XP, items)
- 10-second countdown timer
- "确认返回" button to skip countdown
- Teleports all party members to world spawn

**Reward Format:**
```json
"rewards": [
  { "type": "EXPERIENCE", "amount": 500 },
  { "type": "ITEM", "item": "minecraft:diamond", "amount": 3 }
]
```

---

## Testing Commands

```bash
# Reload stories
/storyadmin reload

# Create and start instance
/storyadmin instance create stranger_things_hawkins

# Show HUD manually
/storyui hud show

# Show dialogue manually
/storyui dialogue
```

---

## File Structure

```
config/storyadventure/stories/
├── stranger_things_hawkins.json    # Main story file
└── VERIFIED_FEATURES.md            # This documentation
```

---

## Integration Notes

### Easy NPC Integration ✅
The mod integrates with Easy NPC for spawning template NPCs at specific coordinates.

**SpawnNPCAction** uses `NPCTemplateManager.spawnFromTemplate(level, templateName, x, y, z)` to spawn NPCs directly.

```json
{
  "type": "SPAWN_NPC",
  "npc_template": "npc_07_joyce_byers",
  "dimension": "minecraft:overworld",
  "x": 1029.863,
  "y": 409.0,
  "z": -1116.588,
  "yaw": 180.0,
  "pitch": 0.0
}
```

---

## Session Updates (2026-01-14 20:20)

### Fixes Applied:

1. **Waypoint Indicator Accuracy** ✅
   - Rewrote projection using camera matrices for proper 3D-to-2D conversion
   - Indicators now correctly track world positions
   - Distance-based sizing (closer = larger)
   - Smaller base indicator size

2. **Dialogue System** ✅
   - Fixed SCREEN_DIALOGUE handler to parse `npcName`, `lines`, and `choices` from JSON
   - Dialogue buttons now correctly send `DialogueChoicePayload` to server
   - Screen closes after choice selection
   - DialogueNodeHandler checks proximity immediately on enter

3. **NPC Spawning** ✅
   - Added Easy NPC as compile-only dependency
   - Uses `NPCTemplateManager.spawnFromTemplate()` API directly
   - Supports dimension, coordinates, and rotation

4. **HUD Branding** ✅
   - Added "WarmPixel原创" branding to bottom-right of HUD panel

5. **Edge Transitions** ✅
   - `evaluateAutoTransitions()` now checks ALL edges (including conditional)
   - Transitions to first edge where ALL conditions are met

6. **Instance Completion** ✅
   - Hides HUD before showing victory screen
   - Clears all waypoints on completion

---

*Last Updated: 2026-01-14 20:20*
*WarmPixel原创*
