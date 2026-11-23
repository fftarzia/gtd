package com.fftarzia.gtddo.quickcapture;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** A fast capture screen accessible from the Quick Settings tile and Android Share. */
public final class QuickCaptureActivity extends Activity {
    private SecureSettings settings;
    private EditText contentInput;
    private TextView statusText;
    private Button saveButton;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private boolean saved = false;
    private boolean saving = false;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_capture);
        // Keep the editor as a small dialog over the current screen.
        getWindow().setLayout(
                (int) (getResources().getDisplayMetrics().widthPixels * 0.92),
                WindowManager.LayoutParams.WRAP_CONTENT);
        InsetUtil.apply(findViewById(R.id.root));
        settings = new SecureSettings(this);
        contentInput = findViewById(R.id.inputNote);
        statusText = findViewById(R.id.statusText);
        saveButton = findViewById(R.id.buttonSaveNote);

        String shared = null;
        if (Intent.ACTION_SEND.equals(getIntent().getAction())
                && "text/plain".equals(getIntent().getType())) {
            shared = getIntent().getStringExtra(Intent.EXTRA_TEXT);
        }
        if (shared != null && !shared.isEmpty()) {
            contentInput.setText(shared);
        } else if (state == null) {
            try {
                contentInput.setText(settings.readDraft());
            } catch (Exception error) {
                statusText.setText("Bozza non disponibile. Il testo nuovo verra' salvato in modo cifrato.");
            }
        }
        contentInput.setSelection(contentInput.length());
        contentInput.requestFocus();
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);

        saveButton.setOnClickListener(v -> submit());
        findViewById(R.id.buttonCancel).setOnClickListener(v -> finish());
        findViewById(R.id.buttonSettings).setOnClickListener(
                v -> startActivity(new Intent(this, MainActivity.class)));

        if (settings.getServerUrl().isEmpty() || !settings.hasToken()) {
            statusText.setText("Prima apertura: tocca Impostazioni e configura il server.");
        }
    }

    private void submit() {
        if (saving) return;
        String note = contentInput.getText().toString().trim();
        if (note.isEmpty()) {
            statusText.setText("Scrivi una nota prima di salvarla.");
            return;
        }
        final String token;
        final String url = settings.getServerUrl();
        try {
            token = settings.readToken();
            if (url.isEmpty() || token.isEmpty()) {
                statusText.setText("Server o token mancanti. Apri Impostazioni.");
                return;
            }
        } catch (Exception error) {
            statusText.setText("Token non disponibile. Apri Impostazioni e salvalo di nuovo.");
            return;
        }
        saving = true;
        saveButton.setEnabled(false);
        statusText.setText("Salvataggio nella Inbox...");
        worker.execute(() -> {
            String failure = null;
            try {
                ApiClient.createInboxItem(url, token, note);
            } catch (Exception error) {
                failure = MainActivity.safeError(error);
            }
            final String errorMessage = failure;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                saving = false;
                saveButton.setEnabled(true);
                if (errorMessage == null) {
                    saved = true;
                    try {
                        settings.saveDraft("");
                    } catch (Exception ignored) {
                        // The note was saved server-side. Never re-submit automatically.
                    }
                    Toast.makeText(this, "Nota salvata nella Inbox.", Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    statusText.setText("Nota non inviata: " + errorMessage + " Il testo resta qui.");
                }
            });
        });
    }

    @Override
    protected void onPause() {
        if (!saved) {
            try {
                settings.saveDraft(contentInput.getText().toString());
            } catch (Exception ignored) {
                // Capture stays visible on failure; no silent server retries.
            }
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        worker.shutdown();
        super.onDestroy();
    }
}
