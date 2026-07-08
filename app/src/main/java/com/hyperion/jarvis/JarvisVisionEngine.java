package com.hyperion.jarvis;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.StrictMode;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
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
        String productGuess = guessKnownProduct(bitmap);
        SceneStats stats = analyseScene(bitmap);
        StringBuilder builder = new StringBuilder();
        if (productGuess != null && productGuess.length() > 0) {
            builder.append("Likely object: ").append(productGuess).append(". ");
        } else {
            builder.append("Vision scan complete. I can see a real object, but local recognition is not fully certain. ");
        }
        builder.append("Lighting: ").append(stats.lightName).append(". Visual detail: ").append(stats.edgeLabel).append(". ");
        if (faceCount > 0) {
            builder.append("I detected ").append(faceCount).append(faceCount == 1 ? " face. " : " faces. ");
        } else {
            builder.append("No faces detected. ");
        }
        builder.append("For stronger naming, use AI recognition or the image-aware Google Lens button.");
        return builder.toString();
    }


    public static String buildProductSearchQuery(Bitmap bitmap) {
        if (bitmap == null) {
            return "unknown product object from camera photo";
        }
        String guessed = guessKnownProduct(bitmap);
        if (guessed != null && guessed.length() > 0) {
            return guessed + " product photo exact model brand";
        }
        SceneStats stats = analyseScene(bitmap);
        StringBuilder query = new StringBuilder();
        query.append("camera photo product object ");
        query.append(stats.dominantName.replace(" tones", "").replace("colours", "colour"));
        if (stats.edgeValue > 0.16f) {
            query.append(" detailed branded item");
        } else {
            query.append(" simple consumer object");
        }
        return query.toString().trim();
    }

    public static String guessKnownProduct(Bitmap bitmap) {
        ProductStats stats = analyseProductShape(bitmap);
        if (stats == null) {
            return "";
        }
        if (looksLikeSmartphone(stats)) {
            return "smartphone";
        }
        if (looksLikeOverEarHeadphones(stats)) {
            return "over-ear headphones";
        }
        if (looksLikeMonsterEnergyCan(stats)) {
            return "Monster Energy can";
        }
        if (looksLikeIrnBruStyleCan(stats)) {
            return "IRN-BRU soft drink can";
        }
        if (looksLikePepsiStyleCan(stats)) {
            return "Pepsi Max / Pepsi drink can";
        }
        if (looksLikeRedDrinkCan(stats)) {
            return "Coca-Cola can or similar red soft drink can";
        }
        if (looksLikeCanOrBottle(stats)) {
            return "drink can or bottle";
        }
        return "";
    }

    public static String buildProductRecognitionHint(Bitmap bitmap) {
        ProductStats stats = analyseProductShape(bitmap);
        String guess = guessKnownProduct(bitmap);
        StringBuilder builder = new StringBuilder();
        if (guess != null && guess.length() > 0) {
            builder.append("Likely product/object: ").append(guess).append(".");
        } else {
            builder.append("Likely product/object: unknown consumer item.");
        }
        if (stats != null) {
            builder.append(" Local cues: dark object ratio ").append(percent(stats.darkRatio));
            builder.append(", bright/white ratio ").append(percent(stats.whiteRatio));
            builder.append(", green ratio ").append(percent(stats.greenRatio));
            builder.append(", red ratio ").append(percent(stats.redRatio));
            builder.append(", blue ratio ").append(percent(stats.blueRatio));
            builder.append(", orange ratio ").append(percent(stats.orangeRatio));
            if (stats.hasForeground) {
                builder.append(", object box ").append(stats.boxWidth).append("x").append(stats.boxHeight);
            }
            builder.append('.');
        }
        return builder.toString();
    }

    private static boolean looksLikeOverEarHeadphones(ProductStats stats) {
        if (stats == null || !stats.hasForeground) {
            return false;
        }
        // Headphones normally form a wide/two-lobed shape. Do not classify a tall,
        // solid dark rectangle as headphones because that is more likely a phone.
        if (looksLikeSmartphone(stats)) {
            return false;
        }
        if (stats.darkRatio < 0.08f) {
            return false;
        }
        if (stats.boxAspect > 1.18f && stats.centreDarkRatio > 0.18f && stats.middleLowerDarkRatio > 0.12f) {
            return false;
        }
        if (stats.leftLowerDarkRatio > 0.12f && stats.rightLowerDarkRatio > 0.12f && stats.upperMiddleDarkRatio > 0.04f && stats.middleLowerDarkRatio < Math.max(stats.leftLowerDarkRatio, stats.rightLowerDarkRatio) * 0.82f) {
            return true;
        }
        if (stats.leftLowerDarkRatio > 0.18f && stats.rightLowerDarkRatio > 0.18f && stats.middleLowerDarkRatio < Math.max(stats.leftLowerDarkRatio, stats.rightLowerDarkRatio) * 0.72f) {
            return true;
        }
        return false;
    }

    private static boolean looksLikeSmartphone(ProductStats stats) {
        if (stats == null || !stats.hasForeground) {
            return false;
        }
        if (stats.boxAreaRatio < 0.045f || stats.boxAreaRatio > 0.82f) {
            return false;
        }
        // Strong phone/tablet cue: a single dark rectangular slab on a lighter background.
        // This deliberately runs before headphones because black phones were previously
        // being missed or confused with headphone ear cups.
        boolean tallRectangle = stats.boxAspect >= 1.06f && stats.boxAspect <= 2.75f;
        boolean landscapeRectangle = stats.boxAspect <= 0.94f && stats.boxAspect >= 0.36f;
        boolean solidDarkCentre = stats.centreDarkRatio > 0.12f || stats.darkRatio > 0.12f;
        boolean lowColourLogo = stats.greenRatio < 0.055f && stats.redRatio < 0.080f;
        boolean largePlainDarkSlab = stats.darkRatio > 0.18f && stats.edgeRatio < 0.34f && lowColourLogo;
        boolean notStrongTwoLobedHeadphones = !(stats.leftLowerDarkRatio > 0.16f && stats.rightLowerDarkRatio > 0.16f && stats.middleLowerDarkRatio < Math.max(stats.leftLowerDarkRatio, stats.rightLowerDarkRatio) * 0.58f);
        if ((tallRectangle || landscapeRectangle) && solidDarkCentre && lowColourLogo && notStrongTwoLobedHeadphones) {
            return true;
        }
        if ((tallRectangle || landscapeRectangle) && largePlainDarkSlab) {
            return true;
        }
        return false;
    }

    private static boolean looksLikeMonsterEnergyCan(ProductStats stats) {
        if (stats == null) {
            return false;
        }
        return stats.darkRatio > 0.30f && stats.greenRatio > 0.035f;
    }

    private static boolean looksLikeIrnBruStyleCan(ProductStats stats) {
        if (stats == null) {
            return false;
        }
        if (!looksLikeCanOrBottle(stats)) {
            return false;
        }
        boolean orangeBlue = stats.orangeRatio > 0.030f && stats.blueRatio > 0.018f;
        boolean orangeRedBlue = stats.orangeRatio > 0.020f && stats.redRatio > 0.020f && stats.blueRatio > 0.012f;
        boolean detailedCan = stats.edgeRatio > 0.045f || stats.darkRatio > 0.16f;
        return detailedCan && (orangeBlue || orangeRedBlue);
    }

    private static boolean looksLikePepsiStyleCan(ProductStats stats) {
        if (stats == null) {
            return false;
        }
        /*
         * v55: Pepsi/Pepsi Max cans were being missed because the full image
         * foreground box can be nearly square after the user's hand/background
         * are included, and the silver can body is not always counted as pure white
         * under flash.  Use the stronger real-world cue: red + blue + can-sized
         * foreground, instead of demanding a tall rectangle and high white ratio.
         */
        boolean canSizedObject = stats.hasForeground
                && stats.boxAreaRatio > 0.10f
                && stats.boxAreaRatio < 0.88f
                && stats.boxAspect > 0.75f
                && stats.boxAspect < 2.45f;
        boolean strongPepsiColours = stats.redRatio > 0.045f && stats.blueRatio > 0.030f;
        boolean moderatePepsiColours = stats.redRatio > 0.030f && stats.blueRatio > 0.020f
                && stats.edgeRatio > 0.045f;
        boolean labelledCanSurface = stats.darkRatio > 0.18f || stats.whiteRatio > 0.004f || stats.edgeRatio > 0.055f;
        return canSizedObject && labelledCanSurface && (strongPepsiColours || moderatePepsiColours);
    }

    private static boolean looksLikeRedDrinkCan(ProductStats stats) {
        if (stats == null) {
            return false;
        }
        return stats.redRatio > 0.12f && stats.whiteRatio > 0.04f;
    }

    private static boolean looksLikeCanOrBottle(ProductStats stats) {
        if (stats == null || !stats.hasForeground) {
            return false;
        }
        if (stats.boxAreaRatio < 0.06f || stats.boxAreaRatio > 0.90f) {
            return false;
        }
        /*
         * v55: A can held close to the camera often measures around 1.1-1.5
         * height/width once the hand and label edges are included.  The old
         * 1.55 threshold was too strict and caused Pepsi Max / similar cans to
         * fall through as "unknown consumer item".
         */
        boolean uprightCanOrBottle = stats.boxAspect > 1.05f && stats.boxAspect < 3.20f;
        boolean wideObjectBottleView = stats.boxAspect < 0.64f;
        boolean colourfulPackaging = stats.redRatio > 0.025f || stats.blueRatio > 0.025f
                || stats.greenRatio > 0.025f || stats.whiteRatio > 0.006f;
        boolean detailedSurface = stats.edgeRatio > 0.040f || stats.darkRatio > 0.16f;
        return (uprightCanOrBottle || wideObjectBottleView) && (colourfulPackaging || detailedSurface);
    }

    private static ProductStats analyseProductShape(Bitmap bitmap) {
        if (bitmap == null) {
            return null;
        }
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        if (width <= 0 || height <= 0) {
            return null;
        }
        int stepX = Math.max(1, width / 64);
        int stepY = Math.max(1, height / 64);
        int samples = 0;
        int dark = 0;
        int green = 0;
        int red = 0;
        int blue = 0;
        int orange = 0;
        int white = 0;
        int edges = 0;
        int foreground = 0;
        int leftLowerDark = 0;
        int leftLowerSamples = 0;
        int rightLowerDark = 0;
        int rightLowerSamples = 0;
        int middleLowerDark = 0;
        int middleLowerSamples = 0;
        int upperMiddleDark = 0;
        int upperMiddleSamples = 0;
        int centreDark = 0;
        int centreSamples = 0;
        int minX = width;
        int minY = height;
        int maxX = 0;
        int maxY = 0;
        int cornerColour = estimateCornerColour(bitmap);
        int cornerR = Color.red(cornerColour);
        int cornerG = Color.green(cornerColour);
        int cornerB = Color.blue(cornerColour);
        for (int y = 1; y < height - 1; y += stepY) {
            for (int x = 1; x < width - 1; x += stepX) {
                int color = bitmap.getPixel(x, y);
                int r = Color.red(color);
                int g = Color.green(color);
                int b = Color.blue(color);
                int brightness = (r + g + b) / 3;
                int max = Math.max(r, Math.max(g, b));
                int min = Math.min(r, Math.min(g, b));
                int saturation = max - min;
                int diffFromCorner = Math.abs(r - cornerR) + Math.abs(g - cornerG) + Math.abs(b - cornerB);
                int pRight = bitmap.getPixel(Math.min(width - 1, x + stepX), y);
                int pDown = bitmap.getPixel(x, Math.min(height - 1, y + stepY));
                int gray = brightness;
                int grayRight = (Color.red(pRight) + Color.green(pRight) + Color.blue(pRight)) / 3;
                int grayDown = (Color.red(pDown) + Color.green(pDown) + Color.blue(pDown)) / 3;
                boolean isDark = brightness < 95;
                boolean isGreen = g > 80 && g > r + 14 && g > b + 8;
                boolean isRed = r > 130 && r > g + 20 && r > b + 20;
                boolean isBlue = b > 120 && b > r + 16 && b > g + 10;
                boolean isOrange = r > 145 && g > 55 && g < 155 && b < 115 && r > b + 45;
                boolean isWhite = brightness > 208 && Math.abs(r - g) < 28 && Math.abs(g - b) < 28;
                boolean isEdge = Math.abs(gray - grayRight) + Math.abs(gray - grayDown) > 38;
                boolean isForeground = isDark || saturation > 42 || diffFromCorner > 72 || isEdge;
                samples++;
                if (isDark) dark++;
                if (isGreen) green++;
                if (isRed) red++;
                if (isBlue) blue++;
                if (isOrange) orange++;
                if (isWhite) white++;
                if (isEdge) edges++;
                if (isForeground) {
                    foreground++;
                    if (x < minX) minX = x;
                    if (y < minY) minY = y;
                    if (x > maxX) maxX = x;
                    if (y > maxY) maxY = y;
                }
                if (x > width * 0.05f && x < width * 0.46f && y > height * 0.42f && y < height * 0.90f) {
                    leftLowerSamples++;
                    if (isDark) leftLowerDark++;
                }
                if (x > width * 0.54f && x < width * 0.95f && y > height * 0.42f && y < height * 0.90f) {
                    rightLowerSamples++;
                    if (isDark) rightLowerDark++;
                }
                if (x > width * 0.35f && x < width * 0.65f && y > height * 0.42f && y < height * 0.92f) {
                    middleLowerSamples++;
                    if (isDark) middleLowerDark++;
                }
                if (x > width * 0.18f && x < width * 0.82f && y > height * 0.10f && y < height * 0.48f) {
                    upperMiddleSamples++;
                    if (isDark) upperMiddleDark++;
                }
                if (x > width * 0.25f && x < width * 0.75f && y > height * 0.20f && y < height * 0.82f) {
                    centreSamples++;
                    if (isDark) centreDark++;
                }
            }
        }
        if (samples <= 0) {
            samples = 1;
        }
        ProductStats stats = new ProductStats();
        stats.darkRatio = dark / (float) samples;
        stats.greenRatio = green / (float) samples;
        stats.redRatio = red / (float) samples;
        stats.blueRatio = blue / (float) samples;
        stats.orangeRatio = orange / (float) samples;
        stats.whiteRatio = white / (float) samples;
        stats.edgeRatio = edges / (float) samples;
        stats.foregroundRatio = foreground / (float) samples;
        stats.leftLowerDarkRatio = ratio(leftLowerDark, leftLowerSamples);
        stats.rightLowerDarkRatio = ratio(rightLowerDark, rightLowerSamples);
        stats.middleLowerDarkRatio = ratio(middleLowerDark, middleLowerSamples);
        stats.upperMiddleDarkRatio = ratio(upperMiddleDark, upperMiddleSamples);
        stats.centreDarkRatio = ratio(centreDark, centreSamples);
        stats.hasForeground = foreground > 0 && minX < maxX && minY < maxY;
        if (stats.hasForeground) {
            stats.boxWidth = Math.max(1, maxX - minX);
            stats.boxHeight = Math.max(1, maxY - minY);
            stats.boxAspect = stats.boxHeight / (float) stats.boxWidth;
            stats.boxAreaRatio = (stats.boxWidth * stats.boxHeight) / (float) (width * height);
        } else {
            stats.boxWidth = 0;
            stats.boxHeight = 0;
            stats.boxAspect = 1.0f;
            stats.boxAreaRatio = 0.0f;
        }
        return stats;
    }

    private static float ratio(int value, int total) {
        if (total <= 0) {
            return 0.0f;
        }
        return value / (float) total;
    }

    private static String percent(float value) {
        int rounded = Math.round(value * 100.0f);
        return String.valueOf(rounded) + "%";
    }

    private static int estimateCornerColour(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] xs = new int[] { 1, Math.max(1, width - 2), 1, Math.max(1, width - 2) };
        int[] ys = new int[] { 1, 1, Math.max(1, height - 2), Math.max(1, height - 2) };
        int red = 0;
        int green = 0;
        int blue = 0;
        for (int i = 0; i < xs.length; i++) {
            int c = bitmap.getPixel(xs[i], ys[i]);
            red += Color.red(c);
            green += Color.green(c);
            blue += Color.blue(c);
        }
        return Color.rgb(red / xs.length, green / xs.length, blue / xs.length);
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
        return openGoogleLens(context, null, "unknown product from camera image");
    }

    public static boolean openGoogleLens(Context context, Bitmap bitmap) {
        return openGoogleLens(context, bitmap, "unknown product from camera image");
    }

    public static boolean openGoogleLens(Context context, Bitmap bitmap, String fallbackQuery) {
        if (context == null) {
            return false;
        }
        String strictQuery = buildStrictVisualSearchQuery(bitmap, fallbackQuery);
        Uri imageUri = bitmap == null ? null : createLensImageUri(context, bitmap);
        if (imageUri != null) {
            grantImageToKnownVisualApps(context, imageUri);

            if (tryOpenImageWithPackage(context, imageUri, "com.google.ar.lens", true)) return true;
            if (tryOpenImageWithPackage(context, imageUri, "com.google.ar.lens", false)) return true;

            if (tryOpenImageWithPackage(context, imageUri, "com.google.android.apps.search.lens", true)) return true;
            if (tryOpenImageWithPackage(context, imageUri, "com.google.android.apps.search.lens", false)) return true;

            if (tryOpenImageWithPackage(context, imageUri, "com.google.android.apps.photos", true)) return true;
            if (tryOpenImageWithPackage(context, imageUri, "com.google.android.apps.photos", false)) return true;

            if (tryOpenImageChooser(context, imageUri)) return true;
        }

        openProductSearch(context, strictQuery);
        return true;
    }

    private static void grantImageToKnownVisualApps(Context context, Uri imageUri) {
        if (context == null || imageUri == null) {
            return;
        }
        String[] packages = new String[] {
                "com.google.ar.lens",
                "com.google.android.apps.search.lens",
                "com.google.android.googlequicksearchbox",
                "com.google.android.apps.photos"
        };
        for (int i = 0; i < packages.length; i++) {
            try {
                context.grantUriPermission(packages[i], imageUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {
            }
        }
    }

    private static Uri createLensImageUri(Context context, Bitmap bitmap) {
        if (context == null || bitmap == null) {
            return null;
        }
        return createCacheImageUri(context, bitmap);
    }

    private static Uri createCacheImageUri(Context context, Bitmap bitmap) {
        FileOutputStream output = null;
        try {
            File imageFile = JarvisImageShareProvider.createImageFile(context, "jarvis_lens_product");
            if (imageFile == null) {
                return null;
            }
            output = new FileOutputStream(imageFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 94, output);
            output.flush();
            return JarvisImageShareProvider.getUriForFile(imageFile);
        } catch (Exception ignored) {
            return null;
        } finally {
            if (output != null) {
                try {
                    output.close();
                } catch (Exception ignoredAgain) {
                }
            }
        }
    }

    private static void allowFileUriExposureForOldAideBuilds() {
        try {
            StrictMode.class.getMethod("disableDeathOnFileUriExposure", new Class[0]).invoke(null, new Object[0]);
        } catch (Exception ignored) {
        }
    }

    private static boolean tryOpenImageWithPackage(Context context, Uri imageUri, String packageName, boolean sendMode) {
        if (context == null || imageUri == null || packageName == null) {
            return false;
        }
        try {
            Intent intent;
            if (sendMode) {
                intent = new Intent(Intent.ACTION_SEND);
                intent.setType("image/*");
                intent.putExtra(Intent.EXTRA_STREAM, imageUri);
                intent.putExtra("android.intent.extra.STREAM", imageUri);
            } else {
                intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(imageUri, "image/*");
            }
            intent.setPackage(packageName);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try {
                intent.setClipData(ClipData.newUri(context.getContentResolver(), "Jarvis product image", imageUri));
            } catch (Exception ignoredClip) {
            }
            try {
                context.grantUriPermission(packageName, imageUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignoredGrant) {
            }
            context.startActivity(intent);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean tryOpenImageChooser(Context context, Uri imageUri) {
        try {
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("image/*");
            send.putExtra(Intent.EXTRA_STREAM, imageUri);
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try {
                send.setClipData(ClipData.newUri(context.getContentResolver(), "Jarvis product image", imageUri));
            } catch (Exception ignoredClip) {
            }
            Intent chooser = Intent.createChooser(send, "Choose Lens / visual image search");
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(chooser);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean tryOpenLensUri(Context context, String uriText) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uriText));
            intent.setPackage("com.google.android.googlequicksearchbox");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String buildStrictVisualSearchQuery(Bitmap bitmap, String fallbackQuery) {
        String query = fallbackQuery == null ? "" : fallbackQuery.trim();
        String guess = bitmap == null ? "" : guessKnownProduct(bitmap);
        if (guess != null && guess.length() > 0) {
            query = guess;
        }
        if (query.length() == 0 || query.toLowerCase(Locale.UK).indexOf("unknown") >= 0) {
            query = "item in camera photo";
        }
        return query + " exact object product brand model visual match photo";
    }

    public static void openProductSearch(Context context, String query) {
        if (context == null) {
            return;
        }
        try {
            String search = query == null || query.trim().length() == 0 ? "unknown product from camera photo" : query.trim();
            String lower = search.toLowerCase(Locale.UK);
            String finalQuery = search;
            if (lower.indexOf("identify exact") < 0 && lower.indexOf("exact product") < 0) {
                finalQuery = search + " identify exact object product model brand photo";
            }
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?tbm=isch&q=" + Uri.encode(finalQuery)));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception ignored) {
        }
    }

    private static final class ProductStats {
        float darkRatio;
        float greenRatio;
        float redRatio;
        float blueRatio;
        float orangeRatio;
        float whiteRatio;
        float edgeRatio;
        float foregroundRatio;
        float leftLowerDarkRatio;
        float rightLowerDarkRatio;
        float middleLowerDarkRatio;
        float upperMiddleDarkRatio;
        float centreDarkRatio;
        boolean hasForeground;
        int boxWidth;
        int boxHeight;
        float boxAspect;
        float boxAreaRatio;
    }

    private static final class SceneStats {
        String lightName;
        String dominantName;
        String edgeLabel;
        String edgeMeaning;
        float edgeValue;
    }
}
