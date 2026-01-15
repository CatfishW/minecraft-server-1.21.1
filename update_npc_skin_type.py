import os
import json

TARGET_DIR = "/data/Yanlai/minecraft_server_fabric_1.21.1_fresh/config/easy_npc/npc_templates"

def update_npc_skins():
    count = 0
    if not os.path.exists(TARGET_DIR):
        print(f"Directory not found: {TARGET_DIR}")
        return

    for filename in os.listdir(TARGET_DIR):
        if filename.endswith(".json"):
            filepath = os.path.join(TARGET_DIR, filename)
            try:
                with open(filepath, 'r', encoding='utf-8') as f:
                    data = json.load(f)
                
                modified = False
                if "skin" in data:
                    if data["skin"].get("type") != "URL_SKIN":
                        data["skin"]["type"] = "URL_SKIN"
                        modified = True
                        print(f"Updated skin type for: {filename}")
                else:
                    # If skin doesn't exist, we might want to add it, but strictly following instruction to changing type
                    # We will just print a warning
                    print(f"Warning: No 'skin' object found in {filename}")

                if modified:
                    with open(filepath, 'w', encoding='utf-8') as f:
                        json.dump(data, f, indent=4, ensure_ascii=False)
                    count += 1
            except Exception as e:
                print(f"Error processing {filename}: {e}")

    print(f"Finished. Updated {count} files.")

if __name__ == "__main__":
    update_npc_skins()
