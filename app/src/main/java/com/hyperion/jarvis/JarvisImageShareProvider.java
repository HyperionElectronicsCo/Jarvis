package com.hyperion.jarvis;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

public class JarvisImageShareProvider extends ContentProvider {
    public static final String AUTHORITY = "com.hyperion.jarvis.imageprovider";
    private static final String ROOT = "images";
    private static final UriMatcher MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
    private static final int MATCH_IMAGE = 1;

    static {
        MATCHER.addURI(AUTHORITY, ROOT + "/*", MATCH_IMAGE);
    }

    public boolean onCreate() {
        return true;
    }

    public static File getImageDirectory(Context context) {
        File dir = new File(context.getCacheDir(), "jarvis_shared_images");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public static File createImageFile(Context context, String prefix) {
        try {
            File dir = getImageDirectory(context);
            String safePrefix = prefix == null || prefix.length() == 0 ? "jarvis_image" : prefix;
            safePrefix = safePrefix.replaceAll("[^a-zA-Z0-9_]+", "_");
            return File.createTempFile(safePrefix + "_", ".jpg", dir);
        } catch (IOException error) {
            return null;
        }
    }

    public static Uri getUriForFile(File file) {
        if (file == null) {
            return null;
        }
        return Uri.parse("content://" + AUTHORITY + "/" + ROOT + "/" + Uri.encode(file.getName()));
    }

    private File getFileForUri(Uri uri) {
        if (uri == null || MATCHER.match(uri) != MATCH_IMAGE) {
            return null;
        }
        String name = uri.getLastPathSegment();
        if (name == null || name.length() == 0 || name.indexOf("..") >= 0 || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0) {
            return null;
        }
        Context context = getContext();
        if (context == null) {
            return null;
        }
        return new File(getImageDirectory(context), name);
    }

    public String getType(Uri uri) {
        return "image/jpeg";
    }

    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File file = getFileForUri(uri);
        if (file == null) {
            throw new FileNotFoundException("Invalid Jarvis image URI.");
        }
        int accessMode = ParcelFileDescriptor.MODE_READ_ONLY;
        if (mode != null && (mode.indexOf('w') >= 0 || mode.indexOf('t') >= 0 || mode.indexOf('+') >= 0)) {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            accessMode = ParcelFileDescriptor.MODE_READ_WRITE | ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE;
        }
        return ParcelFileDescriptor.open(file, accessMode);
    }

    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        File file = getFileForUri(uri);
        if (file == null) {
            return null;
        }
        String[] columns = projection;
        if (columns == null || columns.length == 0) {
            columns = new String[] { OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE };
        }
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        Object[] values = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) {
                values[i] = file.getName();
            } else if (OpenableColumns.SIZE.equals(columns[i])) {
                values[i] = Long.valueOf(file.exists() ? file.length() : 0L);
            } else {
                values[i] = null;
            }
        }
        cursor.addRow(values);
        return cursor;
    }

    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    public int delete(Uri uri, String selection, String[] selectionArgs) {
        File file = getFileForUri(uri);
        if (file != null && file.exists() && file.delete()) {
            return 1;
        }
        return 0;
    }

    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
