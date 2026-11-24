package com.fftarzia.gtddo.quickcapture;

import android.app.Application;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Retains transfer/verification work through configuration changes; all I/O is off the UI thread. */
public final class SettingsViewModel extends AndroidViewModel {
    enum Kind { INFO, SAVED, EXPORT_READY, PASSWORD_NEEDED, IMPORT_READY }
    static final class State {
        final Kind kind;
        final String message;
        State(Kind kind, String message) { this.kind = kind; this.message = message; }
    }
    final MutableLiveData<State> state = new MutableLiveData<>(new State(Kind.INFO, ""));
    volatile boolean busy;
    boolean exportPickerOpen;
    boolean dialogOpen;
    boolean formExpanded;
    String[] form;
    AppConfiguration imported;
    private byte[] exportData;
    private byte[] importData;
    private final SecureSettings settings;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    public SettingsViewModel(@NonNull Application app) {
        super(app);
        settings = new SecureSettings(app);
        formExpanded = !settings.isConfigured();
    }
    void info(String message) { state.setValue(new State(Kind.INFO, message)); }
    private boolean begin(String message) {
        if (busy) return false;
        busy = true;
        info(message);
        return true;
    }
    private void done(Kind kind, String message) {
        busy = false;
        state.postValue(new State(kind, message));
    }
    void verify(AppConfiguration configuration, boolean save) {
        if (!begin("Verifica del collegamento…")) return;
        worker.execute(() -> {
            try {
                AppConfiguration candidate = configuration == null ? settings.readConfiguration() : configuration;
                ApiClient.verify(candidate.serverUrl, candidate.apiToken, candidate.cloudflare);
                if (save) settings.saveConfiguration(candidate);
                imported = null;
                done(save ? Kind.SAVED : Kind.INFO,
                        save ? "Configurazione salvata. Connessione verificata." : "Connessione verificata. GTDdo è raggiungibile.");
            } catch (Exception error) {
                String failure = error instanceof java.net.SocketTimeoutException
                        ? "Il server non risponde alla verifica." : NetworkErrors.message(error);
                done(Kind.INFO, failure + (save ? " La configurazione precedente non è stata modificata." : ""));
            }
        });
    }
    void prepareExport(char[] password) {
        if (!begin("Protezione del backup…")) { Arrays.fill(password, '\0'); return; }
        worker.execute(() -> {
            try {
                exportData = ConfigurationBackup.encrypt(settings.readConfiguration(), password);
                exportPickerOpen = false;
                done(Kind.EXPORT_READY, "Scegli dove salvare il backup cifrato.");
            } catch (Exception error) {
                done(Kind.INFO, "Backup non creato. Verifica la configurazione e usa una password di almeno 12 caratteri.");
            } finally { Arrays.fill(password, '\0'); }
        });
    }
    void writeExport(Uri uri) {
        exportPickerOpen = false;
        if (uri == null) { clearExport(); info("Esportazione annullata."); return; }
        if (exportData == null) { info("Ripeti l’esportazione: il processo è stato interrotto."); return; }
        if (!begin("Salvataggio del backup cifrato…")) return;
        worker.execute(() -> {
            try (OutputStream output = getApplication().getContentResolver().openOutputStream(uri, "wt")) {
                if (output == null) throw new java.io.IOException();
                output.write(exportData);
                output.flush();
                done(Kind.INFO, "Backup cifrato esportato. Conserva la password separatamente dal file.");
            } catch (Exception error) { done(Kind.INFO, "Backup non salvato. Scegli un’altra posizione e riprova."); }
            finally { clearExport(); }
        });
    }
    void readImport(Uri uri) {
        if (uri == null || !begin("Lettura del backup…")) return;
        worker.execute(() -> {
            try (InputStream input = getApplication().getContentResolver().openInputStream(uri)) {
                importData = ConfigurationBackup.read(input);
                done(Kind.PASSWORD_NEEDED, "Inserisci la password del backup.");
            } catch (Exception error) { done(Kind.INFO, "File non leggibile o troppo grande. La configurazione resta invariata."); }
        });
    }
    void decryptImport(char[] password) {
        if (importData == null || !begin("Apertura del backup…")) { Arrays.fill(password, '\0'); return; }
        worker.execute(() -> {
            try {
                imported = ConfigurationBackup.decrypt(importData, password);
                done(Kind.IMPORT_READY, "Controlla il server prima di importare.");
            } catch (Exception error) {
                done(Kind.INFO, "Password errata o backup non valido. La configurazione resta invariata.");
            } finally {
                Arrays.fill(password, '\0');
                clearImport();
            }
        });
    }
    void cancelImport() {
        clearImport();
        imported = null;
        info("Importazione annullata.");
    }
    private void clearExport() {
        if (exportData != null) Arrays.fill(exportData, (byte) 0);
        exportData = null;
    }
    private void clearImport() {
        if (importData != null) Arrays.fill(importData, (byte) 0);
        importData = null;
    }
    @Override protected void onCleared() {
        worker.shutdown();
        form = null;
        imported = null;
    }
}
