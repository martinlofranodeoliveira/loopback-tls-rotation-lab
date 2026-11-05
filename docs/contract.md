# Contract — development 0.1.0

This is the normative small contract for this development version. It describes the implemented subset, not general TLS or PKI compliance.

## Public Java API

Package: `br.com.lofrano.tlslab`.

- `new ContextRegistry(Path initialBundle, char[] password)` requires between 1 and 128 password characters before cloning the caller-owned array, builds a candidate, and starts at generation 1. Failure creates no live registry. The caller must clear its own array.
- `loadCandidate(Path)` returns an opaque final `Candidate`. It reads a regular, non-symlink final component of at most 1,048,576 bytes with exact POSIX mode 0600, using a bounded no-follow channel read. Trusted finite parent directories and a trusted workspace are preconditions. It requires a PKCS12 key entry named `server`, a two-certificate chain, no other key entries, and successful standard JSSE initialization. This is not full key/certificate-policy validation. Failure throws a sanitized `IOException` and never publishes.
- `publish(long expectedGeneration, Candidate)` requires a positive expectation and non-null candidate. It returns `PUBLISHED`, `STALE_GENERATION`, or `GENERATION_EXHAUSTED`. Stale expectations do not replace anything. A matching generation of `Long.MAX_VALUE` cannot advance. Success increments exactly once and publishes one selection atomically. A candidate may be reused; no context is exposed.
- `status()` returns immutable `Status(long generation)`, without paths, context, passwords, certificates, or managers. It can observe the final generation after close.
- `close()` clears the retained password and rejects further loads/publications/accepts. It does not own server shutdown; close the server first. Existing captured sockets own their context reference for their lifetime. Standard provider internals cannot be reliably erased by this API.

Normal closed-registry misuse throws `IllegalStateException`; invalid generation throws `IllegalArgumentException`; null candidate throws `NullPointerException`. A load racing close can finish building its private candidate, but cannot publish into the closed registry.

## Accept/publication ordering

The same lock encloses publication and successful **nonblocking** TCP accept plus capture. Readiness waits are outside that lock. Accepted sockets use their captured context for handshake and lifetime. The publication return is the replacement barrier for subsequent successful accepts, not for TCP SYN arrival or queued TCP connections. No established connection is migrated, renegotiated, or forcibly disconnected by publication.

## Service and clients

The service binds only `127.0.0.1` on an ephemeral port. It has four admitted sockets including pre-handshake sockets, four workers, and a four-slot executor queue; the active registry limits submitted work to four. A fifth accept is immediately closed without admission. The OS backlog is eight and is not an application admission promise.

Only TLS 1.3 is enabled, without client certificates. JSSE clients use a separate JVM, explicit disposable PKCS12 trust, a fresh context for each connection, logical peer `localhost`, and HTTPS endpoint identification while physically connecting to `127.0.0.1`. `wrong.example` is the only alternative logical host, solely for the negative check. There is no trust-all or default-trust path.

The wire subset is specified in [protocol.md](protocol.md): strict UTF-8, LF only, CR rejected, 1024 payload bytes, at most sixteen exchanges, and replies reporting capture generation and payload byte count. Frames are not echoed.

## Time and failure bounds

Connect and handshake idle waits are 2000 ms. Application read idle waits are 8000 ms. Each controller invocation has a 300-second monotonic budget starting before process tests and key generation. Keytool commands have at most 45 seconds each; ordinary child waits have at most 8 seconds (the deliberate 8-second application-read-timeout oracle allows 12 seconds for its control result), further limited by the remaining global budget. Output has a 65,536-byte limit per child stream, interactive lines 2048 bytes, interactive queue 32 entries, and at most four owned children. Argument count is at most 80 and each argument at most 4096 characters. Test-only explicit child timeout is 200 ms after a positive blocked acknowledgement.

The watchdog closes registered sockets and terminates owned process handles. Reaping may wait another 5 seconds, collector joins 2 then 1 seconds per stream, server acceptor join 3 seconds, pool shutdown 5 then 2 seconds. Cleanup verifies zero admitted sockets and children rather than inferring it from silence. These are finite waits, not a wall-clock SLA: scheduling, filesystem calls, cryptographic provider calls and blocking writes can overrun observation. There is no interruptible write-deadline guarantee.

## Artifacts and restart

All build output is under `build/`. Private run directories are randomly named under `build/private/`, mode 0700. A random runtime password file and fixed-template disposable keystores are 0600 when consumed. Keytool output is initially protected by the 0700 parent and normalized to 0600 after each sequential command. Temporary requests/signed leaves are deleted as soon as imported; explicit CA PEM oracle inputs remain private until cleanup. No raw transcripts are persisted. All fixture names are removed in `finally`/resource cleanup, including on ordinary failure. Abrupt external termination or an unresponsive OS may prevent deletion.

Successful cleanup precedes emission of normalized `build/evidence.json`. Publication is never persisted. Restart selects only the explicit initial bundle and generation 1. No watcher or host key/truststore is used. Concurrent harness invocations in one workspace are unsupported because evidence has one fixed output name.

## Success and exclusions

`make test` and `make demo` compile, run the same 24 named checks, and exit zero only after all checks and cleanup. Nonzero exit prints a normalized failure code without raw provider diagnostics. The demo is not a nonasserting illustration. Success JSON reports version, mode, result, generation 2, active sockets 0, owned children 0, fixtures removed true, and check count 24.

No remote service, external certificate issuer, credentials supplied by users, public trust, ACME, mTLS, revocation, session-resumption rotation semantics, production-security guarantee, or certification is part of this contract.

A socket read timeout is an indeterminate wait failure, never evidence of peer closure. The client reports `CLIENT_ERROR` / exit 20 on it. A raw stalled-handshake peer can send a small TLS fatal alert before TCP EOF; the closure oracle drains at most 256 bytes and requires EOF or an explicit reset, never a quiet interval. An actual verified peer that receives a request but deliberately sends no response tests this distinction.

Negative JSSE trust/hostname evidence additionally requires a standard CertificateException in a cause chain limited to 16 links; an SSL handshake exception by itself is insufficient. A separate accepted TCP peer closes before presenting any certificate, and must produce `CLIENT_ERROR` / exit 20 rather than `VERIFY_FAILED` / exit 10. This is a conservative JDK 21 fixed-provider probe policy, not a general taxonomy of TLS failures.
