package com.maxwai.nclientv3.async;

import android.content.Context;

import com.maxwai.nclientv3.api.InspectorV3;
import com.maxwai.nclientv3.api.components.Gallery;
import com.maxwai.nclientv3.api.enums.SpecialTagIds;
import com.maxwai.nclientv3.api.local.LocalGallery;
import com.maxwai.nclientv3.async.database.Queries;
import com.maxwai.nclientv3.settings.Database;
import com.maxwai.nclientv3.settings.Global;
import com.maxwai.nclientv3.utility.LogUtility;

import java.io.File;

public class MetadataFetcher implements Runnable {
    private final Context context;

    public MetadataFetcher(Context context) {
        this.context = context;
    }

    @Override
    public void run() {
        File downloads = Global.DOWNLOADFOLDER;
        if (downloads == null) return;
        File[] files = downloads.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (!f.isDirectory()) continue;
            LocalGallery lg = new LocalGallery(f, false);
            if (lg.getId() == SpecialTagIds.INVALID_ID || lg.hasGalleryData()) continue;
            InspectorV3 inspector = InspectorV3.galleryInspector(context, lg.getId(), null);
            //noinspection CallToThreadRun
            inspector.run();//it is run, not start
            if (inspector.getGalleries() == null || inspector.getGalleries().isEmpty())
                continue;
            Gallery g = (Gallery) inspector.getGalleries().get(0);
            Database.runOnReadyAsync(context, () -> {
                try {
                    Queries.GalleryTable.insert(g);
                } catch (Throwable t) {
                    LogUtility.e("Error saving metadata to DB", t);
                }
            });
        }
    }
}
