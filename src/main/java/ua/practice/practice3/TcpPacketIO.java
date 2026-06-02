package ua.practice.practice3;

import java.io.EOFException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

final class TcpPacketIO {
    // Обмеження захищає сервер від надто великих або пошкоджених TCP-кадрів.
    private static final int MAX_FRAME_LENGTH = 1024 * 1024;
    private static final int LENGTH_FIELD_BYTES = Integer.BYTES;
    private static final long NO_DEADLINE = 0L;

    private TcpPacketIO() {
    }

    static void writePacket(SocketChannel channel, byte[] packetBytes) throws IOException {
        writePacket(channel, packetBytes, NO_DEADLINE);
    }

    static void writePacket(SocketChannel channel, byte[] packetBytes, long deadlineNanos) throws IOException {
        if (packetBytes.length <= 0 || packetBytes.length > MAX_FRAME_LENGTH) {
            throw new IOException("Invalid TCP packet frame length: " + packetBytes.length);
        }

        // TCP є потоком байтів, тому перед пакетом записується 4-байтова довжина.
        ByteBuffer frame = ByteBuffer.allocate(LENGTH_FIELD_BYTES + packetBytes.length);
        frame.putInt(packetBytes.length);
        frame.put(packetBytes);
        // flip переводить ByteBuffer з режиму запису в режим читання перед channel.write.
        frame.flip();

        writeFully(channel, frame, deadlineNanos);
    }

    static byte[] readPacket(SocketChannel channel) throws IOException {
        return readPacket(channel, NO_DEADLINE);
    }

    static byte[] readPacket(SocketChannel channel, long deadlineNanos) throws IOException {
        // Спочатку читаємо довжину, потім рівно стільки байтів тіла пакета.
        ByteBuffer lengthBuffer = ByteBuffer.allocate(LENGTH_FIELD_BYTES);
        boolean hasLength = readFully(channel, lengthBuffer, deadlineNanos, true);
        if (!hasLength) {
            return null;
        }

        lengthBuffer.flip();
        // Довжина записувалась big-endian стандартним ByteBuffer, тому так само читається через getInt.
        int length = lengthBuffer.getInt();
        if (length <= 0 || length > MAX_FRAME_LENGTH) {
            throw new IOException("Invalid TCP packet frame length: " + length);
        }

        ByteBuffer packetBuffer = ByteBuffer.allocate(length);
        readFully(channel, packetBuffer, deadlineNanos, false);
        return packetBuffer.array();
    }

    private static void writeFully(SocketChannel channel, ByteBuffer buffer, long deadlineNanos) throws IOException {
        while (buffer.hasRemaining()) {
            int written = channel.write(buffer);
            if (written == 0) {
                // У NIO запис може записати 0 байтів, тому треба повторити спробу пізніше.
                waitForChannel(deadlineNanos);
            }
        }
    }

    private static boolean readFully(
            SocketChannel channel,
            ByteBuffer buffer,
            long deadlineNanos,
            boolean allowCleanEof
    ) throws IOException {
        boolean readAnyByte = false;
        while (buffer.hasRemaining()) {
            int read = channel.read(buffer);
            if (read < 0) {
                if (allowCleanEof && !readAnyByte) {
                    return false;
                }
                throw new EOFException("TCP connection was closed while reading packet");
            }
            if (read == 0) {
                // У NIO читання може тимчасово не дати байтів, навіть якщо канал ще відкритий.
                waitForChannel(deadlineNanos);
            } else {
                readAnyByte = true;
            }
        }
        return true;
    }

    private static void waitForChannel(long deadlineNanos) throws IOException {
        if (deadlineNanos != NO_DEADLINE && System.nanoTime() >= deadlineNanos) {
            throw new SocketTimeoutException("TCP packet operation timed out");
        }

        try {
            // Короткий sleep не навантажує CPU постійним циклом очікування.
            Thread.sleep(5);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for TCP channel", e);
        }
    }
}
