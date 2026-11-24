package com.fftarzia.gtddo.quickcapture;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Retains an in-flight request across rotation without plaintext Bundle state. */
public final class CaptureViewModel extends AndroidViewModel {
    public final MutableLiveData<String> status = new MutableLiveData<>("");
    public String text;
    public String incomingShare;
    public volatile boolean sending;
    public volatile boolean sent;
    private final SecureSettings settings;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    public CaptureViewModel(@NonNull Application application) {
        super(application);
        settings = new SecureSettings(application);
    }
    public void initialize() {
        if (text != null) return;
        try { text = settings.readDraft(); }
        catch (Exception error) {
            text = "";
            status.setValue("La vecchia bozza non è leggibile. Le credenziali restano invariate.");
        }
    }
    public boolean saveDraft() {
        if (sent) return true;
        try {
            settings.saveDraft(text == null ? "" : text);
            return true;
        } catch (Exception error) {
            status.setValue("Bozza non salvata. Mantieni aperta la finestra finché non hai copiato o inviato il testo.");
            return false;
        }
    }
    public void submit() {
        if (sending || sent) return;
        if (text == null || text.trim().isEmpty()) { status.setValue("Scrivi prima una nota."); return; }
        if (text.length() > 10_000) {
            status.setValue("La nota supera 10.000 caratteri. Riducila prima di inviarla: il testo non è stato tagliato.");
            return;
        }
        if (!settings.isConfigured()) { status.setValue("Collega prima GTDdo dalle impostazioni."); return; }
        if (!saveDraft()) return;
        final String submitted = text;
        sending = true;
        status.setValue("Salvataggio nella Inbox…");
        worker.execute(() -> {
            try {
                AppConfiguration config = settings.readConfiguration();
                ApiClient.createInboxItem(config.serverUrl, config.apiToken, config.cloudflare, submitted);
                sent = true;
                String message = "Nota salvata nella Inbox.";
                try { settings.clearDraftIfEquals(submitted); }
                catch (Exception cleanupError) {
                    message = "Nota salvata nella Inbox, ma la bozza locale non è stata eliminata. Non inviarla di nuovo.";
                }
                sending = false;
                status.postValue(message);
            } catch (Exception error) {
                sending = false;
                status.postValue(NetworkErrors.message(error) + " Il testo resta nella bozza.");
            }
        });
    }
    @Override protected void onCleared() {
        // Let an already-started request finish; never silently retry it.
        worker.shutdown();
    }
}
