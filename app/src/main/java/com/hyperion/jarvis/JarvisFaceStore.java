package com.hyperion.jarvis;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Locale;

public final class JarvisFaceStore {
    private static final String PREF_FACES = "jarvis_faces_blob";
    private static final String RECORD_SEP = "\u001E";
    private static final String FIELD_SEP = "\u001F";

    private JarvisFaceStore() {
    }

    public static void saveFace(Context context, String label, float[] signature) {
        if (context == null || label == null || label.trim().length() == 0 || signature == null || signature.length == 0) {
            return;
        }
        SharedPreferences prefs = context.getSharedPreferences(JarvisCommandCenter.PREFS_NAME, Context.MODE_PRIVATE);
        String old = prefs.getString(PREF_FACES, "");
        String record = clean(label) + FIELD_SEP + signatureToString(signature) + FIELD_SEP + System.currentTimeMillis() + FIELD_SEP + "v18-advanced";
        prefs.edit().putString(PREF_FACES, old == null || old.length() == 0 ? record : old + RECORD_SEP + record).commit();
    }

    public static String recognise(Context context, float[] signature) {
        if (context == null || signature == null || signature.length == 0) {
            return "No face signature available.";
        }
        SharedPreferences prefs = context.getSharedPreferences(JarvisCommandCenter.PREFS_NAME, Context.MODE_PRIVATE);
        String blob = prefs.getString(PREF_FACES, "");
        if (blob == null || blob.length() == 0) {
            return "I detected a face, but no known faces have been enrolled yet. Say: okay Jarvis remember face as followed by a name.";
        }
        String[] records = blob.split(RECORD_SEP);
        String bestLabel = null;
        double bestScore = 999999.0;
        int matchedSamples = 0;
        for (int i = 0; i < records.length; i++) {
            String[] fields = records[i].split(FIELD_SEP, -1);
            if (fields.length >= 2) {
                float[] known = stringToSignature(fields[1]);
                if (known.length > 0) {
                    double score = JarvisVisionEngine.cosineDistance(signature, known);
                    if (score < bestScore) {
                        bestScore = score;
                        bestLabel = fields[0];
                    }
                    matchedSamples++;
                }
            }
        }
        if (bestLabel == null) {
            return "I detected a face, but could not compare it to memory.";
        }
        int confidence = scoreToConfidence(bestScore);
        if (bestScore > 0.42) {
            return "I detected a face, but I am not confident enough to identify it. Closest stored profile was " + bestLabel + " at roughly " + confidence + " percent. Enrol the face again in better light to improve accuracy.";
        }
        return "Closest known face: " + bestLabel + ". Advanced local confidence roughly " + confidence + " percent using " + matchedSamples + " stored face sample" + (matchedSamples == 1 ? "" : "s") + ".";
    }

    public static String getFaceStatus(Context context) {
        if (context == null) {
            return "Face memory is unavailable.";
        }
        SharedPreferences prefs = context.getSharedPreferences(JarvisCommandCenter.PREFS_NAME, Context.MODE_PRIVATE);
        String blob = prefs.getString(PREF_FACES, "");
        if (blob == null || blob.length() == 0) {
            return "No faces are enrolled yet.";
        }
        String[] records = blob.split(RECORD_SEP);
        StringBuilder labels = new StringBuilder();
        int count = 0;
        for (int i = 0; i < records.length; i++) {
            String[] fields = records[i].split(FIELD_SEP, -1);
            if (fields.length >= 1 && fields[0].length() > 0) {
                count++;
                if (labels.indexOf(fields[0]) < 0) {
                    if (labels.length() > 0) labels.append(", ");
                    labels.append(fields[0]);
                }
            }
        }
        return "Face recognition has " + count + " enrolled sample" + (count == 1 ? "" : "s") + ". Known labels: " + labels.toString() + ".";
    }

    public static String clearFaces(Context context) {
        if (context == null) {
            return "Face memory is unavailable.";
        }
        SharedPreferences prefs = context.getSharedPreferences(JarvisCommandCenter.PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().remove(PREF_FACES).commit();
        return "All local face memories have been removed.";
    }

    private static int scoreToConfidence(double score) {
        int confidence = (int) Math.round(100.0 - (score * 165.0));
        if (confidence < 0) confidence = 0;
        if (confidence > 99) confidence = 99;
        return confidence;
    }

    private static String signatureToString(float[] signature) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < signature.length; i++) {
            if (i > 0) builder.append(',');
            builder.append(String.format(Locale.UK, "%.5f", new Float(signature[i])));
        }
        return builder.toString();
    }

    private static float[] stringToSignature(String text) {
        if (text == null || text.length() == 0) {
            return new float[0];
        }
        String[] parts = text.split(",");
        float[] values = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                values[i] = Float.parseFloat(parts[i]);
            } catch (Exception ignored) {
                values[i] = 0.0f;
            }
        }
        return values;
    }

    private static String clean(String value) {
        return value == null ? "" : value.replace(RECORD_SEP, " ").replace(FIELD_SEP, " ").trim();
    }
}
