# Disposable PKI

Development 0.1.0 uses keytool rather than custom cryptographic code. `FixturePki` is the exact, fixed argument-template implementation. There is no caller-selected algorithm, remote issuer, host keystore, supplied credential, or external certificate service.

Two sequential CA/leaf sequences create A and B:

1. Generate RSA-2048 CA key with SHA256withRSA, synthetic subject, `basicConstraints ca:true`, and `keyCertSign,cRLSign` usage; validity two days.
2. Export that CA as PEM for explicit OpenSSL verification and trust-store imports.
3. Generate a separate RSA-2048 `server` key with synthetic localhost subject.
4. Create a CSR, sign via the selected synthetic CA with SHA256withRSA, and set `basicConstraints ca:false`, `SAN=dns:localhost`, `digitalSignature,keyEncipherment`, and `serverAuth` EKU on the issued leaf.
5. Import the CA under `issuer`, then the leaf reply under `server`, producing a two-certificate key chain.
6. Remove the CSR and intermediate leaf PEM immediately after import.

Separate PKCS12 stores contain only CA A, only CA B, and both CAs. Those are the only trust sources for Java probes. OpenSSL receives one explicit CA PEM and disables default CA path/store lookup. The service does not request client certificates and its context is initialized with explicit empty trust rather than host default trust.

The runtime password is 64 random printable hexadecimal characters derived from `SecureRandom`. It is written to a 0600 runtime file and passed to keytool with `-storepass:file` and, where relevant, `-keypass:file`. Password values never appear in argv or evidence. This password protects disposable local artifacts; it is not an operational credential-management design. Java copies are cleared when feasible, without claiming guaranteed erasure inside providers or the OS.

The run directory is created 0700. Keytool-created outputs are inaccessible through that parent to other users, then normalized to 0600 after each child completes before application reads. The password file, private keystores, transient public oracle inputs, malformed test files and symlink test are removed using an exact allowlist at resource cleanup. No directory walk or deletion outside that run directory is performed. Only normalized success evidence persists outside it.

The certificates are valid only as synthetic experiment inputs. They are not publicly trusted, must not be installed into host trust, and must never be reused. Initializing a JSSE context confirms parse/key-manager initialization, not a complete audit of certificate extensions, expiry policy, revocation, issuance controls, key provenance or organizational trust.
