package ua.practice.protocol;

import java.util.Objects;

public final class Crc16 {
    // Поліном 0x8005 використовується для контрольної суми CRC16.
    private static final int POLYNOMIAL = 0x8005;

    private Crc16() {
    }

    public static int calculate(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        return calculate(bytes, 0, bytes.length);
    }

    public static int calculate(byte[] bytes, int offset, int length) {
        Objects.requireNonNull(bytes, "bytes");
        // Перевірка меж не дозволяє випадково рахувати CRC за межами масиву.
        if (offset < 0 || length < 0 || offset > bytes.length - length) {
            throw new IndexOutOfBoundsException("Invalid CRC16 range");
        }

        int crc = 0x0000;
        for (int index = offset; index < offset + length; index++) {
            int current = bytes[index] & 0xFF;
            // Кожен байт обробляється побітово від старшого біта до молодшого.
            for (int mask = 0x80; mask != 0; mask >>>= 1) {
                if ((crc & 0x8000) != 0) {
                    crc = (crc << 1) ^ POLYNOMIAL;
                } else {
                    crc <<= 1;
                }

                if ((current & mask) != 0) {
                    crc ^= POLYNOMIAL;
                }

                crc &= 0xFFFF;
            }
        }
        // int використовується тільки як контейнер для беззнакового 16-бітного значення.
        return crc;
    }
}
