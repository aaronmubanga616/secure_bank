package dev.securebank.events;

import dev.securebank.notifications.NotificationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Component
@ConditionalOnProperty(name="securebank.events-enabled",havingValue="true",matchIfMissing=true)
public class NotificationConsumer {
    private final EventService events;
    private final NotificationService notifications;
    public NotificationConsumer(EventService events,NotificationService notifications) { this.events=events; this.notifications=notifications; }
    @KafkaListener(topics=KafkaConfig.TOPIC,groupId="securebank-notifications")
    @Transactional
    public void receive(String payload) {
        var event=events.parse(payload);
        if (!events.claim("notifications",event.id()) || event.userId()==null) return;
        var data=event.data();
        switch(event.type()) {
            case "NotificationRequested"->notifications.add(event.userId(),event.id(),data.get("title").toString(),data.get("message").toString(),data.get("category").toString());
            case "TransactionCompleted"->{
                String amount="K"+data.get("amount");
                notifications.add(event.userId(),event.id(),"Transaction completed",amount+" in simulated funds was processed successfully.","TRANSACTION");
                Object destination=data.get("destinationUser");
                if (destination!=null && !destination.toString().equals(event.userId().toString())) notifications.add(UUID.fromString(destination.toString()),event.id(),"Money received","You received "+amount+" in simulated funds.","TRANSACTION");
            }
            case "TransactionFailed"->notifications.add(event.userId(),event.id(),"Transaction declined","No funds moved. Open the transaction for details.","TRANSACTION");
            case "TransactionFlagged"->notifications.add(event.userId(),event.id(),"Transfer under review","Your transaction is being reviewed. No funds have moved.","SECURITY");
            case "FraudAlertCreated"->notifications.add(event.userId(),event.id(),"Security alert","We detected unusual activity in your simulator account.","SECURITY");
            default->{ /* Other event types belong to other projections. */ }
        }
    }
}
