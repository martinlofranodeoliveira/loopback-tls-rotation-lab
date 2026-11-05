# Contributing

Author and maintainer organization: **Lofrano Tecnologia**. General contact: [official homepage](https://lofranotecnologia.com.br). Development version: 0.1.0.

Use a trusted POSIX environment with JDK 21, keytool, Make and OpenSSL 3.x. Run:

```sh
make test
make demo
```

Both commands compile first and must complete all assertions and cleanup. Keep scenarios serial and preserve the bounded heap/process/socket/output design. Do not add remote test endpoints, supplied credentials, fixture binaries, telemetry, host trust access, disabled verification, or real personal data. Generate all synthetic material locally.

For behavioral changes, first update the precise contract and add regression cases that would fail the previous behavior. In particular, preserve the shared accept/publication lock, opaque context ownership, positive pre-handshake barrier, fresh-context probes, independent OpenSSL verification and explicit child reaping. Do not replace tests with comparisons to the same implementation helper that is under test.

Keep runtime code in `src/main`, assertion harness code in `src/test`, and build output only under `build/`. Prefer standard providers and standard libraries. Include English and Portuguese user-facing boundary updates when the trust model changes. Do not commit private run directories, certificate material, passwords, provider transcripts or generated classes.

Submit focused issues or pull requests through the repository. Describe the observed normalized failure code, command and tool versions without including keys, password files, raw certificate dumps or unrelated host information. Security issues belong in the private reporting channel in [SECURITY.md](SECURITY.md), not a public issue.

Contributions must be original work compatible with the MIT license. Do not contribute private client code. There is no release or remote-deployment workflow in this project.
