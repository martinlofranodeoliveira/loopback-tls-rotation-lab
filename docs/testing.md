# Testing and reproducibility

Run `make test`; run `make demo` separately to regenerate everything and repeat the assertions. Both compile all original main/test Java sources using `javac --release 21`, then use Java 21's standard library only. No assertion depends on JVM `-ea`.

## Checks in emission order

1. `frames`: strict byte framing, empty/clean EOF, multibyte exact boundary, invalid overlong/surrogate/incomplete UTF-8, CRLF, partial EOF, multiple frames, output injection and response bound.
2. `child-output-bound`: a fixed Java fixture floods output; capture must cap at 65,536 bytes and its process must be reaped.
3. `child-deadline`: a child positively acknowledges blocking, then a 200 ms wait must terminate and reap that exact child.
4. `candidate-atomicity`: valid load is nonpublishing; corrupt, missing, directory, symlink, oversized source and insecure password-file cases reject, and a missing private filename is absent from the API exception including its cause/suppressed data; stale publish is nonmutating; reusable opaque candidate advances normally.
5. `publish-race`: two latch-released publishers with the same expectation produce exactly one winner and one stale result.
6. `generation-exhaustion`: checked successor rejects overflow; diagnostic counters saturate.
7. `transport-not-verification`: a real TCP peer accepts then closes before any TLS certificate; setup must fail as CLIENT_ERROR/exit20, never VERIFY_FAILED/exit10.
8. `timeout-not-closure`: an actual verified TLS peer acknowledges receipt but stays open without replying; the client must report CLIENT_ERROR/exit20, never CLOSED.
9. `before-a`: fresh A-trusted JSSE client verifies TLS 1.3 and generation 1.
10. `before-b-rejected`: B-only verification fails with dedicated failure exit.
11. `before-overlap`: fresh A+B client sees generation 1.
12. `openssl-before`: explicit A succeeds with generation 1; B and wrong hostname fail fatally with verification evidence.
13. `accepted-before-handshake`: server admission counter/generation barrier precedes publication; delayed A handshake after publication still sees generation 1.
14. `existing-session`: already handshaken A connection exchanges again after publication and still sees generation 1.
15. `after-b`: fresh B client sees generation 2.
16. `after-a-rejected`: fresh A-only client fails.
17. `after-overlap`: fresh A+B client sees generation 2.
18. `wrong-host`: overlap-trusted JSSE client verifying wrong.example fails.
19. `openssl-after`: B succeeds with generation 2; A and wrong hostname fail with explicit verification evidence.
20. `line-boundaries`: live 1024-byte frame succeeds; 1025-byte frame closes.
21. `invalid-frames`: live invalid UTF-8 and CR input close.
22. `exchange-bound`: sixteen valid replies, then closure rather than a seventeenth reply.
23. `admission-and-stall`: four positive pre-handshake admissions, fifth rejection and peer closure, then all stalled peers close and active registry reaches zero.
24. `cleanup`: server workers/acceptor have terminated, active sockets and owned children are zero, fixture directory is absent, and watchdog has terminated.

The live rotation scenario also repeats corrupt/stale atomicity against the running service registry. Negative JSSE checks require a standard CertificateException in the bounded cause chain, `VERIFY_FAILED` and exit 10, not just arbitrary nonzero status. OpenSSL negatives require nonzero status, no timeout/overflow, and verification-specific diagnostics. Wrong-host OpenSSL checks additionally require hostname mismatch evidence. A missing tool, unsupported option, early process exit, or timeout is not accepted as verification failure.

Barriers use acknowledgements, condition notification, process exit and reaping. No test treats a sleep or a short quiet interval as proof. The four-admission test intentionally has to complete its local setup within the 2-second handshake idle window; a heavily starved machine can fail rather than falsely claim the boundary held.

## Resource envelope

Scenarios and key generation are serial. Parent heap: 160 MiB. Each Java client: 64 MiB. Each keytool JVM: 96 MiB. Every JVM is given `ActiveProcessorCount=2`. No Gradle workers or third-party test runner exist. The normal scenario holds at most two Java client JVMs simultaneously and does not overlap key generation with service tests. This is designed for a 1 GiB / 2 CPU isolated environment, not a measured performance guarantee; native memory and scheduling remain platform-dependent.

Generated keys, signatures, certificate serials, port and private directory name vary by run. Normalized successful evidence is deterministic apart from the selected `test`/`demo` mode. The harness does not persist raw certificates or diagnostics in evidence. `build/evidence.json` records only successful completion; an older success file may remain after a later failed invocation, so always check the current process exit and stdout, not file existence alone.

CI installs Make/OpenSSL through the runner package manager, records actual versions, and runs both commands serially. Runtime scenarios are offline apart from local loopback sockets. No benchmark, deployment, broad compatibility, or production-readiness inference should be drawn from these tests.
