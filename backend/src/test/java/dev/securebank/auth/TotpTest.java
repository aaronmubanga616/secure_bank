package dev.securebank.auth;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;

class TotpTest {
    private final Totp totp = new Totp();
    // RFC 6238 SHA-1 test secret, "12345678901234567890" encoded as base32.
    private final String secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
    @Test void matchesRfc6238VectorsAtSixDigits() {
        assertThat(totp.atStep(secret, 59 / 30)).isEqualTo("287082");
        assertThat(totp.atStep(secret, 1111111109L / 30)).isEqualTo("081804");
        assertThat(totp.atStep(secret, 20000000000L / 30)).isEqualTo("353130");
    }
    @Test void acceptsBoundedClockSkewAndRejectsMalformedOrExpiredCodes() {
        assertThat(totp.validStep(secret, "287082", Instant.ofEpochSecond(59))).isEqualTo(1);
        assertThat(totp.validStep(secret, "287082", Instant.ofEpochSecond(120))).isEqualTo(-1);
        assertThat(totp.validStep(secret, "123", Instant.ofEpochSecond(59))).isEqualTo(-1);
    }
}
