package com.hyperion.jarvis;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.Settings;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.net.URLDecoder;
import java.util.Locale;
import java.util.Iterator;

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
    private static final String DEFAULT_IMAGE_MODEL = "gpt-image-1";
    private static final String POLLINATIONS_IMAGE_BASE_URL = "https://image.pollinations.ai/prompt/";
    private static final String POLLINATIONS_IMAGE_ALT_URL = "https://pollinations.ai/p/";
    private static final String POLLINATIONS_TEXT_OPENAI_URL = "https://text.pollinations.ai/openai";
    private static final String PREF_LAST_WEB_IMAGE_QUERY = "last_web_image_query_v43";
    private static final String PREF_LAST_WEB_IMAGE_INDEX = "last_web_image_index_v43";
    private static final String GOOGLE_IMAGE_SEARCH_URL = "https://www.google.com/search?tbm=isch&hl=en&safe=off&q=";
    private static final String GOOGLE_IMAGE_SEARCH_ALT_URL = "https://www.google.com/search?udm=2&hl=en&safe=off&q=";
    private static final String BING_IMAGE_SEARCH_URL = "https://www.bing.com/images/search?q=";
    private static final String DUCK_IMAGE_SEARCH_PAGE_URL = "https://duckduckgo.com/?iax=images&ia=images&q=";
    private static final String DUCK_IMAGE_API_URL = "https://duckduckgo.com/i.js?l=us-en&o=json&f=,,,&p=1&q=";
    private static final String WIKIMEDIA_IMAGE_SEARCH_URL = "https://commons.wikimedia.org/w/api.php?action=query&generator=search&gsrnamespace=6&gsrlimit=14&prop=imageinfo&iiprop=url|mime&iiurlwidth=1024&format=json&origin=*&gsrsearch=";
    private static final String WIKIPEDIA_PAGE_IMAGE_URL = "https://en.wikipedia.org/w/api.php?action=query&prop=pageimages|info&inprop=url&piprop=thumbnail&pithumbsize=1200&redirects=1&format=json&titles=";

    public interface VisionCallback {
        void onVisionResult(String text);
    }
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
            connection.setRequestProperty("User-Agent", "JarvisAIDE/1.4.4 Android");
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

    public static String getImageGenerationsUrl(Context context) {
        return getBaseUrl(context) + "/images/generations";
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
                String providerError = "";
                try {
                    String key = getActiveApiKey(appContext);
                    if (key != null && key.trim().length() > 0) {
                        try {
                            return requestChatFromEndpoint(appContext, getChatCompletionsUrl(appContext), key.trim(), getModel(appContext), prompt, 6000);
                        } catch (Exception providerException) {
                            providerError = safeMessage(providerException);
                        }
                    } else {
                        providerError = "No active AI key was configured.";
                    }

                    String fallbackResult = requestChatFromAnonymousFallbacks(appContext, prompt);
                    if (fallbackResult != null && fallbackResult.trim().length() > 0) {
                        return fallbackResult;
                    }

                    return buildFriendlyAiFailure(providerError, "The free anonymous fallback did not return an answer.");
                } catch (Exception error) {
                    String message = safeMessage(error);
                    return buildFriendlyAiFailure(providerError, message);
                }
            }

            protected void onPostExecute(String result) {
                JarvisConversationMemory.rememberAssistant(appContext, result);
                deliver(output, result);
            }
        }.execute();
    }

    private static String requestChatFromAnonymousFallbacks(Context context, String prompt) throws Exception {
        String errors = "";

        String[] pollinationsModels = getPollinationsChatModels();
        for (int i = 0; i < pollinationsModels.length; i++) {
            try {
                String result = requestChatFromEndpoint(context, POLLINATIONS_TEXT_OPENAI_URL, "", pollinationsModels[i], prompt, 1800);
                if (result != null && result.trim().length() > 0) {
                    return result;
                }
            } catch (Exception error) {
                String message = safeMessage(error);
                if (isAnonymousQueueOrRateLimit(message)) {
                    errors = appendError(errors, "Pollinations is currently busy or rate-limited");
                    break;
                }
                errors = appendError(errors, "Pollinations " + pollinationsModels[i] + ": " + message);
            }
        }

        try {
            String direct = requestPollinationsTextDirect(context, prompt);
            if (direct != null && direct.trim().length() > 0) {
                return direct;
            }
        } catch (Exception directError) {
            errors = appendError(errors, "Pollinations direct text fallback: " + safeMessage(directError));
        }

        throw new Exception(buildAnonymousFallbackSummary(errors));
    }

    private static String requestPollinationsTextDirect(Context context, String prompt) throws Exception {
        String safePrompt = buildSystemPrompt(prompt) + "\n\nUser request:\n" + buildUserPrompt(context, prompt);
        if (safePrompt.length() > 3600) {
            safePrompt = safePrompt.substring(0, 3600);
        }
        String address = "https://text.pollinations.ai/" + safeUrlEncode(safePrompt)
                + "?model=openai-fast&private=true&json=false";
        String response = downloadText(address);
        String cleaned = response == null ? "" : response.trim();
        if (cleaned.length() == 0) {
            throw new Exception("empty response");
        }
        String lower = cleaned.toLowerCase(Locale.US);
        if (isAnonymousQueueOrRateLimit(lower)) {
            throw new Exception("free fallback is busy or rate-limited");
        }
        if (cleaned.startsWith("{") && cleaned.indexOf("error") >= 0) {
            throw new Exception(extractApiErrorMessage(cleaned));
        }
        return cleaned;
    }

    private static boolean isAnonymousQueueOrRateLimit(String message) {
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase(Locale.US);
        return lower.indexOf("queue full") >= 0
                || lower.indexOf("already queued") >= 0
                || lower.indexOf("rate limit") >= 0
                || lower.indexOf("too many") >= 0
                || lower.indexOf("insufficient credits") >= 0
                || lower.indexOf("top up") >= 0
                || lower.indexOf("get unlimited") >= 0
                || lower.indexOf("does not exist") >= 0
                || lower.indexOf("no readable answer") >= 0;
    }

    private static String buildAnonymousFallbackSummary(String errors) {
        StringBuilder builder = new StringBuilder();
        builder.append("The configured AI provider could not answer, and the free anonymous fallback is currently unavailable. ");
        builder.append("This normally means the API key has no credits, the free queue is full, or the anonymous provider is rate-limiting requests. ");
        builder.append("Open AI setup and import a working key/provider, or try again later. ");
        if (errors != null && errors.trim().length() > 0) {
            builder.append("Technical detail: ").append(cleanFallbackErrorForDisplay(errors));
        }
        return builder.toString();
    }

    private static String buildFriendlyAiFailure(String providerError, String fallbackError) {
        StringBuilder builder = new StringBuilder();
        builder.append("AI request failed. ");
        if (providerError != null && providerError.trim().length() > 0) {
            builder.append("Provider issue: ").append(cleanFallbackErrorForDisplay(providerError)).append(". ");
        }
        if (fallbackError != null && fallbackError.trim().length() > 0) {
            builder.append("Free fallback issue: ").append(cleanFallbackErrorForDisplay(fallbackError)).append(". ");
        }
        builder.append("Open AI setup, switch to a working provider/key, or try again later when the free service is not busy.");
        return builder.toString();
    }

    private static String cleanFallbackErrorForDisplay(String message) {
        if (message == null) {
            return "unknown error";
        }
        String cleaned = message.replace('\n', ' ').replace('\r', ' ').trim();
        while (cleaned.indexOf("  ") >= 0) {
            cleaned = cleaned.replace("  ", " ");
        }
        String lower = cleaned.toLowerCase(Locale.US);
        if (lower.indexOf("insufficient credits") >= 0 || lower.indexOf("top up") >= 0) {
            return "the configured provider has insufficient credits";
        }
        if (lower.indexOf("queue full") >= 0 || lower.indexOf("already queued") >= 0) {
            return "the free anonymous queue is full";
        }
        if (lower.indexOf("does not exist") >= 0) {
            return "one anonymous fallback model is no longer available";
        }
        if (lower.indexOf("no readable answer") >= 0) {
            return "the anonymous service responded but returned no readable answer";
        }
        if (cleaned.length() > 260) {
            cleaned = cleaned.substring(0, 260).trim() + "...";
        }
        return cleaned;
    }

    private static String requestChatFromEndpoint(Context context, String url, String apiKey, String model, String prompt, int maxTokens) throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", model == null || model.length() == 0 ? DEFAULT_MODEL : model);
        JSONArray messages = new JSONArray();
        JSONObject system = new JSONObject();
        system.put("role", "system");
        system.put("content", buildSystemPrompt(prompt));
        messages.put(system);
        JSONObject user = new JSONObject();
        user.put("role", "user");
        user.put("content", buildUserPrompt(context, prompt));
        messages.put(user);
        body.put("messages", messages);
        body.put("temperature", 0.55);
        body.put("max_tokens", maxTokens <= 0 ? 2400 : maxTokens);
        String response = postJson(url, body.toString(), apiKey == null ? "" : apiKey);
        return extractChatAnswerOrThrow(response);
    }

    private static String extractChatAnswerOrThrow(String response) throws Exception {
        JSONObject json = new JSONObject(response == null ? "" : response);
        if (json.has("error")) {
            JSONObject error = json.optJSONObject("error");
            if (error != null) {
                String message = error.optString("message", "unknown error");
                throw new Exception(message);
            }
            throw new Exception(json.optString("error", "unknown error"));
        }
        JSONArray choices = json.optJSONArray("choices");
        if (choices != null && choices.length() > 0) {
            JSONObject choice = choices.getJSONObject(0);
            JSONObject message = choice.optJSONObject("message");
            if (message != null) {
                String content = message.optString("content", "").trim();
                if (content.length() > 0) {
                    return content;
                }
            }
            String text = choice.optString("text", "").trim();
            if (text.length() > 0) {
                return text;
            }
        }
        throw new Exception("the service responded but no readable answer was returned");
    }

    private static String[] getPollinationsChatModels() {
        return new String[] {
                "openai-fast",
                "openai",
                "mistral"
        };
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




public static void requestImageGeneration(final Context context, final String prompt, final JarvisOutput output) {
    final Context appContext = context.getApplicationContext();
    new AsyncTask<Void, Void, String>() {
        private byte[] imageBytes;
        private String mimeType = "image/png";
        private String suggestedFileName = "jarvis_image.png";

        protected String doInBackground(Void... params) {
            String key = getActiveApiKey(appContext);
            String providerError = "";
            String freeError = "";
            String searchError = "";
            suggestedFileName = buildImageFileName(prompt);

            if (key != null && key.trim().length() > 0) {
                String[] models = getImageGenerationModelFallbacks(appContext);
                for (int i = 0; i < models.length; i++) {
                    String model = models[i];
                    try {
                        byte[] bytes = performOpenAiStyleImageGeneration(appContext, prompt, key.trim(), model);
                        if (bytes != null && bytes.length > 0) {
                            imageBytes = bytes;
                            mimeType = "image/png";
                            return null;
                        }
                    } catch (Exception error) {
                        providerError = appendError(providerError, model + ": " + safeMessage(error));
                    }
                }
            } else {
                providerError = "No active AI key was configured.";
            }

            String[] freeUrls = buildFreeImageGenerationUrls(prompt);
            for (int i = 0; i < freeUrls.length; i++) {
                try {
                    byte[] bytes = downloadBinary(freeUrls[i]);
                    if (looksLikeImageBytes(bytes)) {
                        imageBytes = bytes;
                        mimeType = guessImageMimeType(bytes);
                        return null;
                    }
                    freeError = appendError(freeError, "free endpoint " + (i + 1) + " did not return an image");
                } catch (Exception error) {
                    freeError = appendError(freeError, "free endpoint " + (i + 1) + ": " + safeMessage(error));
                }
            }

            try {
                WebImageResult webResult = loadWebImageResult(prompt, 0);
                if (webResult != null && webResult.imageBytes != null && webResult.imageBytes.length > 0) {
                    imageBytes = webResult.imageBytes;
                    mimeType = webResult.mimeType;
                    suggestedFileName = webResult.suggestedFileName;
                    rememberImageSearch(appContext, prompt, webResult.resultIndex);
                    return null;
                }
            } catch (Exception error) {
                searchError = safeMessage(error);
            }

            StringBuilder message = new StringBuilder();
            message.append("Image generation failed after trying provider models, free fallbacks and web image search.");
            if (providerError != null && providerError.length() > 0) {
                message.append(" Provider: ").append(providerError).append('.');
            }
            if (freeError != null && freeError.length() > 0) {
                message.append(" Free fallback: ").append(freeError).append('.');
            }
            if (searchError != null && searchError.length() > 0) {
                message.append(" Web image fallback: ").append(searchError).append('.');
            }
            return message.toString();
        }

        protected void onPostExecute(String errorMessage) {
            if (errorMessage != null) {
                deliver(output, errorMessage);
            } else if (output != null) {
                output.onImageGenerated(imageBytes, mimeType, suggestedFileName);
            }
        }
    }.execute();
}

public static void requestSearchImage(final Context context, final String query, final JarvisOutput output) {
    requestSearchImageInternal(context, query, 0, output);
}

public static boolean requestNextSearchImage(final Context context, final JarvisOutput output) {
    if (context == null) {
        return false;
    }
    String query = getRememberedImageSearchQuery(context);
    if (query == null || query.trim().length() == 0) {
        return false;
    }
    int nextIndex = getRememberedImageSearchIndex(context) + 1;
    requestSearchImageInternal(context, query, nextIndex, output);
    return true;
}

private static void requestSearchImageInternal(final Context context, final String query, final int requestedIndex, final JarvisOutput output) {
    final Context appContext = context.getApplicationContext();
    new AsyncTask<Void, Void, String>() {
        private byte[] imageBytes;
        private String mimeType = "image/png";
        private String suggestedFileName = "jarvis_image.png";

        protected String doInBackground(Void... params) {
            try {
                WebImageResult result = loadWebImageResult(query, requestedIndex);
                imageBytes = result.imageBytes;
                mimeType = result.mimeType;
                suggestedFileName = result.suggestedFileName;
                rememberImageSearch(appContext, query, result.resultIndex);
                return null;
            } catch (Exception error) {
                return "I could not display an image for " + query + ". I found image results, but this device/provider would not return usable image bytes. Try saying another one, or try a simpler phrase.";
            }
        }

        protected void onPostExecute(String errorMessage) {
            if (errorMessage != null) {
                deliver(output, errorMessage);
            } else if (output != null) {
                output.onImageGenerated(imageBytes, mimeType, suggestedFileName);
            }
        }
    }.execute();
}

private static final class WebImageResult {
    byte[] imageBytes;
    String mimeType;
    String suggestedFileName;
    String sourceUrl;
    int resultIndex;
}

private static final class WebImageCandidate {
    String imageUrl;
    String sourceUrl;
    String title;
    String provider;
    int score;
}

private static WebImageResult loadWebImageResult(String query, int requestedIndex) throws Exception {
    if (query == null || query.trim().length() == 0) {
        throw new Exception("No image search query was supplied.");
    }
    ArrayList<WebImageCandidate> candidates = collectWebImageCandidates(query);
    if (candidates == null || candidates.size() == 0) {
        throw new Exception("No relevant web image results were found.");
    }
    int startIndex = requestedIndex < 0 ? 0 : requestedIndex;
    if (startIndex >= candidates.size()) {
        startIndex = startIndex % candidates.size();
    }
    Exception lastError = null;
    for (int offset = 0; offset < candidates.size(); offset++) {
        int actualIndex = (startIndex + offset) % candidates.size();
        WebImageCandidate candidate = candidates.get(actualIndex);
        if (candidate == null || candidate.imageUrl == null || candidate.imageUrl.length() == 0) {
            continue;
        }
        if (!isAcceptableImageCandidate(candidate, query)) {
            continue;
        }
        try {
            byte[] bytes = downloadImageCandidate(candidate.imageUrl);
            WebImageResult result = new WebImageResult();
            result.imageBytes = bytes;
            result.mimeType = guessImageMimeType(bytes);
            result.suggestedFileName = buildImageFileName(query);
            result.sourceUrl = candidate.sourceUrl != null && candidate.sourceUrl.length() > 0 ? candidate.sourceUrl : candidate.imageUrl;
            result.resultIndex = actualIndex;
            return result;
        } catch (Exception error) {
            lastError = error;
        }
    }
    if (lastError != null) {
        throw new Exception("Image search found candidates, but none were relevant and displayable.");
    }
    throw new Exception("No downloadable web image was found.");
}

private static ArrayList<WebImageCandidate> collectWebImageCandidates(String query) throws Exception {
    ArrayList<WebImageCandidate> raw = new ArrayList<WebImageCandidate>();
    String[] variants = buildImageSearchQueries(query);
    for (int i = 0; i < variants.length; i++) {
        String variant = variants[i];
        try {
            addWikipediaPageImageCandidates(raw, variant);
        } catch (Exception ignored) {
        }
        try {
            addWikimediaImageCandidates(raw, variant);
        } catch (Exception ignored) {
        }
        try {
            addDuckDuckGoImageCandidates(raw, variant);
        } catch (Exception ignored) {
        }
        String encoded = safeUrlEncode(variant);
        try {
            addImageCandidatesFromPage(raw, GOOGLE_IMAGE_SEARCH_ALT_URL + encoded, "google");
        } catch (Exception ignored) {
        }
        try {
            addImageCandidatesFromPage(raw, GOOGLE_IMAGE_SEARCH_URL + encoded, "google");
        } catch (Exception ignored) {
        }
        try {
            addImageCandidatesFromPage(raw, BING_IMAGE_SEARCH_URL + encoded, "bing");
        } catch (Exception ignored) {
        }
        if (raw.size() >= 72) {
            break;
        }
    }
    return rankImageCandidates(raw, query);
}

private static String[] buildImageSearchQueries(String query) {
    LinkedHashSet<String> values = new LinkedHashSet<String>();
    String clean = query == null ? "" : query.trim();
    if (clean.length() == 0) {
        clean = "jarvis image";
    }
    String subject = stripLeadingArticle(clean);
    if (subject.length() == 0) {
        subject = clean;
    }
    values.add(clean);
    values.add(subject);

    String lower = subject.toLowerCase(Locale.US);
    if (lower.indexOf("tom") >= 0 && lower.indexOf("jerry") >= 0) {
        values.add("Tom and Jerry cartoon");
        values.add("Tom and Jerry cartoon characters");
        values.add("Tom and Jerry official art");
    } else if (lower.indexOf("rhino") >= 0 || lower.indexOf("rhinoceros") >= 0) {
        values.add(subject + " animal");
        values.add("rhinoceros animal");
        values.add(subject + " wildlife");
    } else if (lower.indexOf("labrador") >= 0) {
        values.add(subject + " dog");
        values.add(subject + " puppy");
    } else if (lower.indexOf("puppy") >= 0 || lower.indexOf("dog") >= 0) {
        values.add(subject + " dog photo");
    } else if (lower.indexOf("smartphone") >= 0 || lower.indexOf("phone") >= 0) {
        values.add(subject + " device");
    } else if (isLikelyLocationQuery(subject)) {
        values.add(subject + " country");
        values.add(subject + " landscape");
        values.add(subject + " travel");
        values.add(subject + " skyline");
    } else {
        values.add(subject + " photo");
        values.add(subject + " picture");
    }
    return values.toArray(new String[values.size()]);
}

private static String stripLeadingArticle(String value) {
    if (value == null) {
        return "";
    }
    String clean = value.trim();
    String lower = clean.toLowerCase(Locale.US);
    if (lower.startsWith("the ")) {
        return clean.substring(4).trim();
    }
    if (lower.startsWith("a ")) {
        return clean.substring(2).trim();
    }
    if (lower.startsWith("an ")) {
        return clean.substring(3).trim();
    }
    return clean;
}

private static void addWikipediaPageImageCandidates(ArrayList<WebImageCandidate> results, String query) throws Exception {
    if (results == null || query == null || query.trim().length() == 0) {
        return;
    }
    String subject = stripLeadingArticle(query);
    if (subject.length() == 0) {
        subject = query.trim();
    }
    String json = downloadText(WIKIPEDIA_PAGE_IMAGE_URL + safeUrlEncode(subject));
    try {
        JSONObject root = new JSONObject(json);
        JSONObject rootQuery = root.optJSONObject("query");
        JSONObject pages = rootQuery == null ? null : rootQuery.optJSONObject("pages");
        if (pages != null) {
            Iterator<String> keys = pages.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                JSONObject page = pages.optJSONObject(key);
                if (page == null) {
                    continue;
                }
                String title = page.optString("title", "");
                JSONObject thumb = page.optJSONObject("thumbnail");
                String image = thumb == null ? "" : thumb.optString("source", "");
                String source = page.optString("fullurl", "");
                if (image != null && image.length() > 0) {
                    addCandidate(results, image, source, title, "wikipedia");
                }
            }
        }
    } catch (Exception ignored) {
    }
}

private static void addWikimediaImageCandidates(ArrayList<WebImageCandidate> results, String query) throws Exception {
    if (results == null || query == null || query.trim().length() == 0) {
        return;
    }
    String json = downloadText(WIKIMEDIA_IMAGE_SEARCH_URL + safeUrlEncode(query));
    try {
        JSONObject root = new JSONObject(json);
        JSONObject rootQuery = root.optJSONObject("query");
        JSONObject pages = rootQuery == null ? null : rootQuery.optJSONObject("pages");
        if (pages != null) {
            java.util.Iterator<String> keys = pages.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                JSONObject page = pages.optJSONObject(key);
                if (page == null) {
                    continue;
                }
                String title = page.optString("title", "");
                JSONArray info = page.optJSONArray("imageinfo");
                if (info == null || info.length() == 0) {
                    continue;
                }
                JSONObject first = info.optJSONObject(0);
                if (first == null) {
                    continue;
                }
                String image = first.optString("thumburl", "");
                if (image == null || image.length() == 0) {
                    image = first.optString("url", "");
                }
                addCandidate(results, image, first.optString("descriptionurl", ""), title, "wikimedia");
            }
            if (results.size() > 0) {
                return;
            }
        }
    } catch (Exception ignored) {
    }
    collectJsonImageCandidates(results, json, "\\\"thumburl\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"", "wikimedia");
    collectJsonImageCandidates(results, json, "\"thumburl\"\\s*:\\s*\"([^\"]+)\"", "wikimedia");
    collectJsonImageCandidates(results, json, "\\\"url\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"", "wikimedia");
    collectJsonImageCandidates(results, json, "\"url\"\\s*:\\s*\"([^\"]+)\"", "wikimedia");
}

private static void addDuckDuckGoImageCandidates(ArrayList<WebImageCandidate> results, String query) throws Exception {
    if (results == null || query == null || query.trim().length() == 0) {
        return;
    }
    String encoded = safeUrlEncode(query);
    String page = downloadText(DUCK_IMAGE_SEARCH_PAGE_URL + encoded);
    String vqd = extractDuckDuckGoVqd(page);
    if (vqd == null || vqd.length() == 0) {
        return;
    }
    String json = downloadText(DUCK_IMAGE_API_URL + encoded + "&vqd=" + safeUrlEncode(vqd));
    try {
        JSONObject root = new JSONObject(json);
        JSONArray array = root.optJSONArray("results");
        if (array == null) {
            array = root.optJSONArray("data");
        }
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String title = item.optString("title", "");
                String sourceUrl = item.optString("url", "");
                String image = item.optString("image", "");
                String thumb = item.optString("thumbnail", "");
                if (image != null && image.length() > 0) {
                    addCandidate(results, image, sourceUrl, title, "duckduckgo");
                }
                if ((image == null || image.length() == 0) && thumb != null && thumb.length() > 0) {
                    addCandidate(results, thumb, sourceUrl, title, "duckduckgo");
                }
            }
            if (results.size() > 0) {
                return;
            }
        }
    } catch (Exception ignored) {
    }
    collectJsonImageCandidates(results, json, "\\\"image\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"", "duckduckgo");
    collectJsonImageCandidates(results, json, "\"image\"\\s*:\\s*\"([^\"]+)\"", "duckduckgo");
    collectJsonImageCandidates(results, json, "\\\"thumbnail\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"", "duckduckgo");
    collectJsonImageCandidates(results, json, "\"thumbnail\"\\s*:\\s*\"([^\"]+)\"", "duckduckgo");
}

private static String extractDuckDuckGoVqd(String page) {
    if (page == null) {
        return "";
    }
    String[] regexes = new String[] {
            "vqd=\\\"([^\\\"]+)\\\"",
            "vqd='([^']+)'",
            "\\\"vqd\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"",
            "vqd=([^&\\\"']+)"
    };
    for (int i = 0; i < regexes.length; i++) {
        try {
            Matcher matcher = Pattern.compile(regexes[i]).matcher(page);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (Exception ignored) {
        }
    }
    return "";
}

private static ArrayList<WebImageCandidate> rankImageCandidates(ArrayList<WebImageCandidate> candidates, String query) {
    ArrayList<WebImageCandidate> ranked = new ArrayList<WebImageCandidate>();
    if (candidates == null || candidates.size() == 0) {
        return ranked;
    }

    boolean haveStrongCandidate = false;
    for (int i = 0; i < candidates.size(); i++) {
        WebImageCandidate candidate = candidates.get(i);
        if (candidate == null) {
            continue;
        }
        candidate.score = imageCandidateScore(candidate, query);
        if (isStrongQueryCandidate(candidate, query)) {
            haveStrongCandidate = true;
        }
    }

    for (int i = 0; i < candidates.size(); i++) {
        WebImageCandidate candidate = candidates.get(i);
        if (candidate == null || candidate.imageUrl == null || candidate.imageUrl.length() == 0) {
            continue;
        }
        if (isKnownPoorImageFallback(candidate.imageUrl)) {
            continue;
        }
        if (haveStrongCandidate && !isStrongQueryCandidate(candidate, query) && candidate.score < 220) {
            continue;
        }
        if (!haveStrongCandidate && candidate.score < 20) {
            continue;
        }
        insertCandidateByScore(ranked, candidate);
        if (ranked.size() >= 32) {
            break;
        }
    }

    if (ranked.size() == 0) {
        for (int i = 0; i < candidates.size(); i++) {
            WebImageCandidate candidate = candidates.get(i);
            if (candidate == null || candidate.imageUrl == null || candidate.imageUrl.length() == 0) {
                continue;
            }
            if (isKnownPoorImageFallback(candidate.imageUrl)) {
                continue;
            }
            if (candidate.score >= -40) {
                insertCandidateByScore(ranked, candidate);
            }
            if (ranked.size() >= 16) {
                break;
            }
        }
    }
    return ranked;
}

private static void insertCandidateByScore(ArrayList<WebImageCandidate> list, WebImageCandidate candidate) {
    if (list == null || candidate == null) {
        return;
    }
    int index = 0;
    while (index < list.size()) {
        WebImageCandidate existing = list.get(index);
        int existingScore = existing == null ? -1000 : existing.score;
        if (candidate.score > existingScore) {
            break;
        }
        index++;
    }
    list.add(index, candidate);
}

private static int imageCandidateScore(WebImageCandidate candidate, String query) {
    if (candidate == null || candidate.imageUrl == null) {
        return -1000;
    }
    String metadata = buildCandidateSearchText(candidate);
    String normalized = normalizeSearchText(metadata);
    String lowerQuery = query == null ? "" : query.toLowerCase(Locale.US);

    int score = 0;
    if ("wikipedia".equals(candidate.provider)) {
        score += 90;
    } else if ("wikimedia".equals(candidate.provider)) {
        score += 60;
    } else if ("duckduckgo".equals(candidate.provider)) {
        score += 50;
    } else if ("bing".equals(candidate.provider)) {
        score += 18;
    } else if ("google".equals(candidate.provider)) {
        score += 10;
    }

    if (candidate.title != null && candidate.title.trim().length() > 0) {
        score += 28;
    }
    if (candidate.sourceUrl != null && candidate.sourceUrl.trim().length() > 0) {
        score += 18;
    }

    String[] tokens = importantQueryTokens(query);
    int matches = countMatchedTokens(normalized, tokens);
    score += matches * 55;
    if (tokens.length > 0 && matches == tokens.length) {
        score += 110;
    }

    String normalizedQuery = normalizeSearchText(query);
    if (normalizedQuery.length() > 0 && normalized.indexOf(normalizedQuery) >= 0) {
        score += 140;
    }

    if (isStrongQueryCandidate(candidate, query)) {
        score += 220;
    } else {
        score -= 120;
    }

    if (containsTransportKeyword(normalized) && !queryMentionsTransport(lowerQuery)) {
        score -= 190;
    }
    if ((lowerQuery.indexOf("tom") >= 0 && lowerQuery.indexOf("jerry") >= 0)
            && !(containsWord(normalized, "tom") && containsWord(normalized, "jerry"))) {
        score -= 260;
    }
    if ((lowerQuery.indexOf("rhino") >= 0 || lowerQuery.indexOf("rhinoceros") >= 0)
            && !(containsWord(normalized, "rhino") || containsWord(normalized, "rhinoceros"))) {
        score -= 220;
    }
    if (lowerQuery.indexOf("labrador") >= 0
            && !(containsAnyWord(normalized, new String[] { "labrador", "retriever", "dog", "puppy" }))) {
        score -= 220;
    }
    if (lowerQuery.indexOf("tom") >= 0 && lowerQuery.indexOf("jerry") >= 0
            && (containsWord(normalized, "cartoon") || containsWord(normalized, "animation") || containsWord(normalized, "character"))) {
        score += 90;
    }
    if (isLikelyLocationQuery(query)) {
        if (containsLocationCue(normalized)) {
            score += 140;
        }
        if (containsTravelOrCountryCue(normalized)) {
            score += 90;
        }
        if (containsLocationPenaltyKeyword(normalized)) {
            score -= 320;
        }
        if (!containsLocationCue(normalized) && !normalized.contains(normalizeSearchText(stripLeadingArticle(query)))) {
            score -= 160;
        }
    }
    return score;
}

private static boolean isAcceptableImageCandidate(WebImageCandidate candidate, String query) {
    if (candidate == null || candidate.imageUrl == null || candidate.imageUrl.length() == 0) {
        return false;
    }
    if (isKnownPoorImageFallback(candidate.imageUrl)) {
        return false;
    }
    if (candidate.score >= 140) {
        return true;
    }
    return isStrongQueryCandidate(candidate, query);
}

private static String buildCandidateSearchText(WebImageCandidate candidate) {
    StringBuilder builder = new StringBuilder();
    if (candidate == null) {
        return "";
    }
    appendSearchText(builder, candidate.title);
    appendSearchText(builder, candidate.sourceUrl);
    appendSearchText(builder, candidate.imageUrl);
    appendSearchText(builder, candidate.provider);
    return builder.toString().toLowerCase(Locale.US);
}

private static void appendSearchText(StringBuilder builder, String value) {
    if (builder == null || value == null || value.length() == 0) {
        return;
    }
    if (builder.length() > 0) {
        builder.append(' ');
    }
    String text = urlDecode(value);
    if (text == null) {
        text = value;
    }
    builder.append(text);
}

private static String normalizeSearchText(String value) {
    if (value == null) {
        return "";
    }
    String normalized = value.toLowerCase(Locale.US);
    normalized = normalized.replace('&', ' ');
    normalized = normalized.replaceAll("[^a-z0-9]+", " ");
    normalized = normalized.replaceAll("\\s+", " ").trim();
    return normalized;
}

private static int countMatchedTokens(String normalizedText, String[] tokens) {
    if (normalizedText == null || tokens == null) {
        return 0;
    }
    int matches = 0;
    for (int i = 0; i < tokens.length; i++) {
        if (containsWord(normalizedText, tokens[i])) {
            matches++;
        }
    }
    return matches;
}

private static boolean isStrongQueryCandidate(WebImageCandidate candidate, String query) {
    String normalizedText = normalizeSearchText(buildCandidateSearchText(candidate));
    String normalizedQuery = normalizeSearchText(query);
    if (normalizedQuery.length() == 0) {
        return true;
    }
    if (normalizedText.indexOf(normalizedQuery) >= 0) {
        return true;
    }

    String lowerQuery = query == null ? "" : query.toLowerCase(Locale.US);
    if (lowerQuery.indexOf("tom") >= 0 && lowerQuery.indexOf("jerry") >= 0) {
        return containsWord(normalizedText, "tom") && containsWord(normalizedText, "jerry");
    }
    if (lowerQuery.indexOf("rhino") >= 0 || lowerQuery.indexOf("rhinoceros") >= 0) {
        return containsAnyWord(normalizedText, new String[] { "rhino", "rhinoceros" });
    }
    if (lowerQuery.indexOf("labrador") >= 0 || lowerQuery.indexOf("puppy") >= 0 || lowerQuery.indexOf("dog") >= 0) {
        return containsAnyWord(normalizedText, new String[] { "labrador", "retriever", "dog", "puppy" });
    }
    if (isLikelyLocationQuery(query)) {
        String[] tokens = importantQueryTokens(stripLeadingArticle(query));
        int matches = countMatchedTokens(normalizedText, tokens);
        if (containsLocationPenaltyKeyword(normalizedText)) {
            return false;
        }
        return matches >= 1 && (containsLocationCue(normalizedText) || containsTravelOrCountryCue(normalizedText)
                || normalizedText.indexOf(normalizeSearchText(stripLeadingArticle(query))) >= 0);
    }

    String[] tokens = importantQueryTokens(query);
    if (tokens.length == 0) {
        return true;
    }
    int matches = countMatchedTokens(normalizedText, tokens);
    int required = 1;
    if (tokens.length >= 2) {
        required = 2;
    }
    if (tokens.length >= 5) {
        required = 3;
    }
    return matches >= Math.min(required, tokens.length);
}

private static boolean isLikelyLocationQuery(String query) {
    if (query == null) {
        return false;
    }
    String normalized = normalizeSearchText(stripLeadingArticle(query));
    if (normalized.length() == 0) {
        return false;
    }
    if (containsAnyWord(normalized, new String[] { "country", "city", "island", "islands", "province", "state", "beach", "mountain", "river", "lake", "capital", "village", "town", "landscape", "skyline", "travel", "tourism", "map", "flag" })) {
        return true;
    }
    return containsCountryName(normalized);
}

private static boolean containsCountryName(String normalized) {
    String[] countries = new String[] {
            "philippines", "japan", "china", "india", "thailand", "vietnam", "indonesia", "malaysia", "singapore",
            "cambodia", "laos", "myanmar", "south korea", "north korea", "mongolia", "taiwan", "nepal", "bhutan",
            "pakistan", "bangladesh", "sri lanka", "united kingdom", "england", "scotland", "wales", "ireland",
            "france", "germany", "spain", "italy", "portugal", "greece", "turkey", "netherlands", "belgium",
            "switzerland", "austria", "poland", "ukraine", "sweden", "norway", "finland", "denmark", "iceland",
            "united states", "usa", "canada", "mexico", "brazil", "argentina", "chile", "peru", "colombia",
            "venezuela", "ecuador", "bolivia", "australia", "new zealand", "egypt", "morocco", "south africa",
            "nigeria", "kenya", "tanzania", "ethiopia", "saudi arabia", "united arab emirates", "qatar", "oman"
    };
    return containsAnyWord(normalized, countries);
}

private static boolean containsLocationCue(String normalizedText) {
    return containsAnyWord(normalizedText, new String[] {
            "wikipedia", "wikimedia", "country", "city", "province", "state", "island", "islands",
            "capital", "republic", "nation", "archipelago", "asia", "europe", "africa", "travel", "tourism",
            "landscape", "skyline", "beach", "coast", "mountain", "aerial", "map", "flag"
    });
}

private static boolean containsTravelOrCountryCue(String normalizedText) {
    return containsAnyWord(normalizedText, new String[] {
            "travel", "tourism", "destination", "landscape", "skyline", "beach", "island", "archipelago",
            "country", "capital", "city", "flag", "map", "asia", "pacific"
    });
}

private static boolean containsLocationPenaltyKeyword(String normalizedText) {
    return containsAnyWord(normalizedText, new String[] {
            "plant", "flower", "leaf", "leaves", "nursery", "garden", "aglaonema", "philodendron", "pot",
            "headphone", "headphones", "earbuds", "smartphone", "phone", "case", "laptop", "keyboard",
            "cargo", "ship", "train", "rail", "station", "truck", "bus", "dog", "puppy", "cat"
    });
}

private static boolean containsWord(String normalizedText, String word) {
    if (normalizedText == null || normalizedText.length() == 0 || word == null || word.length() == 0) {
        return false;
    }
    String haystack = " " + normalizedText + " ";
    String needle = " " + normalizeSearchText(word) + " ";
    return haystack.indexOf(needle) >= 0;
}

private static boolean containsAnyWord(String normalizedText, String[] words) {
    if (words == null) {
        return false;
    }
    for (int i = 0; i < words.length; i++) {
        if (containsWord(normalizedText, words[i])) {
            return true;
        }
    }
    return false;
}

private static boolean containsTransportKeyword(String normalizedText) {
    return containsAnyWord(normalizedText, new String[] {
            "train", "rail", "railway", "station", "locomotive", "subway", "metro",
            "cargo", "ship", "vessel", "boat", "tanker", "freight", "truck", "bus"
    });
}

private static boolean queryMentionsTransport(String lowerQuery) {
    if (lowerQuery == null) {
        return false;
    }
    return lowerQuery.indexOf("train") >= 0 || lowerQuery.indexOf("rail") >= 0
            || lowerQuery.indexOf("station") >= 0 || lowerQuery.indexOf("subway") >= 0
            || lowerQuery.indexOf("metro") >= 0 || lowerQuery.indexOf("cargo") >= 0
            || lowerQuery.indexOf("ship") >= 0 || lowerQuery.indexOf("boat") >= 0
            || lowerQuery.indexOf("truck") >= 0 || lowerQuery.indexOf("bus") >= 0;
}

private static String[] importantQueryTokens(String query) {
    if (query == null) {
        return new String[0];
    }
    String cleaned = query.toLowerCase(Locale.US).replace('&', ' ');
    cleaned = cleaned.replaceAll("[^a-z0-9]+", " ");
    String[] raw = cleaned.split("\\s+");
    ArrayList<String> tokens = new ArrayList<String>();
    for (int i = 0; i < raw.length; i++) {
        String token = raw[i];
        if (token == null || token.length() < 3) {
            continue;
        }
        if (isImageQueryStopWord(token)) {
            continue;
        }
        if (!tokens.contains(token)) {
            tokens.add(token);
        }
    }
    return tokens.toArray(new String[tokens.size()]);
}

private static boolean isImageQueryStopWord(String token) {
    if (token == null) {
        return true;
    }
    return token.equals("image") || token.equals("picture") || token.equals("photo")
            || token.equals("show") || token.equals("give") || token.equals("find")
            || token.equals("with") || token.equals("the") || token.equals("and")
            || token.equals("for") || token.equals("from") || token.equals("one")
            || token.equals("this") || token.equals("that") || token.equals("please")
            || token.equals("another") || token.equals("me") || token.equals("an")
            || token.equals("of") || token.equals("to") || token.equals("at");
}

private static boolean isKnownPoorImageFallback(String candidate) {
    if (candidate == null) {
        return true;
    }
    String lower = candidate.toLowerCase(Locale.US);
    return lower.indexOf("picsum.photos") >= 0
            || lower.indexOf("loremflickr.com") >= 0
            || lower.indexOf("source.unsplash.com") >= 0
            || lower.indexOf("googlelogo") >= 0
            || lower.indexOf("favicon") >= 0
            || lower.indexOf("gstatic.com/images/branding") >= 0;
}

private static void addImageCandidatesFromPage(ArrayList<WebImageCandidate> results, String address, String provider) throws Exception {
    if (results == null || address == null || address.length() == 0) {
        return;
    }
    String html = downloadText(address);
    collectImageCandidatesFromText(results, html, provider);
}

private static void collectImageCandidatesFromText(ArrayList<WebImageCandidate> results, String text, String provider) {
    if (results == null || text == null || text.length() == 0 || results.size() >= 72) {
        return;
    }
    String normalized = text.replace("\\u003d", "=")
            .replace("\\u0026", "&")
            .replace("\\/", "/")
            .replace("&amp;", "&");
    collectJsonImageCandidates(results, normalized, "\\\"murl\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"", provider);
    collectJsonImageCandidates(results, normalized, "\"murl\"\\s*:\\s*\"([^\"]+)\"", provider);
    collectJsonImageCandidates(results, normalized, "\\\"ou\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"", provider);
    collectJsonImageCandidates(results, normalized, "\"ou\"\\s*:\\s*\"([^\"]+)\"", provider);
    collectJsonImageCandidates(results, normalized, "\\\"mediaurl\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"", provider);
    collectJsonImageCandidates(results, normalized, "\"mediaurl\"\\s*:\\s*\"([^\"]+)\"", provider);
    if (results.size() >= 72) {
        return;
    }
    Matcher matcher = Pattern.compile("https?://[^\\s\\\"'<>\\\\)\\]]+").matcher(normalized);
    while (matcher.find()) {
        String candidate = cleanupCandidateUrl(matcher.group());
        String resolved = null;
        if (isProbablyImageUrl(candidate)) {
            resolved = candidate;
        } else {
            resolved = tryExtractImageUrlFromSearchUrl(candidate);
        }
        if (isProbablyImageUrl(resolved)) {
            addCandidate(results, resolved, candidate, "", provider);
        }
        if (results.size() >= 72) {
            return;
        }
    }
}

private static void collectJsonImageCandidates(ArrayList<WebImageCandidate> results, String text, String regex, String provider) {
    if (results == null || text == null || regex == null || results.size() >= 72) {
        return;
    }
    try {
        Matcher matcher = Pattern.compile(regex).matcher(text);
        while (matcher.find()) {
            String value = matcher.group(1);
            value = value.replace("\\/", "/").replace("\\u0026", "&").replace("\\u003d", "=");
            value = urlDecode(value);
            value = cleanupCandidateUrl(value);
            if (isProbablyImageUrl(value)) {
                addCandidate(results, value, "", "", provider);
            }
            if (results.size() >= 72) {
                return;
            }
        }
    } catch (Exception ignored) {
    }
}

private static void addCandidate(ArrayList<WebImageCandidate> results, String imageUrl, String sourceUrl, String title, String provider) {
    if (results == null) {
        return;
    }
    String cleanImage = cleanupCandidateUrl(imageUrl);
    if (!isProbablyImageUrl(cleanImage)) {
        return;
    }
    if (isKnownPoorImageFallback(cleanImage)) {
        return;
    }
    String cleanSource = cleanupCandidateUrl(sourceUrl);
    String key = normalizeCandidateKey(cleanImage, cleanSource, title);
    for (int i = 0; i < results.size(); i++) {
        WebImageCandidate existing = results.get(i);
        if (existing == null) {
            continue;
        }
        String existingKey = normalizeCandidateKey(existing.imageUrl, existing.sourceUrl, existing.title);
        if (key.equals(existingKey)) {
            if ((existing.sourceUrl == null || existing.sourceUrl.length() == 0) && cleanSource != null && cleanSource.length() > 0) {
                existing.sourceUrl = cleanSource;
            }
            if ((existing.title == null || existing.title.length() == 0) && title != null && title.length() > 0) {
                existing.title = title;
            }
            if ((existing.provider == null || existing.provider.length() == 0) && provider != null && provider.length() > 0) {
                existing.provider = provider;
            }
            return;
        }
    }
    WebImageCandidate candidate = new WebImageCandidate();
    candidate.imageUrl = cleanImage;
    candidate.sourceUrl = cleanSource == null ? "" : cleanSource;
    candidate.title = title == null ? "" : title.trim();
    candidate.provider = provider == null ? "" : provider;
    results.add(candidate);
}

private static String normalizeCandidateKey(String imageUrl, String sourceUrl, String title) {
    StringBuilder builder = new StringBuilder();
    String image = normalizeUrlForKey(imageUrl);
    String source = normalizeUrlForKey(sourceUrl);
    String text = normalizeSearchText(title);
    builder.append(image);
    if (source.length() > 0) {
        builder.append('|').append(source);
    }
    if (text.length() > 0) {
        builder.append('|').append(text);
    }
    return builder.toString();
}

private static String normalizeUrlForKey(String value) {
    if (value == null) {
        return "";
    }
    String normalized = urlDecode(value);
    if (normalized == null) {
        normalized = value;
    }
    normalized = normalized.toLowerCase(Locale.US).trim();
    int hash = normalized.indexOf('#');
    if (hash >= 0) {
        normalized = normalized.substring(0, hash);
    }
    int query = normalized.indexOf('?');
    if (query >= 0) {
        normalized = normalized.substring(0, query);
    }
    if (normalized.startsWith("https://")) {
        normalized = normalized.substring(8);
    } else if (normalized.startsWith("http://")) {
        normalized = normalized.substring(7);
    }
    if (normalized.startsWith("www.")) {
        normalized = normalized.substring(4);
    }
    return normalized;
}

private static String tryExtractImageUrlFromSearchUrl(String address) {
    if (address == null || address.length() == 0) {
        return null;
    }
    String[] keys = new String[] { "imgurl=", "mediaurl=", "mediaUrl=" };
    for (int i = 0; i < keys.length; i++) {
        int index = address.indexOf(keys[i]);
        if (index >= 0) {
            int start = index + keys[i].length();
            int end = address.indexOf('&', start);
            String value = end >= 0 ? address.substring(start, end) : address.substring(start);
            value = urlDecode(value);
            value = cleanupCandidateUrl(value);
            if (isProbablyImageUrl(value)) {
                return value;
            }
        }
    }
    return null;
}

private static String urlDecode(String value) {
    if (value == null) {
        return null;
    }
    try {
        return URLDecoder.decode(value, "UTF-8");
    } catch (Exception ignored) {
        return value;
    }
}

private static String cleanupCandidateUrl(String value) {
    if (value == null) {
        return null;
    }
    String cleaned = value.trim();
    while (cleaned.endsWith("\\") || cleaned.endsWith(",") || cleaned.endsWith("\"")
            || cleaned.endsWith("'") || cleaned.endsWith(")") || cleaned.endsWith("]")
            || cleaned.endsWith("}")) {
        cleaned = cleaned.substring(0, cleaned.length() - 1);
    }
    return cleaned;
}

private static boolean isProbablyImageUrl(String value) {
    if (value == null || value.length() == 0) {
        return false;
    }
    String lower = value.toLowerCase(Locale.US);
    if (lower.startsWith("data:")) {
        return false;
    }
    if (lower.indexOf("googlelogo") >= 0 || lower.indexOf("/logos/") >= 0
            || lower.indexOf("favicon") >= 0 || lower.indexOf("sprite") >= 0
            || lower.indexOf("gstatic.com/images/branding") >= 0) {
        return false;
    }
    return lower.indexOf("encrypted-tbn0.gstatic.com") >= 0
            || lower.indexOf("gstatic.com/images") >= 0
            || lower.indexOf("googleusercontent.com") >= 0
            || lower.indexOf("mm.bing.net") >= 0
            || lower.indexOf("th.bing.com") >= 0
            || lower.indexOf("images.unsplash.com") >= 0
            || lower.indexOf("i.pinimg.com") >= 0
            || lower.indexOf("upload.wikimedia.org") >= 0
            || lower.indexOf("wikimedia.org") >= 0
            || lower.indexOf("pollinations.ai") >= 0
            || lower.indexOf(".jpg") >= 0
            || lower.indexOf(".jpeg") >= 0
            || lower.indexOf(".png") >= 0
            || lower.indexOf(".webp") >= 0
            || lower.indexOf(".gif") >= 0
            || lower.indexOf("format=jpg") >= 0
            || lower.indexOf("format=jpeg") >= 0
            || lower.indexOf("format=png") >= 0
            || lower.indexOf("format=webp") >= 0;
}

private static String downloadText(String address) throws Exception {
    HttpURLConnection connection = null;
    InputStream input = null;
    try {
        URL url = new URL(address);
        connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(20000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; Jarvis) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36");
        connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
        connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        connection.connect();
        int code = connection.getResponseCode();
        input = code >= 200 && code < 400 ? connection.getInputStream() : connection.getErrorStream();
        String response = readStream(input);
        if (code < 200 || code >= 400) {
            throw new Exception("HTTP " + code + " " + shorten(response, 140));
        }
        return response;
    } finally {
        if (input != null) {
            try {
                input.close();
            } catch (Exception ignored) {
            }
        }
        if (connection != null) {
            connection.disconnect();
        }
    }
}

private static void rememberImageSearch(Context context, String query, int index) {
    if (context == null) {
        return;
    }
    SharedPreferences prefs = context.getSharedPreferences(JarvisCommandCenter.PREFS_NAME, Context.MODE_PRIVATE);
    prefs.edit()
            .putString(PREF_LAST_WEB_IMAGE_QUERY, query == null ? "" : query)
            .putInt(PREF_LAST_WEB_IMAGE_INDEX, index)
            .apply();
}

private static String getRememberedImageSearchQuery(Context context) {
    if (context == null) {
        return "";
    }
    SharedPreferences prefs = context.getSharedPreferences(JarvisCommandCenter.PREFS_NAME, Context.MODE_PRIVATE);
    return prefs.getString(PREF_LAST_WEB_IMAGE_QUERY, "");
}

private static int getRememberedImageSearchIndex(Context context) {
    if (context == null) {
        return -1;
    }
    SharedPreferences prefs = context.getSharedPreferences(JarvisCommandCenter.PREFS_NAME, Context.MODE_PRIVATE);
    return prefs.getInt(PREF_LAST_WEB_IMAGE_INDEX, -1);
}


private static byte[] performOpenAiStyleImageGeneration(Context context, String prompt, String apiKey, String model) throws Exception {
    JSONObject body = new JSONObject();
    body.put("model", model);
    body.put("prompt", prompt);
    body.put("size", "1024x1024");
    body.put("response_format", "b64_json");

    String response = postJson(getImageGenerationsUrl(context), body.toString(), apiKey);
    JSONObject json = new JSONObject(response);
    if (json.has("error")) {
        throw new Exception(extractApiErrorMessage(response));
    }

    JSONArray data = json.optJSONArray("data");
    if (data == null || data.length() == 0) {
        throw new Exception("the provider returned no image data");
    }

    JSONObject first = data.optJSONObject(0);
    if (first == null) {
        throw new Exception("the provider returned an invalid image payload");
    }

    String base64 = first.optString("b64_json", "");
    if (base64 != null && base64.length() > 0) {
        return Base64.decode(base64, Base64.DEFAULT);
    }

    String imageUrl = first.optString("url", "");
    if (imageUrl == null || imageUrl.length() == 0) {
        throw new Exception("the provider did not return a usable image");
    }

    return downloadBinary(imageUrl);
}

private static String[] getImageGenerationModelFallbacks(Context context) {
    String baseUrl = getBaseUrl(context).toLowerCase(Locale.UK);
    String provider = getProvider(context);
    if (PROVIDER_OPENAI.equals(provider) || baseUrl.indexOf("api.openai.com") >= 0) {
        return new String[] { "gpt-image-1", "dall-e-3", "dall-e-2" };
    }
    return new String[] {
            "dall-e-3",
            "openai/dall-e-3",
            "dall-e-2",
            "openai/dall-e-2",
            "gpt-image-1",
            "openai/gpt-image-1"
    };
}

private static String[] buildFreeImageGenerationUrls(String prompt) {
    String safePrompt = prompt == null ? "jarvis image" : prompt.trim();
    if (safePrompt.length() == 0) {
        safePrompt = "jarvis image";
    }
    String encodedPrompt = safeUrlEncode(safePrompt);
    return new String[] {
            POLLINATIONS_IMAGE_BASE_URL + encodedPrompt + "?model=flux&width=1024&height=1024&nologo=true&enhance=true&safe=false",
            POLLINATIONS_IMAGE_BASE_URL + encodedPrompt + "?model=turbo&width=1024&height=1024&nologo=true&enhance=true&safe=false",
            POLLINATIONS_IMAGE_BASE_URL + encodedPrompt + "?model=kontext&width=1024&height=1024&nologo=true&enhance=true&safe=false",
            POLLINATIONS_IMAGE_BASE_URL + encodedPrompt + "?model=flux-realism&width=1024&height=1024&nologo=true&enhance=true&safe=false",
            POLLINATIONS_IMAGE_ALT_URL + encodedPrompt + "?width=1024&height=1024&nologo=true&enhance=true&safe=false",
            POLLINATIONS_IMAGE_BASE_URL + encodedPrompt + "?width=1024&height=1024&nologo=true&enhance=true&safe=false"
    };
}

private static String safeUrlEncode(String value) {
    try {
        return URLEncoder.encode(value == null ? "" : value, "UTF-8");
    } catch (Exception ignored) {
        String safe = value == null ? "" : value;
        safe = safe.replace(" ", "%20");
        safe = safe.replace("\n", "%20");
        safe = safe.replace("\r", "%20");
        return safe;
    }
}

private static boolean looksLikeImageBytes(byte[] bytes) {
    if (bytes == null || bytes.length < 16) {
        return false;
    }
    if ((bytes[0] & 255) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47) {
        return true;
    }
    if ((bytes[0] & 255) == 0xFF && (bytes[1] & 255) == 0xD8) {
        return true;
    }
    if (bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F') {
        return true;
    }
    return false;
}

private static String guessImageMimeType(byte[] bytes) {
    if (bytes == null || bytes.length < 4) {
        return "image/png";
    }
    if ((bytes[0] & 255) == 0xFF && (bytes[1] & 255) == 0xD8) {
        return "image/jpeg";
    }
    if (bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F') {
        return "image/gif";
    }
    return "image/png";
}

public static void requestVisionRecognition(final Context context, final Bitmap bitmap, final boolean productMode, final VisionCallback callback) {
    final Context appContext = context.getApplicationContext();
    new AsyncTask<Void, Void, String>() {
        protected String doInBackground(Void... params) {
            if (bitmap == null) {
                return "No image was available for AI analysis.";
            }
            String providerError = "";
            try {
                String key = getActiveApiKey(appContext);
                if (key != null && key.trim().length() > 0) {
                    try {
                        return requestVisionRecognitionWithEndpoint(appContext, bitmap, productMode, getChatCompletionsUrl(appContext), key.trim(), getModel(appContext));
                    } catch (Exception error) {
                        providerError = safeMessage(error);
                    }
                } else {
                    providerError = "No active AI key was configured.";
                }
                try {
                    return requestVisionRecognitionWithEndpoint(appContext, bitmap, productMode, POLLINATIONS_TEXT_OPENAI_URL, "", "openai-fast");
                } catch (Exception freeError) {
                    String freeMessage = safeMessage(freeError);
                    if (providerError != null && providerError.length() > 0) {
                        return "AI vision failed. Provider: " + providerError + ". Free fallback: " + freeMessage;
                    }
                    return "AI vision failed. Free fallback: " + freeMessage;
                }
            } catch (Exception error) {
                return "AI vision failed: " + safeMessage(error);
            }
        }

        protected void onPostExecute(String result) {
            if (callback != null) {
                callback.onVisionResult(result);
            }
        }
    }.execute();
}

private static String requestVisionRecognitionWithEndpoint(Context context, Bitmap bitmap, boolean productMode, String url, String key, String model) throws Exception {
    String base64Image = bitmapToBase64(scaleBitmapForVision(bitmap));

    JSONObject body = new JSONObject();
    body.put("model", model == null || model.length() == 0 ? "openai-fast" : model);
    body.put("temperature", 0.1);
    body.put("max_tokens", 260);

    JSONArray messages = new JSONArray();

    JSONObject system = new JSONObject();
    system.put("role", "system");
    system.put("content", productMode
            ? "You identify products and everyday objects from user photos. Be concrete and practical. If the photo shows a phone, headphones, can, bottle, remote, keyboard, screen, charger, packet, toy, tool, or other object, name that object directly. Avoid abstract colour/shape descriptions unless you genuinely cannot identify it."
            : "You identify the main visible object or scene in a user photo. Be concrete and concise.");
    messages.put(system);

    JSONObject user = new JSONObject();
    user.put("role", "user");

    JSONArray content = new JSONArray();

    JSONObject textPart = new JSONObject();
    textPart.put("type", "text");
    textPart.put("text", productMode
            ? "What is this item? Start with 'Likely product:' and give the most likely object name first. For example, say smartphone, over-ear headphones, drink can, laptop, charger, remote, etc. Then add a short confidence note and any visible clues. Do not answer with vague visual descriptions unless no object is identifiable."
            : "What is this? Start with 'Likely subject:' and identify the main visible thing concretely.");
    content.put(textPart);

    JSONObject imagePart = new JSONObject();
    imagePart.put("type", "image_url");
    JSONObject imageUrl = new JSONObject();
    imageUrl.put("url", "data:image/jpeg;base64," + base64Image);
    imagePart.put("image_url", imageUrl);
    content.put(imagePart);

    user.put("content", content);
    messages.put(user);
    body.put("messages", messages);

    String response = postJson(url, body.toString(), key == null ? "" : key);
    JSONObject json = new JSONObject(response);
    if (json.has("error")) {
        throw new Exception(extractApiErrorMessage(response));
    }
    JSONArray choices = json.optJSONArray("choices");
    if (choices != null && choices.length() > 0) {
        JSONObject message = choices.getJSONObject(0).optJSONObject("message");
        if (message != null) {
            String contentText = message.optString("content", "").trim();
            if (contentText.length() > 0) {
                return contentText;
            }
        }
    }
    throw new Exception("AI vision returned no usable text");
}

private static Bitmap scaleBitmapForVision(Bitmap source) {
    if (source == null) {
        return null;
    }
    int width = source.getWidth();
    int height = source.getHeight();
    if (width <= 0 || height <= 0) {
        return source;
    }
    int largest = Math.max(width, height);
    if (largest <= 1280) {
        return source;
    }
    float scale = 1280.0f / (float) largest;
    int nextWidth = Math.max(1, Math.round(width * scale));
    int nextHeight = Math.max(1, Math.round(height * scale));
    return Bitmap.createScaledBitmap(source, nextWidth, nextHeight, true);
}

private static String bitmapToBase64(Bitmap bitmap) throws Exception {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    try {
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output);
        output.flush();
        return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP);
    } finally {
        try {
            output.close();
        } catch (Exception ignored) {
        }
    }
}

private static byte[] downloadImageCandidate(String address) throws Exception {
    byte[] bytes = downloadBinary(address);
    if (!looksLikeImageBytes(bytes)) {
        throw new Exception("image result did not return displayable image data");
    }
    return bytes;
}

private static byte[] downloadBinary(String address) throws Exception {
    HttpURLConnection connection = null;
    InputStream input = null;
    ByteArrayOutputStream output = null;
    try {
        URL url = new URL(address);
        connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(90000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; Jarvis) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36");
        connection.setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8");
        connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
        int code = connection.getResponseCode();
        input = code >= 200 && code < 400 ? connection.getInputStream() : connection.getErrorStream();
        output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while (input != null && (read = input.read(buffer)) > 0) {
            output.write(buffer, 0, read);
        }
        if (code < 200 || code >= 400) {
            throw new Exception("HTTP " + code);
        }
        return output.toByteArray();
    } finally {
        if (output != null) {
            try {
                output.close();
            } catch (Exception ignored) {
            }
        }
        if (input != null) {
            try {
                input.close();
            } catch (Exception ignored) {
            }
        }
        if (connection != null) {
            connection.disconnect();
        }
    }
}


private static String buildImageFileName(String prompt) {
    String base = prompt == null ? "jarvis_image" : prompt.toLowerCase(Locale.UK);
    base = base.replaceAll("[^a-z0-9]+", "_");
    while (base.startsWith("_")) {
        base = base.substring(1);
    }
    while (base.endsWith("_")) {
        base = base.substring(0, base.length() - 1);
    }
    if (base.length() == 0) {
        base = "jarvis_image";
    }
    if (base.length() > 36) {
        base = base.substring(0, 36);
    }
    return base + ".png";
}


private static String appendError(String oldValue, String nextValue) {
    if (nextValue == null || nextValue.length() == 0) {
        return oldValue == null ? "" : oldValue;
    }
    if (oldValue == null || oldValue.length() == 0) {
        return nextValue;
    }
    return oldValue + " | " + nextValue;
}

private static String shorten(String value, int maxLength) {
    if (value == null) {
        return "";
    }
    if (value.length() <= maxLength) {
        return value;
    }
    return value.substring(0, maxLength) + "...";
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
            connection.setRequestProperty("User-Agent", "JarvisAIDE/1.4.4 Android");
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
            if (apiKey != null && apiKey.length() > 0) {
                connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            }
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
