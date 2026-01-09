#!/usr/bin/env python3
"""Process coin and banknote textures: remove background using rembg and resize to 128x128."""

from rembg import remove
from PIL import Image
import glob
import os
from io import BytesIO

# Original artifacts with coin textures
ARTIFACTS_DIR = "/home/benwulab/.gemini/antigravity/brain/f25f064e-440e-49f0-b345-2e7f8a9b8725"
TEXTURES_DIR = "/data/Yanlai/minecraft_server_fabric_1.21.1_fresh/mods-dev/novus-items/src/main/resources/assets/novus_items/textures/item"

def process_texture(input_pattern, output_name, size=(128, 128)):
    """Remove background using rembg and resize."""
    files = sorted(glob.glob(input_pattern))
    if not files:
        print(f"No files found for pattern: {input_pattern}")
        return False
    
    # Use the latest generated file (sorted by name, which includes timestamp)
    input_path = files[-1]
    output_path = os.path.join(TEXTURES_DIR, output_name)
    print(f"Processing: {input_path}")
    
    # Open and process with rembg
    with open(input_path, 'rb') as f:
        input_data = f.read()
    
    # Remove background using deep learning
    output_data = remove(input_data)
    
    # Load result and resize
    img = Image.open(BytesIO(output_data)).convert("RGBA")
    
    # Resize to target size with LANCZOS for quality
    img_resized = img.resize(size, Image.LANCZOS)
    
    # Save
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    img_resized.save(output_path, "PNG")
    print(f"Saved: {output_path} ({size[0]}x{size[1]})")
    return True

# Process coin and banknote textures (use hires versions for best quality)
textures = [
    ("bronze_coin_hires_*.png", "bronze_novus_coin.png"),
    ("silver_coin_hires_*.png", "silver_novus_coin.png"),
    ("gold_coin_hires_*.png", "gold_novus_coin.png"),
    ("scroll_hires_*.png", "banknote_scroll.png"),
]

print("Processing coin and banknote textures with rembg (deep learning background removal)...")
print(f"Target size: 128x128")
print(f"Output directory: {TEXTURES_DIR}\n")

success_count = 0
for pattern, output_name in textures:
    input_pattern = os.path.join(ARTIFACTS_DIR, pattern)
    if process_texture(input_pattern, output_name):
        success_count += 1

print(f"\nProcessed {success_count}/{len(textures)} textures successfully!")
