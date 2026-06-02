package ua.practice.practice3;

import ua.practice.homework2.CommandResponse;
import ua.practice.homework2.WarehouseService;
import ua.practice.protocol.PacketCodec;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class StoreServerTCP implements AutoCloseable {
    // ServerSocketChannel - NIO-аналог ServerSocket для прийому TCP-підключень.
    private final ServerSocketChannel serverChannel;
    private final int port;
    private final ExecutorService executor;
    private final StoreNetworkCodec networkCodec;
    private final StoreCommandHandler commandHandler;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public StoreServerTCP(int port, PacketCodec packetCodec, WarehouseService warehouseService) throws IOException {
        // Відкриваємо NIO-канал сервера замість класичного ServerSocket.
        this.serverChannel = ServerSocketChannel.open();
        // Серверний канал блокуючий: accept чекає нового клієнта у службовому потоці.
        this.serverChannel.configureBlocking(true);
        this.serverChannel.bind(new InetSocketAddress(port));
        // Якщо port = 0, ОС сама вибирає вільний порт; getLocalAddress повертає фактичне значення.
        this.port = ((InetSocketAddress) serverChannel.getLocalAddress()).getPort();
        this.executor = Executors.newCachedThreadPool(daemonThreadFactory("store-tcp-nio"));
        this.networkCodec = new StoreNetworkCodec(packetCodec);
        this.commandHandler = new StoreCommandHandler(warehouseService);
    }

    public int getPort() {
        return port;
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            // Окремий потік приймає нових клієнтів, щоб основний потік не блокувався на accept.
            executor.execute(this::acceptLoop);
        }
    }

    private void acceptLoop() {
        while (running.get()) {
            try {
                SocketChannel clientChannel = serverChannel.accept();
                // Для кожного TCP-клієнта запускається окремий обробник, як у прикладі з кількома клієнтами.
                executor.execute(() -> handleClient(clientChannel));
            } catch (AsynchronousCloseException e) {
                // При close() канал закривається асинхронно, тому accept може вийти через цей виняток.
                if (running.get()) {
                    throw new IllegalStateException("TCP server channel was closed", e);
                }
            } catch (IOException e) {
                if (running.get()) {
                    throw new IllegalStateException("Failed to accept TCP client", e);
                }
            }
        }
    }

    private void handleClient(SocketChannel clientChannel) {
        try (SocketChannel channel = clientChannel) {
            // Після accept сервер працює з конкретним клієнтом через його SocketChannel.
            channel.configureBlocking(true);
            while (running.get() && channel.isOpen()) {
                // TCP не зберігає межі повідомлень, тому читаємо пакет через власний framing.
                byte[] requestBytes = TcpPacketIO.readPacket(channel);
                if (requestBytes == null) {
                    return;
                }

                byte[] responseBytes = handleRequest(requestBytes);
                TcpPacketIO.writePacket(channel, responseBytes);
            }
        } catch (IOException | RuntimeException ignored) {
            // Один проблемний клієнт не повинен зупиняти весь сервер.
        }
    }

    private byte[] handleRequest(byte[] requestBytes) {
        // На цьому етапі байти вже отримані з мережі, але ще треба перевірити CRC і розшифрувати message.
        StoreNetworkCodec.NetworkRequest request = networkCodec.decodeRequest(requestBytes);
        CommandResponse response = commandHandler.process(request.command());
        // Відповідь кодується з тим самим packetId, щоб клієнт міг перевірити відповідність.
        return networkCodec.encodeResponse(response, request.command().userId(), request.packetId());
    }

    @Override
    public void close() throws IOException {
        running.set(false);
        // Закриття каналу розблокує acceptLoop, якщо він зараз чекає нового клієнта.
        serverChannel.close();
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
}
