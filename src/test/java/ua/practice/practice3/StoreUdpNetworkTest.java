package ua.practice.practice3;

import org.junit.jupiter.api.Test;
import ua.practice.homework2.CommandRequest;
import ua.practice.homework2.CommandResponse;
import ua.practice.homework2.WarehouseService;
import ua.practice.protocol.AesMessageCipher;
import ua.practice.protocol.PacketCodec;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoreUdpNetworkTest {
    private static final byte[] KEY = "0123456789abcdef".getBytes(StandardCharsets.US_ASCII);

    @Test
    void udpClientRetriesLostResponseWithoutProcessingCommandTwice() throws Exception {
        WarehouseService warehouseService = new WarehouseService();

        try (StoreServerUDP server = new StoreServerUDP(0, codec(), warehouseService, true)) {
            // true означає, що сервер навмисно не відправить першу UDP-відповідь.
            server.start();

            try (StoreClientUDP client = new StoreClientUDP(
                    "127.0.0.1",
                    server.getPort(),
                    codec(),
                    Duration.ofMillis(100),
                    5
            )) {
                // Клієнт має повторити UDP-запит після таймауту і все одно отримати OK.
                CommandResponse addResponse = client.send(CommandRequest.addQuantity(1, "rice", 5));
                assertTrue(addResponse.ok(), addResponse.message());

                // Якщо сервер неправильно обробив retry, кількість стала б 10, а не 5.
                CommandResponse quantityResponse = client.send(CommandRequest.getQuantity(1, "rice"));
                assertTrue(quantityResponse.ok(), quantityResponse.message());
                assertEquals(5, quantityResponse.quantity());
            }
        }
    }

    private static PacketCodec codec() {
        // Той самий ключ використовується для шифрування клієнтом і дешифрування сервером.
        return new PacketCodec(new AesMessageCipher(AesMessageCipher.keyFromBytes(KEY)));
    }
}
