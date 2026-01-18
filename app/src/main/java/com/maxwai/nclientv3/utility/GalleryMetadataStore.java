package com.maxwai.nclientv3.utility;

import android.content.Context;
import android.util.JsonReader;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.maxwai.nclientv3.api.components.Gallery;
import com.maxwai.nclientv3.api.components.GalleryData;
import com.maxwai.nclientv3.api.components.GenericGallery;
import com.maxwai.nclientv3.api.enums.TitleType;
import com.maxwai.nclientv3.async.database.Queries;
import com.maxwai.nclientv3.components.classes.Size;
import com.maxwai.nclientv3.files.GalleryFolder;
import com.maxwai.nclientv3.settings.Database;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

public final class GalleryMetadataStore {
    public static final String SIDECAR_FILE_NAME = ".gallery.json";

    private GalleryMetadataStore() {
    }

    @NonNull
    public static File sidecarFile(@NonNull File folder) {
        return new File(folder, SIDECAR_FILE_NAME);
    }

    public static void writeSidecar(@NonNull File folder, @NonNull Gallery gallery) throws IOException {
        File sidecar = sidecarFile(folder);
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(sidecar, false), StandardCharsets.UTF_8)) {
            gallery.jsonWrite(writer);
        }
    }

    /**
     * Attempts to import gallery metadata for a downloaded folder from local files only:
     * - Preferred: {@link #SIDECAR_FILE_NAME}
     * - Legacy: {@code .nomedia} if it contains JSON (migrated to the sidecar; .nomedia is truncated afterwards).
     *
     * @return true if metadata was imported into the DB.
     */
    public static boolean importFromLocalFiles(@NonNull Context context, @NonNull File folder) {
        File sidecar = sidecarFile(folder);
        if (sidecar.isFile() && sidecar.length() > 0) {
            return importJsonToDb(context, sidecar);
        }

        File legacyNoMedia = new File(folder, ".nomedia");
        if (legacyNoMedia.isFile() && legacyNoMedia.length() > 0) {
            boolean imported = importJsonToDb(context, legacyNoMedia);
            if (imported) {
                try {
                    copyFile(legacyNoMedia, sidecar);
                } catch (IOException ignore) {
                }
                truncateFile(legacyNoMedia);
            }
            return imported;
        }

        return false;
    }

    private static boolean importJsonToDb(@NonNull Context context, @NonNull File jsonFile) {
        GalleryData data;
        try {
            Database.ensureInitialized(context);
            data = readGalleryData(jsonFile);
        } catch (Throwable t) {
            LogUtility.e("Error reading local gallery metadata JSON", t);
            return false;
        }
        if (data == null || data.getId() <= 0) return false;
        try {
            Queries.GalleryTable.insert(new PersistableGallery(data));
            return true;
        } catch (Throwable t) {
            LogUtility.e("Error importing local gallery metadata into DB", t);
            return false;
        }
    }

    @Nullable
    private static GalleryData readGalleryData(@NonNull File jsonFile) throws IOException {
        try (JsonReader reader = new JsonReader(new InputStreamReader(new FileInputStream(jsonFile), StandardCharsets.UTF_8))) {
            return new GalleryData(reader);
        }
    }

    private static void truncateFile(@NonNull File file) {
        try (FileOutputStream out = new FileOutputStream(file, false)) {
            out.flush();
        } catch (IOException ignore) {
        }
    }

    private static void copyFile(@NonNull File from, @NonNull File to) throws IOException {
        byte[] buffer = new byte[16 * 1024];
        try (FileInputStream in = new FileInputStream(from); FileOutputStream out = new FileOutputStream(to, false)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }

    /**
     * Lightweight {@link GenericGallery} adapter so we can reuse {@link Queries.GalleryTable#insert(GenericGallery)}.
     * Sizes are unknown for JSON-only restores; they can be updated later via {@link Queries.GalleryTable#updateSizes(Gallery)}.
     */
    private static final class PersistableGallery extends GenericGallery {
        private final GalleryData data;
        private final Size zero = new Size(0, 0);

        private PersistableGallery(@NonNull GalleryData data) {
            this.data = data;
        }

        @Override
        public int getId() {
            return data.getId();
        }

        @Override
        public Type getType() {
            return Type.COMPLETE;
        }

        @Override
        public int getPageCount() {
            return data.getPageCount();
        }

        @Override
        public boolean isValid() {
            return data.isValid();
        }

        @NonNull
        @Override
        public String getTitle() {
            String pretty = data.getTitle(TitleType.PRETTY);
            if (pretty != null && !pretty.isEmpty()) return pretty;
            String english = data.getTitle(TitleType.ENGLISH);
            if (english != null && !english.isEmpty()) return english;
            String japanese = data.getTitle(TitleType.JAPANESE);
            return japanese == null ? "" : japanese;
        }

        @Override
        public Size getMaxSize() {
            return zero;
        }

        @Override
        public Size getMinSize() {
            return zero;
        }

        @Override
        public GalleryFolder getGalleryFolder() {
            return null;
        }

        @Override
        public boolean hasGalleryData() {
            return true;
        }

        @Override
        public GalleryData getGalleryData() {
            return data;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(android.os.Parcel dest, int flags) {
            // Not used: this object is only for DB insert.
        }
    }
}

