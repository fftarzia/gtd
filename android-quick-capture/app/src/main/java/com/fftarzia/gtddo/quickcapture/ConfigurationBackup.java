package com.fftarzia.gtddo.quickcapture;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/** Portable encrypted backup. Contains configuration only, never captured notes. */
public final class ConfigurationBackup {
    private static final byte[] MAGIC = "GTDDO-BACKUP-1\n".getBytes(StandardCharsets.US_ASCII);
    private static final int SALT_LENGTH = 16;
    private static final int IV_LENGTH = 12;
    private static final int ITERATIONS = 600_000;
    public static final int MAX_BYTES = 40_960;

    private ConfigurationBackup() {}

    public static byte[] encrypt(AppConfiguration config, char[] password)
            throws GeneralSecurityException, IOException {
        validatePassword(password);
        byte[] salt = new byte[SALT_LENGTH];
        byte[] iv = new byte[IV_LENGTH];
        SecureRandom random = new SecureRandom();
        random.nextBytes(salt);
        random.nextBytes(iv);
        ByteArrayOutputStream plain = new ByteArrayOutputStream();
        try (DataOutputStream fields = new DataOutputStream(plain)) {
            fields.writeUTF(config.serverUrl);
            fields.writeUTF(config.apiToken);
            fields.writeUTF(config.cloudflare.getClientId());
            fields.writeUTF(config.cloudflare.getClientSecret());
        }
        byte[] plaintext = plain.toByteArray();
        byte[] encrypted;
        try {
            encrypted = cipher(Cipher.ENCRYPT_MODE, password, salt, iv).doFinal(plaintext);
        } finally {
            Arrays.fill(plaintext, (byte) 0);
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(MAGIC);
        output.write(salt);
        output.write(iv);
        output.write(encrypted);
        byte[] backup = output.toByteArray();
        if (backup.length > MAX_BYTES) throw new IOException("Configurazione troppo grande.");
        return backup;
    }

    public static AppConfiguration decrypt(byte[] backup, char[] password)
            throws GeneralSecurityException, IOException {
        validatePassword(password);
        int header = MAGIC.length + SALT_LENGTH + IV_LENGTH;
        if (backup == null || backup.length < header + 16 || backup.length > MAX_BYTES
                || !Arrays.equals(MAGIC, Arrays.copyOf(backup, MAGIC.length))) {
            throw new IOException("Il file non è un backup GTDdo valido.");
        }
        byte[] salt = Arrays.copyOfRange(backup, MAGIC.length, MAGIC.length + SALT_LENGTH);
        byte[] iv = Arrays.copyOfRange(backup, MAGIC.length + SALT_LENGTH, header);
        byte[] plaintext = cipher(Cipher.DECRYPT_MODE, password, salt, iv)
                .doFinal(backup, header, backup.length - header);
        try (DataInputStream fields = new DataInputStream(new ByteArrayInputStream(plaintext))) {
            AppConfiguration configuration = new AppConfiguration(fields.readUTF(), fields.readUTF(),
                    new CloudflareCredentials(fields.readUTF(), fields.readUTF()));
            if (fields.available() != 0) throw new IOException("Formato del backup non valido.");
            return configuration;
        } finally {
            Arrays.fill(plaintext, (byte) 0);
        }
    }

    public static byte[] read(InputStream input) throws IOException {
        if (input == null) throw new IOException("File non leggibile.");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (output.size() + count > MAX_BYTES) throw new IOException("Il backup è troppo grande.");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static Cipher cipher(int mode, char[] password, byte[] salt, byte[] iv)
            throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, 256);
        byte[] key = null;
        try {
            key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            cipher.updateAAD(MAGIC);
            return cipher;
        } finally {
            spec.clearPassword();
            if (key != null) Arrays.fill(key, (byte) 0);
        }
    }

    private static void validatePassword(char[] password) {
        if (password == null || password.length < 12 || password.length > 256) {
            throw new IllegalArgumentException("Usa una password da 12 a 256 caratteri per il backup.");
        }
    }
}
