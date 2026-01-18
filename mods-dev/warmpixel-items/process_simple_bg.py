#!/usr/bin/env python3
"""Process Aether Scroll textures: remove black background and resize to 128x128.
Uses simple color-based removal instead of rembg to avoid over-segmentation."""

from PIL import Image
import glob
import os

ARTIFACTS_DIR = "/home/benwulab/.gemini/antigravity/brain/fd23e05d-95fe-464f-b9d2-e3e3991b4207"
TEXTURES_DIR = "/data/Yanlai/minecraft_server_fabric_1.21.1_fresh/mods-dev/novus-items/src/main/resources/assets/novus_items/textures/item"

def remove_black_background(input_path, output_path, size=(128, 128), threshold=30):
    """Remove black/near-black background and resize."""
    print(f"Processing: {input_path}")
    
    # Open image and convert to RGBA
    img = Image.open(input_path).convert("RGBA")
    
    # Get pixel data
    data = img.getdata()
    
    # Replace black/near-black pixels with transparent
    new_data = []
    for item in data:
        r, g, b, a = item
        # Check if pixel is black or near-black (with threshold)
        if r < threshold and g < threshold and b < threshold:
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

# Process Aether Scroll textures - use v2 versions if available, otherwise original
textures = [
    ("aether_scroll_1h_v2_*.png", "aether_scroll_1h.png"),
    ("aether_scroll_12h_v2_*.png", "aether_scroll_12h.png"),
    ("aether_scroll_24h_*.png", "aether_scroll_24h.png"),  # No v2, use original
    ("aether_scroll_3d_*.png", "aether_scroll_3d.png"),    # No v2, use original
    ("aether_scroll_7d_*.png", "aether_scroll_7d.png"),    # No v2, use original
    ("aether_scroll_permanent_*.png", "aether_scroll_permanent.png"),  # No v2, use original
]

print("Processing Aether Scroll textures with simple black background removal...")
print(f"Target size: 128x128")
print(f"Output directory: {TEXTURES_DIR}\n")

success_count = 0
for pattern, output_name in textures:
    input_pattern = os.path.join(ARTIFACTS_DIR, pattern)
    files = sorted(glob.glob(input_pattern))
    if files:
        output_path = os.path.join(TEXTURES_DIR, output_name)
        if remove_black_background(files[-1], output_path):
            success_count += 1
    else:
        print(f"No files found for pattern: {pattern}")

print(f"\nProcessed {success_count}/{len(textures)} textures successfully!")
