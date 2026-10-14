package dev.securebank.events;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Component
@ConditionalOnProperty(name="securebank.events-enabled",havingValue="true",matchIfMissing=true)
public class AuditConsumer {
    private final EventService events;
    private final JdbcTemplate jdbc;
    public AuditConsumer(EventService events,JdbcTemplate jdbc) { this.events=events; this.jdbc=jdbc; }
    @KafkaListener(topics=KafkaConfig.TOPIC,groupId="securebank-audit")
    @Transactional
    public void receive(String payload) {
        var event=events.parse(payload);
        if (!events.claim("audit",event.id()) || !event.type().equals("AuditEventCreated")) return;
        jdbc.update("INSERT INTO audit_projections(event_id,audit_id,action,result,resource) VALUES (?,?,?,?,?) ON CONFLICT DO NOTHING",
            event.id(),UUID.fromString(event.data().get("auditId").toString()),event.data().get("action"),event.data().get("result"),event.resource());
    }
}
