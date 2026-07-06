package com.hyperion.jarvis;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Locale;

public final class JarvisOnlineBrain {
    private static final String PREF_OPENAI_KEY = "openai_api_key";
    private static final String PREF_OPENAI_KEYS = "openai_api_keys_v2";
    private static final String PREF_OPENAI_ACTIVE_INDEX = "openai_api_active_index_v2";
    private static final String PREF_OPENAI_MODEL = "openai_model";
    private static final String PREF_AI_PROVIDER = "ai_provider_v26";
    private static final String PREF_AI_BASE_URL = "ai_base_url_v26";
    public static final String PROVIDER_OPENAI = "OpenAI";
    public static final String PROVIDER_BAZAARLINK = "BazaarLink";
    public static final String PROVIDER_CUSTOM = "Custom OpenAI-Compatible";
    private static final String DEFAULT_OPENAI_BASE_URL = "https://api.openai.com/v1";
    private static final String BAZAARLINK_BASE_URL = "https://bazaarlink.ai/api/v1";
    private static final String DEFAULT_MODEL = "gpt-4o-mini";
    public static final String MODEL_GPT_4O_MINI = "gpt-4o-mini";
    public static final String MODEL_GPT_41_MINI = "gpt-4.1-mini";
    public static final String MODEL_GPT_4O = "gpt-4o";

    private JarvisOnlineBrain() {
    }

    public static void setApiKey(Context context, String key) {
        setApiKeysFromText(context, key, false);
    }

    public static int setApiKeysFromText(Context context, String keysText, boolean append) {
        if (context == null || keysText == null) {
            return 0;
        }
        String[] incoming = extractCandidateKeys(keysText);
        if (incoming.length == 0) {
            return 0;
        }
        SharedPreferences prefs = context.getSharedPreferences(JarvisCommandCenter.PREFS_NAME, Context.MODE_PRIVATE);
        String[] existing = append ? getStoredKeys(context) : new String[0];
        StringBuilder builder = new StringBuilder();
        int count = 0;
        for (int i = 0; i < existing.length; i++) {
            String key = existing[i];
            if (isUsableKey(key) && !containsKey(builder.toString(), key)) {
                if (builder.length() > 0) builder.append('\n');
                builder.append(key);
                count++;
            }
        }
        for (int i = 0; i < incoming.length; i++) {
            String key = incoming[i];
            if (isUsableKey(key) && !containsKey(builder.toString(), key)) {
                if (builder.length() > 0) builder.append('\n');
                builder.append(key);
                count++;
            }
        }
        if (count > 0) {
            String firstKey = getFirstLine(builder.toString());
            SharedPreferences.Editor editor = prefs.edit()
                    .putString(PREF_OPENAI_KEYS, builder.toString())
                    .putString(PREF_OPENAI_KEY, firstKey)
                    .putInt(PREF_OPENAI_ACTIVE_INDEX, 0);
            if (firstKey.toLowerCase(Locale.UK).startsWith("sk-bl-")) {
                editor.putString(PREF_AI_PROVIDER, PROVIDER_BAZAARLINK);
                editor.putString(PREF_AI_BASE_URL, BAZAARLINK_BASE_URL);
            }
            editor.commit();
        }
        return count;
    }

    public static int getApiKeyCount(Context context) {
        return getStoredKeys(context).length;
    }

    public static int getActiveKeyNumber(Context context) {
        if (context == null) {
            return 0;
        }
        int count = getApiKeyCount(context);
        if (count == 0) {
            return 0;
        }
        int index = context.getSharedPreferences(JarvisCommandCenter.PREFS_NAME, Context.MODE_PRIVATE).getInt(PREF_OPENAI_ACTIVE_INDEX, 0);
        if (index < 0) index = 0;
        if (index >= count) index = count - 1;
        return index + 1;
    }

    public static boolean selectApiKey(Context context, int keyNumber) {
        if (context == null) {
            return false;
        }
        int count = getApiKeyCount(context);
        if (keyNumber < 1 || keyNumber > count) {
            return false;
        }
        context.getSharedPreferences(JarvisCommandCenter.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(PREF_OPENAI_ACTIVE_INDEX, keyNumber - 1)
                .commit();
        return true;
    }

    public static void clearApiKeys(Context context) {
        if (context == null) {
            return;
        }
        context.getSharedPreferences(JarvisCommandCenter.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(PREF_OPENAI_KEYS)
                .remove(PREF_OPENAI_KEY)
                .remove(PREF_OPENAI_ACTIVE_INDEX)
                .commit();
    }

    public static String getApiKeyStatus(Context context) {
        int count = getApiKeyCount(context);
        if (count == 0) {
            return "AI core is not configured. Type: set ai key YOUR_API_KEY, or copy keys and type: import ai keys from clipboard.";
        }
        String active = getActiveApiKey(context);
        return "AI core is configured with " + count + " local key" + (count == 1 ? "" : "s") + ". Active key is " + getActiveKeyNumber(context) + " of " + count + ", ending " + keyEnding(active) + ". Provider is " + getProvider(context) + ". Base URL is " + getBaseUrl(context) + ". Current model is " + getModel(context) + ".";
    }



    public static String getApiKeyPersistenceStatus(Context context) {
        int count = getApiKeyCount(context);
        if (count == 0) {
            return "No AI keys are stored yet. Copy your key, open AI setup, then import keys from clipboard.";
        }
        return "AI keys are stored locally in Jarvis app data. They will normally survive app restarts, phone reboots and normal APK updates. They will be removed only if you uninstall Jarvis, clear app data, change the package name, or say clear AI keys. " + getApiKeyStatus(context);
    }

    public static String getStoredKeySummary(Context context) {
        String[] keys = getStoredKeys(context);
        if (keys.length == 0) {
            return "No keys stored.";
        }
        StringBuilder builder = new StringBuilder();
        builder.append(keys.length).append(" stored key").append(keys.length == 1 ? "" : "s").append(". Active key: ").append(getActiveKeyNumber(context)).append(".\n");
        for (int i = 0; i < keys.length; i++) {
            builder.append(i + 1).append(". ").append(keyEnding(keys[i]));
            if (i == getActiveKeyNumber(context) - 1) {
                builder.append(" active");
            }
            if (i < keys.length - 1) {
                builder.append('\n');
            }
        }
        return builder.toString();
    }

    public static void testActiveApiKey(final Context context, final JarvisOutput output) {
        final Context appContext = context.getApplicationContext();
        new AsyncTask<Void, Void, String>() {
            protected String doInBackground(Void... params) {
                String key = getActiveApiKey(appContext);
                if (key.length() == 0) {
                    return "No AI key is stored. Open AI setup and import one from the clipboard.";
                }
                return "Active AI key test: " + testKeyBlocking(appContext, key);
            }

            protected void onPostExecute(String result) {
                JarvisConversationMemory.rememberAssistant(appContext, result);
                deliver(output, result);
            }
        }.execute();
    }

    public static void selectFirstWorkingApiKey(final Context context, final JarvisOutput output) {
        final Context appContext = context.getApplicationContext();
        new AsyncTask<Void, Void, String>() {
            protected String doInBackground(Void... params) {
                String[] keys = getStoredKeys(appContext);
                if (keys.length == 0) {
                    return "No AI keys are stored. Open AI setup and import keys from the clipboard first.";
                }
                StringBuilder report = new StringBuilder();
                for (int i = 0; i < keys.length; i++) {
                    String result = testKeyBlocking(appContext, keys[i]);
                    report.append("Key ").append(i + 1).append(" ").append(keyEnding(keys[i])).append(": ").append(result);
                    if (i < keys.length - 1) {
                        report.append('\n');
                    }
                    if (result.startsWith("working")) {
                        selectApiKey(appContext, i + 1);
                        return "Selected key " + (i + 1) + " as the active working AI key.\n" + report.toString();
                    }
                }
                return "I tested the stored keys, but none returned a clean working response.\n" + report.toString();
            }

            protected void onPostExecute(String result) {
                deliver(output, result);
            }
        }.execute();
    }

    private static String testKeyBlocking(Context context, String key) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(getModelsUrl(context));
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(16000);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Authorization", "Bearer " + key.trim());
            connection.setRequestProperty("User-Agent", "JarvisAIDE/1.31 Android");
            int code = connection.getResponseCode();
            InputStream input;
            if (code >= 400) {
                input = connection.getErrorStream();
            } else {
                input = connection.getInputStream();
            }
            String body = readStream(input);
            if (code >= 200 && code < 300) {
                return "working. Authentication accepted.";
            }
            String message = extractApiErrorMessage(body);
            if (code == 401 || code == 403) {
                return "not authorised. " + message;
            }
            if (code == 429) {
                return "authorised but rate limited or quota limited. " + message;
            }
            return "HTTP " + code + ". " + message;
        } catch (Exception error) {
            return "test failed: " + safeMessage(error);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String extractApiErrorMessage(String body) {
        try {
            if (body == null || body.trim().length() == 0) {
                return "No extra message.";
            }
            JSONObject root = new JSONObject(body);
            if (root.has("error")) {
                JSONObject error = root.optJSONObject("error");
                if (error != null) {
                    String message = error.optString("message", "");
                    if (message.length() > 0) {
                        if (message.length() > 160) {
                            message = message.substring(0, 160) + "...";
                        }
                        return message;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "No readable service message.";
    }


    public static String[] getProviderOptions() {
        return new String[] { PROVIDER_OPENAI, PROVIDER_BAZAARLINK, PROVIDER_CUSTOM };
    }

    public static int getProviderIndex(Context context) {
        String current = getProvider(context);
        String[] options = getProviderOptions();
        for (int i = 0; i < options.length; i++) {
            if (options[i].equalsIgnoreCase(current)) {
                return i;
            }
        }
        return 0;
    }

    public static void setProvider(Context context, String provider) {
        if (context == null || provider == null) {
            return;
        }
        String normal = normaliseProvider(provider);
        SharedPreferences.Editor edit = context.getSharedPreferences(JarvisCommandCenter.PREFS_NAME, Context.MODE_PRIVATE).edit();
        edit.putString(PREF_AI_PROVIDER, normal);
        if (PROVIDER_OPENAI.equals(normal)) {
            edit.putString(PREF_AI_BASE_URL, DEFAULT_OPENAI_BASE_URL);
        } else if (PROVIDER_BAZAARLINK.equals(normal)) {
            edit.putString(PREF_AI_BASE_URL, BAZAARLINK_BASE_URL);
        }
        edit.commit();
    }

    public static String getProvider(Context context) {
        if (context == null) {
            return PROVIDER_OPENAI;
        }
        String saved = context.getSharedPreferences(JarvisCommandCenter.PREFS_NAME, Context.MODE_PRIVATE).getString(PREF_AI_PROVIDER, PROVIDER_OPENAI);
        return normaliseProvider(saved);
    }

    public static void setBaseUrl(Context context, String baseUrl) {
        if (context == null || baseUrl == null) {
            return;
        }
        String clean = normaliseBaseUrl(baseUrl);
        if (clean.length() == 0) {
            return;
        }
        context.getSharedPreferences(JarvisCommandCenter.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(PREF_AI_BASE_URL, clean)
                .putString(PREF_AI_PROVIDER, providerForBaseUrl(clean))
                .commit();
    }

    public static String getBaseUrl(Context context) {
        if (context == null) {
            return DEFAULT_OPENAI_BASE_URL;
        }
        SharedPreferences prefs = context.getSharedPreferences(JarvisCommandCenter.PREFS_NAME, Context.MODE_PRIVATE);
        String provider = getProvider(context);
        String saved = prefs.getString(PREF_AI_BASE_URL, "");
        if (saved == null || saved.trim().length() == 0) {
            if (PROVIDER_BAZAARLINK.equals(provider)) {
                return BAZAARLINK_BASE_URL;
            }
            return DEFAULT_OPENAI_BASE_URL;
        }
        return normaliseBaseUrl(saved);
    }

    public static String getChatCompletionsUrl(Context context) {
        return getBaseUrl(context) + "/chat/completions";
    }

    public static String getModelsUrl(Context context) {
        return getBaseUrl(context) + "/models";
    }

    private static String normaliseProvider(String provider) {
        if (provider == null) {
            return PROVIDER_OPENAI;
        }
        String low = provider.trim().toLowerCase(Locale.UK);
        if (low.indexOf("bazaar") >= 0 || low.indexOf("sk-bl") >= 0) {
            return PROVIDER_BAZAARLINK;
        }
        if (low.indexOf("custom") >= 0 || low.indexOf("compatible") >= 0) {
            return PROVIDER_CUSTOM;
        }
        return PROVIDER_OPENAI;
    }

    private static String providerForBaseUrl(String baseUrl) {
        if (baseUrl == null) {
            return PROVIDER_OPENAI;
        }
        String low = baseUrl.toLowerCase(Locale.UK);
        if (low.indexOf("bazaarlink.ai") >= 0) {
            return PROVIDER_BAZAARLINK;
        }
        if (low.indexOf("api.openai.com") >= 0) {
            return PROVIDER_OPENAI;
        }
        return PROVIDER_CUSTOM;
    }

    private static String normaliseBaseUrl(String baseUrl) {
        if (baseUrl == null) {
            return "";
        }
        String clean = baseUrl.trim();
        if (clean.length() == 0) {
            return "";
        }
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
            clean = "https://" + clean;
        }
        while (clean.endsWith("/")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        if (clean.endsWith("/chat/completions")) {
            clean = clean.substring(0, clean.length() - "/chat/completions".length());
        }
        if (clean.endsWith("/models")) {
            clean = clean.substring(0, clean.length() - "/models".length());
        }
        while (clean.endsWith("/")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        return clean;
    }


    public static String[] getAvailableModels() {
        return new String[] { MODEL_GPT_4O_MINI, MODEL_GPT_41_MINI, MODEL_GPT_4O };
    }

    public static int getModelIndex(Context context) {
        String current = getModel(context);
        String[] models = getAvailableModels();
        for (int i = 0; i < models.length; i++) {
            if (models[i].equalsIgnoreCase(current)) {
                return i;
            }
        }
        return 0;
    }

    public static void setModel(Context context, String model) {
        if (context == null || model == null || model.trim().length() == 0) {
            return;
        }
        SharedPreferences prefs = context.getSharedPreferences(JarvisCommandCenter.PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(PREF_OPENAI_MODEL, model.trim()).commit();
    }

    public static boolean hasApiKey(Context context) {
        return getActiveApiKey(context).length() > 0;
    }

    public static String getModel(Context context) {
        if (context == null) {
            return DEFAULT_MODEL;
        }
        String model = context.getSharedPreferences(JarvisCommandCenter.PREFS_NAME, Context.MODE_PRIVATE).getString(PREF_OPENAI_MODEL, DEFAULT_MODEL);
        if (model == null || model.trim().length() == 0) {
            return DEFAULT_MODEL;
        }
        return model.trim();
    }



    public static String buildBackupText(Context context) {
        StringBuilder builder = new StringBuilder();
        builder.append("JARVIS_BACKUP_V2\n");
        builder.append("created=").append(new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.UK).format(new java.util.Date())).append("\n");
        builder.append("package=").append(context == null ? "com.hyperion.jarvis" : context.getPackageName()).append("\n");
        builder.append("provider=").append(escapeBackupValue(getProvider(context))).append("\n");
        builder.append("base_url=").append(escapeBackupValue(getBaseUrl(context))).append("\n");
        builder.append("model=").append(escapeBackupValue(getModel(context))).append("\n");
        builder.append("active_key=").append(getActiveKeyNumber(context)).append("\n");
        builder.append("keys_begin\n");
        String[] keys = getStoredKeys(context);
        for (int i = 0; i < keys.length; i++) {
            builder.append(keys[i]).append("\n");
        }
        builder.append("keys_end\n");
        return builder.toString();
    }

    public static String restoreBackupText(Context context, String backupText) {
        if (context == null) {
            return "No Android context available.";
        }
        if (backupText == null || backupText.trim().length() == 0) {
            return "Backup file was empty.";
        }
        if (backupText.indexOf("JARVIS_BACKUP_V1") < 0 && backupText.indexOf("JARVIS_BACKUP_V2") < 0) {
            return "That file is not a recognised Jarvis backup.";
        }
        String[] lines = backupText.replace('\r', '\n').split("\\n");
        String model = DEFAULT_MODEL;
        String provider = "";
        String baseUrl = "";
        int activeKey = 1;
        boolean inKeys = false;
        StringBuilder keys = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line == null) {
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.equals("keys_begin")) {
                inKeys = true;
                continue;
            }
            if (trimmed.equals("keys_end")) {
                inKeys = false;
                continue;
            }
            if (inKeys) {
                if (trimmed.length() > 0) {
                    if (keys.length() > 0) {
                        keys.append('\n');
                    }
                    keys.append(trimmed);
                }
                continue;
            }
            if (trimmed.startsWith("model=")) {
                model = unescapeBackupValue(trimmed.substring(6));
                continue;
            }
            if (trimmed.startsWith("provider=")) {
                provider = unescapeBackupValue(trimmed.substring(9));
                continue;
            }
            if (trimmed.startsWith("base_url=")) {
                baseUrl = unescapeBackupValue(trimmed.substring(9));
                continue;
            }
            if (trimmed.startsWith("active_key=")) {
                try {
                    activeKey = Integer.parseInt(trimmed.substring(11).trim());
                } catch (Exception ignored) {
                    activeKey = 1;
                }
            }
        }
        if (provider != null && provider.trim().length() > 0) {
            setProvider(context, provider.trim());
        }
        if (baseUrl != null && baseUrl.trim().length() > 0) {
            setBaseUrl(context, baseUrl.trim());
        }
        if (model != null && model.trim().length() > 0) {
            setModel(context, model.trim());
        }
        int count = 0;
        if (keys.length() > 0) {
            count = setApiKeysFromText(context, keys.toString(), false);
            if (activeKey > 0) {
                selectApiKey(context, activeKey);
            }
        }
        return "Restored provider " + getProvider(context) + ", base URL " + getBaseUrl(context) + ", model " + getModel(context) + " and " + count + " AI key" + (count == 1 ? "" : "s") + ".";
    }

    private static String escapeBackupValue(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r");
    }

    private static String unescapeBackupValue(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        boolean slash = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (slash) {
                if (c == 'n') {
                    builder.append('\n');
                } else if (c == 'r') {
                    builder.append('\r');
                } else {
                    builder.append(c);
                }
                slash = false;
            } else if (c == '\\') {
                slash = true;
            } else {
                builder.append(c);
            }
        }
        if (slash) {
            builder.append('\\');
        }
        return builder.toString();
    }

    public static void requestChat(final Context context, final String prompt, final JarvisOutput output) {
        final Context appContext = context.getApplicationContext();
        new AsyncTask<Void, Void, String>() {
            protected String doInBackground(Void... params) {
                try {
                    String key = getActiveApiKey(appContext);
                    if (key == null || key.trim().length() == 0) {
                        return "Chat AI is not configured. Type: set ai key YOUR_API_KEY, or copy keys and type: import ai keys from clipboard. You can also type: set ai model MODEL_NAME.";
                    }
                    String model = getModel(appContext);
                    JSONObject body = new JSONObject();
                    body.put("model", model);
                    JSONArray messages = new JSONArray();
                    JSONObject system = new JSONObject();
                    system.put("role", "system");
                    system.put("content", buildSystemPrompt(prompt));
                    messages.put(system);
                    JSONObject user = new JSONObject();
                    user.put("role", "user");
                    user.put("content", buildUserPrompt(appContext, prompt));
                    messages.put(user);
                    body.put("messages", messages);
                    body.put("temperature", 0.55);
                    body.put("max_tokens", 6000);
                    String response = postJson(getChatCompletionsUrl(appContext), body.toString(), key.trim());
                    JSONObject json = new JSONObject(response);
                    if (json.has("error")) {
                        JSONObject error = json.getJSONObject("error");
                        return "The AI service returned an error: " + error.optString("message", "unknown error");
                    }
                    JSONArray choices = json.optJSONArray("choices");
                    if (choices != null && choices.length() > 0) {
                        JSONObject message = choices.getJSONObject(0).optJSONObject("message");
                        if (message != null) {
                            String content = message.optString("content", "").trim();
                            if (content.length() > 0) {
                                return content;
                            }
                        }
                    }
                    return "The AI service responded, but I could not read the answer.";
                } catch (Exception error) {
                    return "AI request failed: " + safeMessage(error);
                }
            }

            protected void onPostExecute(String result) {
                JarvisConversationMemory.rememberAssistant(appContext, result);
                deliver(output, result);
            }
        }.execute();
    }


    private static String buildSystemPrompt(String prompt) {
        String base = "You are JARVIS inside an Android assistant app. Be helpful, detailed and practical. The user may speak naturally without saying ask AI. Use recent conversation memory to understand follow-up requests and corrections. Never give tiny placeholder snippets when the user asks for code. For code requests, provide complete usable code, imports, manifest/build files when relevant, and short setup instructions. Do not say omitted for brevity unless the provider response limit prevents completion. If controlling the phone is needed, explain what Jarvis command the user can say.";
        if (JarvisProjectPackager.looksLikeProjectZipRequest(prompt)) {
            return base + " When the user asks to create a project, full project, Android Java app project, game project, AIDE project, or packaged zip, answer using the Jarvis package format exactly. Start with JARVIS_PROJECT_ZIP: followed by a safe zip filename. Then provide every file using JARVIS_FILE: path/to/file followed immediately by a fenced code block containing the full file content. Include settings.gradle, build.gradle, app/build.gradle, AndroidManifest.xml, Java source, and XML/resources needed for a minimal complete AIDE-compatible Android Java project. Do not include normal explanatory code snippets outside the Jarvis file blocks because Jarvis will ask the user where to save the ZIP. Do not use lambdas. Do not include placeholder comments such as add the rest here.";
        }
        if (JarvisCodeTools.looksLikeCodeRequest(prompt)) {
            return base + " Put the main usable code in fenced Markdown code blocks. Prefer complete files over fragments. Include enough detail that the user can copy and run it in AIDE or Termux.";
        }
        return base;
    }

    private static String buildUserPrompt(Context context, String prompt) {
        String userPrompt = buildUserPrompt(prompt);
        String memory = JarvisConversationMemory.buildContextText(context, prompt);
        if (memory == null || memory.trim().length() == 0) {
            return userPrompt;
        }
        return memory + "\n\nCurrent user request:\n" + userPrompt;
    }

    private static String buildUserPrompt(String prompt) {
        if (prompt == null) {
            prompt = "";
        }
        if (JarvisProjectPackager.looksLikeProjectZipRequest(prompt)) {
            return "Create a complete packaged project for this request: " + prompt
                    + "\n\nUse this exact output format so Jarvis can package the project and ask the user where to save the ZIP:"
                    + "\nJARVIS_PROJECT_ZIP: ProjectName.zip"
                    + "\nJARVIS_FILE: settings.gradle"
                    + "\n```gradle"
                    + "\nFULL FILE CONTENT HERE"
                    + "\n```"
                    + "\nJARVIS_FILE: app/build.gradle"
                    + "\n```gradle"
                    + "\nFULL FILE CONTENT HERE"
                    + "\n```"
                    + "\nJARVIS_FILE: app/src/main/AndroidManifest.xml"
                    + "\n```xml"
                    + "\nFULL FILE CONTENT HERE"
                    + "\n```"
                    + "\nJARVIS_FILE: app/src/main/java/com/example/project/MainActivity.java"
                    + "\n```java"
                    + "\nFULL FILE CONTENT HERE"
                    + "\n```"
                    + "\n\nRules: include all minimal files required, use Android Java, no lambdas, no AndroidX unless absolutely required, target AIDE compatibility, make the answer complete even if long. Do not provide an extra ordinary code snippet for project requests; Jarvis will package the files and ask the user where to save them.";
        }
        if (JarvisCodeTools.looksLikeCodeRequest(prompt)) {
            return prompt + "\n\nGive the full usable answer. If code is needed, use complete fenced code blocks, not a short tiny snippet. Include setup/run instructions after the code.";
        }
        return prompt;
    }

    public static void requestWeather(final Context context, final String location, final JarvisOutput output) {
        new AsyncTask<Void, Void, String>() {
            protected String doInBackground(Void... params) {
                try {
                    String place = location == null ? "" : location.trim();
                    if (place.length() == 0) {
                        return "Tell me a location, for example: okay Jarvis weather in London.";
                    }
                    String geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name=" + encode(place) + "&count=1&language=en&format=json";
                    JSONObject geo = new JSONObject(getText(geoUrl));
                    JSONArray results = geo.optJSONArray("results");
                    if (results == null || results.length() == 0) {
                        return "I could not find weather coordinates for " + place + ".";
                    }
                    JSONObject first = results.getJSONObject(0);
                    double lat = first.getDouble("latitude");
                    double lon = first.getDouble("longitude");
                    String name = first.optString("name", place);
                    String country = first.optString("country", "");
                    String weatherUrl = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon + "&current=temperature_2m,relative_humidity_2m,precipitation,weather_code,wind_speed_10m&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max&timezone=auto";
                    JSONObject weather = new JSONObject(getText(weatherUrl));
                    JSONObject current = weather.optJSONObject("current");
                    JSONObject daily = weather.optJSONObject("daily");
                    if (current == null) {
                        return "I reached the weather service, but no current weather data was returned.";
                    }
                    double temp = current.optDouble("temperature_2m", 0.0);
                    double wind = current.optDouble("wind_speed_10m", 0.0);
                    int humidity = current.optInt("relative_humidity_2m", -1);
                    double rain = current.optDouble("precipitation", 0.0);
                    int code = current.optInt("weather_code", -1);
                    String condition = describeWeatherCode(code);
                    StringBuilder builder = new StringBuilder();
                    builder.append("Weather for ").append(name);
                    if (country.length() > 0) {
                        builder.append(", ").append(country);
                    }
                    builder.append(": ").append(condition);
                    builder.append(", ").append(round1(temp)).append(" degrees Celsius");
                    if (humidity >= 0) {
                        builder.append(", humidity ").append(humidity).append(" percent");
                    }
                    builder.append(", wind ").append(round1(wind)).append(" kilometres per hour");
                    builder.append(", precipitation now ").append(round1(rain)).append(" millimetres.");
                    if (daily != null) {
                        JSONArray highs = daily.optJSONArray("temperature_2m_max");
                        JSONArray lows = daily.optJSONArray("temperature_2m_min");
                        JSONArray pop = daily.optJSONArray("precipitation_probability_max");
                        if (highs != null && lows != null && highs.length() > 0 && lows.length() > 0) {
                            builder.append(" Today should range from ").append(round1(lows.optDouble(0))).append(" to ").append(round1(highs.optDouble(0))).append(" degrees.");
                        }
                        if (pop != null && pop.length() > 0) {
                            builder.append(" Maximum rain chance is ").append(pop.optInt(0)).append(" percent.");
                        }
                    }
                    return builder.toString();
                } catch (Exception error) {
                    return "Weather request failed: " + safeMessage(error);
                }
            }

            protected void onPostExecute(String result) {
                deliver(output, result);
            }
        }.execute();
    }

    public static void requestFactCheckAndStore(final Context context, final String fact, final JarvisOutput output) {
        final Context appContext = context.getApplicationContext();
        new AsyncTask<Void, Void, String>() {
            protected String doInBackground(Void... params) {
                try {
                    String cleanFact = fact == null ? "" : fact.trim();
                    if (cleanFact.length() == 0) {
                        return "Tell me the fact you want me to check.";
                    }
                    String searchUrl = "https://en.wikipedia.org/w/api.php?action=query&format=json&list=search&srlimit=1&srsearch=" + encode(cleanFact);
                    JSONObject root = new JSONObject(getText(searchUrl));
                    JSONObject query = root.optJSONObject("query");
                    JSONArray results = query == null ? null : query.optJSONArray("search");
                    if (results != null && results.length() > 0) {
                        JSONObject top = results.getJSONObject(0);
                        String title = top.optString("title", "Wikipedia");
                        String snippet = top.optString("snippet", "").replaceAll("<[^>]+>", "");
                        JarvisMemoryStore.saveVerifiedFact(appContext, cleanFact, "Wikipedia: " + title);
                        if (snippet.length() > 120) {
                            snippet = snippet.substring(0, 120) + "...";
                        }
                        return "I found a related Wikipedia source, " + title + ", and stored the fact as source checked. Source note: " + snippet;
                    }
                    JarvisMemoryStore.savePendingFact(appContext, cleanFact, "No automatic source match");
                    openWebSearch(appContext, "fact check reliable source " + cleanFact);
                    return "I could not automatically confirm that. I stored it as pending verification and opened a web search for reliable sources.";
                } catch (Exception error) {
                    JarvisMemoryStore.savePendingFact(appContext, fact, "Fact check failed: " + safeMessage(error));
                    openWebSearch(appContext, "fact check reliable source " + fact);
                    return "Fact check failed, so I stored it as pending verification and opened a web search.";
                }
            }

            protected void onPostExecute(String result) {
                deliver(output, result);
            }
        }.execute();
    }


    private static String getActiveApiKey(Context context) {
        if (context == null) {
            return "";
        }
        String[] keys = getStoredKeys(context);
        if (keys.length == 0) {
            return "";
        }
        int index = context.getSharedPreferences(JarvisCommandCenter.PREFS_NAME, Context.MODE_PRIVATE).getInt(PREF_OPENAI_ACTIVE_INDEX, 0);
        if (index < 0) index = 0;
        if (index >= keys.length) index = 0;
        return keys[index];
    }

    private static String[] getStoredKeys(Context context) {
        if (context == null) {
            return new String[0];
        }
        SharedPreferences prefs = context.getSharedPreferences(JarvisCommandCenter.PREFS_NAME, Context.MODE_PRIVATE);
        String saved = prefs.getString(PREF_OPENAI_KEYS, "");
        if (saved == null || saved.trim().length() == 0) {
            String old = prefs.getString(PREF_OPENAI_KEY, "");
            if (isUsableKey(old)) {
                saved = old.trim();
                prefs.edit().putString(PREF_OPENAI_KEYS, saved).putInt(PREF_OPENAI_ACTIVE_INDEX, 0).commit();
            }
        }
        if (saved == null || saved.trim().length() == 0) {
            return new String[0];
        }
        return extractCandidateKeys(saved);
    }

    private static String[] extractCandidateKeys(String text) {
        if (text == null) {
            return new String[0];
        }
        String cleaned = text.replace('\r', ' ').replace('\n', ' ').replace(',', ' ').replace(';', ' ').replace('\t', ' ');
        String[] parts = cleaned.split(" ");
        String[] temp = new String[parts.length];
        int count = 0;
        for (int i = 0; i < parts.length; i++) {
            String part = sanitizeKey(parts[i]);
            if (isUsableKey(part) && !arrayContains(temp, count, part)) {
                temp[count] = part;
                count++;
            }
        }
        String[] result = new String[count];
        for (int i = 0; i < count; i++) {
            result[i] = temp[i];
        }
        return result;
    }

    private static String sanitizeKey(String key) {
        if (key == null) {
            return "";
        }
        String value = key.trim();
        while (value.length() > 0 && "\"'`<>()[]{}".indexOf(value.charAt(0)) >= 0) {
            value = value.substring(1).trim();
        }
        while (value.length() > 0 && "\"'`<>()[]{},.".indexOf(value.charAt(value.length() - 1)) >= 0) {
            value = value.substring(0, value.length() - 1).trim();
        }
        return value;
    }

    private static boolean isUsableKey(String key) {
        if (key == null) {
            return false;
        }
        String value = key.trim();
        String low = value.toLowerCase(Locale.UK);
        if (value.length() < 16 || value.indexOf(' ') >= 0) {
            return false;
        }
        return low.startsWith("sk-")
                || low.startsWith("sk-proj-")
                || low.startsWith("sk-bl-")
                || low.startsWith("sess-")
                || low.startsWith("key-")
                || low.startsWith("api-")
                || low.startsWith("token-")
                || low.startsWith("bearer-");
    }

    private static boolean arrayContains(String[] values, int count, String key) {
        for (int i = 0; i < count; i++) {
            if (key.equals(values[i])) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsKey(String existingText, String key) {
        if (existingText == null || key == null) {
            return false;
        }
        String[] keys = extractCandidateKeys(existingText);
        for (int i = 0; i < keys.length; i++) {
            if (key.equals(keys[i])) {
                return true;
            }
        }
        return false;
    }

    private static String getFirstLine(String text) {
        String[] keys = extractCandidateKeys(text);
        if (keys.length == 0) {
            return "";
        }
        return keys[0];
    }

    private static String keyEnding(String key) {
        if (key == null || key.length() == 0) {
            return "none";
        }
        if (key.length() <= 4) {
            return "****";
        }
        return "****" + key.substring(key.length() - 4);
    }

    private static void deliver(JarvisOutput output, String text) {
        if (output != null) {
            output.onAsyncResponse(text);
        }
    }

    private static String getText(String urlText) throws Exception {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlText);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(14000);
            connection.setReadTimeout(16000);
            connection.setRequestProperty("User-Agent", "JarvisAIDE/1.31 Android");
            InputStream input = new BufferedInputStream(connection.getInputStream());
            return readStream(input);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String postJson(String urlText, String body, String apiKey) throws Exception {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlText);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(16000);
            connection.setReadTimeout(30000);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            OutputStream output = connection.getOutputStream();
            output.write(body.getBytes("UTF-8"));
            output.flush();
            output.close();
            InputStream input;
            if (connection.getResponseCode() >= 400) {
                input = connection.getErrorStream();
            } else {
                input = connection.getInputStream();
            }
            return readStream(input);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String readStream(InputStream input) throws Exception {
        if (input == null) {
            return "";
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"));
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line);
        }
        reader.close();
        return builder.toString();
    }

    private static String encode(String value) throws Exception {
        return URLEncoder.encode(value == null ? "" : value, "UTF-8");
    }

    private static String round1(double value) {
        return String.format(Locale.UK, "%.1f", new Double(value));
    }

    private static String describeWeatherCode(int code) {
        if (code == 0) return "clear sky";
        if (code == 1 || code == 2 || code == 3) return "partly cloudy";
        if (code == 45 || code == 48) return "foggy";
        if (code >= 51 && code <= 57) return "drizzle";
        if (code >= 61 && code <= 67) return "rain";
        if (code >= 71 && code <= 77) return "snow";
        if (code >= 80 && code <= 82) return "rain showers";
        if (code >= 85 && code <= 86) return "snow showers";
        if (code >= 95) return "thunderstorms";
        return "weather code " + code;
    }

    private static void openWebSearch(Context context, String query) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query)));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception ignored) {
        }
    }

    private static String safeMessage(Exception error) {
        if (error == null || error.getMessage() == null) {
            return "unknown error";
        }
        return error.getMessage();
    }
}
