package dev.securebank.monitoring;

import org.apache.kafka.clients.admin.Admin;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Configuration
public class MonitoringConfig {
    @Bean(destroyMethod="close")
    @ConditionalOnProperty(name="securebank.events-enabled",havingValue="true",matchIfMissing=true)
    Admin kafkaMonitor(@Value("${spring.kafka.bootstrap-servers}")String servers){return Admin.create(Map.of("bootstrap.servers",servers,"client.id","securebank-monitor","request.timeout.ms",2000,"default.api.timeout.ms",3000));}
    @Bean
    @ConditionalOnProperty(name="securebank.events-enabled",havingValue="true",matchIfMissing=true)
    HealthIndicator kafkaHealthIndicator(Admin kafkaMonitor){return ()->{try{kafkaMonitor.describeCluster().nodes().get(2,TimeUnit.SECONDS);return Health.up().build();}catch(Exception error){if(error instanceof InterruptedException)Thread.currentThread().interrupt();return Health.down().withDetail("reason","Broker is unavailable; events remain in the outbox.").build();}};}
}
