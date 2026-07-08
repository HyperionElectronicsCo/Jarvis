package com.hyperion.jarvis;

import android.content.Context;
import android.graphics.Bitmap;

public interface JarvisVisionBridge {
    JarvisVisionBridgeResult identify(Context context, Bitmap bitmap, boolean productMode);
}
