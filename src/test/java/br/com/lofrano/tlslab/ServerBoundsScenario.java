package br.com.lofrano.tlslab;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;

final class ServerBoundsScenario {
    private ServerBoundsScenario() { }

    static void run(Children children, FixturePki pki, LoopbackServer server, Deadline deadline,
                    Evidence evidence) throws Exception {
        int port = server.port();
        try (Probe probe = Probe.open(children, pki, port, "trust-b.p12", "localhost", false)) {
            probe.reply("x".repeat(1024), 2, 1024);
        }
        try (Probe probe = Probe.open(children, pki, port, "trust-b.p12", "localhost", false)) {
            Check.equal(probe.line("x".repeat(1025)), "CLOSED", "1025_BYTES_MUST_CLOSE");
        }
        evidence.add("line-boundaries");
        try (Probe probe = Probe.open(children, pki, port, "trust-b.p12", "localhost", false)) {
            Check.equal(probe.bytes(new byte[] {(byte) 0xc0, (byte) 0xaf, '\n'}), "CLOSED",
                    "INVALID_UTF8_MUST_CLOSE");
        }
        try (Probe probe = Probe.open(children, pki, port, "trust-b.p12", "localhost", false)) {
            Check.equal(probe.bytes(new byte[] {'x', '\r', '\n'}), "CLOSED", "CR_MUST_CLOSE");
        }
        evidence.add("invalid-frames");
        try (Probe probe = Probe.open(children, pki, port, "trust-b.p12", "localhost", false)) {
            for (int i = 0; i < 16; i++) {
                probe.reply("", 2, 0);
            }
            Check.equal(probe.line("seventeenth"), "CLOSED", "EXCHANGE_LIMIT_MUST_CLOSE");
        }
        evidence.add("exchange-bound");
        server.await(s -> s.active() == 0, deadline.millis(6000));
        List<Socket> stalled = new ArrayList<>();
        try {
            for (int i = 0; i < 4; i++) {
                long before = server.status().accepted();
                stalled.add(ClientTls.raw(port));
                server.await(s -> s.accepted() > before, deadline.millis(1000));
            }
            Check.equal(server.status().active(), 4, "FOUR_STALLED_ADMITTED");
            long rejected = server.status().rejected();
            try (Socket fifth = ClientTls.raw(port)) {
                fifth.setSoTimeout((int) deadline.millis(3000));
                server.await(s -> s.rejected() > rejected, deadline.millis(1000));
                assertPeerClosed(fifth);
            }
            // A notified active=0 barrier, not a period of silence, proves worker release.
            server.await(s -> s.active() == 0, deadline.millis(6000));
            for (Socket socket : stalled) {
                socket.setSoTimeout((int) deadline.millis(3000));
                assertPeerClosed(socket);
            }
        } finally {
            for (Socket socket : stalled) {
                socket.close();
            }
        }
        Check.that(!server.status().failed(), "SERVER_HEALTH_AFTER_STALL");
        evidence.add("admission-and-stall");
    }

    private static void assertPeerClosed(Socket socket) throws IOException {
        try {
            // JSSE may send a bounded fatal-alert record when its handshake times out.
            // Drain those bytes and require actual EOF; a timeout is never closure proof.
            int drained = 0;
            while (socket.getInputStream().read() != -1) {
                Check.that(++drained <= 256, "PEER_CLOSE_DRAIN_BOUND");
            }
        } catch (SocketException reset) {
            // An explicit TCP reset also proves closure; a read timeout does not.
        }
    }
}
