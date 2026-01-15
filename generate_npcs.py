
import json
import os
import requests
import re
import time
import random
import uuid

# Configuration
OUTPUT_DIR = "/data/Yanlai/minecraft_server_fabric_1.21.1_fresh/config/easy_npc/npc_templates"
OPENROUTER_API_KEY = "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"
OPENROUTER_BASE_URL = "https://openrouter.ai/api/v1"
MODEL_NAME = "xiaomi/mimo-v2-flash:free"
SKIN_MAPPING_FILE = "skin_mapping.json"

# Load pre-crawled skin IDs
if os.path.exists(SKIN_MAPPING_FILE):
    with open(SKIN_MAPPING_FILE, "r") as f:
        SKIN_MAPPING = json.load(f)
else:
    SKIN_MAPPING = {}

CHARACTERS = [
    # Breaking Bad (20)
    {"name": "Walter White", "theme": "Breaking Bad", "desc": "A chemistry teacher turned meth kingpin."},
    {"name": "Jesse Pinkman", "theme": "Breaking Bad", "desc": "A small-time meth cook and dealer."},
    {"name": "Skyler White", "theme": "Breaking Bad", "desc": "Walter's wife, struggling with his secret life."},
    {"name": "Hank Schrader", "theme": "Breaking Bad", "desc": "A DEA agent and Walter's brother-in-law."},
    {"name": "Saul Goodman", "theme": "Breaking Bad", "desc": "A flamboyant criminal defense lawyer."},
    {"name": "Mike Ehrmantraut", "theme": "Breaking Bad", "desc": "A former police officer turned fixer."},
    {"name": "Gustavo Fring", "theme": "Breaking Bad", "desc": "The calm and calculated owner of Los Pollos Hermanos."},
    {"name": "Hector Salamanca", "theme": "Breaking Bad", "desc": "A former cartel enforcer, mute and in a wheelchair."},
    {"name": "Tuco Salamanca", "theme": "Breaking Bad", "desc": "A high-ranking cartel enforcer, volatile and violent."},
    {"name": "Leonel Salamanca", "theme": "Breaking Bad", "desc": "One of the silent and deadly 'Cousins'."},
    {"name": "Marco Salamanca", "theme": "Breaking Bad", "desc": "The other of the silent and deadly 'Cousins'."},
    {"name": "Steven Gomez", "theme": "Breaking Bad", "desc": "Hank's partner at the DEA."},
    {"name": "Skinny Pete", "theme": "Breaking Bad", "desc": "One of Jesse's close friends and dealers."},
    {"name": "Badger", "theme": "Breaking Bad", "desc": "One of Jesse's loyal but dim-witted friends."},
    {"name": "Jane Margolis", "theme": "Breaking Bad", "desc": "Jesse's girlfriend and recovering addict."},
    {"name": "Gale Boetticher", "theme": "Breaking Bad", "desc": "A talented chemist hired by Gus Fring."},
    {"name": "Todd Alquist", "theme": "Breaking Bad", "desc": "An unpredictable associate of Walter and Jesse."},
    {"name": "Lydia Rodarte-Quayle", "theme": "Breaking Bad", "desc": "A high-level executive involved in the meth trade."},
    {"name": "Huell Babineaux", "theme": "Breaking Bad", "desc": "Saul's personal bodyguard and pickpocket."},
    {"name": "The Cousins", "theme": "Breaking Bad", "desc": "Silent, deadly cartel enforcers."},

    # Better Call Saul (16)
    {"name": "Jimmy McGill", "theme": "Better Call Saul", "desc": "The struggling lawyer who becomes Saul Goodman."},
    {"name": "Kim Wexler", "theme": "Better Call Saul", "desc": "A brilliant lawyer and Jimmy's close companion."},
    {"name": "Chuck McGill", "theme": "Better Call Saul", "desc": "Jimmy's brother, a brilliant but troubled lawyer."},
    {"name": "Howard Hamlin", "theme": "Better Call Saul", "desc": "A partner at the prestigious HHM law firm."},
    {"name": "Nacho Varga", "theme": "Better Call Saul", "desc": "A member of the Salamanca organization seeking a way out."},
    {"name": "Lalo Salamanca", "theme": "Better Call Saul", "desc": "A charismatic and dangerous member of the Salamanca family."},
    {"name": "Clifford Main", "theme": "Better Call Saul", "desc": "The head of the Davis & Main law firm."},
    {"name": "Francesca Liddy", "theme": "Better Call Saul", "desc": "The receptionist for Jimmy and Kim."},
    {"name": "Bill Oakley", "theme": "Better Call Saul", "desc": "An overworked assistant district attorney."},
    {"name": "Betsy Kettleman", "theme": "Better Call Saul", "desc": "A manipulative and entitled mother."},
    {"name": "Craig Kettleman", "theme": "Better Call Saul", "desc": "A former county treasurer involved in embezzlement."},
    {"name": "Irene Landry", "theme": "Better Call Saul", "desc": "A resident of Sandpiper Crossing."},
    {"name": "Mrs. Nguyen", "theme": "Better Call Saul", "desc": "The owner of the nail salon Jimmy uses as an office."},
    {"name": "Tyrus Kitt", "theme": "Better Call Saul", "desc": "One of Gus Fring's loyal henchmen."},
    {"name": "Victor", "theme": "Better Call Saul", "desc": "Another of Gus Fring's loyal henchmen."},
    {"name": "Stacey Ehrmantraut", "theme": "Better Call Saul", "desc": "Mike's daughter-in-law."},

    # Life is Strange (16)
    {"name": "Max Caulfield", "theme": "Life is Strange", "desc": "A high school student who can rewind time."},
    {"name": "Chloe Price", "theme": "Life is Strange", "desc": "Max's rebellious and loyal best friend."},
    {"name": "Rachel Amber", "theme": "Life is Strange", "desc": "The most popular girl in Arcadia Bay who went missing."},
    {"name": "Nathan Prescott", "theme": "Life is Strange", "desc": "A troubled student from a rich and powerful family."},
    {"name": "Victoria Chase", "theme": "Life is Strange", "desc": "The leader of the 'Vortex Club' and school bully."},
    {"name": "Kate Marsh", "theme": "Life is Strange", "desc": "A kind and religious student struggling with bullying."},
    {"name": "Mark Jefferson", "theme": "Life is Strange", "desc": "A famous photographer and teacher at Blackwell Academy."},
    {"name": "David Madsen", "theme": "Life is Strange", "desc": "Chloe's stepfather and head of security at school."},
    {"name": "Joyce Price", "theme": "Life is Strange", "desc": "Chloe's mother and owner of the local diner."},
    {"name": "Frank Bowers", "theme": "Life is Strange", "desc": "A local drug dealer with a soft spot for dogs."},
    {"name": "Warren Graham", "theme": "Life is Strange", "desc": "A science geek and friend to Max."},
    {"name": "Samuel Raymond", "theme": "Life is Strange", "desc": "The school janitor with a mystical perspective."},
    {"name": "Dana Ward", "theme": "Life is Strange", "desc": "A cheerleader and student at Blackwell Academy."},
    {"name": "Juliet Watson", "theme": "Life is Strange", "desc": "A school journalist student."},
    {"name": "Hayden Jones", "theme": "Life is Strange", "desc": "A student at Blackwell Academy and member of the Vortex Club."},
    {"name": "Alyssa Jenkins", "theme": "Life is Strange", "desc": "A student who always seems to get into accidents."},
]

def get_skin_url(char_name):
    # Use pre-crawled mapping if available
    skin_id = SKIN_MAPPING.get(char_name)
    if skin_id:
        return f"https://s.namemc.com/i/{skin_id}.png"
    
    # Try common variations
    for name, sid in SKIN_MAPPING.items():
        if char_name in name or name in char_name:
            return f"https://s.namemc.com/i/{sid}.png"
            
    # Fallback to mc-heads or minotar
    clean_name = char_name.replace(" ", "")
    return f"https://mc-heads.net/skin/{clean_name}"

def call_llm(batch):
    prompt_chars = []
    for char in batch:
        prompt_chars.append(f"- Name: {char['name']}, Theme: {char['theme']}, Desc: {char['desc']}")
    
    char_list_str = "\n".join(prompt_chars)

    system_prompt = """
You are a master RPG writer. Generate immersive, multi-level branching dialogs in Chinese (Simplified).
Each character should have a unique story line and a potential "quest" hook.

Format the output as a JSON array of objects.
CRITICAL: 
1. IDs must be ASCII only. 
2. Dialogs in Chinese. 
3. Minimum 3 levels of depth.
4. Include a "btn_quest" button in the 'main' or a sub-dialog that would theoretically lead to a quest.

Structure Example:
{
    "name": "Name",
    "dialogs": {
        "main": {
            "greeting": "...",
            "buttons": [
                {"label": "Talk about something", "action": "SHOW_DIALOG:story_1", "id": "btn_1"},
                {"label": "I want to help", "action": "SHOW_DIALOG:quest_intro", "id": "btn_q"}
            ]
        },
        "story_1": { "greeting": "...", "buttons": [...] },
        "quest_intro": { "greeting": "...", "buttons": [{"label": "Accept", "action": "CLOSE_DIALOG", "id": "btn_acc"}] }
    }
}
"""
    
    user_prompt = f"Generate dialogs for these characters:\n{char_list_str}\n\nStrictly JSON."

    try:
        headers = {
            "Authorization": f"Bearer {OPENROUTER_API_KEY}",
            "Content-Type": "application/json",
            "HTTP-Referer": "https://github.com/Start-Impulse/Easy-NPC",
            "X-Title": "Easy NPC Generator"
        }
        payload = {
            "model": MODEL_NAME,
            "messages": [{"role": "system", "content": system_prompt}, {"role": "user", "content": user_prompt}],
            "temperature": 0.8
        }
        r = requests.post(f"{OPENROUTER_BASE_URL}/chat/completions", headers=headers, json=payload, timeout=90)
        content = r.json()['choices'][0]['message']['content']
        match = re.search(r'\[.*\]', content, re.DOTALL)
        if match:
            return json.loads(match.group(0))
    except Exception as e:
        print(f"Error for batch: {e}")
    return None

def main():
    if not os.path.exists(OUTPUT_DIR):
        os.makedirs(OUTPUT_DIR)

    batch_size = 4
    start_index = 300
    for i in range(16, len(CHARACTERS), batch_size):
        batch = CHARACTERS[i:i+batch_size]
        print(f"Processing batch {i//batch_size + 1}...")
        results = call_llm(batch)
        
        if not results:
            continue

        for j, char_data in enumerate(results):
            name = char_data['name']
            # Find matching character in our list to get the filename correct
            orig_char = next((c for c in batch if c['name'] in name or name in c['name']), {"name": name})
            
            safe_name = orig_char['name'].lower().replace(' ', '_').replace('.', '')
            filename = f"npc_{start_index + i + j:03d}_{safe_name}.json"
            filepath = os.path.join(OUTPUT_DIR, filename)

            colors = ["§c", "§a", "§b", "§e", "§d", "§6", "§9", "§5"]
            colored_name = random.choice(colors) + orig_char['name']

            template = {
                "name": colored_name,
                "entityType": "easy_npc:humanoid",
                "description": orig_char.get('desc', "An NPC."),
                "skin": {
                    "type": "URL_SKIN",
                    "skinUrl": get_skin_url(orig_char['name'])
                },
                "attributes": {
                    "maxHealth": 40,
                    "invulnerable": True
                },
                "dialogs": char_data['dialogs'],
                "pose": "STANDING"
            }
            
            with open(filepath, 'w', encoding='utf-8') as f:
                json.dump(template, f, indent=4, ensure_ascii=False)
            print(f"  Created {filepath}")
        
        # Refresh skin mapping in case crawler added more
        if os.path.exists(SKIN_MAPPING_FILE):
            with open(SKIN_MAPPING_FILE, "r") as f:
                global SKIN_MAPPING
                SKIN_MAPPING = json.load(f)

if __name__ == "__main__":
    main()
