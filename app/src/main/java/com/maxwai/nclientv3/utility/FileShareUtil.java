package com.maxwai.nclientv3.utility;

import android.content.Context;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public final class FileShareUtil {
    private FileShareUtil() {
    }

    private static boolean isSameOrChildOf(@NonNull File child, @NonNull File parent) throws IOException {
        String childPath = child.getCanonicalPath();
        String parentPath = parent.getCanonicalPath();
        if (childPath.equals(parentPath)) return true;
        if (!parentPath.endsWith(File.separator)) parentPath = parentPath + File.separator;
        return childPath.startsWith(parentPath);
    }

    private static void copyFile(@NonNull File src, @NonNull File dst) throws IOException {
        File parent = dst.getParentFile();
        if (parent != null && !parent.exists()) {
            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }
        try (FileInputStream in = new FileInputStream(src);
             FileOutputStream out = new FileOutputStream(dst, false)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
        }
    }

    /**
     * Ensures {@code file} is inside an app-scoped directory covered by the app's FileProvider paths.
     * If not, copies it into the app cache and returns the copied file.
     */
    @NonNull
    public static File ensureShareableCopy(@NonNull Context context, @NonNull File file) throws IOException {
        context = context.getApplicationContext();
        File canonical = file.getCanonicalFile();

        File cacheDir = context.getCacheDir().getCanonicalFile();
        if (isSameOrChildOf(canonical, cacheDir)) return canonical;

        File filesDir = context.getFilesDir().getCanonicalFile();
        if (isSameOrChildOf(canonical, filesDir)) return canonical;

        File extFiles = context.getExternalFilesDir(null);
        if (extFiles != null) {
            File canonicalExt = extFiles.getCanonicalFile();
            if (isSameOrChildOf(canonical, canonicalExt)) return canonical;
        }

        File sharedDir = new File(context.getCacheDir(), "shared");
        File dest = new File(sharedDir, canonical.getName());
        copyFile(canonical, dest);
        return dest;
    }
}

