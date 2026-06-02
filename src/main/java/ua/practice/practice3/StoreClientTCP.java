package ua.practice.practice3;

import ua.practice.homework2.CommandRequest;
import ua.practice.homework2.CommandResponse;
import ua.practice.protocol.PacketCodec;

import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.time.Duration;
import java.util.Objects;

public final class StoreClientTCP implements AutoCloseable {
    private final String host;
    private final int port;
    private final StoreNetworkCodec networkCodec;
    private final int connectTimeoutMillis;
    private final int reconnectDelayMillis;
    private final int requestTimeoutMillis;

    // Канал зберігається між запитами, щоб клієнт міг повторно використовувати TCP-з'єднання.
    // SocketChannel - NIO-канал для TCP-з'єднання з сервером.
    private SocketChannel channel;

    public StoreClientTCP(String host, int port, PacketCodec packetCodec) {
        this(host, port, packetCodec, Duration.ofMillis(300), Duration.ofMillis(100), Duration.ofSeconds(5));
    }

    public StoreClientTCP(
            String host,
            int port,
            PacketCodec packetCodec,
            Duration connectTimeout,
            Duration reconnectDelay,
            Duration requestTimeout
    ) {
        this.host = Objects.requireNonNull(host, "host");
        this.port = port;
        // StoreNetworkCodec ховає деталі створення зашифрованих пакетів.
        this.networkCodec = new StoreNetworkCodec(packetCodec);
        this.connectTimeoutMillis = toMillis(connectTimeout, "connectTimeout");
        this.reconnectDelayMillis = toMillis(reconnectDelay, "reconnectDelay");
        this.requestTimeoutMillis = toMillis(requestTimeout, "requestTimeout");
    }

    public synchronized CommandResponse send(CommandRequest request) throws IOException {
        Objects.requireNonNull(request, "request");

        // Один дедлайн обмежує всю операцію: підключення, відправку і очікування відповіді.
        long deadline = System.nanoTime() + Duration.ofMillis(requestTimeoutMillis).toNanos();
        IOException lastError = null;

        while (System.nanoTime() < deadline) {
            try {
                // Якщо сервер ще недоступний, клієнт тільки пробує відновити з'єднання і не відправляє пакет.
                ensureConnected(deadline);

                // packetId потрібен, щоб відповідь можна було точно пов'язати із запитом.
                long packetId = networkCodec.nextPacketId();
                // Команда складу перетворюється у байти повного пакета перед відправкою.
                byte[] requestBytes = networkCodec.encodeRequest(request, packetId);
                TcpPacketIO.writePacket(channel, requestBytes, deadline);

                // TCP-відповідь читається за тим самим правилом framing: довжина + байти пакета.
                byte[] responseBytes = TcpPacketIO.readPacket(channel, deadline);
                if (responseBytes == null) {
                    throw new EOFException("Server closed TCP connection");
                }

                StoreNetworkCodec.NetworkResponse response = networkCodec.decodeResponse(responseBytes);
                // Якщо packetId не збігся, це не відповідь на наш поточний запит.
                if (response.packetId() != packetId) {
                    throw new IOException("Unexpected TCP response packetId: " + response.packetId());
                }
                return response.command();
            } catch (IOException | RuntimeException e) {
                // При обриві TCP-з'єднання канал закривається, після чого цикл пробує підключитися знову.
                lastError = asIOException(e);
                closeSocket();
                sleepBeforeReconnect(deadline);
            }
        }

        throw new IOException("TCP server is unavailable", lastError);
    }

    private void ensureConnected(long requestDeadline) throws IOException {
        if (channel != null && channel.isConnected() && channel.isOpen()) {
            return;
        }

        IOException lastError = null;
        while (System.nanoTime() < requestDeadline) {
            try {
                SocketChannel newChannel = SocketChannel.open();
                // Неблокуючий режим дозволяє контролювати таймаут підключення вручну.
                newChannel.configureBlocking(false);
                // TcpNoDelay вимикає затримку малих пакетів алгоритмом Nagle.
                newChannel.socket().setTcpNoDelay(true);
                newChannel.connect(new InetSocketAddress(host, port));
                finishConnect(newChannel, requestDeadline);
                channel = newChannel;
                return;
            } catch (IOException e) {
                lastError = e;
                closeSocket();
                sleepBeforeReconnect(requestDeadline);
            }
        }

        throw new IOException("TCP server is unavailable", lastError);
    }

    private void finishConnect(SocketChannel newChannel, long requestDeadline) throws IOException {
        long connectDeadline = System.nanoTime() + Duration.ofMillis(connectTimeoutMillis).toNanos();
        long deadline = Math.min(connectDeadline, requestDeadline);

        // finishConnect завершує NIO-підключення, яке було розпочате методом connect.
        while (!newChannel.finishConnect()) {
            if (System.nanoTime() >= deadline) {
                newChannel.close();
                throw new IOException("TCP connection timed out");
            }
            sleepBeforeReconnect(deadline);
        }
    }

    private void sleepBeforeReconnect(long deadline) throws IOException {
        // Пауза між спробами не дає клієнту надто часто стукати в недоступний сервер.
        long remainingMillis = Duration.ofNanos(Math.max(0, deadline - System.nanoTime())).toMillis();
        if (remainingMillis <= 0) {
            return;
        }

        try {
            Thread.sleep(Math.min(reconnectDelayMillis, remainingMillis));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for TCP reconnect", e);
        }
    }

    private void closeSocket() {
        if (channel == null) {
            return;
        }

        try {
            channel.close();
        } catch (IOException ignored) {
            // Закриття каналу під час відновлення з'єднання не повинно приховати основну помилку.
        } finally {
            channel = null;
        }
    }

    @Override
    public synchronized void close() {
        closeSocket();
    }

    private static IOException asIOException(Exception exception) {
        if (exception instanceof IOException ioException) {
            return ioException;
        }
        return new IOException(exception);
    }

    private static int toMillis(Duration duration, String fieldName) {
        Objects.requireNonNull(duration, fieldName);
        long millis = duration.toMillis();
        if (millis <= 0 || millis > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(fieldName + " must fit into positive int milliseconds");
        }
        return (int) millis;
    }
}
