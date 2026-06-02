package ua.practice.homework2;

import ua.practice.protocol.Message;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class CommandMessageMapper {
    private CommandMessageMapper() {
    }

    public static Message toMessage(CommandRequest request) {
        Objects.requireNonNull(request, "request");

        // cType і userId зберігаються у бінарній частині Message, а решта полів іде в payload.
        // LinkedHashMap зберігає порядок полів, тому payload стабільний і зручний для читання.
        Map<String, String> payload = new LinkedHashMap<>();
        putIfPresent(payload, "groupName", request.groupName());
        putIfPresent(payload, "productName", request.productName());
        if (request.quantity() != null) {
            payload.put("quantity", request.quantity().toString());
        }
        if (request.price() != null) {
            payload.put("price", request.price().toPlainString());
        }

        return new Message(request.type().getCode(), request.userId(), encodePayload(payload));
    }

    public static CommandRequest requestFromMessage(Message message) {
        Objects.requireNonNull(message, "message");
        Map<String, String> payload = decodePayload(message.getPayload());
        // Код команди з протоколу відновлюється назад у enum CommandType.
        return new CommandRequest(
                CommandType.fromCode(message.getCommandType()),
                message.getUserId(),
                payload.get("groupName"),
                payload.get("productName"),
                parseInteger(payload.get("quantity")),
                parseDecimal(payload.get("price"))
        );
    }

    public static Message toMessage(CommandResponse response, int userId) {
        Objects.requireNonNull(response, "response");
        Map<String, String> payload = new LinkedHashMap<>();
        // У відповіді зберігається ознака успіху і, за потреби, текст або кількість товару.
        payload.put("ok", Boolean.toString(response.ok()));
        putIfPresent(payload, "message", response.message());
        if (response.quantity() != null) {
            payload.put("quantity", response.quantity().toString());
        }

        CommandType type = response.ok() ? CommandType.OK : CommandType.ERROR;
        return new Message(type.getCode(), userId, encodePayload(payload));
    }

    public static CommandResponse responseFromMessage(Message message) {
        Objects.requireNonNull(message, "message");
        Map<String, String> payload = decodePayload(message.getPayload());
        CommandType type = CommandType.fromCode(message.getCommandType());
        // Якщо тип відповіді ERROR, результат примусово вважається неуспішним.
        boolean ok = type == CommandType.OK && Boolean.parseBoolean(payload.getOrDefault("ok", "true"));
        String text = payload.getOrDefault("message", ok ? "OK" : "ERROR");
        return new CommandResponse(ok, text, parseInteger(payload.get("quantity")));
    }

    private static byte[] encodePayload(Map<String, String> payload) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : payload.entrySet()) {
            // URL-кодування безпечно зберігає пробіли та спеціальні символи в одному рядку.
            builder.append(entry.getKey())
                    .append('=')
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                    .append('\n');
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static Map<String, String> decodePayload(byte[] payloadBytes) {
        // Payload зберігається як UTF-8 текст, щоб простіше передавати назви товарів і груп.
        String payload = new String(payloadBytes, StandardCharsets.UTF_8);
        Map<String, String> values = new LinkedHashMap<>();
        if (payload.isBlank()) {
            return values;
        }

        String[] lines = payload.split("\\R");
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            // Кожен рядок payload має вигляд key=value.
            int separator = line.indexOf('=');
            if (separator < 0) {
                throw new IllegalArgumentException("Invalid payload line: " + line);
            }
            String key = line.substring(0, separator);
            String value = URLDecoder.decode(line.substring(separator + 1), StandardCharsets.UTF_8);
            values.put(key, value);
        }
        return values;
    }

    private static void putIfPresent(Map<String, String> payload, String key, String value) {
        if (value != null) {
            payload.put(key, value);
        }
    }

    private static Integer parseInteger(String value) {
        return value == null ? null : Integer.parseInt(value);
    }

    private static BigDecimal parseDecimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
