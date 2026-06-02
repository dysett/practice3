package ua.practice.homework2;

import java.util.Arrays;

public enum CommandType {
    // Коди 1-6 - це вхідні команди клієнта для роботи зі складом.
    GET_QUANTITY(1),
    WRITE_OFF(2),
    ADD_QUANTITY(3),
    ADD_GROUP(4),
    ADD_PRODUCT_TO_GROUP(5),
    SET_PRICE(6),
    // Коди 1000+ зарезервовані для відповідей сервера.
    OK(1000),
    ERROR(1001);

    private final int code;

    CommandType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static CommandType fromCode(int code) {
        // Під час декодування з пакета приходить int, тому його треба перетворити назад у enum.
        return Arrays.stream(values())
                .filter(type -> type.code == code)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown command type: " + code));
    }
}
