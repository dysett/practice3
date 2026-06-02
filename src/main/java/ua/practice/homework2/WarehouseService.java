package ua.practice.homework2;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class WarehouseService {
    // У мапі товарів зберігається поточна кількість і ціна кожного товару.
    private final Map<String, ProductState> products = new HashMap<>();
    // У мапі груп зберігається, які товари належать до кожної групи.
    private final Map<String, Set<String>> groups = new HashMap<>();

    public synchronized int getQuantity(String productName) {
        // synchronized захищає читання від одночасних змін іншими потоками.
        return product(productName).quantity;
    }

    public synchronized void writeOff(String productName, int quantity) {
        // Уся операція списання атомарна: перевірка і зміна виконуються під одним lock.
        requirePositiveQuantity(quantity);
        ProductState product = product(productName);
        if (product.quantity < quantity) {
            // Списання не може зробити від'ємний залишок на складі.
            throw new IllegalArgumentException("Not enough product quantity: " + productName);
        }
        product.quantity -= quantity;
    }

    public synchronized void addQuantity(String productName, int quantity) {
        // Додавання кількості також атомарне, тому паралельні клієнти не втрачають оновлення.
        requirePositiveQuantity(quantity);
        product(productName).quantity += quantity;
    }

    public synchronized void addGroup(String groupName) {
        groups.computeIfAbsent(requireName(groupName, "groupName"), ignored -> new HashSet<>());
    }

    public synchronized void addProductToGroup(String groupName, String productName) {
        String group = requireName(groupName, "groupName");
        String product = requireName(productName, "productName");
        // Якщо групи або товару ще немає, вони створюються під час першого звернення.
        addGroup(group);
        product(product);
        groups.get(group).add(product);
    }

    public synchronized void setPrice(String productName, BigDecimal price) {
        Objects.requireNonNull(price, "price");
        if (price.signum() < 0) {
            throw new IllegalArgumentException("price must be zero or positive");
        }
        product(productName).price = price;
    }

    public synchronized BigDecimal getPrice(String productName) {
        return product(productName).price;
    }

    public synchronized boolean groupContainsProduct(String groupName, String productName) {
        return groups.getOrDefault(groupName, Set.of()).contains(productName);
    }

    private ProductState product(String productName) {
        // Товар створюється ліниво, щоб команди могли працювати з новими назвами без окремої реєстрації.
        return products.computeIfAbsent(requireName(productName, "productName"), ignored -> new ProductState());
    }

    private static String requireName(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private static void requirePositiveQuantity(int quantity) {
        // Нульова або від'ємна кількість не має сенсу для зарахування чи списання.
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
    }

    private static final class ProductState {
        private int quantity;
        private BigDecimal price = BigDecimal.ZERO;
    }
}
