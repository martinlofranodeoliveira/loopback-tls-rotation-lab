package br.com.lofrano.tlslab;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

/** Premature TCP EOF is not evidence of certificate rejection. */
final class TransportScenario {
    private TransportScenario() { }

    static void run(Children children, FixturePki pki, Deadline deadline, Evidence evidence)
            throws Exception {
        try (ServerSocket listener = new ServerSocket()) {
            listener.bind(new InetSocketAddress(InetAddress.getByAddress(
                    new byte[] {127, 0, 0, 1}), 0), 1);
            listener.setSoTimeout((int) deadline.millis(8000));
            try (Children.Child child = children.start(Children.javaCommand(ClientMain.class,
                    Integer.toString(listener.getLocalPort()), pki.path("trust-a.p12").toString(),
                    pki.path("password").toString(), "localhost", "normal"), true)) {
                try (Socket accepted = listener.accept()) {
                    // A real transport is accepted, then closed before any TLS certificate.
                    accepted.shutdownOutput();
                }
                Check.equal(child.nextLine(), "CLIENT_ERROR", "EOF_IS_NOT_VERIFICATION");
                Children.Result result = child.finish(8000);
                Check.that(!result.timedOut() && !result.overflow() && result.exit() == 20,
                        "PREMATURE_EOF_SETUP_FAILURE_REQUIRED");
            }
        }
        Check.equal(children.count(), 0, "EOF_CLIENT_REAPED");
        evidence.add("transport-not-verification");
    }
}
