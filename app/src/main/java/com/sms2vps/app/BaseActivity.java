package com.sms2vps.app;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public abstract class BaseActivity extends AppCompatActivity {
    private static final String TAG = "BaseActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Yhtenäinen statuspalkin väri kaikkiin näkymiin
        // Tämä koodi on nyt poistettu, jotta statuspalkin väri periytyy teemasta.
        // try {
        //     Window window = getWindow();
        //     if (window != null) {
        //         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        //             window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        //             window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        //             window.setStatusBarColor(ContextCompat.getColor(this, R.color.google_blue));
        //         }
        //     }
        // } catch (Exception ignored) {}
        try {
            updateFontSize();
        } catch (Exception e) {
            Log.e(TAG, "Error updating font size in onCreate", e);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            updateFontSize();
        } catch (Exception e) {
            Log.e(TAG, "Error updating font size in onResume", e);
        }
    }

    protected void updateFontSize() {
        View rootView = findViewById(android.R.id.content);
        if (rootView != null) {
            updateTextViewsInView(rootView);
        }
    }

    private void updateTextViewsInView(View view) {
        try {
            if (view instanceof ViewGroup viewGroup) {
                for (int i = 0; i < viewGroup.getChildCount(); i++) {
                    updateTextViewsInView(viewGroup.getChildAt(i));
                }
            } else if (view instanceof TextView textView) {
                FontSizeManager.getInstance(this).applyFontSize(textView);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating TextView font size", e);
        }
    }
}
