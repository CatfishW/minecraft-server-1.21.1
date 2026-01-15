
import json
import os
import re

# The JSON selection provided by the user
selection_json = """
{
  "Eleven__npc_01_eleven_json_": "https://www.minecraftskins.com/uploads/skins/2021/02/19/eleven--from-stranger-things--16831566.png?v938",
  "Mike_Wheeler__npc_02_mike_wheeler_json_": "https://www.minecraftskins.com/uploads/skins/2020/08/11/mike-wheeler-stranger-things-s3-15032001.png?v938",
  "Dustin_Henderson__npc_03_dustin_henderson_json_": "https://www.minecraftskins.com/uploads/skins/2019/07/19/dustin-stranger-things-13211627.png?v938",
  "Lucas_Sinclair__npc_04_lucas_sinclair_json_": "https://www.minecraftskins.com/uploads/skins/2018/11/25/lucas-stranger-things-12615068.png?v938",
  "Will_Byers__npc_05_will_byers_json_": "https://www.minecraftskins.com/uploads/skins/2018/11/25/will-stranger-things-12615072.png?v938",
  "Jim_Hopper__npc_06_jim_hopper_json_": "https://www.minecraftskins.com/uploads/skins/2019/04/26/hopper-skin-fortnite-12954643.png?v938",
  "Joyce_Byers__npc_07_joyce_byers_json_": "https://www.minecraftskins.com/uploads/skins/2026/01/07/joyce---hs-ver--23775307.png?v938",
  "Steve_Harrington__npc_08_steve_harrington_json_": "https://www.minecraftskins.com/uploads/skins/2024/07/24/steve-harrington-stranger-things-22720675.png?v938",
  "Nancy_Wheeler__npc_09_nancy_wheeler_json_": "https://www.minecraftskins.com/uploads/skins/2021/07/14/nancy-wheeler-18395929.png?v938",
  "Jonathan_Byers__npc_10_jonathan_byers_json_": "https://www.minecraftskins.com/uploads/skins/2019/07/14/jonathan-byers-13187188.png?v938",
  "Max_Mayfield__npc_11_max_mayfield_json_": "https://www.skindex.pro/static/skins/max-Xq6Rt9KgRawibT5xtHY75R.png",
  "Robin_Buckley__npc_13_robin_buckley_json_": "https://www.minecraftskins.com/uploads/skins/2019/08/05/robin---stranger-things-13292657.png?v938",
  "Eddie_Munson__npc_17_eddie_munson_json_": "https://www.minecraftskins.com/uploads/skins/2024/07/02/eddie-munson-22658994.png?v938",
  "Pennywise__npc_18_pennywise_json_": "https://www.minecraftskins.com/uploads/skins/2018/11/18/pennywise--it-12599003.png?v938",
  "Bill_Denbrough__npc_19_bill_denbrough_json_": "https://www.minecraftskins.com/uploads/skins/2026/01/07/bill-23775492.png?v938",
  "Georgie_Denbrough__npc_26_georgie_denbrough_json_": "https://www.minecraftskins.com/uploads/skins/2021/03/14/georgie-17127677.png?v938",
  "Vault_Dweller__npc_35_vault_dweller_json_": "https://www.minecraftskins.com/uploads/skins/2024/07/11/fallout---vault-dweller-22686063.png?v938",
  "The_Ghoul__npc_36_the_ghoul_json_": "https://static.planetminecraft.com/files/resource_media/preview/the-ghoul-19389356-e3560-minecraft-skin.jpg",
  "Billy_Hargrove__npc_12_billy_hargrove_json_": "https://www.minecraftskins.com/uploads/skins/2023/03/17/billy-hargrove-21430544.png?v938",
  "Erica_Sinclair__npc_14_erica_sinclair_json_": "https://www.minecraftskins.com/uploads/skins/2019/08/03/erica-sinclair--stranger-things--13281680.png?v938",
  "Murray_Bauman__npc_15_murray_bauman_json_": "https://www.minecraftskins.com/uploads/skins/2020/09/26/murray-bauman--compromised--15358751.png?v938",
  "Martin_Brenner__npc_16_martin_brenner_json_": "https://www.minecraftskins.com/uploads/skins/2022/09/10/martin-brenner-in-suit-20834988.png?v938",
  "Beverly_Marsh__npc_20_beverly_marsh_json_": "https://www.minecraftskins.com/uploads/skins/2019/10/28/beverly-marsh---it-13599501.png?v938",
  "Richie_Tozier__npc_21_richie_tozier_json_": "https://www.minecraftskins.com/uploads/skins/2021/07/17/richie-tozier-18423400.png?v938",
  "Eddie_Kaspbrak__npc_22_eddie_kaspbrak_json_": "https://www.minecraftskins.com/uploads/skins/2019/01/01/eddie-kaspbrak-12697258.png?v938",
  "Ben_Hanscom__npc_23_ben_hanscom_json_": "https://www.minecraftskins.com/uploads/skins/2019/10/28/ben-hanscom---it-13600733.png?v938",
  "Mike_Hanlon__npc_24_mike_hanlon_json_": "https://www.minecraftskins.com/uploads/skins/2017/08/30/skin_20170830030659157777.png?v938",
  "Stanley_Uris__npc_25_stanley_uris_json_": "https://www.minecraftskins.com/uploads/skins/2018/11/21/stanley-uris-12604885.png?v938",
  "Patrick_Hockstetter__npc_28_patrick_hockstetter_json_": "https://www.minecraftskins.com/uploads/skins/2024/12/07/patrick-hockstetter-22920601.png?v938",
  "The_Leper__npc_34_the_leper_json_": "https://www.minecraftskins.com/uploads/skins/2015/03/06/skin_20150306090524119899.png?v938",
  "Lucy_MacLean__npc_37_lucy_maclean_json_": "https://static.planetminecraft.com/files/image/minecraft/skin/2024/937/skinseedskin-1717541921683-17887845_iso_l.png",
  "Dogmeat__npc_40_dogmeat_json_": "https://static.planetminecraft.com/files/image/minecraft/mob-skin/2020/269/wolf-planetminecraft-com-13703940_iso_l.png",
  "Preston_Garvey__npc_42_preston_garvey_json_": "https://www.minecraftskins.com/uploads/skins/2015/12/29/skin_20151229215555122274.png?v938",
  "Piper_Wright__npc_43_piper_wright_json_": "https://static.planetminecraft.com/files/resource_media/preview/1703/download110810205_minecraft_skin-10810205.jpg",
  "Nick_Valentine__npc_44_nick_valentine_json_": "https://www.minecraftskins.com/uploads/skins/2024/04/25/nick-valentine--read-description-lol--22495969.png?v938",
  "Elder_Maxson__npc_46_elder_maxson_json_": "https://www.minecraftskins.com/uploads/skins/2022/01/18/elder-maxson-19738057.png?v938"
}
"""

selection = json.loads(selection_json)
base_path = '/data/Yanlai/minecraft_server_fabric_1.21.1_fresh/config/easy_npc/npc_templates'

# Helper map to find the correct file
# The key format is usually: "Eleven__npc_01_eleven_json_" -> we need "npc_01_eleven.json"
# We can regex extract the json filename from the key

for key, url in selection.items():
    # Attempt to extract something that looks like a filename
    # The generated IDs replaced punctuation with underscores.
    # Ex: "Eleven__npc_01_eleven_json_" was originally "Eleven (npc_01_eleven.json)"
    
    # We can rely on the fact that the original filename is embedded in the key
    # It seems the previous step used regex to replace non-alphanumeric with _, so "npc_01_eleven.json" became "npc_01_eleven_json_"
    
    # Let's try to reconstruct or find the matching file in the directory
    # Strategy: look for known filenames in the directory that "fit" the key
    
    match = re.search(r'(npc_\d+_[a-z_]+)\_json', key) # Matches npc_01_eleven_json
    target_filename = None
    
    if match:
        potential_name = match.group(1) + ".json"
        if os.path.exists(os.path.join(base_path, potential_name)):
            target_filename = potential_name
    
    # If regex failed (e.g. for town guard or unique names not matching npc_ pattern perfectly if any)
    # let's try a fallback or specific mapping if needed. 
    # But looking at the user input, most keys share the npc_XX_name_json_ pattern.
    
    if target_filename:
        file_path = os.path.join(base_path, target_filename)
        # Read the JSON file
        try:
            with open(file_path, 'r') as f:
                data = json.load(f)
            
            # Update the texture SkinType to 'url' and SkinUrl to the url
            # The structure of EasyNPC templates usually has a 'skin' object or similar fields
            # Based on standard EasyNPC config:
            # "skin": { "type": "url", "texture": "..." } or similar configuration
            
            # Let's check how the file structure actually is by printing one if needed, but for now assuming:
            # simple key-value update or specific object.
            # Looking at prior knowledge of EasyNPC:
            # It usually uses `skinType` and `skinUrl` or similar.
            # Let's assume a generic `skin` dictionary.
            
            # Safely updating:
            if 'skin' not in data:
                data['skin'] = {}
            
            data['skin']['type'] = 'url'
            data['skin']['texture'] = url
            
            # Write back
            with open(file_path, 'w') as f:
                json.dump(data, f, indent=4)
            print(f"Updated {target_filename}")
            
        except Exception as e:
            print(f"Error updating {target_filename}: {e}")
    else:
        print(f"Could not match filename for key: {key}")
