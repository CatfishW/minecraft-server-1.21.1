#!/usr/bin/env python3
"""
NPC Regenerator - Fixes short dialogues and placeholder quest references
Uses DeepSeek V3.2 to regenerate proper dialogues for NPCs with minimal content
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

MIN_DIALOG_NODES = 5  # NPCs with fewer nodes will be regenerated

ALL_NPC_NAMES = []
color_pattern = re.compile(r'§[0-9a-fk-or]')

def load_all_npc_names():
    global ALL_NPC_NAMES
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

def get_dialog_count(npc_data: dict) -> int:
    """Count the number of dialog nodes."""
    dialogs = npc_data.get('dialogs', {})
    return len(dialogs)

def has_placeholder_quests(npc_data: dict) -> bool:
    """Check if NPC has placeholder quest references like QUEST_xxx."""
    dialogs = npc_data.get('dialogs', {})
    for dialog in dialogs.values():
        if not isinstance(dialog, dict):
            continue
        for btn in dialog.get('buttons', []):
            action = btn.get('action', '')
            if 'OPEN_QUEST_DIALOG:RANDOM_POOL:' in action:
                pool = action.split('RANDOM_POOL:', 1)[1]
                # Check for placeholder patterns (QUEST_XXX instead of UUIDs)
                if re.search(r'QUEST_[A-Z_]+', pool):
                    return True
    return False

def get_existing_quests() -> list:
    """Get list of existing quest UUIDs."""
    quests = []
    if os.path.exists(QUEST_OUTPUT_DIR):
        for f in os.listdir(QUEST_OUTPUT_DIR):
            if f.startswith('quest_') and f.endswith('.json'):
                qid = f.replace('quest_', '').replace('.json', '')
                quests.append(qid)
    return quests

def fix_quest_references(npc_data: dict, quest_ids: list) -> dict:
    """Replace placeholder quest references with real quest UUIDs."""
    if not quest_ids:
        return npc_data
    
    pool = ",".join(quest_ids[:5])  # Use up to 5 quests
    dialogs = npc_data.get('dialogs', {})
    
    for dialog in dialogs.values():
        if not isinstance(dialog, dict):
            continue
        for btn in dialog.get('buttons', []):
            action = btn.get('action', '')
            if 'OPEN_QUEST_DIALOG:RANDOM_POOL:' in action:
                current = action.split('RANDOM_POOL:', 1)[1]
                # Replace if has placeholder patterns
                if re.search(r'QUEST_[A-Z_]+', current):
                    btn['action'] = f"OPEN_QUEST_DIALOG:RANDOM_POOL:{pool}"
    
    return npc_data

def generate_dialogs_for_npc(client: OpenAI, npc_name: str, description: str, theme: str, role: str, connections: list) -> dict:
    """Generate rich dialogues for an NPC."""
    
    prompt = f"""为Minecraft NPC "{npc_name}" 生成完整的中文对话系统。

角色信息:
- 名字: {npc_name}
- 主题: {theme}
- 角色定位: {role}
- 背景描述: {description}
- 关联角色: {', '.join(connections) if connections else '无'}

生成要求:
1. 至少8个对话节点，包含深度分支
2. 全部中文对话
3. 对话要符合角色性格和背景
4. 包含：main主对话、backstory背景、current当前状况、quest任务钩子、secret秘密、farewell告别

JSON格式（严格遵守）:
{{
  "main": {{
    "greeting": "主对话内容",
    "buttons": [
      {{"label": "按钮文字", "action": "SHOW_DIALOG:dialog_id", "id": "btn_xxx"}},
      {{"label": "任务选项", "action": "OPEN_QUEST_DIALOG:RANDOM_POOL:PLACEHOLDER", "id": "btn_quest"}}
    ]
  }},
  "backstory_1": {{
    "greeting": "背景故事...",
    "buttons": [...]
  }},
  "farewell": {{
    "greeting": "告别语",
    "buttons": [{{"label": "再见", "action": "CLOSE_DIALOG", "id": "btn_bye"}}]
  }}
}}

action类型:
- SHOW_DIALOG:节点名 - 跳转对话
- CLOSE_DIALOG - 关闭对话
- OPEN_QUEST_DIALOG:RANDOM_POOL:PLACEHOLDER - 任务（稍后替换）

只输出JSON对象，不要其他内容。"""

    print(f"  Generating dialogues...")
    
    try:
        completion = client.chat.completions.create(
            model=MODEL_NAME,
            messages=[{"role": "user", "content": prompt}],
            temperature=0.8,
            max_tokens=8000,
            stream=False
        )
        
        response = completion.choices[0].message.content
        print(f"  Got response ({len(response)} chars)")
        
        # Extract JSON
        response = re.sub(r'```json?\s*', '', response)
        response = re.sub(r'```', '', response)
        
        # Try to find JSON object
        match = re.search(r'\{[\s\S]*\}', response)
        if match:
            try:
                dialogs = json.loads(match.group(0))
                # Handle nested dialogs structure
                if 'dialogs' in dialogs:
                    return dialogs['dialogs']
                return dialogs
            except json.JSONDecodeError as e:
                print(f"  JSON error: {e}")
                return None
        
    except Exception as e:
        print(f"  API error: {e}")
    
    return None

def create_fallback_dialogs(npc_name: str, description: str, theme: str) -> dict:
    """Create fallback dialogues if API fails."""
    return {
        "main": {
            "greeting": f"（{npc_name}注意到你的到来）\n\n{description[:100]}...",
            "buttons": [
                {"label": "聊聊你自己？", "action": "SHOW_DIALOG:backstory_intro", "id": "btn_about"},
                {"label": "现在的情况如何？", "action": "SHOW_DIALOG:current_situation", "id": "btn_current"},
                {"label": "有什么需要帮忙的？", "action": "SHOW_DIALOG:quest_intro", "id": "btn_quest"},
                {"label": "告辞", "action": "CLOSE_DIALOG", "id": "btn_bye"}
            ]
        },
        "backstory_intro": {
            "greeting": f"（{npc_name}思考了一下）\n\n我的故事...说来话长。在这个世界里，每个人都有自己的过去。我也不例外。",
            "buttons": [
                {"label": "继续说", "action": "SHOW_DIALOG:backstory_deep", "id": "btn_continue"},
                {"label": "我理解", "action": "SHOW_DIALOG:main", "id": "btn_back"}
            ]
        },
        "backstory_deep": {
            "greeting": f"（{npc_name}叹了口气）\n\n有些事情...不是那么容易说出口的。也许我们需要更多的时间来建立信任。",
            "buttons": [
                {"label": "我愿意等待", "action": "SHOW_DIALOG:secret_locked", "id": "btn_wait"},
                {"label": "返回", "action": "SHOW_DIALOG:main", "id": "btn_back2"}
            ]
        },
        "current_situation": {
            "greeting": f"（{npc_name}环顾四周）\n\n现在的处境...还算稳定。但谁知道明天会发生什么呢？这个世界充满了不确定性。",
            "buttons": [
                {"label": "有什么困难吗？", "action": "SHOW_DIALOG:quest_intro", "id": "btn_trouble"},
                {"label": "祝你好运", "action": "SHOW_DIALOG:farewell", "id": "btn_luck"}
            ]
        },
        "quest_intro": {
            "greeting": f"（{npc_name}认真地看着你）\n\n帮忙？确实有些事情需要人手。如果你愿意的话...",
            "buttons": [
                {"label": "我愿意帮忙", "action": "OPEN_QUEST_DIALOG:RANDOM_POOL:PLACEHOLDER", "id": "btn_accept"},
                {"label": "让我考虑一下", "action": "SHOW_DIALOG:main", "id": "btn_think"}
            ]
        },
        "secret_locked": {
            "greeting": f"（{npc_name}压低声音）\n\n有些秘密...需要时间才能揭晓。继续和我交流，也许有一天我会告诉你一切。",
            "buttons": [
                {"label": "我会再来的", "action": "CLOSE_DIALOG", "id": "btn_return"},
                {"label": "我明白", "action": "SHOW_DIALOG:main", "id": "btn_understand"}
            ]
        },
        "farewell": {
            "greeting": f"（{npc_name}点点头）\n\n保重，旅人。希望我们很快能再见面。",
            "buttons": [
                {"label": "再见", "action": "CLOSE_DIALOG", "id": "btn_goodbye"}
            ]
        }
    }

def generate_quests_for_npc(client: OpenAI, npc_name: str, description: str) -> list:
    """Generate quests for an NPC."""
    
    talk_targets = random.sample([n for n in ALL_NPC_NAMES if n != npc_name], min(3, len(ALL_NPC_NAMES)-1))
    
    prompt = f"""为角色"{npc_name}"生成5个Minecraft任务。

角色背景: {description[:150]}

JSON数组格式：
[{{"title":"4-6字中文标题","description":"任务描述（中文）","objective":{{"type":"KILL","target":"minecraft:zombie","amount":10}},"reward":{{"xp":100,"itemId":"numismatic-overhaul:bronze_coin","amount":30}}}}]

类型: KILL(击杀), GATHER(收集), TALK(对话)
KILL目标: minecraft:zombie, minecraft:skeleton, minecraft:spider, minecraft:creeper, minecraft:enderman
GATHER目标: minecraft:iron_ingot, minecraft:diamond, minecraft:wheat, minecraft:leather
TALK目标: {', '.join(talk_targets)}

只输出JSON数组。"""

    try:
        completion = client.chat.completions.create(
            model=MODEL_NAME,
            messages=[{"role": "user", "content": prompt}],
            temperature=0.7,
            max_tokens=4000,
            stream=False
        )
        
        response = completion.choices[0].message.content
        response = re.sub(r'```json?\s*', '', response)
        response = re.sub(r'```', '', response)
        
        match = re.search(r'\[[\s\S]*\]', response)
        if match:
            quests = json.loads(match.group(0))
            
            valid = []
            for q in quests:
                q['id'] = str(uuid.uuid4())
                
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
                
                rew = q.get('reward', {})
                if 'itemId' not in rew:
                    rew['itemId'] = 'numismatic-overhaul:bronze_coin'
                rew['amount'] = int(rew.get('amount', 25))
                rew['xp'] = int(rew.get('xp', 100))
                q['reward'] = rew
                
                valid.append(q)
            
            return valid
            
    except Exception as e:
        print(f"  Quest generation error: {e}")
    
    return []

def create_fallback_quests(npc_name: str) -> list:
    """Create fallback quests."""
    talk_target = random.choice(ALL_NPC_NAMES) if ALL_NPC_NAMES else "Unknown"
    
    return [
        {
            "id": str(uuid.uuid4()),
            "title": "清除亡灵威胁",
            "description": f"{npc_name}需要你帮忙清理附近的亡灵。\n\n消灭僵尸，确保安全。",
            "objective": {"type": "KILL", "target": "minecraft:zombie", "amount": 10},
            "reward": {"xp": 100, "itemId": "numismatic-overhaul:bronze_coin", "amount": 30}
        },
        {
            "id": str(uuid.uuid4()),
            "title": "骷髅猎人",
            "description": f"{npc_name}担心骷髅弓箭手的威胁。\n\n猎杀骷髅以确保安全。",
            "objective": {"type": "KILL", "target": "minecraft:skeleton", "amount": 8},
            "reward": {"xp": 120, "itemId": "numismatic-overhaul:bronze_coin", "amount": 40}
        },
        {
            "id": str(uuid.uuid4()),
            "title": "珍贵矿物",
            "description": f"{npc_name}需要一些铁锭用于重要的事情。\n\n收集铁锭并带回来。",
            "objective": {"type": "GATHER", "target": "minecraft:iron_ingot", "amount": 16},
            "reward": {"xp": 150, "itemId": "numismatic-overhaul:bronze_coin", "amount": 50}
        },
        {
            "id": str(uuid.uuid4()),
            "title": "稀有宝石",
            "description": f"{npc_name}对你的能力感到好奇。\n\n带回钻石证明你的实力。",
            "objective": {"type": "GATHER", "target": "minecraft:diamond", "amount": 3},
            "reward": {"xp": 300, "itemId": "numismatic-overhaul:silver_coin", "amount": 2}
        },
        {
            "id": str(uuid.uuid4()),
            "title": "传递情报",
            "description": f"{npc_name}有重要消息要传达给{talk_target}。\n\n找到他并完成对话。",
            "objective": {"type": "TALK", "target": talk_target, "amount": 1},
            "reward": {"xp": 80, "itemId": "numismatic-overhaul:bronze_coin", "amount": 25}
        }
    ]

def save_quests(quests: list) -> list:
    """Save quests to files."""
    os.makedirs(QUEST_OUTPUT_DIR, exist_ok=True)
    ids = []
    for q in quests:
        path = os.path.join(QUEST_OUTPUT_DIR, f"quest_{q['id']}.json")
        with open(path, 'w', encoding='utf-8') as f:
            json.dump(q, f, indent=4, ensure_ascii=False)
        ids.append(q['id'])
    return ids

def update_quest_placeholders(dialogs: dict, quest_ids: list) -> dict:
    """Replace PLACEHOLDER in dialogs with real quest IDs."""
    if not quest_ids:
        return dialogs
    
    pool = ",".join(quest_ids)
    
    for dialog in dialogs.values():
        if not isinstance(dialog, dict):
            continue
        for btn in dialog.get('buttons', []):
            action = btn.get('action', '')
            if 'PLACEHOLDER' in action:
                btn['action'] = action.replace('PLACEHOLDER', pool)
    
    return dialogs

def main():
    print("="*60)
    print("NPC Regenerator - Fix Short Dialogues & Quest References")
    print("="*60)
    
    load_all_npc_names()
    client = create_client()
    existing_quests = get_existing_quests()
    print(f"Found {len(existing_quests)} existing quests")
    
    # Find NPCs that need fixing (400+ series)
    npc_files = sorted([f for f in os.listdir(NPC_TEMPLATE_DIR) 
                       if f.startswith('npc_4') and f.endswith('.json')])
    
    npcs_to_fix = []
    
    for filename in npc_files:
        filepath = os.path.join(NPC_TEMPLATE_DIR, filename)
        try:
            with open(filepath, 'r', encoding='utf-8') as f:
                npc_data = json.load(f)
        except:
            continue
        
        dialog_count = get_dialog_count(npc_data)
        has_placeholders = has_placeholder_quests(npc_data)
        
        npc_name = color_pattern.sub('', npc_data.get('name', 'Unknown'))
        
        if dialog_count < MIN_DIALOG_NODES or has_placeholders:
            npcs_to_fix.append({
                'filename': filename,
                'filepath': filepath,
                'name': npc_name,
                'data': npc_data,
                'dialog_count': dialog_count,
                'has_placeholders': has_placeholders,
                'needs_regen': dialog_count < MIN_DIALOG_NODES
            })
            print(f"  {filename}: {dialog_count} dialogs, placeholders={has_placeholders}")
    
    print(f"\nFound {len(npcs_to_fix)} NPCs to fix\n")
    
    if not npcs_to_fix:
        print("All NPCs are properly configured!")
        return
    
    for i, npc in enumerate(npcs_to_fix):
        print(f"\n[{i+1}/{len(npcs_to_fix)}] {npc['name']}")
        print("-" * 40)
        
        npc_data = npc['data']
        metadata = npc_data.get('metadata', {})
        theme = metadata.get('theme', 'Unknown')
        role = metadata.get('role', 'NPC')
        connections = metadata.get('connections', [])
        description = npc_data.get('description', '')
        
        # Regenerate dialogues if too few
        if npc['needs_regen']:
            print(f"  Regenerating dialogues (was {npc['dialog_count']} nodes)...")
            
            new_dialogs = generate_dialogs_for_npc(
                client, npc['name'], description, theme, role, connections
            )
            
            if new_dialogs and len(new_dialogs) >= MIN_DIALOG_NODES:
                npc_data['dialogs'] = new_dialogs
                print(f"  ✓ Generated {len(new_dialogs)} dialog nodes")
            else:
                print(f"  Using fallback dialogues...")
                npc_data['dialogs'] = create_fallback_dialogs(npc['name'], description, theme)
        
        # Generate or use existing quests
        quest_ids = []
        
        # First try to find quests specific to this NPC or use random existing ones
        if existing_quests:
            quest_ids = random.sample(existing_quests, min(5, len(existing_quests)))
        
        # If no existing quests, generate new ones
        if not quest_ids:
            print(f"  Generating quests...")
            quests = generate_quests_for_npc(client, npc['name'], description)
            
            if not quests or len(quests) < 5:
                print(f"  Using fallback quests...")
                quests = create_fallback_quests(npc['name'])
            
            quest_ids = save_quests(quests)
            print(f"  ✓ Saved {len(quest_ids)} quests")
        
        # Update quest references
        npc_data['dialogs'] = update_quest_placeholders(npc_data['dialogs'], quest_ids)
        npc_data = fix_quest_references(npc_data, quest_ids)
        
        # Save NPC
        with open(npc['filepath'], 'w', encoding='utf-8') as f:
            json.dump(npc_data, f, indent=4, ensure_ascii=False)
        
        print(f"  ✓ Saved {npc['filename']}")
        
        time.sleep(2)
    
    print("\n" + "="*60)
    print("Done! All NPCs have been fixed.")
    print("="*60)

if __name__ == "__main__":
    main()
