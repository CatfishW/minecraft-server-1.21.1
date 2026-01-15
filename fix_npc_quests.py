import os
import json
import re

QUEST_DIR = "config/easy_npc/quests"
NPC_DIR = "config/easy_npc/npc_templates"

# Common translations for button labels
TRANSLATIONS = {
    "Close": "关闭",
    "Back": "返回",
    "Cancel": "取消",
    "Confirm": "确认",
    "Yes": "是",
    "No": "否",
    "Ok": "好的",
    "Next": "继续",
    "Previous": "上一步",
    "Quest": "任务",
    "Submit": "提交",
    "Accept": "接受",
    "Accept Quest": "接受任务"
}

MANUAL_MAPPING = {
    "Quest: Rabbit's Foot Remedy": "骨头的芬芳",
    "Quest: A Balloon to Pop": "哭泣的黄色雨衣",
    "Quest: Clean Up Your Mess": "公路霸主",
    "Quest: Floating Balloons": "阴影中的窥视者", 
    "Quest: The Silent Stones": "地下室的清道夫",
    "Quest: Rabbit's Foot": "迷失的鸣禽",
    "Quest: The Warden's Roar": "独家新闻：矿井魅影",
    "Quest: Dangerous Pollen": "致命的霉菌",
    "Quest: The Safeguard": "摆脱阴影",
    "Quest: A Butler's Fury": "维护管家尊严",
    "Quest: Echoes of the Past": "尘封往事的回响",
    "Quest: The Upside Down Threat": "来自异界的威胁",
    "Quest: Reclaim the Sewers": "清除下水道的威胁",
    "Quest: Anomalous Material": "来自深渊的样本",
    "Quest: A Glimpse Beyond": "辐射兽的晶状体",
    "Quest: Dismantle the Competition": "剿灭黑吃黑",
    "Quest: Quantum Entanglement Study": "时空回响的收集",
    "Quest: Purge the Glowing Sea": "钢铁动力甲的序曲",
    "Quest: Purification": "净化污染之源",
    "Quest: Phantom Shots": "猎头悬赏：突变狂人",
    "Quest: Clean Up the Common": "街头征服者",
    "Quest: Resonance Cascade": "超维度的共鸣",
    "Quest: Make America Craft Again": "浮华尽褪"
}

def contains_chinese(text):
    return re.search(r'[\u4e00-\u9fff]', text) is not None

def load_quests():
    quests = {} # Map ID -> Data
    quest_title_map = {} # Map Title -> ID
    
    if not os.path.exists(QUEST_DIR):
        print(f"Quest directory not found: {QUEST_DIR}")
        return quests, quest_title_map

    for filename in os.listdir(QUEST_DIR):
        if filename.endswith(".json"):
            filepath = os.path.join(QUEST_DIR, filename)
            try:
                with open(filepath, 'r', encoding='utf-8') as f:
                    data = json.load(f)
                    if 'id' in data:
                        quests[data['id']] = data
                        if 'title' in data:
                            quest_title_map[data['title']] = data['id']
            except Exception as e:
                print(f"Error loading quest {filename}: {e}")
    return quests, quest_title_map

def update_npc_templates(quest_map, quest_title_map):
    if not os.path.exists(NPC_DIR):
        print(f"NPC directory not found: {NPC_DIR}")
        return

    for filename in os.listdir(NPC_DIR):
        if filename.endswith(".json"):
            filepath = os.path.join(NPC_DIR, filename)
            try:
                with open(filepath, 'r', encoding='utf-8') as f:
                    data = json.load(f)
                
                changed = False
                if 'dialogs' in data:
                    for dialog_name, dialog in data['dialogs'].items():
                        if 'buttons' in dialog:
                            for button in dialog['buttons']:
                                original_label = button.get('label', '')
                                action = button.get('action', '')
                                
                                # Check Manual Mapping first
                                if original_label in MANUAL_MAPPING:
                                    target_title = MANUAL_MAPPING[original_label]
                                    if target_title in quest_title_map:
                                        target_id = quest_title_map[target_title]
                                        new_label = f"任务: {target_title}"
                                        new_action = f"OPEN_QUEST_DIALOG:{target_id}"
                                        
                                        button['label'] = new_label
                                        if button['action'] != new_action:
                                             button['action'] = new_action
                                        
                                        print(f"[{filename}] Mapped '{original_label}' -> '{new_label}' (ID: {target_id})")
                                        changed = True
                                        continue

                                # Handle standard Quest Buttons logic
                                if action.startswith("OPEN_QUEST_DIALOG:"):
                                    quest_id = action.split(":")[1].strip()
                                    if quest_id in quest_map:
                                        quest_title = quest_map[quest_id].get('title', 'Unknown Quest')
                                        new_label = f"任务: {quest_title}"
                                        if original_label != new_label:
                                            print(f"[{filename}] Updating quest button: '{original_label}' -> '{new_label}'")
                                            button['label'] = new_label
                                            changed = True
                                    else:
                                        # Only warn if not already handled by mapping
                                        print(f"[{filename}] Warning: Quest ID {quest_id} not found for button '{original_label}'")
                                
                                # Handle Translation
                                else:
                                    if original_label in TRANSLATIONS:
                                        button['label'] = TRANSLATIONS[original_label]
                                        print(f"[{filename}] Translating button: '{original_label}' -> '{button['label']}'")
                                        changed = True

                if changed:
                    with open(filepath, 'w', encoding='utf-8') as f:
                        json.dump(data, f, indent=4, ensure_ascii=False)
                        
            except Exception as e:
                print(f"Error processing NPC {filename}: {e}")

def check_quest_descriptions(quest_map):
    print("\nChecking Quest Descriptions for Chinese content...")
    english_quests = []
    for qid, qdata in quest_map.items():
        desc = qdata.get('description', '')
        if not contains_chinese(desc):
            english_quests.append(qdata.get('title', qid))
            print(f"Quest '{qdata.get('title', qid)}' seems to be English/Empty.")
    
    if not english_quests:
        print("All quests appear to have Chinese content.")

def main():
    print("Loading quests...")
    quest_map, quest_title_map = load_quests()
    print(f"Loaded {len(quest_map)} quests.")
    # for qid, qdata in quest_map.items():
    #     print(f"Quest: {qdata.get('title')} ({qid})")

    print("\nUpdating NPC templates...")
    update_npc_templates(quest_map, quest_title_map)
    
    check_quest_descriptions(quest_map)

if __name__ == "__main__":
    main()
