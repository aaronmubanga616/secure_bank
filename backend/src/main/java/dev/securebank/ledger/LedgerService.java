package dev.securebank.ledger;

import dev.securebank.common.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
public class LedgerService {
    public static final UUID SIMULATED_CASH=UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final JdbcTemplate jdbc;
    public LedgerService(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    public BigDecimal balance(UUID bankAccount) {
        var values=jdbc.queryForList("SELECT balance FROM account_balances WHERE account_id=?",BigDecimal.class,bankAccount);
        if (values.isEmpty()) throw ApiException.notFound();
        return values.getFirst().setScale(2);
    }

    @Transactional(propagation=Propagation.MANDATORY)
    public void post(UUID transaction,UUID source,UUID destination,BigDecimal requestedAmount) {
        BigDecimal amount;
        try { amount=requestedAmount.setScale(2,RoundingMode.UNNECESSARY); }
        catch (ArithmeticException error) { throw ApiException.badRequest("INVALID_AMOUNT","Amounts may have at most two decimal places."); }
        if (amount.signum()<=0) throw ApiException.badRequest("INVALID_AMOUNT","The amount must be positive.");
        UUID debit=source==null?SIMULATED_CASH:ledgerAccount(source);
        UUID credit=destination==null?SIMULATED_CASH:ledgerAccount(destination);
        if (debit.equals(credit)) throw ApiException.badRequest("SAME_ACCOUNT","Select different accounts.");
        jdbc.queryForList("SELECT id FROM ledger_accounts WHERE id IN (?,?) ORDER BY id FOR UPDATE",debit,credit);
        jdbc.update("INSERT INTO ledger_entries(id,transaction_id,ledger_account_id,direction,amount) VALUES (?,?,?,'DEBIT',?),(?,?,?,'CREDIT',?)",
            UUID.randomUUID(),transaction,debit,amount,UUID.randomUUID(),transaction,credit,amount);
    }

    private UUID ledgerAccount(UUID bankAccount) {
        var values=jdbc.queryForList("SELECT id FROM ledger_accounts WHERE bank_account_id=?",UUID.class,bankAccount);
        if (values.isEmpty()) throw ApiException.notFound();
        return values.getFirst();
    }
}
