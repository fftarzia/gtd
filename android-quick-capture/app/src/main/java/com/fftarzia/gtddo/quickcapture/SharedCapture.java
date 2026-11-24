package com.fftarzia.gtddo.quickcapture;

import android.content.Intent;

/** Receives ordinary text and links. No unsupported image/file uploads are advertised. */
public final class SharedCapture {
    private SharedCapture() {}
    public static String from(Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())
                || intent.getType() == null || !intent.getType().startsWith("text/")) return null;
        CharSequence text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
        if (text == null || text.toString().trim().isEmpty()) return null;
        return combine(intent.getStringExtra(Intent.EXTRA_SUBJECT), text.toString());
    }
    static String combine(String subject, String text) {
        if (subject == null || subject.trim().isEmpty() || text.contains(subject.trim())) return text;
        return subject.trim() + "\n\n" + text;
    }
}
