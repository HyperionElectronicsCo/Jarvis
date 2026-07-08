package com.hyperion.jarvis;

import android.content.Context;
import android.graphics.Bitmap;

public final class JarvisLocalVisionBridge implements JarvisVisionBridge {
    public JarvisVisionBridgeResult identify(Context context, Bitmap bitmap, boolean productMode) {
        if (bitmap == null) {
            return JarvisVisionBridgeResult.unavailable("Local vision", "No image was available.");
        }
        String label = JarvisVisionEngine.guessKnownProduct(bitmap);
        String details = JarvisVisionEngine.buildProductRecognitionHint(bitmap);
        if (label != null && label.length() > 0) {
            return JarvisVisionBridgeResult.recognised("Local vision", label, details, 0.55f);
        }
        return JarvisVisionBridgeResult.recognised("Local vision", "unknown object", details, 0.20f);
    }
}
