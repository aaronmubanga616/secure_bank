package dev.securebank.scheduled;

import dev.securebank.transactions.TransactionService;
import dev.securebank.transactions.TransactionDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
public class ScheduledPaymentRunner {
    private final TransactionService transactions;
    public ScheduledPaymentRunner(TransactionService transactions) { this.transactions=transactions; }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public TransactionView pay(UUID owner,UUID schedule,Instant due,UUID source,String destination,BigDecimal amount,String description) {
        return transactions.create(owner,new CreateTransaction(Type.TRANSFER,source,destination,amount,description),Recurrence.key(schedule,due));
    }
}
