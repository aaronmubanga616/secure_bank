package dev.securebank.events;

import dev.securebank.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import java.time.Duration;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

@TestPropertySource(properties="securebank.events-enabled=true")
class KafkaDeliveryIT extends IntegrationTestSupport {
    static final KafkaContainer kafka=new KafkaContainer(DockerImageName.parse("apache/kafka:4.0.1"));
    static { kafka.start(); }
    @DynamicPropertySource static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers",kafka::getBootstrapServers);
    }
    @Autowired KafkaTemplate<String,String> template;
    @Autowired EventService events;
    @Autowired TransactionTemplate transaction;

    @Test void anEventIsDeliveredAndDuplicatesAreIgnored() throws Exception {
        var user=customer();
        UUID id=transaction.execute(status->events.record("NotificationRequested",user.id(),"test",Map.of("title","Reliable delivery","message","Test notification","category","SECURITY")));
        await().atMost(Duration.ofSeconds(30)).untilAsserted(()->assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id=? AND event_id=?",Integer.class,user.id(),id)).isEqualTo(1));
        String payload=jdbc.queryForObject("SELECT payload::text FROM outbox_events WHERE id=?",String.class,id);
        template.send(KafkaConfig.TOPIC,user.id().toString(),payload).get();
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(10)).untilAsserted(()->assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id=? AND event_id=?",Integer.class,user.id(),id)).isEqualTo(1));
    }

    @Test void poisonMessagesReachTheDeadLetterTopic() throws Exception {
        String poison="invalid-event-"+UUID.randomUUID();
        var config=new Properties(); config.put("bootstrap.servers",kafka.getBootstrapServers());
        config.put("group.id","dlt-test-"+UUID.randomUUID()); config.put("auto.offset.reset","earliest");
        try(var consumer=new KafkaConsumer<String,String>(config,new StringDeserializer(),new StringDeserializer())) {
            consumer.subscribe(List.of(KafkaConfig.DLT)); template.send(KafkaConfig.TOPIC,"poison",poison).get();
            await().atMost(Duration.ofSeconds(30)).until(()->{
                for(var record:consumer.poll(Duration.ofMillis(500))) if(poison.equals(record.value())) return true;
                return false;
            });
        }
    }
}
