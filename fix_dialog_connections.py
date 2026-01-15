#!/usr/bin/env python3
"""
Dialog Fixer - Fixes broken dialog connections in NPC files.
Adds missing dialog nodes that are referenced but don't exist.
"""

import json
import os
import re
import uuid

NPC_TEMPLATE_DIR = "/data/Yanlai/minecraft_server_fabric_1.21.1_fresh/config/easy_npc/npc_templates"

def get_dialog_references(dialogs: dict) -> tuple[set, set]:
    """Get all dialog names that are defined and all that are referenced."""
    defined = set(dialogs.keys())
    referenced = set()
    
    for dialog_name, dialog_data in dialogs.items():
        if not isinstance(dialog_data, dict):
            continue
        
        buttons = dialog_data.get('buttons', [])
        for btn in buttons:
            if not isinstance(btn, dict):
                continue
            
            action = btn.get('action', '')
            if action.startswith('SHOW_DIALOG:'):
                target = action.split(':', 1)[1]
                referenced.add(target)
    
    return defined, referenced

def create_fallback_dialog(name: str, npc_name: str) -> dict:
    """Create a fallback dialog based on the dialog name pattern."""
    
    # Determine dialog type from name
    if 'backstory' in name.lower():
        return {
            "greeting": f"（{npc_name}沉默了一会儿）\n\n有些事情…不是那么容易说出口的。也许改天吧。",
            "buttons": [
                {"label": "我理解", "action": "SHOW_DIALOG:main", "id": f"btn_{uuid.uuid4().hex[:8]}"},
                {"label": "没关系", "action": "CLOSE_DIALOG", "id": f"btn_{uuid.uuid4().hex[:8]}"}
            ]
        }
    elif 'current' in name.lower() or 'situation' in name.lower():
        return {
            "greeting": f"（{npc_name}看了看四周）\n\n现在不是谈这个的好时机。等过一阵子再说吧。",
            "buttons": [
                {"label": "好的", "action": "SHOW_DIALOG:main", "id": f"btn_{uuid.uuid4().hex[:8]}"},
                {"label": "保重", "action": "CLOSE_DIALOG", "id": f"btn_{uuid.uuid4().hex[:8]}"}
            ]
        }
    elif 'secret' in name.lower():
        return {
            "greeting": f"（{npc_name}压低声音）\n\n这件事…我还没准备好告诉别人。你需要先证明你值得信任。",
            "buttons": [
                {"label": "我会证明的", "action": "SHOW_DIALOG:main", "id": f"btn_{uuid.uuid4().hex[:8]}"},
                {"label": "我明白", "action": "CLOSE_DIALOG", "id": f"btn_{uuid.uuid4().hex[:8]}"}
            ]
        }
    elif 'quest' in name.lower():
        return {
            "greeting": f"（{npc_name}思考了一下）\n\n我确实有些事情需要帮忙…但是容我再想想具体该怎么做。",
            "buttons": [
                {"label": "随时来找我", "action": "CLOSE_DIALOG", "id": f"btn_{uuid.uuid4().hex[:8]}"},
                {"label": "没问题", "action": "SHOW_DIALOG:main", "id": f"btn_{uuid.uuid4().hex[:8]}"}
            ]
        }
    elif 'fear' in name.lower() or 'emotion' in name.lower() or 'stress' in name.lower():
        return {
            "greeting": f"（{npc_name}深吸一口气）\n\n每个人都有害怕的事情…我也不例外。但我不想多谈这个。",
            "buttons": [
                {"label": "我能理解这种感受", "action": "SHOW_DIALOG:main", "id": f"btn_{uuid.uuid4().hex[:8]}"},
                {"label": "好吧", "action": "CLOSE_DIALOG", "id": f"btn_{uuid.uuid4().hex[:8]}"}
            ]
        }
    elif 'family' in name.lower():
        return {
            "greeting": f"（{npc_name}的表情变得柔和）\n\n家人…是最重要的。但有些事情太私人了，我不想多说。",
            "buttons": [
                {"label": "家人确实很重要", "action": "SHOW_DIALOG:main", "id": f"btn_{uuid.uuid4().hex[:8]}"},
                {"label": "我明白", "action": "CLOSE_DIALOG", "id": f"btn_{uuid.uuid4().hex[:8]}"}
            ]
        }
    elif 'teach' in name.lower() or 'work' in name.lower():
        return {
            "greeting": f"（{npc_name}叹了口气）\n\n工作…生活…都是一言难尽。以后有机会再聊吧。",
            "buttons": [
                {"label": "好的", "action": "SHOW_DIALOG:main", "id": f"btn_{uuid.uuid4().hex[:8]}"}
            ]
        }
    elif 'future' in name.lower():
        return {
            "greeting": f"（{npc_name}望向远方）\n\n未来…谁知道呢？我只能走一步看一步。",
            "buttons": [
                {"label": "希望一切顺利", "action": "CLOSE_DIALOG", "id": f"btn_{uuid.uuid4().hex[:8]}"},
                {"label": "我们再聊", "action": "SHOW_DIALOG:main", "id": f"btn_{uuid.uuid4().hex[:8]}"}
            ]
        }
    else:
        # Generic fallback
        return {
            "greeting": f"（{npc_name}沉默）\n\n……改天再谈这个吧。",
            "buttons": [
                {"label": "好的", "action": "SHOW_DIALOG:main", "id": f"btn_{uuid.uuid4().hex[:8]}"},
                {"label": "再见", "action": "CLOSE_DIALOG", "id": f"btn_{uuid.uuid4().hex[:8]}"}
            ]
        }

def fix_npc_dialogs(filepath: str) -> dict:
    """Fix dialog connections in an NPC file."""
    
    with open(filepath, 'r', encoding='utf-8') as f:
        npc_data = json.load(f)
    
    color_pattern = re.compile(r'§[0-9a-fk-or]')
    npc_name = color_pattern.sub('', npc_data.get('name', 'Unknown'))
    
    dialogs = npc_data.get('dialogs', {})
    if not dialogs:
        return {"file": filepath, "status": "no_dialogs", "added": 0}
    
    defined, referenced = get_dialog_references(dialogs)
    missing = referenced - defined
    
    if not missing:
        return {"file": filepath, "status": "ok", "added": 0}
    
    # Add missing dialogs
    for dialog_name in missing:
        dialogs[dialog_name] = create_fallback_dialog(dialog_name, npc_name)
        print(f"  + Added: {dialog_name}")
    
    npc_data['dialogs'] = dialogs
    
    with open(filepath, 'w', encoding='utf-8') as f:
        json.dump(npc_data, f, indent=4, ensure_ascii=False)
    
    return {"file": filepath, "status": "fixed", "added": len(missing)}

def main():
    print("="*60)
    print("Dialog Connection Fixer")
    print("="*60)
    
    # Process 400+ series NPCs
    npc_files = sorted([f for f in os.listdir(NPC_TEMPLATE_DIR) 
                       if f.startswith('npc_4') and f.endswith('.json')])
    
    print(f"Processing {len(npc_files)} NPC files...\n")
    
    total_fixed = 0
    total_added = 0
    
    for filename in npc_files:
        filepath = os.path.join(NPC_TEMPLATE_DIR, filename)
        print(f"[{filename}]")
        
        result = fix_npc_dialogs(filepath)
        
        if result['status'] == 'fixed':
            total_fixed += 1
            total_added += result['added']
            print(f"  ✓ Fixed ({result['added']} dialogs added)")
        elif result['status'] == 'ok':
            print(f"  ✓ OK (no issues)")
        else:
            print(f"  - No dialogs")
    
    print("\n" + "="*60)
    print(f"Summary: {total_fixed} files fixed, {total_added} dialogs added")
    print("="*60)

if __name__ == "__main__":
    main()
