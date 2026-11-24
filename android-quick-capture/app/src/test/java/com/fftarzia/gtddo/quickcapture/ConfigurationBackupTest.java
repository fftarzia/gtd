package com.fftarzia.gtddo.quickcapture;

import static org.junit.Assert.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

public class ConfigurationBackupTest {
    private AppConfiguration config() {
        return new AppConfiguration("https://tasks.example.com", "tt_test_personal_token",
                new CloudflareCredentials("example.access", "private-service-secret"));
    }
    private char[] password() { return "test-only-passphrase-2026".toCharArray(); }
    @Test public void roundTripPreservesAllCredentialsWithoutPlaintext() throws Exception {
        byte[] encrypted = ConfigurationBackup.encrypt(config(), password());
        String bytes = new String(encrypted, StandardCharsets.ISO_8859_1);
        assertFalse(bytes.contains(config().apiToken));
        assertFalse(bytes.contains(config().cloudflare.getClientSecret()));
        assertFalse(bytes.contains(config().serverUrl));
        AppConfiguration restored = ConfigurationBackup.decrypt(encrypted, password());
        assertEquals(config().serverUrl, restored.serverUrl);
        assertEquals(config().apiToken, restored.apiToken);
        assertEquals(config().cloudflare.getClientId(), restored.cloudflare.getClientId());
        assertEquals(config().cloudflare.getClientSecret(), restored.cloudflare.getClientSecret());
    }
    @Test public void backupsUseFreshSaltAndNonce() throws Exception {
        assertFalse(java.util.Arrays.equals(ConfigurationBackup.encrypt(config(), password()),
                ConfigurationBackup.encrypt(config(), password())));
    }
    @Test public void wrongPasswordAndTamperingAreRejected() throws Exception {
        byte[] backup = ConfigurationBackup.encrypt(config(), password());
        assertThrows(java.security.GeneralSecurityException.class,
                () -> ConfigurationBackup.decrypt(backup, "another-test-passphrase".toCharArray()));
        backup[backup.length - 1] ^= 1;
        assertThrows(java.security.GeneralSecurityException.class,
                () -> ConfigurationBackup.decrypt(backup, password()));
    }
    @Test public void malformedOrOversizedFilesAreRejectedBeforeDecryption() {
        assertThrows(java.io.IOException.class, () -> ConfigurationBackup.decrypt(new byte[0], password()));
        assertThrows(java.io.IOException.class, () -> ConfigurationBackup.read(
                new ByteArrayInputStream(new byte[ConfigurationBackup.MAX_BYTES + 1])));
        assertThrows(IllegalArgumentException.class, () -> ConfigurationBackup.encrypt(config(), "short".toCharArray()));
    }
    @Test public void credentialsMustBeSafeForHeadersAndHttps() {
        assertThrows(IllegalArgumentException.class, () -> new AppConfiguration("http://tasks.example.com", "tt_token", null));
        assertThrows(IllegalArgumentException.class, () -> new AppConfiguration("https://tasks.example.com", "tt_a\r\nInjected:x", null));
        assertThrows(IllegalArgumentException.class, () -> new AppConfiguration("https://tasks.example.com", "", null));
    }
}
