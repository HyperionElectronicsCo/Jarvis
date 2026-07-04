package com.hyperion.jarvis;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class JarvisAccessibilityService extends AccessibilityService {
    private static JarvisAccessibilityService activeService;
    private static boolean youtubeAutoTapPending;
    private static long youtubeAutoTapUntil;
    private static String youtubeAutoTapQuery;

    private Handler handler;

    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
    }

    protected void onServiceConnected() {
        super.onServiceConnected();
        activeService = this;
        if (handler == null) {
            handler = new Handler(Looper.getMainLooper());
        }
    }

    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (youtubeAutoTapPending) {
            tryAutoTapYouTubeResult(false);
        }
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

    public static boolean scheduleYouTubeFirstResultTap(String query) {
        JarvisAccessibilityService service = activeService;
        if (service == null) {
            return false;
        }
        youtubeAutoTapPending = true;
        youtubeAutoTapQuery = query == null ? "" : query;
        youtubeAutoTapUntil = System.currentTimeMillis() + 15000;
        service.postYouTubeAttempt(900);
        service.postYouTubeAttempt(1800);
        service.postYouTubeAttempt(3000);
        service.postYouTubeAttempt(4800);
        service.postYouTubeAttempt(7000);
        return true;
    }

    private void postYouTubeAttempt(long delay) {
        if (handler == null) {
            handler = new Handler(Looper.getMainLooper());
        }
        handler.postDelayed(new Runnable() {
            public void run() {
                tryAutoTapYouTubeResult(true);
            }
        }, delay);
    }

    private void tryAutoTapYouTubeResult(boolean scheduledAttempt) {
        if (!youtubeAutoTapPending) {
            return;
        }
        if (System.currentTimeMillis() > youtubeAutoTapUntil) {
            youtubeAutoTapPending = false;
            youtubeAutoTapQuery = null;
            return;
        }
        AccessibilityNodeInfo root = null;
        try {
            root = getRootInActiveWindow();
            if (root == null) {
                return;
            }
            String packageName = root.getPackageName() == null ? "" : root.getPackageName().toString();
            if (!isYouTubePackage(packageName)) {
                return;
            }
            AccessibilityNodeInfo best = findBestYouTubeResultNode(root);
            if (best != null && clickNodeOrParent(best)) {
                youtubeAutoTapPending = false;
                youtubeAutoTapQuery = null;
            }
        } catch (Exception ignored) {
        } finally {
            try {
                if (root != null) {
                    root.recycle();
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static boolean isYouTubePackage(String packageName) {
        if (packageName == null) {
            return false;
        }
        String value = packageName.toLowerCase(Locale.UK);
        return value.indexOf("youtube") >= 0 || value.indexOf("revanced") >= 0;
    }

    private static AccessibilityNodeInfo findBestYouTubeResultNode(AccessibilityNodeInfo root) {
        ArrayList<AccessibilityNodeInfo> clickable = new ArrayList<AccessibilityNodeInfo>();
        collectClickableNodes(root, clickable, 0);
        AccessibilityNodeInfo best = null;
        int bestScore = -9999;
        for (int i = 0; i < clickable.size(); i++) {
            AccessibilityNodeInfo node = clickable.get(i);
            int score = scoreYouTubeCandidate(node);
            if (score > bestScore) {
                bestScore = score;
                best = node;
            }
        }
        if (best != null && bestScore >= 70) {
            return best;
        }
        return null;
    }

    private static void collectClickableNodes(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> output, int depth) {
        if (node == null || depth > 10) {
            return;
        }
        try {
            if (node.isClickable() && node.isVisibleToUser()) {
                output.add(AccessibilityNodeInfo.obtain(node));
            }
            int count = node.getChildCount();
            for (int i = 0; i < count; i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) {
                    collectClickableNodes(child, output, depth + 1);
                    child.recycle();
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static int scoreYouTubeCandidate(AccessibilityNodeInfo node) {
        if (node == null) {
            return -9999;
        }
        String text = collectText(node, 0).toLowerCase(Locale.UK);
        android.graphics.Rect bounds = new android.graphics.Rect();
        try {
            node.getBoundsInScreen(bounds);
        } catch (Exception ignored) {
        }
        int score = 0;
        if (text.indexOf("views") >= 0 || text.indexOf("view") >= 0) {
            score += 90;
        }
        if (text.indexOf("years ago") >= 0 || text.indexOf("months ago") >= 0 || text.indexOf("weeks ago") >= 0 || text.indexOf("days ago") >= 0 || text.indexOf("hours ago") >= 0) {
            score += 55;
        }
        if (text.indexOf("playlist") >= 0 || text.indexOf("videos") >= 0 || text.indexOf("mix") >= 0) {
            score -= 80;
        }
        if (text.indexOf("subscriptions") >= 0 || text.indexOf("notifications") >= 0 || text.indexOf("home") >= 0 || text.indexOf("you") >= 0) {
            score -= 120;
        }
        if (text.indexOf("search") >= 0 && text.length() < 80) {
            score -= 90;
        }
        if (bounds.top < 120) {
            score -= 80;
        }
        if (bounds.top > 160 && bounds.top < 950) {
            score += 45;
        }
        if (bounds.height() > 110) {
            score += 20;
        }
        if (bounds.width() > 260) {
            score += 15;
        }
        if (text.length() > 20) {
            score += 20;
        }
        String query = youtubeAutoTapQuery == null ? "" : youtubeAutoTapQuery.toLowerCase(Locale.UK);
        if (query.length() > 0 && text.indexOf(query) >= 0) {
            score += 35;
        }
        return score;
    }

    private static String collectText(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > 6) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        try {
            CharSequence text = node.getText();
            CharSequence description = node.getContentDescription();
            if (text != null) {
                builder.append(text).append(' ');
            }
            if (description != null) {
                builder.append(description).append(' ');
            }
            int count = node.getChildCount();
            for (int i = 0; i < count; i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) {
                    builder.append(collectText(child, depth + 1)).append(' ');
                    child.recycle();
                }
            }
        } catch (Exception ignored) {
        }
        return builder.toString();
    }

    private static boolean clickNodeOrParent(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = null;
        try {
            current = AccessibilityNodeInfo.obtain(node);
            while (current != null) {
                if (current.isClickable() && current.isVisibleToUser()) {
                    boolean clicked = current.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    if (clicked) {
                        return true;
                    }
                }
                AccessibilityNodeInfo parent = current.getParent();
                current.recycle();
                current = parent;
            }
        } catch (Exception ignored) {
        } finally {
            try {
                if (current != null) {
                    current.recycle();
                }
            } catch (Exception ignored) {
            }
        }
        return false;
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
