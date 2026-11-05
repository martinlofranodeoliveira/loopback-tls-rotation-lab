package br.com.lofrano.tlslab;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

final class Children implements AutoCloseable {
    record Result(int exit, String out, String err, boolean overflow, boolean timedOut) { }
    private record Event(String line, boolean eof) { }

    private final Deadline deadline;
    private final Set<Child> children = new HashSet<>();
    private boolean cancelled;

    Children(Deadline deadline) {
        this.deadline = deadline;
    }

    static List<String> javaCommand(Class<?> main, String... arguments) {
        List<String> command = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Xmx64m", "-XX:ActiveProcessorCount=2", "-Dfile.encoding=UTF-8",
                "-cp", System.getProperty("java.class.path"), main.getName()));
        command.addAll(List.of(arguments));
        return command;
    }

    synchronized Child start(List<String> command, boolean interactive) throws IOException {
        deadline.millis(1);
        if (cancelled || children.size() >= 4 || command.size() > 80) {
            throw new IOException("CHILD_ADMISSION_REJECTED");
        }
        for (String argument : command) {
            if (argument.length() > 4096 || argument.indexOf('\0') >= 0) {
                throw new IOException("CHILD_ARGUMENT_REJECTED");
            }
        }
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.environment().remove("JAVA_TOOL_OPTIONS");
        builder.environment().remove("JDK_JAVA_OPTIONS");
        builder.environment().remove("_JAVA_OPTIONS");
        builder.environment().remove("CLASSPATH");
        builder.environment().remove("OPENSSL_CONF");
        builder.environment().put("LC_ALL", "C");
        Child child = new Child(builder.start(), interactive);
        children.add(child);
        return child;
    }

    Result run(List<String> command, long timeoutMillis) throws Exception {
        try (Child child = start(command, false)) {
            child.endInput();
            return child.finish(timeoutMillis);
        }
    }

    synchronized int count() {
        return children.size();
    }

    synchronized void cancelAll() {
        cancelled = true;
        for (Child child : children) {
            child.process.destroyForcibly();
        }
    }

    @Override
    public void close() throws Exception {
        List<Child> snapshot;
        synchronized (this) {
            snapshot = List.copyOf(children);
        }
        Exception first = null;
        for (Child child : snapshot) {
            try {
                child.close();
            } catch (Exception failure) {
                if (first == null) {
                    first = failure;
                }
            }
        }
        if (first != null) {
            throw first;
        }
        Check.equal(count(), 0, "CHILDREN_NOT_REAPED");
    }

    final class Child implements AutoCloseable {
        private final Process process;
        private final ArrayBlockingQueue<Event> lines = new ArrayBlockingQueue<>(32);
        private final Collector stdout;
        private final Collector stderr;
        private volatile boolean overflow;
        private boolean closed;

        Child(Process process, boolean interactive) {
            this.process = process;
            stdout = new Collector(process.getInputStream(), interactive, "child-stdout");
            stderr = new Collector(process.getErrorStream(), false, "child-stderr");
            stdout.thread.start();
            stderr.thread.start();
        }

        void send(String text) throws IOException {
            byte[] bytes = text.getBytes(StandardCharsets.US_ASCII);
            if (bytes.length > 2048) {
                throw new IOException("CHILD_INPUT_BOUND");
            }
            deadline.millis(1);
            process.getOutputStream().write(bytes);
            process.getOutputStream().flush();
        }

        void endInput() throws IOException {
            process.getOutputStream().close();
        }

        String nextLine() throws Exception {
            return nextLine(8000);
        }

        String nextLine(long maximumMillis) throws Exception {
            Event event = lines.poll(deadline.millis(maximumMillis), TimeUnit.MILLISECONDS);
            if (event == null || event.eof() || overflow) {
                throw new IOException("CHILD_PROTOCOL_ENDED");
            }
            return event.line();
        }

        Result finish(long maximumMillis) throws Exception {
            boolean timedOut = !process.waitFor(deadline.millis(maximumMillis), TimeUnit.MILLISECONDS);
            if (timedOut) {
                process.destroyForcibly();
            }
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                throw new IOException("CHILD_REAP_TIMEOUT");
            }
            joinCollectors();
            return new Result(process.exitValue(), stdout.text(), stderr.text(), overflow, timedOut);
        }

        private void joinCollectors() throws Exception {
            stdout.thread.join(2000);
            stderr.thread.join(2000);
            if (stdout.thread.isAlive() || stderr.thread.isAlive()) {
                process.getInputStream().close();
                process.getErrorStream().close();
                stdout.thread.join(1000);
                stderr.thread.join(1000);
            }
            if (stdout.thread.isAlive() || stderr.thread.isAlive()) {
                throw new IOException("CHILD_DRAIN_TIMEOUT");
            }
        }

        @Override
        public void close() throws Exception {
            if (closed) {
                return;
            }
            if (process.isAlive()) {
                process.destroyForcibly();
            }
            process.getOutputStream().close();
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                throw new IOException("CHILD_REAP_TIMEOUT");
            }
            joinCollectors();
            process.getInputStream().close();
            process.getErrorStream().close();
            closed = true;
            synchronized (Children.this) {
                children.remove(this);
            }
        }

        private final class Collector implements Runnable {
            private final InputStream input;
            private final boolean interactive;
            private final ByteArrayOutputStream captured = new ByteArrayOutputStream();
            private final Thread thread;

            Collector(InputStream input, boolean interactive, String name) {
                this.input = input;
                this.interactive = interactive;
                thread = new Thread(this, name);
                thread.setDaemon(true);
            }

            String text() {
                return captured.toString(StandardCharsets.UTF_8);
            }

            private void exceed() {
                overflow = true;
                process.destroyForcibly();
            }

            @Override
            public void run() {
                ByteArrayOutputStream line = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                try (input) {
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        int room = Limits.CHILD_BYTES - captured.size();
                        captured.write(buffer, 0, Math.min(room, read));
                        if (read > room) {
                            exceed();
                            break;
                        }
                        if (interactive) {
                            for (int i = 0; i < read; i++) {
                                if (buffer[i] == '\n') {
                                    if (!lines.offer(new Event(line.toString(StandardCharsets.UTF_8), false))) {
                                        exceed();
                                        return;
                                    }
                                    line.reset();
                                } else if (line.size() < 2048) {
                                    line.write(buffer[i]);
                                } else {
                                    exceed();
                                    return;
                                }
                            }
                        }
                    }
                } catch (IOException ignored) {
                    // Exit/reap and the control protocol determine success, not stream diagnostics.
                } finally {
                    if (interactive) {
                        lines.offer(new Event("", true));
                    }
                }
            }
        }
    }
}
