
import json
import os

TEMPLATE_DIR = "config/easy_npc/npc_templates"

def update_npc(filepath):
    try:
        with open(filepath, 'r') as f:
            data = json.load(f)
        
        # 1. Update Attributes
        if "attributes" not in data:
            data["attributes"] = {}
        
        data["attributes"]["maxHealth"] = 40
        data["attributes"]["invulnerable"] = False
        data["attributes"]["attackableByPlayers"] = True
        data["attributes"]["attackableByMonsters"] = True
        
        # 2. Add Drop Table (Coins)
        data["drop"] = {
            "item": {
                "item": "numismatic-overhaul:bronze_coin,numismatic-overhaul:silver_coin",
                "count": 15
            },
            "minCount": 15,
            "maxCount": 30,
            "chance": 0.5,
            "playerKillOnly": True
        }

        # 3. Ensure Objectives are hostile-compatible
        if "objectives" not in data:
            data["objectives"] = {}
        
        data["objectives"]["attackHostileMobs"] = True
        data["objectives"]["attackPlayers"] = False # Don't attack players by default unless provoked? 
        # Actually user said "attackable", not "hostile".
        
        with open(filepath, 'w') as f:
            json.dump(data, f, indent=4, ensure_ascii=False)
        print(f"Updated {filepath}")
        
    except Exception as e:
        print(f"Error updating {filepath}: {e}")

def main():
    files = [f for f in os.listdir(TEMPLATE_DIR) if f.endswith(".json")]
    for filename in files:
        if filename.startswith("npc_"): # Only target generated/standard NPCs
            update_npc(os.path.join(TEMPLATE_DIR, filename))

if __name__ == "__main__":
    main()
