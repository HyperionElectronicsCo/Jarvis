package com.hyperion.jarvis;

import android.content.Context;

import java.util.Locale;

public final class JarvisCodeTools {
    private static final String PREF_LAST_CODE_SNIPPET = "last_ai_code_snippet_v29";

    private JarvisCodeTools() {
    }

    public static void saveLastCodeSnippet(Context context, String code) {
        if (context == null || code == null || code.length() == 0) {
            return;
        }
        context.getSharedPreferences(JarvisCommandCenter.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(PREF_LAST_CODE_SNIPPET, code)
                .commit();
    }

    public static String getLastCodeSnippet(Context context) {
        if (context == null) {
            return "";
        }
        String value = context.getSharedPreferences(JarvisCommandCenter.PREFS_NAME, Context.MODE_PRIVATE)
                .getString(PREF_LAST_CODE_SNIPPET, "");
        return value == null ? "" : value;
    }

    public static void clearLastCodeSnippet(Context context) {
        if (context == null) {
            return;
        }
        context.getSharedPreferences(JarvisCommandCenter.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(PREF_LAST_CODE_SNIPPET)
                .commit();
    }

    public static boolean looksLikeCodeRequest(String lower) {
        if (lower == null) {
            return false;
        }
        String value = lower.toLowerCase(Locale.UK);
        return value.indexOf("code") >= 0
                || value.indexOf("script") >= 0
                || value.indexOf("function") >= 0
                || value.indexOf("class ") >= 0
                || value.indexOf("java") >= 0
                || value.indexOf("python") >= 0
                || value.indexOf("bash") >= 0
                || value.indexOf("shell") >= 0
                || value.indexOf("termux") >= 0
                || value.indexOf("xml") >= 0
                || value.indexOf("html") >= 0
                || value.indexOf("css") >= 0
                || value.indexOf("javascript") >= 0
                || value.indexOf("gradle") >= 0
                || value.indexOf("manifest") >= 0
                || value.indexOf("snippet") >= 0
                || value.indexOf("compile") >= 0
                || value.indexOf("fix this error") >= 0
                || value.indexOf("give me the code") >= 0
                || value.indexOf("write me code") >= 0
                || value.indexOf("make me code") >= 0
                || value.indexOf("create code") >= 0
                || value.indexOf("apk") >= 0
                || value.indexOf("aide") >= 0;
    }

    public static String extractFirstCodeBlock(String text) {
        if (text == null) {
            return "";
        }
        String best = "";
        int index = 0;
        while (index < text.length()) {
            int first = text.indexOf("```", index);
            if (first < 0) {
                break;
            }
            int start = first + 3;
            int end = text.indexOf("```", start);
            if (end < 0) {
                break;
            }
            String inside = text.substring(start, end);
            inside = inside.replace('\r', '\n');
            int newline = inside.indexOf('\n');
            if (newline >= 0) {
                String possibleLanguage = inside.substring(0, newline).trim();
                if (isLanguageMarker(possibleLanguage)) {
                    inside = inside.substring(newline + 1);
                }
            }
            inside = trimBlankLines(inside);
            if (inside.length() > best.length()) {
                best = inside;
            }
            index = end + 3;
        }
        return best;
    }

    public static String stripCodeBlocks(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        int index = 0;
        while (index < text.length()) {
            int first = text.indexOf("```", index);
            if (first < 0) {
                builder.append(text.substring(index));
                break;
            }
            builder.append(text.substring(index, first));
            int end = text.indexOf("```", first + 3);
            if (end < 0) {
                break;
            }
            index = end + 3;
        }
        String result = builder.toString().replace('\r', ' ').replace('\n', ' ').trim();
        while (result.indexOf("  ") >= 0) {
            result = result.replace("  ", " ");
        }
        return result;
    }

    public static String buildSpokenSummaryForCode(String fullText) {
        String stripped = stripCodeBlocks(fullText);
        if (stripped.length() == 0) {
            return "I have prepared the code snippet below. Press Copy Code to copy it.";
        }
        if (stripped.length() > 220) {
            stripped = stripped.substring(0, 220).trim() + "...";
        }
        if (stripped.toLowerCase(Locale.UK).indexOf("copy code") < 0) {
            stripped = stripped + " Copy Code is available below.";
        }
        return stripped;
    }

    private static boolean isLanguageMarker(String value) {
        if (value == null) {
            return false;
        }
        String v = value.trim().toLowerCase(Locale.UK);
        if (v.length() == 0 || v.length() > 24) {
            return false;
        }
        return v.equals("java") || v.equals("python") || v.equals("bash") || v.equals("sh")
                || v.equals("shell") || v.equals("xml") || v.equals("html") || v.equals("css")
                || v.equals("javascript") || v.equals("js") || v.equals("json") || v.equals("gradle")
                || v.equals("kotlin") || v.equals("c") || v.equals("cpp") || v.equals("c++")
                || v.equals("sql") || v.equals("php") || v.equals("txt") || v.equals("text")
                || v.equals("groovy");
    }

    private static String trimBlankLines(String text) {
        if (text == null) {
            return "";
        }
        String value = text;
        while (value.startsWith("\n")) {
            value = value.substring(1);
        }
        while (value.endsWith("\n")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
}
