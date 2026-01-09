
import requests
from bs4 import BeautifulSoup
from urllib.parse import quote

def debug_fetch(name, theme):
    search_query = quote(f"{name} {theme}")
    url = f"https://www.minecraftskins.com/search/skin/{search_query}/1/"
    print(f"Fetching {url}")
    
    headers = {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36'
    }
    
    try:
        response = requests.get(url, headers=headers, timeout=10)
        print(f"Status: {response.status_code}")
        if response.status_code == 200:
            soup = BeautifulSoup(response.text, 'html.parser')
            
            # Print title
            print(f"Title: {soup.title.string}")
            
            # Find any images
            imgs = soup.find_all('img')
            print(f"Found {len(imgs)} images.")
            
            # Check for search results
            # Typical class for search result items?
            # Let's verify commonly known classes for minecraftskins.com
            # .search-card, .card, etc.
            
            cards = soup.select('.search-card')
            print(f"Found {len(cards)} .search-card items.")
            
            if not cards:
                # Try finding standard card classes
                cards = soup.select('.card')
                print(f"Found {len(cards)} .card items.")
                
            if cards:
                first_card = cards[0]
                print("First card HTML snippet:")
                print(str(first_card)[:500])
                
                # Check for link
                link = first_card.find('a')
                if link:
                    print(f"Link found: {link.get('href')}")
                else:
                    print("No link in card.")
            else:
                 # Print body text snippet to see if maybe blocked or different structure
                 print("Body snippet:")
                 print(soup.body.get_text()[:500])

    except Exception as e:
        print(f"Error: {e}")

debug_fetch("Eleven", "Stranger Things")
