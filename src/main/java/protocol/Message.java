package ua.practice.protocol;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

public final class Message {
    private final int commandType;
    private final int userId;
    private final byte[] payload;

    public Message(int commandType, int userId, byte[] payload) {
        this.commandType = commandType;
        this.userId = userId;
        // Копія масиву захищає Message від зміни payload ззовні.
        this.payload = Arrays.copyOf(Objects.requireNonNull(payload, "payload"), payload.length);
    }

    public static Message json(int commandType, int userId, String json) {
        Objects.requireNonNull(json, "json");
        return new Message(commandType, userId, json.getBytes(StandardCharsets.UTF_8));
    }

    public int getCommandType() {
        return commandType;
    }

    public int getUserId() {
        return userId;
    }

    public byte[] getPayload() {
        // Повертається копія, щоб об'єкт залишався незмінним після створення.
        return Arrays.copyOf(payload, payload.length);
    }

    public String payloadAsString(Charset charset) {
        return new String(payload, Objects.requireNonNull(charset, "charset"));
    }
}
