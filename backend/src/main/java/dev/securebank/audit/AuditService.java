package dev.securebank.audit;

import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.UUID;
import java.util.Map;
import dev.securebank.events.EventService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

@Service
public class AuditService {
    private final JdbcTemplate jdbc;
    private final EventService events;
    public AuditService(JdbcTemplate jdbc,EventService events) { this.jdbc = jdbc; this.events=events; }
    @Transactional(propagation=Propagation.MANDATORY)
    public void record(UUID user, String action, String result, String resource) {
        String correlation = MDC.get("correlationId");
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO audit_logs(id,user_id,action,result,resource,correlation_id) VALUES (?,?,?,?,?,?)",
            id, user, action, result, resource, correlation == null ? UUID.randomUUID().toString() : correlation);
        events.record("AuditEventCreated",user,resource,Map.of("auditId",id.toString(),"action",action,"result",result));
        String title=switch(action) {
            case "LOGIN","MFA_LOGIN"->result.equals("SUCCESS")?"New sign-in":null;
            case "PASSWORD_CHANGED","PASSWORD_RESET"->"Password changed";
            case "MFA_ENABLED","MFA_DISABLED"->"Authenticator settings changed";
            case "BENEFICIARY_ADDED"->"Recipient saved";
            case "ACCOUNT_FROZEN"->"Account frozen";
            case "ACCOUNT_CREATED"->"Account opened";
            case "USER_REGISTERED"->"Welcome to SecureBank";
            default->null;
        };
        if (user!=null && title!=null) events.record("NotificationRequested",user,resource,Map.of("title",title,"message","Review this activity in your SecureBank simulator.","category",action.equals("ACCOUNT_CREATED") || action.equals("USER_REGISTERED")?"ACCOUNT":"SECURITY"));
    }
}
