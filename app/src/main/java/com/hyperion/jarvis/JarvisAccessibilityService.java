package com.hyperion.jarvis;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;

public class JarvisAccessibilityService extends AccessibilityService {
    private static JarvisAccessibilityService activeService;
    private Handler handler;

    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
    }

    protected void onServiceConnected() {
        super.onServiceConnected();
        activeService = this;
    }

    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    public void onInterrupt() {
    }

    public void onDestroy() {
        if (activeService == this) {
            activeService = null;
        }
        super.onDestroy();
    }

    public static boolean isActive() {
        return activeService != null;
    }

    public static boolean performBack() {
        JarvisAccessibilityService service = activeService;
        if (service == null) {
            return false;
        }
        return service.performGlobalAction(GLOBAL_ACTION_BACK);
    }

    public static boolean performRecents() {
        JarvisAccessibilityService service = activeService;
        if (service == null) {
            return false;
        }
        return service.performGlobalAction(GLOBAL_ACTION_RECENTS);
    }

    public static boolean performHome(Context context) {
        JarvisAccessibilityService service = activeService;
        if (service != null) {
            return service.performGlobalAction(GLOBAL_ACTION_HOME);
        }
        launchHomeFallback(context);
        return false;
    }

    public static boolean performCloseCurrentApp() {
        JarvisAccessibilityService service = activeService;
        if (service == null) {
            return false;
        }
        service.performGlobalAction(GLOBAL_ACTION_BACK);
        service.postGlobalAction(GLOBAL_ACTION_BACK, 350);
        service.postGlobalAction(GLOBAL_ACTION_BACK, 700);
        service.postGlobalAction(GLOBAL_ACTION_HOME, 1100);
        return true;
    }

    private void postGlobalAction(final int action, long delay) {
        if (handler == null) {
            handler = new Handler(Looper.getMainLooper());
        }
        handler.postDelayed(new Runnable() {
            public void run() {
                try {
                    performGlobalAction(action);
                } catch (Exception ignored) {
                }
            }
        }, delay);
    }

    private static void launchHomeFallback(Context context) {
        if (context == null) {
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception ignored) {
        }
    }
}
