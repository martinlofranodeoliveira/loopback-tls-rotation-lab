package br.com.lofrano.tlslab;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Arrays;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;

final class ClientTls {
    private ClientTls() { }

    static Socket raw(int port) throws IOException {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress("127.0.0.1", port), Limits.HANDSHAKE_MS);
            return socket;
        } catch (IOException failure) {
            socket.close();
            throw failure;
        }
    }

    static SSLSocket wrap(Socket raw, int port, String host, Path trust, Path passwordFile)
            throws IOException, GeneralSecurityException {
        if (!host.equals("localhost") && !host.equals("wrong.example")) {
            throw new IllegalArgumentException("INVALID_LOGICAL_HOST");
        }
        byte[] bytes = PrivateFiles.read(trust, Limits.FILE_BYTES);
        char[] secret = null;
        try (Password password = Password.read(passwordFile)) {
            secret = password.copy();
            KeyStore store = KeyStore.getInstance("PKCS12");
            store.load(new ByteArrayInputStream(bytes), secret);
            TrustManagerFactory managers = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            managers.init(store);
            // A new context for every connection, with no default trust or client key managers.
            SSLContext context = SSLContext.getInstance("TLSv1.3");
            context.init(new KeyManager[0], managers.getTrustManagers(), null);
            SSLSocket tls = (SSLSocket) context.getSocketFactory().createSocket(raw, host, port, true);
            tls.setUseClientMode(true);
            SSLParameters parameters = tls.getSSLParameters();
            parameters.setProtocols(new String[] { "TLSv1.3" });
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            tls.setSSLParameters(parameters);
            tls.setSoTimeout(Limits.HANDSHAKE_MS);
            return tls;
        } finally {
            Arrays.fill(bytes, (byte) 0);
            if (secret != null) {
                Arrays.fill(secret, '\0');
            }
        }
    }
}
