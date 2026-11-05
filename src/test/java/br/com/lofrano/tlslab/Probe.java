package br.com.lofrano.tlslab;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

final class Probe implements AutoCloseable {
    private final Children.Child child;

    private Probe(Children.Child child) {
        this.child = child;
    }

    static Probe open(Children children, FixturePki pki, int port, String trust,
                      String host, boolean delayed) throws Exception {
        Children.Child child = children.start(Children.javaCommand(ClientMain.class,
                Integer.toString(port), pki.path(trust).toString(), pki.path("password").toString(),
                host, delayed ? "delayed" : "normal"), true);
        try {
            Check.equal(child.nextLine(), delayed ? "CONNECTED" : "READY TLSv1.3",
                    "CLIENT_READY_REQUIRED");
            return new Probe(child);
        } catch (Exception | AssertionError failure) {
            child.close();
            throw failure;
        }
    }

    static void rejected(Children children, FixturePki pki, int port, String trust, String host)
            throws Exception {
        try (Children.Child child = children.start(Children.javaCommand(ClientMain.class,
                Integer.toString(port), pki.path(trust).toString(), pki.path("password").toString(),
                host, "normal"), true)) {
            Check.equal(child.nextLine(), "VERIFY_FAILED", "VERIFICATION_FAILURE_REQUIRED");
            Children.Result result = child.finish(8000);
            Check.equal(result.exit(), 10, "VERIFICATION_EXIT_REQUIRED");
            Check.that(!result.overflow() && !result.timedOut(), "VERIFICATION_MUST_FINISH");
        }
    }

    void handshake() throws Exception {
        child.send("HANDSHAKE\n");
        Check.equal(child.nextLine(), "READY TLSv1.3", "DELAYED_HANDSHAKE_REQUIRED");
    }

    String bytes(byte[] bytes) throws Exception {
        child.send("SEND " + Base64.getEncoder().encodeToString(bytes) + "\n");
        return child.nextLine();
    }

    String line(String text) throws Exception {
        return bytes((text + "\n").getBytes(StandardCharsets.UTF_8));
    }

    void reply(String text, long generation, int bytes) throws Exception {
        Check.equal(line(text), "REPLY GEN " + generation + " BYTES " + bytes,
                "APPLICATION_REPLY_REQUIRED");
    }

    @Override
    public void close() throws Exception {
        try {
            child.endInput();
            Children.Result result = child.finish(8000);
            Check.that(!result.timedOut() && !result.overflow() && result.exit() == 0,
                    "CLIENT_CLEAN_EXIT_REQUIRED");
        } finally {
            child.close();
        }
    }
}
