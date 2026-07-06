package com.hyperion.jarvis;

import android.content.Context;
import android.net.Uri;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class JarvisProjectPackager {
    public static final int REQUEST_STORAGE_PERMISSION = 6628;

    private static final String PROJECT_MARKER = "JARVIS_PROJECT_ZIP:";
    private static final String FILE_MARKER = "JARVIS_FILE:";
    private static final String PREF_PENDING_PROJECT_PACKAGE = "pending_project_package_v29";

    private JarvisProjectPackager() {
    }

    public static boolean looksLikeProjectZipRequest(String text) {
        if (text == null) {
            return false;
        }
        String value = text.toLowerCase(Locale.UK);
        boolean mentionsProject = value.indexOf("project") >= 0;
        if (mentionsProject && (value.indexOf("android") >= 0 || value.indexOf("java") >= 0 || value.indexOf("aide") >= 0 || value.indexOf(" app") >= 0 || value.indexOf("application") >= 0 || value.indexOf("game") >= 0)) {
            return true;
        }
        return value.indexOf("packaged zip") >= 0
                || value.indexOf("package zip") >= 0
                || value.indexOf("package a zip") >= 0
                || value.indexOf("make a zip") >= 0
                || value.indexOf("create a zip") >= 0
                || value.indexOf("build a zip") >= 0
                || value.indexOf("zip project") >= 0
                || value.indexOf("project zip") >= 0
                || value.indexOf("create me a project") >= 0
                || value.indexOf("make me a project") >= 0
                || value.indexOf("build me a project") >= 0
                || value.indexOf("make a project") >= 0
                || value.indexOf("create a project") >= 0
                || value.indexOf("full project") >= 0
                || value.indexOf("complete project") >= 0
                || value.indexOf("aide project") >= 0
                || value.indexOf("android project") >= 0
                || value.indexOf("android app project") >= 0
                || value.indexOf("full app") >= 0
                || value.indexOf("complete app") >= 0
                || value.indexOf("full android app") >= 0
                || value.indexOf("complete android app") >= 0
                || value.indexOf("android game") >= 0
                || value.indexOf("game project") >= 0
                || value.indexOf("game example") >= 0
                || value.indexOf("checkers game") >= 0;
    }

    public static boolean containsProjectPackage(String answerText) {
        return answerText != null && answerText.indexOf(FILE_MARKER) >= 0;
    }

    public static boolean rememberPendingProject(Context context, String answerText) {
        if (context == null || !containsProjectPackage(answerText)) {
            return false;
        }
        return context.getSharedPreferences(JarvisCommandCenter.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(PREF_PENDING_PROJECT_PACKAGE, answerText)
                .commit();
    }

    public static String getPendingProject(Context context) {
        if (context == null) {
            return "";
        }
        String value = context.getSharedPreferences(JarvisCommandCenter.PREFS_NAME, Context.MODE_PRIVATE)
                .getString(PREF_PENDING_PROJECT_PACKAGE, "");
        return value == null ? "" : value;
    }

    public static void clearPendingProject(Context context) {
        if (context == null) {
            return;
        }
        context.getSharedPreferences(JarvisCommandCenter.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(PREF_PENDING_PROJECT_PACKAGE)
                .commit();
    }

    public static int countProjectFiles(String answerText) {
        return parseProjectFiles(answerText).size();
    }

    public static String getSuggestedZipName(String answerText) {
        return extractZipName(answerText);
    }

    public static String writeProjectZipToUri(Context context, Uri uri, String answerText) {
        if (context == null) {
            return "Save failed: no Android context available.";
        }
        if (uri == null) {
            return "Save cancelled: no destination was selected.";
        }
        ArrayList<ProjectFile> files = parseProjectFiles(answerText);
        if (files.size() == 0) {
            return "Save failed: Jarvis could not find any project files in the AI answer.";
        }
        OutputStream output = null;
        ZipOutputStream zip = null;
        try {
            output = context.getContentResolver().openOutputStream(uri);
            if (output == null) {
                return "Save failed: Android did not provide a writable file.";
            }
            zip = new ZipOutputStream(output);
            for (int i = 0; i < files.size(); i++) {
                ProjectFile projectFile = files.get(i);
                ZipEntry entry = new ZipEntry(projectFile.path);
                entry.setTime(System.currentTimeMillis());
                zip.putNextEntry(entry);
                byte[] data = projectFile.content.getBytes("UTF-8");
                zip.write(data);
                zip.closeEntry();
            }
            zip.finish();
            return "Project ZIP saved with " + files.size() + " file" + (files.size() == 1 ? "" : "s") + ".";
        } catch (Exception error) {
            return "Save failed: " + safeMessage(error);
        } finally {
            if (zip != null) {
                try {
                    zip.close();
                } catch (Exception ignored) {
                }
            } else if (output != null) {
                try {
                    output.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static ArrayList<ProjectFile> parseProjectFiles(String text) {
        ArrayList<ProjectFile> files = new ArrayList<ProjectFile>();
        if (text == null) {
            return files;
        }
        int index = 0;
        while (index < text.length() && files.size() < 160) {
            int marker = text.indexOf(FILE_MARKER, index);
            if (marker < 0) {
                break;
            }
            int pathStart = marker + FILE_MARKER.length();
            int pathEnd = findLineEnd(text, pathStart);
            String path = sanitizePath(text.substring(pathStart, pathEnd).trim());
            int fenceStart = text.indexOf("```", pathEnd);
            if (path.length() == 0 || fenceStart < 0) {
                index = pathEnd;
                continue;
            }
            int contentStart = fenceStart + 3;
            int firstNewLine = text.indexOf('\n', contentStart);
            if (firstNewLine >= 0 && firstNewLine - contentStart <= 32) {
                String language = text.substring(contentStart, firstNewLine).trim();
                if (isLanguageMarker(language)) {
                    contentStart = firstNewLine + 1;
                }
            }
            int fenceEnd = text.indexOf("```", contentStart);
            if (fenceEnd < 0) {
                index = contentStart;
                continue;
            }
            String content = trimBlankLines(text.substring(contentStart, fenceEnd).replace('\r', '\n'));
            if (content.length() > 0) {
                files.add(new ProjectFile(path, content));
            }
            index = fenceEnd + 3;
        }
        return files;
    }

    private static int findLineEnd(String text, int start) {
        int end = text.indexOf('\n', start);
        if (end < 0) {
            return text.length();
        }
        return end;
    }

    private static String extractZipName(String text) {
        if (text != null) {
            int marker = text.indexOf(PROJECT_MARKER);
            if (marker >= 0) {
                int start = marker + PROJECT_MARKER.length();
                int end = findLineEnd(text, start);
                String name = sanitizeFileName(text.substring(start, end).trim());
                if (name.length() > 0) {
                    if (!name.toLowerCase(Locale.UK).endsWith(".zip")) {
                        name = name + ".zip";
                    }
                    return name;
                }
            }
        }
        String stamp = new SimpleDateFormat("dd-MM-yyyy_HHmmss", Locale.UK).format(new Date());
        return "Jarvis_Project_" + stamp + ".zip";
    }

    private static String sanitizeFileName(String name) {
        if (name == null) {
            return "";
        }
        String cleaned = name.replace('\\', '_').replace('/', '_').replace(':', '_').replace('*', '_')
                .replace('?', '_').replace('"', '_').replace('<', '_').replace('>', '_').replace('|', '_').trim();
        while (cleaned.indexOf("__") >= 0) {
            cleaned = cleaned.replace("__", "_");
        }
        return cleaned;
    }

    private static String sanitizePath(String path) {
        if (path == null) {
            return "";
        }
        String value = path.replace('\\', '/').trim();
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        while (value.indexOf("//") >= 0) {
            value = value.replace("//", "/");
        }
        if (value.indexOf("../") >= 0 || value.indexOf("/..") >= 0 || value.equals("..")) {
            return "";
        }
        if (value.length() > 180) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '/' || c == '_' || c == '-' || c == '.' || c == ' ') {
                builder.append(c);
            } else {
                builder.append('_');
            }
        }
        return builder.toString().trim();
    }

    private static boolean isLanguageMarker(String value) {
        if (value == null) {
            return false;
        }
        String v = value.trim().toLowerCase(Locale.UK);
        if (v.length() == 0 || v.length() > 32) {
            return false;
        }
        return v.equals("java") || v.equals("xml") || v.equals("gradle") || v.equals("groovy")
                || v.equals("properties") || v.equals("txt") || v.equals("text") || v.equals("md")
                || v.equals("markdown") || v.equals("json") || v.equals("bash") || v.equals("sh")
                || v.equals("python") || v.equals("kotlin") || v.equals("html") || v.equals("css")
                || v.equals("javascript") || v.equals("js");
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

    private static String safeMessage(Throwable error) {
        if (error == null) {
            return "unknown error";
        }
        String message = error.getMessage();
        if (message == null || message.trim().length() == 0) {
            return error.getClass().getSimpleName();
        }
        return message;
    }

    private static final class ProjectFile {
        final String path;
        final String content;

        ProjectFile(String path, String content) {
            this.path = path;
            this.content = content;
        }
    }
}
