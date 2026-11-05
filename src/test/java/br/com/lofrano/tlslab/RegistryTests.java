package br.com.lofrano.tlslab;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

final class RegistryTests {
    private RegistryTests() { }

    static void run(FixturePki pki, Evidence evidence) throws Exception {
        Check.rejects(IllegalArgumentException.class,
                () -> new ContextRegistry(pki.path("a-server.p12"), new char[129]),
                "PASSWORD_INPUT_BOUND");
        Check.rejects(IllegalArgumentException.class,
                () -> new ContextRegistry(pki.path("a-server.p12"), new char[0]),
                "PASSWORD_EMPTY_REJECTED");
        try (ContextRegistry registry = pki.registry("a-server.p12")) {
            try {
                registry.loadCandidate(pki.root().resolve("private-name-must-not-appear.p12"));
                throw new AssertionError("MISSING_BUNDLE_ACCEPTED");
            } catch (IOException rejected) {
                Check.equal(rejected.getMessage(), "CANDIDATE_REJECTED", "PRIVATE_PATH_NORMALIZED");
                Check.that(rejected.getCause() == null && rejected.getSuppressed().length == 0,
                        "PRIVATE_PATH_NOT_RETAINED");
            }
            ContextRegistry.Candidate candidate = registry.loadCandidate(pki.path("b-server.p12"));
            Check.equal(registry.status().generation(), 1L, "LOAD_MUST_NOT_PUBLISH");
            Check.rejects(IOException.class, () -> registry.loadCandidate(pki.path("corrupt.p12")),
                    "CORRUPT_BUNDLE");
            Check.rejects(IOException.class, () -> registry.loadCandidate(pki.root()), "DIRECTORY_BUNDLE");
            Files.createSymbolicLink(pki.path("alias-link"), pki.path("a-server.p12").getFileName());
            Check.rejects(IOException.class, () -> registry.loadCandidate(pki.path("alias-link")),
                    "SYMLINK_BUNDLE");
            PrivateFiles.write(pki.path("oversize.p12"), new byte[Limits.FILE_BYTES + 1]);
            Check.rejects(IOException.class, () -> registry.loadCandidate(pki.path("oversize.p12")),
                    "OVERSIZE_BUNDLE");
            PrivateFiles.write(pki.path("public-password"), new byte[64]);
            Files.setPosixFilePermissions(pki.path("public-password"),
                    PosixFilePermissions.fromString("rw-r--r--"));
            Check.rejects(IOException.class, () -> {
                try (Password ignored = Password.read(pki.path("public-password"))) {
                    throw new AssertionError("PUBLIC_PASSWORD_ACCEPTED");
                }
            }, "PUBLIC_PASSWORD");
            Check.equal(registry.status().generation(), 1L, "FAILED_LOAD_ATOMICITY");
            Check.equal(registry.publish(2, candidate), ContextRegistry.Publication.STALE_GENERATION,
                    "STALE_RESULT");
            Check.equal(registry.status().generation(), 1L, "STALE_ATOMICITY");
            Check.equal(registry.publish(1, candidate), ContextRegistry.Publication.PUBLISHED,
                    "VALID_PUBLICATION");
            Check.equal(registry.publish(2, candidate), ContextRegistry.Publication.PUBLISHED,
                    "OPAQUE_CANDIDATE_REUSE");
            Check.equal(registry.status().generation(), 3L, "MONOTONIC_GENERATION");
        }
        evidence.add("candidate-atomicity");

        try (ContextRegistry registry = pki.registry("a-server.p12")) {
            ContextRegistry.Candidate a = registry.loadCandidate(pki.path("a-server.p12"));
            ContextRegistry.Candidate b = registry.loadCandidate(pki.path("b-server.p12"));
            var executor = Executors.newFixedThreadPool(2);
            CountDownLatch start = new CountDownLatch(1);
            try {
                var first = executor.submit(() -> { start.await(); return registry.publish(1, a); });
                var second = executor.submit(() -> { start.await(); return registry.publish(1, b); });
                start.countDown();
                var x = first.get(5, TimeUnit.SECONDS);
                var y = second.get(5, TimeUnit.SECONDS);
                Check.that((x == ContextRegistry.Publication.PUBLISHED
                        && y == ContextRegistry.Publication.STALE_GENERATION)
                        || (y == ContextRegistry.Publication.PUBLISHED
                        && x == ContextRegistry.Publication.STALE_GENERATION), "EXACTLY_ONE_CAS_WINNER");
                Check.equal(registry.status().generation(), 2L, "RACE_GENERATION");
            } finally {
                executor.shutdownNow();
                Check.that(executor.awaitTermination(5, TimeUnit.SECONDS), "RACE_THREADS_REAPED");
            }
        }
        evidence.add("publish-race");
        Check.equal(ContextRegistry.advance(1), 2L, "GENERATION_SUCCESSOR");
        Check.rejects(ArithmeticException.class, () -> ContextRegistry.advance(Long.MAX_VALUE),
                "GENERATION_OVERFLOW");
        Check.equal(Limits.incrementSaturated(Long.MAX_VALUE), Long.MAX_VALUE, "COUNTER_SATURATION");
        evidence.add("generation-exhaustion");
    }
}
