package com.hyperion.jarvis;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

public final class JarvisImageStore {
    private static final String PREF_IMAGE_NAME = "pending_image_name_v32";
    private static final String PREF_IMAGE_MIME = "pending_image_mime_v32";
    private static final String IMAGE_FILE_NAME = "pending_generated_image_v32.bin";

    public static final class PendingImage {
        public final byte[] data;
        public final String mimeType;
        public final String suggestedFileName;

        public PendingImage(byte[] data, String mimeType, String suggestedFileName) {
            this.data = data;
            this.mimeType = mimeType;
            this.suggestedFileName = suggestedFileName;
        }
    }

    private JarvisImageStore() {
    }

    public static void savePendingImage(Context context, byte[] data, String mimeType, String suggestedFileName) {
        if (context == null || data == null || data.length == 0) {
            return;
        }
        FileOutputStream output = null;
        try {
            output = new FileOutputStream(new File(context.getFilesDir(), IMAGE_FILE_NAME));
            output.write(data);
            output.flush();
            SharedPreferences prefs = context.getSharedPreferences(JarvisCommandCenter.PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit()
                    .putString(PREF_IMAGE_MIME, mimeType == null || mimeType.length() == 0 ? "image/png" : mimeType)
                    .putString(PREF_IMAGE_NAME, suggestedFileName == null || suggestedFileName.length() == 0 ? "jarvis_image.png" : suggestedFileName)
                    .commit();
        } catch (Exception ignored) {
        } finally {
            if (output != null) {
                try {
                    output.close();
                } catch (Exception ignoredAgain) {
                }
            }
        }
    }

    public static PendingImage readPendingImage(Context context) {
        if (context == null) {
            return null;
        }
        File file = new File(context.getFilesDir(), IMAGE_FILE_NAME);
        if (!file.exists() || !file.isFile() || file.length() <= 0L) {
            return null;
        }
        byte[] data = readAll(file);
        if (data == null || data.length == 0) {
            return null;
        }
        SharedPreferences prefs = context.getSharedPreferences(JarvisCommandCenter.PREFS_NAME, Context.MODE_PRIVATE);
        String mimeType = prefs.getString(PREF_IMAGE_MIME, "image/png");
        String suggestedName = prefs.getString(PREF_IMAGE_NAME, "jarvis_image.png");
        return new PendingImage(data, mimeType, suggestedName);
    }

    public static void clearPendingImage(Context context) {
        if (context == null) {
            return;
        }
        try {
            File file = new File(context.getFilesDir(), IMAGE_FILE_NAME);
            if (file.exists()) {
                file.delete();
            }
        } catch (Exception ignored) {
        }
        SharedPreferences prefs = context.getSharedPreferences(JarvisCommandCenter.PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().remove(PREF_IMAGE_MIME).remove(PREF_IMAGE_NAME).commit();
    }

    private static byte[] readAll(File file) {
        InputStream input = null;
        try {
            input = new FileInputStream(file);
            int length = (int) file.length();
            byte[] data = new byte[length];
            int offset = 0;
            int read;
            while (offset < length && (read = input.read(data, offset, length - offset)) > 0) {
                offset += read;
            }
            if (offset <= 0) {
                return null;
            }
            if (offset == length) {
                return data;
            }
            byte[] smaller = new byte[offset];
            System.arraycopy(data, 0, smaller, 0, offset);
            return smaller;
        } catch (Exception ignored) {
            return null;
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (Exception ignoredAgain) {
                }
            }
        }
    }
}
