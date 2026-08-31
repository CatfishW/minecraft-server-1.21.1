#!/usr/bin/env python3
"""Generate, validate, and preview Easy NPC Minecraft Java skins.

The tool deliberately separates the creative step from the file-format step:

* an optional OpenAI-compatible Subtoken image endpoint can provide a creative
  draft (the API key is read only from ``SUBTOKEN_API_KEY``);
* every draft is converted to a deterministic 64x64 Java UV atlas and checked
  against the exact texture ids used by the Easy NPC templates;
* a local pixel-art fallback means an offline run still produces a complete,
  playable set;
* ``preview3d`` renders the atlas on a small block model, making UV mistakes
  visible before the files are copied to clients.

Only Pillow and the Python standard library are required.  The generated
manifest contains hashes and provenance, never credentials.
"""

from __future__ import annotations

import argparse
import base64
import binascii
import colorsys
import hashlib
import io
import json
import math
import os
import random
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any, Dict, Iterable, List, Optional, Sequence, Tuple


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_TEMPLATES = ROOT / "config" / "easy_npc" / "npc_templates"
DEFAULT_SKINS = ROOT / "config" / "easy_npc" / "skin"
DEFAULT_MANIFEST = DEFAULT_SKINS / "ai-skin-manifest.json"
DEFAULT_PREVIEWS = ROOT / "artifacts" / "npc-skin-previews3d"
PNG_SIZE = (64, 64)
MODEL_DIRS = ("humanoid", "humanoid_slim", "zombie")
# Texture ids are used as filenames and are also part of the Easy NPC UUID
# contract.  Reject path separators and traversal components before touching
# the filesystem; templates are data, but a future imported template must not
# be able to escape the selected skin directory.
SAFE_TEXTURE_ID = re.compile(r"^[A-Za-z0-9._-]+$")
MAX_IMAGE_BYTES = 20 * 1024 * 1024


def die(message: str, code: int = 2) -> "NoReturn":
    print("error: " + message, file=sys.stderr)
    raise SystemExit(code)


def pillow() -> Tuple[Any, Any, Any]:
    try:
        from PIL import Image, ImageDraw, ImageOps
    except ImportError:
        die("Pillow is required; install it with: python3 -m pip install --user Pillow")
    return Image, ImageDraw, ImageOps


def net(x: int, y: int, width: int, height: int, depth: int) -> List[Tuple[int, int, int, int]]:
    """Return top, bottom, left, front, right, and back UV rectangles."""
    return [
        (x + depth, y, x + depth + width, y + depth),
        (x + depth + width, y, x + depth + width * 2, y + depth),
        (x, y + depth, x + depth, y + depth + height),
        (x + depth, y + depth, x + depth + width, y + depth + height),
        (x + depth + width, y + depth, x + depth + width + depth, y + depth + height),
        (x + depth + width + depth, y + depth, x + depth * 2 + width * 2, y + depth + height),
    ]


# Java's 64x64 classic layout.  Slim skins use the same canvas and leave the
# unused two arm columns transparent; Easy NPC selects the arm model itself.
FACE_GROUPS: Dict[str, List[Tuple[int, int, int, int]]] = {
    "head": net(0, 0, 8, 8, 8),
    "hat": net(32, 0, 8, 8, 8),
    "right_leg": net(0, 16, 4, 12, 4),
    "torso": net(16, 16, 8, 12, 4),
    "right_arm": net(40, 16, 4, 12, 4),
    "right_leg_outer": net(0, 32, 4, 12, 4),
    "jacket": net(16, 32, 8, 12, 4),
    "right_sleeve": net(40, 32, 4, 12, 4),
    "left_leg": net(16, 48, 4, 12, 4),
    "left_leg_outer": net(0, 48, 4, 12, 4),
    "left_arm": net(32, 48, 4, 12, 4),
    "left_sleeve": net(48, 48, 4, 12, 4),
}
ALL_FACES: Tuple[Tuple[int, int, int, int], ...] = tuple(
    rect for group in FACE_GROUPS.values() for rect in group
)
BASE_GROUPS = ("head", "torso", "right_arm", "left_arm", "right_leg", "left_leg")


def inside_faces(x: int, y: int) -> bool:
    return any(a <= x < b and c <= y < d for a, c, b, d in ALL_FACES)


def strip_formatting(value: Any) -> str:
    text = str(value or "")
    return re.sub(r"\u00a7[0-9a-fk-or]", "", text, flags=re.IGNORECASE).strip()


def slug(value: str) -> str:
    value = strip_formatting(value)
    value = re.sub(r"[^A-Za-z0-9 _-]+", "", value)
    value = re.sub(r"\s+", "_", value).strip("_-")
    return value or "NPC"


def stable_seed(value: str) -> int:
    return int(hashlib.sha256(value.encode("utf-8")).hexdigest()[:16], 16)


def display_path(path: Path) -> str:
    """Prefer a repository-relative path, but support temporary output dirs."""
    try:
        return str(path.resolve().relative_to(ROOT))
    except ValueError:
        return str(path.resolve())


def safe_api_error(value: Any) -> str:
    """Keep gateway diagnostics useful without echoing credentials."""
    text = str(value)
    configured_key = os.environ.get("SUBTOKEN_API_KEY", "").strip()
    if configured_key:
        text = text.replace(configured_key, "[redacted]")
    text = re.sub(r"(?i)bearer\s+[^\s,}]+", "Bearer [redacted]", text)
    text = re.sub(r"(?i)sk-[A-Za-z0-9_-]+", "sk-[redacted]", text)
    text = re.sub(r"(?i)(api[_-]?key|authorization|access[_-]?token|token)(\s*[:=]\s*)[^,\s}]+", r"\1\2[redacted]", text)
    return text[:300]


def public_base_url(value: str) -> str:
    """Return a credential-free, normalized API base for logs/manifests."""
    raw = (value or "").strip().rstrip("/")
    if not raw:
        raw = "https://subtoken.shop/v1"
    parsed = urllib.parse.urlparse(raw)
    if not parsed.scheme or not parsed.netloc:
        return "https://subtoken.shop/v1"
    path = parsed.path.rstrip("/")
    if not path.endswith("/v1"):
        path += "/v1"
    return urllib.parse.urlunparse((parsed.scheme, parsed.netloc, path, "", "", ""))


def read_json(path: Path) -> Dict[str, Any]:
    try:
        with path.open("r", encoding="utf-8") as handle:
            data = json.load(handle)
    except Exception as exc:
        die("cannot read %s: %s" % (path, exc))
    if not isinstance(data, dict):
        die("template is not an object: %s" % path)
    return data


def discover_templates(template_dir: Path) -> List[Dict[str, Any]]:
    """Discover every template, including the nested ``temp`` directory."""
    records: List[Dict[str, Any]] = []
    for path in sorted(template_dir.rglob("*.json")):
        data = read_json(path)
        skin = data.get("skin")
        if not isinstance(skin, dict) or str(skin.get("type", "")).upper() != "CUSTOM":
            continue
        texture_id = skin.get("textureId")
        if not isinstance(texture_id, str) or not texture_id.strip():
            continue
        texture_id = texture_id.strip()
        if not SAFE_TEXTURE_ID.fullmatch(texture_id):
            die("unsafe textureId %r in %s" % (texture_id, path))
        entity = str(data.get("entityType", "easy_npc:humanoid"))
        model_dir = "zombie" if entity.endswith(":zombie") else "humanoid"
        records.append(
            {
                "template": str(path.relative_to(template_dir)),
                "template_path": path,
                "name": strip_formatting(data.get("name")) or path.stem,
                "description": str(data.get("description", "")),
                "entity_type": entity,
                "model_dir": model_dir,
                "texture_id": texture_id,
            }
        )
    return records


def unique_records(records: Sequence[Dict[str, Any]]) -> List[Dict[str, Any]]:
    unique: Dict[Tuple[str, str], Dict[str, Any]] = {}
    for record in records:
        key = (record["model_dir"], record["texture_id"])
        if key not in unique:
            copy = dict(record)
            copy["templates"] = [record["template"]]
            unique[key] = copy
        else:
            unique[key]["templates"].append(record["template"])
    return [unique[key] for key in sorted(unique)]


def find_asset(skins_root: Path, record: Dict[str, Any]) -> Optional[Path]:
    exact = skins_root / record["model_dir"] / (record["texture_id"] + ".png")
    if exact.is_file():
        return exact
    # A recursive lookup is limited to the selected model directory.  Preview
    # renders live outside these directories and must never become a texture.
    model_root = skins_root / record["model_dir"]
    matches = sorted(model_root.rglob(record["texture_id"] + ".png")) if model_root.is_dir() else []
    return matches[0] if matches else None


def shade(color: Tuple[int, int, int, int], factor: float, alpha: Optional[int] = None) -> Tuple[int, int, int, int]:
    return (
        max(0, min(255, int(color[0] * factor))),
        max(0, min(255, int(color[1] * factor))),
        max(0, min(255, int(color[2] * factor))),
        color[3] if alpha is None else alpha,
    )


def palette_for(name: str, description: str, seed: int) -> Dict[str, Tuple[int, int, int, int]]:
    text = (name + " " + description).lower()
    rng = random.Random(seed)
    skin_palettes = [
        ("#f1c7a5", "#d89470"),
        ("#d99a72", "#a96345"),
        ("#8d5524", "#603813"),
        ("#f0b98d", "#b86f50"),
        ("#c68662", "#8f4f39"),
    ]
    hair_palettes = ["#171717", "#3b2418", "#704214", "#a66a32", "#d8c29d", "#5b355d"]
    outfit_palettes = [
        ("#315a8a", "#9bc0e8"),
        ("#3d5f43", "#b1c98c"),
        ("#6f2935", "#d78a75"),
        ("#54436d", "#c5a7e8"),
        ("#8a642f", "#e4c27e"),
        ("#3d454d", "#aeb9c4"),
    ]
    if any(word in text for word in ("guard", "police", "captain", "paladin", "soldier", "armed")):
        outfit = ("#263c5e", "#d0a84e")
    elif any(word in text for word in ("merchant", "farmer", "librarian", "murray", "saul", "lawyer")):
        outfit = ("#7a4a2a", "#e2b86b")
    elif any(word in text for word in ("wizard", "enchanter", "witch", "ender", "brenner")):
        outfit = ("#4e2f72", "#c67ee5")
    elif any(word in text for word in ("bandit", "raider", "hank", "danse", "maxson")):
        outfit = ("#4b3026", "#c46b3c")
    elif any(word in text for word in ("demogorgon", "zombie", "ghoul")):
        outfit = ("#4c6b54", "#b6d18f")
    else:
        outfit = rng.choice(outfit_palettes)
    skin, skin_shadow = rng.choice(skin_palettes)
    skin_rgb = tuple(int(skin[i : i + 2], 16) for i in (1, 3, 5))
    shadow_rgb = tuple(int(skin_shadow[i : i + 2], 16) for i in (1, 3, 5))
    hair = rng.choice(hair_palettes)
    hair_rgb = tuple(int(hair[i : i + 2], 16) for i in (1, 3, 5))
    outfit_rgb = tuple(int(outfit[0][i : i + 2], 16) for i in (1, 3, 5))
    accent_rgb = tuple(int(outfit[1][i : i + 2], 16) for i in (1, 3, 5))
    return {
        "skin": (*skin_rgb, 255),
        "skin_shadow": (*shadow_rgb, 255),
        "hair": (*hair_rgb, 255),
        "outfit": (*outfit_rgb, 255),
        "accent": (*accent_rgb, 255),
        "boot": shade((*outfit_rgb, 255), 0.42),
        "eye": (30, 38, 47, 255),
        "metal": (185, 194, 204, 255),
    }


def extract_concept_palette(image: Any, seed: int, fallback: Dict[str, Tuple[int, int, int, int]]) -> Dict[str, Tuple[int, int, int, int]]:
    """Extract a few stable clothing/skin colors from a 3D AI concept.

    The concept is never copied directly into the atlas.  Sampling only its
    broad colors keeps perspective/background pixels out of the UV map while
    still letting a hosted model influence the final procedural skin.
    """
    Image, _, _ = pillow()
    rgba = image.convert("RGBA")
    rgba.thumbnail((192, 192), Image.Resampling.BILINEAR)
    width, height = rgba.size

    # Estimate the studio background from a narrow border.  A dark-blue
    # backdrop is otherwise easily mistaken for a navy coat or black hair.
    border: List[Tuple[int, int, int]] = []
    margin_x = max(1, width // 16)
    margin_y = max(1, height // 16)
    for y in range(height):
        for x in range(width):
            if x < margin_x or x >= width - margin_x or y < margin_y or y >= height - margin_y:
                r, g, b, alpha = rgba.getpixel((x, y))
                if alpha >= 160:
                    border.append((int(r), int(g), int(b)))
    if border:
        bg = tuple(sum(pixel[i] for pixel in border) // len(border) for i in range(3))
    else:
        bg = (0, 0, 0)

    histogram: Dict[Tuple[int, int, int], int] = {}
    for y in range(height):
        for x in range(width):
            r, g, b, alpha = rgba.getpixel((x, y))
            if alpha < 160:
                continue
            # Leave the border out even when the model fills the frame.
            if x < margin_x or x >= width - margin_x or y < margin_y or y >= height - margin_y:
                continue
            distance = math.sqrt(sum((int(channel) - bg[index]) ** 2 for index, channel in enumerate((r, g, b))))
            brightness = (int(r) + int(g) + int(b)) / 3.0
            spread = max(r, g, b) - min(r, g, b)
            # Reject the usual studio background and neutral specular pixels.
            if distance < 32 or brightness < 35 or (brightness > 246 and spread < 24) or spread < 10:
                continue
            bucket = (int(r) // 16 * 16 + 8, int(g) // 16 * 16 + 8, int(b) // 16 * 16 + 8)
            histogram[bucket] = histogram.get(bucket, 0) + 1
    if not histogram:
        return fallback

    def hsv(color: Tuple[int, int, int]) -> Tuple[float, float, float]:
        return colorsys.rgb_to_hsv(color[0] / 255.0, color[1] / 255.0, color[2] / 255.0)

    # Skin is selected by warm hue and a healthy mid/high value, not merely by
    # frequency; this avoids choosing a dark background as a face.
    skin_candidates = [
        color for color in histogram
        if ((hsv(color)[0] < 0.14 or hsv(color)[0] > 0.94) and 0.20 < hsv(color)[1] < 0.90 and hsv(color)[2] > 0.38)
    ]
    skin = max(skin_candidates, key=lambda color: (histogram[color], hsv(color)[2])) if skin_candidates else None

    # Prefer a dark, colorful hair tone, but keep the fallback when the only
    # dark candidate is the backdrop.
    hair_candidates = [color for color in histogram if hsv(color)[1] > 0.20 and 0.12 < hsv(color)[2] < 0.55]
    hair = max(hair_candidates, key=lambda color: (histogram[color], -hsv(color)[2])) if hair_candidates else None

    # Clothing is the most frequent saturated non-skin tone.  Very dark/blue
    # concepts naturally fall back to the role palette, which is preferable to
    # baking a studio background into every atlas.
    outfit_candidates = [
        color for color in histogram
        if hsv(color)[1] > 0.28 and hsv(color)[2] > 0.22 and color != skin
    ]
    outfit = max(outfit_candidates, key=lambda color: (histogram[color], hsv(color)[1])) if outfit_candidates else None
    accent_candidates = [
        color for color in histogram
        if hsv(color)[1] > 0.35 and hsv(color)[2] > 0.50 and color not in (skin, outfit)
    ]
    accent = max(accent_candidates, key=lambda color: (hsv(color)[1], histogram[color])) if accent_candidates else None

    result = dict(fallback)
    if skin is not None:
        result["skin"] = (*skin, 255)
        result["skin_shadow"] = shade(result["skin"], 0.68)
    if hair is not None:
        result["hair"] = (*hair, 255)
    if outfit is not None:
        result["outfit"] = (*outfit, 255)
        result["boot"] = shade(result["outfit"], 0.42)
    if accent is not None:
        result["accent"] = (*accent, 255)
    return result


def rect_fill(image: Any, rect: Tuple[int, int, int, int], color: Tuple[int, int, int, int]) -> None:
    from PIL import ImageDraw

    ImageDraw.Draw(image).rectangle((rect[0], rect[1], rect[2] - 1, rect[3] - 1), fill=color)


def fill_group(image: Any, group: str, color: Tuple[int, int, int, int], alpha: int = 255) -> None:
    factors = (1.10, 0.68, 0.86, 1.0, 0.92, 0.76)
    for index, rect in enumerate(FACE_GROUPS[group]):
        rect_fill(image, rect, shade(color, factors[index], alpha))


def pixel_rect(image: Any, rect: Tuple[int, int, int, int], x: int, y: int, w: int, h: int, color: Tuple[int, int, int, int]) -> None:
    left, top, right, bottom = rect
    if x < 0 or y < 0 or x + w > right - left or y + h > bottom - top:
        return
    rect_fill(image, (left + x, top + y, left + x + w, top + y + h), color)


def generate_pixel_skin(name: str, description: str, texture_id: str, entity_type: str = "easy_npc:humanoid", palette_override: Optional[Dict[str, Tuple[int, int, int, int]]] = None) -> Any:
    """Create a complete, readable classic Java atlas without network access."""
    Image, _, _ = pillow()
    seed = stable_seed(texture_id + "|" + name)
    rng = random.Random(seed)
    colors = palette_for(name, description, seed)
    if palette_override:
        for key, value in palette_override.items():
            if key in colors and isinstance(value, tuple) and len(value) == 4:
                colors[key] = value
    image = Image.new("RGBA", PNG_SIZE, (0, 0, 0, 0))

    if entity_type.endswith(":zombie"):
        # Keep zombie templates recognizable while retaining the humanoid UV
        # contract expected by the Easy NPC custom loader.
        colors["skin"] = (91, 137, 83, 255)
        colors["skin_shadow"] = (53, 87, 55, 255)
        colors["hair"] = (43, 65, 43, 255)

    for group in BASE_GROUPS:
        base = colors["skin"] if group == "head" else colors["outfit"]
        fill_group(image, group, base)

    # Head: hairline, ears, eyes, nose, and mouth are intentionally drawn in
    # the front rectangle so the 3D preview makes facial orientation obvious.
    head_front = FACE_GROUPS["head"][3]
    head_top = FACE_GROUPS["head"][0]
    head_back = FACE_GROUPS["head"][5]
    head_left = FACE_GROUPS["head"][2]
    head_right = FACE_GROUPS["head"][4]
    pixel_rect(image, head_front, 0, 0, 8, 2, colors["hair"])
    pixel_rect(image, head_front, 0, 2, 1, 3, colors["hair"])
    pixel_rect(image, head_front, 7, 2, 1, 3, colors["hair"])
    pixel_rect(image, head_top, 0, 0, 8, 8, colors["hair"])
    pixel_rect(image, head_back, 0, 0, 8, 8, colors["hair"])
    pixel_rect(image, head_left, 0, 0, 2, 8, colors["hair"])
    pixel_rect(image, head_right, 6, 0, 2, 8, colors["hair"])
    pixel_rect(image, head_front, 2, 3, 1, 1, colors["eye"])
    pixel_rect(image, head_front, 5, 3, 1, 1, colors["eye"])
    pixel_rect(image, head_front, 3, 5, 2, 1, colors["skin_shadow"])
    pixel_rect(image, head_front, 2, 6, 4, 1, colors["skin_shadow"])

    # Outfit details: collar, belt, a small role-colored badge, and boots.
    torso_front = FACE_GROUPS["torso"][3]
    pixel_rect(image, torso_front, 0, 0, 8, 2, colors["accent"])
    pixel_rect(image, torso_front, 2, 2, 4, 1, colors["skin_shadow"])
    pixel_rect(image, torso_front, 3, 4, 2, 3, colors["accent"])
    for group in ("right_arm", "left_arm"):
        front = FACE_GROUPS[group][3]
        pixel_rect(image, front, 0, 0, 4, 2, colors["accent"])
        if rng.random() > 0.35:
            pixel_rect(image, front, 1, 5, 2, 3, colors["skin_shadow"])
    for group in ("right_leg", "left_leg"):
        front = FACE_GROUPS[group][3]
        pixel_rect(image, front, 0, 9, 4, 3, colors["boot"])
        pixel_rect(image, FACE_GROUPS[group][4], 0, 9, 4, 3, shade(colors["boot"], 0.8))

    # Outer layers.  They are semi-transparent by design, so a server/client
    # can still distinguish the base atlas if the layer toggle is disabled.
    if any(word in (name + " " + description).lower() for word in ("guard", "coat", "jacket", "police", "merchant", "captain", "bandit")):
        for group in ("jacket", "right_sleeve", "left_sleeve"):
            fill_group(image, group, colors["outfit"], 170)
        pixel_rect(image, FACE_GROUPS["jacket"][3], 0, 0, 8, 2, colors["accent"][:-1] + (190,))
    # A hair/hat layer gives the head a visible silhouette in 3D.
    fill_group(image, "hat", colors["hair"], 150)
    pixel_rect(image, FACE_GROUPS["hat"][3], 1, 0, 6, 2, colors["accent"][:-1] + (180,))

    return sanitize_atlas(image)


def copy_legacy_net(source: Any, target: Any, source_group: str, target_group: str) -> None:
    """Copy a base limb net into the missing left-limb area of a 64x32 skin."""
    Image, _, ImageOps = pillow()
    # The native Easy NPC converter swaps the side faces while mirroring a
    # right limb into the left-limb slot.  Keeping that winding matters for
    # stripes, sleeves, and boots in the 3D preview.
    target_indices = (0, 1, 4, 3, 2, 5)
    for source_rect, target_index in zip(FACE_GROUPS[source_group], target_indices):
        target_rect = FACE_GROUPS[target_group][target_index]
        crop = source.crop(source_rect)
        # Left and right limbs have opposite winding on the model.  Flipping
        # each face keeps stripes/textures facing the expected direction.
        crop = ImageOps.mirror(crop)
        target.alpha_composite(crop, (target_rect[0], target_rect[1]))


def sanitize_atlas(image: Any) -> Any:
    """Return an exact RGBA 64x64 atlas with non-face pixels transparent."""
    Image, _, _ = pillow()
    if image.size != PNG_SIZE:
        image = image.resize(PNG_SIZE, Image.Resampling.NEAREST)
    image = image.convert("RGBA")
    for y in range(PNG_SIZE[1]):
        for x in range(PNG_SIZE[0]):
            if not inside_faces(x, y):
                image.putpixel((x, y), (0, 0, 0, 0))
    return image


def normalize_existing(path: Path, fallback_name: str, description: str, texture_id: str, entity_type: str) -> Tuple[Any, str, Optional[str]]:
    """Normalize a legacy/invalid file, or return a deterministic replacement."""
    Image, _, _ = pillow()
    try:
        with Image.open(path) as opened:
            opened.load()
            source = opened.convert("RGBA")
            original_size = opened.size
    except Exception as exc:
        return generate_pixel_skin(fallback_name, description, texture_id, entity_type), "fallback", str(exc)
    if original_size == PNG_SIZE:
        return sanitize_atlas(source), "existing-normalized", None
    if original_size == (64, 32):
        canvas = Image.new("RGBA", PNG_SIZE, (0, 0, 0, 0))
        canvas.alpha_composite(source, (0, 0))
        copy_legacy_net(source, canvas, "right_leg", "left_leg")
        copy_legacy_net(source, canvas, "right_arm", "left_arm")
        return sanitize_atlas(canvas), "legacy-64x32-converted", None
    return generate_pixel_skin(fallback_name, description, texture_id, entity_type), "fallback", "unsupported dimensions %sx%s" % original_size


def png_bytes(image: Any) -> bytes:
    stream = io.BytesIO()
    image.save(stream, "PNG", optimize=False)
    return stream.getvalue()


def write_png(image: Any, path: Path) -> Dict[str, Any]:
    data = png_bytes(image)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)
    return {
        "size": len(data),
        "sha1": hashlib.sha1(data).hexdigest(),
        "sha256": hashlib.sha256(data).hexdigest(),
        "dimensions": [64, 64],
    }


def validate_image(path: Path) -> Dict[str, Any]:
    Image, _, _ = pillow()
    result: Dict[str, Any] = {"path": str(path), "valid": False, "errors": []}
    if not path.is_file():
        result["errors"].append("missing file")
        return result
    try:
        with Image.open(path) as opened:
            opened.load()
            result["format"] = opened.format
            result["mode"] = opened.mode
            result["dimensions"] = list(opened.size)
            if opened.format != "PNG":
                result["errors"].append("format is %s" % opened.format)
            if opened.size != PNG_SIZE:
                result["errors"].append("dimensions are %sx%s; expected 64x64" % opened.size)
            # A completely transparent face would render as a missing model
            # part.  Check the six base faces, not optional outer layers.
            rgba = opened.convert("RGBA")
            for group in BASE_GROUPS:
                for rect in FACE_GROUPS[group]:
                    opaque = any(rgba.getpixel((x, y))[3] for y in range(rect[1], rect[3]) for x in range(rect[0], rect[2]))
                    if not opaque:
                        result["errors"].append("empty face %s" % group)
                        break
    except Exception as exc:
        result["errors"].append("cannot decode: %s" % exc)
    result["valid"] = not result["errors"]
    return result


def validate_manifest(manifest_path: Path, skins_root: Path) -> List[str]:
    """Validate the file/hash claims in an AI skin manifest."""
    errors: List[str] = []
    try:
        data = json.loads(manifest_path.read_text(encoding="utf-8"))
    except Exception as exc:
        return ["cannot read manifest %s: %s" % (manifest_path, exc)]
    entries = data.get("skins") if isinstance(data, dict) else None
    if not isinstance(entries, list):
        return ["manifest has no skins list: %s" % manifest_path]
    seen = set()
    for item in entries:
        if not isinstance(item, dict):
            errors.append("manifest contains a non-object skin entry")
            continue
        model_dir = str(item.get("model_dir", ""))
        texture_id = str(item.get("texture_id", ""))
        key = (model_dir, texture_id)
        if key in seen:
            errors.append("duplicate manifest entry %s/%s" % key)
        seen.add(key)
        if model_dir not in MODEL_DIRS or not SAFE_TEXTURE_ID.fullmatch(texture_id):
            errors.append("unsafe manifest id %s/%s" % key)
            continue
        path = skins_root / model_dir / (texture_id + ".png")
        result = validate_image(path)
        if not result["valid"]:
            errors.append("%s: %s" % (path, "; ".join(result["errors"])))
            continue
        hashes = file_hashes(path)
        for algorithm in ("sha1", "sha256"):
            expected = item.get(algorithm)
            if expected and expected != hashes[algorithm]:
                errors.append("%s %s mismatch" % (path, algorithm))
    return errors


def api_prompt(record: Dict[str, Any], output_kind: str = "concept") -> str:
    """Build a prompt for either the recommended 3D concept or a UV atlas.

    A concept is the default because image models are much better at designing
    a coherent character in 3D than at placing pixels in Java's discontinuous
    UV layout.  The local decoder then maps the concept's palette to every
    face, so the resulting file remains a valid, deterministic skin.
    """
    if output_kind == "atlas":
        return (
            "Create a flat, front-facing Minecraft Java skin UV atlas for the "
            "blocky NPC named %s. Role/context: %s. Use the standard 64x64 skin "
            "layout, fill every head, torso, arm, and leg face, keep transparent "
            "unused pixels, and do not add a border, labels, perspective, text, "
            "logo, watermark, or extra characters."
            % (record["name"], record.get("description", ""))
        )
    return (
        "Create a clean three-quarter orthographic concept render of a blocky "
        "Minecraft-style NPC named %s. Role/context: %s. Show the full head, "
        "torso, arms, legs, face, hair, clothing, accessories, and readable "
        "pixel-art materials from front and right-side views. Use a neutral studio "
        "background and soft rim light so the clothing colors are easy to sample. "
        "This is a design reference for a deterministic Java skin atlas decoder, "
        "not the final UV sheet. No text, logo, watermark, or extra characters."
        % (record["name"], record.get("description", ""))
    )


class ApiFailure(Exception):
    pass


def api_image(prompt: str, model: str, retries: int, timeout: int = 120) -> Any:
    """Call Subtoken's OpenAI-compatible image endpoint without logging secrets."""
    Image, _, _ = pillow()
    key = os.environ.get("SUBTOKEN_API_KEY", "").strip()
    if not key:
        raise ApiFailure("SUBTOKEN_API_KEY is not set")
    base = public_base_url(os.environ.get("SUBTOKEN_BASE_URL", "https://subtoken.shop/v1"))
    endpoint = base + "/images/generations"
    model_name = model or os.environ.get("SUBTOKEN_IMAGE_MODEL", "grok-imagine-image")
    payload: Dict[str, Any] = {"model": model_name, "prompt": prompt, "n": 1}
    requested_size = os.environ.get("SUBTOKEN_IMAGE_SIZE", "").strip()
    if requested_size:
        payload["size"] = requested_size
    # Some OpenAI-compatible deployments reject response_format even though
    # they return b64_json by default.  Try the richer request first, then a
    # minimal request once.
    attempts: List[Dict[str, Any]] = [dict(payload, response_format="b64_json"), payload]
    last_error = "unknown API error"
    for body in attempts:
        for attempt in range(max(1, retries + 1)):
            request = urllib.request.Request(
                endpoint,
                data=json.dumps(body).encode("utf-8"),
                headers={"Authorization": "Bearer " + key, "Content-Type": "application/json"},
                method="POST",
            )
            try:
                with urllib.request.urlopen(request, timeout=timeout) as response:
                    response_bytes = response.read(MAX_IMAGE_BYTES + 1)
                    if len(response_bytes) > MAX_IMAGE_BYTES:
                        raise ApiFailure("image API response exceeds %d bytes" % MAX_IMAGE_BYTES)
                    decoded = json.loads(response_bytes.decode("utf-8"))
                items = decoded.get("data") if isinstance(decoded, dict) else None
                item = items[0] if isinstance(items, list) and items else None
                if not isinstance(item, dict):
                    raise ApiFailure("image response did not contain data[0]")
                if item.get("b64_json"):
                    raw = base64.b64decode(item["b64_json"])
                elif item.get("url"):
                    returned_url = str(item["url"])
                    parsed_url = urllib.parse.urlparse(returned_url)
                    parsed_base = urllib.parse.urlparse(base)
                    same_origin = parsed_url.scheme in ("", parsed_base.scheme) and parsed_url.netloc in ("", parsed_base.netloc)
                    media_url = returned_url
                    if not parsed_url.scheme:
                        media_url = urllib.parse.urljoin(base + "/", returned_url)
                    if parsed_url.scheme and parsed_url.scheme not in ("http", "https"):
                        raise ApiFailure("image response used an unsupported URL scheme")
                    media_headers: Dict[str, str] = {}
                    if same_origin:
                        # Relative media URLs and same-origin URLs need the
                        # bearer token; never send it to an arbitrary host.
                        media_headers["Authorization"] = "Bearer " + key
                    image_request = urllib.request.Request(media_url, headers=media_headers)
                    with urllib.request.urlopen(image_request, timeout=timeout) as image_response:
                        raw = image_response.read(MAX_IMAGE_BYTES + 1)
                    if len(raw) > MAX_IMAGE_BYTES:
                        raise ApiFailure("image media exceeds %d bytes" % MAX_IMAGE_BYTES)
                else:
                    raise ApiFailure("image response had neither b64_json nor url")
                with Image.open(io.BytesIO(raw)) as opened:
                    opened.load()
                    return opened.convert("RGBA")
            except urllib.error.HTTPError as exc:
                if exc.code in (400, 401, 403, 404):
                    try:
                        detail = json.loads(exc.read().decode("utf-8"))
                        detail_text = safe_api_error(detail.get("error", detail))
                    except Exception:
                        detail_text = "HTTP %s" % exc.code
                    last_error = detail_text
                    break
                if exc.code == 429 or exc.code >= 500:
                    retry_after = exc.headers.get("Retry-After")
                    try:
                        delay = float(retry_after) if retry_after else min(30.0, 2.0 ** attempt)
                    except ValueError:
                        delay = min(30.0, 2.0 ** attempt)
                    time.sleep(delay)
                    last_error = "HTTP %s" % exc.code
                    continue
                last_error = "HTTP %s" % exc.code
                break
            except (urllib.error.URLError, TimeoutError, ValueError, binascii.Error, ApiFailure, OSError) as exc:
                last_error = safe_api_error(exc)
                if attempt < retries:
                    time.sleep(min(30.0, 2.0 ** attempt))
                    continue
                break
    raise ApiFailure(last_error)


def clean_api_draft(draft: Any, record: Dict[str, Any]) -> Any:
    """Convert an explicitly atlas-oriented draft to a safe Java atlas."""
    Image, _, _ = pillow()
    if draft.size == PNG_SIZE:
        return sanitize_atlas(draft)
    # A square atlas draft can still be resized, but nearest neighbour plus
    # face masking guarantees that it cannot break the loader.  3D concepts
    # should use ``concept_to_atlas`` instead so perspective is not misplaced.
    if draft.width == draft.height and draft.width >= 64:
        return sanitize_atlas(draft.resize(PNG_SIZE, Image.Resampling.NEAREST))
    return generate_pixel_skin(record["name"], record.get("description", ""), record["texture_id"], record["entity_type"])


def concept_to_atlas(draft: Any, record: Dict[str, Any]) -> Any:
    """Decode a 3D concept into a deterministic Java UV atlas.

    The model's perspective image is deliberately *not* resized into the
    atlas: doing that puts eyes, background, and lighting into arbitrary UV
    rectangles.  Instead, broad colors are sampled and the local renderer
    paints those colors into the known face rectangles.  This gives the AI a
    meaningful creative role while keeping every output playable and
    reproducible.
    """
    seed = stable_seed(record["texture_id"] + "|concept")
    fallback = palette_for(record["name"], record.get("description", ""), stable_seed(record["texture_id"] + "|" + record["name"]))
    colors = extract_concept_palette(draft, seed, fallback)
    return generate_pixel_skin(
        record["name"],
        record.get("description", ""),
        record["texture_id"],
        record["entity_type"],
        palette_override=colors,
    )


def draw_face(canvas: Any, source: Any, rect: Tuple[int, int, int, int], origin: Tuple[float, float], basis_u: Tuple[float, float], basis_v: Tuple[float, float], scale: float) -> None:
    """Paint each source texel as a tiny parallelogram for an isometric face."""
    Image, ImageDraw, _ = pillow()
    left, top, right, bottom = rect
    overlay = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(overlay)
    for sy in range(top, bottom):
        for sx in range(left, right):
            color = source.getpixel((sx, sy))
            if color[3] == 0:
                continue
            u = sx - left
            v = sy - top
            p0 = (origin[0] + basis_u[0] * u + basis_v[0] * v, origin[1] + basis_u[1] * u + basis_v[1] * v)
            p1 = (p0[0] + basis_u[0], p0[1] + basis_u[1])
            p2 = (p1[0] + basis_v[0], p1[1] + basis_v[1])
            p3 = (p0[0] + basis_v[0], p0[1] + basis_v[1])
            draw.polygon((p0, p1, p2, p3), fill=color)
    canvas.alpha_composite(overlay)


def draw_part(canvas: Any, source: Any, group: str, front_origin: Tuple[float, float], scale: float, depth_ratio: float = 0.62, outer_group: Optional[str] = None) -> None:
    rects = FACE_GROUPS[group]
    front = rects[3]
    width = front[2] - front[0]
    height = front[3] - front[1]
    depth = rects[4][2] - rects[4][0]
    depth_vec = (scale * depth_ratio, -scale * 0.48)
    basis_front_u = (scale, 0.0)
    basis_front_v = (0.0, scale)
    front_right = (front_origin[0] + width * scale, front_origin[1])
    # Top and side first, then the front face, for stable occlusion.
    draw_face(canvas, source, rects[0], front_origin, basis_front_u, depth_vec, scale)
    draw_face(canvas, source, rects[4], front_right, depth_vec, basis_front_v, scale)
    draw_face(canvas, source, front, front_origin, basis_front_u, basis_front_v, scale)
    if outer_group:
        outer_rects = FACE_GROUPS[outer_group]
        # A small offset makes the translucent layer legible without trying to
        # change the model's dimensions.
        offset = (-scale * 0.10, -scale * 0.10)
        outer_front = (front_origin[0] + offset[0], front_origin[1] + offset[1])
        draw_face(canvas, source, outer_rects[0], outer_front, basis_front_u, depth_vec, scale)
        draw_face(canvas, source, outer_rects[4], (outer_front[0] + width * scale, outer_front[1]), depth_vec, basis_front_v, scale)
        draw_face(canvas, source, outer_rects[3], outer_front, basis_front_u, basis_front_v, scale)


def render_3d(source: Any, title: str = "NPC", width: int = 640, height: int = 720) -> Any:
    """Render a readable three-quarter block model using only Pillow."""
    Image, ImageDraw, _ = pillow()
    scale = 8.0
    canvas = Image.new("RGBA", (width, height), (19, 25, 37, 255))
    draw = ImageDraw.Draw(canvas)
    # Subtle studio backdrop and ground shadow.
    for y in range(height):
        t = y / float(height)
        color = (int(19 + 18 * t), int(25 + 22 * t), int(37 + 30 * t), 255)
        draw.line((0, y, width, y), fill=color)
    draw.ellipse((145, 575, 500, 650), fill=(8, 12, 18, 130))
    # Back limbs, then torso, arms, and head.
    # The anchors intentionally touch: a common flat-UV mistake is a floating
    # head or detached limbs, which is much easier to spot in this view.
    # Keep the front faces flush with one another.  At scale 8 the torso is
    # 64 px wide and each leg is 32 px wide, so the two legs must start at
    # x=208 and x=240 (not x=248); otherwise every preview falsely shows an
    # 8-pixel floating gap down the middle.
    draw_part(canvas, source, "left_leg", (240, 450), scale, outer_group="left_leg_outer")
    draw_part(canvas, source, "right_leg", (208, 450), scale, outer_group="right_leg_outer")
    draw_part(canvas, source, "torso", (208, 354), scale, outer_group="jacket")
    # Likewise, the 32-pixel arms meet the 64-pixel torso at x=208 and
    # x=272.  Flush anchors make an actual detached-limb/UV error visible
    # instead of introducing one in the validator itself.
    draw_part(canvas, source, "left_arm", (272, 354), scale, outer_group="left_sleeve")
    draw_part(canvas, source, "right_arm", (176, 354), scale, outer_group="right_sleeve")
    draw_part(canvas, source, "head", (208, 290), scale, outer_group="hat")
    # Crisp silhouette lines and a label are useful in contact sheets.
    draw = ImageDraw.Draw(canvas)
    draw.rounded_rectangle((18, 18, width - 18, height - 18), radius=14, outline=(120, 150, 190, 150), width=2)
    draw.text((32, 35), title[:48], fill=(235, 241, 255, 255))
    draw.text((32, height - 42), "Java 64x64 • three-quarter UV preview", fill=(175, 195, 220, 255))
    return canvas


def make_contact_sheet(paths: Sequence[Path], output: Path, columns: int = 6) -> None:
    Image, ImageDraw, _ = pillow()
    thumb_w, thumb_h = 220, 250
    rows = max(1, int(math.ceil(len(paths) / float(columns))))
    sheet = Image.new("RGB", (columns * thumb_w, rows * thumb_h), (12, 17, 26))
    draw = ImageDraw.Draw(sheet)
    for index, path in enumerate(paths):
        x = (index % columns) * thumb_w
        y = (index // columns) * thumb_h
        try:
            with Image.open(path) as opened:
                thumb = opened.convert("RGBA")
                thumb.thumbnail((thumb_w - 16, thumb_h - 48), Image.Resampling.LANCZOS)
            px = x + (thumb_w - thumb.width) // 2
            py = y + 8
            sheet.paste(thumb, (px, py), thumb)
        except Exception:
            draw.rectangle((x + 8, y + 8, x + thumb_w - 8, y + thumb_h - 48), outline=(220, 80, 80))
        draw.text((x + 8, y + thumb_h - 32), path.stem[:28], fill=(220, 230, 245))
    output.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(output, "PNG", optimize=False)


def make_uv_guide(output: Path, scale: int = 12) -> None:
    """Create a labeled guide suitable as an imagegen edit target."""
    Image, ImageDraw, _ = pillow()
    scale = max(1, min(32, int(scale)))
    image = Image.new("RGBA", (64 * scale, 64 * scale), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    palette = [(125, 211, 252, 235), (196, 181, 253, 235), (134, 239, 172, 235), (253, 230, 138, 235), (252, 165, 165, 235), (103, 232, 249, 235)]
    for index, (name, rects) in enumerate(FACE_GROUPS.items()):
        color = palette[index % len(palette)]
        for left, top, right, bottom in rects:
            box = (left * scale, top * scale, right * scale - 1, bottom * scale - 1)
            draw.rectangle(box, fill=color, outline=(17, 24, 39, 255), width=max(1, scale // 4))
        first = rects[3]
        draw.text((first[0] * scale + 2, first[1] * scale + 2), name.replace("_", " "), fill=(17, 24, 39, 255))
    output = output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    image.save(output, "PNG", optimize=False)
    print(json.dumps({"guide": display_path(output), "logical_size": [64, 64], "rendered_size": list(image.size)}, ensure_ascii=False))


def load_image(path: Path) -> Any:
    Image, _, _ = pillow()
    try:
        with Image.open(path) as opened:
            opened.load()
            return opened.convert("RGBA")
    except Exception as exc:
        die("cannot read draft image %s: %s" % (path, exc))


def file_hashes(path: Path) -> Dict[str, Any]:
    data = path.read_bytes()
    return {"size": len(data), "sha1": hashlib.sha1(data).hexdigest(), "sha256": hashlib.sha256(data).hexdigest()}


def generate(args: argparse.Namespace) -> int:
    templates_dir = args.templates.resolve()
    skins_root = args.skins.resolve()
    discovered = discover_templates(templates_dir)
    custom_name = getattr(args, "name", None)
    custom_texture_id = getattr(args, "texture_id", None)
    if custom_name or custom_texture_id:
        if not custom_name or not custom_texture_id:
            die("custom generation requires both --name and --texture-id")
        custom_texture_id = str(custom_texture_id).strip()
        if not SAFE_TEXTURE_ID.fullmatch(custom_texture_id):
            die("unsafe --texture-id %r" % custom_texture_id)
        entity_type = str(getattr(args, "entity_type", "easy_npc:humanoid"))
        records = [{
            "template": "<command-line>",
            "template_path": None,
            "name": strip_formatting(custom_name) or custom_texture_id,
            "description": str(getattr(args, "description", "")),
            "entity_type": entity_type,
            "model_dir": "zombie" if entity_type.endswith(":zombie") else "humanoid",
            "texture_id": custom_texture_id,
            "templates": ["<command-line>"],
        }]
        scanned_count = 0
    else:
        records = unique_records(discovered)
        scanned_count = len(discovered)
    if args.only and not (custom_name or custom_texture_id):
        requested = set(args.only)
        records = [r for r in records if r["texture_id"] in requested or r["name"] in requested]
    if args.max_items:
        records = records[: args.max_items]
    if not records:
        die("no CUSTOM templates found under %s" % templates_dir)
    draft_image = None
    draft_dimensions: Optional[List[int]] = None
    draft_kind = getattr(args, "draft_kind", "concept")
    if getattr(args, "draft", None):
        if args.api:
            die("--draft and --api are mutually exclusive")
        if len(records) != 1:
            die("--draft requires exactly one selected texture (use --only)")
        draft_path = args.draft.resolve()
        draft_image = load_image(draft_path)
        draft_dimensions = list(draft_image.size)
    # Partial runs (``--only``/``--max-items`` or a one-off imagegen draft)
    # merge into an existing manifest instead of deleting the records that
    # were generated earlier.  This makes the workflow resumable without a
    # second bespoke JSON-editing script.
    previous_manifest: Optional[Dict[str, Any]] = None
    manifest_path = args.manifest.resolve()
    if (args.only or args.max_items or draft_image is not None) and manifest_path.is_file():
        try:
            candidate = json.loads(manifest_path.read_text(encoding="utf-8"))
            if isinstance(candidate, dict) and isinstance(candidate.get("skins"), list):
                previous_manifest = candidate
        except Exception:
            previous_manifest = None
    if draft_image is not None:
        workflow = "imagegen-3d-concept-to-uv" if draft_kind == "concept" else "imagegen-atlas-to-uv"
    elif args.api and getattr(args, "api_output", "concept") == "concept":
        workflow = "3d-concept-to-uv"
    elif args.api:
        workflow = "hosted-atlas-sanitizer"
    else:
        workflow = "offline-normalize-and-fallback"
    manifest: Dict[str, Any] = {
        "schema": 1,
        "generator": "CatfishW/ai_npc_skins",
        "author": "CatfishW",
        "canvas": [64, 64],
        "model": "classic",
        "workflow": workflow,
        "custom_templates_scanned": scanned_count,
        "unique_texture_ids": len(records),
        "api": {
            "enabled": bool(args.api),
            "base_url": public_base_url(os.environ.get("SUBTOKEN_BASE_URL", "https://subtoken.shop/v1")) if args.api else None,
            "model": args.api_model if args.api else None,
            "output": getattr(args, "api_output", None) if args.api else None,
        },
        "draft": {
            "path": display_path(args.draft.resolve()),
            "kind": draft_kind,
            "dimensions": draft_dimensions,
        } if draft_image is not None else None,
        "skins": [],
    }
    failures = 0
    preview_paths: List[Path] = []
    for index, record in enumerate(records, 1):
        output = skins_root / record["model_dir"] / (record["texture_id"] + ".png")
        existing = find_asset(skins_root, record)
        source_kind = "fallback"
        error: Optional[str] = None
        image = None
        # A supplied draft is an explicit override for the selected record.
        # Concepts go through the deterministic palette decoder; atlas drafts
        # go through the strict UV sanitizer.
        if draft_image is not None:
            if draft_kind == "atlas":
                image = clean_api_draft(draft_image, record)
                source_kind = "imagegen-atlas"
            else:
                image = concept_to_atlas(draft_image, record)
                source_kind = "imagegen-3d-concept"
        # Existing assets are retained/normalised unless explicitly
        # regenerated; legacy 64x32 files go through the lossless limb-copy
        # converter instead of being discarded as generic fallbacks.
        elif existing and not args.regenerate_all:
            try:
                image, source_kind, error = normalize_existing(existing, record["name"], record["description"], record["texture_id"], record["entity_type"])
            except Exception as exc:
                image, source_kind, error = generate_pixel_skin(record["name"], record["description"], record["texture_id"], record["entity_type"]), "fallback", str(exc)
        elif args.api:
            try:
                hosted_draft = api_image(api_prompt(record, getattr(args, "api_output", "concept")), args.api_model, args.retries)
                if getattr(args, "api_output", "concept") == "atlas":
                    image = clean_api_draft(hosted_draft, record)
                    source_kind = "subtoken-atlas"
                else:
                    image = concept_to_atlas(hosted_draft, record)
                    source_kind = "subtoken-3d-concept"
            except Exception as exc:
                # API/provider diagnostics can echo request details.  Keep the
                # manifest useful while ensuring credentials never get
                # persisted even if an unexpected exception bypasses
                # ``api_image``'s normal sanitisation path.
                error = safe_api_error(exc)
                image = generate_pixel_skin(record["name"], record["description"], record["texture_id"], record["entity_type"])
                source_kind = "fallback-after-api-error"
                failures += 1
        else:
            image = generate_pixel_skin(record["name"], record["description"], record["texture_id"], record["entity_type"])
            source_kind = "fallback-generated" if not existing else "fallback-replaced-invalid"
        image = sanitize_atlas(image)
        hashes = write_png(image, output)
        check = validate_image(output)
        if not check["valid"]:
            failures += 1
            error = "; ".join(check["errors"])
        preview = args.preview_dir.resolve() / record["model_dir"] / (record["texture_id"] + ".png")
        preview_image = render_3d(image, record["name"])
        preview.parent.mkdir(parents=True, exist_ok=True)
        preview_image.save(preview, "PNG", optimize=False)
        preview_paths.append(preview)
        manifest["skins"].append(
            {
                "texture_id": record["texture_id"],
                "model_dir": record["model_dir"],
                "entity_type": record["entity_type"],
                "name": record["name"],
                "templates": sorted(record["templates"]),
                "file": display_path(output),
                "preview3d": display_path(preview),
                "source": source_kind,
                "error": error,
                "sha1": hashes["sha1"],
                "sha256": hashes["sha256"],
            }
        )
        print("[%3d/%3d] %-34s %s" % (index, len(records), record["texture_id"], source_kind))
    if previous_manifest is not None:
        selected_keys = {(item.get("model_dir"), item.get("texture_id")) for item in manifest["skins"]}
        retained = [
            item for item in previous_manifest.get("skins", [])
            if isinstance(item, dict) and (item.get("model_dir"), item.get("texture_id")) not in selected_keys
        ]
        manifest["skins"] = sorted(retained + manifest["skins"], key=lambda item: (str(item.get("model_dir", "")), str(item.get("texture_id", ""))))
        manifest["custom_templates_scanned"] = previous_manifest.get("custom_templates_scanned", manifest["custom_templates_scanned"])
        manifest["unique_texture_ids"] = len(manifest["skins"])
    source_counts: Dict[str, int] = {}
    for item in manifest["skins"]:
        source_name = str(item.get("source", "unknown"))
        source_counts[source_name] = source_counts.get(source_name, 0) + 1
    manifest["source_counts"] = dict(sorted(source_counts.items()))
    manifest["generated_at_utc"] = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
    manifest_path.parent.mkdir(parents=True, exist_ok=True)
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    if preview_paths:
        make_contact_sheet(preview_paths, args.preview_dir.resolve() / "contact-sheet.png")
    print(json.dumps({"skins": len(manifest["skins"]), "failures": failures, "manifest": display_path(args.manifest.resolve()), "contact_sheet": display_path(args.preview_dir.resolve() / "contact-sheet.png")}, ensure_ascii=False))
    # API failures are recoverable because the fallback is valid.  A malformed
    # output, however, must fail CI/build verification.
    return 2 if failures and any(item["error"] and not item["source"].startswith("fallback") for item in manifest["skins"]) else 0


def check_all(args: argparse.Namespace) -> int:
    records = unique_records(discover_templates(args.templates.resolve()))
    errors: List[str] = []
    for record in records:
        path = find_asset(args.skins.resolve(), record)
        if not path:
            errors.append("%s -> missing %s" % (record["template"], record["texture_id"]))
            continue
        result = validate_image(path)
        if not result["valid"]:
            errors.append("%s: %s" % (path, "; ".join(result["errors"])))
    manifest_path = getattr(args, "manifest", None)
    if manifest_path:
        errors.extend(validate_manifest(manifest_path.resolve(), args.skins.resolve()))
    report = {"templates": len(discover_templates(args.templates.resolve())), "unique_texture_ids": len(records), "valid": not errors, "errors": errors}
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0 if not errors else 2


def preview_all(args: argparse.Namespace) -> int:
    records = unique_records(discover_templates(args.templates.resolve()))
    paths: List[Path] = []
    for record in records:
        path = find_asset(args.skins.resolve(), record)
        if not path:
            continue
        result = validate_image(path)
        if not result["valid"]:
            continue
        with pillow()[0].open(path) as opened:
            image = opened.convert("RGBA")
        output = args.output.resolve() / record["model_dir"] / (record["texture_id"] + ".png")
        output.parent.mkdir(parents=True, exist_ok=True)
        render_3d(image, record["name"]).save(output, "PNG", optimize=False)
        paths.append(output)
    make_contact_sheet(paths, args.output.resolve() / "contact-sheet.png")
    print(json.dumps({"previews": len(paths), "contact_sheet": display_path(args.output.resolve() / "contact-sheet.png")}, ensure_ascii=False))
    return 0


def guide_command(args: argparse.Namespace) -> int:
    make_uv_guide(args.output.resolve(), args.scale)
    return 0


def regenerate_automodpack(args: argparse.Namespace) -> int:
    """Add exact skin entries to an AutoModpack content manifest.

    AutoModpack normally discovers files at startup.  Keeping this command in
    the repository also makes a manual/live repair reproducible and lets CI
    assert that every client receives the same hashes.
    """
    content_path = args.content.resolve()
    skins_root = args.skins.resolve()
    try:
        content = json.loads(content_path.read_text(encoding="utf-8"))
    except Exception as exc:
        die("cannot read AutoModpack content manifest: %s" % exc)
    old_list = content.get("list")
    if not isinstance(old_list, list):
        die("AutoModpack manifest has no list: %s" % content_path)
    kept = [item for item in old_list if not (isinstance(item, dict) and str(item.get("file", "")).startswith("/config/easy_npc/skin/"))]
    skin_items: List[Dict[str, Any]] = []
    for path in sorted(skins_root.rglob("*.png")):
        # Previews are intentionally kept beside the source atlases, but they
        # are not Easy NPC textures and must never be advertised to clients.
        try:
            skin_relative = path.relative_to(skins_root)
        except ValueError:
            continue
        if not skin_relative.parts or skin_relative.parts[0] not in ("humanoid", "humanoid_slim", "zombie"):
            continue
        # ``rglob`` also sees preview/debug images nested below a model
        # directory.  Easy NPC resolves only direct ``<model>/<id>.png``
        # files, so nested paths must never be advertised to clients.
        if len(skin_relative.parts) != 2:
            continue
        if not validate_image(path)["valid"]:
            continue
        rel = "/" + str(path.relative_to(ROOT)).replace(os.sep, "/") if path.is_relative_to(ROOT) else "/config/easy_npc/skin/" + str(skin_relative).replace(os.sep, "/")
        if not rel.startswith("/config/easy_npc/skin/"):
            rel = "/config/easy_npc/skin/" + str(skin_relative).replace(os.sep, "/")
        hashes = file_hashes(path)
        skin_items.append(
            {
                "file": rel,
                "size": str(hashes["size"]),
                "type": "config",
                "editable": False,
                "forceCopy": True,
                "sha1": hashes["sha1"],
            }
        )
    content["list"] = kept + skin_items
    content_path.write_text(json.dumps(content, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"manifest": str(content_path), "removed_skin_entries": len(old_list) - len(kept), "added_skin_entries": len(skin_items), "total_entries": len(content["list"])}, ensure_ascii=False))
    return 0


def doctor(_: argparse.Namespace) -> int:
    try:
        pillow()
        pillow_ok = True
    except SystemExit:
        pillow_ok = False
    print(json.dumps({
        "python": sys.version.split()[0],
        "pillow": pillow_ok,
        "subtoken_base_url": public_base_url(os.environ.get("SUBTOKEN_BASE_URL", "https://subtoken.shop/v1")),
        "subtoken_key_present": bool(os.environ.get("SUBTOKEN_API_KEY")),
        "secret_written_by_tool": False,
    }, ensure_ascii=False, indent=2))
    return 0 if pillow_ok else 2


def list_command(args: argparse.Namespace) -> int:
    records = discover_templates(args.templates.resolve())
    unique = unique_records(records)
    by_model: Dict[str, int] = {}
    for record in unique:
        by_model[record["model_dir"]] = by_model.get(record["model_dir"], 0) + 1
    print(json.dumps({"custom_template_references": len(records), "unique_texture_ids": len(unique), "by_model": by_model}, ensure_ascii=False, indent=2))
    return 0


def parser() -> argparse.ArgumentParser:
    root = argparse.ArgumentParser(description=__doc__)
    root.add_argument("--templates", type=Path, default=DEFAULT_TEMPLATES)
    root.add_argument("--skins", type=Path, default=DEFAULT_SKINS)
    commands = root.add_subparsers(dest="command", required=True)
    commands.add_parser("doctor").set_defaults(func=doctor)
    commands.add_parser("list").set_defaults(func=list_command)
    check = commands.add_parser("check", help="validate every custom texture referenced by templates")
    check.add_argument("--manifest", type=Path, help="also verify the generated manifest's hashes")
    check.set_defaults(func=check_all)
    guide = commands.add_parser("guide", help="write a labeled 64x64 UV guide for an imagegen edit")
    guide.add_argument("--output", type=Path, default=ROOT / "artifacts" / "npc-skin-uv-guide.png")
    guide.add_argument("--scale", type=int, default=12)
    guide.set_defaults(func=guide_command)
    generate_parser = commands.add_parser("generate", help="repair or generate every unique texture id")
    generate_parser.add_argument("--api", action="store_true", help="use SUBTOKEN_API_KEY for creative drafts")
    generate_parser.add_argument("--api-model", default=os.environ.get("SUBTOKEN_IMAGE_MODEL", "grok-imagine-image"))
    generate_parser.add_argument("--api-output", choices=("concept", "atlas"), default="concept", help="interpret hosted output as a 3D concept (recommended) or a UV atlas")
    generate_parser.add_argument("--retries", type=int, default=2)
    generate_parser.add_argument("--regenerate-all", action="store_true", help="regenerate even valid existing files")
    generate_parser.add_argument("--only", action="append", help="texture id or name; may be repeated")
    generate_parser.add_argument("--max-items", type=int, default=0)
    generate_parser.add_argument("--name", help="generate an arbitrary NPC instead of scanning templates")
    generate_parser.add_argument("--texture-id", help="case-sensitive output id for an arbitrary NPC")
    generate_parser.add_argument("--description", default="", help="role/context used by the palette prompt")
    generate_parser.add_argument("--entity-type", default="easy_npc:humanoid", help="entity id; zombie selects the zombie skin folder")
    generate_parser.add_argument("--draft", type=Path, help="use one local image draft for exactly one --only texture")
    generate_parser.add_argument("--draft-kind", choices=("concept", "atlas"), default="concept", help="interpret --draft as a 3D concept or a UV atlas")
    generate_parser.add_argument("--preview-dir", type=Path, default=DEFAULT_PREVIEWS)
    generate_parser.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST)
    generate_parser.set_defaults(func=generate)
    preview = commands.add_parser("preview3d", help="render exact 3D previews from existing atlases")
    preview.add_argument("--output", type=Path, default=DEFAULT_PREVIEWS)
    preview.set_defaults(func=preview_all)
    auto = commands.add_parser("automodpack", help="refresh skin entries in an AutoModpack content manifest")
    auto.add_argument("--content", type=Path, required=True)
    auto.set_defaults(func=regenerate_automodpack)
    return root


def main() -> int:
    args = parser().parse_args()
    return args.func(args)


if __name__ == "__main__":
    raise SystemExit(main())
