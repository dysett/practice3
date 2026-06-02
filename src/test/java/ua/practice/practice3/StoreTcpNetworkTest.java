package ua.practice.practice3;

import org.junit.jupiter.api.Test;
import ua.practice.homework2.CommandRequest;
import ua.practice.homework2.CommandResponse;
import ua.practice.homework2.WarehouseService;
import ua.practice.protocol.AesMessageCipher;
import ua.practice.protocol.PacketCodec;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoreTcpNetworkTest {
    private static final byte[] KEY = "0123456789abcdef".getBytes(StandardCharsets.US_ASCII);

    @Test
    void tcpServerHandlesSeveralClientsAtOnce() throws Exception {
        // Спільний склад використовується одним сервером для всіх TCP-клієнтів.
        WarehouseService warehouseService = new WarehouseService();
        ExecutorService clients = Executors.newFixedThreadPool(4);

        try (StoreServerTCP server = new StoreServerTCP(0, codec(), warehouseService)) {
            // Порт 0 означає, що операційна система сама вибере вільний порт для тесту.
            server.start();

            List<Future<Void>> futures = new ArrayList<>();
            for (int clientIndex = 0; clientIndex < 4; clientIndex++) {
                int userId = clientIndex + 1;
                // Кожен клієнт у своєму потоці відправляє 20 команд зарахування товару.
                futures.add(clients.submit(sendAddQuantityRequests(server.getPort(), userId)));
            }

            for (Future<Void> future : futures) {
                // Якщо клієнт зависне або впаде, тест не буде чекати безкінечно.
                future.get(5, TimeUnit.SECONDS);
            }

            try (StoreClientTCP verifier = new StoreClientTCP("127.0.0.1", server.getPort(), codec())) {
                // 4 клієнти * 20 команд * 1 одиниця = 80 одиниць товару.
                CommandResponse response = verifier.send(CommandRequest.getQuantity(100, "buckwheat"));

                assertTrue(response.ok(), response.message());
                assertEquals(80, response.quantity());
            }
        } finally {
            clients.shutdownNow();
        }
    }

    @Test
    void tcpClientWaitsUntilServerBecomesAvailable() throws Exception {
        // Спочатку беремо вільний порт, але сервер на ньому ще не запускаємо.
        int port = freePort();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        StoreClientTCP client = new StoreClientTCP(
                "127.0.0.1",
                port,
                codec(),
                Duration.ofMillis(100),
                Duration.ofMillis(50),
                Duration.ofSeconds(3)
        );

        try {
            // Запит стартує до запуску сервера, тому клієнт має чекати і пробувати перепідключення.
            Future<CommandResponse> future = executor.submit(
                    () -> client.send(CommandRequest.getQuantity(1, "rice"))
            );

            // Невелика пауза гарантує, що перші спроби підключення відбудуться при недоступному сервері.
            Thread.sleep(250);

            try (StoreServerTCP server = new StoreServerTCP(port, codec(), new WarehouseService())) {
                server.start();

                // Після старту сервера клієнт має завершити той самий логічний запит успішно.
                CommandResponse response = future.get(3, TimeUnit.SECONDS);
                assertTrue(response.ok(), response.message());
                assertEquals(0, response.quantity());
            }
        } finally {
            client.close();
            executor.shutdownNow();
        }
    }

    private static Callable<Void> sendAddQuantityRequests(int port, int userId) {
        return () -> {
            try (StoreClientTCP client = new StoreClientTCP("127.0.0.1", port, codec())) {
                for (int requestIndex = 0; requestIndex < 20; requestIndex++) {
                    // Одна команда додає одну одиницю, тому фінальний результат легко перевірити.
                    CommandResponse response = client.send(CommandRequest.addQuantity(userId, "buckwheat", 1));
                    assertTrue(response.ok(), response.message());
                }
            }
            return null;
        };
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            // Після закриття цього тимчасового сокета порт використовується сервером у тесті.
            return socket.getLocalPort();
        }
    }

    private static PacketCodec codec() {
        // В усіх клієнтів і сервера має бути однаковий AES-ключ, інакше дешифрування не спрацює.
        return new PacketCodec(new AesMessageCipher(AesMessageCipher.keyFromBytes(KEY)));
    }
}
