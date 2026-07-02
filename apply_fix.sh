#!/usr/bin/env bash
set -e

HERE="$(pwd)"
ROOT="$HERE"

# Allow running from AppProjects/ or from the Jarvis project root.
if [ -d "$HERE/Jarvis" ] && [ ! -f "$HERE/settings.gradle" ]; then
  ROOT="$HERE/Jarvis"
fi

if [ ! -f "$ROOT/settings.gradle" ]; then
  echo "ERROR: Run this from the Jarvis project root or the folder containing Jarvis/." >&2
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$ROOT/.github/workflows"
cp -f "$SCRIPT_DIR/patch_files/.github/workflows/android-release.yml" "$ROOT/.github/workflows/android-release.yml"

cat > "$ROOT/RELEASE_WORKFLOW_NOTES.txt" <<'NOTES'
Jarvis GitHub Release workflow added.

New file:
.github/workflows/android-release.yml

How to use it:
1. Push this patch to GitHub.
2. Open the GitHub repository in a browser.
3. Go to Actions > Android Release.
4. Press Run workflow.
5. Enter a version such as v1.0.0.
6. Wait for the build to finish.
7. The APK will appear under the repository Releases page.

You can also create a release by pushing a tag like v1.0.1.

The release workflow builds the same debug APK, uploads it as a workflow artifact,
and publishes it as a GitHub Release asset.
NOTES

echo "Applied Jarvis GitHub Release workflow patch."
echo "Added: .github/workflows/android-release.yml"
echo "Added: RELEASE_WORKFLOW_NOTES.txt"
