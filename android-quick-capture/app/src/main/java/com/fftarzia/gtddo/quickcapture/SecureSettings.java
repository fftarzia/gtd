package com.fftarzia.gtddo.quickcapture;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Encryption at rest with a non-exportable Android Keystore AES-GCM key. */
public final class SecureSettings {
    private static final String STORE = "quick_capture_settings";
    private static final String ALIAS = "tududi_quick_capture_aes";
    private static final String TOKEN = "api_token_encrypted";
    private static final String CF_CLIENT_ID = "cf_access_client_id_encrypted";
    private static final String CF_CLIENT_SECRET = "cf_access_client_secret_encrypted";
    private static final String DRAFT = "draft_encrypted";
    private static final String SERVER_URL = "server_url";
    private static final int GCM_TAG_BITS = 128;

    private final SharedPreferences prefs;

    public SecureSettings(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(STORE, Context.MODE_PRIVATE);
    }

    public String getServerUrl() {
        return prefs.getString(SERVER_URL, "");
    }

    public void saveServerUrl(String url) {
        String normalized = EndpointUrl.normalize(url);
        if (!prefs.edit().putString(SERVER_URL, normalized).commit()) {
            throw new IllegalStateException("Impossibile salvare l'indirizzo del server.");
        }
    }

    public boolean hasToken() {
        return prefs.contains(TOKEN);
    }

    public void saveToken(String value) throws GeneralSecurityException, IOException {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Token vuoto.");
        }
        writeEncrypted(TOKEN, value.trim());
    }

    public String readToken() throws GeneralSecurityException, IOException {
        return readEncrypted(TOKEN);
    }

    public boolean hasCloudflareCredentials() {
        return prefs.contains(CF_CLIENT_ID) && prefs.contains(CF_CLIENT_SECRET);
    }

    public CloudflareCredentials readCloudflareCredentials()
            throws GeneralSecurityException, IOException {
        if (prefs.contains(CF_CLIENT_ID) != prefs.contains(CF_CLIENT_SECRET)) {
            throw new IOException("Credenziali Cloudflare incomplete: salva nuovamente entrambe.");
        }
        return new CloudflareCredentials(
                readEncrypted(CF_CLIENT_ID), readEncrypted(CF_CLIENT_SECRET));
    }

    /** Commit both values at once so no incomplete credential pair is stored. */
    public void saveCloudflareCredentials(CloudflareCredentials credentials)
            throws GeneralSecurityException, IOException {
        if (credentials == null || !credentials.isConfigured()) {
            throw new IllegalArgumentException("Inserisci entrambe le credenziali Cloudflare.");
        }
        String id = encrypt(credentials.getClientId());
        String secret = encrypt(credentials.getClientSecret());
        if (!prefs.edit().putString(CF_CLIENT_ID, id)
                .putString(CF_CLIENT_SECRET, secret).commit()) {
            throw new IOException("Impossibile salvare le credenziali Cloudflare.");
        }
    }

    public void clearCloudflareCredentials() throws IOException {
        if (!prefs.edit().remove(CF_CLIENT_ID).remove(CF_CLIENT_SECRET).commit()) {
            throw new IOException("Impossibile eliminare le credenziali Cloudflare.");
        }
    }

    public void saveDraft(String draft) throws GeneralSecurityException, IOException {
        if (draft == null || draft.isEmpty()) {
            if (!prefs.edit().remove(DRAFT).commit()) {
                throw new IOException("Impossibile eliminare la bozza.");
            }
        } else {
            writeEncrypted(DRAFT, draft);
        }
    }

    public String readDraft() throws GeneralSecurityException, IOException {
        return readEncrypted(DRAFT);
    }

    private void writeEncrypted(String key, String plain) throws GeneralSecurityException, IOException {
        if (!prefs.edit().putString(key, encrypt(plain)).commit()) {
            throw new IOException("Salvataggio sicuro non riuscito.");
        }
    }

    private String encrypt(String plain) throws GeneralSecurityException, IOException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
        byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
        return "v1:" + Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP)
                + ":" + Base64.encodeToString(encrypted, Base64.NO_WRAP);
    }

    private String readEncrypted(String key) throws GeneralSecurityException, IOException {
        String serialized = prefs.getString(key, "");
        if (serialized == null || serialized.isEmpty()) return "";
        String[] components = serialized.split(":", -1);
        if (components.length != 3 || !"v1".equals(components[0])) {
            throw new IOException("Dati cifrati non riconosciuti.");
        }
        try {
            byte[] iv = Base64.decode(components[1], Base64.NO_WRAP);
            byte[] payload = Base64.decode(components[2], Base64.NO_WRAP);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(payload), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            throw new IOException("Dati cifrati non validi.", ex);
        }
    }

    private SecretKey getOrCreateKey() throws GeneralSecurityException, IOException {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(ALIAS)) {
            KeyStore.Entry entry = store.getEntry(ALIAS, null);
            if (entry instanceof KeyStore.SecretKeyEntry) {
                return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
            }
            throw new GeneralSecurityException("Chiave non disponibile.");
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(
                ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return generator.generateKey();
    }
}
