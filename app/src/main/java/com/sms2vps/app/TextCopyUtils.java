package com.sms2vps.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.util.Patterns;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TextCopyUtils {

    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\b\\d{4,}\\b");

    private TextCopyUtils() {}

    public static void applyClickableNumberCopy(TextView textView, CharSequence text, Context context) {
        if (text == null) {
            textView.setText("");
            textView.setOnLongClickListener(null);
            return;
        }

        SpannableString spannable = new SpannableString(text);

        // 1) Linkitä URLit ensin
        List<int[]> urlRanges = new ArrayList<>();
        Matcher urlMatcher = Patterns.WEB_URL.matcher(text);
        while (urlMatcher.find()) {
            int start = urlMatcher.start();
            int end = urlMatcher.end();
            urlRanges.add(new int[]{start, end});
            spannable.setSpan(new URLSpan(urlMatcher.group()), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        // 2) Lisää numerospanit, jotka eivät osu URL-alueisiin
        Matcher numMatcher = NUMBER_PATTERN.matcher(text);
        while (numMatcher.find()) {
            final int start = numMatcher.start();
            final int end = numMatcher.end();
            if (isInsideAnyRange(start, end, urlRanges)) {
                continue; // älä koske URL:n sisällä oleviin numeroihin
            }
            final String number = text.subSequence(start, end).toString();
            spannable.setSpan(new ClickableSpan() {
                @Override
                public void onClick(@NonNull View widget) {
                    copyToClipboard(context, number);
                }

                @Override
                public void updateDrawState(@NonNull TextPaint ds) {
                    super.updateDrawState(ds);
                    ds.setColor(Color.BLUE);
                    ds.setUnderlineText(true);
                }
            }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        textView.setText(spannable);
        textView.setLinksClickable(true);
        textView.setMovementMethod(LinkMovementMethod.getInstance());

        // 3) Pitkä painallus: kopioi koko teksti
        textView.setOnLongClickListener(v -> {
            copyToClipboard(context, text.toString());
            return true;
        });
    }

    private static boolean isInsideAnyRange(int start, int end, List<int[]> ranges) {
        for (int[] r : ranges) {
            if (start >= r[0] && end <= r[1]) return true;
        }
        return false;
    }

    private static void copyToClipboard(Context context, String value) {
        try {
            ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("copied", value));
                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception ignored) {}
    }
}


