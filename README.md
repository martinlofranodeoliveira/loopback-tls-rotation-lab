# Loopback TLS Rotation Lab

Development version **0.1.0**, by **Lofrano Tecnologia**. Original Java 21 code, MIT licensed. [Português](README.pt-BR.md) · [Homepage](https://lofranotecnologia.com.br) · [Repository](https://github.com/martinlofranodeoliveira/loopback-tls-rotation-lab)

A small, headless JSSE experiment: replace the context selected for **new accepts**, without changing the context captured by an existing connection. Every application reply states that captured generation. This is not a production TLS terminator, certificate authority, security certification, or public-trust claim.

## Install and run

Use a trusted POSIX workspace on Linux, JDK **21** including `javac` and `keytool`, GNU Make, and OpenSSL **3.x** with `-no-CAstore` support. The runtime rejects other Java feature versions. No Maven, Gradle, JUnit, downloaded library, account, supplied credential, or model/API service is needed.

For a Debian/Ubuntu environment missing the command-line tools:

```sh
sudo apt-get update
sudo apt-get install --yes --no-install-recommends make openssl
```

Install a JDK 21 distribution through your environment's normal package/image mechanism. Check it and run:

```sh
java -version
javac -version
openssl version
make test
make demo
```

Both targets compile first and execute the full asserting harness. Tests are explicit checks, not Java's disableable `assert`. The demo deliberately runs the same negative cases as the tests. It generates fresh synthetic keys sequentially, starts an ephemeral IPv4 loopback service, runs separate Java clients and OpenSSL, then reaps children and deletes its fixture directory. Only loopback traffic is used during these commands; package installation and CI tool setup are separate network-enabled preparation steps.

Final two lines of a successful `make demo` run, after compilation and named checks:

```text
PASS — all declared checks; generation=2; sockets=0; children=0; fixturesRemoved=true
{"version":"0.1.0","mode":"demo","result":"pass","generation":2,"activeSockets":0,"ownedChildren":0,"fixturesRemoved":true,"checks":24}
```

That JSON is also written to `build/evidence.json`. It contains no paths, ports, passwords, certificate bytes, process IDs, timestamps, or raw provider output. `test` changes only the `mode` field. These are expected output examples, not deployment or benchmark claims.

## Small usage examples

The harness is the supported executable. The public selection API is `ContextRegistry`; password ownership is explicit:

```java
try (ContextRegistry contexts = new ContextRegistry(initialBundle, ownedPassword)) {
    ContextRegistry.Candidate candidate = contexts.loadCandidate(replacementBundle);
    long expected = contexts.status().generation();
    ContextRegistry.Publication result = contexts.publish(expected, candidate);
}
java.util.Arrays.fill(ownedPassword, '\0');
```

`initialBundle`, `replacementBundle`, and `ownedPassword` above are locally generated fixture values, not host keys. In an actual caller, clear the caller-owned array in `finally`. The registry clones it. A malformed load throws a sanitized `IOException`; a stale publication returns `STALE_GENERATION`. Neither changes the selected generation. The opaque candidate exposes neither an `SSLContext` nor its factory.

The fixed wire service accepts `hello` followed by LF and answers:

```text
GEN 1 BYTES 5
```

After replacement, a newly accepted verified connection answers `GEN 2 BYTES 5`; a connection captured under generation 1 still answers generation 1. A positive admission barrier also proves this for a socket accepted **before** replacement but handshaken **after** it.

## Why this design

A nonblocking `ServerSocketChannel.accept()` and context capture execute under the same lock as publication. Selector waiting is outside the lock. Merely swapping an `SSLServerSocket` reference would not establish this accept-time contract. Candidate parsing and full JSSE initialization happen before publication. No mutable context is handed to callers.

Clients physically connect to `127.0.0.1`, wrap that socket with logical peer `localhost`, and enable HTTPS endpoint identification with TLS 1.3 only. Each new probe uses a new process and context with explicit fixture trust. Trust A, trust B, and overlap trust give observable transitions rather than an accidental success through default trust or session reuse. OpenSSL independently checks certificate and hostname verification and requires the application generation reply.

## Limits and boundaries

- Four admitted connections, including stalled handshakes; four workers; a bounded queue. A fifth accepted rejection socket is immediately closed, not admitted. The OS listen backlog is separate.
- LF frames: at most 1024 payload **bytes**, strict UTF-8, CR rejected, 16 exchanges per connection. Replies are at most 64 bytes before LF.
- Handshake/connect waits: 2 seconds; idle application reads: 8 seconds; controller monotonic budget: 300 seconds including key generation. Shutdown/reaping have additional finite grace waits.
- Small blocking writes are not an OS write deadline. The controller closes registered sockets and kills only owned `Process` handles at its deadline. Scheduler starvation, provider stalls, filesystem blocking, or an uninterruptible OS operation can delay observation and cleanup; these are not hard real-time guarantees.
- Fixed RSA-2048/SHA256withRSA keytool templates create two disposable synthetic authorities and localhost SAN leaves. Never reuse them. Passwords are randomly generated at runtime, not command-line password values.
- POSIX mode checks and final-component no-follow reads require trusted, finite parent directories. This is not a hostile-filesystem sandbox. An abrupt JVM/OS kill can prevent `finally` cleanup; treat any remaining `build/private` material as disposable private data.
- Publication is memory-only. Restart explicitly selects an initial bundle and starts at generation 1; no watcher or persisted selection exists.
- No mutual TLS, external CA/ACME, revocation, remote endpoint support, session-resumption rotation semantics, full certificate-policy audit, or general standards-compliance claim.

See the precise [contract](docs/contract.md), [design](docs/design.md), [trust boundaries](docs/trust-boundaries.md), [PKI](docs/pki.md), and [tests](docs/testing.md). General contact is through the [official homepage](https://lofranotecnologia.com.br); security reports use [GitHub private vulnerability reporting](https://github.com/martinlofranodeoliveira/loopback-tls-rotation-lab/security/advisories/new).

The negative probes distinguish evidence from transport failure: a read timeout is `CLIENT_ERROR` / exit 20, and premature TCP EOF before any certificate is also setup failure. `VERIFY_FAILED` / exit 10 requires a standard certificate exception in the bounded cause chain. Both distinctions have real peer-process regressions; a quiet interval never proves closure.
