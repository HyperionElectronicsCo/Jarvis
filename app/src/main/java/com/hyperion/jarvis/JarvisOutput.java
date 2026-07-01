package com.hyperion.jarvis;

public interface JarvisOutput {
    void onConsole(String line);
    void onClearConsole();
    void onMuteChanged(boolean muted);
    void onStopListeningRequested();
    void onBackgroundStateChanged(boolean enabled);
}
