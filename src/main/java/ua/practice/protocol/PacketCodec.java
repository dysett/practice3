package ua.practice.protocol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Objects;

public final class PacketCodec {
    // Magic byte 0x13 позначає початок коректного пакета.
    public static final int MAGIC = 0x13;

    // Перші 14 байтів заголовка входять у CRC заголовка.
    // Поле CRC самого заголовка в ці 14 байтів не входить.
    private static final int HEADER_WITHOUT_CRC_LENGTH = 14;
    // Повний заголовок має 16 байтів: magic, source, packetId, length і header CRC.
    private static final int HEADER_LENGTH = 16;
    private static final int PACKET_CRC_LENGTH = 2;
    private static final int MIN_PACKET_LENGTH = HEADER_LENGTH + PACKET_CRC_LENGTH;

    private final AesMessageCipher cipher;

    public PacketCodec(AesMessageCipher cipher) {
        this.cipher = Objects.requireNonNull(cipher, "cipher");
    }

    public byte[] encode(Packet packet) {
        Objects.requireNonNull(packet, "packet");

        // Спочатку доменне повідомлення переводиться у байти, а вже потім шифрується.
        byte[] plainMessage = MessageCodec.encode(packet.getMessage());
        byte[] encryptedMessage = cipher.encrypt(plainMessage);

        // Усі числові поля записуються big-endian, як вказано в умові.
        // Порядок запису тут повторює таблицю структури пакета із завдання.
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_LENGTH + encryptedMessage.length + PACKET_CRC_LENGTH)
                .order(ByteOrder.BIG_ENDIAN);
        buffer.put((byte) MAGIC);
        buffer.put((byte) packet.getSource());
        buffer.putLong(packet.getPacketId());
        buffer.putInt(encryptedMessage.length);

        byte[] packetBytes = buffer.array();
        // CRC заголовка рахується до поля самого CRC.
        // На цьому етапі перші 14 байтів уже заповнені.
        int headerCrc = Crc16.calculate(packetBytes, 0, HEADER_WITHOUT_CRC_LENGTH);
        buffer.putShort((short) headerCrc);

        buffer.put(encryptedMessage);

        // Другий CRC перевіряє зашифроване тіло повідомлення.
        int messageCrc = Crc16.calculate(encryptedMessage);
        buffer.putShort((short) messageCrc);
        return packetBytes;
    }

    public Packet decode(byte[] packetBytes) {
        Objects.requireNonNull(packetBytes, "packetBytes");
        if (packetBytes.length < MIN_PACKET_LENGTH) {
            throw new ProtocolException("Packet is shorter than the minimum header and CRC length");
        }

        ByteBuffer buffer = ByteBuffer.wrap(packetBytes).order(ByteOrder.BIG_ENDIAN);
        int magic = Byte.toUnsignedInt(buffer.get());
        if (magic != MAGIC) {
            // Якщо перший байт неправильний, це точно не наш пакет.
            throw new ProtocolException("Invalid packet magic byte");
        }

        int source = Byte.toUnsignedInt(buffer.get());
        long packetId = buffer.getLong();
        int encryptedMessageLength = buffer.getInt();
        if (encryptedMessageLength < 0) {
            throw new ProtocolException("Message length is negative");
        }

        int expectedLength = HEADER_LENGTH + encryptedMessageLength + PACKET_CRC_LENGTH;
        if (packetBytes.length != expectedLength) {
            // wLen має точно відповідати фактичній довжині зашифрованого повідомлення.
            throw new ProtocolException("Packet length does not match wLen");
        }

        int actualHeaderCrc = Short.toUnsignedInt(buffer.getShort());
        int expectedHeaderCrc = Crc16.calculate(packetBytes, 0, HEADER_WITHOUT_CRC_LENGTH);
        if (actualHeaderCrc != expectedHeaderCrc) {
            // Невідповідність CRC означає, що заголовок пошкоджений або змінений.
            throw new ProtocolException("Header CRC16 mismatch");
        }

        byte[] encryptedMessage = Arrays.copyOfRange(packetBytes, HEADER_LENGTH, HEADER_LENGTH + encryptedMessageLength);
        int actualMessageCrc = Short.toUnsignedInt(buffer.getShort(HEADER_LENGTH + encryptedMessageLength));
        int expectedMessageCrc = Crc16.calculate(encryptedMessage);
        if (actualMessageCrc != expectedMessageCrc) {
            // Тіло перевіряється до дешифрування, щоб не обробляти пошкоджені байти.
            throw new ProtocolException("Message CRC16 mismatch");
        }

        byte[] plainMessage = cipher.decrypt(encryptedMessage);
        // Після дешифрування тіло пакета знову розбирається як Message.
        Message message = MessageCodec.decode(plainMessage);
        return new Packet(source, packetId, message);
    }
}
