package br.com.lofrano.tlslab;

import java.util.Objects;

final class Check {
    private Check() { }

    static void that(boolean condition, String code) {
        if (!condition) {
            throw new AssertionError(code);
        }
    }

    static void equal(Object actual, Object expected, String code) {
        that(Objects.equals(actual, expected), code);
    }

    interface Action {
        void run() throws Exception;
    }

    static void rejects(Class<? extends Throwable> type, Action action, String code) throws Exception {
        try {
            action.run();
        } catch (Exception failure) {
            if (type.isInstance(failure)) {
                return;
            }
            throw new AssertionError(code + "_WRONG_EXCEPTION");
        }
        throw new AssertionError(code + "_NOT_REJECTED");
    }
}
