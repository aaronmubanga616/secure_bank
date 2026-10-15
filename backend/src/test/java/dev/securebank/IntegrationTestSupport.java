package dev.securebank;

import dev.securebank.auth.AuthService;
import dev.securebank.notifications.EmailGateway;
import dev.securebank.users.User;
import dev.securebank.users.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import java.util.UUID;

@SpringBootTest(properties={
    "securebank.jwt-secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "securebank.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "securebank.events-enabled=false", "securebank.jobs-enabled=false", "securebank.monitoring-enabled=false",
    "securebank.metrics-token=test-monitoring-token-with-32-characters"
})
@AutoConfigureMockMvc
@org.springframework.context.annotation.Import(IntegrationTestSupport.TestClock.class)
public abstract class IntegrationTestSupport {
    @org.springframework.boot.test.context.TestConfiguration
    static class TestClock {
        @org.springframework.context.annotation.Bean @org.springframework.context.annotation.Primary
        java.time.Clock fixedRiskClock() { return java.time.Clock.fixed(java.time.Instant.parse("2026-10-15T12:00:00Z"),java.time.ZoneOffset.UTC); }
    }
    // Shared for the test JVM; Ryuk removes them at JVM exit. Spring's cached context keeps valid endpoints.
    protected static final PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17-alpine");
    protected static final GenericContainer<?> redis=new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);
    static { postgres.start(); redis.start(); }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",postgres::getJdbcUrl);
        registry.add("spring.datasource.username",postgres::getUsername);
        registry.add("spring.datasource.password",postgres::getPassword);
        registry.add("spring.data.redis.host",redis::getHost);
        registry.add("spring.data.redis.port",()->redis.getMappedPort(6379));
        registry.add("spring.data.redis.password",()->"");
    }
    @Autowired protected UserRepository users;
    @Autowired protected PasswordEncoder passwords;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected MockMvc mvc;
    @Autowired protected AuthService auth;
    @MockitoBean protected EmailGateway mail;
    protected static final String PASSWORD="A test password 123!";
    protected User customer() {
        User user=User.register("customer-"+UUID.randomUUID()+"@example.test","Test Customer",passwords.encode(PASSWORD));
        user.verifyEmail(); return users.saveAndFlush(user);
    }
}
