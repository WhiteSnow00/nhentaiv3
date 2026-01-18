package com.maxwai.nclientv3.utility;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.widget.ImageView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.resource.bitmap.Rotate;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.ImageViewTarget;
import com.bumptech.glide.request.target.Target;
import com.maxwai.nclientv3.api.components.Gallery;
import com.maxwai.nclientv3.api.enums.ImageExt;
import com.maxwai.nclientv3.components.GlideX;
import com.maxwai.nclientv3.settings.Global;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

public class ImageDownloadUtility {

    private static final Map<Gallery, Deque<Runnable>> imageDownloadQueue =
        Collections.synchronizedMap(new WeakHashMap<>());

    private static void completeAndRunNext(@NonNull Gallery gallery) {
        Runnable next = null;
        synchronized (imageDownloadQueue) {
            Deque<Runnable> q = imageDownloadQueue.get(gallery);
            if (q == null) return;
            q.pollFirst();
            next = q.peekFirst();
            if (next == null) {
                imageDownloadQueue.remove(gallery);
            }
        }
        if (next != null) next.run();
    }

    public static void preloadImage(Context context, Uri url) {
        if (Global.getDownloadPolicy() == Global.DataUsageType.NONE) return;
        RequestManager manager = GlideX.with(context);
        LogUtility.d("Requested url glide: " + url);
        if (manager != null) manager.load(url).preload();
    }

    public static void loadImageOp(Context context, ImageView view, File file, int angle) {
        RequestManager glide = GlideX.with(context);
        if (glide == null) return;
        Drawable logo = Global.getLogo(context.getResources());
        glide.load(file).transform(new Rotate(angle)).error(logo).placeholder(logo).into(view);
        LogUtility.d("Requested file glide: " + file);
    }

    public static void loadImageOp(Context context, ImageView view, Gallery gallery, int page, int angle) {
        loadImageOp(context, view, gallery, page, angle, true);
    }

    public static void downloadPage(Activity activity, ImageView imageView, Gallery gallery, int page, boolean shouldFull) {
        shouldFull = gallery.getPageExtensionString(page).equals("gif") || shouldFull;
        loadImageOp(activity, imageView, gallery, page, 0, shouldFull);
    }

    public static void loadImageOp(Context context, ImageView imageView, Gallery gallery, int page, int angle, boolean shouldFull) {
        Runnable errorRunnable = new ImageExtensionTryRunnable(context, imageView, gallery, page, angle, shouldFull);
        loadImageOp(context, imageView, gallery, () -> getUrlForGallery(gallery, page, shouldFull), angle, errorRunnable, false);
    }

    private static void loadImageOp(Context context, ImageView view, @Nullable Gallery gallery, Supplier<Uri> url, int angle, Runnable errorRunnable, boolean priority) {
        if (Global.getDownloadPolicy() == Global.DataUsageType.NONE) {
            loadLogo(view);
            return;
        }
        // Do not queue requests with a null Gallery key: keep Glide lifecycle behavior and avoid global serialization.
        if (gallery == null) {
            LogUtility.d("Requested url glide: " + url.get());
            RequestManager glide = GlideX.with(context);
            if (glide == null) return;
            Drawable logo = Global.getLogo(context.getResources());
            RequestBuilder<Drawable> dra = glide.load(url.get());
            if (angle != 0) dra = dra.transform(new Rotate(angle));
            dra.error(logo)
                .addListener(new RequestListener<>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, @Nullable Object model, @NonNull Target<Drawable> target, boolean isFirstResource) {
                        new Handler(context.getMainLooper()).post(errorRunnable);
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(@NonNull Drawable resource, @NonNull Object model, Target<Drawable> target, @NonNull DataSource dataSource, boolean isFirstResource) {
                        return false;
                    }
                })
                .placeholder(logo)
                .into(new ImageViewTarget<Drawable>(view) {
                    @Override
                    protected void setResource(@Nullable Drawable resource) {
                        new Handler(context.getMainLooper()).post(() -> this.view.setImageDrawable(resource));
                    }
                });
            return;
        }

        Runnable task = () -> {
            LogUtility.d("Requested url glide: " + url.get());
            RequestManager glide = GlideX.with(context);
            if (glide == null) return;
            Drawable logo = Global.getLogo(context.getResources());
            RequestBuilder<Drawable> dra = glide.load(url.get());
            if (angle != 0)
                dra = dra.transform(new Rotate(angle));
            dra.error(logo)
                .addListener(new RequestListener<>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, @Nullable Object model, @NonNull Target<Drawable> target, boolean isFirstResource) {
                        new Handler(context.getMainLooper()).post(errorRunnable);
                        completeAndRunNext(gallery);
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(@NonNull Drawable resource, @NonNull Object model, Target<Drawable> target, @NonNull DataSource dataSource, boolean isFirstResource) {
                        if (gallery != null && !gallery.getGalleryData().getCheckedExt())
                            gallery.getGalleryData().setCheckedExt();
                        completeAndRunNext(gallery);
                        return false;
                    }
                })
                .placeholder(logo)
                .into(new ImageViewTarget<Drawable>(view) {
                    @Override
                    protected void setResource(@Nullable Drawable resource) {
                        new Handler(context.getMainLooper()).post(() -> this.view.setImageDrawable(resource));
                    }
                });
        };

        boolean shouldStartNow;
        synchronized (imageDownloadQueue) {
            Deque<Runnable> q = imageDownloadQueue.get(gallery);
            if (q == null) {
                q = new ArrayDeque<>();
                imageDownloadQueue.put(gallery, q);
            }
            if (priority && !q.isEmpty()) {
                Runnable head = q.pollFirst();
                List<Runnable> rest = new ArrayList<>(q);
                q.clear();
                q.addLast(head);
                q.addLast(task);
                q.addAll(rest);
            } else {
                q.addLast(task);
            }
            shouldStartNow = q.size() == 1;
        }
        if (shouldStartNow) task.run();
    }

    private static Uri getUrlForGallery(Gallery gallery, int page, boolean shouldFull) {
        return shouldFull ? gallery.getPageUrl(page) : gallery.getLowPage(page);
    }

    private static void loadLogo(ImageView imageView) {
        imageView.setImageDrawable(Global.getLogo(imageView.getResources()));
    }

    public static void loadImage(Activity activity, Uri url, ImageView imageView) {
        loadImageOp(activity, imageView, null, () -> url, 0, () -> {}, false);
    }

    public static void loadImage(Activity activity, File file, ImageView imageView) {
        loadImage(activity, file == null ? null : Uri.fromFile(file), imageView);
    }

    /**
     * Load Resource using id
     */
    public static void loadImage(@DrawableRes int resource, ImageView imageView) {
        imageView.setImageResource(resource);
    }

    private static class ImageExtensionTryRunnable implements Runnable {

        private final Context context;
        @Nullable
        private final ImageView imageView;
        private final Gallery gallery;
        private final int page;
        private final int angle;
        private final boolean shouldFull;
        private final List<ImageExt> triedExtensions = new ArrayList<>();

        public ImageExtensionTryRunnable(Context context, @Nullable ImageView imageView, Gallery gallery, int page, int angle, boolean shouldFull) {
            this.context = context;
            this.imageView = imageView;
            this.gallery = gallery;
            this.page = page;
            this.angle = angle;
            this.shouldFull = shouldFull;
            triedExtensions.add(gallery.getPageExtension(page));
        }

        @Override
        public void run() {
            if (!triedExtensions.contains(gallery.getPageExtension(page))) {
                LogUtility.d("Trying again with extension " + gallery.getPageExtension(page).getName());
                triedExtensions.add(gallery.getPageExtension(page));
                gallery.setPageExtension(page, gallery.getPageExtension(page));
                loadImageOp(context, imageView, gallery, () -> getUrlForGallery(gallery, page, shouldFull), angle, this, true);
            }
            LogUtility.d("Failed getting image with extension " + gallery.getPageExtensionString(page));
            Arrays.stream(ImageExt.values())
                .filter(imageExt -> !triedExtensions.contains(imageExt))
                .findAny()
                .ifPresent(imageExt -> {
                    LogUtility.d("Trying again with extension " + imageExt.getName());
                    triedExtensions.add(imageExt);
                    gallery.setPageExtensionFrom(page, imageExt);
                    getUrlForGallery(gallery, page, shouldFull);
                    loadImageOp(context, imageView, gallery, () -> getUrlForGallery(gallery, page, shouldFull), angle, this, true);
                });
        }
    }
}
