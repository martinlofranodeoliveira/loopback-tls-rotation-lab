# Fixed protocols — development 0.1.0

## TLS line service

Transport: loopback IPv4 TCP, TLS 1.3, server authentication only. There is no unauthenticated plaintext command channel on the service socket.

A request is zero through 1024 bytes followed by byte LF (0x0A). LF is not part of the payload count. Byte CR (0x0D) is rejected anywhere, including CRLF. Decode only after bounding bytes, using strict UTF-8; malformed encodings and surrogate encodings reject. Other valid UTF-8 characters, including NUL, are not interpreted as commands. Payload is never reflected.

A successful response is ASCII:

```text
GEN 1 BYTES 5
```

The actual positive decimal generation and payload byte count replace those example numbers. Response content before LF is at most 64 bytes, including for the largest generation. There is one reply per complete valid frame. Clean EOF before a new frame ends normally. Partial EOF, excess length, decoding error, TLS error or timeout closes the connection without an application error frame. Exactly sixteen valid requests can receive replies; the server closes after the sixteenth reply.

Publication never sends an unsolicited frame and never changes the captured generation on a connection.

## Separate Java client's controller pipes

These are local harness pipes, not a service administration interface. The fixed client arguments are port, explicit fixture trust path, runtime password-file path, logical host and `normal`/`delayed` mode. Physical destination is not configurable.

Normal startup emits `READY TLSv1.3` after verified handshake. Delayed startup emits `CONNECTED` after TCP connect, waits for `HANDSHAKE` on stdin, then handshakes and emits `READY TLSv1.3`. The harness separately waits for the server's accepted counter/generation; TCP connected alone is insufficient proof of capture.

Controller request `SEND ` followed by Base64 and LF carries at most 1026 decoded bytes, enough to exercise a 1025-byte payload plus LF. The client emits `REPLY ` followed by the bounded service reply, or `CLOSED` when the application transport ends. `QUIT` or controller EOF ends normally. At most eighteen controller commands are read after handshake. Control lines are bounded to 2048 bytes, and child output queues/capture are bounded separately.

A TLS handshake SSL exception emits `VERIFY_FAILED` and exit 10 only when a standard CertificateException appears in its bounded cause chain. EOF, a generic alert or transport failure alone reports `CLIENT_ERROR` / exit 20. This conservative classification is tested with the fixed JDK 21 standard provider; unrecognized provider failures do not count as verification evidence. Other setup/protocol errors emit `CLIENT_ERROR` or return exit 20. The acceptance harness requires the dedicated verification result and independently checks positives; arbitrary connection/setup failures are not accepted as negative trust evidence.

A read timeout reports CLIENT_ERROR with exit 20 rather than CLOSED. It aborts its owned raw transport before TLS cleanup so a silent peer cannot add another close-notify wait. CLOSED requires an observed EOF or connection I/O failure distinct from timeout.
