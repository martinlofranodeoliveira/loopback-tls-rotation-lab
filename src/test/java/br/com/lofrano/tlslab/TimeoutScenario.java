package br.com.lofrano.tlslab;

import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import javax.net.ssl.SSLSocket;

/** An actual TLS peer stays open without replying: timeout must not claim EOF. */
final class TimeoutScenario {
    private TimeoutScenario() { }

    static void run(Children children, FixturePki pki, Deadline deadline, Evidence evidence)
            throws Exception {
        try (ContextRegistry registry = pki.registry("a-server.p12");
             ServerSocketChannel listener = ServerSocketChannel.open();
             Selector selector = Selector.open()) {
            listener.configureBlocking(false);
            listener.bind(new InetSocketAddress("127.0.0.1", 0), 1);
            listener.register(selector, SelectionKey.OP_ACCEPT);
            int port = ((InetSocketAddress) listener.getLocalAddress()).getPort();
            try (Children.Child child = children.start(Children.javaCommand(ClientMain.class,
                    Integer.toString(port), pki.path("trust-a.p12").toString(),
                    pki.path("password").toString(), "localhost", "normal"), true)) {
                ContextRegistry.Captured accepted;
                do {
                    selector.select(deadline.millis(8000));
                    selector.selectedKeys().clear();
                    accepted = registry.accept(listener);
                } while (accepted == null);
                try (ContextRegistry.Captured owned = accepted; SSLSocket tls = owned.wrap()) {
                    tls.startHandshake();
                    Check.equal(child.nextLine(), "READY TLSv1.3", "TIMEOUT_PEER_READY");
                    child.send("SEND eAo=\n");
                    Check.equal(Frames.read(tls.getInputStream(), 1024).text(), "x",
                            "TIMEOUT_REQUEST_RECEIVED");
                    // No application response and no server close until the client reports.
                    Check.equal(child.nextLine(Limits.READ_MS + 4000L), "CLIENT_ERROR",
                            "TIMEOUT_IS_NOT_CLOSURE");
                    Children.Result result = child.finish(2000);
                    Check.that(!result.timedOut() && !result.overflow() && result.exit() == 20,
                            "TIMEOUT_CLIENT_FAILURE_REQUIRED");
                }
            }
        }
        Check.equal(children.count(), 0, "TIMEOUT_CLIENT_REAPED");
        evidence.add("timeout-not-closure");
    }
}
