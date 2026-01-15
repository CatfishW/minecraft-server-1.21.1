
import requests
from bs4 import BeautifulSoup
from urllib.parse import quote

def debug_fetch(name, theme):
    search_query = quote(f"{name} {theme}")
    # Try PlanetMinecraft
    url = f"https://www.planetminecraft.com/skins/?keywords={search_query}"
    print(f"Fetching {url}")
    
    headers = {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36',
        'Referer': 'https://www.google.com/'
    }
    
    try:
        response = requests.get(url, headers=headers, timeout=10)
        print(f"Status: {response.status_code}")
        if response.status_code == 200:
            soup = BeautifulSoup(response.text, 'html.parser')
            print(f"Title: {soup.title.string}")
            
            # PlanetMinecraft search results are usually in .resource-list or similar
            # Look for skin links
            
            # Just print a snippet to check structure
            print("Body snippet:")
            print(soup.body.get_text()[:500])
            
            # Find images?
            imgs = soup.find_all('img')
            print(f"Found {len(imgs)} images.")

    except Exception as e:
        print(f"Error: {e}")

debug_fetch("Eleven", "Stranger Things")
