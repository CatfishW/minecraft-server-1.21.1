#!/usr/bin/env python3
"""Process coin textures: remove background and resize to 128x128.
Uses simple color-based removal for white/light backgrounds."""

from PIL import Image
import glob
import os

ARTIFACTS_DIR = "/home/benwulab/.gemini/antigravity/brain/f25f064e-440e-49f0-b345-2e7f8a9b8725"
TEXTURES_DIR = "/data/Yanlai/minecraft_server_fabric_1.21.1_fresh/mods-dev/novus-items/src/main/resources/assets/novus_items/textures/item"

def remove_white_background(input_path, output_path, size=(128, 128), threshold=240):
    """Remove white/near-white background and resize."""
    print(f"Processing: {input_path}")
    
    # Open image and convert to RGBA
    img = Image.open(input_path).convert("RGBA")
    
    # Get pixel data
    data = list(img.getdata())
    
    # Replace white/near-white pixels with transparent
    new_data = []
    for item in data:
        r, g, b, a = item
        # Check if pixel is white or near-white (with threshold)
        if r > threshold and g > threshold and b > threshold:
            new_data.append((0, 0, 0, 0))  # Transparent
        else:
            new_data.append(item)
    
    img.putdata(new_data)
    
    # Resize to target size with LANCZOS for quality
    img_resized = img.resize(size, Image.LANCZOS)
    
    # Save
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    img_resized.save(output_path, "PNG")
    print(f"Saved: {output_path} ({size[0]}x{size[1]})")
    return True

# Process coin textures - use hires versions
textures = [
    ("bronze_coin_hires_*.png", "bronze_novus_coin.png"),
    ("silver_coin_hires_*.png", "silver_novus_coin.png"),
    ("gold_coin_hires_*.png", "gold_novus_coin.png"),
    ("scroll_hires_*.png", "banknote_scroll.png"),
]

print("Processing coin textures with simple white background removal...")
print(f"Target size: 128x128")
print(f"Output directory: {TEXTURES_DIR}\n")

success_count = 0
for pattern, output_name in textures:
    input_pattern = os.path.join(ARTIFACTS_DIR, pattern)
    files = sorted(glob.glob(input_pattern))
    if files:
        output_path = os.path.join(TEXTURES_DIR, output_name)
        if remove_white_background(files[-1], output_path):
            success_count += 1
    else:
        print(f"No files found for pattern: {pattern}")

print(f"\nProcessed {success_count}/{len(textures)} textures successfully!")
