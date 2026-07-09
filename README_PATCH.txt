Jarvis v1.6.4 image 404 patch

Fixes:
- Stops AI-prefixed image commands like "ask AI generate an image of..." from going through normal chat.
- Routes those commands directly into the native Jarvis image generation path.
- If chat returns an expired/generated image URL and the app gets HTTP 404, Jarvis now falls back to native generation instead of only showing/speaking the HTTP error.
- Keeps generated image display inside the Jarvis app with tap-to-save/file-picker behaviour.
- Version bumped to 1.6.4 / versionCode 64.

Apply:
1. Copy this zip to your Jarvis project root.
2. Unzip it there.
3. Run: bash apply_fix.sh
4. Rebuild in AIDE.
