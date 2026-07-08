package com.hyperion.jarvis;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

public class JarvisAssistantService extends Service implements TextToSpeech.OnInitListener, JarvisOutput {
    public static final int NOTIFICATION_ID = 7249;
    private static final String CHANNEL_ID = "jarvis_background_channel";
    private static final String CHANNEL_NAME = "Jarvis Background Listener";

    private Handler handler;
    private SpeechRecognizer speechRecognizer;
    private Intent recognizerIntent;
    private TextToSpeech textToSpeech;
    private boolean ttsReady;
    private boolean muted;
    private boolean destroyed;
    private boolean commandMode;
    private boolean listening;
    private boolean pendingRestartAfterSpeech;
    private boolean paused;
    private String lastPartialText;
    private String pendingFinalText;
    private Runnable finalCommandRunnable;

    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        ttsReady = false;
        muted = JarvisCommandCenter.isMuted(this);
        destroyed = false;
        commandMode = false;
        listening = false;
        pendingRestartAfterSpeech = false;
        paused = JarvisCommandCenter.isBackgroundPaused(this);
        lastPartialText = null;
        pendingFinalText = null;
        finalCommandRunnable = null;
        startForeground(NOTIFICATION_ID, buildNotification("Say okay Jarvis to wake assistant"));
        textToSpeech = new TextToSpeech(this, this);
        setupSpeechRecognizer();
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (JarvisCommandCenter.ACTION_STOP_BACKGROUND.equals(action)) {
            JarvisCommandCenter.setBackgroundEnabled(this, false);
            JarvisCommandCenter.setBackgroundPaused(this, false);
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (JarvisCommandCenter.ACTION_PAUSE_BACKGROUND.equals(action)) {
            pauseBackgroundListening();
            return START_STICKY;
        }
        if (JarvisCommandCenter.ACTION_RESUME_BACKGROUND.equals(action)) {
            paused = false;
            JarvisCommandCenter.setBackgroundEnabled(this, true);
            JarvisCommandCenter.setBackgroundPaused(this, false);
            startForeground(NOTIFICATION_ID, buildNotification("Listening for okay Jarvis"));
            startListeningSoon(350);
            return START_STICKY;
        }

        if (!JarvisCommandCenter.hasOverlayPermission(this)) {
            JarvisCommandCenter.setBackgroundEnabled(this, false);
            JarvisCommandCenter.setBackgroundPaused(this, false);
            updateNotification("Display over other apps permission required");
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        JarvisCommandCenter.setBackgroundEnabled(this, true);
        paused = JarvisCommandCenter.isBackgroundPaused(this);
        if (paused) {
            startForeground(NOTIFICATION_ID, buildNotification("Paused - microphone off"));
            stopSpeechRecognizerOnly();
            return START_STICKY;
        }
        startForeground(NOTIFICATION_ID, buildNotification("Listening for okay Jarvis"));
        startListeningSoon(600);
        return START_STICKY;
    }

    public IBinder onBind(Intent intent) {
        return null;
    }

    private void setupSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
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
                updateNotification("Listening for okay Jarvis");
            }

            public void onBeginningOfSpeech() {
                updateNotification("Voice detected");
            }

            public void onRmsChanged(float rmsdB) {
            }

            public void onBufferReceived(byte[] buffer) {
            }

            public void onEndOfSpeech() {
                listening = false;
                updateNotification("Processing voice");
            }

            public void onError(int error) {
                listening = false;
                if (!destroyed && !paused) {
                    updateNotification("Listening for okay Jarvis");
                    startListeningSoon(error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ? 1200 : 450);
                }
            }

            public void onResults(Bundle results) {
                listening = false;
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                String best = chooseBestRecognizedText(matches);
                String merged = chooseMoreCompleteText(best, lastPartialText);
                if (merged != null && merged.length() > 0) {
                    scheduleCommandFinalization(merged);
                } else {
                    startListeningSoon(350);
                }
            }

            public void onPartialResults(Bundle partialResults) {
                ArrayList<String> partial = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (partial != null && partial.size() > 0) {
                    String heard = chooseBestRecognizedText(partial);
                    lastPartialText = chooseMoreCompleteText(heard, lastPartialText);
                    if (lastPartialText != null && lastPartialText.length() > 0) {
                        if (JarvisCommandCenter.containsWakePhrase(lastPartialText)) {
                            String afterWake = JarvisCommandCenter.stripWakePhrase(lastPartialText);
                            if (afterWake.length() > 0) {
                                updateNotification("Command forming: " + afterWake);
                            } else {
                                updateNotification("Wake phrase detected - keep speaking");
                            }
                        } else if (commandMode) {
                            updateNotification("Command forming: " + lastPartialText);
                        }
                    }
                }
            }

            public void onEvent(int eventType, Bundle params) {
            }
        });
    }

    private String chooseBestRecognizedText(ArrayList<String> matches) {
        if (matches == null || matches.size() == 0) {
            return null;
        }
        String first = matches.get(0);
        String bestWakeWithCommand = null;
        String bestCommand = null;
        for (int i = 0; i < matches.size(); i++) {
            String item = matches.get(i);
            if (item == null) {
                continue;
            }
            String trimmed = item.trim();
            if (trimmed.length() == 0) {
                continue;
            }
            if (JarvisCommandCenter.containsWakePhrase(trimmed)) {
                String afterWake = JarvisCommandCenter.stripWakePhrase(trimmed);
                if (afterWake.length() > 0) {
                    bestWakeWithCommand = trimmed;
                    break;
                }
            } else if (commandMode && bestCommand == null) {
                bestCommand = trimmed;
            }
        }
        if (bestWakeWithCommand != null) {
            return bestWakeWithCommand;
        }
        if (bestCommand != null) {
            return bestCommand;
        }
        return first;
    }

    private String chooseMoreCompleteText(String first, String second) {
        String a = first == null ? "" : first.trim();
        String b = second == null ? "" : second.trim();
        if (a.length() == 0) {
            return b;
        }
        if (b.length() == 0) {
            return a;
        }
        String aCommand = JarvisCommandCenter.containsWakePhrase(a) ? JarvisCommandCenter.stripWakePhrase(a) : a;
        String bCommand = JarvisCommandCenter.containsWakePhrase(b) ? JarvisCommandCenter.stripWakePhrase(b) : b;
        if (bCommand.length() > aCommand.length() + 2) {
            return b;
        }
        return a;
    }

    private void scheduleCommandFinalization(String text) {
        pendingFinalText = text == null ? "" : text.trim();
        if (finalCommandRunnable != null) {
            handler.removeCallbacks(finalCommandRunnable);
        }
        finalCommandRunnable = new Runnable() {
            public void run() {
                String finalText = pendingFinalText;
                pendingFinalText = null;
                lastPartialText = null;
                finalCommandRunnable = null;
                if (finalText != null && finalText.trim().length() > 0) {
                    handleRecognizedText(finalText.trim());
                } else {
                    startListeningSoon(350);
                }
            }
        };
        updateNotification("Confirming full command...");
        handler.postDelayed(finalCommandRunnable, 1000);
    }

    private void handleRecognizedText(String text) {
        if (paused) {
            return;
        }
        if (text == null) {
            startListeningSoon(350);
            return;
        }

        if (commandMode) {
            commandMode = false;
            runCommand(text);
            return;
        }

        if (JarvisCommandCenter.containsWakePhrase(text)) {
            String command = JarvisCommandCenter.stripWakePhrase(text);
            if (command.length() > 0) {
                runCommand(command);
            } else {
                commandMode = true;
                speak("At your service.", true);
            }
        } else {
            startListeningSoon(350);
        }
    }

    private void runCommand(String command) {
        updateNotification("Command: " + command);
        String response = JarvisCommandCenter.processCommand(this, command, this);
        if (response == null || response.length() == 0 || muted) {
            startListeningSoon(650);
        } else {
            speak(response, true);
        }
    }

    private void startListeningSoon(long delay) {
        if (destroyed || paused || JarvisCommandCenter.isBackgroundPaused(this) || !JarvisCommandCenter.isBackgroundEnabled(this)) {
            return;
        }
        if (!JarvisCommandCenter.hasOverlayPermission(this)) {
            JarvisCommandCenter.setBackgroundEnabled(this, false);
            destroyed = true;
            stopForeground(true);
            stopSelf();
            return;
        }
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(new Runnable() {
            public void run() {
                startListeningNow();
            }
        }, delay);
    }

    private void startListeningNow() {
        if (destroyed || listening || paused || JarvisCommandCenter.isBackgroundPaused(this)) {
            return;
        }
        if (speechRecognizer == null) {
            setupSpeechRecognizer();
        }
        if (speechRecognizer == null || recognizerIntent == null) {
            updateNotification("Speech recognizer unavailable");
            return;
        }
        try {
            speechRecognizer.cancel();
        } catch (Exception ignored) {
        }
        try {
            listening = true;
            speechRecognizer.startListening(recognizerIntent);
        } catch (Exception error) {
            listening = false;
            startListeningSoon(1200);
        }
    }

    private void speak(String text, boolean restartAfter) {
        if (text == null || text.length() == 0) {
            if (restartAfter) {
                startListeningSoon(650);
            }
            return;
        }
        updateNotification(text);
        String speechText = text;
        if (countWords(text) > 30) {
            speechText = "I displayed the full response in Jarvis. Open the app to read it, or say read that out loud inside Jarvis.";
        }
        pendingRestartAfterSpeech = restartAfter;
        try {
            if (speechRecognizer != null) {
                speechRecognizer.cancel();
            }
        } catch (Exception ignored) {
        }
        listening = false;
        if (muted || !ttsReady || textToSpeech == null) {
            if (restartAfter) {
                startListeningSoon(950);
            }
            return;
        }
        String utteranceId = "jarvis_service_" + System.currentTimeMillis();
        if (Build.VERSION.SDK_INT >= 21) {
            textToSpeech.speak(speechText, TextToSpeech.QUEUE_FLUSH, null, utteranceId);
        } else {
            HashMap<String, String> params = new HashMap<String, String>();
            params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);
            textToSpeech.speak(speechText, TextToSpeech.QUEUE_FLUSH, params);
        }
    }

    private int countWords(String text) {
        if (text == null) {
            return 0;
        }
        String trimmed = text.trim();
        if (trimmed.length() == 0) {
            return 0;
        }
        String[] parts = trimmed.split("\\s+");
        return parts.length;
    }

    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            ttsReady = true;
            JarvisVoiceTools.configureJarvisVoice(textToSpeech);
            textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                public void onStart(String utteranceId) {
                }

                public void onDone(String utteranceId) {
                    handler.post(new Runnable() {
                        public void run() {
                            if (pendingRestartAfterSpeech) {
                                pendingRestartAfterSpeech = false;
                                startListeningSoon(450);
                            }
                        }
                    });
                }

                public void onError(String utteranceId) {
                    handler.post(new Runnable() {
                        public void run() {
                            if (pendingRestartAfterSpeech) {
                                pendingRestartAfterSpeech = false;
                                startListeningSoon(650);
                            }
                        }
                    });
                }
            });
            if (JarvisCommandCenter.isBackgroundEnabled(this) && !JarvisCommandCenter.isBackgroundPaused(this)) {
                speak("Background systems online.", true);
            }
        }
    }

    private void pauseBackgroundListening() {
        commandMode = false;
        paused = true;
        JarvisCommandCenter.setBackgroundEnabled(this, true);
        JarvisCommandCenter.setBackgroundPaused(this, true);
        if (finalCommandRunnable != null && handler != null) {
            handler.removeCallbacks(finalCommandRunnable);
            finalCommandRunnable = null;
        }
        pendingFinalText = null;
        lastPartialText = null;
        stopSpeechRecognizerOnly();
        startForeground(NOTIFICATION_ID, buildNotification("Paused - microphone off"));
        updateNotification("Paused - microphone off");
    }

    private void stopSpeechRecognizerOnly() {
        listening = false;
        try {
            if (speechRecognizer != null) {
                speechRecognizer.cancel();
            }
        } catch (Exception ignored) {
        }
    }

    private Notification buildNotification(String message) {
        createNotificationChannel();

        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPendingIntent = PendingIntent.getActivity(this, 1, openIntent, pendingIntentFlags());

        Intent toggleIntent = new Intent(this, JarvisAssistantService.class);
        toggleIntent.setAction(paused ? JarvisCommandCenter.ACTION_RESUME_BACKGROUND : JarvisCommandCenter.ACTION_PAUSE_BACKGROUND);
        PendingIntent togglePendingIntent = PendingIntent.getService(this, 3, toggleIntent, pendingIntentFlags());

        Intent stopIntent = new Intent(this, JarvisAssistantService.class);
        stopIntent.setAction(JarvisCommandCenter.ACTION_STOP_BACKGROUND);
        PendingIntent stopPendingIntent = PendingIntent.getService(this, 2, stopIntent, pendingIntentFlags());

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= 26) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }
        builder.setSmallIcon(getApplicationInfo().icon);
        builder.setContentTitle("J.A.R.V.I.S Background Service");
        builder.setContentText(message);
        builder.setContentIntent(openPendingIntent);
        builder.setOngoing(true);
        builder.setShowWhen(false);
        if (Build.VERSION.SDK_INT >= 16) {
            builder.addAction(getApplicationInfo().icon, paused ? "Resume" : "Pause", togglePendingIntent);
            builder.addAction(getApplicationInfo().icon, "Stop", stopPendingIntent);
        }
        if (Build.VERSION.SDK_INT >= 21) {
            builder.setColor(Color.rgb(0, 216, 255));
            builder.setCategory(Notification.CATEGORY_SERVICE);
            builder.setVisibility(Notification.VISIBILITY_PUBLIC);
        }
        return builder.build();
    }

    private void updateNotification(String message) {
        try {
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.notify(NOTIFICATION_ID, buildNotification(message));
            }
        } catch (Exception ignored) {
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager != null && manager.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel channel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW);
                channel.setDescription("Keeps Jarvis available for the okay Jarvis hot phrase, with pause and resume controls.");
                channel.setSound(null, null);
                manager.createNotificationChannel(channel);
            }
        }
    }

    private int pendingIntentFlags() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return flags;
    }

    public void onConsole(String line) {
    }

    public void onClearConsole() {
    }

    public void onMuteChanged(boolean newMuted) {
        muted = newMuted;
        if (muted && textToSpeech != null) {
            textToSpeech.stop();
        }
    }

    public void onStopListeningRequested() {
        pauseBackgroundListening();
    }

    public void onBackgroundStateChanged(boolean enabled) {
        if (!enabled) {
            paused = false;
            destroyed = true;
            stopForeground(true);
            stopSelf();
        }
    }

    public void onClearCodeSnippet() {
        JarvisCodeTools.clearLastCodeSnippet(this);
    }

    public void onClearGeneratedImage() {
        JarvisImageStore.clearPendingImage(this);
        speak("Generated image cleared.", true);
    }

    public void onSavePendingProjectRequested() {
        String pending = JarvisProjectPackager.getPendingProject(this);
        if (pending != null && pending.length() > 0) {
            speak("Open Jarvis to choose where to save the generated project zip.", true);
        } else {
            speak("There is no generated project waiting to save.", true);
        }
    }

    public void onAsyncResponse(final String text) {
        if (handler == null) {
            return;
        }
        handler.post(new Runnable() {
            public void run() {
                if (text != null && text.length() > 0) {
                    if (JarvisProjectPackager.containsProjectPackage(text)) {
                        JarvisProjectPackager.rememberPendingProject(JarvisAssistantService.this, text);
                        JarvisCodeTools.clearLastCodeSnippet(JarvisAssistantService.this);
                        speak("I prepared the project package. Open Jarvis to choose where to save the zip file.", true);
                        return;
                    }
                    String code = JarvisCodeTools.extractFirstCodeBlock(text);
                    if (code.length() > 0) {
                        JarvisCodeTools.saveLastCodeSnippet(JarvisAssistantService.this, code);
                        speak("I prepared a code snippet. Open Jarvis to view and copy it from the code box.", true);
                    } else {
                        speak(text, true);
                    }
                } else {
                    startListeningSoon(650);
                }
            }
        });
    }


public void onImageGenerated(byte[] imageBytes, String mimeType, String suggestedFileName) {
    if (imageBytes != null && imageBytes.length > 0) {
        JarvisImageStore.savePendingImage(this, imageBytes, mimeType, suggestedFileName);
        speak("I generated the image. Open Jarvis to view it and tap it to save.", true);
    } else {
        speak("I could not prepare the generated image.", true);
    }
}


    public void onDestroy() {
        destroyed = true;
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
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
}
