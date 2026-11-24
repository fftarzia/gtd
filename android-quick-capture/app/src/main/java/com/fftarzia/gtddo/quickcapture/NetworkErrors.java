package com.fftarzia.gtddo.quickcapture;

/** User-facing errors never include server response bodies or credentials. */
public final class NetworkErrors {
    private NetworkErrors() {}
    public static String message(Exception error) {
        if (error instanceof java.net.SocketTimeoutException) return "Invio non confermato. Controlla la Inbox prima di riprovare: la nota potrebbe essere già arrivata.";
        if (error instanceof java.net.UnknownHostException) return "Server non raggiungibile. Controlla la connessione.";
        if (error instanceof javax.net.ssl.SSLException) return "Certificato HTTPS non valido o non attendibile.";
        if (error instanceof java.net.ConnectException) return "Connessione al server non riuscita.";
        if (error instanceof ApiClient.HttpError || error instanceof IllegalArgumentException) return error.getMessage();
        return "Operazione non riuscita. Controlla la connessione e la configurazione.";
    }
}
