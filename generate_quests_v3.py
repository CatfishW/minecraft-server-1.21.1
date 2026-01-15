
import json
import os
import requests
import concurrent.futures
import re
import time
import random
import uuid
import ast

# Configuration
NPC_TEMPLATE_DIR = "/data/Yanlai/minecraft_server_fabric_1.21.1_fresh/config/easy_npc/npc_templates"
QUEST_OUTPUT_DIR = "/data/Yanlai/minecraft_server_fabric_1.21.1_fresh/config/easy_npc/quests"
OPEN_ROUTER_API_KEY = "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"
OPEN_ROUTER_BASE_URL = "https://openrouter.ai/api/v1"
MODEL_NAME = "xiaomi/mimo-v2-flash:free"
QUESTS_PER_NPC = 5

ALL_NPC_NAMES = []

def read_target_npc_templates():
    templates = []
    if not os.path.exists(NPC_TEMPLATE_DIR):
        print(f"Directory not found: {NPC_TEMPLATE_DIR}")
        return templates
    
    # First pass: collect ALL names for talk targets
    global ALL_NPC_NAMES
    all_files = [f for f in os.listdir(NPC_TEMPLATE_DIR) if f.endswith(".json")]
    for filename in all_files:
        try:
            with open(os.path.join(NPC_TEMPLATE_DIR, filename), 'r') as f:
                data = json.load(f)
                raw_name = data.get('name', 'Unknown').replace('§a', '').replace('§e', '').replace('§b', '').replace('§c', '').replace('§9', '').replace('§d', '').replace('§6', '').replace('§7', '').replace('§5', '')
                if raw_name not in ALL_NPC_NAMES:
                    ALL_NPC_NAMES.append(raw_name)
        except: pass

    # Second pass: only return the ones we want to generate quests for (300+)
    target_files = [f for f in all_files if re.match(r'npc_3\d{2}_', f)]
    
    for filename in target_files:
        filepath = os.path.join(NPC_TEMPLATE_DIR, filename)
        try:
            with open(filepath, 'r') as f:
                data = json.load(f)
                templates.append({'filename': filename, 'data': data})
        except Exception as e:
            print(f"Error reading {filename}: {e}")
    
    return templates

def generate_quest_batch(npc_data, count=5):
    name = npc_data.get('name', 'Unknown').replace('§a', '').replace('§e', '').replace('§b', '').replace('§c', '').replace('§9', '').replace('§d', '').replace('§6', '').replace('§7', '').replace('§5', '')
    desc = npc_data.get('description', '一个神秘的角色。')
    
    # Pick random talk targets from our list, excluding self
    possible_targets = [n for n in ALL_NPC_NAMES if n != name]
    talk_targets_str = ", ".join(random.sample(possible_targets, min(5, len(possible_targets))))

    print(f"Generating batch of {count} quests for {name}...")
    
    prompt = f"""
        你是一个Minecraft Mod RPG任务设计师。为角色 "{name}" 设计 {count} 个沉浸式任务。
        
        角色背景: {desc}
        
        要求:
        1. 语言: 简体中文。
        2. 标题: 4-6字甚至更有史诗感的标题。
        3. 描述: 分两段。第一段剧情背景，第二段具体指导。不要提及不存在的地点。
        4. Objective Types: [KILL, GATHER, TALK].
           - KILL: 任何原版生物 (minecraft:zombie, etc)
           - GATHER: 任何原版物品 (minecraft:wheat, minecraft:iron_ingot, etc)
           - TALK: 必须从以下列表中选择目标: [{talk_targets_str}]
        5. 奖励: 
           - 简单: 10-50 numismatic-overhaul:bronze_coin
           - 中等: 50-100 numismatic-overhaul:bronze_coin
           - 困难: 1-5 numismatic-overhaul:silver_coin
        
        Output MUST be a JSON ARRAY of objects.
        [
            {{
                "id": "uuid",
                "title": "...",
                "description": "...",
                "objective": {{ "type": "...", "target": "...", "amount": 1 }},
                "reward": {{ "xp": 100, "itemId": "...", "amount": 10 }}
            }},
            ...
        ]
        """

    headers = {
        "Authorization": f"Bearer {OPEN_ROUTER_API_KEY}",
        "Content-Type": "application/json",
        "HTTP-Referer": "https://github.com/Start-Impulse/Easy-NPC",
        "X-Title": "Easy NPC Quest Generator"
    }
    
    payload = {
        "model": MODEL_NAME,
        "messages": [
            {"role": "system", "content": "Output only valid JSON array. No markdown."},
            {"role": "user", "content": prompt}
        ]
    }

    try:
        response = requests.post(f"{OPEN_ROUTER_BASE_URL}/chat/completions", headers=headers, json=payload, timeout=90)
        content = response.json()['choices'][0]['message']['content']
        
        # Cleanup
        if "```" in content:
            content = content.replace("```json", "").replace("```", "")
        
        match = re.search(r'\[.*\]', content, re.DOTALL)
        if match:
            quests = json.loads(match.group(0))
            
            valid_quests = []
            for q in quests:
                q['id'] = str(uuid.uuid4())
                
                # Check reward (fallback)
                if 'reward' not in q or 'itemId' not in q['reward']:
                    q['reward'] = {"xp": 100, "itemId": "numismatic-overhaul:bronze_coin", "amount": 20}
                
                # Check objective
                if 'objective' in q:
                    obj = q['objective']
                    # Fix common user errors in types
                    if obj['type'] == "CRAFT": obj['type'] = "GATHER" # Mod might not support craft easily or gather is safer
                    
                    # ensure valid targets
                    if obj['type'] == "TALK" and obj['target'] not in ALL_NPC_NAMES:
                        # Fallback to a safe name if LLM hallucinated
                        obj['target'] = talk_targets_str.split(", ")[0] 
                    
                    if obj['type'] in ["KILL", "GATHER"] and "minecraft:" not in obj['target']:
                        obj['target'] = "minecraft:" + obj['target'].lower().replace(" ", "_")

                    valid_quests.append(q)
            return valid_quests
            
    except Exception as e:
        print(f"Error for {name}: {e}")
        return []

    return []

def save_quests(quests):
    if not os.path.exists(QUEST_OUTPUT_DIR):
        os.makedirs(QUEST_OUTPUT_DIR)
        
    ids = []
    for q in quests:
        filename = f"quest_{q['id']}.json"
        filepath = os.path.join(QUEST_OUTPUT_DIR, filename)
        with open(filepath, 'w', encoding='utf-8') as f:
            json.dump(q, f, indent=4, ensure_ascii=False)
        ids.append(q['id'])
    return ids

def update_npc_template(npc_file_data, quest_ids):
    filepath = os.path.join(NPC_TEMPLATE_DIR, npc_file_data['filename'])
    data = npc_file_data['data']
    
    # Add RANDOM_POOL button to main dialog
    if 'dialogs' in data and 'main' in data['dialogs']:
        buttons = data['dialogs']['main'].get('buttons', [])
        
        # Remove old quest buttons if generic
        buttons = [b for b in buttons if "OPEN_QUEST_DIALOG" not in b.get('action', '')]
        
        pool_str = ",".join(quest_ids)
        buttons.insert(0, {
            "label": "§6§l接受任务",
            "action": "OPEN_QUEST_DIALOG:RANDOM_POOL:" + pool_str,
            "id": "btn_quest_" + str(uuid.uuid4())[:8]
        })
        
        data['dialogs']['main']['buttons'] = buttons
        
        with open(filepath, 'w', encoding='utf-8') as f:
            json.dump(data, f, indent=4, ensure_ascii=False)
        print(f"Updated {filepath} with {len(quest_ids)} quests.")

def main():
    templates = read_target_npc_templates()
    print(f"Found {len(templates)} target NPCs (300+).")
    
    # Process in chunks to avoid rate limits
    chunk_size = 5
    for i in range(0, len(templates), chunk_size):
        chunk = templates[i:i+chunk_size]
        print(f"Processing chunk {i//chunk_size + 1}...")
        
        with concurrent.futures.ThreadPoolExecutor(max_workers=5) as executor:
            future_to_npc = {executor.submit(generate_quest_batch, tmpl['data']): tmpl for tmpl in chunk}
            for future in concurrent.futures.as_completed(future_to_npc):
                tmpl = future_to_npc[future]
                try:
                    quests = future.result()
                    if quests:
                        ids = save_quests(quests)
                        update_npc_template(tmpl, ids)
                except Exception as e:
                    print(f"Failed for {tmpl['data']['name']}: {e}")
        
        time.sleep(2)

if __name__ == "__main__":
    main()
