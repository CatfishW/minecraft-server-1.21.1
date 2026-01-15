#!/usr/bin/env python3
"""
Quest Generator V2 - More robust JSON handling
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

def fix_json_string(s: str) -> str:
    """Try to fix common JSON issues."""
    # Remove control characters
    s = re.sub(r'[\x00-\x1f\x7f-\x9f]', '', s)
    # Fix trailing commas
    s = re.sub(r',\s*}', '}', s)
    s = re.sub(r',\s*]', ']', s)
    # Fix missing commas between objects
    s = re.sub(r'}\s*{', '},{', s)
    # Fix unescaped quotes in strings (simple heuristic)
    # This is tricky, so we won't do complex fixing
    return s

def extract_quests_from_text(text: str) -> list:
    """Extract quest objects from possibly malformed text."""
    quests = []
    
    # First try clean JSON extraction
    text = re.sub(r'```json?\s*', '', text)
    text = re.sub(r'```', '', text)
    
    try:
        match = re.search(r'\[[\s\S]*\]', text)
        if match:
            fixed = fix_json_string(match.group(0))
            return json.loads(fixed)
    except:
        pass
    
    # Try to extract individual quest objects
    pattern = r'\{[^{}]*"title"[^{}]*"description"[^{}]*"objective"[^{}]*\}'
    matches = re.findall(pattern, text, re.DOTALL)
    
    for m in matches:
        try:
            q = json.loads(fix_json_string(m))
            if 'title' in q and 'objective' in q:
                quests.append(q)
        except:
            continue
    
    return quests

def create_client():
    return OpenAI(base_url=NVIDIA_BASE_URL, api_key=NVIDIA_API_KEY)

def generate_quests_batch(client, npcs: list) -> dict:
    """Generate quests for multiple NPCs in one call for efficiency."""
    
    talk_targets = random.sample(ALL_NPC_NAMES, min(5, len(ALL_NPC_NAMES)))
    
    npc_list = "\n".join([f"- {npc['name']}: {npc['desc'][:100]}" for npc in npcs])
    
    prompt = f"""为以下{len(npcs)}个NPC各生成5个任务，共{len(npcs)*5}个任务。

NPC列表:
{npc_list}

输出严格JSON数组，每个任务包含npc_name字段:
[
  {{"npc_name":"NPC名","title":"任务标题","description":"任务描述","objective":{{"type":"KILL","target":"minecraft:zombie","amount":10}},"reward":{{"xp":100,"itemId":"numismatic-overhaul:bronze_coin","amount":30}}}},
  ...
]

任务类型type只能是: KILL, GATHER, TALK
- KILL的target: minecraft:zombie, minecraft:skeleton, minecraft:spider, minecraft:creeper
- GATHER的target: minecraft:iron_ingot, minecraft:diamond, minecraft:wheat, minecraft:leather  
- TALK的target: {', '.join(talk_targets)}

全部中文，只输出JSON数组。"""

    print(f"  Generating quests for {len(npcs)} NPCs...")
    
    try:
        completion = client.chat.completions.create(
            model=MODEL_NAME,
            messages=[{"role": "user", "content": prompt}],
            temperature=0.7,
            max_tokens=8000,
            stream=False  # Non-streaming for more reliable response
        )
        
        response = completion.choices[0].message.content
        print(f"  Got response ({len(response)} chars)")
        
        quests = extract_quests_from_text(response)
        print(f"  Extracted {len(quests)} quests")
        
        # Group by NPC name
        result = {}
        for q in quests:
            npc_name = q.pop('npc_name', None)
            if npc_name:
                if npc_name not in result:
                    result[npc_name] = []
                result[npc_name].append(q)
        
        return result
        
    except Exception as e:
        print(f"  Error: {e}")
        return {}

def generate_single_quest(npc_name: str, quest_type: str) -> dict:
    """Generate a single hardcoded quest as fallback."""
    
    talk_target = random.choice(ALL_NPC_NAMES) if ALL_NPC_NAMES else "Unknown"
    
    templates = {
        "KILL": [
            {"title": "清除亡灵威胁", "desc": "附近出现了大量亡灵生物，威胁着居民的安全。\n\n前往附近的废墟，消灭那里的亡灵生物。", "target": "minecraft:zombie", "amount": 10},
            {"title": "骷髅猎人", "desc": "夜晚的骷髅越来越多，它们的箭矢让人不敢出门。\n\n猎杀骷髅弓箭手，让夜晚变得安全。", "target": "minecraft:skeleton", "amount": 8},
            {"title": "蜘蛛巢穴", "desc": "矿洞深处发现了蜘蛛巢穴，阻碍了采矿工作。\n\n清理蜘蛛巢穴，确保矿工安全。", "target": "minecraft:spider", "amount": 12},
            {"title": "末影追踪", "desc": "神秘的末影人在夜间出没，带走了重要物品。\n\n追踪并消灭末影人，找回失物。", "target": "minecraft:enderman", "amount": 5},
            {"title": "爆破危机", "desc": "苦力怕数量激增，农田和建筑遭受威胁。\n\n消灭苦力怕，保护村庄设施。", "target": "minecraft:creeper", "amount": 6},
        ],
        "GATHER": [
            {"title": "铁锭采集", "desc": "工坊需要大量铁锭来锻造武器装备。\n\n收集铁锭并交给我。", "target": "minecraft:iron_ingot", "amount": 16},
            {"title": "黄金储备", "desc": "我们需要黄金来进行重要的贸易。\n\n收集金锭用于交易。", "target": "minecraft:gold_ingot", "amount": 10},
            {"title": "珍贵钻石", "desc": "钻石是最珍贵的宝石，用于制作最强装备。\n\n深入矿洞寻找钻石。", "target": "minecraft:diamond", "amount": 4},
            {"title": "收获季节", "desc": "村庄需要粮食储备以度过冬天。\n\n收获小麦用于制作面包。", "target": "minecraft:wheat", "amount": 32},
            {"title": "皮革加工", "desc": "制革工坊缺少原材料。\n\n收集皮革用于制作护甲和书籍。", "target": "minecraft:leather", "amount": 20},
        ],
        "TALK": [
            {"title": "传递情报", "desc": f"我有重要消息需要传达给{talk_target}。\n\n找到他并传达我的口信。", "target": talk_target, "amount": 1},
        ]
    }
    
    template = random.choice(templates[quest_type])
    
    reward_amount = random.randint(20, 50) if quest_type != "GATHER" else random.randint(30, 80)
    
    return {
        "id": str(uuid.uuid4()),
        "title": template["title"],
        "description": template["desc"],
        "objective": {
            "type": quest_type,
            "target": template["target"],
            "amount": template["amount"]
        },
        "reward": {
            "xp": random.randint(50, 200),
            "itemId": "numismatic-overhaul:bronze_coin",
            "amount": reward_amount
        }
    }

def validate_quest(q: dict) -> dict:
    """Validate and fix a quest object."""
    q['id'] = str(uuid.uuid4())
    
    obj = q.get('objective', {})
    if obj.get('type') not in ['KILL', 'GATHER', 'TALK']:
        obj['type'] = 'KILL'
        obj['target'] = 'minecraft:zombie'
    
    if obj['type'] in ['KILL', 'GATHER']:
        t = str(obj.get('target', 'zombie'))
        if not t.startswith('minecraft:'):
            obj['target'] = 'minecraft:' + t.lower().replace(' ', '_')
    
    if obj['type'] == 'TALK' and obj.get('target') not in ALL_NPC_NAMES:
        obj['target'] = random.choice(ALL_NPC_NAMES) if ALL_NPC_NAMES else "Unknown"
    
    obj['amount'] = max(1, int(obj.get('amount', 5)))
    q['objective'] = obj
    
    rew = q.get('reward', {})
    if 'itemId' not in rew or 'coin' not in str(rew.get('itemId', '')):
        rew['itemId'] = 'numismatic-overhaul:bronze_coin'
    rew['amount'] = max(1, int(rew.get('amount', 25)))
    rew['xp'] = max(10, int(rew.get('xp', 100)))
    q['reward'] = rew
    
    return q

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
    dialogs = npc_data.get('dialogs', {})
    for dialog in dialogs.values():
        if not isinstance(dialog, dict):
            continue
        for btn in dialog.get('buttons', []):
            action = btn.get('action', '')
            if 'OPEN_QUEST_DIALOG:RANDOM_POOL:' in action:
                pool = action.split('RANDOM_POOL:', 1)[1]
                if 'QUEST_' in pool or not pool.strip():
                    return True
                for qid in pool.split(','):
                    qid = qid.strip()
                    if qid and not os.path.exists(os.path.join(QUEST_OUTPUT_DIR, f"quest_{qid}.json")):
                        return True
    return False

def update_quest_buttons(npc_data: dict, quest_ids: list) -> dict:
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
                if 'QUEST_' in current or not current.strip():
                    btn['action'] = f"OPEN_QUEST_DIALOG:RANDOM_POOL:{pool}"
                else:
                    needs_update = any(
                        qid.strip() and not os.path.exists(os.path.join(QUEST_OUTPUT_DIR, f"quest_{qid.strip()}.json"))
                        for qid in current.split(',')
                    )
                    if needs_update:
                        btn['action'] = f"OPEN_QUEST_DIALOG:RANDOM_POOL:{pool}"
    
    return npc_data

def main():
    print("="*60)
    print("Quest Generator V2")
    print("="*60)
    
    load_all_npc_names()
    client = create_client()
    
    color_pattern = re.compile(r'§[0-9a-fk-or]')
    
    # Find NPCs that need quests
    npc_files = sorted([f for f in os.listdir(NPC_TEMPLATE_DIR) 
                       if f.startswith('npc_4') and f.endswith('.json')])
    
    npcs_needing_quests = []
    
    for filename in npc_files:
        filepath = os.path.join(NPC_TEMPLATE_DIR, filename)
        try:
            with open(filepath, 'r', encoding='utf-8') as f:
                npc_data = json.load(f)
        except:
            continue
        
        if has_placeholder_quests(npc_data):
            npc_name = color_pattern.sub('', npc_data.get('name', 'Unknown'))
            description = npc_data.get('description', '')
            npcs_needing_quests.append({
                'filename': filename,
                'filepath': filepath,
                'name': npc_name,
                'desc': description,
                'data': npc_data
            })
    
    print(f"\nFound {len(npcs_needing_quests)} NPCs needing quests")
    
    if not npcs_needing_quests:
        print("All NPCs have valid quests!")
        return
    
    # Try batch generation first (3 NPCs at a time)
    batch_size = 3
    for i in range(0, len(npcs_needing_quests), batch_size):
        batch = npcs_needing_quests[i:i+batch_size]
        
        print(f"\nBatch {i//batch_size + 1}: {[n['name'] for n in batch]}")
        
        # Try API generation
        generated = generate_quests_batch(client, batch)
        
        for npc in batch:
            npc_quests = generated.get(npc['name'], [])
            
            # If API failed or returned too few quests, use fallback
            if len(npc_quests) < 5:
                print(f"  {npc['name']}: Using fallback quests ({len(npc_quests)} from API)")
                quest_types = ['KILL', 'KILL', 'GATHER', 'GATHER', 'TALK']
                random.shuffle(quest_types)
                
                while len(npc_quests) < 5:
                    qtype = quest_types[len(npc_quests) % 5]
                    npc_quests.append(generate_single_quest(npc['name'], qtype))
            
            # Validate all quests
            valid_quests = [validate_quest(q) for q in npc_quests[:5]]
            
            # Save quests
            quest_ids = save_quests(valid_quests)
            print(f"  {npc['name']}: Saved {len(quest_ids)} quests")
            
            # Update NPC file
            npc['data'] = update_quest_buttons(npc['data'], quest_ids)
            with open(npc['filepath'], 'w', encoding='utf-8') as f:
                json.dump(npc['data'], f, indent=4, ensure_ascii=False)
        
        time.sleep(2)
    
    print("\n" + "="*60)
    print("Done! All NPCs now have valid quests.")
    print("="*60)

if __name__ == "__main__":
    main()
