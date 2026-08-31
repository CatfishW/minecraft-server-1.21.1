"""Small offline regression tests for the Easy NPC skin pipeline."""

from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from PIL import Image

try:
    from tools import ai_npc_skins as skins
except ModuleNotFoundError:  # direct ``python tools/test_ai_npc_skins.py``
    import ai_npc_skins as skins


class SkinPipelineTests(unittest.TestCase):
    def test_generated_skin_is_a_valid_java_atlas(self) -> None:
        image = skins.generate_pixel_skin("Captain", "guard", "Captain_test")
        self.assertEqual(image.size, (64, 64))
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "captain.png"
            skins.write_png(image, path)
            result = skins.validate_image(path)
            self.assertTrue(result["valid"], result)

    def test_legacy_atlas_maps_side_faces_to_left_limb(self) -> None:
        # Give each source face a distinct color so the side-face permutation
        # can be asserted without depending on any particular skin art.
        source = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
        colors = [(i * 35, 20, 200 - i * 20, 255) for i in range(6)]
        for index, rect in enumerate(skins.FACE_GROUPS["right_arm"]):
            skins.rect_fill(source, rect, colors[index])
        target = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
        skins.copy_legacy_net(source, target, "right_arm", "left_arm")
        target_rects = skins.FACE_GROUPS["left_arm"]
        for source_index, target_index in enumerate((0, 1, 4, 3, 2, 5)):
            pixel = target.getpixel((target_rects[target_index][0], target_rects[target_index][1]))
            self.assertEqual(pixel, colors[source_index])

    def test_recursive_discovery_and_unique_ids(self) -> None:
        records = skins.discover_templates(skins.DEFAULT_TEMPLATES)
        unique = skins.unique_records(records)
        self.assertEqual(len(records), 159)
        self.assertEqual(len(unique), 112)
        self.assertEqual(sum(item["model_dir"] == "zombie" for item in unique), 1)

    def test_concept_palette_and_3d_render(self) -> None:
        image = Image.new("RGBA", (128, 128), (24, 40, 130, 255))
        for x in range(32, 96):
            for y in range(16, 112):
                image.putpixel((x, y), (210, 130, 80, 255))
        fallback = skins.palette_for("NPC", "", 5)
        palette = skins.extract_concept_palette(image, 5, fallback)
        atlas = skins.generate_pixel_skin("NPC", "", "concept_test", palette_override=palette)
        preview = skins.render_3d(atlas, "NPC")
        self.assertEqual(preview.size, (640, 720))
        self.assertEqual(atlas.size, (64, 64))

    def test_automodpack_excludes_previews(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            skins_root = root / "config" / "easy_npc" / "skin"
            (skins_root / "humanoid").mkdir(parents=True)
            (skins_root / "previews3d").mkdir(parents=True)
            (skins_root / "humanoid" / "nested").mkdir(parents=True)
            atlas = skins.generate_pixel_skin("NPC", "", "manifest_test")
            skins.write_png(atlas, skins_root / "humanoid" / "manifest_test.png")
            skins.write_png(atlas, skins_root / "previews3d" / "manifest_test.png")
            skins.write_png(atlas, skins_root / "humanoid" / "nested" / "hidden.png")
            content_path = root / "content.json"
            content_path.write_text(json.dumps({"list": [{"file": "/config/easy_npc/skin/old.png"}, {"file": "/mods/example.jar"}]}), encoding="utf-8")
            args = type("Args", (), {"content": content_path, "skins": skins_root})()
            self.assertEqual(skins.regenerate_automodpack(args), 0)
            content = json.loads(content_path.read_text(encoding="utf-8"))
            files = [item["file"] for item in content["list"]]
            self.assertIn("/mods/example.jar", files)
            self.assertIn("/config/easy_npc/skin/humanoid/manifest_test.png", files)
            self.assertNotIn("/config/easy_npc/skin/previews3d/manifest_test.png", files)
            self.assertNotIn("/config/easy_npc/skin/humanoid/nested/hidden.png", files)


if __name__ == "__main__":
    unittest.main()
