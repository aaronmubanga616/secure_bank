package dev.securebank.events;

import dev.securebank.fraud.RiskService;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(
    name = "securebank.events-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class FraudConsumer {
  private final EventService events;
  private final RiskService risk;
  private final JdbcTemplate jdbc;

  public FraudConsumer(EventService events, RiskService risk, JdbcTemplate jdbc) {
    this.events = events;
    this.risk = risk;
    this.jdbc = jdbc;
  }

  @KafkaListener(topics = KafkaConfig.TOPIC, groupId = "securebank-fraud")
  @Transactional
  public void receive(String payload) {
    var event = events.parse(payload);
    if (!events.claim("fraud", event.id())
        || !event.type().equals("TransactionCompleted")
        || !"TRANSFER".equals(event.data().get("type"))) return;
    UUID id = UUID.fromString(event.data().get("transactionId").toString());
    UUID destination =
        jdbc.queryForObject(
            "SELECT destination_account FROM transactions WHERE id=?", UUID.class, id);
    // Streaming checks may open an investigation, but never rewrite a posted journal.
    risk.alert(
        event.userId(),
        id,
        risk.assess(
            event.userId(),
            destination,
            new BigDecimal(event.data().get("amount").toString()),
            id));
  }
}
