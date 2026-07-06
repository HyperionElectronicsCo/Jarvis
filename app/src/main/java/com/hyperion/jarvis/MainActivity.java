package com.hyperion.jarvis;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

public class MainActivity extends Activity implements View.OnClickListener, TextToSpeech.OnInitListener, JarvisOutput {
    private static final int REQUEST_RECORD_AUDIO = 4001;
    private static final int REQUEST_BACKGROUND_AUDIO = 4002;
    private static final int REQUEST_OVERLAY_PERMISSION = 4003;
    private static final int REQUEST_CREATE_PROJECT_ZIP = 4004;

    private JarvisHudView hudView;
    private TextView titleView;
    private TextView statusView;
    private TextView transcriptView;
    private TextView consoleView;
    private EditText typedCommand;
    private Button listenButton;
    private Button sendButton;
    private Button stopButton;
    private Button muteButton;
    private Button backgroundButton;
    private LinearLayout codePanel;
    private TextView codeSnippetView;
    private Button copyCodeButton;
    private Button clearCodeButton;
    private String lastCodeSnippet;
    private String pendingProjectPackageText;
    private boolean projectSaveDialogShowing;
    private boolean projectSaveOfferDismissed;

    private SpeechRecognizer speechRecognizer;
    private Intent recognizerIntent;
    private TextToSpeech textToSpeech;
    private boolean ttsReady;
    private boolean listening;
    private boolean muted;
    private boolean restartListeningAfterSpeech;
    private boolean overlayNoticeShownThisSession;
    private boolean accessibilityNoticeShownThisSession;
    private boolean pendingBackgroundEnableAfterOverlay;
    private Handler handler;
    private Runnable clockRunnable;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handler = new Handler();
        ttsReady = false;
        listening = false;
        muted = JarvisCommandCenter.isMuted(this);
        restartListeningAfterSpeech = false;
        overlayNoticeShownThisSession = false;
        accessibilityNoticeShownThisSession = false;
        pendingBackgroundEnableAfterOverlay = false;
        lastCodeSnippet = "";
        pendingProjectPackageText = "";
        projectSaveDialogShowing = false;
        projectSaveOfferDismissed = false;

        Window window = getWindow();
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (Build.VERSION.SDK_INT >= 21) {
            window.setStatusBarColor(Color.BLACK);
            window.setNavigationBarColor(Color.BLACK);
        }

        buildUserInterface();
        setupSpeechRecognizer();
        textToSpeech = new TextToSpeech(this, this);
        appendConsole("BOOT: Native Android Jarvis shell loaded.");
        appendConsole("BOOT: Say OKAY JARVIS, tap LISTEN, or type a command.");
        appendConsole("BOOT: Commands now include direct AI fallback after importing a key, project ZIP packaging with save-location picker, full code snippets with Copy/Clear Code, search, weather, reminders, memory, camera vision, apps, media, volume, navigation control and background service.");
        startClock();
        updateMuteButton();
        updateBackgroundButton();
        handler.postDelayed(new Runnable() {
            public void run() {
                maybeShowStartupPermissionNotices(false);
            }
        }, 700);
    }

    protected void onResume() {
        super.onResume();
        muted = JarvisCommandCenter.isMuted(this);
        enforceOverlayRequirementForBackground();
        updateMuteButton();
        updateBackgroundButton();
        restoreLastCodeSnippetPanel();
        maybeOfferPendingProjectPackage(false);
        if (pendingBackgroundEnableAfterOverlay && JarvisCommandCenter.hasOverlayPermission(this)) {
            pendingBackgroundEnableAfterOverlay = false;
            startBackgroundFromActivity();
        }
        if (JarvisCommandCenter.hasOverlayPermission(this) && !JarvisCommandCenter.isAccessibilityServiceEnabled(this)) {
            handler.postDelayed(new Runnable() {
                public void run() {
                    maybeShowAccessibilityPermissionNotice(false);
                }
            }, 450);
        }
    }

    private void buildUserInterface() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(2, 7, 11));

        hudView = new JarvisHudView(this);
        root.addView(hudView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setPadding(dp(14), dp(16), dp(14), dp(14));
        overlay.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(overlay, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        titleView = new TextView(this);
        titleView.setText("J.A.R.V.I.S");
        titleView.setTextColor(Color.rgb(0, 216, 255));
        titleView.setTextSize(28);
        titleView.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        titleView.setGravity(Gravity.CENTER);
        titleView.setShadowLayer(18, 0, 0, Color.rgb(0, 216, 255));
        overlay.addView(titleView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(42)));

        statusView = new TextView(this);
        statusView.setText("SYSTEM READY");
        statusView.setTextColor(Color.rgb(255, 158, 43));
        statusView.setTextSize(12);
        statusView.setGravity(Gravity.CENTER);
        statusView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        overlay.addView(statusView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(28)));

        SpaceView visualSpacer = new SpaceView(this);
        overlay.addView(visualSpacer, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f));

        transcriptView = new TextView(this);
        transcriptView.setText("Say: open AI setup, import keys from clipboard, choose a model, then ask naturally: okay Jarvis give me the code for a Termux script, weather in London, remind me in 10 minutes, camera vision, or open YouTube.");
        transcriptView.setTextColor(Color.argb(230, 210, 250, 255));
        transcriptView.setTextSize(14);
        transcriptView.setGravity(Gravity.CENTER);
        transcriptView.setPadding(dp(12), dp(8), dp(12), dp(8));
        transcriptView.setBackground(makePanelBackground(72, Color.rgb(0, 216, 255), Color.argb(54, 0, 216, 255)));
        overlay.addView(transcriptView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(68)));

        consoleView = new TextView(this);
        consoleView.setTextColor(Color.argb(225, 0, 216, 255));
        consoleView.setTextSize(11);
        consoleView.setTypeface(Typeface.MONOSPACE);
        consoleView.setPadding(dp(10), dp(8), dp(10), dp(8));
        consoleView.setMovementMethod(new ScrollingMovementMethod());
        consoleView.setBackground(makePanelBackground(62, Color.rgb(0, 216, 255), Color.argb(48, 0, 25, 35)));

        ScrollView consoleScroll = new ScrollView(this);
        consoleScroll.setFillViewport(true);
        consoleScroll.addView(consoleView, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams consoleParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(84));
        consoleParams.topMargin = dp(8);
        overlay.addView(consoleScroll, consoleParams);

        codePanel = new LinearLayout(this);
        codePanel.setOrientation(LinearLayout.VERTICAL);
        codePanel.setPadding(dp(8), dp(6), dp(8), dp(6));
        codePanel.setBackground(makePanelBackground(76, Color.rgb(255, 158, 43), Color.argb(78, 24, 18, 6)));
        codePanel.setVisibility(View.GONE);

        TextView codeTitle = new TextView(this);
        codeTitle.setText("CODE SNIPPET");
        codeTitle.setTextColor(Color.rgb(255, 210, 130));
        codeTitle.setTextSize(11);
        codeTitle.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        codePanel.addView(codeTitle, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(18)));

        codeSnippetView = new TextView(this);
        codeSnippetView.setTextColor(Color.WHITE);
        codeSnippetView.setTextSize(10);
        codeSnippetView.setTypeface(Typeface.MONOSPACE);
        codeSnippetView.setPadding(dp(6), dp(4), dp(6), dp(4));
        codeSnippetView.setTextIsSelectable(true);
        codeSnippetView.setBackgroundColor(Color.argb(110, 0, 0, 0));
        ScrollView codeScroll = new ScrollView(this);
        codeScroll.addView(codeSnippetView, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        codePanel.addView(codeScroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(138)));

        LinearLayout codeButtonRow = new LinearLayout(this);
        codeButtonRow.setOrientation(LinearLayout.HORIZONTAL);
        codeButtonRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams codeButtonRowParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(34));
        codeButtonRowParams.topMargin = dp(5);
        codePanel.addView(codeButtonRow, codeButtonRowParams);

        copyCodeButton = makeButton("COPY CODE");
        copyCodeButton.setOnClickListener(this);
        codeButtonRow.addView(copyCodeButton, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f));

        clearCodeButton = makeButton("CLEAR CODE");
        clearCodeButton.setOnClickListener(this);
        LinearLayout.LayoutParams clearCodeParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f);
        clearCodeParams.leftMargin = dp(6);
        codeButtonRow.addView(clearCodeButton, clearCodeParams);

        LinearLayout.LayoutParams codePanelParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        codePanelParams.topMargin = dp(8);
        overlay.addView(codePanel, codePanelParams);

        LinearLayout typedRow = new LinearLayout(this);
        typedRow.setOrientation(LinearLayout.HORIZONTAL);
        typedRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams typedRowParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(46));
        typedRowParams.topMargin = dp(8);
        overlay.addView(typedRow, typedRowParams);

        typedCommand = new EditText(this);
        typedCommand.setSingleLine(true);
        typedCommand.setHint("Type command...");
        typedCommand.setHintTextColor(Color.argb(145, 210, 250, 255));
        typedCommand.setTextColor(Color.WHITE);
        typedCommand.setTextSize(13);
        typedCommand.setTypeface(Typeface.MONOSPACE);
        typedCommand.setPadding(dp(10), 0, dp(10), 0);
        typedCommand.setBackground(makePanelBackground(76, Color.rgb(0, 216, 255), Color.argb(70, 0, 20, 28)));
        typedRow.addView(typedCommand, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f));

        sendButton = makeButton("SEND");
        sendButton.setOnClickListener(this);
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(dp(76), LinearLayout.LayoutParams.MATCH_PARENT);
        sendParams.leftMargin = dp(8);
        typedRow.addView(sendButton, sendParams);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams controlsParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        controlsParams.topMargin = dp(8);
        overlay.addView(controls, controlsParams);

        listenButton = makeButton("LISTEN");
        listenButton.setOnClickListener(this);
        controls.addView(listenButton, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f));

        stopButton = makeButton("STOP");
        stopButton.setOnClickListener(this);
        LinearLayout.LayoutParams stopParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f);
        stopParams.leftMargin = dp(6);
        controls.addView(stopButton, stopParams);

        muteButton = makeButton("MUTE");
        muteButton.setOnClickListener(this);
        LinearLayout.LayoutParams muteParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f);
        muteParams.leftMargin = dp(6);
        controls.addView(muteButton, muteParams);

        backgroundButton = makeButton("BG OFF");
        backgroundButton.setOnClickListener(this);
        LinearLayout.LayoutParams backgroundParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f);
        backgroundParams.leftMargin = dp(6);
        controls.addView(backgroundButton, backgroundParams);

        setContentView(root);
    }

    private Button makeButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(11);
        button.setTextColor(Color.rgb(210, 250, 255));
        button.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(2), 0, dp(2), 0);
        button.setBackground(makeButtonBackground(false));
        return button;
    }

    private GradientDrawable makeButtonBackground(boolean pressed) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(dp(5));
        if (pressed) {
            drawable.setColor(Color.argb(88, 255, 158, 43));
            drawable.setStroke(dp(1), Color.rgb(255, 158, 43));
        } else {
            drawable.setColor(Color.argb(76, 0, 34, 48));
            drawable.setStroke(dp(1), Color.rgb(0, 216, 255));
        }
        return drawable;
    }

    private GradientDrawable makePanelBackground(int alpha, int strokeColor, int fillColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(dp(6));
        drawable.setColor(fillColor);
        drawable.setStroke(dp(1), Color.argb(alpha, Color.red(strokeColor), Color.green(strokeColor), Color.blue(strokeColor)));
        return drawable;
    }

    private void setupSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            appendConsole("VOICE: Speech recognition is not available on this device.");
            return;
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 2500L);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1600L);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1300L);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            public void onReadyForSpeech(Bundle params) {
                listening = true;
                hudView.setMode(JarvisHudView.MODE_LISTENING);
                setStatus("LISTENING...");
                transcriptView.setText("Listening. You can say okay Jarvis followed by a command...");
            }

            public void onBeginningOfSpeech() {
                setStatus("VOICE DETECTED");
            }

            public void onRmsChanged(float rmsdB) {
                float level = Math.abs(rmsdB) / 12.0f;
                hudView.setAudioLevel(level);
            }

            public void onBufferReceived(byte[] buffer) {
            }

            public void onEndOfSpeech() {
                listening = false;
                setStatus("PROCESSING...");
                hudView.setAudioLevel(0.0f);
            }

            public void onError(int error) {
                listening = false;
                hudView.setAudioLevel(0.0f);
                hudView.setMode(JarvisHudView.MODE_IDLE);
                setStatus("SYSTEM READY");
                String message = recognitionErrorToText(error);
                transcriptView.setText(message);
                appendConsole("VOICE: " + message);
                if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    speak("I could not access the voice system. You can type the command instead.");
                }
            }

            public void onResults(Bundle results) {
                listening = false;
                hudView.setAudioLevel(0.0f);
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && matches.size() > 0) {
                    handleUserCommand(chooseBestActivityRecognition(matches));
                } else {
                    hudView.setMode(JarvisHudView.MODE_IDLE);
                    setStatus("SYSTEM READY");
                    speak("I did not catch that.");
                }
            }

            public void onPartialResults(Bundle partialResults) {
                ArrayList<String> partial = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (partial != null && partial.size() > 0) {
                    transcriptView.setText(partial.get(0));
                }
            }

            public void onEvent(int eventType, Bundle params) {
            }
        });
    }

    private String chooseBestActivityRecognition(ArrayList<String> matches) {
        if (matches == null || matches.size() == 0) {
            return "";
        }
        String best = matches.get(0);
        int bestLength = best == null ? 0 : best.length();
        for (int i = 0; i < matches.size(); i++) {
            String item = matches.get(i);
            if (item == null) {
                continue;
            }
            String trimmed = item.trim();
            int length = trimmed.length();
            if (JarvisCommandCenter.containsWakePhrase(trimmed) && JarvisCommandCenter.stripWakePhrase(trimmed).length() > bestLength) {
                best = trimmed;
                bestLength = JarvisCommandCenter.stripWakePhrase(trimmed).length();
            } else if (length > bestLength + 2) {
                best = trimmed;
                bestLength = length;
            }
        }
        return best == null ? "" : best;
    }

    public void onClick(View view) {
        if (view == listenButton) {
            startListening();
        } else if (view == stopButton) {
            stopListening();
            speak("Voice input stopped.");
        } else if (view == muteButton) {
            muted = !muted;
            JarvisCommandCenter.setMuted(this, muted);
            updateMuteButton();
            if (muted) {
                appendConsole("AUDIO: Speech output muted.");
                if (textToSpeech != null) {
                    textToSpeech.stop();
                }
            } else {
                speak("Speech output restored.");
            }
        } else if (view == copyCodeButton) {
            copyLastCodeSnippet();
        } else if (view == clearCodeButton) {
            clearCodeSnippetPanel(true);
        } else if (view == backgroundButton) {
            if (JarvisCommandCenter.isBackgroundEnabled(this) && JarvisCommandCenter.isBackgroundPaused(this)) {
                startBackgroundFromActivity();
            } else if (JarvisCommandCenter.isBackgroundEnabled(this)) {
                JarvisCommandCenter.setBackgroundEnabled(this, false);
                JarvisCommandCenter.setBackgroundPaused(this, false);
                JarvisCommandCenter.stopBackgroundService(this);
                updateBackgroundButton();
                speak("Background listening service disabled.");
            } else {
                if (!JarvisCommandCenter.hasOverlayPermission(this)) {
                    requestOverlayForBackground(true);
                    return;
                }
                startBackgroundFromActivity();
            }
        } else if (view == sendButton) {
            String command = typedCommand.getText().toString();
            typedCommand.setText("");
            hideKeyboard();
            handleUserCommand(command);
        }
    }

    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            View current = getCurrentFocus();
            if (current != null && current != typedCommand) {
                hideKeyboard();
            }
        }
        return super.dispatchTouchEvent(event);
    }

    private void startBackgroundFromActivity() {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] { Manifest.permission.RECORD_AUDIO }, REQUEST_BACKGROUND_AUDIO);
            return;
        }
        if (!JarvisCommandCenter.hasOverlayPermission(this)) {
            requestOverlayForBackground(true);
            return;
        }
        JarvisCommandCenter.setBackgroundEnabled(this, true);
        JarvisCommandCenter.setBackgroundPaused(this, false);
        JarvisCommandCenter.startBackgroundService(this);
        updateBackgroundButton();
        if (!JarvisCommandCenter.isAccessibilityServiceEnabled(this)) {
            speak("Background listening service enabled. Say okay Jarvis when you need me. Navigation commands like Back, Recent Apps and close app need Jarvis Navigation Control enabled in Accessibility settings.");
        } else {
            speak("Background listening service enabled. Say okay Jarvis when you need me.");
        }
    }

    private void requestOverlayForBackground(boolean enableAfterGrant) {
        pendingBackgroundEnableAfterOverlay = enableAfterGrant;
        JarvisCommandCenter.setBackgroundEnabled(this, false);
        JarvisCommandCenter.setBackgroundPaused(this, false);
        JarvisCommandCenter.stopBackgroundService(this);
        updateBackgroundButton();
        maybeShowOverlayPermissionNotice(true, enableAfterGrant);
        speak("Background mode needs display over other apps permission before it can start. Navigation control also needs Accessibility enabled for Back, Recent Apps and close app commands.");
    }

    private void enforceOverlayRequirementForBackground() {
        if (!JarvisCommandCenter.hasOverlayPermission(this) && JarvisCommandCenter.isBackgroundEnabled(this)) {
            JarvisCommandCenter.setBackgroundEnabled(this, false);
            JarvisCommandCenter.setBackgroundPaused(this, false);
            JarvisCommandCenter.stopBackgroundService(this);
            appendConsole("PERMISSION: Background service locked until Display over other apps is enabled.");
        }
    }

    private void maybeShowStartupPermissionNotices(boolean force) {
        if (!JarvisCommandCenter.hasOverlayPermission(this)) {
            maybeShowOverlayPermissionNotice(force, false);
            return;
        }
        if (!JarvisCommandCenter.isAccessibilityServiceEnabled(this)) {
            maybeShowAccessibilityPermissionNotice(force);
        }
    }

    private void maybeShowAccessibilityPermissionNotice(boolean force) {
        if (JarvisCommandCenter.isAccessibilityServiceEnabled(this)) {
            return;
        }
        if (!force && accessibilityNoticeShownThisSession) {
            return;
        }
        accessibilityNoticeShownThisSession = true;
        try {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Jarvis navigation permission");
            builder.setMessage("To obey commands such as go back, homescreen, show open apps and close app, Android requires the Jarvis Navigation Control Accessibility service. Without it, Jarvis can still open apps, search, use maps and control volume, but it cannot press Back, Home or Recent Apps for you. Enable only the service named Jarvis Navigation Control.");
            builder.setPositiveButton("Open Accessibility", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    JarvisCommandCenter.openAccessibilitySettings(MainActivity.this, MainActivity.this);
                }
            });
            builder.setNegativeButton("Not now", null);
            builder.show();
        } catch (Exception error) {
            appendConsole("PERMISSION: Unable to show accessibility permission dialog: " + error.getMessage());
            JarvisCommandCenter.openAccessibilitySettings(this, this);
        }
    }

    private void maybeShowOverlayPermissionNotice(boolean force, boolean enableAfterGrant) {
        if (JarvisCommandCenter.hasOverlayPermission(this)) {
            return;
        }
        if (!force && overlayNoticeShownThisSession) {
            return;
        }
        overlayNoticeShownThisSession = true;
        if (enableAfterGrant) {
            pendingBackgroundEnableAfterOverlay = true;
        }
        try {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Jarvis permissions setup");
            builder.setMessage("Jarvis needs Display over other apps before background app-launch commands can work reliably. For Back, Home, Recent Apps and close-app commands, Android also needs the Jarvis Navigation Control Accessibility service. Background mode stays locked until Display over other apps is enabled.");
            builder.setPositiveButton("Open Display Permission", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    JarvisCommandCenter.openOverlayPermission(MainActivity.this, MainActivity.this);
                }
            });
            builder.setNeutralButton("Open Accessibility", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    JarvisCommandCenter.openAccessibilitySettings(MainActivity.this, MainActivity.this);
                }
            });
            builder.setNegativeButton("Not now", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    pendingBackgroundEnableAfterOverlay = false;
                    JarvisCommandCenter.setBackgroundEnabled(MainActivity.this, false);
                    JarvisCommandCenter.setBackgroundPaused(MainActivity.this, false);
                    updateBackgroundButton();
                }
            });
            builder.show();
        } catch (Exception error) {
            appendConsole("PERMISSION: Unable to show overlay permission dialog: " + error.getMessage());
            JarvisCommandCenter.openOverlayPermission(this, this);
        }
    }

    private void startListening() {
        if (speechRecognizer == null) {
            speak("Speech recognition is not available on this device. Please type your command.");
            return;
        }
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] { Manifest.permission.RECORD_AUDIO }, REQUEST_RECORD_AUDIO);
            return;
        }
        try {
            stopListeningSilently();
            listening = true;
            vibrateShort();
            speechRecognizer.startListening(recognizerIntent);
        } catch (Exception error) {
            listening = false;
            hudView.setMode(JarvisHudView.MODE_IDLE);
            appendConsole("VOICE ERROR: " + error.getMessage());
            speak("I could not start listening. You can type the command instead.");
        }
    }

    private void stopListening() {
        stopListeningSilently();
        listening = false;
        hudView.setAudioLevel(0.0f);
        hudView.setMode(JarvisHudView.MODE_IDLE);
        setStatus("SYSTEM READY");
    }

    private void stopListeningSilently() {
        try {
            if (speechRecognizer != null) {
                speechRecognizer.cancel();
            }
        } catch (Exception ignored) {
        }
    }

    private void handleUserCommand(String originalCommand) {
        if (originalCommand == null) {
            return;
        }
        String command = originalCommand.trim();
        if (command.length() == 0) {
            speak("Please give me a command.");
            return;
        }
        transcriptView.setText(command);
        appendConsole("USER: " + maskSensitiveConsoleCommand(command));

        if (JarvisCommandCenter.containsWakePhrase(command)) {
            String afterWake = JarvisCommandCenter.stripWakePhrase(command);
            if (afterWake.length() == 0) {
                restartListeningAfterSpeech = true;
                speak("At your service.");
                return;
            }
            command = afterWake;
            appendConsole("WAKE: Command after hot phrase: " + command);
        }

        if (looksLikeBackgroundStart(command)) {
            if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[] { Manifest.permission.RECORD_AUDIO }, REQUEST_BACKGROUND_AUDIO);
                return;
            }
            if (!JarvisCommandCenter.hasOverlayPermission(this)) {
                requestOverlayForBackground(true);
                return;
            }
        }

        String response = JarvisCommandCenter.processCommand(this, command, this);
        updateMuteButton();
        updateBackgroundButton();
        if (response != null && response.length() > 0) {
            speak(response);
        }
    }

    private boolean looksLikeBackgroundStart(String command) {
        String lower = command.toLowerCase(Locale.UK);
        return lower.indexOf("background") >= 0 && (lower.indexOf("enable") >= 0 || lower.indexOf("start") >= 0 || lower.indexOf("on") >= 0 || lower.indexOf("listen") >= 0 || lower.indexOf("run") >= 0);
    }

    private String maskSensitiveConsoleCommand(String command) {
        if (command == null) {
            return "";
        }
        String lower = command.toLowerCase(Locale.UK).trim();
        lower = lower.replace("a i", "ai").replace("a p i", "api").replace("clip board", "clipboard");
        if (lower.startsWith("set ai key ") || lower.startsWith("set api key ") || lower.startsWith("set openai key ") || lower.startsWith("set open ai key ") || lower.startsWith("set chatgpt key ")
                || lower.startsWith("set ai keys ") || lower.startsWith("set api keys ") || lower.startsWith("set openai keys ") || lower.startsWith("set open ai keys ") || lower.startsWith("set chatgpt keys ")
                || lower.startsWith("add ai key ") || lower.startsWith("add api key ") || lower.startsWith("add openai key ") || lower.startsWith("add open ai key ") || lower.startsWith("add chatgpt key ")
                || lower.startsWith("import ai keys") || lower.startsWith("import keys") || lower.startsWith("append ai keys") || lower.startsWith("append keys")) {
            return "AI key command ********";
        }
        return command;
    }

    private String recognitionErrorToText(int error) {
        if (error == SpeechRecognizer.ERROR_AUDIO) {
            return "Audio recording error.";
        }
        if (error == SpeechRecognizer.ERROR_CLIENT) {
            return "Voice client error.";
        }
        if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
            return "Microphone permission is required.";
        }
        if (error == SpeechRecognizer.ERROR_NETWORK) {
            return "Network error during recognition.";
        }
        if (error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT) {
            return "Network timeout during recognition.";
        }
        if (error == SpeechRecognizer.ERROR_NO_MATCH) {
            return "No speech match found.";
        }
        if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
            return "Voice recognizer is busy.";
        }
        if (error == SpeechRecognizer.ERROR_SERVER) {
            return "Voice recognition server error.";
        }
        if (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
            return "No speech detected.";
        }
        return "Unknown voice error.";
    }

    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            ttsReady = true;
            JarvisVoiceTools.configureJarvisVoice(textToSpeech);
            textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                public void onStart(String utteranceId) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            hudView.setMode(JarvisHudView.MODE_SPEAKING);
                            setStatus("SPEAKING...");
                        }
                    });
                }

                public void onDone(String utteranceId) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            hudView.setMode(JarvisHudView.MODE_IDLE);
                            hudView.setAudioLevel(0.0f);
                            setStatus("SYSTEM READY");
                            if (restartListeningAfterSpeech) {
                                restartListeningAfterSpeech = false;
                                handler.postDelayed(new Runnable() {
                                    public void run() {
                                        startListening();
                                    }
                                }, 350);
                            }
                        }
                    });
                }

                public void onError(String utteranceId) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            hudView.setMode(JarvisHudView.MODE_IDLE);
                            hudView.setAudioLevel(0.0f);
                            setStatus("SYSTEM READY");
                            restartListeningAfterSpeech = false;
                        }
                    });
                }
            });
            speak("Jarvis mobile interface online.");
        } else {
            ttsReady = false;
            appendConsole("AUDIO: Text to speech failed to initialise.");
        }
    }

    private void speak(String text) {
        if (text == null || text.length() == 0) {
            return;
        }
        appendConsole("JARVIS: " + text);
        transcriptView.setText(text);
        if (muted || !ttsReady || textToSpeech == null) {
            hudView.setMode(JarvisHudView.MODE_IDLE);
            setStatus("SYSTEM READY");
            if (restartListeningAfterSpeech) {
                restartListeningAfterSpeech = false;
                handler.postDelayed(new Runnable() {
                    public void run() {
                        startListening();
                    }
                }, 350);
            }
            return;
        }
        String utteranceId = "jarvis_" + System.currentTimeMillis();
        hudView.setMode(JarvisHudView.MODE_SPEAKING);
        setStatus("SPEAKING...");
        if (Build.VERSION.SDK_INT >= 21) {
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId);
        } else {
            HashMap<String, String> params = new HashMap<String, String>();
            params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, params);
        }
        if (restartListeningAfterSpeech) {
            handler.postDelayed(new Runnable() {
                public void run() {
                    if (restartListeningAfterSpeech) {
                        restartListeningAfterSpeech = false;
                        startListening();
                    }
                }
            }, 2200);
        }
    }

    private void appendConsole(String line) {
        if (consoleView == null || line == null) {
            return;
        }
        String current = consoleView.getText().toString();
        String time = new SimpleDateFormat("HH:mm:ss", Locale.UK).format(new Date());
        String next = current + "[" + time + "] " + line + "\n";
        if (next.length() > 8000) {
            next = next.substring(next.length() - 8000);
        }
        consoleView.setText(next);
        consoleView.post(new Runnable() {
            public void run() {
                int scrollAmount = consoleView.getLayout() == null ? 0 : consoleView.getLayout().getLineTop(consoleView.getLineCount()) - consoleView.getHeight();
                if (scrollAmount > 0) {
                    consoleView.scrollTo(0, scrollAmount);
                } else {
                    consoleView.scrollTo(0, 0);
                }
            }
        });
    }

    private void setStatus(String status) {
        if (statusView != null) {
            statusView.setText(status);
        }
    }

    private void startClock() {
        clockRunnable = new Runnable() {
            public void run() {
                SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss", Locale.UK);
                titleView.setText("J.A.R.V.I.S  " + format.format(new Date()));
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(clockRunnable);
    }

    private void hideKeyboard() {
        try {
            InputMethodManager manager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            View view = getCurrentFocus();
            if (manager != null && view != null) {
                manager.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
        } catch (Exception ignored) {
        }
    }

    private void vibrateShort() {
        try {
            Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null) {
                vibrator.vibrate(35);
            }
        } catch (Exception ignored) {
        }
    }

    private void updateMuteButton() {
        if (muteButton == null) {
            return;
        }
        if (muted) {
            muteButton.setText("UNMUTE");
        } else {
            muteButton.setText("MUTE");
        }
    }

    private void updateBackgroundButton() {
        if (backgroundButton == null) {
            return;
        }
        if (!JarvisCommandCenter.hasOverlayPermission(this)) {
            backgroundButton.setText("BG LOCK");
            backgroundButton.setTextColor(Color.rgb(170, 170, 170));
            backgroundButton.setBackground(makeLockedButtonBackground());
        } else if (JarvisCommandCenter.isBackgroundEnabled(this) && JarvisCommandCenter.isBackgroundPaused(this)) {
            backgroundButton.setText("BG PAUSE");
            backgroundButton.setTextColor(Color.rgb(255, 158, 43));
            backgroundButton.setBackground(makeButtonBackground(true));
        } else if (JarvisCommandCenter.isBackgroundEnabled(this)) {
            backgroundButton.setText("BG ON");
            backgroundButton.setTextColor(Color.rgb(210, 250, 255));
            backgroundButton.setBackground(makeButtonBackground(true));
        } else {
            backgroundButton.setText("BG OFF");
            backgroundButton.setTextColor(Color.rgb(210, 250, 255));
            backgroundButton.setBackground(makeButtonBackground(false));
        }
    }

    private GradientDrawable makeLockedButtonBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(dp(5));
        drawable.setColor(Color.argb(70, 48, 48, 48));
        drawable.setStroke(dp(1), Color.rgb(110, 110, 110));
        return drawable;
    }

    private void restoreLastCodeSnippetPanel() {
        String saved = JarvisCodeTools.getLastCodeSnippet(this);
        if (saved.length() == 0 || codePanel == null || codeSnippetView == null) {
            return;
        }
        if (lastCodeSnippet == null || lastCodeSnippet.length() == 0) {
            lastCodeSnippet = saved;
            codeSnippetView.setText(saved);
            codePanel.setVisibility(View.VISIBLE);
        }
    }

    private void showCodeSnippet(String code) {
        final String nextSnippet = code == null ? "" : code;
        lastCodeSnippet = nextSnippet;
        JarvisCodeTools.saveLastCodeSnippet(this, lastCodeSnippet);
        if (codeSnippetView != null) {
            codeSnippetView.setText("");
        }
        if (codePanel != null) {
            codePanel.setVisibility(View.GONE);
        }
        Runnable refresh = new Runnable() {
            public void run() {
                if (codeSnippetView != null) {
                    codeSnippetView.setText(nextSnippet);
                }
                if (codePanel != null) {
                    codePanel.setVisibility(View.VISIBLE);
                }
                appendConsole("AI: New code snippet ready. Press COPY CODE to copy it.");
            }
        };
        if (handler != null) {
            handler.postDelayed(refresh, 140);
        } else {
            refresh.run();
        }
    }

    private void hideCodeSnippet() {
        if (codePanel != null) {
            codePanel.setVisibility(View.GONE);
        }
    }

    private void clearCodeSnippetPanel(boolean speakResult) {
        lastCodeSnippet = "";
        JarvisCodeTools.clearLastCodeSnippet(this);
        if (codeSnippetView != null) {
            codeSnippetView.setText("");
        }
        hideCodeSnippet();
        appendConsole("AI: Code snippet cleared.");
        if (speakResult) {
            speak("Code snippet cleared.");
        }
    }

    private void copyLastCodeSnippet() {
        if (lastCodeSnippet == null || lastCodeSnippet.length() == 0) {
            speak("There is no code snippet to copy yet.");
            return;
        }
        try {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("Jarvis code snippet", lastCodeSnippet));
                speak("Code copied to clipboard.");
                return;
            }
        } catch (Exception error) {
            appendConsole("CLIPBOARD: " + error.getMessage());
        }
        speak("I could not copy the code snippet.");
    }

    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startListening();
            } else {
                speak("Microphone permission was denied. Type a command instead.");
            }
        } else if (requestCode == REQUEST_BACKGROUND_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startBackgroundFromActivity();
            } else {
                speak("Microphone permission is required for background listening.");
            }
        }
    }

    public void onConsole(String line) {
        appendConsole(line);
    }

    public void onClearConsole() {
        if (consoleView != null) {
            consoleView.setText("");
        }
        appendConsole("BOOT: Console cleared.");
    }

    public void onMuteChanged(boolean newMuted) {
        muted = newMuted;
        updateMuteButton();
        if (muted && textToSpeech != null) {
            textToSpeech.stop();
        }
    }

    public void onStopListeningRequested() {
        stopListening();
    }

    public void onBackgroundStateChanged(boolean enabled) {
        updateBackgroundButton();
    }

    public void onClearCodeSnippet() {
        runOnUiThread(new Runnable() {
            public void run() {
                clearCodeSnippetPanel(false);
            }
        });
    }

    public void onSavePendingProjectRequested() {
        runOnUiThread(new Runnable() {
            public void run() {
                if (pendingProjectPackageText == null || pendingProjectPackageText.length() == 0) {
                    pendingProjectPackageText = JarvisProjectPackager.getPendingProject(MainActivity.this);
                }
                if (pendingProjectPackageText == null || pendingProjectPackageText.length() == 0) {
                    speak("There is no generated project waiting to save.");
                    return;
                }
                projectSaveOfferDismissed = false;
                maybeOfferPendingProjectPackage(true);
            }
        });
    }

    private void handleProjectPackageAnswer(String text) {
        pendingProjectPackageText = text == null ? "" : text;
        projectSaveOfferDismissed = false;
        JarvisProjectPackager.rememberPendingProject(this, pendingProjectPackageText);
        clearCodeSnippetPanel(false);
        int fileCount = JarvisProjectPackager.countProjectFiles(pendingProjectPackageText);
        appendConsole("AI: Project package ready with " + fileCount + " file" + (fileCount == 1 ? "" : "s") + ". Waiting for save confirmation.");
        maybeOfferPendingProjectPackage(true);
    }

    private void maybeOfferPendingProjectPackage(boolean force) {
        if (projectSaveDialogShowing) {
            return;
        }
        if (pendingProjectPackageText == null || pendingProjectPackageText.length() == 0) {
            pendingProjectPackageText = JarvisProjectPackager.getPendingProject(this);
        }
        if (pendingProjectPackageText == null || pendingProjectPackageText.length() == 0) {
            return;
        }
        if (!JarvisProjectPackager.containsProjectPackage(pendingProjectPackageText)) {
            JarvisProjectPackager.clearPendingProject(this);
            pendingProjectPackageText = "";
            return;
        }
        if (projectSaveOfferDismissed && !force) {
            return;
        }
        final String suggestedName = JarvisProjectPackager.getSuggestedZipName(pendingProjectPackageText);
        final int fileCount = JarvisProjectPackager.countProjectFiles(pendingProjectPackageText);
        projectSaveDialogShowing = true;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Save generated project?");
        builder.setMessage("Jarvis prepared " + fileCount + " project file" + (fileCount == 1 ? "" : "s") + ". Do you want to choose where to save " + suggestedName + "?");
        builder.setPositiveButton("Save ZIP", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                projectSaveDialogShowing = false;
                projectSaveOfferDismissed = false;
                launchProjectSavePicker(suggestedName);
            }
        });
        builder.setNegativeButton("Not Now", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                projectSaveDialogShowing = false;
                projectSaveOfferDismissed = true;
                speak("Okay. I kept the generated project. Say save project zip when you want to choose where to save it.");
            }
        });
        builder.setNeutralButton("Discard", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                projectSaveDialogShowing = false;
                projectSaveOfferDismissed = false;
                pendingProjectPackageText = "";
                JarvisProjectPackager.clearPendingProject(MainActivity.this);
                speak("Generated project discarded.");
            }
        });
        try {
            builder.show();
        } catch (Exception error) {
            projectSaveDialogShowing = false;
            appendConsole("SAVE: " + error.getMessage());
            speak("Project package is ready. Say save project zip to choose where to save it.");
        }
    }

    private void launchProjectSavePicker(String suggestedName) {
        if (pendingProjectPackageText == null || pendingProjectPackageText.length() == 0) {
            pendingProjectPackageText = JarvisProjectPackager.getPendingProject(this);
        }
        if (pendingProjectPackageText == null || pendingProjectPackageText.length() == 0) {
            speak("There is no generated project waiting to save.");
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/zip");
            intent.putExtra(Intent.EXTRA_TITLE, suggestedName == null || suggestedName.length() == 0 ? JarvisProjectPackager.getSuggestedZipName(pendingProjectPackageText) : suggestedName);
            startActivityForResult(intent, REQUEST_CREATE_PROJECT_ZIP);
        } catch (Exception error) {
            appendConsole("SAVE: " + error.getMessage());
            speak("I could not open the Android file picker on this device.");
        }
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CREATE_PROJECT_ZIP) {
            if (resultCode != RESULT_OK || data == null || data.getData() == null) {
                speak("Project save cancelled.");
                return;
            }
            Uri uri = data.getData();
            String text = pendingProjectPackageText;
            if (text == null || text.length() == 0) {
                text = JarvisProjectPackager.getPendingProject(this);
            }
            String result = JarvisProjectPackager.writeProjectZipToUri(this, uri, text);
            appendConsole("SAVE: " + result);
            speak(result);
            if (result.toLowerCase(Locale.UK).indexOf("saved") >= 0) {
                pendingProjectPackageText = "";
                projectSaveOfferDismissed = false;
                JarvisProjectPackager.clearPendingProject(this);
            }
        }
    }

    public void onAsyncResponse(final String text) {
        runOnUiThread(new Runnable() {
            public void run() {
                if (text != null && text.length() > 0) {
                    if (JarvisProjectPackager.containsProjectPackage(text)) {
                        handleProjectPackageAnswer(text);
                        speak("I prepared the project package. Choose Save ZIP to pick where to save it, or Not Now to keep it for later.");
                        return;
                    }
                    String code = JarvisCodeTools.extractFirstCodeBlock(text);
                    if (code.length() > 0) {
                        showCodeSnippet(code);
                        speak(JarvisCodeTools.buildSpokenSummaryForCode(text));
                    } else {
                        hideCodeSnippet();
                        speak(text);
                    }
                }
            }
        });
    }

    protected void onDestroy() {
        if (handler != null && clockRunnable != null) {
            handler.removeCallbacks(clockRunnable);
        }
        try {
            if (speechRecognizer != null) {
                speechRecognizer.destroy();
            }
        } catch (Exception ignored) {
        }
        try {
            if (textToSpeech != null) {
                textToSpeech.stop();
                textToSpeech.shutdown();
            }
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    public static class SpaceView extends View {
        public SpaceView(Context context) {
            super(context);
        }
    }
}
