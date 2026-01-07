#!/usr/bin/env python3
"""Process generated textures: remove white background and resize to 16x16."""

from PIL import Image
import glob
import os

ARTIFACTS_DIR = "/home/benwulab/.gemini/antigravity/brain/f25f064e-440e-49f0-b345-2e7f8a9b8725"
TEXTURES_DIR = "/data/Yanlai/minecraft_server_fabric_1.21.1_fresh/mods-dev/novus-items/src/main/resources/assets/novus_items/textures/item"

def remove_white_bg_and_resize(input_pattern, output_path, size=(16, 16)):
    """Remove white background from image and resize."""
    files = glob.glob(input_pattern)
    if not files:
        print(f"No files found for pattern: {input_pattern}")
        return False
    
    input_path = files[0]
    print(f"Processing: {input_path} -> {output_path}")
    
    # Open image and convert to RGBA
    img = Image.open(input_path).convert("RGBA")
    
    # Get pixel data
    data = img.getdata()
    
    # Replace white/near-white pixels with transparent
    new_data = []
    for item in data:
        # Check if pixel is white or near-white (with some tolerance)
        if item[0] > 240 and item[1] > 240 and item[2] > 240:
            new_data.append((255, 255, 255, 0))  # Transparent
        else:
            new_data.append(item)
    
    img.putdata(new_data)
    
    # Resize to 16x16 with nearest neighbor for pixel art
    img_resized = img.resize(size, Image.NEAREST)
    
    # Save as PNG with transparency
    img_resized.save(output_path, "PNG")
    print(f"Saved: {output_path} ({size[0]}x{size[1]})")
    return True

# Process each texture
textures = [
    ("bronze_coin_solid_*.png", "bronze_novus_coin.png"),
    ("silver_coin_solid_*.png", "silver_novus_coin.png"),
    ("gold_coin_solid_*.png", "gold_novus_coin.png"),
    ("banknote_solid_*.png", "banknote_scroll.png"),
]

for pattern, output_name in textures:
    input_pattern = os.path.join(ARTIFACTS_DIR, pattern)
    output_path = os.path.join(TEXTURES_DIR, output_name)
    remove_white_bg_and_resize(input_pattern, output_path)

print("\nAll textures processed!")
