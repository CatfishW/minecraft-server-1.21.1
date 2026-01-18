#!/usr/bin/env python3
"""Process Aether Scroll textures using rembg with ISNet model (better segmentation)."""

from rembg import remove, new_session
from PIL import Image
import glob
import os
from io import BytesIO

ARTIFACTS_DIR = "/home/benwulab/.gemini/antigravity/brain/fd23e05d-95fe-464f-b9d2-e3e3991b4207"
TEXTURES_DIR = "/data/Yanlai/minecraft_server_fabric_1.21.1_fresh/mods-dev/novus-items/src/main/resources/assets/novus_items/textures/item"

# Use ISNet model for better segmentation
print("Loading ISNet model for better segmentation...")
session = new_session("isnet-general-use")

def process_texture(input_path, output_path, size=(128, 128)):
    """Remove background using rembg with ISNet model and resize."""
    print(f"Processing: {os.path.basename(input_path)}")
    
    # Open and process with rembg
    with open(input_path, 'rb') as f:
        input_data = f.read()
    
    # Remove background using ISNet deep learning model
    output_data = remove(input_data, session=session)
    
    # Load result and resize
    img = Image.open(BytesIO(output_data)).convert("RGBA")
    
    # Resize to target size with LANCZOS for quality
    img_resized = img.resize(size, Image.LANCZOS)
    
    # Save
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    img_resized.save(output_path, "PNG")
    print(f"  -> Saved: {output_path}")
    return True

# Process all Aether Scroll textures
textures = [
    ("aether_scroll_1h_v2_*.png", "aether_scroll_1h.png"),
    ("aether_scroll_12h_v2_*.png", "aether_scroll_12h.png"),
    ("aether_scroll_24h_*.png", "aether_scroll_24h.png"),
    ("aether_scroll_3d_*.png", "aether_scroll_3d.png"),
    ("aether_scroll_7d_*.png", "aether_scroll_7d.png"),
    ("aether_scroll_permanent_*.png", "aether_scroll_permanent.png"),
]

print(f"\nProcessing Aether Scroll textures with ISNet model...")
print(f"Target size: 128x128")
print(f"Output directory: {TEXTURES_DIR}\n")

success_count = 0
for pattern, output_name in textures:
    input_pattern = os.path.join(ARTIFACTS_DIR, pattern)
    files = sorted(glob.glob(input_pattern))
    if files:
        output_path = os.path.join(TEXTURES_DIR, output_name)
        if process_texture(files[-1], output_path):
            success_count += 1
    else:
        print(f"No files found for pattern: {pattern}")

print(f"\n✓ Processed {success_count}/{len(textures)} textures successfully!")
