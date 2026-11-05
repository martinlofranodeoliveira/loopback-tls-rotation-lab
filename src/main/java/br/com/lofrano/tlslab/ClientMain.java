package br.com.lofrano.tlslab;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.file.Path;
import java.security.cert.CertificateException;
import java.util.Base64;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocket;

/** Fixed local client control protocol; no arbitrary endpoint or algorithm arguments. */
public final class ClientMain {
    private ClientMain() { }

    private static void say(String message) {
        System.out.println(message);
        System.out.flush();
    }

    private static boolean certificateFailure(Throwable failure) {
        // Conservative classification for the fixed JDK21 standard-provider probes.
        // An EOF, protocol alert or transport failure alone cannot prove validation.
        for (int depth = 0; failure != null && depth < 16; depth++) {
            if (failure instanceof CertificateException) {
                return true;
            }
            failure = failure.getCause();
        }
        return false;
    }

    public static void main(String[] args) {
        int code;
        try {
            RuntimeGate.check();
            code = run(args);
        } catch (Exception failure) {
            say("CLIENT_ERROR");
            code = 20;
        }
        if (code != 0) {
            System.exit(code);
        }
    }

    private static int run(String[] args) throws Exception {
        if (args.length != 5 || (!args[4].equals("normal") && !args[4].equals("delayed"))) {
            throw new IllegalArgumentException("INVALID_CLIENT_ARGUMENTS");
        }
        int port = Integer.parseInt(args[0]);
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("INVALID_PORT");
        }
        try (Socket raw = ClientTls.raw(port)) {
            if (args[4].equals("delayed")) {
                say("CONNECTED");
                Frames.Line command = Frames.read(System.in, 2048);
                if (command == null || !command.text().equals("HANDSHAKE")) {
                    return 20;
                }
            }
            try (SSLSocket tls = ClientTls.wrap(raw, port, args[3], Path.of(args[1]), Path.of(args[2]))) {
                try {
                    tls.startHandshake();
                } catch (SSLException verificationFailure) {
                    if (!certificateFailure(verificationFailure)) {
                        throw verificationFailure;
                    }
                    say("VERIFY_FAILED");
                    return 10;
                }
                if (!tls.getSession().getProtocol().equals("TLSv1.3")) {
                    return 20;
                }
                tls.setSoTimeout(Limits.READ_MS);
                say("READY TLSv1.3");
                for (int commands = 0; commands < 18; commands++) {
                    Frames.Line command = Frames.read(System.in, 2048);
                    if (command == null || command.text().equals("QUIT")) {
                        return 0;
                    }
                    if (!command.text().startsWith("SEND ")) {
                        return 20;
                    }
                    byte[] bytes = Base64.getDecoder().decode(command.text().substring(5));
                    if (bytes.length > 1026) {
                        return 20;
                    }
                    try {
                        tls.getOutputStream().write(bytes);
                        tls.getOutputStream().flush();
                        Frames.Line reply = Frames.read(tls.getInputStream(), 64);
                        if (reply == null) {
                            say("CLOSED");
                            return 0;
                        }
                        say("REPLY " + reply.text());
                    } catch (SocketTimeoutException noClosureProof) {
                        // Abort our underlying transport before TLS resource cleanup: waiting
                        // for close_notify from this silent peer would add another read wait.
                        raw.close();
                        throw noClosureProof;
                    } catch (IOException connectionClosed) {
                        say("CLOSED");
                        return 0;
                    }
                }
                return 20;
            }
        }
    }
}
