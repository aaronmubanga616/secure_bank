package dev.securebank.transactions;

import dev.securebank.audit.AuditService;
import dev.securebank.common.ApiException;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.UUID;

@Service
public class LimitService {
    public record Limits(
        @NotNull @DecimalMin("0.01") @DecimalMax("1000000000") @Digits(integer=10,fraction=2) BigDecimal perTransaction,
        @NotNull @DecimalMin("0.01") @DecimalMax("1000000000") @Digits(integer=10,fraction=2) BigDecimal dailyTransfer,
        @NotNull @DecimalMin("0.01") @DecimalMax("1000000000") @Digits(integer=10,fraction=2) BigDecimal dailyWithdrawal,
        @NotNull @DecimalMin("0.01") @DecimalMax("1000000000") @Digits(integer=10,fraction=2) BigDecimal dailyDeposit) {
        public BigDecimal daily(TransactionDtos.Type type) { return switch(type) { case TRANSFER->dailyTransfer; case WITHDRAWAL->dailyWithdrawal; case DEPOSIT->dailyDeposit; }; }
        public Map<String,String> view() { return Map.of("perTransaction",perTransaction.setScale(2).toPlainString(),"dailyTransfer",dailyTransfer.setScale(2).toPlainString(),"dailyWithdrawal",dailyWithdrawal.setScale(2).toPlainString(),"dailyDeposit",dailyDeposit.setScale(2).toPlainString()); }
    }
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    public LimitService(JdbcTemplate jdbc,AuditService audit) { this.jdbc=jdbc; this.audit=audit; }
    public Limits current() {
        return jdbc.queryForObject("SELECT * FROM transaction_limits WHERE id=1",(r,n)->new Limits(r.getBigDecimal("per_transaction"),r.getBigDecimal("daily_transfer"),r.getBigDecimal("daily_withdrawal"),r.getBigDecimal("daily_deposit")));
    }
    @Transactional public Map<String,String> update(UUID actor,Limits limits) {
        for (BigDecimal amount:new BigDecimal[]{limits.perTransaction(),limits.dailyTransfer(),limits.dailyWithdrawal(),limits.dailyDeposit()}) {
            try { if (amount==null || amount.signum()<=0 || amount.compareTo(new BigDecimal("1000000000"))>0) throw new ArithmeticException(); amount.setScale(2,RoundingMode.UNNECESSARY); }
            catch(ArithmeticException error) { throw ApiException.badRequest("INVALID_LIMIT","Limits must be positive amounts with at most two decimal places, up to K1 billion."); }
        }
        jdbc.update("UPDATE transaction_limits SET per_transaction=?,daily_transfer=?,daily_withdrawal=?,daily_deposit=?,updated_at=now() WHERE id=1",
            limits.perTransaction(),limits.dailyTransfer(),limits.dailyWithdrawal(),limits.dailyDeposit());
        audit.record(actor,"LIMITS_CHANGED","SUCCESS","transaction-limits"); return current().view();
    }
}
