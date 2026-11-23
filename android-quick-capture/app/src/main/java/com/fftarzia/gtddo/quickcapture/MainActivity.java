package com.fftarzia.gtddo.quickcapture;

import android.app.Activity;
import android.app.StatusBarManager;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private SecureSettings settings;
    private EditText serverInput;
    private EditText tokenInput;
    private TextView tokenState;
    private EditText cfClientIdInput;
    private EditText cfClientSecretInput;
    private TextView cfCredentialsState;
    private TextView statusText;
    private Button testButton;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        InsetUtil.apply(findViewById(R.id.root));
        settings = new SecureSettings(this);
        serverInput = findViewById(R.id.inputServerUrl);
        tokenInput = findViewById(R.id.inputApiToken);
        tokenState = findViewById(R.id.tokenState);
        cfClientIdInput = findViewById(R.id.inputCfClientId);
        cfClientSecretInput = findViewById(R.id.inputCfClientSecret);
        cfCredentialsState = findViewById(R.id.cfCredentialsState);
        statusText = findViewById(R.id.statusText);
        testButton = findViewById(R.id.buttonTestConnection);
        serverInput.setText(settings.getServerUrl());
        refreshTokenState();

        findViewById(R.id.buttonSaveSettings).setOnClickListener(v -> saveConfiguration());
        findViewById(R.id.buttonClearCloudflare).setOnClickListener(v -> {
            try {
                settings.clearCloudflareCredentials();
                cfClientIdInput.setText("");
                cfClientSecretInput.setText("");
                refreshTokenState();
                statusText.setText("Credenziali Cloudflare eliminate dal telefono.");
            } catch (Exception error) {
                statusText.setText("Eliminazione non riuscita: " + safeError(error));
            }
        });
        testButton.setOnClickListener(v -> testConnection());
        findViewById(R.id.buttonOpenCapture).setOnClickListener(
                v -> startActivity(new Intent(this, QuickCaptureActivity.class)));
        findViewById(R.id.buttonAddTile).setOnClickListener(v -> requestQuickSettingsTile());
    }

    private void refreshTokenState() {
        tokenState.setText(settings.hasToken()
                ? "Token salvato in modo cifrato. Lascia vuoto il campo per mantenerlo."
                : "Nessun token salvato. Creane uno nelle impostazioni API di GTDdo.");
        cfCredentialsState.setText(settings.hasCloudflareCredentials()
                ? "Cloudflare Access attivo. Entrambe le credenziali sono cifrate. Lascia i campi vuoti per mantenerle."
                : "Cloudflare Access non configurato. Compila entrambi i campi solo per il dominio protetto.");
    }

    private void saveConfiguration() {
        try {
            String url = EndpointUrl.normalize(serverInput.getText().toString());
            String newToken = tokenInput.getText().toString().trim();
            CloudflareCredentials newCloudflare = new CloudflareCredentials(
                    cfClientIdInput.getText().toString(),
                    cfClientSecretInput.getText().toString());
            if (newToken.isEmpty() && !settings.hasToken()) {
                statusText.setText("Inserisci un token API personale.");
                return;
            }
            if (newToken.isEmpty() && !settings.getServerUrl().isEmpty()
                    && !url.equals(settings.getServerUrl())) {
                statusText.setText("Se cambi server, inserisci anche un token nuovo.");
                return;
            }
            if (!url.equals(settings.getServerUrl())
                    && settings.hasCloudflareCredentials() && !newCloudflare.isConfigured()) {
                statusText.setText("Se cambi server, reinserisci entrambi i dati Cloudflare oppure eliminali.");
                return;
            }
            if (!newToken.isEmpty()) settings.saveToken(newToken);
            if (newCloudflare.isConfigured()) settings.saveCloudflareCredentials(newCloudflare);
            settings.saveServerUrl(url);
            tokenInput.setText("");
            cfClientIdInput.setText("");
            cfClientSecretInput.setText("");
            serverInput.setText(url);
            refreshTokenState();
            statusText.setText("Configurazione salvata. Usa Verifica connessione.");
        } catch (Exception error) {
            statusText.setText("Configurazione non salvata: " + safeError(error));
        }
    }

    private void testConnection() {
        final String url = settings.getServerUrl();
        final String token;
        final CloudflareCredentials cloudflare;
        try {
            token = settings.readToken();
            cloudflare = settings.readCloudflareCredentials();
            if (url.isEmpty() || token.isEmpty()) {
                statusText.setText("Salva prima l'indirizzo e il token.");
                return;
            }
        } catch (Exception error) {
            statusText.setText("Token non leggibile: salva nuovamente il token.");
            return;
        }
        testButton.setEnabled(false);
        statusText.setText("Verifica in corso...");
        worker.execute(() -> {
            String response;
            try {
                ApiClient.verify(url, token, cloudflare);
                response = "Connessione OK. Puoi usare Nota rapida.";
            } catch (Exception error) {
                response = "Connessione non riuscita: " + safeError(error);
            }
            final String message = response;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                testButton.setEnabled(true);
                statusText.setText(message);
            });
        });
    }

    private void requestQuickSettingsTile() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(this, "Aggiungi Nota rapida da Modifica nelle Impostazioni rapide.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        StatusBarManager statusBar = getSystemService(StatusBarManager.class);
        if (statusBar == null) {
            statusText.setText("Apri Modifica nelle Impostazioni rapide e aggiungi Nota rapida.");
            return;
        }
        try {
            statusBar.requestAddTileService(
                    new ComponentName(this, QuickNoteTileService.class),
                    getString(R.string.tile_label),
                    Icon.createWithResource(this, R.drawable.ic_quick_note),
                    getMainExecutor(),
                    result -> {
                        if (isFinishing() || isDestroyed()) return;
                        if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED) {
                            statusText.setText("Pulsante Nota rapida aggiunto.");
                        } else if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED) {
                            statusText.setText("Nota rapida e' gia' presente nelle Impostazioni rapide.");
                        } else {
                            statusText.setText("Per aggiungere il pulsante usa Modifica nelle Impostazioni rapide.");
                        }
                    });
        } catch (RuntimeException error) {
            statusText.setText("Richiesta non disponibile. Aggiungi Nota rapida dal menu Modifica.");
        }
    }

    static String safeError(Exception error) {
        if (error instanceof java.net.UnknownHostException) {
            return "Server non raggiungibile: controlla DNS o VPN.";
        }
        if (error instanceof javax.net.ssl.SSLException) {
            return "Connessione TLS non valida. Serve un certificato HTTPS attendibile.";
        }
        if (error instanceof java.net.SocketTimeoutException) {
            return "Timeout: il server non risponde.";
        }
        if (error instanceof java.net.ConnectException) {
            return "Connessione rifiutata: controlla il server o la rete.";
        }
        String message = error.getMessage();
        return message == null ? "Errore imprevisto." : message;
    }

    @Override
    protected void onDestroy() {
        worker.shutdown();
        super.onDestroy();
    }
}
