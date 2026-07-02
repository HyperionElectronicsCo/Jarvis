Jarvis AIDE v9 - YouTube Search + Conversation Upgrade
=============================================================

This is a plain Java Android project designed for AIDE.

Compatibility rules kept:
- Java source only
- No lambda expressions
- No AndroidX dependency
- No external libraries
- Programmatic UI
- compileSdkVersion 29 / targetSdkVersion 28

V6 changes:
- Background recognizer now waits before executing the final result so a longer phrase like
  "okay Jarvis play music on YouTube" is less likely to be cut down to only "play music".
- Added longer speech silence tuning:
  - minimum speech length
  - complete silence delay
  - possibly-complete silence delay
- Removed the old behaviour that could stop listening too soon after hearing only the wake phrase.
- Added YouTube music handling before generic media-key handling.
- Added commands such as:
  - okay Jarvis play music on YouTube
  - okay Jarvis play rock music on YouTube
  - okay Jarvis play music by Adele on YouTube
  - okay Jarvis play a random song on YouTube
  - okay Jarvis YouTube Android tutorials
- If no music type is given, Jarvis opens YouTube for "random music mix".
- Added temporary background listener pause:
  - okay Jarvis stop listening
  - okay Jarvis pause listening
  - okay Jarvis stand down
- When paused, the background service keeps only a foreground notification and stops microphone monitoring.
- The notification now has Pause / Resume and Stop actions.
- The BG button shows BG PAUSE when the background listener is paused.
- Press BG PAUSE or tap Resume in the notification to resume listening.
- Boot receiver will not restart the listener if you deliberately paused it.
- Expanded app aliases for common apps, while still keeping generic installed-app lookup by visible app name.

Important limits:
- Android apps cannot safely or reliably auto-click the first YouTube result without Accessibility access. This version opens YouTube directly to the requested search/music query.
- Full Google Assistant / ChatGPT intelligence would need an online AI API or a server backend. This AIDE build stays local, dependency-free, and AIDE-compatible, so unknown questions fall back to Google search.

Useful commands:
- okay Jarvis open settings
- okay Jarvis open YouTube
- okay Jarvis open AIDE
- okay Jarvis open Termux
- okay Jarvis navigate to Tesco
- okay Jarvis directions to Manchester Piccadilly
- okay Jarvis search for Android development
- okay Jarvis play music on YouTube
- okay Jarvis play 90s hip hop on YouTube
- okay Jarvis volume up
- okay Jarvis set volume to 50 percent
- okay Jarvis pause music
- okay Jarvis next track
- okay Jarvis stop listening
- okay Jarvis resume listening

Background setup:
1. Open Jarvis after installing.
2. When the Display over other apps dialog appears, tap Open permission.
3. Enable Allow display over other apps.
4. Return to Jarvis.
5. Press BG OFF, or say/type enable background.

If Display over other apps is missing:
- BG shows BG LOCK.
- Background service will not start.
- Tap BG LOCK to open the permission dialog again.

V7 navigation control update:
- Added commands: "okay Jarvis homescreen", "okay Jarvis go back", "okay Jarvis show open apps", "okay Jarvis close app", "okay Jarvis close YouTube".
- Home screen has a normal Android fallback.
- Back, Recent Apps and close-current-app need Android Accessibility enabled manually: say/type "open accessibility settings" then enable "Jarvis Navigation Control".
- Android does not allow normal apps to force-stop arbitrary foreground apps. Jarvis closes apps by sending Back a few times and then Home, which is the closest safe Android-compatible behaviour without root/system permissions.


V8 permission setup update:
- Jarvis now includes Accessibility setup in the same permission flow as Display over other apps.
- First launch explains why Accessibility is needed.
- If Display over other apps is missing, Jarvis shows the existing permission dialog with an extra Open Accessibility option.
- Once Display over other apps is enabled, Jarvis automatically prompts for Jarvis Navigation Control if it is not enabled.
- Accessibility is only needed for navigation-button commands: Back, Home, Recent Apps and close app.
- Opening apps, Google searches, Maps, YouTube music, volume and media commands still work without Accessibility.

Recommended setup after install:
1. Open Jarvis.
2. Enable Display over other apps when prompted.
3. Return to Jarvis.
4. When prompted, tap Open Accessibility.
5. Enable Jarvis Navigation Control.
6. Return to Jarvis and enable BG OFF if you want background listening.


V9 YouTube and conversation update:
- Fixed commands such as:
  - okay Jarvis open YouTube for drum and bass
  - okay Jarvis play drum and bass on YouTube
  - okay Jarvis search YouTube for drum and bass
  - okay Jarvis YouTube drum and bass
- Added recognition for YouTube / You Tube / U Tube style speech results.
- YouTube now falls back properly if ACTION_SEARCH fails on a device.
- Added conversational replies so Jarvis feels less like a fixed command menu:
  - okay Jarvis tell me a joke
  - okay Jarvis thanks
  - okay Jarvis good morning
  - okay Jarvis how are you
  - okay Jarvis good job
  - okay Jarvis sorry
- Added varied responses for greetings, thanks, jokes and casual acknowledgement.

Extra test commands:
- okay Jarvis open YouTube for drum and bass
- okay Jarvis play drum and bass on YouTube
- okay Jarvis search YouTube for synthwave
- okay Jarvis tell me a joke
- okay Jarvis thanks
- okay Jarvis how are you

Note about intelligence:
This build is still fully local and AIDE-compatible with no external libraries. It can behave more naturally through local rules and can fall back to Google/YouTube searches, but full ChatGPT-style conversation would require adding an online AI API or server backend.

GitHub Actions JDK note
-----------------------
The workflow intentionally uses JDK 17 first only for sdkmanager because modern Android command-line tools require JDK 17 or newer. It then switches back to JDK 8 before running Gradle because this AIDE-compatible project uses Android Gradle Plugin 3.5.4 and Gradle 5.6.4.
