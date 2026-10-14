package dev.securebank.notifications;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class SecurityMailListener {
    public record SecurityMail(String recipient, String subject, String message) {}
    private static final Logger log = LoggerFactory.getLogger(SecurityMailListener.class);
    private final EmailGateway gateway;
    public SecurityMailListener(EmailGateway gateway) { this.gateway = gateway; }
    @TransactionalEventListener
    public void send(SecurityMail mail) {
        try { gateway.deliver(mail.recipient(), mail.subject(), mail.message()); }
        catch (Exception error) { log.warn("Security email delivery failed; a new verification email can be requested."); }
    }
}
