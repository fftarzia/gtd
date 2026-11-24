package com.fftarzia.gtddo.quickcapture;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/** Validates a tududi origin. No path, query, credentials or plain HTTP allowed. */
public final class EndpointUrl {
    private EndpointUrl() {}

    public static String normalize(String input) {
        if (input == null || input.trim().isEmpty()) {
            throw new IllegalArgumentException("Inserisci l'indirizzo HTTPS di GTDdo.");
        }
        try {
            URI uri = new URI(input.trim());
            String scheme = uri.getScheme();
            String path = uri.getRawPath();
            if (scheme == null || !"https".equals(scheme.toLowerCase(Locale.ROOT))
                    || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || (path != null && !path.isEmpty() && !"/".equals(path))) {
                throw new IllegalArgumentException(
                        "Usa l'URL HTTPS del server, senza percorsi (es. https://tasks.example.com).");
            }
            return "https://" + uri.getRawAuthority();
        } catch (URISyntaxException error) {
            throw new IllegalArgumentException("Indirizzo del server non valido.");
        }
    }

    public static String inbox(String baseUrl) {
        return normalize(baseUrl) + "/api/v1/inbox";
    }
}
