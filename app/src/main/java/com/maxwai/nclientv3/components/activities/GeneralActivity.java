package com.maxwai.nclientv3.components.activities;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.maxwai.nclientv3.R;
import com.maxwai.nclientv3.BuildConfig;
import com.maxwai.nclientv3.components.views.CFTokenView;
import com.maxwai.nclientv3.settings.Global;
import com.maxwai.nclientv3.utility.LogUtility;

import java.lang.ref.WeakReference;

public abstract class GeneralActivity extends AppCompatActivity {
    private boolean isFastScrollerApplied = false;
    private static WeakReference<GeneralActivity> lastActivity;
    private CFTokenView tokenView = null;

    public static @Nullable
    CFTokenView getLastCFView() {
        if (lastActivity == null) return null;
        GeneralActivity activity = lastActivity.get();
        if (activity != null) {
            activity.runOnUiThread(activity::inflateWebView);
            return activity.tokenView;
        }
        return null;
    }

    private void inflateWebView() {
        if (tokenView == null) {
            Toast.makeText(this, R.string.fetching_cloudflare_token, Toast.LENGTH_SHORT).show();
            ViewGroup rootView= (ViewGroup) findViewById(android.R.id.content).getRootView();
            ViewGroup v= (ViewGroup) LayoutInflater.from(this).inflate(R.layout.cftoken_layout,rootView,false);
            tokenView = new CFTokenView(v);
            ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            tokenView.setVisibility(View.GONE);
            this.addContentView(v, params);
        }
    }

    @Override
    protected void onPause() {
        if (Global.hideMultitask())
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        super.onPause();
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Global.initActivity(this);
    }

    @Override
    protected void onResume() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        super.onResume();
        lastActivity = new WeakReference<>(this);
        if (BuildConfig.DEBUG) logThemeDiagnostics();
        if (!isFastScrollerApplied) {
            isFastScrollerApplied = true;
            Global.applyFastScroller(findViewById(R.id.recycler));
        }
    }

    @Override
    public Resources.Theme getTheme() {
        Resources.Theme theme = super.getTheme();
        SharedPreferences preferences = getSharedPreferences("Settings", 0);
        if (preferences.getBoolean(getString(R.string.preference_key_black_theme), false)) {
            theme.applyStyle(R.style.AppTheme_Black, true);
        }
        return theme;
    }

    private void logThemeDiagnostics() {
        try {
            SharedPreferences preferences = getSharedPreferences("Settings", 0);
            boolean blackTheme = preferences.getBoolean(getString(R.string.preference_key_black_theme), false);
            int night = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            Integer surface = resolveThemeColor(com.google.android.material.R.attr.colorSurface);
            Integer onSurface = resolveThemeColor(com.google.android.material.R.attr.colorOnSurface);
            LogUtility.d("ThemeDiagnostics night=", night, " blackTheme=", blackTheme, " colorSurface=", surface, " colorOnSurface=", onSurface);
        } catch (Throwable t) {
            LogUtility.w("ThemeDiagnostics failed", t);
        }
    }

    @Nullable
    private Integer resolveThemeColor(int attr) {
        TypedValue out = new TypedValue();
        if (!getTheme().resolveAttribute(attr, out, true)) return null;
        return out.type >= TypedValue.TYPE_FIRST_COLOR_INT && out.type <= TypedValue.TYPE_LAST_COLOR_INT ? out.data : null;
    }
}
