# Security

This repository is a development TLS rotation experiment, version 0.1.0, not a production security boundary or certified TLS system. Synthetic authorities and keys are disposable and must never be reused or added to host trust.

Report suspected vulnerabilities using [GitHub private vulnerability reporting](https://github.com/martinlofranodeoliveira/loopback-tls-rotation-lab/security/advisories/new). Do not publish passwords, private keys, raw run directories, real personal data or confidential client code in any report. Provide a minimal synthetic reproduction, command, tool versions and normalized failure code instead. No response-time SLA is stated.

For general non-security contact, use the [official Lofrano Tecnologia homepage](https://lofranotecnologia.com.br).

The supported development contract is documented in [docs/contract.md](docs/contract.md). Relevant limitations include trusted filesystem ancestors/tool binaries, local-peer denial of service, blocking-I/O and scheduler limitations, lack of revocation and resumption policy, and cleanup that cannot survive every abrupt OS/JVM termination. A successful context initialization is not a certificate-policy audit. Retained established connections after publication are intentional, not immediate revocation.

Run in an isolated trusted environment. Runtime fixture files live in private directories under `build/private/` and are removed on ordinary completion/failure. If external termination interrupts cleanup, do not publish or reuse any remaining material. No telemetry, external certificate requests, default host trust, secret storage service or remote deployment mechanism is included.
