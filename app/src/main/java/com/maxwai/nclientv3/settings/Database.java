package com.maxwai.nclientv3.settings;

import android.database.sqlite.SQLiteDatabase;
import android.content.Context;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.maxwai.nclientv3.async.database.DatabaseHelper;
import com.maxwai.nclientv3.async.database.Queries;
import com.maxwai.nclientv3.utility.LogUtility;
import com.maxwai.nclientv3.utility.AppExecutors;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class Database {
    private static SQLiteDatabase database;
    private static final Object INIT_LOCK = new Object();
    private static volatile boolean initStarted = false;
    private static volatile Throwable initError = null;
    private static final List<Runnable> onReadyCallbacks = new CopyOnWriteArrayList<>();

    @Nullable
    public static SQLiteDatabase getDatabase() {
        return database;
    }

    public static boolean isInitialized() {
        return database != null;
    }

    public static void initAsync(@NonNull Context context) {
        context = context.getApplicationContext();
        if (database != null || initStarted) return;
        synchronized (INIT_LOCK) {
            if (database != null || initStarted) return;
            initStarted = true;
        }
        Context finalContext = context;
        AppExecutors.io().execute(() -> {
            try {
                setDatabase(new DatabaseHelper(finalContext).getWritableDatabase());
            } catch (Throwable t) {
                initError = t;
                LogUtility.e("Database init failed", t);
            } finally {
                for (Runnable r : onReadyCallbacks) {
                    try {
                        r.run();
                    } catch (Throwable t) {
                        LogUtility.e("Database onReady callback failed", t);
                    }
                }
                onReadyCallbacks.clear();
            }
        });
    }

    public static void runOnReady(@NonNull Context context, @NonNull Runnable runnable) {
        if (database != null) {
            runnable.run();
            return;
        }
        if (initError != null) {
            LogUtility.e("Database init previously failed; skipping onReady callback", initError);
            return;
        }
        onReadyCallbacks.add(runnable);
        initAsync(context);
    }

    /**
     * Runs {@code runnable} on the IO executor once the database is initialized.
     * Safe to call from the main thread.
     */
    public static void runOnReadyAsync(@NonNull Context context, @NonNull Runnable runnable) {
        runOnReady(context, () -> AppExecutors.io().execute(runnable));
    }

    /**
     * Ensures the database is initialized, synchronously if needed.
     * Callers should avoid invoking this on the main thread.
     */
    public static void ensureInitialized(@NonNull Context context) {
        if (database != null) return;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            LogUtility.w("Database.ensureInitialized called on main thread; this may cause jank/ANR.");
        }
        try {
            setDatabase(new DatabaseHelper(context.getApplicationContext()).getWritableDatabase());
        } catch (Throwable t) {
            initError = t;
            LogUtility.e("Database ensureInitialized failed", t);
        }
    }

    public static void setDatabase(SQLiteDatabase database) {
        Database.database = database;
        LogUtility.d("SETTED database" + database);
        setDBForTables(database);
        Queries.StatusTable.initStatuses();
    }

    private static void setDBForTables(SQLiteDatabase database) {
        Queries.setDb(database);
    }

}
