package ua.practice.protocol;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Objects;

public final class AesMessageCipher {
    private static final String ALGORITHM = "AES";
    // CBC потребує IV, а PKCS5Padding дозволяє шифрувати повідомлення довільної довжини.
    private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";
    private static final int IV_LENGTH = 16;

    private final SecretKey key;
    private final SecureRandom secureRandom;

    public AesMessageCipher(SecretKey key) {
        // SecureRandom створюється тут, щоб кожне шифрування мало випадковий IV.
        this(key, new SecureRandom());
    }

    AesMessageCipher(SecretKey key, SecureRandom secureRandom) {
        this.key = Objects.requireNonNull(key, "key");
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
    }

    public static SecretKey keyFromBytes(byte[] keyBytes) {
        Objects.requireNonNull(keyBytes, "keyBytes");
        int length = keyBytes.length;
        if (length != 16 && length != 24 && length != 32) {
            // AES підтримує ключі 128, 192 або 256 біт.
            throw new IllegalArgumentException("AES key must be 16, 24, or 32 bytes long");
        }
        return new SecretKeySpec(Arrays.copyOf(keyBytes, keyBytes.length), ALGORITHM);
    }

    public byte[] encrypt(byte[] plainMessage) {
        Objects.requireNonNull(plainMessage, "plainMessage");

        byte[] iv = new byte[IV_LENGTH];
        // Новий IV для кожного пакета робить однакові повідомлення різними у шифротексті.
        secureRandom.nextBytes(iv);

        try {
            // Cipher з JCA виконує саме криптографічне перетворення.
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));
            byte[] ciphertext = cipher.doFinal(plainMessage);

            // IV не є секретом, тому він передається перед шифротекстом.
            byte[] encryptedMessage = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, encryptedMessage, 0, iv.length);
            System.arraycopy(ciphertext, 0, encryptedMessage, iv.length, ciphertext.length);
            return encryptedMessage;
        } catch (GeneralSecurityException e) {
            throw new ProtocolException("Failed to encrypt message", e);
        }
    }

    public byte[] decrypt(byte[] encryptedMessage) {
        Objects.requireNonNull(encryptedMessage, "encryptedMessage");
        if (encryptedMessage.length <= IV_LENGTH) {
            throw new ProtocolException("Encrypted message is too short");
        }

        // Перші 16 байтів - це IV, решта - власне зашифровані дані.
        byte[] iv = Arrays.copyOfRange(encryptedMessage, 0, IV_LENGTH);
        byte[] ciphertext = Arrays.copyOfRange(encryptedMessage, IV_LENGTH, encryptedMessage.length);

        try {
            // Для дешифрування використовується той самий ключ і IV, який прийшов у пакеті.
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));
            return cipher.doFinal(ciphertext);
        } catch (GeneralSecurityException e) {
            throw new ProtocolException("Failed to decrypt message", e);
        }
    }
}
