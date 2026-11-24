package com.fftarzia.gtddo.quickcapture;

import org.json.JSONObject;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Small REST client: uses the GTDdo Inbox API, without third-party SDKs. */
public final class ApiClient {
    private static final int TIMEOUT_MS = 12000;
    private ApiClient() {}

    public static void createInboxItem(String baseUrl, String token, CloudflareCredentials cloudflare, String content) throws Exception {
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("Scrivi prima una nota.");
        }
        HttpURLConnection conn = open(baseUrl, token, cloudflare, "POST");
        try {
            JSONObject body = new JSONObject();
            body.put("content", content);
            body.put("source", "android_quick_tile");
            byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setFixedLengthStreamingMode(payload.length);
            try (OutputStream stream = conn.getOutputStream()) {
                stream.write(payload);
            }
            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_CREATED) {
                throw new HttpError(errorFor(code));
            }
        } finally {
            conn.disconnect();
        }
    }

    public static void verify(String baseUrl, String token, CloudflareCredentials cloudflare) throws Exception {
        HttpURLConnection conn = open(baseUrl, token, cloudflare, "GET");
        try {
            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new HttpError(errorFor(code));
            }
            String type = conn.getContentType();
            if (type == null || !type.toLowerCase(java.util.Locale.ROOT).contains("application/json")) {
                throw new HttpError("Il server non ha restituito l'API GTDdo. Controlla l'indirizzo e Cloudflare Access.");
            }
        } finally {
            conn.disconnect();
        }
    }

    private static HttpURLConnection open(String baseUrl, String token, CloudflareCredentials cloudflare, String method) throws IOException {
        if (token == null || token.trim().isEmpty()) {
            throw new IllegalArgumentException("Configura prima il token API.");
        }
        URL url = new URL(EndpointUrl.inbox(baseUrl));
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setInstanceFollowRedirects(false); // Never forward Bearer tokens to redirected origins.
        conn.setRequestMethod(method);
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setRequestProperty("Authorization", "Bearer " + token.trim());
        if (cloudflare != null && cloudflare.isConfigured()) {
            conn.setRequestProperty("CF-Access-Client-Id", cloudflare.getClientId());
            conn.setRequestProperty("CF-Access-Client-Secret", cloudflare.getClientSecret());
        }
        conn.setRequestProperty("Accept", "application/json");
        return conn;
    }

    private static String errorFor(int status) {
        if (status == 401) return "Token API non valido o scaduto (HTTP 401).";
        if (status == 403) return "Accesso negato (HTTP 403). Verifica il token GTDdo e le credenziali Cloudflare.";
        if (status == 404) return "API Inbox non trovata: verifica l'URL e la versione di GTDdo (HTTP 404).";
        if (status == 429) return "Troppe richieste. Riprova fra poco (HTTP 429).";
        if (status >= 300 && status < 400) return "Reindirizzamento HTTP non consentito. Verifica credenziali e criteri Cloudflare Access (HTTP " + status + ").";
        if (status >= 500) return "Errore del server GTDdo (HTTP " + status + ").";
        return "Il server non ha accettato la richiesta (HTTP " + status + ").";
    }

    public static final class HttpError extends IOException {
        HttpError(String message) { super(message); }
    }
}
