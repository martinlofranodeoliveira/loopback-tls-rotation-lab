package br.com.lofrano.tlslab;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class FixturePki implements AutoCloseable {
    private static final List<String> NAMES = List.of(
            "password", "a-ca.p12", "b-ca.p12", "a.pem", "b.pem",
            "a-server.p12", "b-server.p12", "a.req", "b.req", "a-leaf.pem", "b-leaf.pem",
            "trust-a.p12", "trust-b.p12", "trust-both.p12", "corrupt.p12",
            "alias-link", "oversize.p12", "public-password");
    private final Path root;
    private final Children children;

    private FixturePki(Path root, Children children) {
        this.root = root;
        this.children = children;
    }

    static FixturePki create(Children children) throws Exception {
        FixturePki fixture = new FixturePki(PrivateFiles.directory(), children);
        try {
            Password.create(fixture.path("password"));
            fixture.authority("a", "Synthetic Authority A");
            fixture.authority("b", "Synthetic Authority B");
            fixture.trust("trust-a.p12", "a");
            fixture.trust("trust-b.p12", "b");
            fixture.trust("trust-both.p12", "a");
            fixture.trust("trust-both.p12", "b");
            PrivateFiles.write(fixture.path("corrupt.p12"),
                    "synthetic invalid keystore\n".getBytes(StandardCharsets.US_ASCII));
            return fixture;
        } catch (Exception | AssertionError failure) {
            try {
                fixture.close();
            } catch (Exception cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    Path root() { return root; }

    Path path(String name) {
        if (!NAMES.contains(name)) {
            throw new IllegalArgumentException("UNKNOWN_FIXTURE_NAME");
        }
        return root.resolve(name);
    }

    ContextRegistry registry(String name) throws IOException {
        char[] chars = null;
        try (Password password = Password.read(path("password"))) {
            chars = password.copy();
            return new ContextRegistry(path(name), chars);
        } finally {
            if (chars != null) {
                Arrays.fill(chars, '\0');
            }
        }
    }

    private void tool(String command, String store, String... arguments) throws Exception {
        List<String> argv = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", "keytool").toString(),
                "-J-Xmx96m", "-J-XX:ActiveProcessorCount=2", "-J-Duser.language=en",
                "-J-Duser.country=US", command, "-storetype", "PKCS12", "-keystore",
                path(store).toString(), "-storepass:file", path("password").toString()));
        argv.addAll(List.of(arguments));
        Children.Result result = children.run(argv, 45000);
        for (String name : NAMES) {
            PrivateFiles.secure(path(name));
        }
        if (result.exit() != 0 || result.overflow() || result.timedOut()) {
            throw new IOException("KEYTOOL_FAILED");
        }
    }

    private void authority(String prefix, String commonName) throws Exception {
        String ca = prefix + "-ca.p12";
        String server = prefix + "-server.p12";
        String pem = path(prefix + ".pem").toString();
        String request = path(prefix + ".req").toString();
        String leaf = path(prefix + "-leaf.pem").toString();
        tool("-genkeypair", ca, "-alias", "ca", "-keyalg", "RSA", "-keysize", "2048",
                "-sigalg", "SHA256withRSA", "-dname", "CN=" + commonName + ",O=Synthetic Lab",
                "-validity", "2", "-ext", "bc=ca:true", "-ext", "ku=keyCertSign,cRLSign",
                "-keypass:file", path("password").toString());
        tool("-exportcert", ca, "-alias", "ca", "-rfc", "-file", pem);
        tool("-genkeypair", server, "-alias", "server", "-keyalg", "RSA", "-keysize", "2048",
                "-sigalg", "SHA256withRSA", "-dname", "CN=localhost,O=Synthetic Lab",
                "-validity", "2", "-ext", "bc=ca:false", "-ext", "SAN=dns:localhost",
                "-ext", "KU=digitalSignature,keyEncipherment", "-ext", "EKU=serverAuth",
                "-keypass:file", path("password").toString());
        tool("-certreq", server, "-alias", "server", "-sigalg", "SHA256withRSA", "-file", request);
        tool("-gencert", ca, "-alias", "ca", "-infile", request, "-outfile", leaf, "-rfc",
                "-sigalg", "SHA256withRSA", "-validity", "2", "-ext", "bc=ca:false",
                "-ext", "SAN=dns:localhost", "-ext", "KU=digitalSignature,keyEncipherment",
                "-ext", "EKU=serverAuth");
        tool("-importcert", server, "-alias", "issuer", "-file", pem, "-noprompt");
        tool("-importcert", server, "-alias", "server", "-file", leaf, "-noprompt");
        Files.delete(path(prefix + ".req"));
        Files.delete(path(prefix + "-leaf.pem"));
    }

    private void trust(String store, String authority) throws Exception {
        tool("-importcert", store, "-alias", "ca-" + authority,
                "-file", path(authority + ".pem").toString(), "-noprompt");
    }

    @Override
    public void close() throws IOException {
        IOException first = null;
        for (String name : NAMES) {
            try {
                Files.deleteIfExists(path(name));
            } catch (IOException failure) {
                if (first == null) {
                    first = new IOException("FIXTURE_CLEANUP_FAILED");
                }
            }
        }
        try {
            Files.deleteIfExists(root);
        } catch (IOException failure) {
            if (first == null) {
                first = new IOException("FIXTURE_DIRECTORY_NOT_EMPTY");
            }
        }
        if (first != null) {
            throw first;
        }
    }
}
