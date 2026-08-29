#!/usr/bin/env bash
set -euo pipefail

source_script="tools/port-easy-npc-neoforge-1.21.1.sh"
patched_script="${RUNNER_TEMP:-/tmp}/port-easy-npc-neoforge-1.21.1-patched.sh"

python - "$source_script" "$patched_script" <<'PY'
from pathlib import Path
import re
import sys

source = Path(sys.argv[1]).read_text()
pattern = re.compile(
    r'''legacy_imports="\$\(\n.*?\n\)"\nfind \. -type f -path '\*/build/libs/\*\.jar' ''',
    re.DOTALL,
)
replacement = '''legacy_imports="$(python - <<'PYCOUNT'
from pathlib import Path
count = 0
for source_root in Path('.').rglob('src'):
    if not source_root.is_dir():
        continue
    for java_file in source_root.rglob('*.java'):
        try:
            count += java_file.read_text(errors='ignore').count('net.minecraftforge.')
        except OSError:
            pass
print(count)
PYCOUNT
)"
find . -type f -path '*/build/libs/*.jar' '''
patched, count = pattern.subn(replacement, source, count=1)
if count != 1:
    raise SystemExit('Unable to patch the legacy-import scan in the generator.')
Path(sys.argv[2]).write_text(patched)
PY

chmod +x "$patched_script"
exec "$patched_script"
