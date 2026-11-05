# Trust boundaries

Development 0.1.0. [Português](trust-boundaries.pt-BR.md).

## Trusted inputs

The JDK 21 standard providers, local keytool, OpenSSL executable found on the trusted PATH, operating system, workspace ancestors, and fixed harness source are trusted. Tool binaries and host configuration must not be attacker-controlled. The child launcher removes Java option injection variables and `OPENSSL_CONF`, but it is not an environment sandbox. Use an isolated development environment, not a machine containing valuable credentials.

No host cacerts or host keys are loaded. Java managers use only explicit fixture stores; server trust is an explicitly empty store. No global SSL defaults or trust-related system properties are changed. OpenSSL uses one explicit CA file with `-no-CApath -no-CAstore`; verification errors are fatal via `-verify_return_error`. It also checks the application reply, so TLS success against an unrelated service is insufficient.

## Untrusted local peer bytes

Other local processes may reach the ephemeral loopback port. They can occupy four admissions until timeouts, trigger rejection, send malformed frames, or induce verification failures. They cannot supply contexts, paths, algorithms, commands, or published generations over the service. The line service has no administration protocol. Fixed bounds reduce resource exposure but are not an authentication boundary against a hostile machine owner.

`localhost` is a logical certificate name, not a public identity assertion. Connecting to 127.0.0.1 alone is not hostname verification. The JSSE client explicitly enables HTTPS endpoint identification after wrapping with the logical host. `wrong.example` must fail even with overlap trust. Trust A and B are synthetic test roots with no authority outside this run.

## Files and sensitive material

The only supported bundle provenance in the harness is its own locally generated PKI. The API additionally bounds file reads and rejects a final symlink and non-0600 mode, but assumes trusted, finite parent directories. It does not promise resistance to hostile ancestor replacement, mount changes, devices disguised by a compromised OS, or malicious filesystem latency.

A private password file avoids password values in child argument vectors. Password arrays and source bytes are cleared where feasible; JVM/provider copies and OS caching cannot be guaranteed erased. Runtime CA public PEM files are still kept private to avoid accidental reuse or noisy evidence. Fixed fixture-name deletion avoids recursive traversal. Cleanup failure is failure, not a silently successful demo. Unexpected external kill may leave disposable files under the ignored private build directory.

## Evidence

Provider/keytool/OpenSSL output is collected only in bounded memory and never printed or stored verbatim. Success evidence consists of fixed check names and normalized scalars. Failure output is restricted to a class name and uppercase code. There is no telemetry, tracking, network API, uploaded artifact workflow, or real personal data.

## What this does not prove

Context initialization does not audit issuance policy, key provenance, revocation, operational ownership, or public trust. Fresh contexts avoid resumption masking this experiment; they do not implement a resumption invalidation policy. Established connections deliberately survive publication until their normal protocol/time bounds. This behavior must not be mistaken for immediate credential revocation.
