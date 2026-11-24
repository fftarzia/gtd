package com.fftarzia.gtddo.quickcapture;

/** Validated configuration. Never log or stringify credentials. */
public final class AppConfiguration {
    public final String serverUrl;
    public final String apiToken;
    public final CloudflareCredentials cloudflare;

    public AppConfiguration(String url, String token, CloudflareCredentials credentials) {
        serverUrl = EndpointUrl.normalize(url);
        apiToken = token == null ? "" : token.trim();
        if (apiToken.isEmpty() || apiToken.length() > 8192) {
            throw new IllegalArgumentException("Inserisci un token API personale valido.");
        }
        for (int i = 0; i < apiToken.length(); i++) {
            if (Character.isWhitespace(apiToken.charAt(i)) || Character.isISOControl(apiToken.charAt(i))) {
                throw new IllegalArgumentException("Il token API contiene caratteri non validi.");
            }
        }
        cloudflare = credentials == null ? new CloudflareCredentials("", "") : credentials;
        if (serverUrl.length() > 2048 || cloudflare.getClientId().length() > 8192
                || cloudflare.getClientSecret().length() > 8192) {
            throw new IllegalArgumentException("Configurazione troppo grande.");
        }
    }
}
