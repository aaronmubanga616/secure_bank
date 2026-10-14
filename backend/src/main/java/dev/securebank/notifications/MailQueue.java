package dev.securebank.notifications;

import dev.securebank.auth.Crypto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class MailQueue {
    private final JdbcTemplate jdbc;
    private final Crypto crypto;
    private final EmailGateway gateway;
    public MailQueue(JdbcTemplate jdbc,Crypto crypto,EmailGateway gateway) { this.jdbc=jdbc; this.crypto=crypto; this.gateway=gateway; }
    @Transactional(propagation=Propagation.MANDATORY)
    public UUID enqueue(String recipient,String subject,String message) {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO mail_queue(id,recipient,subject,encrypted_body) VALUES (?,?,?,?)",id,recipient,subject,crypto.encrypt(message,"mail:v1"));
        return id;
    }
    @Transactional public void deliver(UUID id) {
        var rows=jdbc.queryForList("SELECT * FROM mail_queue WHERE id=? AND status='PENDING' AND next_attempt_at<=now() FOR UPDATE SKIP LOCKED",id);
        if (rows.isEmpty()) return;
        var row=rows.getFirst(); int attempts=((Number)row.get("attempts")).intValue()+1;
        try {
            gateway.deliver((String)row.get("recipient"),(String)row.get("subject"),crypto.decrypt((String)row.get("encrypted_body"),"mail:v1"));
            jdbc.update("UPDATE mail_queue SET status='SENT',sent_at=now(),attempts=? WHERE id=?",attempts,id);
        } catch(Exception error) {
            jdbc.update("UPDATE mail_queue SET status=?,attempts=?,next_attempt_at=now()+(? * interval '1 second') WHERE id=?",attempts>=5?"DEAD":"PENDING",attempts,Math.min(300,15L<<Math.min(attempts-1,4)),id);
        }
    }
}
