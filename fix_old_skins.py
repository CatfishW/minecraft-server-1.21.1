
import json
import os
import requests
import re
import time

TARGET_DIR = "config/easy_npc/npc_templates"

def get_skin_id(name):
    # Strip color codes
    clean_name = name.replace("§a", "").replace("§b", "").replace("§c", "").replace("§d", "").replace("§e", "").replace("§6", "").replace("§9", "").replace("§5", "").replace("§7", "").replace("§f", "").replace("§l", "").replace("§o", "").strip()
    
    print(f"Searching for {clean_name}...")
    headers = {
        'User-Agent': 'Mozilla/5.0 (iPhone; CPU iPhone OS 14_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/14.0.3 Mobile/15E148 Safari/604.1',
        'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
        'Referer': 'https://namemc.com/'
    }
    
    # URL encode carefully
    query = clean_name.replace(' ', '+')
    
    # 1. Try fully qualified name
    url = f"https://namemc.com/search?q={query}"
    try:
        r = requests.get(url, headers=headers, timeout=10)
        if r.status_code == 200:
            found = re.findall(r'id=([a-f0-9]{16})', r.text)
            if found: return found[0]
            
        time.sleep(1)
        
        # 2. Try first name if full name fails
        if " " in clean_name:
            short = clean_name.split(" ")[0]
            print(f"  Trying short name: {short}")
            r = requests.get(f"https://namemc.com/search?q={short}", headers=headers, timeout=10)
            found = re.findall(r'id=([a-f0-9]{16})', r.text)
            if found: return found[0]
                
    except Exception as e:
        print(f"Error: {e}")
    return None

def main():
    files = [f for f in os.listdir(TARGET_DIR) if f.endswith(".json")]
    
    for filename in files:
        filepath = os.path.join(TARGET_DIR, filename)
        with open(filepath, 'r') as f:
            try:
                data = json.load(f)
            except:
                continue

        # Skip if already a NameMC skin (s.namemc.com or namemc.com)
        current_url = data.get("skin", {}).get("skinUrl", "")
        if "namemc.com" in current_url:
            continue
            
        raw_name = data.get("name", "Unknown")
        print(f"Checking {raw_name} ({filename})...")
        
        skin_id = get_skin_id(raw_name)
        if skin_id:
            new_url = f"https://s.namemc.com/i/{skin_id}.png"
            
            # Additional check to ensure we aren't replacing a custom/good URL with a generic Steve/Alex if searching failed silently
            # But NameMC ID usually guarantees a skin.
            
            if "skin" not in data: data["skin"] = {"type": "URL_SKIN"}
            data["skin"]["skinUrl"] = new_url
            
            with open(filepath, 'w') as f:
                json.dump(data, f, indent=4, ensure_ascii=False)
            print(f"  Updated to {new_url}")
        else:
            print("  No NameMC skin found. Keeping original.")
        
        time.sleep(2)

if __name__ == "__main__":
    main()
