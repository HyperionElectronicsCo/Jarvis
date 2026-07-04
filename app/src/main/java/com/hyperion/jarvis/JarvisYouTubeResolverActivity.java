package com.hyperion.jarvis;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JarvisYouTubeResolverActivity extends Activity {
    public static final String EXTRA_QUERY = "query";
    public static final String EXTRA_AUTO_PLAY = "auto_play";

    private static final String OFFICIAL_YOUTUBE_PACKAGE = "com.google.android.youtube";
    private static final String REVANCED_YOUTUBE_PACKAGE = "app.revanced.android.youtube";
    private static final String YOUTUBE_MUSIC_PACKAGE = "com.google.android.apps.youtube.music";
    private static final String[] VIDEO_PACKAGE_PRIORITY = new String[] {
            OFFICIAL_YOUTUBE_PACKAGE,
            REVANCED_YOUTUBE_PACKAGE,
            YOUTUBE_MUSIC_PACKAGE
    };
    private static final String[] SEARCH_PACKAGE_PRIORITY = new String[] {
            OFFICIAL_YOUTUBE_PACKAGE,
            REVANCED_YOUTUBE_PACKAGE
    };

    private TextView statusText;
    private String query;
    private boolean autoPlay;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        query = getIntent().getStringExtra(EXTRA_QUERY);
        autoPlay = getIntent().getBooleanExtra(EXTRA_AUTO_PLAY, true);
        if (query == null || query.trim().length() == 0) {
            query = "random music mix";
        }
        query = query.trim();
        buildUi();
        new ResolveTask().execute(new String[] { query });
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(32, 32, 32, 32);
        root.setBackgroundColor(0xff02070a);

        statusText = new TextView(this);
        statusText.setTextColor(0xff62eaff);
        statusText.setTextSize(18);
        statusText.setGravity(Gravity.CENTER);
        statusText.setText("Resolving YouTube result for\n" + query + "...");
        root.addView(statusText, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(root);
    }

    private class ResolveTask extends AsyncTask<String, String, String> {
        protected String doInBackground(String[] values) {
            String requested = values != null && values.length > 0 ? values[0] : query;
            if (requested == null || requested.trim().length() == 0) {
                requested = "random music mix";
            }
            requested = requested.trim();
            publishProgress(new String[] { "Searching YouTube for " + requested + "..." });
            return resolveFirstVideoId(requested);
        }

        protected void onProgressUpdate(String[] values) {
            if (values != null && values.length > 0 && statusText != null) {
                statusText.setText(values[0]);
            }
        }

        protected void onPostExecute(String videoId) {
            if (videoId != null && videoId.length() == 11) {
                if (statusText != null) {
                    statusText.setText("Opening best YouTube match...");
                }
                if (openVideo(videoId)) {
                    finishSoon();
                    return;
                }
            }
            if (statusText != null) {
                statusText.setText("Could not resolve a direct video. Opening YouTube search...");
            }
            openSearch(query);
            finishSoon();
        }
    }

    private String resolveFirstVideoId(String searchQuery) {
        try {
            String url = "https://www.youtube.com/results?search_query=" + Uri.encode(searchQuery);
            String html = downloadText(url);
            String fromJson = firstRegexGroup(html, "\\\"videoId\\\"\\s*:\\s*\\\"([A-Za-z0-9_-]{11})\\\"");
            if (fromJson != null) {
                return fromJson;
            }
            String fromWatch = firstRegexGroup(html, "watch\\?v=([A-Za-z0-9_-]{11})");
            if (fromWatch != null) {
                return fromWatch;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String downloadText(String urlText) throws Exception {
        HttpURLConnection connection = null;
        InputStream input = null;
        try {
            URL url = new URL(urlText);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(9000);
            connection.setReadTimeout(12000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36");
            connection.setRequestProperty("Accept-Language", Locale.UK.toString().replace('_', '-') + ",en;q=0.8");
            input = new BufferedInputStream(connection.getInputStream());
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] bytes = new byte[4096];
            int read;
            int total = 0;
            while ((read = input.read(bytes)) != -1) {
                buffer.write(bytes, 0, read);
                total += read;
                if (total > 1600000) {
                    break;
                }
            }
            return new String(buffer.toByteArray(), "UTF-8");
        } finally {
            try {
                if (input != null) {
                    input.close();
                }
            } catch (Exception ignored) {
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String firstRegexGroup(String text, String regex) {
        if (text == null || regex == null) {
            return null;
        }
        try {
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(text);
            ArrayList<String> seen = new ArrayList<String>();
            while (matcher.find()) {
                String value = matcher.group(1);
                if (value != null && value.length() == 11 && !seen.contains(value)) {
                    seen.add(value);
                    return value;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private boolean openVideo(String videoId) {
        if (videoId == null || videoId.length() == 0) {
            return false;
        }
        String httpsUrl = "https://www.youtube.com/watch?v=" + Uri.encode(videoId);
        String vndUrl = "vnd.youtube:" + Uri.encode(videoId);
        for (int i = 0; i < VIDEO_PACKAGE_PRIORITY.length; i++) {
            if (tryOpenUri(vndUrl, VIDEO_PACKAGE_PRIORITY[i])) {
                return true;
            }
            if (tryOpenUri(httpsUrl, VIDEO_PACKAGE_PRIORITY[i])) {
                return true;
            }
        }
        return tryOpenUri(httpsUrl, null);
    }

    private void openSearch(String searchQuery) {
        if (searchQuery == null || searchQuery.trim().length() == 0) {
            searchQuery = "random music mix";
        }
        if (autoPlay) {
            JarvisAccessibilityService.scheduleYouTubeFirstResultTap(searchQuery);
        }
        String appUri = "vnd.youtube://results?search_query=" + Uri.encode(searchQuery);
        String webUri = "https://www.youtube.com/results?search_query=" + Uri.encode(searchQuery);
        for (int i = 0; i < SEARCH_PACKAGE_PRIORITY.length; i++) {
            if (tryOpenUri(appUri, SEARCH_PACKAGE_PRIORITY[i])) {
                return;
            }
            if (tryOpenUri(webUri, SEARCH_PACKAGE_PRIORITY[i])) {
                return;
            }
        }
        if (!tryOpenUri(webUri, null)) {
            String google = "https://www.google.com/search?q=" + Uri.encode("YouTube " + searchQuery);
            tryOpenUri(google, null);
        }
    }

    private boolean tryOpenUri(String uriText, String packageName) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uriText));
            if (packageName != null && packageName.length() > 0) {
                intent.setPackage(packageName);
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void finishSoon() {
        try {
            statusText.postDelayed(new Runnable() {
                public void run() {
                    finish();
                }
            }, 700);
        } catch (Exception ignored) {
            finish();
        }
    }
}
