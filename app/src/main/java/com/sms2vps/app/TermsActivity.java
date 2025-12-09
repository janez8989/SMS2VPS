package com.sms2vps.app;

import android.os.Bundle;
import android.webkit.WebView;
import androidx.appcompat.widget.Toolbar;
import android.view.MenuItem;
import android.util.Log;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class TermsActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_terms);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setLogo(null);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Terms of Use & Privacy Policy");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        WebView webView = findViewById(R.id.webView);
        webView.getSettings().setDefaultTextEncodingName("utf-8");
        try {
            InputStream inputStream = getAssets().open("tos.html");
            int size = inputStream.available();
            byte[] buffer = new byte[size];
            int bytesRead = inputStream.read(buffer);
            if (bytesRead != size) {
                Log.w("TermsActivity", "Incomplete read from tos.html");
            }
            inputStream.close();
            String html = new String(buffer, StandardCharsets.UTF_8);
            int lastUpdatedIndex = html.indexOf("Last updated");
            if (lastUpdatedIndex != -1) {
                html = html.substring(lastUpdatedIndex);
            }
            webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
        } catch (Exception e) {
            Log.e("TermsActivity", "Error loading terms of use", e);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateFontSize();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            getOnBackPressedDispatcher().onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}