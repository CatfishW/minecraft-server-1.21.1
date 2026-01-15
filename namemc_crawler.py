
import requests
import re
import time
import json
import os

def get_skin_id(name):
    print(f"Searching for {name}...")
    # Mobile User-Agent often bypasses some basic Cloudflare checks if not too aggressive
    headers = {
        'User-Agent': 'Mozilla/5.0 (iPhone; CPU iPhone OS 14_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/14.0.3 Mobile/15E148 Safari/604.1',
        'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
        'Accept-Language': 'en-US,en;q=0.5',
        'Referer': 'https://namemc.com/'
    }
    
    url = f"https://namemc.com/search?q={name.replace(' ', '+')}"
    
    try:
        r = requests.get(url, headers=headers, timeout=10)
        if r.status_code == 200:
            # The skin ID is usually in a link like /skin/97b8819ea452a76f or data-id="..."
            # Let's search for the hex ID pattern
            matches = re.findall(r'id=([a-f0-9]{16})', r.text)
            if matches:
                # Filter out those that might not be skins (though usually they are on search page)
                # First one is usually the most relevant "top" skin
                return matches[0]
            else:
                # Try another pattern if the first one fails
                matches = re.findall(r'/skin/([a-f0-9]{16})', r.text)
                if matches:
                    return matches[0]
        else:
            print(f"  Failed with status {r.status_code}")
    except Exception as e:
        print(f"  Error: {e}")
    
    return None

def main():
    characters = [
        "Walter White", "Jesse Pinkman", "Skyler White", "Hank Schrader", "Saul Goodman", 
        "Mike Ehrmantraut", "Gustavo Fring", "Lalo Salamanca", "Nacho Varga", "Kim Wexler",
        "Max Caulfield", "Chloe Price", "Rachel Amber", "Nathan Prescott", "Victoria Chase",
        "Kate Marsh", "Mark Jefferson", "David Madsen", "Frank Bowers", "Warren Graham",
        "Tuco Salamanca", "Hector Salamanca", "Todd Alquist", "Lydia Rodarte-Quayle", "Skinny Pete",
        "Badger", "Jane Margolis", "Gale Boetticher", "Huell Babineaux", "Patrick Kuby",
        "Chuck McGill", "Howard Hamlin", "Clifford Main", "Rich Schweikart", "Francesca Liddy",
        "Bill Oakley", "Kristy Esposito", "Marco Pasternak", "Betsy Kettleman", "Craig Kettleman",
        "Irene Landry", "Mrs. Nguyen", "Tyrus Kitt", "Victor", "Stacey Ehrmantraut",
        "Jefferson", "Victoria", "Nathan", "Max", "Chloe", "Rachel", "Kate", "David", "Joyce", "Frank"
    ]
    
    mapping = {}
    if os.path.exists("skin_mapping.json"):
        with open("skin_mapping.json", "r") as f:
            mapping = json.load(f)
            
    for name in characters:
        if name in mapping:
            continue
            
        skin_id = get_skin_id(name)
        if not skin_id and " " in name:
             short_name = name.split(" ")[0]
             print(f"  Result not found. Trying short name: {short_name}")
             time.sleep(1)
             skin_id = get_skin_id(short_name)

        if skin_id:
            mapping[name] = skin_id
            print(f"  Found ID: {skin_id}")
        else:
            print(f"  No skin found.")
        
        # Be nice to NameMC
        time.sleep(2)
        
        with open("skin_mapping.json", "w") as f:
            json.dump(mapping, f, indent=4)

if __name__ == "__main__":
    main()
