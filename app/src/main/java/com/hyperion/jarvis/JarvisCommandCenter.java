package com.hyperion.jarvis;

import android.app.PendingIntent;
import android.app.SearchManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.AudioManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.KeyEvent;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public final class JarvisCommandCenter {
    public static final String PREFS_NAME = "jarvis_prefs";
    public static final String PREF_MUTED = "muted";
    public static final String PREF_BACKGROUND_ENABLED = "background_enabled";
    public static final String PREF_BACKGROUND_PAUSED = "background_paused";
    public static final String ACTION_STOP_BACKGROUND = "com.hyperion.jarvis.STOP_BACKGROUND";
    public static final String ACTION_PAUSE_BACKGROUND = "com.hyperion.jarvis.PAUSE_BACKGROUND";
    public static final String ACTION_RESUME_BACKGROUND = "com.hyperion.jarvis.RESUME_BACKGROUND";
    private static int launchRequestCounter = 4100;

    private JarvisCommandCenter() {
    }

    public static String stripWakePhrase(String command) {
        if (command == null) {
            return "";
        }
        String trimmed = command.trim();
        String lower = trimmed.toLowerCase(Locale.UK);
        String[] phrases = new String[] { "okay jarvis", "ok jarvis", "hey jarvis", "hello jarvis", "jarvis" };
        for (int i = 0; i < phrases.length; i++) {
            int index = lower.indexOf(phrases[i]);
            if (index >= 0) {
                int start = index + phrases[i].length();
                String after = trimmed.substring(start).trim();
                after = removeLeadingPunctuation(after);
                return after;
            }
        }
        return trimmed;
    }

    public static boolean containsWakePhrase(String command) {
        if (command == null) {
            return false;
        }
        String lower = command.toLowerCase(Locale.UK);
        return lower.indexOf("okay jarvis") >= 0 || lower.indexOf("ok jarvis") >= 0 || lower.indexOf("hey jarvis") >= 0 || lower.indexOf("hello jarvis") >= 0 || lower.startsWith("jarvis") || lower.indexOf(" jarvis ") >= 0;
    }

    public static String processCommand(Context context, String originalCommand, JarvisOutput output) {
        if (originalCommand == null) {
            return "";
        }
        String command = originalCommand.trim();
        if (command.length() == 0) {
            return "Please give me a command.";
        }
        command = stripWakePhrase(command);
        if (command.length() == 0) {
            return "At your service.";
        }

        String lower = command.toLowerCase(Locale.UK);

        if (containsAny(lower, new String[] { "help", "commands", "what can you do" })) {
            return "You can say okay Jarvis, then ask me to search Google, navigate somewhere, open nearly any installed app, play drum and bass on YouTube, tell a joke, change media volume, control music, read battery, show time, open settings, go home, go back, show recent apps, close the current app, pause listening, resume listening, or run in the background.";
        }

        String conversationResponse = handleConversationCommand(lower);
        if (conversationResponse != null) {
            return conversationResponse;
        }

        if (containsAny(lower, new String[] { "who are you", "your name" })) {
            return "I am Jarvis, a native Android assistant shell rebuilt for AIDE.";
        }
        if (containsAny(lower, new String[] { "time", "what time" })) {
            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.UK);
            return "The time is " + timeFormat.format(new Date()) + ".";
        }
        if (containsAny(lower, new String[] { "date", "what day", "today" })) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE d MMMM yyyy", Locale.UK);
            return "Today is " + dateFormat.format(new Date()) + ".";
        }
        if (containsAny(lower, new String[] { "battery", "power level", "charge" }) && lower.indexOf("open ") < 0 && lower.indexOf("settings") < 0) {
            return getBatteryStatus(context);
        }
        if (containsAny(lower, new String[] { "status", "system report", "systems" })) {
            return "All local systems are nominal. Voice core, text core, background core and visual matrix are active.";
        }
        if (containsAny(lower, new String[] { "visual", "hud", "background animation" })) {
            return "The animated interface uses rotating rings, scan lines, particles, pulse waves and audio-reactive bars.";
        }

        if (containsAny(lower, new String[] { "clear console", "clear screen", "clear log" })) {
            if (output != null) {
                output.onClearConsole();
            }
            return "Console cleared.";
        }
        if (containsAny(lower, new String[] { "unmute jarvis", "unmute speech", "speak again" })) {
            setMuted(context, false);
            if (output != null) {
                output.onMuteChanged(false);
            }
            return "Speech output restored.";
        }
        if (containsAny(lower, new String[] { "mute jarvis", "mute speech", "be quiet" })) {
            setMuted(context, true);
            if (output != null) {
                output.onMuteChanged(true);
            }
            return "Speech output muted.";
        }
        if (containsAny(lower, new String[] { "stop listening", "pause listening", "cancel listening", "go silent", "stand down", "pause background listener" })) {
            setBackgroundPaused(context, true);
            pauseBackgroundService(context);
            if (output != null) {
                output.onStopListeningRequested();
                output.onBackgroundStateChanged(isBackgroundEnabled(context));
            }
            return "Background listener paused. Microphone monitoring is stopped. Open Jarvis or use the notification to resume listening.";
        }
        if (containsAny(lower, new String[] { "resume listening", "start listening again", "resume background", "wake up jarvis", "come back online" })) {
            if (!hasOverlayPermission(context)) {
                setBackgroundEnabled(context, false);
                setBackgroundPaused(context, false);
                if (output != null) {
                    output.onBackgroundStateChanged(false);
                }
                openOverlayPermission(context, output);
                return "Background mode is locked until Display over other apps is enabled for Jarvis.";
            }
            setBackgroundEnabled(context, true);
            setBackgroundPaused(context, false);
            startBackgroundService(context);
            if (output != null) {
                output.onBackgroundStateChanged(true);
            }
            return "Background listener resumed.";
        }

        String deviceNavigationResponse = handleDeviceNavigationCommand(context, lower, output);
        if (deviceNavigationResponse != null) {
            return deviceNavigationResponse;
        }

        if (containsAny(lower, new String[] { "enable background", "start background", "background on", "listen in background", "run in background" })) {
            if (!hasOverlayPermission(context)) {
                setBackgroundEnabled(context, false);
                if (output != null) {
                    output.onBackgroundStateChanged(false);
                }
                openOverlayPermission(context, output);
                return "Background mode is locked until Display over other apps is enabled for Jarvis.";
            }
            setBackgroundEnabled(context, true);
            setBackgroundPaused(context, false);
            startBackgroundService(context);
            if (output != null) {
                output.onBackgroundStateChanged(true);
            }
            return "Background listening service enabled. Say okay Jarvis when you need me.";
        }
        if (containsAny(lower, new String[] { "disable background", "stop background", "background off", "turn off background" })) {
            setBackgroundEnabled(context, false);
            setBackgroundPaused(context, false);
            stopBackgroundService(context);
            if (output != null) {
                output.onBackgroundStateChanged(false);
            }
            return "Background listening service disabled.";
        }

        String youtubeResponse = handleYouTubeCommand(context, command, lower, output);
        if (youtubeResponse != null) {
            return youtubeResponse;
        }

        String platformPlayResponse = handlePlatformPlayCommand(context, command, lower, output);
        if (platformPlayResponse != null) {
            return platformPlayResponse;
        }

        String volumeResponse = handleVolumeCommand(context, lower);
        if (volumeResponse != null) {
            return volumeResponse;
        }
        String mediaResponse = handleMediaCommand(context, lower);
        if (mediaResponse != null) {
            return mediaResponse;
        }

        String navigationTarget = extractNavigationTarget(command, lower);
        if (navigationTarget != null && navigationTarget.length() > 0) {
            openNavigation(context, navigationTarget, output);
            return "Opening directions to " + navigationTarget + ".";
        }

        String searchQuery = extractSearchQuery(command, lower);
        if (searchQuery != null && searchQuery.length() > 0) {
            openWebSearch(context, searchQuery, output);
            return "Searching Google for " + searchQuery + ".";
        }

        String settingsResponse = handleSettingsCommand(context, lower, output);
        if (settingsResponse != null) {
            return settingsResponse;
        }

        String dialTarget = extractDialTarget(command, lower);
        if (dialTarget != null && dialTarget.length() > 0) {
            openDialer(context, dialTarget, output);
            return "Opening the dialer.";
        }

        String appName = extractAppName(command, lower);
        if (appName != null && appName.length() > 0) {
            String openedName = openInstalledApp(context, appName, output);
            if (openedName != null) {
                return "Opening " + openedName + ".";
            }
            openWebSearch(context, appName, output);
            return "I could not find that app, so I searched for " + appName + ".";
        }

        if (isQuestion(lower)) {
            openWebSearch(context, command, output);
            return "I will search Google for that.";
        }

        return "I heard, " + command + ". I do not have a local action for that yet. Say search for followed by your question to ask Google.";
    }

    public static boolean isMuted(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(PREF_MUTED, false);
    }

    public static void setMuted(Context context, boolean muted) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(PREF_MUTED, muted).commit();
    }

    public static boolean isBackgroundEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(PREF_BACKGROUND_ENABLED, false);
    }

    public static void setBackgroundEnabled(Context context, boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(PREF_BACKGROUND_ENABLED, enabled).commit();
    }

    public static boolean isBackgroundPaused(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(PREF_BACKGROUND_PAUSED, false);
    }

    public static void setBackgroundPaused(Context context, boolean paused) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(PREF_BACKGROUND_PAUSED, paused).commit();
    }

    public static boolean hasOverlayPermission(Context context) {
        if (context == null) {
            return false;
        }
        if (Build.VERSION.SDK_INT < 23) {
            return true;
        }
        try {
            return Settings.canDrawOverlays(context);
        } catch (Exception ignored) {
            return false;
        }
    }

    public static void startBackgroundService(Context context) {
        if (!hasOverlayPermission(context)) {
            setBackgroundEnabled(context, false);
            setBackgroundPaused(context, false);
            return;
        }
        setBackgroundPaused(context, false);
        Intent serviceIntent = new Intent(context, JarvisAssistantService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        } catch (Exception ignored) {
        }
    }

    public static void pauseBackgroundService(Context context) {
        if (!isBackgroundEnabled(context)) {
            return;
        }
        Intent serviceIntent = new Intent(context, JarvisAssistantService.class);
        serviceIntent.setAction(ACTION_PAUSE_BACKGROUND);
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        } catch (Exception ignored) {
        }
    }

    public static void resumeBackgroundService(Context context) {
        if (!hasOverlayPermission(context)) {
            setBackgroundEnabled(context, false);
            setBackgroundPaused(context, false);
            return;
        }
        setBackgroundEnabled(context, true);
        setBackgroundPaused(context, false);
        Intent serviceIntent = new Intent(context, JarvisAssistantService.class);
        serviceIntent.setAction(ACTION_RESUME_BACKGROUND);
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        } catch (Exception ignored) {
        }
    }

    public static void stopBackgroundService(Context context) {
        setBackgroundPaused(context, false);
        Intent serviceIntent = new Intent(context, JarvisAssistantService.class);
        serviceIntent.setAction(ACTION_STOP_BACKGROUND);
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        } catch (Exception ignored) {
            try {
                context.stopService(new Intent(context, JarvisAssistantService.class));
            } catch (Exception ignoredAgain) {
            }
        }
    }

    public static boolean isAccessibilityServiceEnabled(Context context) {
        if (context == null) {
            return false;
        }
        try {
            int enabled = Settings.Secure.getInt(context.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 0);
            if (enabled != 1) {
                return false;
            }
            String services = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (services == null) {
                return false;
            }
            String packageName = context.getPackageName().toLowerCase(Locale.UK);
            String className = JarvisAccessibilityService.class.getName().toLowerCase(Locale.UK);
            String flattened = packageName + "/" + className;
            String lowered = services.toLowerCase(Locale.UK);
            return lowered.indexOf(flattened) >= 0 || (lowered.indexOf(packageName) >= 0 && lowered.indexOf("jarvisaccessibilityservice") >= 0);
        } catch (Exception ignored) {
            return JarvisAccessibilityService.isActive();
        }
    }

    private static String handleDeviceNavigationCommand(Context context, String lower, JarvisOutput output) {
        if (containsAny(lower, new String[] { "accessibility permission", "accessibility settings", "jarvis permissions", "assistant permissions", "navigation permission", "enable navigation control", "enable back button", "enable recent apps" })) {
            openAccessibilitySettings(context, output);
            return "Opening Accessibility settings. Enable Jarvis Navigation Control so I can press Back, Home and Recent Apps for you.";
        }

        if (isHomeCommand(lower)) {
            boolean usedAccessibility = JarvisAccessibilityService.performHome(context);
            if (usedAccessibility) {
                return "Going to the home screen.";
            }
            return "Going to the home screen. For stronger phone control, enable Jarvis Navigation Control in Accessibility settings.";
        }

        if (isRecentAppsCommand(lower)) {
            if (JarvisAccessibilityService.performRecents()) {
                return "Showing open apps.";
            }
            openAccessibilitySettings(context, output);
            return "I need Jarvis Navigation Control enabled in Accessibility settings before I can show open apps. Open Jarvis and use the permission prompt, or enable Jarvis Navigation Control manually.";
        }

        if (isBackCommand(lower)) {
            if (JarvisAccessibilityService.performBack()) {
                return "Going back.";
            }
            openAccessibilitySettings(context, output);
            return "I need Jarvis Navigation Control enabled in Accessibility settings before I can press Back. Open Jarvis and use the permission prompt, or enable Jarvis Navigation Control manually.";
        }

        if (isCloseAppCommand(lower)) {
            if (JarvisAccessibilityService.performCloseCurrentApp()) {
                return "Closing the current foreground app as far as Android allows.";
            }
            openAccessibilitySettings(context, output);
            return "I need Jarvis Navigation Control enabled in Accessibility settings before I can close or back out of other apps. Open Jarvis and use the permission prompt, or enable Jarvis Navigation Control manually.";
        }
        return null;
    }

    private static boolean isHomeCommand(String lower) {
        if (lower.equals("home") || lower.equals("homescreen") || lower.equals("home screen")) {
            return true;
        }
        return containsAny(lower, new String[] { "go home", "go to home", "go to the home", "go to homescreen", "go to home screen", "open home screen", "show home screen", "phone home screen", "launcher screen", "middle navigation button", "press home button", "press the circle", "circle button" });
    }

    private static boolean isRecentAppsCommand(String lower) {
        return containsAny(lower, new String[] { "show open apps", "show opened apps", "show running apps", "show recent apps", "open recent apps", "open recents", "recent apps", "app switcher", "task switcher", "multitasking", "square button", "press the square", "navigation square" });
    }

    private static boolean isBackCommand(String lower) {
        if (lower.equals("back") || lower.equals("go back")) {
            return true;
        }
        return containsAny(lower, new String[] { "go back", "press back", "back button", "press the back", "previous screen", "return back", "triangle button", "press the triangle", "navigation triangle" });
    }

    private static boolean isCloseAppCommand(String lower) {
        if (lower.equals("close") || lower.equals("close app") || lower.equals("close application") || lower.equals("exit app") || lower.equals("quit app")) {
            return true;
        }
        if (lower.startsWith("close ") || lower.startsWith("exit ") || lower.startsWith("quit ")) {
            return true;
        }
        return containsAny(lower, new String[] { "close current app", "close the current app", "close this app", "close foreground app", "close youtube", "exit youtube", "quit youtube", "close chrome", "close maps", "close settings", "close the app", "shut this app", "exit application", "close application" });
    }

    public static void openAccessibilitySettings(Context context, JarvisOutput output) {
        try {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivityCompat(context, intent, output, "accessibility settings");
        } catch (Exception error) {
            log(output, "SETTINGS: Unable to open Accessibility settings: " + error.getMessage());
            openAndroidSettings(context, Settings.ACTION_SETTINGS, output);
        }
    }

    private static String handleVolumeCommand(Context context, String lower) {
        if (lower.indexOf("volume") < 0 && lower.indexOf("sound") < 0 && lower.indexOf("turn it up") < 0 && lower.indexOf("turn it down") < 0) {
            return null;
        }
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) {
            return "I could not access the audio system.";
        }
        int stream = AudioManager.STREAM_MUSIC;
        int max = audioManager.getStreamMaxVolume(stream);
        int current = audioManager.getStreamVolume(stream);

        int percent = extractFirstNumber(lower);
        if (percent >= 0 && percent <= 100) {
            int target = Math.round((max * percent) / 100.0f);
            audioManager.setStreamVolume(stream, target, AudioManager.FLAG_SHOW_UI);
            return "Media volume set to " + percent + " percent.";
        }
        if (containsAny(lower, new String[] { "max", "maximum", "full" })) {
            audioManager.setStreamVolume(stream, max, AudioManager.FLAG_SHOW_UI);
            return "Media volume set to maximum.";
        }
        if (containsAny(lower, new String[] { "mute", "silent", "zero" })) {
            audioManager.setStreamVolume(stream, 0, AudioManager.FLAG_SHOW_UI);
            return "Media volume muted.";
        }
        if (containsAny(lower, new String[] { "up", "increase", "louder", "turn it up" })) {
            audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
            return "Media volume increased.";
        }
        if (containsAny(lower, new String[] { "down", "decrease", "lower", "quieter", "turn it down" })) {
            audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
            return "Media volume decreased.";
        }
        return "Media volume is currently " + current + " out of " + max + ".";
    }

    private static String handleConversationCommand(String lower) {
        if (isThanksCommand(lower)) {
            return randomResponse(new String[] {
                    "You're welcome, sir.",
                    "Any time.",
                    "Of course.",
                    "Glad to be of service.",
                    "You're very welcome.",
                    "No problem. Standing by."
            });
        }
        if (containsAny(lower, new String[] { "tell a joke", "tell me a joke", "make me laugh", "say something funny", "joke please", "jarvis joke" }) || lower.equals("joke")) {
            return randomResponse(new String[] {
                    "I told my code to take a break. It said it needed a runtime environment.",
                    "Why did the smartphone need glasses? Because it lost its contacts.",
                    "I would tell you a UDP joke, but you might not get it.",
                    "Why do programmers prefer dark mode? Because light attracts bugs.",
                    "I asked the toaster to join the network. It said it was already burnt out.",
                    "I tried to make a belt out of watches. It was a waist of time."
            });
        }
        if (isGreetingCommand(lower)) {
            return randomResponse(new String[] {
                    "Good day. Jarvis mobile interface is online and standing by.",
                    "Hello. Systems are awake and ready.",
                    "At your service.",
                    "Good to hear from you. How may I assist?",
                    "Jarvis online. Awaiting instructions.",
                    "Hello, sir. Local systems are nominal."
            });
        }
        if (containsAny(lower, new String[] { "how are you", "how you doing", "are you okay", "you okay", "how is it going", "are you there" })) {
            return randomResponse(new String[] {
                    "I am operating within normal parameters.",
                    "All systems are stable. Thank you for asking.",
                    "Fully operational and ready to assist.",
                    "I am doing well. My circuits appreciate the concern.",
                    "Online, alert, and only mildly dramatic."
            });
        }
        if (containsAny(lower, new String[] { "good night", "night jarvis", "sleep well" })) {
            return randomResponse(new String[] {
                    "Good night. I will remain on standby.",
                    "Rest well. Jarvis standing down to standby.",
                    "Good night. I will keep the systems quiet."
            });
        }
        if (containsAny(lower, new String[] { "well done", "good job", "nice work", "brilliant", "excellent" })) {
            return randomResponse(new String[] {
                    "Thank you. A little appreciation improves the interface considerably.",
                    "Much appreciated.",
                    "Glad the result was satisfactory.",
                    "Thank you. I shall try not to let it go to my processor."
            });
        }
        if (containsAny(lower, new String[] { "sorry jarvis", "sorry about that", "my mistake" })) {
            return randomResponse(new String[] {
                    "No harm done.",
                    "Understood. We continue.",
                    "Apology accepted.",
                    "Noted. Carrying on."
            });
        }
        return null;
    }

    private static boolean isThanksCommand(String lower) {
        if (lower.equals("thanks") || lower.equals("thank you") || lower.equals("cheers") || lower.equals("ta") || lower.equals("nice one")) {
            return true;
        }
        return containsAny(lower, new String[] { "thanks jarvis", "thank you jarvis", "cheers jarvis", "appreciate it", "much appreciated" });
    }

    private static boolean isGreetingCommand(String lower) {
        if (lower.equals("hello") || lower.equals("hi") || lower.equals("hey") || lower.equals("morning") || lower.equals("evening") || lower.equals("afternoon")) {
            return true;
        }
        return containsAny(lower, new String[] { "hello jarvis", "hi jarvis", "hey jarvis", "good morning", "good afternoon", "good evening", "alright jarvis", "you there jarvis" });
    }

    private static String randomResponse(String[] responses) {
        if (responses == null || responses.length == 0) {
            return "At your service.";
        }
        try {
            Random random = new Random(System.currentTimeMillis());
            return responses[random.nextInt(responses.length)];
        } catch (Exception ignored) {
            return responses[0];
        }
    }

    private static String handleYouTubeCommand(Context context, String command, String lower, JarvisOutput output) {
        if (!mentionsYouTube(lower)) {
            return null;
        }

        String query = extractYouTubeAnyQuery(command, lower);
        if (query != null && query.length() > 0) {
            openYouTubeSearch(context, query, output);
            return "Opening YouTube for " + query + ".";
        }

        if (containsAny(lower, new String[] { "play", "music", "song", "songs", "playlist", "listen" })) {
            query = "random music mix";
            openYouTubeSearch(context, query, output);
            return "Opening YouTube for " + query + ".";
        }

        if (containsAny(lower, new String[] { "open", "launch", "start", "run", "load" }) || normalize(lower).equals("youtube")) {
            String opened = openKnownAppAlias(context, "youtube", output);
            if (opened != null) {
                return "Opening YouTube.";
            }
            openYouTubeSearch(context, "YouTube", output);
            return "Opening YouTube search.";
        }
        return null;
    }

    private static String handlePlatformPlayCommand(Context context, String command, String lower, JarvisOutput output) {
        if (lower.indexOf("spotify") >= 0 && containsAny(lower, new String[] { "play", "music", "song", "songs", "playlist", "listen" })) {
            String query = extractPlatformQuery(command, lower, "spotify");
            String opened = openInstalledApp(context, "spotify", output);
            if (opened != null) {
                if (query.length() > 0) {
                    openWebSearch(context, "Spotify " + query, output);
                    return "Opening Spotify and searching for " + query + ".";
                }
                return "Opening Spotify.";
            }
            openWebSearch(context, "Spotify " + query, output);
            return "Spotify is not installed, so I searched for " + query + ".";
        }
        return null;
    }

    private static boolean mentionsYouTube(String lower) {
        String normalized = normalize(lower);
        return normalized.indexOf("youtube") >= 0 || normalized.indexOf("utube") >= 0 || normalized.indexOf("ytmusic") >= 0 || lower.indexOf("you tube") >= 0 || lower.indexOf("you-tube") >= 0;
    }

    private static String canonicalYouTubeLower(String lower) {
        if (lower == null) {
            return "";
        }
        String result = lower.toLowerCase(Locale.UK);
        result = result.replace("you tube", "youtube");
        result = result.replace("you-tube", "youtube");
        result = result.replace("u tube", "youtube");
        result = result.replace("u-tube", "youtube");
        return result;
    }

    private static String extractYouTubeAnyQuery(String command, String lower) {
        String work = canonicalYouTubeLower(lower);
        if (work.length() == 0) {
            return "";
        }

        String[] directMarkers = new String[] {
                "open youtube for ",
                "open youtube and search for ",
                "open youtube search for ",
                "open youtube and play ",
                "open youtube play ",
                "search youtube for ",
                "search on youtube for ",
                "youtube search for ",
                "find on youtube ",
                "find youtube ",
                "youtube for ",
                "youtube play ",
                "youtube "
        };
        for (int i = 0; i < directMarkers.length; i++) {
            if (work.startsWith(directMarkers[i])) {
                return cleanYouTubeQuery(work.substring(directMarkers[i].length()).trim());
            }
        }

        String[] endMarkers = new String[] { " on youtube", " in youtube", " using youtube", " through youtube", " with youtube" };
        for (int j = 0; j < endMarkers.length; j++) {
            int index = work.indexOf(endMarkers[j]);
            if (index > 0) {
                return cleanYouTubeQuery(work.substring(0, index).trim());
            }
        }

        int youtubeIndex = work.indexOf("youtube");
        if (youtubeIndex >= 0) {
            String before = work.substring(0, youtubeIndex).trim();
            String after = work.substring(youtubeIndex + 7).trim();
            before = cleanYouTubeQuery(before);
            after = cleanYouTubeQuery(after);
            if (after.length() > 0 && !isOnlyOpenWords(after)) {
                return after;
            }
            if (before.length() > 0 && !isOnlyOpenWords(before)) {
                return before;
            }
        }
        return "";
    }

    private static boolean isOnlyOpenWords(String value) {
        String normalized = normalize(value);
        return normalized.equals("open") || normalized.equals("launch") || normalized.equals("start") || normalized.equals("run") || normalized.equals("load") || normalized.equals("please") || normalized.equals("the");
    }

    private static String cleanYouTubeQuery(String query) {
        if (query == null) {
            return "";
        }
        String result = removeLeadingPunctuation(query).trim();
        String lower = result.toLowerCase(Locale.UK);
        String[] starters = new String[] {
                "please ",
                "can you ",
                "could you ",
                "open youtube for ",
                "open youtube and search for ",
                "search youtube for ",
                "search for ",
                "find ",
                "look up ",
                "play me ",
                "play some ",
                "play a ",
                "play an ",
                "play ",
                "listen to ",
                "put on ",
                "start playing ",
                "start ",
                "open ",
                "launch ",
                "for "
        };
        boolean changed = true;
        while (changed) {
            changed = false;
            lower = result.toLowerCase(Locale.UK);
            for (int i = 0; i < starters.length; i++) {
                if (lower.startsWith(starters[i]) && result.length() > starters[i].length()) {
                    result = result.substring(starters[i].length()).trim();
                    changed = true;
                    break;
                }
            }
        }
        lower = result.toLowerCase(Locale.UK);
        if (lower.startsWith("music by ")) {
            result = result.substring(9).trim();
            lower = result.toLowerCase(Locale.UK);
        }
        if (lower.endsWith(" please")) {
            result = result.substring(0, result.length() - 7).trim();
            lower = result.toLowerCase(Locale.UK);
        }
        if (lower.endsWith(" for me")) {
            result = result.substring(0, result.length() - 7).trim();
            lower = result.toLowerCase(Locale.UK);
        }
        if (lower.endsWith(" music")) {
            result = result.trim();
        }
        if (normalize(result).equals("youtube") || normalize(result).equals("utube") || normalize(result).equals("openyoutube")) {
            return "";
        }
        if (normalize(result).equals("music") || normalize(result).equals("song") || normalize(result).equals("songs") || normalize(result).equals("randomsong") || normalize(result).equals("arandomsong")) {
            return "random music mix";
        }
        return result.trim();
    }

    private static String extractYouTubeMusicQuery(String command, String lower) {
        String query = extractPlatformQuery(command, lower, "youtube");
        String normalized = normalize(query);
        if (query.length() == 0 || normalized.equals("music") || normalized.equals("somemusic") || normalized.equals("song") || normalized.equals("songs") || normalized.equals("randomsong") || normalized.equals("arandomsong")) {
            return "random music mix";
        }
        return query;
    }

    private static String extractPlatformQuery(String command, String lower, String platform) {
        String result = command;
        String lowerResult = lower;
        String[] platformMarkers = new String[] { " on " + platform, " in " + platform, " using " + platform, " with " + platform, " through " + platform };
        for (int i = 0; i < platformMarkers.length; i++) {
            int index = lowerResult.indexOf(platformMarkers[i]);
            if (index >= 0) {
                result = result.substring(0, index).trim();
                lowerResult = result.toLowerCase(Locale.UK);
                break;
            }
        }
        if (lowerResult.indexOf(platform) >= 0) {
            int index = lowerResult.indexOf(platform);
            String before = result.substring(0, index).trim();
            String after = result.substring(index + platform.length()).trim();
            result = before.length() >= after.length() ? before : after;
            lowerResult = result.toLowerCase(Locale.UK);
        }
        if (lowerResult.startsWith("and ")) {
            result = result.substring(4).trim();
            lowerResult = result.toLowerCase(Locale.UK);
        }
        String[] starters = new String[] { "please play ", "play me ", "play some ", "play a ", "play an ", "play ", "listen to ", "put on ", "start playing ", "start ", "open ", "launch " };
        for (int i = 0; i < starters.length; i++) {
            if (lowerResult.startsWith(starters[i])) {
                result = result.substring(starters[i].length()).trim();
                lowerResult = result.toLowerCase(Locale.UK);
                break;
            }
        }
        if (lowerResult.startsWith("music by ")) {
            result = result.substring(9).trim();
            lowerResult = result.toLowerCase(Locale.UK);
        }
        if (lowerResult.equals("music") || lowerResult.equals("a song") || lowerResult.equals("song") || lowerResult.equals("songs")) {
            return "";
        }
        result = cleanTrailingPoliteWords(result);
        return result;
    }

    private static void openYouTubeSearch(Context context, String query, JarvisOutput output) {
        if (query == null || query.trim().length() == 0) {
            query = "random music mix";
        }
        query = cleanYouTubeQuery(query);
        if (query.length() == 0) {
            query = "random music mix";
        }
        try {
            Intent intent = new Intent(Intent.ACTION_SEARCH);
            intent.setPackage("com.google.android.youtube");
            intent.putExtra(SearchManager.QUERY, query);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (startActivityCompat(context, intent, output, "YouTube search")) {
                return;
            }
        } catch (Exception first) {
            log(output, "YOUTUBE: ACTION_SEARCH failed: " + first.getMessage());
        }
        try {
            Uri uri = Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query));
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.setPackage("com.google.android.youtube");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (startActivityCompat(context, intent, output, "YouTube app search")) {
                return;
            }
        } catch (Exception second) {
            log(output, "YOUTUBE: app URL failed: " + second.getMessage());
        }
        try {
            Uri uri = Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query));
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivityCompat(context, intent, output, "YouTube browser search");
        } catch (Exception third) {
            log(output, "YOUTUBE: browser fallback failed: " + third.getMessage());
        }
    }

    private static String handleMediaCommand(Context context, String lower) {
        if (containsAny(lower, new String[] { "play music", "resume music", "pause music", "pause media", "play media", "next track", "next song", "previous track", "previous song", "skip track", "stop music", "stop media" })) {
            int keyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
            String response = "Toggling media playback.";
            if (containsAny(lower, new String[] { "next track", "next song", "skip track" })) {
                keyCode = KeyEvent.KEYCODE_MEDIA_NEXT;
                response = "Skipping to the next track.";
            } else if (containsAny(lower, new String[] { "previous track", "previous song" })) {
                keyCode = KeyEvent.KEYCODE_MEDIA_PREVIOUS;
                response = "Returning to the previous track.";
            } else if (containsAny(lower, new String[] { "stop music", "stop media" })) {
                keyCode = KeyEvent.KEYCODE_MEDIA_STOP;
                response = "Stopping media playback.";
            }
            dispatchMediaKey(context, keyCode);
            return response;
        }
        return null;
    }

    private static void dispatchMediaKey(Context context, int keyCode) {
        try {
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (audioManager == null) {
                return;
            }
            long eventTime = System.currentTimeMillis();
            KeyEvent down = new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0);
            KeyEvent up = new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0);
            audioManager.dispatchMediaKeyEvent(down);
            audioManager.dispatchMediaKeyEvent(up);
        } catch (Exception ignored) {
        }
    }

    private static String handleSettingsCommand(Context context, String lower, JarvisOutput output) {
        if (containsAny(lower, new String[] { "open wifi", "wifi settings", "wi-fi settings" })) {
            openAndroidSettings(context, Settings.ACTION_WIFI_SETTINGS, output);
            return "Opening Wi-Fi settings.";
        }
        if (containsAny(lower, new String[] { "open bluetooth", "bluetooth settings" })) {
            openAndroidSettings(context, Settings.ACTION_BLUETOOTH_SETTINGS, output);
            return "Opening Bluetooth settings.";
        }
        if (containsAny(lower, new String[] { "open location", "location settings", "gps settings" })) {
            openAndroidSettings(context, Settings.ACTION_LOCATION_SOURCE_SETTINGS, output);
            return "Opening location settings.";
        }
        if (containsAny(lower, new String[] { "open sound", "sound settings", "audio settings" })) {
            openAndroidSettings(context, Settings.ACTION_SOUND_SETTINGS, output);
            return "Opening sound settings.";
        }
        if (containsAny(lower, new String[] { "open display", "display settings", "brightness settings" })) {
            openAndroidSettings(context, Settings.ACTION_DISPLAY_SETTINGS, output);
            return "Opening display settings.";
        }
        if (containsAny(lower, new String[] { "open battery", "battery settings" })) {
            if (Build.VERSION.SDK_INT >= 22) {
                openAndroidSettings(context, Settings.ACTION_BATTERY_SAVER_SETTINGS, output);
            } else {
                openAndroidSettings(context, Settings.ACTION_SETTINGS, output);
            }
            return "Opening battery settings.";
        }
        if (containsAny(lower, new String[] { "open apps", "app settings", "application settings" })) {
            openAndroidSettings(context, Settings.ACTION_APPLICATION_SETTINGS, output);
            return "Opening application settings.";
        }
        if (containsAny(lower, new String[] { "overlay permission", "display over other apps", "draw over apps", "background launch permission" })) {
            openOverlayPermission(context, output);
            return "Opening Jarvis overlay permission. Allow display over other apps if your phone blocks background commands.";
        }
        if (lower.equals("settings") || lower.equals("setting") || lower.equals("system settings") || lower.equals("device settings") || containsAny(lower, new String[] { "open settings", "open setting", "open the settings", "open the setting", "android settings", "phone settings", "main settings", "system settings", "device settings" })) {
            openAndroidSettings(context, Settings.ACTION_SETTINGS, output);
            return "Opening Android settings.";
        }
        return null;
    }

    private static String extractNavigationTarget(String command, String lower) {
        String[] markers = new String[] { "navigate to ", "directions to ", "get directions to ", "take me to ", "route to ", "drive to ", "walk to " };
        for (int i = 0; i < markers.length; i++) {
            int index = lower.indexOf(markers[i]);
            if (index >= 0) {
                return command.substring(index + markers[i].length()).trim();
            }
        }
        return null;
    }

    private static String extractSearchQuery(String command, String lower) {
        String[] markers = new String[] { "search google for ", "google search for ", "search for ", "google ", "look up ", "find out ", "web search " };
        for (int i = 0; i < markers.length; i++) {
            if (lower.startsWith(markers[i])) {
                return command.substring(markers[i].length()).trim();
            }
            int index = lower.indexOf(" " + markers[i]);
            if (index >= 0) {
                return command.substring(index + markers[i].length() + 1).trim();
            }
        }
        return null;
    }

    private static String extractDialTarget(String command, String lower) {
        String[] markers = new String[] { "call ", "dial ", "phone " };
        for (int i = 0; i < markers.length; i++) {
            if (lower.startsWith(markers[i])) {
                return command.substring(markers[i].length()).trim();
            }
        }
        return null;
    }

    private static String extractAppName(String command, String lower) {
        String[] markers = new String[] { "open the ", "launch the ", "start the ", "run the ", "load the ", "open up ", "open ", "launch ", "start ", "run ", "load " };
        for (int i = 0; i < markers.length; i++) {
            if (lower.startsWith(markers[i])) {
                String app = command.substring(markers[i].length()).trim();
                app = cleanAppName(app);
                return app;
            }
        }
        return null;
    }

    private static String cleanAppName(String app) {
        if (app == null) {
            return "";
        }
        String result = removeLeadingPunctuation(app).trim();
        String lower = result.toLowerCase(Locale.UK);
        if (lower.startsWith("the ")) {
            result = result.substring(4).trim();
            lower = result.toLowerCase(Locale.UK);
        }
        if (lower.endsWith(" please")) {
            result = result.substring(0, result.length() - 7).trim();
            lower = result.toLowerCase(Locale.UK);
        }
        if (lower.endsWith(" for me")) {
            result = result.substring(0, result.length() - 7).trim();
            lower = result.toLowerCase(Locale.UK);
        }
        if (lower.endsWith(" application")) {
            result = result.substring(0, result.length() - 12).trim();
            lower = result.toLowerCase(Locale.UK);
        }
        if (lower.endsWith(" app")) {
            result = result.substring(0, result.length() - 4).trim();
        }
        return result;
    }

    private static void openWebSearch(Context context, String query, JarvisOutput output) {
        try {
            Intent searchIntent = new Intent(Intent.ACTION_WEB_SEARCH);
            searchIntent.putExtra(SearchManager.QUERY, query);
            searchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivityCompat(context, searchIntent, output, "Google search");
            return;
        } catch (Exception first) {
            log(output, "SEARCH: ACTION_WEB_SEARCH failed: " + first.getMessage());
        }
        try {
            String url = "https://www.google.com/search?q=" + Uri.encode(query);
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivityCompat(context, intent, output, "activity launch");
        } catch (Exception second) {
            log(output, "SEARCH: Browser fallback failed: " + second.getMessage());
        }
    }

    private static void openNavigation(Context context, String target, JarvisOutput output) {
        try {
            Uri uri = Uri.parse("google.navigation:q=" + Uri.encode(target));
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.setPackage("com.google.android.apps.maps");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivityCompat(context, intent, output, "activity launch");
            return;
        } catch (Exception first) {
            log(output, "MAPS: Google Maps navigation failed: " + first.getMessage());
        }
        try {
            Uri uri = Uri.parse("geo:0,0?q=" + Uri.encode(target));
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivityCompat(context, intent, output, "activity launch");
        } catch (Exception second) {
            log(output, "MAPS: Generic maps fallback failed: " + second.getMessage());
            openWebSearch(context, "directions to " + target, output);
        }
    }

    private static void openDialer(Context context, String target, JarvisOutput output) {
        try {
            Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(target)));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivityCompat(context, intent, output, "activity launch");
        } catch (Exception error) {
            log(output, "DIAL: Unable to open dialer: " + error.getMessage());
        }
    }

    private static void openAndroidSettings(Context context, String action, JarvisOutput output) {
        try {
            Intent intent = new Intent(action);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivityCompat(context, intent, output, "activity launch");
        } catch (Exception error) {
            log(output, "SETTINGS: Unable to open settings: " + error.getMessage());
        }
    }

    public static void openOverlayPermission(Context context, JarvisOutput output) {
        try {
            Intent intent;
            if (Build.VERSION.SDK_INT >= 23) {
                intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.getPackageName()));
            } else {
                intent = new Intent(Settings.ACTION_SETTINGS);
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivityCompat(context, intent, output, "overlay permission");
        } catch (Exception error) {
            log(output, "SETTINGS: Unable to open overlay permission: " + error.getMessage());
            openAndroidSettings(context, Settings.ACTION_SETTINGS, output);
        }
    }

    private static String openInstalledApp(Context context, String appName, JarvisOutput output) {
        String lowerApp = normalize(appName);
        if (lowerApp.length() == 0) {
            return null;
        }

        String aliasResult = openKnownAppAlias(context, lowerApp, output);
        if (aliasResult != null) {
            return aliasResult;
        }

        if (lowerApp.equals("camera")) {
            if (openCamera(context, output)) {
                return "Camera";
            }
        }
        if (lowerApp.equals("phone") || lowerApp.equals("dialer")) {
            openDialer(context, "", output);
            return "Phone";
        }

        try {
            PackageManager packageManager = context.getPackageManager();
            Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
            mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            int queryFlags = 0;
            if (Build.VERSION.SDK_INT >= 23) {
                queryFlags = PackageManager.MATCH_ALL;
            }
            List<ResolveInfo> apps = packageManager.queryIntentActivities(mainIntent, queryFlags);
            ResolveInfo best = null;
            int bestScore = 0;
            String bestLabel = null;

            for (int i = 0; i < apps.size(); i++) {
                ResolveInfo info = apps.get(i);
                if (info == null || info.activityInfo == null) {
                    continue;
                }
                CharSequence labelChars = info.loadLabel(packageManager);
                String label = labelChars == null ? "" : labelChars.toString();
                String packageName = info.activityInfo.packageName == null ? "" : info.activityInfo.packageName;
                String normalizedLabel = normalize(label);
                String normalizedPackage = normalize(packageName);
                int score = scoreAppMatch(lowerApp, normalizedLabel, normalizedPackage);
                if (score > bestScore) {
                    bestScore = score;
                    best = info;
                    bestLabel = label;
                }
            }

            if (best != null && bestScore >= 240) {
                Intent launchIntent = packageManager.getLaunchIntentForPackage(best.activityInfo.packageName);
                if (launchIntent == null) {
                    launchIntent = new Intent(Intent.ACTION_MAIN);
                    launchIntent.addCategory(Intent.CATEGORY_LAUNCHER);
                    launchIntent.setClassName(best.activityInfo.packageName, best.activityInfo.name);
                }
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivityCompat(context, launchIntent, output, "app launch");
                return bestLabel == null || bestLabel.length() == 0 ? appName : bestLabel;
            }
        } catch (Exception error) {
            log(output, "APP: Unable to open app: " + error.getMessage());
        }
        return null;
    }

    private static String openKnownAppAlias(Context context, String lowerApp, JarvisOutput output) {
        if (lowerApp.equals("settings") || lowerApp.equals("setting") || lowerApp.equals("androidsettings") || lowerApp.equals("phonesettings") || lowerApp.equals("systemsettings")) {
            openAndroidSettings(context, Settings.ACTION_SETTINGS, output);
            return "Android settings";
        }
        if (lowerApp.equals("wifi") || lowerApp.equals("wifisettings") || lowerApp.equals("wireless")) {
            openAndroidSettings(context, Settings.ACTION_WIFI_SETTINGS, output);
            return "Wi-Fi settings";
        }
        if (lowerApp.equals("bluetooth") || lowerApp.equals("bluetoothsettings")) {
            openAndroidSettings(context, Settings.ACTION_BLUETOOTH_SETTINGS, output);
            return "Bluetooth settings";
        }
        if (lowerApp.equals("youtube") || lowerApp.equals("yt")) {
            return openFirstPackage(context, new String[] { "com.google.android.youtube" }, "YouTube", output);
        }
        if (lowerApp.equals("maps") || lowerApp.equals("googlemaps")) {
            return openFirstPackage(context, new String[] { "com.google.android.apps.maps" }, "Google Maps", output);
        }
        if (lowerApp.equals("chrome") || lowerApp.equals("googlechrome")) {
            return openFirstPackage(context, new String[] { "com.android.chrome", "com.chrome.beta", "com.chrome.dev" }, "Chrome", output);
        }
        if (lowerApp.equals("gmail") || lowerApp.equals("googlemail")) {
            return openFirstPackage(context, new String[] { "com.google.android.gm" }, "Gmail", output);
        }
        if (lowerApp.equals("playstore") || lowerApp.equals("googleplay") || lowerApp.equals("store")) {
            return openFirstPackage(context, new String[] { "com.android.vending" }, "Play Store", output);
        }
        if (lowerApp.equals("photos") || lowerApp.equals("googlephotos")) {
            return openFirstPackage(context, new String[] { "com.google.android.apps.photos" }, "Google Photos", output);
        }
        if (lowerApp.equals("messages") || lowerApp.equals("sms") || lowerApp.equals("textmessages")) {
            return openFirstPackage(context, new String[] { "com.google.android.apps.messaging", "com.android.mms" }, "Messages", output);
        }
        if (lowerApp.equals("whatsapp") || lowerApp.equals("whatsap")) {
            return openFirstPackage(context, new String[] { "com.whatsapp", "com.whatsapp.w4b" }, "WhatsApp", output);
        }
        if (lowerApp.equals("facebook")) {
            return openFirstPackage(context, new String[] { "com.facebook.katana" }, "Facebook", output);
        }
        if (lowerApp.equals("messenger") || lowerApp.equals("facebookmessenger")) {
            return openFirstPackage(context, new String[] { "com.facebook.orca" }, "Messenger", output);
        }
        if (lowerApp.equals("instagram")) {
            return openFirstPackage(context, new String[] { "com.instagram.android" }, "Instagram", output);
        }
        if (lowerApp.equals("tiktok") || lowerApp.equals("ticktock")) {
            return openFirstPackage(context, new String[] { "com.zhiliaoapp.musically" }, "TikTok", output);
        }
        if (lowerApp.equals("spotify")) {
            return openFirstPackage(context, new String[] { "com.spotify.music" }, "Spotify", output);
        }
        if (lowerApp.equals("netflix")) {
            return openFirstPackage(context, new String[] { "com.netflix.mediaclient" }, "Netflix", output);
        }
        if (lowerApp.equals("calculator")) {
            return openFirstPackage(context, new String[] { "com.google.android.calculator", "com.android.calculator2", "com.sec.android.app.popupcalculator" }, "Calculator", output);
        }
        if (lowerApp.equals("calendar")) {
            return openFirstPackage(context, new String[] { "com.google.android.calendar", "com.android.calendar" }, "Calendar", output);
        }
        if (lowerApp.equals("contacts")) {
            return openFirstPackage(context, new String[] { "com.google.android.contacts", "com.android.contacts" }, "Contacts", output);
        }
        if (lowerApp.equals("clock") || lowerApp.equals("alarm") || lowerApp.equals("alarms")) {
            return openFirstPackage(context, new String[] { "com.google.android.deskclock", "com.android.deskclock" }, "Clock", output);
        }
        if (lowerApp.equals("files") || lowerApp.equals("filemanager") || lowerApp.equals("myfiles")) {
            return openFirstPackage(context, new String[] { "com.google.android.documentsui", "com.sec.android.app.myfiles", "com.android.documentsui" }, "Files", output);
        }
        if (lowerApp.equals("telegram")) {
            return openFirstPackage(context, new String[] { "org.telegram.messenger", "org.thunderdog.challegram" }, "Telegram", output);
        }
        if (lowerApp.equals("snapchat")) {
            return openFirstPackage(context, new String[] { "com.snapchat.android" }, "Snapchat", output);
        }
        if (lowerApp.equals("x") || lowerApp.equals("twitter")) {
            return openFirstPackage(context, new String[] { "com.twitter.android" }, "X", output);
        }
        if (lowerApp.equals("reddit")) {
            return openFirstPackage(context, new String[] { "com.reddit.frontpage" }, "Reddit", output);
        }
        if (lowerApp.equals("discord")) {
            return openFirstPackage(context, new String[] { "com.discord" }, "Discord", output);
        }
        if (lowerApp.equals("amazon")) {
            return openFirstPackage(context, new String[] { "com.amazon.mShop.android.shopping" }, "Amazon", output);
        }
        if (lowerApp.equals("ebay")) {
            return openFirstPackage(context, new String[] { "com.ebay.mobile" }, "eBay", output);
        }
        if (lowerApp.equals("paypal")) {
            return openFirstPackage(context, new String[] { "com.paypal.android.p2pmobile" }, "PayPal", output);
        }
        if (lowerApp.equals("gallery")) {
            return openFirstPackage(context, new String[] { "com.google.android.apps.photos", "com.sec.android.gallery3d", "com.miui.gallery", "com.android.gallery3d" }, "Gallery", output);
        }
        if (lowerApp.equals("browser") || lowerApp.equals("internet")) {
            return openFirstPackage(context, new String[] { "com.android.chrome", "com.sec.android.app.sbrowser", "org.mozilla.firefox", "com.opera.browser", "com.brave.browser" }, "Browser", output);
        }
        if (lowerApp.equals("termux")) {
            return openFirstPackage(context, new String[] { "com.termux" }, "Termux", output);
        }
        if (lowerApp.equals("aide")) {
            return openFirstPackage(context, new String[] { "com.aide.ui", "io.github.zeroaicy.aide" }, "AIDE", output);
        }
        return null;
    }

    private static String openFirstPackage(Context context, String[] packageNames, String friendlyName, JarvisOutput output) {
        PackageManager packageManager = context.getPackageManager();
        for (int i = 0; i < packageNames.length; i++) {
            try {
                Intent launchIntent = packageManager.getLaunchIntentForPackage(packageNames[i]);
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivityCompat(context, launchIntent, output, "app launch");
                    return friendlyName;
                }
            } catch (Exception error) {
                log(output, "APP: Alias launch failed for " + packageNames[i] + ": " + error.getMessage());
            }
        }
        return null;
    }

    private static boolean openCamera(Context context, JarvisOutput output) {
        try {
            Intent intent = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivityCompat(context, intent, output, "activity launch");
            return true;
        } catch (Exception error) {
            log(output, "CAMERA: Unable to open direct camera: " + error.getMessage());
        }
        return false;
    }

    private static boolean startActivityCompat(Context context, Intent intent, JarvisOutput output, String label) {
        if (context == null || intent == null) {
            return false;
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        boolean launched = false;
        if (context instanceof Service) {
            launched = launchViaPendingIntent(context, intent, output, label);
            if (launched) {
                return true;
            }
        }

        try {
            context.startActivity(intent);
            return true;
        } catch (Exception directError) {
            log(output, "LAUNCH: Direct " + label + " failed: " + directError.getMessage());
        }

        if (!(context instanceof Service)) {
            launched = launchViaPendingIntent(context, intent, output, label);
            if (launched) {
                return true;
            }
        }
        return false;
    }

    private static boolean launchViaPendingIntent(Context context, Intent sourceIntent, JarvisOutput output, String label) {
        try {
            Intent intent = new Intent(sourceIntent);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }
            PendingIntent pendingIntent = PendingIntent.getActivity(context, nextLaunchRequestCode(), intent, flags);
            pendingIntent.send();
            return true;
        } catch (Exception error) {
            log(output, "LAUNCH: PendingIntent " + label + " failed: " + error.getMessage());
            return false;
        }
    }

    private static synchronized int nextLaunchRequestCode() {
        launchRequestCounter++;
        if (launchRequestCounter > 900000) {
            launchRequestCounter = 4100;
        }
        return launchRequestCounter;
    }

    private static int scoreAppMatch(String target, String label, String packageName) {
        if (label.equals(target)) {
            return 1000;
        }
        if (label.startsWith(target)) {
            return 850 - Math.abs(label.length() - target.length());
        }
        if (label.indexOf(target) >= 0) {
            return 650 - Math.abs(label.length() - target.length());
        }
        if (target.indexOf(label) >= 0 && label.length() > 2) {
            return 500;
        }
        if (packageName.indexOf(target) >= 0) {
            return 350;
        }
        if (target.length() > 3 && label.length() > 3) {
            int partial = roughSharedCharacterScore(target, label);
            if (partial >= 240) {
                return partial;
            }
        }
        return 0;
    }

    private static int roughSharedCharacterScore(String target, String label) {
        int hits = 0;
        int lastIndex = -1;
        for (int i = 0; i < target.length(); i++) {
            char c = target.charAt(i);
            int index = label.indexOf(c, lastIndex + 1);
            if (index >= 0) {
                hits++;
                lastIndex = index;
            }
        }
        if (hits >= target.length() - 1 && target.length() >= 4) {
            return 260 + hits;
        }
        return 0;
    }

    private static String getBatteryStatus(Context context) {
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, filter);
        if (batteryStatus == null) {
            return "I could not read the battery level.";
        }
        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int plugged = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        int percent = 0;
        if (level >= 0 && scale > 0) {
            percent = (int) ((level * 100.0f) / scale);
        }
        String charging = plugged == 0 ? "not charging" : "charging";
        return "Battery level is " + percent + " percent and the device is " + charging + ".";
    }

    private static boolean containsAny(String text, String[] words) {
        for (int i = 0; i < words.length; i++) {
            if (text.indexOf(words[i]) >= 0) {
                return true;
            }
        }
        return false;
    }

    private static int extractFirstNumber(String text) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '0' && c <= '9') {
                builder.append(c);
            } else if (builder.length() > 0) {
                break;
            }
        }
        if (builder.length() == 0) {
            return -1;
        }
        try {
            return Integer.parseInt(builder.toString());
        } catch (Exception ignored) {
            return -1;
        }
    }

    private static String cleanTrailingPoliteWords(String text) {
        String result = text == null ? "" : text.trim();
        String lower = result.toLowerCase(Locale.UK);
        String[] endings = new String[] { " please", " for me", " now", " thanks", " thank you" };
        boolean changed = true;
        while (changed) {
            changed = false;
            lower = result.toLowerCase(Locale.UK);
            for (int i = 0; i < endings.length; i++) {
                if (lower.endsWith(endings[i])) {
                    result = result.substring(0, result.length() - endings[i].length()).trim();
                    changed = true;
                    break;
                }
            }
        }
        return result;
    }

    private static boolean isQuestion(String lower) {
        return lower.startsWith("what ") || lower.startsWith("who ") || lower.startsWith("where ") || lower.startsWith("when ") || lower.startsWith("why ") || lower.startsWith("how ") || lower.endsWith("?");
    }

    private static String normalize(String text) {
        String lower = text == null ? "" : text.toLowerCase(Locale.UK);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    private static String removeLeadingPunctuation(String text) {
        String result = text == null ? "" : text.trim();
        while (result.startsWith(",") || result.startsWith(".") || result.startsWith(":") || result.startsWith(";") || result.startsWith("-")) {
            result = result.substring(1).trim();
        }
        return result;
    }

    private static void log(JarvisOutput output, String line) {
        if (output != null) {
            output.onConsole(line);
        }
    }
}
