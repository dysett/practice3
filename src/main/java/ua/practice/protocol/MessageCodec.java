package ua.practice.protocol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Objects;

public final class MessageCodec {
    // Повідомлення складається з двох int-полів: cType і bUserId.
    private static final int HEADER_LENGTH = 8;

    private MessageCodec() {
    }

    public static byte[] encode(Message message) {
        Objects.requireNonNull(message, "message");
        byte[] payload = message.getPayload();

        // ByteBuffer записує примітиви у правильному порядку байтів.
        // Після cType і userId без змін додається payload команди.
        return ByteBuffer.allocate(HEADER_LENGTH + payload.length)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(message.getCommandType())
                .putInt(message.getUserId())
                .put(payload)
                .array();
    }

    public static Message decode(byte[] plainMessage) {
        Objects.requireNonNull(plainMessage, "plainMessage");
        if (plainMessage.length < HEADER_LENGTH) {
            throw new ProtocolException("Message is shorter than 8 bytes");
        }

        ByteBuffer buffer = ByteBuffer.wrap(plainMessage).order(ByteOrder.BIG_ENDIAN);
        // Читання має йти в тому самому порядку, у якому поля були записані.
        int commandType = buffer.getInt();
        int userId = buffer.getInt();
        // Усе після перших 8 байтів є корисним навантаженням команди.
        byte[] payload = Arrays.copyOfRange(plainMessage, HEADER_LENGTH, plainMessage.length);
        return new Message(commandType, userId, payload);
    }
}
