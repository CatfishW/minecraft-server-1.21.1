#!/usr/bin/env bash
set -euo pipefail

repo_root="$(git rev-parse --show-toplevel)"
target_rel="mods-dev/BOs-Easy-NPC-1.21.1"
target="$repo_root/$target_rel"
workspace="${RUNNER_TEMP:-/tmp}/easy-npc-neoforge-port"
upstream="$workspace/upstream"
candidate="$workspace/candidate"
backup="$workspace/original"
status="$workspace/status"

rm -rf "$workspace"
mkdir -p "$workspace" "$status"

if [[ -d "$target" ]]; then
  rsync -a "$target/" "$backup/"
else
  mkdir -p "$backup"
fi

git clone --filter=blob:none --no-checkout \
  https://github.com/MarkusBordihn/BOs-Easy-NPC.git "$upstream"
git -C "$upstream" fetch --tags --prune --quiet

# Select the newest upstream ref whose checked-in metadata explicitly targets
# Minecraft 1.21.1 and whose tree contains a NeoForge module. This avoids using
# a moving default branch that may target another Minecraft release.
: > "$status/candidates.tsv"
while IFS= read -r ref; do
  [[ -n "$ref" ]] || continue
  mapfile -t metadata_files < <(
    git -C "$upstream" ls-tree -r --name-only "$ref" 2>/dev/null \
      | grep -E '(^|/)(gradle\.properties|settings\.gradle(\.kts)?|neoforge\.mods\.toml|mods\.toml)$' \
      | head -40 || true
  )
  ((${#metadata_files[@]})) || continue

  metadata=''
  for file in "${metadata_files[@]}"; do
    metadata+="$(git -C "$upstream" show "$ref:$file" 2>/dev/null || true)"
    metadata+=$'\n'
  done

  grep -Eqi '(^|[^0-9])1[.]21[.]1([^0-9]|$)' <<<"$metadata" || continue
  tree="$(git -C "$upstream" ls-tree -r --name-only "$ref" 2>/dev/null || true)"
  grep -Eqi '(^|/)(NeoForge|neoforge)(/|$)' <<<"$tree" || continue
  timestamp="$(git -C "$upstream" log -1 --format=%ct "$ref")"
  printf '%s\t%s\n' "$timestamp" "$ref" >> "$status/candidates.tsv"
done < <(
  git -C "$upstream" for-each-ref --format='%(refname)' refs/tags refs/remotes/origin \
    | grep -v '/HEAD$'
)

[[ -s "$status/candidates.tsv" ]] || {
  echo 'No upstream ref with explicit Minecraft 1.21.1 + NeoForge metadata was found.' >&2
  exit 20
}

selected_ref="$(sort -t$'\t' -k1,1nr "$status/candidates.tsv" | head -1 | cut -f2)"
git -C "$upstream" checkout --force "$selected_ref"
git -C "$upstream" submodule update --init --recursive
selected_sha="$(git -C "$upstream" rev-parse HEAD)"

mkdir -p "$candidate"
rsync -a --delete \
  --exclude='.git' --exclude='.gradle' --exclude='build' --exclude='run' \
  "$upstream/" "$candidate/"
chmod +x "$candidate/gradlew"

# Preserve only local resources that do not exist in the selected upstream
# source. Updated upstream metadata/code always wins over stale loader files.
: > "$status/preserved-resources.txt"
if [[ -d "$backup" ]]; then
  while IFS= read -r -d '' source_file; do
    rel="${source_file#"$backup/"}"
    case "$rel" in
      *src/main/resources/assets/*|*src/main/resources/data/*)
        if [[ ! -e "$candidate/$rel" ]]; then
          mkdir -p "$(dirname "$candidate/$rel")"
          cp -a "$source_file" "$candidate/$rel"
          printf '%s\n' "$rel" >> "$status/preserved-resources.txt"
        fi
        ;;
    esac
  done < <(find "$backup" -type f -print0)
fi

find "$candidate" -type d \
  \( -name build -o -name .gradle -o -name run -o -name .idea -o -name out \) \
  -prune -exec rm -rf {} + || true
find "$candidate" -type f \
  \( -name '*.iml' -o -name '*.class' -o -name '*.log' \) -delete || true

mkdir -p "$candidate/docs/architecture" "$candidate/scripts"
cat > "$candidate/AGENTS.md" <<'DOC'
# Agent guide — BO's Easy NPC (Minecraft 1.21.1 / NeoForge)

## Read order

1. `docs/architecture/OVERVIEW.md`
2. `settings.gradle` or `settings.gradle.kts`
3. Shared/common entry point and registries
4. NeoForge entry point and platform adapters
5. The feature package, tests, and resources being changed

## Dependency boundaries

- Shared/common code owns NPC behavior, state, serialization contracts, and loader-neutral services.
- NeoForge code owns event-bus registration, payload/network bootstrap, platform hooks, and run configuration.
- The dependency direction is one-way: NeoForge adapters may depend on common code; common code must not depend on NeoForge implementation packages.
- Keep client rendering and client registration under client-only packages/classes.
- Resource and network identifiers are stable API and must not be renamed casually.
- Prefer a narrow common interface plus one NeoForge implementation over loader checks scattered through feature code.

## Server authority

Never trust client-provided entity IDs, UUIDs, distance, ownership, permission, inventory, menu identity, indexes, or numeric bounds. Validate on the server thread before changing an NPC, player, inventory, level, or saved data.

## Verification

- `./scripts/build-neoforge.sh` performs the deterministic clean check/build gate.
- `./scripts/verify-neoforge.sh` additionally runs a GameTest task when one is exposed.
DOC

cat > "$candidate/docs/architecture/OVERVIEW.md" <<'DOC'
# Architecture overview

The port uses a loader-boundary architecture.

- **Common/shared layer:** NPC domain behavior, commands, dialogs, data formats, loader-neutral registries/services, and reusable utilities.
- **NeoForge adapter layer:** mod bootstrap, NeoForge events, payload/network registration, platform services, client setup, and development run configurations.
- **Resources:** `assets/` contains client-visible models, textures, sounds, and language files; `data/` contains server data such as tags and recipes.

NeoForge adapters may depend on common code. Common code must not import NeoForge implementation packages. Cross-feature collaboration should use a small service/interface or immutable request object instead of accessing another feature's mutable internals.

## Placement rules

- New NPC behavior belongs with the domain feature in common code.
- Loader events and API bridges belong in the NeoForge module.
- Packet validation and state transitions belong on the server; rendering responses belong on the client.
- Register content through the owning registry/bootstrap class instead of unrelated static initializers.

## Runtime safety checklist

Before any network-driven mutation, validate sender, loaded level, target type, target UUID/ID, distance, ownership/permission, menu/container identity, index ranges, text lengths, and numeric bounds. Schedule world mutations on the server thread.
DOC

cat > "$candidate/docs/NEOFORGE_1_21_1_PORT.md" <<'DOC'
# NeoForge 1.21.1 port

This tree is generated from an upstream BO's Easy NPC revision whose checked-in metadata explicitly targets Minecraft 1.21.1 and contains the NeoForge module. The exact ref and commit are recorded in `UPSTREAM_REVISION.txt`.

The conversion keeps shared gameplay logic separated from loader bootstrap code, removes generated workspace state, carries forward local-only resources without replacing upstream versioned resources, and adds deterministic build/navigation documentation for human and automated maintainers.
DOC

cat > "$candidate/UPSTREAM_REVISION.txt" <<DOC
Repository: https://github.com/MarkusBordihn/BOs-Easy-NPC
Selected ref: $selected_ref
Selected commit: $selected_sha
Target: Minecraft 1.21.1 / NeoForge
DOC

cat > "$candidate/.editorconfig" <<'DOC'
root = true

[*]
charset = utf-8
end_of_line = lf
insert_final_newline = true
trim_trailing_whitespace = true

[*.{java,kt,kts,gradle}]
indent_style = space
indent_size = 2

[*.{json,json5,mcmeta,toml,yml,yaml,md}]
indent_style = space
indent_size = 2

[*.md]
trim_trailing_whitespace = false
DOC

cat > "$candidate/scripts/build-neoforge.sh" <<'SCRIPT'
#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
chmod +x ./gradlew
projects_log="$(mktemp)"
trap 'rm -f "$projects_log"' EXIT
./gradlew projects --console=plain --no-daemon > "$projects_log"
neo_project="$(
  grep -Eo "Project ':[^']+'" "$projects_log" \
    | sed -E "s/Project '(:[^']+)'/\1/" \
    | grep -Ei 'neo.?forge' \
    | head -1 || true
)"
if [[ -n "$neo_project" ]]; then
  ./gradlew clean "${neo_project}:check" "${neo_project}:build" \
    --console=plain --no-daemon --stacktrace
else
  ./gradlew clean check build --console=plain --no-daemon --stacktrace
fi
SCRIPT

cat > "$candidate/scripts/verify-neoforge.sh" <<'SCRIPT'
#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
./scripts/build-neoforge.sh

tasks_log="$(mktemp)"
trap 'rm -f "$tasks_log"' EXIT
./gradlew tasks --all --console=plain --no-daemon > "$tasks_log"
gametest_task="$(awk '{print $1}' "$tasks_log" | grep -Ei '(^|:)runGameTestServer$' | head -1 || true)"
if [[ -n "$gametest_task" ]]; then
  timeout 20m ./gradlew "$gametest_task" --console=plain --no-daemon --stacktrace
else
  echo 'No runGameTestServer task is exposed by this Gradle configuration.'
fi
SCRIPT

cat > "$candidate/scripts/clean-worktree.sh" <<'SCRIPT'
#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
find . -type d \( -name build -o -name .gradle -o -name run -o -name out \) \
  -prune -exec rm -rf {} +
find . -type f \( -name '*.class' -o -name '*.log' \) -delete
SCRIPT

chmod +x "$candidate/scripts/"*.sh

if [[ -s "$status/preserved-resources.txt" ]]; then
  {
    echo '# Preserved local resources'
    echo
    echo 'These local-only resources were carried into the NeoForge tree:'
    echo
    sed 's/^/- `/' "$status/preserved-resources.txt" | sed 's/$/`/'
  } > "$candidate/docs/PRESERVED_LOCAL_RESOURCES.md"
fi

# Validate the selected source before it can replace the repository copy.
cd "$candidate"
./gradlew projects --console=plain --no-daemon > "$status/projects.log"
neo_project="$(
  grep -Eo "Project ':[^']+'" "$status/projects.log" \
    | sed -E "s/Project '(:[^']+)'/\1/" \
    | grep -Ei 'neo.?forge' \
    | head -1 || true
)"
if [[ -n "$neo_project" ]]; then
  build_task="${neo_project}:build"
  check_task="${neo_project}:check"
else
  build_task='build'
  check_task='check'
fi

./gradlew clean "$check_task" "$build_task" \
  --console=plain --no-daemon --stacktrace | tee "$status/build.log"

./gradlew tasks --all --console=plain --no-daemon > "$status/tasks.log"
gametest_task="$(awk '{print $1}' "$status/tasks.log" | grep -Ei '(^|:)runGameTestServer$' | head -1 || true)"
gametest_result='NOT_EXPOSED'
if [[ -n "$gametest_task" ]]; then
  mkdir -p run NeoForge/run neoforge/run
  printf 'eula=true\n' > run/eula.txt
  printf 'eula=true\n' > NeoForge/run/eula.txt
  printf 'eula=true\n' > neoforge/run/eula.txt
  if timeout 20m ./gradlew "$gametest_task" --console=plain --no-daemon --stacktrace \
      | tee "$status/gametest.log"; then
    gametest_result='PASSED'
  else
    gametest_result='FAILED'
  fi
fi

legacy_imports="$(
  find . -path '*/src/*' -type f -name '*.java' -print0 \
    | xargs -0 grep -h 'net\.minecraftforge\.' 2>/dev/null \
    | wc -l | tr -d ' '
)"
find . -type f -path '*/build/libs/*.jar' -printf '%p\t%s bytes\n' \
  | sort > "$status/jars.txt"

cat > VERIFICATION.md <<DOC
# Verification report

| Gate | Result | Task |
|---|---|---|
| Clean Java/resource/remap/JAR build | **PASSED** | \`./gradlew $build_task\` |
| Gradle checks | **PASSED** | \`./gradlew $check_task\` |
| NeoForge GameTest server | **$gametest_result** | \`${gametest_task:-not exposed}\` |
| Legacy \`net.minecraftforge.*\` imports under source roots | **$legacy_imports found** | static scan |

Build success validates compilation, resource processing, remapping, and JAR assembly from clean outputs. GameTest is reported separately so package validation is not misrepresented as interactive client testing.

## Produced JARs

\`\`\`
$(cat "$status/jars.txt")
\`\`\`
DOC

./scripts/clean-worktree.sh || true
rm -rf run NeoForge/run neoforge/run || true

cd "$repo_root"
rm -rf "$target"
mkdir -p "$target"
rsync -a --delete "$candidate/" "$target/"
chmod +x "$target/gradlew" "$target/scripts/"*.sh

git add -A -- "$target_rel"
if git diff --cached --quiet; then
  echo 'Selected upstream source already matches the repository tree.'
  exit 0
fi

git config user.name 'github-actions[bot]'
git config user.email '41898282+github-actions[bot]@users.noreply.github.com'
git commit -m "Port BO's Easy NPC to NeoForge 1.21.1 [generated-port]"
git push origin "HEAD:${GITHUB_REF_NAME}"
