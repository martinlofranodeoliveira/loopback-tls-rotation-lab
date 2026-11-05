package br.com.lofrano.tlslab;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

final class Deadline {
    private final long end;

    Deadline(long seconds) {
        end = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
    }

    long millis(long localMaximum) throws IOException {
        long remaining = end - System.nanoTime();
        if (remaining <= 0) {
            throw new IOException("CONTROLLER_DEADLINE");
        }
        return Math.max(1, Math.min(localMaximum, TimeUnit.NANOSECONDS.toMillis(remaining)));
    }
}
