package br.com.lofrano.tlslab;

final class RuntimeGate {
    private RuntimeGate() { }

    static void check() {
        if (Runtime.version().feature() != 21) {
            throw new IllegalStateException("JDK_21_REQUIRED");
        }
    }
}
