package com.sms2vps.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.util.TypedValue;
import android.widget.TextView;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.PreferenceManager;

public class FontSizeManager {
    private static final String FONT_SCALE_KEY = "font_scale";
    private static FontSizeManager instance;
    private final SharedPreferences preferences;
    private float currentScale;
    private float lastAppliedScale;

    private FontSizeManager(Context context) {
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
        currentScale = preferences.getFloat(FONT_SCALE_KEY, 1.2f);
        lastAppliedScale = currentScale;
    }

    public static synchronized FontSizeManager getInstance(Context context) {
        if (instance == null) {
            instance = new FontSizeManager(context.getApplicationContext());
        }
        return instance;
    }

    public void setFontScale(float scale) {
        try {
        currentScale = scale;
        preferences.edit().putFloat(FONT_SCALE_KEY, scale).apply();
        } catch (Exception e) {
            Log.e("FontSizeManager", "Error setting font scale", e);
        }
    }

    public float getFontScale() {
        return currentScale;
    }

    public boolean hasFontSizeChanged() {
        try {
            boolean changed = Math.abs(currentScale - lastAppliedScale) > 0.001f;
            if (changed) {
                lastAppliedScale = currentScale;
            }
            return changed;
        } catch (Exception e) {
            Log.e("FontSizeManager", "Error checking font size change", e);
            return false;
        }
    }

    public void applyFontSize(TextView textView) {
        try {
            if (textView != null) {
                // Store original size in tag if not already stored
                final int BASE_SIZE_TAG = 0x7f0b0001; // Arbitrary unique int for tag
                Object tag = textView.getTag(BASE_SIZE_TAG);
                Float baseSize;
                if (tag instanceof Float) {
                    baseSize = (Float) tag;
                } else {
                    baseSize = textView.getTextSize();
                    textView.setTag(BASE_SIZE_TAG, baseSize);
                }
                float scaledSize = baseSize * currentScale;
                textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, scaledSize);
            }
        } catch (Exception e) {
            Log.e("FontSizeManager", "Error applying font size", e);
        }
    }
}
