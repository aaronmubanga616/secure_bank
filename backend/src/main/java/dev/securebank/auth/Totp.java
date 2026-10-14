package dev.securebank.auth;

import org.springframework.stereotype.Component;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;

@Component
public class Totp {
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    public String secret() {
        byte[] bytes = new byte[20]; new SecureRandom().nextBytes(bytes);
        StringBuilder result = new StringBuilder(); int buffer = 0, bits = 0;
        for (byte value : bytes) {
            buffer = (buffer << 8) | (value & 255); bits += 8;
            while (bits >= 5) { bits -= 5; result.append(ALPHABET.charAt((buffer >> bits) & 31)); }
        }
        return result.toString();
    }

    public long validStep(String secret, String code, Instant now) {
        if (code == null || !code.matches("[0-9]{6}")) return -1;
        long step = now.getEpochSecond() / 30;
        for (long candidate = step - 1; candidate <= step + 1; candidate++) {
            if (MessageDigest.isEqual(atStep(secret, candidate).getBytes(StandardCharsets.US_ASCII), code.getBytes(StandardCharsets.US_ASCII))) return candidate;
        }
        return -1;
    }

    public String atStep(String secret, long step) {
        try {
            byte[] key = decode(secret);
            Mac mac = Mac.getInstance("HmacSHA1"); mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] digest = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int offset = digest[digest.length - 1] & 15;
            int binary = ByteBuffer.wrap(digest, offset, 4).getInt() & 0x7fffffff;
            return String.format(Locale.ROOT, "%06d", binary % 1_000_000);
        } catch (Exception error) { throw new IllegalStateException("TOTP calculation failed", error); }
    }

    private byte[] decode(String value) {
        byte[] result = new byte[value.length() * 5 / 8]; int buffer = 0, bits = 0, index = 0;
        for (char character : value.toUpperCase(Locale.ROOT).toCharArray()) {
            int digit = ALPHABET.indexOf(character);
            if (digit < 0) throw new IllegalArgumentException("Invalid base32 secret");
            buffer = (buffer << 5) | digit; bits += 5;
            if (bits >= 8) { bits -= 8; result[index++] = (byte) (buffer >> bits); }
        }
        return result;
    }
}
