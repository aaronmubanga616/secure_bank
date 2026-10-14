package dev.securebank.auth;

import org.junit.jupiter.api.Test;
import java.util.Base64;
import static org.assertj.core.api.Assertions.*;

class CryptoTest {
    private final Crypto crypto = new Crypto(Base64.getEncoder().encodeToString(new byte[32]));
    @Test void encryptsWithUniqueNoncesAndAuthenticatesCiphertext() {
        String first = crypto.encrypt("JBSWY3DPEHPK3PXP");
        assertThat(crypto.encrypt("JBSWY3DPEHPK3PXP")).isNotEqualTo(first);
        assertThat(crypto.decrypt(first)).isEqualTo("JBSWY3DPEHPK3PXP");
        byte[] changed = Base64.getDecoder().decode(first); changed[20] ^= 1;
        assertThatThrownBy(() -> crypto.decrypt(Base64.getEncoder().encodeToString(changed))).isInstanceOf(IllegalStateException.class);
    }
    @Test void rejectsInvalidKeyLength() {
        assertThatThrownBy(() -> new Crypto(Base64.getEncoder().encodeToString(new byte[16]))).isInstanceOf(IllegalArgumentException.class);
    }
}
