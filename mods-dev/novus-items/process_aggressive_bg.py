#!/usr/bin/env python3
"""Process Aether Scroll textures with aggressive black background removal."""

from PIL import Image
import glob
import os

ARTIFACTS_DIR = "/home/benwulab/.gemini/antigravity/brain/fd23e05d-95fe-464f-b9d2-e3e3991b4207"
TEXTURES_DIR = "/data/Yanlai/minecraft_server_fabric_1.21.1_fresh/mods-dev/novus-items/src/main/resources/assets/novus_items/textures/item"

def remove_dark_background(input_path, output_path, size=(128, 128), threshold=50):
    """Remove dark background from image using aggressive thresholding."""
    print(f"Processing: {input_path}")
    
    # Open image and convert to RGBA
    img = Image.open(input_path).convert("RGBA")
    pixels = img.load()
    width, height = img.size
    
    # Process each pixel
    for y in range(height):
        for x in range(width):
            r, g, b, a = pixels[x, y]
            # Calculate brightness
            brightness = (r + g + b) / 3
            # If pixel is dark/near-black, make transparent
            if brightness < threshold:
                pixels[x, y] = (0, 0, 0, 0)
    
    # Resize to target size with LANCZOS for quality
    img_resized = img.resize(size, Image.LANCZOS)
    
    # Save
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    img_resized.save(output_path, "PNG")
    print(f"Saved: {output_path} ({size[0]}x{size[1]})")
    return True

# Process all Aether Scroll textures with high threshold
textures = [
    ("aether_scroll_1h_v2_*.png", "aether_scroll_1h.png", 40),
    ("aether_scroll_12h_v2_*.png", "aether_scroll_12h.png", 40),
    ("aether_scroll_24h_*.png", "aether_scroll_24h.png", 50),  # More aggressive for problematic ones
    ("aether_scroll_3d_*.png", "aether_scroll_3d.png", 50),
    ("aether_scroll_7d_*.png", "aether_scroll_7d.png", 50),
    ("aether_scroll_permanent_*.png", "aether_scroll_permanent.png", 45),
]

print("Processing Aether Scroll textures with aggressive dark background removal...")
print(f"Target size: 128x128")
print(f"Output directory: {TEXTURES_DIR}\n")

success_count = 0
for pattern, output_name, thresh in textures:
    input_pattern = os.path.join(ARTIFACTS_DIR, pattern)
    files = sorted(glob.glob(input_pattern))
    if files:
        output_path = os.path.join(TEXTURES_DIR, output_name)
        if remove_dark_background(files[-1], output_path, threshold=thresh):
            success_count += 1
    else:
        print(f"No files found for pattern: {pattern}")

print(f"\nProcessed {success_count}/{len(textures)} textures successfully!")
