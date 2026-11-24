package com.fftarzia.gtddo.quickcapture;

import static org.junit.Assert.*;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.tls.HandshakeCertificates;
import okhttp3.tls.HeldCertificate;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 37)
public class ApiClientTest {
    private MockWebServer server;
    private SSLSocketFactory original;
    private String baseUrl;
    private final CloudflareCredentials cloudflare = new CloudflareCredentials("test.access", "test-secret");
    @Before public void localHttpsServer() throws Exception {
        HeldCertificate certificate = new HeldCertificate.Builder().addSubjectAlternativeName("127.0.0.1").build();
        HandshakeCertificates serverTls = new HandshakeCertificates.Builder().heldCertificate(certificate).build();
        HandshakeCertificates clientTls = new HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate()).build();
        original = HttpsURLConnection.getDefaultSSLSocketFactory();
        HttpsURLConnection.setDefaultSSLSocketFactory(clientTls.sslSocketFactory());
        server = new MockWebServer();
        server.useHttps(serverTls.sslSocketFactory(), false);
        server.start(java.net.InetAddress.getByName("127.0.0.1"), 0);
        baseUrl = server.url("/").newBuilder().host("127.0.0.1").build().toString();
    }
    @After public void restoreDefaultTrust() throws Exception {
        HttpsURLConnection.setDefaultSSLSocketFactory(original);
        server.shutdown();
    }
    @Test public void capturePreservesOriginalTextAndSendsBothAuthenticationLayers() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(201).setHeader("Content-Type", "application/json").setBody("{\"uid\":\"test\"}"));
        String content = "  Riga uno\nRiga due: https://example.com  ";
        ApiClient.createInboxItem(baseUrl, "tt_test", cloudflare, content);
        RecordedRequest request = server.takeRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("/api/v1/inbox", request.getPath());
        assertEquals("Bearer tt_test", request.getHeader("Authorization"));
        assertEquals("test.access", request.getHeader("CF-Access-Client-Id"));
        assertEquals("test-secret", request.getHeader("CF-Access-Client-Secret"));
        assertEquals(content, new JSONObject(request.getBody().readUtf8()).getString("content"));
    }
    @Test public void connectionCheckIsReadOnlyAndAcceptsJson() throws Exception {
        server.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody("[]"));
        ApiClient.verify(baseUrl, "tt_test", cloudflare);
        assertEquals("GET", server.takeRequest().getMethod());
    }
    @Test public void cloudflareHtmlIsNotReportedAsAValidConnection() {
        server.enqueue(new MockResponse().setHeader("Content-Type", "text/html").setBody("<html>Sign in</html>"));
        assertThrows(ApiClient.HttpError.class, () -> ApiClient.verify(baseUrl, "tt_test", cloudflare));
    }
    @Test public void redirectsAreNeverFollowed() {
        server.enqueue(new MockResponse().setResponseCode(302).setHeader("Location", "https://other.example.com"));
        assertThrows(ApiClient.HttpError.class, () -> ApiClient.createInboxItem(baseUrl, "tt_test", cloudflare, "A note"));
        assertEquals(1, server.getRequestCount());
    }
    @Test public void authenticationFailureDoesNotLeakServerResponse() {
        server.enqueue(new MockResponse().setResponseCode(403).setBody("private server details"));
        Exception error = assertThrows(ApiClient.HttpError.class, () -> ApiClient.verify(baseUrl, "tt_test", cloudflare));
        assertFalse(error.getMessage().contains("private server details"));
    }
}
