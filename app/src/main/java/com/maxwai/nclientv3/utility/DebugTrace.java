package com.maxwai.nclientv3.utility;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Process;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.maxwai.nclientv3.BuildConfig;

public final class DebugTrace {
    private static final String TAG = "NClientTrace";

    private DebugTrace() {
    }

    public static void log(@NonNull String event, @Nullable Activity activity, @Nullable Bundle savedInstanceState, @Nullable String extra) {
        if (!BuildConfig.DEBUG) return;
        try {
            int pid = Process.myPid();
            long now = System.currentTimeMillis();
            String activityName = activity == null ? "null" : activity.getClass().getName();
            boolean recreated = savedInstanceState != null;
            Intent intent = activity == null ? null : activity.getIntent();
            int flags = intent == null ? 0 : intent.getFlags();
            Log.d(TAG, event
                + " pid=" + pid
                + " t=" + now
                + " activity=" + activityName
                + " recreated=" + recreated
                + " intentFlags=0x" + Integer.toHexString(flags)
                + (extra == null ? "" : (" " + extra))
            );
        } catch (Throwable ignored) {
        }
    }
}

