package com.hyperion.jarvis;

public final class JarvisVisionBridgeResult {
    public String engineName;
    public String label;
    public String details;
    public float confidence;
    public boolean available;
    public boolean confident;

    public JarvisVisionBridgeResult() {
        engineName = "Unknown";
        label = "";
        details = "";
        confidence = 0.0f;
        available = false;
        confident = false;
    }

    public static JarvisVisionBridgeResult unavailable(String engine, String message) {
        JarvisVisionBridgeResult result = new JarvisVisionBridgeResult();
        result.engineName = engine == null ? "Unknown" : engine;
        result.details = message == null ? "" : message;
        result.available = false;
        result.confident = false;
        return result;
    }

    public static JarvisVisionBridgeResult recognised(String engine, String label, String details, float confidence) {
        JarvisVisionBridgeResult result = new JarvisVisionBridgeResult();
        result.engineName = engine == null ? "Unknown" : engine;
        result.label = label == null ? "" : label.trim();
        result.details = details == null ? "" : details.trim();
        result.confidence = confidence;
        result.available = true;
        result.confident = result.label.length() > 0 && confidence >= 0.42f;
        return result;
    }

    public boolean hasUsefulLabel() {
        return available && label != null && label.length() > 0 && confident;
    }

    public String toDisplayText() {
        StringBuilder builder = new StringBuilder();
        builder.append(engineName == null ? "Vision bridge" : engineName);
        if (label != null && label.length() > 0) {
            builder.append(": ").append(label);
        }
        if (confidence > 0.0f) {
            builder.append(" (").append(Math.round(confidence * 100.0f)).append("% confidence)");
        }
        if (details != null && details.length() > 0) {
            builder.append("\n").append(details);
        }
        return builder.toString();
    }
}
