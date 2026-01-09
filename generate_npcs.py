
import json
import os
import requests
import concurrent.futures
import re
import time
import random
from bs4 import BeautifulSoup
from urllib.parse import quote
from openai import OpenAI

# Configuration
OUTPUT_DIR = "/data/Yanlai/minecraft_server_fabric_1.21.1_fresh/config/easy_npc/npc_templates"
OPENROUTER_API_KEY = "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"
OPENROUTER_BASE_URL = "https://openrouter.ai/api/v1"
MODEL_NAME = "xiaomi/mimo-v2-flash:free"

NOVUS_COINS = [
    "novus_items:bronze_novus_coin",
    "novus_items:silver_novus_coin",
    "novus_items:gold_novus_coin"
]

CHARACTERS = [
    # Stranger Things (17)
    {"name": "Eleven", "theme": "Stranger Things", "desc": "Possesses psychokinetic abilities. Mysterious and powerful."},
    {"name": "Mike Wheeler", "theme": "Stranger Things", "desc": "Leader of the party. Loyal and determined."},
    {"name": "Dustin Henderson", "theme": "Stranger Things", "desc": "The scientific genius. Curious and funny."},
    {"name": "Lucas Sinclair", "theme": "Stranger Things", "desc": "The ranger. Realistic and brave."},
    {"name": "Will Byers", "theme": "Stranger Things", "desc": "The survivor. Has a connection to the Upside Down."},
    {"name": "Jim Hopper", "theme": "Stranger Things", "desc": "Police Chief. Grumpy but protective."},
    {"name": "Joyce Byers", "theme": "Stranger Things", "desc": "A fierce mother. Never gives up."},
    {"name": "Steve Harrington", "theme": "Stranger Things", "desc": "The babysitter. Brave with a bat."},
    {"name": "Nancy Wheeler", "theme": "Stranger Things", "desc": "Investigative journalist. Sharp shooter."},
    {"name": "Jonathan Byers", "theme": "Stranger Things", "desc": "Quiet photographer. Courageous outsider."},
    {"name": "Max Mayfield", "theme": "Stranger Things", "desc": "The zoomer. Tough and skateboarder."},
    {"name": "Billy Hargrove", "theme": "Stranger Things", "desc": "Antagonist stepbrother. Wild and dangerous."},
    {"name": "Robin Buckley", "theme": "Stranger Things", "desc": "Smarts and sarcasm. Works at Scoops Ahoy."},
    {"name": "Erica Sinclair", "theme": "Stranger Things", "desc": "Sassy and smart. Can't spell America without Erica."},
    {"name": "Murray Bauman", "theme": "Stranger Things", "desc": "Conspiracy theorist. Speaks Russian."},
    {"name": "Martin Brenner", "theme": "Stranger Things", "desc": "Papa. The scientist behind the experiments."},
    {"name": "Eddie Munson", "theme": "Stranger Things", "desc": "Hellfire Club leader. Metalhead hero."},

    # IT: Welcome to Derry (17)
    {"name": "Pennywise", "theme": "IT", "desc": "The dancing clown. Horror incarnate."},
    {"name": "Bill Denbrough", "theme": "IT", "desc": "Stuttering Bill. Leader of the Losers."},
    {"name": "Beverly Marsh", "theme": "IT", "desc": "Brave and kind. The heart of the group."},
    {"name": "Richie Tozier", "theme": "IT", "desc": "Trashmouth. Jokes to hide fear."},
    {"name": "Eddie Kaspbrak", "theme": "IT", "desc": "Hypochondriac. Carries an inhaler."},
    {"name": "Ben Hanscom", "theme": "IT", "desc": "Historian. Architect. Sensitive soul."},
    {"name": "Mike Hanlon", "theme": "IT", "desc": "The keeper of Derry's history."},
    {"name": "Stanley Uris", "theme": "IT", "desc": "Logical and skeptical. Bird watcher."},
    {"name": "Georgie Denbrough", "theme": "IT", "desc": "The first victim. In a yellow raincoat."},
    {"name": "Henry Bowers", "theme": "IT", "desc": "Town bully. Violent and crazy."},
    {"name": "Patrick Hockstetter", "theme": "IT", "desc": "Psychopathic bully. Likes fire."},
    {"name": "Victor Criss", "theme": "IT", "desc": "Henry's reluctant follower."},
    {"name": "Belch Huggins", "theme": "IT", "desc": "The muscle. Drives the Trans Am."},
    {"name": "Mrs Kersh", "theme": "IT", "desc": "Creepy old lady. An illusion of IT."},
    {"name": "Adrian Mellon", "theme": "IT", "desc": "Victim of hate crime in Derry."},
    {"name": "Don Hagarty", "theme": "IT", "desc": "Adrian's partner."},
    {"name": "The Leper", "theme": "IT", "desc": "Disgusting manifestation of IT."},

    # Fallout (16)
    {"name": "Vault Dweller", "theme": "Fallout", "desc": "Survivor from a Vault. Seeking water chip."},
    {"name": "The Ghoul", "theme": "Fallout", "desc": "Cooper Howard. Bounty hunter mutant."},
    {"name": "Lucy MacLean", "theme": "Fallout", "desc": "Optimistic vault dweller. Okey dokey."},
    {"name": "Maximus", "theme": "Fallout", "desc": "BoS Squire. Wants power armor."},
    {"name": "Overseer Hank", "theme": "Fallout", "desc": "Overseer of Vault 33. Has secrets."},
    {"name": "Dogmeat", "theme": "Fallout", "desc": "Loyal dog companion. Finds items."},
    {"name": "Codsworth", "theme": "Fallout", "desc": "Mr Handy robot butler."},
    {"name": "Preston Garvey", "theme": "Fallout", "desc": "Minuteman. Another settlement needs help."},
    {"name": "Piper Wright", "theme": "Fallout", "desc": "Reporter for Publick Occurrences."},
    {"name": "Nick Valentine", "theme": "Fallout", "desc": "Synth detective. Noir style."},
    {"name": "Paladin Danse", "theme": "Fallout", "desc": "Brotherhood of Steel Paladin."},
    {"name": "Elder Maxson", "theme": "Fallout", "desc": "Leader of the brotherhood. Strict."},
    {"name": "Hancock", "theme": "Fallout", "desc": "Ghoul mayor of Goodneighbor."},
    {"name": "Cait", "theme": "Fallout", "desc": "Irish cage fighter. Tough."},
    {"name": "MacCready", "theme": "Fallout", "desc": "Mercenary sharpshooter."},
    {"name": "Strong", "theme": "Fallout", "desc": "Super Mutant. Looking for milk of kindness."},
]

# Randomly select 2 characters to be Reward Givers
REWARD_NPC_NAMES = set([c['name'] for c in random.sample(CHARACTERS, 2)])
print(f"Selected Reward NPCs: {REWARD_NPC_NAMES}")

client = OpenAI(
    base_url=OPENROUTER_BASE_URL,
    api_key=OPENROUTER_API_KEY,
)

def fetch_skin_url(character):
    name = character['name']
    clean_name = name.replace(" ", "")
    return f"https://minotar.net/skin/{clean_name}.png"

def call_llm(batch, is_reward):
    prompt_chars = []
    for char in batch:
        prompt_chars.append(f"- Name: {char['name']}, Theme: {char['theme']}, Desc: {char['desc']}")
    
    char_list_str = "\n".join(prompt_chars)

    # Base rules
    common_rules = """
**CRITICAL RULES:**
1. Each button object MUST include a unique "id" field using ASCII characters (e.g., "btn_start").
2. Do not use Chinese characters in "id" fields.
3. Dialogs should be interesting and thematic, in Chinese (Simplified).
    """

    # Reward Prompt
    reward_structure = """
{
    "name": "Character Name",
    "dialogs": {
        "main": {
            "greeting": "Greeting...",
            "buttons": [ {"label": "Option 1", "action": "SHOW_DIALOG:story_1", "id": "btn_1"} ]
        },
        "story_1": {
            "greeting": "Story part...",
            "buttons": [ {"label": "Next", "action": "SHOW_DIALOG:story_end", "id": "btn_2"} ]
        },
        "story_end": {
            "greeting": "Take this reward...",
            "buttons": [ {"label": "Thanks", "action": "COMMAND:give @p novus_items:silver_novus_coin 1", "id": "btn_reward"} ]
        }
    }
}
    """

    # Normal Prompt
    normal_structure = """
{
    "name": "Character Name",
    "dialogs": {
        "main": {
            "greeting": "Greeting...",
            "buttons": [ {"label": "Option 1", "action": "SHOW_DIALOG:story_1", "id": "btn_1"} ]
        },
        "story_1": {
            "greeting": "Story part...",
            "buttons": [ {"label": "Next", "action": "SHOW_DIALOG:story_end", "id": "btn_2"} ]
        },
        "story_end": {
            "greeting": "Conclusion...",
            "buttons": [ {"label": "Goodbye", "action": "CLOSE_DIALOG", "id": "btn_close"} ]
        }
    }
}
    """

    structure = reward_structure if is_reward else normal_structure
    system_prompt = f"""
You are a creative writer for a Minecraft mod.
Generate unique, DEEP, and IMMERSIVE branching dialog trees in **Chinese** (Simplified) for the following characters.
The output must be a valid JSON array of objects.

**CRITICAL RULES:**
1. Each button object MUST include a unique "id" field using ASCII characters (e.g., "btn_start").
2. Do not use Chinese characters in "id" fields.
3. Dialogs should be thematic and engaging, in Chinese (Simplified).
4. **LENGTH REQUIREMENT:** Each 'greeting' text MUST be at least 2-3 sentences long. Avoid short one-liners.
5. **DEPTH REQUIREMENT:** The conversation tree MUST be at least 3-4 levels deep (Main -> Story Part 1 -> Story Part 2 -> Conclusion).
6. **BRANCHING:** Provide at least 2 options for the player in the earlier stages.

{common_rules}

Each object must correspond to one character and follow this structure (Example):
{structure}
    """
    
    user_prompt = f"Generate detailed, multi-step dialogs for these characters:\n{char_list_str}\n\nStrictly output valid JSON. No markdown."

    try:
        completion = client.chat.completions.create(
            model=MODEL_NAME,
            messages=[
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_prompt}
            ],
        )
        content = completion.choices[0].message.content
        # Extract JSON array
        start_idx = content.find('[')
        end_idx = content.rfind(']')
        if start_idx != -1 and end_idx != -1:
            content = content[start_idx:end_idx+1]
        else:
            print("No JSON array found in response")
            return None
            
        return json.loads(content)
    except Exception as e:
        print(f"Error calling LLM: {e}")
        return None

def create_npc_file(char_data, index, skin_url):
    safe_name = char_data['name'].lower().replace(' ', '_').replace('.', '')
    filename = f"npc_{index:02d}_{safe_name}.json"
    filepath = os.path.join(OUTPUT_DIR, filename)

    # Random color for name
    colors = ["§c", "§a", "§b", "§e", "§d", "§6", "§9", "§5"]
    colored_name = random.choice(colors) + char_data['name']

    # Random Tool
    tools = [
        "minecraft:iron_sword", "minecraft:stone_sword", "minecraft:iron_axe", "minecraft:stone_axe",
        "minecraft:iron_pickaxe", "minecraft:stone_pickaxe", "minecraft:iron_shovel",
        "minecraft:clock", "minecraft:compass", "minecraft:spyglass", "minecraft:writable_book"
    ]
    tool_item = random.choice(tools)

    # Base Template
    template = {
        "name": colored_name, 
        "entityType": "easy_npc:humanoid",
        "description": char_data.get('desc', "An NPC."),
        "skin": {
            "type": "URL_SKIN",
            "skinUrl": skin_url
        },
        "attributes": {
            "maxHealth": 40,
            "movementSpeed": 0.3,
            "attackDamage": 5.0,
            "armor": 5.0,
            "invulnerable": True
        },
        "objectives": {
            "attackHostileMobs": True,
            "attackPlayers": False,
            "returnToSpawn": False,
            "wanderRange": 25.0
        },
        "dialogs": char_data['dialogs'],
        "pose": "STANDING",
        "equipment": {
            "mainHand": {
                "item": tool_item,
                "count": 1
            }
        }
    }
    
    with open(filepath, 'w') as f:
        json.dump(template, f, indent=4, ensure_ascii=False)
    print(f"Created {filepath}")

def process_batch(batch, start_index):
    # Split batch
    reward_batch = [c for c in batch if c['name'] in REWARD_NPC_NAMES]
    normal_batch = [c for c in batch if c['name'] not in REWARD_NPC_NAMES]

    results = []
    if reward_batch:
        res = call_llm(reward_batch, True)
        if res: results.extend(res)
    if normal_batch:
        res = call_llm(normal_batch, False)
        if res: results.extend(res)

    # Fallback map
    dialog_map = {item['name']: item['dialogs'] for item in results}

    for i, char in enumerate(batch):
        if char['name'] in dialog_map:
            char['dialogs'] = dialog_map[char['name']]
        else:
             char['dialogs'] = {
                "main": {
                    "greeting": f"你好，我是 {char['name']}。",
                    "buttons": [{"label": "再见", "action": "CLOSE_DIALOG", "id": "btn_fallback"}]
                }
            }
        
        skin_url = fetch_skin_url(char)
        create_npc_file(char, start_index + i, skin_url)

def main():
    if not os.path.exists(OUTPUT_DIR):
        os.makedirs(OUTPUT_DIR)

    batch_size = 5 
    for i in range(0, len(CHARACTERS), batch_size):
        batch = CHARACTERS[i:i+batch_size]
        print(f"Processing batch {i//batch_size + 1}...")
        process_batch(batch, i + 1)
        time.sleep(1)

if __name__ == "__main__":
    main()
