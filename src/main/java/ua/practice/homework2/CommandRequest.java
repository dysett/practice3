package ua.practice.homework2;

import java.math.BigDecimal;
import java.util.Objects;

public record CommandRequest(
        CommandType type,
        int userId,
        String groupName,
        String productName,
        Integer quantity,
        BigDecimal price
) {
    // record автоматично створює поля, конструктор, equals/hashCode і getters-методи.
    public CommandRequest {
        // Тип команди обов'язковий, бо саме він записується у поле cType протоколу.
        Objects.requireNonNull(type, "type");
    }

    public static CommandRequest getQuantity(int userId, String productName) {
        // Для запиту кількості достатньо знати користувача і назву товару.
        return new CommandRequest(CommandType.GET_QUANTITY, userId, null, productName, null, null);
    }

    public static CommandRequest writeOff(int userId, String productName, int quantity) {
        // Списання потребує товар і кількість, яку треба відняти зі складу.
        return new CommandRequest(CommandType.WRITE_OFF, userId, null, productName, quantity, null);
    }

    public static CommandRequest addQuantity(int userId, String productName, int quantity) {
        // Зарахування додає кількість до вже наявного або нового товару.
        return new CommandRequest(CommandType.ADD_QUANTITY, userId, null, productName, quantity, null);
    }

    public static CommandRequest addGroup(int userId, String groupName) {
        // Група створюється окремо, а товари можна додавати до неї пізніше.
        return new CommandRequest(CommandType.ADD_GROUP, userId, groupName, null, null, null);
    }

    public static CommandRequest addProductToGroup(int userId, String groupName, String productName) {
        // Команда пов'язує конкретну назву товару з конкретною групою.
        return new CommandRequest(CommandType.ADD_PRODUCT_TO_GROUP, userId, groupName, productName, null, null);
    }

    public static CommandRequest setPrice(int userId, String productName, BigDecimal price) {
        // BigDecimal використовується для ціни, щоб не втрачати копійки через double.
        return new CommandRequest(CommandType.SET_PRICE, userId, null, productName, null, price);
    }
}
