package br.com.lofrano.tlslab;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;

/** In-memory context selection, not a certificate-policy auditor. */
public final class ContextRegistry implements AutoCloseable {
    public enum Publication { PUBLISHED, STALE_GENERATION, GENERATION_EXHAUSTED }
    public record Status(long generation) { }

    public static final class Candidate {
        private final SSLContext context;
        private Candidate(SSLContext context) {
            this.context = context;
        }
    }

    private record Live(long generation, Candidate candidate) { }

    static final class Captured implements AutoCloseable {
        private final Socket socket;
        private final long generation;
        private final Candidate candidate;

        private Captured(Socket socket, Live live) {
            this.socket = socket;
            this.generation = live.generation();
            this.candidate = live.candidate();
        }

        long generation() { return generation; }

        SSLSocket wrap() throws IOException {
            SSLSocket tls = (SSLSocket) candidate.context.getSocketFactory().createSocket(
                    socket, "localhost", socket.getPort(), true);
            tls.setUseClientMode(false);
            tls.setEnabledProtocols(new String[] { "TLSv1.3" });
            tls.setNeedClientAuth(false);
            tls.setSoTimeout(Limits.HANDSHAKE_MS);
            return tls;
        }

        @Override
        public void close() {
            try {
                socket.close();
            } catch (IOException ignored) {
                // Closing an already failed, locally owned socket is idempotent.
            }
        }
    }

    private final Object publicationLock = new Object();
    private final char[] password;
    private final AtomicReference<Live> current;
    private boolean closed;

    public ContextRegistry(Path initialBundle, char[] password) throws IOException {
        Objects.requireNonNull(password);
        if (password.length < 1 || password.length > 128) {
            throw new IllegalArgumentException("INVALID_PASSWORD_BOUND");
        }
        this.password = password.clone();
        Candidate initial;
        try {
            initial = build(initialBundle, this.password);
        } catch (IOException | RuntimeException failure) {
            Arrays.fill(this.password, '\0');
            throw failure;
        }
        current = new AtomicReference<>(new Live(1, initial));
    }

    public Candidate loadCandidate(Path path) throws IOException {
        char[] local;
        synchronized (publicationLock) {
            requireOpen();
            local = password.clone();
        }
        try {
            return build(path, local);
        } finally {
            Arrays.fill(local, '\0');
        }
    }

    public Publication publish(long expectedGeneration, Candidate candidate) {
        Objects.requireNonNull(candidate);
        if (expectedGeneration < 1) {
            throw new IllegalArgumentException("INVALID_GENERATION");
        }
        synchronized (publicationLock) {
            requireOpen();
            Live before = current.get();
            if (before.generation() != expectedGeneration) {
                return Publication.STALE_GENERATION;
            }
            if (before.generation() == Long.MAX_VALUE) {
                return Publication.GENERATION_EXHAUSTED;
            }
            Live replacement = new Live(advance(before.generation()), candidate);
            if (!current.compareAndSet(before, replacement)) {
                throw new IllegalStateException("PUBLICATION_INVARIANT");
            }
            return Publication.PUBLISHED;
        }
    }

    public Status status() {
        return new Status(current.get().generation());
    }

    static long advance(long generation) {
        return Math.incrementExact(generation);
    }

    Captured accept(ServerSocketChannel listener) throws IOException {
        synchronized (publicationLock) {
            requireOpen();
            // Nonblocking accept and generation capture share the publication lock.
            SocketChannel accepted = listener.accept();
            if (accepted == null) {
                return null;
            }
            return new Captured(accepted.socket(), current.get());
        }
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("REGISTRY_CLOSED");
        }
    }

    private static Candidate build(Path path, char[] password) throws IOException {
        byte[] bytes;
        try {
            bytes = PrivateFiles.read(path, Limits.FILE_BYTES);
        } catch (IOException readFailure) {
            // Filesystem exceptions can embed the private caller path. The API's
            // normalized failure must not retain them as a cause or suppressed data.
            throw new IOException("CANDIDATE_REJECTED");
        }
        try {
            KeyStore keys = KeyStore.getInstance("PKCS12");
            keys.load(new ByteArrayInputStream(bytes), password);
            if (!keys.isKeyEntry("server") || keys.getCertificateChain("server") == null
                    || keys.getCertificateChain("server").length != 2) {
                throw new GeneralSecurityException("INVALID_SERVER_ALIAS");
            }
            var aliases = keys.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                if (keys.isKeyEntry(alias) && !alias.equals("server")) {
                    throw new GeneralSecurityException("EXTRA_KEY_ALIAS");
                }
            }
            KeyManagerFactory keyManagers = KeyManagerFactory.getInstance(
                    KeyManagerFactory.getDefaultAlgorithm());
            keyManagers.init(keys, password);
            KeyStore emptyTrust = KeyStore.getInstance("PKCS12");
            emptyTrust.load(null, null);
            TrustManagerFactory trustManagers = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            trustManagers.init(emptyTrust);
            SSLContext context = SSLContext.getInstance("TLSv1.3");
            context.init(keyManagers.getKeyManagers(), trustManagers.getTrustManagers(), null);
            context.getServerSessionContext().setSessionCacheSize(16);
            context.getServerSessionContext().setSessionTimeout(30);
            return new Candidate(context);
        } catch (GeneralSecurityException | IOException failure) {
            // Deliberately do not expose provider diagnostics, paths or key material.
            throw new IOException("CANDIDATE_REJECTED");
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    @Override
    public void close() {
        synchronized (publicationLock) {
            closed = true;
            Arrays.fill(password, '\0');
        }
    }
}
