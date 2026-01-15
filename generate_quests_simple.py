#!/usr/bin/env python3
"""
Quest Generator - Generates quests for NPCs with placeholder or missing quests.
Uses simpler prompts for better JSON extraction.
"""

import json
import os
import re
import time
import random
import uuid
from openai import OpenAI

# ============== CONFIGURATION ==============
NPC_TEMPLATE_DIR = "/data/Yanlai/minecraft_server_fabric_1.21.1_fresh/config/easy_npc/npc_templates"
QUEST_OUTPUT_DIR = "/data/Yanlai/minecraft_server_fabric_1.21.1_fresh/config/easy_npc/quests"

NVIDIA_API_KEY = "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"
NVIDIA_BASE_URL = "https://integrate.api.nvidia.com/v1"
MODEL_NAME = "deepseek-ai/deepseek-v3.2"

# ============== LOAD NPC NAMES ==============
ALL_NPC_NAMES = []

def load_all_npc_names():
    global ALL_NPC_NAMES
    color_pattern = re.compile(r'§[0-9a-fk-or]')
    
    for filename in os.listdir(NPC_TEMPLATE_DIR):
        if filename.endswith('.json'):
            try:
                with open(os.path.join(NPC_TEMPLATE_DIR, filename), 'r', encoding='utf-8') as f:
                    data = json.load(f)
                    name = color_pattern.sub('', data.get('name', ''))
                    if name and name not in ALL_NPC_NAMES:
                        ALL_NPC_NAMES.append(name)
            except:
                pass
    print(f"Loaded {len(ALL_NPC_NAMES)} NPC names")

def create_client():
    return OpenAI(base_url=NVIDIA_BASE_URL, api_key=NVIDIA_API_KEY)

def generate_quests(client, npc_name: str, description: str, count: int = 5) -> list:
    """Generate quests with simpler prompt for reliable JSON."""
    
    talk_targets = random.sample([n for n in ALL_NPC_NAMES if n != npc_name], min(3, len(ALL_NPC_NAMES)-1))
    
    prompt = f"""生成{count}个Minecraft RPG任务，JSON数组格式。

角色: {npc_name}
背景: {description[:200]}

要求:
- title: 4-6字中文标题
- description: 2段中文描述
- objective.type: KILL/GATHER/TALK
- objective.target: 
  - KILL: minecraft:zombie, minecraft:skeleton, minecraft:spider, minecraft:creeper, minecraft:enderman
  - GATHER: minecraft:iron_ingot, minecraft:diamond, minecraft:gold_ingot, minecraft:emerald, minecraft:wheat
  - TALK: {', '.join(talk_targets)}
- objective.amount: 1-20
- reward.itemId: numismatic-overhaul:bronze_coin 或 numismatic-overhaul:silver_coin
- reward.amount: 10-100 bronze 或 1-5 silver
- reward.xp: 50-500

严格JSON格式:
[{{"title":"标题","description":"描述","objective":{{"type":"KILL","target":"minecraft:zombie","amount":10}},"reward":{{"xp":100,"itemId":"numismatic-overhaul:bronze_coin","amount":30}}}}]"""

    print(f"  Calling API for {npc_name}...")
    
    try:
        completion = client.chat.completions.create(
            model=MODEL_NAME,
            messages=[{"role": "user", "content": prompt}],
            temperature=0.7,
            max_tokens=4000,
            stream=True
        )
        
        response = ""
        for chunk in completion:
            if chunk.choices and chunk.choices[0].delta.content:
                response += chunk.choices[0].delta.content
                print(".", end="", flush=True)
        print()
        
        # Extract JSON array
        response = re.sub(r'```json?\s*', '', response)
        response = re.sub(r'```', '', response)
        
        match = re.search(r'\[[\s\S]*\]', response)
        if match:
            quests = json.loads(match.group(0))
            
            valid = []
            for q in quests:
                q['id'] = str(uuid.uuid4())
                
                # Validate objective
                obj = q.get('objective', {})
                if obj.get('type') not in ['KILL', 'GATHER', 'TALK']:
                    obj['type'] = 'KILL'
                    obj['target'] = 'minecraft:zombie'
                
                if obj['type'] in ['KILL', 'GATHER']:
                    t = obj.get('target', 'zombie')
                    if not t.startswith('minecraft:'):
                        obj['target'] = 'minecraft:' + t.lower().replace(' ', '_')
                
                if obj['type'] == 'TALK' and obj.get('target') not in ALL_NPC_NAMES:
                    obj['target'] = random.choice(talk_targets) if talk_targets else "Unknown"
                
                obj['amount'] = int(obj.get('amount', 5))
                q['objective'] = obj
                
                # Validate reward
                rew = q.get('reward', {})
                if 'itemId' not in rew:
                    rew['itemId'] = 'numismatic-overhaul:bronze_coin'
                rew['amount'] = int(rew.get('amount', 25))
                rew['xp'] = int(rew.get('xp', 100))
                q['reward'] = rew
                
                valid.append(q)
            
            return valid
            
    except Exception as e:
        print(f"\n  Error: {e}")
    
    return []

def save_quests(quests: list) -> list:
    os.makedirs(QUEST_OUTPUT_DIR, exist_ok=True)
    ids = []
    for q in quests:
        path = os.path.join(QUEST_OUTPUT_DIR, f"quest_{q['id']}.json")
        with open(path, 'w', encoding='utf-8') as f:
            json.dump(q, f, indent=4, ensure_ascii=False)
        ids.append(q['id'])
    return ids

def has_placeholder_quests(npc_data: dict) -> bool:
    """Check if NPC has placeholder quest references."""
    dialogs = npc_data.get('dialogs', {})
    for dialog in dialogs.values():
        if not isinstance(dialog, dict):
            continue
        for btn in dialog.get('buttons', []):
            action = btn.get('action', '')
            if 'OPEN_QUEST_DIALOG:RANDOM_POOL:' in action:
                pool = action.split('RANDOM_POOL:', 1)[1]
                # Check for placeholder patterns
                if 'QUEST_' in pool or not pool.strip():
                    return True
                # Check if any referenced quests don't exist
                for qid in pool.split(','):
                    qid = qid.strip()
                    if qid and not os.path.exists(os.path.join(QUEST_OUTPUT_DIR, f"quest_{qid}.json")):
                        return True
    return False

def update_quest_buttons(npc_data: dict, quest_ids: list) -> dict:
    """Replace placeholder quest references with real quest IDs."""
    if not quest_ids:
        return npc_data
        
    pool = ",".join(quest_ids)
    dialogs = npc_data.get('dialogs', {})
    
    for dialog in dialogs.values():
        if not isinstance(dialog, dict):
            continue
        for btn in dialog.get('buttons', []):
            action = btn.get('action', '')
            if 'OPEN_QUEST_DIALOG:RANDOM_POOL:' in action:
                current = action.split('RANDOM_POOL:', 1)[1]
                # Replace if placeholder or missing quests
                if 'QUEST_' in current or not current.strip():
                    btn['action'] = f"OPEN_QUEST_DIALOG:RANDOM_POOL:{pool}"
                else:
                    # Check if referenced quests exist
                    needs_update = False
                    for qid in current.split(','):
                        qid = qid.strip()
                        if qid and not os.path.exists(os.path.join(QUEST_OUTPUT_DIR, f"quest_{qid}.json")):
                            needs_update = True
                            break
                    if needs_update:
                        btn['action'] = f"OPEN_QUEST_DIALOG:RANDOM_POOL:{pool}"
    
    return npc_data

def main():
    print("="*60)
    print("Quest Generator for NPCs")
    print("="*60)
    
    load_all_npc_names()
    client = create_client()
    
    color_pattern = re.compile(r'§[0-9a-fk-or]')
    
    # Find NPCs that need quests (400+ series)
    npc_files = sorted([f for f in os.listdir(NPC_TEMPLATE_DIR) 
                       if f.startswith('npc_4') and f.endswith('.json')])
    
    print(f"\nProcessing {len(npc_files)} NPC files...\n")
    
    for filename in npc_files:
        filepath = os.path.join(NPC_TEMPLATE_DIR, filename)
        
        try:
            with open(filepath, 'r', encoding='utf-8') as f:
                npc_data = json.load(f)
        except Exception as e:
            print(f"Error reading {filename}: {e}")
            continue
        
        npc_name = color_pattern.sub('', npc_data.get('name', 'Unknown'))
        description = npc_data.get('description', '')
        
        print(f"\n[{filename}] {npc_name}")
        
        if not has_placeholder_quests(npc_data):
            print("  ✓ Has valid quests, skipping")
            continue
        
        print("  Needs quest generation...")
        
        quests = generate_quests(client, npc_name, description, 5)
        
        if quests:
            quest_ids = save_quests(quests)
            print(f"  ✓ Generated {len(quests)} quests")
            
            npc_data = update_quest_buttons(npc_data, quest_ids)
            
            with open(filepath, 'w', encoding='utf-8') as f:
                json.dump(npc_data, f, indent=4, ensure_ascii=False)
            print(f"  ✓ Updated NPC file")
        else:
            print("  ✗ Failed to generate quests")
        
        time.sleep(1)
    
    print("\n" + "="*60)
    print("Done!")
    print("="*60)

if __name__ == "__main__":
    main()
