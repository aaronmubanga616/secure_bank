package dev.securebank.events;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(
    name = "securebank.events-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class OutboxPublisher {
  private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
  private final JdbcTemplate jdbc;
  private final KafkaTemplate<String, String> kafka;

  public OutboxPublisher(JdbcTemplate jdbc, KafkaTemplate<String, String> kafka) {
    this.jdbc = jdbc;
    this.kafka = kafka;
  }

  @Scheduled(fixedDelayString = "${securebank.outbox-interval-ms:1000}")
  @Transactional
  public void publish() {
    var rows =
        jdbc.queryForList(
            "SELECT id,user_id,payload::text,attempts FROM outbox_events WHERE published_at IS NULL AND next_attempt_at<=now() ORDER BY created_at,id FOR UPDATE SKIP LOCKED LIMIT 20");
    for (var row : rows) {
      UUID id = (UUID) row.get("id");
      try {
        kafka
            .send(
                KafkaConfig.TOPIC,
                row.get("user_id") == null ? id.toString() : row.get("user_id").toString(),
                (String) row.get("payload"))
            .get(6, TimeUnit.SECONDS);
        jdbc.update(
            "UPDATE outbox_events SET published_at=now(),attempts=attempts+1 WHERE id=?", id);
      } catch (Exception error) {
        int attempts = ((Number) row.get("attempts")).intValue() + 1;
        jdbc.update(
            "UPDATE outbox_events SET attempts=?,next_attempt_at=now()+(? * interval '1 second') WHERE id=?",
            attempts,
            Math.min(300, 1L << Math.min(attempts, 8)),
            id);
        if (error instanceof InterruptedException) Thread.currentThread().interrupt();
        log.warn("Event publication delayed for {}", id);
        break;
      }
    }
  }
}
