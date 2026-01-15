
import json
import os
import requests
import concurrent.futures
import re
import time
import random
import uuid
from bs4 import BeautifulSoup
from urllib.parse import quote
from openai import OpenAI

# Configuration
NPC_TEMPLATE_DIR = "/data/Yanlai/minecraft_server_fabric_1.21.1_fresh/config/easy_npc/npc_templates"
QUEST_OUTPUT_DIR = "/data/Yanlai/minecraft_server_fabric_1.21.1_fresh/config/easy_npc/quests"
OPENROUTER_API_KEY = "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"
OPENROUTER_BASE_URL = "https://openrouter.ai/api/v1"
MODEL_NAME = "xiaomi/mimo-v2-flash:free" # Fast flash model

client = OpenAI(
    base_url=OPENROUTER_BASE_URL,
    api_key=OPENROUTER_API_KEY,
)

def read_npc_templates():
    templates = []
    if not os.path.exists(NPC_TEMPLATE_DIR):
        print(f"Directory not found: {NPC_TEMPLATE_DIR}")
        return templates
    
    for filename in os.listdir(NPC_TEMPLATE_DIR):
        if filename.endswith(".json"):
            filepath = os.path.join(NPC_TEMPLATE_DIR, filename)
            try:
                with open(filepath, 'r') as f:
                    data = json.load(f)
                    templates.append({'filename': filename, 'data': data})
            except Exception as e:
                print(f"Error reading {filename}: {e}")
    return templates

def generate_quest_for_npc(npc_data):
    name = npc_data.get('name', 'Unknown').replace('§a', '').replace('§e', '').replace('§b', '').replace('§c', '').replace('§9', '').replace('§d', '').replace('§6', '').replace('§7', '')
    desc = npc_data.get('description', '一个神秘的角色。')
    
    prompt = f"""
    你是一个顶级RPG剧情设计师。请为Minecraft中的角色 "{name}" 设计一个具有沉浸感、代入感且指导明确的任务。
    
    角色背景: {desc}
    
    要求:
    1. 语言: 必须全是中文，严禁出现英文。
    2. 标题: 具有文学修辞感的4-6字标题。
    3. 任务描述: 
       - 第一段: 以NPC口吻描述当前面临的危机、困难或日常背景，与背景故事紧密结合。
       - 第二段: 明确告知玩家需要具体做什么、去哪里做、以及为什么这么做。具有指导性。
    4. 类型(type): 只能在 [KILL, GATHER, CRAFT, TALK] 中选择。
    5. 目标(target): 必须是有效的 minecraft:id (如 minecraft:zombie, minecraft:wheat) 或特定的NPC名称。
    6. TALK目标仅限: "Mike Wheeler", "Steve Harrington", "亨利 (Henry)"。
    7. 奖励(reward): 所有的任务必须奖励 "numismatic-overhaul:bronze_coin"，数量2-5个。
    
    输出格式 (仅输出纯JSON):
    {{
        "id": "随机UUID",
        "title": "标题",
        "description": "沉浸式描述\\n\\n指导性文字",
        "objective": {{ "type": "类型", "target": "minecraft:目标", "amount": 数量 }},
        "reward": {{ "xp": 200, "itemId": "numismatic-overhaul:bronze_coin", "amount": 数量 }}
    }}
    """
    
    try:
        completion = client.chat.completions.create(
            model=MODEL_NAME,
            messages=[
                {"role": "system", "content": "You are a RPG designer. Output only valid JSON. No conversational text."},
                {"role": "user", "content": prompt}
            ],
        )
        content = completion.choices[0].message.content
        
        # Robust JSON extraction
        json_match = re.search(r'\{.*\}', content, re.DOTALL)
        if json_match:
            json_str = json_match.group(0)
            try:
                quest_data = json.loads(json_str)
            except json.JSONDecodeError:
                # Try cleaning common LLM artifacts
                json_str = re.sub(r'\"id\":\s*\"随机UUID\"', f'\"id\": \"{uuid.uuid4()}\"', json_str)
                quest_data = json.loads(json_str)
            
            # Ensure id is a valid UUID
            if 'id' not in quest_data or not re.match(r'^[0-9a-fA-F-]{36}$', str(quest_data['id'])):
                quest_data['id'] = str(uuid.uuid4())
            
            # Force string type for id
            quest_data['id'] = str(quest_data['id'])
            
            # Force fixed reward item
            if 'reward' not in quest_data: quest_data['reward'] = {}
            quest_data['reward']['itemId'] = "numismatic-overhaul:bronze_coin"
            
            return quest_data
        else:
            print(f"Failed to find JSON for {name}")
            return None
    except Exception as e:
        print(f"Error generating quest for {name} using LLM: {e}")
        return None

def save_quest(quest_data):
    if not os.path.exists(QUEST_OUTPUT_DIR):
        os.makedirs(QUEST_OUTPUT_DIR)
        
    # Use full UUID as filename for guaranteed uniqueness and matching
    filename = f"quest_{quest_data['id']}.json"
    filepath = os.path.join(QUEST_OUTPUT_DIR, filename)
    
    with open(filepath, 'w', encoding='utf-8') as f:
        json.dump(quest_data, f, indent=4, ensure_ascii=False)
    print(f"Saved quest: {filepath}")
    return quest_data['id']

def update_npc_template(npc_file_data, quest_id=None, quest_title=None):
    filepath = os.path.join(NPC_TEMPLATE_DIR, npc_file_data['filename'])
    data = npc_file_data['data']
    
    # 1. CLEANUP: Recursively remove all old quest buttons from all dialogs
    updated = False
    if 'dialogs' in data:
        for dialog_id in data['dialogs']:
            dialog = data['dialogs'][dialog_id]
            if 'buttons' in dialog:
                old_buttons = dialog['buttons']
                new_buttons = [b for b in old_buttons if 'OPEN_QUEST_DIALOG' not in b.get('action', '')]
                if len(old_buttons) != len(new_buttons):
                    dialog['buttons'] = new_buttons
                    updated = True
    
    # 2. ADD NEW: If a quest was generated, add the new button
    if quest_id and quest_title:
        if 'dialogs' in data and 'main' in data['dialogs']:
            buttons = data['dialogs']['main'].get('buttons', [])
            buttons.insert(0, {
                "label": quest_title,
                "action": "OPEN_QUEST_DIALOG:" + quest_id, 
                "id": "btn_quest_" + str(uuid.uuid4())[:8]
            })
            data['dialogs']['main']['buttons'] = buttons
            updated = True
        else:
            print(f"Warning: No 'main' dialog found for {npc_file_data['filename']}")
            
    if updated:
        with open(filepath, 'w', encoding='utf-8') as f:
            json.dump(data, f, indent=4, ensure_ascii=False)
        print(f"Updated NPC template: {filepath}")

def main():
    # Clear existing quests
    if os.path.exists(QUEST_OUTPUT_DIR):
        print(f"Clearing old quests in {QUEST_OUTPUT_DIR}...")
        for filename in os.listdir(QUEST_OUTPUT_DIR):
            file_path = os.path.join(QUEST_OUTPUT_DIR, filename)
            try:
                if os.path.isfile(file_path):
                    os.unlink(file_path)
            except Exception as e:
                print(f"Error clearing {file_path}: {e}")

    templates = read_npc_templates()
    print(f"Found {len(templates)} templates.")
    
    # Process a subset for testing or all
    # templates = templates[:5] 
    
    for tmpl in templates:
        print(f"Generating quest for {tmpl['data'].get('name')}...")
        quest = generate_quest_for_npc(tmpl['data'])
        if quest:
            quest_id = save_quest(quest)
            update_npc_template(tmpl, quest_id, quest['title'])
        else:
            # If generation fails, we still want to clean up any OLD quest buttons
            # pointing to deleted files.
            update_npc_template(tmpl)
        time.sleep(1) # Fast model allows smaller delay

if __name__ == "__main__":
    main()
