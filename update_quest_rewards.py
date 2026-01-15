
import os
import json
import random

QUEST_DIR = "/data/Yanlai/minecraft_server_fabric_1.21.1_fresh/config/easy_npc/quests"

def update_rewards():
    count = 0
    for filename in os.listdir(QUEST_DIR):
        if not filename.endswith(".json"):
            continue
        
        filepath = os.path.join(QUEST_DIR, filename)
        try:
            with open(filepath, 'r', encoding='utf-8') as f:
                data = json.load(f)
            
            obj = data.get('objective', {})
            amount = obj.get('amount', 1)
            q_type = obj.get('type', 'GATHER')
            
            # Simple difficulty heuristic
            if q_type == 'TALK':
                difficulty = 'easy'
            elif q_type == 'KILL':
                difficulty = 'hard' if amount > 5 else 'medium'
            elif q_type == 'GATHER':
                difficulty = 'hard' if amount > 15 else 'medium'
            else:
                difficulty = 'medium'
            
            # Chance to roll a Gold coin (legendary reward)
            roll = random.random()
            
            if roll < 0.1: # 10% chance for Gold regardless of difficulty
                data['reward']['itemId'] = "numismatic-overhaul:gold_coin"
                data['reward']['amount'] = 1
            elif difficulty == 'easy' or (difficulty == 'medium' and random.random() < 0.3):
                # Easy or some medium tasks get bronze
                data['reward']['itemId'] = "numismatic-overhaul:bronze_coin"
                data['reward']['amount'] = random.randint(300, 500)
            else:
                # Most medium and all hard tasks get silver
                data['reward']['itemId'] = "numismatic-overhaul:silver_coin"
                data['reward']['amount'] = random.randint(50, 200)
            
            with open(filepath, 'w', encoding='utf-8') as f:
                json.dump(data, f, indent=4, ensure_ascii=False)
            count += 1
        except Exception as e:
            print(f"Error processing {filename}: {e}")
            
    print(f"Updated {count} quests.")

if __name__ == "__main__":
    update_rewards()
