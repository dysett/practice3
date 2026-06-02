package ua.practice.homework2;

public record CommandResponse(boolean ok, String message, Integer quantity) {
    public static CommandResponse success() {
        // Стандартна успішна відповідь для команд, які не повертають кількість.
        return new CommandResponse(true, "OK", null);
    }

    public static CommandResponse quantity(int quantity) {
        // Окрема відповідь для команди отримання кількості товару на складі.
        return new CommandResponse(true, "OK", quantity);
    }

    public static CommandResponse error(String message) {
        // Помилка повертається клієнту як звичайна відповідь протоколу.
        return new CommandResponse(false, message, null);
    }
}
