package dev.securebank.events;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@ConditionalOnProperty(name="securebank.events-enabled",havingValue="true",matchIfMissing=true)
public class KafkaConfig {
    public static final String TOPIC="securebank.events";
    public static final String DLT="securebank.events.DLT";
    @Bean NewTopic domainEventsTopic() { return TopicBuilder.name(TOPIC).partitions(3).replicas(1).build(); }
    @Bean NewTopic deadLettersTopic() { return TopicBuilder.name(DLT).partitions(3).replicas(1).build(); }
    @Bean DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String,String> template) {
        var recoverer=new DeadLetterPublishingRecoverer(template,(record,error)->new TopicPartition(DLT,record.partition()));
        recoverer.setFailIfSendResultIsError(true);
        return new DefaultErrorHandler(recoverer,new FixedBackOff(1000,3));
    }
}
