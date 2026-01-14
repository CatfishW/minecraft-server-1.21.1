Warm Pixel FTB Quest Extend

Purpose
- Import AI-friendly JSON quest packs into FTB Quests SNBT files.
- Supports both zh_cn and en_us strings in the same JSON.

Import location
- Place JSON files in: config/warm-pixel-ftb-quest-extend/import
- In-game command (op level 2+):
  - /wpftb import <file>
  - /wpftb import_all

JSON format (v1)
{
  "format_version": 1,
  "chapter_group": {
    "id": "ai_import",
    "title": {"zh_cn": "AI Import", "en_us": "AI Import"}
  },
  "chapters": [
    {
      "id": "starter",
      "title": {"zh_cn": "Starter", "en_us": "Starter"},
      "subtitle": {"zh_cn": "First steps", "en_us": "First steps"},
      "icon": "minecraft:book",
      "order": 0,
      "quests": [
        {
          "id": "gather_wood",
          "title": {"zh_cn": "Gather Wood", "en_us": "Gather Wood"},
          "subtitle": {"zh_cn": "Basic materials", "en_us": "Basic materials"},
          "description": {
            "zh_cn": ["Collect logs.", "Bring them back."],
            "en_us": ["Collect logs.", "Bring them back."]
          },
          "x": 0,
          "y": 0,
          "size": 2.0,
          "shape": "circle",
          "dependencies": [],
          "tasks": [
            {"type": "item", "item": "minecraft:oak_log", "count": 16}
          ],
          "rewards": [
            {"type": "item", "item": "minecraft:stone_axe", "count": 1}
          ]
        },
        {
          "id": "craft_planks",
          "title": "Craft Planks",
          "description": "Turn logs into planks.",
          "dependencies": ["gather_wood"],
          "tasks": [
            {"type": "checkmark"}
          ]
        }
      ]
    }
  ]
}

Notes
- Chapter/quest IDs can be short strings; the mod generates stable FTB IDs.
- If x/y are omitted, a simple dependency-based layout is auto-generated.
- Supported task types: checkmark, item, kill, xp.
- Supported reward types: item, xp.
