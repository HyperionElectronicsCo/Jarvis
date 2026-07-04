package com.hyperion.jarvis;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class JarvisMemoryStore {
    private static final String PREF_FACTS = "jarvis_facts_blob";
    private static final String RECORD_SEP = "\u001E";
    private static final String FIELD_SEP = "\u001F";

    private JarvisMemoryStore() {
    }

    public static boolean isPersonalFact(String fact) {
        String lower = " " + (fact == null ? "" : fact.toLowerCase(Locale.UK).trim()) + " ";
        if (lower.indexOf(" my ") >= 0 || lower.indexOf(" me ") >= 0 || lower.indexOf(" mine ") >= 0 || lower.indexOf(" our ") >= 0) {
            return true;
        }
        if (lower.indexOf(" i am ") >= 0 || lower.indexOf(" i'm ") >= 0 || lower.indexOf(" i live ") >= 0 || lower.indexOf(" i like ") >= 0 || lower.indexOf(" i love ") >= 0 || lower.indexOf(" i have ") >= 0 || lower.indexOf(" i prefer ") >= 0 || lower.indexOf(" i want ") >= 0) {
            return true;
        }
        return false;
    }

    public static String savePersonalFact(Context context, String fact) {
        saveFact(context, "personal", fact, "user supplied personal fact");
        return "Understood. I have stored that personal fact locally.";
    }

    public static String saveVerifiedFact(Context context, String fact, String source) {
        saveFact(context, "source_checked", fact, source == null ? "source checked" : source);
        return "I found a supporting source and stored the fact locally.";
    }

    public static String savePendingFact(Context context, String fact, String source) {
        saveFact(context, "pending", fact, source == null ? "pending manual verification" : source);
        return "I stored that as pending verification.";
    }

    public static void saveFact(Context context, String type, String fact, String source) {
        if (context == null || fact == null || fact.trim().length() == 0) {
            return;
        }
        SharedPreferences prefs = context.getSharedPreferences(JarvisCommandCenter.PREFS_NAME, Context.MODE_PRIVATE);
        String oldBlob = prefs.getString(PREF_FACTS, "");
        String record = System.currentTimeMillis() + FIELD_SEP + clean(type) + FIELD_SEP + clean(fact.trim()) + FIELD_SEP + clean(source);
        String newBlob = oldBlob == null || oldBlob.length() == 0 ? record : oldBlob + RECORD_SEP + record;
        prefs.edit().putString(PREF_FACTS, trimToLastRecords(newBlob, 60)).commit();
    }

    public static String listFacts(Context context) {
        String[] records = getRecords(context);
        if (records.length == 0) {
            return "I do not have any stored facts yet.";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("Here is what I currently remember: ");
        int count = 0;
        for (int i = records.length - 1; i >= 0 && count < 8; i--) {
            String[] fields = splitRecord(records[i]);
            if (fields.length >= 4) {
                if (count > 0) {
                    builder.append("; ");
                }
                builder.append(fields[2]);
                builder.append(" [").append(fields[1]).append("]");
                count++;
            }
        }
        return builder.toString();
    }

    public static String findFacts(Context context, String query) {
        String search = query == null ? "" : query.toLowerCase(Locale.UK).trim();
        if (search.length() == 0) {
            return listFacts(context);
        }
        String[] records = getRecords(context);
        StringBuilder builder = new StringBuilder();
        int count = 0;
        for (int i = records.length - 1; i >= 0 && count < 8; i--) {
            String[] fields = splitRecord(records[i]);
            if (fields.length >= 4) {
                String factLower = fields[2].toLowerCase(Locale.UK);
                String sourceLower = fields[3].toLowerCase(Locale.UK);
                if (factLower.indexOf(search) >= 0 || sourceLower.indexOf(search) >= 0) {
                    if (count == 0) {
                        builder.append("I found: ");
                    } else {
                        builder.append("; ");
                    }
                    builder.append(fields[2]).append(" [").append(fields[1]).append("]");
                    count++;
                }
            }
        }
        if (count == 0) {
            return "I could not find a stored fact matching " + query + ".";
        }
        return builder.toString();
    }

    public static String forgetFactsMatching(Context context, String query) {
        String search = query == null ? "" : query.toLowerCase(Locale.UK).trim();
        if (search.length() == 0) {
            return "Tell me which fact to forget.";
        }
        String[] records = getRecords(context);
        StringBuilder kept = new StringBuilder();
        int removed = 0;
        for (int i = 0; i < records.length; i++) {
            String[] fields = splitRecord(records[i]);
            boolean remove = false;
            if (fields.length >= 3) {
                remove = fields[2].toLowerCase(Locale.UK).indexOf(search) >= 0;
            }
            if (remove) {
                removed++;
            } else if (records[i].trim().length() > 0) {
                if (kept.length() > 0) {
                    kept.append(RECORD_SEP);
                }
                kept.append(records[i]);
            }
        }
        SharedPreferences prefs = context.getSharedPreferences(JarvisCommandCenter.PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(PREF_FACTS, kept.toString()).commit();
        if (removed == 0) {
            return "I could not find a matching stored fact to forget.";
        }
        return "Forgot " + removed + " matching fact" + (removed == 1 ? "." : "s.");
    }

    private static String[] getRecords(Context context) {
        if (context == null) {
            return new String[0];
        }
        SharedPreferences prefs = context.getSharedPreferences(JarvisCommandCenter.PREFS_NAME, Context.MODE_PRIVATE);
        String blob = prefs.getString(PREF_FACTS, "");
        if (blob == null || blob.length() == 0) {
            return new String[0];
        }
        return blob.split(RECORD_SEP);
    }

    private static String[] splitRecord(String record) {
        if (record == null) {
            return new String[0];
        }
        return record.split(FIELD_SEP, -1);
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        return value.replace(RECORD_SEP, " ").replace(FIELD_SEP, " ").replace('\n', ' ').replace('\r', ' ').trim();
    }

    private static String trimToLastRecords(String blob, int maxRecords) {
        if (blob == null || blob.length() == 0) {
            return "";
        }
        String[] records = blob.split(RECORD_SEP);
        if (records.length <= maxRecords) {
            return blob;
        }
        StringBuilder builder = new StringBuilder();
        int start = records.length - maxRecords;
        for (int i = start; i < records.length; i++) {
            if (builder.length() > 0) {
                builder.append(RECORD_SEP);
            }
            builder.append(records[i]);
        }
        return builder.toString();
    }
}
