package ua.practice.protocol;

import java.util.Objects;

public final class Packet {
    private final int source;
    private final long packetId;
    private final Message message;

    public Packet(int source, long packetId, Message message) {
        if (source < 0 || source > 0xFF) {
            throw new IllegalArgumentException("source must fit into one unsigned byte");
        }
        this.source = source;
        this.packetId = packetId;
        this.message = Objects.requireNonNull(message, "message");
    }

    public int getSource() {
        return source;
    }

    public long getPacketId() {
        return packetId;
    }

    public Message getMessage() {
        return message;
    }
}
