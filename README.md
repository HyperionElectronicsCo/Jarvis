# J.A.R.V.I.S Android Assistant

<p align="center">
  <img src="assets/jarvis-hud-preview.png" alt="J.A.R.V.I.S Android Assistant HUD preview" width="360" />
</p>

<p align="center">
  <b>A lightweight Android voice assistant with a cinematic J.A.R.V.I.S-style HUD, wake phrase control, app launching, phone navigation, media controls, search, maps, and background listening.</b>
</p>

<p align="center">
  <img alt="Android" src="https://img.shields.io/badge/platform-Android-brightgreen" />
  <img alt="Java" src="https://img.shields.io/badge/language-Java-orange" />
  <img alt="AIDE Compatible" src="https://img.shields.io/badge/AIDE-compatible-blue" />
  <img alt="No Lambdas" src="https://img.shields.io/badge/no-lambdas-success" />
  <img alt="AndroidX" src="https://img.shields.io/badge/AndroidX-not_required-lightgrey" />
</p>

---

## Overview

**J.A.R.V.I.S Android Assistant** is a native Android Java assistant designed to feel like a mobile version of a futuristic AI interface. It combines a live animated HUD with voice recognition, Text-To-Speech replies, background listening, app launching, phone controls, Google-style search, YouTube search/music commands, maps navigation, and Accessibility-powered navigation controls.

The project is designed to remain friendly for **AIDE/on-device Android development**:

- Java-only source.
- No lambda expressions.
- No AndroidX requirement.
- No external dependency-heavy setup.
- Programmatic UI/HUD to avoid fragile layout dependencies.

---

## Main Features

### Cinematic J.A.R.V.I.S HUD

- Animated futuristic interface.
- Rotating circular HUD rings.
- Scanning grid background.
- Moving particles and pulse effects.
- Terminal-style command console.
- System status messages.
- Background-service state indicator.
- Voice-core status display.

### Wake Phrase Voice Control

Jarvis can listen for wake phrases such as:

```text
okay Jarvis
ok Jarvis
hey Jarvis
Jarvis
```

Example:

```text
okay Jarvis open YouTube
okay Jarvis play drum and bass on YouTube
okay Jarvis navigate to Tesco
```

### Background Listening Service

- Runs as a foreground service with a persistent notification.
- Allows hands-free use while other apps are open.
- Supports wake phrase activation from the background.
- Includes notification actions for:
  - Pause
  - Resume
  - Stop
- Can restart after reboot if background mode was enabled.
- Will not restart after reboot if the user deliberately paused listening.

### Temporary Listening Pause

To save battery and stop microphone monitoring, say:

```text
okay Jarvis stop listening
okay Jarvis pause listening
okay Jarvis stand down
```

Jarvis pauses microphone monitoring without fully removing the foreground service.

Resume from:

- The Jarvis notification.
- The in-app background button.

### App Launching

Jarvis can open installed apps by name.

Examples:

```text
okay Jarvis open YouTube
okay Jarvis open Chrome
okay Jarvis open WhatsApp
okay Jarvis open Gmail
okay Jarvis open Play Store
okay Jarvis open Maps
okay Jarvis open Camera
okay Jarvis open Calculator
okay Jarvis open Settings
```

It also includes support for common app-name aliases and generic installed-app lookup.

### YouTube Music and Search Commands

Jarvis recognises YouTube commands beyond simply opening the app.

Examples:

```text
okay Jarvis play music on YouTube
okay Jarvis play drum and bass on YouTube
okay Jarvis open YouTube for drum and bass
okay Jarvis search YouTube for 90s hip hop
okay Jarvis YouTube relaxing synthwave
okay Jarvis play music by Adele on YouTube
```

Jarvis opens YouTube directly to the requested search/music query.

### Google-Style Web Search

Examples:

```text
okay Jarvis search for Android development news
okay Jarvis google best restaurants near me
okay Jarvis what is Vulkan
okay Jarvis who invented Java
```

Jarvis opens a web search for the requested query.

### Maps and Navigation

Examples:

```text
okay Jarvis navigate to Tesco
okay Jarvis directions to Manchester Piccadilly
okay Jarvis take me to London
okay Jarvis find petrol station near me
```

Jarvis opens Maps/navigation with the requested destination or search.

### Phone Settings Shortcuts

Examples:

```text
okay Jarvis open settings
okay Jarvis open Wi-Fi settings
okay Jarvis open Bluetooth settings
okay Jarvis open location settings
okay Jarvis open display settings
okay Jarvis open sound settings
okay Jarvis open app settings
okay Jarvis open accessibility settings
okay Jarvis open overlay permission
```

### Media and Volume Controls

Examples:

```text
okay Jarvis volume up
okay Jarvis volume down
okay Jarvis set volume to 50 percent
okay Jarvis max volume
okay Jarvis mute media
okay Jarvis play music
okay Jarvis pause music
okay Jarvis next track
okay Jarvis previous track
```

### Navigation Button Commands

Using the optional Accessibility service, Jarvis can simulate common Android navigation actions.

Examples:

```text
okay Jarvis homescreen
okay Jarvis go home
okay Jarvis go back
okay Jarvis show open apps
okay Jarvis show recent apps
okay Jarvis close app
okay Jarvis close YouTube
okay Jarvis close Chrome
```

> Android does not allow normal apps to silently force-close other apps. The close command uses the safest available Accessibility behaviour, such as Back/Home-style navigation.

### Conversational Replies

Jarvis can respond more naturally to simple interaction commands.

Examples:

```text
okay Jarvis tell me a joke
okay Jarvis thanks
okay Jarvis thank you
okay Jarvis good morning
okay Jarvis good evening
okay Jarvis how are you
okay Jarvis good job
okay Jarvis sorry
okay Jarvis good night
```

Jarvis includes varied replies so it feels less repetitive.

### Built-In Utility Commands

Examples:

```text
okay Jarvis help
okay Jarvis time
okay Jarvis date
okay Jarvis battery
okay Jarvis status
okay Jarvis clear console
okay Jarvis mute
okay Jarvis unmute
```

### Voice Output

- Uses Android Text-To-Speech.
- Attempts to use a UK English voice.
- Uses a lower pitch and slower speech rate for a more assistant-like tone.
- Attempts to select a male/UK-style TTS voice if one is installed on the phone.

> The exact voice depends on the Text-To-Speech engines and voices installed on the device.

---

## Required Permissions

Jarvis explains these permissions during first launch and when a feature needs them.

### Microphone

Required for voice recognition.

### Display Over Other Apps

Required so background command launching works reliably while Jarvis is not open on screen.

Without this permission, background mode is locked and the background button is greyed out.

### Accessibility Service

Required for navigation-style commands:

```text
go back
homescreen
show open apps
close app
```

The service is listed as:

```text
Jarvis Navigation Control
```

Android does not allow apps to silently enable Accessibility services. The user must enable it manually in Android Settings.

### Boot Completed

Allows Jarvis to restore background service mode after reboot, only if the user previously enabled it.

### Query Installed Apps

Allows Jarvis to find and open installed applications by visible app name on newer Android versions.

---

## Example Commands

```text
okay Jarvis open YouTube
okay Jarvis open YouTube for drum and bass
okay Jarvis play drum and bass on YouTube
okay Jarvis search for Android SDK 35
okay Jarvis navigate to Tesco
okay Jarvis volume up
okay Jarvis set volume to 60 percent
okay Jarvis show open apps
okay Jarvis go back
okay Jarvis homescreen
okay Jarvis tell me a joke
okay Jarvis thanks
okay Jarvis stop listening
```

---

## AIDE Build Notes

This project is intended to be opened directly in AIDE as an Android project.

Recommended project location on Android:

```text
/storage/emulated/0/AppProjects/Jarvis
```

Design goals:

- Keep source Java-only.
- Avoid lambda expressions.
- Avoid AndroidX unless deliberately added later.
- Avoid Gradle features that older AIDE setups may not support.
- Keep compatibility with on-device Android development.

---

## Termux Setup Example

If using a downloaded project ZIP:

```bash
termux-setup-storage

mkdir -p "$HOME/storage/shared/AppProjects"

cp "$HOME/storage/shared/Download/Jarvis.zip" "$HOME/storage/shared/AppProjects/"

cd "$HOME/storage/shared/AppProjects"

rm -rf Jarvis

unzip -o Jarvis.zip
```

Then open this folder in AIDE:

```text
/storage/emulated/0/AppProjects/Jarvis
```

---

## Current Limitations

- Android does not allow a normal app to silently enable overlay or Accessibility permissions.
- Android does not allow a normal app to truly force-close another app like the system task manager can.
- Background microphone listening uses battery, so Jarvis includes a pause/stand down command.
- YouTube first-result auto-clicking is not reliably available without deeper Accessibility automation, so Jarvis opens YouTube search results for the requested query.
- The assistant is a local command engine, not a full cloud AI model by default.

---

## Project Goal

The goal is to build a practical Android-based J.A.R.V.I.S-style mobile assistant that can control common phone actions, open apps, search the web, navigate, control media, and respond conversationally, while staying lightweight enough to compile directly on Android through AIDE.

---

## Disclaimer

This project is a fan-made Android assistant interface inspired by futuristic cinematic AI assistants. It is not affiliated with Marvel, Disney, Iron Man, or any official J.A.R.V.I.S product.

---

## v1.13 Intelligence Expansion

This build adds a larger local assistant layer while staying AIDE-friendly, Java-only, no lambdas, no AndroidX, and no external app dependencies.

### New commands

```text
okay Jarvis weather in London
okay Jarvis remind me to check the oven in 10 minutes
okay Jarvis set alarm for 7:30
okay Jarvis show reminders
okay Jarvis remember that my favourite colour is blue
okay Jarvis what do you remember
okay Jarvis fact check the Moon orbits the Earth
okay Jarvis set ai key YOUR_API_KEY
okay Jarvis set ai model gpt-4o-mini
okay Jarvis ask AI explain Android services
okay Jarvis camera vision
okay Jarvis recognise face
okay Jarvis remember face as Jacob
```

### Added features

- Local personal fact memory using SharedPreferences.
- Public fact-check workflow using a Wikipedia source lookup, with Google fallback for manual verification.
- Weather by place using Open-Meteo, no API key required.
- Local alarms and reminders using Android AlarmManager and notifications.
- Expanded media/music control.
- Optional OpenAI-compatible chat endpoint integration through a locally stored API key.
- Camera vision activity for face detection and scene/colour analysis.
- Lightweight local face enrolment and recognition prototype using Android's built-in FaceDetector and simple image signatures.

### Notes

Camera vision and face recognition are deliberately local and dependency-free. This means they are lightweight and AIDE-compatible, but they are not as accurate as a full ML Kit, TensorFlow Lite, or cloud vision model.


## Local AI Key Vault

Jarvis supports Chat AI through a locally stored OpenAI-compatible API key. For safety, API keys are **not hardcoded** into the APK or repository. Add keys on-device instead:

```text
set ai key YOUR_API_KEY
add ai key YOUR_API_KEY
import ai keys from clipboard
ai key status
use ai key 2
clear ai keys
```

The app masks key commands in the console and only shows the last four characters of the active key in status output.


## v15 AI Setup & Local Key Vault

Jarvis now includes an on-device AI setup screen so API keys can be imported locally without committing them to GitHub or embedding them in the APK source.

Commands:

```text
okay Jarvis open AI setup
okay Jarvis import AI keys from clipboard
okay Jarvis append AI keys from clipboard
okay Jarvis test AI connection
okay Jarvis use working AI key
okay Jarvis are AI keys saved
okay Jarvis AI key status
okay Jarvis clear AI keys
```

Keys are stored in Jarvis app data and should survive normal app restarts, phone restarts, and APK updates. They are removed if Jarvis is uninstalled, app data is cleared, the package name changes, or `clear AI keys` is used.


## v18 Vision Upgrade

Jarvis now includes an upgraded AIDE-friendly vision layer:

- Advanced local facial recognition using a 128-value face embedding rather than the original simple colour histogram prototype.
- Multiple face samples can be enrolled for the same person for stronger matching.
- Face status and clearing commands:
  - `okay Jarvis known faces`
  - `okay Jarvis facial recognition status`
  - `okay Jarvis clear face memory`
- Product and visual search commands:
  - `okay Jarvis what is this`
  - `okay Jarvis what product is this`
  - `okay Jarvis identify this product`
  - `okay Jarvis product search`
  - `okay Jarvis open Google Lens`
- QR and barcode scanning commands:
  - `okay Jarvis scan QR code`
  - `okay Jarvis scan barcode`
  - `okay Jarvis read QR code`

The build remains Java-only, AIDE-compatible, no lambdas, no AndroidX, and no bundled external ML dependencies. For exact product matching and QR/barcode decoding, Jarvis will use Google Lens or a ZXing-compatible scanner app when installed.


## v19 Joke Engine Upgrade

Jarvis now includes a larger local joke bank and remembers the last joke it told, so commands like `okay Jarvis tell me a joke`, `another joke`, and `make me laugh` rotate through different responses instead of repeating the same line over and over.
