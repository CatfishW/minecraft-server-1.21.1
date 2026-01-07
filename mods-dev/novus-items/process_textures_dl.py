#!/usr/bin/env python3
"""Process textures using rembg for deep learning background removal."""

from rembg import remove
from PIL import Image
import glob
import os

ARTIFACTS_DIR = "/home/benwulab/.gemini/antigravity/brain/f25f064e-440e-49f0-b345-2e7f8a9b8725"
TEXTURES_DIR = "/data/Yanlai/minecraft_server_fabric_1.21.1_fresh/mods-dev/novus-items/src/main/resources/assets/novus_items/textures/item"

def process_texture(input_pattern, output_path, size=(16, 16)):
    """Remove background using rembg and resize."""
    files = glob.glob(input_pattern)
    if not files:
        print(f"No files found for pattern: {input_pattern}")
        return False
    
    input_path = files[0]
    print(f"Processing: {input_path}")
    
    # Open and process with rembg
    with open(input_path, 'rb') as f:
        input_data = f.read()
    
    # Remove background using deep learning
    output_data = remove(input_data)
    
    # Load result and resize
    from io import BytesIO
    img = Image.open(BytesIO(output_data)).convert("RGBA")
    
    # Resize to 16x16 with LANCZOS for better quality
    img_resized = img.resize(size, Image.LANCZOS)
    
    # Save
    img_resized.save(output_path, "PNG")
    print(f"Saved: {output_path} ({size[0]}x{size[1]})")
    return True

# Process each texture
textures = [
    ("bronze_coin_v2_*.png", "bronze_novus_coin.png"),
    ("silver_coin_v2_*.png", "silver_novus_coin.png"),
    ("gold_coin_v2_*.png", "gold_novus_coin.png"),
    ("scroll_v2_*.png", "banknote_scroll.png"),
]

print("Using rembg for deep learning background removal...")
for pattern, output_name in textures:
    input_pattern = os.path.join(ARTIFACTS_DIR, pattern)
    output_path = os.path.join(TEXTURES_DIR, output_name)
    process_texture(input_pattern, output_path)

print("\nAll textures processed with rembg!")
