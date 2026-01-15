
import json
import os
import requests
import re
import time

TARGET_DIR = "config/easy_npc/npc_templates"

def get_skin_id(name):
    print(f"Searching for {name}...")
    headers = {
        'User-Agent': 'Mozilla/5.0 (iPhone; CPU iPhone OS 14_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/14.0.3 Mobile/15E148 Safari/604.1',
        'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
        'Referer': 'https://namemc.com/'
    }
    
    # Try full name
    url = f"https://namemc.com/search?q={name.replace(' ', '+')}"
    try:
        r = requests.get(url, headers=headers, timeout=10)
        if r.status_code == 200:
            found = re.findall(r'id=([a-f0-9]{16})', r.text)
            if found: return found[0]
            
            # Try short name
            if " " in name:
                short = name.split(" ")[0]
                print(f"  Trying short name: {short}")
                time.sleep(1)
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
                
        skin_url = data.get("skin", {}).get("skinUrl", "")
        if "mc-heads.net" in skin_url:
            raw_name = data.get("name", "").replace("§a", "").replace("§b", "").replace("§c", "").replace("§d", "").replace("§e", "").replace("§6", "").replace("§9", "").replace("§5", "")
            print(f"Processing {raw_name} in {filename}...")
            
            # Strip extra chars if necessary
            clean_name = raw_name.strip()
            
            skin_id = get_skin_id(clean_name)
            if skin_id:
                new_url = f"https://s.namemc.com/i/{skin_id}.png"
                data["skin"]["skinUrl"] = new_url
                
                with open(filepath, 'w') as f:
                    json.dump(data, f, indent=4, ensure_ascii=False)
                print(f"  Updated to {new_url}")
            else:
                print("  Could not find replacement skin.")
            
            time.sleep(2)

if __name__ == "__main__":
    main()
