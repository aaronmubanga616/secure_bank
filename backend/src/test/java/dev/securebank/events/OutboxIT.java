package dev.securebank.events;

import dev.securebank.IntegrationTestSupport;
import dev.securebank.notifications.MailQueue;
import dev.securebank.auth.Crypto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OutboxIT extends IntegrationTestSupport {
    @Autowired EventService events;
    @Autowired MailQueue queue;
    @Autowired Crypto crypto;
    @Autowired TransactionTemplate transaction;

    @Test void rolledBackChangesCannotPublishEventsOrMail() {
        var user=customer(); String resource="rollback-"+UUID.randomUUID();
        assertThatThrownBy(()->transaction.execute(status->{
            events.record("TestEvent",user.id(),resource,Map.of());
            queue.enqueue(user.email(),resource,"private-token");
            throw new IllegalStateException("Force rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE payload->>'resource'=?",Integer.class,resource)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mail_queue WHERE subject=?",Integer.class,resource)).isZero();
    }

    @Test void mailIsEncryptedRetriedAndEventuallyParked() {
        var user=customer();
        UUID id=transaction.execute(status->queue.enqueue(user.email(),"Test delivery","private-token"));
        String encrypted=jdbc.queryForObject("SELECT encrypted_body FROM mail_queue WHERE id=?",String.class,id);
        assertThat(encrypted).doesNotContain("private-token");
        assertThat(crypto.decrypt(encrypted,"mail:v1")).isEqualTo("private-token");
        doThrow(new IllegalStateException("SMTP unavailable")).when(mail).deliver(user.email(),"Test delivery","private-token");
        for(int attempt=0;attempt<5;attempt++) {
            jdbc.update("UPDATE mail_queue SET next_attempt_at=now() WHERE id=?",id); queue.deliver(id);
        }
        assertThat(jdbc.queryForObject("SELECT status FROM mail_queue WHERE id=?",String.class,id)).isEqualTo("DEAD");
        queue.deliver(id);
        verify(mail,times(5)).deliver(user.email(),"Test delivery","private-token");
    }
}
