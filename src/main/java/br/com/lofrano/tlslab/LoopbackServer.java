package br.com.lofrano.tlslab;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import javax.net.ssl.SSLSocket;

final class LoopbackServer implements AutoCloseable {
    record Snapshot(int active, long accepted, long rejected, long finished,
                    long lastGeneration, boolean failed) { }

    private final ContextRegistry registry;
    private final ServerSocketChannel listener;
    private final Selector selector;
    private final Thread acceptor;
    private final ThreadPoolExecutor workers;
    private final Object monitor = new Object();
    private final Set<ContextRegistry.Captured> active = new HashSet<>();
    private volatile boolean running = true;
    private long accepted;
    private long rejected;
    private long finished;
    private long lastGeneration;
    private boolean failed;

    LoopbackServer(ContextRegistry registry) throws IOException {
        this.registry = registry;
        listener = ServerSocketChannel.open();
        selector = openSelector(listener);
        try {
            listener.configureBlocking(false);
            listener.bind(new InetSocketAddress(
                    InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), 0), 8);
            listener.register(selector, SelectionKey.OP_ACCEPT);
        } catch (IOException | RuntimeException failure) {
            listener.close();
            selector.close();
            throw failure;
        }
        workers = new ThreadPoolExecutor(4, 4, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(4), task -> new Thread(task, "tls-worker"));
        acceptor = new Thread(this::acceptLoop, "tls-acceptor");
        acceptor.start();
    }

    private static Selector openSelector(ServerSocketChannel ownedListener) throws IOException {
        try {
            return Selector.open();
        } catch (IOException | RuntimeException failure) {
            try {
                ownedListener.close();
            } catch (IOException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    int port() throws IOException {
        return ((InetSocketAddress) listener.getLocalAddress()).getPort();
    }

    Snapshot status() {
        synchronized (monitor) {
            return new Snapshot(active.size(), accepted, rejected, finished, lastGeneration, failed);
        }
    }

    Snapshot await(Predicate<Snapshot> condition, long timeoutMillis) throws Exception {
        long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        synchronized (monitor) {
            while (true) {
                Snapshot snapshot = status();
                if (snapshot.failed()) {
                    throw new IOException("SERVER_FAILED");
                }
                if (condition.test(snapshot)) {
                    return snapshot;
                }
                long remaining = end - System.nanoTime();
                if (remaining <= 0) {
                    throw new IOException("SERVER_BARRIER_TIMEOUT");
                }
                TimeUnit.NANOSECONDS.timedWait(monitor, remaining);
            }
        }
    }

    private void acceptLoop() {
        try {
            while (running) {
                // Never wait for readiness while holding the publication lock.
                selector.select(250);
                selector.selectedKeys().clear();
                while (running) {
                    ContextRegistry.Captured captured = registry.accept(listener);
                    if (captured == null) {
                        break;
                    }
                    synchronized (monitor) {
                        if (!running || active.size() == Limits.CONNECTIONS) {
                            captured.close();
                            rejected = Limits.incrementSaturated(rejected);
                            monitor.notifyAll();
                            continue;
                        }
                        active.add(captured);
                        accepted = Limits.incrementSaturated(accepted);
                        lastGeneration = captured.generation();
                        monitor.notifyAll();
                    }
                    try {
                        workers.execute(() -> serve(captured));
                    } catch (RuntimeException failure) {
                        release(captured);
                        if (running) {
                            throw failure;
                        }
                    }
                }
            }
        } catch (IOException | RuntimeException failure) {
            synchronized (monitor) {
                if (running) {
                    failed = true;
                }
                monitor.notifyAll();
            }
        }
    }

    private void serve(ContextRegistry.Captured captured) {
        try (SSLSocket tls = captured.wrap()) {
            tls.startHandshake();
            tls.setSoTimeout(Limits.READ_MS);
            for (int exchange = 0; exchange < Limits.EXCHANGES; exchange++) {
                Frames.Line line = Frames.read(tls.getInputStream(), Limits.LINE);
                if (line == null) {
                    break;
                }
                String reply = "GEN " + captured.generation() + " BYTES " + line.bytes();
                Frames.write(tls.getOutputStream(), reply, 64);
            }
        } catch (IOException | RuntimeException expectedConnectionFailure) {
            // A peer failure terminates this connection only. No raw diagnostic is persisted.
        } finally {
            release(captured);
        }
    }

    private void release(ContextRegistry.Captured captured) {
        captured.close();
        synchronized (monitor) {
            if (active.remove(captured)) {
                finished = Limits.incrementSaturated(finished);
            }
            monitor.notifyAll();
        }
    }

    void abort() {
        running = false;
        selector.wakeup();
        try {
            listener.close();
        } catch (IOException ignored) {
            // Owned listener is already closed or closing.
        }
        synchronized (monitor) {
            for (ContextRegistry.Captured captured : active) {
                captured.close();
            }
            monitor.notifyAll();
        }
    }

    @Override
    public void close() throws Exception {
        abort();
        workers.shutdown();
        acceptor.join(3000);
        if (!workers.awaitTermination(5, TimeUnit.SECONDS)) {
            workers.shutdownNow();
            workers.awaitTermination(2, TimeUnit.SECONDS);
        }
        selector.close();
        if (acceptor.isAlive() || !workers.isTerminated() || status().active() != 0) {
            throw new IOException("SERVER_SHUTDOWN_INCOMPLETE");
        }
    }
}
