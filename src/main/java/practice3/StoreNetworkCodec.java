package ua.practice.practice3;

import ua.practice.homework2.CommandMessageMapper;
import ua.practice.homework2.CommandRequest;
import ua.practice.homework2.CommandResponse;
import ua.practice.protocol.Packet;
import ua.practice.protocol.PacketCodec;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

final class StoreNetworkCodec {
    private static final int CLIENT_SOURCE = 1;
    private static final int SERVER_SOURCE = 2;

    // PacketCodec відповідає за структуру пакета, CRC16 і шифрування повідомлення.
    private final PacketCodec packetCodec;
    private final AtomicLong packetIds = new AtomicLong(1);

    StoreNetworkCodec(PacketCodec packetCodec) {
        this.packetCodec = Objects.requireNonNull(packetCodec, "packetCodec");
    }

    long nextPacketId() {
        // AtomicLong безпечно видає унікальні номери навіть при паралельних клієнтах.
        return packetIds.getAndIncrement();
    }

    byte[] encodeRequest(CommandRequest request, long packetId) {
        // Доменна команда складу спочатку стає Message, а потім повним зашифрованим Packet.
        Packet packet = new Packet(CLIENT_SOURCE, packetId, CommandMessageMapper.toMessage(request));
        return packetCodec.encode(packet);
    }

    byte[] encodeResponse(CommandResponse response, int userId, long packetId) {
        // Відповідь використовує той самий packetId, що й запит, щоб клієнт міг її перевірити.
        Packet packet = new Packet(SERVER_SOURCE, packetId, CommandMessageMapper.toMessage(response, userId));
        return packetCodec.encode(packet);
    }

    NetworkRequest decodeRequest(byte[] packetBytes) {
        Packet packet = packetCodec.decode(packetBytes);
        return new NetworkRequest(
                packet.getSource(),
                packet.getPacketId(),
                CommandMessageMapper.requestFromMessage(packet.getMessage())
        );
    }

    NetworkResponse decodeResponse(byte[] packetBytes) {
        Packet packet = packetCodec.decode(packetBytes);
        return new NetworkResponse(
                packet.getSource(),
                packet.getPacketId(),
                CommandMessageMapper.responseFromMessage(packet.getMessage())
        );
    }

    // Невеликий record зберігає вже розшифровану команду разом із службовими полями пакета.
    record NetworkRequest(int source, long packetId, CommandRequest command) {
    }

    record NetworkResponse(int source, long packetId, CommandResponse command) {
    }
}
