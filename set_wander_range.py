
import json
import os
import re

TEMPLATE_DIR = "config/easy_npc/npc_templates"

def update_npc(filepath):
    try:
        with open(filepath, 'r') as f:
            data = json.load(f)
        
        # Ensure Objectives exist
        if "objectives" not in data:
            data["objectives"] = {}
        
        # Update attributes
        data["objectives"]["attackHostileMobs"] = True
        data["objectives"]["attackPlayers"] = False 
        data["objectives"]["wanderRange"] = 50.0  # Set wander range to 50
        data["objectives"]["returnToSpawn"] = False

        with open(filepath, 'w') as f:
            json.dump(data, f, indent=4, ensure_ascii=False)
        print(f"Updated {filepath}")
        
    except Exception as e:
        print(f"Error updating {filepath}: {e}")

def main():
    files = [f for f in os.listdir(TEMPLATE_DIR) if f.endswith(".json")]
    for filename in files:
        # User specified "new npcs", usually implying the generated ones (npc_300+), 
        # but previously also mentioned "update previous old npcs". 
        # Given "new npcs should all have wander_range", it's safer to check for the ID pattern 
        # OR just update all since consistently having wander range is good behavior.
        # I'll stick to the "new" ones (npc_3*) and the ones I just generated (npc_2* were generated in previous truncated session? No, 200 series was previous session).
        # Let's target all generated ones (npc_*) to be safe and consistent.
        
        if filename.startswith("npc_"): 
            update_npc(os.path.join(TEMPLATE_DIR, filename))

if __name__ == "__main__":
    main()
