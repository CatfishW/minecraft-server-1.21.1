#!/usr/bin/env python3
"""
NPC Fixer and Quest Generator
Validates dialogue connections, generates missing quests, and fixes broken NPCs.
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

# NVIDIA API Configuration
NVIDIA_API_KEY = "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"
NVIDIA_BASE_URL = "https://integrate.api.nvidia.com/v1"
MODEL_NAME = "deepseek-ai/deepseek-v3.2"

# ============== ALL NPC NAMES (for TALK quests) ==============
ALL_NPC_NAMES = []

def load_all_npc_names():
    """Load all NPC names from templates for TALK quest targets."""
    global ALL_NPC_NAMES
    if not os.path.exists(NPC_TEMPLATE_DIR):
        return
    
    color_codes = ['§a', '§b', '§c', '§d', '§e', '§f', '§0', '§1', '§2', '§3', 
                   '§4', '§5', '§6', '§7', '§8', '§9', '§l', '§o', '§n', '§m', '§k', '§r']
    
    for filename in os.listdir(NPC_TEMPLATE_DIR):
        if filename.endswith('.json'):
            try:
                with open(os.path.join(NPC_TEMPLATE_DIR, filename), 'r', encoding='utf-8') as f:
                    data = json.load(f)
                    name = data.get('name', '')
                    # Strip color codes
                    for code in color_codes:
                        name = name.replace(code, '')
                    if name and name not in ALL_NPC_NAMES:
                        ALL_NPC_NAMES.append(name)
            except:
                pass
    
    print(f"Loaded {len(ALL_NPC_NAMES)} NPC names for TALK quest targets")

def create_nvidia_client():
    """Create OpenAI client configured for NVIDIA API."""
    return OpenAI(
        base_url=NVIDIA_BASE_URL,
        api_key=NVIDIA_API_KEY
    )

def call_deepseek_api(client: OpenAI, system_prompt: str, user_prompt: str) -> str:
    """Call DeepSeek API with streaming."""
    print("  [API] Calling DeepSeek V3.2...")
    
    full_response = ""
    
    try:
        completion = client.chat.completions.create(
            model=MODEL_NAME,
            messages=[
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_prompt}
            ],
            temperature=0.9,
            top_p=0.95,
            max_tokens=30000,
            extra_body={"chat_template_kwargs": {"thinking": True}},
            stream=True
        )
        
        for chunk in completion:
            if not getattr(chunk, "choices", None):
                continue
            
            # Progress indicator for thinking
            reasoning = getattr(chunk.choices[0].delta, "reasoning_content", None)
            if reasoning:
                print(".", end="", flush=True)
            
            # Capture actual content
            if chunk.choices[0].delta.content is not None:
                full_response += chunk.choices[0].delta.content
        
        print()  # Newline
        return full_response
        
    except Exception as e:
        print(f"\n  [API ERROR] {e}")
        return ""

def extract_json(content: str, is_array: bool = True):
    """Extract JSON from API response."""
    content = re.sub(r'```json\s*', '', content)
    content = re.sub(r'```\s*', '', content)
    
    pattern = r'\[.*\]' if is_array else r'\{.*\}'
    match = re.search(pattern, content, re.DOTALL)
    
    if match:
        try:
            return json.loads(match.group(0))
        except json.JSONDecodeError:
            # Try to fix common issues
            fixed = match.group(0)
            fixed = re.sub(r',\s*}', '}', fixed)
            fixed = re.sub(r',\s*]', ']', fixed)
            try:
                return json.loads(fixed)
            except:
                pass
    return None

def validate_dialog_connections(dialogs: dict) -> tuple[bool, list, set]:
    """
    Validate that all dialog button actions point to existing dialogs.
    Returns: (is_valid, issues, referenced_dialogs)
    """
    if not dialogs:
        return False, ["No dialogs found"], set()
    
    issues = []
    referenced = set()
    
    for dialog_name, dialog_data in dialogs.items():
        if not isinstance(dialog_data, dict):
            issues.append(f"Dialog '{dialog_name}' is not a dict")
            continue
            
        buttons = dialog_data.get('buttons', [])
        if not isinstance(buttons, list):
            issues.append(f"Dialog '{dialog_name}' buttons is not a list")
            continue
        
        for btn in buttons:
            if not isinstance(btn, dict):
                continue
            
            action = btn.get('action', '')
            
            # Check SHOW_DIALOG actions
            if action.startswith('SHOW_DIALOG:'):
                target = action.split(':', 1)[1]
                referenced.add(target)
                if target not in dialogs:
                    issues.append(f"Dialog '{dialog_name}' button points to non-existent '{target}'")
    
    return len(issues) == 0, issues, referenced

def fix_dialog_connections(dialogs: dict) -> dict:
    """Fix broken dialog connections by pointing them to 'main'."""
    if not dialogs:
        return dialogs
    
    for dialog_name, dialog_data in dialogs.items():
        if not isinstance(dialog_data, dict):
            continue
            
        buttons = dialog_data.get('buttons', [])
        if not isinstance(buttons, list):
            continue
        
        for btn in buttons:
            if not isinstance(btn, dict):
                continue
            
            action = btn.get('action', '')
            
            if action.startswith('SHOW_DIALOG:'):
                target = action.split(':', 1)[1]
                if target not in dialogs:
                    # Fix by pointing to main, or creating a farewell
                    if 'farewell' in dialogs:
                        btn['action'] = 'SHOW_DIALOG:farewell'
                    elif 'main' in dialogs:
                        btn['action'] = 'SHOW_DIALOG:main'
                    else:
                        btn['action'] = 'CLOSE_DIALOG'
    
    # Ensure main dialog exists
    if 'main' not in dialogs:
        # Create a basic main dialog
        first_dialog = list(dialogs.keys())[0] if dialogs else None
        dialogs['main'] = {
            "greeting": "你好，有什么我能帮你的？",
            "buttons": [
                {"label": "告辞", "action": "CLOSE_DIALOG", "id": "btn_bye"}
            ]
        }
        if first_dialog and first_dialog != 'main':
            dialogs['main']['buttons'].insert(0, {
                "label": "聊一聊",
                "action": f"SHOW_DIALOG:{first_dialog}",
                "id": "btn_talk"
            })
    
    return dialogs

def get_npc_quest_ids(npc_data: dict) -> list:
    """Extract quest IDs from NPC dialog actions."""
    quest_ids = []
    dialogs = npc_data.get('dialogs', {})
    
    for dialog_name, dialog_data in dialogs.items():
        if not isinstance(dialog_data, dict):
            continue
        
        for btn in dialog_data.get('buttons', []):
            if not isinstance(btn, dict):
                continue
            
            action = btn.get('action', '')
            if 'OPEN_QUEST_DIALOG:RANDOM_POOL:' in action:
                pool = action.split('OPEN_QUEST_DIALOG:RANDOM_POOL:', 1)[1]
                ids = [qid.strip() for qid in pool.split(',') if qid.strip()]
                quest_ids.extend(ids)
    
    return list(set(quest_ids))

def check_quests_exist(quest_ids: list) -> tuple[list, list]:
    """Check which quest IDs have corresponding quest files."""
    existing = []
    missing = []
    
    for qid in quest_ids:
        # Skip placeholder text
        if 'QUEST_' in qid and '_' in qid and not qid.startswith('quest_'):
            missing.append(qid)
            continue
        
        quest_file = os.path.join(QUEST_OUTPUT_DIR, f"quest_{qid}.json")
        if os.path.exists(quest_file):
            existing.append(qid)
        else:
            missing.append(qid)
    
    return existing, missing

def generate_quests_for_npc(client: OpenAI, npc_name: str, npc_description: str, count: int = 5) -> list:
    """Generate quests for an NPC."""
    
    # Get other NPC names for TALK objectives
    other_npcs = [n for n in ALL_NPC_NAMES if n != npc_name]
    talk_targets = random.sample(other_npcs, min(5, len(other_npcs))) if other_npcs else ["Unknown NPC"]
    
    system_prompt = """你是Minecraft RPG任务设计师。生成高质量的中文任务。

任务类型：
- KILL: 击杀生物 (target为minecraft:实体ID)
- GATHER: 收集物品 (target为minecraft:物品ID)  
- TALK: 与NPC对话 (target为NPC名字)

输出JSON数组：
[
    {
        "id": "placeholder",
        "title": "4-6字史诗标题",
        "description": "第一段背景故事\\n\\n第二段具体指导",
        "objective": {"type": "KILL|GATHER|TALK", "target": "目标", "amount": 数量},
        "reward": {"xp": 经验, "itemId": "numismatic-overhaul:bronze_coin", "amount": 数量}
    }
]

奖励参考：
- 简单: 10-30 bronze_coin
- 中等: 50-100 bronze_coin
- 困难: 1-5 silver_coin"""

    user_prompt = f"""为角色 "{npc_name}" 设计 {count} 个沉浸式任务。

角色描述: {npc_description}
可用TALK目标: {', '.join(talk_targets)}

要求：
1. 全部中文
2. 任务符合角色特点
3. 包含2个KILL、2个GATHER、1个TALK
4. 难度有梯度

常用实体: zombie, skeleton, spider, creeper, enderman, witch, pillager, blaze, ghast
常用物品: iron_ingot, gold_ingot, diamond, emerald, wheat, leather, bone, ender_pearl, blaze_rod

只输出JSON数组。"""

    print(f"  Generating {count} quests for {npc_name}...")
    response = call_deepseek_api(client, system_prompt, user_prompt)
    
    if not response:
        return []
    
    quests = extract_json(response, is_array=True)
    if not quests:
        return []
    
    # Validate and fix quests
    valid_quests = []
    for q in quests:
        try:
            q['id'] = str(uuid.uuid4())
            
            # Ensure reward
            if 'reward' not in q or not isinstance(q.get('reward'), dict):
                q['reward'] = {"xp": 100, "itemId": "numismatic-overhaul:bronze_coin", "amount": 25}
            
            if 'itemId' not in q['reward']:
                q['reward']['itemId'] = "numismatic-overhaul:bronze_coin"
            if 'amount' not in q['reward']:
                q['reward']['amount'] = 20
            if 'xp' not in q['reward']:
                q['reward']['xp'] = 100
            
            # Ensure objective
            if 'objective' not in q or not isinstance(q.get('objective'), dict):
                continue
            
            obj = q['objective']
            
            if obj.get('type') not in ['KILL', 'GATHER', 'TALK']:
                obj['type'] = 'GATHER'
            
            # Fix target format
            if obj['type'] in ['KILL', 'GATHER']:
                target = str(obj.get('target', 'zombie'))
                if not target.startswith('minecraft:'):
                    obj['target'] = 'minecraft:' + target.lower().replace(' ', '_')
            
            # Validate TALK targets
            if obj['type'] == 'TALK':
                if obj.get('target') not in ALL_NPC_NAMES:
                    obj['target'] = random.choice(talk_targets)
            
            # Ensure amount
            if 'amount' not in obj or not isinstance(obj.get('amount'), int):
                obj['amount'] = 5
            
            valid_quests.append(q)
            
        except Exception as e:
            print(f"    [QUEST ERROR] {e}")
            continue
    
    return valid_quests

def save_quests(quests: list) -> list:
    """Save quests to files and return their IDs."""
    os.makedirs(QUEST_OUTPUT_DIR, exist_ok=True)
    
    ids = []
    for q in quests:
        filepath = os.path.join(QUEST_OUTPUT_DIR, f"quest_{q['id']}.json")
        with open(filepath, 'w', encoding='utf-8') as f:
            json.dump(q, f, indent=4, ensure_ascii=False)
        ids.append(q['id'])
        print(f"    Saved: quest_{q['id']}.json")
    
    return ids

def update_npc_quest_buttons(npc_data: dict, quest_ids: list) -> dict:
    """Update NPC dialog buttons with valid quest pool."""
    if not quest_ids:
        return npc_data
    
    dialogs = npc_data.get('dialogs', {})
    quest_pool = ",".join(quest_ids)
    
    # Find and update quest-related buttons
    updated = False
    for dialog_name, dialog_data in dialogs.items():
        if not isinstance(dialog_data, dict):
            continue
        
        buttons = dialog_data.get('buttons', [])
        for btn in buttons:
            if not isinstance(btn, dict):
                continue
            
            action = btn.get('action', '')
            
            # Replace placeholder pools
            if 'OPEN_QUEST_DIALOG:RANDOM_POOL:' in action:
                # Check if pool has placeholder values
                current_pool = action.split('OPEN_QUEST_DIALOG:RANDOM_POOL:', 1)[1]
                if 'QUEST_' in current_pool or not current_pool.strip():
                    btn['action'] = f"OPEN_QUEST_DIALOG:RANDOM_POOL:{quest_pool}"
                    updated = True
    
    # If no quest button exists in main, add one
    if 'main' in dialogs:
        main_buttons = dialogs['main'].get('buttons', [])
        has_quest_btn = any('OPEN_QUEST_DIALOG' in btn.get('action', '') for btn in main_buttons if isinstance(btn, dict))
        
        if not has_quest_btn and quest_ids:
            main_buttons.insert(0, {
                "label": "§6§l接受任务",
                "action": f"OPEN_QUEST_DIALOG:RANDOM_POOL:{quest_pool}",
                "id": f"btn_quest_{uuid.uuid4().hex[:8]}"
            })
            dialogs['main']['buttons'] = main_buttons
            updated = True
    
    npc_data['dialogs'] = dialogs
    return npc_data

def process_npc_file(client: OpenAI, filepath: str) -> dict:
    """Process a single NPC file: validate dialogs, generate quests if needed."""
    
    filename = os.path.basename(filepath)
    print(f"\n{'='*50}")
    print(f"Processing: {filename}")
    print('='*50)
    
    try:
        with open(filepath, 'r', encoding='utf-8') as f:
            npc_data = json.load(f)
    except Exception as e:
        print(f"  ERROR: Could not read file: {e}")
        return {"status": "error", "file": filename, "error": str(e)}
    
    # Strip color codes from name for display
    color_codes = ['§a', '§b', '§c', '§d', '§e', '§f', '§0', '§1', '§2', '§3', 
                   '§4', '§5', '§6', '§7', '§8', '§9', '§l', '§o', '§n', '§m', '§k', '§r']
    npc_name = npc_data.get('name', 'Unknown')
    for code in color_codes:
        npc_name = npc_name.replace(code, '')
    
    npc_description = npc_data.get('description', '')
    
    result = {
        "status": "ok",
        "file": filename,
        "name": npc_name,
        "dialog_fixed": False,
        "quests_generated": 0
    }
    
    # 1. Validate and fix dialog connections
    dialogs = npc_data.get('dialogs', {})
    is_valid, issues, referenced = validate_dialog_connections(dialogs)
    
    if not is_valid:
        print(f"  Dialog issues found ({len(issues)}):")
        for issue in issues[:5]:  # Show first 5
            print(f"    - {issue}")
        
        dialogs = fix_dialog_connections(dialogs)
        npc_data['dialogs'] = dialogs
        result["dialog_fixed"] = True
        print("  ✓ Dialog connections fixed")
    else:
        print(f"  ✓ Dialog connections valid ({len(dialogs)} nodes)")
    
    # 2. Check existing quests
    quest_ids = get_npc_quest_ids(npc_data)
    existing_quests, missing_quests = check_quests_exist(quest_ids)
    
    print(f"  Quest references: {len(existing_quests)} valid, {len(missing_quests)} missing/placeholder")
    
    # 3. Generate quests if needed
    if len(existing_quests) < 5 or missing_quests:
        needed = 5 - len(existing_quests)
        if needed > 0:
            print(f"  Generating {needed} new quests...")
            new_quests = generate_quests_for_npc(client, npc_name, npc_description, needed)
            
            if new_quests:
                new_ids = save_quests(new_quests)
                existing_quests.extend(new_ids)
                result["quests_generated"] = len(new_ids)
                print(f"  ✓ Generated {len(new_ids)} quests")
            else:
                print(f"  WARNING: Failed to generate quests")
    
    # 4. Update NPC with valid quest pool
    if existing_quests:
        npc_data = update_npc_quest_buttons(npc_data, existing_quests)
    
    # 5. Save updated NPC
    with open(filepath, 'w', encoding='utf-8') as f:
        json.dump(npc_data, f, indent=4, ensure_ascii=False)
    
    print(f"  ✓ NPC saved")
    
    return result

def main():
    print("=" * 60)
    print("NPC Fixer and Quest Generator")
    print("=" * 60)
    
    # Load all NPC names first
    load_all_npc_names()
    
    # Find target NPC files (400+ series)
    npc_files = []
    for filename in sorted(os.listdir(NPC_TEMPLATE_DIR)):
        if filename.startswith('npc_4') and filename.endswith('.json'):
            npc_files.append(os.path.join(NPC_TEMPLATE_DIR, filename))
    
    print(f"\nFound {len(npc_files)} NPC files to process")
    
    # Create API client
    client = create_nvidia_client()
    
    # Process each NPC
    results = []
    for filepath in npc_files:
        result = process_npc_file(client, filepath)
        results.append(result)
        
        # Rate limiting
        if result.get('quests_generated', 0) > 0:
            print("  Waiting 2 seconds...")
            time.sleep(2)
    
    # Summary
    print("\n" + "=" * 60)
    print("SUMMARY")
    print("=" * 60)
    
    dialogs_fixed = sum(1 for r in results if r.get('dialog_fixed'))
    quests_generated = sum(r.get('quests_generated', 0) for r in results)
    errors = sum(1 for r in results if r.get('status') == 'error')
    
    print(f"  NPCs processed: {len(results)}")
    print(f"  Dialogs fixed: {dialogs_fixed}")
    print(f"  Quests generated: {quests_generated}")
    print(f"  Errors: {errors}")
    
    if errors > 0:
        print("\nErrors:")
        for r in results:
            if r.get('status') == 'error':
                print(f"  - {r['file']}: {r.get('error', 'Unknown')}")

if __name__ == "__main__":
    main()
