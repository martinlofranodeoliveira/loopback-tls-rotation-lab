package br.com.lofrano.tlslab;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

final class FrameTests {
    private FrameTests() { }

    private static Frames.Line read(byte[] bytes) throws IOException {
        return Frames.read(new ByteArrayInputStream(bytes), 1024);
    }

    static void run(Evidence evidence) throws Exception {
        Check.equal(read(new byte[0]), null, "CLEAN_EOF");
        Check.equal(read(new byte[] {'\n'}), new Frames.Line("", 0), "EMPTY_LINE");
        String unicode = "é".repeat(512);
        Check.equal(read((unicode + "\n").getBytes(StandardCharsets.UTF_8)),
                new Frames.Line(unicode, 1024), "BYTE_NOT_CHARACTER_BOUND");
        Check.rejects(IOException.class,
                () -> read(("é".repeat(513) + "\n").getBytes(StandardCharsets.UTF_8)), "UNICODE_OVERSIZE");
        Check.rejects(IOException.class, () -> read(new byte[] {(byte) 0xc0, (byte) 0xaf, '\n'}),
                "OVERLONG_UTF8");
        Check.rejects(IOException.class, () -> read(new byte[] {(byte) 0xed, (byte) 0xa0, (byte) 0x80, '\n'}),
                "SURROGATE_UTF8");
        Check.rejects(IOException.class, () -> read(new byte[] {(byte) 0xc3, '\n'}), "INCOMPLETE_UTF8");
        Check.rejects(IOException.class, () -> read(new byte[] {'x'}), "TRUNCATED_LINE");
        Check.rejects(IOException.class, () -> read(new byte[] {'x', '\r', '\n'}), "CRLF_REJECTED");
        ByteArrayInputStream two = new ByteArrayInputStream("a\nb\n".getBytes(StandardCharsets.US_ASCII));
        Check.equal(Frames.read(two, 1024).text(), "a", "FIRST_FRAME");
        Check.equal(Frames.read(two, 1024).text(), "b", "SECOND_FRAME");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Check.rejects(IOException.class, () -> Frames.write(out, "injected\nreply", 64), "OUTPUT_INJECTION");
        Check.rejects(IOException.class, () -> Frames.write(out, "x".repeat(65), 64), "OUTPUT_BOUND");
        Check.equal(out.size(), 0, "NO_PARTIAL_INVALID_OUTPUT");
        evidence.add("frames");
    }
}
