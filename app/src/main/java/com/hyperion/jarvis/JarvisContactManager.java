package com.hyperion.jarvis;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract;
import android.telephony.SmsManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class JarvisContactManager {
    private static final String PREFS = "jarvis_contact_learning";
    private static final String PREF_PENDING = "pending_contact_candidates";
    private static final String PREF_LAST_QUERY = "last_contact_query";
    private static final int REQ_CONTACT_CALL_PERMS = 5458;
    private static final String ACTION_CALL = "call";
    private static final String ACTION_TEXT = "text";
    private static final String PREF_PENDING_ACTION = "pending_contact_action";
    private static final String PREF_PENDING_MESSAGE = "pending_contact_message";
    private static final String PREF_PENDING_SMS_NAME = "pending_sms_name";
    private static final String PREF_PENDING_SMS_NUMBER = "pending_sms_number";
    private static final String PREF_PENDING_SMS_BODY = "pending_sms_body";
    private static final String PREF_PENDING_SMS_QUERY = "pending_sms_query";

    private JarvisContactManager() {
    }

    public static String handleCallCommand(Context context, String rawTarget, JarvisOutput output) {
        String target = cleanTarget(rawTarget);
        if (target.length() == 0) {
            return "Who should I call? Say call followed by a contact name or phone number.";
        }
        if (isPermissionRequest(target)) {
            requestPermissionsIfPossible(context);
            return "Jarvis needs Contacts and Phone permission for instant calling. I opened the permission request if Android allows it.";
        }
        PendingChoice choice = readPendingChoice(context, target);
        if (choice != null) {
            directCall(context, choice.number, output);
            learnSuccessfulCall(context, choice.query, choice.name, choice.number);
            clearPending(context);
            return "Calling " + choice.name + ".";
        }
        if (looksLikePhoneNumber(target)) {
            directCall(context, target, output);
            rememberNumberUse(context, target);
            return "Calling " + target + ".";
        }
        if (!hasReadContactsPermission(context)) {
            requestPermissionsIfPossible(context);
            return "I need Contacts permission to find " + target + ". Grant Contacts permission, then say call " + target + " again.";
        }
        ArrayList<ContactCandidate> contacts = loadContacts(context);
        if (contacts.size() == 0) {
            return "I could not read any contacts. Check Contacts permission, then try again.";
        }
        ArrayList<ContactCandidate> matches = rankMatches(context, target, contacts);
        if (matches.size() == 0) {
            return "I could not find a contact close to " + target + ". Try saying the full contact name again.";
        }
        ContactCandidate first = matches.get(0);
        ContactCandidate second = matches.size() > 1 ? matches.get(1) : null;
        if (shouldCallImmediately(first, second)) {
            directCall(context, first.number, output);
            learnSuccessfulCall(context, target, first.displayName, first.number);
            clearPending(context);
            return "Calling " + first.displayName + ".";
        }
        storePending(context, target, matches);
        return buildAmbiguousPrompt(target, matches);
    }

    public static String handleTextCommand(Context context, String command, String lower, JarvisOutput output) {
        TextRequest request = parseTextRequest(context, command, lower);
        if (request == null || request.target == null || request.target.length() == 0) {
            return "Who should I text? Say text followed by a contact name, or say text mum saying your message.";
        }
        String target = cleanTarget(request.target);
        String message = request.message == null ? "" : request.message.trim();
        if (target.length() == 0) {
            return "Who should I text? Say text followed by a contact name.";
        }
        if (isPermissionRequest(target)) {
            requestPermissionsIfPossible(context);
            return "Jarvis needs Contacts and SMS permission to find and send texts. I opened the permission request if Android allows it.";
        }
        PendingChoice choice = readPendingChoice(context, lower == null ? command : lower);
        if (choice != null && ACTION_TEXT.equals(choice.action)) {
            clearPending(context);
            return prepareSmsForConfirmation(context, choice.query, choice.name, choice.number, choice.message, output);
        }
        if (looksLikePhoneNumber(target)) {
            rememberTextNumberUse(context, target);
            return prepareSmsForConfirmation(context, target, target, target, message, output);
        }
        if (!hasReadContactsPermission(context)) {
            requestPermissionsIfPossible(context);
            return "I need Contacts permission to find " + target + ". Grant Contacts permission, then say text " + target + " again.";
        }
        ArrayList<ContactCandidate> contacts = loadContacts(context);
        if (contacts.size() == 0) {
            return "I could not read any contacts. Check Contacts permission, then try again.";
        }
        if (message.length() == 0) {
            TextRequest better = inferTextRequestFromContactPrefix(context, target, contacts);
            if (better != null && better.target != null && better.target.length() > 0) {
                target = better.target;
                message = better.message == null ? "" : better.message;
            }
        }
        ArrayList<ContactCandidate> matches = rankMatches(context, target, contacts);
        if (matches.size() == 0) {
            return "I could not find a contact close to " + target + ". Try saying the full contact name again.";
        }
        ContactCandidate first = matches.get(0);
        ContactCandidate second = matches.size() > 1 ? matches.get(1) : null;
        if (shouldCallImmediately(first, second)) {
            clearPending(context);
            return prepareSmsForConfirmation(context, target, first.displayName, first.number, message, output);
        }
        storePending(context, target, matches, ACTION_TEXT, message);
        return buildAmbiguousPrompt(target, matches, ACTION_TEXT);
    }

    public static boolean hasPendingSms(Context context) {
        if (context == null) {
            return false;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return prefs.getString(PREF_PENDING_SMS_NUMBER, "").length() > 0;
    }

    public static boolean isSmsConfirmationResponse(Context context, String lower) {
        if (!hasPendingSms(context) || lower == null) {
            return false;
        }
        String value = normalizeName(lower);
        if (value.length() == 0) {
            return false;
        }
        if (isSmsYes(value) || isSmsNo(value) || isSmsDiscard(value) || isSmsEdit(value)) {
            return true;
        }
        if (extractSmsReplacement(lower).length() > 0) {
            return true;
        }
        String body = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(PREF_PENDING_SMS_BODY, "");
        return body == null || body.trim().length() == 0;
    }

    public static String handlePendingSmsResponse(Context context, String command, String lower, JarvisOutput output) {
        if (!hasPendingSms(context)) {
            return null;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String name = prefs.getString(PREF_PENDING_SMS_NAME, "that contact");
        String number = prefs.getString(PREF_PENDING_SMS_NUMBER, "");
        String body = prefs.getString(PREF_PENDING_SMS_BODY, "");
        String query = prefs.getString(PREF_PENDING_SMS_QUERY, name);
        String spoken = command == null ? "" : command.trim();
        String value = normalizeName(lower == null ? spoken : lower);

        if (isSmsDiscard(value)) {
            clearPendingSms(context);
            return "Text discarded.";
        }
        if (isSmsEdit(value)) {
            storePendingSms(context, name, number, "", query);
            return "Okay, what should the text to " + name + " say?";
        }
        String replacement = extractSmsReplacement(spoken);
        if (replacement.length() > 0) {
            storePendingSms(context, name, number, replacement, query);
            return buildSmsConfirmationPrompt(name, replacement);
        }
        if (body == null || body.trim().length() == 0) {
            if (isSmsNo(value)) {
                clearPendingSms(context);
                return "Text discarded.";
            }
            String newBody = stripLeadingMessageFillers(spoken);
            if (newBody.length() == 0) {
                return "What should the text to " + name + " say?";
            }
            storePendingSms(context, name, number, newBody, query);
            return buildSmsConfirmationPrompt(name, newBody);
        }
        if (isSmsYes(value)) {
            boolean sent = sendSmsDirect(context, number, body, output);
            if (sent) {
                learnSuccessfulText(context, query, name, number);
                clearPendingSms(context);
                return "Sent your text to " + name + ".";
            }
            return "I need SMS permission to send that directly. I requested permission if Android allows it. After granting it, say yes send.";
        }
        if (isSmsNo(value)) {
            return "Okay. Say edit to change the message, or discard to cancel it.";
        }
        return buildSmsConfirmationPrompt(name, body);
    }

    public static boolean isFollowUpSelection(String lower) {
        if (lower == null) {
            return false;
        }
        String cleaned = lower.trim();
        return cleaned.equals("first one") || cleaned.equals("second one") || cleaned.equals("third one")
                || cleaned.equals("the first one") || cleaned.equals("the second one") || cleaned.equals("the third one")
                || cleaned.startsWith("call option ") || cleaned.startsWith("text option ")
                || cleaned.startsWith("message option ") || cleaned.startsWith("send option ")
                || cleaned.startsWith("option ")
                || cleaned.startsWith("call number ") || cleaned.startsWith("text number ")
                || cleaned.startsWith("message number ") || cleaned.startsWith("number ");
    }

    public static String handleFollowUpSelection(Context context, String command, JarvisOutput output) {
        PendingChoice choice = readPendingChoice(context, command);
        if (choice == null) {
            return "I do not have a contact choice waiting. Say call or text followed by the contact name again.";
        }
        if (ACTION_TEXT.equals(choice.action)) {
            clearPending(context);
            return prepareSmsForConfirmation(context, choice.query, choice.name, choice.number, choice.message, output);
        }
        directCall(context, choice.number, output);
        learnSuccessfulCall(context, choice.query, choice.name, choice.number);
        clearPending(context);
        return "Calling " + choice.name + ".";
    }

    private static boolean shouldCallImmediately(ContactCandidate first, ContactCandidate second) {
        if (first == null) {
            return false;
        }
        if (first.score >= 145) {
            return true;
        }
        if (first.score >= 118 && (second == null || first.score - second.score >= 28)) {
            return true;
        }
        return false;
    }

    private static String buildAmbiguousPrompt(String query, ArrayList<ContactCandidate> matches) {
        return buildAmbiguousPrompt(query, matches, ACTION_CALL);
    }

    private static String buildAmbiguousPrompt(String query, ArrayList<ContactCandidate> matches, String action) {
        StringBuilder builder = new StringBuilder();
        builder.append("I heard ").append(query).append(", but I found similar contacts. ");
        int limit = Math.min(4, matches.size());
        for (int i = 0; i < limit; i++) {
            ContactCandidate c = matches.get(i);
            if (i > 0) {
                builder.append("; ");
            }
            builder.append(i + 1).append(ordinalSuffix(i + 1)).append(" ").append(c.displayName);
        }
        if (ACTION_TEXT.equals(action)) {
            builder.append(". Say text option 1, option 2, or say the contact name again.");
        } else {
            builder.append(". Say call option 1, option 2, or say the contact name again.");
        }
        return builder.toString();
    }

    private static String ordinalSuffix(int number) {
        if (number == 1) return "st";
        if (number == 2) return "nd";
        if (number == 3) return "rd";
        return "th";
    }

    private static void directCall(Context context, String number, JarvisOutput output) {
        String clean = number == null ? "" : number.trim();
        if (clean.length() == 0) {
            return;
        }
        Uri uri = Uri.parse("tel:" + Uri.encode(clean));
        if (hasCallPermission(context)) {
            try {
                Intent intent = new Intent(Intent.ACTION_CALL, uri);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                return;
            } catch (Exception error) {
                log(output, "CALL: Direct call failed, falling back to dialler: " + error.getMessage());
            }
        } else {
            requestPermissionsIfPossible(context);
            log(output, "CALL: CALL_PHONE permission not granted, opening dialler fallback.");
        }
        try {
            Intent dial = new Intent(Intent.ACTION_DIAL, uri);
            dial.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(dial);
        } catch (Exception error) {
            log(output, "CALL: Unable to open phone app: " + error.getMessage());
        }
    }

    private static String prepareSmsForConfirmation(Context context, String query, String name, String number, String message, JarvisOutput output) {
        String who = name == null || name.length() == 0 ? "that contact" : name;
        String body = message == null ? "" : message.trim();
        storePendingSms(context, who, number, body, query == null ? who : query);
        if (body.length() == 0) {
            return "What should the text to " + who + " say?";
        }
        return buildSmsConfirmationPrompt(who, body);
    }

    private static void storePendingSms(Context context, String name, String number, String body, String query) {
        if (context == null) {
            return;
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(PREF_PENDING_SMS_NAME, name == null ? "that contact" : name)
                .putString(PREF_PENDING_SMS_NUMBER, number == null ? "" : number)
                .putString(PREF_PENDING_SMS_BODY, body == null ? "" : body)
                .putString(PREF_PENDING_SMS_QUERY, query == null ? "" : query)
                .apply();
    }

    private static void clearPendingSms(Context context) {
        if (context == null) {
            return;
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .remove(PREF_PENDING_SMS_NAME)
                .remove(PREF_PENDING_SMS_NUMBER)
                .remove(PREF_PENDING_SMS_BODY)
                .remove(PREF_PENDING_SMS_QUERY)
                .apply();
    }

    private static String buildSmsConfirmationPrompt(String name, String message) {
        String who = name == null || name.length() == 0 ? "that contact" : name;
        String body = message == null ? "" : message.trim();
        if (body.length() == 0) {
            return "What should the text to " + who + " say?";
        }
        return "Ready to send this SMS to " + who + ": \"" + body + "\". Say yes to send, edit to change it, or discard to cancel.";
    }

    private static boolean sendSmsDirect(Context context, String number, String message, JarvisOutput output) {
        String clean = number == null ? "" : number.trim();
        String body = message == null ? "" : message.trim();
        if (context == null || clean.length() == 0 || body.length() == 0) {
            return false;
        }
        if (!hasSendSmsPermission(context)) {
            requestPermissionsIfPossible(context);
            log(output, "SMS: SEND_SMS permission not granted yet.");
            return false;
        }
        try {
            SmsManager manager = SmsManager.getDefault();
            ArrayList<String> parts = manager.divideMessage(body);
            if (parts != null && parts.size() > 1) {
                manager.sendMultipartTextMessage(clean, null, parts, null, null);
            } else {
                manager.sendTextMessage(clean, null, body, null, null);
            }
            log(output, "SMS: Sent direct SMS to " + clean + ".");
            return true;
        } catch (Exception error) {
            log(output, "SMS: Direct send failed: " + error.getMessage());
            return false;
        }
    }

    private static boolean isSmsYes(String value) {
        if (value == null) return false;
        String v = normalizeName(value);
        return v.equals("yes") || v.equals("yeah") || v.equals("yep") || v.equals("correct")
                || v.equals("send") || v.equals("send it") || v.equals("send text")
                || v.equals("send message") || v.equals("yes send") || v.equals("yes send it")
                || v.equals("that is correct") || v.equals("thats correct") || v.equals("ok send")
                || v.equals("okay send") || v.equals("confirm") || v.equals("confirmed");
    }

    private static boolean isSmsNo(String value) {
        if (value == null) return false;
        String v = normalizeName(value);
        return v.equals("no") || v.equals("nope") || v.equals("not correct") || v.equals("wrong")
                || v.equals("that is wrong") || v.equals("thats wrong") || v.equals("incorrect");
    }

    private static boolean isSmsDiscard(String value) {
        if (value == null) return false;
        String v = normalizeName(value);
        return v.equals("discard") || v.equals("cancel") || v.equals("cancel it")
                || v.equals("delete") || v.equals("delete it") || v.equals("forget it")
                || v.equals("stop") || v.equals("dont send") || v.equals("do not send");
    }

    private static boolean isSmsEdit(String value) {
        if (value == null) return false;
        String v = normalizeName(value);
        return v.equals("edit") || v.equals("edit it") || v.equals("change") || v.equals("change it")
                || v.equals("correct it") || v.equals("rewrite") || v.equals("rewrite it");
    }

    private static String extractSmsReplacement(String command) {
        if (command == null) {
            return "";
        }
        String value = command.trim();
        String lower = value.toLowerCase(Locale.UK);
        String[] prefixes = new String[] {
                "change it to ", "change message to ", "change the message to ",
                "edit it to ", "edit message to ", "edit the message to ",
                "make it say ", "make the message say ", "message should say ",
                "replace it with ", "say instead ", "no say ", "no make it say "
        };
        for (int i = 0; i < prefixes.length; i++) {
            if (lower.startsWith(prefixes[i])) {
                return value.substring(prefixes[i].length()).trim();
            }
        }
        return "";
    }

    private static String stripLeadingMessageFillers(String command) {
        if (command == null) {
            return "";
        }
        String value = command.trim();
        String lower = value.toLowerCase(Locale.UK);
        String[] prefixes = new String[] { "say ", "message saying ", "text saying ", "tell them ", "tell her ", "tell him " };
        for (int i = 0; i < prefixes.length; i++) {
            if (lower.startsWith(prefixes[i])) {
                return value.substring(prefixes[i].length()).trim();
            }
        }
        return value;
    }

    private static boolean hasReadContactsPermission(Context context) {
        if (context == null) return false;
        if (Build.VERSION.SDK_INT < 23) return true;
        return context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
    }

    private static boolean hasCallPermission(Context context) {
        if (context == null) return false;
        if (Build.VERSION.SDK_INT < 23) return true;
        return context.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED;
    }

    private static boolean hasSendSmsPermission(Context context) {
        if (context == null) return false;
        if (Build.VERSION.SDK_INT < 23) return true;
        return context.checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED;
    }

    private static void requestPermissionsIfPossible(Context context) {
        if (context instanceof Activity && Build.VERSION.SDK_INT >= 23) {
            Activity activity = (Activity) context;
            ArrayList<String> missing = new ArrayList<String>();
            if (activity.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                missing.add(Manifest.permission.READ_CONTACTS);
            }
            if (activity.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                missing.add(Manifest.permission.CALL_PHONE);
            }
            if (activity.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                missing.add(Manifest.permission.SEND_SMS);
            }
            if (missing.size() > 0) {
                activity.requestPermissions(missing.toArray(new String[missing.size()]), REQ_CONTACT_CALL_PERMS);
            }
        }
    }

    private static ArrayList<ContactCandidate> loadContacts(Context context) {
        ArrayList<ContactCandidate> list = new ArrayList<ContactCandidate>();
        if (context == null) return list;
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[] {
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                            ContactsContract.CommonDataKinds.Phone.NUMBER,
                            ContactsContract.CommonDataKinds.Phone.CONTACT_ID
                    },
                    null,
                    null,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC");
            if (cursor == null) return list;
            Set<String> seen = new HashSet<String>();
            while (cursor.moveToNext()) {
                String name = cursor.getString(0);
                String number = cursor.getString(1);
                String id = cursor.getString(2);
                if (name == null || number == null) continue;
                String cleanNumber = number.trim();
                if (cleanNumber.length() == 0) continue;
                String key = normalizeName(name) + "|" + normalizeNumber(cleanNumber);
                if (seen.contains(key)) continue;
                seen.add(key);
                ContactCandidate c = new ContactCandidate();
                c.displayName = name.trim();
                c.normalizedName = normalizeName(name);
                c.aliasName = expandFamilyAlias(c.normalizedName);
                c.number = cleanNumber;
                c.contactId = id == null ? "" : id;
                list.add(c);
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) {
                try { cursor.close(); } catch (Exception ignored) { }
            }
        }
        return list;
    }

    private static ArrayList<ContactCandidate> rankMatches(Context context, String query, ArrayList<ContactCandidate> contacts) {
        ArrayList<ContactCandidate> matches = new ArrayList<ContactCandidate>();
        String cleanQuery = normalizeName(query);
        String aliasQuery = expandFamilyAlias(cleanQuery);
        String storedTarget = getLearnedAlias(context, cleanQuery);
        for (int i = 0; i < contacts.size(); i++) {
            ContactCandidate c = contacts.get(i);
            int score = scoreContact(context, cleanQuery, aliasQuery, storedTarget, c);
            if (score >= 42) {
                c.score = score;
                matches.add(c);
            }
        }
        Collections.sort(matches, new Comparator<ContactCandidate>() {
            public int compare(ContactCandidate a, ContactCandidate b) {
                if (a.score == b.score) {
                    return a.displayName.compareToIgnoreCase(b.displayName);
                }
                return b.score - a.score;
            }
        });
        return matches;
    }

    private static int scoreContact(Context context, String query, String aliasQuery, String storedTarget, ContactCandidate c) {
        int score = 0;
        if (query.length() == 0 || c == null) return 0;
        if (storedTarget.length() > 0 && storedTarget.equals(normalizeNumber(c.number))) {
            score += 80;
        }
        if (c.normalizedName.equals(query)) score += 130;
        if (c.aliasName.equals(aliasQuery)) score += 120;
        if (c.normalizedName.startsWith(query) || query.startsWith(c.normalizedName)) score += 75;
        if (c.normalizedName.indexOf(query) >= 0 || query.indexOf(c.normalizedName) >= 0) score += 64;
        if (c.aliasName.indexOf(aliasQuery) >= 0 || aliasQuery.indexOf(c.aliasName) >= 0) score += 62;
        score += tokenScore(query, c.normalizedName);
        score += fuzzyScore(query, c.normalizedName);
        score += fuzzyScore(aliasQuery, c.aliasName);
        int interactionCount = getCallCount(context, c.number) + getTextCount(context, c.number);
        if (interactionCount > 0) {
            score += Math.min(65, 12 + interactionCount * 8);
        }
        return score;
    }

    private static int tokenScore(String query, String name) {
        String[] parts = query.split(" ");
        int score = 0;
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.length() < 2) continue;
            if (containsWord(name, part)) score += 24;
        }
        return score;
    }

    private static int fuzzyScore(String query, String name) {
        if (query == null || name == null || query.length() == 0 || name.length() == 0) return 0;
        int distance = levenshtein(query, name);
        int max = Math.max(query.length(), name.length());
        int similarity = max == 0 ? 0 : (100 - (distance * 100 / max));
        int phonetic = simplePhonetic(query).equals(simplePhonetic(name)) ? 24 : 0;
        if (similarity >= 86) return 54 + phonetic;
        if (similarity >= 76) return 38 + phonetic;
        if (similarity >= 66) return 24 + phonetic;
        if (phonetic > 0) return phonetic;
        return 0;
    }

    private static boolean containsWord(String haystack, String word) {
        if (haystack == null || word == null) return false;
        return (" " + haystack + " ").indexOf(" " + word + " ") >= 0;
    }

    private static String expandFamilyAlias(String value) {
        String v = normalizeName(value);
        if (v.equals("mum") || v.equals("mom") || v.equals("mam") || v.equals("mummy") || v.equals("mommy") || v.equals("mother") || v.equals("ma")) {
            return "mother mum mom mam mummy mommy ma";
        }
        if (v.equals("dad") || v.equals("daddy") || v.equals("father") || v.equals("pa") || v.equals("papa")) {
            return "father dad daddy papa pa";
        }
        if (v.equals("nan") || v.equals("nanna") || v.equals("grandma") || v.equals("granny")) {
            return "nan nanna grandma granny grandmother";
        }
        if (v.equals("grandad") || v.equals("granddad") || v.equals("grandpa")) {
            return "grandad granddad grandpa grandfather";
        }
        return v;
    }

    private static String normalizeName(String value) {
        if (value == null) return "";
        String lower = value.toLowerCase(Locale.UK).trim();
        lower = lower.replace('&', ' ');
        lower = lower.replaceAll("[^a-z0-9+]+", " ");
        lower = lower.replaceAll("\\s+", " ").trim();
        if (lower.startsWith("the ")) lower = lower.substring(4).trim();
        return lower;
    }

    private static String normalizeNumber(String value) {
        if (value == null) return "";
        return value.replaceAll("[^0-9+]", "");
    }

    private static String simplePhonetic(String value) {
        String v = normalizeName(value);
        if (v.length() == 0) return "";
        v = v.replace("ph", "f");
        v = v.replace("ck", "k");
        v = v.replace("q", "k");
        v = v.replace("x", "ks");
        v = v.replace("z", "s");
        v = v.replaceAll("[aeiou]", "");
        v = v.replaceAll("(.)\\1+", "$1");
        return v;
    }

    private static int levenshtein(String a, String b) {
        int[] costs = new int[b.length() + 1];
        for (int j = 0; j < costs.length; j++) costs[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            costs[0] = i;
            int nw = i - 1;
            for (int j = 1; j <= b.length(); j++) {
                int cj = Math.min(1 + Math.min(costs[j], costs[j - 1]), a.charAt(i - 1) == b.charAt(j - 1) ? nw : nw + 1);
                nw = costs[j];
                costs[j] = cj;
            }
        }
        return costs[b.length()];
    }

    private static TextRequest parseTextRequest(Context context, String command, String lower) {
        if (command == null) {
            return null;
        }
        String lowerValue = lower == null ? command.toLowerCase(Locale.UK) : lower;
        String rest = null;
        if (lowerValue.startsWith("text ")) {
            rest = command.substring(5).trim();
        } else if (lowerValue.startsWith("sms ")) {
            rest = command.substring(4).trim();
        } else if (lowerValue.startsWith("message ")) {
            rest = command.substring(8).trim();
        } else if (lowerValue.startsWith("send text to ")) {
            rest = command.substring(13).trim();
        } else if (lowerValue.startsWith("send message to ")) {
            rest = command.substring(16).trim();
        } else if (lowerValue.startsWith("send a text to ")) {
            rest = command.substring(15).trim();
        } else if (lowerValue.startsWith("send a message to ")) {
            rest = command.substring(18).trim();
        } else if (lowerValue.startsWith("send ") && (lowerValue.indexOf(" a message") > 0 || lowerValue.indexOf(" a text") > 0)) {
            rest = command.substring(5).trim();
        }
        if (rest == null) {
            return null;
        }
        rest = removeSmsAppPhrase(rest);
        TextRequest request = splitTextTargetAndMessage(rest);
        if (request.target == null || request.target.length() == 0) {
            request.target = rest.trim();
        }
        return request;
    }

    public static boolean looksLikeTextCommand(String lower) {
        if (lower == null) {
            return false;
        }
        String value = lower.trim();
        return value.startsWith("text ") || value.startsWith("sms ") || value.startsWith("message ")
                || value.startsWith("send text to ") || value.startsWith("send message to ")
                || value.startsWith("send a text to ") || value.startsWith("send a message to ")
                || (value.startsWith("send ") && (value.indexOf(" a message") > 0 || value.indexOf(" a text") > 0));
    }

    private static TextRequest splitTextTargetAndMessage(String rest) {
        TextRequest request = new TextRequest();
        String value = rest == null ? "" : rest.trim();
        String lower = value.toLowerCase(Locale.UK);
        String[] markers = new String[] {
                " saying ", " that says ", " with message ", " with the message ",
                " message saying ", " text saying ", " to say ", " and say "
        };
        for (int i = 0; i < markers.length; i++) {
            int index = lower.indexOf(markers[i]);
            if (index >= 0) {
                request.target = value.substring(0, index).trim();
                request.message = value.substring(index + markers[i].length()).trim();
                request.target = removeTrailingMessageWords(request.target);
                return request;
            }
        }
        int colon = value.indexOf(':');
        if (colon > 0) {
            request.target = value.substring(0, colon).trim();
            request.message = value.substring(colon + 1).trim();
            request.target = removeTrailingMessageWords(request.target);
            return request;
        }
        lower = value.toLowerCase(Locale.UK);
        int msgIndex = lower.indexOf(" a message");
        int textIndex = lower.indexOf(" a text");
        int useIndex = msgIndex >= 0 ? msgIndex : textIndex;
        if (useIndex > 0) {
            request.target = value.substring(0, useIndex).trim();
            request.message = "";
            return request;
        }
        request.target = value;
        request.message = "";
        return request;
    }

    private static TextRequest inferTextRequestFromContactPrefix(Context context, String rest, ArrayList<ContactCandidate> contacts) {
        if (context == null || rest == null || contacts == null) {
            return null;
        }
        String[] words = rest.trim().split("\\s+");
        if (words.length < 2) {
            return null;
        }
        TextRequest best = null;
        int bestScore = 0;
        int maxPrefix = Math.min(words.length - 1, 5);
        for (int i = 1; i <= maxPrefix; i++) {
            StringBuilder targetBuilder = new StringBuilder();
            for (int j = 0; j < i; j++) {
                if (j > 0) targetBuilder.append(' ');
                targetBuilder.append(words[j]);
            }
            String possibleTarget = targetBuilder.toString();
            ArrayList<ContactCandidate> matches = rankMatches(context, possibleTarget, contacts);
            if (matches.size() == 0) {
                continue;
            }
            int score = matches.get(0).score;
            if (score > bestScore && score >= 118) {
                StringBuilder msgBuilder = new StringBuilder();
                for (int j = i; j < words.length; j++) {
                    if (j > i) msgBuilder.append(' ');
                    msgBuilder.append(words[j]);
                }
                TextRequest candidate = new TextRequest();
                candidate.target = possibleTarget;
                candidate.message = msgBuilder.toString().trim();
                best = candidate;
                bestScore = score;
            }
        }
        return best;
    }

    private static String removeTrailingMessageWords(String target) {
        if (target == null) return "";
        String value = target.trim();
        String lower = value.toLowerCase(Locale.UK);
        if (lower.endsWith(" a message")) {
            return value.substring(0, value.length() - 10).trim();
        }
        if (lower.endsWith(" a text")) {
            return value.substring(0, value.length() - 7).trim();
        }
        if (lower.endsWith(" message")) {
            return value.substring(0, value.length() - 8).trim();
        }
        if (lower.endsWith(" text")) {
            return value.substring(0, value.length() - 5).trim();
        }
        return value;
    }

    private static String removeSmsAppPhrase(String value) {
        if (value == null) return "";
        String result = value.trim();
        String lower = result.toLowerCase(Locale.UK);
        String[] phrases = new String[] { " via text message", " via sms", " by text message", " by sms", " using text message", " using sms" };
        for (int i = 0; i < phrases.length; i++) {
            if (lower.endsWith(phrases[i])) {
                return result.substring(0, result.length() - phrases[i].length()).trim();
            }
        }
        return result;
    }

    private static String cleanTarget(String raw) {
        if (raw == null) return "";
        String target = raw.trim();
        target = target.replaceAll("^(the|my) ", "").trim();
        if (target.toLowerCase(Locale.UK).endsWith(" please")) {
            target = target.substring(0, target.length() - 7).trim();
        }
        return target;
    }

    private static boolean looksLikePhoneNumber(String target) {
        String n = normalizeNumber(target);
        return n.length() >= 3 && target.matches("[0-9+()\\-\\s]+");
    }

    private static boolean isPermissionRequest(String target) {
        String lower = target.toLowerCase(Locale.UK);
        return lower.indexOf("permission") >= 0 || lower.indexOf("permissions") >= 0;
    }

    private static void learnSuccessfulCall(Context context, String query, String name, String number) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String normalizedNumber = normalizeNumber(number);
        String normalizedQuery = normalizeName(query);
        int count = prefs.getInt("count_" + normalizedNumber, 0) + 1;
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt("count_" + normalizedNumber, count);
        editor.putString("alias_" + normalizedQuery, normalizedNumber);
        editor.putString("name_" + normalizedNumber, name == null ? "" : name);
        editor.apply();
    }

    private static void rememberNumberUse(Context context, String number) {
        learnSuccessfulCall(context, number, number, number);
    }

    private static void learnSuccessfulText(Context context, String query, String name, String number) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String normalizedNumber = normalizeNumber(number);
        String normalizedQuery = normalizeName(query);
        int count = prefs.getInt("text_count_" + normalizedNumber, 0) + 1;
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt("text_count_" + normalizedNumber, count);
        editor.putString("alias_" + normalizedQuery, normalizedNumber);
        editor.putString("name_" + normalizedNumber, name == null ? "" : name);
        editor.apply();
    }

    private static void rememberTextNumberUse(Context context, String number) {
        learnSuccessfulText(context, number, number, number);
    }

    private static int getCallCount(Context context, String number) {
        if (context == null) return 0;
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("count_" + normalizeNumber(number), 0);
    }

    private static int getTextCount(Context context, String number) {
        if (context == null) return 0;
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("text_count_" + normalizeNumber(number), 0);
    }

    private static String getLearnedAlias(Context context, String normalizedQuery) {
        if (context == null) return "";
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("alias_" + normalizedQuery, "");
    }

    private static void storePending(Context context, String query, ArrayList<ContactCandidate> matches) {
        storePending(context, query, matches, ACTION_CALL, "");
    }

    private static void storePending(Context context, String query, ArrayList<ContactCandidate> matches, String action, String message) {
        if (context == null || matches == null) return;
        StringBuilder builder = new StringBuilder();
        int limit = Math.min(5, matches.size());
        for (int i = 0; i < limit; i++) {
            ContactCandidate c = matches.get(i);
            if (i > 0) builder.append("\n");
            builder.append(escape(c.displayName)).append("|").append(escape(c.number));
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(PREF_PENDING, builder.toString())
                .putString(PREF_LAST_QUERY, query == null ? "" : query)
                .putString(PREF_PENDING_ACTION, action == null ? ACTION_CALL : action)
                .putString(PREF_PENDING_MESSAGE, message == null ? "" : message)
                .apply();
    }

    private static void clearPending(Context context) {
        if (context == null) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(PREF_PENDING).remove(PREF_LAST_QUERY).remove(PREF_PENDING_ACTION).remove(PREF_PENDING_MESSAGE).apply();
    }

    private static PendingChoice readPendingChoice(Context context, String command) {
        if (context == null || command == null) return null;
        int index = parseChoiceIndex(command);
        if (index < 0) return null;
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String pending = prefs.getString(PREF_PENDING, "");
        if (pending.length() == 0) return null;
        String[] rows = pending.split("\\n");
        if (index >= rows.length) return null;
        String[] parts = rows[index].split("\\|", 2);
        if (parts.length < 2) return null;
        PendingChoice choice = new PendingChoice();
        choice.name = unescape(parts[0]);
        choice.number = unescape(parts[1]);
        choice.query = prefs.getString(PREF_LAST_QUERY, "");
        choice.action = prefs.getString(PREF_PENDING_ACTION, ACTION_CALL);
        choice.message = prefs.getString(PREF_PENDING_MESSAGE, "");
        return choice;
    }

    private static int parseChoiceIndex(String command) {
        String lower = normalizeName(command);
        if (lower.equals("first one") || lower.equals("the first one")) return 0;
        if (lower.equals("second one") || lower.equals("the second one")) return 1;
        if (lower.equals("third one") || lower.equals("the third one")) return 2;
        String[] markers = new String[] { "call option ", "text option ", "message option ", "send option ", "option ", "call number ", "text number ", "message number ", "number " };
        for (int i = 0; i < markers.length; i++) {
            if (lower.startsWith(markers[i].trim())) {
                String marker = markers[i].trim();
                String rest = lower.substring(marker.length()).trim();
                if (rest.startsWith("one") || rest.equals("1")) return 0;
                if (rest.startsWith("two") || rest.equals("2")) return 1;
                if (rest.startsWith("three") || rest.equals("3")) return 2;
                if (rest.startsWith("four") || rest.equals("4")) return 3;
                if (rest.startsWith("five") || rest.equals("5")) return 4;
            }
        }
        return -1;
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("|", "\\p").replace("\n", " ");
    }

    private static String unescape(String value) {
        if (value == null) return "";
        return value.replace("\\p", "|").replace("\\\\", "\\");
    }

    private static void log(JarvisOutput output, String text) {
        if (output != null) {
            output.onConsole(text);
        }
    }

    private static final class ContactCandidate {
        String displayName;
        String normalizedName;
        String aliasName;
        String number;
        String contactId;
        int score;
    }

    private static final class PendingChoice {
        String name;
        String number;
        String query;
        String action;
        String message;
    }

    private static final class TextRequest {
        String target;
        String message;
    }
}
