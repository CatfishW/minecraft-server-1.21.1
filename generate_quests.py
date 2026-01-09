
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
MODEL_NAME = "xiaomi/mimo-v2-flash:free" # Using a fast model for generation

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
    name = npc_data.get('name', 'Unknown')
    desc = npc_data.get('description', '')
    
    prompt = f"""
    为NPC "{name}" ({desc}) 生成1个Minecraft任务JSON。
    要求:
    1. 必须是中文，描述简短。
    2. 类型(type): KILL, GATHER, CRAFT, TALK (TALK目标见下)。
    3. 目标(target): 必须是正规 minecraft:id (如 minecraft:zombie, minecraft:wheat)。
    4. TALK目标只能是: "亨利 (Henry)", "Mike Wheeler", "Steve Harrington"。
    5. 奖励物品(itemId)必须是: "novus_items:bronze_novus_coin"。
    
    结构示例 (输出 JSON):
    {{
        "id": "随机UUID",
        "title": "标题",
        "description": "描述",
        "objective": {{ "type": "类型", "target": "目标", "amount": 数量 }},
        "reward": {{ "xp": 200, "itemId": "novus_items:bronze_novus_coin", "amount": 2 }}
    }}
    """
    
    try:
        completion = client.chat.completions.create(
            model=MODEL_NAME,
            messages=[
                {"role": "system", "content": "You are a professional RPG quest designer. Output ONLY valid JSON."},
                {"role": "user", "content": prompt}
            ],
        )
        content = completion.choices[0].message.content
        # Extract JSON
        content = re.sub(r'```json\s*|\s*```', '', content)
        start_idx = content.find('{')
        end_idx = content.rfind('}')
        if start_idx != -1 and end_idx != -1:
            json_str = content[start_idx:end_idx+1]
            quest_data = json.loads(json_str)
            
            # Ensure id is a valid UUID
            try:
                if 'id' in quest_data:
                    uuid.UUID(str(quest_data['id']))
                else:
                    quest_data['id'] = str(uuid.uuid4())
            except (ValueError, TypeError):
                quest_data['id'] = str(uuid.uuid4())
            
            # Ensure rewards are consistent
            if 'reward' not in quest_data:
                quest_data['reward'] = {}
            quest_data['reward']['itemId'] = "novus_items:bronze_novus_coin"
            
            return quest_data
        else:
            print(f"Failed to parse JSON for {name}")
            return None
    except Exception as e:
        print(f"Error generating quest for {name}: {e}")
        return None

def save_quest(quest_data, npc_filename):
    if not os.path.exists(QUEST_OUTPUT_DIR):
        os.makedirs(QUEST_OUTPUT_DIR)
        
    filename = f"quest_{quest_data['id'][:8]}.json"
    filepath = os.path.join(QUEST_OUTPUT_DIR, filename)
    
    with open(filepath, 'w', encoding='utf-8') as f:
        json.dump(quest_data, f, indent=4, ensure_ascii=False)
    print(f"Saved quest: {filepath}")
    return quest_data['id']

def update_npc_template(npc_file_data, quest_id, quest_title):
    filepath = os.path.join(NPC_TEMPLATE_DIR, npc_file_data['filename'])
    data = npc_file_data['data']
    
    # Add a quest button to the main dialog
    if 'dialogs' in data and 'main' in data['dialogs']:
        buttons = data['dialogs']['main'].get('buttons', [])
        
        # REMOVE ALL OLD QUEST BUTTONS FIRST
        buttons = [b for b in buttons if not (b.get('label', '').startswith("任务: ") or b.get('label', '').startswith("Quest: "))]
        
        # Add the button to main to link to quest offer
        buttons.insert(0, {
            "label": quest_title, # No prefix needed if requested by user to be clean
            "action": "OPEN_QUEST_DIALOG:" + quest_id, 
            "id": "btn_quest_" + str(uuid.uuid4())[:8]
        })
        data['dialogs']['main']['buttons'] = buttons
        
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
            quest_id = save_quest(quest, tmpl['filename'])
            update_npc_template(tmpl, quest_id, quest['title'])
            time.sleep(1) # Rate limit

if __name__ == "__main__":
    main()
