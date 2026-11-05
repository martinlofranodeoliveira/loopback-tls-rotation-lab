package br.com.lofrano.tlslab;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

final class PrivateFiles {
    private PrivateFiles() { }

    static Path directory() throws IOException {
        Path parent = Path.of("build", "private");
        Files.createDirectories(parent);
        if (Files.isSymbolicLink(parent)) {
            throw new IOException("UNSAFE_DIRECTORY");
        }
        Files.setPosixFilePermissions(parent, PosixFilePermissions.fromString("rwx------"));
        return Files.createTempDirectory(parent, "run-",
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
    }

    static void write(Path path, byte[] data) throws IOException {
        Files.createFile(path, PosixFilePermissions.asFileAttribute(
                PosixFilePermissions.fromString("rw-------")));
        Files.write(path, data, StandardOpenOption.WRITE);
    }

    static void secure(Path path) throws IOException {
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(path)) {
                throw new IOException("SYMLINK_REJECTED");
            }
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
        }
    }

    static byte[] read(Path path, int limit) throws IOException {
        if (limit < 1 || limit > Limits.FILE_BYTES) {
            throw new IllegalArgumentException("INVALID_FILE_BOUND");
        }
        BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isRegularFile() || attributes.size() > limit) {
            throw new IOException("INVALID_BOUNDED_FILE");
        }
        if (!Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS).equals(
                PosixFilePermissions.fromString("rw-------"))) {
            throw new IOException("PRIVATE_MODE_REQUIRED");
        }
        try (var channel = Files.newByteChannel(path,
                Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS));
             InputStream input = Channels.newInputStream(channel)) {
            byte[] bytes = input.readNBytes(limit + 1);
            if (bytes.length > limit) {
                java.util.Arrays.fill(bytes, (byte) 0);
                throw new IOException("FILE_TOO_LARGE");
            }
            return bytes;
        }
    }
}
