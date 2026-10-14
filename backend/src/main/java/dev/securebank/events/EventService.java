package dev.securebank.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

@Service
public class EventService {
    public record Event(UUID id,String type,UUID userId,String resource,Map<String,Object> data,Instant occurredAt,String correlationId) {}
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public EventService(JdbcTemplate jdbc,ObjectMapper mapper) { this.jdbc=jdbc; this.mapper=mapper; }
    @Transactional(propagation=Propagation.MANDATORY)
    public UUID record(String type,UUID user,String resource,Map<String,Object> data) {
        UUID id=UUID.randomUUID();
        String correlation=MDC.get("correlationId");
        var event=new Event(id,type,user,resource,data,Instant.now(),correlation==null?UUID.randomUUID().toString():correlation);
        try { jdbc.update("INSERT INTO outbox_events(id,event_type,user_id,payload) VALUES (?,?,?,?::jsonb)",id,type,user,mapper.writeValueAsString(event)); }
        catch(com.fasterxml.jackson.core.JsonProcessingException error) { throw new IllegalStateException("Cannot serialize the domain event",error); }
        return id;
    }
    public Event parse(String value) {
        try {
            Event event=mapper.readValue(value,Event.class);
            if (event.id()==null || event.type()==null || event.data()==null) throw new IllegalArgumentException("Incomplete domain event");
            return event;
        } catch(Exception error) { throw new IllegalArgumentException("Invalid domain event envelope"); }
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public boolean claim(String consumer,UUID id) {
        return jdbc.update("INSERT INTO consumed_events(consumer,event_id) VALUES (?,?) ON CONFLICT DO NOTHING",consumer,id)==1;
    }
}
