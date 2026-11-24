package com.fftarzia.gtddo.quickcapture;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Native Material capture dialog, opened by the tile or Sharesheet. */
public final class QuickCaptureActivity extends AppCompatActivity {
    private CaptureViewModel model;
    private EditText editor;
    private MaterialButton save;
    private int lastAvailableHeight;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_capture);
        model = new ViewModelProvider(this).get(CaptureViewModel.class);
        boolean firstCreation = model.text == null;
        model.initialize();
        editor = findViewById(R.id.inputNote);
        save = findViewById(R.id.buttonSaveNote);
        TextView status = findViewById(R.id.statusText);
        editor.setText(model.text);
        editor.setSelection(editor.length());
        editor.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                model.text = s.toString();
                save.setEnabled(!model.sending && !model.sent && !s.toString().trim().isEmpty() && s.length() <= 10_000);
            }
            public void afterTextChanged(Editable value) {}
        });
        findViewById(R.id.buttonClose).setOnClickListener(v -> closeSafely());
        findViewById(R.id.buttonCancel).setOnClickListener(v -> closeSafely());
        findViewById(R.id.buttonSettings).setOnClickListener(v -> {
            if (!model.sending && model.saveDraft()) startActivity(new Intent(this, MainActivity.class));
        });
        save.setOnClickListener(v -> model.submit());
        model.status.observe(this, message -> {
            status.setText(message);
            status.setVisibility(message == null || message.isEmpty() ? View.GONE : View.VISIBLE);
            editor.setEnabled(!model.sending && !model.sent);
            save.setEnabled(!model.sending && !model.sent && !model.text.trim().isEmpty() && model.text.length() <= 10_000);
            save.setText(model.sending ? R.string.sending : R.string.save_note);
            findViewById(R.id.buttonClose).setEnabled(!model.sending);
            findViewById(R.id.buttonCancel).setEnabled(!model.sending);
            findViewById(R.id.buttonSettings).setEnabled(!model.sending);
            if (model.sent) {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                finish();
            }
        });
        boolean configured = new SecureSettings(this).isConfigured();
        findViewById(R.id.buttonSettings).setVisibility(configured ? View.GONE : View.VISIBLE);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { closeSafely(); }
        });
        resizeForKeyboard();
        if (firstCreation) model.incomingShare = SharedCapture.from(getIntent());
        acceptShare(model.incomingShare);
        editor.requestFocus();
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }
    private void resizeForKeyboard() {
        View root = findViewById(R.id.root);
        int margin = getResources().getDimensionPixelSize(R.dimen.capture_outer_margin);
        int maxWidth = getResources().getDimensionPixelSize(R.dimen.capture_max_width);
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        getWindow().setLayout(Math.min(maxWidth, Math.max(1, screenWidth - 2 * margin)),
                WindowManager.LayoutParams.WRAP_CONTENT);
        root.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            android.graphics.Rect visible = new android.graphics.Rect();
            getWindow().getDecorView().getWindowVisibleDisplayFrame(visible);
            int available = Math.max(1, visible.height() - 2 * margin);
            if (available != lastAvailableHeight) {
                lastAvailableHeight = available;
                ((CapturePanel) root).setMaximumHeight(available);
                editor.post(() -> {
                    if (editor.hasFocus()) editor.bringPointIntoView(editor.getSelectionEnd());
                });
            }
        });
        ViewCompat.requestApplyInsets(root);
    }
    private void acceptShare(String shared) {
        if (shared == null) return;
        if (shared.equals(model.text)) { model.incomingShare = null; return; }
        if (model.text.trim().isEmpty()) replaceWith(shared);
        else new MaterialAlertDialogBuilder(this).setTitle(R.string.existing_draft_title)
                .setMessage(R.string.existing_draft_message)
                .setNegativeButton(R.string.keep_draft, (d, w) -> model.incomingShare = null)
                .setPositiveButton(R.string.use_shared, (d, w) -> replaceWith(shared))
                .setOnCancelListener(d -> model.incomingShare = null).show();
    }
    private void replaceWith(String shared) {
        model.incomingShare = null;
        editor.setText(shared);
        editor.setSelection(editor.length());
        model.saveDraft();
    }
    private void closeSafely() {
        if (model.sending) model.status.setValue(getString(R.string.wait_for_send));
        else if (model.saveDraft()) finish();
    }
    @Override protected void onPause() {
        model.saveDraft();
        super.onPause();
    }
}
