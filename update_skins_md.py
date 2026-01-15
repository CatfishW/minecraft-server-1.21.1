
import re

file_path = '/data/Yanlai/minecraft_server_fabric_1.21.1_fresh/npc_skins.md'

with open(file_path, 'r') as f:
    lines = f.readlines()

new_lines = []
for line in lines:
    stripped = line.strip()
    if stripped.startswith('- http'):
        # match - url [optional text]
        match = re.match(r'- (https?://\S+)(.*)', stripped)
        if match:
            url = match.group(1)
            extra = match.group(2)
            # Creating the markdown image syntax
            new_line = f'- ![:3]( {url} ) [Link]({url}){extra}\n'
            # Using standard markdown image syntax: ![alt](url)
            # The user asked for thumbnail. Since we don't have a separate thumbnail URL, we use the main URL.
            # To make it "thumbnail-like" in some renderers we can't do much, but just embedding it matches "attach thumbnail".
            new_line = f'- ![]({url}) [Link]({url}){extra}\n'
            new_lines.append(new_line)
        else:
            new_lines.append(line)
    else:
        new_lines.append(line)

with open(file_path, 'w') as f:
    f.writelines(new_lines)
