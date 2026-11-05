package br.com.lofrano.tlslab;

import java.util.concurrent.CountDownLatch;

public final class ProcessTests {
    private ProcessTests() { }

    public static void main(String[] args) throws Exception {
        RuntimeGate.check();
        if (args.length == 1 && args[0].equals("flood")) {
            byte[] block = new byte[4096];
            java.util.Arrays.fill(block, (byte) 'x');
            for (int i = 0; i < 64; i++) {
                System.out.write(block);
            }
            System.out.flush();
        } else if (args.length == 1 && args[0].equals("stall")) {
            System.out.println("BLOCKED");
            System.out.flush();
            new CountDownLatch(1).await();
        } else {
            throw new IllegalArgumentException("INVALID_PROCESS_FIXTURE");
        }
    }

    static void run(Children children, Evidence evidence) throws Exception {
        Children.Result flood = children.run(Children.javaCommand(ProcessTests.class, "flood"), 8000);
        Check.that(flood.overflow() && flood.out().length() <= Limits.CHILD_BYTES,
                "CHILD_OUTPUT_MUST_BE_BOUNDED");
        Check.equal(children.count(), 0, "FLOOD_CHILD_REAPED");
        evidence.add("child-output-bound");
        try (Children.Child child = children.start(Children.javaCommand(ProcessTests.class, "stall"), true)) {
            Check.equal(child.nextLine(), "BLOCKED", "CHILD_BLOCK_BARRIER");
            Children.Result stalled = child.finish(200);
            Check.that(stalled.timedOut(), "STALLED_CHILD_MUST_TIMEOUT");
        }
        Check.equal(children.count(), 0, "STALLED_CHILD_REAPED");
        evidence.add("child-deadline");
    }
}
