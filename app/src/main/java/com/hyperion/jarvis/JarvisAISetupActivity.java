package com.hyperion.jarvis;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class JarvisAISetupActivity extends Activity implements JarvisOutput {
    private TextView statusView;
    private EditText modelEdit;
    private Spinner modelSpinner;
    private Spinner providerSpinner;
    private EditText baseUrlEdit;
    private TextView logView;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildInterface();
        refreshStatus();
    }

    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private void buildInterface() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);
        root.setBackgroundColor(Color.rgb(3, 10, 16));
        scrollView.addView(root, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("JARVIS AI SETUP");
        title.setTextColor(Color.rgb(80, 220, 255));
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(title, fullWidthParams());

        TextView explain = new TextView(this);
        explain.setText("Paste or import your own API key on this phone. Choose OpenAI, BazaarLink, or a custom OpenAI-compatible endpoint. Keys and provider settings are stored locally in Jarvis app data and are not placed in GitHub, the APK source, or the build workflow. They normally survive app restarts, phone reboots and normal APK updates. They are removed if you uninstall Jarvis, clear app data, change package name, or press Clear Keys.");
        explain.setTextColor(Color.rgb(185, 235, 245));
        explain.setTextSize(14);
        explain.setPadding(0, 18, 0, 18);
        root.addView(explain, fullWidthParams());

        statusView = new TextView(this);
        statusView.setTextColor(Color.WHITE);
        statusView.setTextSize(14);
        statusView.setPadding(14, 14, 14, 14);
        statusView.setBackgroundColor(Color.rgb(8, 28, 40));
        root.addView(statusView, fullWidthParams());

        TextView providerLabel = new TextView(this);
        providerLabel.setText("AI Provider");
        providerLabel.setTextColor(Color.rgb(185, 235, 245));
        providerLabel.setTextSize(14);
        root.addView(providerLabel, fullWidthParams());

        providerSpinner = new Spinner(this);
        final String[] providers = JarvisOnlineBrain.getProviderOptions();
        ArrayAdapter<String> providerAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, providers);
        providerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        providerSpinner.setAdapter(providerAdapter);
        providerSpinner.setSelection(JarvisOnlineBrain.getProviderIndex(this));
        providerSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < providers.length && baseUrlEdit != null) {
                    if (JarvisOnlineBrain.PROVIDER_OPENAI.equals(providers[position])) {
                        baseUrlEdit.setText("https://api.openai.com/v1");
                    } else if (JarvisOnlineBrain.PROVIDER_BAZAARLINK.equals(providers[position])) {
                        baseUrlEdit.setText("https://bazaarlink.ai/api/v1");
                    }
                }
            }

            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        root.addView(providerSpinner, fullWidthParams());

        baseUrlEdit = new EditText(this);
        baseUrlEdit.setSingleLine(true);
        baseUrlEdit.setTextColor(Color.WHITE);
        baseUrlEdit.setHintTextColor(Color.rgb(110, 170, 190));
        baseUrlEdit.setHint("Base URL, e.g. https://bazaarlink.ai/api/v1");
        baseUrlEdit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        baseUrlEdit.setText(JarvisOnlineBrain.getBaseUrl(this));
        baseUrlEdit.setPadding(12, 12, 12, 12);
        root.addView(baseUrlEdit, fullWidthParams());

        root.addView(makeButton("Save Provider / Endpoint", new View.OnClickListener() {
            public void onClick(View v) {
                saveProviderEndpoint();
            }
        }), fullWidthParams());

        TextView modelLabel = new TextView(this);
        modelLabel.setText("AI Model");
        modelLabel.setTextColor(Color.rgb(185, 235, 245));
        modelLabel.setTextSize(14);
        root.addView(modelLabel, fullWidthParams());

        modelSpinner = new Spinner(this);
        final String[] models = JarvisOnlineBrain.getAvailableModels();
        ArrayAdapter<String> modelAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, models);
        modelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        modelSpinner.setAdapter(modelAdapter);
        modelSpinner.setSelection(JarvisOnlineBrain.getModelIndex(this));
        modelSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < models.length && modelEdit != null) {
                    modelEdit.setText(models[position]);
                }
            }

            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        root.addView(modelSpinner, fullWidthParams());

        modelEdit = new EditText(this);
        modelEdit.setSingleLine(true);
        modelEdit.setTextColor(Color.WHITE);
        modelEdit.setHintTextColor(Color.rgb(110, 170, 190));
        modelEdit.setHint("AI model, e.g. gpt-4o-mini");
        modelEdit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL);
        modelEdit.setText(JarvisOnlineBrain.getModel(this));
        modelEdit.setPadding(12, 12, 12, 12);
        root.addView(modelEdit, fullWidthParams());

        root.addView(makeButton("Save Selected Model", new View.OnClickListener() {
            public void onClick(View v) {
                String model = modelEdit.getText().toString().trim();
                if (modelSpinner != null && modelSpinner.getSelectedItem() != null) {
                    model = modelSpinner.getSelectedItem().toString().trim();
                    modelEdit.setText(model);
                }
                if (model.length() == 0) {
                    toast("Choose a model first.");
                    return;
                }
                JarvisOnlineBrain.setModel(JarvisAISetupActivity.this, model);
                appendLog("Model saved: " + model);
                refreshStatus();
            }
        }), fullWidthParams());

        root.addView(makeButton("Import Keys From Clipboard", new View.OnClickListener() {
            public void onClick(View v) {
                importClipboard(false);
            }
        }), fullWidthParams());

        root.addView(makeButton("Append Keys From Clipboard", new View.OnClickListener() {
            public void onClick(View v) {
                importClipboard(true);
            }
        }), fullWidthParams());

        root.addView(makeButton("Test Active Key", new View.OnClickListener() {
            public void onClick(View v) {
                appendLog("Testing active key...");
                JarvisOnlineBrain.testActiveApiKey(JarvisAISetupActivity.this, JarvisAISetupActivity.this);
            }
        }), fullWidthParams());

        root.addView(makeButton("Use First Working Key", new View.OnClickListener() {
            public void onClick(View v) {
                appendLog("Testing stored keys and selecting the first working key...");
                JarvisOnlineBrain.selectFirstWorkingApiKey(JarvisAISetupActivity.this, JarvisAISetupActivity.this);
            }
        }), fullWidthParams());

        root.addView(makeButton("Clear AI Keys", new View.OnClickListener() {
            public void onClick(View v) {
                JarvisOnlineBrain.clearApiKeys(JarvisAISetupActivity.this);
                appendLog("All locally stored AI keys cleared.");
                refreshStatus();
            }
        }), fullWidthParams());

        root.addView(makeButton("Close Setup", new View.OnClickListener() {
            public void onClick(View v) {
                finish();
            }
        }), fullWidthParams());

        logView = new TextView(this);
        logView.setTextColor(Color.rgb(165, 245, 255));
        logView.setTextSize(13);
        logView.setPadding(14, 18, 14, 18);
        logView.setBackgroundColor(Color.rgb(4, 18, 28));
        logView.setText("Setup log ready.");
        root.addView(logView, fullWidthParams());

        setContentView(scrollView);
    }

    private Button makeButton(String text, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setBackgroundColor(Color.rgb(12, 70, 95));
        button.setOnClickListener(listener);
        return button;
    }

    private LinearLayout.LayoutParams fullWidthParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 8, 0, 8);
        return params;
    }

    private void saveProviderEndpoint() {
        String provider = JarvisOnlineBrain.PROVIDER_OPENAI;
        if (providerSpinner != null && providerSpinner.getSelectedItem() != null) {
            provider = providerSpinner.getSelectedItem().toString();
        }
        String baseUrl = baseUrlEdit == null ? "" : baseUrlEdit.getText().toString().trim();
        JarvisOnlineBrain.setProvider(this, provider);
        if (baseUrl.length() > 0) {
            JarvisOnlineBrain.setBaseUrl(this, baseUrl);
        }
        appendLog("Provider saved: " + JarvisOnlineBrain.getProvider(this) + " at " + JarvisOnlineBrain.getBaseUrl(this));
        refreshStatus();
    }

    private void importClipboard(boolean append) {
        String text = getClipboardText();
        if (text.length() == 0) {
            toast("Clipboard is empty.");
            return;
        }
        int count = JarvisOnlineBrain.setApiKeysFromText(this, text, append);
        if (count == 0) {
            appendLog("No usable OpenAI-style keys found in the clipboard.");
            toast("No usable keys found.");
        } else {
            appendLog((append ? "Appended/imported " : "Imported ") + count + " local key" + (count == 1 ? "" : "s") + ".");
            toast("Keys stored locally.");
        }
        refreshStatus();
    }

    private String getClipboardText() {
        try {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null || !clipboard.hasPrimaryClip()) {
                return "";
            }
            ClipData data = clipboard.getPrimaryClip();
            if (data == null || data.getItemCount() == 0) {
                return "";
            }
            CharSequence text = data.getItemAt(0).coerceToText(this);
            if (text == null) {
                return "";
            }
            return text.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private void refreshStatus() {
        if (statusView != null) {
            statusView.setText(JarvisOnlineBrain.getApiKeyPersistenceStatus(this) + "\n\n" + JarvisOnlineBrain.getStoredKeySummary(this));
        }
        if (modelEdit != null && modelEdit.getText().toString().trim().length() == 0) {
            modelEdit.setText(JarvisOnlineBrain.getModel(this));
        }
        if (modelSpinner != null) {
            modelSpinner.setSelection(JarvisOnlineBrain.getModelIndex(this));
        }
        if (providerSpinner != null) {
            providerSpinner.setSelection(JarvisOnlineBrain.getProviderIndex(this));
        }
        if (baseUrlEdit != null && baseUrlEdit.getText().toString().trim().length() == 0) {
            baseUrlEdit.setText(JarvisOnlineBrain.getBaseUrl(this));
        }
    }

    private void appendLog(String text) {
        if (logView != null) {
            String old = logView.getText().toString();
            logView.setText(old + "\n\n" + text);
        }
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    public void onConsole(String line) {
        appendLog(line);
    }

    public void onClearConsole() {
        if (logView != null) {
            logView.setText("");
        }
    }

    public void onClearCodeSnippet() {
        JarvisCodeTools.clearLastCodeSnippet(this);
        appendLog("Code snippet cleared.");
    }

    public void onSavePendingProjectRequested() {
        appendLog("Open the main Jarvis screen to choose where to save a generated project ZIP.");
    }

    public void onMuteChanged(boolean muted) {
    }

    public void onStopListeningRequested() {
    }

    public void onBackgroundStateChanged(boolean enabled) {
    }

    public void onAsyncResponse(String text) {
        appendLog(text);
        refreshStatus();
    }
}
