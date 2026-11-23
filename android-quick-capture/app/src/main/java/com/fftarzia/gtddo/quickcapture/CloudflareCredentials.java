package com.fftarzia.gtddo.quickcapture;

/**
 * A pair of Cloudflare Access service-token credentials. Both fields must be
 * supplied together, or both omitted for servers that do not use Access.
 * Never log or embed these values in a distributed APK.
 */
public final class CloudflareCredentials {
    private final String clientId;
    private final String clientSecret;

    public CloudflareCredentials(String id, String secret) {
        clientId = id == null ? "" : id.trim();
        clientSecret = secret == null ? "" : secret.trim();
        if (clientId.isEmpty() != clientSecret.isEmpty()) {
            throw new IllegalArgumentException(
                    "Inserisci sia il Client ID sia il Client Secret Cloudflare.");
        }
        rejectControls(clientId);
        rejectControls(clientSecret);
    }

    private static void rejectControls(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) {
                throw new IllegalArgumentException("Credenziale Cloudflare non valida.");
            }
        }
    }

    public boolean isConfigured() {
        return !clientId.isEmpty();
    }

    public String getClientId() {
        return clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }
}
