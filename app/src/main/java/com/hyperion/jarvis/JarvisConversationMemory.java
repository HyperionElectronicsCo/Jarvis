package com.hyperion.jarvis;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class JarvisConversationMemory {
    private static final String PREF_HISTORY = "conversation_history_v31";
    private static final String PREF_LAST_USER = "conversation_last_user_v31";
    private static final int MAX_TURNS = 10;
    private static final int MAX_ENTRY_CHARS = 900;
    private static final int MAX_CONTEXT_CHARS = 3800;

    private JarvisConversationMemory() {
    }

    public static void rememberUser(Context context, String text) {
        remember(context, "USER", text);
        if (context != null && isSafeToStore(text)) {
            context.getSharedPreferences(JarvisCommandCenter.PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(PREF_LAST_USER, shorten(text, MAX_ENTRY_CHARS))
                    .commit();
        }
    }

    public static void rememberAssistant(Context context, String text) {
        remember(context, "JARVIS", text);
    }

    public static void clear(Context context) {
        if (context == null) {
            return;
        }
        context.getSharedPreferences(JarvisCommandCenter.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(PREF_HISTORY)
                .remove(PREF_LAST_USER)
                .commit();
    }

    public static String getLastUserRequest(Context context) {
        if (context == null) {
            return "";
        }
        String value = context.getSharedPreferences(JarvisCommandCenter.PREFS_NAME, Context.MODE_PRIVATE)
                .getString(PREF_LAST_USER, "");
        return value == null ? "" : value;
    }

    public static String getPreviousUserRequest(Context context, String currentPrompt) {
        if (context == null) {
            return "";
        }
        String history = context.getSharedPreferences(JarvisCommandCenter.PREFS_NAME, Context.MODE_PRIVATE)
                .getString(PREF_HISTORY, "");
        if (history == null || history.length() == 0) {
            return "";
        }
        String current = sanitize(shorten(currentPrompt, MAX_ENTRY_CHARS));
        String[] lines = history.split("\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i];
            int marker = line.indexOf("USER: ");
            if (marker < 0) {
                continue;
            }
            String value = line.substring(marker + 6).trim();
            if (value.length() == 0) {
                continue;
            }
            if (current.length() > 0 && value.equals(current)) {
                continue;
            }
            return value;
        }
        return "";
    }

    public static String buildContextText(Context context, String currentPrompt) {
        if (context == null) {
            return "";
        }
        String history = context.getSharedPreferences(JarvisCommandCenter.PREFS_NAME, Context.MODE_PRIVATE)
                .getString(PREF_HISTORY, "");
        if (history == null || history.trim().length() == 0) {
            return "";
        }
        if (history.length() > MAX_CONTEXT_CHARS) {
            history = history.substring(history.length() - MAX_CONTEXT_CHARS);
            int firstLine = history.indexOf('\n');
            if (firstLine >= 0 && firstLine + 1 < history.length()) {
                history = history.substring(firstLine + 1);
            }
        }
        StringBuilder builder = new StringBuilder();
        builder.append("Recent Jarvis conversation memory. Use this to understand follow-up requests, pronouns such as it/that/this, and corrections. If the current request could refer to one earlier thing but is still ambiguous, ask a short confirmation.\n");
        builder.append(history.trim());
        return builder.toString();
    }

    public static boolean looksLikeFollowUpReference(String lower) {
        if (lower == null) {
            return false;
        }
        String value = lower.trim().toLowerCase(Locale.UK);
        if (value.length() == 0) {
            return false;
        }
        if (value.equals("yes") || value.equals("yeah") || value.equals("do that") || value.equals("that one") || value.equals("use that")) {
            return true;
        }
        if (value.indexOf("previous") >= 0 || value.indexOf("earlier") >= 0 || value.indexOf("above") >= 0 || value.indexOf("last one") >= 0 || value.indexOf("last request") >= 0) {
            return true;
        }
        if (value.indexOf(" it ") >= 0 || value.startsWith("it ") || value.endsWith(" it")) {
            return true;
        }
        if (value.indexOf(" that ") >= 0 || value.startsWith("that ") || value.endsWith(" that")) {
            return true;
        }
        if (value.indexOf(" this ") >= 0 || value.startsWith("this ") || value.endsWith(" this")) {
            return true;
        }
        if ((value.indexOf("zip") >= 0 || value.indexOf("package") >= 0 || value.indexOf("packaged") >= 0) && (value.indexOf("make") >= 0 || value.indexOf("create") >= 0 || value.indexOf("put") >= 0 || value.indexOf("need") >= 0 || value.indexOf("instead") >= 0)) {
            return true;
        }
        return false;
    }

    public static String buildFollowUpPrompt(Context context, String currentPrompt) {
        String previous = getPreviousUserRequest(context, currentPrompt);
        if (previous.length() == 0) {
            previous = getLastUserRequest(context);
        }
        if (previous.length() == 0) {
            return currentPrompt;
        }
        return "The user is making a follow-up request. Previous user request: \"" + previous + "\". Current user request: \"" + currentPrompt + "\". Use the previous request as context if the current request says it, that, this, previous, package, or zip. If it is clearly referring to the previous request, continue without asking. If it is not clear, ask a short confirmation.";
    }

    private static void remember(Context context, String speaker, String text) {
        if (context == null || !isSafeToStore(text)) {
            return;
        }
        String entry = buildEntry(speaker, text);
        SharedPreferences prefs = context.getSharedPreferences(JarvisCommandCenter.PREFS_NAME, Context.MODE_PRIVATE);
        String history = prefs.getString(PREF_HISTORY, "");
        if (history == null) {
            history = "";
        }
        String combined = history.length() == 0 ? entry : history + "\n" + entry;
        combined = keepLastTurns(combined, MAX_TURNS * 2);
        prefs.edit().putString(PREF_HISTORY, combined).commit();
    }

    private static String buildEntry(String speaker, String text) {
        String stamp = new SimpleDateFormat("HH:mm", Locale.UK).format(new Date());
        return "[" + stamp + "] " + speaker + ": " + sanitize(shorten(text, MAX_ENTRY_CHARS));
    }

    private static String keepLastTurns(String text, int maxLines) {
        String[] lines = text.split("\\n");
        if (lines.length <= maxLines) {
            return text;
        }
        StringBuilder builder = new StringBuilder();
        int start = lines.length - maxLines;
        for (int i = start; i < lines.length; i++) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(lines[i]);
        }
        return builder.toString();
    }

    private static boolean isSafeToStore(String text) {
        if (text == null) {
            return false;
        }
        String value = text.trim();
        if (value.length() == 0) {
            return false;
        }
        String lower = value.toLowerCase(Locale.UK);
        if (lower.indexOf("sk-") >= 0 || lower.indexOf("sk_proj") >= 0 || lower.indexOf("sk-proj") >= 0 || lower.indexOf("sk-bl") >= 0 || lower.indexOf("api key") >= 0 || lower.indexOf("set ai key") >= 0 || lower.indexOf("password") >= 0 || lower.indexOf("token-") >= 0 || lower.indexOf("bearer-") >= 0) {
            return false;
        }
        return true;
    }

    private static String shorten(String text, int limit) {
        if (text == null) {
            return "";
        }
        String value = text.trim();
        if (value.length() <= limit) {
            return value;
        }
        return value.substring(0, limit).trim() + "...";
    }

    private static String sanitize(String text) {
        if (text == null) {
            return "";
        }
        return text.replace('\r', ' ').replace('\n', ' ').trim();
    }
}
