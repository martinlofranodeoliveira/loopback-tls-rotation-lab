package br.com.lofrano.tlslab;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Offline asserting runner shared by test and demo. */
public final class LabMain {
    private LabMain() { }

    public static void main(String[] args) {
        try {
            RuntimeGate.check();
            if (args.length != 1 || (!args[0].equals("test") && !args[0].equals("demo"))) {
                throw new IllegalArgumentException("USE_TEST_OR_DEMO");
            }
            run(args[0]);
        } catch (Exception | AssertionError failure) {
            String message = failure.getMessage();
            if (message == null || !message.matches("[A-Z0-9_]{1,120}")) {
                message = "NORMALIZED_FAILURE";
            }
            System.err.println("FAIL " + failure.getClass().getSimpleName() + " " + message);
            System.exit(1);
        }
    }

    private static void run(String mode) throws Exception {
        Evidence evidence = new Evidence();
        Deadline deadline = new Deadline(300);
        AtomicReference<LoopbackServer> service = new AtomicReference<>();
        Children children = new Children(deadline);
        var watchdog = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "controller-deadline");
            thread.setDaemon(true);
            return thread;
        });
        watchdog.schedule(() -> {
            children.cancelAll();
            LoopbackServer server = service.get();
            if (server != null) {
                server.abort();
            }
        }, 300, TimeUnit.SECONDS);
        Path fixtureDirectory;
        try (children) {
            FrameTests.run(evidence);
            ProcessTests.run(children, evidence);
            try (FixturePki pki = FixturePki.create(children)) {
                fixtureDirectory = pki.root();
                RegistryTests.run(pki, evidence);
                TransportScenario.run(children, pki, deadline, evidence);
                TimeoutScenario.run(children, pki, deadline, evidence);
                try (ContextRegistry registry = pki.registry("a-server.p12")) {
                    LoopbackServer server = new LoopbackServer(registry);
                    service.set(server);
                    try (server) {
                        RotationScenario.run(children, pki, registry, server, deadline, evidence);
                        ServerBoundsScenario.run(children, pki, server, deadline, evidence);
                        Check.equal(registry.status().generation(), 2L, "FINAL_GENERATION");
                    } finally {
                        service.set(null);
                    }
                    Check.equal(server.status().active(), 0, "SHUTDOWN_ACTIVE_ZERO");
                    Check.that(!server.status().failed(), "SHUTDOWN_SERVER_HEALTH");
                }
                Check.equal(children.count(), 0, "SCENARIO_CHILDREN_ZERO");
            }
            Check.that(!Files.exists(fixtureDirectory), "FIXTURES_REMOVED");
            deadline.millis(1);
        } finally {
            watchdog.shutdownNow();
            Check.that(watchdog.awaitTermination(10, TimeUnit.SECONDS), "WATCHDOG_REAPED");
        }
        Check.equal(children.count(), 0, "FINAL_CHILDREN_ZERO");
        evidence.add("cleanup");
        evidence.emit(mode);
    }
}
