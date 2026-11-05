# Design — development 0.1.0

## Selection, not socket migration

`ContextRegistry` owns an atomic reference to a private `(generation, candidate)` pair. Candidate construction reads at most 1 MiB, parses PKCS12, validates the fixed key alias/chain shape, initializes standard key/trust manager factories, and initializes a fresh TLSv1.3 context. That work is outside the publication critical section. Candidate fields are final and private. There is no context getter, external-context constructor, or reinitialization API.

JSSE contexts have internal mutable caches; “immutable candidate” means callers cannot mutate the context or change its configured managers. It is not a claim that JSSE's implementation has no mutable state. Server cache size/time are bounded; resumed-session rotation behavior is deliberately not studied.

`publish` checks the expected generation under the selection lock, checks exhaustion, and performs compare-and-set. Concurrent publishers with the same expectation have at most one winner. `status` returns only a scalar generation. Candidates can be reused; each successful publication creates a new selection pair and generation.

## Linearization

The acceptor waits on a selector outside the publication lock. Under that same lock it calls nonblocking `accept()` and captures the current pair before unlocking. The accepted channel is in blocking mode as specified by the channel API. The worker wraps its plain socket through the captured context's socket factory, switches to server mode, enables only TLS 1.3, then handshakes.

A TCP connect may complete while still queued in the kernel. Such a connection has no application-captured generation yet. The successful nonblocking accept is the selection linearization point. Publication completing before that point selects the replacement; publication after it cannot alter the capture, even when no TLS bytes have arrived.

The demo proves both old-session cases: an already handshaken connection exchanges again after publication, and a delayed client receives a positive TCP-connected acknowledgement followed by a server accepted-generation barrier before publication and only then sends handshake bytes.

## Resource accounting

The active registry includes raw accepted sockets before handshake. Four workers and a bounded queue serve at most four admitted sockets. A fifth accept is a transient rejection socket, closed synchronously and never submitted. The queue accommodates worker-return races without an unbounded backlog. Counters saturate instead of wrapping. Histories are not retained.

A worker reads bounded byte frames and emits bounded metadata, not reflected payloads. EOF, timeout, decoding failure, excess input, and TLS failure close only that connection. The sixteenth valid exchange receives its reply and closes the connection. Shutdown closes every registered underlying socket, wakes/closes the listener, shuts down the pool, and waits finite intervals for acceptor/workers. Success requires zero active sockets.

All child invocations are constructed from fixed tool templates without a shell. The controller bounds arguments, output, line queues, admission, and waits. Output overflow kills the owned process. Java Process objects, never numeric PID signals or process groups, identify children. Collector threads are joined after reaping. Fixture cleanup deletes only a fixed allowlist of locally created names; there is no recursive filesystem walk.

## Deliberate tradeoffs

The harness and a local adversarial peer share a trusted machine. SO_TIMEOUT is an idle blocking-read bound, not a complete message deadline; slow progress can extend a frame. Small socket/pipe writes can block. The independent monotonic controller watchdog closes sockets and kills children at 300 seconds, but scheduling and blocking provider/OS calls preclude a hard real-time guarantee. Teardown adds bounded grace intervals.

No durable publication record exists. A restart is an explicit new experiment with its initial bundle and generation 1. No background watcher, key distribution, operational reload policy, remote endpoint, or certificate policy engine is hidden in the lab.

## Primary references

- [JSSE guide, Java 21](https://docs.oracle.com/en/java/javase/21/security/java-secure-socket-extension-jsse-reference-guide.html)
- [SSLParameters](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/net/ssl/SSLParameters.html)
- [SSLSocketFactory wrapping](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/net/ssl/SSLSocketFactory.html)
- [ServerSocketChannel](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/ServerSocketChannel.html)
- [AtomicReference](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/atomic/AtomicReference.html)
- [keytool](https://docs.oracle.com/en/java/javase/21/docs/specs/man/keytool.html)
- [OpenSSL s_client](https://docs.openssl.org/3.5/man1/openssl-s_client/)
