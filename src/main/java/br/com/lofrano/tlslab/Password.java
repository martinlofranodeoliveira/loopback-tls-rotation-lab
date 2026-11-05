package br.com.lofrano.tlslab;

import java.io.IOException;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Arrays;

final class Password implements AutoCloseable {
    private final char[] value;

    private Password(char[] value) {
        this.value = value;
    }

    static void create(Path path) throws IOException {
        byte[] random = new byte[32];
        byte[] encoded = new byte[64];
        byte[] alphabet = "0123456789abcdef".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        try {
            new SecureRandom().nextBytes(random);
            for (int i = 0; i < random.length; i++) {
                encoded[i * 2] = alphabet[(random[i] & 255) >>> 4];
                encoded[i * 2 + 1] = alphabet[random[i] & 15];
            }
            PrivateFiles.write(path, encoded);
        } finally {
            Arrays.fill(random, (byte) 0);
            Arrays.fill(encoded, (byte) 0);
        }
    }

    static Password read(Path path) throws IOException {
        byte[] bytes = PrivateFiles.read(path, 128);
        try {
            if (bytes.length < 32) {
                throw new IOException("INVALID_PASSWORD_FILE");
            }
            char[] chars = new char[bytes.length];
            for (int i = 0; i < bytes.length; i++) {
                if (bytes[i] < 33 || bytes[i] > 126) {
                    Arrays.fill(chars, '\0');
                    throw new IOException("INVALID_PASSWORD_FILE");
                }
                chars[i] = (char) bytes[i];
            }
            return new Password(chars);
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    char[] copy() {
        return value.clone();
    }

    @Override
    public void close() {
        Arrays.fill(value, '\0');
    }
}
