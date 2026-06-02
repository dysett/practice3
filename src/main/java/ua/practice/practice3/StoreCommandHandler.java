package ua.practice.practice3;

import ua.practice.homework2.CommandRequest;
import ua.practice.homework2.CommandResponse;
import ua.practice.homework2.WarehouseService;

import java.math.BigDecimal;
import java.util.Objects;

public final class StoreCommandHandler {
    private final WarehouseService warehouseService;

    public StoreCommandHandler(WarehouseService warehouseService) {
        this.warehouseService = Objects.requireNonNull(warehouseService, "warehouseService");
    }

    public CommandResponse process(CommandRequest request) {
        Objects.requireNonNull(request, "request");

        try {
            // Обробник виконує команди складу, які прийшли з TCP або UDP після розшифрування пакета.
            return switch (request.type()) {
                case GET_QUANTITY -> CommandResponse.quantity(
                        warehouseService.getQuantity(requireProductName(request))
                );
                case WRITE_OFF -> {
                    warehouseService.writeOff(requireProductName(request), requireQuantity(request));
                    yield CommandResponse.success();
                }
                case ADD_QUANTITY -> {
                    warehouseService.addQuantity(requireProductName(request), requireQuantity(request));
                    yield CommandResponse.success();
                }
                case ADD_GROUP -> {
                    warehouseService.addGroup(requireGroupName(request));
                    yield CommandResponse.success();
                }
                case ADD_PRODUCT_TO_GROUP -> {
                    warehouseService.addProductToGroup(requireGroupName(request), requireProductName(request));
                    yield CommandResponse.success();
                }
                case SET_PRICE -> {
                    warehouseService.setPrice(requireProductName(request), requirePrice(request));
                    yield CommandResponse.success();
                }
                case OK, ERROR -> CommandResponse.error("Responses cannot be processed as commands");
            };
        } catch (RuntimeException e) {
            // Некоректна команда не зупиняє сервер, а повертається клієнту як відповідь з помилкою.
            return CommandResponse.error(e.getMessage());
        }
    }

    private static String requireGroupName(CommandRequest request) {
        if (request.groupName() == null || request.groupName().isBlank()) {
            throw new IllegalArgumentException("groupName is required");
        }
        return request.groupName();
    }

    private static String requireProductName(CommandRequest request) {
        if (request.productName() == null || request.productName().isBlank()) {
            throw new IllegalArgumentException("productName is required");
        }
        return request.productName();
    }

    private static int requireQuantity(CommandRequest request) {
        if (request.quantity() == null || request.quantity() <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        return request.quantity();
    }

    private static BigDecimal requirePrice(CommandRequest request) {
        if (request.price() == null || request.price().signum() < 0) {
            throw new IllegalArgumentException("price must be zero or positive");
        }
        return request.price();
    }
}
