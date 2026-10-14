package dev.securebank.transactions;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import java.util.function.Supplier;
import static dev.securebank.transactions.TransactionDtos.TransactionView;

@Service
public class IdempotencyService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public IdempotencyService(JdbcTemplate jdbc,ObjectMapper mapper) { this.jdbc=jdbc; this.mapper=mapper; }
    @Transactional(propagation=Propagation.MANDATORY)
    public TransactionView response(UUID owner,String key,Supplier<TransactionView> original) {
        var rows=jdbc.queryForList("SELECT response_json::text FROM idempotency_responses WHERE user_id=? AND idempotency_key=?",String.class,owner,key);
        try {
            if (!rows.isEmpty()) return mapper.readValue(rows.getFirst(),TransactionView.class);
            TransactionView value=original.get();
            jdbc.update("INSERT INTO idempotency_responses(user_id,idempotency_key,transaction_id,response_json) VALUES (?,?,?,?::jsonb)",owner,key,value.id(),mapper.writeValueAsString(value));
            return value;
        } catch(com.fasterxml.jackson.core.JsonProcessingException error) { throw new IllegalStateException("Cannot preserve the financial response",error); }
    }
}
