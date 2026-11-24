package com.fftarzia.gtddo.quickcapture;

import static org.junit.Assert.*;
import org.junit.Test;

public class CloudflareCredentialsTest {
    @Test
    public void emptyPairIsOptionalForPrivateServers() {
        assertFalse(new CloudflareCredentials("", "").isConfigured());
        assertFalse(new CloudflareCredentials(null, null).isConfigured());
    }

    @Test
    public void completePairIsAccepted() {
        CloudflareCredentials creds =
                new CloudflareCredentials(" client-id ", " secret-value ");
        assertTrue(creds.isConfigured());
        assertEquals("client-id", creds.getClientId());
        assertEquals("secret-value", creds.getClientSecret());
    }

    @Test
    public void incompletePairsAreRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                new CloudflareCredentials("client-id", ""));
        assertThrows(IllegalArgumentException.class, () ->
                new CloudflareCredentials("", "secret-value"));
    }

    @Test
    public void controlCharactersAreRejectedToProtectHttpHeaders() {
        assertThrows(IllegalArgumentException.class, () ->
                new CloudflareCredentials("id\r\nInjected: true", "secret"));
        assertThrows(IllegalArgumentException.class, () ->
                new CloudflareCredentials("id", "secret\u0000junk"));
    }
}
