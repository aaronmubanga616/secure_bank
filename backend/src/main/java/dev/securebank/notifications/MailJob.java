package dev.securebank.notifications;

import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "securebank.jobs-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class MailJob {
  private final JdbcTemplate jdbc;
  private final MailQueue queue;

  public MailJob(JdbcTemplate jdbc, MailQueue queue) {
    this.jdbc = jdbc;
    this.queue = queue;
  }

  @Scheduled(fixedDelayString = "${securebank.mail-interval-ms:3000}")
  public void run() {
    for (UUID id :
        jdbc.queryForList(
            "SELECT id FROM mail_queue WHERE status='PENDING' AND next_attempt_at<=now() ORDER BY created_at LIMIT 20",
            UUID.class)) queue.deliver(id);
  }
}
