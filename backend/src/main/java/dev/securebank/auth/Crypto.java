package dev.securebank.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Component
public class Crypto {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final SecretKeySpec key;

    public Crypto(@Value("${securebank.encryption-key}") String encodedKey) {
        byte[] bytes = Base64.getDecoder().decode(encodedKey);
        if (bytes.length != 32) throw new IllegalArgumentException("ENCRYPTION_KEY must encode exactly 32 random bytes.");
        key = new SecretKeySpec(bytes, "AES");
    }

    public static String token() {
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hash(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception error) { throw new IllegalStateException("SHA-256 is unavailable", error); }
    }

    public String encrypt(String plaintext) {
        return encrypt(plaintext,"mfa:v1");
    }
    public String encrypt(String plaintext,String purpose) {
        try {
            byte[] nonce = new byte[12]; RANDOM.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
            cipher.updateAAD(("securebank:"+purpose).getBytes(StandardCharsets.UTF_8));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(nonce.length + ciphertext.length).put(nonce).put(ciphertext).array());
        } catch (Exception error) { throw new IllegalStateException("Encryption failed", error); }
    }

    public String decrypt(String value) {
        return decrypt(value,"mfa:v1");
    }
    public String decrypt(String value,String purpose) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(Base64.getDecoder().decode(value));
            byte[] nonce = new byte[12]; buffer.get(nonce);
            byte[] ciphertext = new byte[buffer.remaining()]; buffer.get(ciphertext);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, nonce));
            cipher.updateAAD(("securebank:"+purpose).getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception error) { throw new IllegalStateException("Encrypted value could not be authenticated", error); }
    }
}
