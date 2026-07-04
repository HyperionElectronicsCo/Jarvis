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
    private static final String DEFAULT_MODEL = "gpt-4o-mini";

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
            prefs.edit()
                    .putString(PREF_OPENAI_KEYS, builder.toString())
                    .putString(PREF_OPENAI_KEY, getFirstLine(builder.toString()))
                    .putInt(PREF_OPENAI_ACTIVE_INDEX, 0)
                    .commit();
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
        return "AI core is configured with " + count + " local key" + (count == 1 ? "" : "s") + ". Active key is " + getActiveKeyNumber(context) + " of " + count + ", ending " + keyEnding(active) + ". Current model is " + getModel(context) + ".";
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
                return "Active AI key test: " + testKeyBlocking(key);
            }

            protected void onPostExecute(String result) {
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
                    String result = testKeyBlocking(keys[i]);
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

    private static String testKeyBlocking(String key) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL("https://api.openai.com/v1/models");
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(16000);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Authorization", "Bearer " + key.trim());
            connection.setRequestProperty("User-Agent", "JarvisAIDE/1.15 Android");
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
                    system.put("content", "You are JARVIS inside an Android assistant app. Be helpful, concise and practical. If controlling the phone is needed, explain what command the user can say.");
                    messages.put(system);
                    JSONObject user = new JSONObject();
                    user.put("role", "user");
                    user.put("content", prompt);
                    messages.put(user);
                    body.put("messages", messages);
                    body.put("temperature", 0.6);
                    String response = postJson("https://api.openai.com/v1/chat/completions", body.toString(), key.trim());
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
                deliver(output, result);
            }
        }.execute();
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
        return value.startsWith("sk-") && value.length() >= 20 && value.indexOf(' ') < 0;
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
            connection.setRequestProperty("User-Agent", "JarvisAIDE/1.13 Android");
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
