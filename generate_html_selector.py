
import re
import os

md_path = '/data/Yanlai/minecraft_server_fabric_1.21.1_fresh/npc_skins.md'
html_path = '/data/Yanlai/minecraft_server_fabric_1.21.1_fresh/skin_selector.html'

html_header = """
<!DOCTYPE html>
<html>
<head>
<style>
  body { font-family: sans-serif; background: #222; color: #eee; padding: 20px; }
  .npc-section { background: #333; margin-bottom: 20px; padding: 10px; border-radius: 8px; }
  .skin-option { display: inline-block; margin: 10px; text-align: center; vertical-align: top; width: 150px; cursor: pointer; }
  .skin-option:hover { background: #444; border-radius: 4px; }
  .skin-option img { max-width: 100px; height: auto; display: block; margin: 0 auto; }
  .skin-option input { margin-top: 5px; transform: scale(1.5); }
  h2 { margin-top: 0; color: #81d4fa; }
  #controls { position: fixed; bottom: 0; left: 0; right: 0; background: #111; padding: 15px; text-align: right; border-top: 1px solid #444; }
  button { padding: 10px 20px; font-size: 16px; background: #4caf50; border: none; color: white; cursor: pointer; border-radius: 4px; }
  button:hover { background: #45a049; }
  textarea { width: 100%; height: 200px; background: #111; color: #0f0; border: 1px solid #555; margin-top: 10px; }
</style>
</head>
<body>
<h1>NPC Skin Selector</h1>
<form id="skinForm">
"""

html_footer = """
</form>
<div style="height: 100px;"></div>
<div id="controls">
    <button onclick="generateJSON()">Generate Selection JSON</button>
</div>
<textarea id="output" placeholder="Selection JSON will appear here..."></textarea>

<script>
function generateJSON() {
    const from = document.getElementById('skinForm');
    const formData = new FormData(from);
    const selection = {};
    for (const [key, value] of formData) {
        selection[key] = value;
    }
    const json = JSON.stringify(selection, null, 2);
    document.getElementById('output').value = json;
    
    // Auto-copy to clipboard if possible
    navigator.clipboard.writeText(json).then(() => {
        alert("Selection JSON copied to clipboard!");
    }, () => {
        alert("JSON generated below. Please copy it.");
    });
}
</script>
</body>
</html>
"""

def parse_markdown(path):
    with open(path, 'r') as f:
        lines = f.readlines()
    
    content = ""
    current_npc = None
    npc_counter = 0

    for line in lines:
        line = line.strip()
        if not line:
            continue
            
        # Parse Headers
        if line.startswith('###'):
            if current_npc:
                content += "</div>\n"
            
            raw_title = line.replace('###', '').strip()
            # Extract basic name for key, avoiding duplicates or special chars
            # content is like: "Town Guard (town_guard.json, ...)"
            # we try to grab the first part or filename
            
            # Simple unique ID generation
            safe_id = re.sub(r'[^a-zA-Z0-9_]', '_', raw_title)
            current_npc = safe_id
            
            content += f'<div class="npc-section"><h2>{raw_title}</h2>\n'
            
        # Parse Skin Images
        elif line.startswith('-'):
            # match url inside ![]() or just url
            # The line format from previous step: - [ ] ![](url) [Link](url)
            match = re.search(r'\!\[\]\((https?://[^)]+)\)', line)
            if match and current_npc:
                url = match.group(1)
                content += f"""
                <label class="skin-option">
                    <img src="{url}" loading="lazy">
                    <input type="radio" name="{current_npc}" value="{url}">
                </label>
                """
    
    if current_npc:
        content += "</div>\n"
        
    return content

html_body = parse_markdown(md_path)

with open(html_path, 'w') as f:
    f.write(html_header + html_body + html_footer)
