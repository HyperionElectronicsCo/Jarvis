#!/data/data/com.termux/files/usr/bin/bash
set -e

if [ ! -f "settings.gradle" ] || [ ! -d "app" ]; then
  echo "ERROR: run this from the Jarvis project root."
  echo "Expected files: settings.gradle and app/"
  exit 1
fi

mkdir -p .github/workflows
if [ ! -f ".github/workflows/android-build.yml" ]; then
  echo "ERROR: patch file .github/workflows/android-build.yml was not found."
  echo "Unzip the patch with: unzip -o Jarvis_GHA_runner_temp_fix_patch.zip"
  exit 1
fi

chmod +x gradlew 2>/dev/null || true

echo "Patch applied: .github/workflows/android-build.yml"
echo "Now commit and push the workflow file."
