package dev.securebank.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class DatabaseMigrationIT {
  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

  @Test
  void migrationsApplyToAnEmptyPostgresDatabaseAndCanBeValidatedAgain() throws Exception {
    Flyway flyway =
        Flyway.configure()
            .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .load();
    assertThat(flyway.migrate().success).isTrue();
    assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
    assertThat(flyway.migrate().migrationsExecuted).isZero();
    try (var connection =
            DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        var query =
            connection.prepareStatement("SELECT value FROM platform_metadata WHERE key = ?")) {
      query.setString(1, "mode");
      try (var result = query.executeQuery()) {
        assertThat(result.next()).isTrue();
        assertThat(result.getString(1)).isEqualTo("EDUCATIONAL_SIMULATOR");
      }
    }
  }
}
