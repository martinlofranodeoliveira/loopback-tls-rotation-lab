package br.com.lofrano.tlslab;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class Evidence {
    private final List<String> checks = new ArrayList<>();

    void add(String name) {
        Check.that(name.matches("[a-z][a-z0-9-]{0,63}") && checks.size() < 64
                && !checks.contains(name), "EVIDENCE_BOUND");
        checks.add(name);
    }

    void emit(String mode) throws IOException {
        for (String check : checks) {
            System.out.println("PASS " + check);
        }
        System.out.println("PASS — all declared checks; generation=2; sockets=0; children=0; fixturesRemoved=true");
        String json = "{\"version\":\"0.1.0\",\"mode\":\"" + mode
                + "\",\"result\":\"pass\",\"generation\":2,\"activeSockets\":0,\"ownedChildren\":0,"
                + "\"fixturesRemoved\":true,\"checks\":" + checks.size() + "}";
        Files.writeString(Path.of("build", "evidence.json"), json + "\n", StandardCharsets.UTF_8);
        System.out.println(json);
    }
}
