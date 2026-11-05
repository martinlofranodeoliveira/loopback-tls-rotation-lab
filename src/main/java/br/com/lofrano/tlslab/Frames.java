package br.com.lofrano.tlslab;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

final class Frames {
    private Frames() { }

    record Line(String text, int bytes) { }

    static Line read(InputStream input, int limit) throws IOException {
        if (limit < 1 || limit > 2048) {
            throw new IllegalArgumentException("INVALID_LINE_BOUND");
        }
        byte[] buffer = new byte[limit];
        int size = 0;
        while (true) {
            int next = input.read();
            if (next == -1) {
                if (size == 0) {
                    return null;
                }
                throw new IOException("TRUNCATED_FRAME");
            }
            if (next == '\n') {
                break;
            }
            if (next == '\r') {
                throw new IOException("CR_REJECTED");
            }
            if (size == limit) {
                throw new IOException("LINE_TOO_LONG");
            }
            buffer[size++] = (byte) next;
        }
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(buffer, 0, size)).toString();
            return new Line(text, size);
        } catch (CharacterCodingException failure) {
            throw new IOException("INVALID_UTF8");
        }
    }

    static void write(OutputStream output, String text, int limit) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > limit || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) {
            throw new IOException("INVALID_OUTPUT_FRAME");
        }
        output.write(bytes);
        output.write('\n');
        output.flush();
    }
}
