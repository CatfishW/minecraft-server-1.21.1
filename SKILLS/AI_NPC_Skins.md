# CatfishW AI NPC skin pipeline

This repository contains a reproducible skin pipeline for the Easy NPC
NeoForge 1.21.1 pack. It treats a skin as a Java Edition UV texture, then
renders that texture on a block model for review. The checked-in files are
safe to distribute: no API key, source URL, or raw hosted response is stored.

## What is generated

`config/easy_npc/npc_templates` is scanned recursively, including `temp/`.
The current set has 159 `CUSTOM` references which resolve to 112 unique
texture IDs (111 humanoid atlases and one zombie atlas). Each ID is kept
case-sensitive and written to:

```
config/easy_npc/skin/<model>/<textureId>.png
```

Every promoted atlas is an RGBA 64x64 PNG. Existing valid skins are
normalised; legacy 64x32 files are expanded to the modern canvas; unsupported
images are replaced with a deterministic pixel-art skin. This avoids treating
a portrait or an arbitrary screenshot as a texture sheet.

`artifacts/npc-skin-previews3d/` contains an isometric three-quarter render for
every atlas and a contact sheet. The preview is generated from the same UV
rectangles used by the validator, so a swapped face, detached limb, or
transparent base face is visible before deployment.

## Offline/reproducible run

From the repository root:

```bash
python3 -m pip install --user Pillow
python3 tools/ai_npc_skins.py doctor
python3 tools/ai_npc_skins.py list
python3 tools/ai_npc_skins.py generate
python3 tools/ai_npc_skins.py check
```

`generate` is resumable in practice: it keeps valid existing atlases and only
repairs legacy/invalid files. Use `--regenerate-all` when a complete new
palette is wanted. To render previews without changing skins:

```bash
python3 tools/ai_npc_skins.py preview3d
```

The same command can create a new, template-independent NPC. The id is kept
case-sensitive because it becomes the Easy NPC filename/UUID input:

```bash
python3 tools/ai_npc_skins.py generate \
  --name "Harbor Warden" \
  --texture-id Harbor_Warden_demo \
  --description "navy coat, brass trim, lantern keeper" \
  --regenerate-all
```

To create a labeled guide for an image-generation edit:

```bash
python3 tools/ai_npc_skins.py guide
```

## Optional AI drafts through Subtoken

The tool uses the OpenAI-compatible image endpoint only when `--api` is
passed. Put the credential in the shell environment, never in a file tracked
by Git:

```bash
export SUBTOKEN_API_KEY='(set this in your local shell)'
export SUBTOKEN_BASE_URL='https://subtoken.shop/v1'
export SUBTOKEN_IMAGE_MODEL='grok-imagine-image'
python3 tools/ai_npc_skins.py generate --api --regenerate-all
```

The endpoint, model, retries, and output promotion are configurable. HTTP 429
and transient 5xx responses use bounded backoff. If a request fails, the
command records a short error in `ai-skin-manifest.json` and falls back to a
valid local atlas; it never prints the bearer token. Hosted results are
treated as drafts and still pass through the 64x64 UV sanitizer and 3D
preview. The recommended `concept` output is a three-quarter 3D character
reference: the tool samples broad colors from it and deterministically paints
the Java UV rectangles. This avoids putting perspective/background pixels in
arbitrary atlas faces. Use `--api-output atlas` only when the provider really
returns a flat Java UV sheet. Keep concurrency low and resume from the
manifest to control quota.

For a one-off local draft (including an ImageGen result), select exactly one
texture and choose how to interpret it:

```bash
python3 tools/ai_npc_skins.py generate \
  --only Captain_Ironclaw_1308dc9a \
  --draft /path/to/concept-or-atlas.png \
  --draft-kind concept       # or: atlas
```

Partial runs merge their item into the existing manifest, so other NPCs are
not lost.

## AutoModpack/client distribution

Easy NPC custom skins are client-side assets. The server and every client must
have identical files under `config/easy_npc/skin/<model>`. After copying the
atlases to a server, regenerate its AutoModpack content manifest:

```bash
python3 tools/ai_npc_skins.py automodpack \
  --content automodpack/host-modpack/automodpack-content.json
```

The command removes stale skin entries, adds one hash entry per actual atlas,
and leaves preview images out of the download list. The server's
`automodpack-server.json` must include `/config/easy_npc/skin/**` in both
`syncedFiles` and `forceCopyFilesToStandardLocation`. On AutoModpack versions
that support editable-file overrides, also list the skin glob in
`overwriteEditableFiles`. For older versions, exclude the skin tree from the
broad config edit rule so the generated entries remain authoritative:

```json
"allowEditsInFiles": [
  "/config/**",
  "!/config/easy_npc/skin/**",
  "/options.txt"
]
```

This prevents an old client cache from winning. Restart or run
`/automodpack generate`, then use Easy NPC's **Reload Custom Textures** action
or restart the client.

## Format references

The placement of the 64x64/64x32 Java faces follows the community Java skin
spec and the Easy NPC custom-skin layout. See the [Minecraft Java skin
spec](https://github.com/minotar/skin-spec) and the [Easy NPC skin
guide](https://github-wiki-see.page/m/MarkusBordihn/BOs-Easy-NPC/wiki/Skins).
The optional hosted workflow follows Subtoken's [OpenAI-compatible image
API](https://subtoken.shop/); credentials are intentionally environment only.
