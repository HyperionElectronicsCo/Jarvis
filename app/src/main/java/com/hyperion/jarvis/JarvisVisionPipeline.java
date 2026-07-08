package com.hyperion.jarvis;

import android.content.Context;
import android.graphics.Bitmap;

public final class JarvisVisionPipeline {
    private JarvisVisionPipeline() {
    }

    public static JarvisVisionBridgeResult identify(Context context, Bitmap bitmap, boolean productMode) {
        JarvisVisionBridgeResult ml = new JarvisMlKitVisionBridge().identify(context, bitmap, productMode);
        if (isStrongMlKitProductResult(ml, productMode)) {
            return ml;
        }

        JarvisVisionBridgeResult lensApi = new JarvisLensApiBridge().identify(context, bitmap, productMode);
        if (lensApi != null && lensApi.hasUsefulLabel()) {
            return lensApi;
        }

        if (ml != null && ml.hasUsefulLabel()) {
            return ml;
        }

        JarvisVisionBridgeResult local = new JarvisLocalVisionBridge().identify(context, bitmap, productMode);
        if (local == null) {
            local = JarvisVisionBridgeResult.unavailable("Vision pipeline", "No local vision result was available.");
        }
        if (ml != null && ml.details != null && ml.details.length() > 0) {
            if (local.details == null) {
                local.details = "";
            }
            local.details = local.details + "\nML Kit status: " + ml.details;
        }
        if (lensApi != null && lensApi.details != null && lensApi.details.length() > 0) {
            if (local.details == null) {
                local.details = "";
            }
            local.details = local.details + "\nLens API status: " + lensApi.details;
        }
        return local;
    }

    private static boolean isStrongMlKitProductResult(JarvisVisionBridgeResult result, boolean productMode) {
        if (!productMode || result == null || !result.hasUsefulLabel()) {
            return false;
        }
        String engine = result.engineName == null ? "" : result.engineName.toLowerCase();
        String label = result.label == null ? "" : result.label.toLowerCase();
        if (engine.indexOf("ocr") >= 0 || engine.indexOf("barcode") >= 0) {
            return true;
        }
        if (result.confidence >= 0.78f) {
            return true;
        }
        if (label.indexOf("irn") >= 0 || label.indexOf("pepsi") >= 0 || label.indexOf("coca") >= 0 || label.indexOf("monster") >= 0
                || label.indexOf("red bull") >= 0 || label.indexOf("sprite") >= 0 || label.indexOf("fanta") >= 0) {
            return true;
        }
        return false;
    }
}
