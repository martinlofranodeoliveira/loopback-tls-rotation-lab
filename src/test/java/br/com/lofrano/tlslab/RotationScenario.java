package br.com.lofrano.tlslab;

import java.io.IOException;

final class RotationScenario {
    private RotationScenario() { }

    static void run(Children children, FixturePki pki, ContextRegistry registry,
                    LoopbackServer server, Deadline deadline, Evidence evidence) throws Exception {
        int port = server.port();
        try (Probe probe = Probe.open(children, pki, port, "trust-a.p12", "localhost", false)) {
            probe.reply("before", 1, 6);
        }
        evidence.add("before-a");
        Probe.rejected(children, pki, port, "trust-b.p12", "localhost");
        evidence.add("before-b-rejected");
        try (Probe probe = Probe.open(children, pki, port, "trust-both.p12", "localhost", false)) {
            probe.reply("overlap", 1, 7);
        }
        evidence.add("before-overlap");
        OpenSslOracle.check(children, pki, port, "a", "b", 1);
        evidence.add("openssl-before");
        server.await(s -> s.active() == 0, deadline.millis(6000));

        ContextRegistry.Candidate replacement = registry.loadCandidate(pki.path("b-server.p12"));
        Check.rejects(IOException.class, () -> registry.loadCandidate(pki.path("corrupt.p12")),
                "LIVE_CORRUPT_LOAD");
        Check.equal(registry.status().generation(), 1L, "LIVE_CORRUPT_ATOMICITY");
        Check.equal(registry.publish(2, replacement), ContextRegistry.Publication.STALE_GENERATION,
                "LIVE_STALE_RESULT");
        Check.equal(registry.status().generation(), 1L, "LIVE_STALE_ATOMICITY");

        try (Probe established = Probe.open(children, pki, port, "trust-a.p12", "localhost", false)) {
            established.reply("first", 1, 5);
            long before = server.status().accepted();
            try (Probe delayed = Probe.open(children, pki, port, "trust-a.p12", "localhost", true)) {
                LoopbackServer.Snapshot barrier = server.await(s -> s.accepted() > before,
                        deadline.millis(1000));
                Check.equal(barrier.lastGeneration(), 1L, "ACCEPT_CAPTURE_BARRIER");
                Check.equal(barrier.active(), 2, "PRE_HANDSHAKE_SOCKET_ADMITTED");
                Check.equal(registry.publish(1, replacement), ContextRegistry.Publication.PUBLISHED,
                        "ROTATION_PUBLICATION");
                Check.equal(registry.status().generation(), 2L, "PUBLICATION_BARRIER");
                delayed.handshake();
                delayed.reply("late", 1, 4);
                evidence.add("accepted-before-handshake");
                established.reply("second", 1, 6);
                evidence.add("existing-session");
            }
        }
        try (Probe probe = Probe.open(children, pki, port, "trust-b.p12", "localhost", false)) {
            probe.reply("after", 2, 5);
        }
        evidence.add("after-b");
        Probe.rejected(children, pki, port, "trust-a.p12", "localhost");
        evidence.add("after-a-rejected");
        try (Probe probe = Probe.open(children, pki, port, "trust-both.p12", "localhost", false)) {
            probe.reply("overlap", 2, 7);
        }
        evidence.add("after-overlap");
        Probe.rejected(children, pki, port, "trust-both.p12", "wrong.example");
        evidence.add("wrong-host");
        OpenSslOracle.check(children, pki, port, "b", "a", 2);
        evidence.add("openssl-after");
    }
}
