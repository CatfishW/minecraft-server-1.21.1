#!/usr/bin/env python3
"""
NPC Generator using NVIDIA API with DeepSeek V3.2
Generates immersive NPCs with deep storylines, branching dialogues, and quests
for Minecraft Fabric 1.21.1 Easy NPC mod.
"""

import json
import os
import re
import time
import random
import uuid
from openai import OpenAI

# ============== CONFIGURATION ==============
OUTPUT_DIR = "/data/Yanlai/minecraft_server_fabric_1.21.1_fresh/config/easy_npc/npc_templates"
QUEST_OUTPUT_DIR = "/data/Yanlai/minecraft_server_fabric_1.21.1_fresh/config/easy_npc/quests"
SKIN_MAPPING_FILE = "/data/Yanlai/minecraft_server_fabric_1.21.1_fresh/skin_mapping.json"

# NVIDIA API Configuration
NVIDIA_API_KEY = "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"
NVIDIA_BASE_URL = "https://integrate.api.nvidia.com/v1"
MODEL_NAME = "deepseek-ai/deepseek-v3.2"

# Generation settings
QUESTS_PER_NPC = 5
BATCH_SIZE = 2  # Process 2 NPCs at a time for quality
START_INDEX = 400  # Starting index for new NPCs

# Load skin mapping
SKIN_MAPPING = {}
if os.path.exists(SKIN_MAPPING_FILE):
    with open(SKIN_MAPPING_FILE, "r") as f:
        SKIN_MAPPING = json.load(f)

# ============== CHARACTER DEFINITIONS ==============
# Characters organized by theme/universe with detailed backstories
CHARACTERS = [
    # ===== Breaking Bad Universe =====
    {
        "name": "Walter White",
        "theme": "Breaking Bad",
        "role": "Chemistry Teacher / Kingpin",
        "backstory": "A brilliant chemist who co-founded Gray Matter Technologies but left before it became a billion-dollar company. Now a high school chemistry teacher diagnosed with terminal lung cancer, he turns to manufacturing methamphetamine with a former student to secure his family's financial future.",
        "personality": "Calculating, prideful, brilliant, increasingly ruthless. Has a fragile ego that drives his transformation into Heisenberg.",
        "connections": ["Jesse Pinkman", "Skyler White", "Gustavo Fring", "Hank Schrader"],
        "quest_themes": ["chemical synthesis", "territory control", "money laundering", "rival elimination"]
    },
    {
        "name": "Jesse Pinkman",
        "theme": "Breaking Bad",
        "role": "Cook / Dealer",
        "backstory": "A former student of Walter White who was already involved in the drug trade. Despite his street-smart exterior, Jesse has a strong moral compass and genuinely cares about children and the innocent.",
        "personality": "Emotional, loyal, haunted by guilt, uses slang excessively. Underneath the bravado is a sensitive soul seeking redemption.",
        "connections": ["Walter White", "Mike Ehrmantraut", "Badger", "Skinny Pete"],
        "quest_themes": ["cooking product", "street dealing", "protecting innocents", "escaping the life"]
    },
    {
        "name": "Gustavo Fring",
        "theme": "Breaking Bad",
        "role": "Drug Lord / Businessman",
        "backstory": "A Chilean immigrant who operates Los Pollos Hermanos as a front for his methamphetamine empire. His partner was murdered by the cartel, fueling a decades-long quest for revenge against the Salamanca family.",
        "personality": "Meticulous, patient, ruthless beneath a polite exterior. Never loses composure. Every action is calculated for maximum effect.",
        "connections": ["Walter White", "Mike Ehrmantraut", "Hector Salamanca", "Lydia Rodarte-Quayle"],
        "quest_themes": ["distribution network", "cartel politics", "quality control", "eliminating threats"]
    },
    {
        "name": "Mike Ehrmantraut",
        "theme": "Breaking Bad",
        "role": "Fixer / Enforcer",
        "backstory": "A former Philadelphia police officer who became corrupt to protect his son, also a cop. After his son was murdered for not taking bribes, Mike moved to Albuquerque to be near his granddaughter Kaylee.",
        "personality": "Stoic, professional, world-weary. Has a strict code of ethics despite his criminal work. Deeply loves his granddaughter.",
        "connections": ["Gustavo Fring", "Jesse Pinkman", "Saul Goodman", "Lydia Rodarte-Quayle"],
        "quest_themes": ["surveillance", "cleanup operations", "security", "protecting family"]
    },
    {
        "name": "Saul Goodman",
        "theme": "Breaking Bad",
        "role": "Criminal Lawyer",
        "backstory": "Born James McGill, he reinvented himself as the flamboyant criminal defense attorney Saul Goodman. Operates from a strip mall office and connects criminals with the services they need.",
        "personality": "Charismatic, sleazy, clever, uses humor to deflect. Underneath the showmanship is a skilled legal mind and a lonely man.",
        "connections": ["Walter White", "Jesse Pinkman", "Mike Ehrmantraut", "Huell Babineaux"],
        "quest_themes": ["money laundering", "identity creation", "legal loopholes", "client protection"]
    },
    
    # ===== Better Call Saul Universe =====
    {
        "name": "Kim Wexler",
        "theme": "Better Call Saul",
        "role": "Lawyer",
        "backstory": "A brilliant attorney who worked her way up from humble beginnings. She's torn between her love for Jimmy McGill and her own moral compass, finding herself drawn to the thrill of his schemes.",
        "personality": "Intelligent, determined, principled but with a hidden wild side. Willing to bend rules for what she believes is right.",
        "connections": ["Jimmy McGill", "Howard Hamlin", "Chuck McGill"],
        "quest_themes": ["pro bono work", "corporate law", "con schemes", "moral dilemmas"]
    },
    {
        "name": "Nacho Varga",
        "theme": "Better Call Saul",
        "role": "Cartel Lieutenant",
        "backstory": "A smart and capable member of the Salamanca organization who desperately wants out. His aging father runs an upholstery shop and knows nothing of his criminal life.",
        "personality": "Strategic, conflicted, protective of his father. Willing to take huge risks to escape the cartel life.",
        "connections": ["Lalo Salamanca", "Gustavo Fring", "Mike Ehrmantraut", "Tuco Salamanca"],
        "quest_themes": ["double agent work", "protecting father", "sabotage", "escape planning"]
    },
    {
        "name": "Lalo Salamanca",
        "theme": "Better Call Saul",
        "role": "Cartel Heir",
        "backstory": "The charming and terrifying nephew of Hector Salamanca, sent to investigate Gus Fring's operations. Unlike his brutish cousins, Lalo is intelligent, personable, and utterly remorseless.",
        "personality": "Charismatic, curious, unpredictable, enjoys violence. Can switch from friendly to deadly in an instant.",
        "connections": ["Hector Salamanca", "Nacho Varga", "Gustavo Fring", "Kim Wexler"],
        "quest_themes": ["investigation", "cartel enforcement", "territory expansion", "eliminating traitors"]
    },
    
    # ===== Life is Strange Universe =====
    {
        "name": "Max Caulfield",
        "theme": "Life is Strange",
        "role": "Photography Student",
        "backstory": "A shy photography student at Blackwell Academy who discovers she can rewind time. Returns to her hometown of Arcadia Bay and reconnects with childhood friend Chloe Price.",
        "personality": "Introspective, artistic, indecisive, compassionate. Struggles with the weight of her choices and their consequences.",
        "connections": ["Chloe Price", "Rachel Amber", "Warren Graham", "Kate Marsh"],
        "quest_themes": ["photography", "time manipulation", "investigation", "friendship"]
    },
    {
        "name": "Chloe Price",
        "theme": "Life is Strange",
        "role": "Rebel",
        "backstory": "Once a straight-A student, Chloe spiraled into rebellion after her father's death and best friend Max leaving town. She's been obsessively searching for her missing friend Rachel Amber.",
        "personality": "Punk, angry, loyal, secretly vulnerable. Uses sarcasm and bravado to hide deep emotional wounds.",
        "connections": ["Max Caulfield", "Rachel Amber", "David Madsen", "Frank Bowers"],
        "quest_themes": ["finding Rachel", "rebelling", "uncovering secrets", "confronting the past"]
    },
    {
        "name": "Rachel Amber",
        "theme": "Life is Strange",
        "role": "Missing Student",
        "backstory": "The most popular girl at Blackwell Academy who formed an intense bond with Chloe Price before mysteriously disappearing. Everyone in Arcadia Bay seems to have known her differently.",
        "personality": "Chameleon-like, ambitious, charming, mysterious. Different people knew different versions of her.",
        "connections": ["Chloe Price", "Frank Bowers", "Mark Jefferson", "Nathan Prescott"],
        "quest_themes": ["uncovering her fate", "her secret life", "the Dark Room", "multiple personas"]
    },
    {
        "name": "Nathan Prescott",
        "theme": "Life is Strange",
        "role": "Troubled Heir",
        "backstory": "Heir to the wealthy Prescott family that essentially owns Arcadia Bay. Mentally unstable and heavily medicated, he's caught up in something darker than anyone realizes.",
        "personality": "Volatile, insecure, dangerous, seeking approval. A victim who became a victimizer.",
        "connections": ["Mark Jefferson", "Victoria Chase", "Rachel Amber", "Sean Prescott"],
        "quest_themes": ["family pressure", "the Dark Room", "covering tracks", "mental instability"]
    },
    
    # ===== Original Minecraft-Themed Characters =====
    {
        "name": "Elder Stoneheart",
        "theme": "Minecraft Original",
        "role": "Ancient Miner",
        "backstory": "An ancient dwarf who has mined these lands for centuries. He remembers when the first diamonds were discovered and has mapped every cave system in the region.",
        "personality": "Gruff, knowledgeable, protective of mining traditions. Speaks in riddles about ore locations.",
        "connections": ["Forge Master Ember", "Crystal Sage", "The Deep Dweller"],
        "quest_themes": ["rare ore discovery", "cave exploration", "ancient mining techniques", "defeating cave dwellers"]
    },
    {
        "name": "Forge Master Ember",
        "theme": "Minecraft Original",
        "role": "Master Blacksmith",
        "backstory": "A legendary smith who claims to have forged weapons for heroes of old. Her forge burns with netherite-infused flames that never die.",
        "personality": "Perfectionist, proud of craft, harsh critic. Only respects those who prove their worth.",
        "connections": ["Elder Stoneheart", "Captain Ironclaw", "The Wandering Enchanter"],
        "quest_themes": ["rare material gathering", "weapon forging", "armor crafting", "netherite secrets"]
    },
    {
        "name": "The Wandering Enchanter",
        "theme": "Minecraft Original",
        "role": "Mysterious Mage",
        "backstory": "A robed figure who appears at random, offering powerful enchantments in exchange for exotic items. No one knows their true face or origin.",
        "personality": "Enigmatic, speaks in prophecies, obsessed with magical knowledge. Never stays in one place long.",
        "connections": ["Crystal Sage", "The Ender Watcher", "Librarian Quill"],
        "quest_themes": ["enchantment materials", "magical artifacts", "End exploration", "ancient knowledge"]
    },
    {
        "name": "Captain Ironclaw",
        "theme": "Minecraft Original",
        "role": "Village Guard Captain",
        "backstory": "A veteran warrior who has defended this village from countless raids. Bears scars from battles with pillagers, zombies, and even a Wither.",
        "personality": "Stern, honorable, protective. Respects strength but values courage more.",
        "connections": ["Forge Master Ember", "Mayor Goldsworth", "Scout Shadowstep"],
        "quest_themes": ["village defense", "raid preparation", "monster hunting", "training recruits"]
    },
    {
        "name": "The Ender Watcher",
        "theme": "Minecraft Original",
        "role": "End Dimension Expert",
        "backstory": "Someone who ventured into the End and returned... changed. Now sees visions of Endermen and speaks of cities floating in the void.",
        "personality": "Distant, prophetic, slightly unhinged. Sometimes stares at nothing for hours.",
        "connections": ["The Wandering Enchanter", "Crystal Sage", "Dragon Slayer Ash"],
        "quest_themes": ["End exploration", "Ender Pearl collection", "Elytra recovery", "End City raids"]
    },
    {
        "name": "Farmer Chen",
        "theme": "Minecraft Original",
        "role": "Master Agriculturist",
        "backstory": "A humble farmer who has perfected the art of growing crops in any biome. His knowledge of breeding and farming is unparalleled in all the land.",
        "personality": "Patient, wise about nature, generous with food but protective of seed secrets.",
        "connections": ["Cook Mama Rosa", "Animal Whisperer Fauna", "Merchant Goldsworth"],
        "quest_themes": ["rare crop cultivation", "animal breeding", "food processing", "biome farming"]
    },
    {
        "name": "Nether Scout Blaze",
        "theme": "Minecraft Original",
        "role": "Nether Explorer",
        "backstory": "One of the few who regularly ventures into the Nether and returns alive. Maps Nether fortresses and knows safe paths through the hellish dimension.",
        "personality": "Thrill-seeker, confident, slightly pyromaniac. Collects Nether artifacts.",
        "connections": ["The Ender Watcher", "Forge Master Ember", "Captain Ironclaw"],
        "quest_themes": ["Nether exploration", "blaze rod collection", "fortress raids", "ancient debris hunting"]
    },
    {
        "name": "Librarian Quill",
        "theme": "Minecraft Original",
        "role": "Knowledge Keeper",
        "backstory": "The keeper of the village's vast library, containing books on every craft and enchantment. Trades knowledge for rare items and experiences.",
        "personality": "Scholarly, curious, values books above all else. Pays well for new knowledge.",
        "connections": ["The Wandering Enchanter", "Mayor Goldsworth", "Adventurer's Guild Master"],
        "quest_themes": ["book collection", "knowledge discovery", "enchantment research", "history preservation"]
    },
]

# Collect all NPC names for TALK quest targets
ALL_NPC_NAMES = [char["name"] for char in CHARACTERS]

# ============== HELPER FUNCTIONS ==============

def get_skin_url(char_name: str) -> str:
    """Get skin URL from mapping or generate fallback."""
    skin_id = SKIN_MAPPING.get(char_name)
    if skin_id:
        return f"https://s.namemc.com/i/{skin_id}.png"
    
    # Try partial matching
    for name, sid in SKIN_MAPPING.items():
        if char_name in name or name in char_name:
            return f"https://s.namemc.com/i/{sid}.png"
    
    # Fallback to mc-heads
    clean_name = char_name.replace(" ", "").replace("'", "")
    return f"https://mc-heads.net/skin/{clean_name}"

def create_nvidia_client():
    """Create OpenAI client configured for NVIDIA API."""
    return OpenAI(
        base_url=NVIDIA_BASE_URL,
        api_key=NVIDIA_API_KEY
    )

def call_deepseek_api(client: OpenAI, system_prompt: str, user_prompt: str, thinking: bool = True) -> str:
    """Call DeepSeek API through NVIDIA with streaming and thinking support."""
    print("  [API] Calling DeepSeek V3.2 with thinking enabled...")
    
    full_response = ""
    reasoning_content = ""
    
    try:
        completion = client.chat.completions.create(
            model=MODEL_NAME,
            messages=[
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_prompt}
            ],
            temperature=1,
            top_p=0.95,
            max_tokens=30000,
            extra_body={"chat_template_kwargs": {"thinking": thinking}},
            stream=True
        )
        
        for chunk in completion:
            if not getattr(chunk, "choices", None):
                continue
            
            # Capture reasoning content (thinking)
            reasoning = getattr(chunk.choices[0].delta, "reasoning_content", None)
            if reasoning:
                reasoning_content += reasoning
                print(".", end="", flush=True)  # Progress indicator
            
            # Capture actual content
            if chunk.choices[0].delta.content is not None:
                full_response += chunk.choices[0].delta.content
        
        print()  # Newline after progress dots
        
        if reasoning_content:
            print(f"  [API] Thinking complete ({len(reasoning_content)} chars of reasoning)")
        
        return full_response
        
    except Exception as e:
        print(f"  [API ERROR] {e}")
        return ""

def extract_json(content: str, is_array: bool = True) -> any:
    """Extract JSON from API response."""
    # Clean up markdown code blocks
    content = re.sub(r'```json\s*', '', content)
    content = re.sub(r'```\s*', '', content)
    
    # Try to find JSON structure
    if is_array:
        match = re.search(r'\[.*\]', content, re.DOTALL)
    else:
        match = re.search(r'\{.*\}', content, re.DOTALL)
    
    if match:
        try:
            return json.loads(match.group(0))
        except json.JSONDecodeError as e:
            print(f"  [JSON ERROR] {e}")
            # Try to fix common issues
            fixed = match.group(0)
            fixed = re.sub(r',\s*}', '}', fixed)  # Remove trailing commas
            fixed = re.sub(r',\s*]', ']', fixed)
            try:
                return json.loads(fixed)
            except:
                pass
    return None

# ============== GENERATION FUNCTIONS ==============

def generate_npc_dialogue(client: OpenAI, character: dict) -> dict:
    """Generate deep branching dialogue for a character using DeepSeek."""
    
    system_prompt = """你是一位顶级RPG叙事设计师，专门为Minecraft模组创造沉浸式NPC对话。

你的任务是生成具有以下特点的对话系统：
1. **深度分支** - 至少4层对话深度，每层有2-4个选择
2. **角色一致性** - 对话必须反映角色的背景、性格和说话方式
3. **隐藏故事线** - 包含需要多次对话才能解锁的秘密
4. **情感投入** - 对话应该让玩家在意这个角色
5. **任务钩子** - 自然地引入任务而不是强行推销

输出必须是JSON格式的对话系统：
{
    "dialogs": {
        "main": {
            "greeting": "初次见面的问候语",
            "buttons": [
                {"label": "选项文字", "action": "SHOW_DIALOG:dialog_id", "id": "btn_unique_id"},
                {"label": "任务相关", "action": "SHOW_DIALOG:quest_intro", "id": "btn_quest"}
            ]
        },
        "dialog_id": {
            "greeting": "对话内容...\n可以有多行...",
            "buttons": [...]
        },
        "quest_intro": {
            "greeting": "任务介绍...",
            "buttons": [
                {"label": "接受任务", "action": "OPEN_QUEST_DIALOG:RANDOM_POOL:QUEST_PLACEHOLDER", "id": "btn_accept"},
                {"label": "稍后再说", "action": "SHOW_DIALOG:main", "id": "btn_later"}
            ]
        }
    }
}

对话动作类型：
- SHOW_DIALOG:dialog_name - 跳转到另一个对话
- CLOSE_DIALOG - 关闭对话
- OPEN_QUEST_DIALOG:RANDOM_POOL:quest_ids - 打开任务（稍后填充）

要求：
- 所有文字使用简体中文
- ID必须是ASCII字符（如btn_1, story_deep_secret）
- 对话内容可以使用§颜色代码（§c红§a绿§b青§e黄§6金§d粉§5紫§9蓝）
- 创造引人入胜的故事而不是泛泛的对话"""

    user_prompt = f"""为以下角色创造一个深度对话系统：

角色名: {character['name']}
主题/世界观: {character['theme']}
角色定位: {character['role']}
背景故事: {character['backstory']}
性格特点: {character['personality']}
关联角色: {', '.join(character['connections'])}
任务主题: {', '.join(character['quest_themes'])}

请创造一个有至少8个不同对话节点的深度对话系统，包含：
1. 主对话 (main) - 带有多个探索方向
2. 背景故事线 (backstory_*) - 2-3个深入了解角色的对话
3. 当前处境 (current_*) - 角色现在面临的问题
4. 任务相关 (quest_*) - 自然引入任务的对话
5. 秘密/隐藏 (secret_*) - 需要建立信任才能解锁的内容
6. 告别 (farewell) - 离开时的对话

只输出JSON，不要其他内容。"""

    print(f"  Generating dialogue for {character['name']}...")
    response = call_deepseek_api(client, system_prompt, user_prompt)
    
    if not response:
        return None
    
    result = extract_json(response, is_array=False)
    return result.get('dialogs') if result else None

def generate_npc_quests(client: OpenAI, character: dict, all_npcs: list) -> list:
    """Generate immersive quests for a character."""
    
    # Get other NPC names for TALK objectives
    other_npcs = [n for n in all_npcs if n != character['name']]
    talk_targets = random.sample(other_npcs, min(5, len(other_npcs)))
    
    system_prompt = """你是一位Minecraft RPG任务设计师，创造引人入胜的任务。

任务类型：
1. KILL - 击杀生物 (target为minecraft:实体ID, 如minecraft:zombie)
2. GATHER - 收集物品 (target为minecraft:物品ID, 如minecraft:diamond)
3. TALK - 与NPC对话 (target为NPC名字)

输出格式 - JSON数组：
[
    {
        "id": "会被替换成UUID",
        "title": "史诗感的4-6字标题",
        "description": "第一段：剧情背景，为什么需要做这个任务\\n\\n第二段：具体指导，怎么完成",
        "objective": {
            "type": "KILL|GATHER|TALK",
            "target": "目标ID或NPC名",
            "amount": 数量
        },
        "reward": {
            "xp": 经验值,
            "itemId": "numismatic-overhaul:bronze_coin|silver_coin|gold_coin",
            "amount": 数量
        }
    }
]

难度奖励参考：
- 简单(杀5只): 10-30 bronze_coin, 50 xp
- 中等(杀20只/收集稀有): 50-100 bronze_coin, 150 xp  
- 困难(杀Boss级/收集钻石): 1-5 silver_coin, 300 xp
- 史诗(TALK链/稀有任务): 1 gold_coin, 500 xp"""

    user_prompt = f"""为角色 "{character['name']}" 设计5个沉浸式任务。

角色背景: {character['backstory']}
角色性格: {character['personality']}
任务主题应该围绕: {', '.join(character['quest_themes'])}
可用的TALK目标NPC: {', '.join(talk_targets)}

任务要求：
1. 任务必须符合角色的世界观和性格
2. 标题要有史诗感和角色特色
3. 描述要讲述一个小故事，不只是"杀X只Y"
4. 5个任务中至少包含：2个KILL、2个GATHER、1个TALK
5. 难度应该有梯度，从简单到困难

常用Minecraft实体ID:
- 敌对: zombie, skeleton, spider, creeper, enderman, witch, pillager, vindicator, phantom, blaze, ghast, wither_skeleton
- 中立: iron_golem, wolf, bee, piglin
- 动物: cow, pig, sheep, chicken, rabbit

常用Minecraft物品ID:
- 矿物: coal, iron_ingot, gold_ingot, diamond, emerald, redstone, lapis_lazuli, netherite_scrap
- 食物: wheat, carrot, potato, beetroot, apple, bread, cooked_beef, golden_apple
- 材料: leather, bone, string, gunpowder, ender_pearl, blaze_rod, ghast_tear, nether_star
- 农产品: sugar_cane, cocoa_beans, pumpkin, melon, bamboo

只输出JSON数组，不要其他内容。"""

    print(f"  Generating quests for {character['name']}...")
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
            
            # Ensure reward exists
            if 'reward' not in q:
                q['reward'] = {"xp": 100, "itemId": "numismatic-overhaul:bronze_coin", "amount": 20}
            
            # Ensure objective exists
            if 'objective' not in q:
                continue
            
            obj = q['objective']
            
            # Fix type issues
            if obj.get('type') not in ['KILL', 'GATHER', 'TALK']:
                obj['type'] = 'GATHER'
            
            # Fix target format
            if obj['type'] in ['KILL', 'GATHER']:
                target = obj.get('target', '')
                if not target.startswith('minecraft:'):
                    obj['target'] = 'minecraft:' + target.lower().replace(' ', '_')
            
            # Validate TALK targets
            if obj['type'] == 'TALK':
                if obj.get('target') not in all_npcs:
                    obj['target'] = random.choice(talk_targets)
            
            # Ensure amount
            if 'amount' not in obj or not isinstance(obj.get('amount'), int):
                obj['amount'] = 1
            
            valid_quests.append(q)
            
        except Exception as e:
            print(f"    [QUEST ERROR] {e}")
            continue
    
    return valid_quests

def create_npc_template(character: dict, dialogs: dict, quest_ids: list, index: int) -> dict:
    """Create complete NPC template."""
    
    # Color the name
    colors = ["§c", "§a", "§b", "§e", "§d", "§6", "§9", "§5"]
    colored_name = random.choice(colors) + character['name']
    
    # Update quest placeholder in dialogs
    if dialogs and quest_ids:
        quest_pool = ",".join(quest_ids)
        dialogs_str = json.dumps(dialogs)
        dialogs_str = dialogs_str.replace("QUEST_PLACEHOLDER", quest_pool)
        dialogs = json.loads(dialogs_str)
    
    template = {
        "name": colored_name,
        "entityType": "easy_npc:humanoid",
        "description": character['backstory'][:200] + "..." if len(character['backstory']) > 200 else character['backstory'],
        "skin": {
            "type": "URL_SKIN",
            "skinUrl": get_skin_url(character['name'])
        },
        "attributes": {
            "maxHealth": 40,
            "invulnerable": False,
            "attackDamage": 4
        },
        "dialogs": dialogs or {},
        "pose": "STANDING",
        "metadata": {
            "theme": character['theme'],
            "role": character['role'],
            "connections": character['connections'],
            "generated_by": "deepseek-v3.2",
            "generated_at": time.strftime("%Y-%m-%d %H:%M:%S")
        }
    }
    
    return template

# ============== MAIN EXECUTION ==============

def main():
    print("=" * 60)
    print("NPC Generator - DeepSeek V3.2 via NVIDIA API")
    print("=" * 60)
    
    # Create output directories
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    os.makedirs(QUEST_OUTPUT_DIR, exist_ok=True)
    
    # Initialize client
    client = create_nvidia_client()
    
    all_npc_names = [c['name'] for c in CHARACTERS]
    
    # Process characters
    for i, character in enumerate(CHARACTERS):
        index = START_INDEX + i
        print(f"\n[{i+1}/{len(CHARACTERS)}] Processing: {character['name']}")
        print("-" * 40)
        
        # Generate dialogue
        dialogs = generate_npc_dialogue(client, character)
        if not dialogs:
            print(f"  WARNING: Failed to generate dialogue, using fallback")
            dialogs = {
                "main": {
                    "greeting": f"你好，我是{character['name']}。",
                    "buttons": [
                        {"label": "告辞", "action": "CLOSE_DIALOG", "id": "btn_bye"}
                    ]
                }
            }
        else:
            print(f"  ✓ Generated {len(dialogs)} dialogue nodes")
        
        # Generate quests
        quests = generate_npc_quests(client, character, all_npc_names)
        quest_ids = []
        
        if quests:
            # Save quests
            for q in quests:
                quest_file = os.path.join(QUEST_OUTPUT_DIR, f"quest_{q['id']}.json")
                with open(quest_file, 'w', encoding='utf-8') as f:
                    json.dump(q, f, indent=4, ensure_ascii=False)
                quest_ids.append(q['id'])
            print(f"  ✓ Generated {len(quests)} quests")
        else:
            print(f"  WARNING: Failed to generate quests")
        
        # Create and save NPC template
        template = create_npc_template(character, dialogs, quest_ids, index)
        
        safe_name = character['name'].lower().replace(' ', '_').replace("'", "").replace(".", "")
        filename = f"npc_{index:03d}_{safe_name}.json"
        filepath = os.path.join(OUTPUT_DIR, filename)
        
        with open(filepath, 'w', encoding='utf-8') as f:
            json.dump(template, f, indent=4, ensure_ascii=False)
        
        print(f"  ✓ Saved: {filename}")
        
        # Rate limiting - pause between characters
        if i < len(CHARACTERS) - 1:
            print("  Waiting 3 seconds before next character...")
            time.sleep(3)
    
    print("\n" + "=" * 60)
    print(f"Generation Complete!")
    print(f"  NPCs: {OUTPUT_DIR}")
    print(f"  Quests: {QUEST_OUTPUT_DIR}")
    print("=" * 60)

if __name__ == "__main__":
    main()
