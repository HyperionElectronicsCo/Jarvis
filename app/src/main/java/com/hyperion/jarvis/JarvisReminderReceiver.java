package com.hyperion.jarvis;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;

public class JarvisReminderReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "jarvis_reminders_channel";

    public void onReceive(Context context, Intent intent) {
        String text = intent == null ? "Reminder" : intent.getStringExtra(JarvisReminderManager.EXTRA_TEXT);
        if (text == null || text.length() == 0) {
            text = "Reminder";
        }
        showNotification(context, text);
    }

    private void showNotification(Context context, String text) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 26 && manager.getNotificationChannel(CHANNEL_ID) == null) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Jarvis Reminders", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Alarms and reminders created by Jarvis");
            manager.createNotificationChannel(channel);
        }
        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 8071, openIntent, flags);
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= 26) {
            builder = new Notification.Builder(context, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(context);
        }
        builder.setSmallIcon(context.getApplicationInfo().icon);
        builder.setContentTitle("J.A.R.V.I.S Reminder");
        builder.setContentText(text);
        builder.setContentIntent(pendingIntent);
        builder.setAutoCancel(true);
        builder.setShowWhen(true);
        builder.setPriority(Notification.PRIORITY_HIGH);
        if (Build.VERSION.SDK_INT >= 21) {
            builder.setColor(Color.rgb(0, 216, 255));
            builder.setCategory(Notification.CATEGORY_REMINDER);
        }
        manager.notify((int) (System.currentTimeMillis() % 2147483000), builder.build());
    }
}
