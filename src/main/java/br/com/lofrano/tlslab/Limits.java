package br.com.lofrano.tlslab;

final class Limits {
    static final int CONNECTIONS = 4;
    static final int LINE = 1024;
    static final int EXCHANGES = 16;
    static final int HANDSHAKE_MS = 2000;
    static final int READ_MS = 8000;
    static final int FILE_BYTES = 1_048_576;
    static final int CHILD_BYTES = 65_536;
    private Limits() { }

    static long incrementSaturated(long value) {
        return value == Long.MAX_VALUE ? value : value + 1;
    }
}
