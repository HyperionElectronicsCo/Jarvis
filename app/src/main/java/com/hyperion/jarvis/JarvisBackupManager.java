package com.hyperion.jarvis;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class JarvisBackupManager {
    public static final int REQUEST_STORAGE_PERMISSION = 6424;
    private static final String BACKUP_ENTRY_NAME = "jarvisbackup.jvs";
    private static final String BACKUP_PREFIX = "Jarvis_";

    private JarvisBackupManager() {
    }

    public static boolean hasStoragePermission(Context context) {
        if (context == null) {
            return false;
        }
        if (Build.VERSION.SDK_INT < 23) {
            return true;
        }
        try {
            return context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static void requestStoragePermission(Activity activity) {
        if (activity == null || Build.VERSION.SDK_INT < 23) {
            return;
        }
        try {
            activity.requestPermissions(new String[] {
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, REQUEST_STORAGE_PERMISSION);
        } catch (Exception ignored) {
        }
    }

    public static String createBackup(Context context) {
        if (context == null) {
            return "Backup failed: no Android context available.";
        }
        if (!hasStoragePermission(context)) {
            if (context instanceof Activity) {
                requestStoragePermission((Activity) context);
            }
            return "Storage permission is required before Jarvis can write a backup to Downloads. Allow storage permission, then run backup again.";
        }
        try {
            File downloadDir = getDownloadDirectory();
            if (!downloadDir.exists()) {
                downloadDir.mkdirs();
            }
            if (!downloadDir.exists()) {
                return "Backup failed: could not open the Downloads folder.";
            }
            File target = buildBackupFile(downloadDir);
            ZipOutputStream zip = null;
            try {
                zip = new ZipOutputStream(new FileOutputStream(target));
                ZipEntry entry = new ZipEntry(BACKUP_ENTRY_NAME);
                entry.setTime(System.currentTimeMillis());
                zip.putNextEntry(entry);
                byte[] data = JarvisOnlineBrain.buildBackupText(context).getBytes("UTF-8");
                zip.write(data);
                zip.closeEntry();
            } finally {
                if (zip != null) {
                    try {
                        zip.close();
                    } catch (Exception ignored) {
                    }
                }
            }
            return "Jarvis configuration backup saved to Downloads as " + target.getName() + ". It contains " + BACKUP_ENTRY_NAME + ".";
        } catch (Exception error) {
            return "Backup failed: " + safeMessage(error);
        }
    }

    public static String restoreLatestBackup(Context context) {
        if (context == null) {
            return "Restore failed: no Android context available.";
        }
        if (!hasStoragePermission(context)) {
            if (context instanceof Activity) {
                requestStoragePermission((Activity) context);
            }
            return "Storage permission is required before Jarvis can read backups from Downloads. Allow storage permission, then run restore again.";
        }
        try {
            File downloadDir = getDownloadDirectory();
            if (!downloadDir.exists()) {
                return "Restore failed: Downloads folder was not found.";
            }
            File[] files = downloadDir.listFiles();
            if (files == null || files.length == 0) {
                return "No Jarvis backup zip was found in Downloads.";
            }
            File bestFile = null;
            String bestText = null;
            long bestTime = -1L;
            for (int i = 0; i < files.length; i++) {
                File file = files[i];
                if (file == null || !file.isFile()) {
                    continue;
                }
                String name = file.getName();
                String low = name == null ? "" : name.toLowerCase(Locale.UK);
                if (!low.endsWith(".zip") || low.indexOf("jarvis") < 0) {
                    continue;
                }
                String text = readBackupTextFromZip(file);
                if (text != null && text.length() > 0) {
                    long modified = file.lastModified();
                    if (bestFile == null || modified > bestTime) {
                        bestFile = file;
                        bestText = text;
                        bestTime = modified;
                    }
                }
            }
            if (bestFile == null || bestText == null) {
                return "No Jarvis backup containing " + BACKUP_ENTRY_NAME + " was found in Downloads.";
            }
            String result = JarvisOnlineBrain.restoreBackupText(context, bestText);
            return "Restored backup from " + bestFile.getName() + ". " + result;
        } catch (Exception error) {
            return "Restore failed: " + safeMessage(error);
        }
    }

    private static File getDownloadDirectory() {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
    }

    private static File buildBackupFile(File downloadDir) {
        String date = new SimpleDateFormat("dd-MM-yyyy", Locale.UK).format(new Date());
        File target = new File(downloadDir, BACKUP_PREFIX + date + ".zip");
        if (!target.exists()) {
            return target;
        }
        for (int i = 2; i < 1000; i++) {
            File alternate = new File(downloadDir, BACKUP_PREFIX + date + "_" + i + ".zip");
            if (!alternate.exists()) {
                return alternate;
            }
        }
        return new File(downloadDir, BACKUP_PREFIX + date + "_" + System.currentTimeMillis() + ".zip");
    }

    private static String readBackupTextFromZip(File file) {
        ZipInputStream zip = null;
        try {
            zip = new ZipInputStream(new FileInputStream(file));
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (name != null && name.equalsIgnoreCase(BACKUP_ENTRY_NAME)) {
                    return readAll(zip);
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (zip != null) {
                try {
                    zip.close();
                } catch (Exception ignoredAgain) {
                }
            }
        }
        return null;
    }

    private static String readAll(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = input.read(buffer)) >= 0) {
            if (read > 0) {
                output.write(buffer, 0, read);
            }
        }
        return new String(output.toByteArray(), "UTF-8");
    }

    private static String safeMessage(Throwable error) {
        if (error == null) {
            return "unknown error";
        }
        String message = error.getMessage();
        if (message == null || message.trim().length() == 0) {
            return error.getClass().getSimpleName();
        }
        return message;
    }
}
