package ua.practice.practice3;

import ua.practice.homework2.CommandResponse;
import ua.practice.homework2.WarehouseService;
import ua.practice.protocol.PacketCodec;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.DatagramChannel;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class StoreServerUDP implements AutoCloseable {
    private static final int MAX_DATAGRAM_LENGTH = 65_507;

    private final DatagramChannel channel;
    private final int port;
    private final ExecutorService executor;
    private final StoreNetworkCodec networkCodec;
    private final StoreCommandHandler commandHandler;
    // Кеш захищає склад від повторної обробки тієї самої UDP-команди після retry.
    private final ConcurrentMap<UdpRequestKey, byte[]> responseCache = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    // Прапорець потрібен для тесту втрати першої UDP-відповіді.
    private final AtomicBoolean dropNextResponse;

    public StoreServerUDP(int port, PacketCodec packetCodec, WarehouseService warehouseService) throws IOException {
        this(port, packetCodec, warehouseService, false);
    }

    public StoreServerUDP(
            int port,
            PacketCodec packetCodec,
            WarehouseService warehouseService,
            boolean dropFirstResponse
    ) throws IOException {
        // DatagramChannel використовується замість DatagramSocket, щоб реалізація відповідала Java NIO.
        this.channel = DatagramChannel.open();
        // Серверний UDP-канал блокуючий: receive чекає нову датаграму у службовому потоці.
        this.channel.configureBlocking(true);
        this.channel.bind(new InetSocketAddress(port));
        // Порт зберігається окремо, бо при port = 0 його вибирає операційна система.
        this.port = ((InetSocketAddress) channel.getLocalAddress()).getPort();
        this.executor = Executors.newSingleThreadExecutor(daemonThreadFactory("store-udp-nio"));
        this.networkCodec = new StoreNetworkCodec(packetCodec);
        this.commandHandler = new StoreCommandHandler(warehouseService);
        this.dropNextResponse = new AtomicBoolean(dropFirstResponse);
    }

    public int getPort() {
        return port;
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            executor.execute(this::receiveLoop);
        }
    }

    private void receiveLoop() {
        ByteBuffer buffer = ByteBuffer.allocate(MAX_DATAGRAM_LENGTH);

        while (running.get()) {
            try {
                // Один і той самий буфер можна використовувати для багатьох UDP-пакетів.
                buffer.clear();
                SocketAddress clientAddress = channel.receive(buffer);
                if (clientAddress == null) {
                    continue;
                }

                // Після receive буфер переводиться в режим читання, щоб дістати саме отримані байти.
                buffer.flip();
                byte[] requestBytes = new byte[buffer.remaining()];
                buffer.get(requestBytes);
                handleDatagram(clientAddress, requestBytes);
            } catch (AsynchronousCloseException e) {
                if (running.get()) {
                    throw new IllegalStateException("UDP server channel was closed", e);
                }
            } catch (IOException | RuntimeException ignored) {
                // Некоректна датаграма або пошкоджений пакет просто ігнорується, сервер продовжує роботу.
            }
        }
    }

    private void handleDatagram(SocketAddress clientAddress, byte[] requestBytes) throws IOException {
        // Після декодування пакет уже пройшов перевірку CRC і дешифрування.
        StoreNetworkCodec.NetworkRequest request = networkCodec.decodeRequest(requestBytes);
        // Ключ включає адресу клієнта і packetId, бо різні клієнти можуть мати однакові номери пакетів.
        UdpRequestKey key = new UdpRequestKey(clientAddress, request.packetId());

        byte[] responseBytes = responseCache.computeIfAbsent(key, ignored -> {
            // computeIfAbsent гарантує, що повторний UDP-пакет не виконає команду складу ще раз.
            CommandResponse response = commandHandler.process(request.command());
            return networkCodec.encodeResponse(response, request.command().userId(), request.packetId());
        });

        if (dropNextResponse.compareAndSet(true, false)) {
            // Цей прапорець використовується тестом, щоб показати втрату UDP-відповіді.
            return;
        }

        // DatagramChannel.send відправляє одну UDP-датаграму на адресу клієнта.
        channel.send(ByteBuffer.wrap(responseBytes), clientAddress);
    }

    @Override
    public void close() throws IOException {
        running.set(false);
        channel.close();
        executor.shutdownNow();
    }

    private static ThreadFactory daemonThreadFactory(String prefix) {
        AtomicInteger threadNumber = new AtomicInteger(1);
        return task -> {
            Thread thread = new Thread(task, prefix + "-" + threadNumber.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
    }

    private record UdpRequestKey(SocketAddress clientAddress, long packetId) {
        private UdpRequestKey {
            Objects.requireNonNull(clientAddress, "clientAddress");
        }
    }
}
