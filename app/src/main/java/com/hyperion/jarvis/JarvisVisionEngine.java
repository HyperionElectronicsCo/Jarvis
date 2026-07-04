package com.hyperion.jarvis;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;

import java.util.Locale;

public final class JarvisVisionEngine {
    private JarvisVisionEngine() {
    }

    public static float[] buildAdvancedFaceEmbedding(Bitmap source) {
        if (source == null) {
            return new float[0];
        }
        Bitmap face = cropCentreFaceArea(source);
        Bitmap scaled = Bitmap.createScaledBitmap(face, 48, 48, false);
        float[] gray = new float[48 * 48];
        float sum = 0.0f;
        for (int y = 0; y < 48; y++) {
            for (int x = 0; x < 48; x++) {
                int p = scaled.getPixel(x, y);
                float g = (0.299f * Color.red(p)) + (0.587f * Color.green(p)) + (0.114f * Color.blue(p));
                gray[(y * 48) + x] = g;
                sum += g;
            }
        }
        float mean = sum / gray.length;
        float variance = 0.0f;
        for (int i = 0; i < gray.length; i++) {
            float d = gray[i] - mean;
            variance += d * d;
        }
        float std = (float) Math.sqrt(variance / gray.length);
        if (std < 1.0f) {
            std = 1.0f;
        }

        float[] embedding = new float[128];
        int index = 0;

        // 8x8 normalised intensity map, similar to a compact face embedding backbone.
        for (int by = 0; by < 8; by++) {
            for (int bx = 0; bx < 8; bx++) {
                float block = 0.0f;
                int count = 0;
                for (int y = by * 6; y < (by + 1) * 6; y++) {
                    for (int x = bx * 6; x < (bx + 1) * 6; x++) {
                        block += (gray[(y * 48) + x] - mean) / std;
                        count++;
                    }
                }
                embedding[index++] = block / count;
            }
        }

        // 4x4 gradient energy and direction features.
        for (int by2 = 0; by2 < 4; by2++) {
            for (int bx2 = 0; bx2 < 4; bx2++) {
                float energy = 0.0f;
                float direction = 0.0f;
                int count2 = 0;
                for (int y2 = 1 + (by2 * 12); y2 < ((by2 + 1) * 12) - 1; y2++) {
                    for (int x2 = 1 + (bx2 * 12); x2 < ((bx2 + 1) * 12) - 1; x2++) {
                        float gx = gray[(y2 * 48) + x2 + 1] - gray[(y2 * 48) + x2 - 1];
                        float gy = gray[((y2 + 1) * 48) + x2] - gray[((y2 - 1) * 48) + x2];
                        energy += Math.abs(gx) + Math.abs(gy);
                        direction += Math.abs(gx) - Math.abs(gy);
                        count2++;
                    }
                }
                if (count2 <= 0) {
                    count2 = 1;
                }
                embedding[index++] = energy / (count2 * 255.0f);
                embedding[index++] = direction / (count2 * 255.0f);
            }
        }

        // Local binary pattern style texture histogram.
        float[] lbp = new float[32];
        int lbpSamples = 0;
        for (int y3 = 1; y3 < 47; y3 += 2) {
            for (int x3 = 1; x3 < 47; x3 += 2) {
                float c = gray[(y3 * 48) + x3];
                int code = 0;
                if (gray[((y3 - 1) * 48) + x3] > c) code |= 1;
                if (gray[((y3 + 1) * 48) + x3] > c) code |= 2;
                if (gray[(y3 * 48) + x3 - 1] > c) code |= 4;
                if (gray[(y3 * 48) + x3 + 1] > c) code |= 8;
                if (gray[((y3 - 1) * 48) + x3 - 1] > c) code |= 16;
                int bin = code % 32;
                lbp[bin] += 1.0f;
                lbpSamples++;
            }
        }
        if (lbpSamples <= 0) {
            lbpSamples = 1;
        }
        for (int l = 0; l < lbp.length && index < embedding.length; l++) {
            embedding[index++] = lbp[l] / lbpSamples;
        }

        normaliseVector(embedding);
        return embedding;
    }

    private static Bitmap cropCentreFaceArea(Bitmap source) {
        int w = source.getWidth();
        int h = source.getHeight();
        int size = Math.min(w, h);
        int cropW = Math.max(1, (int) (size * 0.78f));
        int cropH = Math.max(1, (int) (size * 0.88f));
        int x = Math.max(0, (w - cropW) / 2);
        int y = Math.max(0, (h - cropH) / 3);
        if (x + cropW > w) cropW = w - x;
        if (y + cropH > h) cropH = h - y;
        try {
            return Bitmap.createBitmap(source, x, y, cropW, cropH);
        } catch (Exception ignored) {
            return source;
        }
    }

    private static void normaliseVector(float[] values) {
        if (values == null || values.length == 0) {
            return;
        }
        double total = 0.0;
        for (int i = 0; i < values.length; i++) {
            total += values[i] * values[i];
        }
        double norm = Math.sqrt(total);
        if (norm < 0.00001) {
            norm = 1.0;
        }
        for (int j = 0; j < values.length; j++) {
            values[j] = (float) (values[j] / norm);
        }
    }

    public static double cosineDistance(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || b.length == 0) {
            return 999999.0;
        }
        int count = Math.min(a.length, b.length);
        double dot = 0.0;
        double na = 0.0;
        double nb = 0.0;
        for (int i = 0; i < count; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na <= 0.0 || nb <= 0.0) {
            return 999999.0;
        }
        double similarity = dot / (Math.sqrt(na) * Math.sqrt(nb));
        return 1.0 - similarity;
    }

    public static String buildSceneDescription(Bitmap bitmap, int faceCount) {
        if (bitmap == null) {
            return "No camera image available.";
        }
        SceneStats stats = analyseScene(bitmap);
        StringBuilder builder = new StringBuilder();
        builder.append("Vision scan complete. The image appears ").append(stats.lightName).append(" with ").append(stats.dominantName).append(". ");
        builder.append("Edge density is ").append(stats.edgeLabel).append(", which suggests ").append(stats.edgeMeaning).append(". ");
        if (faceCount > 0) {
            builder.append("I detected ").append(faceCount).append(faceCount == 1 ? " face. " : " faces. ");
        } else {
            builder.append("No faces detected. ");
        }
        builder.append("For exact object naming, use the product/Lens command so I can pass the visual search to a stronger vision engine.");
        return builder.toString();
    }

    public static String buildProductSearchQuery(Bitmap bitmap) {
        if (bitmap == null) {
            return "unknown product";
        }
        SceneStats stats = analyseScene(bitmap);
        StringBuilder query = new StringBuilder();
        query.append(stats.dominantName.replace(" tones", "").replace("colours", "colour"));
        query.append(' ');
        if (stats.edgeValue > 0.20f) {
            query.append("detailed patterned product");
        } else if (stats.lightName.indexOf("bright") >= 0) {
            query.append("bright object product");
        } else {
            query.append("object product");
        }
        return query.toString().trim();
    }

    private static SceneStats analyseScene(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        long red = 0;
        long green = 0;
        long blue = 0;
        int samples = 0;
        float edgeTotal = 0.0f;
        int edgeSamples = 0;
        int stepX = Math.max(1, width / 40);
        int stepY = Math.max(1, height / 40);
        for (int y = 1; y < height - 1; y += stepY) {
            for (int x = 1; x < width - 1; x += stepX) {
                int pixel = bitmap.getPixel(x, y);
                red += Color.red(pixel);
                green += Color.green(pixel);
                blue += Color.blue(pixel);
                samples++;
                int p1 = bitmap.getPixel(Math.min(width - 1, x + 1), y);
                int p2 = bitmap.getPixel(x, Math.min(height - 1, y + 1));
                int g0 = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3;
                int g1 = (Color.red(p1) + Color.green(p1) + Color.blue(p1)) / 3;
                int g2 = (Color.red(p2) + Color.green(p2) + Color.blue(p2)) / 3;
                edgeTotal += Math.abs(g0 - g1) + Math.abs(g0 - g2);
                edgeSamples++;
            }
        }
        if (samples <= 0) samples = 1;
        if (edgeSamples <= 0) edgeSamples = 1;
        int avgR = (int) (red / samples);
        int avgG = (int) (green / samples);
        int avgB = (int) (blue / samples);
        int brightness = (avgR + avgG + avgB) / 3;
        String dominant = "balanced colours";
        if (avgR > avgG + 20 && avgR > avgB + 20) dominant = "red or orange tones";
        else if (avgG > avgR + 20 && avgG > avgB + 20) dominant = "green tones";
        else if (avgB > avgR + 20 && avgB > avgG + 20) dominant = "blue tones";
        String light = brightness < 55 ? "dark" : (brightness > 185 ? "bright" : "moderately lit");
        float edgeValue = edgeTotal / (edgeSamples * 510.0f);
        String edgeLabel = edgeValue < 0.07f ? "low" : (edgeValue > 0.18f ? "high" : "medium");
        String meaning = edgeValue < 0.07f ? "a smooth surface or simple object" : (edgeValue > 0.18f ? "text, packaging, labels or fine detail" : "a normal object scene");
        SceneStats stats = new SceneStats();
        stats.lightName = light;
        stats.dominantName = dominant;
        stats.edgeValue = edgeValue;
        stats.edgeLabel = edgeLabel;
        stats.edgeMeaning = meaning;
        return stats;
    }

    public static boolean openGoogleLens(Context context) {
        if (context == null) {
            return false;
        }
        PackageManager pm = context.getPackageManager();
        String[] packages = new String[] { "com.google.ar.lens", "com.google.android.googlequicksearchbox" };
        for (int i = 0; i < packages.length; i++) {
            try {
                Intent launch = pm.getLaunchIntentForPackage(packages[i]);
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(launch);
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        try {
            Intent web = new Intent(Intent.ACTION_VIEW, Uri.parse("https://lens.google.com/"));
            web.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(web);
            return true;
        } catch (Exception ignoredAgain) {
            return false;
        }
    }

    public static void openProductSearch(Context context, String query) {
        if (context == null) {
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?tbm=shop&q=" + Uri.encode(query)));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception ignored) {
        }
    }

    private static final class SceneStats {
        String lightName;
        String dominantName;
        String edgeLabel;
        String edgeMeaning;
        float edgeValue;
    }
}
