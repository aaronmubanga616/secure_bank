package dev.securebank.auth;

import static dev.securebank.auth.AuthDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;

import dev.securebank.common.ApiException;
import dev.securebank.notifications.EmailGateway;
import dev.securebank.users.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(
    properties = {
      "securebank.jwt-secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
      "securebank.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
      "securebank.events-enabled=false",
      "securebank.jobs-enabled=false",
      "securebank.monitoring-enabled=false"
    })
class AuthenticationIT {
  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

  @Container
  static GenericContainer<?> redis =
      new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.data.redis.host", redis::getHost);
    registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    registry.add("spring.data.redis.password", () -> "");
  }

  @Autowired AuthService auth;
  @Autowired UserRepository users;
  @Autowired JdbcTemplate jdbc;
  @Autowired JwtDecoder decoder;
  @Autowired Totp totp;
  @Autowired Crypto crypto;
  @MockitoBean EmailGateway mail;
  private final String password = "A test password 123!";

  private String mailToken(String email, String subject) {
    String encrypted =
        jdbc.queryForObject(
            "SELECT encrypted_body FROM mail_queue WHERE recipient=? AND subject=? ORDER BY created_at DESC LIMIT 1",
            String.class,
            email,
            subject);
    return crypto.decrypt(encrypted, "mail:v1").split("#token=")[1].split("\\s")[0];
  }

  private String registerAndVerify() {
    String email = "customer-" + UUID.randomUUID() + "@example.test";
    auth.register(new Register(email, "Test Customer", password));
    assertThatThrownBy(() -> auth.login(new Login(email, password)))
        .isInstanceOf(ApiException.class)
        .extracting(e -> ((ApiException) e).code())
        .isEqualTo("EMAIL_UNVERIFIED");
    String token = mailToken(email, "Verify your SecureBank email");
    auth.verifyEmail(token);
    assertThatThrownBy(() -> auth.verifyEmail(token)).isInstanceOf(ApiException.class);
    return email;
  }

  @Test
  void rotatesRefreshTokensAndRevokesTheFamilyOnReplay() {
    String email = registerAndVerify();
    var first = auth.login(new Login(email, password));
    var second = auth.refresh(first.refreshToken());
    assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
    assertThat(decoder.decode(second.accessToken()).getClaimAsString("role")).isEqualTo("CUSTOMER");
    assertThat(
            jdbc.queryForObject(
                "SELECT token_hash FROM refresh_tokens WHERE token_hash=?",
                String.class,
                Crypto.hash(second.refreshToken())))
        .hasSize(64)
        .isNotEqualTo(second.refreshToken());
    assertThatThrownBy(() -> auth.refresh(first.refreshToken())).isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> decoder.decode(second.accessToken())).isInstanceOf(JwtException.class);
    assertThatThrownBy(() -> auth.refresh(second.refreshToken())).isInstanceOf(ApiException.class);
  }

  @Test
  void totpAndRecoveryCodesCannotBeReplayed() {
    String email = registerAndVerify();
    var first = auth.login(new Login(email, password));
    var setup = auth.setupMfa(first.user().id(), password);
    String code = totp.atStep(setup.secret(), Instant.now().getEpochSecond() / 30);
    var recovery = auth.confirmMfa(first.user().id(), code);
    assertThat(users.findById(first.user().id()).orElseThrow().mfaSecret())
        .doesNotContain(setup.secret());
    assertThatThrownBy(() -> decoder.decode(first.accessToken())).isInstanceOf(JwtException.class);
    var challenge = auth.login(new Login(email, password));
    assertThat(challenge.mfaRequired()).isTrue();
    assertThat(challenge.accessToken()).isNull();
    var signedIn = auth.verifyMfa(new MfaVerify(challenge.challenge(), code));
    assertThat(signedIn.accessToken()).isNotBlank();
    var next = auth.login(new Login(email, password));
    assertThatThrownBy(() -> auth.verifyMfa(new MfaVerify(next.challenge(), code)))
        .isInstanceOf(ApiException.class);
    String recoveryCode = recovery.recoveryCodes().getFirst();
    assertThat(auth.verifyMfa(new MfaVerify(next.challenge(), recoveryCode)).accessToken())
        .isNotBlank();
    var last = auth.login(new Login(email, password));
    assertThatThrownBy(() -> auth.verifyMfa(new MfaVerify(last.challenge(), recoveryCode)))
        .isInstanceOf(ApiException.class);
  }

  @Test
  void repeatedLoginAttemptsAreLockedOutAndLogoutInvalidatesAccess() {
    String email = registerAndVerify();
    var session = auth.login(new Login(email, password));
    auth.logout(session.refreshToken());
    assertThatThrownBy(() -> decoder.decode(session.accessToken()))
        .isInstanceOf(JwtException.class);
    for (int i = 0; i < 8; i++)
      assertThatThrownBy(() -> auth.login(new Login(email, "incorrect")))
          .isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> auth.login(new Login(email, password)))
        .isInstanceOf(ApiException.class)
        .extracting(e -> ((ApiException) e).code())
        .isEqualTo("RATE_LIMITED");
  }

  @Test
  void passwordResetInvalidatesSessionsAndOnlyAcceptsTheTokenOnce() {
    String email = registerAndVerify();
    var session = auth.login(new Login(email, password));
    auth.requestVerification(email, "RESET_PASSWORD");
    String token = mailToken(email, "Reset your SecureBank password");
    auth.resetPassword(new ResetPassword(token, "A different password 456!"));
    assertThatThrownBy(() -> decoder.decode(session.accessToken()))
        .isInstanceOf(JwtException.class);
    assertThatThrownBy(() -> auth.login(new Login(email, password)))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> auth.resetPassword(new ResetPassword(token, "Another long password!")))
        .isInstanceOf(ApiException.class);
    assertThat(auth.login(new Login(email, "A different password 456!")).accessToken())
        .isNotBlank();
  }
}
