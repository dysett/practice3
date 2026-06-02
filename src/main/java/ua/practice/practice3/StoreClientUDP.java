package ua.practice.practice3;

import ua.practice.homework2.CommandRequest;
import ua.practice.homework2.CommandResponse;
import ua.practice.protocol.PacketCodec;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.time.Duration;
import java.util.Objects;

public final class StoreClientUDP implements AutoCloseable {
    private static final int MAX_DATAGRAM_LENGTH = 65_507;

    // DatagramChannel - NIO-канал для UDP. Він працює з окремими датаграмами.
    private final DatagramChannel channel;
    // Адреса сервера зберігається один раз і використовується для кожної відправки.
    private final InetSocketAddress serverAddress;
    private final StoreNetworkCodec networkCodec;
    private final long responseTimeoutNanos;
    private final int maxAttempts;

    public StoreClientUDP(String host, int port, PacketCodec packetCodec) throws IOException {
        this(host, port, packetCodec, Duration.ofMillis(300), 5);
    }

    public StoreClientUDP(
            String host,
            int port,
            PacketCodec packetCodec,
            Duration responseTimeout,
            int maxAttempts
    ) throws IOException {
        this.channel = DatagramChannel.open();
        // Неблокуючий режим дозволяє самостійно рахувати таймаут очікування відповіді.
        this.channel.configureBlocking(false);
        this.serverAddress = new InetSocketAddress(Objects.requireNonNull(host, "host"), port);
        this.networkCodec = new StoreNetworkCodec(packetCodec);
        this.responseTimeoutNanos = toNanos(responseTimeout, "responseTimeout");
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        this.maxAttempts = maxAttempts;
    }

    public synchronized CommandResponse send(CommandRequest request) throws IOException {
        Objects.requireNonNull(request, "request");

        // packetId створюється один раз на логічний запит і не змінюється між UDP-повторами.
        long packetId = networkCodec.nextPacketId();
        byte[] requestBytes = networkCodec.encodeRequest(request, packetId);

        SocketTimeoutException lastTimeout = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            // Для UDP повторно відправляється той самий пакет із тим самим packetId.
            // Завдяки цьому сервер може відрізнити повтор від нової команди.
            channel.send(ByteBuffer.wrap(requestBytes), serverAddress);

            try {
                StoreNetworkCodec.NetworkResponse response = waitForResponse(
                        packetId,
                        System.nanoTime() + responseTimeoutNanos
                );
                return response.command();
            } catch (SocketTimeoutException e) {
                // Таймаут означає, що запит або відповідь могли загубитися, тому буде наступна спроба.
                lastTimeout = e;
            }
        }

        throw new IOException("UDP response was not received after " + maxAttempts + " attempts", lastTimeout);
    }

    private StoreNetworkCodec.NetworkResponse waitForResponse(long packetId, long deadlineNanos) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(MAX_DATAGRAM_LENGTH);

        while (System.nanoTime() < deadlineNanos) {
            buffer.clear();
            // У неблокуючому режимі receive повертає null, якщо відповіді ще немає.
            if (channel.receive(buffer) == null) {
                sleepForDatagram();
                continue;
            }

            // Після отримання датаграми зчитуємо тільки реально отримані байти.
            buffer.flip();
            byte[] responseBytes = new byte[buffer.remaining()];
            buffer.get(responseBytes);

            StoreNetworkCodec.NetworkResponse response = networkCodec.decodeResponse(responseBytes);
            // UDP може принести стару відповідь, тому packetId обов'язково перевіряється.
            if (response.packetId() == packetId) {
                return response;
            }
            // Стара або чужа відповідь ігнорується, клієнт чекає саме свій packetId.
        }

        throw new SocketTimeoutException("UDP response timeout for packetId=" + packetId);
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }

    private static void sleepForDatagram() throws IOException {
        try {
            // Невелика пауза робить очікування відповіді спокійним для процесора.
            Thread.sleep(5);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for UDP response", e);
        }
    }

    private static long toNanos(Duration duration, String fieldName) {
        Objects.requireNonNull(duration, fieldName);
        long nanos = duration.toNanos();
        if (nanos <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return nanos;
    }
}
