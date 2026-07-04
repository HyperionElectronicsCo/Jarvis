package com.hyperion.jarvis;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JarvisReminderManager {
    public static final String ACTION_REMINDER = "com.hyperion.jarvis.REMINDER_ALARM";
    public static final String EXTRA_TEXT = "text";
    public static final String EXTRA_ID = "id";
    private static final String PREF_REMINDERS = "jarvis_reminders_blob";
    private static final String RECORD_SEP = "\u001E";
    private static final String FIELD_SEP = "\u001F";

    private JarvisReminderManager() {
    }

    public static String handleReminderCommand(Context context, String command, String lower, JarvisOutput output) {
        if (lower == null) {
            return null;
        }
        if (lower.indexOf("show reminders") >= 0 || lower.indexOf("list reminders") >= 0 || lower.indexOf("what reminders") >= 0) {
            return listReminders(context);
        }
        boolean wantsReminder = lower.indexOf("remind me") >= 0 || lower.indexOf("set reminder") >= 0 || lower.indexOf("create reminder") >= 0;
        boolean wantsAlarm = lower.indexOf("set alarm") >= 0 || lower.indexOf("wake me") >= 0 || lower.startsWith("alarm ");
        if (!wantsReminder && !wantsAlarm) {
            return null;
        }

        long timeMillis = parseTimeMillis(lower);
        if (timeMillis <= 0) {
            return "I can set that, but I need a time. Try: remind me to check the oven in 10 minutes, or set alarm for 7:30.";
        }
        String task = wantsAlarm ? "Alarm" : extractReminderText(command, lower);
        if (task.length() == 0) {
            task = wantsAlarm ? "Alarm" : "Reminder";
        }
        schedule(context, task, timeMillis);
        SimpleDateFormat format = new SimpleDateFormat("EEE HH:mm", Locale.UK);
        return (wantsAlarm ? "Alarm" : "Reminder") + " set for " + format.format(new java.util.Date(timeMillis)) + ": " + task + ".";
    }

    public static void schedule(Context context, String text, long timeMillis) {
        long id = System.currentTimeMillis();
        Intent intent = new Intent(context, JarvisReminderReceiver.class);
        intent.setAction(ACTION_REMINDER);
        intent.putExtra(EXTRA_TEXT, text);
        intent.putExtra(EXTRA_ID, id);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, (int) (id % 2147483000), intent, flags);
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            if (Build.VERSION.SDK_INT >= 23) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMillis, pendingIntent);
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, timeMillis, pendingIntent);
            }
        }
        saveReminder(context, id, timeMillis, text);
    }

    private static long parseTimeMillis(String lower) {
        Calendar calendar = Calendar.getInstance();
        Matcher relative = Pattern.compile("in\\s+(\\d+)\\s*(minute|minutes|min|mins|hour|hours|hr|hrs|day|days)").matcher(lower);
        if (relative.find()) {
            int amount = Integer.parseInt(relative.group(1));
            String unit = relative.group(2);
            if (unit.startsWith("min")) {
                calendar.add(Calendar.MINUTE, amount);
            } else if (unit.startsWith("hour") || unit.startsWith("hr")) {
                calendar.add(Calendar.HOUR_OF_DAY, amount);
            } else {
                calendar.add(Calendar.DAY_OF_YEAR, amount);
            }
            return calendar.getTimeInMillis();
        }
        Matcher clock = Pattern.compile("(?:at|for)\\s+(\\d{1,2})(?::|\\s)(\\d{2})(?:\\s*(am|pm))?").matcher(lower);
        if (clock.find()) {
            int hour = Integer.parseInt(clock.group(1));
            int minute = Integer.parseInt(clock.group(2));
            String ampm = clock.group(3);
            if (ampm != null) {
                if (ampm.equals("pm") && hour < 12) hour += 12;
                if (ampm.equals("am") && hour == 12) hour = 0;
            }
            calendar.set(Calendar.HOUR_OF_DAY, hour);
            calendar.set(Calendar.MINUTE, minute);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
                calendar.add(Calendar.DAY_OF_YEAR, 1);
            }
            return calendar.getTimeInMillis();
        }
        Matcher hourOnly = Pattern.compile("(?:at|for)\\s+(\\d{1,2})\\s*(am|pm)").matcher(lower);
        if (hourOnly.find()) {
            int hour = Integer.parseInt(hourOnly.group(1));
            String ampm = hourOnly.group(2);
            if (ampm.equals("pm") && hour < 12) hour += 12;
            if (ampm.equals("am") && hour == 12) hour = 0;
            calendar.set(Calendar.HOUR_OF_DAY, hour);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
                calendar.add(Calendar.DAY_OF_YEAR, 1);
            }
            return calendar.getTimeInMillis();
        }
        return -1;
    }

    private static String extractReminderText(String command, String lower) {
        String result = command;
        String[] starts = new String[] { "remind me to ", "set reminder to ", "create reminder to ", "reminder to ", "remind me " };
        for (int i = 0; i < starts.length; i++) {
            int index = lower.indexOf(starts[i]);
            if (index >= 0) {
                result = command.substring(index + starts[i].length()).trim();
                break;
            }
        }
        int inIndex = result.toLowerCase(Locale.UK).lastIndexOf(" in ");
        if (inIndex > 0) {
            result = result.substring(0, inIndex).trim();
        }
        int atIndex = result.toLowerCase(Locale.UK).lastIndexOf(" at ");
        if (atIndex > 0) {
            result = result.substring(0, atIndex).trim();
        }
        return result;
    }

    private static void saveReminder(Context context, long id, long timeMillis, String text) {
        SharedPreferences prefs = context.getSharedPreferences(JarvisCommandCenter.PREFS_NAME, Context.MODE_PRIVATE);
        String old = prefs.getString(PREF_REMINDERS, "");
        String record = id + FIELD_SEP + timeMillis + FIELD_SEP + clean(text);
        prefs.edit().putString(PREF_REMINDERS, old == null || old.length() == 0 ? record : old + RECORD_SEP + record).commit();
    }

    public static String listReminders(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(JarvisCommandCenter.PREFS_NAME, Context.MODE_PRIVATE);
        String blob = prefs.getString(PREF_REMINDERS, "");
        if (blob == null || blob.length() == 0) {
            return "No reminders are currently stored.";
        }
        String[] records = blob.split(RECORD_SEP);
        SimpleDateFormat format = new SimpleDateFormat("EEE HH:mm", Locale.UK);
        StringBuilder builder = new StringBuilder("Stored reminders: ");
        int count = 0;
        for (int i = records.length - 1; i >= 0 && count < 6; i--) {
            String[] fields = records[i].split(FIELD_SEP, -1);
            if (fields.length >= 3) {
                try {
                    long when = Long.parseLong(fields[1]);
                    if (count > 0) builder.append("; ");
                    builder.append(format.format(new java.util.Date(when))).append(" - ").append(fields[2]);
                    count++;
                } catch (Exception ignored) {
                }
            }
        }
        if (count == 0) {
            return "No readable reminders are currently stored.";
        }
        return builder.toString();
    }

    private static String clean(String text) {
        return text == null ? "" : text.replace(RECORD_SEP, " ").replace(FIELD_SEP, " ").replace('\n', ' ').replace('\r', ' ').trim();
    }
}
