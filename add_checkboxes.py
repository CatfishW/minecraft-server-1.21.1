
file_path = '/data/Yanlai/minecraft_server_fabric_1.21.1_fresh/npc_skins.md'

with open(file_path, 'r') as f:
    lines = f.readlines()

new_lines = []
for line in lines:
    # Check if line acts as a list item with an image
    if line.strip().startswith('- !['):
        # Insert [ ] after the dash
        new_line = line.replace('- ![', '- [ ] ![', 1)
        new_lines.append(new_line)
    else:
        new_lines.append(line)

with open(file_path, 'w') as f:
    f.writelines(new_lines)
