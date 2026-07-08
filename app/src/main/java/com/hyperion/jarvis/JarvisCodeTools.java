package com.hyperion.jarvis;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        return context.getSharedPreferences(JarvisCommandCenter.PREFS_NAME, Context.MODE_PRIVATE)
                .getString(PREF_LAST_CODE_SNIPPET, "");
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
        return containsAny(value, new String[] {
                " code", "script", "java", "xml", "layout", "snippet", "source code",
                "termux", "bash", "shell script", "androidmanifest", "manifest",
                "make me a", "write me a", "give me the code", "give me code", "example project"
        });
    }

    public static String extractFirstCodeBlock(String text) {
        if (text == null || text.length() == 0) {
            return "";
        }
        int index = 0;
        String best = "";
        while (true) {
            int start = text.indexOf("```", index);
            if (start < 0) {
                break;
            }
            int afterTicks = start + 3;
            int lineEnd = text.indexOf('\n', afterTicks);
            if (lineEnd < 0) {
                break;
            }
            int end = text.indexOf("```", lineEnd + 1);
            if (end < 0) {
                break;
            }
            String block = text.substring(lineEnd + 1, end);
            String marker = text.substring(afterTicks, lineEnd).trim();
            if (block.length() > 0 && block.length() >= best.length()) {
                if (!isLanguageMarker(marker) && marker.length() > 0) {
                    block = marker + "\n" + block;
                }
                best = trimBlankLines(block);
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
            int start = text.indexOf("```", index);
            if (start < 0) {
                builder.append(text.substring(index));
                break;
            }
            builder.append(text.substring(index, start));
            int lineEnd = text.indexOf('\n', start + 3);
            if (lineEnd < 0) {
                break;
            }
            int end = text.indexOf("```", lineEnd + 1);
            if (end < 0) {
                break;
            }
            index = end + 3;
        }
        return builder.toString();
    }

    public static String buildSpokenSummaryForCode(String text) {
        String stripped = stripCodeBlocks(text).replace('\r', ' ').replace('\n', ' ').trim();
        while (stripped.indexOf("  ") >= 0) {
            stripped = stripped.replace("  ", " ");
        }
        if (stripped.length() == 0) {
            return "I prepared the code snippet.";
        }
        if (stripped.length() > 220) {
            stripped = stripped.substring(0, 220).trim() + "...";
        }
        return stripped;
    }

    public static CharSequence highlightCodeSnippet(String code) {
        String safe = code == null ? "" : code;
        SpannableStringBuilder builder = new SpannableStringBuilder(safe);
        if (safe.length() == 0) {
            return builder;
        }
        applyComments(builder, safe);
        applyStrings(builder, safe);
        if (looksLikeXml(safe)) {
            applyPattern(builder, safe, Pattern.compile("</?[A-Za-z0-9_:-]+"), Color.rgb(97, 175, 239), Typeface.BOLD);
            applyPattern(builder, safe, Pattern.compile("\\b[A-Za-z_:][A-Za-z0-9_:-]*(?=\\=)"), Color.rgb(209, 154, 102), Typeface.BOLD);
            applyPattern(builder, safe, Pattern.compile("(?<![A-Za-z0-9_])(true|false|null)(?![A-Za-z0-9_])"), Color.rgb(198, 120, 221), Typeface.BOLD);
        } else {
            String[] keywords = new String[] {
                    "public", "private", "protected", "class", "static", "final", "void", "int", "long", "float", "double",
                    "boolean", "char", "byte", "short", "new", "return", "if", "else", "for", "while", "do", "switch", "case",
                    "break", "continue", "try", "catch", "finally", "throw", "throws", "extends", "implements", "import", "package",
                    "null", "true", "false", "this", "super", "String", "Context", "Activity", "Intent", "View", "LinearLayout",
                    "echo", "cd", "mkdir", "cp", "mv", "rm", "chmod", "bash", "sh", "fi", "then", "done", "function", "export",
                    "gradle", "android", "manifest"
            };
            applyKeywordSet(builder, safe, keywords, Color.rgb(198, 120, 221), Typeface.BOLD);
            applyPattern(builder, safe, Pattern.compile("(?<![A-Za-z0-9_])@[A-Za-z0-9_]+"), Color.rgb(97, 175, 239), Typeface.BOLD);
        }
        applyPattern(builder, safe, Pattern.compile("(?<![A-Za-z0-9_])(0x[0-9A-Fa-f]+|\\d+)(?![A-Za-z0-9_])"), Color.rgb(229, 192, 123), Typeface.NORMAL);
        return builder;
    }

    private static boolean looksLikeXml(String code) {
        String safe = code == null ? "" : code.trim();
        return safe.startsWith("<") || safe.indexOf("</") >= 0 || safe.indexOf("/>") >= 0;
    }

    private static void applyComments(SpannableStringBuilder builder, String code) {
        applyPattern(builder, code, Pattern.compile("//[^\n\r]*"), Color.rgb(92, 99, 112), Typeface.ITALIC);
        applyPattern(builder, code, Pattern.compile("/\\*([\\s\\S]*?)\\*/"), Color.rgb(92, 99, 112), Typeface.ITALIC);
        applyPattern(builder, code, Pattern.compile("(?m)^\\s*#.*$"), Color.rgb(92, 99, 112), Typeface.ITALIC);
    }

    private static void applyStrings(SpannableStringBuilder builder, String code) {
        applyPattern(builder, code, Pattern.compile("\"([^\"\\\\]|\\\\.)*\""), Color.rgb(152, 195, 121), Typeface.NORMAL);
        applyPattern(builder, code, Pattern.compile("'([^'\\\\]|\\\\.)*'"), Color.rgb(152, 195, 121), Typeface.NORMAL);
    }

    private static void applyKeywordSet(SpannableStringBuilder builder, String code, String[] keywords, int color, int style) {
        StringBuilder joined = new StringBuilder();
        for (int i = 0; i < keywords.length; i++) {
            if (i > 0) {
                joined.append("|");
            }
            joined.append(Pattern.quote(keywords[i]));
        }
        Pattern keywordPattern = Pattern.compile("(?<![A-Za-z0-9_])(" + joined.toString() + ")(?![A-Za-z0-9_])");
        applyPattern(builder, code, keywordPattern, color, style);
    }

    private static void applyPattern(SpannableStringBuilder builder, String code, Pattern pattern, int color, int style) {
        Matcher matcher = pattern.matcher(code);
        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();
            if (start >= 0 && end > start && end <= builder.length()) {
                builder.setSpan(new ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                if (style != Typeface.NORMAL) {
                    builder.setSpan(new StyleSpan(style), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
        }
    }

    private static boolean containsAny(String value, String[] matches) {
        if (value == null || matches == null) {
            return false;
        }
        for (int i = 0; i < matches.length; i++) {
            if (value.indexOf(matches[i]) >= 0) {
                return true;
            }
        }
        return false;
    }

    public static boolean isLanguageMarker(String value) {
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
