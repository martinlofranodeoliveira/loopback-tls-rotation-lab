package br.com.lofrano.tlslab;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class OpenSslOracle {
    private OpenSslOracle() { }

    static void check(Children children, FixturePki pki, int port,
                      String selected, String other, long generation) throws Exception {
        probe(children, pki, port, selected, "localhost", generation, true);
        probe(children, pki, port, other, "localhost", generation, false);
        probe(children, pki, port, selected, "wrong.example", generation, false);
    }

    private static void probe(Children children, FixturePki pki, int port, String authority,
                              String hostname, long generation, boolean success) throws Exception {
        List<String> command = new ArrayList<>(List.of("openssl", "s_client", "-quiet",
                "-no_ign_eof", "-connect", "127.0.0.1:" + port, "-servername", "localhost",
                "-verify_hostname", hostname, "-verify_return_error", "-CAfile",
                pki.path(authority + ".pem").toString(), "-no-CApath", "-no-CAstore", "-tls1_3"));
        try (Children.Child child = children.start(command, success)) {
            child.send("oracle\n");
            if (success) {
                Check.equal(child.nextLine(), "GEN " + generation + " BYTES 6",
                        "OPENSSL_APPLICATION_REPLY_REQUIRED");
                child.endInput();
            }
            Children.Result result = child.finish(8000);
            Check.that(!result.timedOut() && !result.overflow(), "OPENSSL_MUST_FINISH");
            if (success) {
                Check.equal(result.exit(), 0, "OPENSSL_SUCCESS_REQUIRED");
            } else {
                String error = result.err().toLowerCase(Locale.ROOT);
                Check.that(result.exit() != 0, "OPENSSL_FAILURE_REQUIRED");
                Check.that(error.contains("verify error") || error.contains("verification error")
                        || error.contains("certificate verify failed"), "OPENSSL_VERIFY_EVIDENCE_REQUIRED");
                if (hostname.equals("wrong.example")) {
                    Check.that(error.contains("hostname mismatch"), "OPENSSL_HOSTNAME_EVIDENCE_REQUIRED");
                }
            }
        }
    }
}
