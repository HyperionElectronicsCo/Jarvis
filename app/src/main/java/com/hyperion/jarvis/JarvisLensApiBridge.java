package com.hyperion.jarvis;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

public final class JarvisLensApiBridge implements JarvisVisionBridge {
    public static final String PREF_LENS_API_URL = "vision_lens_api_url_v53";
    public static final String PREF_LENS_API_KEY = "vision_lens_api_key_v53";

    public JarvisVisionBridgeResult identify(Context context, Bitmap bitmap, boolean productMode) {
        if (context == null) {
            return JarvisVisionBridgeResult.unavailable("Lens API", "No context was available.");
        }
        if (bitmap == null) {
            return JarvisVisionBridgeResult.unavailable("Lens API", "No image was available for Lens API recognition.");
        }
        String url = getEndpoint(context);
        if (url.length() == 0) {
            return JarvisVisionBridgeResult.unavailable("Lens API", "No Lens-style API endpoint is configured. Say: set lens api url followed by your endpoint.");
        }
        try {
            String response = postImageJson(context, url, bitmap);
            String label = extractBestLabel(response);
            String details = compact(response, 420);
            if (label.length() > 0) {
                return JarvisVisionBridgeResult.recognised("Lens API", label, "Structured Lens API response: " + details, 0.92f);
            }
            return JarvisVisionBridgeResult.unavailable("Lens API", "The Lens API responded but no product title could be extracted. Response: " + details);
        } catch (Exception error) {
            return JarvisVisionBridgeResult.unavailable("Lens API", "Lens API request failed: " + safeMessage(error));
        }
    }

    public static void setEndpoint(Context context, String endpoint) {
        if (context == null) return;
        if (endpoint == null) endpoint = "";
        context.getSharedPreferences(JarvisCommandCenter.PREFS_NAME, Context.MODE_PRIVATE).edit().putString(PREF_LENS_API_URL, endpoint.trim()).apply();
    }

    public static void setApiKey(Context context, String key) {
        if (context == null) return;
        if (key == null) key = "";
        context.getSharedPreferences(JarvisCommandCenter.PREFS_NAME, Context.MODE_PRIVATE).edit().putString(PREF_LENS_API_KEY, key.trim()).apply();
    }

    public static void clear(Context context) {
        if (context == null) return;
        context.getSharedPreferences(JarvisCommandCenter.PREFS_NAME, Context.MODE_PRIVATE).edit().remove(PREF_LENS_API_URL).remove(PREF_LENS_API_KEY).apply();
    }

    public static String getEndpoint(Context context) {
        if (context == null) return "";
        String value = context.getSharedPreferences(JarvisCommandCenter.PREFS_NAME, Context.MODE_PRIVATE).getString(PREF_LENS_API_URL, "");
        return value == null ? "" : value.trim();
    }

    public static String getApiKey(Context context) {
        if (context == null) return "";
        String value = context.getSharedPreferences(JarvisCommandCenter.PREFS_NAME, Context.MODE_PRIVATE).getString(PREF_LENS_API_KEY, "");
        return value == null ? "" : value.trim();
    }

    public static String getStatus(Context context) {
        String endpoint = getEndpoint(context);
        String key = getApiKey(context);
        if (endpoint.length() == 0) {
            return "Lens API is not configured. Say: set lens api url followed by your self-hosted Google Lens endpoint or scraping API endpoint.";
        }
        return "Lens API endpoint is configured: " + endpoint + ". API key: " + (key.length() == 0 ? "not set" : "set") + ".";
    }

    private String postImageJson(Context context, String endpoint, Bitmap bitmap) throws Exception {
        ByteArrayOutputStream imageOut = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, imageOut);
        String imageBase64 = Base64.encodeToString(imageOut.toByteArray(), Base64.NO_WRAP);

        JSONObject payload = new JSONObject();
        payload.put("image_base64", imageBase64);
        payload.put("image", imageBase64);
        payload.put("imageData", imageBase64);
        payload.put("mime_type", "image/jpeg");
        payload.put("mimeType", "image/jpeg");
        payload.put("query", "identify exact product brand model visual match");
        payload.put("type", "product");

        HttpURLConnection connection = null;
        try {
            URL url = new URL(endpoint);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json, text/plain, */*");
            String key = getApiKey(context);
            if (key.length() > 0) {
                connection.setRequestProperty("Authorization", "Bearer " + key);
                connection.setRequestProperty("x-api-key", key);
            }
            OutputStream out = connection.getOutputStream();
            out.write(payload.toString().getBytes("UTF-8"));
            out.flush();
            out.close();
            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 400 ? connection.getInputStream() : connection.getErrorStream();
            String response = readAll(stream);
            if (code < 200 || code >= 400) {
                throw new Exception("HTTP " + code + ": " + compact(response, 300));
            }
            return response;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private String extractBestLabel(String text) {
        if (text == null || text.trim().length() == 0) return "";
        try {
            String trimmed = text.trim();
            if (trimmed.startsWith("{")) {
                return extractFromJson(new JSONObject(trimmed));
            }
            if (trimmed.startsWith("[")) {
                return extractFromJson(new JSONArray(trimmed));
            }
        } catch (Exception ignored) {
        }
        String fallback = extractByRegex(text, "\\\"(?:title|name|product|label|text|description)\\\"\\s*:\\s*\\\"([^\\\"]{3,160})\\\"");
        return cleanLabel(fallback);
    }

    private String extractFromJson(Object node) {
        if (node == null) return "";
        if (node instanceof JSONObject) {
            JSONObject object = (JSONObject) node;
            String[] keys = new String[] { "product", "title", "name", "label", "text", "description", "best_match", "match", "source_title" };
            for (int i = 0; i < keys.length; i++) {
                String value = object.optString(keys[i], "");
                value = cleanLabel(value);
                if (isUsefulLensLabel(value)) return value;
            }
            java.util.Iterator<String> it = object.keys();
            while (it.hasNext()) {
                String key = it.next();
                Object child = object.opt(key);
                String label = extractFromJson(child);
                if (isUsefulLensLabel(label)) return label;
            }
        } else if (node instanceof JSONArray) {
            JSONArray array = (JSONArray) node;
            for (int i = 0; i < array.length() && i < 30; i++) {
                String label = extractFromJson(array.opt(i));
                if (isUsefulLensLabel(label)) return label;
            }
        }
        return "";
    }

    private boolean isUsefulLensLabel(String value) {
        if (value == null) return false;
        String label = cleanLabel(value);
        if (label.length() < 3) return false;
        String lower = label.toLowerCase(Locale.UK);
        if (lower.indexOf("http") >= 0 || lower.indexOf("data:") >= 0 || lower.indexOf("image/jpeg") >= 0) return false;
        if (lower.indexOf("unknown") >= 0 || lower.indexOf("no result") >= 0) return false;
        return true;
    }

    private String cleanLabel(String value) {
        if (value == null) return "";
        String label = value.replace('\n', ' ').replace('\r', ' ').trim();
        label = label.replaceAll("\\s+", " ");
        if (label.length() > 140) label = label.substring(0, 140).trim();
        return label;
    }

    private String extractByRegex(String text, String regex) {
        try {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(regex).matcher(text);
            if (matcher.find()) return matcher.group(1);
        } catch (Exception ignored) {
        }
        return "";
    }

    private String readAll(InputStream stream) throws Exception {
        if (stream == null) return "";
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line).append('\n');
        }
        reader.close();
        return builder.toString();
    }

    private String compact(String text, int max) {
        if (text == null) return "";
        String value = text.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim();
        if (value.length() > max) return value.substring(0, max) + "...";
        return value;
    }

    private String safeMessage(Throwable error) {
        if (error == null) return "unknown error";
        Throwable cause = error.getCause();
        if (cause != null && cause.getMessage() != null && cause.getMessage().length() > 0) return cause.getMessage();
        String message = error.getMessage();
        if (message == null || message.length() == 0) message = error.getClass().getName();
        return message;
    }
}
