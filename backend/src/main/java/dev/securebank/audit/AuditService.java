package dev.securebank.audit;

import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class AuditService {
    private final JdbcTemplate jdbc;
    public AuditService(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public void record(UUID user, String action, String result, String resource) {
        String correlation = MDC.get("correlationId");
        jdbc.update("INSERT INTO audit_logs(id,user_id,action,result,resource,correlation_id) VALUES (?,?,?,?,?,?)",
            UUID.randomUUID(), user, action, result, resource, correlation == null ? UUID.randomUUID().toString() : correlation);
    }
}
