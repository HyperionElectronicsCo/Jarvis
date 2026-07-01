package com.hyperion.jarvis;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class JarvisBootReceiver extends BroadcastReceiver {
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) {
            return;
        }
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || "android.intent.action.QUICKBOOT_POWERON".equals(action)) {
            if (JarvisCommandCenter.isBackgroundEnabled(context) && !JarvisCommandCenter.isBackgroundPaused(context) && JarvisCommandCenter.hasOverlayPermission(context)) {
                JarvisCommandCenter.startBackgroundService(context);
            } else if (!JarvisCommandCenter.hasOverlayPermission(context)) {
                JarvisCommandCenter.setBackgroundEnabled(context, false);
            }
        }
    }
}
